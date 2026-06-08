Strict

Global GLOBAL_RANDOM_SEED%

Type Random
    Field seed%
    Method create.Random(seed%=0)
        Random::setSeed(self, seed)
        return self
    End Method

    Method setSeed(seed%=0)
        If (self = Null)
            self = new Random()
        End If

        If (NOT seed)
            If (NOT self\seed)
                If (NOT GLOBAL_RANDOM_SEED)
                    GLOBAL_RANDOM_SEED = MilliSecs()
                    SeedRnd(GLOBAL_RANDOM_SEED)
                End If
            End If
        Else
            ; Re-seed only when the value differs. The previous form
            ; `If (NOT self\seed = seed)` parses as `(NOT self\seed) = seed`
            ; (operator precedence), so re-seeding silently never fired
            ; for any non-zero seed value.
            If self\seed <> seed
                self\seed = seed
                SeedRnd(self\seed)
            End If
        End If
    End Method

    Method f#(min% = 1, max%=2147483647)
        Random::setSeed(self)
        
        return Rnd(min, max)
    End Method

    Method i%(min% = 1, max%=2147483647)
        Random::setSeed(self)

        return Rand(min, max)
    End Method
End Type