;
;	Early Scripting Language DEMO for createing a scene
;	in Blitz3d. Editer to follow
;	dave@birdie72.freeserve.co.uk
;	David Bird(Birdie)
;

Global DEBUG=1
Global version$="v0.41"
Const C_LIGHT=5001
Const C_CUBE=10001
Const C_SPHERE=10002
Const C_CYLINDER=10003
Const C_CONE=10004
Const C_PLANE=10005

Graphics3D 640,480
SetBuffer BackBuffer()

Dim lines$(10000)

piv=CreatePivot()
cam=CreateCamera(piv)
PositionEntity cam,0,0,-8


If DEBUG=0 Then 
	Load_Script("test1.scn")
Else
	Read COUNT
	For r=1 To COUNT
		Read a$
		lines$(r)=a$
		Process_Line(a$)
	Next
End If

;WireFrame True
While Not KeyDown(1)
	TurnEntity piv,.5,1,0
	UpdateWorld
	RenderWorld
	Text 0,0,"Script is as shown."
	For r=1 To count
		Text 0,r*15,Lines$(r)
	Next
	Flip
Wend
Save_Script("test1.scn")

FreeEntity cam
EndGraphics
End


Function Process_Line(com$)
	Local wrd$[200]
	;
	;Process one line of scene map
	;
	For w=1 To 200
		wrd[w]=""
	Next
	w=1
	For p=1 To Len(com$)
		c$=Mid$(com$,p,1)
		If c$="#" Then Return EOL							;ignor comments
		If c$=" " Or c$="(" Or c$=")" Or c$="," Then
			w=w+1
		Else
			wrd[w]=wrd[w]+c$
		End If
	Next
	id=-1
	
	For a=1 To w
		Select wrd[a]
			Case "ENDSCENE"
				Return
			Case "NEW"
				ob.entity=New entity
				ob\piv=CreatePivot()
				ob\sx=1:ob\sy=1:ob\sz=1
				ob\rx=1:ob\ry=1:ob\rz=1
				ob\segs=20
				ob\alpha=1
				ob\texid=0
				ob\r=255
				ob\g=255
				ob\b=255
			Case "LOADTEXTURE"
				tex.textures=New textures
				tex\tex=LoadTexture(wrd[a+1])
				tex\su=wrd[a+3]
				tex\sv=wrd[a+4]
				tex\id=wrd[a+2]
				tex\file=wrd[a+1]
				ScaleTexture tex\tex,tex\su,tex\sv
				a=a+4
			Case "N"
				id=wrd[a+1]
				a=a+1
			Case "LIGHT"
				ob\ent=CreateLight(wrd[a+1],ob\piv)
				ob\typ=C_LIGHT
				ob\name="LIGHT"+id
				ob\segs=wrd[a+1]
				If DEBUG=1 Then
					ScaleEntity CreateCube(ob\ent),.1,.1,.1
				End If
				ob\r=wrd[a+2]
				ob\g=wrd[a+3]
				ob\b=wrd[a+4]
				LightColor ob\ent,ob\r,ob\g,ob\b
				a=a+4
			Case "PRIM"											;Create a New Object
				ob\name=wrd[a+1]+id
				id=-1
				Select wrd[a+1]
					Case "CUBE"
						ob\ent=CreateCube(ob\piv)
						ob\typ=C_CUBE
						a=a+2
					Case "SPHERE"
						If wrd[a+2]>=3 Then 
							ob\ent=CreateSphere(wrd[a+2],ob\piv)
						Else
							ob\ent=CreateSphere(10,ob\piv)
						End If
						ob\typ=C_SPHERE
						a=a+2
					Case "CYLINDER"
						If wrd[a+2]>=3 Then 
							ob\ent=CreateCylinder(wrd[a+2],ob\piv)
						Else
							ob\ent=CreateCylinder(10,ob\piv)
						End If
						ob\typ=C_CYLINDER
						a=a+2
					Case "CONE"
						If wrd[a+2]>=3 Then 
							ob\ent=CreateCone(wrd[a+2],True,ob\piv)
						Else
							ob\ent=CreateCone(10,True,ob\piv)
						End If
						ob\typ=C_CONE
						a=a+2
					Case "PLANE"
						ob\ent=CreateMesh(ob\piv)
						surf=CreateSurface(ob\ent)
						AddVertex surf,-1,-1,0,0,0
						AddVertex surf,-1,1,0,0,1
						AddVertex surf,1,1,0,1,1
						AddVertex surf,1,-1,0,1,0
						AddTriangle surf,0,1,2
						AddTriangle surf,2,3,0
						ob\segs=-1
						If wrd[a+2]="TRUE" Then
							AddTriangle surf,2,1,0
							AddTriangle surf,0,3,2
							ob\segs=1
						End If
						ob\typ=C_PLANE
						a=a+2
				End Select
				NameEntity ob\ent,ob\name
			Case "SIZE"
				ob\sx=wrd[a+1]
				ob\sy=wrd[a+2]
				ob\sz=wrd[a+3]
				ScaleEntity ob\ent,ob\sx,ob\sy,ob\sz
				a=a+3
			Case "ROTATE"
				ob\rx=wrd[a+1]
				ob\ry=wrd[a+2]
				ob\rz=wrd[a+3]
				RotateEntity ob\ent,ob\rx,ob\ry,ob\rz
				a=a+3
			Case "POS"
				ob\px=wrd[a+1]
				ob\py=wrd[a+2]
				ob\pz=wrd[a+3]
				PositionEntity ob\ent,ob\px,ob\py,ob\pz
				a=a+3
			Case "TEXTURE"
				ob\texid=wrd[a+1]
				tx=Get_Texture(ob\texid)
				EntityTexture ob\ent,tx
			Case "ALPHA"
				ob\alpha=wrd[a+1]
				EntityAlpha ob\ent,ob\alpha
				a=a+1
			Case "COLOR"
				ob\r=wrd[a+1]
				ob\g=wrd[a+2]
				ob\b=wrd[a+3]
				EntityColor ob\ent,ob\r,ob\g,ob\b
				a=a+3
		End Select
	Next
End Function

Function Get_Texture(id)
	For a.textures=Each textures
		If a\id=id Then Return a\tex
	Next
End Function

Function Load_Script(file$="Temp.Scn")
	files=ReadFile(file$)
	While Not Eof(files)
		a$=ReadLine(files)
		Process_Line(a$)
	Wend
End Function

Function Save_Script(file$="Temp.Scn")
	files=WriteFile(file$)
	WriteLine files,"# Scene Script Language (c)2001 David Bird"
	WriteLine files,"# "+version$+" October 8th 2001."
	WriteLine files,"# "+MilliSecs()
	
	cnt=0
	For t.textures=Each textures
		WriteLine files,"LOADTEXTURE("+t\file+","+t\id+","+t\su+","+t\sv+")"
	Next
	
	For a.Entity=Each Entity
		cnt=cnt+1
		ln$="N "+cnt+" NEW "
		If a\typ>=C_CUBE Then
			ln$=ln$+"PRIM "
			Select a\typ
				Case C_Cube
					ln$=ln$+"CUBE(0) "
				Case C_Sphere
					ln$=ln$+"SPHERE("+a\segs+") "
				Case C_Cylinder
					ln$=ln$+"CYLINDER("+a\segs+") "
				Case C_Cone
					ln$=ln$+"CONE("+a\segs+") "
				Case C_Plane
					ln$=ln$+"PLANE("
					If a\segs>0 Then ;double sided
						ln$=ln$+"TRUE) "
					Else
						ln$=ln$+"FALSE) "
					End If
					
			End Select
			ln$=ln$+"SIZE("+a\sx+","+a\sy+","+a\sz+") "		;Can only alter the size of prims/meshes
			If a\r<255 Or a\g<255 Or a\b<255 Then
				ln$=ln$+"COLOR("+a\r+","+a\g+","+a\b+") "
			End If
		Else	;not a primative etc
			Select a\typ
				Case C_LIGHT
					ln$=ln$+"LIGHT("+a\segs+","+a\r+","+a\g+","+a\b+") "
			End Select
		End If

		ln$=ln$+"ROTATE("+a\rx+","+a\ry+","+a\rz+") "
		ln$=ln$+"POS("+a\px+","+a\py+","+a\pz+") "
		If a\texid>0 Then
			ln$=ln$+"TEXTURE("+a\texid+") "
		End If
		If a\alpha<1 Then
			ln$=ln$+"ALPHA("+a\alpha+") "
		End If
		WriteLine files,ln$
	Next
End Function

Type Entity
	Field piv
	Field ent
	Field typ
	Field name$
	Field sx# , sy# , sz#
	Field rx# , ry# , rz#
	Field px# , py# , pz#
	Field segs
	Field texid
	Field alpha#
	Field r,g,b
End Type

Type textures
	Field file$
	Field tex
	Field id
	Field su#,sv#
End Type

;
;	Commands are as follows
;	LOADTEXTURE(filename,textureid,scaleu,scalev)	-loads a texture into memory stored in textureid
;	NEW												-new object
;	PRIM											-primative object
;	LIGHT(lighttype,red,grn,blu)					-create a light (type,color red,color green,color blue)
;	ALPHA(val)										-sets the alpha for an object
;	SIZE(x,y,z)										-sets the scale of an object
;	POS(x,y,z)										-Position an entity in world at x,y and z
;	ROTATE(ax,ay,az)								-rotate an object
;	TEXTURE(textureid)								-add a texture to the entity
;	COLOR(red,green,blue)							-colour an entity
;	CUBE(segs)										-creates a cube(0) no segments are implimented
;	SPHERE(segs)									-Creates a sphere primative
;	CONE(segs)										-Creates a cone primative
;	CYLINDER(segs)									-Creates a cylinder primative
;	PLANE(segs)										-Creates a Plane primative.

;How many lines	
Data 7
Data "LOADTEXTURE(tex0.bmp,1,.1,.1)"
Data "N 1 NEW PRIM PLANE(TRUE) ROTATE(0,0,0) SIZE(1,1,1) POS(-2,0,0)"
Data "N 2 NEW PRIM CUBE(0) ROTATE(0,0,0) SIZE(1,1,1) POS(2,0,0)"
Data "N 3 NEW PRIM SPHERE(0) ROTATE(0,0,0) SIZE(1,1,1) POS(0,2,0)"
Data "N 4 NEW PRIM CONE(30) POS(0,-2,0) COLOR(255,0,0)"
Data "N 5 NEW PRIM CYLINDER(3) POS(0,0,0) TEXTURE(1) ALPHA(0.5)"
Data "N 6 NEW LIGHT(2,0,0,255) POS(4,4,4)"