; Realm Crafter Gubbin Tool for adjusting gubbin/item mesh offset, rotation and scale	
; By Rob W, August 2005

; Initialisation ---------------------------------------------------------------------------------------------------------
Global RootDir$ = "..\..\"
Global LogMode = 1; (0 = standard logging, 1 = debug mode)
ChangeDir RootDir$

Global AH_AppB$ = "RCSTD", AH_Loca$ = "..\New Game\"
;Include "AntiHack.bb"
Const testing=True

; Includes
Include "Modules\Spells.bb"
Include "Modules\Language.bb"
Include "Modules\Items.bb"
Include "Modules\Inventories.bb"
Include "Modules\Media.bb"
Include "Modules\MediaDialogs.bb"
Include "Modules\Animations.bb"
Include "Modules\RottParticles.bb"
Include "Modules\Actors.bb"
Include "Modules\CharacterEditorLoader.bb" ; RifRaf's character editor loading function
Include "Modules\Actors3D.bb"
Include "Modules\F-UI.bb"
Include "Modules\RCEnet.bb"
Include "Modules\Logging.bb"

; Load data
LoadAnimSets("Data\Game Data\Animations.dat")
TotalActors = LoadActors("Data\Server Data\Actors.dat")
If TotalActors < 1 Then RuntimeError("No actors found in project!")

LoadGubbinNames()

; Graphics mode
If GetSystemMetrics(0) > 800 And GetSystemMetrics(1) > 600
	Graphics3D(800, 600, 0, 2)
	FUI_Initialise(800, 600, 0, 2, False, True, "Realm Crafter Gubbin Tool")
Else
	Graphics3D(800, 600, 0, 0)
	FUI_Initialise(800, 600, 0, 0, False, True, "Realm Crafter Gubbin Tool")
EndIf
AppTitle("Realm Crafter Gubbin Tool")
HidePointer()

; Create gadgets
WMain = FUI_Window(0, 0, 800, 600, "", "", 0, 0)
BSave = FUI_Button(WMain, 715, 10, 70, 20, "Save")
BRevert = FUI_Button(WMain, 715, 35, 70, 20, "Revert")
BReset = FUI_Button(WMain, 715, 60, 70, 20, "Reset")
VPreview = FUI_View(WMain, 50, 155, 700, 440, 50, 0, 200)
Global PreviewCam = FUI_SendMessage(VPreview, M_GETCAMERA)
FUI_Label(WMain, 15, 12, "Current actor:")
Global CActorSelected = FUI_ComboBox(WMain, 95, 10, 300, 20, 9)
BActorPrev = FUI_Button(WMain, 415, 10, 25, 20, "<<") : FUI_ToolTip(BActorPrev, "Previous actor")
BActorNext = FUI_Button(WMain, 455, 10, 25, 20, ">>") : FUI_ToolTip(BActorNext, "Next actor")
FUI_Label(WMain, 510, 12, "Actor gender:")
Global CGender = FUI_ComboBox(WMain, 590, 10, 90, 20)
FUI_ComboBoxItem(CGender, "Male")
FUI_ComboBoxItem(CGender, "Female")
FUI_Label(WMain, 15, 42, "Current bone:")
Global CBoneSelected = FUI_ComboBox(WMain, 95, 40, 200, 20, 5)
FUI_ComboBoxItem(CBoneSelected, "Head")
FUI_ComboBoxItem(CBoneSelected, "Chest")
FUI_ComboBoxItem(CBoneSelected, "R_Hand")
FUI_ComboBoxItem(CBoneSelected, "L_Hand")
FUI_ComboBoxItem(CBoneSelected, "R_Shoulder")
FUI_ComboBoxItem(CBoneSelected, "L_Shoulder")
FUI_ComboBoxItem(CBoneSelected, "R_Forearm")
FUI_ComboBoxItem(CBoneSelected, "L_Forearm")
FUI_ComboBoxItem(CBoneSelected, "R_Shin")
FUI_ComboBoxItem(CBoneSelected, "L_Shin")
For i = 0 To 5
	If ValidBoneName(GubbinJoints(i)) Then FUI_ComboBoxItem(CBoneSelected, GubbinJoints(i))
Next

FUI_Label(WMain, 15, 72, "Current mesh:")
Global LGubbin = FUI_TextBox(WMain, 95, 70, 300, 20, "")
FUI_DisableGadget(LGubbin)
BChangeMesh = FUI_Button(WMain, 415, 70, 90, 20, "Change")
BCopyMesh = FUI_Button(WMain, 515, 70, 90, 20, "Duplicate")
BForward =   FUI_Button(WMain, 60, 110, 70, 20, "Forward")
BBack =      FUI_Button(WMain, 60, 135, 70, 20, "Back")
BRight =     FUI_Button(WMain, 140, 110, 70, 20, "Right")
BLeft =      FUI_Button(WMain, 140, 135, 70, 20, "Left")
BUp =        FUI_Button(WMain, 220, 110, 70, 20, "Up")
BDown =      FUI_Button(WMain, 220, 135, 70, 20, "Down")
BTUp =       FUI_Button(WMain, 350, 110, 70, 20, "Pitch up")
BTDown =     FUI_Button(WMain, 350, 135, 70, 20, "Pitch down")
BTRight =    FUI_Button(WMain, 430, 110, 70, 20, "Yaw right")
BTLeft =     FUI_Button(WMain, 430, 135, 70, 20, "Yaw left")
BTRRight =   FUI_Button(WMain, 510, 110, 70, 20, "Roll right")
BTRLeft =    FUI_Button(WMain, 510, 135, 70, 20, "Roll left")
BScaleUp =   FUI_Button(WMain, 640, 110, 90, 20, "Scale larger")
BScaleDown = FUI_Button(WMain, 640, 135, 90, 20, "Scale smaller")
ButtonDown = 0
ButtonTime = MilliSecs()

; Initial values
Global ChangesSaved = True
InitMediaDialogs()
For At.Actor = Each Actor
	Item = FUI_ComboBoxItem(CActorSelected, At\Race$ + " [" + At\Class$ + "]")
	FUI_SendMessage(Item, M_SETDATA, At\ID)
Next
FUI_SendMessage(CBoneSelected, M_SETINDEX, 1)
FUI_SendMessage(CActorSelected, M_SETINDEX, 1)
FUI_SendMessage(CGender, M_SETINDEX, 1)
Global SelectedActor.Actor, ActorPreview.ActorInstance
Global SelectedMeshID = 65535, PreviewMesh
Global PreviewPitch#, PreviewYaw#, PreviewDistance# = 30.0
SetActor()
UpdatePreviewCam()
Global TempPosX#, TempPosY#, TempPosZ#, TempScale#
Global gubbinID

; Delta timing
Dim DeltaBuffer(5)
For i = 0 To 5 : DeltaBuffer(i) = 35 : Next
Global FPS#, Delta#
Const BaseFramerate# = 30.0

; Main loop --------------------------------------------------------------------------------------------------------------

Repeat

	; Process events
	For E.Event = Each Event
		Select E\EventID

			; Apply all changes
			Case BSave
				SaveAll()

			; Duplicate gubbin mesh
			Case BCopyMesh
				If PreviewMesh <> 0
					; Save changes dialog
					If ChangesSaved = False
						Result = FUI_CustomMessageBox("All changes to the current gubbin will be saved. Continue?", "Warning", MB_YESNO)
						If Result = IDYES Then SaveAll()
					Else
						Result = IDYES
					EndIf
					; Duplicate
					If Result = IDYES
						Name$ = MeshNameDialog$()
						If Name$ <> ""
							OldName$ = EditorMeshName$(SelectedMeshID)
							CopyFile("Data\Meshes\" + OldName$, "Data\Meshes\" + Name$)
							Result = AddMeshToDatabase(Name$, False)
							If Result > -1
								MeshNames$(Result) = Name$ + Chr$(0)
								SetMeshOffset(Result, LoadedMeshX#(SelectedMeshID), LoadedMeshY#(SelectedMeshID), LoadedMeshZ#(SelectedMeshID))
								SetMeshScale(Result, LoadedMeshScales#(SelectedMeshID))
								SaveRotation(Result)
							EndIf
						EndIf
					EndIf
				EndIf

			; Revert gubbin to last saved settings
			Case BRevert
				If PreviewMesh <> 0
					SetGubbinMesh(SelectedMeshID)
					ChangesSaved = True
				EndIf

			; Reset gubbin
			Case BReset
				If PreviewMesh <> 0
					TempPosX# = 0.0
					TempPosY# = 0.0
					TempPosZ# = 0.0
					TempScale# = 1.0
					PositionEntity PreviewMesh, 0, 0, 0
					RotateEntity PreviewMesh, 0, 0, 0
					ScaleEntity PreviewMesh, 1, 1, 1
					SetMeshOffset(SelectedMeshID, 0, 0, 0)
					SetMeshScale(SelectedMeshID, 1)
					; Correct rotation for Max models
					If ActorPreview\TeamID = True Then TurnEntity(PreviewMesh, 0, 180, 90)
				EndIf

			; Actor changed
			Case CActorSelected
				SetActor()
			Case BActorPrev
				Idx = FUI_SendMessage(CActorSelected, M_GETINDEX)
				Idx = Idx - 1
				If Idx < 1 Then Idx = TotalActors
				FUI_SendMessage(CActorSelected, M_SETINDEX, Idx)
				SetActor()
			Case BActorNext
				Idx = FUI_SendMessage(CActorSelected, M_GETINDEX)
				Idx = Idx + 1
				If Idx > TotalActors Then Idx = 1
				FUI_SendMessage(CActorSelected, M_SETINDEX, Idx)
				SetActor()
			Case CGender
				SetActor()

			; Bone changed
			Case CBoneSelected
				SetGubbinMesh(SelectedMeshID)
				Bone = FindChild(ActorPreview\EN, FUI_SendMessage(CBoneSelected, M_GETCAPTION))
				If Bone <> 0 Then TranslateEntity(ActorPreview\CollisionEN, -EntityX#(Bone, True), -EntityY#(Bone, True), -EntityZ#(Bone, True))

			; Gubbin mesh changed
			Case BChangeMesh
				; Save changes dialog
				If ChangesSaved = False
					Result = FUI_CustomMessageBox("All changes to the current gubbin will be lost. Continue?", "Warning", MB_YESNO)
				Else
					Result = IDYES
				EndIf
				; Change mesh
				If Result = IDYES
					ID = ChooseMeshDialog(MeshDialog_All)
					If ID > -1
						SetGubbinMesh(ID)
						Name$ = EditorMeshName$(SelectedMeshID)
						If Right$(Upper$(Name$), 3) <> "B3D"
							FUI_CustomMessageBox("Rotation changes cannot be saved for this mesh format!", "Warning", MB_OK)
						EndIf
					EndIf
				EndIf

			; Gubbin mesh control
			Case BForward
				If PreviewMesh <> 0
					If ButtonDown <> BForward Or MilliSecs() - ButtonTime < 750
						TranslateEntity PreviewMesh, 0, 0, 0.1, True
						TempPosX# = EntityX#(PreviewMesh) : TempPosY# = EntityY#(PreviewMesh) : TempPosZ# = EntityZ#(PreviewMesh)
						ChangesSaved = False
					EndIf
				EndIf
			Case BBack
				If PreviewMesh <> 0
					If ButtonDown <> BBack Or MilliSecs() - ButtonTime < 750
						TranslateEntity PreviewMesh, 0, 0, -0.1, True
						TempPosX# = EntityX#(PreviewMesh) : TempPosY# = EntityY#(PreviewMesh) : TempPosZ# = EntityZ#(PreviewMesh)
						ChangesSaved = False
					EndIf
				EndIf
			Case BRight
				If PreviewMesh <> 0
					If ButtonDown <> BRight Or MilliSecs() - ButtonTime < 750
						TranslateEntity PreviewMesh, 0.1, 0, 0, True
						TempPosX# = EntityX#(PreviewMesh) : TempPosY# = EntityY#(PreviewMesh) : TempPosZ# = EntityZ#(PreviewMesh)
						ChangesSaved = False
					EndIf
				EndIf
			Case BLeft
				If PreviewMesh <> 0
					If ButtonDown <> BLeft Or MilliSecs() - ButtonTime < 750
						TranslateEntity PreviewMesh, -0.1, 0, 0, True
						TempPosX# = EntityX#(PreviewMesh) : TempPosY# = EntityY#(PreviewMesh) : TempPosZ# = EntityZ#(PreviewMesh)
						ChangesSaved = False
					EndIf
				EndIf
			Case BUp
				If PreviewMesh <> 0
					If ButtonDown <> BUp Or MilliSecs() - ButtonTime < 750
						TranslateEntity PreviewMesh, 0, 0.1, 0, True
						TempPosX# = EntityX#(PreviewMesh) : TempPosY# = EntityY#(PreviewMesh) : TempPosZ# = EntityZ#(PreviewMesh)
						ChangesSaved = False
					EndIf
				EndIf
			Case BDown
				If PreviewMesh <> 0
					If ButtonDown <> BDown Or MilliSecs() - ButtonTime < 750
						TranslateEntity PreviewMesh, 0, -0.1, 0, True
						TempPosX# = EntityX#(PreviewMesh) : TempPosY# = EntityY#(PreviewMesh) : TempPosZ# = EntityZ#(PreviewMesh)
						ChangesSaved = False
					EndIf
				EndIf
			Case BTUp
				If PreviewMesh <> 0
					If ButtonDown <> BTUp Or MilliSecs() - ButtonTime < 750
						TurnEntity PreviewMesh, 1, 0, 0
						ChangesSaved = False
					EndIf
				EndIf
			Case BTDown
				If PreviewMesh <> 0
					If ButtonDown <> BTDown Or MilliSecs() - ButtonTime < 750
						TurnEntity PreviewMesh, -1, 0, 0
						ChangesSaved = False
					EndIf
				EndIf
			Case BTRight
				If PreviewMesh <> 0
					If ButtonDown <> BTRight Or MilliSecs() - ButtonTime < 750
						TurnEntity PreviewMesh, 0, 1, 0
						ChangesSaved = False
					EndIf
				EndIf
			Case BTLeft
				If PreviewMesh <> 0
					If ButtonDown <> BTLeft Or MilliSecs() - ButtonTime < 750
						TurnEntity PreviewMesh, 0, -1, 0
						ChangesSaved = False
					EndIf
				EndIf
			Case BTRRight
				If PreviewMesh <> 0
					If ButtonDown <> BTRRight Or MilliSecs() - ButtonTime < 750
						TurnEntity PreviewMesh, 0, 0, 1
						ChangesSaved = False
					EndIf
				EndIf
			Case BTRLeft
				If PreviewMesh <> 0
					If ButtonDown <> BTRLeft Or MilliSecs() - ButtonTime < 750
						TurnEntity PreviewMesh, 0, 0, -1
						ChangesSaved = False
					EndIf
				EndIf
			Case BScaleUp
				If PreviewMesh <> 0
					If ButtonDown <> BScaleUp Or MilliSecs() - ButtonTime < 750
						TempScale# = TempScale# * 1.007
						ScaleEntity(PreviewMesh, TempScale#, TempScale#, TempScale#)
						ChangesSaved = False
					EndIf
				EndIf
			Case BScaleDown
				If PreviewMesh <> 0
					If ButtonDown <> BScaleDown Or MilliSecs() - ButtonTime < 750
						TempScale# = TempScale# * 0.993
						ScaleEntity(PreviewMesh, TempScale#, TempScale#, TempScale#)
						ChangesSaved = False
					EndIf
				EndIf

		End Select
		Delete(E)
	Next

	; Gubbin mesh control
	If MouseDown(1) And PreviewMesh <> 0
		; Movement
		If FUI_OverGadget(BForward)
			If ButtonDown = BForward
				If MilliSecs() - ButtonTime > 750
					TranslateEntity PreviewMesh, 0, 0, 1.0 * Delta#, True
					TempPosX# = EntityX#(PreviewMesh) : TempPosY# = EntityY#(PreviewMesh) : TempPosZ# = EntityZ#(PreviewMesh)
					ChangesSaved = False
				EndIf
			Else
				ButtonDown = BForward
				ButtonTime = MilliSecs()
			EndIf
		ElseIf FUI_OverGadget(BBack)
			If ButtonDown = BBack
				If MilliSecs() - ButtonTime > 750
					TranslateEntity PreviewMesh, 0, 0, -1.0 * Delta#, True
					TempPosX# = EntityX#(PreviewMesh) : TempPosY# = EntityY#(PreviewMesh) : TempPosZ# = EntityZ#(PreviewMesh)
					ChangesSaved = False
				EndIf
			Else
				ButtonDown = BBack
				ButtonTime = MilliSecs()
			EndIf
		ElseIf FUI_OverGadget(BRight)
			If ButtonDown = BRight
				If MilliSecs() - ButtonTime > 750
					TranslateEntity PreviewMesh, 1.0 * Delta#, 0, 0, True
					TempPosX# = EntityX#(PreviewMesh) : TempPosY# = EntityY#(PreviewMesh) : TempPosZ# = EntityZ#(PreviewMesh)
					ChangesSaved = False
				EndIf
			Else
				ButtonDown = BRight
				ButtonTime = MilliSecs()
			EndIf
		ElseIf FUI_OverGadget(BLeft)
			If ButtonDown = BLeft
				If MilliSecs() - ButtonTime > 750
					TranslateEntity PreviewMesh, -1.0 * Delta#, 0, 0, True
					TempPosX# = EntityX#(PreviewMesh) : TempPosY# = EntityY#(PreviewMesh) : TempPosZ# = EntityZ#(PreviewMesh)
					ChangesSaved = False
				EndIf
			Else
				ButtonDown = BLeft
				ButtonTime = MilliSecs()
			EndIf
		ElseIf FUI_OverGadget(BUp)
			If ButtonDown = BUp
				If MilliSecs() - ButtonTime > 750
					TranslateEntity PreviewMesh, 0, 1.0 * Delta#, 0, True
					TempPosX# = EntityX#(PreviewMesh) : TempPosY# = EntityY#(PreviewMesh) : TempPosZ# = EntityZ#(PreviewMesh)
					ChangesSaved = False
				EndIf
			Else
				ButtonDown = BUp
				ButtonTime = MilliSecs()
			EndIf
		ElseIf FUI_OverGadget(BDown)
			If ButtonDown = BDown
				If MilliSecs() - ButtonTime > 750
					TranslateEntity PreviewMesh, 0, -1.0 * Delta#, 0, True
					TempPosX# = EntityX#(PreviewMesh) : TempPosY# = EntityY#(PreviewMesh) : TempPosZ# = EntityZ#(PreviewMesh)
					ChangesSaved = False
				EndIf
			Else
				ButtonDown = BDown
				ButtonTime = MilliSecs()
			EndIf
		; Rotation
		ElseIf FUI_OverGadget(BTUp)
			If ButtonDown = BTUp
				If MilliSecs() - ButtonTime > 750
					TurnEntity PreviewMesh, 2.5 * Delta#, 0, 0
					ChangesSaved = False
				EndIf
			Else
				ButtonDown = BTUp
				ButtonTime = MilliSecs()
			EndIf
		ElseIf FUI_OverGadget(BTDown)
			If ButtonDown = BTDown
				If MilliSecs() - ButtonTime > 750
					TurnEntity PreviewMesh, -2.5 * Delta#, 0, 0
					ChangesSaved = False
				EndIf
			Else
				ButtonDown = BTDown
				ButtonTime = MilliSecs()
			EndIf
		ElseIf FUI_OverGadget(BTRight)
			If ButtonDown = BTRight
				If MilliSecs() - ButtonTime > 750
					TurnEntity PreviewMesh, 0, 2.5 * Delta#, 0
					ChangesSaved = False
				EndIf
			Else
				ButtonDown = BTRight
				ButtonTime = MilliSecs()
			EndIf
		ElseIf FUI_OverGadget(BTLeft)
			If ButtonDown = BTLeft
				If MilliSecs() - ButtonTime > 750
					TurnEntity PreviewMesh, 0, -2.5 * Delta#, 0
					ChangesSaved = False
				EndIf
			Else
				ButtonDown = BTLeft
				ButtonTime = MilliSecs()
			EndIf
		ElseIf FUI_OverGadget(BTRRight)
			If ButtonDown = BTRRight
				If MilliSecs() - ButtonTime > 750
					TurnEntity PreviewMesh, 0, 0, 2.5 * Delta#
					ChangesSaved = False
				EndIf
			Else
				ButtonDown = BTRRight
				ButtonTime = MilliSecs()
			EndIf
		ElseIf FUI_OverGadget(BTRLeft)
			If ButtonDown = BTRLeft
				If MilliSecs() - ButtonTime > 750
					TurnEntity PreviewMesh, 0, 0, -2.5 * Delta#
					ChangesSaved = False
				EndIf
			Else
				ButtonDown = BTRLeft
				ButtonTime = MilliSecs()
			EndIf
		; Scale
		ElseIf FUI_OverGadget(BScaleUp)
			If ButtonDown = BScaleUp
				If MilliSecs() - ButtonTime > 750
					TempScale# = TempScale# * (1.0 + (0.1 * Delta#))
					ScaleEntity(PreviewMesh, TempScale#, TempScale#, TempScale#)
					ChangesSaved = False
				EndIf
			Else
				ButtonDown = BScaleUp
				ButtonTime = MilliSecs()
			EndIf
		ElseIf FUI_OverGadget(BScaleDown)
			If ButtonDown = BScaleDown
				If MilliSecs() - ButtonTime > 750
					TempScale# = TempScale# * (1.0 - (0.1 * Delta#))
					ScaleEntity(PreviewMesh, TempScale#, TempScale#, TempScale#)
					ChangesSaved = False
				EndIf
			Else
				ButtonDown = BScaleDown
				ButtonTime = MilliSecs()
			EndIf
		EndIf
	Else
		ButtonTime = MilliSecs()
	EndIf

	; Keyboard camera movement
	If KeyDown(200)
		PreviewDistance# = PreviewDistance# - (2.5 * Delta#)
		If PreviewDistance# < 5.0 Then PreviewDistance# = 5.0
		UpdatePreviewCam()
	ElseIf KeyDown(208)
		PreviewDistance# = PreviewDistance# + (2.5 * Delta#)
		If PreviewDistance# > 150.0 Then PreviewDistance# = 150.0
		UpdatePreviewCam()
	ElseIf KeyDown(205)
		PreviewYaw# = PreviewYaw# + (5.0 * Delta#)
		If PreviewYaw# > 180.0 Then PreviewYaw# = PreviewYaw# - 360.0
		UpdatePreviewCam()
	ElseIf KeyDown(203)
		PreviewYaw# = PreviewYaw# - (5.0 * Delta#)
		If PreviewYaw# < -180.0 Then PreviewYaw# = PreviewYaw# + 360.0
		UpdatePreviewCam()
	ElseIf KeyDown(30)
		PreviewPitch# = PreviewPitch# - (5.0 * Delta#)
		If PreviewPitch# < -85.0 Then PreviewPitch# = -85.0
		UpdatePreviewCam()
	ElseIf KeyDown(44)
		PreviewPitch# = PreviewPitch# + (5.0 * Delta#)
		If PreviewPitch# > 85.0 Then PreviewPitch# = 85.0
		UpdatePreviewCam()
	EndIf

	; Quit
	If FUI_ShortCut("Alt", "F4") = True Or KeyHit(1)
		; Save changes dialog
		If ChangesSaved = False
			Result = FUI_CustomMessageBox("All changes to the current gubbin will be lost. Continue?", "Warning", MB_YESNO)
		Else
			Result = IDYES
		EndIf
		If Result = IDYES Then End()
	EndIf

	; Delta timing bits
	DeltaTime = MilliSecs() - DeltaTime
	DeltaBuffer(DeltaBufferIndex) = DeltaTime
	DeltaBufferIndex = DeltaBufferIndex + 1
	If DeltaBufferIndex > 5 Then DeltaBufferIndex = 0

	; Take average of last 6 frames to get delta time coefficient
	Time# = 0.0
	For i = 0 To 5
		Time# = Time# + DeltaBuffer(i)
	Next
	Time# = Time# / 6.0
	; Divide-by-zero guard -- see Client.bb. Sub-millisecond average frame
	; time would yield Inf or a RuntimeError without this clamp.
	If Time# < 1.0 Then Time# = 1.0
	FPS# = 1000.0 / Time#
	Delta# = BaseFramerate# / FPS#
	DeltaTime = MilliSecs()

	; Update screen
	FUI_Update()
	Flip(0)

	; Add quit confirmation dialog
	If ChangesSaved = False Then AppTitle("Realm Crafter Gubbin Tool", "All changes to the current gubbin will be lost. Continue?")

Forever

; Functions --------------------------------------------------------------------------------------------------------------

; Duplicate gubbin mesh name dialog
Function MeshNameDialog$()

	W = FUI_Window(0, 0, 150, 80, "Enter Mesh Name", "", 1, 1)
	TName = FUI_TextBox(W, 10, 5, 130, 20)
	BCancel = FUI_Button(W, 40, 30, 60, 20, "Cancel")
	BDone = FUI_Button(W, 110, 30, 30, 20, "OK")

	FUI_ModalWindow(W)
	FUI_CenterWindow(W)

	; Event loop
	Done = False
	Repeat

		If FUI_SendMessage(TName, M_GETCAPTION) = ""
			FUI_DisableGadget(BDone)
		Else
			FUI_EnableGadget(BDone)
		EndIf

		; Events
		For E.Event = Each Event
			Select E\EventID
				; Window closed
				Case W
					If E\EventData$ = "closed" Then Result$ = "" : Done = True
				; Cancel clicked
				Case BCancel
					Result$ = ""
					Done = True
				; OK clicked
				Case BDone
					Name$ = EditorMeshName$(SelectedMeshID)
					Result$ = GetFolder$(Name$) + FUI_SendMessage(TName, M_GETCAPTION) + GetExtension$(Name$)
					If FileType("Data\Meshes\" + Result$) = 0
						Done = True
					Else
						FUI_CustomMessageBox("A file with that name already exists!", "Error", MB_OK)
					EndIf
			End Select
			Delete E
		Next

		; Render
		FUI_Update()
		Flip()

	Until Done = True

	FlushKeys
	FUI_DeleteGadget(W)
	Return Result$

End Function

; Updates the camera position
Function UpdatePreviewCam()

	PositionEntity PreviewCam, 0, 0, 0
	RotateEntity PreviewCam, PreviewPitch#, PreviewYaw#, 0.0
	MoveEntity PreviewCam, 0.0, 0.0, -PreviewDistance#

End Function

; Changes the preview gubbin mesh
Function SetGubbinMesh(ID)

	OldID = SelectedMeshID
	SelectedMeshID = ID
	FUI_SendMessage(LGubbin, M_SETCAPTION, GetFilename$(EditorMeshName$(SelectedMeshID)))

	; Out with the old
	If PreviewMesh <> 0
		FreeEntity(PreviewMesh)
		UnloadMesh(OldID)
	EndIf

	; And in with the new
	Bone = FindChild(ActorPreview\EN, FUI_SendMessage(CBoneSelected, M_GETCAPTION))
	If Bone <> 0 And SelectedMeshID < 65535 And SelectedMeshID > -1
		PreviewMesh = GetMesh(SelectedMeshID)
		If PreviewMesh <> 0
			TempPosX# = LoadedMeshX#(SelectedMeshID)
			TempPosY# = LoadedMeshY#(SelectedMeshID)
			TempPosZ# = LoadedMeshZ#(SelectedMeshID)
			TempScale# = LoadedMeshScales#(SelectedMeshID)
			
			PositionEntity(PreviewMesh, 0, 0, 0)
			ScaleEntity(PreviewMesh, 1,1,1)
			
			EntityParent(PreviewMesh, Bone, False)
			PositionEntity(PreviewMesh, TempPosX#, TempPosY#, TempPosZ#)
			ScaleEntity(PreviewMesh, TempScale#, TempScale#, TempScale#)
			; Correct rotation for Max models
			If ActorPreview\TeamID = True Then TurnEntity PreviewMesh, 0, 180, 90
		EndIf
	EndIf

End Function

; Changes the preview actor
Function SetActor()

	; Get actor ID
	ID = FUI_SendMessage(FUI_SendMessage(CActorSelected, M_GETSELECTED), M_GETDATA)
	SelectedActor = ActorList(ID)
	If SelectedActor = Null Then RuntimeError("Actor not found!")

	; Free gubbin preview mesh
	If PreviewMesh <> 0
		FreeEntity(PreviewMesh)
		UnloadMesh(SelectedMeshID)
		PreviewMesh = 0
	EndIf

	; Unload previous preview
	If ActorPreview <> Null
		If ActorPreview\Gender = 0
			UnloadMesh(ActorPreview\Actor\MeshIDs[0])
		Else
			UnloadMesh(ActorPreview\Actor\MeshIDs[1])
		EndIf
		SafeFreeActorInstance(ActorPreview)
	EndIf

	; Create new preview
	ActorPreview = CreateActorInstance(SelectedActor)
	If FUI_SendMessage(CGender, M_GETINDEX) = 2
		If SelectedActor\Genders = 0 Then ActorPreview\Gender = 1
	Else
		If SelectedActor\Genders = 2 Then ActorPreview\Gender = 1
	EndIf
	Result = LoadActorInstance3D(ActorPreview, 0.5 / SelectedActor\Scale#, True, False)
	If Result = False
		Delete ActorPreview
		Idx = FUI_SendMessageI(CActorSelected, M_GETINDEX)
		If Idx > 1
			FUI_SendMessage(CActorSelected, M_SETINDEX, Idx - 1)
			SetActor()
		ElseIf SelectedActor <> Last Actor
			FUI_SendMessage(CActorSelected, M_SETINDEX, Idx + 1)
			SetActor()
		Else
			RuntimeError("Actor does not have a mesh!")
		EndIf
		Return
	EndIf
	
	; Update the bone list
	FUI_SendMessage(CBoneSelected,M_RESET)
	
	If FindChild(ActorPreview\EN, "Head") Then FUI_ComboBoxItem(CBoneSelected, "Head")
	If FindChild(ActorPreview\EN, "Chest") Then FUI_ComboBoxItem(CBoneSelected, "Chest")
	If FindChild(ActorPreview\EN, "R_Hand") Then FUI_ComboBoxItem(CBoneSelected, "R_Hand")
	If FindChild(ActorPreview\EN, "L_Hand") Then FUI_ComboBoxItem(CBoneSelected, "L_Hand")
	If FindChild(ActorPreview\EN, "R_Shoulder") Then FUI_ComboBoxItem(CBoneSelected, "R_Shoulder")
	If FindChild(ActorPreview\EN, "L_Shoulder") Then FUI_ComboBoxItem(CBoneSelected, "L_Shoulder")
	If FindChild(ActorPreview\EN, "R_Forearm") Then FUI_ComboBoxItem(CBoneSelected, "R_Forearm")
	If FindChild(ActorPreview\EN, "L_Forearm") Then FUI_ComboBoxItem(CBoneSelected, "L_Forearm")
	If FindChild(ActorPreview\EN, "R_Shin") Then FUI_ComboBoxItem(CBoneSelected, "R_Shin")
	If FindChild(ActorPreview\EN, "L_Shin") Then FUI_ComboBoxItem(CBoneSelected, "L_Shin")
	
	For i = 0 To 5
		If ValidBoneName(GubbinJoints(i)) And FindChild(ActorPreview\EN, GubbinJoints(i)) Then FUI_ComboBoxItem(CBoneSelected, GubbinJoints(i))
	Next
	
	Bone = FindChild(ActorPreview\EN, FUI_SendMessage(CBoneSelected, M_GETCAPTION))
	If Bone <> 0 Then TranslateEntity(ActorPreview\CollisionEN, -EntityX#(Bone, True), -EntityY#(Bone, True), -EntityZ#(Bone, True))
	SetGubbinMesh(SelectedMeshID)

End Function

; Gets a mesh name, strips the information byte, and replaces it with [NONE] if it's invalid
Function EditorMeshName$(ID)

	If ID < 0 Or ID > 65534 Then Return "[NONE]"
	Name$ = MeshNames$(ID)
	If Len(Name$) > 1 Then Return Left$(Name$, Len(Name$) - 1) Else Return "[NONE]" 

End Function

; Returns only the filename from a path
Function GetFilename$(P$)

	For i = Len(P$) To 1 Step -1
		If Mid$(P$, i, 1) = "\" Or Mid$(P$, i, 1) = "/" Then Return Mid$(P$, i + 1)
	Next
	Return P$

End Function

; Returns only the folder from a path
Function GetFolder$(P$)

	For i = Len(P$) To 1 Step -1
		If Mid$(P$, i, 1) = "\" Or Mid$(P$, i, 1) = "/" Then Return Left$(P$, i)
	Next
	Return P$

End Function

; Returns only the file extension from a path
Function GetExtension$(P$)

	For i = Len(P$) To 1 Step -1
		If Mid$(P$, i, 1) = "." Then Return Mid$(P$, i)
	Next
	Return ""

End Function

; Applies all changes
Function SaveAll()

	If PreviewMesh <> 0
		SetMeshOffset(SelectedMeshID, TempPosX#, TempPosY#, TempPosZ#)
		SetMeshScale(SelectedMeshID, TempScale#)
		SaveRotation(SelectedMeshID)
		AppTitle("Realm Crafter Gubbin Tool")
		ChangesSaved = True
	EndIf

End Function

; Updates the B3D file of the current mesh with its rotation.
;
; Note: .eb3d (encrypted Blitz3D) rotation editing is not fully supported
; in this build -- EncryptMesh below is empty, so any rotation save on an
; encrypted mesh writes the rotated vertices to a sibling foo.eb3d.b3d
; file (the DecryptMesh scratch output) and leaves the original .eb3d
; untouched. Callers should treat .eb3d rotation as a no-op until the
; encryption pipeline is restored.
;
; Atomicity (issue #43): the prior implementation opened
; "Data\Meshes\Name$" directly via OpenFile and rewrote vertex chunks
; in place. Any failure (process kill, RuntimeError downstream, the
; user-reported VRTS-walk desync that silently destroys the file) left
; the original mesh truncated or zeroed with no recovery path. The
; mesh is part of the data project and not easily reproducible by the
; user.
;
; The new path: CopyFile the source to a sibling .tmp, mutate the
; .tmp in place using the existing VRTS walker, then SafeWriteCommit
; atomic-promotes the .tmp into production (with the previous version
; retained as .bak). Any failure leaves the original intact. Matches
; the SafeWriteOpen / SafeWriteCommit pattern established by the 2025
; sweep across Server / GUE / RCTE / Tools (Logging.bb).
Function SaveRotation(TargetMeshID)

	Local TName$ = ""
	Local Name$ = ""

	If PreviewMesh <> 0
		Name$ = EditorMeshName$(TargetMeshID)
		If Right$(Upper$(Name$), 3) = "B3D" Then
			If Right$(Upper$(Name$), 5) = ".EB3D" Then
				TName$ = Name$
				DecryptMesh("Data\Meshes\" + Name$)
				Name$ = Name$ + ".b3d"
			End If
		Else
			Return
		End If

		Local FinalPath$ = "Data\Meshes\" + Name$
		Local TempPath$ = SafeWriteOpen$(FinalPath$)
		DebugLog( "Save File: " + FinalPath$ + " (via " + TempPath$ + ")")

		; Stage a working copy. CopyFile returns non-zero on success in
		; Blitz3D; we treat any case where the temp doesn't actually
		; appear on disk as a failure and bail without touching the
		; original.
		CopyFile(FinalPath$, TempPath$)
		If FileType(TempPath$) <> 1
			DebugLog( "SaveRotation: could not stage temp for " + FinalPath$ + " -- save aborted, original preserved")
			Return
		EndIf

		Local F = OpenFile(TempPath$)
		If F = 0
			; CopyFile produced a file but we can't open it. Clean up
			; the orphan so the next save attempt starts from a known
			; state, and leave the original untouched.
			DebugLog( "SaveRotation: could not open temp " + TempPath$ + " for in-place rewrite -- save aborted, original preserved")
			SafeWriteAbort(TempPath$)
			Return
		EndIf

		temp_bank = CreateBank(12)
		GPP = CreatePivot()
		RotateEntity(GPP, EntityPitch#(PreviewMesh), EntityYaw#(PreviewMesh), EntityRoll#(PreviewMesh))
		While Not Eof(F)
			SeekFile(F, fspot)
			fspot = FilePos(F) + 1
			Testr$ = Chr$(ReadByte(F))
			Testr$ = Testr$ + Chr$(ReadByte(F))
			Testr$ = Testr$ + Chr$(ReadByte(F))
			Testr$ = Testr$ + Chr$(ReadByte(F))

			; Found a vertices chunk
			If Testr$ = "VRTS"
				vertend = FilePos(F) + 4 + ReadInt(F)
				Flags = ReadInt(F)
				tc_sets = ReadInt(F)
				tc_size = ReadInt(F)

				; Process each vertex
				While FilePos(F) < vertend
					; Read in floats
					temppos = FilePos(F)
					X# = ReadFloat(F)
					Y# = ReadFloat(F)
					Z# = ReadFloat(F)

					; Put them into the bank
					PokeFloat(temp_bank, 0, X#)
					PokeFloat(temp_bank, 4, Y#)
					PokeFloat(temp_bank, 8, Z#)

					; Decrypt with Blowfish
					;BFOld_decrypt(temp_bank, 12)

					; Rotate
					X# = PeekFloat(temp_bank, 0)
					Y# = PeekFloat(temp_bank, 4)
					Z# = PeekFloat(temp_bank, 8)
					TFormPoint(X#, Y#, Z#, GPP, 0)
					PokeFloat(temp_bank, 0, TFormedX#())
					PokeFloat(temp_bank, 4, TFormedY#())
					PokeFloat(temp_bank, 8, TFormedZ#())

					; Encrypt again
					;BFOld_encrypt(temp_bank, 12)

					; Write new values back to the file
					SeekFile F, temppos
					WriteFloat F, PeekFloat(temp_bank, 0)
					WriteFloat F, PeekFloat(temp_bank, 4)
					WriteFloat F, PeekFloat(temp_bank, 8)

					; Skip unnecessary vertex data
					If Flags And 1
						ReadFloat#(F)
						ReadFloat#(F)
						ReadFloat#(F)
					EndIf
					If Flags And 2
						ReadFloat#(F)
						ReadFloat#(F)
						ReadFloat#(F)
						ReadFloat#(F)
					EndIf
					For j = 1 To tc_sets * tc_size
						ReadFloat#(F)
					Next
				Wend

			EndIf
		Wend
		FreeEntity(GPP)
		FreeBank(temp_bank)

		; SafeWriteCommit closes F, refuses to promote an empty temp,
		; demotes the existing production file to .bak, then promotes
		; the mutated temp into production. On any commit failure the
		; helper logs to MainLog and leaves the original (.bak rollback
		; if the promote half-completed). The previous code called
		; CloseFile here unconditionally -- SafeWriteCommit owns that
		; now, do not double-close.
		If SafeWriteCommit%(TempPath$, FinalPath$, F) = False
			DebugLog( "SaveRotation: commit failed for " + FinalPath$ + " -- previous version retained (see .bak)")
		EndIf
	EndIf


; REMOVE FOR DECRYPT SIREDBLOOD SAYS MUHAHAHA #######################
;
; Re-encrypt path retired: the isEncrypted gate was always False (its
; setter at line 804 was commented out), and EncryptMesh below has been
; an empty function for the lifetime of this tool. The branch only ran
; to delete the just-written .b3d sidecar; without re-encryption the
; .eb3d would have been the stale original anyway. See header comment
; on SaveRotation -- .eb3d rotation is documented as a no-op until the
; encryption pipeline is restored.

	Name$ = EditorMeshName$(TargetMeshID)

;#####################################################################

	SetGubbinMesh(TargetMeshID)

End Function

; Writes a .b3d sidecar next to a source .eb3d. Atomic via
; SafeWriteOpen / SafeWriteCommit so a crash mid-decrypt doesn't
; leave a zero-length sidecar that SaveRotation would then
; "successfully" rewrite into an empty file (the upstream half of
; issue #43).
Function DecryptMesh(Name$)

	If Len(Name$) > 5
		If Lower$(Right$(Name$, 5)) = ".eb3d"
			Size = FileSize(Name$)
			If Size <= 0 Then Return
			B = CreateBank(Size)

			In = ReadFile(Name$)
			If In = 0 Then
				FreeBank(B)
				Return
			EndIf
			ReadBytes(B, In, 0, Size)
			CloseFile(In)

		;	Name$ = Name$ + "temp.b3d"
			Name$ = Name$ + ".b3d"

			Local TempPath$ = SafeWriteOpen$(Name$)
			Out = WriteFile(TempPath$)
			If Out = 0 Then
				FreeBank(B)
				Return
			EndIf

			DebugLog( "Out is: " + Out )
			DecryptBank = CreateBank(64)

			Pos = 0
			Repeat
				If Pos + 64 >= Size Then Exit
				CopyBank(B, Pos, DecryptBank, 0, 64)
				;BF_decrypt(DecryptBank, 64)
				WriteBytes(DecryptBank, Out, 0, 64)
				Pos = Pos + 64
			Forever
			If Pos < Size
				CopyBank(B, Pos, DecryptBank, 0, Size - Pos)
				;BF_decrypt(DecryptBank, 64)
				WriteBytes(DecryptBank, Out, 0, Size - Pos)
			EndIf

			FreeBank(DecryptBank)
			; SafeWriteCommit closes Out internally; do not double-close.
			If SafeWriteCommit%(TempPath$, Name$, Out) = False
				DebugLog( "DecryptMesh: commit failed for " + Name$ + " -- previous sidecar retained (see .bak)")
			EndIf

			FreeBank(B)
		EndIf
	EndIf

End Function

Function EncryptMesh(Name$)

;	If Len(Name$) > 5
;		If Lower$(Right$(Name$, 4)) = ".b3d"
;			Size = FileSize(Name$)
;			B = CreateBank(Size)
;
;			In = ReadFile(Name$)
;			ReadBytes(B, In, 0, Size)
;			CloseFile(In)
;
;			Name$ = Left(Name$, Len(Name$) - 8)
;			Out = WriteFile(Name$)
;			DecryptBank = CreateBank(64)
;
;			Pos = 0
;			Repeat
;				If Pos + 64 >= Size Then Exit
;				CopyBank(B, Pos, DecryptBank, 0, 64)
;				BF_encrypt(DecryptBank, 64)
;				WriteBytes(DecryptBank, Out, 0, 64)
;				Pos = Pos + 64
;			Forever
;			If Pos < Size
;				CopyBank(B, Pos, DecryptBank, 0, Size - Pos)
;				BF_encrypt(DecryptBank, 64)
;				WriteBytes(DecryptBank, Out, 0, Size - Pos)
;			EndIf
;
;			FreeBank(DecryptBank)
;			CloseFile(Out)
;
;			FreeBank(B)
;		EndIf
;	EndIf

End Function 

; checks if a bone is named like a hardcoded bone and returns false in that case
Function ValidBoneName%(name$)
	If name = "Head" Then Return False
	If name = "Chest" Then Return False
	If name = "R_Hand" Then Return False
	If name = "L_Hand" Then Return False
	If name = "R_Shoulder" Then Return False
	If name = "L_Shoulder" Then Return False
	If name = "R_Forearm" Then Return False
	If name = "L_Forearm" Then Return False
	If name = "R_Shin" Then Return False
	If name = "L_Shin" Then Return False
	If name = "R_Thigh" Then Return False
	If name = "L_Thigh" Then Return False
	
		
	Return True
End Function
