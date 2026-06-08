; Globals ------------------------------------------------------------------------------------------------------------
Global RCE_ConvertBank = CreateBank(4)

; RCE_Connect errors
Const RCE_PortInUse       = -1
Const RCE_HostNotFound    = -2
Const RCE_TimedOut        = -3
Const RCE_ServerFull      = -4
Const RCE_ConnectionInUse = -5

; Local message types for user
Const RCE_PlayerTimedOut     = 200 
Const RCE_PlayerHasLeft      = 201
Const RCE_PlayerKicked       = 202

; A received message
Type RCE_Message
  Field Connection   ; Connection this message was received on
  Field MessageType  ; Packet type
  Field MessageData$ ; Packet data
  Field FromID       ; ID the message was from
End Type


; Funtions -----------------------------------------------------------------------------------------------------------
Function RCE_Send(Connection, Destination, MessageType, MessageData$, ReliableFlag = 0, PlayerFrom = 0, DoNotUse = 0, ConfirmID = -1)
   RCE_FSend( Destination, MessageType, MessageData$, ReliableFlag, Len(MessageData$) )
End Function 

Function RCE_CreateMessages()
	If (RCE_MoveToFirstMessage() <> 0)

		Repeat
			Local incomingType% = RCE_GetMessageType()
			Local incomingConn% = RCE_GetMessageConnection()

			; Reject "local-only" sentinel types if they arrive over the network.
			; RCE_PlayerTimedOut / PlayerHasLeft / PlayerKicked are supposed to be
			; synthesized by the network library when it detects a disconnect on
			; *this* host; the message handlers act on them as authoritative and
			; (in the kicked case) take the target RNID straight out of the
			; payload. Without this guard any connected peer can spoof a
			; PlayerKicked message and disconnect any other player — see
			; ServerNet.bb P_PlayerKicked handler.
			If incomingType% >= RCE_PlayerTimedOut And incomingType% <= RCE_PlayerKicked And incomingConn% <> 0
				; Drain the payload so the underlying queue advances, but do
				; NOT create an RCE_Message for the handlers to act on.
				Length% = RCE_MessageLength()
				If Length > 0
					MessageData = CreateBank(Length)
					RCE_GetMessageData(MessageData)
					FreeBank(MessageData)
				EndIf
			Else
				M.RCE_Message = New RCE_Message
				M\Connection = incomingConn%
				M\FromID = M\Connection
				M\MessageType = incomingType%

				Length% = RCE_MessageLength()
				If (Length > 0)
					MessageData= CreateBank(Length)
					RCE_GetMessageData(MessageData)
					; Copy the data
					For i = 0 To Length - 1
						M\MessageData$ = M\MessageData$ + Chr$(PeekByte(MessageData, i))
					Next
					FreeBank(MessageData)
				EndIf
			EndIf

		Until RCE_AreMoreMessage() = 0
	EndIf
End Function

; Conversions
Function RCE_IntFromStr(Dat$)
  PokeInt RCE_ConvertBank, 0, 0
  For i = 1 To Len(Dat$)
    PokeByte RCE_ConvertBank, i - 1, Asc(Mid$(Dat$, i, 1))
  Next
  Return PeekInt(RCE_ConvertBank, 0)
End Function

; Reads a SIGNED 16-bit value from a 2-byte wire field. RCE_IntFromStr zero-
; fills the 4-byte bank and writes only the bytes present, so a 2-byte field
; always decodes 0..65535 (unsigned). A field that carries a signed value in
; a 2-byte slot must sign-extend: a decoded value >= 32768 is negative. Use
; this (not RCE_IntFromStr) for such fields -- e.g. reputation, which the
; save path already stores signed via WriteShort/ReadShort, but every wire
; reader had been decoding unsigned (so a hostile/negative reputation arrived
; as a large positive on the client).
Function RCE_SignedShortFromStr(Dat$)
  Local v = RCE_IntFromStr(Dat$)
  If v >= 32768 Then v = v - 65536
  Return v
End Function

Function RCE_StrFromInt$(Num, Length = 4)
  PokeInt RCE_ConvertBank, 0, Num
  Dat$ = ""
  For i = Length - 1 To 0 Step -1
    Dat$ = Chr$(PeekByte(RCE_ConvertBank, i)) + Dat$
  Next
  Return Dat$
End Function

Function RCE_StrFromFloat$(Num#)
  PokeFloat RCE_ConvertBank, 0, Num#
  Dat$ = ""
  For i = 3 To 0 Step -1
    Dat$ = Chr$(PeekByte(RCE_ConvertBank, i)) + Dat$
  Next
  Return Dat$
End Function

Function RCE_FloatFromStr#(Dat$)
  PokeFloat RCE_ConvertBank, 0, 0.0
  For i = 1 To 4
    PokeByte RCE_ConvertBank, i - 1, Asc(Mid$(Dat$, i, 1))
  Next
  Return PeekFloat#(RCE_ConvertBank, 0)
End Function

; Clamp a world coordinate to a sane range. NaN compares false against any
; bound so it always falls through to the explicit 0 reset; Inf/large
; magnitudes get pulled to the limit. Used before persisting / broadcasting
; player-supplied position floats so a single crafted packet can't poison
; dropped-item / scenery / actor positions with NaN that subsequently
; corrupts every receiver's spatial code.
Const WorldCoordMax# = 100000.0
Function ClampWorldCoord#(v#)
  If v# > -WorldCoordMax# And v# < WorldCoordMax# Then Return v#
  Return 0.0
End Function

; Sane bounds for non-position floats coming off the wire (e.g. UI dims).
; Same NaN-via-comparison trick; range is intentionally permissive so the
; only thing rejected is NaN/Inf/extreme magnitudes.
Const FloatSanityMax# = 1000000000.0
Function ClampSaneFloat#(v#)
  If v# > -FloatSanityMax# And v# < FloatSanityMax# Then Return v#
  Return 0.0
End Function