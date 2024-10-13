; Gubbin joint names
Dim GubbinJoints$(5)
; Default values
GubbinJoints$(0) = "Head"
GubbinJoints$(1) = "Chest"
GubbinJoints$(2) = "L_Shoulder"
GubbinJoints$(3) = "R_Shoulder"
GubbinJoints$(4) = "L_Forearm"
GubbinJoints$(5) = "R_Forearm"

; Other options
Global HideNametags = 0, DisableCollisions = False

Const nearFadeModifier# = 0.25 ; 500 units
Const farFadeModifier# = 0.75 ; 1500 units

; Loads gubbin joint names from file
Function LoadGubbinNames()

	F = ReadFile("Data\Game Data\Gubbins.dat")
	If F = 0 Then RuntimeError("File not found: " + "Data\Game Data\Gubbins.dat!")
		For i = 0 To 5
			GubbinJoints$(i) = ReadString$(F)
		Next
	CloseFile(F)

End Function

; Sets the weapon mesh
Function SetActorWeapon(AI.ActorInstance, MeshID)

	; Free existing mesh if there is one
	If AI\WeaponEN <> 0
		FreeEntityEmitters(AI\WeaponEN)
		FreeEntity(AI\WeaponEN)
		AI\WeaponEN = 0
	EndIf

	If MeshID > -1 And MeshID < 65535
		AI\WeaponEN = GetMesh(MeshID)
		If AI\WeaponEN = 0 Then RuntimeError("Could not load weapon mesh!")
		EntityAutoFade(AI\WeaponEN, nearFadeModifier * CameraViewRange, farFadeModifier * CameraViewRange)
		RHand = FindChild(AI\EN, "R_Hand")
		If RHand = 0 Then RuntimeError(AI\Actor\Race$ + " actor mesh is missing an 'R_Hand' joint!")
		EntityParent AI\WeaponEN, RHand, False
		PositionEntity AI\WeaponEN, LoadedMeshX#(MeshID), LoadedMeshY#(MeshID), LoadedMeshZ#(MeshID)
		ScaleEntity AI\WeaponEN, LoadedMeshScales#(MeshID), LoadedMeshScales#(MeshID), LoadedMeshScales#(MeshID)
		; Correct rotation for Max models
		If AI\TeamID = True Then TurnEntity AI\WeaponEN, 0, 180, 90
		CreateEntityEmitters(AI\WeaponEN)
	EndIf

End Function

; Sets the shield mesh
Function SetActorShield(AI.ActorInstance, MeshID)

	; Free existing mesh if there is one
	If AI\ShieldEN <> 0
		FreeEntityEmitters(AI\ShieldEN)
		FreeEntity(AI\ShieldEN)
		AI\ShieldEN = 0
	EndIf

	If MeshID > -1 And MeshID < 65535
		AI\ShieldEN = GetMesh(MeshID)
		If AI\ShieldEN = 0 Then RuntimeError("Could not load shield mesh!")
		EntityAutoFade(AI\ShieldEN, nearFadeModifier * CameraViewRange, farFadeModifier * CameraViewRange)
		LHand = FindChild(AI\EN, "L_Hand")
		If LHand = 0 Then RuntimeError(AI\Actor\Race$ + " actor mesh is missing an 'L_Hand' joint!")
		EntityParent AI\ShieldEN, LHand, False
		PositionEntity AI\ShieldEN, LoadedMeshX#(MeshID), LoadedMeshY#(MeshID), LoadedMeshZ#(MeshID)
		ScaleEntity AI\ShieldEN, LoadedMeshScales#(MeshID), LoadedMeshScales#(MeshID), LoadedMeshScales#(MeshID)
		; Correct rotation for Max models
		If AI\TeamID = True Then TurnEntity AI\ShieldEN, 0, 180, 90
		CreateEntityEmitters(AI\ShieldEN)
	EndIf

End Function

; Sets the chest mesh
Function SetActorChestArmour(AI.ActorInstance, MeshID)

	; Free existing mesh if there is one
	If AI\ChestEN <> 0
		FreeEntityEmitters(AI\ChestEN)
		FreeEntity(AI\ChestEN)
		AI\ChestEN = 0
	EndIf

	If MeshID > -1 And MeshID < 65535
		AI\ChestEN = GetMesh(MeshID)
		If AI\ChestEN = 0 Then RuntimeError("Could not load chest item mesh!")
		EntityAutoFade(AI\ChestEN, nearFadeModifier * CameraViewRange, farFadeModifier * CameraViewRange)
		Chest = FindChild(AI\EN, "Chest")
		If Chest = 0 Then RuntimeError(AI\Actor\Race$ + " actor mesh is missing a 'Chest' joint!")
		EntityParent AI\ChestEN, Chest, False
		PositionEntity AI\ChestEN, LoadedMeshX#(MeshID), LoadedMeshY#(MeshID), LoadedMeshZ#(MeshID)
		ScaleEntity AI\ChestEN, LoadedMeshScales#(MeshID), LoadedMeshScales#(MeshID), LoadedMeshScales#(MeshID)
		; Correct rotation for Max models
		If AI\TeamID = True Then TurnEntity AI\ChestEN, 0, 180, 90
		CreateEntityEmitters(AI\ChestEN)
	EndIf

End Function

; Sets the hair/helmet mesh
Function SetActorHat(AI.ActorInstance, MeshID)

	; Free existing mesh if there is one
	If AI\HatEN <> 0
		FreeEntityEmitters(AI\HatEN)
		FreeEntity(AI\HatEN)
		AI\HatEN = 0
	EndIf

	If MeshID > -1 And MeshID < 65535
		AI\HatEN = GetMesh(MeshID)
		If AI\HatEN = 0 Then RuntimeError("Could not load hat item mesh!")
		EntityAutoFade(AI\HatEN, nearFadeModifier* CameraViewRange, farFadeModifier * CameraViewRange)
		Bonce = FindChild(AI\EN, "Head")
		If Bonce = 0 Then RuntimeError(AI\Actor\Race$ + " actor mesh is missing a 'Head' joint!")
		EntityParent AI\HatEN, Bonce, False
		PositionEntity AI\HatEN, LoadedMeshX#(MeshID), LoadedMeshY#(MeshID), LoadedMeshZ#(MeshID)
		ScaleEntity AI\HatEN, LoadedMeshScales#(MeshID), LoadedMeshScales#(MeshID), LoadedMeshScales#(MeshID)
		; Correct rotation for Max models
		If AI\TeamID = True Then TurnEntity AI\HatEN, 0, 180, 90
		CreateEntityEmitters(AI\HatEN)
	Else
		If AI\Gender = 0
			ID = AI\Actor\MaleHairIDs[AI\Hair]
		Else
			ID = AI\Actor\FemaleHairIDs[AI\Hair]
		EndIf
		If ID > -1 And ID < 65535
			AI\HatEN = GetMesh(ID)
			If AI\HatEN = 0 Then RuntimeError("Could not load hair mesh!")
			EntityAutoFade(AI\HatEN, nearFadeModifier * CameraViewRange, farFadeModifier * CameraViewRange)
			Bonce = FindChild(AI\EN, "Head")
			If Bonce = 0 Then RuntimeError(AI\Actor\Race$ + " actor mesh is missing a 'Head' joint!")
			EntityParent AI\HatEN, Bonce, False
			PositionEntity AI\HatEN, LoadedMeshX#(ID), LoadedMeshY#(ID), LoadedMeshZ#(ID)
			ScaleEntity AI\HatEN, LoadedMeshScales#(ID), LoadedMeshScales#(ID), LoadedMeshScales#(ID)
			; Correct rotation for Max models
			If AI\TeamID = True Then TurnEntity AI\HatEN, 0, 180, 90
			CreateEntityEmitters(AI\HatEN)
		EndIf
	EndIf

End Function

; Loads the 3D stuff for an actor instance
Function LoadActorInstance3D(A.ActorInstance, Scale# = 1.0, SkipAttachments = False, autoFade = True)

	A\Actor\Radius# = 0.0
	A\CollisionEN = CreatePivot()

	; Main mesh and textures
	If A\Gender = 0
		ActorAnimSet.AnimSet = AnimList(A\Actor\MAnimationSet)
		Name$ = GetMeshName$(A\Actor\MeshIDs[0])
		; CE model
		If Upper$(Left$(Name$, 10)) = "CE\CESAVES"
			A\EN = LoadCEMesh(A\Actor\MeshIDs[0], ActorAnimSet\AnimStart[Anim_Idle], ActorAnimSet\AnimStart[Anim_Idle])
		; Normal model
		Else
			A\EN = GetMesh(A\Actor\MeshIDs[0], ActorHasMultipleTextures(A\Actor, 0))
			If A\EN = 0
				FreeEntity(A\CollisionEN)
				Return False
			EndIf
			; Meshes exported from Max have a pointless root pivot - get rid of it
			If CountSurfaces(A\EN) < 1
				; This is a server field, being used as a flag to say that the mesh is a 3DS Max model.
				; I have avoided adding a new field for this to conserve server memory, as both client
				; and server use the same Actor type definition.
				; It is used when attaching gubbins, as Max meshes have different joint rotations to
				; all other meshes.
				A\TeamID = True
				For i = 1 To CountChildren(A\EN)
					EN = GetChild(A\EN, i)
					If CountSurfaces(EN) > 0 Then A\EN = EN : Exit
				Next
			EndIf
		EndIf
		Scale# = Scale# * LoadedMeshScales#(A\Actor\MeshIDs[0]) * A\Actor\Scale#
		EntityParent(A\EN, A\CollisionEN)
		MMV.MeshMinMaxVertices = MeshMinMaxVertices(A\EN)
		PositionEntity A\EN, 0.0, (MMV\MaxY# - MMV\MinY#) / -2.0, 0.0
		FaceTex = A\FaceTex
		If A\Actor\MaleFaceIDs[FaceTex] = 65535 And FaceTex > 0 Then FaceTex = 0
		BodyTex = A\BodyTex
		If A\Actor\MaleBodyIDs[BodyTex] = 65535 And BodyTex > 0 Then BodyTex = 0
		If CountSurfaces(A\EN) > 1 And A\Actor\MaleFaceIDs[FaceTex] < 65535
			; Find which surface is body
			B = GetSurfaceBrush(GetSurface(A\EN, 1)) : T = GetBrushTexture(B)
			Name$ = TextureName$(T)
			FreeTexture T : FreeBrush(B)
			; Default way round
			FaceSurface = GetSurface(A\EN, 2)
			BodySurface = GetSurface(A\EN, 1)
			; Should be the other way round
			If Instr(Upper$(Name$), "HEAD") > 0
				FaceSurface = GetSurface(A\EN, 1)
				BodySurface = GetSurface(A\EN, 2)
			Else
				B = GetSurfaceBrush(GetSurface(A\EN, 2)) : T = GetBrushTexture(B)
				Name$ = Upper$(TextureName$(T))
				FreeTexture T : FreeBrush(B)
				; A texture is assigned which is not one of the dummy textures, check if it's a body texture
				If Instr(Name$, "HEAD") = 0
					For i = 0 To 4
						Name2$ = Upper$(GetTextureName$(A\Actor\MaleBodyIDs[i]))
						If Name2$ <> ""
							Name2$ = Left$(Name2$, Len(Name2$) - 1)
							If Instr(Name$, Name2$)
								FaceSurface = GetSurface(A\EN, 1)
								BodySurface = GetSurface(A\EN, 2)
								Exit
							EndIf
						EndIf
					Next
				EndIf
			EndIf

			; Paint
			B = CreateBrush()
			Tex = GetTexture(A\Actor\MaleBodyIDs[BodyTex])
			If Tex <> 0
				BrushTexture(B, Tex)
				PaintSurface(BodySurface, B)
			EndIf
			Tex = GetTexture(A\Actor\MaleFaceIDs[FaceTex])
			If Tex <> 0
				BrushTexture(B, Tex)
				PaintSurface(FaceSurface, B)
			EndIf
			FreeBrush(B)
			UnloadTexture(A\Actor\MaleBodyIDs[BodyTex])
			UnloadTexture(A\Actor\MaleFaceIDs[FaceTex])
		Else
			Tex = GetTexture(A\Actor\MaleBodyIDs[BodyTex])
			If Tex <> 0 Then EntityTexture(A\EN, Tex)
			UnloadTexture(A\Actor\MaleBodyIDs[BodyTex])
		EndIf

		; Beard
		If A\Actor\BeardIDs[A\Beard] > -1 And A\Actor\BeardIDs[A\Beard] < 65535 And SkipAttachments = False
			ID = A\Actor\BeardIDs[A\Beard]
			BeardEN = GetMesh(ID, True)
			If BeardEN <> 0
				Bonce = FindChild(A\EN, "Head")
				If Bonce = 0 Then RuntimeError(A\Actor\Race$ + " actor mesh is missing a 'Head' joint!")
				EntityParent BeardEN, Bonce, False
				PositionEntity BeardEN, LoadedMeshX#(ID), LoadedMeshY#(ID), LoadedMeshZ#(ID)
				ScaleEntity BeardEN, LoadedMeshScales#(ID), LoadedMeshScales#(ID), LoadedMeshScales#(ID)
				; Correct rotation for Max models
				If A\TeamID = True Then TurnEntity BeardEN, 0, 180, 90
				NameEntity(BeardEN, "Beard")
			EndIf
		EndIf

		; Get radius
		MaxLength# = MeshWidth#(A\EN)
		If MeshDepth#(A\EN) > MaxLength# Then MaxLength# = MeshDepth#(A\EN)
		A\Actor\Radius# = (MaxLength# * LoadedMeshScales#(A\Actor\MeshIDs[0]) * A\Actor\Scale#) / 2.0
	Else
		ActorAnimSet.AnimSet = AnimList(A\Actor\FAnimationSet)
		Name$ = GetMeshName$(A\Actor\MeshIDs[1])
		; CE model
		If Upper$(Left$(Name$, 10)) = "CE\CESAVES"
			A\EN = LoadCEMesh(A\Actor\MeshIDs[1], ActorAnimSet\AnimStart[Anim_Idle], ActorAnimSet\AnimStart[Anim_Idle])
		; Normal model
		Else
			A\EN = GetMesh(A\Actor\MeshIDs[1], ActorHasMultipleTextures(A\Actor, 1))
			If A\EN = 0
				FreeEntity(A\CollisionEN)
				Return False
			EndIf
			If CountSurfaces(A\EN) < 1
				; This is a server field, being used as a flag to say that the model is a Max model.
				; I have avoided adding a new field for this to conserve server memory, as both client
				; and server use the same Actor type definition.
				; It is used when attaching gubbins, as Max meshes have different joint rotations to
				; all other meshes.
				A\TeamID = True
				For i = 1 To CountChildren(A\EN)
					EN = GetChild(A\EN, i)
					If CountSurfaces(EN) > 0 Then A\EN = EN : Exit
				Next
			EndIf
		EndIf
		Scale# = Scale# * LoadedMeshScales#(A\Actor\MeshIDs[1]) * A\Actor\Scale#
		EntityParent(A\EN, A\CollisionEN)
		MMV.MeshMinMaxVertices = MeshMinMaxVertices(A\EN)
		PositionEntity A\EN, 0.0, (MMV\MaxY# - MMV\MinY#) / -2.0, 0.0
		BodyTex = A\BodyTex
		If A\Actor\FemaleBodyIDs[BodyTex] = 65535 And BodyTex > 0 Then BodyTex = 0
		FaceTex = A\FaceTex
		If A\Actor\FemaleFaceIDs[FaceTex] = 65535 And FaceTex > 0 Then FaceTex = 0
		
		If CountSurfaces(A\EN) > 1 And A\Actor\FemaleFaceIDs[FaceTex] < 65535
			; Find which surface is body
			B = GetSurfaceBrush(GetSurface(A\EN, 1)) : T = GetBrushTexture(B)
			Name$ = TextureName$(T)
			FreeTexture T : FreeBrush(B)
			FaceSurface = GetSurface(A\EN, 2)
			BodySurface = GetSurface(A\EN, 1)
			If Instr(Upper$(Name$), "HEAD") > 0
				FaceSurface = GetSurface(A\EN, 1)
				BodySurface = GetSurface(A\EN, 2)
			Else
				B = GetSurfaceBrush(GetSurface(A\EN, 2)) : T = GetBrushTexture(B)
				Name$ = Upper$(TextureName$(T))
				FreeTexture T : FreeBrush(B)
				; A texture is assigned which is not one of the dummy textures, check if it's a face texture
				If Instr(Name$, "HEAD") = 0
					For i = 0 To 5
						Name2$ = Upper$(GetTextureName$(A\Actor\FemaleBodyIDs[i]))
						If Name2$ <> ""
							Name2$ = Left$(Name2$, Len(Name2$) - 1)
							If Instr(Name$, Name2$)
								FaceSurface = GetSurface(A\EN, 1)
								BodySurface = GetSurface(A\EN, 2)
								Exit
							EndIf
						EndIf
					Next
				EndIf
			EndIf

			; Paint
			B = CreateBrush()
			Tex = GetTexture(A\Actor\FemaleBodyIDs[BodyTex])
			If Tex <> 0
				BrushTexture(B, Tex)
				PaintSurface(BodySurface, B)
			EndIf
			Tex = GetTexture(A\Actor\FemaleFaceIDs[FaceTex])
			If Tex <> 0
				BrushTexture(B, Tex)
				PaintSurface(FaceSurface, B)
			EndIf
			FreeBrush(B)
			UnloadTexture(A\Actor\FemaleBodyIDs[BodyTex])
			UnloadTexture(A\Actor\FemaleFaceIDs[FaceTex])
		Else
			Tex = GetTexture(A\Actor\FemaleBodyIDs[BodyTex])
			If Tex <> 0 Then EntityTexture A\EN, Tex
			UnloadTexture(A\Actor\FemaleBodyIDs[BodyTex])
		EndIf

		; Get radius
		MaxLength# = MeshWidth#(A\EN)
		If MeshDepth#(A\EN) > MaxLength# Then MaxLength# = MeshDepth#(A\EN)
		A\Actor\Radius# = (MaxLength# * LoadedMeshScales#(A\Actor\MeshIDs[1]) * A\Actor\Scale#) / 2.0
	EndIf

	; Animations
	If ActorAnimSet <> Null
		For i = 0 To 149
			If ActorAnimSet\AnimEnd[i] > 0
				A\AnimSeqs[i] = ExtractAnimSeq(A\EN, ActorAnimSet\AnimStart[i], ActorAnimSet\AnimEnd[i])
			EndIf
		Next
	EndIf

	; Scale
	ScaleEntity A\CollisionEN, Scale#, Scale#, Scale#

	If SkipAttachments = False
		; Attached emitters
		CreateEntityEmitters(A\EN)

		; Hair
		SetActorHat(A, -1)

		; Collision
		MaxLength# = MMV\MaxX# - MMV\MinX#
		If MMV\MaxZ# - MMV\MinZ# > MaxLength# Then MaxLength# = ((MMV\MaxZ# - MMV\MinZ#) + MaxLength#) / 2.0
		EntityRadius(A\CollisionEN, (MaxLength# * Scale#) / 2.0, ((MMV\MaxY# - MMV\MinY#) * Scale#) / 2.0)
		Width# = (MMV\MaxX# - MMV\MinX#) * Scale#
		Height# = (MMV\MaxY# - MMV\MinY#) * Scale#
		Depth# = (MMV\MaxZ# - MMV\MinZ#) * Scale#
		EntityBox(A\CollisionEN, MMV\MinX# * Scale#, MMV\MinY# * Scale#, MMV\MinZ# * Scale#, Width#, Height#, Depth#)
		If A\Actor\PolyCollision = False
			EntityType(A\CollisionEN, C_Actor)
		Else
			EntityType(A\CollisionEN, C_ActorTri1)
			EntityType(A\EN, C_ActorTri2)
		EndIf

		; Shadow	[###]	
	;	A\ShadowEN = CreateMesh()
	;	s = CreateSurface(A\ShadowEN)
	;	v1 = AddVertex(s, -1.0, 0.0, -1.0, 0.0, 0.0)
	;	v2 = AddVertex(s, -1.0, 0.0, 1.0,  0.0, 1.0)
	;	v3 = AddVertex(s, 1.0,  0.0, 1.0,  1.0, 1.0)
	;	v4 = AddVertex(s, 1.0,  0.0, -1.0, 1.0, 0.0)
	;	AddTriangle s, v1, v2, v3
	;	AddTriangle s, v1, v3, v4
	;	EntityFX A\ShadowEN, 1
	;	EntityBlend A\ShadowEN, 2
	;	ScaleEntity A\ShadowEN, (MaxLength# * Scale#) * 0.3, 1, (MaxLength# * Scale#) * 0.3 ; 0.5 for half side * 0.75 to compensate for falloff ; MeshWidth#(A\EN) * Scale# * 0.5, 1.0, MeshDepth#(A\EN) * Scale# * 0.5  ;
	;	Tex = LoadTexture("Data\Textures\Shadow.bmp")
	;	If Tex = 0 Then RuntimeError("Could not load Data\Textures\Shadow.bmp!")
	;	EntityTexture(A\ShadowEN, Tex)

		; Debug code to display the collision radius
;		CollisionSphere = CreateSphere()
;		EntityColor(CollisionSphere, 255, 0, 0)
;		EntityAlpha(CollisionSphere, 0.35)
;		EntityParent(CollisionSphere, A\CollisionEN, False)
;		ScaleEntity(CollisionSphere, MaxLength# / 2.0, (MMV\MaxY# - MMV\MinY#) / 2.0, MaxLength# / 2.0)

		; Items
		UpdateActorItems(A)

		; Nametag
		If HideNametags <> 1 Then CreateActorNametag(A)
	EndIf

	;- Enable AutoFade when not in CharSelection Screen
	If autoFade
		EntityAutoFade(A\EN, nearFadeModifier * CameraViewRange, farFadeModifier * CameraViewRange)
		If BeardEN <> 0 Then EntityAutoFade(BeardEN, nearFadeModifier * CameraViewRange, farFadeModifier * CameraViewRange)
		;If A\ShadowEN <> 0 Then EntityAutoFade(A\ShadowEN, nearFadeModifier * CameraViewRange, farFadeModifier * CameraViewRange)
	EndIf
	
	; Free MinMaxVertices data
	Delete(MMV)

	; Type handle
	NameEntity A\CollisionEN, Handle(A)
	NameEntity A\EN, Handle(A)
	
	
	Return True

End Function

; Textures an actor instance's nametag entity
Function CreateActorNametag(A.ActorInstance)

	If A\NametagEN <> 0 Then FreeEntity(A\NametagEN)

	MMV.MeshMinMaxVertices = MeshMinMaxVertices(A\EN)

	; Name
	A\NametagEN = GY_Create3DText(0.0, 0.0, 1.0, 1.0, Len(A\Name$), GY_TitleFont, 0, 0)
	GY_Set3DText(A\NametagEN, A\Name$)
	PositionMesh(A\NametagEN, MeshWidth#(A\NametagEN) / -2.0, 1.0, 0.0)
	EntityParent(A\NametagEN, A\EN)
	ScaleEntity(A\NametagEN, -0.35 * Len(A\Name$), 0.65, 1, True) ;-0.45, 0.75
	PositionEntity(A\NametagEN, 0, MMV\MaxY#, 0)
	TranslateEntity(A\NametagEN, 0, 0.5, 0, True) ;0, 0.5, 0
	EntityAutoFade(A\NametagEN, 70, 75)
	If A\RNID = 0
	;Adding 3rd color options for name tags Cysis145
		If A\Actor\Aggressiveness = 3
			EntityColor(A\NametagEN, 0, 174, 14) ;Non Combatant
			
		ElseIf A\Actor\Aggressiveness = 0
			EntityColor(A\NametagEN, 255, 219, 114) ; Passive
			
		ElseIf A\Actor\Aggressiveness = 1
			EntityColor(A\NametagEN, 255, 219, 114) ;Defensive
			
		Else
			EntityColor(A\NametagEN, 168, 0, 0) ; Always Attacks
		EndIf
	EndIf

	; Extra tag (second line)
	If A\Tag$ <> ""
		Tag$ = "<" + A\Tag$ + ">"
		EN = GY_Create3DText(0.0, 0.0, 1.0, 1.0, Len(Tag$), GY_TitleFont, 0, 0)
		GY_Set3DText(EN, Tag$)
		PositionMesh(EN, MeshWidth#(EN) / -2.0, 1.0, 0.0)
		EntityParent(EN, A\NametagEN)
		ScaleEntity(EN, -0.3 * Len(Tag$), 0.6, 1, True) ;-0.45, 0.75
		PositionEntity(EN, 0, 0, 0)
		TranslateEntity(EN, 0.0, -0.5, 0.0, True) ; 0,-0.7
		TranslateEntity(A\NametagEN, 0.0, 0.7, 0.0, True) ;0, 0.7, 0
		EntityAutoFade(EN, 70, 75)
		If A\RNID = 0
		;Adding 3rd color options for name tags Cysis145
			If A\Actor\Aggressiveness = 3 ;Non combatant
				EntityColor(EN, 0, 174, 14)
			
			ElseIf A\Actor\Aggressiveness = 0 ; Passive
				EntityColor(EN, 255, 219, 114)
				
			ElseIf A\Actor\Aggressiveness = 1 ; Defensive
				EntityColor(EN, 255, 219, 114)
			
			Else
				EntityColor(EN, 168, 0, 0) ;Always Attacks
			EndIf
		EndIf
		TranslateEntity(A\NametagEN, 0, 0.5, 0, True)
	EndIf

	Delete(MMV)

End Function

; Frees the 3D stuff for an actor instance, but leaves the instance
Function FreeActorInstance3D(A.ActorInstance)

	For i = 0 To 5
		If A\GubbinEN[i] <> 0 Then FreeEntityEmitters(A\GubbinEN[i]) : FreeEntity A\GubbinEN[i] : A\GubbinEN[i] = 0
	Next
	If A\HatEN <> 0 Then FreeEntityEmitters(A\HatEN) : FreeEntity A\HatEN : A\HatEN = 0
	If A\ShieldEN <> 0 Then FreeEntityEmitters(A\ShieldEN) : FreeEntity A\ShieldEN : A\ShieldEN = 0
	If A\WeaponEN <> 0 Then FreeEntityEmitters(A\WeaponEN) : FreeEntity A\WeaponEN : A\WeaponEN = 0
	If A\ChestEN <> 0 Then FreeEntityEmitters(A\ChestEN) : FreeEntity A\ChestEN : A\ChestEN = 0
	;If A\ShadowEN <> 0 Then FreeEntity A\ShadowEN : A\ShadowEN = 0
	If A\NametagEN <> 0 Then FreeEntity A\NametagEN : A\NametagEN = 0
	If A\EN <> 0
		; If no other actor instances are using this mesh, unload it completely
		Found = False
		For A2.ActorInstance = Each ActorInstance
			If A2\Actor\MeshIDs[A2\Gender] = A\Actor\MeshIDs[A\Gender]
				If A2 <> A
					Found = True
					Exit
				EndIf
			EndIf
		Next
		If Found = False Then UnloadMesh(A\Actor\MeshIDs[A\Gender])
		; Free main mesh
		FreeEntityEmitters(A\EN)
		FreeEntity(A\EN)
		A\EN = 0
	EndIf
	FreeEntity A\CollisionEN : A\CollisionEN = 0

End Function

; Frees an actor instance and the 3D as well
Function SafeFreeActorInstance(A.ActorInstance)

	; Free actor instance
	If Handle(A) = PlayerTarget Then PlayerTarget = 0
	FreeActorInstance3D(A)
	FreeActorInstance(A)

End Function

; Shows a gubbin
Function ShowGubbin(A.ActorInstance, Num, SuppressError = False)

	If A\GubbinEN[Num] = 0
		ID = A\Actor\MeshIDs[2 + Num]
		If ID < 65535
			A\GubbinEN[Num] = GetMesh(ID)
			If A\GubbinEN[Num] = 0
				If SuppressError = False
					RuntimeError("Could not load gubbin mesh for " + A\Actor\Race$ + " actor!")
				Else
					Return
				EndIf
			EndIf
			EntityAutoFade(A\GubbinEN[Num], nearFadeModifier * CameraViewRange, farFadeModifier * CameraViewRange)
			Bone = FindChild(A\EN, GubbinJoints$(Num))
			If Bone = 0 Then RuntimeError(A\Actor\Race$ + " actor mesh is missing a '" + GubbinJoints$(Num) + "' joint!")
			EntityParent(A\GubbinEN[Num], Bone, False)
			PositionEntity A\GubbinEN[Num], LoadedMeshX#(ID), LoadedMeshY#(ID), LoadedMeshZ#(ID)
			ScaleEntity A\GubbinEN[Num], LoadedMeshScales#(ID), LoadedMeshScales#(ID), LoadedMeshScales#(ID)
			; Correct rotation for Max models
			If A\TeamID = True Then TurnEntity A\GubbinEN[Num], 0, 180, 90
			CreateEntityEmitters(A\GubbinEN[Num])
		EndIf
	Else
		ShowEntity(A\GubbinEN[Num])
		CreateEntityEmitters(A\GubbinEN[Num])
	EndIf

End Function

; Hides a gubbin
Function HideGubbin(A.ActorInstance, Num)

	If A\GubbinEN[Num] <> 0
		HideEntity(A\GubbinEN[Num])
		FreeEntityEmitters(A\GubbinEN[Num])
	EndIf

End Function

; Creates emitters on an entity's children based on names (RECURSIVE)
Function CreateEntityEmitters(E)

	For i = 1 To CountChildren(E)
		CE = GetChild(E, i)
		CreateEntityEmitters(CE)
		Name$ = EntityName$(CE)
		If Len(Name$) > 4
			If Upper$(Left$(Name$, 2)) = "E_"
				Name$ = Mid$(Name$, 3)
				Pos = Instr(Name$, "_")
				If Pos > 0
					EmitterName$ = Left$(Name$, Pos - 1)
					TextureID = Mid$(Name$, Pos + 1)
					Texture = GetTexture(TextureID)
					If Texture <> 0 And Len(EmitterName$) > 1
						Config = RP_LoadEmitterConfig("Data\Emitter Configs\" + EmitterName$ + ".rpc", Texture, Cam)
						If Config <> 0
							EmitterEN = RP_CreateEmitter(Config)
							If EmitterEN <> 0
								EntityParent(EmitterEN, CE, False)
							Else
								RP_FreeEmitterConfig(Config, False)
								UnloadTexture(TextureID)
							EndIf
						EndIf
					EndIf
				EndIf
			EndIf
		EndIf
	Next

End Function

; Frees emitters attached to an entity (RECURSIVE)
Function FreeEntityEmitters(E)

	For i = 1 To CountChildren(E)
		CE = GetChild(E, i)
		FreeEntityEmitters(CE)
		If Object.RP_Emitter(EntityName$(CE)) <> Null
			RP_FreeEmitter(CE, True, False)
		EndIf
	Next

End Function

; Updates an actor instance's 3D based on what is on their inventory
Function UpdateActorItems(A.ActorInstance)

	; Items
	If A\Inventory\Items[SlotI_Weapon] <> Null
		If A\Gender = 0
			SetActorWeapon(A, A\Inventory\Items[SlotI_Weapon]\Item\MMeshID)
		Else
			SetActorWeapon(A, A\Inventory\Items[SlotI_Weapon]\Item\FMeshID)
		EndIf
	ElseIf A\WeaponEN <> 0
		SetActorWeapon(A, -1)
	EndIf
	If A\Inventory\Items[SlotI_Shield] <> Null
		If A\Gender = 0
			SetActorShield(A, A\Inventory\Items[SlotI_Shield]\Item\MMeshID)
		Else
			SetActorShield(A, A\Inventory\Items[SlotI_Shield]\Item\FMeshID)
		EndIf
	ElseIf A\ShieldEN <> 0
		SetActorShield(A, -1)
	EndIf
	If A\Inventory\Items[SlotI_Chest] <> Null
		If A\Gender = 0
			SetActorChestArmour(A, A\Inventory\Items[SlotI_Chest]\Item\MMeshID)
		Else
			SetActorChestArmour(A, A\Inventory\Items[SlotI_Chest]\Item\FMeshID)
		EndIf
	ElseIf A\ChestEN <> 0
		SetActorChestArmour(A, -1)
	EndIf
	If A\Inventory\Items[SlotI_Hat] <> Null
		If A\Gender = 0
			SetActorHat(A, A\Inventory\Items[SlotI_Hat]\Item\MMeshID)
		Else
			SetActorHat(A, A\Inventory\Items[SlotI_Hat]\Item\FMeshID)
		EndIf
	ElseIf A\HatEN <> 0
		SetActorHat(A, -1)
	EndIf

	; Gubbins
	For i = 0 To 5
		HideGubbin(A, i)
	Next
	For i = 0 To SlotI_Backpack - 1
		If A\Inventory\Items[i] <> Null
			For j = 0 To 5
				If A\Inventory\Items[i]\Item\Gubbins[j] = True
					ShowGubbin(A, j)
				EndIf
			Next
		EndIf
	Next

End Function

; Plays an actor speech sound
Function PlayActorSound(A.ActorInstance, Speech)

		Result = 65535
		If A\Gender = 0
			If A\Actor\MSpeechIDs[Speech] < 65535 Then Result = A\Actor\MSpeechIDs[Speech]
		Else
			If A\Actor\FSpeechIDs[Speech] < 65535 Then Result = A\Actor\FSpeechIDs[Speech]
		EndIf
		If Result < 65535
			EN = FindChild(A\EN, "Head")
			If EN = 0 Then EN = A\EN
			EmitSound(GetSound(Result), EN)
		EndIf

End Function