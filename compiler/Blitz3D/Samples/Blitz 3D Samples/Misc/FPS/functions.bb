Global spark=LoadSprite( "artwork\frags\bluspark.bmp" )
EntityAlpha spark, 0

Function functions()
	updateparticles()
End Function

Type frags
	Field speed#, entity, alpha#
End Type

Function createparticle(x#,y#,z#)
	For a = 1 To 5
		f.frags = New frags
		f\entity = CopyEntity(spark)
		PositionEntity f\entity, x#, y#, z#
		f\speed# = Rnd(3,4)
		f\alpha# = 1
		RotateEntity f\entity, Rand(360), Rand(360), Rand(360)
		EntityColor f\entity, Rand(255), Rand(255), Rand(255)
		EntityAlpha f\entity, f\alpha#
	Next
End Function


Function updateparticles()
	For f.frags = Each frags
		If f\alpha# > 0
			MoveEntity f\entity, 0, 0, f\speed#
			f\alpha# = f\alpha# - 0.1
		Else
			FreeEntity f\entity
			Delete f
		EndIf
	Next
End Function


Function createdecal(x#, y#, z#)
	For g.gun = Each gun
		If g\selected = 1
			dumpvar = CopyEntity(g\decal)
		;	ScaleEntity dumpvar, 20, 20, 20
			PositionEntity dumpvar, x#, y#, z#
			RotateEntity dumpvar, EntityPitch(camera), EntityYaw(camera), EntityRoll(camera)
			EntityAlpha dumpvar, 1
			MoveEntity dumpvar, 0, 0, -1
		EndIf
	Next
End Function