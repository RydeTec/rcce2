
; This is a test of the Test block

Test test1()
    ;Assert(0) ; Fail
    ;Assert(False) ; Fail
    DebugLog "You can inline asserts " + Assert(True) + " and false asserts " + Assert(NOT False)
    assert2 = Assert("this" = "this") ; Assert returns True if pass and False if fail.
    assert3 = Assert(NOT "this" = "that")
End Test