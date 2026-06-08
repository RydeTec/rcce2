---
name: rcce2-test-writing
description: Write or modify a unit test under src/Tests/ in rcce2. Invoke whenever the user asks to test a module, cover a bug fix with a regression test, add coverage for a serialization round-trip, or asks about the test framework's Strict-mode + inline-stub pattern. Also invoke when CI is failing on a test file, when the known intermittent "ItemsTest Stack overflow" flake appears, or when the user wants to understand why a test compiles standalone but breaks under test.bat. The rcce2 test framework has a specific structure (Strict + EnableGC + inline type/function stubs to keep the dependency graph small) that does NOT match generic Blitz3D testing, and the build runner has a known-flake retry workflow that's documented nowhere else.
---

# rcce2 test writing

The rcce2 test suite lives under [src/Tests/](../../../src/Tests/) and runs via [test.bat](../../../test.bat) (Windows) or [test.sh](../../../test.sh) (Unix). The runner walks every `.bb` in `src/Tests/` and runs each through `blitzcc -t -w src` (test mode, working dir = `src`). First failure exits non-zero.

The framework itself is part of BlitzForge — `Test foo() ... End Test` blocks compile into test entry points that `-t` mode runs. Inside a Test block, `Assert(expr)` records a pass/fail and (importantly) **does not abort the test on failure** — every Assert runs and the test fails if any failed.

## The cardinal pattern: Strict + inline stubs

Test files do NOT include `Server.bb` or `Client.bb`. Those entry points pull in dozens of modules — network code, the BVM, the world, the renderer. A test compiling against them would take ages and need real handles, MySQL, etc.

Instead, every test file:

1. Opens with `Strict` + `EnableGC` so type/sigil mistakes are caught at compile time.
2. Inlines **type stubs** for any Type the module-under-test references but doesn't define itself.
3. Inlines **function stubs** for any cross-module functions the module-under-test calls that aren't being exercised.
4. Then `Include "Modules\TheModule.bb"`.
5. Then defines `Test ...()` blocks that exercise the module's exported functions.

Canonical example: [src/Tests/Modules/ItemsTest.bb](../../../src/Tests/Modules/ItemsTest.bb). Walks the pattern step by step. Read it before writing a new test.

### Why this matters

Items.bb references `Attributes` (a Type defined in Actors.bb) and `ActorInstance` (also Actors.bb). Including Actors.bb pulls in the world graph, network code, and a server-only dependency cascade. Inlining stubs:

```basic
; --- External type stubs ---
Type Attributes
    Field Value[39]
    Field Maximum[39]
    Field My_ID
End Type

Type ActorInstance
    Field Account
End Type
```

...gives the compiler enough to resolve field accesses in Items.bb without dragging in the rest of the world. Items.bb works on `Attributes\Value[i]` — the test stub gives it that shape, and that's all.

### Function stubs

If Items.bb calls `WriteLog(MainLog, msg$)` from Logging.bb, you need a stub:

```basic
Global MainLog = 0

Function WriteLog(LogID%, Message$, Timestamp% = True, Datestamp% = False)
End Function
```

The body is empty because the test doesn't care about logging. The signature must match the real one (param count, sigils, default values).

### Module-specific helper stubs

Items.bb calls `RCE_StrFromInt$` and `RCE_IntFromStr` from RCEnet.bb. Re-implement them in the test with a private Bank:

```basic
Global ItemsTest_ConvertBank.BBBank = CreateBank(8)

Function RCE_IntFromStr(Dat$)
    PokeInt ItemsTest_ConvertBank, 0, 0
    Local i
    For i = 1 To Len(Dat$)
        PokeByte ItemsTest_ConvertBank, i - 1, Asc(Mid$(Dat$, i, 1))
    Next
    Return PeekInt(ItemsTest_ConvertBank, 0)
End Function

Function RCE_StrFromInt$(Num, Length = 4)
    PokeInt ItemsTest_ConvertBank, 0, Num
    Local Dat$ = ""
    Local i
    For i = Length - 1 To 0 Step -1
        Dat$ = Chr$(PeekByte(ItemsTest_ConvertBank, i)) + Dat$
    Next
    Return Dat$
End Function
```

Verbatim from the real RCEnet.bb except the Bank is private to the test (avoids the global-bank dependency).

## When the module's API changed

Recurring failure mode: a PR adds a new helper function to a real module (e.g., `SafeWriteOpen$` / `SafeWriteCommit%` in Logging.bb), and a test that includes the consumer module now fails because the new helper isn't stubbed.

Example from merged history: [PR #124](https://github.com/RydeTec/pulls/124) added atomic-write helpers to several Save functions. ItemsTest.bb (which doesn't include Logging.bb) suddenly broke on CI: `"Function 'safewriteopen' not found"`. Fix was to add stubs:

```basic
; --- SafeWrite stubs ---
Function SafeWriteOpen$(FinalPath$)
    Return FinalPath$
End Function

Function SafeWriteCommit%(TempPath$, FinalPath$, F)
    Return True
End Function
```

When you change a module's exported surface, **grep `src/Tests/` for any test that includes the changed module** and update its stubs to match.

## The `Test foo()` / `End Test` block

```basic
Test testItemInstanceRoundTrip()
    ClearItemList()                         ; setup
    Local sword.Item = SeedItem("Sword")    ; arrange

    Local original.ItemInstance = CreateItemInstance(sword)
    original\ItemHealth = 75

    Local s$ = ItemInstanceToString$(original)
    Assert(Len(s$) = ItemInstanceStringLength())

    Local restored.ItemInstance = ItemInstanceFromString(s$)
    Assert(restored <> Null)
    Assert(ItemInstancesIdentical(original, restored) = True)

    ClearItemList()                         ; teardown
End Test
```

- One Test per concern. Group setup/arrange/act/assert clearly.
- Use `Assert(expr)` — returns True/False, does not abort. Every Assert in the test runs even after a failure.
- Test outputs `Active strings : N` and friends at the end — the BlitzForge GC is checking for leaks. Tests with reachable leaks fail.
- Setup/teardown is manual: write a `ClearItemList()` helper (see [ItemsTest.bb line 83](../../../src/Tests/Modules/ItemsTest.bb#L83)) that resets the module state.
- Test names start with `test` by convention.

## Pinning contract values

Pin tests are valuable: they document and enforce specific values that downstream code depends on.

```basic
; ItemInstanceStringLength is a contract constant -- pin it so anyone who
; changes the serialization format has to update the test consciously.
Test testItemInstanceStringLengthIs83Bytes()
    Assert(ItemInstanceStringLength() = 83)
End Test
```

The test name encodes the expected value. Anyone changing the serialization format and breaking this test sees exactly what they broke.

## Testing rejection paths

For functions that should return Null / -1 / False on bad input:

```basic
; Truncated payload: ItemInstanceFromString must return Null rather than
; crash on under-length input.
Test testItemInstanceFromStringRejectsShortPayload()
    ClearItemList()
    Local defaultItem.Item = SeedItem("Default")

    Assert(ItemInstanceFromString("") = Null)
    Assert(ItemInstanceFromString("xx") = Null)
    Local underSized$ = String$("x", ItemInstanceStringLength() - 1)
    Assert(ItemInstanceFromString(underSized) = Null)

    ClearItemList()
End Test
```

These tests are the safety net for hostile-server / malformed-packet recovery. Add one for every soft-fail path you ship.

## The "test compiles standalone but breaks under test.bat" problem

`blitzcc -t -w src TheTest.bb` from the `src/Tests/Modules/` directory with `-w src` as the working dir. Includes resolve from `src/`, so `Include "Modules\PasswordHash.bb"` works.

If a test passes standalone (`blitzcc -t PasswordHashTest.bb` from the file's directory) but fails under `test.bat`, the include path is the most likely cause. Always use `Include "Modules\..."` (capital M, backslashes — the convention matches the runtime).

## The known ItemsTest flake

There's an intermittent flake where `ItemsTest.bb` fails with `Error: Stack overflow!` in CI. It's pre-existing infrastructure noise, not caused by Items.bb itself. The flake's been seen on PRs that touch nothing item-related.

**Workaround**: close + reopen the PR to re-trigger CI:

```bash
gh pr close <N> && sleep 3 && gh pr reopen <N>
```

Usually passes on the second run. If it fails twice in a row on an unrelated PR, investigate — but two retries is the established norm.

When you write a long-running test (heavy loops, large data structures), watch for the stack-overflow shape. The framework's per-test isolation is imperfect; bleeding state between tests can compound.

## Test runner details

[test.bat](../../../test.bat) (~46 lines):

```batch
@echo off
setlocal
set "ROOTDIR=%~dp0"
set "BLITZPATH=%ROOTDIR%\compiler\BlitzForge"
set "TESTDIR=%ROOTDIR%\src\Tests"
cd /d "%TESTDIR%"

REM walk every .bb and run blitzcc -t against it
for /r %%f in (*.bb) do (
    "%BLITZPATH%\bin\blitzcc.exe" -t -w "%ROOTDIR%\src" "%%f"
    if errorlevel 1 (
        echo "%%f failed at least one test"
        set FAILED=1
    )
)
```

Exit code 1 on any failure. The script keeps running through all tests so you see every failure, not just the first.

## Where tests live

```
src/Tests/
├── Modules/                       # most module tests
│   ├── AccountsServerTest.bb
│   ├── EnvironmentTest.bb
│   ├── ItemsTest.bb               # canonical example
│   ├── PasswordHashTest.bb
│   ├── SafeWriteTest.bb
│   ├── MediaImportTest.bb
│   ├── SpawnTrackingTest.bb
│   └── RecentProjectsTest.bb
├── Framework/                     # tests for src/Modules/Framework/
├── Helpers/                       # helper-utility tests
└── UI/Components/                 # UI component tests
```

When adding coverage for a new module, mirror the source path: a test for `src/Modules/Foo.bb` goes in `src/Tests/Modules/FooTest.bb`. Easy to navigate, easy to grep.

## Checklist before committing a new test

- [ ] File opens with `Strict` then `EnableGC`.
- [ ] All `Type` references resolve (stubs for non-included types).
- [ ] All function calls resolve (stubs for non-included functions; signatures match real ones).
- [ ] `Include "Modules\TheModule.bb"` uses backslash + capital M.
- [ ] One Test block per concern; test names start with `test`; setup/teardown call helpers (`ClearXList()` pattern).
- [ ] No reachable leaks (GC report at end shows 0 unreleased objects).
- [ ] `test.bat` runs cleanly with your new file.
- [ ] If you also changed a real module's exported surface, you've updated every consumer test's stubs.
