Strict
EnableGC

; Pins the waypoint-slot bounds-clamp contract applied to Area\SpawnWaypoint
; at load (src/Modules/ServerAreas.bb, in LoadArea right after the ReadShort).
;
; WHY THIS MATTERS: SpawnWaypoint is read signed via ReadShort (-32768..32767)
; from the area .dat, then copied verbatim into AI\CurrentWaypoint at spawn
; (Server.bb:565 / :775) -- which BYPASSES SetArea's own range clamp -- and is
; finally used to index WaypointX#[AI\CurrentWaypoint] (a Field[1999]) on the
; AI patrol tick (GameServer.bb:864/869/930). Those consumer sites are
; UNGUARDED, unlike the NextWaypoint siblings at GameServer.bb:880-892. A
; corrupt or hand-edited area file with an out-of-range slot therefore
; Field-OOBs and crashes the shared server, disconnecting every player.
;
; The fix clamps at the load boundary: `If v < 0 Or v > 1999 Then v = 0`.
; This is a REPLICATED-LOGIC test (the real clamp is inline in LoadArea, which
; cannot be unit-loaded without the full area-file format + ServerAreas dep
; graph). clampWaypointSlot% below mirrors the source expression exactly; the
; source fix is additionally verified by clean compile + the traced consumer
; chain. The assertions that carry the contract are the NEGATIVE and >1999
; cases -- a `> 1999`-only guard (dropping the `< 0` half) is precisely the
; original NextWaypoint bug recorded in the GameServer.bb:871-879 comment, and
; this test fails if that regression is reintroduced here.

; Exact mirror of the clamp applied at ServerAreas.bb LoadArea.
Function clampWaypointSlot%(v%)
	If v < 0 Or v > 1999 Then Return 0
	Return v
End Function

; In-range values pass through untouched (lower edge, interior, upper edge).
Test testInRangeSlotsUnchangedAtBothEdges()
	Assert(clampWaypointSlot%(0) = 0)
	Assert(clampWaypointSlot%(1) = 1)
	Assert(clampWaypointSlot%(1000) = 1000)
	Assert(clampWaypointSlot%(1999) = 1999)
End Test

; Just past the upper bound clamps to the origin slot (0). 2000 is the first
; out-of-bounds index for a Field[1999] (slots 0..1999 inclusive).
Test testJustAboveUpperBoundClampsToZero()
	Assert(clampWaypointSlot%(2000) = 0)
End Test

; The full positive reach of a signed ReadShort clamps. This is the value a
; corrupt .dat can carry in the high byte.
Test testMaxSignedShortClampsToZero()
	Assert(clampWaypointSlot%(32767) = 0)
End Test

; The NEGATIVE half -- the historically-missed case. ReadShort yields negatives
; for any slot with the sign bit set; a `> 1999`-only guard would let these
; through and Field-OOB with a negative index.
Test testNegativeSlotsClampToZero()
	Assert(clampWaypointSlot%(-1) = 0)
	Assert(clampWaypointSlot%(-1000) = 0)
	Assert(clampWaypointSlot%(-32768) = 0)
End Test
