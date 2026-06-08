Strict
EnableGC

// =============================================================================
// SearchScoreTest -- unit tests for Loom/SearchScore.bb's palette ranking.
// =============================================================================
//
// SearchScore.bb has ZERO dependencies, so this Includes it with no stubs.
// The tests pin the tier ordering (exact > prefix > substring > none) and
// the within-tier tie-breakers (shorter prefix-name first, earlier substring
// position first) -- the contract the Ctrl+K palette relies on to surface
// the right results first. Query is passed already-lowercased (caller
// contract).

Include "Modules\Loom\SearchScore.bb"


// No match returns 0.
Test testNoMatchIsZero()
    Assert(Loom_ScoreName%("zzz", "Goblin") = 0)
    Assert(Loom_ScoreName%("sword", "") = 0)
End Test


// Exact match scores highest.
Test testExactBeatsPrefix()
    Local exact% = Loom_ScoreName%("sword", "Sword")
    Local prefix% = Loom_ScoreName%("sword", "Swordsman")
    Assert(exact > prefix)
End Test


// Prefix match beats substring match, for ANY name lengths within range.
Test testPrefixBeatsSubstring()
    // "sword" is a prefix of "Swordfish" and a mid-substring of "Greatsword".
    Local prefix% = Loom_ScoreName%("sword", "Swordfish")
    Local substr% = Loom_ScoreName%("sword", "Greatsword")
    Assert(prefix > substr)
    Assert(substr > 0)
End Test


// Within the prefix tier, a shorter name ranks higher (closer to an exact
// match -- "Axe" should beat "Axehandle" for query "axe").
Test testShorterPrefixRanksHigher()
    Local shortName% = Loom_ScoreName%("axe", "Axes")
    Local longName%  = Loom_ScoreName%("axe", "Axehandle of Doom")
    Assert(shortName > longName)
End Test


// Within the substring tier, an earlier match position ranks higher.
Test testEarlierSubstringRanksHigher()
    // "ron" at pos 2 in "Iron" vs pos 5 in "Best ron" -- earlier wins.
    Local early% = Loom_ScoreName%("ron", "Iron")
    Local late%  = Loom_ScoreName%("ron", "Best ron")
    Assert(early > late)
    Assert(late > 0)
End Test


// Matching is case-insensitive on the name side (caller lowercases query).
Test testCaseInsensitiveName()
    Assert(Loom_ScoreName%("goblin", "GOBLIN") = LOOM_SCORE_EXACT)
    Assert(Loom_ScoreName%("gob", "GOBLIN") >= LOOM_SCORE_PREFIX)
End Test
