Strict
EnableGC

; P_ActorDead frees Dialog records through FreeDialog, which deletes the
; current Dialog. The bounded production block must capture After before that
; call so multiple dialogs owned by one dead actor are all visited safely.

Function ActorDeathDialogCleanupUsesAfterCursor%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InCase%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_ActorDead") > 0 Then InCase = True
		If InCase = True And Instr(Line$, "Case P_AttackActor") > 0 Then Exit
		If InCase = True
			If Instr(Line$, "For Di.Dialog = Each Dialog") > 0
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "Local DeadDialog.Dialog = First Dialog") > 0 Then Stage = 1
			If Stage = 1 And Instr(Line$, "Local NextDeadDialog.Dialog = Null") > 0 Then Stage = 2
			If Stage = 2 And Instr(Line$, "While DeadDialog <> Null") > 0 Then Stage = 3
			If Stage = 3 And Instr(Line$, "NextDeadDialog = After DeadDialog") > 0 Then Stage = 4
			If Stage = 4 And Instr(Line$, "If DeadDialog\ActorInstance = A Then FreeDialog(Handle(DeadDialog))") > 0 Then Stage = 5
			If Stage = 5 And Instr(Line$, "DeadDialog = NextDeadDialog") > 0 Then Stage = 6
		EndIf
	Wend

	CloseFile F
	Return Stage = 6

End Function

Test testActorDeathDialogCleanupCapturesNextBeforeFree()
	Assert(ActorDeathDialogCleanupUsesAfterCursor%("Modules\ClientNet.bb") = True)
End Test
