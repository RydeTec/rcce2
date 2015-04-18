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
; Realm Crafter Packets module by Rob W (rottbott@hotmail.com), August 2004

; Packet types
Const P_CreateAccount      = 1
Const P_VerifyAccount      = 2
Const P_FetchCharacter     = 3
Const P_CreateCharacter    = 4
Const P_DeleteCharacter    = 5
Const P_ChangePassword     = 6
Const P_FetchActors        = 7
Const P_FetchItems         = 8
Const P_ChangeArea         = 9
Const P_FetchUpdateFiles   = 10
Const P_NewActor           = 11
Const P_StartGame          = 12
Const P_ActorGone          = 13
Const P_StandardUpdate     = 14
Const P_InventoryUpdate    = 15
Const P_ChatMessage        = 16
Const P_WeatherChange      = 17
Const P_AttackActor        = 18
Const P_ActorDead          = 19
Const P_RightClick         = 20
Const P_Dialog             = 21
Const P_StatUpdate         = 22
Const P_QuestLog           = 23
Const P_GoldChange         = 24
Const P_NameChange         = 25
Const P_KnownSpellUpdate   = 26
Const P_SpellUpdate        = 27
Const P_CreateEmitter      = 28
Const P_Sound              = 29
Const P_AnimateActor       = 30
Const P_ActionBarUpdate    = 31
Const P_XPUpdate           = 32
Const P_ScreenFlash        = 33
Const P_Music              = 34
Const P_OpenTrading        = 35
Const P_ActorEffect        = 36
Const P_Projectile         = 37
Const P_PartyUpdate        = 38
Const P_AppearanceUpdate   = 39
Const P_CloseTrading       = 40
Const P_UpdateTrading      = 41
Const P_SelectScenery      = 42
Const P_ItemScript         = 43
Const P_EatItem            = 44
Const P_ItemHealth         = 45
Const P_Jump               = 46
Const P_Dismount           = 47
Const P_FloatingNumber     = 48
Const P_RepositionActor    = 49
Const P_Speech             = 50
Const P_ProgressBar        = 51
Const P_BubbleMessage      = 52
Const P_ScriptInput        = 53
Const P_KickedPlayer       = 60
