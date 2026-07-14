; =============================================================================
; Loom/InterfaceLayout.bb -- Interface (HUD) layout editing (GUE "Interface"
; tab parity)
; =============================================================================
;
; The data lives in the shared Interface.bb module (included by Server /
; Client / GUE / Loom alike): a fixed roster of InterfaceComponent instances
; -- Chat / ChatEntry / 40 AttributeDisplays / BuffsArea / Radar / Compass /
; InventoryWindow / InventoryDrop / InventoryEat / InventoryGold / 46
; InventoryButtons -- persisted to Data\Game Data\Interface.dat via
; SaveInterfaceSettings (atomic SafeWriteOpen/Commit). Each component carries
; X / Y / Width / Height (fraction 0..1 of screen), Alpha (0..1) and R / G / B
; (bytes); Chat additionally carries a Texture (Short, 65535 = none).
;
; Loom loads through the exact same LoadInterfaceSettings GUE runs at boot
; (GUE.bb:168) and saves through the exact same SaveInterfaceSettings GUE's
; "Save interface layout" button fires (GUE.bb:4042) -- so the two editors
; cannot drift in how they read or write the format. The CLIENT reads the
; same Interface.dat for its live HUD, so the on-disk format is load-bearing
; well beyond the editor.
;
; This module is the write path only. It's non-Strict on purpose: two of the
; component rosters are Dim'd global arrays (AttributeDisplays / InventoryButtons)
; and BlitzForge Strict cannot write to a Dim'd global array from inside a
; Method (the "Dim-write trap" -- see docs/loom/architecture.md "Known
; BlitzForge gotchas"). The Strict Composer routes every "interface" field
; commit through LoomIface_WriteField below, the same shape Seasons.bb uses
; for LoomEnv_WriteField and Settings.bb uses for the LoomCfg_* setters.
;
; Dirty flag: InterfaceSaved -- shared with GUE (GUE.bb declares it next to
; SpellsSaved; Loom.bb redeclares the same set). Composer::markDirtyForKind
; ("interface") flips it False; commitSaveForKind / LoomIface_DiscardReload
; restore it True. InterfaceSaved participates in GUE's menuSaveAll + exit-
; prompt (GUE.bb:9739/9764/10713), so the deferred model is correct here --
; NOT immediate-write like the Media tab.
;
; fieldId grammar (parsed by LoomIface_Field / LoomIface_CompKey): the field
; token is everything before the FIRST underscore; the component key is
; everything after it. The component key may itself contain underscores:
;   x_chat        -> field "x",   compKey "chat"
;   a_attr_5      -> field "a",   compKey "attr_5"    (AttributeDisplays(5))
;   tex_chat      -> field "tex", compKey "chat"      (Chat texture)
;   w_invbtn_12   -> field "w",   compKey "invbtn_12" (InventoryButtons(12))


; -----------------------------------------------------------------------------
; LoomIface_Field -- the field token (before the first underscore) of a
; fieldId. "x" / "y" / "w" / "h" / "r" / "g" / "b" / "a" / "tex".
; -----------------------------------------------------------------------------
Function LoomIface_Field$(fieldId$)
	p = Instr(fieldId$, "_")
	If p = 0 Then Return fieldId$
	Return Left$(fieldId$, p - 1)
End Function


; -----------------------------------------------------------------------------
; LoomIface_CompKey -- the component key (everything after the first
; underscore) of a fieldId. "chat" / "attr_5" / "invbtn_12" / ...
; -----------------------------------------------------------------------------
Function LoomIface_CompKey$(fieldId$)
	p = Instr(fieldId$, "_")
	If p = 0 Then Return ""
	Return Mid$(fieldId$, p + 1)
End Function


; -----------------------------------------------------------------------------
; LoomIface_Resolve -- map a component key to its InterfaceComponent instance.
; Returns Null for an unknown key or an out-of-range array index (the caller
; drops the write). Reading the Dim'd array slots here (non-Strict) is what
; lets the Strict Composer address AttributeDisplays / InventoryButtons at all.
; Index ranges mirror the LoadInterfaceSettings roster exactly: 40 attribute
; displays (0..39) and Slots_Inventory+1 inventory buttons (0..Slots_Inventory).
; -----------------------------------------------------------------------------
Function LoomIface_Resolve.InterfaceComponent(compKey$)
	If compKey$ = "chat"       Then Return Chat
	If compKey$ = "chatentry"  Then Return ChatEntry
	If compKey$ = "buffs"      Then Return BuffsArea
	If compKey$ = "radar"      Then Return Radar
	If compKey$ = "compass"    Then Return Compass
	If compKey$ = "invwindow"  Then Return InventoryWindow
	If compKey$ = "invdrop"    Then Return InventoryDrop
	If compKey$ = "inveat"     Then Return InventoryEat
	If compKey$ = "invgold"    Then Return InventoryGold
	If Left$(compKey$, 5) = "attr_"
		i = Int(Mid$(compKey$, 6))
		If i >= 0 And i <= 39 Then Return AttributeDisplays(i)
		Return Null
	EndIf
	If Left$(compKey$, 7) = "invbtn_"
		i = Int(Mid$(compKey$, 8))
		If i >= 0 And i <= Slots_Inventory Then Return InventoryButtons(i)
		Return Null
	EndIf
	Return Null
End Function


; -----------------------------------------------------------------------------
; LoomIface_WriteField -- the full "interface" write dispatch. Called from
; Composer::writeField (Strict) with the committed edit-buffer string. Every
; field clamps through Loom_ParseIntClamped / Loom_ParseFloatClamped with the
; SAME ranges GUE's spinners/sliders enforce (cited per field); garbage input
; falls back to the stored value.
;
; Percent <-> fraction: GUE stores X/Y/W/H as a 0..1 fraction of screen but
; edits them as a 0..100 percentage (spinner min 0, max 100, step 0.1 --
; GUE.bb:1900-1903), dividing by 100 on commit (GUE.bb:4061/4065/4069/4073).
; Loom shows the same percentage and divides by 100 here.
;
; Alpha: GUE's slider is an integer 0..255 (GUE.bb:1911) stored as a 0..1
; fraction, divided by 255 on commit (GUE.bb:4086). R/G/B: integer sliders
; 0..255 stored as bytes (GUE.bb:1908-1910). Texture: GUE's chooser assigns a
; texture ID stored as a Short; 65535 = none (GUE.bb:4092/4097).
; -----------------------------------------------------------------------------
Function LoomIface_WriteField(fieldId$, value$)
	fld$ = LoomIface_Field(fieldId$)
	key$ = LoomIface_CompKey(fieldId$)
	IC.InterfaceComponent = LoomIface_Resolve(key$)
	If IC = Null
		WriteLog(LoomLog, "Interface: write dropped -- unknown component in " + fieldId$)
		Return
	EndIf

	If fld$ = "x"
		IC\X# = Loom_ParseFloatClamped(value$, IC\X# * 100.0, 0.0, 100.0) / 100.0
		Return
	EndIf
	If fld$ = "y"
		IC\Y# = Loom_ParseFloatClamped(value$, IC\Y# * 100.0, 0.0, 100.0) / 100.0
		Return
	EndIf
	If fld$ = "w"
		IC\Width# = Loom_ParseFloatClamped(value$, IC\Width# * 100.0, 0.0, 100.0) / 100.0
		Return
	EndIf
	If fld$ = "h"
		IC\Height# = Loom_ParseFloatClamped(value$, IC\Height# * 100.0, 0.0, 100.0) / 100.0
		Return
	EndIf
	If fld$ = "r"
		IC\R = Loom_ParseIntClamped(value$, IC\R, 0, 255)
		Return
	EndIf
	If fld$ = "g"
		IC\G = Loom_ParseIntClamped(value$, IC\G, 0, 255)
		Return
	EndIf
	If fld$ = "b"
		IC\B = Loom_ParseIntClamped(value$, IC\B, 0, 255)
		Return
	EndIf
	If fld$ = "a"
		av = Loom_ParseIntClamped(value$, Int(IC\Alpha# * 255.0 + 0.5), 0, 255)
		IC\Alpha# = av / 255.0
		Return
	EndIf
	If fld$ = "tex"
		IC\Texture = Loom_ParseIntClamped(value$, IC\Texture, 0, 65535)
		Return
	EndIf

	WriteLog(LoomLog, "Interface: LoomIface_WriteField -- no handler for " + fieldId$)
End Function


; -----------------------------------------------------------------------------
; LoomIface_CreateDefaults -- build the full component roster with zeroed
; defaults, in the exact set LoadInterfaceSettings creates. Used as the
; missing/corrupt-file fallback so the Interface tab never derefs a Null
; component. GUE RuntimeErrors on a missing Interface.dat (GUE.bb:169); Loom
; is tolerant of half-set-up projects (matching Loom_LoadSettings).
; -----------------------------------------------------------------------------
Function LoomIface_CreateDefaults()
	Chat = New InterfaceComponent
	Chat\Texture = 65535
	ChatEntry = New InterfaceComponent
	For i = 0 To 39
		AttributeDisplays(i) = New InterfaceComponent
	Next
	BuffsArea = New InterfaceComponent
	Radar = New InterfaceComponent
	Compass = New InterfaceComponent
	InventoryWindow = New InterfaceComponent
	InventoryDrop = New InterfaceComponent
	InventoryEat = New InterfaceComponent
	InventoryGold = New InterfaceComponent
	For i = 0 To Slots_Inventory
		InventoryButtons(i) = New InterfaceComponent
	Next
End Function


; -----------------------------------------------------------------------------
; LoomIface_EnsureLoaded -- boot loader. Reads Interface.dat through the same
; LoadInterfaceSettings GUE boots with; on failure creates a default roster so
; the tab is still usable. Called once from Loom.bb's data-load block.
; -----------------------------------------------------------------------------
Function LoomIface_EnsureLoaded()
	If LoadInterfaceSettings("Data\Game Data\Interface.dat") = True Then Return
	WriteLog(LoomLog, "Interface: Interface.dat missing/corrupt -- creating default layout")
	LoomIface_CreateDefaults()
End Function


; -----------------------------------------------------------------------------
; LoomIface_DiscardReload -- drop all in-memory Interface layout state and
; re-read it from disk (Composer Discard button). Every InterfaceComponent is
; freed first so LoadInterfaceSettings repopulates a clean roster (it assigns
; New instances to each global + array slot). If the file has since gone
; missing, fall back to defaults so the globals never stay dangling.
; -----------------------------------------------------------------------------
Function LoomIface_DiscardReload()
	Delete Each InterfaceComponent
	If LoadInterfaceSettings("Data\Game Data\Interface.dat") = False Then LoomIface_CreateDefaults()
	InterfaceSaved = True
End Function
