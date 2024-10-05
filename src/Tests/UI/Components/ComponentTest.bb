Strict

Include "Modules\Graphics\UI\Components\Component.bb"
Include "Modules\Traits\IdentifierTrait.bb"

Test testId()
    local comp.Component = new Component("test")

    Assert(NOT comp\id = Null)
    Assert(comp\componentType = "test")

    local comp2.Component = new Component("test")

    Assert(NOT IdentifierTrait::equals(comp\id, comp2\id))
    Assert(comp\componentType = comp2\componentType)

    local comp3.Component = new Component("test2", "anid")

    Assert(comp3\id\id = "anid")
    Assert(comp3\componentType = "test2")

    Component::create(comp3, "test", "nextid")

    Assert(comp3\id\id = "nextid")
    Assert(comp3\componentType = "test")
End Test

Test testPos()
    local comp.Component = new Component("test")
    Component::setPosition(comp, 1,2)
    Assert(comp\pos\x = 1)
    Assert(comp\pos\y = 2)
    Assert(comp\pos\z = 0)

    Component::setPosition(comp, 3,4,5)
    Assert(comp\pos\x = 3)
    Assert(comp\pos\y = 4)
    Assert(comp\pos\z = 5)
End Test