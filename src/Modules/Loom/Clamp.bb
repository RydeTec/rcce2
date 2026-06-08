Strict

// =============================================================================
// Loom/Clamp.bb -- pure parse-and-clamp helpers for composer field edits
// =============================================================================
//
// Zero dependencies (only Blitz built-ins: Trim$ / Int / Float) so the logic
// is unit-testable in isolation -- see src/Tests/Modules/Loom/ClampTest.bb.
//
// Why this matters: every editable numeric field in the composer routes its
// typed string through one of these before writing it to the entity, with a
// per-field [lo, hi] range (~80 call sites across actor / item / spell / zone
// / animset / settings). The clamp is the only thing standing between a
// fat-fingered "999999999999" or "-5" or "abc" and a poisoned field
// (negative max-HP, scale 0 that freezes the actor, an out-of-range mesh ID).
// The contract:
//   - empty / whitespace-only string  -> keep the existing value (fallback)
//   - otherwise parse, then clamp into [lo, hi]
//   - garbage (non-numeric) parses to 0 via Int()/Float() and then clamps,
//     so it can never write something wilder than the field's own bounds.
//
// The logic used to live inline as Composer methods (untestable -- Composer
// pulls in the whole UI). It now lives here; the Composer methods are thin
// wrappers that delegate, so the ~80 call sites are unchanged.


// -----------------------------------------------------------------------------
// Loom_ParseIntClamped -- parse s as an int and clamp to [lo, hi]; empty
// string keeps `fallback`.
// -----------------------------------------------------------------------------
Function Loom_ParseIntClamped%(s$, fallback%, lo%, hi%)
    If Trim$(s) = "" Then Return fallback
    Local v% = Int(s)
    If v < lo Then v = lo
    If v > hi Then v = hi
    Return v
End Function


// -----------------------------------------------------------------------------
// Loom_ParseFloatClamped -- parse s as a float and clamp to [lo, hi]; empty
// string keeps `fallback`.
// -----------------------------------------------------------------------------
Function Loom_ParseFloatClamped#(s$, fallback#, lo#, hi#)
    If Trim$(s) = "" Then Return fallback
    Local v# = Float(s)
    If v# < lo# Then v# = lo#
    If v# > hi# Then v# = hi#
    Return v#
End Function
