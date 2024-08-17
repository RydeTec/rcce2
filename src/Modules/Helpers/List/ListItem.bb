Strict

Include "Modules\Traits\IdentifierTrait.bb"

Type ListItem
    Field listId.IdentifierTrait
    Field itemPointer@

    Method create.ListItem(listId.IdentifierTrait, itemPointer@)
        self\listId = listId
        self\itemPointer = itemPointer

        return self
    End Method
End Type