Strict

Include "Modules\Helpers\Random.bb"

Type IdentifierTrait
    Field id$

    Method create.IdentifierTrait(id$=0)
        If (NOT id)
            self\id = Random::i() + "-" + Random::i() + "-" + Random::i()
        Else
            self\id = id
        End If

        return self
    End Method

    Method equals(id.IdentifierTrait)
        return self\id = id\id
    End Method
End Type