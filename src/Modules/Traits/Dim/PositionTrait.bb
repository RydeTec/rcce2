Strict

Include "Modules\Traits\Dim\DimTrait.bb"

Type PositionTrait.DimTrait
    Method create.PositionTrait(x%, y%, z%=0)
        self\x = x
        self\y = y
        self\z = z
        return self
    End Method
End Type