
; This is a test of the Test block

Test test1()
    ;Assert(0) ; Fail
    DebugLog "Assert: " + Assert(True)
    assert2 = Assert("this" = "this")
    DebugLog "Assert2: " + assert2
    ;assert3 = Assert("this" = "that") ; Fail
    ;DebugLog assert3
    DebugLog "Test 1 ran"
End Test