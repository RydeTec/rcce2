Strict
EnableGC

; LoadAccounts opens a fixed server-data path and depends on account-window
; globals, so this focused source contract pins the record boundary without
; creating a fragile nested Data\Server Data fixture in every test runner.

Function AccountMetadataPreflightPrecedesPublish%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Stage = 0 And Instr(Line$, "Function LoadAccounts()") > 0 Then Stage = 1
		If Stage = 1 And (Instr(Line$, "Accounts\TotalAccounts =") > 0 Or Instr(Line$, "A.Account = New Account") > 0 Or Instr(Line$, "AddListBoxItem Accounts\List") > 0)
			CloseFile F
			Return False
		EndIf
		Select Stage
			Case 1
				If Instr(Line$, "RecordPos = FilePos(F)") > 0 Then Stage = 2
			Case 2
				If Instr(Line$, "If AccountRecordIsComplete(F, FileBytes) = False Then Exit") > 0 Then Stage = 3
			Case 3
				If Instr(Line$, "SeekFile F, RecordPos") > 0 Then Stage = 4
			Case 4
				If Instr(Line$, "Accounts\TotalAccounts = Accounts\TotalAccounts + 1") > 0 Then Stage = 5
			Case 5
				If Instr(Line$, "A.Account = New Account") > 0 Then Stage = 6
			Case 6
				If Instr(Line$, "AddListBoxItem Accounts\List") > 0
					CloseFile F
					Return True
				EndIf
		End Select
	Wend

	CloseFile F
	Return False

End Function

Function AccountMetadataPreflightCoversEveryField%(Path$)

	Local F.BBStream = ReadFile(Path$)
	Local BoundedStrings%, HasFlags%, HasChars%, InHelper%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Function AccountRecordIsComplete%(F, FileBytes)") > 0 Then InHelper = True
		If InHelper
			If Instr(Line$, "If AccountBoundedStringIsComplete(F, FileBytes,") > 0 Then BoundedStrings = BoundedStrings + 1
			If Instr(Line$, "If FilePos(F) + 2 > FileBytes Then Return False") > 0 Then HasFlags = True
			If Instr(Line$, "If FilePos(F) + 1 > FileBytes Then Return False") > 0 Then HasChars = True
			If Trim$(Line$) = "End Function"
				CloseFile F
				Return BoundedStrings = 4 And HasFlags And HasChars
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False

End Function

Test testLoadAccountsPreflightsMetadataBeforePublishingAccountState()
	Assert(AccountMetadataPreflightPrecedesPublish%("Modules\AccountsServer.bb") = True)
End Test

Test testLoadAccountsPreflightCoversAllMetadataFields()
	Assert(AccountMetadataPreflightCoversEveryField%("Modules\AccountsServer.bb") = True)
End Test
