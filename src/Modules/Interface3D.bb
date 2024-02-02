;##############################################################################################################################
; Realm Crafter version 1.10
; Copyright (C) 2007 Solstar Games, LLC. All rights reserved
; contact@solstargames.com
; #
; Programmer: Rob Williams
; Program: Realm Crafter Actors module
;
;This is a licensed product:
;BY USING THIS SOURCECODE, YOU ARE CONFIRMING YOUR ACCEPTANCE OF THE SOFTWARE AND AGREEING TO BECOME BOUND BY THE TERMS OF
;THIS AGREEMENT. IF YOU DO NOT AGREE TO BE BOUND BY THESE TERMS, THEN DO NOT USE THE SOFTWARE.
;
;Licensee may NOT:
; (i) create any derivative works of the Engine, including translations Or localizations, other than Games;
; (ii) redistribute, encumber, sell, rent, lease, sublicense, Or otherwise transfer rights To the Engine; or
; (iii) remove Or alter any trademark, logo, copyright Or other proprietary notices, legends, symbols Or labels in the Engine.
; (iv) licensee may Not distribute the source code Or documentation To the engine in any manner, unless recipient also has a
; license To the Engine.
; (v) use the Software to develop any software or other technology having the same primary function as the Software,
; including but not limited to using the Software in any development or test procedure that seeks to develop like
; software or other technology, or to determine if such software or other technology performs in a similar manner as the
; Software
;##############################################################################################################################
; Realm Crafter Interface 3D module by Rob W (rottbott@hotmail.com), April 2005

; Alphabetically sorted list of abilities
Dim KnownSpellSort(999)

; Creates a text input window and returns the handle
Function CreateTextInput(Title$, Prompt$, Numeric, ScriptHandle)

	TI.TextInput = New TextInput
	TI\ScriptHandle = ScriptHandle

	TI\Win = GY_CreateWindow(Title$, 0.3, 0.55, 0.5, 0.17, True, False, True)
	PromptLabel = GY_CreateLabel(TI\Win, 0.05, 0.2, Prompt$)
	Gad.GY_Gadget = Object.GY_Gadget(PromptLabel)
	Width# = GY_TextWidth#(Gad\EN, Prompt$)
	TI\TextBox = GY_CreateTextField(TI\Win, Width# + 0.1, 0.2, 0.85 - Width#, Numeric, 150)
	TI\AcceptButton = GY_CreateButton(TI\Win, 0.75, 0.7, 0.2, 0.18, LanguageString$(LS_Accept))

	GY_GadgetAlpha(TI\Win, 1.0)
	;GY_GadgetAlpha(TI\Win, 0.75)
	GY_ActivateWindow(TI\Win)
	GY_ActivateTextField(TI\TextBox)
	InDialog = True

	Return Handle(TI)

End Function

; Frees a text input dialog and all gadgets
Function FreeTextInput(Han)

	TI.TextInput = Object.TextInput(Han)
	If TI <> Null
		GY_FreeGadget(TI\Win)
		Delete(TI)
	EndIf

End Function

; Creates a dialog window and returns the handle
Function CreateDialog(Title$, A.ActorInstance, ScriptHandle, BackgroundTexID = 65535)

	; Play speech if applicable
	If A <> Null Then PlayActorSound(A, Rand(Speech_Greet1, Speech_Greet2))

	; Create dialog
	D.Dialog = New Dialog
	D\ActorInstance = A
	D\ScriptHandle = ScriptHandle
	If BackgroundTexID < 65535
		D\Win = GY_CreateWindow(Title$, 0.02, 0.15, 0.32, 0.5, True, False, False, GetTexture(BackgroundTexID), False)
	Else
		D\Win = GY_CreateWindow(Title$, 0.02, 0.15, 0.32, 0.5, True, False, False)
	EndIf
	Y# = 0.07
	For i = 0 To 13
		D\TextLines[i] = GY_CreateLabel(D\Win, 0.02, Y#, String$(" ", 75)) ;75
		GY_UpdateLabel(D\TextLines[i], "")
		GY_DropGadget(D\TextLines[i])
		Y# = Y# + 0.05
	Next
	GY_GadgetAlpha(D\Win, 1.0) ;0.75
	GY_ActivateWindow(D\Win)
	InDialog = True

	Return Handle(D)

End Function

; Frees a dialog and everything in it
Function FreeDialog(Han)

	D.Dialog = Object.Dialog(Han)
	If D <> Null

		; Play speech if applicable
		If D\ActorInstance <> Null Then PlayActorSound(D\ActorInstance, Rand(Speech_Bye1, Speech_Bye2))

		; Free dialog
		GY_FreeGadget(D\Win)
		Delete(D)

	EndIf

End Function

; Adds an option to a dialog
Function AddDialogOption(Han, Opt$)

	D.Dialog = Object.Dialog(Han)
	If D <> Null
		D\TotalOptions = D\TotalOptions + 1
		DialogOutput(Han, Opt$, 0, 255, 0, D\TotalOptions)
	EndIf

End Function

; Adds text to a dialog
Function DialogOutput(Han, T$, R = 255, G = 255, B = 255, Opt = 0)

	D.Dialog = Object.Dialog(Han)

	; Word wrap
	Gad.GY_Gadget = Object.GY_Gadget(D\TextLines[0])
	If GY_TextWidth#(Gad\EN, T$) >= 0.4
		For i = Len(T$) To 1 Step -1
			If Mid$(T$, i, 1) = " "
				If GY_TextWidth#(Gad\EN, Left$(T$, i - 1)) < 0.4
					DialogOutput(Han, Left$(T$, i - 1), R, G, B, Opt)
					DialogOutput(Han, Mid$(T$, i + 1), R, G, B, Opt)
					Return
				EndIf
			EndIf
		Next
		For i = Len(T$) To 1 Step -1
			If GY_TextWidth#(Gad\EN, Left$(T$, i - 1)) < 0.4
				DialogOutput(Han, Left$(T$, i - 1), R, G, B, Opt)
				DialogOutput(Han, Mid$(T$, i), R, G, B, Opt)
				Return
			EndIf
		Next
		Return
	EndIf

	; Add the line
	If D <> Null
		If D\LastLine <= 13
			GY_UpdateLabel(D\TextLines[D\LastLine], T$, R, G, B)
			D\TextText$[D\LastLine] = T$
			D\TextR[D\LastLine] = R
			D\TextG[D\LastLine] = G
			D\TextB[D\LastLine] = B
			D\OptionNum[D\LastLine] = Opt
			D\LastLine = D\LastLine + 1
		Else
			For i = 0 To 12
				GY_UpdateLabel(D\TextLines[i], D\TextText$[i + 1], D\TextR[i + 1], D\TextG[i + 1], D\TextB[i + 1])
				D\TextText$[i] = D\TextText$[i + 1]
				D\TextR[i] = D\TextR[i + 1]
				D\TextG[i] = D\TextG[i + 1]
				D\TextB[i] = D\TextB[i + 1]
				D\OptionNum[i] = D\OptionNum[i + 1]
			Next
			GY_UpdateLabel(D\TextLines[13], T$, R, G, B)
			D\TextText$[13] = T$
			D\TextR[13] = R
			D\TextG[13] = G
			D\TextB[13] = B
			D\OptionNum[13] = Opt
		EndIf
	EndIf

End Function

; Adds a new chat message
Function Output(Dat$, R = 255, G = 255, B = 255)

	; Word wrap
	MaxWidth# = Chat\Width#
	If Chat\X# <= 0.5 Then MaxWidth# = MaxWidth# - 0.035
	Gad.GY_Gadget = Object.GY_Gadget(ChatLines(0))
	If GY_TextWidth#(Gad\EN, Dat$) >= MaxWidth#
		For i = Len(Dat$) To 1 Step -1
			If Mid$(Dat$, i, 1) = " "
				If GY_TextWidth#(Gad\EN, Left$(Dat$, i - 1)) <= MaxWidth#
					Output(Left$(Dat$, i - 1), R, G, B)
					Output(Mid$(Dat$, i + 1), R, G, B)
					Return
				EndIf
			EndIf
		Next
		For i = Len(Dat$) To 1 Step -1
			If GY_TextWidth#(Gad\EN, Left$(Dat$, i - 1)) <= MaxWidth#
				Output(Left$(Dat$, i - 1), R, G, B)
				Output(Mid$(Dat$, i), R, G, B)
				Return
			EndIf
		Next
		Return
	EndIf

	; Add to history
	If MaxHistoryLine < 1999
		MaxHistoryLine = MaxHistoryLine + 1
	Else
		For i = 0 To 1998
			ChatHistory$(i) = ChatHistory$(i + 1)
			ChatHistoryColour(i) = ChatHistoryColour(i + 1)
		Next
	EndIf
	ChatHistory$(MaxHistoryLine) = Dat$
	ChatHistoryColour(MaxHistoryLine) = R Or (G Shl 8) Or (B Shl 16)

	; Add to current chat text
	CC.CurrentChat = New CurrentChat
	CC\Dat$ = Dat$
	CC\cR = R
	CC\cG = G
	CC\cB = B
	CC\Timer = MilliSecs()

	; Display
	UpdateChatTextDisplay()

End Function

; Creates a new chat bubble
Dim BubbleLines$(9)
Dim BubbleLinesEN(9)
Function BubbleOutput(Label$, R, G, B, AI.ActorInstance, NoText = False)

	; Normal output
	If NoText = False
		Name$ = Trim$(AI\Name$)
		If Name$ = "" Then Name$ = AI\Actor\Race$
		If R = 0 And G = 0 And B = 0
			Output("<" + Name$ + "> " + Label$, 255, 255, 255)
		Else
			Output("<" + Name$ + "> " + Label$, R, G, B)
		EndIf
	EndIf

	; Remove any chat bubbles already attached to this actor instance
	For Bb.Bubble = Each Bubble
		If Bb\ActorInstance = AI
			FreeEntity(Bb\EN)
			Delete(Bb)
		EndIf
	Next

	; Create new bubble
	Bb.Bubble = New Bubble
	Bb\EN = CopyMesh(ChatBubbleEN)
	EntityTexture(Bb\EN, ChatBubbleTex)
	EntityFX(Bb\EN, 1 + 8)
	EntityAutoFade(Bb\EN, 30.0, 35.0)
	Bb\ActorInstance = AI
	Bb\Timer = MilliSecs()

	; Split text into lines
	BubbleLines$(0) = Label$
	TotalLines = 1
	Pos = WordWrap(Label$, 30)
	While Pos > 0
		BubbleLines$(TotalLines - 1) = Left$(Label$, Pos)
		If TotalLines = 10
			BubbleLines$(9) = Left$(BubbleLines$(9), Len(BubbleLines$(9)) - 3) + "..."
			Exit
		EndIf
		Label$ = Mid$(Label$, Pos + 1)
		BubbleLines$(TotalLines) = Label$
		TotalLines = TotalLines + 1
		Pos = WordWrap(Label$, 30)
	Wend
	Bb\Height# = 0.2 + (Float#(TotalLines) * 0.36)

	; Create text
	MaxWidth# = 0.0
	For i = 0 To TotalLines - 1
		BubbleLinesEN(i) = GY_Create3DText(0.0, 0.0, Len(BubbleLines$(i)) / 2.9, 1.0, Len(BubbleLines$(i)), ChatBubbleFont, 0, 0)
		GY_Set3DText(BubbleLinesEN(i), BubbleLines$(i))
		EntityAutoFade(BubbleLinesEN(i), 30.0, 35.0)
		EntityColor(BubbleLinesEN(i), R, G, B)
		EntityParent(BubbleLinesEN(i), Bb\EN, False)
		Width# = GY_TextWidth#(BubbleLinesEN(i), BubbleLines$(i))
		If Width# > MaxWidth# Then MaxWidth# = Width#
	Next
	Bb\Width# = MaxWidth#

	; Position and scale bubble
	Surf = GetSurface(Bb\EN, 1)
	BorderX# = 0.15 / Bb\Width#
	BorderY# = 0.15 / Bb\Height#
	BorderB# = 0.28 / Bb\Height#
	VertexCoords(Surf, 0, -BorderX#,      -(1.0 + BorderY#), 0.0)
	VertexCoords(Surf, 1, 0.0,            -(1.0 + BorderY#), 0.0)
	VertexCoords(Surf, 2, 1.0,            -(1.0 + BorderY#), 0.0)
	VertexCoords(Surf, 3, 1.0 + BorderX#, -(1.0 + BorderY#), 0.0)
	VertexCoords(Surf, 4, -BorderX#,      -1.0, 0.0)
	VertexCoords(Surf, 7, 1.0 + BorderX#, -1.0, 0.0)
	VertexCoords(Surf, 8, -BorderX#,       0.0, 0.0)
	VertexCoords(Surf, 11, 1.0 + BorderX#, 0.0, 0.0)
	VertexCoords(Surf, 12, -BorderX#,      BorderB#, 0.0)
	VertexCoords(Surf, 13, 0.0,            BorderB#, 0.0)
	VertexCoords(Surf, 14, 1.0,            BorderB#, 0.0)
	VertexCoords(Surf, 15, 1.0 + BorderX#, BorderB#, 0.0)
	ScaleEntity(Bb\EN, -Bb\Width#, -Bb\Height#, 1, True)

	; Position and scale text lines
	For i = 0 To TotalLines - 1
		ScaleEntity(BubbleLinesEN(i), Len(BubbleLines$(i)) / -2.9, 0.36, 1, True)
		PositionEntity(BubbleLinesEN(i), 0, -1.0, 0)
		TranslateEntity(BubbleLinesEN(i), 0, Float#(i) * -0.36, 0.05, True)
	Next

End Function

; Updates all labels in the chat text display
;Turns chat text in history mode to white Cysis145
Function UpdateChatTextDisplay()

	; Draw history
	If HistoryMode
		CurrentLine = FirstHistoryLine
		For i = 0 To MaxChatLine
			R = ChatHistoryColour(CurrentLine) And 255
			G = (ChatHistoryColour(CurrentLine) Shr 8) And 255
			B = (ChatHistoryColour(CurrentLine) Shr 16) And 255
			GY_UpdateLabel(ChatLines(i), ChatHistory$(CurrentLine), R, G, B) ;R, G, B)
			CurrentLine = CurrentLine + 1
			If CurrentLine = MaxHistoryLine + 1
				For j = i + 1 To MaxChatLine
					GY_UpdateLabel(ChatLines(j), "")
				Next
				Exit
			EndIf
			If CurrentLine > 1999 Then Return
		Next
	; Draw current messages
	Else
		Count = 0
		For CC.CurrentChat = Each CurrentChat
			GY_UpdateLabel(ChatLines(Count), CC\Dat$, CC\cR, CC\cG, CC\cB)
			Count = Count + 1
			If Count > MaxChatLine Then Return
		Next
		For i = Count To MaxChatLine
			GY_UpdateLabel(ChatLines(i), "")
		Next
	EndIf

End Function

; Selects the player target from the entity clicked on
Function GetTarget$(EN)

	; Is it an actor instance?
	AI.ActorInstance = Object.ActorInstance(EntityName$(EN))
	If AI <> Null Then Return "A"

	; Is it a dropped item?
	D.DroppedItem = Object.DroppedItem(EntityName$(EN))
	If D <> Null Then Return "D"

	; Must be scenery
	Return ""

End Function

; Updates the standard components and handles player input
Function UpdateInterface()

	; Gooey system
	GY_Update()

	; Screenshot
	If KeyHit(74)
		Num = 1
		While FileType("Screenshot " + Str$(Num) + ".bmp") > 0
			Num = Num + 1
		Wend
		SaveBuffer(BackBuffer(), "Screenshot " + Str$(Num) + ".bmp")
	EndIf

	; Escape used both to close windows and quit the game
	EscapeHit = KeyHit(1)

	; Toggle always run mode
	If ControlHit(Key_AlwaysRun) Then AlwaysRun = Not AlwaysRun

	; Cycle target
	If ControlHit(Key_CycleTarget)
		StartAI.ActorInstance = Object.ActorInstance(PlayerTarget)
		If StartAI = Null Then StartAI = First ActorInstance
		AI.ActorInstance = StartAI
		Repeat
			AI = After AI
			If AI = Null Then AI = First ActorInstance
			If AI = StartAI Then Exit
			If AI\Actor\Aggressiveness < 3 And AI\RNID = 0
				OldTarget = PlayerTarget
				PlayerTarget = Handle(AI)
				MaxLength# = MeshWidth#(AI\EN)
				If MeshDepth#(AI\EN) > MaxLength# Then MaxLength# = (MeshDepth#(AI\EN) + MeshWidth#(AI\EN)) / 2.0
				ScaleEntity(ActorSelectEN, MaxLength# * 0.03, 1.0, MaxLength# * 0.03)
				ShowEntity(ActorSelectEN)
				AttackTarget = False
				Exit
			EndIf
		Forever
	EndIf

	; Hide movement marker after time expires {@@@~}
	If MilliSecs() - ClickMarkerTimer > 3000 Then HideEntity(ClickMarkerEN)
	
	;[~~~] automatic rotation behind character if mouse button 3 is hit cysis145
	If MouseHit(3) = True
		CamYaw# = EntityYaw#(Me\CollisionEN) + 180
		CamPitch# = 0.0
	EndIf
	
	
	; Get environment type
	EType = Me\Actor\Environment
	If Me\Mount <> Null Then EType = Me\Mount\Actor\Environment

	; Keyboard controls
	If ChatEntry\Alpha# < 0.5 And First TextInput = Null
		; Jump
		If ControlHit(Key_Jump) And PlayerHasTouchedDown = True
			If Me\Mount = Null
				Me\Y# = JumpStrength# * Gravity#
				PlayAnimation(Me, 3, 0.05, Anim_Jump)
				PlayerHasTouchedDown = False
				RCE_Send(Connection, PeerToHost, P_Jump, "", True)
			EndIf
		EndIf
		; Change camera mode
		If ControlHit(Key_ChangeViewMode) And ViewMode = 2
			CamMode = Not CamMode
			CamYaw# = 0.0
			CamPitch# = 0.0
		EndIf
		; Walk around / Fixed Bug where the player could still move once dead Cysis145
		If InDialog = False And SMemorising = 0 And Me\Attributes\Value[HealthStat] > 0
		
		; Forward [~~~]
			If ControlDown(Key_Forward)
				If Me\WalkingBackward = True
 					If Me\Mount = Null
						If CurrentSeq(Me) = Anim_Walk Then Animate(Me\EN, 0)
					Else
						If CurrentSeq(Me\Mount) = Anim_Walk Then Animate(Me\Mount\EN, 0)
					EndIf
				EndIf
				Me\IsRunning = ControlDown(Key_Run) Or AlwaysRun
				NewX# = EntityX#(Me\CollisionEN) + (Sin#(EntityYaw#(Me\CollisionEN)) * (KeyboardMoveDistance# * (1.0 + Float#(Me\IsRunning))))
				NewZ# = EntityZ#(Me\CollisionEN) - (Cos#(EntityYaw#(Me\CollisionEN)) * (KeyboardMoveDistance# * (1.0 + Float#(Me\IsRunning))))
				SetDestination(Me, NewX#, NewZ#, EntityY#(Me\CollisionEN))
				;If EnergyStat > -1 And Me\Mount = Null
				;	If Me\Attributes\Value[EnergyStat] <= 0 Then Me\IsRunning = False
				;EndIf
				AttackTarget = False
								
			; Backward [~~~]
			ElseIf ControlDown(Key_Back)
				
				If Me\WalkingBackward = False
 					If Me\Mount = Null
						If CurrentSeq(Me) = Anim_Walk Then Animate(Me\EN, 0)
					Else
						If CurrentSeq(Me\Mount) = Anim_Walk Then Animate(Me\Mount\EN, 0)
					EndIf
				EndIf
			
				Me\IsRunning = False ;ControlDown(Key_Run) Or AlwaysRun
				NewX# = EntityX#(Me\CollisionEN) + (Sin#(EntityYaw#(Me\CollisionEN)) * (-KeyboardMoveDistance#))
				NewZ# = EntityZ#(Me\CollisionEN) - (Cos#(EntityYaw#(Me\CollisionEN)) * (-KeyboardMoveDistance#))
				SetDestination(Me, NewX#, NewZ#, EntityY#(Me\CollisionEN))
				Me\WalkingBackward = True
				
				If Me\Mount <> Null Then Me\Mount\WalkingBackward = True
				AttackTarget = False
				
			EndIf
			; Turn right [{}] straffing code goes here.
			If ControlDown(Key_TurnRight)
				PositionEntity(GPP, Me\DestX#, EntityY#(Me\CollisionEN), Me\DestZ#)
				EntityParent(GPP, Me\CollisionEN)
				TurnEntity(Me\CollisionEN, 0, -3.0 * Delta#, 0)
				EntityParent(GPP, 0)
				WasBack = Me\WalkingBackward
				SetDestination(Me, EntityX#(GPP), EntityZ#(GPP), EntityY#(GPP))
				Me\WalkingBackward = WasBack
				If Me\Mount <> Null
					Me\Mount\WalkingBackward = WasBack
					RotateEntity(Me\Mount\CollisionEN, 0, EntityYaw#(Me\CollisionEN), 0)
				EndIf
								
			; Turn left
			ElseIf ControlDown(Key_TurnLeft)
				PositionEntity(GPP, Me\DestX#, EntityY#(Me\CollisionEN), Me\DestZ#)
				EntityParent(GPP, Me\CollisionEN)
				TurnEntity(Me\CollisionEN, 0, 3.0 * Delta#, 0)
				EntityParent(GPP, 0)
				WasBack = Me\WalkingBackward
				SetDestination(Me, EntityX#(GPP), EntityZ#(GPP), EntityY#(GPP))
				Me\WalkingBackward = WasBack
				If Me\Mount <> Null
					Me\Mount\WalkingBackward = WasBack
					RotateEntity(Me\Mount\CollisionEN, 0, EntityYaw#(Me\CollisionEN), 0)
				EndIf
			EndIf
		EndIf

		; Breath bar ;disabled Cysis145 
		;If BreathStat > -1 And Me\Actor\Environment <> Environment_Swim
		;	If AttributeDisplays(BreathStat)\Component <> 0
		;		If Me\Underwater <> 0
		;			GY_GadgetAlpha(AttributeDisplays(BreathStat)\Component, AttributeDisplays(BreathStat)\Alpha#)
		;			GY_GadgetAlpha(AttributeDisplayNumbers(BreathStat), 1.0)
		;		Else
		;			GY_GadgetAlpha(AttributeDisplays(BreathStat)\Component, 0.0)
		;			GY_GadgetAlpha(AttributeDisplayNumbers(BreathStat), 0.0)
		;		EndIf
		;	EndIf
		;EndIf

		; Flying/swimming
		If EType = Environment_Fly Or Me\Underwater <> 0
			; Up
			If ControlDown(Key_FlyUp)
				If Me\Mount = Null
					TranslateEntity(Me\CollisionEN, 0.0, 0.5 * Delta#, 0.0)
				Else
					TranslateEntity(Me\Mount\CollisionEN, 0.0, 0.5 * Delta#, 0.0)
				EndIf

				; Do not allow above water surface if swimming
				If Me\Underwater <> 0
					W.Water = Object.Water(Me\Underwater)
					If EntityY#(Me\CollisionEN) > EntityY#(W\EN) - 0.5
						PositionEntity(Me\CollisionEN, EntityX#(Me\CollisionEN), EntityY#(W\EN) - 0.505, EntityZ#(Me\CollisionEN))
					EndIf
				EndIf
				QuitActive = False
			; Down
			ElseIf ControlDown(Key_FlyDown)
				; Do not allow below water surface if flying
				Allowed = True
				If Me\Underwater = 0
					For W.Water = Each Water
						If EntityY#(Me\CollisionEN) <= EntityY#(W\EN) + 3.0
							If Abs(EntityX#(Me\CollisionEN) - EntityX#(W\EN)) < (W\ScaleX# / 2.0)
								If Abs(EntityZ#(Me\CollisionEN) - EntityZ#(W\EN)) < (W\ScaleZ# / 2.0)
									Allowed = False
									Exit
								EndIf
							EndIf
						EndIf
					Next
				EndIf
				If Allowed = True
					If Me\Mount = Null
						TranslateEntity(Me\CollisionEN, 0.0, -0.5 * Delta#, 0.0)
					Else
						TranslateEntity(Me\Mount\CollisionEN, 0.0, -0.5 * Delta#, 0.0)
					EndIf
					QuitActive = False
				EndIf
			EndIf
		EndIf
	EndIf

	; Mouselook 
		If MouseDown(2)
		; Look left/right in first person mode (rotate character)
		If CamMode = 1
			PositionEntity(GPP, Me\DestX#, EntityY#(Me\CollisionEN), Me\DestZ#)
			EntityParent(GPP, Me\CollisionEN)
			TurnEntity(Me\CollisionEN, 0, Float#(-GY_MouseXSpeed) * Delta#, 0)
			EntityParent(GPP, 0)
			SetDestination(Me, EntityX#(GPP), EntityZ#(GPP), EntityY#(GPP))
			Invert# = InvertAxis1
			
			;PositionEntity(GPP, Me\DestX#, EntityY#(Me\CollisionEN), Me\DestZ#)
			;	EntityParent(GPP, Me\CollisionEN)
			;	TurnEntity(Me\CollisionEN, 0, Float#(-GY_MouseXSpeed) * 0.3, 0) ;Delta#, 0)
			;	EntityParent(GPP, 0)
			;	
			;	If Me\WalkingBackward = False
			;		SetDestination(Me, EntityX#(GPP), EntityZ#(GPP), EntityY#(GPP))
			;	EndIf
			;	Invert# = InvertAxis1

		; Look left/right in third person mode (rotate camera)
		Else
			CamYaw# = CamYaw# - (Float#(GY_MouseXSpeed) * Delta#)
			If CamYaw# < -180.0
				CamYaw# = CamYaw# + 360.0
			ElseIf CamYaw# > 180.0
				CamYaw# = CamYaw# - 360.0
			EndIf
			Invert# = InvertAxis3
		EndIf
		
		; Look up/down
		CamPitch# = CamPitch# - (Float#(GY_MouseYSpeed) * Delta# * Invert#)
	    If CamPitch# > 85.0
			CamPitch# = 85.0
		ElseIf CamPitch# < -70.0
			CamPitch# = -70.0
		EndIf
	EndIf

	; Camera zoom controls
	; Mousewheel [~~~]
	MZSpeed = MouseZSpeed()
	If MZSpeed <> 0
		CamDist# = CamDist# - (Float#(MZSpeed) * 1.5); * Delta#)
		If CamDist# < 5.0 Then CamDist# = 5.0 ;5.0
		If CamDist# > 25.0 Then CamDist# = 25.0
	EndIf
	; Keyboard
	If ControlDown(Key_CameraIn)
		CamDist# = CamDist# - Delta#
		If CamDist# < 3.0 Then CamDist# = 3.0
	ElseIf ControlDown(Key_CameraOut)
		CamDist# = CamDist# + Delta#
		If CamDist# > 25.0 Then CamDist# = 25.0
	EndIf

	; Update these buttons if the mouse is not over a dialog or the action bar
	If GY_MouseOverGadget = False And GY_MouseY# < 0.85 And (CurrentSeq(Me) >= Anim_LookRound Or Animating(Me\EN) = False) And InDialog = False And SMemorising = 0
		; Talk to button down, remember the time (so we ignore presses which take more than 500ms)
		If ControlDown(Key_TalkTo)
			If RightWasDown = False Then RightDownTime = MilliSecs()
			RightWasDown = True
		Else
			RightWasDown = False
		EndIf
		; Talk to button was clicked
		If ControlHit(Key_TalkTo) And TradingVisible = False And MilliSecs() - RightDownTime < 500
			ClickedTarget = False
			; Selected target actor, show interaction window
			If PlayerTarget > 0
				AI.ActorInstance = Object.ActorInstance(PlayerTarget)
				
				If CharInteractVisible
					UpdateCharInteractionWindow()
				Else
					CreateCharInteractionWindow( AI )
					;CysisLibs Outline
					;Outline ActorSelectEN
				EndIf
				If EntityDistance#(AI\CollisionEN, Me\CollisionEN) < InteractRange
					RCE_Send(Connection, PeerToHost, P_RightClick, RCE_StrFromInt$(AI\RuntimeID, 2), True)
				EndIf
				QuitActive = False
				ClickedTarget = True
			EndIf
			If ClickedTarget = False
				SetPickModes(-1, 3, True)
				Result = CameraPick(Cam, MouseX(), MouseY())
				If Result <> 0
					Target$ = GetTarget$(Result)
					; Target is an actor
					If Target$ = "A"
						PlayerTarget = EntityName$(Result)
						AI.ActorInstance = Object.ActorInstance(PlayerTarget)
						MaxLength# = MeshWidth#(AI\EN)
						If MeshDepth#(AI\EN) > MaxLength# Then MaxLength# = (MeshDepth#(AI\EN) + MeshWidth#(AI\EN)) / 2.0
						ScaleEntity(ActorSelectEN, MaxLength# * 0.03, 1.0, MaxLength# * 0.03)
						ShowEntity(ActorSelectEN)
						If CharInteractVisible
							UpdateCharInteractionWindow()
						ElseIf CharInteract = Null
							CreateCharInteractionWindow(AI)
							;CysisLibs Outline
							;Outline ActorSelectEN
						EndIf
						; If friendly and in range, close interact window and execute right click
						If AI\FactionRatings[Me\HomeFaction] > 99 And EntityDistance#(AI\CollisionEN, Me\CollisionEN) < InteractRange
							RCE_Send(Connection, PeerToHost, P_RightClick, RCE_StrFromInt$(AI\RuntimeID, 2), True)
						EndIf
						QuitActive = False
					EndIf
				EndIf
			EndIf
		EndIf
		
		; Select target (actor, dropped item or usable scenery)
		UsedClick = False
		If ControlDown(Key_Select)
			SelectKeyWasDown = True
		ElseIf SelectKeyWasDown = True
			; Was this a double click?
			IsDouble = False
			If MilliSecs() - SelectKeyClickTime < 500 Then IsDouble = True
			

			; Store time of click
			SelectKeyClickTime = MilliSecs()
			SelectKeyWasDown = False

			; Pick a target
			SetPickModes(0, 3, True, 1)
			Result = CameraPick(Cam, MouseX(), MouseY())
			If Result <> 0
				Target$ = GetTarget$(Result)
				; Target is another actor
				If Target$ = "A"
					UsedClick = True

					; Single or double click selects the target
					OldTarget = PlayerTarget
					PlayerTarget = EntityName$(Result)
					AI.ActorInstance = Object.ActorInstance(PlayerTarget)
					MaxLength# = MeshWidth#(AI\EN)
					If MeshDepth#(AI\EN) > MaxLength# Then MaxLength# = (MeshDepth#(AI\EN) + MeshWidth#(AI\EN)) / 2.0
					ScaleEntity(ActorSelectEN, MaxLength# * 0.03, 1.0, MaxLength# * 0.03)
					ShowEntity(ActorSelectEN)

					; Double clicking the target makes you run towards it and attack if in range [~~~]
					If IsDouble = True And OldTarget = PlayerTarget
						SetDestination(Me, PickedX#(), PickedZ#(), PickedY#())
						CamYaw# = 0.0
						CamPitch# = 0.0
						Me\IsRunning = True
						If Me\Mount <> Null Then Me\Mount\IsRunning = True
						
						; Disable double-clicking for attacks [ref14] ****************************
						
						; Check target is a combatant
						If AI\Actor\Aggressiveness < 3
							 ;Check faction rating
							If Me\FactionRatings[AI\HomeFaction] <= 150 Then AttackTarget = True
						EndIf
						;*************************************************************************
					Else
						If CharInteractVisible
							UpdateCharInteractionWindow()
						ElseIf CharInteract = Null
							CreateCharInteractionWindow(AI)
							;CysisLibs Outline
							;Outline ActorSelectEN
						EndIf
						If AI\FactionRatings[Me\HomeFaction] > 99 And EntityDistance#(AI\CollisionEN, Me\CollisionEN) < InteractRange
							RCE_Send(Connection, PeerToHost, P_RightClick, RCE_StrFromInt$(AI\RuntimeID, 2), True)
						EndIf
						AttackTarget = False
					EndIf
					;HideEntity(ClickMarkerEN) ;{@@@~}
				; Target is a dropped item
				ElseIf Target$ = "D"
					; In range - pick it up if room in inventory
					If EntityDistance#(Result, Me\EN) < 25.0
						UsedClick = True

						DItem.DroppedItem = Object.DroppedItem(EntityName$(Result))
						FoundSlot = -1

						For i = 0 To Slots_Inventory
							If Me\Inventory\Items[i] = Null
								If DItem\Item <> Null
									If SlotsMatch(DItem\Item\Item, i) And ActorHasSlot(Me\Actor, i, DItem\Item\Item) Then FoundSlot = i : Exit
								EndIf
							ElseIf (ItemInstancesIdentical(DItem\Item, Me\Inventory\Items[i]) And  DItem\Item <> Null And i >= SlotI_Backpack)
								If DItem\Item\Item\Stackable = True
									If SlotsMatch(DItem\Item\Item, i) And ActorHasSlot(Me\Actor, i, DItem\Item\Item) Then FoundSlot = i : Exit
								EndIf
							EndIf
						Next
						; Room, request it from server
						If FoundSlot > -1
							RCE_Send(Connection, PeerToHost, P_InventoryUpdate, "P" + RCE_StrFromInt$(DItem\ServerHandle, 4) + RCE_StrFromInt$(FoundSlot, 1), True)
						; No room!
						Else
							Output(LanguageString$(LS_NoInventorySpace), 255, 0, 0)
						EndIf
					EndIf
				; Target is scenery
				ElseIf Target$ = ""
					; If I am riding a mount, get off
					If Me\Mount <> Null
						RCE_Send(Connection, PeerToHost, P_Dismount, "", True)
					; Otherwise check if I can use this scenery
					Else
						Sc.Scenery = Object.Scenery(EntityName$(Result))
						If Sc <> Null
							PositionEntity GPP, PickedX#(), PickedY#(), PickedZ#()
							If EntityDistance#(Me\CollisionEN, GPP) < 10.0
								UsedClick = True

								; Animate now
								If Sc\AnimationMode = 3 ;And Sc\SceneryID = 0 {##}
									If AnimTime(Sc\EN) > 0.0
										Animate(Sc\EN, 3, -1.0, 0, 1.0)
									Else
										Animate(Sc\EN, 3, 1.0, 0, 1.0)
									EndIf
								EndIf
								; Ask server whether I own this scenery {##}
								If Sc\SceneryID > 0
									RCE_Send(Connection, PeerToHost, P_SelectScenery, RCE_StrFromInt$(Sc\SceneryID, 2) + RCE_StrFromInt$(Handle(Sc), 4), True)
								EndIf
							EndIf
						EndIf
					EndIf
				EndIf
			EndIf
		EndIf

		; Move-to button is being held down {@@@~}
		If ControlDown(Key_MoveTo)
			MouseWasDown = True
			; If the mouse still on the same scenery target, continue updating player destination
			If InDialog = False And SMemorising = 0
				SetPickModes(0, 3, True)
				Result = CameraPick(Cam, MouseX(), MouseY())
				If Result <> 0
					Target$ = GetTarget$(Result)
					If Target$ = ""
						SetDestination(Me, PickedX#(), PickedZ#(), PickedY#())
						If Target$ = ""
							ShowEntity(ClickMarkerEN)
							ClickMarkerTimer = MilliSecs()
							PositionEntity(ClickMarkerEN, PickedX#(), PickedY#(), PickedZ#())
							AlignToVector(ClickMarkerEN, PickedNX#(), PickedNY#(), PickedNZ#(), 2)
							MoveEntity(ClickMarkerEN, 0, 0.085, 0)
						EndIf
						CamYaw# = 0.0
						CamPitch# = 0.0
						; If in first person view, "compress" angle to destination
						If CamMode = 1
							PositionEntity GPP, Me\DestX#, EntityY#(Me\CollisionEN), Me\DestZ#
							Dist# = EntityDistance#(Me\CollisionEN, GPP)
							If Abs(GY_MouseX# - 0.5) < 0.15 Then Angle# = 0.0 Else Angle# = DeltaYaw#(Me\CollisionEN, GPP) / 75.0
							PositionEntity GPP, EntityX#(Me\CollisionEN), EntityY#(Me\CollisionEN), EntityZ#(Me\CollisionEN)
							RotateEntity GPP, 0.0, (EntityYaw#(Me\CollisionEN) - Angle#) + 180.0, 0.0
							TFormPoint(0.0, 0.0, Dist#, GPP, 0)
							SetDestination(Me, TFormedX#(), TFormedZ#(), TFormedY#())
						EndIf
					EndIf
				EndIf
			EndIf
			
		; Left mouse button was just clicked
		ElseIf MouseWasDown = True
			; Was this a double click?
			IsDouble = False
			If MilliSecs() - LastLeftClick < 500 Then IsDouble = True

			; Store time of click
			LastLeftClick = MilliSecs()
			MouseWasDown = False

			; Pick a target unless this click was already used by the Select control
			If UsedClick = False Or Key_Select <> Key_MoveTo
				SetPickModes(0)
				Result = CameraPick(Cam, MouseX(), MouseY())
				If Result <> 0
					Target$ = GetTarget$(Result)
					; Target is scenery
					If Target$ = ""
						If IsDouble = False
							SetDestination(Me, PickedX#(), PickedZ#(), PickedY#())
							CamYaw# = 0.0
							CamPitch# = 0.0
							Me\IsRunning = AlwaysRun
							If Me\Mount <> Null Then Me\Mount\IsRunning = Me\IsRunning
							AttackTarget = False
							ShowEntity(ClickMarkerEN)
							ClickMarkerTimer = MilliSecs()
							PositionEntity(ClickMarkerEN, PickedX#(), PickedY#(), PickedZ#())
							AlignToVector(ClickMarkerEN, PickedNX#(), PickedNY#(), PickedNZ#(), 2)
							MoveEntity(ClickMarkerEN, 0, 0.085, 0)
							; If in first person view, "compress" angle to destination and add dead zone
							If CamMode = 1
								PositionEntity GPP, Me\DestX#, EntityY#(Me\CollisionEN), Me\DestZ#
								Dist# = EntityDistance#(Me\CollisionEN, GPP)
								If Abs(GY_MouseX# - 0.5) < 0.15 Then Angle# = 0.0 Else Angle# = DeltaYaw#(Me\CollisionEN, GPP) / 75.0
								PositionEntity GPP, EntityX#(Me\CollisionEN), EntityY#(Me\CollisionEN), EntityZ#(Me\CollisionEN)
								RotateEntity GPP, 0.0, (EntityYaw#(Me\CollisionEN) - Angle#) + 180.0, 0.0
								TFormPoint(0.0, 0.0, Dist#, GPP, 0)
								SetDestination(Me, TFormedX#(), TFormedZ#(), TFormedY#())
							EndIf
						Else
							Me\IsRunning = True
							If Me\Mount <> Null Then Me\Mount\IsRunning = True
						EndIf
					EndIf

					; Do not run if player has insufficient energy
				;	If EnergyStat > -1 And Me\Mount = Null
				;		If Me\Attributes\Value[EnergyStat] <= 0 Then Me\IsRunning = False
				;	EndIf
				EndIf
			EndIf
		EndIf
	EndIf

	; Attack selected actor with the attack key
	If ControlHit(Key_Attack)
		AI.ActorInstance = Object.ActorInstance(PlayerTarget)
		If AI <> Null
			SetDestination(Me, EntityX#(AI\CollisionEN), EntityZ#(AI\CollisionEN), EntityY#(AI\CollisionEN))
			;[~~~]
			CamYaw# = 0.0
			CamPitch# = 0.0
			Me\IsRunning = True
			If Me\Mount <> Null Then Me\Mount\IsRunning = True
			; Check target is a combatant
			If AI\Actor\Aggressiveness < 3
				; Check faction rating
				If Me\FactionRatings[AI\HomeFaction] <= 150 Then AttackTarget = True
				;AttackTarget = True

			EndIf
		EndIf
	EndIf

	; Update selection highlight mesh
	If PlayerTarget > 0
		AI.ActorInstance = Object.ActorInstance(PlayerTarget)
		SetPickModes()
		Result = LinePick(EntityX#(AI\CollisionEN), EntityY#(AI\CollisionEN), EntityZ#(AI\CollisionEN), 0.0, -5000.0, 0.0)
		If Result <> 0
			PositionEntity(ActorSelectEN, PickedX#(), PickedY#() + 0.2, PickedZ#())
			AlignToVector(ActorSelectEN, PickedNX#(), PickedNY#(), PickedNZ#(), 2)
		EndIf
	Else
		HideEntity(ActorSelectEN)
	EndIf

	; Switch action bars
	If GY_ButtonHit(BPrevBar)
		If ActionBarStart > 1 Then ActionBarStart = ActionBarStart - 1
		UpdateActionBarIcons()
	ElseIf GY_ButtonHit(BNextBar)
		If ActionBarStart < 3 Then ActionBarStart = ActionBarStart + 1
		UpdateActionBarIcons()
	EndIf
	; Update action bar quick-slot buttons
	For i = 0 To 11
		; Left clicked
		If GY_ButtonHit(BActionBar(i))
			Slot = i
			If ActionBarStart = 2
				Slot = Slot + 12
			ElseIf ActionBarStart = 3
				Slot = Slot + 24
			EndIf
			; Execute this slot
			If MouseSlotSource = -1
				; Spell
				If ActionBarSlots(Slot) < 0
					If RequireMemorise
						Num = ActionBarSlots(Slot) + 10
						RechargeTime = SpellsList(Me\KnownSpells[Me\MemorisedSpells[Num]])\RechargeTime
						Pa$ = RCE_StrFromInt$(Me\KnownSpells[Me\MemorisedSpells[Num]], 2)
					Else
						Num = ActionBarSlots(Slot) + 1000
						RechargeTime = SpellsList(Me\KnownSpells[Num])\RechargeTime
						Pa$ = RCE_StrFromInt$(Me\KnownSpells[Num], 2)
					EndIf
					; Recharged
					If Me\SpellCharge[Num] <= 0
						If PlayerTarget > 0
							AI.ActorInstance = Object.ActorInstance(PlayerTarget)
							Pa$ = Pa$ + RCE_StrFromInt$(AI\RuntimeID, 2)
						EndIf
						RCE_Send(Connection, PeerToHost, P_SpellUpdate, "F" + Pa$, True)
						Me\SpellCharge[Num] = RechargeTime
						
						;[654]
						RechargeTime = RechargeTime - 1000
						;Output ("RechargeTime = "+RechargeTime/1000)
						If GY_ButtonHit(BActionBar(i))
						If RechargeTime = 0 

						EndIf
						EndIf
					
					; Not recharged
					Else
						Output(LanguageString$(LS_AbilityNotRecharged), 255, 50, 50)	
						;Output ("RechargeTime = "+RechargeTime/1000, 255, 50, 50)

			;			
			;		; Not recharged
			;		Else
			;			Output(LanguageString$(LS_AbilityNotRecharged), 255, 50, 50)
					EndIf
				; Item
				ElseIf ActionBarSlots(Slot) < 65535
					For i = 0 To Slots_Inventory
						If Me\Inventory\Items[i] <> Null
							If Me\Inventory\Items[i]\Item\ID = ActionBarSlots(Slot)
								UseItem(i, 1)
								Exit
							EndIf
						EndIf
					Next
				EndIf
			; Add item to this slot
			ElseIf MouseSlotSource >= 0
				; Update slot
				ActionBarSlots(Slot) = MouseSlotItem\Item\ID
				GYG.GY_Gadget = Object.GY_Gadget(BActionBar(i))
				EntityTexture GYG\EN, GetTexture(MouseSlotItem\Item\ThumbnailTexID)
				; Tell server
				RCE_Send(Connection, PeerToHost, P_ActionBarUpdate, "I" + RCE_StrFromInt$(Slot, 1) + RCE_StrFromInt$(MouseSlotItem\Item\ID, 2), True)
				; Blank mouse slot
				GY_SetButtonState(BSlots(MouseSlotSource), False)
				GYG.GY_Gadget = Object.GY_Gadget(BSlots(MouseSlotSource))
				EntityTexture GYG\EN, GetTexture(Me\Inventory\Items[MouseSlotSource]\Item\ThumbnailTexID)
				EnableInventoryBlanks(True)
				If Me\Inventory\Amounts[MouseSlotSource] > 1
					GY_SetButtonLabel(BSlots(MouseSlotSource), Me\Inventory\Amounts[MouseSlotSource], 100, 255, 0, True)
				Else
					GY_SetButtonLabel(BSlots(MouseSlotSource), "")
				EndIf
				HideEntity MouseSlotEN : MouseSlotItem = Null : MouseSlotAmount = 0 : MouseSlotSource = -1
			; Add spell to this slot
			ElseIf MouseSlotSource = -2
				; Update slot
				If RequireMemorise
					ActionBarSlots(Slot) = MouseSlotAmount - 10
					Sp.Spell = SpellsList(Me\KnownSpells[Me\MemorisedSpells[MouseSlotAmount]])
				Else
					ActionBarSlots(Slot) = MouseSlotAmount - 1000
					Sp.Spell = SpellsList(Me\KnownSpells[MouseSlotAmount])
				EndIf
				GYG.GY_Gadget = Object.GY_Gadget(BActionBar(i))
				EntityTexture(GYG\EN, GetTexture(Sp\ThumbnailTexID))
				; Tell server
				RCE_Send(Connection, PeerToHost, P_ActionBarUpdate, "S" + RCE_StrFromInt$(Slot, 1) + Sp\Name$, True)
				; Blank mouse slot
				HideEntity(MouseSlotEN) : MouseSlotAmount = 0 : MouseSlotSource = -1
			EndIf
		; Right clicked (remove item)
		ElseIf GY_ButtonRightHit(BActionBar(i))
			Slot = i
			If ActionBarStart = 2
				Slot = Slot + 12
			ElseIf ActionBarStart = 3
				Slot = Slot + 24
			EndIf
			; Clear slot
			ActionBarSlots(Slot) = 0
			GYG.GY_Gadget = Object.GY_Gadget(BActionBar(i))
			GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
			EntityTexture GYB\Gadget\EN, GYB\UserTexture
			; Tell server
			RCE_Send(Connection, PeerToHost, P_ActionBarUpdate, "N" + RCE_StrFromInt$(Slot, 1), True)
		EndIf
		; Execute this slot via F-key
		If i < 10 Then FScan = 2 + i Else FScan = 2 + i
		;If i < 10 Then FScan = 59 + i Else FScan = 77 + i
		If KeyHit(FScan)
			Slot = i
			If ActionBarStart = 2
				Slot = Slot + 12
			ElseIf ActionBarStart = 3
				Slot = Slot + 24
			EndIf
			; Spell
			If ActionBarSlots(Slot) < 0
				If RequireMemorise
					Num = ActionBarSlots(Slot) + 10
					RechargeTime = SpellsList(Me\KnownSpells[Me\MemorisedSpells[Num]])\RechargeTime
					Pa$ = RCE_StrFromInt$(Me\KnownSpells[Me\MemorisedSpells[Num]], 2)
				Else
					Num = ActionBarSlots(Slot) + 1000
					RechargeTime = SpellsList(Me\KnownSpells[Num])\RechargeTime
					Pa$ = RCE_StrFromInt$(Me\KnownSpells[Num], 2)
				EndIf
				; Recharged
				If Me\SpellCharge[Num] <= 0
					If PlayerTarget > 0
						AI.ActorInstance = Object.ActorInstance(PlayerTarget)
						Pa$ = Pa$ + RCE_StrFromInt$(AI\RuntimeID, 2)
					EndIf
					RCE_Send(Connection, PeerToHost, P_SpellUpdate, "F" + Pa$, True)
					Me\SpellCharge[Num] = RechargeTime
					
				;[654] fixing cooldown reset bug in action bar
						RechargeTime = RechargeTime - 1000
					;	Output ("RechargeTime = "+RechargeTime/1000)
						If GY_ButtonHit(BActionBar(i))
						If RechargeTime = 0 
						EndIf
						EndIf
					
					; Not recharged
					Else
						Output(LanguageString$(LS_AbilityNotRecharged), 255, 50, 50)	
					;	Output ("RechargeTime = "+RechargeTime/1000, 255, 50, 50)
					EndIf

			
			;	; Not recharged
			;	Else
			;		Output(LanguageString$(LS_AbilityNotRecharged), 255, 50, 50)
			;	EndIf
			; Item
			ElseIf ActionBarSlots(Slot) < 65535
				For i = 0 To Slots_Inventory
					If Me\Inventory\Items[i] <> Null
						If Me\Inventory\Items[i]\Item\ID = ActionBarSlots(Slot)
							PlaySound(GY_SBeep)
							UseItem(i, 1)
							Exit
						EndIf
					EndIf
				Next
			EndIf
		EndIf
	Next

	; Update character interaction window
	If CharInteractVisible
		; Window closed
		If GY_WindowClosed(WCharInteract) Or CharInteract = Null
			CharInteract = Null
			GY_FreeGadget(WCharInteract)
			CharInteractVisible = False
		; Window still open
		Else
			; Change target
			AI.ActorInstance = Object.ActorInstance(PlayerTarget)
			If AI <> CharInteract
				DestroyCharInteractionWindow()
				If AI <> Null
					CreateCharInteractionWindow(AI)
					;CysisLibs Outline
					;Outline ActorSelectEN
				EndIf
			ElseIf CharInteractVisible
				UpdateCharInteractionWindow()
			EndIf
			
			;If target gets out of range destory the target window cysis145 [987]
		;	If AI <> Null
		;		If EntityDistance(AI\EN,cam) > 85 And Me\Attributes\Value[HealthStat] > 0 
		;			DestroyCharInteractionWindow()
		;			HideEntity(ActorSelectEN)
		;			PlayerTarget = 0
		;		EndIf 
		;	EndIf

		EndIf
	EndIf

	; Update compass direction
	UpdateCompass()

	; Recharge spells
	If MilliSecs() - LastSpellRecharge > 100
		If RequireMemorise
			For i = 0 To 9
				If Me\SpellCharge[i] > 0 Then Me\SpellCharge[i] = Me\SpellCharge[i] - 100
			Next
		Else
			For i = 0 To 999
				If Me\SpellCharge[i] > 0 Then Me\SpellCharge[i] = Me\SpellCharge[i] - 100
			Next
		EndIf
		LastSpellRecharge = MilliSecs()
	EndIf

	; Update spells window
	If GY_ButtonHit(BNextSpells)
		If FirstSpell < 0 
			FirstSpell = 0
		ElseIf KnownSpellSort(FirstSpell + 10) > 0
			FirstSpell = FirstSpell + 10
		EndIf
		UpdateSpellbook()
	ElseIf GY_ButtonHit(BPrevSpells) And FirstSpell > -1
		; Adjust to new page
		FirstSpell = FirstSpell - 10
		If RequireMemorise
			If FirstSpell < 0 Then FirstSpell = -1
		Else
			If FirstSpell < 0 Then FirstSpell = 0
		EndIf
		UpdateSpellbook()
	EndIf
	; Spell remove confirmed
	If GY_ButtonHit(BSpellRemoveOK)
		; Tell server
		RCE_Send(Connection, PeerToHost, P_SpellUpdate, "U" + RCE_StrFromInt$(Me\MemorisedSpells[SpellRemoveNum], 2), True)
		; Remove it from the action bar if it's there
		For i = 0 To 35
			If ActionBarSlots(i) = SpellRemoveNum - 10
				ActionBarSlots(i) = 0
				
				; Tell server
				RCE_Send(Connection, PeerToHost, P_ActionBarUpdate, "N" + RCE_StrFromInt$(i, 1), True)
			EndIf
			UpdateActionBarIcons()
		Next
		; Remove it
		Me\MemorisedSpells[SpellRemoveNum] = 5000
		UpdateSpellbook()
		; Hide the confirmation window
		GY_LockGadget(WSpellRemove, True)
		GY_GadgetAlpha(WSpellRemove, 0.0, True)
		GY_ActivateWindow(WSpells)
		GY_Modal = False
		SpellRemoveNum = -1
	; Spell remove cancelled
	ElseIf GY_ButtonHit(BSpellRemoveCancel) Or GY_WindowClosed(WSpellRemove)
		GY_LockGadget(WSpellRemove, True)
		GY_GadgetAlpha(WSpellRemove, 0.0, True)
		GY_ActivateWindow(WSpells)
		GY_Modal = False
		SpellRemoveNum = -1
	; Spell error message closed
	ElseIf GY_ButtonHit(BSpellError) Or GY_WindowClosed(WSpellError)
		GY_FreeGadget(WSpellError)
		GY_Modal = False
		GY_ActivateWindow(WSpells)
	EndIf

	; Update spell memorisation
	If SMemorising <> 0
		If MilliSecs() - LastMemoriseUpdate > 100
			MemoriseProgress = MemoriseProgress + 1
			GY_UpdateProgressBar(SMemorising, MemoriseProgress)
			LastMemoriseUpdate = MilliSecs()
			; Done
			If MemoriseProgress = 60
				GY_FreeGadget(LMemorising) : LMemorising = 0
				GY_FreeGadget(SMemorising) : SMemorising = 0
				Me\MemorisedSpells[MemoriseSlot] = MemoriseSpell
				If SpellsVisible = True Then UpdateSpellbook()
			EndIf
		EndIf
	EndIf

	; Check for memorised spell buttons being clicked
	If FirstSpell < 0 And MouseSlotSource = -1
		For i = 0 To 9
			; Right click (un-memorise)
			If GY_ButtonRightHit(BSpellImgs(i))
				GY_LockGadget(WSpellRemove, False)
				GY_GadgetAlpha(WSpellRemove, 1.0, True)
				GY_ActivateWindow(WSpellRemove)
				GY_Modal = True
				SpellRemoveNum = i
			; Left click (put in mouse slot for moving to action bar)
			ElseIf GY_ButtonHit(BSpellImgs(i))
				; If one is already in the mouse slot, just remove it
				If MouseSlotSource <> -1
					HideEntity(MouseSlotEN) : MouseSlotAmount = 0 : MouseSlotSource = -1
				; Otherwise put this in the mouse slot
				Else
					Sp.Spell = SpellsList(Me\KnownSpells[Me\MemorisedSpells[i]])
					ShowEntity(MouseSlotEN)
					EntityTexture(MouseSlotEN, GetTexture(Sp\ThumbnailTexID))
					MouseSlotAmount = i
					MouseSlotSource = -2
				EndIf
			EndIf
		Next
	EndIf
	; Check for known spell buttons being clicked
	If SMemorising = 0 And FirstSpell >= 0
		For i = 0 To 9
			; Memorise if required
			If RequireMemorise
				If GY_ButtonRightHit(BSpellImgs(i)) Or GY_ButtonHit(BSpellImgs(i))
					; Find known spell number
					MemoriseSpell = KnownSpellSort(FirstSpell + i) - 1
					; Check it's not already memorised
					Found = False
					For j = 0 To 9
						If Me\MemorisedSpells[j] = MemoriseSpell Then Found = True : Exit
					Next
					; Not already memorised, find free slot
					If Found = False
						For j = 0 To 9
							If Me\MemorisedSpells[j] = 5000
							
							;Adding the progress bar for memorisation to the abilities window. Cysis145 [#]
								; Progress bar stuff
								LMemorising = GY_CreateLabel(WSpells, 0.5, 0.9, LanguageString$(LS_MemorisingAbility), 255, 255, 255, Justify_Centre)
								SMemorising = GY_CreateProgressBar(WSpells, 0.05, 0.91, 0.9, 0.07, 1, 60, 255, 255, 255)
								MemoriseSlot = j
								MemoriseProgress = 0
								LastMemoriseUpdate = MilliSecs()
								SetDestination(Me, EntityX#(Me\CollisionEN), EntityZ#(Me\CollisionEN), EntityY#(Me\CollisionEN))
								
								;##########
								; Moved outside of SetDestination() by clan_fd on Apr 1, 2014
								QuitActive = False
								LogOutActive = False
								; End edit
								;##########								
								
								; Tell server
								RCE_Send(Connection, PeerToHost, P_SpellUpdate, "M" + RCE_StrFromInt$(MemoriseSpell, 2), True)
								; Done
								Found = True
								Exit
							EndIf
						Next
						; Rebuilt memorise error messages Cysis145 {#}
						; No free slot
						If Found = False
							WSpellError = GY_CreateWindowInter(LanguageString$(LS_MemoriseAbility), 0.31, 0.7, 0.38, 0.07, True, True, False, LoadTexture("Data\Textures\GUI\ToolTip.png"))
							GY_CreateLabel(WSpellError, 0.5, 0.05, LanguageString$(LS_MaximumMemorised), 255, 255, 255, Justify_Centre)
							BSpellError = GY_CreateCustomButton(WSpellError, 0.35, 0.35, 0.27, 0.55, LoadButtonU("Okay"), LoadButtonD("Okay"), LoadButtonH("Okay"))
							GY_GadgetAlpha(WSpellError, 1.0, True)
							GY_Modal = True
						EndIf
					; Already memorised
					Else
						WSpellError = GY_CreateWindowInter(LanguageString$(LS_MemoriseAbility), 0.31, 0.7, 0.38, 0.07, True, True, False, LoadTexture("Data\Textures\GUI\ToolTip.png"))
						GY_CreateLabel(WSpellError, 0.5, 0.05, LanguageString$(LS_AlreadyMemorised), 255, 255, 255, Justify_Centre)
						BSpellError = GY_CreateCustomButton(WSpellError, 0.35, 0.35, 0.27, 0.55, LoadButtonU("Okay"), LoadButtonD("Okay"), LoadButtonH("Okay"))
						GY_GadgetAlpha(WSpellError, 1.0, True)
						GY_Modal = True
					EndIf
				EndIf
			; If memorisation is not required
			Else
				; Right click to add spell to mouse slot for movement to action bar
				If GY_ButtonRightHit(BSpellImgs(i))
					; If one is already in the mouse slot, just remove it
					If MouseSlotSource <> -1
						HideEntity(MouseSlotEN) : MouseSlotAmount = 0 : MouseSlotSource = -1
					; Otherwise put this in the mouse slot
					Else
						Sp.Spell = SpellsList(Me\KnownSpells[KnownSpellSort(FirstSpell + i) - 1])
						ShowEntity(MouseSlotEN)
						EntityTexture(MouseSlotEN, GetTexture(Sp\ThumbnailTexID))
						MouseSlotAmount = KnownSpellSort(FirstSpell + i) - 1
						MouseSlotSource = -2
					EndIf
				; Left click to fire spell
				ElseIf GY_ButtonHit(BSpellImgs(i))
					Num = KnownSpellSort(FirstSpell + i) - 1
					; Recharged
					If Me\SpellCharge[Num] <= 0
						Pa$ = RCE_StrFromInt$(Me\KnownSpells[Num], 2)
						If PlayerTarget > 0
							AI.ActorInstance = Object.ActorInstance(PlayerTarget)
							Pa$ = Pa$ + RCE_StrFromInt$(AI\RuntimeID, 2)
						EndIf
						RCE_Send(Connection, PeerToHost, P_SpellUpdate, "F" + Pa$, True)
						Me\SpellCharge[Num] = SpellsList(Me\KnownSpells[Num])\RechargeTime
					; Not recharged
					Else
						Output(LanguageString$(LS_AbilityNotRecharged), 255, 50, 50)
					EndIf
				EndIf
			EndIf
		Next
	EndIf
	; Right clicking while a spell is in the mouse slot removes it
	If MouseDown(2) And MouseSlotSource = -2
		HideEntity(MouseSlotEN)
		MouseSlotAmount = 0
		MouseSlotSource = -1
	EndIf

	; Update dialogs
	For D.Dialog = Each Dialog
		If D\TotalOptions > 0
			Opt = -1
			For i = 0 To 13
				If GY_MouseHovering(D\TextLines[i]) = True And D\OptionNum[i] > 0
					Opt = D\OptionNum[i]
					Exit
				EndIf
			Next
			For i = 0 To 13
				If D\OptionNum[i] = Opt
					GY_UpdateLabel(D\TextLines[i], D\TextText$[i], 0, 125, 255)
				Else
					GY_UpdateLabel(D\TextLines[i], D\TextText$[i], D\TextR[i], D\TextG[i], D\TextB[i])
				EndIf
			Next
			; Option selected!
			If GY_LeftClick And Opt > 0 And GY_WindowActive(D\Win)
				PlaySound(GY_SClick)
				RCE_Send(Connection, PeerToHost, P_Dialog, "O" + RCE_StrFromInt$(D\ScriptHandle, 4) + RCE_StrFromInt$(Opt, 1), True)
				For i = 0 To 13 : D\OptionNum[i] = 0 : Next
				D\TotalOptions = 0
			EndIf
		EndIf
	Next
	; Update text input dialogs
	For TI.TextInput = Each TextInput
		If GY_TextFieldHit(TI\TextBox) Or GY_ButtonHit(TI\AcceptButton) And Len(GY_TextFieldText$(TI\TextBox)) > 0
			RCE_Send(Connection, PeerToHost, P_ScriptInput, RCE_StrFromInt$(TI\ScriptHandle, 4) + GY_TextFieldText$(TI\TextBox), True)
			FreeTextInput(Handle(TI))
		EndIf
	Next
	If First Dialog = Null And First TextInput = Null And MouseDown(2) = False Then InDialog = False : FlushMouse()

	;- Update chat bubbles
	For Bubble.Bubble = Each Bubble
		; Position above character's head
		AI.ActorInstance = Bubble\ActorInstance
		If AI <> Null
			PositionEntity(Bubble\EN, EntityX#(AI\CollisionEN), EntityY#(AI\CollisionEN) + 3.0, EntityZ#(AI\CollisionEN))
			PointEntity(Bubble\EN, Cam)
			MoveEntity(Bubble\EN, Bubble\Width#, 0.0, 0.0)
			RotateEntity(Bubble\EN, 0, EntityYaw#(Bubble\EN), 0)
	
			Time = MilliSecs() - Bubble\Timer
			; Fade in over quarter of a second
			If Time < 250
				EntityAlpha(Bubble\EN, Float#(Time) / 312.5)
			; Fade out after 5 seconds
			ElseIf Time > 5000
				Time = Time - 5000
				If Time > 500
					FreeEntity(Bubble\EN)
					Delete(Bubble)
				Else
					EntityAlpha(Bubble\EN, 0.8 - (Float#(Time) / 625.0))
				EndIf
			Else
				EntityAlpha(Bubble\EN, 0.8)
			EndIf
		Else
			; remove bubble as the corresponding actor no longer exists
			FreeEntity(Bubble\EN)
			Delete(Bubble)
		EndIf
	Next

	; Update quest log window
	If QuestLogVisible = True
		If GY_CheckBoxHit(BCompleteQuests) Then RedrawQuestLog()
		If GY_ButtonHit(BNextQuest)
			FirstQuest = FirstQuest + 1
			RedrawQuestLog()
		ElseIf GY_ButtonHit(BPrevQuest)
			FirstQuest = FirstQuest - 1
			RedrawQuestLog()
		EndIf
	EndIf

	; Update party window
	If PartyVisible = True
		For i = 0 To 6
			If PartyName$(i) <> ""
				If GY_MouseHovering(LPartyName(i)) = True
					GY_UpdateLabel(LPartyName(i), PartyName$(i), 0, 125, 255)
					If GY_LeftClick And GY_WindowActive(WParty)
						ChatEntry\Alpha# = 1.0
						GY_GadgetAlpha(ChatEntry\Component, 1.0)
						GY_ActivateTextField(ChatEntry\Component)
						GY_SetButtonState(BChat, True)
						GY_UpdateTextField(ChatEntry\Component, "/p " + PartyName$(i) + " ")
					EndIf
				Else
					GY_UpdateLabel(LPartyName(i), PartyName$(i), 0, 255, 0)
				EndIf
			EndIf
		Next
		If GY_ButtonHit(BPartyLeave) Then RCE_Send(Connection, PeerToHost, P_ChatMessage, "/leave", True)
	EndIf

	;&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
	; Added May 12 by Clan-fd - Modified by terrier 
	;functional buttons
	If GY_ButtonHit(BLogOut)
	
		GY_GadgetAlpha(WMenu, 0.0, True)
		GY_GadgetAlpha(WMenu, 0.0, True)
		LogOutActive = True
		QuitActiveMS = MilliSecs()
		Me\DestX# = EntityX#(Me\CollisionEN)
		Me\DestZ# = EntityZ#(Me\CollisionEN)
		GY_GadgetAlpha(QuitBar, 1.0)
		GY_UpdateLabel(QuitLabel, "Logging out...")
		GY_GadgetAlpha(QuitLabel, 1.0)
	
	;ElseIf GY_ButtonHit(BCharSelect)
	;	UnloadArea()
	;	UnloadGame()
	;	LoadOptions()
	;	InitialSetup()
	;	ClientMode = 1
	;	Return -1
	
	ElseIf GY_ButtonHit(BOptions)
		GameOptionsMenu()
	ElseIf GY_ButtonHit(BExit)
		GY_GadgetAlpha(WMenu, 0.0, True)
		QuitActive = True
		QuitActiveMS = MilliSecs()
		Me\DestX# = EntityX#(Me\CollisionEN)
		Me\DestZ# = EntityZ#(Me\CollisionEN)
		GY_GadgetAlpha(QuitBar, 1.0)
		GY_GadgetAlpha(QuitLabel, 1.0)	
	EndIf
	;&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&


	
	; Update help window
	If HelpVisible = True
		If MaxHelpLine > 14
			Pos# = GY_ScrollBarValue#(SHelpScroll)
			TheLine = Pos# * Float#(MaxHelpLine - 14)
			If TheLine <> CurrentHelpLine
				CurrentHelpLine = TheLine
				For i = 0 To 14
					GY_UpdateLabel(LHelp(i), HelpText$(i + CurrentHelpLine))
				Next
			EndIf
		EndIf
	EndIf

	; Update character stats window
	If CharStatsVisible = True
		; Reputation and gold display
		GY_UpdateLabel(LReputation, LanguageString$(LS_Reputation) + " " + Me\Reputation)
		;GY_UpdateLabel(LGold, Money$(Me\Gold))
		GY_UpdateLabel(LLevel, LanguageString$(LS_Level) + " " + Me\Level)
		GY_UpdateLabel(LXP, LanguageString$(LS_Experience) + " " + Me\XP)
		; Next/previous buttons
		If GY_ButtonHit(BNextAttribute)
			; Check we have a next attribute to go to
			Count = 0
			For i = FirstAttribute + 1 To 39
				If AttributeNames$(i) <> "" And AttributeHidden(i) = False
					Count = Count + 1
				EndIf
			Next
			; If so, go to it
			If Count > 9
				For i = FirstAttribute + 1 To 39
					If AttributeNames$(i) <> "" And AttributeHidden(i) = False
						FirstAttribute = i
						Att = FirstAttribute - 1
						Found = 0
						Repeat
							Att = Att + 1
						If AttributeNames$(Att) <> "" And AttributeHidden(Att) = False
								GY_UpdateLabel(LAttributeNames(Found), AttributeNames$(Att))
								Found = Found + 1
								If Found = 10 Then Exit
							EndIf
						Forever
						Exit
					EndIf
			Next
			EndIf
		ElseIf GY_ButtonHit(BPrevAttribute)
			; Go to previous attribute if there is one
			For i = FirstAttribute - 1 To 0 Step -1
				If AttributeNames$(i) <> "" And AttributeHidden(i) = False
					FirstAttribute = i
					Att = FirstAttribute - 1
					Found = 0
				Repeat
						Att = Att + 1
						If AttributeNames$(Att) <> "" And AttributeHidden(Att) = False
							GY_UpdateLabel(LAttributeNames(Found), AttributeNames$(Att))
							Found = Found + 1
							If Found = 10 Then Exit
						EndIf
					Forever
					Exit
				EndIf
		Next
		EndIf
	; Display attributes
		Att = FirstAttribute - 1
		Found = 0
		Repeat
			Att = Att + 1
			If AttributeNames$(Att) <> "" And AttributeHidden(Att) = False
				If AttributeIsSkill(Att)
					GY_UpdateLabel(LAttributeVals(Found), Me\Attributes\Value[Att])
				Else
					GY_UpdateLabel(LAttributeVals(Found), Me\Attributes\Value[Att] + " / " + Me\Attributes\Maximum[Att])
				EndIf
				Found = Found + 1
				If Found = 10 Then Exit
			EndIf
			If Att >= 39 Then Exit
		Forever

;ItemShop Buttons
	If GY_ButtonHit(BOffers)
		Offer1 = GY_CreateLabel(WCharStats, 0.847, 0.24, "Im an Offer", 255, 255, 255)
		GY_DropGadget(Offer1)
EndIf
	
	EndIf

	; Count time since mouse last moved and remove tooltip window when it moves
	If Abs(GY_MouseXSpeed) > 3 Or Abs(GY_MouseYSpeed) > 3 Or GY_LeftClick Or GY_RightClick
		LastMouseMove = MilliSecs()
		If WTooltip <> 0
			GY_FreeGadget(WTooltip) : WTooltip = 0
			GY_ActivateWindow(WTooltipReturn) : WTooltipReturn = 0
		EndIf
		If LTooltip <> 0 Then GY_FreeGadget(LTooltip) : LTooltip = 0
	EndIf

   ; Action Bar ToolTip Cysis145 [##]
   If MilliSecs() - LastMouseMove > 1000
      If WTooltip = 0 And LTooltip = 0
         ; Action bar quick slots
         If ActionBarStart = 2
            Offset = 12
         ElseIf ActionBarStart = 3
            Offset = 24
         EndIf
         For i = 0 To 11
            If GY_MouseHovering(BActionBar(i))
               If ActionBarSlots(i + Offset) < 0 And ActionBarSlots(i + Offset) <> 0
                  If RequireMemorise
                     Num = ActionBarSlots(i + Offset) + 10
                     Sp.Spell = SpellsList(Me\KnownSpells[Me\MemorisedSpells[Num]])
                  Else
                     Num = ActionBarSlots(i + Offset) + 1000
                     Sp.Spell = SpellsList(Me\KnownSpells[Num])
                  EndIf
                  If Sp <> Null
                     X# = GY_MouseX#
                     Y# = GY_MouseY# + 0.008
                     If Y# + 0.4 > 0.99 Then Y# = 0.59
                     If X# + 0.4 > 0.99 Then X# = 0.59
                     If LTooltip <> 0 Then GY_FreeGadget(LTooltip) : LTooltip = 0
                     WTooltip = GY_CreateWindow(Sp\Name$, 0.63, 0.81, 0.37, 0.08, False, False, False, LoadTexture("Data\Textures\GUI\ToolTip.png"))
                     WTooltipReturn = WSpells
                     Desc$ = Sp\Description$
                     If Desc$ = "" Then Desc$ = LanguageString$(LS_NoDescription)
                     Y# = 0.07
					 LTitle = GY_CreateLabelBig(WTooltip, 0.02, Y#, Sp\Name$)
					 LRank = GY_CreateLabel(WTooltip, 0.02, Y# + 0.22, "[Rank" + " " + Me\SpellLevels[Me\MemorisedSpells[i]] + "]", 100, 255, 0)
                     ; Word wrap
                     While Desc$ <> ""
                        LDesc = GY_CreateLabel(WTooltip, 0.02, Y# + 0.42, Desc$)
                        Y# = Y# + 0.18
                        Gad.GY_Gadget = Object.GY_Gadget(LDesc)
                        If GY_TextWidth#(Gad\EN, Desc$) >= 0.36
                           Split = False
                           For i = Len(Desc$) To 1 Step -1
                              If Mid$(Desc$, i, 1) = " "
                                 If GY_TextWidth#(Gad\EN, Left$(Desc$, i - 1)) < 0.36
                                    GY_UpdateLabel(LDesc, Left$(Desc$, i - 1))
                                    Desc$ = Mid$(Desc$, i + 1)
                                    Split = True
                                    Exit
                                 EndIf
                              EndIf
                           Next
                           If Split = False
                              For i = Len(Desc$) To 1 Step -1
                                 If GY_TextWidth#(Gad\EN, Left$(Desc$, i - 1)) < 0.36
                                    GY_UpdateLabel(LDesc, Left$(Desc$, i - 1))
                                    Desc$ = Mid$(Desc$, i)
                                    Exit
                                 EndIf
                              Next
                           EndIf
                        Else
                           Desc$ = ""
                        EndIf
                     Wend
                     GY_GadgetAlpha(WTooltip, 0.95, True)
                  EndIf
                  Goto TooltipsDone
               EndIf
            EndIf
         Next

			; Attribute bars
			For i = 0 To 39
				If AttributeDisplays(i)\Component <> 0
					If GY_MouseHovering(AttributeDisplays(i)\Component)
						LTooltip = GY_CreateLabel(0, GY_MouseX#, GY_MouseY# + 0.03, AttributeNames$(i))
						Goto TooltipsDone
					EndIf
				EndIf
			Next

			; Actor effect icons
			If GY_MouseX# > BuffsArea\X# And GY_MouseX# < BuffsArea\X# + BuffsArea\Width#
				If GY_MouseY# > BuffsArea\Y# And GY_MouseY# < BuffsArea\Y# + BuffsArea\Height#
					IconsAcross = Floor#(BuffsArea\Width# / 0.0225)
					IconsDown = Floor#(BuffsArea\Height# / 0.03)
					X# = BuffsArea\X#
					Y# = BuffsArea\Y#
					EISlot.EffectIconSlot = First EffectIconSlot
					For i = 1 To IconsDown
						For j = 1 To IconsAcross
							If EISlot\Effect <> Null
								If GY_MouseX# > X# And GY_MouseY# > Y#
									If GY_MouseX# < X# + 0.0225 And GY_MouseY# < Y# + 0.03
										LTooltip = GY_CreateLabel(0, GY_MouseX#, GY_MouseY# + 0.03, EISlot\Effect\Name$)
										Goto TooltipsDone
									EndIf
								EndIf
							EndIf
							X# = X# + 0.0225
							EISlot = After EISlot
						Next
						X# = BuffsArea\X#
						Y# = Y# + 0.03
					Next
				EndIf
			EndIf
		EndIf
	EndIf
	.TooltipsDone

;Rebuilt Item ToolTip System Cysis145
	; If hovering over inventory, create tooltip
	If InventoryVisible = True And GY_WindowActive(WInventory)
		If MilliSecs() - LastMouseMove > 1300 And GY_MouseOverGadget = True And WTooltip = 0
			For i = 0 To Slots_Inventory
				If GY_MouseHovering(BSlots(i)) = True And Me\Inventory\Items[i] <> Null
					Name$ = Me\Inventory\Items[i]\Item\Name$
					X# = GY_MouseX# + 0.03
					Y# = GY_MouseY#
					If Y# + 0.4 > 0.99 Then Y# = 0.59
					If X# + 0.4 > 0.99 Then X# = 0.59
					If LTooltip <> 0 Then GY_FreeGadget(LTooltip) : LTooltip = 0
					WTooltip = GY_CreateWindowInventory(Name$, X#, Y#, 0.15, 0.1, True, False, False)
					WTooltipReturn = WInventory
					GY_CreateLabel(WTooltip, 0.02, 0.01, LanguageString$(LS_Type) + " " + GetItemType$(Me\Inventory\Items[i]\Item))
					If Me\Inventory\Items[i]\Item\TakesDamage = True
						Damage = 100 - Me\Inventory\Items[i]\ItemHealth
						GY_CreateLabel(WTooltip, 0.02, 0.15, LanguageString$(LS_Damage) + " " + Str$(Damage) + "%")
					Else
						;GY_CreateLabel(WTooltip, 0.02, 0.12, LanguageString$(LS_Indestructible), 255, 0, 0)
					EndIf
					GY_CreateLabel(WTooltip, 0.02, 0.29, "Value :" + " " + Me\Inventory\Items[i]\Item\Value) ;LanguageString$(LS_Value)
					GY_CreateLabel(WTooltip, 0.02, 0.43, "Weight:" + " " + Me\Inventory\Items[i]\Item\Mass) ;LanguageString$(LS_Mass)
					If Me\Inventory\Items[i]\Item\Stackable = True
						GY_CreateLabel(WTooltip, 0.02, 0.57, "Item can be stacked", 255, 255, 255) ;LanguageString$(LS_CanBeStacked)
					Else
						GY_CreateLabel(WTooltip, 0.02, 0.57, "Item does not stack", 255, 255, 255) ;LanguageString$(LS_CannotBeStacked)
					EndIf
					Select Me\Inventory\Items[i]\Item\ItemType
						Case I_Weapon
							Dam = Me\Inventory\Items[i]\Item\WeaponDamage
							DamType$ = DamageTypes$(Me\Inventory\Items[i]\Item\WeaponDamageType)
							WepType$ = GetWeaponType$(Me\Inventory\Items[i]\Item)
							GY_CreateLabel(WTooltip, 0.02, 0.71, LanguageString$(LS_Damage) + " " + Str$(Dam))
							GY_CreateLabel(WTooltip, 0.02, 0.55, LanguageString$(LS_DamageType) + " " + DamType$)
							GY_CreateLabel(WTooltip, 0.02, 0.99, LanguageString$(LS_WeaponType) + " " + WepType$)
							Y# = 0.61
						Case I_Armour
							AP = Me\Inventory\Items[i]\Item\ArmourLevel
							GY_CreateLabel(WTooltip, 0.02, 0.71, LanguageString$(LS_ArmourLevel) + " " + Str$(AP))
							Y# = 0.47
						Case I_Ingredient, I_Potion
							EatEffects = Me\Inventory\Items[i]\Item\EatEffectsLength
							GY_CreateLabel(WTooltip, 0.02, 0.71, LanguageString$(LS_EffectsLast) + " " + Str$(EatEffects) + " " + LanguageString$(LS_Seconds))
							Y# = 0.47
					End Select
					If Me\Inventory\Items[i]\Item\ExclusiveRace$ <> ""
						Ex$ = Me\Inventory\Items[i]\Item\ExclusiveRace$
						If Upper$(Me\Actor\Race$) <> Upper$(Ex$)
							GY_CreateLabel(WTooltip, 0.02, Y#, Ex$ + " " + LanguageString$(LS_RaceOnly), 255, 0, 0)
							Y# = Y# + 0.07
						EndIf
					EndIf
					If Me\Inventory\Items[i]\Item\ExclusiveClass$ <> ""
						Ex$ = Me\Inventory\Items[i]\Item\ExclusiveClass$
						If Upper$(Me\Actor\Class$) <> Upper$(Ex$)
							GY_CreateLabel(WTooltip, 0.02, Y#, Ex$ + " " + LanguageString$(LS_ClassOnly), 255, 0, 0)
							Y# = Y# + 0.07
						EndIf
					EndIf
					GY_GadgetAlpha(WTooltip, 0.85, True)
					Exit
				EndIf
			Next
		EndIf
	; If hovering over spellbook, create tooltip
	ElseIf SpellsVisible = True And GY_WindowActive(WSpells)
		If MilliSecs() - LastMouseMove > 1000 And GY_MouseOverGadget = True And WTooltip = 0
			For i = 0 To 9
				If GY_MouseHovering(BSpellImgs(i)) = True
					Sp.Spell = Null
					If FirstSpell = -1
						If Me\MemorisedSpells[i] <> 5000
							Sp = SpellsList(Me\KnownSpells[Me\MemorisedSpells[i]])
						EndIf
					Else
						If KnownSpellSort(FirstSpell + i) > 0
							Sp = SpellsList(Me\KnownSpells[KnownSpellSort(FirstSpell + i) - 1])
						EndIf
					EndIf
					If Sp <> Null
						X# = GY_MouseX# + 0.03
						Y# = GY_MouseY#
						If Y# + 0.4 > 0.99 Then Y# = 0.59
						If X# + 0.4 > 0.99 Then X# = 0.59
						If LTooltip <> 0 Then GY_FreeGadget(LTooltip) : LTooltip = 0
						WTooltip = GY_CreateWindow(Sp\Name$, X#, Y#, 0.37, 0.08, False, False, False, LoadTexture("Data\Textures\GUI\ToolTip.png"))
						WTooltipReturn = WSpells
						Desc$ = Sp\Description$
						If Desc$ = "" Then Desc$ = LanguageString$(LS_NoDescription)
						Y# = 0.01
						; If it's a memorised spell, add extra text
						If FirstSpell = -1
							Y# = 0.29
							;GY_CreateLabel(WTooltip, 0.02, 0.01, LanguageString$(LS_MemorisedYouMust), 255, 0, 0)
							;GY_CreateLabel(WTooltip, 0.02, 0.15, LanguageString$(LS_MoveItToActionBar), 255, 0, 0)
						EndIf
						
						Y# = 0.07
						LTitle = GY_CreateLabelBig(WTooltip, 0.02, Y#, Sp\Name$, 0, 0, 255) 
						LRank = GY_CreateLabel(WTooltip, 0.02, Y# + 0.22, "[Rank" + " " + Me\SpellLevels[Me\MemorisedSpells[i]] + "]", 100, 255, 0)
						While Desc$ <> ""
						LDesc = GY_CreateLabel(WTooltip, 0.02, Y# + 0.42, Desc$)
                        Y# = Y# + 0.18
						
                        Gad.GY_Gadget = Object.GY_Gadget(LDesc)
                        If GY_TextWidth#(Gad\EN, Desc$) >= 0.36
                           Split = False
                           For i = Len(Desc$) To 1 Step -1
                              If Mid$(Desc$, i, 1) = " "
                                 If GY_TextWidth#(Gad\EN, Left$(Desc$, i - 1)) < 0.36
                                    GY_UpdateLabel(LDesc, Left$(Desc$, i - 1))
                                    Desc$ = Mid$(Desc$, i + 1)
                                    Split = True
                                    Exit
                                 EndIf
                              EndIf
                           Next
                           If Split = False
                              For i = Len(Desc$) To 1 Step -1
                                 If GY_TextWidth#(Gad\EN, Left$(Desc$, i - 1)) < 0.36
                                    GY_UpdateLabel(LDesc, Left$(Desc$, i - 1))
                                    Desc$ = Mid$(Desc$, i)
                                    Exit
                                 EndIf
                             Next
                           EndIf
                        Else
                           Desc$ = ""
                        EndIf
                     Wend
                     GY_GadgetAlpha(WTooltip, 0.95, True)
					EndIf
					Exit
				EndIf
			Next
		EndIf
	EndIf

	; Enable chat entry
	ChatHit = GY_ButtonHit(BChat)
	OthersVisible = InventoryVisible Or CharStatsVisible Or QuestLogVisible Or SpellsVisible Or GY_Modal Or First TextInput <> Null
	If (GY_EnterHit Or ChatHit) And ChatEntry\Alpha# < 0.5 And OthersVisible = False
		ChatEntry\Alpha# = 0.5
		GY_GadgetAlpha(ChatEntry\Component, 1.0)
		GY_ActivateTextField(ChatEntry\Component)
		GY_SetButtonState(BChat, True)
	; Open chat entry with / key
	ElseIf KeyHit(53) And ChatEntry\Alpha# < 0.5 And OthersVisible = False
		ChatEntry\Alpha# = 0.5
		GY_GadgetAlpha(ChatEntry\Component, 1.0)
		GY_ActivateTextField(ChatEntry\Component)
		GY_SetButtonState(BChat, True)
		GY_UpdateTextField(ChatEntry\Component, "/")
		GY_ActivateTextField(ChatEntry\Component)
	; Send message and disable chat entry
	ElseIf GY_TextFieldHit(ChatEntry\Component)
		If Len(GY_TextFieldText$(ChatEntry\Component)) > 0
			RCE_Send(Connection, PeerToHost, P_ChatMessage, GY_TextFieldText$(ChatEntry\Component), True)
		EndIf
		ChatEntry\Alpha# = 0.0
		GY_GadgetAlpha(ChatEntry\Component, 0.0)
		GY_UpdateTextField(ChatEntry\Component, "")
		GY_SetButtonState(BChat, False)
		FlushKeys()
	; Close with escape
	ElseIf ChatEntry\Alpha# > 0.5 And EscapeHit = True
		EscapeHit = False
		ChatEntry\Alpha# = 0.0
		GY_GadgetAlpha(ChatEntry\Component, 0.0)
		GY_UpdateTextField(ChatEntry\Component, "")
		GY_SetButtonState(BChat, False)
	; Action bar chat button toggled
	ElseIf ChatHit
		; Show chat
		If ChatEntry\Alpha# < 0.5 And OthersVisible = False
			ChatEntry\Alpha# = 1.0
			GY_GadgetAlpha(ChatEntry\Component, 1.0)
			GY_ActivateTextField(ChatEntry\Component)
			GY_SetButtonState(BChat, True)
		; Cancel chat
		Else
			ChatEntry\Alpha# = 0.0
			GY_GadgetAlpha(ChatEntry\Component, 0.0)
			GY_UpdateTextField(ChatEntry\Component, "")
			GY_SetButtonState(BChat, False)
		EndIf
	EndIf

	; Ok button clicked on amount dialog or enter hit in text field
	If GY_ButtonHit(BAmountOK) Or GY_TextFieldHit(TAmount)
		AmountVisible = False
		GY_GadgetAlpha(WAmount, 0.0, True)
		Amount = GY_TextFieldText$(TAmount)
		If Amount < 1 Then Amount = 1
		If AmountSlot < 1000
			GY_Modal = False
			GY_ActivateWindow(WInventory)
			ShowEntity MouseSlotEN
			EntityTexture MouseSlotEN, GetTexture(Me\Inventory\Items[AmountSlot]\Item\ThumbnailTexID)
			GY_SetButtonState(BSlots(AmountSlot), True)
			If Amount > Me\Inventory\Amounts[AmountSlot] Then Amount = Me\Inventory\Amounts[AmountSlot]
			MouseSlotItem = Me\Inventory\Items[AmountSlot]
			MouseSlotAmount = Amount
			MouseSlotSource = AmountSlot
			If Me\Inventory\Amounts[AmountSlot] > Amount Then GY_SetButtonState(BSlots(AmountSlot), False)
			If Me\Inventory\Amounts[AmountSlot] > Amount + 1
				GY_SetButtonLabel(BSlots(AmountSlot), Me\Inventory\Amounts[AmountSlot] - Amount, 100, 255, 0, True)
			Else
				GY_SetButtonLabel(BSlots(AmountSlot), "")
				If Me\Inventory\Amounts[AmountSlot] = Amount
					GYG.GY_Gadget = Object.GY_Gadget(BSlots(AmountSlot))
					GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
					EntityTexture GYB\Gadget\EN, GYB\UserTexture
					GY_SetButtonState(BSlots(AmountSlot), True)
				EndIf
			EndIf
			EnableInventoryBlanks()
		Else
			GY_ActivateWindow(WTrading)
			If AmountSlot < 2000
				If Amount > TradeAmounts(AmountSlot - 1000) Then Amount = TradeAmounts(AmountSlot - 1000)
				TradeAmountsHis(AmountSlot - 1000) = Amount
			Else
				If Amount > Me\Inventory\Amounts[(AmountSlot - 2000) + SlotI_Backpack] Then Amount = Me\Inventory\Amounts[(AmountSlot - 2000) + SlotI_Backpack]
				TradeAmountsMine(AmountSlot - 2000) = Amount
				If TradeType = 2 Then RCE_Send(Connection, PeerToHost, P_UpdateTrading, RCE_StrFromInt$(AmountSlot - 2000, 1) + RCE_StrFromInt$(Amount, 2), True)
			EndIf
			UpdateTrading()
		EndIf
	; Close button clicked on amount dialog
	ElseIf GY_WindowClosed(WAmount) Or (AmountVisible = True And EscapeHit = True)
		EscapeHit = False
		AmountVisible = False
		GY_GadgetAlpha(WAmount, 0.0, True)
		If AmountSlot < 1000
			GY_Modal = False
			GY_ActivateWindow(WInventory)
			GY_SetButtonState(BSlots(AmountSlot), False)
		ElseIf AmountSlot < 2000
			GY_ActivateWindow(WTrading)
			GY_SetButtonState(BSlotsHis(AmountSlot - 1000), False)
		Else
			GY_ActivateWindow(WTrading)
			GY_SetButtonState(BSlotsHis(AmountSlot - 2000), False)
		EndIf
	EndIf

	; Trading window
	If GY_WindowClosed(WTrading) Or GY_ButtonHit(BTradingCancel) Or (TradingVisible = True And EscapeHit = True)
		EscapeHit = False
		GY_GadgetAlpha(WTrading, 0.0, True)
		GY_Modal = False
		TradingVisible = False
		RCE_Send(Connection, PeerToHost, P_OpenTrading, "", True)
	EndIf
	If GY_ButtonHit(BCostUp)
		TradeCost = TradeCost + 1
		GY_UpdateLabel(LTradingCost, LanguageString$(LS_Cost) + " " + Money$(TradeCost))
	ElseIf GY_ButtonHit(BCostDown)
		TradeCost = TradeCost - 1
		GY_UpdateLabel(LTradingCost, LanguageString$(LS_Cost) + " " + Money$(TradeCost))
	EndIf
	If GY_ButtonHit(BTradingOK)
		; Player -> NPC trading and player -> scenery trading
		If TradeType = 1 Or TradeType = 3
			; Calculate value
			If TradeType = 1
				Value = 0
				For i = 0 To 31
					If GY_ButtonDown(BSlotsMine(i)) = True And Me\Inventory\Items[i + SlotI_Backpack] <> Null
						Value = Value - (Me\Inventory\Items[i + SlotI_Backpack]\Item\Value * TradeAmountsMine(i))
					EndIf
					If GY_ButtonDown(BSlotsHis(i)) = True And TradeItems(i) <> Null
						Value = Value + (TradeItems(i)\Item\Value * TradeAmountsHis(i))
					EndIf
				Next
			Else
				Value = 0
			EndIf

			; Check I can afford it
			If Value <= Me\Gold
				; Check I have enough space in my inventory
				If True
					; Tell server
					Pa$ = ""
					For i = 0 To 31
						If GY_ButtonDown(BSlotsHis(i)) = True And TradeItems(i) <> Null
							Pa$ = Pa$ + RCE_StrFromInt$(ServerTradeIDs(i), 4) + RCE_StrFromInt$(TradeAmountsHis(i), 2)
						Else
							Pa$ = Pa$ + RCE_StrFromInt$(-1, 4) + RCE_StrFromInt$(0, 2)
						EndIf
					Next
					For i = 0 To 31
						If GY_ButtonDown(BSlotsMine(i)) = True And Me\Inventory\Items[i + SlotI_Backpack] <> Null
							Pa$ = Pa$ + RCE_StrFromInt$(i + SlotI_Backpack, 1) + RCE_StrFromInt$(TradeAmountsMine(i), 2)
						Else
							Pa$ = Pa$ + RCE_StrFromInt$(0, 1) + RCE_StrFromInt$(0, 2)
						EndIf
					Next
					RCE_Send(Connection, PeerToHost, P_OpenTrading, Pa$, True)

					; Close window
					GY_GadgetAlpha(WTrading, 0.0, True)
					GY_Modal = False
					TradingVisible = False
				Else
					GY_MessageBox(LanguageString$(LS_Trading), LanguageString$(LS_TradingNoSpace))
				EndIf
			Else
				GY_MessageBox(LanguageString$(LS_Trading), LanguageString$(LS_TradingNoMoney))
			EndIf
		; Player -> player trading
		ElseIf TradeType = 2
			; Tell server I have accepted, what I have sold, and what I expect to receive
			Pa$ = RCE_StrFromInt$(TradeCost, 4)
			For i = 0 To 31
				Pa$ = Pa$ + RCE_StrFromInt$(ServerTradeIDs(i), 1) + RCE_StrFromInt$(TradeAmounts(i), 2)
			Next
			For i = 0 To 31
				Pa$ = Pa$ + RCE_StrFromInt$(TradeAmountsMine(i), 2)
			Next
			RCE_Send(Connection, PeerToHost, P_OpenTrading, Pa$, True)
		EndIf
	EndIf
	; Check for buy/sell buttons being pressed
	For i = 0 To 31
		; Buy his stuff
		If GY_ButtonHit(BSlotsHis(i))
			; Only if he's an NPC or scenery
			If TradeType = 1 Or TradeType = 3
				If GY_ButtonDown(BSlotsHis(i))
					If TradeAmounts(i) = 1 Or KeyDown(42) Or KeyDown(54)
						TradeAmountsHis(i) = 1
					ElseIf KeyDown(29) Or KeyDown(157)
						TradeAmountsHis(i) = TradeAmounts(i)
					Else
						AmountSlot = i + 1000
						GY_GadgetAlpha(WAmount, 1.0, True)
						GY_ActivateWindow(WAmount)
						GY_UpdateTextField(TAmount, TradeAmounts(i))
						GY_ActivateTextField(TAmount)
						AmountVisible = True
					EndIf
				EndIf
				UpdateTrading()
			Else
				GY_SetButtonState(BSlotsHis(i), False)
			EndIf
		; Sell my stuff
		ElseIf GY_ButtonHit(BSlotsMine(i))
			If GY_ButtonDown(BSlotsMine(i)) = True
				If Me\Inventory\Amounts[i + SlotI_Backpack] = 1 Or KeyDown(42) Or KeyDown(54)
					TradeAmountsMine(i) = 1
					If TradeType = 2 Then RCE_Send(Connection, PeerToHost, P_UpdateTrading, RCE_StrFromInt$(i, 1) + RCE_StrFromInt$(1, 2), True)
				ElseIf KeyDown(29) Or KeyDown(157)
					TradeAmountsMine(i) = Me\Inventory\Amounts[i + SlotI_Backpack]
					If TradeType = 2 Then RCE_Send(Connection, PeerToHost, P_UpdateTrading, RCE_StrFromInt$(i, 1) + RCE_StrFromInt$(TradeAmountsMine(i), 2), True)
				Else
					AmountSlot = i + 2000
					GY_GadgetAlpha(WAmount, 1.0, True)
					GY_ActivateWindow(WAmount)
					GY_UpdateTextField(TAmount, Me\Inventory\Amounts[i + SlotI_Backpack])
					GY_ActivateTextField(TAmount)
					AmountVisible = True
				EndIf
			Else
				TradeAmountsMine(i) = 0
				If TradeType = 2 Then RCE_Send(Connection, PeerToHost, P_UpdateTrading, RCE_StrFromInt$(i, 1) + RCE_StrFromInt$(0, 2), True)
			EndIf
			UpdateTrading()
		EndIf
	Next

	; Inventory drop button clicked
	If GY_ButtonHit(BInventoryDrop) > 0
		If MouseSlotItem <> Null
			Result = InventoryDrop(Me, MouseSlotSource, MouseSlotAmount, True)
			If Result <> 0
				If Me\Inventory\Amounts[MouseSlotSource] > 0
					GY_SetButtonState(BSlots(MouseSlotSource), False)
					GYG.GY_Gadget = Object.GY_Gadget(BSlots(MouseSlotSource))
					EntityTexture GYG\EN, GetTexture(Me\Inventory\Items[MouseSlotSource]\Item\ThumbnailTexID)
					Amount = Me\Inventory\Amounts[MouseSlotSource]
					If Amount > 1
						GY_SetButtonLabel(BSlots(MouseSlotSource), Amount, 100, 255, 0, True)
					Else
						GY_SetButtonLabel(BSlots(MouseSlotSource), "")
					EndIf
				Else
					GY_SetButtonState(BSlots(MouseSlotSource), True)
					GY_SetButtonLabel(BSlots(MouseSlotSource), "")
					GYG.GY_Gadget = Object.GY_Gadget(BSlots(MouseSlotSource))
					GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
					EntityTexture GYB\Gadget\EN, GYB\UserTexture
				EndIf
				HideEntity MouseSlotEN : MouseSlotItem = Null : MouseSlotAmount = 0 : MouseSlotSource = -1
				EnableInventoryBlanks(True)
				UpdateActorItems(Me)
			EndIf
		EndIf
		
		
		
		;-----------------------------------------------------------------------------------------------------------------
	; Inventory use button clicked
	ElseIf GY_ButtonHit(BInventoryEat) > 0
		If MouseSlotItem <> Null
			UseItem(MouseSlotSource, MouseSlotAmount)
			If Me\Inventory\Amounts[MouseSlotSource] > 0
				GY_SetButtonState(BSlots(MouseSlotSource), False)
				GYG.GY_Gadget = Object.GY_Gadget(BSlots(MouseSlotSource))
				EntityTexture GYG\EN, GetTexture(Me\Inventory\Items[MouseSlotSource]\Item\ThumbnailTexID)
				Amount = Me\Inventory\Amounts[MouseSlotSource]
				If Amount > 1
					GY_SetButtonLabel(BSlots(MouseSlotSource), Amount, 100, 255, 0, True)
				Else
					GY_SetButtonLabel(BSlots(MouseSlotSource), "")
				EndIf
			Else
				GY_SetButtonState(BSlots(MouseSlotSource), True)
				GY_SetButtonLabel(BSlots(MouseSlotSource), "")
				GYG.GY_Gadget = Object.GY_Gadget(BSlots(MouseSlotSource))
				GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
				EntityTexture GYB\Gadget\EN, GYB\UserTexture
			EndIf
			HideEntity MouseSlotEN : MouseSlotItem = Null : MouseSlotAmount = 0 : MouseSlotSource = -1
			EnableInventoryBlanks(True)
		EndIf
	EndIf
	
	;-----------------------

	; Inventory slot clicked
	For i = 0 To Slots_Inventory
		If GY_ButtonHit(BSlots(i)) > 0
			; Mouse is empty - pick up item
			If MouseSlotItem = Null
				; Pick up all items
				If Me\Inventory\Amounts[i] = 1 Or KeyDown(29) Or KeyDown(157)
					ShowEntity MouseSlotEN
					EntityTexture MouseSlotEN, GetTexture(Me\Inventory\Items[i]\Item\ThumbnailTexID)
					GYG.GY_Gadget = Object.GY_Gadget(BSlots(i))
					GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
					EntityTexture GYB\Gadget\EN, GYB\UserTexture
					GY_SetButtonLabel(BSlots(i), "")
					MouseSlotItem = Me\Inventory\Items[i]
					MouseSlotAmount = Me\Inventory\Amounts[i]
					MouseSlotSource = i
					EnableInventoryBlanks()
				; Pick up just one item
				ElseIf KeyDown(42) Or KeyDown(54)
					ShowEntity MouseSlotEN
					EntityTexture MouseSlotEN, GetTexture(Me\Inventory\Items[i]\Item\ThumbnailTexID)
					MouseSlotItem = Me\Inventory\Items[i]
					MouseSlotAmount = 1
					MouseSlotSource = i
					If Me\Inventory\Amounts[i] > 1 Then GY_SetButtonState(BSlots(i), False)
					If Me\Inventory\Amounts[i] > 2
						GY_SetButtonLabel(BSlots(i), Me\Inventory\Amounts[i] - 1, 100, 255, 0, True)
					Else
						GY_SetButtonLabel(BSlots(i), "")
					EndIf
					EnableInventoryBlanks()
				; Display amount dialog
				Else
					AmountSlot = i
					GY_GadgetAlpha(WAmount, 1.0, True)
					GY_ActivateWindow(WAmount)
					GY_UpdateTextField(TAmount, Me\Inventory\Amounts[i])
					GY_ActivateTextField(TAmount)
					GY_Modal = True
					AmountVisible = True
				EndIf
			; Mouse is full - swap or drop/add to stack
			Else
				; Putting it back where it came from
				If i = MouseSlotSource
					GY_SetButtonState(BSlots(i), False)
					GYG.GY_Gadget = Object.GY_Gadget(BSlots(i))
					EntityTexture GYG\EN, GetTexture(Me\Inventory\Items[i]\Item\ThumbnailTexID)
					HideEntity MouseSlotEN : MouseSlotItem = Null : MouseSlotAmount = 0 : MouseSlotSource = -1
					EnableInventoryBlanks(True)
					If Me\Inventory\Amounts[i] > 1
						GY_SetButtonLabel(BSlots(i), Me\Inventory\Amounts[i], 100, 255, 0, True)
					Else
						GY_SetButtonLabel(BSlots(i), "")
					EndIf
				; There is an item in the destination
				ElseIf Me\Inventory\Items[i] <> Null
					; Add to stack
					IsIdentical = ItemInstancesIdentical(MouseSlotItem, Me\Inventory\Items[i])
					If (MouseSlotItem = Me\Inventory\Items[i] Or IsIdentical = True) And Me\Inventory\Items[i]\Item\Stackable = True
						Result = InventoryAdd(Me, MouseSlotSource, i, MouseSlotAmount)
						If Result = True
							GY_SetButtonState(BSlots(i), False)
							GYG.GY_Gadget = Object.GY_Gadget(BSlots(i))
							EntityTexture GYG\EN, GetTexture(Me\Inventory\Items[i]\Item\ThumbnailTexID)
							If Me\Inventory\Amounts[i] > 1
								GY_SetButtonLabel(BSlots(i), Me\Inventory\Amounts[i], 100, 255, 0, True)
							Else
								GY_SetButtonLabel(BSlots(i), "")
							EndIf
							If Me\Inventory\Amounts[MouseSlotSource] > 0
								GY_SetButtonState(BSlots(MouseSlotSource), False)
								GYG.GY_Gadget = Object.GY_Gadget(BSlots(MouseSlotSource))
								EntityTexture GYG\EN, GetTexture(Me\Inventory\Items[MouseSlotSource]\Item\ThumbnailTexID)
								Amount = Me\Inventory\Amounts[MouseSlotSource]
								If Amount > 1
									GY_SetButtonLabel(BSlots(MouseSlotSource), Amount, 100, 255, 0, True)
								Else
									GY_SetButtonLabel(BSlots(MouseSlotSource), "")
								EndIf
							Else
								GY_SetButtonState(BSlots(MouseSlotSource), True)
								GY_SetButtonLabel(BSlots(MouseSlotSource), "")
								GYG.GY_Gadget = Object.GY_Gadget(BSlots(MouseSlotSource))
								GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
								EntityTexture GYB\Gadget\EN, GYB\UserTexture
							EndIf
							HideEntity MouseSlotEN : MouseSlotItem = Null : MouseSlotAmount = 0 : MouseSlotSource = -1
							EnableInventoryBlanks(True)
						Else
							GY_SetButtonState(BSlots(i), False)
						EndIf
					; Swap
					ElseIf Me\Inventory\Amounts[MouseSlotSource] = MouseSlotAmount
						Result = InventorySwap(Me, MouseSlotSource, i)
						If Result = True
							GY_SetButtonState(BSlots(i), False)
							GY_SetButtonState(BSlots(MouseSlotSource), True)
							GYG.GY_Gadget = Object.GY_Gadget(BSlots(i))
							EntityTexture GYG\EN, GetTexture(Me\Inventory\Items[i]\Item\ThumbnailTexID)
							If Me\Inventory\Amounts[i] > 1
								GY_SetButtonLabel(BSlots(i), Me\Inventory\Amounts[i], 100, 255, 0, True)
							Else
								GY_SetButtonLabel(BSlots(i), "")
							EndIf
							UpdateActorItems(Me)
							MouseSlotItem = Me\Inventory\Items[MouseSlotSource]
							MouseSlotAmount = Me\Inventory\Amounts[MouseSlotSource]
							EntityTexture MouseSlotEN, GetTexture(Me\Inventory\Items[MouseSlotSource]\Item\ThumbnailTexID)
						Else
							GY_SetButtonState(BSlots(i), False)
						EndIf
					Else
						GY_SetButtonState(BSlots(i), False)
					EndIf
				; No item, drop it here
				Else
					Result = InventorySwap(Me, MouseSlotSource, i, MouseSlotAmount)
					If Result = True
						GYG.GY_Gadget = Object.GY_Gadget(BSlots(i))
						EntityTexture GYG\EN, GetTexture(Me\Inventory\Items[i]\Item\ThumbnailTexID)
						If Me\Inventory\Amounts[i] > 1
							GY_SetButtonLabel(BSlots(i), Me\Inventory\Amounts[i], 100, 255, 0, True)
						Else
							GY_SetButtonLabel(BSlots(i), "")
						EndIf
						UpdateActorItems(Me)
						HideEntity MouseSlotEN : MouseSlotItem = Null : MouseSlotAmount = 0 : MouseSlotSource = -1
						EnableInventoryBlanks(True)
					Else
						GY_SetButtonState(BSlots(i), True)
					EndIf
				EndIf
			EndIf
		; Item right clicked - use it
		ElseIf GY_ButtonRightHit(BSlots(i)) And MouseSlotItem = Null
			UseItem(i, 1)
		EndIf
	Next

	; Toggle inventory window
	If EscapeHit And GY_ButtonDown(BInventory) = True And GY_WindowActive(WInventory)
		GY_SetButtonState(BInventory, False)
		EscapeHit = False
	EndIf
	If (KeyHit(48) And ChatEntry\Alpha# < 0.5 And First TextInput = Null) Or GY_WindowClosed(WInventory) ;Changed keybindings to B
		GY_SetButtonState(BInventory, Not InventoryVisible)
		PlaySound(GY_SBeep)
	EndIf
	If GY_ButtonDown(BInventory) <> InventoryVisible
		If GY_Modal = False
			InventoryVisible = GY_ButtonDown(BInventory)
			; Show inventory
			If InventoryVisible = True
				GY_GadgetAlpha(WInventory, 1.0, True);0.75
				GY_UpdateLabel(LInventoryGold, Money$(Me\Gold))
				GY_LockGadget(BInventoryDrop)
				GY_LockGadget(BInventoryEat)
				GY_ActivateWindow(WInventory)
				; Display thumbnails
				LockTextures()
				For i = 0 To Slots_Inventory
					If BSlots(i) <> 0
						GYG.GY_Gadget = Object.GY_Gadget(BSlots(i))
						GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
						EntityTexture GYB\Gadget\EN, GYB\UserTexture
						GY_SetButtonState(BSlots(i), True)
						GY_LockGadget(BSlots(i), True)
						If Me\Inventory\Items[i] <> Null
							EntityTexture GYB\Gadget\EN, GetTexture(Me\Inventory\Items[i]\Item\ThumbnailTexID)
							GY_SetButtonState(BSlots(i), False)
							If Me\Inventory\Amounts[i] > 1
								GY_SetButtonLabel(BSlots(i), Me\Inventory\Amounts[i], 100, 255, 0, True)
							Else
								GY_SetButtonLabel(BSlots(i), "")
							EndIf
							GY_LockGadget(BSlots(i), False)
						Else
							GY_SetButtonLabel(BSlots(i), "")
						EndIf
					EndIf
				Next
				UnlockTextures()

				; Hide spellbook when showing inventory to prevent conflicts with the mouse slot
				GY_SetButtonState(BSpells, False)
				GY_GadgetAlpha(WSpells, 0.0, True)
				GY_LockGadget(WSpells, False)
				If SpellRemoveNum > -1
					GY_GadgetAlpha(WSpellRemove, 0.0, True)
					GY_LockGadget(WSpellRemove, True)
					GY_Modal = False
				EndIf
				HideEntity MouseSlotEN
				MouseSlotAmount = 0
				MouseSlotSource = -1
			; Hide inventory
			Else
				If WTooltip <> 0 Then GY_FreeGadget(WTooltip) : WTooltip = 0
				If AmountVisible = True Then GY_Modal = False : GY_GadgetAlpha(WAmount, 0.0, True) : AmountVisible = False
				GY_GadgetAlpha(WInventory, 0.0, True)
				GY_LockGadget(WInventory, False)
				If MouseSlotItem <> Null
					MouseSlotItem = Null
					MouseSlotAmount = 0
					HideEntity MouseSlotEN
				EndIf
			EndIf
		Else
			GY_SetButtonState(BInventory, False)
		EndIf
	EndIf
	If WItemWindow <> 0
		If GY_WindowClosed(WItemWindow)
			GY_FreeGadget(WItemWindow)
			WItemWindow = 0
			If ItemWindowFromInventory = True
				GY_SetButtonState(BInventory, True)
			EndIf
		EndIf
	EndIf

	; Toggle quest log window
	If EscapeHit And GY_ButtonDown(BQuestLog) = True And GY_WindowActive(WQuestLog)
		GY_SetButtonState(BQuestLog, False)
		EscapeHit = False
	EndIf
	If (KeyHit(38) And ChatEntry\Alpha# < 0.5 And First TextInput = Null) Or GY_WindowClosed(WQuestLog) ; Changed keybinding to L
		GY_SetButtonState(BQuestLog, Not QuestLogVisible)
		PlaySound(GY_SBeep)
	EndIf
	If GY_ButtonDown(BQuestLog) <> QuestLogVisible
		If GY_Modal = False
			QuestLogVisible = GY_ButtonDown(BQuestLog)
			If QuestLogVisible = True
				GY_GadgetAlpha(WQuestLog, 1.0, True)
				GY_ActivateWindow(WQuestLog)
				RedrawQuestLog()
			Else
				GY_GadgetAlpha(WQuestLog, 0.0, True)
				GY_LockGadget(WQuestLog, False)
			EndIf
		Else
			GY_SetButtonState(BQuestLog, False)
		EndIf
	EndIf

	; Toggle party window
	If EscapeHit And GY_ButtonDown(BParty) = True And GY_WindowActive(WParty)
		GY_SetButtonState(BParty, False)
		EscapeHit = False
	EndIf
	If (KeyHit(25) And ChatEntry\Alpha# < 0.5 And First TextInput = Null) Or GY_WindowClosed(WParty)
		GY_SetButtonState(BParty, Not PartyVisible)
		PlaySound(GY_SBeep)
	EndIf
	If GY_ButtonDown(BParty) <> PartyVisible
		If GY_Modal = False
			PartyVisible = GY_ButtonDown(BParty)
			If PartyVisible = True
				GY_GadgetAlpha(WParty, 1.0, True)
				GY_ActivateWindow(WParty)
			Else
				GY_GadgetAlpha(WParty, 0.0, True)
				GY_LockGadget(WParty, False)
			EndIf
		Else
			GY_SetButtonState(BParty, False)
		EndIf
	EndIf
		; Toggle Itemshop window Cysis145 BItemShop WItemShop
;	If EscapeHit And GY_ButtonDown(BItemShop) = True And GY_WindowActive(WItemShop)
;		GY_SetButtonState(BItemShop, False)
;		EscapeHit = False
;	EndIf
;	If (KeyHit(25) And ChatEntry\Alpha# < 0.5 And First TextInput = Null) Or GY_WindowClosed(WItemShop)
;		GY_SetButtonState(BItemShop, Not ItemShopVisible)
;		PlaySound(GY_SBeep)
;	EndIf
;	If GY_ButtonDown(BItemShop) <> ItemShopVisible
;		If GY_Modal = False
;			ItemShopVisible = GY_ButtonDown(BItemShop)
;			If ItemShopVisible = True
;				GY_GadgetAlpha(WItemShop, 0.85, True)
;				GY_ActivateWindow(WItemShop)
;			Else
;				GY_GadgetAlpha(WItemShop, 0.0, True)
;				GY_LockGadget(WItemShop, False)
;			EndIf
;		Else
;			GY_SetButtonState(BItemShop, False)
;		EndIf
;	EndIf


	; Toggle character stats window
	If EscapeHit And GY_ButtonDown(BCharStats) = True And GY_WindowActive(WCharStats)
		GY_SetButtonState(BCharStats, False)
		EscapeHit = False
	EndIf
	If (KeyHit(46) And ChatEntry\Alpha# < 0.5 And First TextInput = Null) Or GY_WindowClosed(WCharStats)
		GY_SetButtonState(BCharStats, Not CharStatsVisible)
		PlaySound(GY_SBeep)
	EndIf
	If GY_ButtonDown(BCharStats) <> CharStatsVisible
		If GY_Modal = False ;this may need to be commented out.
			CharStatsVisible = GY_ButtonDown(BCharStats)
			If CharStatsVisible = True
				GY_GadgetAlpha(WCharStats, 1.0, True)
				GY_ActivateWindow(WCharStats)
			Else
				GY_GadgetAlpha(WCharStats, 0.0, True)
				GY_LockGadget(WCharStats, False)
			EndIf
		Else
			GY_SetButtonState(BCharStats, False)
		EndIf
	EndIf
	


	; Toggle spellbook
	If EscapeHit And GY_ButtonDown(BSpells) = True And GY_WindowActive(WSpells)
		GY_SetButtonState(BSpells, False)
		EscapeHit = False
	EndIf
	If (KeyHit(37) And ChatEntry\Alpha# < 0.5 And First TextInput = Null) Or GY_WindowClosed(WSpells) ;Changed keybinding to K
		GY_SetButtonState(BSpells, Not SpellsVisible)
		PlaySound(GY_SBeep)
	EndIf
	If GY_ButtonDown(BSpells) <> SpellsVisible
		If GY_Modal = False
			SpellsVisible = GY_ButtonDown(BSpells)
			; Show spellbook
			If SpellsVisible = True
				GY_GadgetAlpha(WSpells, 1.0, True)
				GY_ActivateWindow(WSpells)
				UpdateSpellbook()

				; Turn inventory off when turning spellbook on to prevent conflicts with the mouse slot
				If WTooltip <> 0 Then GY_FreeGadget(WTooltip) : WTooltip = 0
				If AmountVisible = True Then GY_Modal = False : GY_GadgetAlpha(WAmount, 0.0, True) : AmountVisible = False
				GY_GadgetAlpha(WInventory, 0.0, True)
				GY_LockGadget(WInventory, False)
				If MouseSlotItem <> Null
					MouseSlotItem = Null
					MouseSlotAmount = 0
					HideEntity MouseSlotEN
				EndIf
				GY_SetButtonState(BInventory, False)
			; Hide spellbook
			Else
				HideEntity MouseSlotEN : MouseSlotAmount = 0 : MouseSlotSource = -1
				GY_GadgetAlpha(WSpells, 0.0, True)
				GY_LockGadget(WSpells, False)
				If SpellRemoveNum > -1
					GY_GadgetAlpha(WSpellRemove, 0.0, True)
					GY_LockGadget(WSpellRemove, True)
					GY_Modal = False
				EndIf
			EndIf
		Else
			GY_SetButtonState(BSpells, False)
		EndIf
	EndIf
	
		;#######################################################################################################
	;	Toggle menu window added by clan_fd, ESC options added by terrier [013]
	If  EscapeHit And GY_ButtonDown(BMenu) = True And GY_WindowActive(WMenu) 
		GY_SetButtonState(BMenu, False)
		EscapeHit = False
	EndIf
	
	If EscapeHit Or GY_WindowClosed(WMenu)
		GY_SetButtonState(BMenu, Not MenuVisible)
		PlaySound(GY_SBeep)
	EndIf
	
	If GY_ButtonDown(BMenu) <> MenuVisible
		If GY_Modal = False
			MenuVisible = GY_ButtonDown(BMenu)  
			If MenuVisible = True
				GY_GadgetAlpha(WMenu, 1.0, True)
				GY_ActivateWindow(WMenu)
			Else
				GY_GadgetAlpha(WMenu, 0.0, True)
			EndIf
		Else
			GY_SetButtonState(BMenu, False)
		EndIf
	EndIf

	;	End edit
	;#######################################################################################################

	
	
	; Toggle help window
	If EscapeHit And GY_ButtonDown(BHelp) = True And GY_WindowActive(WHelp)
		GY_SetButtonState(BHelp, False)
		EscapeHit = False
	EndIf
	If GY_WindowClosed(WHelp)
		GY_SetButtonState(BHelp, Not HelpVisible)
		PlaySound(GY_SBeep)
	EndIf
	If GY_ButtonDown(BHelp) <> HelpVisible
		If GY_Modal = False
			HelpVisible = GY_ButtonDown(BHelp)
			If HelpVisible = True
				GY_GadgetAlpha(WHelp, 1.0, True)
				GY_ActivateWindow(WHelp)
			Else
				GY_GadgetAlpha(WHelp, 0.0, True)
			EndIf
		Else
			GY_SetButtonState(BHelp, False)
		EndIf
	EndIf

	; Toggle large map
	If EscapeHit And GY_ButtonDown(BMap) = True And GY_WindowActive(WLargeMap)
		GY_SetButtonState(BMap, False)
		EscapeHit = False
	EndIf
	If (KeyHit(50) And ChatEntry\Alpha# < 0.5 And First TextInput = Null) Or GY_WindowClosed(WLargeMap)
		GY_SetButtonState(BMap, Not LargeMapVisible)
		PlaySound(GY_SBeep)
	EndIf
	If GY_ButtonDown(BMap) <> LargeMapVisible
		If GY_Modal = False
			LargeMapVisible = GY_ButtonDown(BMap)
			If LargeMapVisible = True
				GY_GadgetAlpha(WLargeMap, 1.0, True)
				GY_ActivateWindow(WLargeMap)
			Else
				GY_GadgetAlpha(WLargeMap, 0.0, True)
			EndIf
		Else
			GY_SetButtonState(BMap, False)
		EndIf
	EndIf

	; Toggle radar
	If KeyHit(19) And ChatEntry\Alpha# < 0.5 And First TextInput = Null
		ShowRadar = Not ShowRadar
		PlaySound(GY_SBeep)
		If ShowRadar = True
			Show_Radar()
		Else
			Hide_Radar()
		EndIf
	EndIf

	; Quit  modified by terrier [013]****************************************************************
		If QuitTimer = True
		;If EscapeHit
			QuitActive = True
			QuitActiveMS = MilliSecs()
			Me\DestX# = EntityX#(Me\CollisionEN)
			Me\DestZ# = EntityZ#(Me\CollisionEN)
			QuitTimer = False
			GY_GadgetAlpha(QuitBar, 1.0)
			GY_GadgetAlpha(QuitLabel, 1.0)
		ElseIf MilliSecs() - QuitTimerMS > 5000
			QuitTimer = False
	;	EndIf
	;Else
	;	If EscapeHit
	;		Output(LanguageString$(LS_EscapeAgainToQuit), 255, 255, 0)
	;		QuitTimer = True
	;		QuitTimerMS = MilliSecs()
	;	EndIf**************************************
	EndIf
	
	;&&&&&&&&&&
	; Change quit/log out progress
	
	;	ORIGINAL CODE
	;If QuitActive = True
	;	Progress = (MilliSecs() - QuitActiveMS) / 100
	;	GY_UpdateProgressBar(QuitBar, Progress)
	;	If Progress >= 100 Then QuitComplete = True
	;Else
	;	GY_GadgetAlpha(QuitBar, 0.0)
	;	GY_GadgetAlpha(QuitLabel, 0.0)
	;EndIf
	
	;	NEW CODE
	If (QuitActive = True Or LogOutActive = True)
		Progress = (MilliSecs() - QuitActiveMS) / 100
		GY_UpdateProgressBar(QuitBar, Progress)
	Else
		GY_GadgetAlpha(QuitBar, 0.0)
		GY_GadgetAlpha(QuitLabel, 0.0)
		GY_GadgetAlpha(LogOutLabel, 0.0)
	EndIf
	
	; Exit
	If QuitActive = True
		If Progress >= 100 Then QuitComplete = True
	; Log out
	;ElseIf LogOutActive = True
	;	If Progress >= 100
	;		UnloadArea()
	;		UnloadGame()
	;		LoadOptions()
	;		;InitialSetup()
	;		ClientMode = 0
	;		LogOutActive = False
	;		Return -1
	;	EndIf
	EndIf
	;***********************************************************************************************

	; Attribute displays
	For i = 0 To 39
		If AttributeDisplays(i)\Component <> 0
			GY_UpdateProgressBar(AttributeDisplays(i)\Component, (Float#(Me\Attributes\Value[i]) / Float#(Me\Attributes\Maximum[i])) * 100.0)
			GY_UpdateLabel(AttributeDisplayNumbers(i), Str$(Me\Attributes\Value[i]) + " / " + Str$(Me\Attributes\Maximum[i]))	
		EndIf
	Next

	; Chat text history buttons
	If GY_ButtonHit(BHistoryMode)
		HistoryMode = Not HistoryMode
		; Hide history
		If HistoryMode = False
			GYG.GY_Gadget = Object.GY_Gadget(BHistoryMode)
			EntityTexture(GYG\EN, ActionBarDownTex)
			GY_PositionGadget(BHistoryMode, GY_GadgetX#(BHistoryMode), Chat\Y# + 0.005)
			GY_GadgetAlpha(BHistoryUp, 0.0)
			GY_GadgetAlpha(BHistoryDown, 0.0)
			UpdateChatTextDisplay()
		; Show history
		Else
			GYG.GY_Gadget = Object.GY_Gadget(BHistoryMode)
			EntityTexture(GYG\EN, ActionBarUpTex)
			GY_PositionGadget(BHistoryMode, GY_GadgetX#(BHistoryMode), Chat\Y# + Chat\Height# - 0.02)
			GY_GadgetAlpha(BHistoryUp, 0.85)
			GY_GadgetAlpha(BHistoryDown, 0.85)
			FirstHistoryLine = MaxHistoryLine - MaxChatLine
			If FirstHistoryLine < 0 Then FirstHistoryLine = 0
			UpdateChatTextDisplay()
		EndIf
	EndIf

	; Scroll up through history
	If GY_ButtonHit(BHistoryUp)
		If FirstHistoryLine > 0
			FirstHistoryLine = FirstHistoryLine - 1
			UpdateChatTextDisplay()
		EndIf
	; Scroll down through history
	ElseIf GY_ButtonHit(BHistoryDown)
		If FirstHistoryLine < MaxHistoryLine
			FirstHistoryLine = FirstHistoryLine + 1
			UpdateChatTextDisplay()
		EndIf
	EndIf

;Increased Chat fader timer from 10 to 15 seconds cysis145
	; Make current chat text disappear after 15 seconds
	For CC.CurrentChat = Each CurrentChat
		If MilliSecs() - CC\Timer > 15000
			Delete(CC)
			; Remove from display
			If HistoryMode = False Then UpdateChatTextDisplay()
		EndIf
	Next
	
	;If ( ControlDown(29) Or ControlDown(157) ) And ControlDown(199) ; Ctrl + Home [#@#]
	;	ToggleDebugBannerVisibility()
	;EndIf
	;UpdateDebugBanner()
	
End Function

; Updates the direction of the compass
Function UpdateCompass()

	Yaw# = EntityYaw#(Me\CollisionEN)
	MinU# = Yaw# / -360.0
	Surf = GetSurface(Compass\Component, 1)
	VertexTexCoords(Surf, 0, MinU#, 1.0)
	VertexTexCoords(Surf, 1, MinU# + 1.0, 1.0)
	VertexTexCoords(Surf, 2, MinU# + 1.0, 0.0)
	VertexTexCoords(Surf, 3, MinU#, 0.0)

End Function

; Updates the spells/abilities book gadgets
Function UpdateSpellbook()

	; Clear all gadgets
	For i = 0 To 9
		GY_UpdateLabel(LSpellNames(i), "")
		GY_UpdateLabel(LSpellLevels(i), "")
		GY_UpdateLabel(LSpellDesc(i), "")
		;GY_UpdateLabel(LSpellDesc2(i), "")
		GYG.GY_Gadget = Object.GY_Gadget(BSpellImgs(i))
		GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
		EntityTexture GYB\Gadget\EN, GYB\UserTexture
		GY_LockGadget(BSpellImgs(i), True)
		GY_GadgetAlpha(BSpellImgs(i), 0.0, True)
	Next

	; Memorised spells
	If FirstSpell = -1
		GY_UpdateLabel(LSpellsPage, LanguageString$(LS_MemorisedAbilities))
		For i = 0 To 9
			; Assign memorised spell if there is one
			If Me\MemorisedSpells[i] <> 5000
				Sp.Spell = SpellsList(Me\KnownSpells[Me\MemorisedSpells[i]])
				GY_UpdateLabel(LSpellNames(i), Sp\Name$, 0, 125, 255)
				GY_UpdateLabel(LSpellLevels(i), "[Rank" + " " + Me\SpellLevels[Me\MemorisedSpells[i]] + "]", 100, 255, 0)
				
				Lett = Len(Sp\Description$)
				If Lett > 26
					GY_UpdateLabel(LSpellDesc(i), Left$(Sp\Description$,23) + "...", 255, 255, 255) ;Left$(Sp\Description$,26)
				Else
					GY_UpdateLabel(LSpellDesc(i), Left$(Sp\Description$,26), 255, 255, 255) ;Left$(Sp\Description$,26)
				EndIf

			
				
				;For T = 1 To Len(Sp\Description$)
				;GY_UpdateLabel(LSpellDesc2(i), Mid$(Sp\Description$,27,52), 255, 255, 255) ;Right$(Sp\Description$,26)
				;Next
				
				GYG.GY_Gadget = Object.GY_Gadget(BSpellImgs(i))
				EntityTexture GYG\EN, GetTexture(Sp\ThumbnailTexID)
				GY_LockGadget(BSpellImgs(i), False)
				GY_GadgetAlpha(BSpellImgs(i), 0.85, True)
			EndIf
		Next
	; Known spells
	Else
		GY_UpdateLabel(LSpellsPage, "Page" + " " + Str$((FirstSpell / 10) + 1)) ;LanguageString$(LS_Page)
		Spell = FirstSpell
		Count = 0
		Repeat
			If KnownSpellSort(Spell) > 0
				If Me\SpellLevels[KnownSpellSort(Spell) - 1] > 0
					Sp.Spell = SpellsList(Me\KnownSpells[KnownSpellSort(Spell) - 1])
					GY_UpdateLabel(LSpellNames(Count), Sp\Name$, 255, 255, 255)
					GY_UpdateLabel(LSpellLevels(Count), "[Rank" + " " + Me\SpellLevels[KnownSpellSort(Spell) - 1] + "]", 100, 255, 0)
					
					Lett = Len(Sp\Description$)
					If Lett > 26
						GY_UpdateLabel(LSpellDesc(count), Left$(Sp\Description$,23) + "...", 255, 255, 255) ;Left$(Sp\Description$,26)
					Else
						GY_UpdateLabel(LSpellDesc(count), Left$(Sp\Description$,26), 255, 255, 255) ;Left$(Sp\Description$,26)
					EndIf
					
					;GY_UpdateLabel(LSpellDesc(count), Left$(Sp\Description$,23) + "...", 255, 255, 255)
					;GY_UpdateLabel(LSpellDesc2(count), Mid$(Sp\Description$,27,52), 255, 255, 255)
					GYG.GY_Gadget = Object.GY_Gadget(BSpellImgs(Count))
					EntityTexture GYG\EN, GetTexture(Sp\ThumbnailTexID)
					GY_LockGadget(BSpellImgs(Count), False)
					GY_GadgetAlpha(BSpellImgs(Count), 0.85, True)
					; Change text colour if it's memorised
					If RequireMemorise
						For i = 0 To 9
							If Me\MemorisedSpells[i] = KnownSpellSort(Spell) - 1 Then GY_UpdateLabel(LSpellNames(Count), Sp\Name$, 255, 50, 0) : Exit
						Next
					EndIf
					Count = Count + 1
				EndIf
			EndIf
			Spell = Spell + 1
		Until Count = 10 Or Spell = 1000
	EndIf

End Function

; Updates the experience bar
Function UpdateXPBar()

	Amount# = Float#(Me\XPBarLevel) / 255.0
	ScaleEntity XPEN, Amount#, 1.0, 1.0
	Surf = GetSurface(XPEN, 1)
	VertexTexCoords(Surf, 1, Amount#, 1.0)
	VertexTexCoords(Surf, 2, Amount#, 0.0)

End Function

; Updates the trading window
Function UpdateTrading()

	If TradeType = 1 Or TradeType = 3
		Value = 0
		For i = 0 To 31
			If Me\Inventory\Amounts[i + SlotI_Backpack] <= 1
				GY_SetButtonLabel(BSlotsMine(i), "")
			Else
				GY_SetButtonLabel(BSlotsMine(i), Me\Inventory\Amounts[i + SlotI_Backpack], 100, 255, 0, True)
			EndIf
			If TradeAmounts(i) <= 1
				GY_SetButtonLabel(BSlotsHis(i), "")
			Else
				GY_SetButtonLabel(BSlotsHis(i), TradeAmounts(i), 100, 255, 0, True)
			EndIf
			If GY_ButtonDown(BSlotsMine(i)) = True And Me\Inventory\Items[i + SlotI_Backpack] <> Null
				Value = Value - (Me\Inventory\Items[i + SlotI_Backpack]\Item\Value * TradeAmountsMine(i))
				GY_SetButtonLabel(BSlotsMine(i), TradeAmountsMine(i), 255, 100, 0, True)
			EndIf
			If GY_ButtonDown(BSlotsHis(i)) = True And TradeItems(i) <> Null
				Value = Value + (TradeItems(i)\Item\Value * TradeAmountsHis(i))
				GY_SetButtonLabel(BSlotsHis(i), TradeAmountsHis(i), 255, 100, 0, True)
			EndIf
		Next
		GY_UpdateLabel(LTradingCost, LanguageString$(LS_Cost) + " " + Money$(Value))
	EndIf

End Function

; Updates actor effect icons display
Function UpdateEffectIcons()

	; Clear all slots
	For Slot.EffectIconSlot = Each EffectIconSlot
		HideEntity Slot\EN
		Slot\Effect = Null
	Next

	; Retexture
	Slot = First EffectIconSlot
	For E.EffectIcon = Each EffectIcon
		If Slot = Null Then Return
		Slot\Effect = E
		ShowEntity Slot\EN
		EntityTexture Slot\EN, GetTexture(E\TextureID)
		Slot = After Slot
	Next

End Function

;#Region Char Interaction Window
; Updates the display for the character interaction window
Function CreateCharInteractionWindow(AI.ActorInstance)

	Name$ = AI\Name$
	If Trim$(Name$) = "" Then Name$ = AI\Actor\Race$
	
	If Resolutiontype = 1
		WCharInteract = GY_CreateWindow(Name$, 0.89, 0.03, 0.27, 0.15, True, True, False, LoadTexture("Data\Textures\GUI\PartyBG.png"))
	Else
		WCharInteract = GY_CreateWindow(Name$, 0.60, 0.03, 0.27, 0.15, True, True, False, LoadTexture("Data\Textures\GUI\PartyBG.png"))
	EndIf
	GY_CreateLabel(WCharInteract, 0.05, 0.1, AttributeNames$(HealthStat) + ":")
	HealthVal = (Float#(AI\Attributes\Value[HealthStat]) / Float#(AI\Attributes\Maximum[HealthStat])) * 500.0
	SCharInteractHealth = GY_CreateProgressBar(WCharInteract, 0.3, 0.1, 0.6, 0.18, HealthVal, 500, 255, 0, 0)
	LCharInteractFaction = GY_CreateLabel(WCharInteract, 0.05, 0.35, LanguageString$(LS_Faction) + " " + FactionNames$(AI\HomeFaction))
	LCharInteractLevel = GY_CreateLabel(WCharInteract, 0.05, 0.55, LanguageString$(LS_Level) + " " + AI\Level)
	LCharInteractReputation = GY_CreateLabel(WCharInteract, 0.05, 0.75, LanguageString$(LS_Reputation) + " " + AI\Reputation)

	GY_GadgetAlpha(WCharInteract, 1.0, True)
	GY_ActivateWindow(WCharInteract)

	CharInteract = AI
	CharInteractVisible = True

End Function

Function UpdateCharInteractionWindow()
	If CharInteract = Null Then Return
	
	Local AI.ActorInstance = CharInteract
	HealthVal = (Float#(AI\Attributes\Value[HealthStat]) / Float#(AI\Attributes\Maximum[HealthStat])) * 500.0
	GY_UpdateProgressBar( SCharInteractHealth, HealthVal )
	GY_UpdateLabel( LCharInteractFaction, LanguageString$(LS_Faction) + " " + FactionNames$(AI\HomeFaction) )
	GY_UpdateLabel( LCharInteractLevel, LanguageString$(LS_Level) + " " + AI\Level )
	GY_UpdateLabel( LCharInteractReputation, LanguageString$(LS_Reputation) + " " + AI\Reputation )

End Function

Function DestroyCharInteractionWindow()
	GY_FreeGadget( WCharInteract )
	CharInteract = Null
	CharInteractVisible = False
End Function

;#End Region

; Creates the GUI stuff for the interface ready to be displayed
Function CreateInterface()

	;Widescreen Window X adjustments [=+=]
	If Resolutiontype = 1 ;16:9 ratio
		WX1# = 0.15
		WX2# = 3.0
		WX3# = 0.17
	Else
		WX1# = 0.0
		WX2# = 0.0
		WX3# = 0.0
	EndIf
	
	; Quit bar
	QuitLabel = GY_CreateLabel(0, 0.5, 0.4, LanguageString$(LS_QuitProgress), 255, 255, 255, Justify_Centre)
	QuitBar = GY_CreateProgressBar(0, 0.35, 0.43, 0.3, 0.02, 0, 100, 0, 255, 0)

	; Chat bubble
	ChatBubbleFont = GY_LoadFont("Data\UI\Fonts\Bubble", True)
	ChatBubbleEN = CreateMesh()
	Surf = CreateSurface(ChatBubbleEN)
	v1  = AddVertex(Surf, -0.1, -1.1,  0.0, 1.0, 0.0)
	v2  = AddVertex(Surf,  0.0, -1.1,  0.0, 0.9, 0.0)
	v3  = AddVertex(Surf,  1.0, -1.1,  0.0, 0.1, 0.0)
	v4  = AddVertex(Surf,  1.1, -1.1,  0.0, 0.0, 0.0)
	v5  = AddVertex(Surf, -0.1, -1.0,  0.0, 1.0, 0.1)
	v6  = AddVertex(Surf,  0.0, -1.0,  0.0, 0.9, 0.1)
	v7  = AddVertex(Surf,  1.0, -1.0,  0.0, 0.1, 0.1)
	v8  = AddVertex(Surf,  1.1, -1.0,  0.0, 0.0, 0.1)
	v9  = AddVertex(Surf, -0.1,  0.0,  0.0, 1.0, 0.66)
	v10 = AddVertex(Surf,  0.0,  0.0,  0.0, 0.9, 0.66)
	v11 = AddVertex(Surf,  1.0,  0.0,  0.0, 0.1, 0.66)
	v12 = AddVertex(Surf,  1.1,  0.0,  0.0, 0.0, 0.66)
	v13 = AddVertex(Surf, -0.1,  0.25, 0.0, 1.0, 1.0)
	v14 = AddVertex(Surf,  0.0,  0.25, 0.0, 0.9, 1.0)
	v15 = AddVertex(Surf,  1.0,  0.25, 0.0, 0.1, 1.0)
	v16 = AddVertex(Surf,  1.1,  0.25, 0.0, 0.0, 1.0)
	AddTriangle(Surf, v1,  v2,  v5)
	AddTriangle(Surf, v2,  v6,  v5)
	AddTriangle(Surf, v2,  v3,  v6)
	AddTriangle(Surf, v3,  v7,  v6)
	AddTriangle(Surf, v3,  v4,  v7)
	AddTriangle(Surf, v4,  v8,  v7)
	AddTriangle(Surf, v5,  v6,  v9)
	AddTriangle(Surf, v6,  v10, v9)
	AddTriangle(Surf, v6,  v7,  v10)
	AddTriangle(Surf, v7,  v11, v10)
	AddTriangle(Surf, v7,  v8,  v11)
	AddTriangle(Surf, v8,  v12, v11)
	AddTriangle(Surf, v9,  v10, v13)
	AddTriangle(Surf, v10, v14, v13)
	AddTriangle(Surf, v10, v11, v14)
	AddTriangle(Surf, v11, v15, v14)
	AddTriangle(Surf, v11, v12, v15)
	AddTriangle(Surf, v12, v16, v15)
	HideEntity(ChatBubbleEN)
	ChatBubbleTex = LoadTexture("Data\Textures\Bubble.png", 1 + 4)
	If ChatBubbleTex = 0 Then RuntimeError("File not found: Data\Textures\Bubble.png!")

	; Chat bar
	If ChatBar <> Null
		ChatBar\Component = CreateInterfaceQuad(GY_Cam)
		EntityTexture(ChatBar\Component, GetTexture(ChatBar\Texture))
		UnloadTexture(ChatBar\Texture)
		ScaleMesh(ChatBar\Component, ChatBar\Width# * 20.0, ChatBar\Height# * 15.0, 1.0)
		PositionEntity(ChatBar\Component, (ChatBar\X# * 10.0) - 10.0 - WX2#, (ChatBar\Y# * -15.0) + 7.5, 10.0)
		EntityAlpha(ChatBar\Component, ChatBar\Alpha#)
	EndIf

	; Attribute displays
	For i = 0 To 39
		If AttributeDisplays(i)\Width# > 0.0 And AttributeDisplays(i)\Height# > 0.0
			; Create bar
			R = AttributeDisplays(i)\R : G = AttributeDisplays(i)\G : B = AttributeDisplays(i)\B
			X# = AttributeDisplays(i)\X#
			Y# = AttributeDisplays(i)\Y#
			W# = AttributeDisplays(i)\Width#
			H# = AttributeDisplays(i)\Height#
			
			If X# < 0.5
				AttributeDisplays(i)\Component = GY_CreateProgressBar(0, X# - WX1#, Y#, W#, H#, 50, 100, R, G, B)
			ElseIf X# > 0.5
				AttributeDisplays(i)\Component = GY_CreateProgressBar(0, X# + WX1#, Y#, W#, H#, 50, 100, R, G, B)
			ElseIf X# = 0.5
				AttributeDisplays(i)\Component = GY_CreateProgressBar(0, X#, Y#, W#, H#, 50, 100, R, G, B)
			EndIf
			
			GY_GadgetAlpha(AttributeDisplays(i)\Component, 0.85, True)
			GY_DropGadget(AttributeDisplays(i)\Component)

			; Create number display
			X# = X# + (W# / 2.0)
			Y# = (Y# + (H# / 2.0)) - 0.015
			
			If X# < 0.5
				AttributeDisplayNumbers(i) = GY_CreateLabel(0, X# - WX1#, Y#, "00000 / 00000", 255, 255, 255, Justify_Centre)
			ElseIf X# > 0.5
				AttributeDisplayNumbers(i) = GY_CreateLabel(0, X# + WX1#, Y#, "00000 / 00000", 255, 255, 255, Justify_Centre)
			ElseIf X# = 0.5
				AttributeDisplayNumbers(i) = GY_CreateLabel(0, X#, Y#, "00000 / 00000", 255, 255, 255, Justify_Centre)
			EndIf
			
		EndIf
	Next

	; Large map
	WLargeMap = GY_CreateWindow(LanguageString$(LS_Map), 0.0, 0.1, 0.6, 0.6, False, True, False, 0, False)

	; Chat entry
	ChatEntry\Component = GY_CreateTextField(0, ChatEntry\X# - WX1#, ChatEntry\Y#, ChatEntry\Width#, 0, 100)
	ChatEntry\Alpha# = 0.0
	GY_GadgetAlpha(ChatEntry\Component, 0.0)

	; Compass
	Compass\Component = GY_CreateQuad(GY_Cam)
	Tex = LoadTexture("Data\Textures\Compass Overlay.PNG", 1 + 4)
	If Tex = 0 Then RuntimeError("File not found: Data\Textures\Compass Overlay.PNG!")
	EntityTexture(Compass\Component, Tex)
	FreeTexture(Tex)
	ScaleMesh(Compass\Component, Compass\Width# * 20.0, Compass\Height# * 15.0, 1.0)
	PositionEntity(Compass\Component, (Compass\X# * 20.0) - 10.0, (Compass\Y# * -15.0) + 7.5, 10.0)
	EntityOrder(Compass\Component, -1)
	EntityAlpha(Compass\Component, Compass\Alpha#)
	CompassBackground = GY_CreateQuad(GY_Cam)
	Tex = LoadTexture("Data\Textures\Compass.PNG", 1 + 4)
	If Tex = 0 Then RuntimeError("File not found: Data\Textures\Compass.PNG!")
	EntityTexture(CompassBackground, Tex)
	FreeTexture(Tex)
	ScaleMesh(CompassBackground, Compass\Width# * 20.0, Compass\Height# * 15.0, 1.0)
	PositionEntity(CompassBackground, (Compass\X# * 20.0) - 10.0, (Compass\Y# * -15.0) + 7.5, 10.0)
	EntityOrder(CompassBackground, -2)
	EntityAlpha(CompassBackground, Compass\Alpha#)
	EntityParent(CompassBackground, Compass\Component)

	; Actor effect icons
	IconsAcross = Floor#(BuffsArea\Width# / 0.0225)
	IconsDown = Floor#(BuffsArea\Height# / 0.03)
	X# = BuffsArea\X#
	Y# = BuffsArea\Y#
	For i = 1 To IconsDown
		For j = 1 To IconsAcross
			EIS.EffectIconSlot = New EffectIconSlot
			EIS\EN = GY_CreateQuad(GY_Cam)
			EntityOrder(EIS\EN, -1)
			PositionEntity(EIS\EN, (X# * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
			ScaleMesh(EIS\EN, 0.0225 * 20.0, 0.03 * 15.0, 1.0)
			HideEntity(EIS\EN)
			X# = X# + 0.0225
		Next
		X# = BuffsArea\X#
		Y# = Y# + 0.03
	Next

	; Action bar
	ActionBarUpTex = LoadTexture("Data\Textures\GUI\ArrowUp.bmp")
	If ActionBarUpTex = 0 Then RuntimeError("File not found: Data\Textures\GUI\ArrowUp.bmp!")
	ActionBarDownTex = LoadTexture("Data\Textures\GUI\ArrowDown.bmp")
	If ActionBarDownTex = 0 Then RuntimeError("File not found: Data\Textures\GUI\ArrowDown.bmp!")
	ActionEN = GY_CreateQuad(GY_Cam)
	EntityOrder ActionEN, -1

	; Use fullscreen bar
	Tex = LoadTexture("Data\Textures\GUI\Action Bar_full.dds", 1 + 2 + 16 + 32)
	If tex = 0 Then Tex = LoadTexture("Data\Textures\GUI\Action Bar_full.bmp", 1 + 4 + 16 + 32)
	If tex <> 0
		;Widescreen Action bar Scale adjustment Cysis145
		If ResolutionType = 1 ; 16:9 ratio
			PositionEntity ActionEN, -13.5, 7.5, 10.0
			ScaleMesh ActionEN, 27.0, 15.05, 1
		Else
			PositionEntity ActionEN, -10.0, 7.5, 10.0
			ScaleMesh ActionEN, 20.0, 15.0, 1
		EndIf
	EndIf
	
	; Use the regular bar if the fullscreen bar is not available
	If tex = 0 
		Tex = LoadTexture("Data\Textures\GUI\Action Bar.dds", 1 + 2 + 16 + 32)
		If tex = 0 Then Tex = LoadTexture("Data\Textures\GUI\Action Bar.bmp", 1 + 4 + 16 + 32)
		
		PositionEntity ActionEN, -10.0, -5.0, 10.0
		ScaleMesh ActionEN, 20.0, 2.5, 1.0
	EndIf
	EntityTexture(ActionEN, Tex)
	FreeTexture Tex
	
	;XP bar	
	XPEN = GY_CreateQuad(GY_Cam)
	EntityOrder XPEN, -2
	PositionEntity XPEN, -6.416015625, -5.0, 10.0 : ScaleMesh XPEN, 12.83203125, 2.5, 1.0
	Tex = LoadTexture("Data\Textures\GUI\Action Bar XP.bmp", 1 + 4 + 16 + 32)
	EntityTexture(XPEN, Tex)
	FreeTexture Tex
	UpdateXPBar()
	
	;Widescreen Adjustments
	If ResolutionType = 1 ; 16:9 ratio
		;Function Action Buttons
		BChat = CreateActionBarButton("Chat.bmp", 0.72) 
		BMap = CreateActionBarButton("Map.bmp", 0.76)
		BInventory = CreateActionBarButton("Inventory.bmp", 0.80)
		BSpells = CreateActionBarButton("Abilities.bmp", 0.84)
		BCharStats = CreateActionBarButton("Character.bmp", 0.88)
		BQuestLog = CreateActionBarButton("Quests.bmp", 0.92)
		BParty = CreateActionBarButton("Party.bmp", 0.96)
		BMenu = CreateActionBarButton("Menu.bmp", 1.0)
		;Action Bar Swap
		BPrevBar = GY_CreateButton(0, 0.34, 0.9415 - 0.001364583334, 0.02, 0.015, "", False, 0, 0, 0, ActionBarUpTex)
		BNextBar = GY_CreateButton(0, 0.34, 0.9655 - 0.001364583334, 0.02, 0.015, "", False, 0, 0, 0, ActionBarDownTex)
	Else
		;Function Action Buttons
		BChat = CreateActionBarButton("Chat.bmp", 0.62890625) 
		BMap = CreateActionBarButton("Map.bmp", 0.666015625)
		BInventory = CreateActionBarButton("Inventory.bmp", 0.702148437)
		BSpells = CreateActionBarButton("Abilities.bmp", 0.739257812)
		BCharStats = CreateActionBarButton("Character.bmp", 0.77734375)
		BQuestLog = CreateActionBarButton("Quests.bmp", 0.813476562)
		BParty = CreateActionBarButton("Party.bmp", 0.850585937)
		BMenu = CreateActionBarButton("Menu.bmp", 0.89)
		;Action Bar Swap
		BPrevBar = GY_CreateButton(0, 0.53, 0.9415 - 0.001364583334, 0.02, 0.015, "", False, 0, 0, 0, ActionBarUpTex)
		BNextBar = GY_CreateButton(0, 0.53, 0.9655 - 0.001364583334, 0.02, 0.015, "", False, 0, 0, 0, ActionBarDownTex)
	EndIf

	GY_DropGadget(BPrevBar)
	GY_DropGadget(BNextBar)
	
	For i = 0 To 11
		If ResolutionType = 1 ; 16:9 ratio
			BActionBar(i) = CreateActionBarButtonSlots("EmptySlot.bmp", 0.0 + (Float#(i) * 0.036132812), False, False)
		Else
			BActionBar(i) = CreateActionBarButtonSlots("EmptySlot.bmp", 0.089867187 + (Float#(i) * 0.036132812), False, False)
		EndIf
	Next

	; Chat text display
	MaxChatLine = Int(Floor#(Chat\Height# / 0.025)) - 1
	Dim ChatLines(MaxChatLine)
	Y# = Chat\Y#
	X# = Chat\X#
	If Chat\X# <= 0.5 Then X# = X# + 0.035
	For i = 0 To MaxChatLine
		ChatLines(i) = GY_CreateLabel(0, X# - WX1#, Y#, String$(" ", 200))
		
		GY_UpdateLabel(ChatLines(i), "")
		GY_DropGadget(ChatLines(i))
		Y# = Y# + 0.025
	Next
	X# = Chat\X# + 0.005
	If Chat\X# > 0.5 Then X# = Chat\X# + Chat\Width# - 0.025
	
	BHistoryMode = GY_CreateButton(0, X# - WX1#, Chat\Y# + 0.005, 0.02, 0.015, "", False, 0, 0, 0, ActionBarDownTex)
	BHistoryUp = GY_CreateButton(0, X# - WX1#, Chat\Y# + 0.005, 0.02, 0.015, "", False, 0, 0, 0, ActionBarUpTex)
	BHistoryDown = GY_CreateButton(0, X# - WX1#, Chat\Y# + 0.025, 0.02, 0.015, "", False, 0, 0, 0, ActionBarDownTex)

	GY_GadgetAlpha(BHistoryMode, 0.85)
	GY_GadgetAlpha(BHistoryUp, 0.0)
	GY_GadgetAlpha(BHistoryDown, 0.0)

	; Amount dialog
	WAmount = GY_CreateWindow(LanguageString$(LS_ChooseAmount), 0.35, 0.4, 0.4, 0.1, True, True, False)
	GY_CreateLabel(WAmount, 0.05, 0.05, LanguageString$(LS_ChooseAmountDetail))
	BAmountOK = GY_CreateButton(WAmount, 0.8, 0.55, 0.15, 0.38, "OK")
	TAmount = GY_CreateTextField(WAmount, 0.05, 0.55, 0.7, 2, 5)

	; Party window
	WParty = GY_CreateWindow(LanguageString$(LS_Party), 0.3, 0.3, 0.4, 0.4, True, True, False, LoadTexture("Data\Textures\GUI\PartyBG.png"))
	BPartyLeave = GY_CreateButton(WParty, 0.6, 0.85, 0.35, 0.1, LanguageString$(LS_LeaveParty))
	Y# = 0.05
	For i = 0 To 6
		LPartyName(i) = GY_CreateLabel(WParty, 0.05, Y#, "LONGEST PLAYER NAME GOES HERE!")
		Y# = Y# + 0.07
	Next
	For i = 0 To 6
		GY_UpdateLabel(LPartyName(i), "", 0, 255, 0)
	Next

	; Spells (abilities) window
	If RequireMemorise = 0 Then FirstSpell = 0
	WSpells = GY_CreateWindow(LanguageString$(LS_Abilities), 0.1, 0.1, 0.6, 0.5, True, True, False, LoadTexture("Data\Textures\GUI\AbilitiesBG.png"))
	BPrevSpells = GY_CreateButton(WSpells, 0.01, 0.94, 0.05, 0.05, "<<")
	BNextSpells = GY_CreateButton(WSpells, 0.94, 0.94, 0.05, 0.05, ">>")
	LSpellsPage = GY_CreateLabel(WSpells, 0.5, 0.94, Upper$(LanguageString$(LS_MemorisedAbilities)), 255, 255, 255, Justify_Centre)
	X# = 0.01
	Y# = 0.05
	ButtonTex = CreateTexture(2, 2)
	For i = 0 To 9
		; Create gadgets
		If i = 5 Then X# = 0.51 : Y# = 0.05 ; Move sideways to second 'page'
		
		BSpellImgs(i) = GY_CreateButton(WSpells, X# + 0.39, Y#, 0.1, 0.15, "", False, 0, 0, 0, ButtonTex)
	;	BSpellImgs(i) = GY_CreateButton(WSpells, X# + 0.39, Y#, 0.1, 0.15, "", False, 0, 0, 0, CreateTexture(2, 2))
		
		LSpellNames(i) = GY_CreateLabel(WSpells, X#, Y#, "LONGEST SPELL NAME GOES HERE!")
		LSpellLevels(i) = GY_CreateLabel(WSpells, X#, Y# + 0.06, LanguageString$(LS_Level) + " 00000")
		Y# = Y# + 0.175
	Next
	UpdateSpellbook()
		

	; Spell unmemorisation confirmation box
	WSpellRemove = GY_CreateWindow(LanguageString$(LS_Unmemorise), 0.35, 0.4, 0.4, 0.1, True, True, False)
	GY_CreateLabel(WSpellRemove, 0.5, 0.05, LanguageString$(LS_UnmemoriseDetail), 255, 255, 255, Justify_Centre)
	BSpellRemoveOK = GY_CreateButton(WSpellRemove, 0.1, 0.7, 0.3, 0.2, LanguageString$(LS_Yes))
	BSpellRemoveCancel = GY_CreateButton(WSpellRemove, 0.6, 0.7, 0.3, 0.2, LanguageString$(LS_No))
	
	;############################################################################################################
	;	Add menu, logout, charselect, exit buttons and move help button to menu window by clan_fd Mar 20, 2014
	; Modified by terrier 


	WMenu = GY_CreateWindow("Menu", 0.3, 0.3, 0.4, 0.4, True, True, False, LoadTexture("Data\Textures\GUI\MenuBG.png"))
	
	;BLogOut = GY_CreateButton(WMenu, 0.05, 0.05, 0.9, 0.14, "Log Out", False)

	;BCharSelect = GY_CreateButton(WMenu, 0.05, 0.24, 0.9, 0.14, "Select Character", False)

	BOptions = GY_CreateButton(WMenu, 0.05, 0.24, 0.9, 0.14, "Options", False)
	;BOptions = GY_CreateButton(WMenu, 0.05, 0.43, 0.9, 0.14, "Options", False)

	BHelp = GY_CreateButton(WMenu, 0.05, 0.43, 0.9, 0.14, "Help", True)	
	;BHelp = GY_CreateButton(WMenu, 0.05, 0.62, 0.9, 0.14, "Help", True)

	BExit = GY_CreateButton(WMenu, 0.05, 0.62, 0.9, 0.14, "Exit", False)
	;BExit = GY_CreateButton(WMenu, 0.05, 0.81, 0.9, 0.14, "Exit", False)
	
	;	End edit
	;############################################################################################################




	; Quest log window
	WQuestLog = GY_CreateWindow(LanguageString$(LS_Quests), 0.1, 0.1, 0.6, 0.7, True, True, False, LoadTexture("Data\Textures\GUI\QuestLogBG.png"))
	For i = 0 To 16
		LQuestLines(i) = GY_CreateLabel(WQuestLog, 0.015, 0.175 + (Float#(i) * 0.043), String$(" ", 43))
	Next
	BCompleteQuests = GY_CreateCheckBox(WQuestLog, 0.6, 0.01, LanguageString$(LS_ShowCompleted), True)
	BPrevQuest = GY_CreateButton(WQuestLog, 0.45, 0.08, 0.1, 0.05, LanguageString$(LS_Up))
	BNextQuest = GY_CreateButton(WQuestLog, 0.45, 0.94, 0.1, 0.05, LanguageString$(LS_Down))


; Stats window
	WCharStats = GY_CreateWindow(LanguageString$(LS_Character), 0.1, 0.1, 0.5, 0.7, True, True, False, LoadTexture("Data\Textures\GUI\CharBG.png"))
	GY_CreateLabel(WCharStats, 0.5, 0.03, Me\Name$, 255, 255, 255, Justify_Centre)
	LReputation = GY_CreateLabel(WCharStats, 0.05, 0.09, LanguageString$(LS_Reputation) + " 00000", 0, 255, 0)
	;LGold = GY_CreateLabel(WCharStats, 0.05, 0.13, "000000000000000000000000000000000000000000000000000000000000", 0, 255, 0)
	LLevel = GY_CreateLabel(WCharStats, 0.05, 0.17, LanguageString$(LS_Level) + " 0000000", 0, 255, 0)
	LXP = GY_CreateLabel(WCharStats, 0.05, 0.21, LanguageString$(LS_Experience) + " 0000000000000", 0, 255, 0)
	GY_CreateLabel(WCharStats, 0.5, 0.27, LanguageString$(LS_Attributes), 255, 255, 255, Justify_Centre)
	For i = 0 To 9
		LAttributeNames(i) = GY_CreateLabel(WCharStats, 0.03, 0.4 + (Float#(i) * 0.05), "LONGEST ATTRIBUTE NAME HERE!")
		LAttributeVals(i) = GY_CreateLabel(WCharStats, 0.97, 0.4 + (Float#(i) * 0.05), "00000 / 00000", 255, 255, 255, Justify_Right)
		GY_UpdateLabel(LAttributeNames(i), "")
		GY_UpdateLabel(LAttributeVals(i), "")
	Next
	Found = 0
	For i = 0 To 39
		If AttributeNames$(i) <> "" And AttributeHidden(i) = False
			GY_UpdateLabel(LAttributeNames(Found), AttributeNames$(i))
			Found = Found + 1
			If Found = 10 Then Exit
		EndIf
	Next
	If Found >= 10
		BPrevAttribute = GY_CreateButton(WCharStats, 0.425, 0.33, 0.15, 0.05, LanguageString$(LS_Up))
		BNextAttribute = GY_CreateButton(WCharStats, 0.425, 0.93, 0.15, 0.05, LanguageString$(LS_Down))
	EndIf

	; Help window
	WHelp = GY_CreateWindow(LanguageString$(LS_Help), 0.25, 0.2, 0.5, 0.6, True, True, False, LoadTexture("Data\Textures\GUI\HelpBG.png"))
	SHelpScroll = GY_CreateScrollBar(WHelp, 0.9, 0.05, 0.05, 0.9, 0.5)
	For i = 0 To 14
		LHelp(i) = GY_CreateLabel(WHelp, 0.05, 0.05 + (Float#(i) * 0.06), "THE LONGEST POSSIBLE HELP LINE ALLOWED GOES IN HERE! XXXXXXXXXX")
	Next
	CurrentLine = 0
	F = ReadFile("Data\Game Data\Help.txt")
		While Eof(F) = False
			ThisLine$ = ReadLine$(F)
			Gad.GY_Gadget = Object.GY_Gadget(LHelp(0))
			While GY_TextWidth#(Gad\EN, ThisLine$) > 0.4
				Found = False
				For i = Len(ThisLine$) To 1 Step -1
					If Mid$(ThisLine$, i, 1) = " "
						If GY_TextWidth#(Gad\EN, Left$(ThisLine$, i - 1)) <= 0.4
							HelpText$(CurrentLine) = Left$(ThisLine$, i - 1)
							ThisLine$ = Mid$(ThisLine$, i + 1)
							CurrentLine = CurrentLine + 1
							Found = True
							Exit
						EndIf
					EndIf
				Next
				If Found = False
					For i = Len(ThisLine$) To 1 Step -1
						If GY_TextWidth#(Gad\EN, Left$(ThisLine$, i - 1)) <= 0.4
							HelpText$(CurrentLine) = Left$(ThisLine$, i - 1)
							ThisLine$ = Mid$(ThisLine$, i)
							CurrentLine = CurrentLine + 1
							Exit
						EndIf
					Next
				EndIf
				If CurrentLine = 99 Then Exit
			Wend
			HelpText$(CurrentLine) = ThisLine$
			CurrentLine = CurrentLine + 1
		Wend
	CloseFile(F)
	MaxHelpLine = CurrentLine
	If MaxHelpLine > 14
		GY_SetScrollBarInterval(SHelpScroll, 1.0 / Float#(MaxHelpLine - 14))
	Else
		GY_GadgetAlpha(SHelpScroll, 0.0)
		GY_LockGadget(SHelpScroll, True)
	EndIf
	For i = 0 To 14
		GY_UpdateLabel(LHelp(i), HelpText$(i))
	Next
;------------------------------------------------------------------------
	; Inventory
	MouseSlotEN = GY_CreateQuad(GY_Mouse) : EntityOrder MouseSlotEN, -3010
	PositionEntity MouseSlotEN, 0.4, -0.4, 0 : ScaleMesh MouseSlotEN, 1.5, 1.5, 1.0
	HideEntity MouseSlotEN
	X# = InventoryWindow\X#
	Y# = InventoryWindow\Y#
	Width# = InventoryWindow\Width#
	Height# = InventoryWindow\Height#
	WInventory = GY_CreateWindow(LanguageString$(LS_Inventory), X#, Y#, Width#, Height#, True, True, False, LoadTexture("Data\Textures\GUI\InventoryBG.png"))
	LInventoryGold = GY_CreateLabel(WInventory, InventoryGold\X#, InventoryGold\Y#, "00000000000000000000000000000000000000000000000000000000")
	BInventoryDrop = GY_CreateButton(WInventory, InventoryDrop\X#, InventoryDrop\Y#, InventoryDrop\Width#, InventoryDrop\Height#, LanguageString$(LS_Drop))
	BInventoryEat = GY_CreateButton(WInventory, InventoryEat\X#, InventoryEat\Y#, InventoryEat\Width#, InventoryEat\Height#, LanguageString$(LS_Use))
	
	Tex = LoadTexture("Data\Textures\GUI\Weapon.bmp", 4)
	CreateInventoryButton(WInventory, SlotI_Weapon, Tex)
	Tex = LoadTexture("Data\Textures\GUI\Shield.bmp", 4)
	CreateInventoryButton(WInventory, SlotI_Shield, Tex)
	Tex = LoadTexture("Data\Textures\GUI\Hat.bmp", 4)
	CreateInventoryButton(WInventory, SlotI_Hat, Tex)
	Tex = LoadTexture("Data\Textures\GUI\Chest.bmp", 4)
	CreateInventoryButton(WInventory, SlotI_Chest, Tex)
	Tex = LoadTexture("Data\Textures\GUI\Hand.bmp", 4)
	CreateInventoryButton(WInventory, SlotI_Hand, Tex)
	Tex = LoadTexture("Data\Textures\GUI\Belt.bmp", 4)
	CreateInventoryButton(WInventory, SlotI_Belt, Tex)
	Tex = LoadTexture("Data\Textures\GUI\Legs.bmp", 4)
	CreateInventoryButton(WInventory, SlotI_Legs, Tex)
	Tex = LoadTexture("Data\Textures\GUI\Feet.bmp", 4)
	CreateInventoryButton(WInventory, SlotI_Feet, Tex)
	Tex = LoadTexture("Data\Textures\GUI\Ring.bmp", 4)
	CreateInventoryButton(WInventory, SlotI_Ring1, Tex)
	CreateInventoryButton(WInventory, SlotI_Ring2, Tex)
	CreateInventoryButton(WInventory, SlotI_Ring3, Tex)
	CreateInventoryButton(WInventory, SlotI_Ring4, Tex)
	Tex = LoadTexture("Data\Textures\GUI\Amulet.bmp", 4)
	CreateInventoryButton(WInventory, SlotI_Amulet1, Tex)
	CreateInventoryButton(WInventory, SlotI_Amulet2, Tex)
	Tex = LoadTexture("Data\Textures\GUI\Backpack.bmp", 4)
	If GetFlag(Me\Actor\InventorySlots, Slot_Backpack - 1) = True
		For i = SlotI_Backpack To SlotI_Backpack + 31
			CreateInventoryButton(WInventory, i, Tex)
		Next
	EndIf

	; Trading
	WTrading = GY_CreateWindow("Trading", 0.1, 0.2, 0.8, 0.6, True, True, False, LoadTexture("Data\Textures\GUI\TradeBG.png"))
	LTradingGold = GY_CreateLabel(WTrading, 0.02, 0.81, LanguageString$(LS_Money) + " 00000000000000000000000000000000000000000000000000000000")
	LTradingCost = GY_CreateLabel(WTrading, 0.02, 0.87, LanguageString$(LS_Cost) + " 00000000000000000000000000000000000000000000000000000000")
	BCostUp = GY_CreateButton(WTrading, 0.02, 0.93, 0.2, 0.045, LanguageString$(LS_IncreaseCost))
	BCostDown = GY_CreateButton(WTrading, 0.25, 0.93, 0.2, 0.045, LanguageString$(LS_DecreaseCost))
	BTradingOK = GY_CreateButton(WTrading, 0.88, 0.93, 0.1, 0.045, LanguageString$(LS_Accept))
	BTradingCancel = GY_CreateButton(WTrading, 0.75, 0.93, 0.1, 0.045, LanguageString$(LS_Cancel))
	X# = 0.02 : Y# = 0.05
	For i = 0 To 31
		BSlotsMine(i) = GY_CreateButton(WTrading, X#, Y#, 0.05625, 0.1, "", True, 0, 0, 0, Tex)
		BSlotsHis(i) = GY_CreateButton(WTrading, X# + 0.5, Y#, 0.05625, 0.1, "", True, 0, 0, 0, Tex)
		X# = X# + 0.07
		If X# > 0.42 Then X# = 0.02 : Y# = Y# + 0.125
	Next

	; Hide all windows
	GY_GadgetAlpha(WCharStats, 0.0, True)
	GY_GadgetAlpha(WQuestLog, 0.0, True)
	GY_GadgetAlpha(WParty, 0.0, True)
	GY_GadgetAlpha(WInventory, 0.0, True)
	GY_GadgetAlpha(WAmount, 0.0, True)
	GY_GadgetAlpha(WSpells, 0.0, True)
	GY_GadgetAlpha(WSpellRemove, 0.0, True)
	GY_GadgetAlpha(WTrading, 0.0, True)
	GY_GadgetAlpha(WHelp, 0.0, True)
	GY_GadgetAlpha(WLargeMap, 0.0, True)
	;########################################################################################
	;	Hide menu window added by clan_fd on Mar 20, 2014
	GY_GadgetAlpha(WMenu, 0.0, True)
	;	End edit
	;########################################################################################


	LastMouseMove = MilliSecs()
	LastLeftClick = MilliSecs()

End Function

; Creates a quad mesh
Function CreateInterfaceQuad(P = 0)

	If P Then EN = CreateMesh(P) Else EN = CreateMesh()
	s = CreateSurface(EN)
	v1 = AddVertex(s, 0.0, -1.0, 0.0, 1.0, 1.0)
	v2 = AddVertex(s, 1.0, -1.0, 0.0, 0.0, 1.0)
	v3 = AddVertex(s, 1.0, 0.0, 0.0, 0.0, 0.0)
	v4 = AddVertex(s, 0.0, 0.0, 0.0, 1.0, 0.0)
	AddTriangle(s, v3, v2, v1)
	AddTriangle(s, v4, v3, v1)
	Return EN

End Function

; Enables/disables all the buttons for empty inventory slots
Function EnableInventoryBlanks(Disable = False)

	GY_LockGadget(BInventoryDrop, Disable)
	GY_LockGadget(BInventoryEat, Disable)
	For i = 0 To Slots_Inventory
		If BSlots(i) <> 0
			If Me\Inventory\Items[i] = Null Then GY_LockGadget(BSlots(i), Disable)
		EndIf
	Next

End Function

; Sets picking modes
Function SetPickModes(Scenery = 0, Actors = 0, NonCombatants = True, DroppedItems = 0)

	If Scenery = 0
		For S.Scenery = Each Scenery
			Collides = GetEntityType(S\EN)
			If Collides = C_Sphere
				EntityPickMode(S\EN, 1, True)
			ElseIf Collides = C_Triangle
				EntityPickMode(S\EN, 2, True)
			ElseIf Collides = C_Box
				EntityPickMode(S\EN, 3, True)
			EndIf
		Next
		For T.Terrain = Each Terrain : EntityPickMode(T\EN, 2, True) : Next
	ElseIf Scenery = -1
		For S.Scenery = Each Scenery : EntityPickMode(S\EN, 0, True) : Next
		For T.Terrain = Each Terrain : EntityPickMode(T\EN, 0, True) : Next
	Else
		For S.Scenery = Each Scenery : EntityPickMode(S\EN, Scenery, True) : Next
		For T.Terrain = Each Terrain : EntityPickMode(T\EN, Scenery, True) : Next
	EndIf

	For AI.ActorInstance = Each ActorInstance
		If Actors = 4
			If AI\Attributes\Value[HealthStat] > 0
				EntityPickMode AI\EN, 0, True
				EntityPickMode AI\CollisionEN, 1
			Else
				EntityPickMode AI\EN, 0, False
				EntityPickMode AI\CollisionEN, 0
			EndIf
		Else
			If AI <> Me And AI\Rider = Null
				If AI\Attributes\Value[HealthStat] <= 0 Or (AI\Actor\Aggressiveness = 3 And NonCombatants = False) Or (AI\RNID = True And PvPEnabled = False And NonCombatants = False)
					EntityPickMode AI\EN, 0, True
					EntityPickMode AI\CollisionEN, 0
				ElseIf Actors = 1
					EntityPickMode AI\EN, 0, True
					EntityPickMode AI\CollisionEN, 1
				ElseIf Actors = 2
					EntityPickMode AI\EN, 2, True
					EntityPickMode AI\CollisionEN, 0
				ElseIf Actors = 3
					EntityPickMode AI\EN, 0, True
					EntityPickMode AI\CollisionEN, 1
				Else
					EntityPickMode AI\EN, 0, True
					EntityPickMode AI\CollisionEN, 0
				EndIf
			Else
				EntityPickMode AI\EN, 0, True
				EntityPickMode AI\CollisionEN, 0
			EndIf
		EndIf
	Next

	For D.DroppedItem = Each DroppedItem
		EntityPickMode(D\EN, DroppedItems)
	Next

End Function
;:::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::
; Creates an action bar button
;Function CreateActionBarButton(TexName$, X#, Toggle = True, FreeTex = True)
;
;	Tex = LoadTexture("Data\Textures\GUI\" + TexName$, 1)
;	Button = GY_CreateButton(0, X# + 0.000, 0.912, 0.033 - 0.006, 0.044 - 0.008, "", Toggle, 0, 0, 0, Tex)
;	;Button = GY_CreateButton(0, X# + 0.003, 0.9415, 0.033203125 - 0.006, 0.044270833 - 0.008, "", Toggle, 0, 0, 0, Tex)
;
;	If FreeTex = True Then FreeTexture(Tex)
;	GY_DropGadget(Button)
;	Return Button
;
;End Function
;
; Creates an action bar button2
;Function CreateActionBarButton2(TexName$, X#, Toggle = True, FreeTex = True)
;
;	Tex = LoadTexture("Data\Textures\GUI\" + TexName$, 1)
;	Button = GY_CreateButton(0, X# , 0.9597, 0.017 - 0.000, 0.022 - 0.000, "", Toggle, 0, 0, 0, Tex)
;	;Button = GY_CreateButton(0, X# + 0.003, 0.9415, 0.033203125 - 0.006, 0.044270833 - 0.008, "", Toggle, 0, 0, 0, Tex)
;
;	If FreeTex = True Then FreeTexture(Tex)
;	GY_DropGadget(Button)
;	Return Button
;
;End Function
;
; Creates an action bar button3
;Function CreateActionBarButton3(TexName$, X#, Toggle = True, FreeTex = True)
;
;	Tex = LoadTexture("Data\Textures\GUI\" + TexName$, 1)
;	Button = GY_CreateButton(0, X# , 0.9597, 0.1, 0.022 - 0.000, "", Toggle, 0, 0, 0, Tex)
;;	;Button = GY_CreateButton(0, X# + 0.003, 0.9415, 0.033203125 - 0.006, 0.044270833 - 0.008, "", Toggle, 0, 0, 0, Tex)
;
;	If FreeTex = True Then FreeTexture(Tex)
;	GY_DropGadget(Button)
;	Return Button
;
;End Function

; Creates an action bar button
Function CreateActionBarButton(TexName$, X#, Toggle = True, FreeTex = True)

	;Widescreen Action bar Scale adjustment Cysis145
	If ResolutionType = 1 ; 16:9 ratio
		Button = GY_CreateButton(0, X# + 0.07, 0.9415, 0.033203125 - 0.006, 0.044270833 - 0.008, "", Toggle, 0, 0, 0, LoadTexture("Data\Textures\GUI\" + TexName$, 1))
	Else 
		Button = GY_CreateButton(0, X# + 0.003, 0.9415, 0.033203125 - 0.006, 0.044270833 - 0.008, "", Toggle, 0, 0, 0, LoadTexture("Data\Textures\GUI\" + TexName$, 1))
	EndIf
	
	GY_DropGadget(Button)
	Return Button

End Function

Function CreateActionBarButtonSlots(TexName$, X#, Toggle = True, FreeTex = True)
	
	;Widescreen Action bar Scale adjustment Cysis145
	If ResolutionType = 1 ; 16:9 ratio
		Button = GY_CreateButton(0, X# - 0.1, 0.9415, 0.033203125 - 0.006, 0.044270833 - 0.008, "", Toggle, 0, 0, 0, LoadTexture("Data\Textures\GUI\" + TexName$, 1))
	Else 
		Button = GY_CreateButton(0, X# + 0.003, 0.9415, 0.033203125 - 0.006, 0.044270833 - 0.008, "", Toggle, 0, 0, 0, LoadTexture("Data\Textures\GUI\" + TexName$, 1))
	EndIf

	GY_DropGadget(Button)
	Return Button

End Function

; Creates an inventory slot button
Function CreateInventoryButton(W, S, Tex)

	BSlots(S) = GY_CreateInventoryButton(W, InventoryButtons(S)\X#, InventoryButtons(S)\Y#, InventoryButtons(S)\Width#, InventoryButtons(S)\Height#, "", True, 0, 0, 0, Tex)

End Function

; Creates an Character slot button
Function CreateCharacterButton(W, S, Tex)

	BSlots(S) = GY_CreateButton(W, InventoryButtons(S)\X#, InventoryButtons(S)\Y#, InventoryButtons(S)\Width#, InventoryButtons(S)\Height#, "", True, 0, 0, 0, Tex)


End Function

; Resets the text on the quest log
Function RedrawQuestLog()

	; Clear all labels
	For i = 0 To 16
		GY_UpdateLabel(LQuestLines(i), "")
	Next

	; Buttons
	If QuestLogVisible Then GY_GadgetAlpha(WQuestLog, 0.85, True)
	GY_LockGadget(BPrevQuest, False) : GY_LockGadget(BNextQuest, False)
	MaxQuest = CountQuests(QuestLog) - 1
	If MaxQuest < 0
		GY_GadgetAlpha(BPrevQuest, 0.0) : GY_LockGadget(BPrevQuest, True)
		GY_GadgetAlpha(BNextQuest, 0.0) : GY_LockGadget(BNextQuest, True)
		GY_UpdateLabel(LQuestLines(0), LanguageString$(LS_NoQuestsAvailable))
		Return
	EndIf
	If FirstQuest = 0 Then GY_GadgetAlpha(BPrevQuest, 0.0) : GY_LockGadget(BPrevQuest, True)
	If FirstQuest >= MaxQuest Then FirstQuest = MaxQuest : GY_GadgetAlpha(BNextQuest, 0.0) : GY_LockGadget(BNextQuest, True)

	DrawDone = GY_CheckBoxDown(BCompleteQuests)

	; Find first quest to draw
	Num = 0
	Start = -1
	For i = 0 To 499
		If QuestLog\EntryName$[i] <> ""
			If DrawDone = True Or Asc(Mid$(QuestLog\EntryStatus$[i], 4, 1)) <> 254
				If Num = FirstQuest Then Start = i : Exit
				Num = Num + 1
			EndIf
		EndIf
	Next

	; If there wasn't one (usually when not displaying completed)
	If Start < 0
		GY_GadgetAlpha(BPrevQuest, 0.0) : GY_LockGadget(BPrevQuest, True)
		GY_GadgetAlpha(BNextQuest, 0.0) : GY_LockGadget(BNextQuest, True)
		GY_UpdateLabel(LQuestLines(0), LanguageString$(LS_NoQuestsAvailable))
		Return
	EndIf

	; Draw until we run out of lines
	CurrentLine = 0
	For CurrentQuest = Start To 499
		If CurrentLine > 16 Then Exit
		If QuestLog\EntryName$[CurrentQuest] <> "" And (DrawDone = True Or Asc(Mid$(QuestLog\EntryStatus$[CurrentQuest], 4, 1)) <> 254)
			GY_UpdateLabel(LQuestLines(CurrentLine), QuestLog\EntryName$[CurrentQuest] + " -", 255, 255, 255)
			CurrentLine = CurrentLine + 1
			Status$ = QuestLog\EntryStatus$[CurrentQuest]
			cR = RCE_IntFromStr(Mid$(Status$, 1, 1))
			cG = RCE_IntFromStr(Mid$(Status$, 2, 1))
			cB = RCE_IntFromStr(Mid$(Status$, 3, 1))
			Status$ = Mid$(Status$, 4)
			If Status$ = Chr$(254)
				Status$ = "  " + LanguageString$(LS_Completed)
				cR = 255 : cG = 225 : cB = 100
			Else
				Status$ = "  " + Status$
			EndIf
			While Status$ <> "" And CurrentLine <= 16
				; Word wrap
				If Len(Status$) > 43
					SplitChar = 0
					For i = 43 To 1 Step -1
						If Mid$(Status$, i, 1) = " " Then SplitChar = i : Exit
					Next
					If SplitChar > 0
						GY_UpdateLabel(LQuestLines(CurrentLine), Left$(Status$, SplitChar - 1), cR, cG, cB)
						Status$ = Mid$(Status$, SplitChar + 1)
					Else
						GY_UpdateLabel(LQuestLines(CurrentLine), Left$(Status$, 25), cR, cG, cB)
						Status$ = Mid$(Status$, 26)
					EndIf
					CurrentLine = CurrentLine + 1
				Else
					GY_UpdateLabel(LQuestLines(CurrentLine), Status$, cR, cG, cB)
					Status$ = ""
				EndIf
			Wend
			CurrentLine = CurrentLine + 2
		EndIf
	Next

End Function

; Updates the button icons for the action bar quick-slots
Function UpdateActionBarIcons()

   If ActionBarStart = 2
      Offset = 12
   ElseIf ActionBarStart = 3
      Offset = 24
   EndIf
   
   For i = 0 To 11
      ; Spell
      If ActionBarSlots(i + Offset) < 0 And ActionBarSlots(i + Offset) <> 0   
         If RequireMemorise
            Num = ActionBarSlots(i + Offset) + 10
            Sp.Spell = SpellsList(Me\KnownSpells[Me\MemorisedSpells[Num]])
         Else
            Num = ActionBarSlots(i + Offset) + 1000
            Sp.Spell = SpellsList(Me\KnownSpells[Num])
         EndIf
         GYG.GY_Gadget = Object.GY_Gadget(BActionBar(i))
         EntityTexture(GYG\EN, GetTexture(Sp\ThumbnailTexID))
      ; Item
      ElseIf ActionBarSlots(i + Offset) < 65535 And ActionBarSlots(i + Offset) <> 0   
         It.Item = ItemList(ActionBarSlots(i + Offset))
         GYG.GY_Gadget = Object.GY_Gadget(BActionBar(i))
         EntityTexture GYG\EN, GetTexture(It\ThumbnailTexID)
      Else
         GYG.GY_Gadget = Object.GY_Gadget(BActionBar(i))
         GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
         EntityTexture GYG\EN, GYB\UserTexture
      EndIf
   Next

End Function

; Eats/equips/calls the script for an item
Function UseItem(SlotIndex, Amount)

	If Me\Inventory\Items[SlotIndex] <> Null
		If Me\Inventory\Amounts[SlotIndex] >= Amount
			GY_SetButtonState(BSlots(SlotIndex), False)

			; Eat food
			If Me\Inventory\Items[SlotIndex]\Item\ItemType = I_Potion Or Me\Inventory\Items[SlotIndex]\Item\ItemType = I_Ingredient
				If Me\Inventory\Items[SlotIndex]\Item\ExclusiveClass$ = "" Or Upper$(Me\Inventory\Items[SlotIndex]\Item\ExclusiveClass$) = Upper$(Me\Actor\Class$)
					If Me\Inventory\Items[SlotIndex]\Item\ExclusiveRace$ = "" Or Upper$(Me\Inventory\Items[SlotIndex]\Item\ExclusiveRace$) = Upper$(Me\Actor\Race$)
						RCE_Send(Connection, PeerToHost, P_EatItem, RCE_StrFromInt$(SlotIndex, 1) + RCE_StrFromInt$(Amount, 2), True)
						Me\Inventory\Amounts[SlotIndex] = Me\Inventory\Amounts[SlotIndex] - Amount
						If Me\Inventory\Amounts[SlotIndex] <= 0
							Me\Inventory\Items[SlotIndex] = Null
							GY_SetButtonState(BSlots(SlotIndex), True)
							GY_SetButtonLabel(BSlots(SlotIndex), "")
							GYG.GY_Gadget = Object.GY_Gadget(BSlots(SlotIndex))
							GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
							EntityTexture(GYB\Gadget\EN, GYB\UserTexture)
							EnableInventoryBlanks(True)
						Else
							GY_SetButtonLabel(BSlots(SlotIndex), Me\Inventory\Amounts[SlotIndex], 100, 255, 0, True)
						EndIf
					EndIf
				EndIf
				
			ElseIf Me\Inventory\Items[SlotIndex]\Item\ItemType = I_Image
				; Image item
				Pa$ = RCE_StrFromInt$(SlotIndex, 1)
				RCE_Send(Connection, PeerToHost, P_ItemScript, Pa$, True)
				
				Local gwidth# = GraphicsWidth()
				Local gheight# = GraphicsHeight()
				
				Local texture = GetTexture(Me\Inventory\Items[SlotIndex]\Item\ImageID, False)
				Local sizex# = gwidth / TextureWidth(texture)
				Local sizey# = gheight / TextureHeight(texture)
				Local aspect# = TextureWidth(texture) / Float(TextureHeight(texture))
				
				If sizex > 0.9 Or sizey*aspect > 0.9
					sizex = 0.9
					sizey = sizex/aspect
					
				EndIf
				If sizey > 0.8
					sizey = 0.8
					sizex = sizey*aspect
					
				EndIf
				If sizex < 0.2 Or sizey*aspect < 0.2
					
					sizex = 0.2
					sizey = sizex/aspect
					
				EndIf
				If sizey < 0.2
					sizey = 0.2
					sizex = sizey * aspect
						
				EndIf
				
				If GY_ButtonDown(BInventory) = True Or ItemWindowFromInventory = True
					GY_SetButtonState(BInventory, False)
					ItemWindowFromInventory = True
				Else
					ItemWindowFromInventory = False
				EndIf
				If WItemWindow <> 0
					GY_FreeGadget(WItemWindow)
				EndIf
				WItemWindow = GY_CreateWindow(Me\Inventory\Items[SlotIndex]\Item\Name, (1.0-sizex)/2, (1.0-sizey)/2-0.05, sizex, sizey, True, True, False, 0, False)
				GY_UpdateWindowBackground(WItemWindow, texture )
				GY_ActivateWindow(WItemWindow)
				
			Else
				; Item script
				Pa$ = RCE_StrFromInt$(SlotIndex, 1)
				If PlayerTarget > 0
					AI.ActorInstance = Object.ActorInstance(PlayerTarget)
					Pa$ = Pa$ + RCE_StrFromInt$(AI\RuntimeID, 2)
				EndIf
				RCE_Send(Connection, PeerToHost, P_ItemScript, Pa$, True)
;-------------------------------------------------------------------------------------------------
;CSlots
;-------------------------------------------------------------------------------------------------
				; Equip weapon
				If Me\Inventory\Items[SlotIndex]\Item\ItemType = I_Weapon And SlotIndex >= SlotI_Backpack
					If Me\Inventory\Items[SlotI_Weapon] = Null
						EnableInventoryBlanks()
						Result = InventorySwap(Me, SlotIndex, SlotI_Weapon, Amount)
						If Result = True
							; Update icons
							GYG.GY_Gadget = Object.GY_Gadget(BSlots(SlotI_Weapon))
							EntityTexture(GYG\EN, GetTexture(Me\Inventory\Items[SlotI_Weapon]\Item\ThumbnailTexID))
							If Me\Inventory\Amounts[SlotIndex] <= 0
								GYG.GY_Gadget = Object.GY_Gadget(BSlots(SlotIndex))
								GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
								EntityTexture(GYB\Gadget\EN, GYB\UserTexture)
								GY_SetButtonState(BSlots(SlotIndex), True)
								GY_SetButtonLabel(BSlots(SlotIndex), "")
							EndIf
							UpdateActorItems(Me)
						EndIf
						EnableInventoryBlanks(True)
					EndIf
				; Equip armour
				ElseIf Me\Inventory\Items[SlotIndex]\Item\ItemType = I_Armour And SlotIndex >= SlotI_Backpack
					i = Me\Inventory\Items[SlotIndex]\Item\SlotType - 1
					If Me\Inventory\Items[i] = Null
						EnableInventoryBlanks()
						Result = InventorySwap(Me, SlotIndex, i, Amount)
						If Result = True
							; Update icons
							GYG.GY_Gadget = Object.GY_Gadget(BSlots(i))
							EntityTexture(GYG\EN, GetTexture(Me\Inventory\Items[i]\Item\ThumbnailTexID))
							If Me\Inventory\Amounts[SlotIndex] <= 0
								GYG.GY_Gadget = Object.GY_Gadget(BSlots(SlotIndex))
								GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
								EntityTexture(GYB\Gadget\EN, GYB\UserTexture)
								GY_SetButtonState(BSlots(SlotIndex), True)
								GY_SetButtonLabel(BSlots(SlotIndex), "")
							EndIf
							UpdateActorItems(Me)
						EndIf
						EnableInventoryBlanks(True)
					EndIf
				EndIf
			EndIf
		EndIf
	EndIf


End Function

Function FreeInterface()

	; Quit bar
	GY_FreeGadget(QuitLabel); = GY_CreateLabel(0, 0.5, 0.4, LanguageString$(LS_QuitProgress), 255, 255, 255, Justify_Centre)
	GY_FreeGadget(QuitBar); = GY_CreateProgressBar(0, 0.35, 0.43, 0.3, 0.02, 0, 100, 0, 255, 0)

	; Chat bubble
	FreeEntity(ChatBubbleEN); = CreateMesh()	;entity
	FreeEntity(Surf); = CreateSurface(ChatBubbleEN)	;entity

	; Chat bar
	If ChatBar <> Null
		FreeEntity(ChatBar\Component); = CreateInterfaceQuad(GY_Cam)	;entity
	EndIf


	; Attribute displays
	For i = 0 To 39
		If AttributeDisplays(i)\Width# > 0.0 And AttributeDisplays(i)\Height# > 0.0
			GY_FreeGadget(AttributeDisplays(i)\Component); = GY_CreateProgressBar(0, X#, Y#, W#, H#, 50, 100, R, G, B)
			GY_FreeGadget(AttributeDisplayNumbers(i)); = GY_CreateLabel(0, X#, Y#, "00000 / 00000", 255, 255, 255, Justify_Centre)
		EndIf
	Next

	; Large map
	GY_FreeGadget(WLargeMap); = GY_CreateWindow(LanguageString$(LS_Map), 0.15, 0.1, 0.6, 0.8, True, True, False, 0, False)

	; Chat entry
	GY_FreeGadget(ChatEntry\Component); = GY_CreateTextField(0, ChatEntry\X#, ChatEntry\Y#, ChatEntry\Width#, 0, 100)

	; Compass
	FreeEntity(Compass\Component); = GY_CreateQuad(GY_Cam)	;entity
	FreeEntity(CompassBackground); = GY_CreateQuad(GY_Cam)	;entity

	; Actor effect icons
	For EIS.EffectIconSlot = Each EffectIconslot
		FreeEntity(EIS\EN)
		Delete EIS
	Next

	; Action bar
	FreeEntity(ActionEN); = GY_CreateQuad(GY_Cam)	;entity

	FreeEntity(XPEN); = GY_CreateQuad(GY_Cam)	;entity
	
	GY_FreeGadget(BChat); = CreateActionBarButton("Chat.bmp", 0.62890625)	

	;Problem occurs when freeing these

	GY_FreeGadget(BMap); = CreateActionBarButton("Map.bmp", 0.666015625)

	GY_FreeGadget(BInventory); = CreateActionBarButton("Inventory.bmp", 0.702148437)
	
	GY_FreeGadget(BSpells); = CreateActionBarButton("Abilities.bmp", 0.739257812)
	
	GY_FreeGadget(BCharStats); = CreateActionBarButton("Character.bmp", 0.77734375)
	
	GY_FreeGadget(BQuestLog); = CreateActionBarButton("Quests.bmp", 0.813476562)
	
	GY_FreeGadget(BParty); = CreateActionBarButton("Party.bmp", 0.850585937)

	GY_FreeGadget(BMenu); = CreateActionBarButton("Help.bmp", 0.88671875)
	
	GY_FreeGadget(WMenu); = GY_CreateWindow("Menu", 0.3, 0.3, 0.4, 0.4, True, True, False, LoadTexture("Data\Textures\GUI\MenuBG.png"))

	
	;GY_FreeGadget(BLogOut); = GY_CreateButton(WMenu, 0.05, 0.05, 0.9, 0.14, "Log Out", False)
	;GY_FreeGadget(BCharSelect); = GY_CreateButton(WMenu, 0.05, 0.24, 0.9, 0.14, "Select Character", False)
	GY_FreeGadget(BOptions); = GY_CreateButton(WMenu, 0.05, 0.43, 0.9, 0.14, "Options", False)
	GY_FreeGadget(BHelp); = GY_CreateButton(WMenu, 0.05, 0.62, 0.9, 0.14, "Help", True)
	GY_FreeGadget(BExit); = GY_CreateButton(WMenu, 0.05, 0.81, 0.9, 0.14, "Exit", False)


	; Action bar quick-slot buttons
	GY_FreeGadget(BPrevBar); = GY_CreateButton(0, 0.53, 0.9415 - 0.001364583334, 0.02, 0.015, "", False, 0, 0, 0, ActionBarUpTex)
	GY_FreeGadget(BNextBar); = GY_CreateButton(0, 0.53, 0.9655 - 0.001364583334, 0.02, 0.015, "", False, 0, 0, 0, ActionBarDownTex)
	For i = 0 To 11
		GY_FreeGadget(BActionBar(i)); = CreateActionBarButton("EmptySlot.bmp", 0.089867187 + (Float#(i) * 0.036132812), False, False)
	Next

		; Chat text display
	MaxChatLine = Int(Floor#(Chat\Height# / 0.025)) - 1
	For i = 0 To MaxChatLine
		GY_FreeGadget(ChatLines(i)); = GY_CreateLabel(0, X#, Y#, String$(" ", 200))
	Next
	
	;problem occurs when trying to free these	

;&&&&&&&&&
	GY_FreeGadget(BHistoryMode); = GY_CreateButton(0, X#, Chat\Y# + 0.005, 0.02, 0.015, "", False, 0, 0, 0, ActionBarDownTex)
	GY_FreeGadget(BHistoryUp); = GY_CreateButton(0, X#, Chat\Y# + 0.005, 0.02, 0.015, "", False, 0, 0, 0, ActionBarUpTex)
	GY_FreeGadget(BHistoryDown); = GY_CreateButton(0, X#, Chat\Y# + 0.025, 0.02, 0.015, "", False, 0, 0, 0, ActionBarDownTex)

;&&&&&&&&&&

	; Amount dialog
	GY_FreeGadget(WAmount); = GY_CreateWindow(LanguageString$(LS_ChooseAmount), 0.35, 0.4, 0.4, 0.1, True, True, False)

	; Party window
	GY_FreeGadget(WParty); = GY_CreateWindow(LanguageString$(LS_Party), 0.3, 0.3, 0.4, 0.4, True, True, False, LoadTexture("Data\Textures\GUI\PartyBG.png"))
	
	; Spells (abilities) window
	
	GY_FreeGadget(WSpells); = GY_CreateWindow(LanguageString$(LS_Abilities), 0.1, 0.1, 0.6, 0.5, True, True, False, LoadTexture("Data\Textures\GUI\AbilitiesBG.png"))
;Stop
	;GY_FreeGadget(BPrevSpells); = GY_CreateButton(WSpells, 0.01, 0.94, 0.05, 0.05, "<<")
	;GY_FreeGadget(BNextSpells); = GY_CreateButton(WSpells, 0.94, 0.94, 0.05, 0.05, ">>")
	;GY_FreeGadget(LSpellsPage); = GY_CreateLabel(WSpells, 0.5, 0.94, Upper$(LanguageString$(LS_MemorisedAbilities)), 255, 255, 255, Justify_Centre)

	;For i = 0 To 9
	;	; Create gadgets
	;	GY_FreeGadget(BSpellImgs(i)); = GY_CreateButton(WSpells, X# + 0.39, Y#, 0.1, 0.15, "", False, 0, 0, 0, ButtonTex)
	;	GY_FreeGadget(LSpellNames(i)); = GY_CreateLabel(WSpells, X#, Y#, "LONGEST SPELL NAME GOES HERE!")
	;	GY_FreeGadget(LSpellLevels(i)); = GY_CreateLabel(WSpells, X#, Y# + 0.06, LanguageString$(LS_Level) + " 00000")

	;Next

	; Spell unmemorisation confirmation box
	GY_FreeGadget(WSpellRemove); = GY_CreateWindow(LanguageString$(LS_Unmemorise), 0.35, 0.4, 0.4, 0.1, True, True, False)

	;BSpellRemoveOK = GY_CreateButton(WSpellRemove, 0.1, 0.7, 0.3, 0.2, LanguageString$(LS_Yes))
	;BSpellRemoveCancel = GY_CreateButton(WSpellRemove, 0.6, 0.7, 0.3, 0.2, LanguageString$(LS_No))

	; Quest log window
	GY_FreeGadget(WQuestLog); = GY_CreateWindow(LanguageString$(LS_Quests), 0.1, 0.1, 0.6, 0.7, True, True, False, LoadTexture("Data\Textures\GUI\QuestLogBG.png"))

	;For i = 0 To 16
	;	LQuestLines(i) = GY_CreateLabel(WQuestLog, 0.015, 0.175 + (Float#(i) * 0.043), String$(" ", 43))
	;Next
	;BCompleteQuests = GY_CreateCheckBox(WQuestLog, 0.6, 0.01, LanguageString$(LS_ShowCompleted), True)
	;BPrevQuest = GY_CreateButton(WQuestLog, 0.45, 0.08, 0.1, 0.05, LanguageString$(LS_Up))
	;BNextQuest = GY_CreateButton(WQuestLog, 0.45, 0.94, 0.1, 0.05, LanguageString$(LS_Down))

	; Stats window
	GY_FreeGadget(WCharStats); = GY_CreateWindow(LanguageString$(LS_Character), 0.1, 0.1, 0.5, 0.7, True, True, False, LoadTexture("Data\Textures\GUI\CharBG.png"))

	;LReputation = GY_CreateLabel(WCharStats, 0.05, 0.09, LanguageString$(LS_Reputation) + " 00000", 0, 255, 0)
	;LGold = GY_CreateLabel(WCharStats, 0.05, 0.13, "000000000000000000000000000000000000000000000000000000000000", 0, 255, 0)
	;LLevel = GY_CreateLabel(WCharStats, 0.05, 0.17, LanguageString$(LS_Level) + " 0000000", 0, 255, 0)
	;LXP = GY_CreateLabel(WCharStats, 0.05, 0.21, LanguageString$(LS_Experience) + " 0000000000000", 0, 255, 0)
	;For i = 0 To 9
	;	LAttributeNames(i) = GY_CreateLabel(WCharStats, 0.03, 0.4 + (Float#(i) * 0.05), "LONGEST ATTRIBUTE NAME HERE!")
	;	LAttributeVals(i) = GY_CreateLabel(WCharStats, 0.97, 0.4 + (Float#(i) * 0.05), "00000 / 00000", 255, 255, 255, Justify_Right)
	;Next

	;If Found >= 10
	;	BPrevAttribute = GY_CreateButton(WCharStats, 0.425, 0.33, 0.15, 0.05, LanguageString$(LS_Up))
	;	BNextAttribute = GY_CreateButton(WCharStats, 0.425, 0.93, 0.15, 0.05, LanguageString$(LS_Down))
	;EndIf

	; Help window
	GY_FreeGadget(WHelp); = GY_CreateWindow(LanguageString$(LS_Help), 0.25, 0.2, 0.5, 0.6, True, True, False, LoadTexture("Data\Textures\GUI\HelpBG.png"))

	;SHelpScroll = GY_CreateScrollBar(WHelp, 0.9, 0.05, 0.05, 0.9, 0.5)
	;For i = 0 To 14
	;	LHelp(i) = GY_CreateLabel(WHelp, 0.05, 0.05 + (Float#(i) * 0.06), "THE LONGEST POSSIBLE HELP LINE ALLOWED GOES IN HERE! XXXXXXXXXX")
	;Next



	; Inventory
	FreeEntity(MouseSlotEN); = GY_CreateQuad(GY_Mouse)	;entity

	GY_FreeGadget(WInventory); = GY_CreateWindow(LanguageString$(LS_Inventory), X#, Y#, Width#, Height#, True, True, False, LoadTexture("Data\Textures\GUI\InventoryBG.png"))
	;GY_FreeGadget(LInventoryGold); = GY_CreateLabel(WInventory, InventoryGold\X#, InventoryGold\Y#, "00000000000000000000000000000000000000000000000000000000")
	;GY_FreeGadget(BInventoryDrop); = GY_CreateButton(WInventory, InventoryDrop\X#, InventoryDrop\Y#, InventoryDrop\Width#, InventoryDrop\Height#, LanguageString$(LS_Drop))
	;GY_FreeGadget(BInventoryEat); = GY_CreateButton(WInventory, InventoryEat\X#, InventoryEat\Y#, InventoryEat\Width#, InventoryEat\Height#, LanguageString$(LS_Use))	

	; Trading
	GY_FreeGadget(WTrading); = GY_CreateWindow("Trading", 0.1, 0.2, 0.8, 0.6, True, True, False, LoadTexture("Data\Textures\GUI\TradeBG.png"))
	;GY_FreeGadget(LTradingGold); = GY_CreateLabel(WTrading, 0.02, 0.81, LanguageString$(LS_Money) + " 00000000000000000000000000000000000000000000000000000000")
	;GY_FreeGadget(LTradingCost); = GY_CreateLabel(WTrading, 0.02, 0.87, LanguageString$(LS_Cost) + " 00000000000000000000000000000000000000000000000000000000")
	;GY_FreeGadget(BCostUp); = GY_CreateButton(WTrading, 0.02, 0.93, 0.2, 0.045, LanguageString$(LS_IncreaseCost))
	;GY_FreeGadget(BCostDown); = GY_CreateButton(WTrading, 0.25, 0.93, 0.2, 0.045, LanguageString$(LS_DecreaseCost))
	;GY_FreeGadget(BTradingOK); = GY_CreateButton(WTrading, 0.88, 0.93, 0.1, 0.045, LanguageString$(LS_Accept))
	;GY_FreeGadget(BTradingCancel); = GY_CreateButton(WTrading, 0.75, 0.93, 0.1, 0.045, LanguageString$(LS_Cancel))
	;For i = 0 To 31
	;	GY_FreeGadget(BSlotsMine(i)); = GY_CreateButton(WTrading, X#, Y#, 0.05625, 0.1, "", True, 0, 0, 0, Tex)
	;	GY_FreeGadget(BSlotsHis(i)); = GY_CreateButton(WTrading, X# + 0.5, Y#, 0.05625, 0.1, "", True, 0, 0, 0, Tex)
	;Next

End Function