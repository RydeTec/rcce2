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
; Realm Crafter Animation module by Rob W (rottbott@hotmail.com), August 2004

; Required animations
Const Anim_Walk          = 149
Const Anim_Run           = 148
Const Anim_SwimIdle      = 147
Const Anim_SwimSlow      = 146
Const Anim_SwimFast      = 145
Const Anim_RideIdle      = 144
Const Anim_RideWalk      = 143
Const Anim_RideRun       = 142
Const Anim_DefaultAttack = 141
Const Anim_RightAttack   = 140
Const Anim_TwoHandAttack = 139
Const Anim_StaffAttack   = 138
Const Anim_DefaultParry  = 137
Const Anim_RightParry    = 136
Const Anim_TwoHandParry  = 135
Const Anim_StaffParry    = 134
Const Anim_ShieldParry   = 133
Const Anim_LastHit       = 132
Const Anim_FirstHit      = 130
Const Anim_LastDeath     = 129
Const Anim_FirstDeath    = 127
Const Anim_Jump          = 126
Const Anim_Idle          = 125
Const Anim_Yawn          = 124
Const Anim_LookRound     = 123
Const Anim_SitDown       = 122
Const Anim_SitIdle       = 121
Const Anim_StandUp       = 120

Dim AnimList.AnimSet(999)
Type AnimSet
	Field Name$
	Field ID
	Field AnimName$[149], AnimStart[149], AnimEnd[149], AnimSpeed#[149]
End Type

; Plays a given animation at the given speed
Function PlayAnimation(AI.ActorInstance, Mode, Speed#, Seq, FixedSpeed = True)

	If AI\Gender = 0
		A.AnimSet = AnimList(AI\Actor\MAnimationSet)
	Else
		A.AnimSet = AnimList(AI\Actor\FAnimationSet)
	EndIf
	If A\AnimEnd[Seq] = 0 Or A\AnimStart[Seq] > A\AnimEnd[Seq] Then Return
	If FixedSpeed = True
		Length# = A\AnimEnd[Seq] - A\AnimStart[Seq]
		If Length# < 1.0 Then Length# = 1.0
		Speed# = Speed# * Length#
	EndIf
	Animate(AI\EN, Mode, Speed# * A\AnimSpeed#[Seq], AI\AnimSeqs[Seq], 1.0)

End Function

; Creates a new animation set
Function CreateAnimSet()

	A.AnimSet = New AnimSet
	A\ID = -1
	For i = 0 To 999
		If AnimList(i) = Null Then A\ID = i : Exit
	Next
	If A\ID = -1 Then Delete A : Return -1
	AnimList(A\ID) = A
	A\Name$ = "New Animation Set"
	A\AnimName$[149] = "Walk"
	A\AnimName$[148] = "Run"
	A\AnimName$[147] = "Swim idle"
	A\AnimName$[146] = "Swim slow"
	A\AnimName$[145] = "Swim fast"
	A\AnimName$[144] = "Ride idle"
	A\AnimName$[143] = "Ride walk"
	A\AnimName$[142] = "Ride run"
	A\AnimName$[141] = "Default attack"
	A\AnimName$[140] = "Right hand attack"
	A\AnimName$[139] = "Two hand attack"
	A\AnimName$[138] = "Staff attack"
	A\AnimName$[137] = "Default parry"
	A\AnimName$[136] = "Right hand parry"
	A\AnimName$[135] = "Two hand parry"
	A\AnimName$[134] = "Staff parry"
	A\AnimName$[133] = "Shield parry"
	A\AnimName$[132] = "Hit 1"
	A\AnimName$[131] = "Hit 2"
	A\AnimName$[130] = "Hit 3"
	A\AnimName$[129] = "Death 1"
	A\AnimName$[128] = "Death 2"
	A\AnimName$[127] = "Death 3"
	A\AnimName$[126] = "Jump"
	A\AnimName$[125] = "Idle"
	A\AnimName$[124] = "Yawn"
	A\AnimName$[123] = "Look around"
	A\AnimName$[122] = "Sit down"
	A\AnimName$[121] = "Sit idle"
	A\AnimName$[120] = "Stand up"
	For i = 0 To 149
		A\AnimSpeed#[i] = 1.0
	Next
	Return A\ID

End Function

; Loads all animation sets
Function LoadAnimSets(Filename$)

	Local Sets = 0

	F = ReadFile(Filename$)
	If F = 0 Then Return -1

		While Not Eof(F)
			A.AnimSet = New AnimSet
			A\ID = ReadShort(F)
			AnimList(A\ID) = A
			A\Name$ = ReadString$(F)
			For i = 0 To 149
				A\AnimName$[i] = ReadString$(F)
				A\AnimStart[i] = ReadShort(F)
				A\AnimEnd[i] = ReadShort(F)
				A\AnimSpeed#[i] = ReadFloat#(F)
			Next
			Sets = Sets + 1
		Wend

	CloseFile(F)
	Return(Sets)

End Function

; Saves all animation sets
Function SaveAnimSets(Filename$)

	F = WriteFile(Filename$)
	If F = 0 Then Return False

		For A.AnimSet = Each AnimSet
			WriteShort F, A\ID
			WriteString F, A\Name$
			For i = 0 To 149
				WriteString F, A\AnimName$[i]
				WriteShort F, A\AnimStart[i]
				WriteShort F, A\AnimEnd[i]
				WriteFloat F, A\AnimSpeed#[i]
			Next
		Next

	CloseFile(F)
	;Allows for animation sets to be recovered or something... cysis145
	G = WriteFile("Data\Game Data\Animations_debug.txt")
	If G = 0 Then Return False  
	For A.AnimSet = Each AnimSet
		WriteLine(G, "Anim ID: " + A\ID)
		WriteLine(G, "Anim Set: " + A\Name$)
		For i = 0 To 149
			If A\AnimName$[i] <> "" Then WriteLine(G, A\AnimName$[i] + "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~" + A\AnimStart[i] + "-" + A\AnimEnd[i])
		Next
		WriteLine(G, "")
		WriteLine(G, "")
	Next
	CloseFile(G)

	
	Return True

End Function

; Finds an animation in a given set
Function FindAnimation(A.AnimSet, AnimName$)

	AnimName$ = Upper$(AnimName$)
	For i = 0 To 149
		If Upper$(A\AnimName$[i]) = AnimName$ Then Return i
	Next
	Return -1

End Function

; Finds the current animation number of an actor
Function CurrentSeq(AI.ActorInstance)

	Seq = AnimSeq(AI\EN)
	If Seq > 0
		For i = 149 To 0 Step -1
			If AI\AnimSeqs[i] = Seq Then Return i
		Next
	EndIf
	Return -1

End Function