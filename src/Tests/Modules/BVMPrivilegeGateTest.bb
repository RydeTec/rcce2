Strict
EnableGC

; Regression tests pinning the BVM privilege-gate contract for the
; "equivalent-effect bypass" cluster in ScriptingCommands.bb.
;
; Nineteen BVM functions had effects identical to already-gated
; peers but lacked the gate themselves, defeating the privilege model:
;
;   Newly-gated function    Bypass of           Gate chosen
;   -------------------------------------------------------------------
;   BVM_CHANGEGOLD          BVM_SETGOLD         RequirePrivileged
;   BVM_CHANGEMONEY         BVM_SETMONEY        RequirePrivileged
;   BVM_GIVEXP              BVM_SETACTORLEVEL   RequirePrivileged
;   BVM_GIVEKILLXP          BVM_SETACTORLEVEL   RequirePrivileged
;   BVM_SETATTRIBUTE        BVM_KILLACTOR       RequirePrivileged
;   BVM_CHANGEATTRIBUTE     BVM_KILLACTOR       RequirePrivileged
;   BVM_SETMAXATTRIBUTE     BVM_SETATTRIBUTE    RequirePrivileged
;   BVM_CHANGEMAXATTRIBUTE  BVM_SETATTRIBUTE    RequirePrivileged
;   BVM_SETREPUTATION       BVM_SETHOMEFACTION  RequirePrivileged
;   BVM_SETLEADER           (pet recruitment)   RequirePrivileged
;   BVM_SETABILITYLEVEL     BVM_SETATTRIBUTE    RequirePrivileged
;   BVM_SETITEMHEALTH       (item brick)        RequirePrivileged
;   BVM_SETRESISTANCE       BVM_SETFACTIONRATING RequirePrivileged
;   BVM_SETACTORGENDER      (cosmetic griefing) RequirePrivileged
;   BVM_SETACTORBEARD       (cosmetic griefing) RequirePrivileged
;   BVM_SETACTORHAIR        (cosmetic griefing) RequirePrivileged
;   BVM_SETACTORFACE        (cosmetic griefing) RequirePrivileged
;   BVM_SETACTORCLOTHES     (cosmetic griefing) RequirePrivileged
;   BVM_REMOVEZONEINSTANCE  (admin-only)        RequirePrivileged
;
; Four sibling functions (SETACTORAISTATE / SETACTORTARGET / SETNAME /
; SETTAG) intentionally stay UNGATED -- shipped content scripts in
; data/Server Data/Scripts (AOE Damage Spell Template, /Assist chat
; command, marriage, Spawn_Test) call them from non-priv spawns and a
; full-priv gate would silently break them. Audit comments at each
; function record this and point at the follow-up.
;
; ScriptingCommands.bb can't be Included directly into a test build --
; it pulls in the entire actor / item / wire / scripting graph. Following
; the established ClampFloatTest.bb / ItemsTest.bb pattern, we replicate
; the gate semantics and the gate-relevant prologue of each function,
; then assert the gate behaviour.
;
; Replication shape: production has the helpers read SI fields via
;   SI = Object.ScriptInstance(hSI); If SI = Null Then Return False
;   If SI\Privileged <> 0 Then Return True
; We split SI's fields into individual globals (ScriptActive flag plus
; Privileged / AI / AIContext) so the test can configure script state
; without depending on the Object/Handle table or fighting GC over a
; transient ScriptInstance object. Gate decision logic is verbatim.
; A refactor that changes the gate decision must update both copies.

; --- Replicated gate state --------------------------------------------
; Production: Object.ScriptInstance(hSI). A truthy ScriptActive means
; "the lookup succeeded" -- i.e., there IS an executing script context.
; Setting ScriptActive=False reproduces "hSI is stale / freed".
Global ScriptActive = False
Global ScriptPrivileged = 0
Global ScriptAI = 0
Global ScriptAIContext = 0
Global ScriptName$ = "TestScript"

; Capture the last refusal so tests can assert the gate refusal path
; actually fired (not a silent no-op).
Global LastScriptLog$ = ""

Function BVM_ScriptLog(Message$)
	LastScriptLog$ = Message$
End Function

; Production: Function BVM_RequirePrivileged%() at ScriptingCommands.bb:132
Function BVM_RequirePrivileged%()
	If ScriptActive = False Then Return False
	If ScriptPrivileged <> 0 Then Return True
	BVM_ScriptLog("Privileged BVM call refused from non-privileged script: " + ScriptName)
	Return False
End Function

; Production: Function BVM_RequireSelfOrPrivileged%(Param1%) at ScriptingCommands.bb:148
Function BVM_RequireSelfOrPrivileged%(Param1%)
	If ScriptActive = False Then Return False
	If ScriptPrivileged <> 0 Then Return True
	If Param1% <> 0 And (Param1% = ScriptAI Or Param1% = ScriptAIContext) Then Return True
	BVM_ScriptLog("BVM call refused: target is neither script's actor nor context (" + ScriptName + ")")
	Return False
End Function

; --- Mock-mutation surface ---------------------------------------------
; Each newly-gated function in production performs a state mutation
; (gold transfer, attribute change, zone teardown, ...). Replicate just
; the gate prologue + a single observable side-effect for each, so the
; test asserts the gate actually short-circuits the function body.
Global MutationGold = 0
Global MutationMoney = 0
Global MutationXP = 0
Global MutationKillXP = 0
Global MutationSetAttr = 0
Global MutationChangeAttr = 0
Global MutationSetMaxAttr = 0
Global MutationChangeMaxAttr = 0
Global MutationSetReputation = 0
Global MutationSetLeader = 0
Global MutationSetAbilityLevel = 0
Global MutationSetItemHealth = 0
Global MutationSetResistance = 0
Global MutationSetActorAppearance = 0  ; shared counter for the 5 appearance setters
Global MutationSetActorGroup = 0
Global MutationRemoveZone = 0

Function MockBVM_CHANGEGOLD(Param1%, Param2%)
	If Not BVM_RequirePrivileged() Then Return
	MutationGold = MutationGold + Param2%
End Function

Function MockBVM_CHANGEMONEY(Param1%, Param2%)
	If Not BVM_RequirePrivileged() Then Return
	MutationMoney = MutationMoney + Param2%
End Function

Function MockBVM_GIVEXP(Param1%, Param2%, Param3%=0)
	If Not BVM_RequirePrivileged() Then Return
	MutationXP = MutationXP + Param2%
End Function

Function MockBVM_GIVEKILLXP(Param1%, Param2%)
	If Not BVM_RequirePrivileged() Then Return
	MutationKillXP = MutationKillXP + 1
End Function

Function MockBVM_SETATTRIBUTE(Param1%, Param2$, Param3%)
	If Not BVM_RequirePrivileged() Then Return
	MutationSetAttr = MutationSetAttr + Param3%
End Function

Function MockBVM_CHANGEATTRIBUTE(Param1%, Param2$, Param3%)
	If Not BVM_RequirePrivileged() Then Return
	MutationChangeAttr = MutationChangeAttr + Param3%
End Function

Function MockBVM_SETMAXATTRIBUTE(Param1%, Param2$, Param3%)
	If Not BVM_RequirePrivileged() Then Return
	MutationSetMaxAttr = MutationSetMaxAttr + Param3%
End Function

Function MockBVM_CHANGEMAXATTRIBUTE(Param1%, Param2$, Param3%)
	If Not BVM_RequirePrivileged() Then Return
	MutationChangeMaxAttr = MutationChangeMaxAttr + Param3%
End Function

Function MockBVM_SETREPUTATION(Param1%, Param2%)
	If Not BVM_RequirePrivileged() Then Return
	MutationSetReputation = MutationSetReputation + Param2%
End Function

Function MockBVM_SETLEADER(Param1%, Param2%)
	If Not BVM_RequirePrivileged() Then Return
	MutationSetLeader = MutationSetLeader + 1
End Function

Function MockBVM_SETABILITYLEVEL(Param1%, Param2$, Param3%)
	If Not BVM_RequirePrivileged() Then Return
	MutationSetAbilityLevel = MutationSetAbilityLevel + Param3%
End Function

Function MockBVM_SETITEMHEALTH(Param1%, Param2%)
	If Not BVM_RequirePrivileged() Then Return
	MutationSetItemHealth = MutationSetItemHealth + Param2%
End Function

Function MockBVM_SETRESISTANCE(Param1%, Param2$, Param3%)
	If Not BVM_RequirePrivileged() Then Return
	MutationSetResistance = MutationSetResistance + Param3%
End Function

; Shared appearance-setter mock -- threat model + gate shape are
; identical across GENDER / BEARD / HAIR / FACE / CLOTHES, so a single
; mock covers the contract. The production functions are 5 separate
; bodies (each writing a distinct field + broadcasting a distinct
; sub-code) and each gets its own gate; the test just proves the
; gate is wired.
Function MockBVM_SETACTORAPPEARANCE(Param1%, Param2%)
	If Not BVM_RequirePrivileged() Then Return
	MutationSetActorAppearance = MutationSetActorAppearance + 1
End Function

Function MockBVM_REMOVEZONEINSTANCE(Param1$, Instance%)
	If Not BVM_RequirePrivileged() Then Return
	MutationRemoveZone = MutationRemoveZone + 1
End Function

; SetActorGroup gate (PR #325). TeamID is the team / party / faction
; identifier consumed by chat routing and combat friendly-fire gating;
; flipping it via a clicker script lets a non-priv NPC reassign the
; clicker's team. Zero shipped content-script callers (verified via
; `grep -rni SetActorGroup data/`), so RequirePrivileged was clean to
; land without content rewrites. Same threat model + gate shape as the
; appearance cluster.
Function MockBVM_SETACTORGROUP(Param1%, Param2%)
	If Not BVM_RequirePrivileged() Then Return
	MutationSetActorGroup = MutationSetActorGroup + 1
End Function

; --- Test fixture helpers ----------------------------------------------
Function ResetMutationCounters()
	MutationGold = 0
	MutationMoney = 0
	MutationXP = 0
	MutationKillXP = 0
	MutationSetAttr = 0
	MutationChangeAttr = 0
	MutationSetMaxAttr = 0
	MutationChangeMaxAttr = 0
	MutationSetReputation = 0
	MutationSetLeader = 0
	MutationSetAbilityLevel = 0
	MutationSetItemHealth = 0
	MutationSetResistance = 0
	MutationSetActorAppearance = 0
	MutationSetActorGroup = 0
	MutationRemoveZone = 0
	LastScriptLog$ = ""
End Function

Function InstallScript(Privileged%, AI%, AIContext%)
	ScriptActive = True
	ScriptPrivileged = Privileged
	ScriptAI = AI
	ScriptAIContext = AIContext
End Function

Function ClearScript()
	ScriptActive = False
	ScriptPrivileged = 0
	ScriptAI = 0
	ScriptAIContext = 0
End Function

; ======================================================================
; Gate helper contract -- a non-priv script returns False; the refusal
; goes through BVM_ScriptLog so server operators have an audit trail.
; ======================================================================

Test testRequirePrivilegedAllowsPrivilegedScript()
	InstallScript(1, 0, 0)
	Assert(BVM_RequirePrivileged() = True)
End Test

Test testRequirePrivilegedRefusesNonPrivilegedScript()
	InstallScript(0, 0, 0)
	LastScriptLog$ = ""
	Assert(BVM_RequirePrivileged() = False)
	; Refusal must be audit-logged, not silently dropped.
	Assert(Len(LastScriptLog$) > 0)
End Test

Test testRequirePrivilegedFailsClosedOnMissingScript()
	; A stale/invalid script context must not be treated as privileged.
	; Closed-by-default is the entire point of the gate.
	ClearScript()
	Assert(BVM_RequirePrivileged() = False)
End Test

Test testRequireSelfOrPrivilegedAllowsPrivilegedScript()
	InstallScript(1, 0, 0)
	Assert(BVM_RequireSelfOrPrivileged(12345) = True)
End Test

Test testRequireSelfOrPrivilegedAllowsOwnAI()
	InstallScript(0, 777, 0)
	Assert(BVM_RequireSelfOrPrivileged(777) = True)
End Test

Test testRequireSelfOrPrivilegedAllowsOwnContext()
	InstallScript(0, 0, 888)
	Assert(BVM_RequireSelfOrPrivileged(888) = True)
End Test

Test testRequireSelfOrPrivilegedRefusesArbitraryTarget()
	InstallScript(0, 100, 200)
	LastScriptLog$ = ""
	Assert(BVM_RequireSelfOrPrivileged(999) = False)
	Assert(Len(LastScriptLog$) > 0)
End Test

Test testRequireSelfOrPrivilegedRejectsZeroTarget()
	; Param1 = 0 must not match SI\AI = 0 or SI\AIContext = 0 -- otherwise
	; a freshly-created script (both fields default 0) would be allowed
	; to target "actor 0" from any non-priv context.
	InstallScript(0, 0, 0)
	Assert(BVM_RequireSelfOrPrivileged(0) = False)
End Test

Test testRequireSelfOrPrivilegedFailsClosedOnMissingScript()
	ClearScript()
	Assert(BVM_RequireSelfOrPrivileged(0) = False)
	Assert(BVM_RequireSelfOrPrivileged(123) = False)
End Test

; ======================================================================
; Per-function gate enforcement -- the seven newly-gated functions must
; short-circuit when the calling script is non-privileged.
; ======================================================================

Test testChangeGoldGateBlocksNonPrivileged()
	InstallScript(0, 0, 0)
	ResetMutationCounters()
	MockBVM_CHANGEGOLD(0, 1000000)
	; State unchanged: the gate ran *before* the gold mutation.
	Assert(MutationGold = 0)
End Test

Test testChangeGoldGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_CHANGEGOLD(0, 1000000)
	Assert(MutationGold = 1000000)
End Test

Test testChangeMoneyGateBlocksNonPrivileged()
	InstallScript(0, 0, 0)
	ResetMutationCounters()
	MockBVM_CHANGEMONEY(0, -500000)
	Assert(MutationMoney = 0)
End Test

Test testChangeMoneyGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_CHANGEMONEY(0, -500000)
	Assert(MutationMoney = -500000)
End Test

Test testGiveXPGateBlocksNonPrivileged()
	InstallScript(0, 0, 0)
	ResetMutationCounters()
	MockBVM_GIVEXP(0, 999999999)
	Assert(MutationXP = 0)
End Test

Test testGiveXPGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_GIVEXP(0, 100)
	Assert(MutationXP = 100)
End Test

Test testGiveKillXPGateBlocksNonPrivileged()
	InstallScript(0, 0, 0)
	ResetMutationCounters()
	MockBVM_GIVEKILLXP(0, 0)
	Assert(MutationKillXP = 0)
End Test

Test testGiveKillXPGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_GIVEKILLXP(0, 0)
	Assert(MutationKillXP = 1)
End Test

Test testSetAttributeGateBlocksArbitraryTarget()
	InstallScript(0, 100, 0)
	ResetMutationCounters()
	MockBVM_SETATTRIBUTE(999, "Health", 0)
	Assert(MutationSetAttr = 0)
End Test

; THE EXPLOIT this gate exists to block. For Examine/Trade/RightClick/
; ItemScript spawns, ThreadScript("...", "Examine", Handle(clicker),
; Handle(NPC)) makes `SI\AI = Handle(clicker)`. A self-or-priv gate on
; Param1=clicker_handle would match SI\AI and let SetAttribute(clicker,
; "Health", 0) reach KillActor(...) on the clicker. The full-priv gate
; refuses regardless of how the target handle relates to SI\AI.
Test testSetAttributeGateBlocksKillingOwnAITarget()
	; Non-priv clicker-driven script: SI\AI = clicker handle (777 here).
	; The exploit call passes Param1 = 777 == SI\AI, which would defeat
	; a self-or-priv gate. The full-priv gate must still refuse.
	InstallScript(0, 777, 200)
	ResetMutationCounters()
	MockBVM_SETATTRIBUTE(777, "Health", 0)
	Assert(MutationSetAttr = 0)
End Test

Test testSetAttributeGateBlocksKillingOwnContextTarget()
	; Same exploit, but a script spawn shape where the lethal target
	; happens to be SI\AIContext. The gate must still refuse.
	InstallScript(0, 100, 500)
	ResetMutationCounters()
	MockBVM_SETATTRIBUTE(500, "Health", 0)
	Assert(MutationSetAttr = 0)
End Test

Test testSetAttributeGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_SETATTRIBUTE(999, "Health", 42)
	Assert(MutationSetAttr = 42)
End Test

Test testChangeAttributeGateBlocksArbitraryTarget()
	; Same exploit shape via ChangeAttribute(target, "Health", -big%).
	InstallScript(0, 100, 0)
	ResetMutationCounters()
	MockBVM_CHANGEATTRIBUTE(999, "Health", -1000000)
	Assert(MutationChangeAttr = 0)
End Test

Test testChangeAttributeGateBlocksKillingOwnAITarget()
	; Mirror of the SetAttribute clicker-bypass test on the Change path.
	InstallScript(0, 777, 200)
	ResetMutationCounters()
	MockBVM_CHANGEATTRIBUTE(777, "Health", -1000000)
	Assert(MutationChangeAttr = 0)
End Test

Test testChangeAttributeGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_CHANGEATTRIBUTE(999, "Health", -7)
	Assert(MutationChangeAttr = -7)
End Test

; ======================================================================
; SetMaxAttribute / ChangeMaxAttribute sibling-asymmetry gates.
;
; The SET/CHANGE pair above is gated because their HealthStat branch
; falls through to KillActor(...) -- a clicker one-shot kill exploit.
; The SET/CHANGE-MAX pair sits next door and was left ungated, but it's
; still a brick vector:
;
;   SetMaxAttribute(player, "Health", 1)  -> max HP = 1 forever, next
;                                            damage tick kills them
;   SetMaxAttribute(player, "Speed",  0)  -> player can't move
;   SetMaxAttribute(player, "Energy", 0)  -> player can't cast spells
;
; Same clicker-bypass mechanics as SET/CHANGE: ThreadScript spawn for
; Examine/Trade/RightClick/ItemScript makes SI\AI = Handle(clicker), so
; the gate has to be RequirePrivileged (not SelfOrPrivileged).
; ======================================================================

Test testSetMaxAttributeGateBlocksArbitraryTarget()
	InstallScript(0, 100, 0)
	ResetMutationCounters()
	MockBVM_SETMAXATTRIBUTE(999, "Health", 1)
	Assert(MutationSetMaxAttr = 0)
End Test

Test testSetMaxAttributeGateBlocksBrickingOwnAITarget()
	; The SI\AI = clicker shape -- a self-or-priv gate would incorrectly
	; match Param1 = 777 against SI\AI = 777 and let the brick through.
	; Full-priv must refuse.
	InstallScript(0, 777, 200)
	ResetMutationCounters()
	MockBVM_SETMAXATTRIBUTE(777, "Health", 1)
	Assert(MutationSetMaxAttr = 0)
End Test

Test testSetMaxAttributeGateBlocksBrickingOwnContextTarget()
	InstallScript(0, 100, 500)
	ResetMutationCounters()
	MockBVM_SETMAXATTRIBUTE(500, "Speed", 0)
	Assert(MutationSetMaxAttr = 0)
End Test

Test testSetMaxAttributeGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_SETMAXATTRIBUTE(999, "Health", 200)
	Assert(MutationSetMaxAttr = 200)
End Test

Test testChangeMaxAttributeGateBlocksArbitraryTarget()
	; ChangeMaxAttribute(target, "Health", -big%) drives Maximum[Health]
	; toward zero -- same brick vector via the relative-mutation path.
	InstallScript(0, 100, 0)
	ResetMutationCounters()
	MockBVM_CHANGEMAXATTRIBUTE(999, "Health", -1000000)
	Assert(MutationChangeMaxAttr = 0)
End Test

Test testChangeMaxAttributeGateBlocksBrickingOwnAITarget()
	InstallScript(0, 777, 200)
	ResetMutationCounters()
	MockBVM_CHANGEMAXATTRIBUTE(777, "Health", -1000000)
	Assert(MutationChangeMaxAttr = 0)
End Test

Test testChangeMaxAttributeGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_CHANGEMAXATTRIBUTE(999, "Health", 50)
	Assert(MutationChangeMaxAttr = 50)
End Test

; ======================================================================
; Faction / leader / ability / item-health -- the next slice of the
; brick-vector sweep flagged by PR #300's reviewer. Each one is a
; non-priv-script-reachable mutation that bricks gameplay state.
; ======================================================================

; SetReputation -- faction-interaction key. Brick: SetReputation(clicker,
; -10000) locks the player out of every reputation-gated vendor / quest
; / zone.
Test testSetReputationGateBlocksArbitraryTarget()
	InstallScript(0, 100, 0)
	ResetMutationCounters()
	MockBVM_SETREPUTATION(999, -10000)
	Assert(MutationSetReputation = 0)
End Test

Test testSetReputationGateBlocksBrickingOwnAITarget()
	; The exact clicker shape: SI\AI = clicker, Param1 = clicker.
	; Full-priv must refuse where self-or-priv would not.
	InstallScript(0, 777, 200)
	ResetMutationCounters()
	MockBVM_SETREPUTATION(777, -10000)
	Assert(MutationSetReputation = 0)
End Test

Test testSetReputationGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_SETREPUTATION(999, 50)
	Assert(MutationSetReputation = 50)
End Test

; SetLeader -- pet recruitment. Brick: SetLeader(SomeWorldGuard,
; clicker) recruits world NPCs as private pets. Production function
; restricts Param1 to NPCs (Actor\RNID = -1), so the player-as-pet
; shape is impossible, but the world-NPC-as-pet shape is the gap.
Test testSetLeaderGateBlocksNonPrivileged()
	InstallScript(0, 100, 0)
	ResetMutationCounters()
	MockBVM_SETLEADER(999, 777)
	Assert(MutationSetLeader = 0)
End Test

Test testSetLeaderGateBlocksOwnAILeader()
	; Clicker-shape: SI\AI = clicker handle (777), Param2 = clicker
	; (the would-be new leader). Full-priv refuses.
	InstallScript(0, 777, 200)
	ResetMutationCounters()
	MockBVM_SETLEADER(999, 777)
	Assert(MutationSetLeader = 0)
End Test

Test testSetLeaderGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_SETLEADER(999, 777)
	Assert(MutationSetLeader = 1)
End Test

; SetAbilityLevel -- combat ability scaling. Brick: SetAbilityLevel(
; clicker, "<spell>", 0) zeros out the chosen ability; iteration over
; the spell list bricks the player's entire combat toolkit.
Test testSetAbilityLevelGateBlocksArbitraryTarget()
	InstallScript(0, 100, 0)
	ResetMutationCounters()
	MockBVM_SETABILITYLEVEL(999, "Fireball", 0)
	Assert(MutationSetAbilityLevel = 0)
End Test

Test testSetAbilityLevelGateBlocksBrickingOwnAITarget()
	InstallScript(0, 777, 200)
	ResetMutationCounters()
	MockBVM_SETABILITYLEVEL(777, "Fireball", 0)
	Assert(MutationSetAbilityLevel = 0)
End Test

Test testSetAbilityLevelGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_SETABILITYLEVEL(999, "Fireball", 5)
	Assert(MutationSetAbilityLevel = 5)
End Test

; SetItemHealth -- durability. Brick: iterate clicker's
; Inventory\Items[] and zero each ItemHealth, gutting all gear in one
; click. Param1 is an ItemInstance handle (not an ActorInstance), so
; the self-or-priv shortcut doesn't apply -- there is no SI\AI for an
; item. RequirePrivileged is the only sensible gate; tests just cover
; the priv vs. non-priv contract.
Test testSetItemHealthGateBlocksNonPrivileged()
	InstallScript(0, 0, 0)
	ResetMutationCounters()
	MockBVM_SETITEMHEALTH(12345, 0)
	Assert(MutationSetItemHealth = 0)
End Test

Test testSetItemHealthGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_SETITEMHEALTH(12345, 100)
	Assert(MutationSetItemHealth = 100)
End Test

; SetResistance -- damage-type resistance; consumed by the combat
; damage formula the same way FactionRatings[] is. Brick path is
; symmetric to SETFACTIONRATING (already gated): negative value =
; catastrophic damage taken; >100 value = invulnerable in PvE.
Test testSetResistanceGateBlocksArbitraryTarget()
	InstallScript(0, 100, 0)
	ResetMutationCounters()
	MockBVM_SETRESISTANCE(999, "Fire", -100)
	Assert(MutationSetResistance = 0)
End Test

Test testSetResistanceGateBlocksBrickingOwnAITarget()
	; Clicker-shape: SI\AI = clicker (777), Param1 = clicker.
	InstallScript(0, 777, 200)
	ResetMutationCounters()
	MockBVM_SETRESISTANCE(777, "Fire", -100)
	Assert(MutationSetResistance = 0)
End Test

Test testSetResistanceGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_SETRESISTANCE(999, "Fire", 50)
	Assert(MutationSetResistance = 50)
End Test

; ======================================================================
; Appearance-cluster cosmetic-griefing gates. The 5 production
; functions (SETACTORGENDER / BEARD / HAIR / FACE / CLOTHES) all use
; the same gate shape (full-priv). The SETACTORAISTATE / SETACTORTARGET
; / SETNAME / SETTAG functions are intentionally UNGATED -- see test
; file header for the content-script-callers explanation.
;
; Shared mock covers the contract; each production gate is verified by
; inspection (audit-comment template is identical).
; ======================================================================

Test testSetActorAppearanceGateBlocksArbitrary()
	InstallScript(0, 100, 0)
	ResetMutationCounters()
	MockBVM_SETACTORAPPEARANCE(999, 2)
	Assert(MutationSetActorAppearance = 0)
End Test

Test testSetActorAppearanceGateBlocksOwnAITarget()
	; Clicker self-grief shape: SI\AI = clicker, Param1 = clicker.
	InstallScript(0, 777, 200)
	ResetMutationCounters()
	MockBVM_SETACTORAPPEARANCE(777, 2)
	Assert(MutationSetActorAppearance = 0)
End Test

Test testSetActorAppearanceGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_SETACTORAPPEARANCE(999, 3)
	Assert(MutationSetActorAppearance = 1)
End Test

Test testRemoveZoneInstanceGateBlocksNonPrivileged()
	InstallScript(0, 0, 0)
	ResetMutationCounters()
	MockBVM_REMOVEZONEINSTANCE("MainZone", 5)
	Assert(MutationRemoveZone = 0)
End Test

Test testRemoveZoneInstanceGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_REMOVEZONEINSTANCE("MainZone", 5)
	Assert(MutationRemoveZone = 1)
End Test

; ======================================================================
; SetActorGroup gate (PR #325). TeamID drives chat-routing (/g guild
; chat keys on `A2\TeamID = AI\TeamID`) and friendly-fire / aggression
; gating. Flipping TeamID via a non-priv clicker script lets an NPC
; reassign the clicker's team -- griefing + chat-exfiltration vector.
; Zero shipped content-script callers (verified at recon time), so
; RequirePrivileged was clean to land. Sibling-asymmetry with the 12
; already-gated mutators in this file.
; ======================================================================

Test testSetActorGroupGateBlocksNonPrivileged()
	InstallScript(0, 0, 0)
	ResetMutationCounters()
	MockBVM_SETACTORGROUP(999, 5)
	Assert(MutationSetActorGroup = 0)
End Test

Test testSetActorGroupGateBlocksOwnAITarget()
	; The clicker shape: Examine/Trade/RightClick/ItemScript spawn
	; sets SI\AI = Handle(clicker). A self-or-priv gate would
	; incorrectly let Param1 = clicker_handle through; this test
	; confirms full RequirePrivileged blocks even that case.
	InstallScript(0, 777, 0)
	ResetMutationCounters()
	MockBVM_SETACTORGROUP(777, 5)
	Assert(MutationSetActorGroup = 0)
End Test

Test testSetActorGroupGatePassesForPrivileged()
	InstallScript(1, 0, 0)
	ResetMutationCounters()
	MockBVM_SETACTORGROUP(999, 5)
	Assert(MutationSetActorGroup = 1)
End Test
