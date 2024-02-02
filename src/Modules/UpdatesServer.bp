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
; Realm Crafter Updates Server module by Rob W (rottbott@hotmail.com), August 2004

Type UpdatesWindow
	Field Window
	Field LockPanel
	Field LockButton
	Field LockLabel
End Type

Type UpdateFile
	Field Name$, Checksum
End Type

Global Updates.UpdatesWindow

; Loads the files list
Function LoadUpdateFiles()

	F = ReadFile("Data\Server Data\Files.dat")
	If F = 0 Then Return 0

		Files = 0
		While Eof(F) = False
			Files = Files + 1
			U.UpdateFile = New UpdateFile
			U\Name$ = ReadString$(F)
			U\Checksum = ReadInt(F)
		Wend

	CloseFile(F)
	Return Files

End Function

; Creates the Updates window
Function CreateUpdatesWindow.UpdatesWindow()

	U.UpdatesWindow = New UpdatesWindow
	U\Window = CreateWindow("Updates", 530, 10, 300, 450, Desktop(), 1)

	U\LockButton = CreateButton("Unlock Updates Server", 10, ClientHeight(U\Window) - 30, ClientWidth(U\Window) - 20, 25, U\Window)
	U\LockPanel = CreatePanel(0, 0, 50, 50, U\Window)
	CentreGadget(U\LockPanel)
	SetGadgetShape U\LockPanel, GadgetX(U\LockPanel), 300, 50, 50
	SetPanelImage(U\LockPanel, "Data\Server Data\RedLight.bmp")

	L = CreateLabel("Updates Server Status:", 0, 0, 110, 20, U\Window)
	CentreGadget(L)
	SetGadgetShape L, GadgetX(L), 275, 110, 20

	Dat$ = "The updates server is locked. This means that no players can join the game or download updates."
	Dat$ = Dat$ + "Any players currently in the game will be removed and must wait until the server is unlocked "
	Dat$ = Dat$ + "before they can rejoin. This is to allow you to upload a game update without players downloading "
	Dat$ = Dat$ + "at the same time, and receiving old or corrupt files. Make sure you unlock the server as soon as "
	Dat$ = Dat$ + "you have finished uploading any updates."
	U\LockLabel = CreateLabel(Dat$, 10, 10, 280, 200, U\Window, 3)

	Return U

End Function