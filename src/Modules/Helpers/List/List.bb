Strict

Include "Modules\Helpers\List\ListItem.bb"
Include "Modules\Traits\IdentifierTrait.bb"

Type List
    Field id.IdentifierTrait
    Field size%

    Method create.List()
        self\id = new IdentifierTrait()

        return self
    End Method

    Method push(item@)
        local itemWrapper.ListItem = new ListItem(self\id, item)
        self\size = self\size + 1
    End Method

    Method pop@()
        local itemWrapper.ListItem = List::getListItem(self, self\size - 1)
        local item@ = itemWrapper\itemPointer
        Delete itemWrapper
        self\size = self\size - 1
        return item
    End Method

    Method get@(index%)
        local itemWrapper.ListItem = List::getListItem(self, index)
        local item@ = itemWrapper\itemPointer
        return item
    End Method

    Method set(index%, item@)
        local current.ListItem = List::getListItem(self, index)
        local itemWrapper.ListItem = new ListItem(self\id, item)
        Insert itemWrapper After current
        Delete current
    End Method

    Method remove(item@)
        List::removeAt(self, List::getIndex(self, item))
    End Method

    Method removeAt(index%)
        local current.ListItem = List::getListItem(self, index)
        Delete current
        self\size = self\size - 1
    End Method

    Method getIndex(item@)
        local listCount = self\size - 1
        for i = 0 to listCount
            local wrappedItem@ = List::get(self, i)
            if (wrappedItem = item)
                return i
            end if
        next
        return -1
    End Method

    Method clear()
        local listCount = self\size - 1
        for i = 0 to listCount
            List::removeAt(self, listCount - i)
        next
    End Method

    Method isEmpty()
        return self\size = 0
    End Method

    Method contains(item@)
        return List::getIndex(self, item) >= 0
    End Method

    Method getSize%()
        return self\size
    End Method

    ; Private
    Method getListItem.ListItem(index%)
        if self\id = Null Then RuntimeError("List has not been initialised")
        if self\size = 0 Then RuntimeError("List " + self\id\id + " has no members")
        if self\size <= index Then RuntimeError("Can't access " + index + " on List " + self\id\id + " with size " + self\size)
        local itemWrapper.ListItem = First ListItem
        local cursor = 0
        while(cursor <= index)
            if IdentifierTrait::equals(self\id, itemWrapper\listId)
                cursor = cursor + 1
                if (cursor > index)
                    return itemWrapper
                end if
            end if
            if (cursor <= index)
                itemWrapper = After itemWrapper
            end if
            if (itemWrapper = Last ListItem) return itemWrapper
        wend
        return itemWrapper
    End Method
End Type