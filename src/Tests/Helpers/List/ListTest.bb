Strict

Include "Modules\Helpers\List\List.bb"

Type TestType
    Field var$
End Type

Test testListCreation()
    local l.List = new List()
    Assert(NOT l = Null)
End Test

Test testPushPop()
    local testType1.TestType = new TestType()
    local l.List = new List()
    List::push(l, testType1)
    List::pop(l)
End Test

Test testPushPopAccuracy()
    local testType1.TestType = new TestType()
    testType1\var = "test"
    local l.List = new List()
    List::push(l, testType1)
    local testType2.TestType = List::pop(l)
    Assert(testType2\var = "test")
End Test

Test testListFillAndEmpty()
    local l.List = new List()

    For i = 0 to 9
        local testType1.TestType = new TestType()
        testType1\var = "test" + i
        List::push(l, testType1)
    Next

    Assert(l\size = 10)
    local listCount = (l\size - 1)
    For i = 0 to listCount
        testType1.TestType = List::pop(l)
        Assert(testType1\var = "test" + (listCount - i))
    Next

    Assert(l\size = 0)
End Test

Test testListGet()
    local l.List = new List()

    For i = 0 to 9
        local testType1.TestType = new TestType()
        testType1\var = "test" + i
        List::push(l, testType1)
    Next

    testType1.TestType = List::get(l, 5)
    Assert(testType1\var = "test5")

    testType1.TestType = List::get(l, 3)
    Assert(testType1\var = "test3")

    testType1.TestType = List::get(l, 5)
    Assert(testType1\var = "test5")
End Test

Test testListSet()
    local l.List = new List()

    For i = 0 to 9
        local testType1.TestType = new TestType()
        testType1\var = "test" + i
        List::push(l, testType1)
    Next

    Assert(l\size = 10)
    testType1.TestType = List::get(l, 5)
    Assert(testType1\var = "test5")

    local testType2.TestType = new TestType()
    testType2\var = "after-set"

    List::set(l, 5, testType2)

    Assert(l\size = 10)
    local testType3.TestType = List::get(l, 5)
    Assert(testType3\var = "after-set")

    local testType4.TestType = List::get(l, 4)
    Assert(testType4\var = "test4")

    local testType5.TestType = List::get(l, 6)
    Assert(testType5\var = "test6")
End Test

Test testListDelete()
    local l.List = new List()

    For i = 0 to 9
        local testType1.TestType = new TestType()
        testType1\var = "test" + i
        List::push(l, testType1)
    Next

    Assert(l\size = 10)

    List::removeAt(l, 3)

    Assert(l\size = 9)

    List::removeAt(l, 7)

    Assert(l\size = 8)

    testType1.TestType = List::get(l, 3)
    Assert(testType1\var = "test4")

    local testType2.TestType = List::get(l, 7)
    Assert(testType2\var = "test9")
End Test

Test testGetIndex()
    local l.List = new List()

    local testCheck.TestType = Null

    For i = 0 to 9
        local testType1.TestType = new TestType()
        testType1\var = "test" + i
        List::push(l, testType1)

        if (i=4)
            testCheck = testType1
        end if
    Next

    Assert(NOT testCheck = Null)
    local testIndex = List::getIndex(l, testCheck)

    Assert(testIndex = 4)

    testType1.TestType = List::get(l, testIndex)

    Assert(testCheck\var = testType1\var)
End Test

Test testClear()
    local l.List = new List()

    For i = 0 to 9
        local testType1.TestType = new TestType()
        testType1\var = "test" + i
        List::push(l, testType1)
    Next

    Assert(l\size = 10)
    Assert(NOT List::isEmpty(l))

    List::clear(l)

    Assert(l\size = 0)
    Assert(List::isEmpty(l))
End Test

Test testContains()
    local l.List = new List()

    local testCheck.TestType = Null

    For i = 0 to 9
        local testType1.TestType = new TestType()
        testType1\var = "test" + i
        List::push(l, testType1)

        if (i=4)
            testCheck = testType1
        end if
    Next

    Assert(List::contains(l, testCheck))
End Test

Test testListIntegrity()
    local l.List = new List()
    local testType1.TestType = new TestType()
    testType1\var = "this"
    List::push(l, testType1)
    testType1\var = "that"
    testType1 = Null

    local testType2.TestType = List::pop(l)
    Assert(testType2\var = "that")
    testType2 = Null

    testType1.TestType = new TestType()
    testType1\var = "foo"
    List::push(l, testType1)
    testType1 = Null

    testType2.TestType = List::get(l, 0)
    testType2\var = "bar"
    testType2 = Null

    local testType3.TestType = List::pop(l)
    Assert(testType3\var = "bar")
End Test