Dim LoadedTextures(65534)
Dim LoadedMeshes(65534)
Dim LoadedMeshScales#(65534)
Dim LoadedMeshX#(65534)
Dim LoadedMeshY#(65534)
Dim LoadedMeshZ#(65534)
Dim LoadedMeshShaders(65534)
Dim LoadedSounds(65534)
Dim TextureFlags(65534)
Global LockedMeshes = 0, LockedTextures = 0, LockedSounds = 0, LockedMusic = 0
Global DefaultVolume# = 1.0

Type LoadedMediaData
	Field ID, DataAddress, Name$, ExtraData, Shader
	Field Scale#, X#, Y#, Z#
End Type

; Self-contained bounded ReadString$ used by the GetMesh / GetTexture /
; GetSound filename reads below. Module-local so the Tools (RC Architect,
; etc.) can include Media.bb without pulling Logging.bb in for the
; ReadBoundedString$ definition. Same shape: ReadInt prefix, refuse on
; negative or >MaxLen, then byte-by-byte read. No WriteLog dependency.
Function MediaReadFilename$(F, MaxLen)
	If F = 0 Then Return ""
	Local L = ReadInt(F)
	If L < 0 Or L > MaxLen Then Return ""
	If L = 0 Then Return ""
	Local s$ = ""
	Local i
	For i = 1 To L
		If Eof(F) Then Exit
		s$ = s$ + Chr$(ReadByte(F))
	Next
	Return s$
End Function

; Locks the meshes database (keeps the file open for faster batched Get...() calls)
Function LockMeshes()

	LockedMeshes = OpenFile("Data\Game Data\Meshes.dat")
	Return LockedMeshes

End Function

; Unlocks the meshes database (closes the file again)
Function UnlockMeshes()

	If LockedMeshes <> 0 Then CloseFile LockedMeshes : LockedMeshes = 0 : Return True

End Function

; Locks the textures database (keeps the file open for faster batched Get...() calls)
Function LockTextures()

	LockedTextures = OpenFile("Data\Game Data\Textures.dat")
	Return LockedTextures

End Function

; Unlocks the textures database (closes the file again)
Function UnlockTextures()

	If LockedTextures <> 0 Then CloseFile LockedTextures : LockedTextures = 0 : Return True

End Function

; Locks the sounds database (keeps the file open for faster batched Get...() calls)
Function LockSounds()

	LockedSounds = OpenFile("Data\Game Data\Sounds.dat")
	Return LockedSounds

End Function

; Unlocks the sounds database (closes the file again)
Function UnlockSounds()

	If LockedSounds <> 0 Then CloseFile LockedSounds : LockedSounds = 0 : Return True

End Function

; Locks the music database (keeps the file open for faster batched Get...() calls)
Function LockMusic()

	LockedMusic = OpenFile("Data\Game Data\Music.dat")
	Return LockedMusic

End Function

; Unlocks the sounds database (closes the file again)
Function UnlockMusic()

	If LockedMusic <> 0 Then CloseFile LockedMusic : LockedMusic = 0 : Return True

End Function

; Creates a new (blank) media database.
;
; Atomic via SafeWriteOpen / SafeWriteCommit. The 65535-int zero-init
; produces a ~256KB file; a crash mid-init previously left a truncated
; index that subsequent OpenFile loads would read past EOF on (Blitz
; zero-fills past EOF silently, so missing slots became "ID 0" — a
; class of "Meshes.dat is corrupted" mystery loads). SafeWriteCommit
; refuses to promote an empty temp and keeps any prior version as .bak.
Function CreateDatabase(Filename$)

	Local TempPath$ = SafeWriteOpen$(Filename$)
	F = WriteFile(TempPath$)
	If F = 0 Then Return False

	For ID = 0 To 65534
		WriteInt F, 0
	Next

	Return SafeWriteCommit%(TempPath$, Filename$, F)

End Function

; Removes a mesh from the database (slow)
Function RemoveMeshFromDatabase(ID)

	UnloadMesh(ID)

	; Open index file
	If LockedMeshes = 0
		F = OpenFile("Data\Game Data\Meshes.dat")
		If F = 0 Then Return False
	Else
		F = LockedMeshes
	EndIf

	; Read in the data address for every ID except this one
	For i = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress > 0 And i <> ID
			L.LoadedMediaData = New LoadedMediaData
			L\ID = i
			L\DataAddress = DataAddress
		EndIf
	Next

	; Read in the actual data for each existing ID
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\DataAddress
		L\ExtraData = ReadByte(F)
		L\Scale# = ReadFloat#(F)
		L\X# = ReadFloat#(F)
		L\Y# = ReadFloat#(F)
		L\Z# = ReadFloat#(F)
		L\Shader = ReadShort(F)
		L\Name$ = MediaReadFilename$(F, 260)
	Next

	; Clear the database
	CloseFile(F)
	Result = CreateDatabase("Data\Game Data\Meshes.dat")
	If Result = False Then LockedMeshes = 0 : Return False

	; Reopen it
	F = OpenFile("Data\Game Data\Meshes.dat")
	If LockedMeshes <> 0 Then LockedMeshes = F

	; Write everything back out again
	FileEnd = 65535 * 4
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\ID * 4
		WriteInt F, FileEnd
		SeekFile F, FileEnd
		WriteByte F, L\ExtraData
		WriteFloat F, L\Scale#
		WriteFloat F, L\X#
		WriteFloat F, L\Y#
		WriteFloat F, L\Z#
		WriteShort F, L\Shader
		WriteString F, L\Name$
		FileEnd = FilePos(F)
	Next
	Delete Each LoadedMediaData

	If LockedMeshes = 0 Then CloseFile(F)
	Return True

End Function

; Removes a texture from the database (slow)
Function RemoveTextureFromDatabase(ID)

	UnloadTexture(ID)

	; Open index file
	If LockedTextures = 0
		F = OpenFile("Data\Game Data\Textures.dat")
		If F = 0 Then Return False
	Else
		F = LockedTextures
	EndIf

	; Read in the data address for every ID except this one
	For i = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress > 0 And i <> ID
			L.LoadedMediaData = New LoadedMediaData
			L\ID = i
			L\DataAddress = DataAddress
		EndIf
	Next

	; Read in the actual data for each existing ID
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\DataAddress
		L\ExtraData = ReadShort(F)
		L\Name$ = MediaReadFilename$(F, 260)
	Next

	; Clear the database
	CloseFile(F)
	Result = CreateDatabase("Data\Game Data\Textures.dat")
	If Result = False Then LockedTextures = 0 : Return False

	; Reopen it
	F = OpenFile("Data\Game Data\Textures.dat")
	If LockedTextures <> 0 Then LockedTextures = F

	; Write everything back out again
	FileEnd = 65535 * 4
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\ID * 4
		WriteInt F, FileEnd
		SeekFile F, FileEnd
		WriteShort F, L\ExtraData
		WriteString F, L\Name$
		FileEnd = FilePos(F)
	Next
	Delete Each LoadedMediaData

	If LockedTextures = 0 Then CloseFile(F)
	Return True

End Function

; Removes a sound from the database (slow)
Function RemoveSoundFromDatabase(ID)

	UnloadSound(ID)

	; Open index file
	If LockedSounds = 0
		F = OpenFile("Data\Game Data\Sounds.dat")
		If F = 0 Then Return False
	Else
		F = LockedSounds
	EndIf

	; Read in the data address for every ID except this one
	For i = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress > 0 And i <> ID
			L.LoadedMediaData = New LoadedMediaData
			L\ID = i
			L\DataAddress = DataAddress
		EndIf
	Next

	; Read in the actual data for each existing ID
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\DataAddress
		L\ExtraData = ReadByte(F)
		L\Name$ = MediaReadFilename$(F, 260)
	Next

	; Clear the database
	CloseFile(F)
	Result = CreateDatabase("Data\Game Data\Sounds.dat")
	If Result = False Then LockedSounds = 0 : Return False

	; Reopen it
	F = OpenFile("Data\Game Data\Sounds.dat")
	If LockedSounds <> 0 Then LockedSounds = F

	; Write everything back out again
	FileEnd = 65535 * 4
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\ID * 4
		WriteInt F, FileEnd
		SeekFile F, FileEnd
		WriteByte F, L\ExtraData
		WriteString F, L\Name$
		FileEnd = FilePos(F)
	Next
	Delete Each LoadedMediaData

	If LockedSounds = 0 Then CloseFile(F)
	Return True

End Function

; Removes music from the database (slow)
Function RemoveMusicFromDatabase(ID)

	; Open index file
	If LockedMusic = 0
		F = OpenFile("Data\Game Data\Music.dat")
		If F = 0 Then Return False
	Else
		F = LockedMusic
	EndIf

	; Read in the data address for every ID except this one
	For i = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress > 0 And i <> ID
			L.LoadedMediaData = New LoadedMediaData
			L\ID = i
			L\DataAddress = DataAddress
		EndIf
	Next

	; Read in the actual data for each existing ID
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\DataAddress
		L\Name$ = MediaReadFilename$(F, 260)
	Next

	; Clear the database
	CloseFile(F)
	Result = CreateDatabase("Data\Game Data\Music.dat")
	If Result = False Then LockedMusic = 0 : Return False

	; Reopen it
	F = OpenFile("Data\Game Data\Music.dat")
	If LockedMusic <> 0 Then LockedMusic = F

	; Write everything back out again
	FileEnd = 65535 * 4
	For L.LoadedMediaData = Each LoadedMediaData
		SeekFile F, L\ID * 4
		WriteInt F, FileEnd
		SeekFile F, FileEnd
		WriteString F, L\Name$
		FileEnd = FilePos(F)
	Next
	Delete Each LoadedMediaData

	If LockedMusic = 0 Then CloseFile(F)
	Return True

End Function

; Adds a new mesh to the database and returns its ID
Function AddMeshToDatabase(Filename$, IsAnim)

	; Open index file
	If LockedMeshes = 0
		F = OpenFile("Data\Game Data\Meshes.dat")
		If F = 0 Then Return -1
	Else
		F = LockedMeshes
	EndIf

	; Check every mesh to make sure this one isn't already there
	SeekFile F, (65535 * 4)
	While Eof(F) = False
		MIsAnim = ReadByte(F)
		ReadFloat#(F)
		ReadFloat#(F)
		ReadFloat#(F)
		ReadFloat#(F)
		ReadShort(F)
		Name$ = MediaReadFilename$(F, 260)
		; If this mesh is already in the file, return error
		If (Upper$(Name$) = Upper$(Filename$)) And (MIsAnim = IsAnim)
			If LockedMeshes = 0 Then CloseFile(F)
			Return -1
		EndIf
	Wend

	; Find the first free ID
	SeekFile F, 0
	For ID = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress = 0
			; Write mesh data to index file
			SeekFile F, ID * 4
			WriteInt F, FileSize("Data\Game Data\Meshes.dat")
			SeekFile F, FileSize("Data\Game Data\Meshes.dat")
			WriteByte F, IsAnim
			WriteFloat F, 1.0
			WriteFloat F, 0.0
			WriteFloat F, 0.0
			WriteFloat F, 0.0
			WriteShort F, 65535
			WriteString F, Filename$
			If LockedMeshes = 0 Then CloseFile(F)
			; Return new ID
			Return ID
		EndIf
	Next

	; No free ID found
	If LockedMeshes = 0 Then CloseFile(F)
	Return -1

End Function

; Adds a new texture to the database and returns its ID
Function AddTextureToDatabase(Filename$, Flags)

	; Open index file
	If LockedTextures = 0
		F = OpenFile("Data\Game Data\Textures.dat")
		If F = 0 Then Return -1
	Else
		F = LockedTextures
	EndIf

	; Check every texture to make sure this one isn't already there
	SeekFile(F, 65535 * 4)
	While Eof(F) = False
		TFlags = ReadShort(F)
		Name$ = MediaReadFilename$(F, 260)
		; If this texture is already in the file, return error
		If (Upper$(Name$) = Upper$(Filename$)) And (TFlags = Flags)
			If LockedTextures = 0 Then CloseFile(F)
			Return -1
		EndIf
	Wend

	; Find the first free ID
	SeekFile F, 0
	For ID = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress = 0
			; Write texture data to index file
			SeekFile(F, ID * 4)
			WriteInt(F, FileSize("Data\Game Data\Textures.dat"))
			SeekFile(F, FileSize("Data\Game Data\Textures.dat"))
			WriteShort(F, Flags)
			WriteString(F, Filename$)
			If LockedTextures = 0 Then CloseFile(F)
			; Return new ID
			Return ID
		EndIf
	Next

	; No free ID found
	If LockedTextures = 0 Then CloseFile(F)
	Return -1

End Function

; Adds a new sound to the database and returns its ID
Function AddSoundToDatabase(Filename$, Is3D)

	; Open index file
	If LockedSounds = 0
		F = OpenFile("Data\Game Data\Sounds.dat")
		If F = 0 Then Return -1
	Else
		F = LockedSounds
	EndIf

	; Check every sound to make sure this one isn't already there
	SeekFile(F, 65535 * 4)
	While Eof(F) = False
		SIs3D = ReadByte(F)
		Name$ = MediaReadFilename$(F, 260)
		; If this sound is already in the file, return error
		If (Upper$(Name$) = Upper$(Filename$)) And (SIs3D = Is3D)
			If LockedSounds = 0 Then CloseFile(F)
			Return -1
		EndIf
	Wend

	; Find the first free ID
	SeekFile F, 0
	For ID = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress = 0
			; Write sound data to index file
			SeekFile F, ID * 4
			WriteInt F, FileSize("Data\Game Data\Sounds.dat")
			SeekFile F, FileSize("Data\Game Data\Sounds.dat")
			WriteByte F, Is3D
			WriteString F, Filename$
			If LockedSounds = 0 Then CloseFile(F)
			; Return new ID
			Return ID
		EndIf
	Next

	; No free ID found
	If LockedSounds = 0 Then CloseFile(F)
	Return -1

End Function

; Adds new music to the database and returns its ID
Function AddMusicToDatabase(Filename$)

	; Open index file
	If LockedMusic = 0
		F = OpenFile("Data\Game Data\Music.dat")
		If F = 0 Then Return -1
	Else
		F = LockedMusic
	EndIf

	; Check all music to make sure this one isn't already there
	SeekFile F, (65535 * 4)
	While Eof(F) = False
		Name$ = MediaReadFilename$(F, 260)
		; If this music is already in the file, return error. Previously
		; the dup branch closed the file but then fell through to the
		; insert path below -- so every duplicate was added a second time.
		If Upper$(Name$) = Upper$(Filename$)
			If LockedMusic = 0 Then CloseFile(F)
			Return -1
		EndIf
	Wend

	; Find the first free ID
	SeekFile F, 0
	For ID = 0 To 65534
		DataAddress = ReadInt(F)
		If DataAddress = 0
			; Write sound data to index file
			SeekFile F, ID * 4
			WriteInt F, FileSize("Data\Game Data\Music.dat")
			SeekFile F, FileSize("Data\Game Data\Music.dat")
			WriteString F, Filename$
			; Return new ID
			If LockedMusic = 0 Then CloseFile(F)
			Return ID
		EndIf
	Next

	; No free ID found
	If LockedMusic = 0 Then CloseFile(F)
	Return -1

End Function

; Gets the name and animation byte for a given mesh
Function GetMeshName$(ID)

	; Mirror GetMesh's bound check. The sibling getter does the same.
	; Negative ID makes SeekFile go to a negative offset (undefined);
	; > 65534 walks past the index table. Sentinel IDs like -1 (used
	; by some Actors paths to mean "no mesh") would otherwise crash
	; the client every frame an actor with that sentinel renders.
	If ID < 0 Or ID > 65534 Then Return ""

	; Open index file
	If LockedMeshes = 0
		F = OpenFile("Data\Game Data\Meshes.dat")
		If F = 0 Then Return ""
	Else
		F = LockedMeshes
	EndIf

	; Find data address in file index
	SeekFile(F, ID * 4)
	DataAddress = ReadInt(F)
	If DataAddress = 0
		If LockedMeshes = 0 Then CloseFile(F)
		Return ""
	EndIf
	; Read in mesh data
	SeekFile(F, DataAddress)
	IsAnim = ReadByte(F)
	ReadFloat#(F)
	ReadFloat#(F)
	ReadFloat#(F)
	ReadFloat#(F)
	ReadShort(F)
	Name$ = MediaReadFilename$(F, 260)

	If LockedMeshes = 0 Then CloseFile(F)

	Return Name$ + Chr$(IsAnim)

End Function

; Gets the name and flags for a given texture
Function GetTextureName$(ID)

	; Mirror GetTexture's bound check (line 844).
	If ID < 0 Or ID > 65534 Then Return ""

	; Open index file
	If LockedTextures = 0
		F = OpenFile("Data\Game Data\Textures.dat")
		If F = 0 Then Return ""
	Else
		F = LockedTextures
	EndIf

	; Find data address in file index
	SeekFile F, ID * 4
	DataAddress = ReadInt(F)
	If DataAddress = 0
		If LockedTextures = 0 Then CloseFile(F)
		Return ""
	EndIf
	; Read in texture data
	SeekFile F, DataAddress
	Flags = ReadShort(F)
	Name$ = MediaReadFilename$(F, 260)

	If LockedTextures = 0 Then CloseFile(F)

	Return Name$ + Chr$(Flags)

End Function

; Gets the name and 3D byte for a given sound
Function GetSoundName$(ID)

	; Mirror GetSound's bound check (line 891).
	If ID < 0 Or ID > 65534 Then Return ""

	; Open index file
	If LockedSounds = 0
		F = OpenFile("Data\Game Data\Sounds.dat")
		If F = 0 Then Return ""
	Else
		F = LockedSounds
	EndIf

	; Find data address in file index
	SeekFile F, ID * 4
	DataAddress = ReadInt(F)
	If DataAddress = 0
		If LockedSounds = 0 Then CloseFile(F)
		Return ""
	EndIf
	; Read in sound data
	SeekFile F, DataAddress
	Is3D = ReadByte(F)
	Name$ = MediaReadFilename$(F, 260)

	If LockedSounds = 0 Then CloseFile(F)

	Return Name$ + Chr$(Is3D)

End Function

; Gets the name of a given piece of music
Function GetMusicName$(ID)

	; Music IDs are also indexed into a 4-byte-per-entry Data/Game
	; Data/Music.dat table. Same bound as GetSound / GetMesh / etc.
	If ID < 0 Or ID > 65534 Then Return ""

	; Open index file
	If LockedMusic = 0
		F = OpenFile("Data\Game Data\Music.dat")
		If F = 0 Then Return ""
	Else
		F = LockedMusic
	EndIf

	; Find data address in file index
	SeekFile F, ID * 4
	DataAddress = ReadInt(F)
	If DataAddress = 0
		If LockedMusic = 0 Then CloseFile(F)
		Return ""
	EndIf
	; Read in sound data
	SeekFile F, DataAddress
	Name$ = MediaReadFilename$(F, 260)

	If LockedMusic = 0 Then CloseFile(F)

	Return Name$

End Function

; Changes the scale for a mesh
Function SetMeshScale(ID, Scale#)

	; LoadedMeshScales# is Dim'd 0..65534. Mirror GetMesh's bound check
	; (line 756) -- editor-supplied IDs from MediaDialogs can land here
	; via the Architect's mesh-config flow; a typo or sentinel would
	; otherwise OOB-write into adjacent globals.
	If ID < 0 Or ID > 65534 Then Return False

	LoadedMeshScales#(ID) = Scale#

	; Open index file
	If LockedMeshes = 0
		F = OpenFile("Data\Game Data\Meshes.dat")
		If F = 0 Then Return False
	Else
		F = LockedMeshes
	EndIf

	; Find data address in file index
	SeekFile F, ID * 4
	DataAddress = ReadInt(F)
	If DataAddress = 0
		If LockedMeshes = 0 Then CloseFile(F)
		Return False
	EndIf
	; Write new scale float
	SeekFile F, DataAddress + 1
	WriteFloat F, Scale#

	If LockedMeshes = 0 Then CloseFile(F)

	Return True

End Function

; Changes the offset for a mesh
Function SetMeshOffset(ID, X#, Y#, Z#)

	; LoadedMeshX#/Y#/Z# are Dim'd 0..65534. Same bound as SetMeshScale.
	If ID < 0 Or ID > 65534 Then Return False

	LoadedMeshX#(ID) = X#
	LoadedMeshY#(ID) = Y#
	LoadedMeshZ#(ID) = Z#

	; Open index file
	If LockedMeshes = 0
		F = OpenFile("Data\Game Data\Meshes.dat")
		If F = 0 Then Return False
	Else
		F = LockedMeshes
	EndIf

	; Find data address in file index
	SeekFile F, ID * 4
	DataAddress = ReadInt(F)
	If DataAddress = 0
		If LockedMeshes = 0 Then CloseFile(F)
		Return False
	EndIf
	; Write new scale float
	SeekFile F, DataAddress + 5
	WriteFloat F, X#
	WriteFloat F, Y#
	WriteFloat F, Z#

	If LockedMeshes = 0 Then CloseFile(F)

	Return True

End Function

; Changes the shader for a mesh
Function SetMeshShader(ID, Shader)

	; LoadedMeshShaders is Dim'd 0..65534. Same bound as SetMeshScale.
	If ID < 0 Or ID > 65534 Then Return False

	LoadedMeshShaders(ID) = Shader

	; Open index file
	If LockedMeshes = 0
		F = OpenFile("Data\Game Data\Meshes.dat")
		If F = 0 Then Return False
	Else
		F = LockedMeshes
	EndIf

	; Find data address in file index
	SeekFile F, ID * 4
	DataAddress = ReadInt(F)
	If DataAddress = 0
		If LockedMeshes = 0 Then CloseFile(F)
		Return False
	EndIf
	; Write new scale float
	SeekFile F, DataAddress + 17
	WriteShort(F, Shader)

	If LockedMeshes = 0 Then CloseFile(F)

	Return True

End Function

; Gets the handle for a given mesh (this will load it if it isn't present)
Function GetMesh(ID, Duplicate = False)

	; Mirror GetSound's ID guard. LoadedMeshes is Dim'd 0..65534; a caller
	; passing -1 (the inconsistent sentinel some Actors3D paths use instead
	; of 65535) or any out-of-range ID would write through a wild pointer.
	If ID < 0 Or ID > 65534 Then Return 0

	; Load from file if this is the first time the mesh has been loaded
	If LoadedMeshes(ID) = 0

		; Read in filename and other data from index file
		If LockedMeshes = 0
			F = OpenFile("Data\Game Data\Meshes.dat")
			If F = 0 Then Return 0
		Else
			F = LockedMeshes
		EndIf

		; Find data address in file index
		SeekFile F, ID * 4
		DataAddress = ReadInt(F)
		If DataAddress = 0
			If LockedMeshes = 0 Then CloseFile(F)
			Return 0
		EndIf
		; Read in mesh data. Bound the filename read so a corrupted
		; Meshes.dat (or one tampered with via the update channel)
		; can't hang the client allocating gigabytes on a wild
		; length prefix. 260 covers a generous max path length.
		SeekFile F, DataAddress
		IsAnim = ReadByte(F)
		LoadedMeshScales#(ID) = ReadFloat#(F)
		LoadedMeshX#(ID) = ReadFloat#(F)
		LoadedMeshY#(ID) = ReadFloat#(F)
		LoadedMeshZ#(ID) = ReadFloat#(F)
		LoadedMeshShaders(ID) = ReadShort(F)
		Name$ = MediaReadFilename$(F, 260)

		If LockedMeshes = 0 Then CloseFile(F)

		; Reject path-traversal in Meshes.dat-stored names. The update
		; channel can rewrite Meshes.dat, so a hostile update payload could
		; plant `..\..\<x>` and force a LoadMesh on an arbitrary file.
		; LoadMesh would mostly return 0 on miss, but the attempt itself is
		; the wrong shape -- bail before we touch the filesystem.
		If Instr(Name$, "..") > 0 Then Return 0

		; Load the mesh
		If IsAnim = True
			LoadedMeshes(ID) = LoadAnimMesh("Data\Meshes\" + Name$)
			If LoadedMeshes(ID) = 0 Then Return(0)
			HideEntity LoadedMeshes(ID)
			ScaleEntity LoadedMeshes(ID), LoadedMeshScales#(ID), LoadedMeshScales#(ID), LoadedMeshScales#(ID)
		Else
			LoadedMeshes(ID) = LoadMesh("Data\Meshes\" + Name$)
			If LoadedMeshes(ID) = 0 Then Return(0)
			HideEntity(LoadedMeshes(ID))
			ScaleEntity(LoadedMeshes(ID), LoadedMeshScales#(ID), LoadedMeshScales#(ID), LoadedMeshScales#(ID))
		EndIf

	EndIf

	; Return a copy of the entity
	If Duplicate = False
		Return(CopyEntity(LoadedMeshes(ID)))
	; Duplicate a mesh - required for actors because annoyingly Blitz textures are mesh, not entity, specific
	Else
		Name$ = GetMeshName$(ID)
		IsAnim = Asc(Right$(Name$, 1))
		Name$ = Left$(Name$, Len(Name$) - 1)
		
		If Lower(Right(Name$, 4)) = ".eb3d" Then
			MessageBox 0, "Cannot load eb3d files!", "Mesh Error", 16
		End If
		
		If IsAnim = True
			EN = LoadAnimMesh("Data\Meshes\" + Name$)
			If EN = 0 Then Return(0)
			ScaleEntity(EN, LoadedMeshScales#(ID), LoadedMeshScales#(ID), LoadedMeshScales#(ID))
		Else
			EN = LoadMesh("Data\Meshes\" + Name$)
			If EN = 0 Then Return(0)
				ScaleEntity(EN, LoadedMeshScales#(ID), LoadedMeshScales#(ID), LoadedMeshScales#(ID))
		EndIf	

		Return(EN)
	EndIf

End Function

; Gets the handle for a given texture (this will load it if it isn't present)
Function GetTexture(ID, Copy = False)

	If ID < 0 Or ID > 65534 Then Return 0

	If LoadedTextures(ID) = 0

		; Read in filename and other data from index file
		If LockedTextures = 0
			F = OpenFile("Data\Game Data\Textures.dat")
			If F = 0 Then Return -1
		Else
			F = LockedTextures
		EndIf

		; Find data address in file index
		SeekFile(F, ID * 4)
		DataAddress = ReadInt(F)
		If DataAddress = 0
			If LockedTextures = 0 Then CloseFile(F)
			Return 0
		EndIf
		; Read in texture data. Bound filename + path-traversal guard
		; mirror GetMesh's defense: an update-channel payload could
		; otherwise plant `..\..\<x>` and force LoadTexture against an
		; arbitrary file. LoadTexture won't execute the contents, but
		; reading from outside Data\Textures\ is the wrong shape.
		SeekFile(F, DataAddress)
		Flags = ReadShort(F)
		Name$ = MediaReadFilename$(F, 260)

		If LockedTextures = 0 Then CloseFile(F)

		If Instr(Name$, "..") > 0 Then Return 0
		LoadedTextures(ID) = LoadTexture("Data\Textures\" + Name$, Flags)
		TextureFlags(ID) = Flags

	EndIf
	If Copy = True
		If LoadedTextures(ID) = 0 Then Return 0
		Return CopyTexture(LoadedTextures(ID), TextureFlags(ID))
	Else
		Return LoadedTextures(ID)
	EndIf

End Function

; Gets the handle for a given sound (this will load it if it isn't present)
Function GetSound(ID)

	If ID < 0 Or ID > 65534 Then Return 0

	If LoadedSounds(ID) = 0

		; Read in filename and other data from index file
		If LockedSounds = 0
			F = OpenFile("Data\Game Data\Sounds.dat")
			If F = 0 Then Return -1
		Else
			F = LockedSounds
		EndIf

		; Find data address in file index
		SeekFile F, ID * 4
		DataAddress = ReadInt(F)
		If DataAddress = 0
			If LockedSounds = 0 Then CloseFile(F)
			Return 0
		EndIf
		; Read in sound data. Bound filename + path-traversal guard
		; mirror GetMesh / GetTexture (see above).
		SeekFile F, DataAddress
		Is3D = ReadByte(F)
		Name$ = MediaReadFilename$(F, 260)

		If LockedSounds = 0 Then CloseFile(F)

		If Instr(Name$, "..") > 0 Then Return 0
		LoadedSounds(ID) = LoadSound("Data\Sounds\" + Name$, Is3D)

	EndIf
	Return LoadedSounds(ID)

End Function

; Unloads a mesh
Function UnloadMesh(ID)
	; Check for correct input
	If ID < 0 Or ID > 65534 Then Return
	If LoadedMeshes(ID) <> 0 Then FreeEntity LoadedMeshes(ID)
	LoadedMeshes(ID) = 0

End Function

; Unloads a texture
Function UnloadTexture(ID)
	; Check for correct input
	If ID < 0 Or ID > 65534 Then Return
	If LoadedTextures(ID) <> 0 Then FreeTexture LoadedTextures(ID)
	LoadedTextures(ID) = 0

End Function

; Unloads a sound
Function UnloadSound(ID)
	; Check for correct input
	If ID < 0 Or ID > 65534 Then Return
	If LoadedSounds(ID) <> 0 Then FreeSound LoadedSounds(ID)
	LoadedSounds(ID) = 0

End Function

; Scales a mesh entity to be a certain size without altering the mesh (works on animated meshes)
Function SizeEntity(EN, Width#, Height#, Depth#, Uniform = False)

	; Find mesh edges
	Result.MeshMinMaxVertices = MeshMinMaxVertices(EN)
	MWidth#  = Result\MaxX# - Result\MinX#
	MHeight# = Result\MaxY# - Result\MinY#
	MDepth#  = Result\MaxZ# - Result\MinZ#
	Delete Result

	; Scale
	If Uniform = False
		ScaleEntity(EN, Width# / MWidth#, Height# / MHeight#, Depth# / MDepth#)
	Else
		XScale# = Width# / MWidth#
		YScale# = Height# / MHeight#
		ZScale# = Depth# / MDepth#

		If YScale# < XScale# Then XScale# = YScale#
		If ZScale# < XScale# Then XScale# = ZScale#

		ScaleEntity(EN, XScale#, XScale#, XScale#)
	EndIf

End Function

; Copies a texture in memory
Function CopyTexture(Tex, Flags)

	NewTex = CreateTexture(TextureWidth(Tex), TextureHeight(Tex), Flags)

	OldBuffer = GraphicsBuffer()
	SetBuffer(TextureBuffer(NewTex))
		LockBuffer()
		LockBuffer(TextureBuffer(Tex))
			For x = 0 To TextureWidth(Tex) - 1
			For y = 0 To TextureHeight(Tex) - 1
				CopyPixelFast(x, y, TextureBuffer(Tex), x, y)
			Next
			Next
		UnlockBuffer(TextureBuffer(Tex))
		UnlockBuffer()
	SetBuffer(OldBuffer)

	Return(NewTex)

End Function

; Retrieves the min/max vertex positions of a mesh or heirarchy of meshes (RECURSIVE)
Type MeshMinMaxVertices
	Field MinX#, MaxX#
	Field MinY#, MaxY#
	Field MinZ#, MaxZ#
End Type
Function MeshMinMaxVertices.MeshMinMaxVertices(EN)

	Result.MeshMinMaxVertices = New MeshMinMaxVertices
	If Upper$(EntityClass$(EN)) = "MESH"
		For i = 1 To CountSurfaces(EN)
			Surf = GetSurface(EN, i)
			For j = 0 To CountVertices(Surf) - 1
				X# = VertexX#(Surf, j)
				Y# = VertexY#(Surf, j)
				Z# = VertexZ#(Surf, j)
				If X# < Result\MinX# Then Result\MinX# = X# ElseIf X# > Result\MaxX# Then Result\MaxX# = X#
				If Y# < Result\MinY# Then Result\MinY# = Y# ElseIf Y# > Result\MaxY# Then Result\MaxY# = Y#
				If Z# < Result\MinZ# Then Result\MinZ# = Z# ElseIf Z# > Result\MaxZ# Then Result\MaxZ# = Z#
			Next
		Next
	EndIf
	For i = 1 To CountChildren(EN)
		ChildResult.MeshMinMaxVertices = MeshMinMaxVertices(GetChild(EN, i))
		If ChildResult\MinX# < Result\MinX# Then Result\MinX# = ChildResult\MinX#
		If ChildResult\MinY# < Result\MinY# Then Result\MinY# = ChildResult\MinY#
		If ChildResult\MinZ# < Result\MinZ# Then Result\MinZ# = ChildResult\MinZ#
		If ChildResult\MaxX# > Result\MaxX# Then Result\MaxX# = ChildResult\MaxX#
		If ChildResult\MaxY# > Result\MaxY# Then Result\MaxY# = ChildResult\MaxY#
		If ChildResult\MaxZ# > Result\MaxZ# Then Result\MaxZ# = ChildResult\MaxZ#
		Delete ChildResult
	Next
	Return(Result)

End Function
; Same thing but for transformed meshes
Function MeshMinMaxVerticesTransformed.MeshMinMaxVertices(EN, Pitch#, Yaw#, Roll#, ScaleX#, ScaleY#, ScaleZ#)

	Result.MeshMinMaxVertices = New MeshMinMaxVertices
	If Upper$(EntityClass$(EN)) = "MESH"
		For i = 1 To CountSurfaces(EN)
			Surf = GetSurface(EN, i)
			For j = 0 To CountVertices(Surf) - 1
				X# = VertexX#(Surf, j) * ScaleX#
				Y# = VertexY#(Surf, j) * ScaleY#
				Z# = VertexZ#(Surf, j) * ScaleZ#
				P = CreatePivot()
				RotateEntity(P, Pitch#, Yaw#, Roll#)
				TFormPoint(X#, Y#, Z#, P, 0)
				X# = TFormedX#()
				Y# = TFormedY#()
				Z# = TFormedZ#()
				FreeEntity(P)
				If X# < Result\MinX# Then Result\MinX# = X# ElseIf X# > Result\MaxX# Then Result\MaxX# = X#
				If Y# < Result\MinY# Then Result\MinY# = Y# ElseIf Y# > Result\MaxY# Then Result\MaxY# = Y#
				If Z# < Result\MinZ# Then Result\MinZ# = Z# ElseIf Z# > Result\MaxZ# Then Result\MaxZ# = Z#
			Next
		Next
	EndIf
	For i = 1 To CountChildren(EN)
		ChildResult.MeshMinMaxVertices = MeshMinMaxVerticesTransformed(GetChild(EN, i), Pitch#, Yaw#, Roll#, ScaleX#, ScaleY#, ScaleZ#)
		If ChildResult\MinX# < Result\MinX# Then Result\MinX# = ChildResult\MinX#
		If ChildResult\MinY# < Result\MinY# Then Result\MinY# = ChildResult\MinY#
		If ChildResult\MinZ# < Result\MinZ# Then Result\MinZ# = ChildResult\MinZ#
		If ChildResult\MaxX# > Result\MaxX# Then Result\MaxX# = ChildResult\MaxX#
		If ChildResult\MaxY# > Result\MaxY# Then Result\MaxY# = ChildResult\MaxY#
		If ChildResult\MaxZ# > Result\MaxZ# Then Result\MaxZ# = ChildResult\MaxZ#
		Delete ChildResult
	Next
	Return(Result)

End Function

;Bump mapping
Function GetMeshNameClean$(ID)
; Same bound as GetMeshName$ -- ID seeks into the 4-byte-per-entry
; mesh index table; a sentinel like -1 would SeekFile to -4.
If ID < 0 Or ID > 65534 Then Return ""
If LockedMeshes = 0
   F = OpenFile("Data\Game Data\Meshes.dat")
   If F = 0 Then Return ""
Else
   F = LockedMeshes
EndIf

SeekFile(F, ID * 4)
DataAdress = ReadInt(F)
If DataAdress = 0
   If LockedMeshes = 0 Then CloseFile(F)
   Return ""
EndIf

SeekFile(F, DataAdress)
IsAnim = ReadByte(F)
ReadFloat#(F)
ReadFloat#(F)
ReadFloat#(F)
ReadFloat#(F)
ReadShort(F)
NameWithExt$ = MediaReadFilename$(F, 260)
NameClean$ = Left(NameWithExt$, Len(NameWithExt$)-4)   
If LockedMeshes = 0 Then CloseFile(F)
Return NameClean$
End Function
