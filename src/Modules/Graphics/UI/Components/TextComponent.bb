Strict

Include "Modules\Graphics\UI\Components\Component.bb"
Include "Modules\Traits\Dim\ColorTrait.bb"

Type TextComponent.Component
    Field centerHorizontal
    Field centerVertical
    Field string$
    Field col.ColorTrait

    Method create.TextComponent(string$, centerHorizontal=False, centerVertical=False)
        self\string = string
        self\centerHorizontal = centerHorizontal
        self\centerVertical = centerVertical

        return self
    End Method

    Method setText(string$)
        self\string = string
    End Method

    Method place(x%, y%)
        if (self = Null) Then DebugLog("issue")
        Component::setPosition(self, x, y)
    End Method

    Method setColor(r%, g%, b%)
        self\col = new ColorTrait(r,g,b)
    End Method

    Method draw()
        Color(ColorTrait::red(self\col), ColorTrait::green(self\col), ColorTrait::blue(self\col))
        Text(self\pos\x, self\pos\y, self\string, self\centerHorizontal, self\centerVertical)
    End Method
End Type