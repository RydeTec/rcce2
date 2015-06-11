;##############################################################################################################################
; Realm Crafter version 1.10																									
; Copyright (C) 2007 Solstar Games, LLC. All rights reserved																	
; contact@solstargames.com																																																		
;																																																																#
; Programmer: Rob Williams																										
; Program: Realm Crafter Actors module
;																																
;This is a licensed product:
;BY USING THIS SOURCECODE, YOU ARE CONFIRMING YOUR ACCEPTANCE OF THE SOFTWARE AND AGREEING TO BECOME BOUND BY THE TERMS OF 
;THIS AGREEMENT. IF YOU DO NOT AGREE TO BE BOUND BY THESE TERMS, THEN DO NOT USE THE SOFTWARE.
;																		
;Licensee may NOT: 
; (i)   create any derivative works of the Engine, including translations Or localizations, other than Games;
; (ii)  redistribute, encumber, sell, rent, lease, sublicense, Or otherwise transfer rights To the Engine; or
; (iii) remove Or alter any trademark, logo, copyright Or other proprietary notices, legends, symbols Or labels in the Engine.
; (iv)   licensee may Not distribute the source code Or documentation To the engine in any manner, unless recipient also has a 
;       license To the Engine.													
; (v)  use the Software to develop any software or other technology having the same primary function as the Software, 
;       including but not limited to using the Software in any development or test procedure that seeks to develop like 
;       software or other technology, or to determine if such software or other technology performs in a similar manner as the
;       Software																																
;##############################################################################################################################

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
		M.RCE_Message = New RCE_Message
		M\Connection = RCE_GetMessageConnection()
		M\FromID = M\Connection
		M\MessageType = RCE_GetMessageType()
		
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