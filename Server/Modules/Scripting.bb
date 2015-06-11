; Realm Crafter BVM Scripting module by William "Mr.Bill" Steelhammer

; -Script SuperGlobal Array --------------------------------------------------------
Dim SuperGlobals$(99)

; Brisk VM Required Includes -------------------------------------------------------
Include "Modules\briskvm.bb"
Include "Modules\RC_Standard_invoker.bb"
Include "Modules\ScriptingCommands.bb"

;-BVM Globals-----------------------------------------------------------------------
Global hSI% = 0												;Current ScriptInstance handle
Global ScriptTimeout% = 1									;Timeout period for scripts in milliseconds
Global RCScripts$ = "Data\Server Data\Scripts\"
Global RCScriptFiles$ = "Data\Server Data\Script Files\"
Global hCommandset% = BVM_GetMainCommandSet()
Global hAltCmdSet% = BVM_GetAltCommandSet()

; -Threaded Script Execution Globals -----------------------------------------------
Global Scripts%
Global Threads%
Global Pass%

; -Set Brisk VM environment --------------------------------------------------------
BVM_SetFileExt(".rsl", BVM_REP_SCRIPT)
BVM_SetFileExt(".rcm", BVM_REP_MODULE)
BVM_SetFileExt(".rcs", BVM_REP_CMD_SET)
BVM_SetRepository(RCScripts$, BVM_REP_ALL)
; ----------------------------------------------------------------------------------

Type ScriptSource
	Field Name$
	Field Code$
	Field Compile%
	Field AltSyntax%
	Field hModule%
End Type

Type PausedScript
	Field Reason% ; 1 for actor logged out, 2 for WaitKill, 3 for WaitItem, 4 for WaitSpeak
	Field ReasonActor.ActorInstance, ReasonContextActor.ActorInstance, ReasonKillActor.Actor, ReasonItem$, ReasonAmount% ; Data for Wait... reasons
	Field ReasonCount% ; How many done so far
	Field S.ScriptInstance
End Type

Type ThreadScript
	Field SS.ScriptSource
	Field Name$
	Field Func$
	Field AI.ActorInstance
	Field AIContext.ActorInstance
	Field Param$
End Type

Type ScriptInstance
	Field Name$
	Field hContext%

	Field AI%
	Field AIContext%
	Field Param$

	Field Persistent%
	
	Field Waiting%
	Field WaitStart%, WaitTime%
	Field WaitHour%, WaitMinute%
	Field WaitResult$
	
	Field Ended%
	Field Thread%
End Type

; Maps a script and places it on the execution stack
Function RunScript(SS.ScriptSource, Func$, AI.ActorInstance, AIContext.ActorInstance, Param$ = "")
	; This function maps the module to the execution context in preperation to being run
		If BVM_FindFunction(SS\hModule, Func$) <> -1
			S.ScriptInstance = New ScriptInstance
			S\Name = SS\Name
			S\hContext = BVM_CreateContext() : BVM_CheckError()
			S\AI = Handle(AI.ActorInstance)
			S\AIContext = Handle(AIContext.ActorInstance)
			S\Param = Param
			S\Thread = -1 ;Every script gets one pass through the VM before being threaded
			BVM_MapModule(S\hContext, SS\hModule)
			Local hEntryPoint% = BVM_FindEntryPoint(S\hContext, SS\hModule, Func$)
			BVM_SelectContext(S\hContext)
			BVM_StartDebug(S\hContext)
			BVM_SelectEntryPoint(hEntryPoint)
		Else
			BVM_ScriptLog("Function " + Func$ + " not found in " + SS\Name$ + ".rsl")
			If SS\Name = "In-game Commands"
				BVM_OUTPUT(Handle(AI), "Command " + Func +  " is not known" )
			EndIf
		EndIf
End Function

Function ThreadScript(Name$, Func$, Actor%, CActor%, Param$ = "")
	; This function only adds the scripts to the ScriptInstance type and maps the module
	Local Found = False
	AI.ActorInstance = Object.ActorInstance(Actor)
	AIContext.ActorInstance = Object.ActorInstance(CActor)
	For SS.ScriptSource = Each ScriptSource
		If Upper(SS\Name$) = Upper(Name$)
			Found = 1
			Exit
		EndIf
	Next
	If Found = True
		TS.ThreadScript = New ThreadScript
		TS\Name$ = Name$
		TS\Func$ = Func$
		TS\SS = SS.ScriptSource
		TS\AI.ActorInstance = AI.ActorInstance
		TS\AIContext.ActorInstance = AIContext.ActorInstance
		TS\Param$ = Param$
	Else
		 WriteLog(Mainlog, "Script " + Name$ + " does not exist.")
	EndIf
End Function

; Updates running scripts
Function UpdateScripts()
	If Pass = 0 Then Pass = Order()
	; Handle threaded scripts
	For TS.ThreadScript = Each ThreadScript
		RunScript(TS\SS.ScriptSource, TS\Func$, TS\AI.ActorInstance, TS\AIContext.ActorInstance, TS\Param$)
		Delete TS
	Next

	; Update paused scripts if necessary
	For PS.PausedScript = Each PausedScript
		; Check whether waited for items are available
		If PS\Reason = 3
			If PS\ReasonActor <> Null
				If InventoryHasItem(PS\ReasonActor\Inventory, PS\ReasonItem$, PS\ReasonAmount)
					PS\S\WaitResult$ = "1"
					Delete PS
				EndIf
			Else
				FreeScriptInstance(PS\S)
				Delete PS
			EndIf
		Else
			; Check that wait actors are still available
			If PS\ReasonActor = Null Or (PS\ReasonContextActor = Null And PS\Reason = 4)
				FreeScriptInstance(PS\S)
				Delete PS
			EndIf
		EndIf
	Next
	Scripts = 0
	For S.ScriptInstance = Each ScriptInstance
			;Clean up of prematurely exited scripts
			If S\Ended = False
				; Update waiting scripts
				If S\Waiting = 1
					If Len(S\WaitResult$) > 0
						S\Waiting = 0
					EndIf
				ElseIf S\Waiting = 2
					If MilliSecs() - S\WaitStart >= S\WaitTime
						S\Waiting = 0
					EndIf
				; Waiting for a certain game time
				ElseIf S\Waiting = 3
					If TimeH >= S\WaitHour
						If TimeM >= S\WaitMinute
							S\Waiting = 0
							S\WaitResult$ = "1"
						EndIf
					EndIf
				EndIf
			;Threading Code
				Scripts = Scripts + 1
				If S\Thread = Pass Or S\Thread = -1
				If S\Waiting = 0
					hSI = Handle(S)
					BVM_SelectContext(S\hContext)
					BVM_StartTimeOut(ScriptTimeout%) ; Set the timout
					Local invokeRet% = BVM_Invoke(True) ; IMPORTANT: pass True To enable timeout
					Select invokeRet
						Case 0
							RuntimeError("The script function aborted due to an error: " + BVM_GetLastErrorMsg())
						Case 1
							;Cleanup scripts which have finished executing
							BVM_PopInt()
							BVM_DeleteContext(S\hContext)
							Delete S
						Case -1
							; The execution timed out.
							; We just do nothing (keep running the loop)
					End Select
					 BVM_CheckError()
					;BVM_EndDebug(S\hContext)
				EndIf
				EndIf
			Else
					BVM_DeleteContext(S\hContext)
					Delete S
			EndIf
	Next
	If Pass <> 0 Then Pass = Pass - 1 Else Pass = Threads

End Function

; Loads the superglobal variables
Function LoadSuperGlobals(File$)

	F = ReadFile(File$)
	If F = 0
		F = WriteFile(File$)
	Else
		For i = 0 To 99
			SuperGlobals$(i) = ReadString$(F)
		Next
	EndIf
	CloseFile(F)

End Function

; Saves the superglobal variables
Function SaveSuperGlobals(File$)

	F = WriteFile(File$)
	If F = 0 Then Return False

		For i = 0 To 99
			WriteString F, SuperGlobals$(i)
		Next

	CloseFile F
	Return True

End Function

; Loads and compiles all scripts in a folder
Function LoadScripts()
	;Preload RC_Core.rcm file to verify a valid version exists
	If Not BVM_LoadModule("RC_Core") Then CreateCore()

	;Preloads all scripts to strings in memory. Scripts will be compiled from the strings as needed by the server.
	SNumber = 0
	D = ReadDir(RCScripts)
	If D = 0 Then Return 0
		File$ = NextFile$(D)
		While Len(File$) > 0
			If FileType(RCScripts + "\" + File) = 1 And Lower$(Right$(File$, 3)) = "rsl"
				SS.ScriptSource = New ScriptSource
				SNumber = SNumber + 1
				F = ReadFile(RCScripts$ + "\" + File)
					If Lower$(File$) = "rc_core.rsl" Then SS\Code = "" Else SS\Code = "Using " + Chr$(34) + "RC_Core.rcm" + Chr$(34) + Chr$(10)
					While Eof(F) = False
						; Scripts not compacted for accurate error line numbers.
						TheLine$ = FullTrim(ReadLine$(F))
						If Left$(TheLine$, 1) = "!"
							If Upper(Right$(TheLine, Len(TheLine) - 1)) = "ALTSYNTAX"
								SS\AltSyntax = 1
								TheLine = " "
							ElseIf Upper(Right$(TheLine, Len(TheLine) - 1)) = "COMPILE"
								SS\Compile = 1
								TheLine = " "
							Else
								TheLine = " "
							EndIf
						EndIf
						SS\Code = SS\Code + TheLine + Chr$(10)
					Wend
				CloseFile(F)
				SS\Name = Left$(File, Len(File) - 4)
				EndIf
			File = NextFile$(D)
		Wend
	CloseDir(D)

	Return SNumber

End Function

; Frees all scripts associated with an actor instance
Function FreeActorScripts(A.ActorInstance)
		For S.ScriptInstance = Each ScriptInstance
			If S\AI = Handle(A) Or S\AIContext = Handle(A)
				For PS.PausedScript = Each PausedScript
					If PS\Reason <> 1
						If PS\S = S Then Delete PS
					EndIf
				Next
				FreeScriptInstance(S)
			EndIf
		Next
End Function

;Sets the flag for the cleanup of prematurely exited scripts
Function FreeScriptInstance(S.ScriptInstance)
	S\Ended = True
End Function


; Returns the nth record in a string for a given delimiter
Function Split$(St$, n, Delimiter$)

	PrevPos = 0
	Pos = Instr(St$, Delimiter$)
	For i = 2 To n
		PrevPos = Pos
		Pos = Instr(St$, Delimiter$, Pos + 1)
		If Pos = 0
			If i = n Then Pos = Len(St$) + 1 Else Return ""
		EndIf
	Next
	Return Mid$(St$, PrevPos + 1, (Pos - PrevPos) - 1)

End Function

; Returns the nth record in a string for a given delimiter, ignoring characters between quotes
Function SemiSplit$(St$, n, Delimiter$)

	OldPos = 0
	Num = 0
	InQuote = False
	For i = 1 To Len(St$)
		If Mid$(St$, i, 1) = Chr$(34)
			InQuote = Not InQuote
		ElseIf InQuote = False
			If Mid$(St$, i, 1) = Delimiter$
				Num = Num + 1
				If Num = n
					Return Mid$(St$, OldPos + 1, (i - OldPos) - 1)
					Exit
				EndIf
				OldPos = i
			EndIf
		EndIf
	Next
	If Num = n - 1
		Return Mid$(St$, OldPos + 1, Len(St$) - OldPos)
	Else
		Return ""
	EndIf

End Function

; Returns the nth record in a string for a given delimiter, ignoring characters between quotes, and stripping quotes
Function SafeSplit$(St$, n, Delimiter$)

	OldPos = 0
	Num = 0
	InQuote = False
	For i = 1 To Len(St$)
		If Mid$(St$, i, 1) = Chr$(34)
			InQuote = Not InQuote
		ElseIf InQuote = False
			If Mid$(St$, i, 1) = Delimiter$
				Num = Num + 1
				If Num = n
					Return Replace$(Mid$(St$, OldPos + 1, (i - OldPos) - 1), Chr$(34), "")
					Exit
				EndIf
				OldPos = i
			EndIf
		EndIf
	Next
	If Num = n - 1
		Return Replace$(Mid$(St$, OldPos + 1, Len(St$) - OldPos), Chr$(34), "")
	Else
		Return ""
	EndIf

End Function

; Fully trims a string including tab spaces
Function FullTrim$(S$)

	S$ = Trim$(S$)
	For i = 1 To Len(S$)
		If Asc(Mid$(S$, i, 1)) < 32
			S$ = Left$(S$, i - 1) + Mid$(S$, i + 1)
			i = i - 1
		EndIf
	Next
	Return S$

End Function


Function BVM_CheckError$()
	Local errorMsg$ = BVM_GetLastErrorMsg()
	If errorMsg <> "" Then
		Return errorMsg
	EndIf
End Function

Function CompileModules%()
Local Number% = 0
Local cmdSet% = 0
	For SS.ScriptSource = Each ScriptSource
		If SS\Compile = 1
			If SS\AltSyntax = 1
				cmdSet% = hAltCmdSet
			Else
				cmdSet% = hCommandset
			EndIf
			hModule = BVM_CompileString(SS\Code, cmdSet, SS\Name, BVM_CF_NONE)
			If hModule <> BVM_INVALID_MODULE
				SS\hModule = hModule
				BVM_SaveModule(hModule)
			Else
				Local errMsg$ = BVM_CheckError()
				BVM_ScriptLog(Replace$(errMsg, "'<string>'", SS\Name + ".rsl"))
			EndIf
			Number = Number + 1
		Else
			If SS\AltSyntax = 1
				cmdSet% = hAltCmdSet
			Else
				cmdSet% = hCommandset
			EndIf
			hModule = BVM_CompileString(SS\Code, cmdSet, SS\Name, BVM_CF_NONE)
			If hModule <> BVM_INVALID_MODULE
				SS\hModule = hModule
			Else
				errMsg$ = BVM_CheckError()
				BVM_ScriptLog(Replace$(errMsg, "'<string>'", SS\Name + ".rsl"))
			EndIf
		EndIf
	Next
Return Number
End Function

Function BVM_InterpretString%(Name$, Func$ = "Main")
	bRet% = True
	For SS.ScriptSource = Each ScriptSource
		If Upper(SS\Name$) = Upper(Name$) Then Exit
	Next
		hCmdSet% = hCommandset

	; We compile the temporary file
	hModule% = BVM_CompileString(SS\Code$, hCmdSet%, SS\Name$, BVM_CF_NONE)
	If hModule% = BVM_INVALID_MODULE% Then
		bRet% = False
	Else
		; We create a context
		hContext% = BVM_CreateContext()
		If hContext% = BVM_INVALID_CONTEXT% Then
			bRet% = False
		Else
			; We Select the context
			If (BVM_SelectContext(hContext%) = 0) Then
				bRet% = False
			Else
				; We map the module
				If (BVM_MapModule(hContext%, hModule%) = 0) Then
					bRet% = False
				Else
					; We get the entry point
					hEntryPoint% = BVM_FindEntryPoint(hContext%, hModule%, Func$)
					If hEntryPoint% = BVM_INVALID_ENTRY_POINT% Then
						bRet% = False
					Else
						; We select the entry point
						If (BVM_SelectEntryPoint(hEntryPoint%) = 0) Then
							bRet% = False
						Else
							; We execute the code
							If (BVM_Invoke() = 0) Then
								bRet% = False
							End If
						End If
					End If
				End If
			End If
		End If
	End If

	; Cleanup
	BVM_ReleaseModule(hModule%)
	BVM_DeleteContext(hContext%)
	BVM_SelectContext(hPrevContext%)

	Return bRet%
End Function

Function Order()
;Number of simultaneous scripts before threading takes place
If Scripts > 20
	;Sets the number of scripts per thread
	Threads = Scripts/10
	Count = 0
	For S.ScriptInstance = Each ScriptInstance
		;Attack scripts have priority status (Thread -1) and execute every pass
		If Upper(S\Name) = "ATTACK" Then S\Thread = -1 Else S\Thread = Count
		S\Param = Str(Count)
		If Count <> 0 Then Count = Count - 1 Else Count = Threads
	Next
Else
	Threads = 1
	For S.ScriptInstance = Each ScriptInstance
		If S\Thread <> -1 Then S\Thread = 1
	Next
EndIf

	; Update running scripts/threads label on Game window
	SetGadgetText(Game\LScripts, "Scripts: " + Scripts + " : " + Threads)

End Function

Function CreateCore()
;Consider also generating Core.rcm (Core.bvm)
filename$ = RCScripts$ + "RC_Core.rcm"
	Restore Core
	DeleteFile(filename)
	W = WriteFile(filename)
	Read count
	For i = 1 To count
		Read Dat
		WriteByte(W, Dat)
	Next
	CloseFile(W)
End Function


;==============================================================================
;Internally stored RC_Core.rcm file
.Core
Data 3476,200,183,138,135,23,73,76,201,69,187,216,135,244,128,225,143,235,138,248,156,156,73,73,73,73,73,73,73,73,73,73,73,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,201,73,73,73,71,71,79,150,251,154,243,157,157,77,236,196,237,237,75,78,72,73,72,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,73,201,201,78,31,197,73,89,73,73,73,64,141,194,135,209,148,218,142,221,221,76,236,196,225,200,200,77,78,72,73,72,75,78,72,73,73,75,73,73,73,73,73,73,73,73,73,72,73,73,73,73,73,73,73,73,73,77,73,73,73,73,73,73,73,73,73,201,201,19,82,72,208,77,197,77,82,74,198,75,82,76,198,77,82,78,82,72,198,77,162,198,79,155,133,101,82,72,198,77,162,198,79,155,132,98,197,73,89,73,73,73,66,134,214,147,221,153,208,145,221,146,213,213,66,236,196,225,205,232,196,224,204,233,192,192,77,78,72,73,72,65,78,72,73,73,78,72,73,75,66,72,89,73,72,201,78,72,89,77,73,66,73,75,73,73,73,73,73,73,73,74,73,72,73,73,73,73,73,73,73,68,73,74,73,73,73,73,73,73,73,201,201,203,13,198,95,208,91,198,89,82,64,208,89,198,93,208,71,216,77,210,77,198,91,208,69,82,66,208,67,198,89,197,73,153,133,202,115,198,71,197,73,153,133,203,53,198,69,197,73,153,133,97,198,69,82,68,208,65,130,93,197,206,182,55,208,65,198,67,197,73,135,133,87,197,206,182,55,208,79,130,93,198,67,208,79,82,70,198,89,198,67,198,67,216,75,82,88,197,75,82,74,82,90,82,92,197,73,135,133,99,82,90,82,92,197,73,135,132,96,82,90,73,82,94,208,77,130,93,199,75,82,80,130,93,199,77,82,80,198,75,89,197,73,89,73,73,73,69,138,198,137,218,159,219,146,211,159,208,151,151,78,236,196,225,205,232,193,193,77,78,72,73,72,77,78,72,73,73,78,72,73,75,76,73,73,73,73,73,73,73,73,73,75,73,73,73,73,73,73,73,73,73,78,73,73,73,73,73,73,73,73,73,201,201,50,198,67,208,65,198,79,82,64,208,79,198,65,208,77,198,79,197,73,153,133,57,198,77,197,73,153,133,123,82,70,198,79,198,79,82,82,130,93,199,79,82,80,130,93,199,65,82,80,197,73,89,73,73,73,68,141,196,133,201,134,193,142,219,143,223,138,222,222,70,236,196,225,205,232,196,224,204,233,197,224,204,233,192,192,77,78,72,73,72,69,78,72,73,73,78,72,73,75,66,72,73,73,78,72,89,77,202,55,78,72,89,79,202,55,78,72,89,65,202,55,69,73,75,73,73,73,73,73,73,73,76,73,72,73,73,73,73,73,73,73,71,73,74,73,73,73,73,73,73,73,201,201,203,89,198,81,208,89,198,71,82,64,208,71,198,95,208,69,216,77,210,77,198,93,208,67,198,91,208,65,198,89,208,79,198,71,197,73,153,133,203,119,198,69,197,73,153,133,203,73,198,67,197,73,153,133,200,11,82,70,198,71,198,69,198,69,198,69,198,93,216,75,82,84,197,75,82,74,82,90,82,92,197,73,135,133,99,82,90,82,92,197,73,135,132,96,130,93,199,67,82,80,130,93,199,69,82,80,130,93,199,71,82,80,198,75,89,197,73,89,73,73,73,69,141,196,133,201,134,193,136,198,150,195,151,151,66,236,196,225,205,232,196,224,204,232,193,193,77,78,72,73,72,65,78,72,73,73,78,72,73,75,66,72,73,73,66,72,89,75,75,229,229,79,73,75,73,73,73,73,73,73,73,75,73,75,73,73,73,73,73,73,73,65,73,74,73,73,73,73,73,73,73,201,201,200,32,198,69,208,67,198,65,82,64,208,65,198,67,208,79,198,65,197,73,153,133,203,9,198,79,197,73,153,133,203,75,198,77,197,73,153,133,200,13,82,70,198,65,198,65,216,77,216,77,82,86,197,75,82,74,82,90,82,92,197,73,135,133,99,82,90,82,92,197,73,135,132,96,82,90,73,208,77,130,93,199,89,82,80,130,93,199,91,82,80,130,93,199,93,82,80,198,75,89,197,73,89,73,73,73,79,128,206,158,203,159,159,66,237,197,224,204,232,196,224,204,233,192,192,77,66,72,73,72,65,78,72,73,73,66,72,73,73,66,72,73,75,78,72,89,75,73,76,73,76,73,73,73,73,73,73,73,75,73,75,73,73,73,73,73,73,73,78,73,79,73,73,73,73,73,73,73,201,201,200,20,198,67,208,65,198,79,82,64,208,79,198,65,208,77,198,79,197,73,153,133,203,97,198,77,197,73,153,133,200,35,216,67,210,65,216,65,210,79,82,70,198,79,198,79,216,79,216,79,82,104,197,75,82,74,82,90,82,92,197,73,135,133,99,82,90,82,92,197,73,135,132,96,82,90,210,77,130,93,199,95,82,80,130,93,199,81,82,80,216,75,91,199,73,91,73,73,73,91,138,216,157,220,136,205,157,207,128,199,149,208,131,208,146,211,129,129,80,236,196,225,205,232,196,225,205,232,196,231,203,232,196,231,203,232,196,225,205,232,196,224,201,201,77,78,72,73,72,95,78,72,73,73,78,72,73,75,78,72,73,77,78,72,73,79,64,72,73,73,64,72,73,75,64,72,73,77,64,72,73,79,78,72,73,65,78,72,73,67,66,72,73,73,65,77,72,73,73,73,73,73,73,73,79,77,72,73,73,73,73,73,73,73,67,77,72,73,73,73,73,73,73,73,201,201,200,23,198,89,82,64,208,79,198,89,197,73,153,133,203,27,198,77,197,73,153,133,203,93,82,70,198,79,198,91,198,91,198,91,217,65,217,65,217,65,217,65,198,91,198,91,216,75,82,106,197,75,82,74,82,90,82,92,197,73,135,133,99,82,90,82,92,197,73,135,132,96,82,90,73,82,94,208,77,130,93,199,83,82,80,130,93,199,85,82,80,198,75,89,197,73,89,73,73,73,91,141,200,132,193,149,208,128,210,157,218,136,205,158,205,143,206,156,156,78,236,196,225,205,232,193,193,77,78,72,73,72,77,78,72,73,73,78,72,73,75,76,73,73,73,73,73,73,73,73,73,75,73,73,73,73,73,73,73,73,73,78,73,73,73,73,73,73,73,73,73,201,201,50,198,67,208,65,198,79,82,64,208,79,198,79,197,73,153,133,200,77,198,77,197,73,153,133,15,198,65,208,77,82,70,198,79,198,79,82,108,130,93,199,87,82,80,130,93,199,105,82,80,197,73,89,73,73,73,91,156,204,136,201,157,216,136,218,149,210,128,197,150,197,135,198,148,148,64,236,196,225,205,232,196,225,200,200,77,78,72,73,72,79,78,72,73,73,78,72,73,75,78,72,73,77,78,73,73,73,73,73,73,73,73,73,74,73,73,73,73,73,73,73,73,73,64,73,73,73,73,73,73,73,73,73,201,201,200,67,198,71,208,67,198,65,82,64,208,65,198,65,197,73,153,133,200,107,198,79,197,73,153,133,45,198,69,208,79,198,67,208,77,82,70,198,65,198,65,198,65,82,110,130,93,199,107,82,80,130,93,199,109,82,80,197,73,89,73,73,73,64,158,255,150,226,182,223,178,215,215,78,236,196,225,205,232,193,193,77,78,72,73,72,77,78,72,73,73,78,72,73,75,75,73,73,73,73,73,73,73,73,73,75,73,73,73,73,73,73,73,73,73,77,73,73,73,73,73,73,73,73,73,201,201,48,197,79,82,74,198,75,197,49,152,133,9,198,77,197,75,161,208,79,198,75,197,49,162,208,77,198,77,198,77,82,96,82,90,82,92,197,73,135,133,99,82,90,82,92,197,73,135,132,96,197,73,89,73,73,73,67,158,223,150,194,145,193,132,197,142,142,78,236,196,225,205,232,193,193,77,78,72,73,72,77,78,72,73,73,78,72,73,75,77,73,73,73,73,73,73,73,73,73,75,73,73,73,73,73,73,73,73,73,79,73,73,73,73,73,73,73,73,73,201,201,20,198,65,208,79,198,79,208,77,198,77,198,77,82,98,197,75,82,74,82,90,82,92,197,73,135,133,99,82,90,82,92,197,73,135,132,96,197,73,89,73,73,73,64,158,223,150,194,139,223,154,215,215,64,236,196,225,205,233,197,224,201,201,77,78,72,73,72,79,78,72,73,73,66,72,73,73,78,72,73,75,77,73,75,73,73,73,73,73,73,73,75,73,72,73,73,73,73,73,73,73,79,73,74,73,73,73,73,73,73,73,201,201,56,198,65,208,79,216,77,82,100,210,77,198,79,208,77,198,77,216,75,198,77,82,102,197,75,82,74,82,90,82,92,197,73,135,133,99,82,90,82,92,197,73,135,132,96,197,73,89,73,73,73,64,158,223,150,194,137,192,140,192,192,64,236,196,225,205,232,196,225,200,200,77,78,72,73,72,79,78,72,73,73,78,72,73,75,78,72,73,77,79,73,73,73,73,73,73,73,73,73,74,73,73,73,73,73,73,73,73,73,65,73,73,73,73,73,73,73,73,73,201,201,37,198,69,208,65,198,67,208,79,198,65,208,77,198,79,198,79,198,79,82,120,197,75,82,74,82,90,82,92,197,73,135,133,99,82,90,82,92,197,73,135,132,96,197,73,89,73,73,73,73,80,202,97,68,132,237,129,237,132,215,178,209,162,135,175,134,134,72,205,81,71,154,223,139,220,157,212,128,201,135,192,232,205,228,228,72,205,85,89,154,223,139,220,157,212,128,211,135,198,148,192,232,205,228,228,72,205,84,70,154,223,139,220,157,212,128,212,157,208,149,189,152,177,177,72,202,72,69,142,203,159,205,131,202,142,171,131,166,143,143,72,203,72,91,151,240,149,225,134,234,133,231,134,234,180,220,143,198,227,203,226,226,72,202,75,88,142,203,159,205,152,214,130,203,134,195,138,206,235,195,230,207,207,72,203,73,90,151,240,149,225,134,234,133,231,134,234,180,252,147,224,148,177,153,176,176,72,202,18,87,155,216,157,194,145,212,154,222,145,193,132,202,142,199,134,202,133,194,234,207,227,198,234,207,227,198,234,206,231,231,72,202,74,88,142,203,159,200,137,192,148,198,131,208,133,201,157,185,145,184,184,72,202,104,65,133,224,142,171,131,167,142,142,72,202,88,65,128,238,154,191,151,178,155,155,72,202,62,68,154,217,139,194,146,198,138,197,130,170,142,167,167,72,202,28,82,155,216,157,194,145,212,154,222,157,209,158,205,136,204,133,196,136,199,128,168,141,161,132,168,141,164,164,72,202,16,109,155,216,157,194,145,212,154,222,154,211,146,222,145,214,153,204,152,200,157,201,225,196,232,205,225,196,232,205,225,196,232,205,225,197,236,236,72,202,17,86,155,216,157,194,145,212,154,222,154,211,146,222,145,214,159,209,129,212,128,168,141,161,132,168,141,161,133,169,141,164,164,72,202,19,80,155,216,157,194,145,212,154,222,151,217,137,220,136,160,133,169,140,160,133,169,141,161,133,172,172,72,202,31,122,155,216,157,194,145,212,154,222,157,207,138,203,159,218,138,216,151,208,130,199,148,199,133,196,150,190,155,183,146,190,155,183,146,190,155,183,148,184,155,183,148,184,155,183,146,190,155,183,147,186,186,72,202,30,104,155,216,157,194,145,212,154,222,154,223,147,214,130,199,151,197,138,205,159,218,137,218,152,217,139,163,134,170,143,163,134,175,175,72,202,21,106,155,216,157,194,145,212,154,222,139,219,159,222,138,207,159,205,130,197,151,210,129,210,144,209,131,171,142,162,135,171,142,162,135,174,174,72,205,94,88,154,223,139,220,157,212,128,201,135,193,142,166,131,175,138,163,163,72,205,82,91,154,223,139,220,157,212,128,211,131,198,135,204,228,193,237,200,225,225,72,205,100,64,157,239,134,235,207,231,195,234,234,72,205,80,90,154,223,139,220,157,212,128,201,157,216,149,189,152,180,144,188,153,176,176,72,205,83,90,154,223,139,220,157,212,128,203,130,206,130,170,143,163,134,170,143,166,166,72,73,73,72,72,72,72,72,72,111,73,13,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,32,39,105,6,57,44,39,13,32,40,37,38,46,13,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,0,39,105,6,57,44,39,13,32,40,37,38,46,15,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,32,39,105,10,37,38,58,44,13,32,40,37,38,46,15,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,0,39,105,10,37,38,58,44,13,32,40,37,38,46,17,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,13,32,40,37,38,46,105,33,40,39,45,37,44,105,32,39,105,13,32,40,37,38,46,6,60,61,57,60,61,1,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,32,39,105,13,32,40,37,38,46,6,60,61,57,60,61,1,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,0,39,105,13,32,40,37,38,46,6,60,61,57,60,61,31,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,13,32,40,37,38,46,105,33,40,39,45,37,44,105,32,39,105,13,32,40,37,38,46,32,39,57,60,61,15,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,32,39,105,13,32,40,37,38,46,32,39,57,60,61,15,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,0,39,105,13,32,40,37,38,46,32,39,57,60,61,115,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,32,39,105,0,39,57,60,61,115,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,0,39,105,0,39,57,60,61,27,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,32,39,105,10,59,44,40,61,44,25,59,38,46,59,44,58,58,11,40,59,27,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,0,39,105,10,59,44,40,61,44,25,59,38,46,59,44,58,58,11,40,59,27,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,32,39,105,13,44,37,44,61,44,25,59,38,46,59,44,58,58,11,40,59,27,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,0,39,105,13,44,37,44,61,44,25,59,38,46,59,44,58,58,11,40,59,27,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,32,39,105,28,57,45,40,61,44,25,59,38,46,59,44,58,58,11,40,59,27,12,59,59,38,59,115,105,0,39,63,40,37,32,45,105,8,42,61,38,59,105,0,39,105,28,57,45,40,61,44,25,59,38,46,59,44,58,58,11,40,59

