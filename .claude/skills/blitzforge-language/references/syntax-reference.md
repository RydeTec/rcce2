# BlitzForge syntax reference (deep dive)

This file is loaded on demand when the SKILL.md surface isn't enough. Topics in order:

1. [Strict mode rules](#strict-mode-rules)
2. [Garbage collection in depth](#garbage-collection-in-depth)
3. [Type inheritance internals](#type-inheritance-internals)
4. [Methods: dispatch and constructor semantics](#methods-dispatch-and-constructor-semantics)
5. [BBList operations and patterns](#bblist-operations-and-patterns)
6. [Function pointers and async deep dive](#function-pointers-and-async-deep-dive)
7. [Try / Throw / Catch deep dive](#try--throw--catch-deep-dive)
8. [Standard-library highlights](#standard-library-highlights)
9. [Bank operations and binary serialization](#bank-operations-and-binary-serialization)
10. [Common idiom translations](#common-idiom-translations)

---

## Strict mode rules

`Strict` must appear as the first non-comment, non-blank line. It enables:

- **Mandatory declarations**: every variable must be introduced with `Local`, `Global`, or `Const`. Implicit globals are rejected at compile time. A typo that auto-created a new variable in legacy Blitz3D now errors loudly.
- **Type sigil consistency**: assigning a float to a `%`-sigil variable errors. Truncate explicitly with `Int(x#)`.
- **Function signature enforcement**: arg count and types must match the declared signature. The legacy "Blitz silently ignores extra args" behavior is gone.
- **Field access discipline**: `obj\unknownField` errors. The field must exist on the declared type (or its inheritance chain).

When inheriting non-Strict modules via `Include`, those modules continue to compile under their own rules. Your Strict file can call into them. The boundary is the file, not the program.

### What Strict does NOT do

- It does not change runtime behavior of legacy ops. `Mid$(s, large_offset)` still returns empty string rather than erroring.
- It does not enforce const correctness — assigning to a `Const` errors, but a `Global` is just a global, mutable from anywhere.
- It does not catch logical errors (writing 0 where you meant -1, off-by-one), only declaration / type / arity errors.

## Garbage collection in depth

`EnableGC` requires `Strict`. It opts the **whole file** (and the program as a whole if all files opt in) into the reference-counting runtime:

- Every `Type` instance carries a refcount in its header. `RefCount(obj)` returns it.
- Field assignments do retain/release: `a\field = b` releases the old `a\field`, retains `b`.
- When refcount drops to 0, the runtime calls the per-type release dispatch (which recursively releases owned fields, including `BBList` contents).
- `BBList`s are GC-aware: when the list itself is freed, contained elements are released.
- Strings are reference-counted independently.

### What this changes for you

- **Don't write `Delete obj`** for objects whose lifetime is owned by reachability. The GC will free them when no live reference exists.
- **`Delete obj` still works** but it's a manual free that bypasses GC tracking. Useful when you have a specific reason to release immediately (e.g., during shutdown), but generally unnecessary.
- **Cycles leak**. The GC is purely refcount-based; there's no cycle detector. If `A` references `B` and `B` references `A`, neither will free even when both become otherwise unreachable. Break cycles manually or use a non-owning index/handle pattern.

### Inspecting refcounts in tests

```basic
Type Foo
    Field x
End Type

Test testRetain()
    Local a.Foo = New Foo()
    Assert(RefCount(First Foo) = 1)
    Local b.Foo = a
    Assert(RefCount(First Foo) = 2)   // both vars reference the same object
End Test
```

The `RefCount(First Foo)` pattern uses the legacy `First Foo` enumerator to grab the first live instance. Useful for diagnostics; don't rely on the iteration order in production code.

## Type inheritance internals

```basic
Type Base
    Field a$, b$
End Type

Type Child.Base
    Field c$
End Type
```

Memory layout: `Child` is `Base`'s fields followed by `Child`'s additional fields. Casting down to `Base` doesn't change the object — it just narrows the visible field set. Casting back up with `Recast.Child(b)` is unchecked: the runtime trusts that the underlying memory really is a `Child`.

### Why `Recast` is "unsafe"

```basic
Type A
    Field x$
End Type
Type B.A
    Field bField$
End Type
Type C.A
    Field cField$
End Type

Local b.B = New B()
b\bField = "B's value"

Local a.A = b                       // narrow to A (safe, same memory)
Local c.C = Recast.C(a)             // tell runtime "treat as C" — UNCHECKED
Print c\cField                      // prints "B's value" — same memory slot, wrong field name
```

This is documented as `testIncorrectCrossCasting` in [InheritTest.bb](../../../compiler/BlitzForge/tests/InheritTest.bb). Field-by-position aliasing means `Recast` between unrelated children of the same base is structurally legal but semantically nonsense. Only `Recast` to a type that the object actually is.

### Method overriding

```basic
Type Base
    Method greet()
        DebugLog "Hello from Base"
    End Method
End Type

Type Child.Base
    Method greet()
        DebugLog "Hello from Child"
    End Method
End Type

Local c.Child = New Child()
c\greet()                           // "Hello from Child"

Local b.Base = c                    // narrow to Base
b\greet()                           // "Hello from Child" — dispatch is dynamic
```

Dispatch goes by the *runtime* type, not the declared type. So once you've created a `Child`, narrowing to `Base` doesn't change which method runs.

### Calling parent method

There's no `super.method()` syntax. To call the base implementation from an override, use static call form:

```basic
Type Child.Base
    Method greet()
        Base::greet(self)           // call parent's version
        DebugLog "...and from Child"
    End Method
End Type
```

## Methods: dispatch and constructor semantics

### Constructor convention

`create` is the special method name that `New TypeName(args)` calls. If `create` is absent, `New TypeName()` allocates a zero-initialized instance.

```basic
Type Player
    Field name$
    Field level%

    Method create.Player(name$)     // <-- note the `.Player` return type
        self\name = name
        self\level = 1
        Return self                 // <-- must return self
    End Method
End Type

Local p.Player = New Player("Bob")   // calls create("Bob"); p\name = "Bob", p\level = 1
```

The `.Player` return type on `create` is required (it returns the constructed object). You must `Return self` (the runtime uses the return value, not the allocation).

### Multiple constructors?

Not supported directly. Common patterns:

- Default args: `Method create.Foo(a$, b% = 0, c# = 0.0)`.
- Static factory functions: `Function NewFooFromBar.Foo(bar.Bar)`.

### Static method call form

```basic
Local p.Player = New Player("Alice")
p\heal(10)                          // dynamic; dispatch by runtime type
Player::heal(p, 10)                 // static; always calls Player::heal regardless of runtime type
```

Use static form (a) inside an override to call the parent's version, or (b) when you specifically need to bypass dynamic dispatch.

## BBList operations and patterns

Full API surface from [ListTest.bb](../../../compiler/BlitzForge/tests/ListTest.bb):

| Function | Returns | Purpose |
|---|---|---|
| `CreateList()` | `.BBList` | new empty list |
| `ListAdd(list, obj)` | — | append |
| `ListInsert(list, idx, obj)` | — | insert before index |
| `ListRemove(list, idx)` | — | remove at index |
| `ListReplace(list, idx, obj)` | — | overwrite at index |
| `ListSize(list)` | `%` | count |
| `ListIsEmpty(list)` | `%` | True if empty |
| `ListFind(list, obj)` | `%` | index, or -1 if absent |
| `ListFirst(list)` | obj | first element (or Null) |
| `ListLast(list)` | obj | last element (or Null) |
| `ListAt(list, idx)` | obj | element at index (or Null if out of range) |
| `ListClear(list)` | — | remove all elements (list still usable) |
| `FreeList(list)` | — | release the list itself |

### Iteration

There is no built-in `for each element in list` syntax. Use index iteration:

```basic
For i = 0 To ListSize(items) - 1            // note -1: ListSize is count, not max index
    Local item.Item = ListAt(items, i)
    ; ... use item ...
Next
```

For nested or mutation-during-iteration, take a snapshot first:

```basic
Local count = ListSize(items)
For i = count - 1 To 0 Step -1              // iterate backwards if removing
    Local item.Item = ListAt(items, i)
    If shouldRemove(item) Then ListRemove(items, i)
Next
```

### When NOT to use BBList

- **All-live-instances iteration**: the legacy `For obj.Type = Each Type` walks the runtime's instance list. This is what you want for "do something to every Player" semantics. `BBList` is a separate, explicit container — you choose what goes in.
- **Fixed-size collections**: `Dim arr.Type(N)` and `Field arr.Type[N]` remain the right tool for fixed-size slots (player character slots, action bar slots).
- **Sparse maps by integer key**: there's no `Dictionary` / `Map` type. Use a `Dim` array with `Null` for missing entries, or a `BBList` of key-value DTOs if order matters.

## Function pointers and async deep dive

### Anatomy of an `@`-tagged function

```basic
Function worker@(input.MyInput = Null)
    If input = Null Then Return FunctionPtr()    // self-pointer bootstrap
    // ... real work using `input` ...
    Return New MyOutput(result)                  // typed return
End Function
```

The `@` after the function name marks the *function* as returning a `FunctionPtr`. Internally:

- When called with `Null` (the conventional sentinel), it returns its own pointer via `FunctionPtr()`.
- Otherwise it does the actual work.
- Return value type is determined by the actual return statement; if you `Return New MyOutput(...)`, the caller can declare `Local r.MyOutput = Call(fp, Ptr arg)`.

Why the `Null`-sentinel idiom? Because there's no way to get a function pointer without calling the function. The Null branch is the bootstrap path.

### Calling with `Call(fp, Ptr arg)`

`Call` invokes through the pointer. The single argument is a generic pointer (`Ptr`), so you must:

```basic
Local fp.BBFunction = worker(Null)
Local input.MyInput = New MyInput("payload")
Local output.MyOutput = Call(fp, Ptr input)
```

The `Ptr input` cast converts the typed object reference to a raw pointer. The called function receives it back as its typed `input.MyInput` parameter (Blitz handles the cast on entry).

### Multiple arguments

`Call` takes one pointer arg. To pass multiple values, pack them in a DTO Type:

```basic
Type WorkerInput
    Field id%
    Field payload$
    Field flags%
End Type

Local input.WorkerInput = New WorkerInput()
input\id = 42
input\payload = "hello"
input\flags = 0
Local out = Call(workerFp, Ptr input)
```

### Async / Await / Poll / AsyncThen

```basic
Local fp.BBFunction = slowJob(Null)
Local future.BBThread = Async(fp, New JobInput("payload"))

// Three ways to handle the future:

// 1) Wait synchronously
Local result.JobOutput = Await(future)

// 2) Non-blocking poll, then await
While True
    If Poll(future)
        Local result.JobOutput = Await(future)
        Exit
    EndIf
    doOtherWork()
Wend

// 3) Chain — run nextFp on the result of the first
Local nextFp.BBFunction = postProcess(Null)
Local future2.BBThread = AsyncThen(future, nextFp)
Local final = Await(future2)
```

`Async` is preemptive (real thread/coroutine), not cooperative. Inside the async function:

- `Delay`, file I/O, network ops block the async without freezing the main thread.
- Shared globals are still shared — synchronize access carefully.
- The async function's return value is captured by the future and delivered to `Await`.

Use for:

- Loading assets in the background while UI stays responsive.
- Spawning long-running scripts from gameplay code (rcce2's `ThreadScript` is built on this).
- Network I/O that shouldn't stall the game tick.

## Try / Throw / Catch deep dive

```basic
Function divide@(input.DivInput = Null)
    If input = Null Then Return FunctionPtr()
    If input\divisor = 0 Then Throw New DivError("division by zero")
    Return New DivResult(input\dividend / input\divisor)
End Function

Function handleDivError@(err.DivError = Null)
    If err = Null Then Return FunctionPtr()
    WriteLog(MainLog, "divide failed: " + err\msg)
    Return New DivResult(0)            // fallback
End Function

Local result.DivResult = TryCatch( ..
    divide(Null), ..
    handleDivError(Null), ..
    New DivInput(10, 0))
```

### Semantics

- `TryCatch(tryFp, catchFp, arg)` calls `tryFp(arg)`.
- If `tryFp` returns normally, that return value is the `TryCatch` result.
- If `tryFp` (or anything it calls) executes `Throw obj`, the stack unwinds and `catchFp(obj)` is called instead. That return value is the `TryCatch` result.
- Both `tryFp` and `catchFp` must have compatible return types from the caller's perspective (callers `Local r.X = TryCatch(...)` and X has to work for both).

### Nested throws

`Throw` propagates up the call stack until a `TryCatch` catches it. If nothing catches, it's a runtime error (process exit). See [TryThrowCatchTest.bb](../../../compiler/BlitzForge/tests/TryThrowCatchTest.bb) `testNestedThrow`.

### When to throw vs. `RuntimeError`

- **Throw** — recoverable conditions where you want the caller to be able to handle (bad input, missing optional resource, network blip). Caller can catch and fall back.
- **RuntimeError** — invariant violations, programmer errors, unrecoverable corruption. Process should die so the operator sees the failure clearly.

In rcce2 specifically: server packet handlers and client renderers should **almost never** call `RuntimeError` on data sourced from network or save files. Soft-fail via `WriteLog` + graceful skip (see [src/CLAUDE.md](../../../CLAUDE.md) and recently merged "soft-fail" PRs).

## Standard-library highlights

These exist in legacy Blitz3D but are commonly forgotten:

- **`Asc(c$)` / `Chr$(n)`** — char ↔ ASCII int (`Asc("A")` = 65).
- **`Trim$(s)`** — strip leading/trailing whitespace.
- **`Replace$(s, find, replacement)`** — string substitution.
- **`Instr(s, find, start=1)`** — find substring (1-based, 0 if absent).
- **`Mid$(s, start, len)`** — substring, 1-based start.
- **`Left$(s, n)`, `Right$(s, n)`** — first/last n chars.
- **`Upper$(s)` / `Lower$(s)`** — case conversion.
- **`Str$(n)`** — number to string (no separator).
- **`Int(x)` / `Float(x)`** — numeric conversion.
- **`Abs(x)`, `Sgn(x)`, `Min(a,b)`, `Max(a,b)`, `Sqr(x)`** — math primitives.
- **`Rand(low, high)`** — pseudo-random int in `[low, high]` inclusive.
- **`Sin(deg)` / `Cos(deg)`** — angle is in **degrees, not radians**.
- **`MilliSecs()`** — monotonic ms counter (wraps at int32 max ~24 days, beware).

## Bank operations and binary serialization

`Bank` is a fixed-size byte buffer for binary I/O. Used heavily in rcce2 for wire encoding.

```basic
Local b.BBBank = CreateBank(8)        // 8 bytes
PokeInt b, 0, 42                       // write int at offset 0
PokeByte b, 4, $FF                     // write byte at offset 4
Local n = PeekInt(b, 0)                // read int from offset 0
FreeBank b
```

Functions: `PokeByte`, `PokeShort`, `PokeInt`, `PokeFloat`, `PeekByte`, `PeekShort`, `PeekInt`, `PeekFloat`. Lengths: 1, 2, 4, 4 bytes. **Be deliberate with length tags**: writing `PokeInt b, 0, 5` then `PeekShort b, 0` gives you 5's low 16 bits.

rcce2's `RCE_StrFromInt$(n, length=4)` and `RCE_IntFromStr(s$)` are built on `Bank`. The bank is a 8-byte module global, so `length > 8` will silently truncate.

## Common idiom translations

| Training-data idiom (won't compile / won't work right) | BlitzForge correct form |
|---|---|
| `enable_strict = True` | `Strict` (top of file) |
| `Type Foo .. End Type ; obj = New Foo` | `New Foo()` (parens required) |
| `obj.method()` | `obj\method()` |
| `Function Foo.Bar.SomeFn()` (nested namespace) | use prefix in name: `BVM_SomeFn` / `RCE_SomeFn` |
| `Const Foo As Int = 5` | `Const Foo = 5` (no `As`; sigil if needed: `Const Foo% = 5`) |
| `Public/Private` keywords | not present; use `_`-prefix naming convention for private |
| `Module Foo .. End Module` | `Include "Foo.bb"` |
| `For Each item In list` | `For i = 0 To ListSize(list) - 1` + `ListAt(list, i)` |
| `obj?.field` (null-conditional) | `If obj <> Null Then field = obj\field` |
| `x = a > 0 ? a : 0` (ternary) | `If a > 0 Then x = a Else x = 0` |
| `try { ... } catch (e) { ... }` (block) | `TryCatch(tryFp, catchFp, arg)` |
| `await someAsync()` (keyword) | `Await(future)` where `future = Async(fp, arg)` |
| `goto label` | `Goto label` works but rare; structured flow preferred |
| `print x` | `DebugLog x` (test contexts) or `Print x` (interactive) |
| `len(s)` (function-style) | `Len(s)` |
| `s.Length` | `Len(s)` |
| `arr.Length` | no length attr; track manually or use `BBList` + `ListSize` |
| `String.Format(...)` | string concatenation with `+`; use `Str$()` for numbers |

When in doubt, grep the rcce2 codebase for the operation you need — there's a working example for almost everything. The `compiler/BlitzForge/tests/*Test.bb` files are the second source of truth.
