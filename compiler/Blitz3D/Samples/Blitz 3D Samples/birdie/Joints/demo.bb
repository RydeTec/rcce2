;
;	Joints Demo
;	control with mouse
;	David Bird
;	dave@birdie72.freeserve.co.uk
;

Graphics3D 640,480
SetBuffer BackBuffer()

;Setup the camrea etc
piv=CreatePivot()
camera=CreateCamera(piv)
PositionEntity camera,0,0,-8

;Create the object to morph
;this case a sphere. Scale it and add a texture
sphere=CreateSphere(20)
ScaleMesh sphere,4,1,1
tex=LoadTexture("banana.bmp")
EntityTexture sphere,tex

;add a light
light=CreateLight()

;Now add the joints
;
j1.Joint=Add_Joint(sphere,-2,0,0,Null)
j2.Joint=Add_Joint(sphere,1,0,0,j1)

;Main loop
While Not KeyDown(1)
	;toggle wireframe on/off
	If KeyHit(17) Then w=1-w
	WireFrame w

	;Get user input
	px1#=0:px2#=0
	py1#=0:py2#=0
	If MouseDown(2) Then 
		px1=Float(MouseXSpeed()/10)
		py1=Float(MouseYSpeed()/10)
	End If
	If MouseDown(1) Then 
		px2=Float(MouseXSpeed()/10)
		py2=Float(MouseYSpeed()/10)
	End If
	
	;Do incremental rotations of the joints
	Rotate_Joint(j2,0,px1,py1)
	Rotate_Joint(j1,0,px2,py2)

	;Reset position of mouse
	MoveMouse GraphicsWidth()/2,GraphicsHeight()/2
	
	;Spin the camera a bit
	TurnEntity piv,0,.3,0
	
	;Update and render world
	UpdateWorld
	RenderWorld
	Text 0,0,"Hold Left MB and move mouse to control left Joint"
	Text 0,15,"Hold Right MB and move mouse to control Right Joint"
	Text 0,30,"Vertex Morphing using Joints"
	Text 0,45,"Toggle W for wireframe"
	Text 0,60,"dave@birdie72.freeserve.co.uk"
	Flip
Wend

EndGraphics
End

;Joint Data(only simple at the moment
Type Joint
	Field surf		;surface that the joint is on
	Field mesh		;mesh to deform
	Field jvertex	;vertex that is the joint
	Field count		;how many vertices does the joint control
	Field cverts[1500];array holding the vertex list
End Type

;Add a joint the the mesh
;** At the moment you can only go from left to right
;I suppose to could specify the verts you want to control
Function Add_Joint.Joint(mesh,x#,y#,z#,parent.Joint)
	j.Joint=New Joint
	j\count=0
	j\mesh=mesh
	j\surf=GetSurface(mesh,1)
	For a=0 To CountVertices(j\surf)-1
		If VertexX(j\surf,a)>=x Then ;**
			j\count=j\count+1
			j\cverts[j\count]=a
		End If
	Next
	j\jvertex=AddVertex(j\surf,x,y,z)
	;If this is a child joint add its pivotpoint to the parents list
	If parent<>Null Then
		parent\count=parent\count+1
		parent\cverts[parent\count]=j\jvertex
	End If
	Return j	
End Function

;Do the rotation
Function Rotate_Joint(j.joint,ax#,ay#,az#)
	piv=CreatePivot(j\mesh)
	px#=VertexX(j\surf,j\jvertex)
	py#=VertexY(j\surf,j\jvertex)
	pz#=VertexZ(j\surf,j\jvertex)
	PositionEntity piv,px,py,pz
	RotateEntity piv,ax,ay,az
	For a=1 To j\count
		x#=VertexX(j\surf,j\cverts[a])-px
		y#=VertexY(j\surf,j\cverts[a])-py
		z#=VertexZ(j\surf,j\cverts[a])-pz
		TFormPoint x,y,z,piv,mesh
		x=TFormedX()
		y=TFormedY()
		z=TFormedZ()
		VertexCoords j\surf,j\cverts[a],x,y,z
	Next
	FreeEntity piv
End Function