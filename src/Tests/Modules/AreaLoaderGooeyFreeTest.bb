Strict
EnableGC

; =============================================================================
; AreaLoaderGooeyFreeTest.bb -- structural UI-freedom gate for AreaLoader.bb
; (ADR-004 Phase B: the data-only zone loader carved out of ClientAreas.bb).
;
; PRIMARY ASSERTION IS THE COMPILE ITSELF. AreaLoader.bb's load-bearing
; contract is that it contains ZERO references to Gooey (GY_*), F-UI, or any
; GUE UI globals, so a target (Loom, tools) can Include it without the UI
; stack. This file Includes AreaLoader.bb with ONLY small inline stubs for
; its data-layer dependencies (Media/Logging/RCTrees/RottParticles/RCEnet/
; Actors) and the three AreaLoad* presentation hooks -- deliberately NOT
; Including Gooey.bb, F-UI.bb, or Media.bb. If someone later adds a GY_* /
; F-UI call (or any other UI-stack dependency) to AreaLoader.bb, this file
; stops compiling and test.bat fails. That is the test.
;
; Runtime coverage is limited to the pure helper NearestPower(N#, Snapper#),
; plus pinning the module's default global/constant values. LoadAreaData /
; UnloadArea / CreateSubdividedPlane / ChunkTerrain / SetViewDistance /
; RemoveSurface all require a Graphics3D context (CreateMesh / entity
; commands) and CANNOT run under the headless test runner -- their gate is
; the compile, not a runtime call. Do not add runtime calls to them here.
;
; Strict-mode note: this test file is Strict + EnableGC per the suite
; convention (see ItemsTest.bb), while AreaLoader.bb itself is a non-Strict
; legacy module. Strict is per-file, so the non-Strict include compiles
; unchanged -- the same arrangement ItemsTest.bb uses with Items.bb. The
; compiler reports "Included file is not Strict, disabling GC globally"
; for this shape; that is expected and harmless (the leak report still
; prints, and Strict checking of THIS file's code is unaffected).
; =============================================================================

; --- External type stubs ----------------------------------------------------
; AreaLoader.bb dereferences Me\Actor\Environment (Water walking-collision
; branch) and the MeshMinMaxVertices result type from Media.bb's
; MeshMinMaxVerticesTransformed. Stub the minimal shapes.
Type Actor
	Field Environment
End Type

Type ActorInstance
	Field Actor.Actor
End Type

Global Me.ActorInstance = Null

Type MeshMinMaxVertices
	Field MinX#, MaxX#
	Field MinY#, MaxY#
	Field MinZ#, MaxZ#
End Type

; --- Constant stubs ---------------------------------------------------------
; Collision constants live in Client.bb / GUE.bb; Environment_Walk in
; Actors.bb. Values pinned to the real ones (Client.bb:103-105, Actors.bb:29).
Const C_Sphere    = 1
Const C_Box       = 2
Const C_Triangle  = 3
Const Environment_Walk = 3

; --- Logging stubs (Logging.bb) ----------------------------------------------
Global MainLog = 0

Function WriteLog(LogHandle, Dat$, Timestamp = True, Datestamp = False)
End Function

Function ReadBoundedString$(F, MaxLen)
	Return ""
End Function

; SaveArea (relocated into AreaLoader.bb) uses the atomic-write helpers; the
; test is a compile gate and never runs the save path, so stub them as
; pass-throughs (same shape as ItemsTest.bb).
Function SafeWriteOpen$(FinalPath$)
	Return FinalPath$
End Function

Function SafeWriteCommit%(TempPath$, FinalPath$, F)
	Return True
End Function

; --- Media stubs (Media.bb) --------------------------------------------------
; Signatures match Media.bb. Bodies are no-ops: the test never runs the
; load path (needs Graphics3D), the compiler only needs resolution.
Function LockMeshes()
End Function

Function UnlockMeshes()
End Function

Function LockTextures()
End Function

Function UnlockTextures()
End Function

Function GetMesh(ID, Duplicate = False)
	Return 0
End Function

Function GetTexture(ID, Copy = False)
	Return 0
End Function

Function GetSound(ID)
	Return 0
End Function

Function GetMeshName$(ID)
	Return ""
End Function

Function GetMeshNameClean$(ID)
	Return ""
End Function

Function GetSoundName$(ID)
	Return ""
End Function

Function GetMusicName$(ID)
	Return ""
End Function

Function UnloadMesh(ID)
End Function

Function UnloadTexture(ID)
End Function

Function UnloadSound(ID)
End Function

Function MeshMinMaxVerticesTransformed.MeshMinMaxVertices(EN, Pitch#, Yaw#, Roll#, ScaleX#, ScaleY#, ScaleZ#)
	Return New MeshMinMaxVertices()
End Function

; --- RCEnet stub (RCEnet.bb) -------------------------------------------------
; RCE_Update() is a userlib decl (src/userlibs/RCEnet.decls) and resolves
; without a stub; RCE_CreateMessages is a module function and needs one.
Function RCE_CreateMessages()
End Function

; --- RCTrees stub (RCTrees.bb) -----------------------------------------------
Function UnloadTrees(deltree = True)
End Function

; --- RottParticles stubs (RottParticles.bb) ----------------------------------
Function RP_LoadEmitterConfig(File$, Texture, FaceEntity)
	Return 0
End Function

Function RP_CreateEmitter(Configuration, Scale# = 1.0)
	Return 0
End Function

Function RP_FreeEmitter(ID, FreeConfig = False, FreeTex = False)
End Function

; --- AreaLoad* presentation hooks ---------------------------------------------
; The contract under test: AreaLoader.bb delegates ALL UI presentation to
; these three functions, which the including target must define. GUE's
; Gooey implementations live in ClientAreas.bb; here they are no-ops --
; proving a target can satisfy the contract without any UI stack.
Function AreaLoadBegin(DisplayItems)
End Function

Function AreaLoadProgress(Pct)
End Function

Function AreaLoadEnd()
End Function

; --- Module under test --------------------------------------------------------
; Path.bb is a pure string-helper module (GetFilename$ etc.) -- including it
; is simpler and more faithful than re-stubbing it.
Include "Modules\Path.bb"
Include "Modules\AreaLoader.bb"

; --- Helpers -------------------------------------------------------------------
Function AreaLoaderTest_FloatEq%(A#, B#)
	Return Abs(A# - B#) < 0.0001
End Function

; --- Tests ----------------------------------------------------------------------

; NearestPower(N#, Snapper#) snaps N to the multiple of Snapper nearest zero
; (Int truncation of the quotient). Values are chosen so that truncation and
; round-to-nearest agree (quotient fraction < 0.5), so the assertions hold
; regardless of Blitz's float->int conversion mode.
Test testNearestPowerExactMultiples()
	Assert(AreaLoaderTest_FloatEq(NearestPower(8.0, 4.0), 8.0))
	Assert(AreaLoaderTest_FloatEq(NearestPower(0.0, 5.0), 0.0))
	Assert(AreaLoaderTest_FloatEq(NearestPower(100.0, 10.0), 100.0))
	Assert(AreaLoaderTest_FloatEq(NearestPower(-20.0, 4.0), -20.0))
End Test

Test testNearestPowerSnapsPositive()
	; 9.0 / 4.0 = 2.25 -> 2 -> 8.0
	Assert(AreaLoaderTest_FloatEq(NearestPower(9.0, 4.0), 8.0))
	; 12.0 / 5.0 = 2.4 -> 2 -> 10.0
	Assert(AreaLoaderTest_FloatEq(NearestPower(12.0, 5.0), 10.0))
	; 1.2 / 1.0 = 1.2 -> 1 -> 1.0
	Assert(AreaLoaderTest_FloatEq(NearestPower(1.2, 1.0), 1.0))
End Test

Test testNearestPowerSnapsNegative()
	; -9.0 / 4.0 = -2.25 -> -2 -> -8.0 (snaps toward zero)
	Assert(AreaLoaderTest_FloatEq(NearestPower(-9.0, 4.0), -8.0))
	; -12.0 / 5.0 = -2.4 -> -2 -> -10.0
	Assert(AreaLoaderTest_FloatEq(NearestPower(-12.0, 5.0), -10.0))
End Test

; Pin the module's load-bearing defaults. Downstream code (fog clamping in
; LoadAreaData, slope movement restriction, sentinel 65535 "no texture"
; checks) depends on these exact values; changing them must be a conscious
; decision that updates this test.
Test testAreaLoaderDefaultsPinned()
	Assert(AreaLoaderTest_FloatEq(MaxFogFar#, 2000.0))
	Assert(AreaLoaderTest_FloatEq(SlopeRestrict#, 0.6))
	Assert(SkyTexID = 65535)
	Assert(CloudTexID = 65535)
	Assert(StormCloudTexID = 65535)
	Assert(StarsTexID = 65535)
	Assert(LoadingTexID = 65535)
	Assert(LoadingMusicID = 65535)
	Assert(AmbientR = 100)
	Assert(AmbientG = 100)
	Assert(AmbientB = 100)
End Test
