;
;	Camera Pick and parenting entities
;	dave@birdie72.freeserve.co.uk
;	David Bird(Birdie)
;
Graphics3D 640,480
SetBuffer BackBuffer()

cam=CreateCamera()
PositionEntity cam,0,0,-6

light=CreateLight()
TurnEntity light,45,45,0

Type blob
	Field ent		;entity eg blob
	Field x#,y#,z#	;rotation
End Type

Global sp=CreateSphere(5)
EntityPickMode sp,2
HideEntity sp

;Stage pivot
stage=CreatePivot()

;Add the First Blob the the scene
Add_Blob(stage,0,0,0)

While Not KeyDown(1)
	xm=MouseX()
	ym=MouseY()
	
	; Left Click the add a blob
	If MouseHit(1) Then
		ent=CameraPick(cam,xm,ym)
		If ent<>0 Then
			Add_Blob(ent,PickedX(),PickedY(),PickedZ())
		End If
	End If
	
	;Spin the stage around
	If MouseDown(2) Then
		TurnEntity stage,Float(MouseXSpeed()/10),0,Float(MouseYSpeed()/10)
		MoveMouse 320,200
	End If

	If KeyHit(57) Then f=1-f
	;Update all the blobs if not frozen
	If f=0 Then Update_Blobs()

	;Update the world and render
	UpdateWorld
	RenderWorld

	;Draw the cursor	
	Color Rand(100,255),0,0
	Line xm-3,ym,xm+3,ym
	Line xm,ym-3,xm,ym+3
	
	Color 255,255,255
	Text 0,0,"Left Click the add a blob. Right Click n Hold to Spin the stage."
	Text 0,15,"Last entity Picked:"+ent+" PX:"+PickedX()+" PY:"+PickedY()+" PZ:"+PickedZ()
	If f=0 Then a$="Running." Else a$="Frozen"
	Text 0,30,"Press Space the Freeze rotation:"+a$
	Text 0,45,"dave@birdie72.freeserve.co.uk"
	Flip
Wend

EndGraphics
End

Function Update_Blobs()
	For a.blob=Each blob
		TurnEntity a\ent,a\x,a\y,a\z
	Next
End Function

Function Add_Blob(par,x#,y#,z#)
	a.blob=New blob
	a\ent=CopyEntity(sp,par)
	PositionEntity a\ent,x,y,z,True
	EntityColor a\ent,Rand(100,255),Rand(100,255),Rand(100,255)
	s#=Rnd(.8,1)
	ScaleEntity a\ent,s,s,s
	a\x=Rnd(-1,1)
	a\y=Rnd(-1,1)
	a\z=0
End Function


	
	