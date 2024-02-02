; Establish a reference for root
Global ce_root$ = SystemProperty$("appdir")
; Running from the IDE
If Lower$(Right$(ce_root$, 4)) = "bin\"
	ce_root$ = CurrentDir$()
End If
If Right$(CE_Root$,1) <> "\" Then ce_root$ = ce_root$ + "\"

Const RCEFolder$ = "Data\Meshes\CE\CESaves\"

; Loads a Character Editor mesh and attaches all body parts
Function LoadCEMesh(ThisID, IdleStart, IdleEnd)

	; Get RCE data filename
	RCEFile$ = StripPath$(GetMeshName$(ThisID))
	If Len(RCEFile$) < 5 Then Return -1
	RCEFile$ = RCEFolder$ + Left$(RCEFile$, Len(RCEFile$) - 4) + "RCE"
	If FileType(RCEFile$) <> 1 Then RuntimeError "Character Editor error " + RCEFfile$

	; Load base torso model
	TorsoModel = GetMesh(ThisID)
	If TorsoModel = -1 Then RuntimeError "GetMesh() returned -1 on torso"

	; Set to idle pose
	Seq = ExtractAnimSeq(TorsoModel, IdleStart, IdleEnd)
	Animate TorsoModel, 1, 0.1, Seq

	; Load body parts and attach to torso with correct position/scale/rotation
	F = ReadFile(RCEFile$)

	While Not Eof(F)
		ID = ReadInt(F)
		X# = ReadFloat#(F)
		Y# = ReadFloat#(F)
		Z# = ReadFloat#(F)
		Pitch# = ReadFloat#(F)
		Yaw# = ReadFloat#(F)
		Roll# = ReadFloat#(F)
		ScaleX# = ReadFloat#(F)
		ScaleY# = ReadFloat#(F)
		ScaleZ# = ReadFloat#(F)
        Shine# = ReadFloat#(f)
        Alpha# = ReadFloat#(f)

        NewTex$ = ReadString$(f)
        NSphereMap$ = ReadString$(f)
		Bone$ = ReadString$(F)
		Name$ = ReadString$(F)

		EN = GetMesh(ID)
		If EN <> -1
			ScaleEntity EN, Scalex#, Scaley#, Scalez#
			RotateEntity EN, Pitch#, Yaw#, Roll#
			BoneEN = FindChild(TorsoModel, Bone$)
			If BoneEN
				PositionEntity EN, EntityX#(BoneEN, True) + X#, EntityY#(BoneEN, True) + Y#, EntityZ#(BoneEN, True) + Z#
				EntityParent EN, BoneEN
			Else
				PositionEntity EN, X#, Y#, Z#
				EntityParent EN, TorsoModel
			EndIf
			EntityAlpha EN, Alpha#
			EntityShininess EN, Shine#
            ; Check for new textures
            If NewTex$ <> "" Then ApplyCETexture(EN, ID, NewTex$, 0, 1+8)  
            If NSphereMap$ <> "" Then ApplyCETexture(EN, ID,NSphereMap$, 1, 64)  
		EndIf
	Wend

	; Return the completed model
	Return TorsoModel

End Function

; Returns only the filename from a path
Function StripPath$(S$)

	For i = Len(S$) To 1 Step -1
		If Mid$(S$, i, 1) = "\" Or Mid$(S$, i, 1) = "/" Then Return Mid$(S$, i + 1)
	Next

End Function

Function ApplyCETexture(CE_En, ModelID, TexN$, TexChannel = 0, Flags = 1+8)

	; Check for a texture to be in the same path as the model, and if so apply it
	OldPath$ = CurrentDir$()
	ChangeDir(CE_Root$)
	MN$ = GetMeshName$(ModelID)
	TName$ = CE_Root$ + "Data\Meshes\" + MN$

	; We have the ModelID, now extract the path from it
	For i2 = Len(TName$) To 1 Step -1
		If Mid$(TName$, i2, 1) = "\"
			TName$ = Mid$(TName$, 1, i2 - 1)
			Exit
		EndIf
	Next
	TName$ = TName$ + "\" + TexN$
	If FileType(TName$) = 1
		TempTex = LoadTexture(TName$, Flags)
		EntityTexture(Ce_En, TempTex, 0, TexChannel)
	EndIf
	ChangeDir OldPath$

End Function