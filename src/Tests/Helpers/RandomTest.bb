Strict
EnableGC

Include "Modules\Helpers\Random.bb"

Test testRandom()
    local r.Random = New Random(MilliSecs())

    local seed% = r\seed
    Assert(seed%)
    ; Two consecutive draws from the same RNG should almost never collide.
    ; The previous form `NOT Random::i(r) = Random::i(r)` parsed as
    ; `(NOT Random::i(r)) = Random::i(r)` and asserted a tautology --
    ; the test passed for the wrong reason and never exercised the RNG.
    Assert(Random::i(r) <> Random::i(r))
    Assert(seed = r\seed)

    Assert(Random::i())
    Assert(Random::f() <> 0)
    Assert(Random::f() <> Random::f())

    DebugLog("Random int: " + Random::i())
    DebugLog("Random float: " + Random::f())
End Test