Strict

Include "Modules\Graphics\UI\Components\Component.bb"
Include "Modules\F-UI.bb"

Type MenuItemComponent.Component
    Field string$

    Field fuiHandle%
    Field fuiParentHandle%

    Method create.MenuItemComponent(fuiParentHandle%, string$)
        self\string = string
        self\fuiParentHandle = fuiParentHandle
        self\fuiHandle = FUI_MenuItem(self\fuiParentHandle, self\string)

        return self
    End Method

    Method setText(string$)
        self\string = string
        FUI_SendMessage(self\fuiHandle, M_SETTEXT, self\string)
        FUI_BuildMenuItem(Object.MenuItem(self\fuiHandle))
    End Method
End Type