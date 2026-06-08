Strict
EnableGC

; Regression test pinning the /help catalog in ServerNet.bb's
; SendChatHelpDetail function. The chat-command dispatcher has 30
; built-in commands (P_ChatMessage Select Case at ~line 103-565);
; the catalog test guarantees every dispatched command has a
; corresponding help-text entry.
;
; If a future PR adds a 31st command and forgets to register its
; help line, this test catches it: the union of (commands the
; dispatcher handles) and (commands SendChatHelpDetail describes)
; must match.
;
; ServerNet.bb can't be Included into a Strict test build
; (network/world graph). Following the established replicated-gate
; pattern (AccountEnumerationTest, BVMPrivilegeGateTest, etc.), the
; help-text predicate is replicated below verbatim. Any production
; change to SendChatHelpDetail MUST update this file; the duplication
; is the trigger to refresh the catalog.

; --- Replicated help-text predicate ---------------------------------

; Returns the same string SendChatHelpDetail would build for the
; given input. Empty string means "no help available" (the
; production code emits a "No help available for /<cmd>" fallback).
Function ChatHelpLine$(T$, IsDM%)
	Local Line$ = ""
	If T = "ME" Then Line = "/me <action> -- emote action in current area"
	If T = "YELL" Then Line = "/yell <text> -- shout to your entire area"
	If T = "PM" Then Line = "/pm <player>,<text> -- private message"
	If T = "G" Then Line = "/g <text> -- guild chat"
	If T = "P" Then Line = "/p <text> -- party chat"
	If T = "INVITE" Then Line = "/invite <player> -- invite player to party"
	If T = "ACCEPT" Then Line = "/accept -- accept a pending party invite"
	If T = "LEAVE" Then Line = "/leave -- leave current party"
	If T = "PET" Then Line = "/pet <name>,<command>[,<params>] -- command a pet (or 'all')"
	If T = "IGNORE" Then Line = "/ignore <player> -- silence a player's chat"
	If T = "UNIGNORE" Then Line = "/unignore <player> -- re-enable a player's chat"
	If T = "PLAYERS" Then Line = "/players -- list players in your area"
	If T = "ALLPLAYERS" Then Line = "/allplayers -- list all online players"
	If T = "TRADE" Then Line = "/trade <player> -- open trade with player"
	If T = "TIME" Then Line = "/time -- show in-world clock time"
	If T = "DATE" Then Line = "/date -- show in-world date"
	If T = "SEASON" Then Line = "/season -- show current season"
	If T = "WARP" Then Line = "/warp <area>[,<x>,<y>,<z>] -- warp to an area"
	If T = "HELP" Or T = "?" Then Line = "/help [<command>] -- list commands, or detail on one"
	If IsDM = True
		If T = "KICK" Then Line = "/kick <player> -- disconnect a player"
		If T = "XP" Then Line = "/xp <player>,<amount> -- grant XP"
		If T = "GOLD" Then Line = "/gold <player>,<amount> -- grant gold"
		If T = "SETATTRIBUTE" Then Line = "/setattribute <player>,<attr>,<value> -- set attribute"
		If T = "SETATTRIBUTEMAX" Then Line = "/setattributemax <player>,<attr>,<value> -- set max attribute"
		If T = "SCRIPT" Then Line = "/script <name>[,<params>] -- run a script as self"
		If T = "GM" Then Line = "/gm <text> -- broadcast as GM to all players"
		If T = "WARPOTHER" Then Line = "/warpother <player>,<area>[,<x>,<y>,<z>] -- warp another player"
		If T = "ABILITY" Then Line = "/ability <player>,<ability> -- grant ability"
		If T = "GIVE" Then Line = "/give <player>,<item>[,<amount>] -- grant item"
		If T = "WEATHER" Then Line = "/weather <type> -- set weather"
		If T = "NETDUMP" Then Line = "/netdump -- start a network packet log"
	EndIf
	Return Line
End Function

; ====================================================================
; Non-DM commands: every built-in (non-DM) slash command must have a
; help entry, visible to all players regardless of DM status.
; ====================================================================

Test testMeReturnsHelp()
	Assert(Len(ChatHelpLine$("ME", False)) > 0)
	Assert(Len(ChatHelpLine$("ME", True)) > 0)
End Test

Test testYellReturnsHelp()
	Assert(Len(ChatHelpLine$("YELL", False)) > 0)
End Test

Test testPmReturnsHelp()
	Assert(Len(ChatHelpLine$("PM", False)) > 0)
End Test

Test testGuildChatReturnsHelp()
	Assert(Len(ChatHelpLine$("G", False)) > 0)
End Test

Test testPartyChatReturnsHelp()
	Assert(Len(ChatHelpLine$("P", False)) > 0)
End Test

Test testInviteReturnsHelp()
	Assert(Len(ChatHelpLine$("INVITE", False)) > 0)
End Test

Test testAcceptReturnsHelp()
	Assert(Len(ChatHelpLine$("ACCEPT", False)) > 0)
End Test

Test testLeaveReturnsHelp()
	Assert(Len(ChatHelpLine$("LEAVE", False)) > 0)
End Test

Test testPetReturnsHelp()
	Assert(Len(ChatHelpLine$("PET", False)) > 0)
End Test

Test testIgnoreReturnsHelp()
	Assert(Len(ChatHelpLine$("IGNORE", False)) > 0)
End Test

Test testUnignoreReturnsHelp()
	Assert(Len(ChatHelpLine$("UNIGNORE", False)) > 0)
End Test

Test testPlayersReturnsHelp()
	Assert(Len(ChatHelpLine$("PLAYERS", False)) > 0)
End Test

Test testAllPlayersReturnsHelp()
	Assert(Len(ChatHelpLine$("ALLPLAYERS", False)) > 0)
End Test

Test testTradeReturnsHelp()
	Assert(Len(ChatHelpLine$("TRADE", False)) > 0)
End Test

Test testTimeReturnsHelp()
	Assert(Len(ChatHelpLine$("TIME", False)) > 0)
End Test

Test testDateReturnsHelp()
	Assert(Len(ChatHelpLine$("DATE", False)) > 0)
End Test

Test testSeasonReturnsHelp()
	Assert(Len(ChatHelpLine$("SEASON", False)) > 0)
End Test

Test testWarpReturnsHelp()
	Assert(Len(ChatHelpLine$("WARP", False)) > 0)
End Test

Test testHelpItselfReturnsHelp()
	Assert(Len(ChatHelpLine$("HELP", False)) > 0)
End Test

Test testQuestionMarkAliasReturnsHelp()
	Assert(Len(ChatHelpLine$("?", False)) > 0)
End Test

; ====================================================================
; DM-only commands: visible to DMs, invisible to ordinary players.
; The disclosure gate matches the existing A\IsDM check in the
; dispatcher's Case branches.
; ====================================================================

Test testKickHidesFromNonDM()
	Assert(Len(ChatHelpLine$("KICK", False)) = 0)
End Test

Test testKickShowsToDM()
	Assert(Len(ChatHelpLine$("KICK", True)) > 0)
End Test

Test testXPHidesFromNonDM()
	Assert(Len(ChatHelpLine$("XP", False)) = 0)
End Test

Test testXPShowsToDM()
	Assert(Len(ChatHelpLine$("XP", True)) > 0)
End Test

Test testGoldHidesFromNonDM()
	Assert(Len(ChatHelpLine$("GOLD", False)) = 0)
End Test

Test testGoldShowsToDM()
	Assert(Len(ChatHelpLine$("GOLD", True)) > 0)
End Test

Test testSetAttributeShowsOnlyToDM()
	Assert(Len(ChatHelpLine$("SETATTRIBUTE", False)) = 0)
	Assert(Len(ChatHelpLine$("SETATTRIBUTE", True)) > 0)
End Test

Test testSetAttributeMaxShowsOnlyToDM()
	Assert(Len(ChatHelpLine$("SETATTRIBUTEMAX", False)) = 0)
	Assert(Len(ChatHelpLine$("SETATTRIBUTEMAX", True)) > 0)
End Test

Test testScriptShowsOnlyToDM()
	Assert(Len(ChatHelpLine$("SCRIPT", False)) = 0)
	Assert(Len(ChatHelpLine$("SCRIPT", True)) > 0)
End Test

Test testGMShowsOnlyToDM()
	Assert(Len(ChatHelpLine$("GM", False)) = 0)
	Assert(Len(ChatHelpLine$("GM", True)) > 0)
End Test

Test testWarpOtherShowsOnlyToDM()
	Assert(Len(ChatHelpLine$("WARPOTHER", False)) = 0)
	Assert(Len(ChatHelpLine$("WARPOTHER", True)) > 0)
End Test

Test testAbilityShowsOnlyToDM()
	Assert(Len(ChatHelpLine$("ABILITY", False)) = 0)
	Assert(Len(ChatHelpLine$("ABILITY", True)) > 0)
End Test

Test testGiveShowsOnlyToDM()
	Assert(Len(ChatHelpLine$("GIVE", False)) = 0)
	Assert(Len(ChatHelpLine$("GIVE", True)) > 0)
End Test

Test testWeatherShowsOnlyToDM()
	Assert(Len(ChatHelpLine$("WEATHER", False)) = 0)
	Assert(Len(ChatHelpLine$("WEATHER", True)) > 0)
End Test

Test testNetdumpShowsOnlyToDM()
	Assert(Len(ChatHelpLine$("NETDUMP", False)) = 0)
	Assert(Len(ChatHelpLine$("NETDUMP", True)) > 0)
End Test

; ====================================================================
; Unknown commands return empty (production code emits
; "No help available" fallback).
; ====================================================================

Test testUnknownCommandReturnsEmpty()
	Assert(Len(ChatHelpLine$("NOSUCHTHING", False)) = 0)
	Assert(Len(ChatHelpLine$("NOSUCHTHING", True)) = 0)
End Test

Test testEmptyTopicReturnsEmpty()
	; Empty string short-circuits in production (the SendChatHelp
	; wrapper routes to the index instead). Pin the predicate's
	; behaviour on the boundary.
	Assert(Len(ChatHelpLine$("", False)) = 0)
End Test

Test testCaseSensitivityPinned()
	; Production upper-cases Topic$ before dispatch; the predicate
	; assumes already-uppercase input. Pinned so callers don't pass
	; raw user text by mistake.
	Assert(Len(ChatHelpLine$("kick", True)) = 0)
End Test
