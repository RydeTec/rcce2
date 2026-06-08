; Everything the server needs to know about an area (except water)
Type Area
	; Area name
	Field Name$
	; Environment
	Field WeatherChance[4]
	Field Outdoors
	Field WeatherLink$, WeatherLinkArea.Area
	; Area scripts
	Field EntryScript$, ExitScript$
	; Script triggers
	Field TriggerX#[149], TriggerY#[149], TriggerZ#[149], TriggerSize#[149], TriggerScript$[149], TriggerMethod$[149]
	; Waypoints
	Field WaypointX#[1999], WaypointY#[1999], WaypointZ#[1999]
	Field PrevWaypoint[1999], NextWaypointA[1999], NextWaypointB[1999]
	Field WaypointPause[1999]
	; Portals
	Field PortalName$[99], PortalLinkArea$[99], PortalLinkName$[99]
	Field PortalX#[99], PortalY#[99], PortalZ#[99], PortalSize#[99], PortalYaw#[99]
	; Spawn points
	Field SpawnActor[999], SpawnWaypoint[999], SpawnSize#[999], SpawnScript$[999], SpawnActorScript$[999], SpawnDeathScript$[999]
	Field SpawnFrequency[999], SpawnMax[999], SpawnRange#[999]
	; Is PvP allowed
	Field PvP
	; Gravity strength (0-1000)
	Field Gravity
	; Track instances
	Field Instances.AreaInstance[99]
	; Head of this area's per-Area ServerWater chain. The global
	; `For Each ServerWater` collection still owns every water record
	; (creation / Delete go through Each); this field is an O(1)-lookup
	; index into the subset belonging to this Area, used by the
	; per-tick underwater-damage check in GameServer.bb to avoid
	; walking the entire global list once per actor.
	Field FirstWater.ServerWater
End Type

; Water areas for damaging things
Type ServerWater
	Field Area.Area
	Field X#, Y#, Z#
	Field Width#, Depth#
	Field Damage, DamageType
	; Next link in Area\FirstWater chain. Maintained at allocation
	; (ServerLoadArea below). Becomes dangling when the owning Area
	; is deleted (ServerUnloadArea Deletes both the chain heads and
	; every linked ServerWater in a single call, so no caller can
	; observe a stale NextWater pointer).
	Field NextWater.ServerWater
End Type

; Each area instance may have up to 500 player owned items of scenery (e.g. chests, doors, etc.) {##}
;Type OwnedScenery
;	Field InventorySize
;	Field Inventory.Inventory
;	Field AccountName$, CharNumber
;End Type

; Instancing structure
Type AreaInstance
	Field Area.Area
	Field ID
	Field FirstInZone.ActorInstance ; Head of linked list containing all actor instances in a zone
	Field CurrentWeather, CurrentWeatherTime
	Field SpawnLast[999], Spawned[999]
	;Field OwnedScenery.OwnedScenery[499] ; {##}
End Type

; Updates weather for an area
Function UpdateWeather(A.AreaInstance)

	A\CurrentWeatherTime = A\CurrentWeatherTime - 1

	; Time to update the weather for this area
	If A\CurrentWeatherTime <= 0
		; Get weather from linked area
		If A\Area\WeatherLinkArea <> Null
			A\CurrentWeatherTime = A\Area\WeatherLinkArea\Instances[0]\CurrentWeatherTime
			A\CurrentWeather = A\Area\WeatherLinkArea\Instances[0]\CurrentWeather
		; Choose own weather from probabilities
		Else
			A\CurrentWeatherTime = Rand(2500, 10000)
			A\CurrentWeather = 0
			NewWeather = Rand(1, 100)
			Min = 0
			For i = 0 To 4
				If A\Area\WeatherChance[i] > 0
					Max = Min + A\Area\WeatherChance[i]
					If NewWeather >= Min And NewWeather < Max Then A\CurrentWeather = i + 1 : Exit
					Min = Max
				EndIf
			Next
		EndIf

		; Inform players in this area
		AI.ActorInstance = A\FirstInZone
		While AI <> Null
			If AI\RNID > 0 Then RCE_Send(Host, AI\RNID, P_WeatherChange, RCE_StrFromInt$(Handle(A), 4) + RCE_StrFromInt$(A\CurrentWeather, 1), True)
			AI = AI\NextInZone
		Wend
	EndIf

End Function


; Creates a new blank area
Function ServerCreateArea.Area()

	A.Area = New Area
	For i = 0 To 1999
		A\PrevWaypoint[i] = 2005
		A\NextWaypointA[i] = 2005
		A\NextWaypointB[i] = 2005
		If i < 1000 Then A\SpawnFrequency[i] = 10
	Next
	A\Gravity = 300
	ServerCreateAreaInstance(A, 0)
	Return A

End Function

; Creates a new instance of an area
Function ServerCreateAreaInstance.AreaInstance(Ar.Area, ID)

	; New instance
	AInstance.AreaInstance = New AreaInstance
	Ar\Instances[ID] = AInstance
	AInstance\Area = Ar
	AInstance\ID = ID

	; Initial spawn point times
	For i = 0 To 999
		AInstance\SpawnLast[i] = MilliSecs()
	Next

	; Copy ownable scenery data from default instance [@@@]
	;If ID > 0
	;	For i = 0 To 499
	;		If Ar\Instances[0]\OwnedScenery[i] <> Null
	;			AInstance\OwnedScenery[i] = New OwnedScenery
	;			AInstance\OwnedScenery[i]\InventorySize = Ar\Instances[0]\OwnedScenery[i]\InventorySize
	;			If AInstance\OwnedScenery[i]\InventorySize > 0 Then AInstance\OwnedScenery[i]\Inventory = New Inventory
	;		EndIf
	;	Next
	;EndIf

	; Done
	Return AInstance

End Function

; Finds an area by the name
Function FindArea.Area(Name$)

	Name$ = Upper$(Name$)
	For A.Area = Each Area
		If Upper$(A\Name$) = Name$ Then Return A
	Next

End Function

; Unloads all server data for an area
; Duplicate an Area template -- allocate via ServerCreateArea then copy
; every field from the source. Returns the new Area instance, or Null
; if ServerCreateArea fails or the source is Null.
;
; The caller (EntityFactory_DuplicateZone) is responsible for fixing up
; the name to avoid colliding with the source -- ServerSaveArea uses
; A\Name$ as the .dat filename. We set Name$ to src.Name$ + " (copy)"
; here; the EntityFactory layer applies UniqueZoneName$ on top for
; subsequent collisions.
;
; Instances[] + FirstWater are intentionally NOT copied -- they're
; runtime state for live area instances, not data the editor owns.
; WeatherLinkArea pointer is also skipped (the WeatherLink$ string is
; copied; the resolved pointer rebuilds at load).
Function DuplicateAreaTemplate.Area(Src.Area)
	If Src = Null Then Return Null

	Local Dst.Area = ServerCreateArea()
	If Dst = Null Then Return Null

	Dst\Name$ = Src\Name$ + " (copy)"

	For i = 0 To 4
		Dst\WeatherChance[i] = Src\WeatherChance[i]
	Next
	Dst\Outdoors = Src\Outdoors
	Dst\WeatherLink$ = Src\WeatherLink$
	; WeatherLinkArea pointer skipped -- resolved at load from the string
	Dst\EntryScript$ = Src\EntryScript$
	Dst\ExitScript$  = Src\ExitScript$

	; Triggers (X/Y/Z/Size/Script/Method) x 150
	For i = 0 To 149
		Dst\TriggerX#[i]    = Src\TriggerX#[i]
		Dst\TriggerY#[i]    = Src\TriggerY#[i]
		Dst\TriggerZ#[i]    = Src\TriggerZ#[i]
		Dst\TriggerSize#[i] = Src\TriggerSize#[i]
		Dst\TriggerScript$[i] = Src\TriggerScript$[i]
		Dst\TriggerMethod$[i] = Src\TriggerMethod$[i]
	Next

	; Waypoints (X/Y/Z/Prev/NextA/NextB/Pause) x 2000. Big loop but
	; cheap -- pure assignment, no allocation, runs once per duplicate.
	For i = 0 To 1999
		Dst\WaypointX#[i]    = Src\WaypointX#[i]
		Dst\WaypointY#[i]    = Src\WaypointY#[i]
		Dst\WaypointZ#[i]    = Src\WaypointZ#[i]
		Dst\PrevWaypoint[i]  = Src\PrevWaypoint[i]
		Dst\NextWaypointA[i] = Src\NextWaypointA[i]
		Dst\NextWaypointB[i] = Src\NextWaypointB[i]
		Dst\WaypointPause[i] = Src\WaypointPause[i]
	Next

	; Portals (Name/LinkArea/LinkName/X/Y/Z/Size/Yaw) x 100
	For i = 0 To 99
		Dst\PortalName$[i]      = Src\PortalName$[i]
		Dst\PortalLinkArea$[i]  = Src\PortalLinkArea$[i]
		Dst\PortalLinkName$[i]  = Src\PortalLinkName$[i]
		Dst\PortalX#[i]         = Src\PortalX#[i]
		Dst\PortalY#[i]         = Src\PortalY#[i]
		Dst\PortalZ#[i]         = Src\PortalZ#[i]
		Dst\PortalSize#[i]      = Src\PortalSize#[i]
		Dst\PortalYaw#[i]       = Src\PortalYaw#[i]
	Next

	; Spawns (Actor/Waypoint/Size/Script/ActorScript/DeathScript/
	;        Frequency/Max/Range) x 1000
	For i = 0 To 999
		Dst\SpawnActor[i]       = Src\SpawnActor[i]
		Dst\SpawnWaypoint[i]    = Src\SpawnWaypoint[i]
		Dst\SpawnSize#[i]       = Src\SpawnSize#[i]
		Dst\SpawnScript$[i]     = Src\SpawnScript$[i]
		Dst\SpawnActorScript$[i] = Src\SpawnActorScript$[i]
		Dst\SpawnDeathScript$[i] = Src\SpawnDeathScript$[i]
		Dst\SpawnFrequency[i]   = Src\SpawnFrequency[i]
		Dst\SpawnMax[i]         = Src\SpawnMax[i]
		Dst\SpawnRange#[i]      = Src\SpawnRange#[i]
	Next

	Dst\PvP     = Src\PvP
	Dst\Gravity = Src\Gravity

	Return Dst
End Function

Function ServerUnloadArea(A.Area)

	; Walk the per-Area chain instead of the global ServerWater
	; collection. Capture the next link BEFORE Delete so the
	; current node's NextWater isn't read post-free. Skipping the
	; global For-Each here also drops the `If W\Area = A` filter,
	; turning O(global_waters) into O(this_area_waters). The
	; per-area chain head + NextWater links go out of scope when
	; A is Deleted below, so no caller can observe stale links.
	Local W.ServerWater = A\FirstWater
	Local WNext.ServerWater = Null
	While W <> Null
		WNext = W\NextWater
		Delete(W)
		W = WNext
	Wend
	A\FirstWater = Null
	;For j = 0 To 99 {##}
	;	If A\Instances[j] <> Null
	;		For i = 0 To 499
	;			If A\Instances[j]\OwnedScenery[i] <> Null
	;				If A\Instances[j]\OwnedScenery[i]\Inventory <> Null Then Delete A\Instances[j]\OwnedScenery[i]\Inventory
	;				Delete A\Instances[j]\OwnedScenery[i]
	;			EndIf
	;		Next
	;		Delete A\Instances[j]
	;	EndIf
	;Next
	Delete(A)

End Function

; Loads the server data for an area
Function ServerLoadArea.Area(Name$)

	F = ReadFile("Data\Server Data\Areas\" + Name$ + ".dat")
	If F = 0 Then Return Null

		A.Area = New Area
		A\Name$ = Name$
		For i = 0 To 4 : A\WeatherChance[i] = ReadByte(F) : Next
		; Bound every string read against a corrupted / tampered
		; Area .dat file. 1024 caps match ReadActorInstance's per-char
		; Script$/DeathScript$ ceiling; 256 caps cover short identifiers
		; like portal / area names. Same shape as the data-loader sweep
		; in PR #149 -- a wild ReadInt length prefix would otherwise hang
		; the server at boot allocating gigabytes per Area file.
		A\EntryScript$ = ReadBoundedString$(F, 1024)
		A\ExitScript$  = ReadBoundedString$(F, 1024)
		A\PvP          = ReadByte(F)
		A\Gravity      = ReadShort(F)
		A\Outdoors     = ReadByte(F)
		A\WeatherLink$ = ReadBoundedString$(F, 256)
		For i = 0 To 149
			A\TriggerX#[i]      = ReadFloat#(F)
			A\TriggerY#[i]      = ReadFloat#(F)
			A\TriggerZ#[i]      = ReadFloat#(F)
			A\TriggerSize#[i]   = ReadFloat#(F)
			A\TriggerScript$[i] = ReadBoundedString$(F, 1024)
			A\TriggerMethod$[i] = ReadBoundedString$(F, 256)
		Next
		For i = 0 To 1999
			A\WaypointX#[i]    = ReadFloat#(F)
			A\WaypointY#[i]    = ReadFloat#(F)
			A\WaypointZ#[i]    = ReadFloat#(F)
			A\NextWaypointA[i] = ReadShort(F)
			A\NextWaypointB[i] = ReadShort(F)
			A\PrevWaypoint[i]  = ReadShort(F)
			A\WaypointPause[i] = ReadInt(F)
		Next
		For i = 0 To 99
			A\PortalName$[i]     = ReadBoundedString$(F, 256)
			A\PortalLinkArea$[i] = ReadBoundedString$(F, 256)
			A\PortalLinkName$[i] = ReadBoundedString$(F, 256)
			A\PortalX#[i]        = ReadFloat#(F)
			A\PortalY#[i]        = ReadFloat#(F)
			A\PortalZ#[i]        = ReadFloat#(F)
			A\PortalSize#[i]     = ReadFloat#(F)
			A\PortalYaw#[i]      = ReadFloat#(F)
		Next
		For i = 0 To 999
			A\SpawnActor[i]        = ReadShort(F)
			A\SpawnWaypoint[i]     = ReadShort(F)
			; WaypointX/Y/Z are Field[1999]. SpawnWaypoint is read signed
			; (-32768..32767) and later copied verbatim into AI\CurrentWaypoint
			; at spawn (Server.bb:565 / :775), bypassing SetArea's own range
			; clamp, then used to index WaypointX#[AI\CurrentWaypoint] on the AI
			; patrol tick (GameServer.bb:864/869/930 -- unguarded, unlike the
			; NextWaypoint siblings at :880-892). A corrupt or hand-edited area
			; file with an out-of-range slot would Field-OOB and crash the shared
			; server (every connected player disconnected). Clamp at the load
			; boundary -- same pattern as the DamageType clamp below and the
			; SpawnActor range guard in PreLoadSpawns (Server.bb:758). Slot 0 is a
			; valid origin fallback, matching SetArea's own out-of-range behaviour.
			If A\SpawnWaypoint[i] < 0 Or A\SpawnWaypoint[i] > 1999 Then A\SpawnWaypoint[i] = 0
			A\SpawnSize#[i]        = ReadFloat#(F)
			A\SpawnScript$[i]      = ReadBoundedString$(F, 1024)
			A\SpawnActorScript$[i] = ReadBoundedString$(F, 1024)
			A\SpawnDeathScript$[i] = ReadBoundedString$(F, 1024)
			A\SpawnMax[i]          = ReadShort(F)
			A\SpawnFrequency[i]    = ReadShort(F)
			A\SpawnRange#[i]       = ReadFloat#(F)
		Next
		Waters = ReadShort(F)
		For i = 1 To Waters
			W.ServerWater = New ServerWater
			W\Area = A
			W\X# = ReadFloat#(F)
			W\Y# = ReadFloat#(F)
			W\Z# = ReadFloat#(F)
			W\Width#     = ReadFloat#(F)
			W\Depth#     = ReadFloat#(F)
			W\Damage     = ReadShort(F)
			W\DamageType = ReadShort(F)
			; A\Resistances is Field[19]; DamageTypes$ is Dim'd (19).
			; ReadShort can carry -32768..32767; clamp before the runtime
			; SafeZone-damage loop (GameServer.bb ~773) indexes
			; A\Resistances[SW\DamageType].
			If W\DamageType < 0 Or W\DamageType > 19 Then W\DamageType = 0
			; Link into A\FirstWater chain. With SaveArea +
			; ServerUnloadArea also using the chain, the global
			; `For Each ServerWater` collection still owns every
			; record (creation and Delete are the only sites that
			; touch the Each iterator), but no per-frame or
			; per-save code paths have to filter it by Area.
			; Head-insert is fine -- the underwater check Exits
			; on first match and SaveArea writes are
			; order-insensitive (ServerLoadArea just reads N
			; records).
			W\NextWater = A\FirstWater
			A\FirstWater = W
		Next

	CloseFile(F)

	; Create default instance (#0)
	AInstance.AreaInstance = ServerCreateAreaInstance(A, 0)

	; Load in any scenery ownerships {##}
	;For k = 0 To 99
	;	F = ReadFile("Data\Server Data\Areas\Ownerships\" + Name$ + " (" + Str$(k) + ") Ownerships.dat")
	;	If F <> 0
;
			; Create instance if required
	;		If k > 0 Then AInstance = ServerCreateAreaInstance(A, k)
;
			; Load data into instance
	;		For i = 0 To 499
	;			Exists = ReadByte(F)
	;			If Exists = 1
	;				AInstance\OwnedScenery[i] = New OwnedScenery
	;				AInstance\OwnedScenery[i]\AccountName$ = ReadString$(F)
	;				AInstance\OwnedScenery[i]\CharNumber = ReadByte(F)
	;				AInstance\OwnedScenery[i]\InventorySize = ReadByte(F)
	;				If AInstance\OwnedScenery[i]\InventorySize > 0
	;					AInstance\OwnedScenery[i]\Inventory = New Inventory
	;					For j = 0 To AInstance\OwnedScenery[i]\InventorySize - 1
	;						AInstance\OwnedScenery[i]\Inventory\Items[j] = ReadItemInstance(F)
	;						AInstance\OwnedScenery[i]\Inventory\Amounts[j] = ReadShort(F)
	;					Next
	;				EndIf
	;			EndIf
	;		Next
	;		CloseFile(F)
;
;		EndIf
;	Next

	Return A

End Function

; Saves the server data for an area.
;
; Atomic rewrite: writes to <area>.dat.tmp first; on success demotes
; the existing area file to <area>.dat.bak and promotes the temp. A
; crash mid-write previously left the area file truncated -- areas
; hold NPC spawn data, scripts, waypoints, weather configuration,
; trigger volumes; a 0-byte save was a meaningful data loss every
; time the server crashed during a save flush.
Function ServerSaveArea(A.Area)

	; Save map data
	Local FinalPath$ = "Data\Server Data\Areas\" + A\Name$ + ".dat"
	Local TempPath$ = SafeWriteOpen(FinalPath$)
	F = WriteFile(TempPath$)
	If F = 0 Then Return False

		For i = 0 To 4 : WriteByte F, A\WeatherChance[i] : Next
		WriteString(F, A\EntryScript$)
		WriteString(F, A\ExitScript$)
		WriteByte(F,   A\PvP)
		WriteShort(F,  A\Gravity)
		WriteByte( F,  A\Outdoors)
		WriteString(F, A\WeatherLink$)
		For i = 0 To 149
			WriteFloat(F, A\TriggerX#[i])
			WriteFloat(F, A\TriggerY#[i])
			WriteFloat(F, A\TriggerZ#[i])
			WriteFloat(F, A\TriggerSize#[i])
			WriteString(F, A\TriggerScript$[i])
			WriteString(F, A\TriggerMethod$[i])
		Next
		For i = 0 To 1999
			WriteFloat(F, A\WaypointX#[i])
			WriteFloat(F, A\WaypointY#[i])
			WriteFloat(F, A\WaypointZ#[i])
			WriteShort(F, A\NextWaypointA[i])
			WriteShort(F, A\NextWaypointB[i])
			WriteShort(F, A\PrevWaypoint[i])
			WriteInt(F, A\WaypointPause[i])
		Next
		For i = 0 To 99
			WriteString(F, A\PortalName$[i])
			WriteString(F, A\PortalLinkArea$[i])
			WriteString(F, A\PortalLinkName$[i])
			WriteFloat(F, A\PortalX#[i])
			WriteFloat(F, A\PortalY#[i])
			WriteFloat(F, A\PortalZ#[i])
			WriteFloat(F, A\PortalSize#[i])
			WriteFloat(F, A\PortalYaw#[i])
		Next
		For i = 0 To 999
			WriteShort(F, A\SpawnActor[i])
			WriteShort(F, A\SpawnWaypoint[i])
			WriteFloat(F, A\SpawnSize#[i])
			WriteString(F, A\SpawnScript$[i])
			WriteString(F, A\SpawnActorScript$[i])
			WriteString(F, A\SpawnDeathScript$[i])
			WriteShort(F, A\SpawnMax[i])
			WriteShort(F, A\SpawnFrequency[i])
			WriteFloat(F, A\SpawnRange#[i])
		Next

		; Water areas — walk this area's chain twice (count, then
		; write). Replaces two global For-Each loops that each
		; filtered by `If W\Area = A`. Chain head-insert order in
		; ServerLoadArea is the reverse of file order; chain order
		; doesn't affect read semantics because ServerLoadArea
		; just reads N records.
		Local W.ServerWater
		Count = 0
		W = A\FirstWater
		While W <> Null
			Count = Count + 1
			W = W\NextWater
		Wend
		WriteShort(F, Count)
		W = A\FirstWater
		While W <> Null
			WriteFloat(F, W\X#)
			WriteFloat(F, W\Y#)
			WriteFloat(F, W\Z#)
			WriteFloat(F, W\Width#)
			WriteFloat(F, W\Depth#)
			WriteShort(F, W\Damage)
			WriteShort(F, W\DamageType)
			W = W\NextWater
		Wend

	If Not SafeWriteCommit(TempPath$, FinalPath$, F) Then Return False

	;ServerSaveAreaOwnerships(A) {##}

	Return True

End Function

; Copies an area object exactly
Function ServerCopyArea.Area(A.Area)

	; Create area
	NewA.Area = New Area
	NewA\Name$ = "Copied zone"
	AInstance.AreaInstance = New AreaInstance
	NewA\Instances[0] = AInstance
	AInstance\Area = NewA

	; Copy data
	For i = 0 To 4
		NewA\WeatherChance[i] = A\WeatherChance[i]
	Next
	NewA\Outdoors = A\Outdoors
	NewA\WeatherLink$ = A\WeatherLink$
	;For i = 0 To 499 ;{##}
	;	If A\Instances[0]\OwnedScenery[i] <> Null
	;		NewA\Instances[0]\OwnedScenery[i] = New OwnedScenery
	;		NewA\Instances[0]\OwnedScenery[i]\InventorySize = A\Instances[0]\OwnedScenery[i]\InventorySize
	;		If NewA\Instances[0]\OwnedScenery[i]\InventorySize > 0 Then NewA\Instances[0]\OwnedScenery[i]\Inventory = New Inventory
	;	EndIf
	;Next
	NewA\EntryScript$ = A\EntryScript$
	NewA\ExitScript$ = A\ExitScript$
	For i = 0 To 149
		NewA\TriggerX#[i] = A\TriggerX#[i]
		NewA\TriggerY#[i] = A\TriggerY#[i]
		NewA\TriggerZ#[i] = A\TriggerZ#[i]
		NewA\TriggerSize#[i] = A\TriggerSize#[i]
		NewA\TriggerScript$[i] = A\TriggerScript$[i]
		NewA\TriggerMethod$[i] = A\TriggerMethod$[i]
	Next
	For i = 0 To 1999
		NewA\WaypointX#[i] = A\WaypointX#[i]
		NewA\WaypointY#[i] = A\WaypointY#[i]
		NewA\WaypointZ#[i] = A\WaypointZ#[i]
		NewA\PrevWaypoint[i] = A\PrevWaypoint[i]
		NewA\NextWaypointA[i] = A\NextWaypointA[i]
		NewA\NextWaypointB[i] = A\NextWaypointB[i]
		NewA\WaypointPause[i] = A\WaypointPause[i]
	Next
	For i = 0 To 999
		NewA\SpawnActor[i] = A\SpawnActor[i]
		NewA\SpawnWaypoint[i] = A\SpawnWaypoint[i]
		NewA\SpawnSize#[i] = A\SpawnSize#[i]
		NewA\SpawnScript$[i] = A\SpawnScript$[i]
		NewA\SpawnActorScript$[i] = A\SpawnActorScript$[i]
		NewA\SpawnDeathScript$[i] = A\SpawnDeathScript$[i]
		NewA\SpawnFrequency[i] = A\SpawnFrequency[i]
		NewA\SpawnMax[i] = A\SpawnMax[i]
	Next
	For i = 0 To 99
		NewA\PortalName$[i] = A\PortalName$[i]
		NewA\PortalLinkArea$[i] = A\PortalLinkArea$[i]
		NewA\PortalLinkName$[i] = A\PortalLinkName$[i]
		NewA\PortalX#[i] = A\PortalX#[i]
		NewA\PortalY#[i] = A\PortalY#[i]
		NewA\PortalZ#[i] = A\PortalZ#[i]
		NewA\PortalSize#[i] = A\PortalSize#[i]
		NewA\PortalYaw#[i] = A\PortalYaw#[i]
	Next
	NewA\PvP = A\PvP
	NewA\Gravity = A\Gravity

	Return NewA

End Function

; Save scenery ownerships {##}
;Function ServerSaveAreaOwnerships(Ar.Area)
;
;	For j = 0 To 99
;		; Find whether this instance has any ownerships which need saving
;		If j = 0
;			SaveInstance = True
;		Else
;			SaveInstance = False
;			If Ar\Instances[j] <> Null
;				For i = 0 To 499
;					If Ar\Instances[j]\OwnedScenery[i] <> Null
;						If Ar\Instances[j]\OwnedScenery[i]\AccountName$ <> ""
;							SaveInstance = True
;							Exit
;						Else
;							For k = 0 To Ar\Instances[j]\OwnedScenery[i]\InventorySize - 1
;								If Ar\Instances[j]\OwnedScenery[i]\Inventory\Items[k] <> Null
;									SaveInstance = True
;									Exit
;								EndIf
;							Next
;						EndIf
;					EndIf
;				Next
;			EndIf
;		EndIf
;
;		; Save ownerships for this instance
;		If SaveInstance = True
;			F = WriteFile("Data\Server Data\Areas\Ownerships\" + Ar\Name$ + " (" + Ar\Instances[j]\ID + ") Ownerships.dat")
;			If F = 0 Then RuntimeError("Could not write to " + "Data\Server Data\Areas\Ownerships\" + Ar\Name$ + " (" + Ar\Instances[j]\ID + ") Ownerships.dat!")
;
;				For i = 0 To 499
;					If Ar\Instances[j]\OwnedScenery[i] <> Null
;						WriteByte(F, 1)
;						WriteString(F, Ar\Instances[j]\OwnedScenery[i]\AccountName$)
;						WriteByte(F, Ar\Instances[j]\OwnedScenery[i]\CharNumber)
;						WriteByte(F, Ar\Instances[j]\OwnedScenery[i]\InventorySize)
;						If Ar\Instances[j]\OwnedScenery[i]\Inventory <> Null
;							For k = 0 To Ar\Instances[j]\OwnedScenery[i]\InventorySize - 1
;								WriteItemInstance(F, Ar\Instances[j]\OwnedScenery[i]\Inventory\Items[k])
;								WriteShort(F, Ar\Instances[j]\OwnedScenery[i]\Inventory\Amounts[k])
;							Next
;						EndIf
;					Else
;						WriteByte(F, 0)
;					EndIf
;				Next
;
;			CloseFile(F)
;		EndIf
;;	Next
;
;End Function