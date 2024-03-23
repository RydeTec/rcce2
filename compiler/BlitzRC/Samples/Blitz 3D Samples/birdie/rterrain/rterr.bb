;
;	Rolling Terrain
;	Just a quick demo of how to do it
;	texturemapping the terrain may be the only problem
;	just need to calc the offset position of the texture
;	David Bird
;	dave@birdie72.freeserve.co.uk


Graphics3D 640,480
SetBuffer BackBuffer()


Global mapsquare=256				;Square size of the heightmap
Dim map#(mapsquare,mapsquare)		;Ive had this 2048^2 no problem. a bit slow to create map though
Global Gridsegs=30					;How many segments in the terrain ** Careful here **

Create_MapLayout("hmap.bmp")

camera=CreateCamera()
CameraRange camera,1,800
CameraClsColor camera,30,90,200
CameraFogRange camera,300,800
CameraFogColor camera,30,90,200

CameraFogMode camera,1

PositionEntity camera,0,200,-0
Global MAPMESH=CreateMesh()

Global texture=LoadTexture("tex0.bmp")
ScaleTexture texture,1.0/Float(gridsegs),1.0/Float(gridsegs)


light=CreateLight()
TurnEntity light,15,30,0
x#=128
z#=4
Color 255,255,255

While Not KeyDown(1)
	If KeyHit(17) Then w=1-w
	WireFrame w
	;Render the terrain at grid offset x,z
	Render_Terrain(x#,z#)
	
	;shift the terrain in x
	If KeyDown(203) Then x=x-.35
	If KeyDown(205) Then x=x+.35
	
	;move then camera up and down
	If KeyDown(200) And EntityY(camera)>10 Then MoveEntity camera,0,-2,0
	If KeyDown(208) And EntityY(camera)<200 Then MoveEntity camera,0,2,0
	
	;speed over the terrain
	z=z+.25

	UpdateWorld
	RenderWorld
	Text 0,0,"Press w to toggle wireframe"
	Text 0,15,"Use the cursors to move up,down,left and right"
	Text 0,30,"CamPos X"+EntityX(camera)+","+EntityY(camera)+","+EntityZ(camera)

	Flip
Wend
FreeEntity MAPMESH
EndGraphics
End

;Wrapper function to CreateMap
Function Render_Terrain(xpos#,zpos#)
	FreeEntity MAPMESH
	MAPMESH=Createmap(1500,GridSegs,195,xpos,zpos)
	EntityTexture MAPMESH,texture
End Function

;At the moment this function just recreates the mesh
;totally. Hint to speed this up setup two functions
;the second function should only alter the vertices
;and not rebuild the mesh complete
Function CreateMap(size#,segs,scaley#=1,mapstartx#=0,mapstartz#=0)

	diff#=size/Float(segs)
	ox#=-diff*(mapstartx-Floor(mapstartx))
	oz#=-diff*(mapstartz-Floor(mapstartz))
	mesh=CreateMesh()
	surf=CreateSurface(mesh)
	stx#=-size/2
	stz#=stx
	stp#=size/segs
	z#=stz+50
	mz=Floor(mapstartz)
	
	voff#=0;	todo sort out the texture offset
	uoff#=0;	position

	For a=0 To segs
		x#=stx
		v#=a/Float(segs)
		mx=Floor(mapstartx)
		
		For b=0 To segs
			u#=b/Float(segs)
			If mz<0 Then 
				h#=map(mx Mod mapsquare,mapsquare+mz)
			Else
				h#=map(mx Mod mapsquare,mz Mod mapsquare)
			End If
			AddVertex surf,x,h*scaley,z,u+uoff,v+voff
			x=x+stp
			mx=mx+1
		Next
		mz=mz+1
		z=z+stp
	Next
	For a=0 To segs-1
		For b=0 To segs-1
			v0=a*(segs+1)+b:v1=v0+1
			v2=(a+1)*(segs+1)+b+1:v3=v2-1
			AddTriangle surf,v0,v2,v1
			AddTriangle surf,v0,v3,v2
		Next
	Next
	PositionEntity mesh,ox,0,oz
	UpdateNormals mesh
	Return mesh
End Function

;Function to load in a hieghtmap
;into an array from its red component
Function Create_MapLayout(file$)
	hmap=LoadImage(file)
	SetBuffer ImageBuffer(hmap)
	For z=0 To mapsquare-1
		For x=0 To mapsquare-1
			GetColor x,z
			map(x,z)=Float(ColorRed())/255
		Next
	Next
	SetBuffer BackBuffer()
	FreeImage hmap
End Function