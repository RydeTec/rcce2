; A projectile instance
Type ProjectileInstance
	Field Target.ActorInstance
	Field TargetX#, TargetY#, TargetZ#
	Field EN, EmitterEN1, EmitterEN2
	Field TexID1, TexID2
	Field Speed#
End Type

; Creates a new projectile instance
Function CreateProjectile(Source.ActorInstance, Target.ActorInstance, MeshID, Homing, Speed#, Emitter1$, Emitter2$, TexID1, TexID2)

	; Create projectile
	Local P.ProjectileInstance = New ProjectileInstance
	P\TexID1 = -1
	P\TexID2 = -1
	P\Speed# = Speed# * 2.0
	If Homing = True
		P\Target = Target
	Else
		P\TargetX# = EntityX#(Target\CollisionEN)
		P\TargetY# = EntityY#(Target\CollisionEN)
		P\TargetZ# = EntityZ#(Target\CollisionEN)
	EndIf

	; Create main mesh
	If MeshID > -1 And MeshID < 65535
		P\EN = GetMesh(MeshID)
		If P\EN <> 0 Then ScaleEntity(P\EN, LoadedMeshScales#(MeshID), LoadedMeshScales#(MeshID), LoadedMeshScales#(MeshID))
	EndIf
	If P\EN = 0 Then P\EN = CreatePivot()

	; Create emitters
	If Emitter1$ <> ""
		Tex = GetTexture(TexID1)
		If Tex <> 0
			P\TexID1 = TexID1
			Config = RP_LoadEmitterConfig("Data\Emitter Configs\" + Emitter1$ + ".rpc", Tex, Cam)
			If Config <> 0
				P\EmitterEN1 = RP_CreateEmitter(Config)
				EntityParent(P\EmitterEN1, P\EN, False)
			EndIf
		EndIf
	EndIf
	If Emitter2$ <> ""
		Tex = GetTexture(TexID2)
		If Tex <> 0
			P\TexID2 = TexID2
			Config = RP_LoadEmitterConfig("Data\Emitter Configs\" + Emitter2$ + ".rpc", Tex, Cam)
			If Config <> 0
				P\EmitterEN2 = RP_CreateEmitter(Config)
				EntityParent(P\EmitterEN2, P\EN, False)
			EndIf
		EndIf
	EndIf

	; Initial position
	PositionEntity(P\EN, EntityX#(Source\CollisionEN), EntityY#(Source\CollisionEN), EntityZ#(Source\CollisionEN))

End Function

; Updates all projectile instances
Function UpdateProjectiles()

	For P.ProjectileInstance = Each ProjectileInstance
		; Move
		If P\Target <> Null
			P\TargetX# = EntityX#(P\Target\CollisionEN)
			P\TargetY# = EntityY#(P\Target\CollisionEN)
			P\TargetZ# = EntityZ#(P\Target\CollisionEN)
		EndIf
		PositionEntity(GPP, P\TargetX#, P\TargetY#, P\TargetZ#)
		PointEntity(P\EN, GPP)
		MoveEntity(P\EN, 0, 0, P\Speed# * Delta#)

		; Destroy when close enough to target
		If EntityDistance#(P\EN, GPP) < 2.0
			FreeProjectileInstance(P)
		EndIf
	Next

End Function

; Frees a projectile instance
Function FreeProjectileInstance(P.ProjectileInstance)

	If P\TexID1 > -1 Then UnloadTexture(P\TexID1)
	If P\TexID2 > -1 Then UnloadTexture(P\TexID2)
	If P\EmitterEN1 <> 0
		EntityParent(P\EmitterEN1, 0)
		RP_KillEmitter(P\EmitterEN1, True, False)
	EndIf
	If P\EmitterEN2 <> 0
		EntityParent(P\EmitterEN2, 0)
		RP_KillEmitter(P\EmitterEN2, True, False)
	EndIf
	FreeEntity(P\EN)
	Delete(P)

End Function