Strict

Include "Modules\Traits\Dim\DimTrait.bb"

Type ColorTrait.DimTrait
    Method create.ColorTrait(r%, g%, b%=0)
        self\x = r
        self\y = g
        self\z = b
        return self
    End Method

    Method red%()
        return self\x
    End Method

    Method green%()
        return self\y
    End Method

    Method blue%()
        return self\z
    End Method
End Type