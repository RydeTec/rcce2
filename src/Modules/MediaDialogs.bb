;##############################################################################################################################
; Realm Crafter version 1.10																									
; Copyright (C) 2007 Solstar Games, LLC. All rights reserved																	
; contact@solstargames.com																																																		
;																																																																#
; Programmer: Rob Williams																										
; Program: Realm Crafter Actors module
;																																
;This is a licensed product:
;BY USING THIS SOURCECODE, YOU ARE CONFIRMING YOUR ACCEPTANCE OF THE SOFTWARE AND AGREEING TO BECOME BOUND BY THE TERMS OF 
;THIS AGREEMENT. IF YOU DO NOT AGREE TO BE BOUND BY THESE TERMS, THEN DO NOT USE THE SOFTWARE.
;																		
;Licensee may NOT: 
; (i)   create any derivative works of the Engine, including translations Or localizations, other than Games;
; (ii)  redistribute, encumber, sell, rent, lease, sublicense, Or otherwise transfer rights To the Engine; or
; (iii) remove Or alter any trademark, logo, copyright Or other proprietary notices, legends, symbols Or labels in the Engine.
; (iv)   licensee may Not distribute the source code Or documentation To the engine in any manner, unless recipient also has a 
;       license To the Engine.													
; (v)  use the Software to develop any software or other technology having the same primary function as the Software, 
;       including but not limited to using the Software in any development or test procedure that seeks to develop like 
;       software or other technology, or to determine if such software or other technology performs in a similar manner as the
;       Software																																
;##############################################################################################################################
; Realm Crafter Media Dialogs module by Rob W (rottbott@hotmail.com), May 2005
; First revision by Rob W, February 2005
; Original version by Rob W, August 2004

; Globals
Global WMeshDialog, LMeshFolder, LMeshDialog, BMeshDialogOK, BMeshDialogCancel
Global WTextureDialog, LTextureFolder, LTextureDialog, BTextureDialogOK, BTextureDialogCancel
Global WSoundDialog, LSoundFolder, LSoundDialog, BSoundDialogOK, BSoundDialogCancel, BSoundDialogPlay
Global WMusicDialog, LMusicFolder, LMusicDialog, BMusicDialogOK, BMusicDialogCancel, BMusicDialogPlay

; Constants
Const MeshDialog_All      = 1
Const MeshDialog_Animated = 2
Const MeshDialog_Static   = 3
Const SoundDialog_All     = 1
Const SoundDialog_3D      = 2
Const SoundDialog_Normal  = 3

; Media
Dim MeshNames$(65534)
Dim TextureNames$(65534)
Dim SoundNames$(65534)
Dim MusicNames$(65534)

; Initialise dialogs
Function InitMediaDialogs()

	; Mesh names
	LockMeshes()
		For i = 0 To 65534 : MeshNames$(i) = GetMeshName$(i) : Next
	UnlockMeshes()
	; Texture names
	LockTextures()
		For i = 0 To 65534 : TextureNames$(i) = GetTextureName$(i) : Next
	UnlockTextures()
	; Sound names
	LockSounds()
		For i = 0 To 65534 : SoundNames$(i) = GetSoundName$(i) : Next
	UnlockSounds()
	; Music names
	LockMusic()
		For i = 0 To 65534 : MusicNames$(i) = GetMusicName$(i) : Next
	UnlockMusic()

	; Meshes window
	WMeshDialog = FUI_Window(-1000, -1000, 630, 370, "Choose Mesh", 0, WS_TITLEBAR, 1)
	BMeshDialogOK = FUI_Button(WMeshDialog, 490, 315, 60, 25, "Accept")
	BMeshDialogCancel = FUI_Button(WMeshDialog, 560, 315, 60, 25, "Cancel")
	LMeshFolder = FUI_ListBox(WMeshDialog, 5, 10, 210, 100, 0, 1)
	LMeshDialog = FUI_ListBox(WMeshDialog, 5, 115, 210, 195, 0, 1)

	; Textures window
	WTextureDialog = FUI_Window(-1000, -1000, 630, 370, "Choose Texture", 0, WS_TITLEBAR, 1)
	BTextureDialogOK = FUI_Button(WTextureDialog, 490, 315, 60, 25, "Accept")
	BTextureDialogCancel = FUI_Button(WTextureDialog, 560, 315, 60, 25, "Cancel")
	LTextureFolder = FUI_ListBox(WTextureDialog, 5, 10, 210, 100, 0, 1)
	LTextureDialog = FUI_ListBox(WTextureDialog, 5, 115, 210, 195, 0, 1)

	; Sounds window
	WSoundDialog = FUI_Window(-1000, -1000, 400, 340, "Choose Sound", 0, WS_TITLEBAR, 1)
	BSoundDialogOK = FUI_Button(WSoundDialog, 260, 285, 60, 25, "Accept")
	BSoundDialogCancel = FUI_Button(WSoundDialog, 330, 285, 60, 25, "Cancel")
	LSoundFolder = FUI_ListBox(WSoundDialog, 5, 10, 210, 100, 0, 1)
	LSoundDialog = FUI_ListBox(WSoundDialog, 5, 115, 210, 195, 0, 1)
	BSoundDialogPlay = FUI_Button(WSoundDialog, 230, 20, 90, 20, "Play/stop sound")

	; Music window
	WMusicDialog = FUI_Window(-1000, -1000, 400, 340, "Choose Music", 0, WS_TITLEBAR, 1)
	BMusicDialogOK = FUI_Button(WMusicDialog, 260, 285, 60, 25, "Accept")
	BMusicDialogCancel = FUI_Button(WMusicDialog, 330, 285, 60, 25, "Cancel")
	LMusicFolder = FUI_ListBox(WMusicDialog, 5, 10, 210, 100, 0, 1)
	LMusicDialog = FUI_ListBox(WMusicDialog, 5, 115, 210, 195, 0, 1)
	BMusicDialogPlay = FUI_Button(WMusicDialog, 230, 20, 90, 20, "Play/stop music")

End Function

; Choose a mesh (MeshType can be MeshDialog_All, MeshDialog_Animated or MeshDialog_Static)
Function ChooseMeshDialog(MeshType = MeshDialog_All, InitialFolder$ = "", XPos = -1, YPos = -1)

	; Init window
	W.Window = Object.Window(WMeshDialog)
	Insert W Before First Window
	If XPos > -1
		FUI_SendMessage(WMeshDialog, M_SETPOS, XPos, YPos)
	Else
		FUI_SendMessage(WMeshDialog, M_SETPOS, (GraphicsWidth() / 2) - 315, (GraphicsHeight() / 2) - 185)
	EndIf
	FUI_ModalWindow(WMeshDialog)
	VPreview = FUI_View(WMeshDialog, 220, 10, 400, 300)
	Local Cam = FUI_SendMessage(VPreview, M_GETCAMERA)
	PositionEntity Cam, 0, -20000, 0

	; Init meshes list
	FillMeshesList(LMeshDialog, InitialFolder$, MeshType)
	FillMeshesFolderList(LMeshFolder, InitialFolder$)

	; Init preview
	ID = FUI_SendMessage(FUI_SendMessage(LMeshDialog, M_GETINDEX), M_GETDATA)
	Mesh = GetMesh(ID)
	If Mesh <> 0
		UnloadMesh(ID)
		SizeEntity(Mesh, 50.0, 50.0, 500.0, True)
		PositionEntity Mesh, 0, -20020, 70
	EndIf

	; Event loop
	Result = 0
	While Result = 0

		For E.Event = Each Event
			Select E\EventID
				; Selected folder changed
				Case LMeshFolder
					Name$ = FUI_SendMessage(LMeshFolder, M_GETTEXT)
					InitialFolder$ = FolderChangeHandler$(Name$, InitialFolder$)
					FillMeshesList(LMeshDialog, InitialFolder$, MeshType)
					FillMeshesFolderList(LMeshFolder, InitialFolder$)
					FUI_CreateEvent(LMeshDialog)
				; Selected mesh changed
				Case LMeshDialog
					If Mesh <> 0 Then FreeEntity Mesh
					ID = FUI_SendMessage(FUI_SendMessage(LMeshDialog, M_GETINDEX), M_GETDATA)
					Mesh = GetMesh(ID)
					If Mesh <> 0
						UnloadMesh(ID)
						SizeEntity(Mesh, 50.0, 50.0, 500.0, True)
						PositionEntity Mesh, 0, -20020, 70
					EndIf
				; Window closed
				Case WMeshDialog
					If Lower$(E\EventData$) = "closed" Then Result = -1
				; Cancel hit
				Case BMeshDialogCancel
					Result = -1
				; OK hit
				Case BMeshDialogOK
					Result = FUI_SendMessageI(FUI_SendMessage(LMeshDialog, M_GETINDEX), M_GETDATA) + 1
			End Select
			Delete E
		Next

		If Mesh <> 0 Then TurnEntity Mesh, 0, 0.5, 0
		FUI_Update()
		Flip(0)

	Wend

	; Return result
	If Mesh <> 0 Then FreeEntity Mesh
	FUI_DeleteGadget(VPreview)
	FUI_ModalWindow(WMeshDialog, False)
	FUI_SendMessage(WMeshDialog, M_SETPOS, -1000, -1000)
	Insert W After Last Window
	FlushKeys
	If Result > 0 Then Result = Result - 1
	Return Result

End Function

; Choose a texture
Function ChooseTextureDialog(InitialFolder$ = "", XPos = -1, YPos = -1)

	; Init window
	W.Window = Object.Window(WTextureDialog)
	Insert W Before First Window
	If XPos > -1
		FUI_SendMessage(WTextureDialog, M_SETPOS, XPos, YPos)
	Else
		FUI_SendMessage(WTextureDialog, M_SETPOS, (GraphicsWidth() / 2) - 315, (GraphicsHeight() / 2) - 185)
	EndIf
	FUI_ModalWindow(WTextureDialog)

	; Init textures list
	FillTexturesList(LTextureDialog, InitialFolder$)
	FillTexturesFolderList(LTextureFolder, InitialFolder$)

	; Init preview
	VPreview = FUI_View(WTextureDialog, 220, 10, 400, 300)
	Local Cam = FUI_SendMessage(VPreview, M_GETCAMERA)
	PositionEntity Cam, 0, -20000, 0
	Sprite = CreateSprite()
	PositionEntity Sprite, 0, -20000, 10
	ScaleSprite Sprite, 10, 7.5
	ID = FUI_SendMessage(FUI_SendMessage(LTextureDialog, M_GETINDEX), M_GETDATA)
	Tex = GetTexture(ID)
	If Tex <> 0 Then EntityTexture(Sprite, Tex)
	UnloadTexture(ID)

	; Event loop
	Result = 0
	While Result = 0

		For E.Event = Each Event
			Select E\EventID
				; Selected folder changed
				Case LTextureFolder
					Name$ = FUI_SendMessage(LTextureFolder, M_GETTEXT)
					InitialFolder$ = FolderChangeHandler$(Name$, InitialFolder$)
					FillTexturesList(LTextureDialog, InitialFolder$)
					FillTexturesFolderList(LTextureFolder, InitialFolder$)
					FUI_CreateEvent(LTextureDialog)
				; Selected texture changed
				Case LTextureDialog
					ID = FUI_SendMessage(FUI_SendMessage(LTextureDialog, M_GETINDEX), M_GETDATA)
					Tex = GetTexture(ID)
					If Tex <> 0 Then EntityTexture(Sprite, Tex)
					UnloadTexture(ID)
				; Window closed
				Case WTextureDialog
					If Lower$(E\EventData$) = "closed" Then Result = -1
				; Cancel hit
				Case BTextureDialogCancel
					Result = -1
				; OK hit
				Case BTextureDialogOK
					Result = FUI_SendMessageI(FUI_SendMessage(LTextureDialog, M_GETINDEX), M_GETDATA) + 1
			End Select
			Delete E
		Next

		FUI_Update()
		Flip(0)

	Wend

	; Return result
	FreeEntity Sprite
	FUI_DeleteGadget(VPreview)
	FUI_ModalWindow(WTextureDialog, False)
	FUI_SendMessage(WTextureDialog, M_SETPOS, -1000, -1000)
	Insert W After Last Window
	FlushKeys
	If Result > 0 Then Result = Result - 1
	Return Result

End Function

; Choose a sound (SoundType can be SoundDialog_All, SoundDialog_3D or SoundDialog_Normal)
Function ChooseSoundDialog(SoundType = SoundDialog_All, InitialFolder$ = "", XPos = -1, YPos = -1)

	; Init window
	W.Window = Object.Window(WSoundDialog)
	Insert W Before First Window
	If XPos > -1
		FUI_SendMessage(WSoundDialog, M_SETPOS, XPos, YPos)
	Else
		FUI_SendMessage(WSoundDialog, M_SETPOS, (GraphicsWidth() / 2) - 200, (GraphicsHeight() / 2) - 185)
	EndIf
	FUI_ModalWindow(WSoundDialog)

	; Init sounds list
	FillSoundsList(LSoundDialog, InitialFolder$, SoundType)
	FillSoundsFolderList(LSoundFolder, InitialFolder$)

	; Event loop
	Result = 0
	While Result = 0

		For E.Event = Each Event
			Select E\EventID
				; Selected folder changed
				Case LSoundFolder
					Name$ = FUI_SendMessage(LSoundFolder, M_GETTEXT)
					InitialFolder$ = FolderChangeHandler$(Name$, InitialFolder$)
					FillSoundsList(LSoundDialog, InitialFolder$, SoundType)
					FillSoundsFolderList(LSoundFolder, InitialFolder$)
					FUI_CreateEvent(LSoundDialog)
				; Stop/play
				Case BSoundDialogPlay
					If Chan <> 0
						If ChannelPlaying(Chan) Then StopChannel(Chan)
						Chan = 0
					Else
						Name$ = SoundNames$(FUI_SendMessage(FUI_SendMessage(LSoundDialog, M_GETINDEX), M_GETDATA))
						If Len(Name$) > 1
							Sound = LoadSound(RootDir$ + "Data\Sounds\" + Left$(Name$, Len(Name$) - 1))
							Chan = PlaySound(Sound)
						EndIf
					EndIf
				; Selected sound changed
				Case LSoundDialog
					If ChannelPlaying(Chan) Then StopChannel(Chan)
					Chan = 0
					If Sound <> 0 Then FreeSound(Sound) : Sound = 0
				; Window closed
				Case WSoundDialog
					If Lower$(E\EventData$) = "closed" Then Result = -1
				; Cancel hit
				Case BSoundDialogCancel
					Result = -1
				; OK hit
				Case BSoundDialogOK
					Result = FUI_SendMessageI(FUI_SendMessage(LSoundDialog, M_GETINDEX), M_GETDATA) + 1
			End Select
			Delete E
		Next

		FUI_Update()
		Flip(0)

	Wend

	; Return result
	If ChannelPlaying(Chan) Then StopChannel(Chan)
	If Sound <> 0 Then FreeSound(Sound)
	FUI_ModalWindow(WSoundDialog, False)
	FUI_SendMessage(WSoundDialog, M_SETPOS, -1000, -1000)
	Insert W After Last Window
	FlushKeys
	If Result > 0 Then Result = Result - 1
	Return Result

End Function

; Choose music
Function ChooseMusicDialog(InitialFolder$ = "", XPos = -1, YPos = -1)

	; Init window
	W.Window = Object.Window(WMusicDialog)
	Insert W Before First Window
	If XPos > -1
		FUI_SendMessage(WMusicDialog, M_SETPOS, XPos, YPos)
	Else
		FUI_SendMessage(WMusicDialog, M_SETPOS, (GraphicsWidth() / 2) - 200, (GraphicsHeight() / 2) - 185)
	EndIf
	FUI_ModalWindow(WMusicDialog)

	; Init music list
	FillMusicList(LMusicDialog, InitialFolder$)
	FillMusicFolderList(LMusicFolder, InitialFolder$)

	; Event loop
	Result = 0
	While Result = 0

		For E.Event = Each Event
			Select E\EventID
				; Selected folder changed
				Case LMusicFolder
					Name$ = FUI_SendMessage(LMusicFolder, M_GETTEXT)
					InitialFolder$ = FolderChangeHandler$(Name$, InitialFolder$)
					FillMusicList(LMusicDialog, InitialFolder$)
					FillMusicFolderList(LMusicFolder, InitialFolder$)
					FUI_CreateEvent(LMusicDialog)
				; Stop/play
				Case BMusicDialogPlay
					If Chan <> 0
						If ChannelPlaying(Chan) Then StopChannel(Chan)
						Chan = 0
					Else
						Name$ = MusicNames$(FUI_SendMessage(FUI_SendMessage(LMusicDialog, M_GETINDEX), M_GETDATA))
						If Len(Name$) > 0
							Chan = PlayMusic(RootDir$ + "Data\Music\" + Name$)
						EndIf
					EndIf
				; Selected music changed
				Case LMusicDialog
					If ChannelPlaying(Chan) Then StopChannel(Chan)
					Chan = 0
				; Window closed
				Case WMusicDialog
					If Lower$(E\EventData$) = "closed" Then Result = -1
				; Cancel hit
				Case BMusicDialogCancel
					Result = -1
				; OK hit
				Case BMusicDialogOK
					Result = FUI_SendMessageI(FUI_SendMessage(LMusicDialog, M_GETINDEX), M_GETDATA) + 1
			End Select
			Delete E
		Next

		FUI_Update()
		Flip(0)

	Wend

	; Return result
	If ChannelPlaying(Chan) Then StopChannel(Chan)
	FUI_ModalWindow(WMusicDialog, False)
	FUI_SendMessage(WMusicDialog, M_SETPOS, -1000, -1000)
	Insert W After Last Window
	FlushKeys
	If Result > 0 Then Result = Result - 1
	Return Result

End Function

; Selects the new folder name when changing folder
Function FolderChangeHandler$(Name$, InitialFolder$)

	If Name$ = "(Previous folder)"
		For j = Len(InitialFolder$) To 1 Step -1
			If Mid$(InitialFolder$, j, 1) = "\" Or Mid$(InitialFolder$, j, 1) = "/" Then Return Left$(InitialFolder$, j - 1)
		Next
		Return ""
	Else
		If InitialFolder$ <> ""
			Return InitialFolder$ + "\" + Name$
		Else
			Return Name$
		EndIf
	EndIf

End Function

; Fills a FUI listbox with all the meshes from a certain folder
Function FillMeshesList(List, Folder$, MeshType)

	FUI_SendMessage(List, M_RESET)
	For i = 0 To 65534
		; Valid mesh
		If Len(MeshNames$(i)) > 1
			IsAnim = Asc(Right$(MeshNames$(i), 1))
			If (IsAnim = True And MeshType = MeshDialog_Animated) Or (IsAnim = False And MeshType = MeshDialog_Static) Or MeshType = MeshDialog_All
				; Get mesh folder
				Valid = False
				If Folder$ <> ""
					For j = Len(MeshNames$(i)) - 1 To 1 Step -1
						If Mid$(MeshNames$(i), j, 1) = "\" Or Mid$(MeshNames$(i), j, 1) = "/"
							Name$ = Mid$(MeshNames$(i), j + 1)
							Path$ = Left$(MeshNames$(i), j - 1)
							If Upper$(Right$(Path$, Len(Folder$))) = Upper$(Folder$) Then Valid = True
							Exit
						EndIf
					Next
				Else
					If Instr(MeshNames$(i), "/") = 0 And Instr(MeshNames$(i), "\") = 0
						Valid = True
						Name$ = MeshNames$(i)
					EndIf
				EndIf

				; If it's in the selected folder, add it to the list
				If Valid = True
					Name$ = Left$(Name$, Len(Name$) - 1)
					If MeshType = MeshDialog_All And IsAnim = True Then Name$ = Name$ + " (Animated)"
					Item = FUI_ListBoxItem(List, Name$, 0, True) : FUI_SendMessage(Item, M_SETDATA, i)
				EndIf
			EndIf
		EndIf
	Next
	FUI_SendMessage(List, M_SETINDEX, 1)

End Function

; Fills a FUI listbox with all the subfolders from a certain meshes folder
Function FillMeshesFolderList(List, Folder$)

	FUI_SendMessage(List, M_RESET)
	If Folder$ <> ""
		FUI_ListBoxItem(List, "(Previous folder)", ICON_OPEN)
		Entries = 1
	Else
		Entries = 0
	EndIf
	For i = 0 To 65534
		If Len(MeshNames$(i)) > 1
			Name$ = Left$(MeshNames$(i), Len(MeshNames$(i)) - 1)
			If Upper$(Left$(Name$, Len(Folder$))) = Upper$(Folder$)
				If Folder$ <> "" Then Name$ = Mid$(Name$, Len(Folder$) + 2)
				PathEnd1 = Instr(Name$, "\")
				PathEnd2 = Instr(Name$, "/")
				If PathEnd2 > 0 And PathEnd2 < PathEnd1 Then PathEnd = PathEnd2 Else PathEnd = PathEnd1
				If PathEnd > 0
					Name$ = Left$(Name$, PathEnd - 1)
					Valid = True
					For j = 1 To Entries
						ExistingName$ = FUI_SendMessage(List, M_GETTEXT, j)
						If Upper$(ExistingName$) = Upper$(Name$) Then Valid = False : Exit
					Next
					If Valid = True
						FUI_ListBoxItem(List, Name$, ICON_OPEN, True)
						Entries = Entries + 1
					EndIf
				EndIf
			EndIf
		EndIf
	Next

End Function

; Fills a FUI listbox with all the textures from a certain folder
Function FillTexturesList(List, Folder$)

	FUI_SendMessage(List, M_RESET)
	For i = 0 To 65534
		; Valid texture
		If Len(TextureNames$(i)) > 1
			; Get texture folder
			Valid = False
			If Folder$ <> ""
				For j = Len(TextureNames$(i)) - 1 To 1 Step -1
					If Mid$(TextureNames$(i), j, 1) = "\" Or Mid$(TextureNames$(i), j, 1) = "/"
						Name$ = Mid$(TextureNames$(i), j + 1)
						Path$ = Left$(TextureNames$(i), j - 1)
						If Upper$(Right$(Path$, Len(Folder$))) = Upper$(Folder$) Then Valid = True
						Exit
					EndIf
				Next
			Else
				If Instr(TextureNames$(i), "/") = 0 And Instr(TextureNames$(i), "\") = 0
					Valid = True
					Name$ = TextureNames$(i)
				EndIf
			EndIf

			; If it's in the selected folder, add it to the list
			If Valid = True
				Flags = Asc(Right$(Name$, 1))
				Name$ = Left$(Name$, Len(Name$) - 1)
				Name$ = Name$ + " (" + Str$(Flags) + ")"
				Item = FUI_ListBoxItem(List, Name$, 0, True) : FUI_SendMessage(Item, M_SETDATA, i)
			EndIf
		EndIf
	Next
	FUI_SendMessage(List, M_SETINDEX, 1)

End Function

; Fills a FUI listbox with all the subfolders from a certain textures folder
Function FillTexturesFolderList(List, Folder$)

	FUI_SendMessage(List, M_RESET)
	If Folder$ <> ""
		FUI_ListBoxItem(List, "(Previous folder)", ICON_OPEN)
		Entries = 1
	Else
		Entries = 0
	EndIf
	For i = 0 To 65534
		If Len(TextureNames$(i)) > 1
			Name$ = Left$(TextureNames$(i), Len(TextureNames$(i)) - 1)
			If Upper$(Left$(Name$, Len(Folder$))) = Upper$(Folder$)
				If Folder$ <> "" Then Name$ = Mid$(Name$, Len(Folder$) + 2)
				PathEnd1 = Instr(Name$, "\")
				PathEnd2 = Instr(Name$, "/")
				If PathEnd2 > 0 And PathEnd2 < PathEnd1 Then PathEnd = PathEnd2 Else PathEnd = PathEnd1
				If PathEnd > 0
					Name$ = Left$(Name$, PathEnd - 1)
					Valid = True
					For j = 1 To Entries
						ExistingName$ = FUI_SendMessage(List, M_GETTEXT, j)
						If Upper$(ExistingName$) = Upper$(Name$) Then Valid = False : Exit
					Next
					If Valid = True
						FUI_ListBoxItem(List, Name$, ICON_OPEN, True)
						Entries = Entries + 1
					EndIf
				EndIf
			EndIf
		EndIf
	Next

End Function

; Fills a FUI listbox with all the sounds from a certain folder
Function FillSoundsList(List, Folder$, SoundType)

	FUI_SendMessage(List, M_RESET)
	For i = 0 To 65534
		; Valid sound
		If Len(SoundNames$(i)) > 1
			Is3D = Asc(Right$(SoundNames$(i), 1))
			If (Is3D = True And SoundType = SoundDialog_3D) Or (Is3D = False And SoundType = SoundDialog_Normal) Or SoundType = SoundDialog_All
				; Get sound folder
				Valid = False
				If Folder$ <> ""
					For j = Len(SoundNames$(i)) - 1 To 1 Step -1
						If Mid$(SoundNames$(i), j, 1) = "\" Or Mid$(SoundNames$(i), j, 1) = "/"
							Name$ = Mid$(SoundNames$(i), j + 1)
							Path$ = Left$(SoundNames$(i), j - 1)
							If Upper$(Right$(Path$, Len(Folder$))) = Upper$(Folder$) Then Valid = True
							Exit
						EndIf
					Next
				Else
					If Instr(SoundNames$(i), "/") = 0 And Instr(SoundNames$(i), "\") = 0
						Valid = True
						Name$ = SoundNames$(i)
					EndIf
				EndIf

				; If it's in the selected folder, add it to the list
				If Valid = True
					Name$ = Left$(Name$, Len(Name$) - 1)
					If SoundType = SoundDialog_All And Is3D = True Then Name$ = Name$ + " (3D)"
					Item = FUI_ListBoxItem(List, Name$, 0, True) : FUI_SendMessage(Item, M_SETDATA, i)
				EndIf
			EndIf
		EndIf
	Next
	FUI_SendMessage(List, M_SETINDEX, 1)

End Function

; Fills a FUI listbox with all the subfolders from a certain sounds folder
Function FillSoundsFolderList(List, Folder$)

	FUI_SendMessage(List, M_RESET)
	If Folder$ <> ""
		FUI_ListBoxItem(List, "(Previous folder)", ICON_OPEN)
		Entries = 1
	Else
		Entries = 0
	EndIf
	For i = 0 To 65534
		If Len(SoundNames$(i)) > 1
			Name$ = Left$(SoundNames$(i), Len(SoundNames$(i)) - 1)
			If Upper$(Left$(Name$, Len(Folder$))) = Upper$(Folder$)
				If Folder$ <> "" Then Name$ = Mid$(Name$, Len(Folder$) + 2)
				PathEnd1 = Instr(Name$, "\")
				PathEnd2 = Instr(Name$, "/")
				If PathEnd2 > 0 And PathEnd2 < PathEnd1 Then PathEnd = PathEnd2 Else PathEnd = PathEnd1
				If PathEnd > 0
					Name$ = Left$(Name$, PathEnd - 1)
					Valid = True
					For j = 1 To Entries
						ExistingName$ = FUI_SendMessage(List, M_GETTEXT, j)
						If Upper$(ExistingName$) = Upper$(Name$) Then Valid = False : Exit
					Next
					If Valid = True
						FUI_ListBoxItem(List, Name$, ICON_OPEN, True)
						Entries = Entries + 1
					EndIf
				EndIf
			EndIf
		EndIf
	Next

End Function


; Fills a FUI listbox with all the music from a certain folder
Function FillMusicList(List, Folder$)

	FUI_SendMessage(List, M_RESET)
	For i = 0 To 65534
		; Valid music
		If Len(MusicNames$(i)) > 1
			; Get music folder
			Valid = False
			If Folder$ <> ""
				For j = Len(MusicNames$(i)) - 1 To 1 Step -1
					If Mid$(MusicNames$(i), j, 1) = "\" Or Mid$(MusicNames$(i), j, 1) = "/"
						Name$ = Mid$(MusicNames$(i), j + 1)
						Path$ = Left$(MusicNames$(i), j - 1)
						If Upper$(Right$(Path$, Len(Folder$))) = Upper$(Folder$) Then Valid = True
						Exit
					EndIf
				Next
			Else
				If Instr(MusicNames$(i), "/") = 0 And Instr(MusicNames$(i), "\") = 0
					Valid = True
					Name$ = MusicNames$(i)
				EndIf
			EndIf

			; If it's in the selected folder, add it to the list
			If Valid = True
				Item = FUI_ListBoxItem(List, Name$, 0, True) : FUI_SendMessage(Item, M_SETDATA, i)
			EndIf
		EndIf
	Next
	FUI_SendMessage(List, M_SETINDEX, 1)

End Function

; Fills a FUI listbox with all the subfolders from a certain music folder
Function FillMusicFolderList(List, Folder$)

	FUI_SendMessage(List, M_RESET)
	If Folder$ <> ""
		FUI_ListBoxItem(List, "(Previous folder)", ICON_OPEN)
		Entries = 1
	Else
		Entries = 0
	EndIf
	For i = 0 To 65534
		If Len(MusicNames$(i)) > 1
			Name$ = Left$(MusicNames$(i), Len(MusicNames$(i)) - 1)
			If Upper$(Left$(Name$, Len(Folder$))) = Upper$(Folder$)
				If Folder$ <> "" Then Name$ = Mid$(Name$, Len(Folder$) + 2)
				PathEnd1 = Instr(Name$, "\")
				PathEnd2 = Instr(Name$, "/")
				If PathEnd2 > 0 And PathEnd2 < PathEnd1 Then PathEnd = PathEnd2 Else PathEnd = PathEnd1
				If PathEnd > 0
					Name$ = Left$(Name$, PathEnd - 1)
					Valid = True
					For j = 1 To Entries
						ExistingName$ = FUI_SendMessage(List, M_GETTEXT, j)
						If Upper$(ExistingName$) = Upper$(Name$) Then Valid = False : Exit
					Next
					If Valid = True
						FUI_ListBoxItem(List, Name$, ICON_OPEN, True)
						Entries = Entries + 1
					EndIf
				EndIf
			EndIf
		EndIf
	Next

End Function