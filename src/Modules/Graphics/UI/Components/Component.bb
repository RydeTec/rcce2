Strict

Include "Modules\Traits\Dim\PositionTrait.bb"
Include "Modules\Traits\Dim\ScaleTrait.bb"
Include "Modules\Traits\IdentifierTrait.bb"

Type Component
    Field id.IdentifierTrait
    Field componentType$
    Field pos.PositionTrait
    Field scale.ScaleTrait

    Method create.Component(componentType$, id$=0)
        self\id = new IdentifierTrait(id)
        
        self\componentType = componentType

        return self
    End Method

    Method setPosition(x%, y%, z%=0)
        self\pos = new PositionTrait(x%, y%, z%)
    End Method

    Method setScale(x%, y%, z%=0)
        self\scale = new ScaleTrait(x%, y%, z%)
    End Method
End Type