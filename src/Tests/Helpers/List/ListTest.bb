Strict

Type TestType
    Field var$
End Type

Test testListCreation()
    local l.BBList = CreateList()
    Assert(NOT l = Null)

    FreeList(l)
End Test

Test testPushPop()
    local testType1.TestType = new TestType()
    local l.BBList = CreateList()
    ListAdd(l, testType1)
    ListFirst(l)
    ListRemove(l, 0)

    FreeList(l)
End Test

Test testPushPopAccuracy()
    local testType1.TestType = new TestType()
    testType1\var = "test"
    local l.BBList = CreateList()
    ListAdd(l, testType1)
    local testType2.TestType = ListFirst(l)
    ListRemove(l, 0)
    Assert(testType2\var = "test")

    FreeList(l)
End Test

Test testListFillAndEmpty()
    local l.BBList = CreateList()

    For i = 0 to 9
        local testType1.TestType = new TestType()
        testType1\var = "test" + i
        ListAdd(l, testType1)
    Next

    Assert(ListSize(l) = 10)
    local listCount = (ListSize(l) - 1)
    For i = 0 to listCount
        testType1.TestType = ListLast(l)
        ListRemove(l, listCount - i)
        Assert(testType1\var = "test" + (listCount - i))
    Next

    Assert(ListSize(l) = 0)

    FreeList(l)
End Test

Test testListGet()
    local l.BBList = CreateList()

    For i = 0 to 9
        local testType1.TestType = new TestType()
        testType1\var = "test" + i
        ListAdd(l, testType1)
    Next

    testType1.TestType = ListAt(l, 5)
    Assert(testType1\var = "test5")

    testType1.TestType = ListAt(l, 3)
    Assert(testType1\var = "test3")

    testType1.TestType = ListAt(l, 5)
    Assert(testType1\var = "test5")

    FreeList(l)
End Test

Test testListSet()
    local l.BBList = CreateList()

    For i = 0 to 9
        local testType1.TestType = new TestType()
        testType1\var = "test" + i
        ListAdd(l, testType1)
    Next

    Assert(ListSize(l) = 10)
    testType1.TestType = ListAt(l, 5)
    Assert(testType1\var = "test5")

    local testType2.TestType = new TestType()
    testType2\var = "after-set"

    ListReplace(l, 5, testType2)

    Assert(ListSize(l) = 10)
    local testType3.TestType = ListAt(l, 5)
    Assert(testType3\var = "after-set")

    local testType4.TestType = ListAt(l, 4)
    Assert(testType4\var = "test4")

    local testType5.TestType = ListAt(l, 6)
    Assert(testType5\var = "test6")

    FreeList(l)
End Test

Test testListDelete()
    local l.BBList = CreateList()

    For i = 0 to 9
        local testType1.TestType = new TestType()
        testType1\var = "test" + i
        ListAdd(l, testType1)
    Next

    Assert(ListSize(l) = 10)

    ListRemove(l, 3)

    Assert(ListSize(l) = 9)

    ListRemove(l, 7)

    Assert(ListSize(l) = 8)

    testType1.TestType = ListAt(l, 3)
    Assert(testType1\var = "test4")

    local testType2.TestType = ListAt(l, 7)
    Assert(testType2\var = "test9")

    FreeList(l)
End Test

Test testGetIndex()
    local l.BBList = CreateList()

    local testCheck.TestType = Null

    For i = 0 to 9
        local testType1.TestType = new TestType()
        testType1\var = "test" + i
        ListAdd(l, testType1)

        if (i=4)
            testCheck = testType1
        end if
    Next

    Assert(NOT testCheck = Null)
    local testIndex = ListFind(l, testCheck)

    Assert(testIndex = 4)

    testType1.TestType = ListAt(l, testIndex)

    Assert(testCheck\var = testType1\var)

    FreeList(l)
End Test

Test testClear()
    local l.BBList = CreateList()

    For i = 0 to 9
        local testType1.TestType = new TestType()
        testType1\var = "test" + i
        ListAdd(l, testType1)
    Next

    Assert(ListSize(l) = 10)
    Assert(NOT ListIsEmpty(l))

    ListClear(l)

    Assert(ListSize(l) = 0)
    Assert(ListIsEmpty(l))

    FreeList(l)
End Test

Test testContains()
    local l.BBList = CreateList()

    local testCheck.TestType = Null

    For i = 0 to 9
        local testType1.TestType = new TestType()
        testType1\var = "test" + i
        ListAdd(l, testType1)

        if (i=4)
            testCheck = testType1
        end if
    Next

    Assert(ListFind(l, testCheck) <> -1)

    FreeList(l)
End Test

Test testListIntegrity()
    local l.BBList = CreateList()
    local testType1.TestType = new TestType()
    testType1\var = "this"
    ListAdd(l, testType1)
    testType1\var = "that"
    testType1 = Null

    local testType2.TestType = ListFirst(l)
    ListRemove(l, 0)
    Assert(testType2\var = "that")
    testType2 = Null

    testType1.TestType = new TestType()
    testType1\var = "foo"
    ListAdd(l, testType1)
    testType1 = Null

    testType2.TestType = ListAt(l, 0)
    testType2\var = "bar"
    testType2 = Null

    local testType3.TestType = ListFirst(l)
    ListRemove(l, 0)
    Assert(testType3\var = "bar")

    FreeList(l)
End Test