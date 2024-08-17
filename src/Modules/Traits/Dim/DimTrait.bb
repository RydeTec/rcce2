Strict

Type DimTrait
    Field x%, y%, z%

    Method create.DimTrait(x%, y%, z%=0)
        self\x = x
        self\y = y
        self\z = z
        return self
    End Method
End Type