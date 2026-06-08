; Shared resize math for F-UI surfaces. Keep this pure so the resize
; projection contract can be covered by the lightweight test harness.

Function FUI_WindowAspect#(width%, height%)
	If width <= 0
		Return 0.0
	EndIf

	Return Float(height) / Float(width)
End Function

Function FUI_WindowScale#(width%)
	If width <= 0
		Return 0.0
	EndIf

	Return 2.0 / Float(width)
End Function
