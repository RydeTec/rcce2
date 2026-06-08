Strict

// =============================================================================
// Loom/SearchScore.bb -- pure name-ranking score for the command palette
// =============================================================================
//
// Zero dependencies (only Blitz string built-ins) so the ranking is unit-
// testable in isolation -- see src/Tests/Modules/Loom/SearchScoreTest.bb.
//
// This is the ranking behind the Ctrl+K find-anywhere palette: every entity
// name is scored against the query and the highest scores surface first. If
// the tiers ever invert (a substring match outranking a prefix match), the
// palette silently shows the wrong results first -- exactly the kind of UX
// rot that's invisible until a test catches it. The contract:
//
//   exact match      -> LOOM_SCORE_EXACT                       (highest)
//   prefix match     -> LOOM_SCORE_PREFIX + (200 - len(name))  (shorter wins)
//   substring match  -> LOOM_SCORE_SUBSTR + (100 - pos)        (earlier wins)
//   no match         -> 0
//
// With EXACT=5000, PREFIX base=1000 (so prefix scores ~1000..1199 for normal
// name lengths), SUBSTR base=100 (~1..199), the tiers never cross: any exact
// outranks any prefix outranks any substring outranks no-match. The within-
// tier bonuses break ties (shorter prefix-name first; earlier substring
// position first).
//
// Caller contract: `q` is already lower-cased and trimmed; this function
// lower-cases `name`. (The palette lower/trims the query once before scoring
// the whole roster, rather than per-candidate.)

Const LOOM_SCORE_PREFIX = 1000
Const LOOM_SCORE_SUBSTR = 100
Const LOOM_SCORE_EXACT  = 5000


Function Loom_ScoreName%(q$, name$)
    If name = "" Then Return 0
    Local lname$ = Lower$(name)

    If lname = q Then Return LOOM_SCORE_EXACT
    If Left$(lname, Len(q)) = q Then Return LOOM_SCORE_PREFIX + (200 - Len(name))

    // Substring -- Instr returns 1-based index, 0 = not found
    Local pos% = Instr(lname, q)
    If pos > 0 Then Return LOOM_SCORE_SUBSTR + (100 - pos)
    Return 0
End Function
