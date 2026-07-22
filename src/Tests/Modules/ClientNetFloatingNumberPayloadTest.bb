Strict
EnableGC

; P_FloatingNumber is a fixed server-to-client frame: RuntimeID(2), Amount(4),
; and RGB(3). Bind the receive boundary directly because ClientNet pulls in the
; full renderer/network graph and this regression only needs its source order.

Function ClientNetSource$()
	Local Paths$[2]
	Paths[0] = "Modules\\ClientNet.bb"
	Paths[1] = "..\\Modules\\ClientNet.bb"
	Local i
	For i = 0 To 1
		Local F.BBStream = ReadFile(Paths[i])
		If F <> Null
			Local Source$ = ""
			While Not Eof(F)
				Source$ = Source$ + ReadLine$(F) + Chr$(10)
			Wend
			CloseFile F
			Return Source$
		EndIf
	Next
	Return ""
End Function

Function Between$(Source$, StartNeedle$, EndNeedle$)
	Local Start = Instr(Source$, StartNeedle$)
	If Start = 0 Then Return ""
	Local EndAt = Instr(Mid$(Source$, Start + Len(StartNeedle$)), EndNeedle$)
	If EndAt = 0 Then Return Mid$(Source$, Start)
	Return Mid$(Source$, Start, Len(StartNeedle$) + EndAt - 1)
End Function

Function FloatingNumberLengthGuard$()
	Return "If Len(M" + Chr$(92) + "MessageData$) = 9"
End Function

Function FloatingNumberRuntimeDecode$()
	Return "RuntimeID = RCE_IntFromStr(Mid$(M" + Chr$(92) + "MessageData$, 1, 2))"
End Function

Function FloatingNumberCreatesAfterGuard%(Section$)
	Local GuardAt = Instr(Section$, FloatingNumberLengthGuard$())
	If GuardAt = 0 Then Return False
	Local DecodeAt = Instr(Section$, FloatingNumberRuntimeDecode$())
	If DecodeAt = 0 Or DecodeAt < GuardAt Then Return False
	Local LookupAt = Instr(Section$, "RuntimeIDList(RuntimeID)")
	If LookupAt = 0 Or LookupAt < DecodeAt Then Return False
	Local CreateAt = Instr(Section$, "CreateFloatingNumber(AI, Amount, cR, cG, cB)")
	Return CreateAt > LookupAt
End Function

Test testFloatingNumberRejectsMalformedFramesBeforeDecodeAndCreation()
	Local Section$ = Between$(ClientNetSource$(), "Case P_FloatingNumber", "Case P_Projectile")
	Assert(FloatingNumberCreatesAfterGuard%(Section$) = True)
End Test
