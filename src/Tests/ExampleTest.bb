
; This is a test of the Test block

Test test1()
    DebugLog "Test 1 ran"
    Assert(0)
    DebugLog Assert(True)
    assert2 = Assert("this" = "this")
    DebugLog assert2
    assert3 = Assert("this" = "that")
    DebugLog assert3
End Test