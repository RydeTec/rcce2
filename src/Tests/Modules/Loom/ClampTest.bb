Strict
EnableGC

// =============================================================================
// ClampTest -- unit tests for Loom/Clamp.bb's parse-and-clamp helpers.
// =============================================================================
//
// Clamp.bb has ZERO dependencies (only Blitz built-ins), so this Includes it
// directly with no stubs. The logic it covers is the last line of defense
// against a typed field value poisoning an entity: every numeric composer
// field routes through these with a per-field [lo, hi].

Include "Modules\Loom\Clamp.bb"


// ---- Int ----

// A valid in-range value passes through unchanged.
Test testIntInRange()
    Assert(Loom_ParseIntClamped%("42", 0, 0, 100) = 42)
End Test

// Empty / whitespace keeps the existing value (fallback), NOT 0 -- so
// clearing a field and tabbing away doesn't silently zero it.
Test testIntEmptyKeepsFallback()
    Assert(Loom_ParseIntClamped%("", 7, 0, 100) = 7)
    Assert(Loom_ParseIntClamped%("   ", 7, 0, 100) = 7)
End Test

// Below the low bound clamps up to lo; above the high bound clamps down.
Test testIntClampsToBounds()
    Assert(Loom_ParseIntClamped%("-5", 0, 0, 100) = 0)
    Assert(Loom_ParseIntClamped%("999999", 0, 0, 100) = 100)
End Test

// Garbage parses to 0 (Int()) then clamps -- can never exceed the field's
// own range. With lo=1 it clamps up to 1, not 0.
Test testIntGarbageClampsIntoRange()
    Assert(Loom_ParseIntClamped%("abc", 50, 1, 100) = 1)
End Test

// Negative ranges (used by attribute Value/Max) work both directions.
Test testIntNegativeRange()
    Assert(Loom_ParseIntClamped%("-1000", 0, -100, 100) = -100)
    Assert(Loom_ParseIntClamped%("-50", 0, -100, 100) = -50)
End Test


// ---- Float ----

// Valid in-range float passes through. Values chosen to be exactly
// representable so the equality assert is safe.
Test testFloatInRange()
    Assert(Loom_ParseFloatClamped#("3.5", 1.0, 0.0, 100.0) = 3.5)
End Test

Test testFloatEmptyKeepsFallback()
    Assert(Loom_ParseFloatClamped#("", 2.5, 0.0, 100.0) = 2.5)
End Test

// Scale field guard: lo=0.01 keeps an actor from being scaled to 0 (invisible
// / degenerate). "0" clamps up to the floor.
Test testFloatClampsToBounds()
    Assert(Loom_ParseFloatClamped#("-5", 1.0, 0.01, 100.0) = 0.01)
    Assert(Loom_ParseFloatClamped#("250", 1.0, 0.0, 100.0) = 100.0)
End Test
