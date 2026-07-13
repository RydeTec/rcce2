; =============================================================================
; Loom/Seasons.bb -- Days & seasons editing (GUE "Days & seasons" tab parity)
; =============================================================================
;
; The data lives in the shared Environment.bb module (included by Server /
; Client / GUE / Loom alike): the calendar globals + Dim'd arrays
; (Year / Day / TimeFactor, SeasonName$ / SeasonStartDay / SeasonDawnH /
; SeasonDuskH, MonthName$ / MonthStartDay) persisted to
; Data\Server Data\Environment.dat via SaveEnvironment(True), and the Sun
; type instances persisted to Data\Game Data\Suns.dat via SaveSuns().
; Loom loads through the exact same LoadEnvironment() / LoadSuns() GUE runs
; at boot (GUE.bb "Load environment" block), and saves through the exact
; same pair GUE's BSeasonSave button fires -- so the two editors cannot
; drift in how they read or write the format.
;
; This module is the write path only. It's non-Strict on purpose: the
; calendar tables are Dim'd global arrays and BlitzForge Strict cannot
; write to those from inside a Method (the "Dim-write trap" -- see
; docs/loom/architecture.md "Known BlitzForge gotchas"). The Strict
; Composer routes every "environment" field commit through
; LoomEnv_WriteField below, the same shape Settings.bb uses for the
; LoomCfg_* setters and Actors.bb uses for SetFactionName.
;
; Cumulative-day semantics (mirrors GUE.bb's SMonthStart / SSeasonStart
; event handlers):
;   * MonthStartDay(0) / SeasonStartDay(0) hold the YEAR LENGTH in days.
;   * MonthStartDay(k) for k = 1..19 is the cumulative day on which month
;     k ends (month k+1 starts). Same for SeasonStartDay(k), k = 1..11.
;   * Editing month k's length rewrites MonthStartDay(k) and shifts every
;     later cumulative start by the delta, clamping each to stay strictly
;     after its predecessor -- GUE.bb "Move future months" loop.
;   * GUE only edits months 1..18 (its handler guards `SelectedMonth < 19`)
;     and seasons 1..10 (`SelectedSeason < 11`); the trailing entries are
;     derived from the year length. Loom keeps the same editability split.
;
; Dirty flag: EnvironmentSaved -- shared with GUE (GUE.bb declares it next
; to SpellsSaved; Loom.bb redeclares the same set). Composer::
; markDirtyForKind("environment") flips it; commitSaveForKind /
; LoomEnv_DiscardReload restore it.


; -----------------------------------------------------------------------------
; LoomEnv_MonthLength -- displayed length (days) of 1-based month k (1..20).
; Month 1 starts at day 0 so its length IS its cumulative end. Month 20 has
; no stored end; it runs to the year length.
; -----------------------------------------------------------------------------
Function LoomEnv_MonthLength%(k)
	If k <= 1 Then Return MonthStartDay(1)
	If k >= 20 Then Return MonthStartDay(0) - MonthStartDay(19)
	Return MonthStartDay(k) - MonthStartDay(k - 1)
End Function


; -----------------------------------------------------------------------------
; LoomEnv_SeasonLength -- displayed length (days) of 1-based season k (1..12).
; -----------------------------------------------------------------------------
Function LoomEnv_SeasonLength%(k)
	If k <= 1 Then Return SeasonStartDay(1)
	If k >= 12 Then Return SeasonStartDay(0) - SeasonStartDay(11)
	Return SeasonStartDay(k) - SeasonStartDay(k - 1)
End Function


; -----------------------------------------------------------------------------
; LoomEnv_SetMonthLength -- GUE.bb "Case SMonthStart" semantics: set month
; k's length by rewriting its cumulative end day, then shift every later
; month's start by the delta, keeping the table strictly increasing.
; k must be 1..18 (GUE's editability guard); callers enforce it.
; -----------------------------------------------------------------------------
Function LoomEnv_SetMonthLength(k, newLen)
	Change = MonthStartDay(k)
	If k > 1
		MonthStartDay(k) = MonthStartDay(k - 1) + newLen
	Else
		MonthStartDay(k) = newLen
	EndIf
	Change = MonthStartDay(k) - Change
	; Move future months (GUE.bb "Move future months" loop)
	For i = k + 1 To 19
		MonthStartDay(i) = MonthStartDay(i) + Change
		If MonthStartDay(i) <= MonthStartDay(i - 1) Then MonthStartDay(i) = MonthStartDay(i - 1) + 1
	Next
End Function


; -----------------------------------------------------------------------------
; LoomEnv_SetSeasonLength -- GUE.bb "Case SSeasonStart" semantics; k = 1..10.
; -----------------------------------------------------------------------------
Function LoomEnv_SetSeasonLength(k, newLen)
	Change = SeasonStartDay(k)
	If k > 1
		SeasonStartDay(k) = SeasonStartDay(k - 1) + newLen
	Else
		SeasonStartDay(k) = newLen
	EndIf
	Change = SeasonStartDay(k) - Change
	; Move future seasons (GUE.bb "Move future seasons" loop)
	For i = k + 1 To 11
		SeasonStartDay(i) = SeasonStartDay(i) + Change
		If SeasonStartDay(i) <= SeasonStartDay(i - 1) Then SeasonStartDay(i) = SeasonStartDay(i - 1) + 1
	Next
End Function


; -----------------------------------------------------------------------------
; LoomEnv_NewSun -- create a Sun with GUE's "New sun" defaults (GUE.bb
; Case BSunNew): size 1.0, rise 05:00 / set 22:00 in every season, white
; light. Returns the new instance's handle.
; -----------------------------------------------------------------------------
Function LoomEnv_NewSun%()
	S.Sun = New Sun
	S\Size# = 1.0
	For i = 0 To 11
		S\StartH[i] = 5
		S\EndH[i] = 22
	Next
	S\LightR = 255
	S\LightG = 255
	S\LightB = 255
	Return Handle(S)
End Function


; -----------------------------------------------------------------------------
; LoomEnv_DiscardReload -- drop all in-memory Days & seasons state and
; re-read it from disk (Composer Discard button). Suns are freed first so
; LoadSuns doesn't duplicate the pool; the calendar tables are overwritten
; in place by LoadEnvironment.
; -----------------------------------------------------------------------------
Function LoomEnv_DiscardReload()
	Delete Each Sun
	LoadEnvironment()
	LoadSuns()
	EnvironmentSaved = True
End Function


; -----------------------------------------------------------------------------
; LoomEnv_Token -- nth (1-based) underscore-separated token of a fieldId.
; Free function in this non-Strict module because the scanning loop's
; rebindable locals hit the Strict "reassign a Method Local from a nested
; block" trap if written as a Composer Method.
; -----------------------------------------------------------------------------
Function LoomEnv_Token$(s$, n)
	count = 1
	out$ = ""
	For i = 1 To Len(s$)
		c$ = Mid$(s$, i, 1)
		If c$ = "_"
			count = count + 1
			If count > n Then Return out$
		Else
			If count = n Then out$ = out$ + c$
		EndIf
	Next
	Return out$
End Function


; -----------------------------------------------------------------------------
; LoomEnv_WriteField -- the full "environment" write dispatch. Called from
; Composer::writeField (Strict) with the committed edit-buffer string.
; Every numeric field clamps through Loom_ParseIntClamped /
; Loom_ParseFloatClamped with the SAME ranges GUE's spinners enforce
; (cited per field below); garbage input falls back to the stored value.
; -----------------------------------------------------------------------------
Function LoomEnv_WriteField(fieldId$, value$)

	; ---- General (GUE.bb SYearLength / STimeFactor / SYear / SDay) ----------
	If fieldId$ = "year_length"
		; GUE spinner 25..10000 days; writing it updates BOTH cumulative
		; tables' slot 0 (GUE.bb Case SYearLength).
		v = Loom_ParseIntClamped(value$, MonthStartDay(0), 25, 10000)
		MonthStartDay(0) = v
		SeasonStartDay(0) = v
		Return
	EndIf
	If fieldId$ = "time_factor"
		; GUE spinner 1..255x. 0 would divide-by-zero UpdateEnvironment.
		TimeFactor = Loom_ParseIntClamped(value$, TimeFactor, 1, 255)
		Return
	EndIf
	If fieldId$ = "year"
		; GUE spinner -100000..1000000.
		Year = Loom_ParseIntClamped(value$, Year, -100000, 1000000)
		Return
	EndIf
	If fieldId$ = "day"
		; UI is 1-based (GUE displays Day + 1, spinner 1..10000); storage is
		; 0-based, clamped to the year length (GUE.bb Case SDay).
		v = Loom_ParseIntClamped(value$, Day + 1, 1, 10000)
		Day = v - 1
		If Day > MonthStartDay(0) Then Day = MonthStartDay(0)
		Return
	EndIf

	; ---- Months (GUE.bb TMonthName / SMonthStart) ----------------------------
	If Left$(fieldId$, 11) = "month_name_"
		i = Int(Mid$(fieldId$, 12))
		; GUE's name TextBox caps at 50 chars.
		If i >= 0 And i <= 19 Then MonthName$(i) = Left$(value$, 50)
		Return
	EndIf
	If Left$(fieldId$, 10) = "month_len_"
		k = Int(Mid$(fieldId$, 11))
		; GUE edits months 1..18 only (SelectedMonth < 19 guard); spinner
		; 1..10000 days.
		If k >= 1 And k <= 18 Then LoomEnv_SetMonthLength(k, Loom_ParseIntClamped(value$, LoomEnv_MonthLength(k), 1, 10000))
		Return
	EndIf

	; ---- Seasons (GUE.bb TSeasonName / SSeasonStart / SDawn / SDusk) ---------
	If Left$(fieldId$, 12) = "season_name_"
		i = Int(Mid$(fieldId$, 13))
		If i >= 0 And i <= 11 Then SeasonName$(i) = Left$(value$, 50)
		Return
	EndIf
	If Left$(fieldId$, 11) = "season_len_"
		k = Int(Mid$(fieldId$, 12))
		; GUE edits seasons 1..10 only (SelectedSeason < 11 guard).
		If k >= 1 And k <= 10 Then LoomEnv_SetSeasonLength(k, Loom_ParseIntClamped(value$, LoomEnv_SeasonLength(k), 1, 10000))
		Return
	EndIf
	If Left$(fieldId$, 12) = "season_dawn_"
		i = Int(Mid$(fieldId$, 13))
		; GUE spinner 0..23 (":00" suffix -- whole hours).
		If i >= 0 And i <= 11 Then SeasonDawnH(i) = Loom_ParseIntClamped(value$, SeasonDawnH(i), 0, 23)
		Return
	EndIf
	If Left$(fieldId$, 12) = "season_dusk_"
		i = Int(Mid$(fieldId$, 13))
		If i >= 0 And i <= 11 Then SeasonDuskH(i) = Loom_ParseIntClamped(value$, SeasonDuskH(i), 0, 23)
		Return
	EndIf

	; ---- Suns & Moons --------------------------------------------------------
	If Left$(fieldId$, 4) = "sun_"
		LoomEnv_WriteSunField(fieldId$, value$)
		Return
	EndIf

	WriteLog(LoomLog, "Seasons: LoomEnv_WriteField -- no handler for " + fieldId$)
End Function


; -----------------------------------------------------------------------------
; LoomEnv_WriteSunField -- sun fieldId grammar (handle-addressed since Sun
; has no ID field; handles are session-stable which is all an edit needs):
;   3 tokens: sun_<field>_<handle>          e.g. "sun_size_1234"
;   4 tokens: sun_<field>_<idx>_<handle>    e.g. "sun_riseh_3_1234" (season 3)
;                                                "sun_tex_0_1234"   (phase 0)
; A stale handle (sun deleted mid-edit) resolves Null and the write drops.
; -----------------------------------------------------------------------------
Function LoomEnv_WriteSunField(fieldId$, value$)
	part$ = LoomEnv_Token(fieldId$, 2)

	fourToken = False
	If part$ = "tex" Or part$ = "riseh" Or part$ = "risem" Or part$ = "seth" Or part$ = "setm" Then fourToken = True

	idx = 0
	If fourToken = True
		idx = Int(LoomEnv_Token(fieldId$, 3))
		S.Sun = Object.Sun(Int(LoomEnv_Token(fieldId$, 4)))
	Else
		S.Sun = Object.Sun(Int(LoomEnv_Token(fieldId$, 3)))
	EndIf
	If S = Null
		WriteLog(LoomLog, "Seasons: sun write dropped -- stale handle in " + fieldId$)
		Return
	EndIf

	; GUE spinner/slider ranges cited per field.
	If part$ = "size"
		; SSunSize: 1..10, float.
		S\Size# = Loom_ParseFloatClamped(value$, S\Size#, 1.0, 10.0)
		Return
	EndIf
	If part$ = "angle"
		; SSunAngle: 0..360 degrees (integer spinner into a float field).
		S\PathAngle# = Loom_ParseIntClamped(value$, Int(S\PathAngle#), 0, 360)
		Return
	EndIf
	If part$ = "lightr"
		; SLSunR/G/B sliders: 0..255 (byte in Suns.dat).
		S\LightR = Loom_ParseIntClamped(value$, S\LightR, 0, 255)
		Return
	EndIf
	If part$ = "lightg"
		S\LightG = Loom_ParseIntClamped(value$, S\LightG, 0, 255)
		Return
	EndIf
	If part$ = "lightb"
		S\LightB = Loom_ParseIntClamped(value$, S\LightB, 0, 255)
		Return
	EndIf
	If part$ = "flares"
		; BSunShowFlares checkbox.
		S\ShowFlares = (value$ = "1")
		Return
	EndIf
	If part$ = "phases"
		; BSunShowPhases checkbox.
		S\ShowPhases = (value$ = "1")
		Return
	EndIf
	If part$ = "phaselen"
		; SSunPhase_Length: 1..200 days (byte in Suns.dat).
		S\Phase_Length = Loom_ParseIntClamped(value$, S\Phase_Length, 1, 200)
		Return
	EndIf
	If part$ = "tex"
		; Texture ID is a Short in Suns.dat (WriteShort); GUE assigns from
		; its texture chooser. Phase idx 0..7 (TexID[7] -> 8 slots).
		If idx >= 0 And idx <= 7 Then S\TexID[idx] = Loom_ParseIntClamped(value$, S\TexID[idx], 0, 65535)
		Return
	EndIf
	If part$ = "riseh"
		; SSunRiseH: 0..23, per-season (idx 0..11).
		If idx >= 0 And idx <= 11 Then S\StartH[idx] = Loom_ParseIntClamped(value$, S\StartH[idx], 0, 23)
		Return
	EndIf
	If part$ = "risem"
		; SSunRiseM: 0..59.
		If idx >= 0 And idx <= 11 Then S\StartM[idx] = Loom_ParseIntClamped(value$, S\StartM[idx], 0, 59)
		Return
	EndIf
	If part$ = "seth"
		If idx >= 0 And idx <= 11 Then S\EndH[idx] = Loom_ParseIntClamped(value$, S\EndH[idx], 0, 23)
		Return
	EndIf
	If part$ = "setm"
		If idx >= 0 And idx <= 11 Then S\EndM[idx] = Loom_ParseIntClamped(value$, S\EndM[idx], 0, 59)
		Return
	EndIf

	WriteLog(LoomLog, "Seasons: LoomEnv_WriteSunField -- no handler for " + fieldId$)
End Function
