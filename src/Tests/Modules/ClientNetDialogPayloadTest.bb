Strict
EnableGC

; P_Dialog is a server-to-client multiplexed frame. The production UI graph is
; too broad for a standalone runtime test, so this contract pins the guards
; before their decode, mutation, and acknowledgement boundaries.

Function LeadingTabs%(Line$)
	Local Index%
	For Index = 1 To Len(Line$)
		If Mid$(Line$, Index, 1) <> Chr$(9) Then Return Index - 1
	Next
	Return Len(Line$)
End Function

Function DialogCaseContainsContained%(Path$, CaseNeedle$, GuardNeedle$, SensitiveNeedle$)
	Local F.BBStream = ReadFile(Path$)
	Local InDialog%, InCase%, InElse%, GuardIndent% = -1, Indent%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_Dialog") > 0 Then InDialog = True
		If InDialog = True And Instr(Line$, "Case P_ActorDead") > 0 Then Exit
		If InDialog = True
			If InCase = False
				If Instr(Line$, CaseNeedle$) > 0 Then InCase = True
			Else If GuardIndent < 0
				If Instr(Line$, GuardNeedle$) > 0 Then GuardIndent = LeadingTabs(Line$)
			Else If InElse = False
				If LeadingTabs(Line$) = GuardIndent And Instr(Line$, "Else") > 0 Then InElse = True
			Else
				Indent = LeadingTabs(Line$)
				If Indent = GuardIndent And Instr(Line$, "EndIf") > 0 Then Exit
				If Instr(Line$, SensitiveNeedle$) > 0
					CloseFile F
					Return True
				EndIf
			EndIf
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Function DialogCaseContainsTrueBranch%(Path$, CaseNeedle$, GuardNeedle$, SensitiveNeedle$)
	Local F.BBStream = ReadFile(Path$)
	Local InDialog%, InCase%, GuardIndent% = -1, Indent%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False
	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_Dialog") > 0 Then InDialog = True
		If InDialog = True And Instr(Line$, "Case P_ActorDead") > 0 Then Exit
		If InDialog = True
			If InCase = False
				If Instr(Line$, CaseNeedle$) > 0 Then InCase = True
			Else If GuardIndent < 0
				If Instr(Line$, GuardNeedle$) > 0 Then GuardIndent = LeadingTabs(Line$)
			Else
				Indent = LeadingTabs(Line$)
				If Indent = GuardIndent And (Instr(Line$, "Else") > 0 Or Instr(Line$, "EndIf") > 0) Then Exit
				If Instr(Line$, SensitiveNeedle$) > 0
					CloseFile F
					Return True
				EndIf
			EndIf
		EndIf
	Wend
	CloseFile F
	Return False
End Function

Test testDialogGuardsContainSensitiveWork()
	Assert(DialogCaseContainsContained%("Modules\ClientNet.bb", "Case " + Chr$(34) + "N" + Chr$(34), "If Len(M\MessageData$) < 9", "RuntimeIDList") = True)
	Assert(DialogCaseContainsContained%("Modules\ClientNet.bb", "Case " + Chr$(34) + "N" + Chr$(34), "If Len(M\MessageData$) < 9", "D = CreateDialog") = True)
	Assert(DialogCaseContainsContained%("Modules\ClientNet.bb", "Case " + Chr$(34) + "N" + Chr$(34), "If Len(M\MessageData$) < 9", "RCE_Send(Connection, PeerToHost, P_Dialog, " + Chr$(34) + "N" + Chr$(34)) = True)
	Assert(DialogCaseContainsContained%("Modules\ClientNet.bb", "Case " + Chr$(34) + "N" + Chr$(34), "If Len(M\MessageData$) < 9", "Me\DestX#") = True)
	Assert(DialogCaseContainsContained%("Modules\ClientNet.bb", "Case " + Chr$(34) + "N" + Chr$(34), "If Len(M\MessageData$) < 9", "Me\DestZ#") = True)
	Assert(DialogCaseContainsContained%("Modules\ClientNet.bb", "Case " + Chr$(34) + "N" + Chr$(34), "If Len(M\MessageData$) < 9", "PointEntity Me\CollisionEN") = True)
	Assert(DialogCaseContainsContained%("Modules\ClientNet.bb", "Case " + Chr$(34) + "T" + Chr$(34), "If Len(M\MessageData$) < 8", "DialogOutput") = True)
	Assert(DialogCaseContainsContained%("Modules\ClientNet.bb", "Case " + Chr$(34) + "T" + Chr$(34), "If Len(M\MessageData$) < 8", "RCE_Send(Connection, PeerToHost, P_Dialog, " + Chr$(34) + "T" + Chr$(34)) = True)
	Assert(DialogCaseContainsContained%("Modules\ClientNet.bb", "Case " + Chr$(34) + "C" + Chr$(34), "If Len(M\MessageData$) <> 5", "FreeDialog") = True)
End Test
Test testDialogOptionFramePrevalidatesEveryDeclaredLength()
	Assert(DialogCaseContainsContained%("Modules\ClientNet.bb", "Case " + Chr$(34) + "O" + Chr$(34), "If Len(M\MessageData$) < 5", "If NameLen > Len(M\MessageData$) - Offset") = True)
	Assert(DialogCaseContainsContained%("Modules\ClientNet.bb", "Case " + Chr$(34) + "O" + Chr$(34), "If Len(M\MessageData$) < 5", "AddDialogOption") = True)
	Assert(DialogCaseContainsTrueBranch%("Modules\ClientNet.bb", "Case " + Chr$(34) + "O" + Chr$(34), "If DialogOptionsValid", "AddDialogOption") = True)
End Test

Test testDialogGuardContractRejectsEscapedWork()
	Local Fixture$ = "ClientNetDialogPayloadEscapeFixture.bb"
	Local F.BBStream = WriteFile(Fixture$)
	Assert(F <> Null)
	If F = Null Then Return
	WriteLine F, "Case P_Dialog"
	WriteLine F, Chr$(9) + "Case " + Chr$(34) + "N" + Chr$(34)
	WriteLine F, Chr$(9) + Chr$(9) + "If Len(M\MessageData$) < 9"
	WriteLine F, Chr$(9) + Chr$(9) + "Else"
	WriteLine F, Chr$(9) + Chr$(9) + "EndIf"
	WriteLine F, Chr$(9) + Chr$(9) + "D = CreateDialog"
	WriteLine F, Chr$(9) + Chr$(9) + "Me\DestZ# = EntityZ#(Me\CollisionEN)"
	WriteLine F, Chr$(9) + "Case " + Chr$(34) + "T" + Chr$(34)
	WriteLine F, Chr$(9) + Chr$(9) + "If Len(M\MessageData$) < 8"
	WriteLine F, Chr$(9) + Chr$(9) + "Else"
	WriteLine F, Chr$(9) + Chr$(9) + "EndIf"
	WriteLine F, Chr$(9) + Chr$(9) + "RCE_Send(Connection, PeerToHost, P_Dialog, " + Chr$(34) + "T" + Chr$(34) + ")"
	WriteLine F, Chr$(9) + "Case " + Chr$(34) + "O" + Chr$(34)
	WriteLine F, Chr$(9) + Chr$(9) + "If DialogOptionsValid"
	WriteLine F, Chr$(9) + Chr$(9) + "Else"
	WriteLine F, Chr$(9) + Chr$(9) + Chr$(9) + "AddDialogOption"
	WriteLine F, Chr$(9) + Chr$(9) + "EndIf"
	CloseFile F
	Assert(DialogCaseContainsContained%(Fixture$, "Case " + Chr$(34) + "N" + Chr$(34), "If Len(M\MessageData$) < 9", "D = CreateDialog") = False)
	Assert(DialogCaseContainsContained%(Fixture$, "Case " + Chr$(34) + "N" + Chr$(34), "If Len(M\MessageData$) < 9", "Me\DestZ#") = False)
	Assert(DialogCaseContainsContained%(Fixture$, "Case " + Chr$(34) + "T" + Chr$(34), "If Len(M\MessageData$) < 8", "RCE_Send(Connection, PeerToHost, P_Dialog, " + Chr$(34) + "T" + Chr$(34)) = False)
	Assert(DialogCaseContainsTrueBranch%(Fixture$, "Case " + Chr$(34) + "O" + Chr$(34), "If DialogOptionsValid", "AddDialogOption") = False)
	DeleteFile Fixture$
End Test
