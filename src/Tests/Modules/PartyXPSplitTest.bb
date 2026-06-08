Strict
EnableGC

; Tests for the party-XP split conservation invariant in GameServer.bb's
; GiveXP (the IgnoreParty = 0 share block).
;
; A kill that awards XP to a partied player splits it among party members
; IN THE SAME AREA. The per-member share uses the in-area count:
;     PartyXP = XP / Members          ; Members = party members in A's area
; each of the (Members-1) OTHER in-area members receives PartyXP, and the
; recipient A keeps the remainder. The bug: the remainder used the TOTAL
; party count (Party\Members) instead of the in-area Members:
;     XP = PartyXP + (XP Mod Party\Members)   ; WRONG divisor
; so the distributed total only equalled the award when the whole party was
; in one zone (Members == Party\Members). Split across zones, it fabricated
; or dropped XP on every shared kill. Fix: remainder over `Members`.
;
; GameServer.bb can't be Included into a test build (pulls the world/network
; surface), so the split arithmetic is replicated here per the established
; ClampFloatTest convention. The test computes the FULL distribution and
; asserts conservation against the award; it independently models the buggy
; formula to demonstrate the non-conservation the fix removes. `Members >= 1`
; always (the production guard requires Members > 0 before this code runs),
; so there is no divide-by-zero.

; Recipient A's keep-share under the FIXED formula (remainder over Members).
Function RecipientShareFixed(XP, Members)
	Return (XP / Members) + (XP Mod Members)
End Function

; Total XP actually distributed under the FIX: (Members-1) other in-area
; members each get XP/Members, plus the recipient's keep-share.
Function TotalDistributedFixed(XP, Members)
	Return (Members - 1) * (XP / Members) + RecipientShareFixed(XP, Members)
End Function

; Total distributed under the BUG: remainder taken over the total party
; count (PartyMembers) rather than the in-area count (Members).
Function TotalDistributedBuggy(XP, Members, PartyMembers)
	Local per = XP / Members
	Local recipient = per + (XP Mod PartyMembers)
	Return (Members - 1) * per + recipient
End Function


; THE invariant: the fix conserves XP exactly for any in-area member count
; and any award (including ones not divisible by the member count).
Test testFixedConservesXP()
	Assert(TotalDistributedFixed(1, 1) = 1)
	Assert(TotalDistributedFixed(7, 2) = 7)
	Assert(TotalDistributedFixed(10, 2) = 10)
	Assert(TotalDistributedFixed(10, 3) = 10)
	Assert(TotalDistributedFixed(100, 3) = 100)
	Assert(TotalDistributedFixed(999, 5) = 999)
	Assert(TotalDistributedFixed(13, 7) = 13)
	Assert(TotalDistributedFixed(100, 5) = 100)
End Test

; Solo-in-zone member of a multi-zone party keeps the entire award.
Test testSoloInZoneKeepsAllXP()
	Assert(TotalDistributedFixed(10, 1) = 10)
	Assert(RecipientShareFixed(10, 1) = 10)
	Assert(RecipientShareFixed(999, 1) = 999)
End Test

; The bug: party split across zones (Members < PartyMembers) fabricates XP.
; XP=10, in-area Members=2, total Party\Members=3 -> 5 + (10 Mod 3)=6, total 11.
Test testBuggyFabricatesXPWhenPartySpansZones()
	Assert(TotalDistributedBuggy(10, 2, 3) = 11)   ; conjures 1 XP
	Assert(TotalDistributedBuggy(10, 2, 3) <> 10)
End Test

; The bug can also DROP XP for other divisor relationships.
; XP=10, Members=3, PartyMembers=2 -> per=3, recipient=3+(10 Mod 2)=3, others=6, total 9.
Test testBuggyDropsXPForOtherSplits()
	Assert(TotalDistributedBuggy(10, 3, 2) = 9)
	Assert(TotalDistributedBuggy(10, 3, 2) <> 10)
End Test

; The fix is a no-op in the common case: whole party in one zone
; (Members == Party\Members) -> buggy and fixed agree, both conserve.
Test testBuggyEqualsFixedWhenWholePartyInZone()
	Assert(TotalDistributedBuggy(7, 2, 2)   = 7)
	Assert(TotalDistributedBuggy(10, 3, 3)  = 10)
	Assert(TotalDistributedBuggy(100, 5, 5) = 100)
	Assert(TotalDistributedBuggy(10, 3, 3)  = TotalDistributedFixed(10, 3))
	Assert(TotalDistributedBuggy(100, 5, 5) = TotalDistributedFixed(100, 5))
End Test
