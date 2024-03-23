;
;	Shadow trials
;	dave@birdie72.freeserve.co.uk
;
Graphics3D 640,480
SetBuffer BackBuffer()

;create two images
grab=CreateImage(100,100,1)
grb2=CreateImage(100,100,1)

;Set up all entities here
piv=CreatePivot()
camera=CreateCamera(piv)
PositionEntity camera,0,15,-11
TurnEntity camera,40,0,0
lit=CreateLight()

;This one is for the shadow (NO TEXTURE)
dragon=LoadMD2("model\dragon.md2"):ScaleEntity dragon,.05,.05,.05
RotateEntity dragon,0,180,0
AnimateMD2 dragon,1,.2,0,84
PositionEntity dragon,0,5,0

dragon2=CopyEntity(dragon) ; Copy the Same dragon but texture this one
AnimateMD2 dragon2,1,.2,0,84
PositionEntity dragon2,0,10,0
tex=LoadTexture( "model\dragon.bmp" )
EntityTexture dragon2,tex

terrain=LoadTerrain("hmap.bmp")
PositionEntity terrain,-32,0,-32
ScaleEntity terrain,2,9,2
TerrainShading terrain,True
tex2=LoadTexture("rock.bmp")
ScaleTexture tex2,3,3
EntityTexture terrain,tex2
While Not KeyDown(1)

	xm=270;MouseX()
	ym=220;MouseY()

	;hide terrain etc.
	HideEntity terrain
	HideEntity dragon2
	ShowEntity dragon

	;Spin the camera
	TurnEntity piv,0,.4,0

	;Render the first dragon to grab the stencil
	RenderWorld
	
	;and grab it
	GrabImage grab,xm,ym
	
	;Now make the stencil
	Make_Sten(grab)

	;hide the first dragon and display everything else	
	ShowEntity terrain
	ShowEntity dragon2
	HideEntity dragon
	
	;rerender again
	RenderWorld
	
	;Now grab the render
	GrabImage grb2,xm,ym

	;mask off shadow and create new image
	ShadowImage(grb2,grab)
	;And display it
	DrawImage grb2,xm,ym
	
	;show the first dragon before updating the world
	ShowEntity dragon
	UpdateWorld
	Flip
Wend

EndGraphics
End

;
;	Make a stencil from an image
;
Function Make_Sten(image)
	LockBuffer ImageBuffer(image)
	For y=0 To ImageHeight(image)-1
		For x=0 To ImageWidth(image)-1
			rgb=ReadPixelFast(x,y,ImageBuffer(image)) And $ffffff
			If rgb=0 Then
				rgb=COLORRGB(255,0,0)
			Else
				rgb=0
			End If
			WritePixelFast x,y,rgb,ImageBuffer(image)
		Next
	Next
	UnlockBuffer ImageBuffer(image)
	Return
End Function

;
;	Shadow the image using a stencil
;
Function ShadowImage(source,stencil)
	LockBuffer ImageBuffer(source)
	LockBuffer ImageBuffer(stencil)
	For y=0 To ImageHeight(source)-1 Step 1
		For x=0 To ImageWidth(source)-1 Step 1
			rgb=ReadPixelFast(x,y,ImageBuffer(stencil)) And $ffffff
			If rgb=0 Then
				rgb2=ReadPixelFast(x,y,ImageBuffer(source)) And $ffffff
				WritePixelFast x,y,rgb2 Shr 1,ImageBuffer(source)
			End If
		Next
	Next
	UnlockBuffer ImageBuffer(source)
	UnlockBuffer ImageBuffer(stencil)

End Function

Function COLORRGB(r,g,b)
	 Return (((r Shl 8) Xor g) Shl 8) Xor b
End Function