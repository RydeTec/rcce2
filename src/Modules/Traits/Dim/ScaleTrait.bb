Strict

Include "Modules\Traits\Dim\DimTrait.bb"

Type ScaleTrait.DimTrait
    Method create.ScaleTrait(x%, y%, z%=0)
        self\x = x
        self\y = y
        self\z = z
        return self
    End Method
End Type