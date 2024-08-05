Strict

Include "Modules\IO\Filesystem.bb"
Include "Modules\Project Manager\Variables.bb"

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
    UpdateGame$ = ReadLine$(F)
    UpdateMusic = ReadLine$(F)
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

Function GenerateFullInstall()

	FUI_CustomMessageBox("Building full client may take some time. Please be patient.", "Warning", MB_OK)
	; Clear \Game folder
	Filesystem::DelTree(Null,"Game")
	; Create required folders
	CreateDir("Game")
	CreateDir("Game\bin")
	CreateDir("Game\Data")
	CreateDir("Game\Data\Logs")
	For i = 0 To 49
		If Len(UpdatesList$(i)) = 0 Then Exit
		CreateDir("Game\" + UpdatesList$(i))
	Next
	; Copy required files to \Game folder
	Filesystem::SafeCopyFile(Null, "bin\Client.exe", "Game\bin\" + GameName$ + ".exe")
	Filesystem::SafeCopyFile(Null, "bin\RCEnet.dll", "Game\bin\RCEnet.dll")
	Filesystem::SafeCopyFile(Null, "Data\Game Data\Language.txt", "Game\Data\Game Data\Language.txt")
	Filesystem::SafeCopyFile(Null, "bin\libbz2w.dll", "Game\bin\libbz2w.dll")
	Filesystem::SafeCopyFile(Null, "bin\blitzsys.dll", "Game\bin\blitzsys.dll")
	Filesystem::SafeCopyFile(Null, "bin\rc64.dll", "Game\bin\rc64.dll")
	Filesystem::SafeCopyFile(Null, "bin\rc63.dll", "Game\bin\rc63.dll")
	Filesystem::SafeCopyFile(Null, "bin\OpenAL32.dll", "Game\bin\OpenAL32.dll")
	Filesystem::SafeCopyFile(Null, "bin\QuickCrypt.dll", "Game\bin\QuickCrypt.dll")
	If FileType("dx7test.dll") = 1 Then CopyFile("dx7test.dll", "Game\dx7test.dll")
	Filesystem::SafeCopyFile(Null, "Data\Last Username.dat", "Game\Data\Last Username.dat")
	Filesystem::SafeCopyFile(Null, "Data\Options.dat", "Game\Data\Options.dat")
	Filesystem::SafeCopyFile(Null, "Data\Controls.dat", "Game\Data\Controls.dat")
	Filesystem::SafeCopyFile(Null, "Data\Patch.exe", "Game\Data\Patch.exe")
	For i = 0 To 49
		If Len(UpdatesList$(i)) = 0 Then Exit
		Filesystem::CopyTree(Null, UpdatesList$(i), "Game\" + UpdatesList$(i))
	Next
	; Change to non development version
	F = WriteFile("Game\Data\Game Data\Misc.dat")
		WriteLine(F, GameName$)
		WriteLine(F, "Normal")
		WriteLine(F, "1")
	CloseFile(F)
	; Complete
	FUI_CustomMessageBox("Complete! Required files are in the \Game folder.", "Build Client", MB_OK)

End Function

Function GenerateServer()
	Result = FUI_CustomMessageBox("Include dynamic data (e.g. accounts)?", "Build Server", MB_YESNO)
	; Clear \Server folder
	Filesystem::DelTree(Null, "Server")
	; Create required folders
	CreateDir("Server")
	CreateDir("Server\bin")
	CreateDir("Server\Data")
	CreateDir("Server\Data\Logs")
	CreateDir("Server\Data\Server Data")
	CreateDir("Server\Data\Server Data\Areas")
	CreateDir("Server\Data\Server Data\Script Files")
	CreateDir("Server\Data\Server Data\Scripts")
	Filesystem::SafeCopyFile(Null, "bin\RCEnet.dll", "Server\bin\RCEnet.dll")
	Filesystem::SafeCopyFile(Null, "bin\briskvm.dll", "Server\bin\briskvm.dll")
	; Copy required files to \Server folder
	;SQLResult = FUI_CustomMessageBox("Build a MySQL server?", "Build Server", MB_YESNO)
	;If SQLResult = IDNO
		Filesystem::SafeCopyFile(Null, "bin\Server.exe", "Server\bin\Server.exe")
	;Else
	;	SafeCopyFile("MySQL Server.exe", "Server\MySQL Server.exe")
	;	SafeCopyFile("MySQL Configure.exe", "Server\MySQL Configure.exe")
	;	SafeCopyFile("libmySQL.dll", "Server\libmySQL.dll")
	;	SafeCopyFile("SQLDLL.dll", "Server\SQLDLL.dll")
	;	SafeCopyFile("BlitzSQL.dll", "Server\BlitzSQL.dll")
	;	SafeCopyFile("MySql.Data.dll", "Server\MySql.Data.dll")
	;	SafeCopyFile("rcsql.sql", "Server\rcsql.sql")
	;	SafeCopyFile("rcsql_flat.sql", "Server\rcsql_flat.sql")
	;	SafeCopyFile("mini.exe", "Server\mini.exe")
	;EndIf
	CopyFile("bin\ggTray.dll", "Server\bin\ggTray.dll")
	CopyFile("bin\fmod.dll", "Server\bin\fmod.dll")
	Filesystem::CopyTree(Null, "Data\Server Data", "Server\Data\Server Data")
	; If it's only an update, delete accounts etc.
	If Result = IDNO
		DeleteFile("Server\Data\Server Data\Accounts.dat")
		DeleteFile("Server\Data\Server Data\Dropped Items.dat")
		DeleteFile("Server\Data\Server Data\Superglobals.dat")
		Filesystem::DelTree(Null, "Server\Data\Server Data\Areas\Ownerships")
		CreateDir("Server\Data\Server Data\Areas\Ownerships")
	EndIf
	; Complete
	FUI_CustomMessageBox("Complete! Required files are in the \Server folder.", "Build Server", MB_OK)
End Function