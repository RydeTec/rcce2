Strict
EnableGC

; P_ChangeArea has a fixed 25-byte prefix that ends with the one-byte area-name
; length, followed by the corresponding name bytes. The dispatcher owns world teardown,
; so malformed packets must be rejected before they change local zone state.

Const ChangeAreaHeaderBytes% = 25

Function ChangeAreaFixedHeaderPresent%(PayloadLen%)
	If PayloadLen < ChangeAreaHeaderBytes Then Return False
	Return True
End Function

Function ChangeAreaDeclaredNamePresent%(PayloadLen%, NameLen%)
	If PayloadLen < ChangeAreaHeaderBytes + NameLen Then Return False
	Return True
End Function

Function ChangeAreaStateMutation%(Line$)
	If Instr(Line$, "OldAreaName$ = AreaName$") > 0 Then Return True
	If Instr(Line$, "OldAreaID = CurrentAreaID") > 0 Then Return True
	If Instr(Line$, "Me\") > 0 Then Return True
	If Instr(Line$, "Y# = RCE_FloatFromStr#") > 0 Then Return True
	If Instr(Line$, "Yaw# = RCE_FloatFromStr#") > 0 Then Return True
	If Instr(Line$, "PvPEnabled =") > 0 Then Return True
	If Instr(Line$, "Grav =") > 0 Then Return True
	If Instr(Line$, "CurrentAreaID =") > 0 Then Return True
	If Instr(Line$, "AreaName$ = Mid$(M\MessageData$") > 0 Then Return True
	If Instr(Line$, "FreeShadowCaster%") > 0 Then Return True
	If Instr(Line$, "RP_FreeEmitter(") > 0 Then Return True
	If Instr(Line$, "FreeProjectileInstance(") > 0 Then Return True
	If Instr(Line$, "SafeFreeActorInstance(") > 0 Then Return True
	If Instr(Line$, "FreeEntity DItemR\EN") > 0 Then Return True
	If Instr(Line$, "Delete DItemR") > 0 Then Return True
	If Instr(Line$, "UnloadArea()") > 0 Then Return True
	If Instr(Line$, "LoadArea(AreaName$") > 0 Then Return True
	If Instr(Line$, "SetWeather(") > 0 Then Return True
	If Instr(Line$, "PlayerTarget =") > 0 Then Return True
	If Instr(Line$, "AttackTarget =") > 0 Then Return True
	If Instr(Line$, "PositionEntity(Cam") > 0 Then Return True
	If Instr(Line$, "RotateEntity(Cam") > 0 Then Return True
	If Instr(Line$, "ResetEntity(Cam)") > 0 Then Return True
	If Instr(Line$, "ZonedMS =") > 0 Then Return True
	Return False
End Function

Function ChangeAreaGuardsPrecedeStateMutation%(Path$)
	Local F.BBStream = ReadFile(Path$)
	Local InCase%, Stage%
	Local Line$
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then Return False

	While Not Eof(F)
		Line$ = ReadLine$(F)
		If Instr(Line$, "Case P_ChangeArea") > 0 Then InCase = True
		If InCase = True And Instr(Line$, "Case P_KickedPlayer") > 0 Then Exit
		If InCase = True
			If Stage < 11 And ChangeAreaStateMutation%(Line$)
				CloseFile F
				Return False
			EndIf
			If Stage = 0 And Instr(Line$, "If Len(M\MessageData$) < 25") > 0
				Stage = 1
			EndIf
			If Stage = 1 And Instr(Line$, "P_ChangeArea: truncated header, dropping") > 0 Then Stage = 2
			If Stage = 2 And Trim$(Line$) = "Delete M" Then Stage = 3
			If Stage = 3 And Trim$(Line$) = "M = MNext" Then Stage = 4
			If Stage = 4 And Trim$(Line$) = "Continue" Then Stage = 5
			If Stage = 5 And Instr(Line$, "NameLen = RCE_IntFromStr(Mid$(M\MessageData$, 25, 1))") > 0 Then Stage = 6
			If Stage = 6 And Instr(Line$, "If Len(M\MessageData$) < 25 + NameLen") > 0 Then Stage = 7
			If Stage = 7 And Instr(Line$, "P_ChangeArea: truncated area name, dropping") > 0 Then Stage = 8
			If Stage = 8 And Trim$(Line$) = "Delete M" Then Stage = 9
			If Stage = 9 And Trim$(Line$) = "M = MNext" Then Stage = 10
			If Stage = 10 And Trim$(Line$) = "Continue" Then Stage = 11
			If Stage = 11 And Instr(Line$, "OldAreaName$ = AreaName$") > 0
				CloseFile F
				Return True
			EndIf
		EndIf
	Wend

	CloseFile F
	Return False
End Function

Test testChangeAreaRejectsTruncatedFixedHeader()
	Assert(ChangeAreaFixedHeaderPresent%(24) = False)
	Assert(ChangeAreaFixedHeaderPresent%(25) = True)
End Test

Test testChangeAreaRejectsTruncatedDeclaredName()
	Assert(ChangeAreaDeclaredNamePresent%(25, 0) = True)
	Assert(ChangeAreaDeclaredNamePresent%(26, 1) = True)
	Assert(ChangeAreaDeclaredNamePresent%(25, 1) = False)
End Test

Test testChangeAreaGuardsPrecedeZoneStateMutation()
	Assert(ChangeAreaGuardsPrecedeStateMutation%("Modules\ClientNet.bb") = True)
End Test
