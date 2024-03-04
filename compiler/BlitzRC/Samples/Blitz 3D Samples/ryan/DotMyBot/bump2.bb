; Dot3 Lighting example
;
; By Mikkel Fredborg
; Model and texture from http://www.soclab.bth.se/practices/orb.html

Type Dot3Light
	Field ent
	Field mul#
	Field typ
End Type

Graphics3D 640,480,0,2

eyetex = LoadTexture("eye.jpg",1+8+16+32)
decl=LoadTexture( "derhead_decal.jpg",1+8)
bump=LoadTexture( "derhead_local.tga",1+8)
bump2=LoadTexture("water.jpg")
cube=LoadMesh("head.x")
EntityFX cube,1+2
TextureBlend bump,4
TextureBlend bump2,4
EntityTexture cube,bump,0,0
EntityTexture cube,bump2,0,1
EntityTexture cube,decl,0,2

eye = CreateSphere(12,cube)
ScaleEntity eye,0.091,0.091,0.091
PositionEntity eye,-0.17,0.12,-0.33
EntityShininess eye,1.0
EntityTexture eye,eyetex
RotateMesh eye,90,0,0

eye2 = CreateSphere(12,cube)
ScaleEntity eye2,0.091,0.091,0.091
PositionEntity eye2,0.17,0.12,-0.33
EntityShininess eye2,1.0
EntityTexture eye2,eyetex
RotateMesh eye2,90,0,0

UpdateNormals cube

light = Dot3_CreateLight(2)
lighticon = CreateSphere(8,light)
EntityFX lighticon,1
ScaleEntity lighticon,0.1,0.1,0.1
Dot3_LightRange light,8

camera=CreateCamera()
CameraClsColor camera,100,120,130
PositionEntity camera,0,0,-2.0
CameraRange camera,0.1,100

a# = 180.0

AmbientLight 0,0,0

declon = True
bumpon = True
bump2on = True

While Not KeyHit(1)

	a = a + 0.5
	If a = 360.0 Then a = 0
	PositionEntity light,Cos(a)*5,Sin(a)*5,-9

	If KeyHit(2)
		If declon
			TextureBlend decl,0
		Else
			TextureBlend decl,2
		End If
		declon = Not declon
	End If
	If KeyHit(3)
		If bumpon
			TextureBlend bump,0
			EntityFX cube,0
		Else
			TextureBlend bump,4
			EntityFX cube,1+2
		End If
		bumpon = Not bumpon
	End If
	If KeyHit(4)
		If bump2on
			TextureBlend bump2,0
			EntityFX cube,0
		Else
			TextureBlend bump2,4
			EntityFX cube,1+2
		End If
		bump2on = Not bump2on
	End If

	If KeyDown(200) TurnEntity cube, 1,0,0
	If KeyDown(208) TurnEntity cube,-1,0,0
	
	If KeyDown(205) TurnEntity cube,0,-1,0
	If KeyDown(203) TurnEntity cube,0, 1,0
	
	If KeyDown(30) TranslateEntity camera,0,0,0.01
	If KeyDown(44) TranslateEntity camera,0,0,-0.01
	
	RotateEntity cube,EntityPitch(cube),EntityYaw(cube),0
	
	PointEntity eye,camera
	PointEntity eye2,camera
	
	If bumpon Or bump2on
		UpdateBumpNormals(cube,light)
	End If
	
	UpdateWorld
	RenderWorld
	
	Text GraphicsWidth()/2,0,"1 - Toggle Texture | 2 - Toggle Bumpmap",True,False
	Flip
Wend

Function UpdateBumpNormals(mesh,light,lighttype=0)

	n_surf = CountSurfaces(mesh)
	For s = 1 To n_surf
		surf = GetSurface(mesh,s)
		n_vert = CountVertices(surf)-1
		For v = 0 To n_vert
			red2# = 0.0
			grn2# = 0.0
			blu2# = 0.0	
			For d3l.Dot3Light = Each Dot3Light
				If d3l\typ = 1 ; Directional light
					TFormVector 0,0,1,d3l\ent,0
					nx# = TFormedX()
					ny# = TFormedY()
					nz# = TFormedZ()
					TFormNormal VertexNX(surf,v),VertexNY(surf,v),VertexNZ(surf,v),mesh,0
					red# = TFormedX()
					grn# = TFormedY()
					blu# = TFormedZ()
				ElseIf d3l\typ = 2 ; Point light
					TFormNormal VertexNX(surf,v),VertexNY(surf,v),VertexNZ(surf,v),mesh,0
					nx# = -TFormedX()
					ny# = -TFormedY()
					nz# = -TFormedZ()
					TFormPoint VertexX(surf,v),VertexY(surf,v),VertexZ(surf,v),mesh,0
					red# = TFormedX()-EntityX(d3l\ent,True)
					grn# = TFormedY()-EntityY(d3l\ent,True)
					blu# = TFormedZ()-EntityZ(d3l\ent,True)
					d# = Sqr(red*red + grn*grn + blu*blu)
					red = red/d
					grn = grn/d
					blu = blu/d
				Else
					RuntimeError "Do it yourself, will ya?"
				End If

				dot# = (red*nx + grn*ny + blu*nz)
				If dot<0.0 Then dot = 0.0	

				red# = ((1.0+(red*dot))*127.5)*d3l\mul
				grn# = ((1.0+(grn*dot))*127.5)*d3l\mul
				blu# = ((1.0+(blu*dot))*127.5)*d3l\mul
				If red<0 Then red = 0
				If grn<0 Then grn = 0
				If blu<0 Then blu = 0				
				red2# = red2+red
				grn2# = grn2+grn
				blu2# = blu2+blu	
			Next
			
			VertexColor surf,v,red2,grn2,blu2
		Next
	Next

End Function

Function Dot3_CreateLight(typ=1,parent=0,real=True)
	
	d3l.Dot3Light = New Dot3Light
	
	If real
		d3l\ent = CreateLight(typ,parent)
	Else
		d3l\ent = CreatePivot(parent)
	End If

	d3l\typ = typ
	d3l\mul = 1.0
	
	Return d3l\ent

End Function

Function Dot3_LightRange(ent,range#)

	For d3l.dot3light = Each dot3light
		If d3l\ent = ent
			If Lower$(EntityClass(d3l\ent))="light"
				LightRange d3l\ent,range
				Return
			End If
		End If
	Next
	
End Function

Function Dot3_LightIntensity(ent,intens#)
	For d3l.dot3light = Each dot3light
		If d3l\ent = ent
			d3l\mul = intens
			If Lower$(EntityClass(d3l\ent))="light"
				LightColor d3l\ent,d3l\mul*255.0,d3l\mul*255.0,d3l\mul*255.0
			End If			
			Return
		End If
	Next
End Function


End