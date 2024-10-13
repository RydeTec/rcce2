Strict

Include "Modules\IO\Filesystem.bb"
Include "Modules\Project Manager\Variables.bb"

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
	Filesystem::SafeCopyFile(Null, RootDir$ + "bin\Client.exe", "Game\" + GameName$ + ".exe")
	Filesystem::SafeCopyFile(Null, RootDir$ + "bin\RCEnet.dll", "Game\bin\RCEnet.dll")
	Filesystem::SafeCopyFile(Null, "Data\Game Data\Language.txt", "Game\Data\Game Data\Language.txt")
	Filesystem::SafeCopyFile(Null, RootDir$ + "bin\libbz2w.dll", "Game\bin\libbz2w.dll")
	Filesystem::SafeCopyFile(Null, RootDir$ + "bin\blitzsys.dll", "Game\bin\blitzsys.dll")
	Filesystem::SafeCopyFile(Null, RootDir$ + "bin\rc64.dll", "Game\bin\rc64.dll")
	Filesystem::SafeCopyFile(Null, RootDir$ + "bin\rc63.dll", "Game\bin\rc63.dll")
	Filesystem::SafeCopyFile(Null, RootDir$ + "bin\OpenAL32.dll", "Game\bin\OpenAL32.dll")
	Filesystem::SafeCopyFile(Null, RootDir$ + "bin\QuickCrypt.dll", "Game\bin\QuickCrypt.dll")
	Filesystem::SafeCopyFile(Null, RootDir$ + "bin\FastExt.dll", "Game\bin\FastExt.dll")
	If FileType(RootDir$ + "bin\dx7test.dll") = 1 Then CopyFile(RootDir$ + "bin\dx7test.dll", "Game\bin\dx7test.dll")
	Filesystem::SafeCopyFile(Null, "Data\Last Username.dat", "Game\Data\Last Username.dat")
	Filesystem::SafeCopyFile(Null, "Data\Options.dat", "Game\Data\Options.dat")
	Filesystem::SafeCopyFile(Null, "Data\Controls.dat", "Game\Data\Controls.dat")
	Filesystem::SafeCopyFile(Null, "Data\Patch.exe", "Game\Data\Patch.exe")
	For i = 0 To 49
		If Len(UpdatesList$(i)) = 0 Then Exit
		Filesystem::CopyTree(Null, UpdatesList$(i), "Game\" + UpdatesList$(i))
	Next
	; Change to non development version
	local F.BBStream = WriteFile("Game\Data\Game Data\Misc.dat")
		WriteLine(F, GameName$)
		WriteLine(F, "Normal")
		WriteLine(F, "1")
	CloseFile(F)
	; Complete
	FUI_CustomMessageBox("Complete! Required files are in the \Game folder.", "Build Client", MB_OK)

End Function

Function GenerateServer()
	local Result = FUI_CustomMessageBox("Include dynamic data (e.g. accounts)?", "Build Server", MB_YESNO)
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
	Filesystem::SafeCopyFile(Null, RootDir$ + "bin\RCEnet.dll", "Server\bin\RCEnet.dll")
	Filesystem::SafeCopyFile(Null, RootDir$ + "bin\briskvm.dll", "Server\bin\briskvm.dll")
	; Copy required files to \Server folder
	Filesystem::SafeCopyFile(Null, RootDir$ + "bin\Server.exe", "Server\Server.exe")
	CopyFile(RootDir$ + "bin\ggTray.dll", "Server\bin\ggTray.dll")
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