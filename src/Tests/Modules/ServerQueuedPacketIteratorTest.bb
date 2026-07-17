Strict
EnableGC

; Source-contract regression for the server's queued-packet drain. Including
; ServerNet would pull in the complete network/server graph, so bind the
; deletion-safe cursor ordering directly in the bounded production loop.

Function SourceLine%(Path$, Needle$)
	Local F.BBStream = ReadFile(Path$)
	Local Line$
	Local LineNumber% = 0
	If F = Null Then F = ReadFile("..\" + Path$)
	If F = Null Then F = ReadFile("..\..\" + Path$)
	If F = Null Then Return 0
	While Not Eof(F)
		LineNumber = LineNumber + 1
		Line$ = ReadLine$(F)
		If Instr(Line$, Needle$) > 0
			CloseFile F
			Return LineNumber
		EndIf
	Wend
	CloseFile F
	Return 0
End Function

Test testQueuedPacketDrainCapturesNextBeforeDeletingCurrentCursor()
	Local StartLine% = SourceLine%("Modules\ServerNet.bb", "Local Q.QueuedPacket = First QueuedPacket")
	Local CaptureLine% = SourceLine%("Modules\ServerNet.bb", "QNext = After Q")
	Local SendLine% = SourceLine%("Modules\ServerNet.bb", "RCE_Send(Q\Connection, Q\Destination, Q\PacketType")
	Local DeleteLine% = SourceLine%("Modules\ServerNet.bb", "Delete(Q)")
	Local AdvanceLine% = SourceLine%("Modules\ServerNet.bb", "Q = QNext")

	Assert(StartLine > 0)
	Assert(CaptureLine > StartLine)
	Assert(SendLine > CaptureLine)
	Assert(DeleteLine > SendLine)
	Assert(AdvanceLine > DeleteLine)
	Assert(SourceLine%("Modules\ServerNet.bb", "For Q.QueuedPacket = Each QueuedPacket") = 0)
End Test
