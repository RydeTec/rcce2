Global TradePackets = 0
Global TradeMsg1$, TradeMsg2$, TradeMsg3$

Global newEntityX#,newEntityY#,newEntityZ#
Global oldEntityX#,oldEntityY#,oldEntityZ#

; Connects to the server
Function Connect()

	; Attempt connection
    Port = 11002
	Repeat
		PeerToHost = RCE_Connect(ServerHost$, ServerPort, Port, Me\Name$, "", "Data\Logs\Client Connection.txt", True)
		Port = Port + 1
	Until PeerToHost <> RN_ConnectionInUse And PeerToHost <> RN_PortInUse
	Select PeerToHost
		Case PeerToHostNotFound : RuntimeError(LanguageString$(LS_InvalidHost) + " (" + ServerHost$ + ")")
		Case RN_TimedOut : RuntimeError(LanguageString$(LS_NoResponse))
		Case RN_ServerFull : RuntimeError(LanguageString$(LS_TooManyPlayers))
	End Select
	Pa$ = RCE_StrFromInt$(Len(UName$), 1) + UName$ + RCE_StrFromInt$(Len(PWord$), 1) + PWord$
	RCE_Send(Connection, PeerToHost, P_StartGame, Pa$ + Chr$(SelectedCharacter), True)

	; Await reply
	Done = 0
	While Done < 13
		Delay 10
		; We get signal!
		For M.RCE_Message = Each RCE_Message
			If M\MessageType = P_StartGame
				; Error message
				If M\MessageData$ = "N" Then RuntimeError(LanguageString$(LS_AlreadyInGame))
				; Action bar data
				If Len(M\MessageData$) > 2
					SlotNum = RCE_IntFromStr(Left$(M\MessageData$, 1))
					Offset = 2
					For i = 1 To 3
						; Get slot data
						NameLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 2))
						Slot$ = Mid$(M\MessageData$, Offset + 2, NameLen)
						Offset = Offset + 2 + NameLen
						; Set up slot
						If Slot$ <> ""
							; Ability
							If Left$(Slot$, 1) = "S"
								SpellName$ = Mid$(Slot$, 2)
								; Must be memorised
								If RequireMemorise
									For j = 0 To 9
										If Me\MemorisedSpells[j] <> 5000
											Sp.Spell = SpellsList(Me\KnownSpells[Me\MemorisedSpells[j]])
											If Sp\Name$ = SpellName$
												ActionBarSlots(SlotNum) = j - 10
												Exit
											EndIf
										EndIf
									Next
								; Must be known
								Else
									For j = 0 To 999
										If Me\SpellLevels[j] > 0
											Sp.Spell = SpellsList(Me\KnownSpells[j])
											If Sp\Name$ = SpellName$
												ActionBarSlots(SlotNum) = j - 1000
												Exit
											EndIf
										EndIf
									Next
								EndIf
							; Item
							ElseIf Left$(Slot$, 1) = "I"
								ActionBarSlots(SlotNum) = RCE_IntFromStr(Mid$(Slot$, 2))
							EndIf
						Else
							ActionBarSlots(SlotNum) = 65535
						EndIf
						SlotNum = SlotNum + 1
					Next
				; My actor's runtime ID and XP bar level
				Else
					Me\RuntimeID = RCE_IntFromStr(M\MessageData$)
					RuntimeIDList(Me\RuntimeID) = Me
				EndIf
				Delete M : Done = Done + 1
			ElseIf M\MessageType = PeerToHostHasLeft
				RuntimeError(LanguageString$(LS_LostConnection))
				Delete M
			EndIf
		Next
		
		RCE_Update()
		RCE_CreateMessages()
		
		Cls
		Flip VSync
	Wend
	UpdateActionBarIcons()

	; Success
	WriteLog(MainLog, "Successfully connected to server")

End Function

; Processes all network messages
Function UpdateNetwork()

	; Incoming messages
	For M.RCE_Message = Each RCE_Message
		; What happen?
		Select M\MessageType

			; Scripted progress bar
			Case P_ProgressBar ; :)
				; Create new
				If Left$(M\MessageData$, 1) = "C"
					Red = RCE_IntFromStr(Mid$(M\MessageData$, 2, 1))
					Green = RCE_IntFromStr(Mid$(M\MessageData$, 3, 1))
					Blue = RCE_IntFromStr(Mid$(M\MessageData$, 4, 1))
					X# = RCE_FloatFromStr#(Mid$(M\MessageData$, 5, 4))
					Y# = RCE_FloatFromStr#(Mid$(M\MessageData$, 9, 4))
					W# = RCE_FloatFromStr#(Mid$(M\MessageData$, 13, 4))
					H# = RCE_FloatFromStr#(Mid$(M\MessageData$, 17, 4))
					Max = RCE_IntFromStr(Mid$(M\MessageData$, 25, 2))
					Value = RCE_IntFromStr(Mid$(M\MessageData$, 27, 2))
					DisplayText$ = Mid$(M\MessageData$, 29)

					PBar = GY_CreateProgressBar(0, X#, Y#, W#, H#, Value, Max, Red, Green, Blue)
					GY_CreateLabel(PBar, 0.5, 0.5 - (0.015 / H#), DisplayText$, 255, 255, 255, Justify_Centre)
					RCE_Send(Connection, PeerToHost, P_ProgressBar, "C" + Mid$(M\MessageData$, 21, 4) + RCE_StrFromInt$(PBar), True)
				; Update
				ElseIf Left$(M\MessageData$, 1) = "U"
					PBar = RCE_IntFromStr(Mid$(M\MessageData$, 2, 4))
					Value = RCE_IntFromStr(Mid$(M\MessageData$, 6, 2))
					GY_UpdateProgressBar(PBar, Value)
				; Delete
				ElseIf Left$(M\MessageData$, 1) = "D"
					PBar = RCE_IntFromStr(Mid$(M\MessageData$, 2))
					GY_FreeGadget(PBar)
				EndIf

			; Reposition an actor
			Case P_RepositionActor
				RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))
				AI.ActorInstance = RuntimeIDList(RuntimeID)
				If AI <> Null
					; Move
					If Left$(M\MessageData$, 1) = "M"
						AI\X# = RCE_FloatFromStr(Mid$(M\MessageData$, 4, 4))
						Y# = RCE_FloatFromStr(Mid$(M\MessageData$, 8, 4))
						AI\Z# = RCE_FloatFromStr(Mid$(M\MessageData$, 12, 4))
						MoveCamera = RCE_IntFromStr(Mid$(M\MessageData$, 16, 1))
						AI\DestX# = AI\X#
						AI\DestZ# = AI\Z#
						PositionEntity(AI\CollisionEN, AI\X#, Y#, AI\Z#)
						; Ignore collision
						If RCE_IntFromStr(Mid$(M\MessageData$, 16, 1)) = 0 Then ResetEntity(AI\CollisionEN)
						; Move the camera directly to the new spot, otherwise it will fly there
						If MoveCamera = False Then PositionEntity(Cam, AI\X#, Y#, AI\Z#)
					; Rotate
					Else
						AI\Yaw# = RCE_FloatFromStr(Mid$(M\MessageData$, 4))
						RotateEntity(AI\CollisionEN, 0, AI\Yaw#, 0)
					EndIf
				EndIf

			; Floating number (for damage or whatever)
			Case P_FloatingNumber
				RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 1, 2))
				AI.ActorInstance = RuntimeIDList(RuntimeID)
				If AI <> Null
					Amount = RCE_IntFromStr(Mid$(M\MessageData$, 3, 4))
					cR = RCE_IntFromStr(Mid$(M\MessageData$, 7, 1))
					cG = RCE_IntFromStr(Mid$(M\MessageData$, 8, 1))
					cB = RCE_IntFromStr(Mid$(M\MessageData$, 9, 1))
					CreateFloatingNumber(AI, Amount, cR, cG, cB)
				EndIf

			; Projectile created
			Case P_Projectile
				; Get source and target actor instance
				RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 1, 2))
				AI.ActorInstance = RuntimeIDList(RuntimeID)
				RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 3, 2))
				TargetAI.ActorInstance = RuntimeIDList(RuntimeID)
				; Target is valid
				If TargetAI <> Null And AI <> Null
					; Get projectile data
					MeshID = RCE_IntFromStr(Mid$(M\MessageData$, 5, 2))
					TexID1 = RCE_IntFromStr(Mid$(M\MessageData$, 7, 2))
					TexID2 = RCE_IntFromStr(Mid$(M\MessageData$, 9, 2))
					Homing = RCE_IntFromStr(Mid$(M\MessageData$, 11, 1))
					Speed# = Float#(RCE_IntFromStr(Mid$(M\MessageData$, 12, 1))) / 50.0
					NameLen = RCE_IntFromStr(Mid$(M\MessageData$, 13, 1))
					Emitter1$ = ""
					If NameLen > 0 Then Emitter1$ = Mid$(M\MessageData$, 14, NameLen)
					Emitter2$ = Mid$(M\MessageData$, 14 + NameLen)

					; Create it
					CreateProjectile(AI, TargetAI, MeshID, Homing, Speed#, Emitter1$, Emitter2$, TexID1, TexID2)
				EndIf

			; An actor has jumped
			Case P_Jump
				AI.ActorInstance = RuntimeIDList(RCE_IntFromStr(M\MessageData$))
				If AI <> Null
					PlayAnimation(AI, 3, 0.05, Anim_Jump)
					AI\Y# = JumpStrength# * Gravity#
				EndIf

			; Item health updated
			Case P_ItemHealth
				SlotI = RCE_IntFromStr(Left$(M\MessageData$, 1))
				Health = RCE_IntFromStr(Right$(M\MessageData$, 2))
				If Me\Inventory\Items[SlotI] <> Null Then Me\Inventory\Items[SlotI]\ItemHealth = Health

			; Owned scenery selected {##}
			Case P_SelectScenery
				Sc.Scenery = Object.Scenery(RCE_IntFromStr(M\MessageData$))
				If Sc <> Null
					If Sc\AnimationMode = 3 ;And Sc\SceneryID > 0 {##}
						If AnimTime(Sc\EN) > 0.0
							Animate(Sc\EN, 3, -1.0, 0, 1.0)
						Else
							Animate(Sc\EN, 3, 1.0, 0, 1.0)
						EndIf
					EndIf
				EndIf

			; Actor appearance (clothes, face, etc.) changed
			Case P_AppearanceUpdate
				AI.ActorInstance = RuntimeIDList(RCE_IntFromStr(Mid$(M\MessageData$, 2, 2)))
				If AI <> Null
					Select Left$(M\MessageData$, 1)
						; Entire actor
						Case "C"
							ID = RCE_IntFromStr(Right$(M\MessageData$, 2))
							AI\Actor = ActorList(ID)
							If AI\Actor\Genders = 2 And AI\Gender <> 1 Then AI\Gender = 1
							If (AI\Actor\Genders = 1 Or AI\Actor\Genders = 3) And AI\Gender <> 0 Then AI\Gender = 0
							X# = EntityX#(AI\CollisionEN)
							Y# = EntityY#(AI\CollisionEN)
							Z# = EntityZ#(AI\CollisionEN)
							FreeActorInstance3D(AI)
							Result = LoadActorInstance3D(AI, 0.05)
							If Result = False Then RuntimeError("Could not load actor mesh for " + AI\Actor\Race$ + "!")
							AI\Y# = 0.0
							PositionEntity(AI\CollisionEN, X#, Y#, Z#)
							ResetEntity(AI\CollisionEN)
							If AI = Me
								EntityType(Me\CollisionEN, C_Player)
								FreeEntity(Me\NametagEN)
								Me\NametagEN = 0
								Bonce = FindChild(Me\EN, "Head")
								If Bonce = 0 Then RuntimeError(Me\Actor\Race$ + " actor mesh is missing a 'Head' joint!")
								CamHeight# = EntityDistance#(Bonce, Me\CollisionEN)
							EndIf
						; Gender
						Case "G"
							AI\Gender = Asc(Right$(M\MessageData$, 1))
							X# = EntityX#(AI\CollisionEN)
							Y# = EntityY#(AI\CollisionEN)
							Z# = EntityZ#(AI\CollisionEN)
							FreeActorInstance3D(AI)
							Result = LoadActorInstance3D(AI, 0.05)
							If Result = False Then RuntimeError("Could not load actor mesh for " + AI\Actor\Race$ + "!")
							AI\Y# = 0.0
							PositionEntity(AI\CollisionEN, X#, Y#, Z#)
							ResetEntity(AI\CollisionEN)
						; Beard
						Case "D"
							AI\Beard = Asc(Right$(M\MessageData$, 1))

							Bonce = FindChild(AI\EN, "Head")
							If Bonce = 0 Then RuntimeError(AI\Actor\Race$ + " actor mesh is missing a 'Head' joint!")

							; Remove old beard
							BeardEN = FindChild(Bonce, "Beard")
							If BeardEN <> 0 Then FreeEntity(BeardEN)

							; Apply new beard
							If AI\Actor\BeardIDs[AI\Beard] > -1 And AI\Actor\BeardIDs[AI\Beard] < 65535
								ID = AI\Actor\BeardIDs[AI\Beard]
								BeardEN = GetMesh(ID, True)
								If BeardEN <> 0
									EntityParent(BeardEN, Bonce, False)
									PositionEntity(BeardEN, LoadedMeshX#(ID), LoadedMeshY#(ID), LoadedMeshZ#(ID))
									ScaleEntity(BeardEN, LoadedMeshScales#(ID), LoadedMeshScales#(ID), LoadedMeshScales#(ID))
									; Correct rotation for Max models
									If AI\TeamID = True Then TurnEntity(BeardEN, 0, 180, 90)
									NameEntity(BeardEN, "Beard")
								EndIf
							EndIf
						; Hair
						Case "H"
							AI\Hair = Asc(Right$(M\MessageData$, 1))
							If AI\Inventory\Items[SlotI_Hat] = Null Then SetActorHat(AI, -1)
						; Face
						Case "F"
							AI\FaceTex = Asc(Right$(M\MessageData$, 1))
							; Repaint
							If AI\Gender = 0
								FaceTex = AI\Actor\MaleFaceIDs[AI\FaceTex]
								BodyTex = AI\Actor\MaleBodyIDs[AI\BodyTex]
							Else
								FaceTex = AI\Actor\FemaleFaceIDs[AI\FaceTex]
								BodyTex = AI\Actor\FemaleBodyIDs[AI\BodyTex]
							EndIf
							If CountSurfaces(AI\EN) > 1 And FaceTex < 65535
								; Find which surface is body
								Br = GetSurfaceBrush(GetSurface(AI\EN, 1)) : T = GetBrushTexture(Br)
								Name$ = TextureName$(T)
								FreeTexture T : FreeBrush(Br)
								FaceSurface = GetSurface(AI\EN, 2)
								BodySurface = GetSurface(AI\EN, 1)
								If Instr(Upper$(Name$), "HEAD") > 0
									FaceSurface = GetSurface(AI\EN, 1)
									BodySurface = GetSurface(AI\EN, 2)
								EndIf

								; Paint
								Br = CreateBrush()
								BrushTexture(Br, GetTexture(BodyTex))
								PaintSurface(BodySurface, Br)
								BrushTexture(Br, GetTexture(FaceTex))
								PaintSurface(FaceSurface, Br)
								FreeBrush(Br)
								UnloadTexture(BodyTex)
								UnloadTexture(FaceTex)
							Else
								EntityTexture AI\EN, GetTexture(BodyTex)
								UnloadTexture(BodyTex)
							EndIf
						; Body
						Case "B"
							AI\BodyTex = Asc(Right$(M\MessageData$, 1))
							; Repaint
							If AI\Gender = 0
								FaceTex = AI\Actor\MaleFaceIDs[AI\FaceTex]
								BodyTex = AI\Actor\MaleBodyIDs[AI\BodyTex]
							Else
								FaceTex = AI\Actor\FemaleFaceIDs[AI\FaceTex]
								BodyTex = AI\Actor\FemaleBodyIDs[AI\BodyTex]
							EndIf
							If CountSurfaces(AI\EN) > 1 And FaceTex < 65535
								; Find which surface is body
								Br = GetSurfaceBrush(GetSurface(AI\EN, 1)) : T = GetBrushTexture(Br)
								Name$ = TextureName$(T)
								FreeTexture T : FreeBrush(Br)
								FaceSurface = GetSurface(AI\EN, 2)
								BodySurface = GetSurface(AI\EN, 1)
								If Instr(Upper$(Name$), "HEAD") > 0
									FaceSurface = GetSurface(AI\EN, 1)
									BodySurface = GetSurface(AI\EN, 2)
								EndIf

								; Paint
								Br = CreateBrush()
								BrushTexture(Br, GetTexture(BodyTex))
								PaintSurface(BodySurface, Br)
								BrushTexture(Br, GetTexture(FaceTex))
								PaintSurface(FaceSurface, Br)
								FreeBrush(Br)
								UnloadTexture(BodyTex)
								UnloadTexture(FaceTex)
							Else
								EntityTexture AI\EN, GetTexture(BodyTex)
								UnloadTexture(BodyTex)
							EndIf
					End Select
				EndIf

			; Party changed
			Case P_PartyUpdate
				Offset = 1
				For i = 0 To 6
					NameLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
					PartyName$(i) = Mid$(M\MessageData$, Offset + 1, NameLen)
					Offset = Offset + 1 + NameLen
					GY_UpdateLabel(LPartyName(i), PartyName$(i), 0, 255, 0)
				Next

			; Actor effect added, removed, or updated
			Case P_ActorEffect
				; Added
				If Left$(M\MessageData$, 1) = "A"
					EI.EffectIcon = New EffectIcon
					EI\ID = RCE_IntFromStr(Mid$(M\MessageData$, 2, 4))
					EI\TextureID = RCE_IntFromStr(Mid$(M\MessageData$, 6, 2))
					EI\Name$ = Mid$(M\MessageData$, 8)
					UpdateEffectIcons()
				; Effect updated
				ElseIf Left$(M\MessageData$, 1) = "E"
					Att = RCE_IntFromStr(Mid$(M\MessageData$, 2, 1))
					Amount = RCE_IntFromStr(Mid$(M\MessageData$, 3, 4))
					Me\Attributes\Value[Att] = Me\Attributes\Value[Att] + Amount
				; Removed
				ElseIf Left$(M\MessageData$, 1) = "R"
					ID = RCE_IntFromStr(Mid$(M\MessageData$, 2, 4))
					For EI.EffectIcon = Each EffectIcon
						If EI\ID = ID
							Delete EI
							UpdateEffectIcons()
							Exit
						EndIf
					Next
					For i = 0 To 39
						Amount = RCE_IntFromStr(Mid$(M\MessageData$, 6 + (i * 4), 4))
						Me\Attributes\Value[i] = Me\Attributes\Value[i] - Amount
					Next
				EndIf

			; Trading updated
			Case P_UpdateTrading
				If TradingVisible = True
					Slot = RCE_IntFromStr(Mid$(M\MessageData$, 1, 1))
					Amount = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))
					; Remove item
					If Amount = 0
						For i = 0 To 31
							If ServerTradeIDs(i) = Slot
								ServerTradeIDs(i) = 0
								TradeAmounts(i) = 0
								If TradeItems(i) <> Null Then FreeItemInstance(TradeItems(i))
								GYG.GY_Gadget = Object.GY_Gadget(BSlotsHis(i))
								GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
								EntityTexture GYB\Gadget\EN, GYB\UserTexture
								GY_SetButtonLabel(BSlotsHis(i), "")
								Exit
							EndIf
						Next
					; Add item
					Else
						For i = 0 To 31
							If TradeItems(i) = Null
								ServerTradeIDs(i) = Slot
								TradeAmounts(i) = Amount
								TradeItems(i) = ItemInstanceFromString(Mid$(M\MessageData$, 4))
								GYG.GY_Gadget = Object.GY_Gadget(BSlotsHis(i))
								GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
								EntityTexture GYB\Gadget\EN, GetTexture(TradeItems(i)\Item\ThumbnailTexID)
								If TradeAmounts(i) > 1
									GY_SetButtonLabel(BSlotsHis(i), TradeAmounts(i), 100, 255, 0, True)
								Else
									GY_SetButtonLabel(BSlotsHis(i), "")
								EndIf
								Exit
							EndIf
						Next
					EndIf
				EndIf

			; Trading closed
			Case P_CloseTrading
				If TradingVisible = True
					GY_GadgetAlpha(WTrading, 0.0, True)
					GY_Modal = False
					TradingVisible = False
				EndIf


			; Trading opened
			Case P_OpenTrading
				If TradePackets < 3
					TradePackets = TradePackets + 1
					Num = Mid$(M\MessageData$, 1, 1)
					Total = Mid$(M\MessageData$, 2, 1)
					If Num = 1 Then TradeMsg1$ = Mid$(M\MessageData$, 3)
					If Num = 2 Then TradeMsg2$ = Mid$(M\MessageData$, 3)
					If Num = 3 Then TradeMsg3$ = Mid$(M\MessageData$, 3)

					If TradePackets = Total And TradingVisible = False
						; Assemble message
						TradePackets = 0
						M\MessageData$ = TradeMsg1$ + TradeMsg2$ + TradeMsg3$

						; Clear trading window
						GY_GadgetAlpha(WTrading, 0.85, True)
						GY_ActivateWindow(WTrading)
						TradingVisible = True
						GY_Modal = True
						GY_UpdateLabel(LTradingGold, LanguageString$(LS_Money) + " " + Money$(Me\Gold))
						GY_UpdateLabel(LTradingCost, LanguageString$(LS_Cost) + " " + Money$(0))
						For i = 0 To 31
							If TradeItems(i) <> Null Then FreeItemInstance(TradeItems(i))
							TradeAmounts(i) = 0
							GYG.GY_Gadget = Object.GY_Gadget(BSlotsHis(i))
							GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
							EntityTexture GYB\Gadget\EN, GYB\UserTexture
							GY_SetButtonState(BSlotsHis(i), True)
							GY_LockGadget(BSlotsHis(i), True)
							GY_SetButtonLabel(BSlotsHis(i), "")
							GYG.GY_Gadget = Object.GY_Gadget(BSlotsMine(i))
							GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
							EntityTexture GYB\Gadget\EN, GYB\UserTexture
							GY_SetButtonState(BSlotsMine(i), True)
							GY_LockGadget(BSlotsMine(i), True)
							GY_SetButtonLabel(BSlotsMine(i), "")
							If Me\Inventory\Items[i + SlotI_Backpack] <> Null
								EntityTexture GYB\Gadget\EN, GetTexture(Me\Inventory\Items[i + SlotI_Backpack]\Item\ThumbnailTexID)
								GY_SetButtonState(BSlotsMine(i), False)
								If Me\Inventory\Amounts[i + SlotI_Backpack] > 1
									GY_SetButtonLabel(BSlotsMine(i), Me\Inventory\Amounts[i + SlotI_Backpack], 100, 255, 0, True)
								Else
									GY_SetButtonLabel(BSlotsMine(i), "")
								EndIf
								GY_LockGadget(BSlotsMine(i), False)
							EndIf
						Next

						; Trade with NPC or scenery object
						TradeWith$ = Left$(M\MessageData$, 1)
						If TradeWith$ = "N" Or TradeWith$ = "S"
							If TradeWith$ = "S"
								GY_GadgetAlpha(LTradingCost, 0.0)
								TradeType = 3
							Else
								TradeType = 1
							EndIf
							GY_GadgetAlpha(BCostUp, 0.0)
							GY_GadgetAlpha(BCostDown, 0.0)
							Offset = 2
							Num = 0
							LockTextures()
							While Offset < Len(M\MessageData$)
								Item.ItemInstance = ItemInstanceFromString(Mid$(M\MessageData$, Offset, ItemInstanceStringLength()))
								TradeItems(Num) = Item
								Offset = Offset + ItemInstanceStringLength()
								TradeAmounts(Num) = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 2))
								ServerTradeIDs(Num) = RCE_IntFromStr(Mid$(M\MessageData$, Offset + 2, 4))
								Offset = Offset + 6
								GYG.GY_Gadget = Object.GY_Gadget(BSlotsHis(Num))
								GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
								EntityTexture GYB\Gadget\EN, GetTexture(Item\Item\ThumbnailTexID)
								GY_SetButtonState(BSlotsHis(Num), False)
								If TradeAmounts(Num) > 1
									GY_SetButtonLabel(BSlotsHis(Num), TradeAmounts(Num), 100, 255, 0, True)
								Else
									GY_SetButtonLabel(BSlotsHis(Num), "")
								EndIf
								GY_LockGadget(BSlotsHis(Num), False)
								Num = Num + 1
							Wend
							UnlockTextures()
						; Trade with player
						ElseIf TradeWith$ = "P"
							TradeType = 2
							TradeCost = 0
						EndIf
					EndIf
				EndIf

			; Screen flash
			Case P_ScreenFlash
				Red = RCE_IntFromStr(Mid$(M\MessageData$, 1, 1))
				Green = RCE_IntFromStr(Mid$(M\MessageData$, 2, 1))
				Blue = RCE_IntFromStr(Mid$(M\MessageData$, 3, 1))
				Alpha# = RCE_IntFromStr(Mid$(M\MessageData$, 4, 1)) / 255.0
				Length = RCE_IntFromStr(Mid$(M\MessageData$, 5, 4))
				TexID = RCE_IntFromStr(Mid$(M\MessageData$, 9, 2))
				ScreenFlash(Red, Green, Blue, TexID, Length, Alpha#)

			; Received XP points, or somebody got a level-up
			Case P_XPUpdate
				; The position of my XP bar changed
				If Left(M\MessageData$, 1) = "B"
					Me\XPBarLevel = RCE_IntFromStr(Mid$(M\MessageData$, 2))
					UpdateXPBar()
				; I received XP points
				ElseIf Left(M\MessageData$, 1) = "M"
					XP = RCE_IntFromStr(Mid$(M\MessageData$, 2))
					Me\XP = Me\XP + XP
					Output(Str$(XP) + " " + LanguageString$(LS_XPReceived), 255, 225, 100)
				; Another actor's level changed
				ElseIf Left(M\MessageData$, 1) = "L"
					RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))
					Level = RCE_IntFromStr(Mid$(M\MessageData$, 4, 2))
					AI.ActorInstance = RuntimeIDList(RuntimeID)
					If AI <> Null Then AI\Level = Level
				; My level has been changed
				ElseIf Left(M\MessageData$, 1) = "U"
					Level = RCE_IntFromStr(Mid$(M\MessageData$, 2))
					Me\Level = Level
					Me\XP = 0
				EndIf

			; Animate actor
			Case P_AnimateActor
				RuntimeID = RCE_IntFromStr(Left$(M\MessageData$, 2))
				A.ActorInstance = RuntimeIDList(RuntimeID)
				If A <> Null
					FixedSpeed = RCE_IntFromStr(Mid$(M\MessageData$, 3, 1))
					Speed# = RCE_FloatFromStr#(Mid$(M\MessageData$, 4, 4))
					Anim$ = Mid$(M\MessageData$, 8)
					If A\Gender = 0
						ID = FindAnimation(AnimList(A\Actor\MAnimationSet), Anim$)
					Else
						ID = FindAnimation(AnimList(A\Actor\FAnimationSet), Anim$)
					EndIf
					If ID > -1
						PlayAnimation(A, 3, Speed#, ID, FixedSpeed)
						A\DestX# = EntityX#(A\CollisionEN)
						A\DestZ# = EntityZ#(A\CollisionEN)
					EndIf
				EndIf

			; Actor speech
			Case P_Speech
				ID = RCE_IntFromStr(Mid$(M\MessageData$, 1, 2))
				AI.ActorInstance = RuntimeIDList(RCE_IntFromStr(Mid$(M\MessageData$, 3, 2)))
				If AI <> Null Then PlayActorSound(AI, ID)

			; Sound effect
			Case P_Sound
				ID = RCE_IntFromStr(Mid$(M\MessageData$, 1, 2))
				Name$ = GetSoundName$(ID)
				S = GetSound(ID)
				If Asc(Right$(Name$, 1)) = True
					AI.ActorInstance = RuntimeIDList(RCE_IntFromStr(Mid$(M\MessageData$, 3, 2)))
					If AI <> Null
						Channel = EmitSound(S, AI\CollisionEN)
						If Channel <> 0 Then ChannelVolume(Channel, DefaultVolume#)
					EndIf
				Else
					Channel = PlaySound(S)
					If Channel <> 0 Then ChannelVolume(Channel, DefaultVolume#)
				EndIf

			; Music
			Case P_Music
				Name$ = GetMusicName$(RCE_IntFromStr(Mid$(M\MessageData$, 1, 2)))
				Loaded = LoadSound("Data\Music\" + Name$, False)
				LoopSound Loaded
				Channel = PlaySound(Loaded)
				If Channel <> 0 Then ChannelVolume(Channel, DefaultVolume#)

			; Particles effect
			Case P_CreateEmitter
				TexID = RCE_IntFromStr(Mid$(M\MessageData$, 1, 2))
				Time = RCE_IntFromStr(Mid$(M\MessageData$, 3, 4))
				RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 7, 2))
				XPos# = RCE_FloatFromStr#(Mid$(M\MessageData$, 9, 4))
				YPos# = RCE_FloatFromStr#(Mid$(M\MessageData$, 13, 4))
				ZPos# = RCE_FloatFromStr#(Mid$(M\MessageData$, 17, 4))
				Name$ = Mid$(M\MessageData$, 21)
				Em.ScriptedEmitter = New ScriptedEmitter
				Em\Length = Time
				Em\StartTime = MilliSecs()
				Config = RP_LoadEmitterConfig("Data\Emitter Configs\" + Name$ + ".rpc", GetTexture(TexID), Cam)
				If Config = 0 Then RuntimeError("Could not load emitter: " + Name$ + "!")
				Em\EN = RP_CreateEmitter(Config)
				AI.ActorInstance = RuntimeIDList(RuntimeID)
				If AI <> Null
					EntityParent(Em\EN, AI\CollisionEN)
					RotateEntity(Em\EN, EntityPitch#(AI\CollisionEN), EntityYaw#(AI\CollisionEN), EntityRoll#(AI\CollisionEN))
					If AI = Me Then Em\AttachedToPlayer = True
				EndIf
				PositionEntity Em\EN, XPos#, YPos#, ZPos#

			; Known spell update
			Case P_KnownSpellUpdate
				; Spell added
				If Left$(M\MessageData$, 1) = "A"
					If Me\SpellLevels[Spells] = 0
						DebugLog "Spell creation entered"
						Offset = 2
						Me\SpellLevels[Spells] = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 2))
						Sp.Spell = New Spell
						Sp\ID = RCE_IntFromStr(Mid$(M\MessageData$, Offset + 2, 2))
						SpellsList(Sp\ID) = Sp
						Me\KnownSpells[Spells] = Sp\ID
						Sp\ThumbnailTexID = RCE_IntFromStr(Mid$(M\MessageData$, Offset + 4, 2))
						Sp\RechargeTime = RCE_IntFromStr(Mid$(M\MessageData$, Offset + 6, 2))
						Offset = Offset + 8
						NameLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 2))
						Sp\Name$ = Mid$(M\MessageData$, Offset + 2, NameLen)
						Offset = Offset + 2 + NameLen
						NameLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 2))
						Sp\Description$ = Mid$(M\MessageData$, Offset + 2, NameLen)
						Offset = Offset + 2 + NameLen
						If RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1)) = True And Memorised < 10
							Me\MemorisedSpells[Memorised] = Spells
							Memorised = Memorised + 1
						EndIf
						Spells = Spells + 1
						SortSpells()
					EndIf
				; Spell removed
				ElseIf Left$(M\MessageData$, 1) = "D"
					Name$ = Upper$(Mid$(M\MessageData$, 2))
					; Remove the spell from spellbar
					found = False
					If ActionBarStart = 2
						Offset = 12
					ElseIf ActionBarStart = 3
						Offset = 24
					EndIf
					For i = 0 To 11
						slot = i + offset
						If ActionBarSlots(slot) < 0
							If RequireMemorise
								Num = ActionBarSlots(slot) + 10
								If Upper$(SpellsList(Me\KnownSpells[Me\MemorisedSpells[Num]])\Name$) = Name$
									found = True
								EndIf
							Else
								Num = ActionBarSlots(slot) + 1000
								If Upper$(SpellsList(Me\KnownSpells[Num])\Name$) = Name$
									found = True
								EndIf
							EndIf
							If found = True
								ActionBarSlots(slot) = 0
								GYG.GY_Gadget = Object.GY_Gadget(BActionBar(i))
								GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
								EntityTexture GYB\Gadget\EN, GYB\UserTexture
							EndIf
							found = False
						EndIf
					Next
					
					; Remove memorised
					For i = 0 To 9
						If Me\MemorisedSpells[i] <> 5000
							If Upper$(SpellsList(Me\KnownSpells[Me\MemorisedSpells[i]])\Name$) = Name$ Then Me\MemorisedSpells[i] = 5000
						EndIf
					Next
					; Remove known
					For i = 0 To 999
						If Me\SpellLevels[i] > 0
							If Upper$(SpellsList(Me\KnownSpells[i])\Name$) = Name$
								Me\KnownSpells[i] = 0
								Me\SpellLevels[i] = 0
							EndIf
						EndIf
					Next
					If SpellsVisible Then UpdateSpellbook()
				; Spell level update
				ElseIf Left$(M\MessageData$, 1) = "L"
					Level = RCE_IntFromStr(Mid$(M\MessageData$, 2, 4))
					Name$ = Upper$(Mid$(M\MessageData$, 6))
					For i = 0 To 999
						If Me\SpellLevels[i] > 0
							If Upper$(SpellsList(Me\KnownSpells[i])\Name$) = Name$ Then Me\SpellLevels[i] = Level
						EndIf
					Next
					If SpellsVisible Then UpdateSpellbook()
				EndIf

			; Name change
			Case P_NameChange
				RuntimeID = RCE_IntFromStr(Left$(M\MessageData$, 2))
				A.ActorInstance = RuntimeIDList(RuntimeID)
				If A <> Null
					NameLen = RCE_IntFromStr(Mid$(M\MessageData$, 3, 1))
					A\Name$ = Mid$(M\MessageData$, 4, NameLen)
					A\Tag$ = Mid$(M\MessageData$, 4 + NameLen)
					If A <> Me Then CreateActorNametag(A)
				EndIf

			; Gold change
			Case P_GoldChange
				Amount = RCE_IntFromStr(Mid$(M\MessageData$, 2))
				If Left$(M\MessageData$, 1) = "D" Then Amount = 0 - Amount
				Me\Gold = Me\Gold + Amount
				If Me\Gold < 0 Then Me\Gold = 0
				If InventoryVisible = True Then GY_UpdateLabel(LInventoryGold, Money$(Me\Gold))

			; Quest log update
			Case P_QuestLog
				; New entry
				If Left$(M\MessageData$, 1) = "N"
					For i = 0 To 499
						If QuestLog\EntryName$[i] = ""
							NameLen = RCE_IntFromStr(Mid$(M\MessageData$, 2, 1))
							QuestLog\EntryName$[i] = Mid$(M\MessageData$, 3, NameLen)
							Offset = 3 + NameLen
							NameLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 2))
							QuestLog\EntryStatus$[i] = Mid$(M\MessageData$, Offset + 2, NameLen)
							Output(LanguageString$(LS_QuestLogUpdate), 255, 225, 100)
							Exit
						EndIf
					Next
				; Status update
				ElseIf Left$(M\MessageData$, 1) = "U"
					NameLen = RCE_IntFromStr(Mid$(M\MessageData$, 2, 1))
					Name$ = Upper$(Mid$(M\MessageData$, 3, NameLen))
					Offset = 3 + NameLen
					For i = 0 To 499
						If Upper$(QuestLog\EntryName$[i]) = Name$
							NameLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 2))
							QuestLog\EntryStatus$[i] = Mid$(M\MessageData$, Offset + 2, NameLen)
							Output(LanguageString$(LS_QuestLogUpdate), 255, 225, 100)
							Exit
						EndIf
					Next
				; Quest deleted
				ElseIf Left$(M\MessageData$, 1) = "D"
					Name$ = Upper$(Mid$(M\MessageData$, 2))
					For i = 0 To 499
						If Upper$(QuestLog\EntryName$[i]) = Name$
							QuestLog\EntryName$[i] = ""
							QuestLog\EntryStatus$[i] = ""
							Exit
						EndIf
					Next
				EndIf
				If QuestLogVisible Then RedrawQuestLog()

			; Character stat update
			Case P_StatUpdate
				A.ActorInstance = RuntimeIDList(RCE_IntFromStr(Mid$(M\MessageData$, 2, 2)))
				If Left$(M\MessageData$, 1) = "A"
					Attribute = RCE_IntFromStr(Mid$(M\MessageData$, 4, 1))
					A\Attributes\Value[Attribute] = RCE_IntFromStr(Mid$(M\MessageData$, 5, 2))
				ElseIf Left$(M\MessageData$, 1) = "M"
					Attribute = RCE_IntFromStr(Mid$(M\MessageData$, 4, 1))
					A\Attributes\Maximum[Attribute] = RCE_IntFromStr(Mid$(M\MessageData$, 5, 2))
				ElseIf Left$(M\MessageData$, 1) = "R"
					A\Reputation = RCE_IntFromStr(Mid$(M\MessageData$, 4, 2))
				EndIf

			; Scripted text input dialog
			Case P_ScriptInput
				NameLen = RCE_IntFromStr(Mid$(M\MessageData, 6, 2))
				Title$ = Mid$(M\MessageData$, 8, NameLen)
				Prompt$ = Mid$(M\MessageData$, 8 + NameLen)
				CreateTextInput(Title$, Prompt$, RCE_IntFromStr(Mid$(M\MessageData, 5, 1)), RCE_IntFromStr(Mid$(M\MessageData, 1, 4)))

			; Dialog message
			Case P_Dialog
				Select Left$(M\MessageData$, 1)
					; New dialog
					Case "N"
						RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 6, 2))
						If RuntimeID < 65535 Then A.ActorInstance = RuntimeIDList(RuntimeID) Else A = Null
						D = CreateDialog(Mid$(M\MessageData$, 10), A, RCE_IntFromStr(Mid$(M\MessageData, 2, 4)), RCE_IntFromStr(Mid$(M\MessageData$, 8, 2)))
						RCE_Send(Connection, PeerToHost, P_Dialog, "N" + Mid$(M\MessageData, 2, 4) + RCE_StrFromInt$(D), True)
						Me\DestX# = EntityX#(Me\CollisionEN)
						Me\DestZ# = EntityZ#(Me\CollisionEN)
						If A <> Null
							; Face player towards dialog actor
							PointEntity Me\CollisionEN, A\CollisionEN
							RotateEntity Me\CollisionEN, 0.0, EntityYaw#(Me\CollisionEN) + 180.0, 0.0
						; npc rotate to see you terrier
						PointEntity  A\CollisionEN, Me\CollisionEN ; add 
						RotateEntity A\CollisionEN, 0.0, EntityYaw#(A\CollisionEN) + 180.0, 0.0 ; add 

						
						
						EndIf
					; Dialog text
					Case "T"
						Red = RCE_IntFromStr(Mid$(M\MessageData$, 2, 1))
						Green = RCE_IntFromStr(Mid$(M\MessageData$, 3, 1))
						Blue = RCE_IntFromStr(Mid$(M\MessageData$, 4, 1))
						D = RCE_IntFromStr(Mid$(M\MessageData$, 5, 4))
						DialogOutput(D, Mid$(M\MessageData$, 9), Red, Green, Blue)
						RCE_Send(Connection, PeerToHost, P_Dialog, "T" + RCE_StrFromInt$(DialogScriptHandle(D)), True)
					; Dialog options
					Case "O"
						D = RCE_IntFromStr(Mid$(M\MessageData$, 2, 4))
						Offset = 6
						While Offset < Len(M\MessageData$)
							NameLen = RCE_IntFromStr(Mid$(M\MessageData$, Offset, 1))
							AddDialogOption(D, Mid$(M\MessageData$, Offset + 1, NameLen))
							Offset = Offset + NameLen + 1
						Wend
					; Close dialog
					Case "C"
						FreeDialog(RCE_IntFromStr(Mid$(M\MessageData$, 2)))
				End Select

			; Actor dead
			Case P_ActorDead
				RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 1, 2))
				A.ActorInstance = RuntimeIDList(RuntimeID)
				If A <> Null
					; Dismount if required
					If A\Mount <> Null
						A\Mount\Rider = Null
						A\Mount = Null
					EndIf
					If A\Rider <> Null
						A\Rider\Mount = Null
						A\Rider = Null
					EndIf

					; If I killed it, display message
					If Len(M\MessageData$) > 2
						RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 3, 2))
						Killer.ActorInstance = RuntimeIDList(RuntimeID)
						Name$ = Trim$(A\Name$)
						If Name$ = "" Then Name$ = A\Actor\Race$
						If Killer = Me Then Output(LanguageString$(LS_YouKilled) + " " + Name$ + "!", 0, 255, 0)
					EndIf
					; Death sound/animation
					PlayActorSound(A, Speech_Death)
					Animate(A\EN, 0)
					PlayAnimation(A, 3, 0.02, Rand(Anim_FirstDeath, Anim_LastDeath))
					; Remove the actor
					A\Attributes\Value[HealthStat] = 0
					A\AIMode = 501 ; For fade out
					;FreeEntity A\ShadowEN : A\ShadowEN = 0 ;[###]
					FreeShadowCaster% (A\EN)

					FreeEntity A\NametagEN : A\NametagEN = 0
					EntityType A\CollisionEN, 0
					If Handle(A) = PlayerTarget Then PlayerTarget = 0 : AttackTarget = False
					If A = CharInteract Then DestroyCharInteractionWindow()

					; Free any dialogs
					For Di.Dialog = Each Dialog
						If Di\ActorInstance = A Then FreeDialog(Handle(Di))
					Next
				EndIf

			; Actor attacked
			Case P_AttackActor
				RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))
				A.ActorInstance = RuntimeIDList(RuntimeID)
				If A <> Null
					; I attacked someone else
					If Left$(M\MessageData$, 1) = "H"
						AnimateActorAttack(Me)
						PlayActorSound(Me, Rand(Speech_Attack1, Speech_Attack2))
						Damage = RCE_IntFromStr(Mid$(M\MessageData$, 4, 2)) - 1
						DType$ = DamageTypes$(RCE_IntFromStr(Mid$(M\MessageData$, 6, 1)))
						; And hit them
						If Damage > 0
							CombatDamageOutput(A, Damage, DType$)
							A\Attributes\Value[HealthStat] = A\Attributes\Value[HealthStat] - Damage
							PlayAnimation(A, 3, 0.035, Rand(Anim_FirstHit, Anim_LastHit))
							PlayActorSound(A, Rand(Speech_Hit1, Speech_Hit2))
							If A\Actor\BloodTexID > 0
								B.BloodSpurt = New BloodSpurt
								B\Timer = MilliSecs()
								B\EmitterEN = RP_CreateEmitter(A\Actor\BloodTexID)
								PositionEntity(B\EmitterEN, EntityX#(A\CollisionEN), EntityY#(A\CollisionEN), EntityZ#(A\CollisionEN))
								PointEntity(B\EmitterEN, Me\CollisionEN)
								MoveEntity(B\EmitterEN, 0.0, 0.0, 1.0)
							EndIf
						; And missed
						ElseIf Damage < 0
							CombatDamageOutput(A, 0, "0")
							AnimateActorParry(A)
						EndIf
					; Someone else attacked me
					ElseIf Left$(M\MessageData$, 1) = "Y"
						Damage = RCE_IntFromStr(Mid$(M\MessageData$, 4, 2)) - 1
						DType$ = DamageTypes$(RCE_IntFromStr(Mid$(M\MessageData$, 6, 1)))
						PointEntity A\CollisionEN, Me\CollisionEN
						RotateEntity A\CollisionEN, 0.0, EntityYaw#(A\CollisionEN) + 180.0, 0.0
						; And hit me
						If Damage > 0
							CombatDamageOutput(A, -Damage, DType$)
							Me\Attributes\Value[HealthStat] = Me\Attributes\Value[HealthStat] - Damage
							AnimateActorAttack(A)
							PlayAnimation(Me, 3, 0.035, Rand(Anim_FirstHit, Anim_LastHit))
							PlayActorSound(A, Rand(Speech_Attack1, Speech_Attack2))
							PlayActorSound(Me, Rand(Speech_Hit1, Speech_Hit2))
							If Me\Actor\BloodTexID > 0
								B.BloodSpurt = New BloodSpurt
								B\Timer = MilliSecs()
								B\EmitterEN = RP_CreateEmitter(Me\Actor\BloodTexID)
								PositionEntity(B\EmitterEN, EntityX#(Me\CollisionEN), EntityY#(Me\CollisionEN), EntityZ#(Me\CollisionEN))
								PointEntity(B\EmitterEN, A\CollisionEN)
								MoveEntity(B\EmitterEN, 0.0, 0.0, 1.0)
							EndIf
						; And missed
						ElseIf Damage < 0
							CombatDamageOutput(A, 0, "1")
							AnimateActorAttack(A)
							AnimateActorParry(Me)
							PlayActorSound(A, Rand(Speech_Attack1, Speech_Attack2))
						EndIf
					; Someone else attacked someone else
					Else
						RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 4, 2))
						A2.ActorInstance = RuntimeIDList(RuntimeID)
						If A2 <> Null
							AnimateActorAttack(A)
							A2\Attributes\Value[HealthStat] = A2\Attributes\Value[HealthStat] - Damage
							PlayAnimation(A2, 3, 0.035, Rand(Anim_FirstHit, Anim_LastHit))
							PlayActorSound(A, Rand(Speech_Attack1, Speech_Attack2))
							PlayActorSound(A2, Rand(Speech_Hit1, Speech_Hit2))
							If A2\Actor\BloodTexID > 0
								B.BloodSpurt = New BloodSpurt
								B\Timer = MilliSecs()
								B\EmitterEN = RP_CreateEmitter(A2\Actor\BloodTexID)
								PositionEntity(B\EmitterEN, EntityX#(A2\CollisionEN), EntityY#(A2\CollisionEN), EntityZ#(A2\CollisionEN))
								PointEntity(B\EmitterEN, A\CollisionEN)
								MoveEntity(B\EmitterEN, 0.0, 0.0, 1.0)
							EndIf
							PointEntity A\CollisionEN, A2\CollisionEN
							RotateEntity A\CollisionEN, 0.0, EntityYaw#(A\CollisionEN) + 180.0, 0.0
						EndIf
					EndIf
					If A = CharInteract Then UpdateCharInteractionWindow()
				EndIf

			; Chat bubble message
			Case P_BubbleMessage
				AI.ActorInstance = RuntimeIDList(RCE_IntFromStr(Left$(M\MessageData$, 2)))
				If AI <> Null
					Red = Asc(Mid$(M\MessageData$, 3, 1))
					Green = Asc(Mid$(M\MessageData$, 4, 1))
					Blue = Asc(Mid$(M\MessageData$, 5, 1))
					BubbleOutput(Mid$(M\MessageData$, 6), Red, Green, Blue, AI)
				EndIf

			; Chat message
			Case P_ChatMessage
				; Special colours
				If Asc(Left$(M\MessageData$, 1)) = 254
					Output(Mid$(M\MessageData$, 2), 255, 255, 0)
				ElseIf Asc(Left$(M\MessageData$, 1)) = 253
					Output(Mid$(M\MessageData$, 2), 255, 50, 50)
				ElseIf Asc(Left$(M\MessageData$, 1)) = 252
					Output(Mid$(M\MessageData$, 2), 200, 10, 200)
				ElseIf Asc(Left$(M\MessageData$, 1)) = 251
					Output(Mid$(M\MessageData$, 2), 20, 220, 50)
				ElseIf Asc(Left$(M\MessageData$, 1)) = 250
					Red = Asc(Mid$(M\MessageData$, 2, 1))
					Green = Asc(Mid$(M\MessageData$, 3, 1))
					Blue = Asc(Mid$(M\MessageData$, 4, 1))
					Output(Mid$(M\MessageData$, 5), Red, Green, Blue)
				; Normal
				Else
					; Use a chat bubble
					If Left$(M\MessageData$, 1) = "<" And UseBubbles > 1
						; Find actor
						Found = False
						Pos = Instr(M\MessageData$, ">")
						If Pos > 0
							Name$ = Mid$(M\MessageData$, 2, Pos - 2)
							AI.ActorInstance = FindPlayerFromName(Name$)
							If AI <> Null Then Found = True
						EndIf
						; Found, create bubble
						If Found = True
							If AI = Me
								BubbleOutput(Mid$(M\MessageData$, Pos + 2), 0, 128, 255, AI, UseBubbles - 2)
							Else
								BubbleOutput(Mid$(M\MessageData$, Pos + 2), BubblesR, BubblesG, BubblesB, AI, UseBubbles - 2)
							EndIf
						; No actor found, use normal text output
						Else
							If Instr(M\MessageData$, "<" + Me\Name$ + ">") = 1
								Output(M\MessageData$, 0, 128, 255)
							Else
								Output(M\MessageData$)
							EndIf
						EndIf
					; Normal text output
					Else
						If Instr(M\MessageData$, "<" + Me\Name$ + ">") = 1
							Output(M\MessageData$, 0, 128, 255)
						Else
							Output(M\MessageData$)
						EndIf
					EndIf
				EndIf

			; Weather change
			Case P_WeatherChange
				ServerArea = RCE_IntFromStr(Mid$(M\MessageData$, 1, 4))
				If ServerArea = CurrentAreaID Then SetWeather(RCE_IntFromStr(Mid$(M\MessageData$, 5, 1)))

			; Inventory update
			Case P_InventoryUpdate
				Select Left$(M\MessageData$, 1)
					; An item health has changed
					Case "H"
						SlotI = RCE_IntFromStr(Mid$(M\MessageData$, 2, 1))
						Amount = RCE_IntFromStr(Mid$(M\MessageData$, 3, 1))
						If Me\Inventory\Items[SlotI] <> Null
							Me\Inventory\Items[SlotI]\ItemHealth = Amount
						EndIf
					; An item has been taken from my inventory
					Case "T"
						SlotI = RCE_IntFromStr(Mid$(M\MessageData$, 2, 1))
						Amount = RCE_IntFromStr(Mid$(M\MessageData$, 3, 2))
						If Me\Inventory\Items[SlotI] <> Null
							Me\Inventory\Amounts[SlotI] = Me\Inventory\Amounts[SlotI] - Amount
							; All gone
							If Me\Inventory\Amounts[SlotI] <= 0
								; Remove item
								Me\Inventory\Amounts[SlotI] = 0
								FreeItemInstance(Me\Inventory\Items[SlotI])

								; Visual stuff
								If InventoryVisible = True
									GY_SetButtonState(BSlots(SlotI), True)
									GY_SetButtonLabel(BSlots(SlotI), "")
									GYG.GY_Gadget = Object.GY_Gadget(BSlots(SlotI))
									GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
									EntityTexture GYB\Gadget\EN, GYB\UserTexture
									If MouseSlotSource = SlotI Or MouseSlotSource = -1
										HideEntity MouseSlotEN : MouseSlotItem = Null : MouseSlotAmount = 0 : MouseSlotSource = -1
										EnableInventoryBlanks(True)
									Else
										EnableInventoryBlanks(False)
									EndIf
								EndIf
								UpdateActorItems(Me)
							; Not all gone but update the amount
							ElseIf InventoryVisible = True
								GY_SetButtonLabel(BSlots(SlotI), Me\Inventory\Amounts[SlotI], 100, 255, 0, True)
							EndIf
						EndIf
					; I received a dropped item
					Case "R"
						For DItem.DroppedItem = Each DroppedItem
							If DItem\ServerHandle = RCE_IntFromStr(Mid$(M\MessageData$, 2, 4))
								; Put in slot
								i = RCE_IntFromStr(Mid$(M\MessageData$, 6, 1))
								If Me\Inventory\Items[i] <> Null
									FreeItemInstance(Me\Inventory\Items[i])
								Else
									Me\Inventory\Amounts[i] = 0
								EndIf
								Me\Inventory\Items[i] = DItem\Item
								Me\Inventory\Amounts[i] = Me\Inventory\Amounts[i] + DItem\Amount

								; Visual stuff
								If InventoryVisible = True
									GYG.GY_Gadget = Object.GY_Gadget(BSlots(i))
									GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
									EntityTexture GYB\Gadget\EN, GetTexture(Me\Inventory\Items[i]\Item\ThumbnailTexID)
									GY_SetButtonState(BSlots(i), False)
									If Me\Inventory\Amounts[i] > 1
										GY_SetButtonLabel(BSlots(i), Me\Inventory\Amounts[i], 100, 255, 0, True)
									Else
										GY_SetButtonLabel(BSlots(i), "")
									EndIf
									GY_LockGadget(BSlots(i), False)
								EndIf
								UpdateActorItems(Me)

								; Inform user and delete dropped item
								If DItem\Amount > 1
									Output(LanguageString$(LS_PickedUpItem) + " " + DItem\Item\Item\Name$ + " (x" + DItem\Amount + ")", 0, 255, 0)
								Else
									Output(LanguageString$(LS_PickedUpItem) + " " + DItem\Item\Item\Name$, 0, 255, 0)
								EndIf
								FreeEntity(DItem\EN)
								Delete DItem
								Exit
							EndIf
						Next
					; Dropped item has been picked up by someone else
					Case "P"
						For DItem.DroppedItem = Each DroppedItem
							If DItem\ServerHandle = RCE_IntFromStr(Mid$(M\MessageData$, 2, 4))
								FreeEntity(DItem\EN)
								Delete DItem\Item
								Delete DItem
								Exit
							EndIf
						Next
					; Item dropped
					Case "D"
						; Create dropped item
						DItem.DroppedItem = New DroppedItem
						DItem\Amount = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))
						DItem\X# = RCE_FloatFromStr#(Mid$(M\MessageData$, 4, 4))
						DItem\Y# = RCE_FloatFromStr#(Mid$(M\MessageData$, 8, 4))
						DItem\Z# = RCE_FloatFromStr#(Mid$(M\MessageData$, 12, 4))
						DItem\ServerHandle = RCE_IntFromStr(Mid$(M\MessageData$, 16, 4))
						DItem\Item = ItemInstanceFromString(Mid$(M\MessageData$, 20))
						If DItem\Item = Null Then WriteLog(MainLog,"Dropped Item received but Item not found!") : Delete DItem : Return
						; Find suitable mesh
						If DItem\Item <> Null
							If DItem\Item\Item\MMeshID < 65535
								DItem\EN = GetMesh(DItem\Item\Item\MMeshID)
								If DItem\EN <> 0
									Scale# = LoadedMeshScales#(DItem\Item\Item\MMeshID) * 0.05
								EndIf
							ElseIf DItem\Item\Item\FMeshID < 65535
								DItem\EN = GetMesh(DItem\Item\Item\FMeshID)
								If DItem\EN <> 0
									Scale# = LoadedMeshScales#(DItem\Item\Item\FMeshID) * 0.05
								EndIf
							EndIf
						EndIf
						; Use default loot bag mesh
						If DItem\EN = 0
							DItem\EN = CopyEntity(LootBagEN)
							Y# = 0.0
						; Custom item mesh has been found - set scale and get offset for correct ground level position
						Else
							ScaleEntity(DItem\EN, Scale#, Scale#, Scale#)
							MMV.MeshMinMaxVertices = MeshMinMaxVertices(DItem\EN)
							Y# = 0.0 - (MMV\MinY# * Scale#)
						EndIf
						; Position mesh entity
						SetPickModes()
						Result = LinePick(DItem\X#, DItem\Y# + 10.0, DItem\Z#, 0.0, -1000.0, 0.0)
						If Result <> 0
							PositionEntity DItem\EN, PickedX#(), PickedY#() + Y#, PickedZ#()
						Else
							PositionEntity DItem\EN, DItem\X#, DItem\Y# + Y#, DItem\Z#
						EndIf
						RotateEntity(DItem\EN, 0.0, Rnd#(-180.0, 180.0), 0.0)
						NameEntity(DItem\EN, Handle(DItem))
					; Update for another actor
					Case "O"
						RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 2, 2))
						A.ActorInstance = RuntimeIDList(RuntimeID)
						If A <> Null
							WeaponID = RCE_IntFromStr(Mid$(M\MessageData$, 4, 2))
							ShieldID = RCE_IntFromStr(Mid$(M\MessageData$, 6, 2))
							ChestID = RCE_IntFromStr(Mid$(M\MessageData$, 8, 2))
							HatID = RCE_IntFromStr(Mid$(M\MessageData$, 10, 2))
							If A\Inventory\Items[SlotI_Weapon] <> Null Then FreeItemInstance(A\Inventory\Items[SlotI_Weapon])
							If A\Inventory\Items[SlotI_Shield] <> Null Then FreeItemInstance(A\Inventory\Items[SlotI_Shield])
							If A\Inventory\Items[SlotI_Chest] <> Null Then FreeItemInstance(A\Inventory\Items[SlotI_Chest])
							If A\Inventory\Items[SlotI_Hat] <> Null Then FreeItemInstance(A\Inventory\Items[SlotI_Hat])
							If WeaponID < 65535 Then A\Inventory\Items[SlotI_Weapon] = CreateItemInstance(ItemList(WeaponID))
							If ShieldID < 65535 Then A\Inventory\Items[SlotI_Shield] = CreateItemInstance(ItemList(ShieldID))
							If ChestID < 65535 Then A\Inventory\Items[SlotI_Chest] = CreateItemInstance(ItemList(ChestID))
							If HatID < 65535 Then A\Inventory\Items[SlotI_Hat] = CreateItemInstance(ItemList(HatID))
							UpdateActorItems(A)
							For i = 0 To 5
								If RCE_IntFromStr(Mid$(M\MessageData$, 12 + i, 1)) = True
									ShowGubbin(A, i)
								Else
									HideGubbin(A, i)
								EndIf
							Next
						EndIf
					; Given an item
					Case "G"
						ItemID = RCE_IntFromStr(Mid$(M\MessageData$, 6, 2))
						Amount = RCE_IntFromStr(Mid$(M\MessageData$, 8, 2))
						; Find free slot
						Found = False
						II.ItemInstance = CreateItemInstance(ItemList(ItemID))
						For i = 0 To 49
							If Me\Inventory\Items[i] = Null Or (ItemInstancesIdentical(II, Me\Inventory\Items[i]) And II\Item\Stackable = True And i >= SlotI_Backpack)
								If SlotsMatch(ItemList(ItemID), i) And ActorHasSlot(Me\Actor, i, ItemList(ItemID))
									; Put in slot
									If Me\Inventory\Items[i] <> Null
										FreeItemInstance(Me\Inventory\Items[i])
									Else
										Me\Inventory\Amounts[i] = 0
									EndIf
									Me\Inventory\Items[i] = II
									Me\Inventory\Amounts[i] = Me\Inventory\Amounts[i] + Amount
									If InventoryVisible = True
										GYG.GY_Gadget = Object.GY_Gadget(BSlots(i))
										GYB.GY_Button = Object.GY_Button(GYG\TypeHandle)
										EntityTexture GYB\Gadget\EN, GetTexture(Me\Inventory\Items[i]\Item\ThumbnailTexID)
										GY_SetButtonState(BSlots(i), False)
										If Me\Inventory\Amounts[i] > 1
											GY_SetButtonLabel(BSlots(i), Me\Inventory\Amounts[i], 100, 255, 0, True)
										Else
											GY_SetButtonLabel(BSlots(i), "")
										EndIf
										GY_LockGadget(BSlots(i), False)
									EndIf
									UpdateActorItems(Me)
									; Reply to server
									Pa$ = Mid$(M\MessageData$, 2, 4) + RCE_StrFromInt$(i, 1)
									RCE_Send(Connection, PeerToHost, P_InventoryUpdate, "GY" + Pa$, True)
									Found = True : Exit
								EndIf
							EndIf
						Next
						; If not found, tell server
						If Found = False
							FreeItemInstance(II)
							RCE_Send(Connection, PeerToHost, P_InventoryUpdate, "GN" + Mid$(M\MessageData$, 2, 4), True)
						EndIf
				End Select

			; A standard update for an actor instance
			Case P_StandardUpdate
				RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 1, 2))
				A.ActorInstance = RuntimeIDList(RuntimeID)
				If A <> Null
					; Get position  - only if we are not currenly in the process of moving 
					;If A\IgnoreUpdate = 0
					A\X# = RCE_FloatFromStr#(Mid$(M\MessageData$, 3, 4))
					A\Z# = RCE_FloatFromStr#(Mid$(M\MessageData$, 7, 4))
					;EndIf
					; Get mount if the actor has one
					RuntimeID = RCE_IntFromStr(Mid$(M\MessageData$, 21, 2))
					If RuntimeID <> 0
						Mount.ActorInstance = RuntimeIDList(RuntimeID)
						If Mount <> Null
							; Actor did not have a mount until now
							If A\Mount <> Mount
								A\Mount = Mount
								Mount\Rider = A
								If GetEntityType(A\CollisionEN) <> C_None Then EntityType(A\CollisionEN, C_None)
								If A = Me
									If Handle(Mount) = PlayerTarget Then PlayerTarget = 0 : AttackTarget = False
									PlayActorSound(Mount, Rand(Speech_Greet1, Speech_Greet2))
								EndIf
								Mount\Y# = 0.0
							EndIf
						EndIf
					; Unmount a previously mounted actor
					ElseIf A\Mount <> Null
						If A = Me
							If GetEntityType(A\CollisionEN) <> C_Player Then EntityType(A\CollisionEN, C_Player)
							PlayActorSound(A\Mount, Rand(Speech_Bye1, Speech_Bye2))
						ElseIf A\Actor\PolyCollision = False
							If GetEntityType(A\CollisionEN) <> C_Actor Then EntityType(A\CollisionEN, C_Actor)
						Else
							If GetEntityType(A\CollisionEN) <> C_ActorTri1 Then EntityType(A\CollisionEN, C_ActorTri1)
						EndIf
						Animate(A\EN, 0)
						A\Mount\Rider = Null
						A\Mount = Null
					EndIf
					; For players other than me, read in movement details
					If A <> Me And A <> Me\Mount
						A\IsRunning = RCE_IntFromStr(Mid$(M\MessageData$, 11, 1))
						OldWalkBack = A\WalkingBackward
						A\WalkingBackward = RCE_IntFromStr(Mid$(M\MessageData$, 12, 1))
						A\DestX# = RCE_FloatFromStr#(Mid$(M\MessageData$, 13, 4))
						A\DestZ# = RCE_FloatFromStr#(Mid$(M\MessageData$, 17, 4))
						If A\Actor\Environment = Environment_Fly
							A\Y# = RCE_FloatFromStr#(Mid$(M\MessageData$, 23, 4))
						EndIf
						If OldWalkBack <> A\WalkingBackward
							If CurrentSeq(A) = Anim_Walk Then Animate(A\EN, 0)
						EndIf
					; For myself only, update my energy level if the energy stat is present
					;ElseIf EnergyStat > -1
					;	A\Attributes\Value[EnergyStat] = RCE_IntFromStr(Mid$(M\MessageData$, 23, 2))
					EndIf
				EndIf

			; An actor instance has left my area
			Case P_ActorGone
				RuntimeID = RCE_IntFromStr(M\MessageData$)
				A.ActorInstance = RuntimeIDList(RuntimeID)
				If A <> Null
					If A <> Me
						; Free projectiles targeted at this actor
						For ProjI.ProjectileInstance = Each ProjectileInstance
							If ProjI\Target = A
								FreeProjectileInstance(ProjI)
							EndIf
						Next
						
						;Actor shadows Cysis145
						FreeShadowCaster% (A\EN)
						; Display exit message
						
						If A\RNID = True Then Output(LanguageString$(LS_PlayerLeftZone) + " " + A\Name$, 255, 0, 0)
						; Free actor instance
						SafeFreeActorInstance(A)
					Else
						RuntimeError("Serious error: player object was deleted")
					EndIf
				EndIf

			; A new actor instance has entered my area
			Case P_NewActor
				A.ActorInstance = ActorInstanceFromString(M\MessageData$)
				If A <> Null
				
					FreeShadowCaster% (A\EN)
					
					Result = LoadActorInstance3D(A, 0.05)
					If Result = False Then RuntimeError("Could not load actor mesh for " + A\Actor\Race$ + "!")
					PositionEntity A\CollisionEN, A\X#, A\Y#, A\Z#
					If A\Actor\Environment <> Environment_Fly
						SetPickModes()
						Height# = LoadedMeshScales#(A\Actor\MeshIDs[A\Gender]) * A\Actor\Scale# * MeshHeight#(A\EN) * 0.05
						Result = LinePick(A\X#, A\Y# + 5.0, A\Z#, 0.0, -10000.0, 0.0)
						If Result <> 0
							PositionEntity A\CollisionEN, A\X#, PickedY#() + Height#, A\Z#
						Else
							Result = LinePick(A\X#, A\Y# + 10000.0, A\Z#, 0.0, -20000.0, 0.0)
							If Result <> 0
								PositionEntity A\CollisionEN, A\X#, PickedY#() + Height#, A\Z#
							EndIf
						EndIf
					EndIf
					RotateEntity(A\CollisionEN, 0, A\Yaw#, 0)
					A\Y# = 0.0
					ResetEntity(A\CollisionEN)
					If A\RNID = True And MilliSecs() - ZonedMS > 5000
						Output(LanguageString$(LS_PlayerEnteredZone) + " " + A\Name$, 0, 100, 255)
					EndIf
				EndIf

			; I have gone into a new area
			Case P_ChangeArea
				; Retrieve info for new zone
				OldAreaName$ = AreaName$
				OldAreaID = CurrentAreaID
				Me\X# = RCE_FloatFromStr#(Mid$(M\MessageData$, 1, 4))
				Y# = RCE_FloatFromStr#(Mid$(M\MessageData$, 5, 4))
				Me\Z# = RCE_FloatFromStr#(Mid$(M\MessageData$, 9, 4))
				If Me\Actor\Environment = Environment_Fly
					Me\Y# = Y#
				Else
					Me\Y# = 0.0
				EndIf
				Me\DestX# = Me\X#
				Me\DestZ# = Me\Z#
				Yaw# = RCE_FloatFromStr#(Mid$(M\MessageData$, 13, 4))
				PvPEnabled = RCE_IntFromStr(Mid$(M\MessageData$, 17, 1))
				Grav = RCE_IntFromStr(Mid$(M\MessageData$, 18, 2))
				Gravity# = 0.05 * Float#((Grav - 200) / 100)
				CurrentAreaID = RCE_IntFromStr(Mid$(M\MessageData$, 20, 4))
				NameLen = RCE_IntFromStr(Mid$(M\MessageData$, 25, 1))
				AreaName$ = Mid$(M\MessageData$, 26, NameLen)
				
				;Actor Shadows Cysis145
				If AreaName$ <> OldAreaName$
					For A.ActorInstance = Each ActorInstance
      					FreeShadowCaster% (A\EN)
   					Next
				EndIf
				
				If AreaName$ = OldAreaName$
					For A.ActorInstance = Each ActorInstance
      					FreeShadowCaster% (A\EN)
   					Next
				EndIf

				; Going to new zone or instance
				If OldAreaID <> CurrentAreaID
					; Remove scripted emitters
					For SEm.ScriptedEmitter = Each ScriptedEmitter
						If SEm\AttachedToPlayer = False Then RP_FreeEmitter(SEm\EN, True, False)
					Next

					; Remove all in-flight projectiles
					For ProjI.ProjectileInstance = Each ProjectileInstance
						FreeProjectileInstance(ProjI)
					Next

					; Save radar state
					If OldAreaName$ <> "" Then Save_Radar_Fog(RadarPath$ + Me\Name$ + "-" + OldAreaName$ + ".rdr")

					; Remove old actor instances
					For A.ActorInstance = Each ActorInstance
						If A <> Me Then SafeFreeActorInstance(A)
					Next
				EndIf

				; Remove dropped loot
				For DItem.DroppedItem = Each DroppedItem
					FreeEntity DItem\EN
					Delete DItem
				Next

				; Unload old and load new zone if necessary
				If AreaName$ <> OldAreaName$
					UnloadArea()
					LoadArea(AreaName$, Cam, False, True)
					If TimeH >= SeasonDuskH(GetSeason()) Or TimeH < SeasonDawnH(GetSeason())
						EntityAlpha SkyEN, 0.0 : EntityAlpha StarsEN, 1.0
						SkyChange = 0
					Else
						EntityAlpha SkyEN, 1.0 : EntityAlpha StarsEN, 0.0
						SkyChange = 0
					EndIf
					For L.Light = Each Light
						L\R# = 0.0
						L\G# = 0.0
						L\B# = 0.0
						L\DestR = 0
						L\DestG = 0
						L\DestB = 0
						HideEntity(L\EN)
					Next
					RotateEntity(DefaultLight\EN, DefaultLightPitch#, DefaultLightYaw#, 0)
					;Tree_SetSeason(GetSeason()) [~@~]
					;If GrassEnabled = True
					;	ClumpGrass(False)
					;Else
					;	Tree_HideAll(1)
					;EndIf
					GY_UpdateWindowBackground(WLargeMap, GetTexture(MapTexID, True))
					Load_Radar(Me\Name$ + "-" + AreaName$, Radar\X#, Radar\Y#, Radar\Width#, Radar\Height#, Not Outdoors, "Radar_Border.png", "Radar_Player.png")
					If ShowRadar = False Then Hide_Radar()
				EndIf

				; Update settings
				SetWeather(RCE_IntFromStr(Mid$(M\MessageData$, 24, 1)))
				PlayerTarget = 0
				AttackTarget = False
				PositionEntity(Me\CollisionEN, Me\X#, Y# + 5.0, Me\Z#)
				RotateEntity(Me\CollisionEN, 0.0, Yaw#, 0.0)
				ResetEntity(Me\CollisionEN)
				
				;Shadow [###]
				PositionEntity(Cam, 0.0, Y# + 10.0, 0.0)
				ResetEntity(Cam)
				;UpdateShadows Cam
				
				
				PositionEntity(Cam, Me\X#, Y# + 10.0, Me\Z#)
				ResetEntity(Cam)
				MoveMouse GraphicsWidth() / 2, GraphicsHeight() / 2
				ZonedMS = MilliSecs()

				; If the new zone is different to the old
				If AreaName$ <> OldAreaName$
					WriteLog(MainLog, "Entered zone: " + AreaName$)
				Else
					WriteLog(MainLog, "Reloaded current zone")
				EndIf

				;we are done, let the server know the it can resume paying attention to standard updates for our player actor again
				;RCE_Send(Connection, RN_Host, P_ChangeArea, M\MessageData$, True)
				;resume processing standard updates from the server
				Me\IgnoreUpdate = 0
	
			; Host disconnected
			Case P_KickedPlayer
				RCE_Disconnect()
				RuntimeError(LanguageString$(LS_LostConnection))

		End Select
		Delete M
	Next
	
	
	; Send update packet
	
	newEntityY#=EntityY#(Me\CollisionEN)
	newEntityX#=EntityX#(Me\CollisionEN)
	newEntityZ#=EntityZ#(Me\CollisionEN)
	
	
	
	If oldEntityY# <> newEntityY# Or oldEntityX# <> newEntityX# Or oldEntityZ# <> newEntityZ#
		
		If MilliSecs() - LastNetwork > NetworkMS
			Pa$ = RCE_StrFromFloat$(Me\DestX#) + RCE_StrFromFloat$(Me\DestZ#) + RCE_StrFromFloat$(EntityY#(Me\CollisionEN))
			Pa$ = Pa$ + RCE_StrFromFloat$(EntityX#(Me\CollisionEN)) + RCE_StrFromFloat$(EntityZ#(Me\CollisionEN))
			Pa$ = Pa$ + RCE_StrFromInt$(Me\IsRunning, 1) + RCE_StrFromInt$(Me\WalkingBackward, 1)
			RCE_Send(Connection, PeerToHost, P_StandardUpdate, Pa$)
			LastNetwork = MilliSecs()
			
			
			oldEntityY# = EntityY#(Me\CollisionEN)
			oldEntityX# = EntityX#(Me\CollisionEN)
			oldEntityZ# = EntityZ#(Me\CollisionEN)
			
            
		EndIf
		
	EndIf

End Function