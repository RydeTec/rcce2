Strict

Function loadRecentProjects()
	For recentFile.RecentFile = Each RecentFile
		Delete recentFile
	Next

    RECENT_COUNT = 0

	RF.BBStream = ReadFile(RootDir$ + "res\Recent.dat")
	if RF <> Null
		For i = 0 to 10
            If Eof(RF) Then Exit
			Local recentDir$ = ReadLine$(RF)
			FUI_RecentFile(recentDir$)
		Next
		CloseFile(RF)
	EndIf
End Function

Function setProject(dir$)
    if FileType(dir$ + "Data") <> 2
        FUI_CustomMessageBox("The selected directory does not contain a valid project.", "Error", MB_OK)
        Return
    EndIf

    Splash.BBImage = LoadImage(RootDir$ + "res\Logo_Splash.bmp")
	ResizeImage Splash, GUE_width, GUE_height
	Color 255, 255, 255 : DrawImage(Splash, 0, 0) : Flip()

	GameDir$ = dir$
	ChangeDir GameDir$

    ; Misc options
	F.BBStream = ReadFile("Data\Game Data\Misc.dat")
	If F = Null Then RuntimeError("Could not open Data\Game Data\Misc.dat!")

	GameName$ = ReadLine$(F)
	CloseFile(F)

    ;Splash Screen
	Text (ImageWidth(Splash)/2), 200, "Loading Project: " + GameName$, True : Flip()
	FreeImage(Splash)

	loadRecentProjects()

	For recentFile.RecentFile = Each RecentFile
		If recentFile\filename$ = GameDir$
			Delete recentFile
			Exit
		EndIf
	Next

	F.BBStream = WriteFile(RootDir$ + "res\Recent.dat")
    If F = Null Then RuntimeError("Could not open res\Recent.dat!")
    WriteLine F, GameDir$
    For recentFile.RecentFile = Each RecentFile
        WriteLine F, recentFile\filename$
    Next
    CloseFile(F)

    local order = 0
    For recentFile.RecentFile = Each RecentFile
        local count = 0
        For menuItem.MenuItem = Each MenuItem
            If Handle( menuItem\Parent ) = M_ProjectsRecent
                if count = order
                    FUI_SendMessage(Handle(menuItem), M_SETTEXT, recentFile\filename$)
                    FUI_BuildMenuItem(menuItem)
                EndIf

                count = count + 1

                if count > order
                    Exit
                EndIf
            EndIf
        Next

        if count = order
            FUI_MenuItem(M_ProjectsRecent, recentFile\filename$)
        EndIf

        order = order + 1
    Next

	FUI_SendMessage(ProName, M_SETCAPTION, GameName$)
    FUI_SendMessage(GDIR, M_SETTEXT, GameDir$)
End Function

Function dq$(s$)
    Return Chr(34) + s$ + Chr(34)
End Function