; =======================================================================
; = Include file for BriskVM                         					=
; = Must be included by any Blitz3D/BlitzPlus project using BriskVM.    =
; = This file is not stand alone, the shared library is needed as well  =
; =======================================================================
; = By Regis JEAN-GILLES aka Koriolis                                   =
; =======================================================================

; Invalid handles
Const BVM_INVALID_CMD_SET%       = 0
Const BVM_INVALID_MODULE%        = 0
Const BVM_INVALID_CONTEXT%       = 0
Const BVM_INVALID_PROCESS%       = 0
Const BVM_INVALID_PROCESS_GROUP% = 0
Const BVM_INVALID_ENTRY_POINT%   = -1

; Error codes
Const BVM_ERR_SUCCESS%                = 0
Const BVM_ERR_ERROR%                  = 1 ; generic error code
Const BVM_ERR_PARSE_ERROR%            = 2
Const BVM_ERR_FILE_NOT_FOUND%         = 3
Const BVM_ERR_INVALID_CMD_SET%        = 4
Const BVM_ERR_INVALID_MODULE%         = 5
Const BVM_ERR_INVALID_ENTRY_POINT%    = 6
Const BVM_ERR_INVALID_CONTEXT%        = 7
;Const BVM_ERR_INVALID_PROCESS%       = 8
Const BVM_ERR_DIV_BY_ZERO%            = 9
Const BVM_ERR_OUT_OF_BOUND%           = 10
Const BVM_ERR_OBJECT_DOES_NOT_EXIST%  = 11
;Const BVM_ERR_INVALID_HASH%           = 12
Const BVM_ERR_STACK_OVERFLOW%         = 13
Const BVM_ERR_ASSERT_FAILED%		      = 14
Const BVM_ERR_ABORTED%				        = 15
Const BVM_ERR_UNCAUGHT_EXCEPTION%     = 16
Const BVM_ERR_INVALID_PROCESS_GROUP%  = 17
Const BVM_ERR_TRIAL_TIME_OUT% = 1001 ; Error emited by the trial version when it times out

; Compiling flags
Const BVM_CF_NONE%             = 0
Const BVM_CF_LINES_INFO%       = 1 ; Include source lines information in the module
Const BVM_CF_VARIABLES_INFO%   = 2 ; Include local and global variables symbolic information
Const BVM_CF_ALL%              = 3 ; All flags

; Access level ( = enum AccessLevel )
Const BVM_ACCESS_PUBLIC%		= 0
Const BVM_ACCESS_PROTECTED%		= 1
Const BVM_ACCESS_FRIENDLY%		= 2
Const BVM_ACCESS_PRIVATE%		= 3

; Debugging step action
Const BVM_DBG_ASK_AGAIN%       = 0
Const BVM_DBG_CONTINUE%        = 1
Const BVM_DBG_STEP_OVER%       = 2
Const BVM_DBG_STEP_INTO%       = 3
Const BVM_DBG_STEP_OUT%        = 4
Const BVM_DBG_STOP_EXEC%       = 5

; Repositories flags
Const BVM_REP_SCRIPT%          = 1
Const BVM_REP_MODULE%          = 2
Const BVM_REP_CMD_SET%         = 4
Const BVM_REP_ALL%             = 7



; Compiles a source file and saves the resulting module to disk
; Returns True for sucessful compiling, false, for error.
Function BVM_HELPER_CompileAndSave%(bbSourceFileName$, cmdSetFileName$, moduleFileName$, flags%=BVM_CF_NONE)
	; We load the Blitz3D command set
	Local hCmdSet% = BVM_LoadCommandSet(cmdSetFileName)
	If (hCmdSet = BVM_INVALID_CMD_SET) Return False

	; We compile the source file
	Local hModule% = BVM_CompileFile(bbSourceFileName, hCmdSet, moduleFileName, flags)
	If (hModule = BVM_INVALID_MODULE) Then
		BVM_DeleteCommandSet(hCmdSet)
		Return False
	EndIf

	; We save the module to file
	If (Not BVM_SaveModule(hModule)) Then
		BVM_DeleteCommandSet(hCmdSet)
		BVM_ReleaseModule(hModule)
		Return False
	EndIf
	BVM_DeleteCommandSet(hCmdSet)
	BVM_ReleaseModule(hModule)
	
	Return True
End Function



Function BVM_ClearErrors()
	BVM_SetLastError(BVM_ERR_SUCCESS, "")
End Function



Function BVM_WriteVarLenInt(hStream%, n%)
	If n < 0 Then
		n = ((Not n) Shl 1) Or 1
	Else
		n = n Shl 1
	EndIf
	
	If (n And $ffffff80) = 0 Then
		WriteByte(hStream, n or $80)
	ElseIf (n and $ffffc000) = 0 Then
		WriteByte(hStream, n Shr 7)
		WriteByte(hStream, (n and $7f) or $80)
	ElseIf (n and $ffe00000) = 0 Then
		WriteByte(hStream, n Shr 14)
		WriteByte(hStream, (n Shr 7) and $7f)
		WriteByte(hStream, (n And $7f) Or $80)
	ElseIf (n and $f0000000) = 0 Then
		WriteByte(hStream, n Shr 21)
		WriteByte(hStream, (n Shr 14) and $7f)
		WriteByte(hStream, (n Shr 7) and $7f)
		WriteByte(hStream, (n And $7f) Or $80)
	Else
		WriteByte(hStream, n Shr 28)
		WriteByte(hStream, (n Shr 21) and $7f)
		WriteByte(hStream, (n Shr 14) and $7f)
		WriteByte(hStream, (n Shr 7) and $7f)
		WriteByte(hStream, (n And $7f) Or $80)
	EndIf
End Function


Function BVM_ReadVarLenInt%(hStream%)
	Local n% = 0
	Local b% = ReadByte(hStream)
	If b < $80 Then
		n = b
		b = ReadByte(hStream)
		If b < $80 Then
			n = (n Shl 7) or b
			b = ReadByte(hStream)
			If b < $80 Then
				n = (n Shl 7) or b
				b = ReadByte(hStream)
				If b < $80 Then
					n = (n Shl 7) or b
					n = (n Shl 7) or (ReadByte(hStream) and $7f)
				Else
					n = (n Shl 7) or (b and $7f)
				EndIf
			Else
				n = (n Shl 7) or (b and $7f)
			EndIf
		Else
			n = (n Shl 7) or (b and $7f)
		EndIf
	Else
		n = b and $7f
	EndIf
	
	If (n and 1) Then
		Return not (n Shr 1)
	Else
		Return n Shr 1
	EndIf
End Function


; === Serialization functions used by the scripts ===
Function BVM_SerializeInt(hStream%, n%)
	Return BVM_WriteVarLenInt(hStream, n)
End Function

Function BVM_UnserializeInt%(hStream%)
	Return BVM_ReadVarLenInt(hStream)
End Function

Function BVM_SerializeFloat(hStream%, f#)
	WriteFloat(hStream, f)
End Function

Function BVM_UnserializeFloat#(hStream%)
		Return ReadFloat(hStream)
End Function

Function BVM_SerializeString(hStream%, s$)
	; Writes the String as an ASCIIZ String
	Local l = Len(length)
	WriteInt(hStream, l)
	Local i%
	For i = 1 To l
		WriteByte(hStream, Mid(s, i))
	Next 
End Function

Function BVM_UnserializeString$(hStream%)
	; Reads the String as an ASCIIZ String
	Local l = ReadInt(hStream)
	Local res$ = ""
	Local i%
	For i = 1 To l
		s = s + Chr(ReadByte(hStream))
	Next 
	Return s
End Function
; === End of serialization functions ===


;Const BVM_INVALID_HOSTAPPOBJECT_% = 0
;Const BVM_INVALID_I2IMAP_% = 0

Function BVM_StrIEq%(s1$, s2$)
	Return Lower(s1) = Lower(s2)
End Function

Function BVM_IntToStr$(val%)
	Return val
End Function



Function BVM_OpenReadSerializationStream%(name$)
	Return ReadFile(name)
End Function

Function BVM_OpenWriteSerializationStream%(name$)
	Return WriteFile(name)
End Function

Function BVM_CloseReadSerializationStream%(hStream%)
	CloseFile(hStream)
End Function

Function BVM_CloseWriteSerializationStream%(hStream%)
	CloseFile(hStream)
End Function



Function BVM_GenerateDoc%(sourceFileName$,hCmdSet%,docFilePath$,rootPath$,styleSheetPath$="",styleSheetType$="text/xsl",accessLevel%=1,bIncludeInner%=False) 
	Return BVM_GenerateDoc_(sourceFileName,hCmdSet,docFilePath,rootPath,styleSheetPath,styleSheetType,accessLevel,bIncludeInner)
End Function
