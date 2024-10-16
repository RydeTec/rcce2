Strict
EnableGC

Include "Modules\Helpers\Random.bb"

Test testRandom()
    local r.Random = New Random(MilliSecs())

    local seed% = r\seed
    Assert(seed%)
    Assert(NOT Random::i(r) = Random::i(r))
    Assert(seed = r\seed)

    Assert(Random::i())
    Assert(NOT Random::f() = 0)
    Assert(NOT Random::f() = Random::f())

    DebugLog("Random int: " + Random::i())
    DebugLog("Random float: " + Random::f())
End Test