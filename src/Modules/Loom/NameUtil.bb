Strict

// =============================================================================
// Loom/NameUtil.bb -- pure name-deduplication helpers
// =============================================================================
//
// Zero dependencies (only Blitz string built-ins) so the logic is unit-
// testable in isolation -- see src/Tests/Modules/Loom/NameUtilTest.bb.
//
// Why this exists: zone files are saved as "<Name$>.dat" (ServerSaveArea
// uses the zone name as the on-disk filename). Creating or duplicating a
// zone whose name collides with an existing zone would silently OVERWRITE
// that zone's .dat -- data loss. EntityFactory_UniqueZoneName uses these to
// pick a non-colliding name ("New Zone" -> "New Zone 2" -> ...). The
// dedup logic used to live inline in EntityFactory (coupled to a
// `For A.Area = Each Area` walk, so untestable); it is now a pure function
// over a name-set string, with the Area walk reduced to building that set.
//
// The "set" is encoded as a Chr(1)-delimited, uppercased string with a
// leading + trailing delimiter ("\1FOO\1BAR\1"). The bounding delimiters
// make membership an exact match -- "Foo" cannot false-match inside
// "Foobar" -- which a naive Instr(set, name) would get wrong. Chr(1) is the
// delimiter because it never appears in a legitimate entity name.


// -----------------------------------------------------------------------------
// Loom_AddNameToSet -- append `name` (uppercased) to a set string, keeping
// the leading/trailing-delimiter invariant. Pass "" as the initial set.
// -----------------------------------------------------------------------------
Function Loom_AddNameToSet$(setStr$, name$)
    If setStr = "" Then setStr = Chr(1)
    Return setStr + Upper$(name) + Chr(1)
End Function


// -----------------------------------------------------------------------------
// Loom_NameInSet -- True if `name` is present in `setStr` (case-insensitive,
// whole-name match). Empty set is never a match.
// -----------------------------------------------------------------------------
Function Loom_NameInSet%(name$, setStr$)
    If setStr = "" Then Return False
    Return Instr(setStr, Chr(1) + Upper$(name) + Chr(1)) > 0
End Function


// -----------------------------------------------------------------------------
// Loom_NextUniqueName -- return `base` if it is not in the set, else the
// first of "base 2", "base 3", ... that is free. Case-insensitive (the
// filesystem is). Bounded at 999 tries; the final fallback appends the
// loop counter so the return is always defined (unreachable in practice).
// -----------------------------------------------------------------------------
Function Loom_NextUniqueName$(base$, setStr$)
    If Loom_NameInSet%(base, setStr) = False Then Return base
    Local i% = 2
    While i < 1000
        Local candidate$ = base + " " + Str(i)
        If Loom_NameInSet%(candidate, setStr) = False Then Return candidate
        i = i + 1
    Wend
    Return base + " " + Str(i)
End Function
