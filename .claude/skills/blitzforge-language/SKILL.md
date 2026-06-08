---
name: blitzforge-language
description: Write or review code in the BlitzForge language (a modernized Blitz3D fork used by rcce2 and other projects). Invoke whenever editing, reviewing, or generating any `.bb` file — INCLUDING when the user only says "Blitz", "Blitz3D", "BlitzBasic", or shows .bb source — because your training data is base Blitz3D from ~2003 and does not have BlitzForge's modern additions (Strict mode, garbage collection, type inheritance, methods, BBList, function pointers, Async/Await, Try/Catch/Throw, the `Continue` keyword, `//` and `/* */` comments). Without this skill you WILL write outdated code that fails to compile under Strict or misses safer modern alternatives.
---

# BlitzForge language

BlitzForge is a community fork of the Blitz3D compiler that keeps full source-compatibility with original `.bb` programs but adds modern language features. Your training data covers original Blitz3D (Mark Sibly, 2003); it does **not** cover the additions below. When you write Blitz code without this skill, the most common failure modes are:

- Using `Type Foo` with `New Foo` instead of `New Foo()` (BlitzForge needs the parens — the implicit `create` method requires a call).
- Writing stateful modules as `Module_*`-prefixed free functions reading module-scope globals, instead of as `Type` with `Method`s called as `TypeName::method(obj, args)`. The project's modern convention is the Type-with-Methods form for anything that owns state. See [Module architecture](#module-architecture-types-with-methods-not-prefixed-free-functions).
- Skipping `Strict` / `EnableGC` at the top of files that should have them, then writing untyped `Local`s or relying on implicit globals.
- Writing manual `For obj.Type = Each Type ... Delete obj : Next` cleanup in GC-enabled files instead of letting GC handle it.
- Using `;` comments only — BlitzForge accepts `;` for back-compat but `//` and `/* ... */` are the preferred modern style.
- Reimplementing iteration/storage with `For Each` everywhere instead of reaching for `BBList` when a dynamic container is the right tool.
- Throwing `RuntimeError` for recoverable failures instead of `Throw` + `TryCatch`.
- Spawning manual threads instead of using `Async` / `Await` / `Poll` / `AsyncThen`.

This document is the always-on quick reference. For deep examples or edge cases, read [references/syntax-reference.md](references/syntax-reference.md). The canonical, executable examples live in [compiler/BlitzForge/tests/](../../../compiler/BlitzForge/tests/) — every feature below has a matching `*Test.bb` file.

## File preamble

Every new `.bb` file you author should start with:

```basic
Strict
EnableGC
```

**`Strict`** enforces explicit `Local` / `Global` / `Const` declarations and type sigils. **`EnableGC`** opts the file into reference-counted garbage collection so you don't have to `Delete` objects manually. Both must be the very first lines of the file (before `Include`s).

**Exception**: when adding to an existing module that is not `Strict`, leave it as-is. Mixing Strict and non-Strict via includes works, but converting a large legacy module to Strict is its own audit-worthy change.

## Comments

```basic
// Single-line preferred (modern)
; Single-line (legacy, accepted for back-compat — avoid in new code)
/* Block comment
   spanning lines */
```

## Custom types

### Definition + construction

```basic
Type Player
    Field name$
    Field health%, maxHealth%

    Method create.Player(name$, maxHealth%)    // implicit constructor
        self\name = name
        self\maxHealth = maxHealth
        self\health = maxHealth
        Return self
    End Method

    Method heal(amount%)
        self\health = self\health + amount
        If self\health > self\maxHealth Then self\health = self\maxHealth
    End Method
End Type

Local p.Player = New Player("Alice", 100)   // <-- parens are REQUIRED
Player::heal(p, 25)                          // <-- project convention
```

Key facts your training data won't have:

- **`Method name(args) ... End Method`** declares an instance method. `self` is implicit. Both call forms work — `obj\method(args)` (dynamic dispatch through the backslash) and `TypeName::method(obj, args)` (static-style, `obj` becomes the implicit `self`). **The rcce2 project convention is the static form.** See the [Module architecture](#module-architecture-types-with-methods-not-prefixed-free-functions) section below for why and when this matters.
- **`create.TypeName(...)`** is a special constructor method. When present, `New TypeName(args)` calls it and `New` returns whatever `create` returns (typically `self`). When absent, `New TypeName()` works and gives you a zero-initialized instance.
- **`new TypeName()` parens are required** even with no args. Bare `new TypeName` errors.

### Inheritance

```basic
Type Base
    Field baseVar$
End Type

Type Child.Base                  // Child extends Base
    Field childVar$
End Type

Type Grandchild.Child            // multi-level
    Field grandchildVar$
End Type

Local gc.Grandchild = New Grandchild()
gc\baseVar = "from base"         // inherited field works
gc\childVar = "from child"
```

**Casting between related types:**

```basic
// Casting down (child -> base) is implicit:
Local b.Base = New Child()       // OK; b is typed as Base but holds a Child
Print b\baseVar                  // OK
// Print b\childVar              // ERROR — b is declared as Base

// Casting up requires Recast:
Local c.Child = Recast.Child(b)  // explicit; runtime-checked
Print c\childVar                 // OK now
```

Method overrides work as you'd expect — a `Method` in `Child` overrides the same-name `Method` in `Base`. Dispatch is dynamic (based on the runtime type, not declared type).

**Recast is memory-unsafe across unrelated branches.** `Recast.SecondInherit(child_of_first_inherit)` silently aliases fields by memory offset. Stay within the actual inheritance chain.

## Module architecture: Types with Methods, not prefixed free functions

When a module owns state (a UI surface, an application object, a service that holds caches or handles), write it as a **`Type` with `Method`s**, not as a collection of `Module_*`-prefixed free functions operating on module-scope globals. The project's modern convention is also the **static `TypeName::method(self, args)` call form** even though `obj\method(args)` works.

This is the architectural call most likely to be missed if you're working from training data — Blitz3D in 2003 didn't have methods, so older code (and code generated by agents without this skill) often defaults to prefixed-function modules.

### Wrong (training-data default — don't do this for new modules)

```basic
// Browser.bb -- C-style module with prefixed free functions
Global Browser_Category$ = "actor"
Global Browser_FirstCard.Card = Null

Function Browser_Init()
    Browser_Category$ = "actor"
End Function

Function Browser_RenderAndUpdate(sw%, sh%)
    // ... reads Browser_Category$, mutates Browser_FirstCard ...
End Function
```

Symptoms this pattern produces:
- Module state spreads across file-scope globals that anything can reach into
- "Two of them" becomes impossible without renaming every global
- Test isolation requires resetting globals between runs
- Refactoring boundaries are by-convention rather than enforced

### Right (project convention — do this)

```basic
// Browser.bb
Strict

Type Browser
    Field category$
    Field firstCard.Card

    Method create.Browser()
        self\category = "actor"
        Return self
    End Method

    Method renderAndUpdate(sw%, sh%)
        // self\category, self\firstCard
    End Method
End Type
```

```basic
// Caller (Loom.bb or wherever)
Local br.Browser = New Browser()
Browser::renderAndUpdate(br, sw, sh)
```

### Why the static call style (`Browser::method(br, ...)`)

Both `Browser::renderAndUpdate(br, sw, sh)` and `br\renderAndUpdate(sw, sh)` compile and produce the same behavior. The project's canonical OO files use the **static form throughout**:

- [src/Project Manager.bb](../../../../src/Project%20Manager.bb)
- [src/Modules/Framework/RCCEApp.bb](../../../../src/Modules/Framework/RCCEApp.bb)
- [src/Modules/Framework/Project/Project.bb](../../../../src/Modules/Framework/Project/Project.bb)

Match the convention. Mixing styles in the same codebase makes review harder and gives future agents conflicting examples to pattern-match from.

### Constructor pattern: `Method create.TypeName(args)`

The canonical constructor returns `self`:

```basic
Type Project
    Field rootDir$
    Field name$

    Method create.Project(rootDir$)
        self\rootDir = rootDir
        Return self
    End Method
End Type

Local p.Project = New Project("C:\projects\Embergloom\")
```

The constructor is named `create.TypeName` and returns the type. `New TypeName(args)` is sugar that calls `create` with those args and returns its result.

### Inheritance bootstrap: parent's `create` first, then `Recast`

A child type's `create` calls its parent's `create` explicitly, captures the result, and `Recast`s back to the child type so subsequent `self\childField` accesses work:

```basic
Type ProjectManager.RCCEApp
    Field window%
    Field assetList.BBList

    Method create.ProjectManager()
        // Bootstrap parent fields, then recast so self points at the
        // ProjectManager view of the same instance (with child fields
        // exposed). Pattern from src/Project Manager.bb line 33.
        self = Recast.ProjectManager(RCCEApp::create(self, "RCCE", ".\"))
        Return self
    End Method

    Method init()
        RCCEApp::init(self, 560, 310)   // call parent's init explicitly
        // ... own init logic ...
    End Method
End Type
```

The `RCCEApp::create(self, ...)` form calls the parent's constructor *on the child instance* — same memory, just typed as the parent for the duration of the call. The `Recast.ProjectManager(...)` afterward returns the same memory typed back to the child so `self\window` etc. become accessible.

### When free functions are still OK

**Stateless helpers** — pure functions operating on data the caller passes in — can stay as prefixed free functions. [src/Modules/Project Manager/RecentProjects.bb](../../../../src/Modules/Project%20Manager/RecentProjects.bb) is the canonical example:

```basic
Function RecentProjectsPromote(recentProjects.BBList, prj.Project)
    // operates entirely on its arguments; owns nothing
End Function
```

It exports `RecentProjectsPromote`, `RecentProjectsFindByRootDir%`, `RecentProjectsRemoveByRootDir`, `RecentProjectsTrim` — all taking the `BBList` and the entity as arguments. No globals, no state across calls. This is the right shape for stateless utilities.

### Rule of thumb

- **The module has globals or stores state across calls** → make it a `Type` with `Method`s.
- **Every function in the module takes the data as a parameter and returns a result** → free functions are fine.
- **It's an entity definition** (`Type Player`, `Type Item`) → always a Type; methods optional but encouraged for behavior that belongs to the entity.

### Canonical examples to read before writing a new module

- [src/Modules/Framework/RCCEApp.bb](../../../../src/Modules/Framework/RCCEApp.bb) — base application type with `create.RCCEApp`, `init`, `version$` methods
- [src/Project Manager.bb](../../../../src/Project%20Manager.bb) — child type `ProjectManager.RCCEApp` showing parent-call bootstrap, asset loading, BBList-of-components, recent-projects state
- [src/Modules/Framework/Project/Project.bb](../../../../src/Modules/Framework/Project/Project.bb) — a simpler standalone type with `create.Project`, `verify`, `load` methods
- [src/Modules/Project Manager/RecentProjects.bb](../../../../src/Modules/Project%20Manager/RecentProjects.bb) — the *counter*example: stateless helper module, prefixed free functions are correct here

## Variables (Strict mode)

```basic
Local count% = 0              // integer
Local ratio# = 1.5            // float
Local name$ = "Bob"           // string
Local p.Player = Null         // typed object reference

Global serverPort% = 1234     // module-level
Const MaxSlots% = 32          // compile-time constant
```

Sigils: `%` int (default), `#` float, `$` string, `.TypeName` typed reference. The default for integer is unwritten (i.e., `Local count = 0` is equivalent to `Local count% = 0`), but in Strict mode declarations are still required.

`Local`-shadowing-a-`Global` of the same name compiles but produces confusing behavior; pick distinct names.

**Strict-mode gotcha: reassigning a Method-scope `Local` from inside nested `If`/`For` blocks doesn't compile.** Error: `<varname> assignment should start with local, global or const modifier`. Reassigning at the same nesting level as the `Local` declaration is fine; reassigning from a deeper nested block (or from a sibling `Else If` branch after the variable was used in an earlier branch) errors. Workaround: write to a **`Field` on the enclosing Type** instead — `self\latch = True` works at any depth. If you're not inside a `Type` and need cross-block state, hoist the variable to a `Global` or refactor the deep block into its own `Function` / `Method` with its own scope.

## Loops

```basic
For i = 0 To 9                 // 0..9 inclusive
    If i = 5 Then Continue     // <-- new keyword, skips to next iteration
    If i = 8 Then Exit         // breaks out
    Print i
Next

While condition
    ; ...
Wend

Repeat
    ; ...
Until condition
```

**`Continue`** is a BlitzForge addition. Note: an old codegen bug affected `Continue` inside certain `Select Case`-inside-`For` shapes (fixed in commit `78f3204`); current versions are stable, but verify after large refactors.

## Arrays

Sizes are **inclusive upper bounds**: `Dim arr(N)` and `Field arr[N]` both allocate `N+1` slots indexed `0..N`. `For i = 0 To N` is the correct iteration; `0 To N-1` skips the last slot. This is the #1 off-by-one source for agents from training data.

```basic
Dim NPCs.Player(99)            // 100 slots, 0..99
NPCs(0) = New Player("Goblin", 20)

Type Inventory
    Field Items.ItemInstance[45]   // 46 slots, 0..45
End Type
```

## BBList (modern dynamic container)

When you need a resizable, ordered collection (and especially when you'd otherwise write awkward `For Each` filtering), reach for `BBList`. Critical syntax:

```basic
Local items.BBList = CreateList()
ListAdd(items, New Item())
ListAdd(items, anotherItem)
ListInsert(items, 0, frontItem)    // insert at index
ListRemove(items, 1)               // remove at index
ListReplace(items, 2, newItem)     // overwrite at index

Local count% = ListSize(items)
Local empty% = ListIsEmpty(items)
Local idx% = ListFind(items, target)   // -1 if absent

Local first.Item = ListFirst(items)
Local last.Item = ListLast(items)
Local mid.Item = ListAt(items, 5)

ListClear(items)
FreeList(items)
```

When to use:
- **`BBList`** — heterogeneous collections, insertion/removal at arbitrary positions, finite scope outside a `Type` instance pool.
- **`For instance.Type = Each Type`** — iterating *all* live instances of a Type managed by the runtime. The legacy `Each` enumerator is still the right tool when that's what you actually want.

## Function pointers

```basic
Function handler@(arg.MyDTO = Null)   // @ suffix marks "returns a FunctionPtr"
    If arg = Null Then Return FunctionPtr()   // self-pointer when called with Null
    ; ... real work ...
    Return New MyDTO(result)
End Function

Local fp.BBFunction = handler(Null)            // get pointer
Local out.MyDTO = Call(fp, Ptr New MyDTO(5))   // invoke; Ptr cast required
```

Idiom: a function-returning-`FunctionPtr` accepts a `Null` sentinel arg to mean "give me your own pointer." This is how you bootstrap a pointer without calling the function for real.

`Call(fp, Ptr arg)` invokes through the pointer. Multi-arg functions need a DTO (data transfer object) Type to pack arguments — there is no varargs `Call`.

## Async / Await / Poll / AsyncThen

```basic
Local fp.BBFunction = slowWorker(Null)
Local thread.BBThread = Async(fp, New WorkerInput("payload"))

// Main thread keeps going while the async runs.
doOtherStuff()

// Non-blocking check:
If Poll(thread)
    Local result.WorkerOutput = Await(thread)   // returns immediately when done
EndIf

// Or just wait:
Local result.WorkerOutput = Await(thread)       // blocks until done

// Chain:
Local thread2.BBThread = AsyncThen(thread, nextStepFp)
Local final.Result = Await(thread2)
```

`Async(fp, arg)` launches `fp(arg)` on a worker, returns a `.BBThread` handle (a future). `Await` blocks; `Poll` peeks; `AsyncThen` chains another function pointer to run after the first finishes.

Use cases that previously required ugly polling loops: file I/O, network ops, expensive scripts spawned from gameplay code, anything that would otherwise stall the game tick.

## Try / Throw / Catch

```basic
Function tryFn@(arg.MyDTO = Null)
    If arg = Null Then Return FunctionPtr()
    If somethingWentWrong Then Throw New ErrorDTO("descriptive message")
    Return New MyDTO(successResult)
End Function

Function catchFn@(err.ErrorDTO = Null)
    If err = Null Then Return FunctionPtr()
    WriteLog(MainLog, "tryFn threw: " + err\msg)
    Return New MyDTO(fallbackResult)
End Function

Local result.MyDTO = TryCatch(tryFn(Null), catchFn(Null), New MyDTO(0))
```

`Throw obj` unwinds the stack and invokes the catch handler with the thrown object. `TryCatch(tryFp, catchFp, arg)` is the entry point. Returns whichever completed (`tryFn`'s return value if no throw, `catchFn`'s if there was one). Throws propagate up nested calls until caught.

Replaces the old `RuntimeError(...)` pattern for recoverable failures. Reserve `RuntimeError` for genuinely unrecoverable invariant violations.

## Pointers (`@` and `Ptr`)

```basic
Local p.MyType = New MyType()
Local raw% = Ptr p              // cast object reference to integer pointer
; ... pass raw through APIs that want an int ...
Local back.MyType = Object.MyType(raw)   // recover the typed reference
```

Used for callbacks across native API boundaries and inside `Call(fp, Ptr arg)`. Do not arithmetic on pointers; treat as opaque handles.

## Includes (namespace-style)

```basic
Strict
EnableGC

Include "Modules/Items.bb"
Include "Modules/Spells.bb"
```

`Include` paths resolve relative to the **root file being compiled** (typically `src/Server.bb` or `src/Client.bb`), not the file the `Include` lives in. This is a behavior change from base Blitz3D and behaves more like namespaces — predict imports from the top of the include cascade, not from the file you're editing.

## Tests

BlitzForge has a built-in test framework:

```basic
Strict
EnableGC

Include "Modules/MyModule.bb"

Test testThing()
    Local result = doThing(input)
    Assert(result = expected)              // asserts also return Bool if you want to chain
End Test
```

Run with `blitzcc -t file.bb`. In rcce2 the test runner is `test.bat` (Windows) / `test.sh` (Unix) which walks `src/Tests/` recursively. See the `rcce2-test-writing` skill for the project-specific test-file pattern (Strict + inline stubs to avoid network/world dep pull-in).

## Not present (so don't try)

- Ternary `?:`, null-coalesce `??`, null-conditional `?.`
- Lambdas / anonymous functions (use named `Function ...@(...)` + pass the pointer)
- Generics / templates (use `BBList` + DTOs for type-erased containers)
- Multiple return values (return a DTO)
- Real namespaces beyond `Include`. For stateful modules use `Type` + `Method`s with the `TypeName::method(obj, args)` call form (see [Module architecture](#module-architecture-types-with-methods-not-prefixed-free-functions)). For stateless utilities and legacy modules, prefixed free functions (`BVM_*`, `RCE_*`, `GY_*`) are the namespace substitute.
- Conditional compilation (`#If` / `#IfDef`) — not exposed in Blitz source
- Operator overloading
- Typed `Const` with sigil — `Const Foo = 1` works but `Const Foo% = 1` is not the preferred form (use `Global Foo% = 1` if you specifically need a typed compile-time value)

## Common training-data corrections

| Training-data Blitz3D | BlitzForge correct form |
|---|---|
| `New Foo` (no parens) | `New Foo()` |
| `Local x = 1` (in non-Strict) | `Local x% = 1` (in Strict) |
| `obj.method()` (C++/dot-call style) | `TypeName::method(obj, args)` (project convention) or `obj\method(args)` (also works) |
| Prefixed-function module with globals (`Module_Init`, `Module_Foo`, `Module_State$`) | `Type Module` with `Method create.Module / Method foo`, called as `Module::foo(self, args)`. See [Module architecture](#module-architecture-types-with-methods-not-prefixed-free-functions). |
| `For each.Type ... Delete each : Next` in GC file | omit `Delete`, GC handles it |
| `; comment` (preferred) | `// comment` (preferred) |
| Manual array resize via copy | `BBList` + `ListAdd` |
| `RuntimeError(...)` for any failure | `Throw newDTO` + `TryCatch` for recoverable; `RuntimeError` for invariants only |
| Polling loop with `Delay` for background work | `Async` + `Poll`/`Await` |
| `For i = 0 To N-1` over `Dim arr(N)` | `For i = 0 To N` (Dim is inclusive) |

## Reference materials

- [references/syntax-reference.md](references/syntax-reference.md) — deeper feature reference with extended examples, edge cases, gotchas
- [compiler/BlitzForge/tests/](../../../compiler/BlitzForge/tests/) — authoritative executable examples. Every feature here has a matching `*Test.bb`
- [compiler/BlitzForge/help/language/](../../../compiler/BlitzForge/help/language/) — HTML language reference (most pages cover legacy Blitz3D, but still useful for basics)
- [compiler/BlitzForge/.cursor/rules/blitzforge.mdc](../../../compiler/BlitzForge/.cursor/rules/blitzforge.mdc) — the BlitzForge maintainers' own short-form "what changed" notes
- [compiler/BlitzForge/ReadMe.md](../../../compiler/BlitzForge/ReadMe.md) — project overview, repo layout, build instructions
