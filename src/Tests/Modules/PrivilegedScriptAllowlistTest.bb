Strict
EnableGC

; Regression test pinning the spawn-side privileged-script allowlist in
; Scripting.bb's ThreadScript + LoadPrivilegedScripts + IsPrivilegedScript.
;
; The allowlist is the "carve-out at the spawn boundary" path
; documented in CLAUDE.md's "Known ungated brick / griefing surface"
; deferred-follow-up note. It lets shipped content scripts that need
; privileged BVMs (marriage.rsl, AOE Damage Spell Template.rsl, ...)
; keep working when invoked from non-privileged spawn paths, while
; closing the four BVM gates that were previously open
; (SETACTORAISTATE / SETACTORTARGET / SETNAME / SETTAG).
;
; Three invariants to pin:
;
;   1. Lookup is case-insensitive (script names are stored verbatim
;      but compared via Upper()). A future refactor that drops the
;      Upper() on either side breaks the trust model.
;
;   2. Elevation only -- never demote. A caller that passes
;      Privileged=1 explicitly keeps that privilege regardless of
;      allowlist membership. Engine-tick spawns (LoginScript,
;      DeathScript, DM `/script` command) all pass Privileged=1; if
;      a future "demote-non-allowlisted" change crept in it would
;      silently break those.
;
;   3. Empty / missing allowlist returns False for every lookup
;      (= safe default). The production LoadPrivilegedScripts
;      tolerates a missing file; the lookup must also tolerate
;      PrivilegedScriptCount = 0 without bounds-walking the Dim.
;
; Replicated-gate pattern: Scripting.bb can't be Included into a Strict
; test build (pulls in briskvm + ScriptingCommands + the full BVM
; surface). Replicate just the allowlist + elevation predicates here.
; Any production change to LoadPrivilegedScripts /
; IsPrivilegedScript / the ThreadScript elevation block MUST update
; this file -- the duplication is the trigger to refresh.

; ====================================================================
; Mock allowlist registry. Mirrors the production
; `PrivilegedScriptList$(PrivilegedScriptListMax-1)` +
; `PrivilegedScriptCount%` shape. Strict-mode Dim assignment from
; within functions errors (per feedback_strict_mode_dim_array_assignment
; memory), so the test populates the registry via a named-slot global
; array indirectly: Install*() sets specific slot count + uses a
; switch-style assignment. To sidestep entirely, we use per-slot
; globals.
; ====================================================================

Global MockSlot0$ = ""
Global MockSlot1$ = ""
Global MockSlot2$ = ""
Global MockSlot3$ = ""
Global MockSlot4$ = ""
Global MockSlotCount% = 0

Function MockReset()
	MockSlot0 = ""
	MockSlot1 = ""
	MockSlot2 = ""
	MockSlot3 = ""
	MockSlot4 = ""
	MockSlotCount = 0
End Function

Function MockAdd(Name$)
	If MockSlotCount = 0 Then MockSlot0 = Name
	If MockSlotCount = 1 Then MockSlot1 = Name
	If MockSlotCount = 2 Then MockSlot2 = Name
	If MockSlotCount = 3 Then MockSlot3 = Name
	If MockSlotCount = 4 Then MockSlot4 = Name
	MockSlotCount = MockSlotCount + 1
End Function

; Replicates the production IsPrivilegedScript%(Name$) -- case-
; insensitive scan of the populated slots. Empty list returns False
; without bounds-walking.
Function MockIsPrivileged%(Name$)
	If MockSlotCount = 0 Then Return False
	Local NameU$ = Upper(Name$)
	If MockSlotCount > 0 Then If Upper(MockSlot0) = NameU Then Return True
	If MockSlotCount > 1 Then If Upper(MockSlot1) = NameU Then Return True
	If MockSlotCount > 2 Then If Upper(MockSlot2) = NameU Then Return True
	If MockSlotCount > 3 Then If Upper(MockSlot3) = NameU Then Return True
	If MockSlotCount > 4 Then If Upper(MockSlot4) = NameU Then Return True
	Return False
End Function

; Replicates ThreadScript's elevation block. Returns the effective
; privilege that the spawned ThreadScript Type instance would carry.
; If CallerPriv is already 1, returns 1 unchanged (never demote).
; If CallerPriv is 0 AND HSI is 0 (engine-initiated, not script-initiated)
;     AND Name is allowlisted, returns 1.
; Else returns 0.
;
; The HSI gate is critical: BVM_THREADEXECUTE runs from inside a script's
; body, where hSI is set (the currently-running script's instance). Without
; this gate, a hostile non-priv script could call ThreadExecute on any
; allowlisted name and inherit its elevation. Engine-initiated spawns (chat
; dispatch, spell-cast, right-click, NPC-init) all have hSI = 0.
Function MockEffectivePriv%(Name$, CallerPriv%, HSI%)
	Local EffectivePriv% = CallerPriv%
	If EffectivePriv = 0 And HSI = 0
		If MockIsPrivileged%(Name) Then EffectivePriv = 1
	EndIf
	Return EffectivePriv
End Function

; ====================================================================
; Empty allowlist -- safe default
; ====================================================================

Test testEmptyListReturnsFalseForAnyLookup()
	MockReset()
	Assert(MockIsPrivileged%("Spawn_Test") = False)
	Assert(MockIsPrivileged%("") = False)
	Assert(MockIsPrivileged%("anything") = False)
End Test

Test testEmptyListDoesNotElevate()
	MockReset()
	Assert(MockEffectivePriv%("Spawn_Test", 0, 0) = 0)
	Assert(MockEffectivePriv%("anything",   0, 0) = 0)
End Test

Test testEmptyListPreservesExplicitPrivCaller()
	; Engine-tick spawns (LoginScript, DM /script command) pass
	; Privileged=1 explicitly. Empty allowlist must NOT demote them.
	MockReset()
	Assert(MockEffectivePriv%("LoginScript", 1, 0) = 1)
End Test

; ====================================================================
; Populated allowlist -- elevation works (engine-initiated; hSI=0)
; ====================================================================

Test testAllowlistedScriptElevatesFromEngineContext()
	MockReset()
	MockAdd("marriage")
	Assert(MockEffectivePriv%("marriage", 0, 0) = 1)
End Test

Test testNonAllowlistedScriptDoesNotElevate()
	MockReset()
	MockAdd("marriage")
	Assert(MockEffectivePriv%("user_script", 0, 0) = 0)
	Assert(MockEffectivePriv%("Bad_Brick",   0, 0) = 0)
End Test

Test testMultipleAllowlistEntries()
	MockReset()
	MockAdd("In-game Commands")
	MockAdd("AOE Damage Spell Template")
	MockAdd("marriage")
	Assert(MockEffectivePriv%("In-game Commands",            0, 0) = 1)
	Assert(MockEffectivePriv%("AOE Damage Spell Template",   0, 0) = 1)
	Assert(MockEffectivePriv%("marriage",                    0, 0) = 1)
	Assert(MockEffectivePriv%("not_listed",                  0, 0) = 0)
End Test

; ====================================================================
; Case-insensitive lookup
; ====================================================================

Test testLookupIsCaseInsensitiveLower()
	MockReset()
	MockAdd("Spawn_Test")
	Assert(MockEffectivePriv%("spawn_test", 0, 0) = 1)
	Assert(MockEffectivePriv%("SPAWN_TEST", 0, 0) = 1)
	Assert(MockEffectivePriv%("Spawn_Test", 0, 0) = 1)
End Test

Test testLookupIsCaseInsensitiveMixedCaseListedEntry()
	MockReset()
	MockAdd("MARRIAGE")
	Assert(MockEffectivePriv%("marriage", 0, 0) = 1)
	Assert(MockEffectivePriv%("Marriage", 0, 0) = 1)
End Test

; ====================================================================
; Elevation-only / never-demote
; ====================================================================

Test testExplicitPrivCallerStaysPrivWhenAllowlisted()
	; DM /script command spawns with Privileged=1 against a script
	; name that happens to also be on the allowlist. The caller's
	; privilege must be preserved unchanged (idempotent for this case).
	MockReset()
	MockAdd("marriage")
	Assert(MockEffectivePriv%("marriage", 1, 0) = 1)
End Test

Test testExplicitPrivCallerStaysPrivWhenNotAllowlisted()
	; DM /script command spawns with Privileged=1 against an arbitrary
	; user script. The caller's privilege must NOT be demoted to 0
	; just because the script name isn't on the allowlist.
	MockReset()
	MockAdd("marriage")
	Assert(MockEffectivePriv%("any_user_script", 1, 0) = 1)
End Test

Test testExplicitPrivCallerStaysPrivWithEmptyAllowlist()
	; If the data file is missing, the allowlist is empty. Engine-tick
	; privileged spawns must continue to function -- the carve-out
	; cannot accidentally introduce a "must be on the list" rule.
	MockReset()
	Assert(MockEffectivePriv%("LoginScript", 1, 0) = 1)
	Assert(MockEffectivePriv%("DeathScript", 1, 0) = 1)
End Test

; ====================================================================
; Boundary: an empty Name$ should never elevate (defense against a
; future caller that passes "" by accident).
; ====================================================================

Test testEmptyNameDoesNotElevate()
	MockReset()
	MockAdd("marriage")
	Assert(MockEffectivePriv%("", 0, 0) = 0)
End Test

; ====================================================================
; Script-initiated spawn (BVM_THREADEXECUTE bypass) — hSI != 0 means a
; script is currently executing and calling ThreadScript via
; BVM_THREADEXECUTE. The allowlist elevation MUST NOT fire here;
; otherwise a hostile non-priv script could call
; `ThreadExecute("In-game Commands", "ItemPack", victim_handle, ...)`
; and inherit elevation via the allowlisted name. PR #329 reviewer
; raised this exact concern; closed via the `hSI = 0` gate in
; ThreadScript's elevation block.
; ====================================================================

Test testScriptInitiatedAllowlistedSpawnDoesNotElevate()
	; Non-priv script (hSI != 0, CallerPriv = 0) calls ThreadExecute
	; on an allowlisted name. Expected: NO elevation.
	MockReset()
	MockAdd("marriage")
	MockAdd("In-game Commands")
	Assert(MockEffectivePriv%("marriage",         0, 12345) = 0)
	Assert(MockEffectivePriv%("In-game Commands", 0, 12345) = 0)
End Test

Test testScriptInitiatedExplicitPrivIsStillPreserved()
	; Privileged script calling ThreadExecute -- BVM_THREADEXECUTE
	; propagates the caller's priv. Should pass through to the new
	; spawn unchanged (1 stays 1 regardless of hSI / allowlist).
	MockReset()
	MockAdd("marriage")
	Assert(MockEffectivePriv%("marriage",         1, 12345) = 1)
	Assert(MockEffectivePriv%("user_script",      1, 12345) = 1)
End Test

Test testScriptInitiatedNonAllowlistedStaysNonPriv()
	; Non-priv script calls ThreadExecute on a non-allowlisted name.
	; Expected: no elevation (the default path).
	MockReset()
	MockAdd("marriage")
	Assert(MockEffectivePriv%("user_script", 0, 12345) = 0)
End Test

; ====================================================================
; hSI lifecycle (PR fixing the stale-hSI elevation regression).
;
; The elevation gate above keys "engine-initiated" off hSI = 0. Its
; correctness depends on hSI ACTUALLY being 0 when no script is running.
; UpdateScripts sets hSI = Handle(S) for each invoked script (BVM_Invoke)
; but, before the fix, never reset it after the invoke loop -- so hSI
; lingered as the last (often freed) script's handle between ticks. The
; main loop runs RCE_Update() (engine-initiated ThreadScript for chat /
; spell / right-click / NPC-init) BEFORE the next UpdateScripts(), so
; after the first script ever ran, every engine-initiated allowlisted
; spawn saw hSI <> 0, was misclassified as script-initiated, and did NOT
; elevate -- the #329 allowlist was silently inert. The fix resets hSI = 0
; at the end of UpdateScripts's invoke loop. These tests model the tick
; lifecycle to pin both the bug and the fix.
; ====================================================================

Global LifecycleHSI% = 0

; Models UpdateScripts's invoke loop WITH the fix: hSI is set per invoked
; script, then reset to 0 once the loop exits (no script running after).
Function TickInvokeLoopFixed(ScriptHandle%)
	LifecycleHSI = ScriptHandle   ; hSI = Handle(S) at the BVM_Invoke call
	LifecycleHSI = 0              ; the fix: reset after the invoke loop
End Function

; Models the pre-fix loop: hSI set, never reset -> lingers stale.
Function TickInvokeLoopBuggy(ScriptHandle%)
	LifecycleHSI = ScriptHandle
End Function

Test testEngineSpawnAfterScriptRanStillElevatesWithReset()
	; A script ran this tick (hSI got set); the fix resets it. The next
	; engine-initiated allowlisted spawn reads hSI = 0 and elevates.
	MockReset()
	MockAdd("marriage")
	TickInvokeLoopFixed(98765)
	Assert(LifecycleHSI = 0)
	Assert(MockEffectivePriv%("marriage", 0, LifecycleHSI) = 1)
End Test

Test testWithoutResetStaleHSIDefeatsElevation()
	; Demonstrates the regression the reset closes: without the reset, hSI
	; lingers nonzero, so the engine-initiated allowlisted spawn is
	; misclassified as script-initiated and silently does NOT elevate.
	MockReset()
	MockAdd("marriage")
	TickInvokeLoopBuggy(98765)
	Assert(LifecycleHSI = 98765)
	Assert(MockEffectivePriv%("marriage", 0, LifecycleHSI) = 0)
End Test

Test testStaleHSIStillBlocksGenuineScriptInitiatedBypass()
	; The reset must not weaken the BVM_THREADEXECUTE protection: a spawn
	; genuinely initiated from inside a running script (hSI set to the live
	; script's handle during the invoke loop) must still NOT elevate.
	MockReset()
	MockAdd("In-game Commands")
	LifecycleHSI = 55555   ; a live script is mid-invoke
	Assert(MockEffectivePriv%("In-game Commands", 0, LifecycleHSI) = 0)
End Test
