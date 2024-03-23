Graphics3D 640,480,16,2
SetBuffer BackBuffer()


wpivot=CreatePivot()

mecha=LoadAnimMesh("walkanim.3ds")

cam=CreateCamera(wpivot)
PositionEntity cam,0,0,150

lite=CreateLight(2,cam)
PositionEntity lite,0,64,0

PointEntity cam,mecha

PositionEntity cam,0,450,300

TurnEntity cam,35,0,0

Animate mecha,1,.25


neckjoint=FindChild(mecha,"Neck")
gun1=FindChild(mecha,"GunR")
gun2=FindChild(mecha,"GunL")
shot1=FindChild(mecha,"ShooterR")
shot2=FindChild(mecha,"ShooterL")

neckrot#=0:gunrot#=0:mechrot#=0:gunspeed#=0:shotrot#=0:mecht#=0

MoveMouse 320,240
FlushMouse

Repeat

	Flip

	If KeyDown(203)
		mechrot=mechrot+.5
	EndIf

	If KeyDown(205)
		mechrot=mechrot-.5
	EndIf

	If KeyDown(200)
		mecht=mecht-.5
	EndIf

	If KeyDown(208)
		mecht=mecht+.5
	EndIf

	If MouseDown(1)
		If gunspeed<25
			gunspeed=gunspeed+.5
		EndIf
	Else
		If gunspeed>0
			gunspeed=gunspeed-.2
		EndIf
	EndIf	

	If gunspeed<.1 Then gunspeed=0

	neckrot=neckrot-MouseXSpeed()/2
	gunrot=gunrot+MouseYSpeed()/2
	MoveMouse 320,240

	If neckrot> 90 Then neckrot= 90
	If neckrot<-90 Then neckrot=-90
	If gunrot > 55 Then gunrot = 55
	If gunrot <-40 Then gunrot =-40
	shotrot=(shotrot+gunspeed) Mod 360

;	PointEntity cam,sigte1,-EntityRoll(cam,1) 

	UpdateWorld

	TurnEntity neckjoint,0,neckrot,0
	TurnEntity gun1,0,0,gunrot
	TurnEntity gun2,0,0,gunrot
	TurnEntity shot1,0,0,shotrot
	TurnEntity shot2,0,0,shotrot

	RotateEntity mecha,mecht,mechrot,0

	RenderWorld

Until KeyHit(1)
End