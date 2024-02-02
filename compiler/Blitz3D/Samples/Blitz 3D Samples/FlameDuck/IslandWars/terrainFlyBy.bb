Graphics3D 640,480
SetBuffer BackBuffer()

Type shot
	Field life,ID
End Type

Type exhaust
	Field ID
End Type

Type smoke
	Field ID,life
End Type

Type scriptentry
	Field sdat$
End Type

Type model
	Field number,ID
End Type

Type weapon
	Field number,ID,sID,life,recycle
End Type

Type hardpoint
	Field ID,group,recycle,number,weapon,wID
End Type

Type ChaseCam
	Field entity,camera,target,heading,sky
End Type

;World Entity Globals
Global terra=0,camroot=0,cam=0,lightpivot=0,house12=0,smokespr=0
;ShadowMesh Globals
Global shademesh=0,shadesurf=0
;Control Globals
Global banking#=0,pitch#=0
;Collision Globals
Global col_terra=10,col_craft=20,col_scene=30,col_bullit=40

;Temporary Globals (?)
Global wps=0,hps=0,m.model=First model,watr=0


Local shadowres=1:angle#=0:wya#=.01:wy#=16

Local sv0y#=0,sv1y#=0,sv2y#=0,sv3y#=0,sv4y#=0,sv5y#=0,sv6y#=0,sv7y#=0



;Execute external configuration script
ParseScript("script.txt")
BuildWorld()

m.model=First model
ShowEntity m\ID

PurgeHardpoints()
hps=GetHardpoints(m\ID)
wps=0
For w.weapon=Each weapon
	wps=wps+1
Next

If shadowres=0
	BuildShadowMesh()
Else
	BuildShadowMesh2()
EndIf

OptionsMenu()

;PositionEntity cam,0,0,-3
;PointEntity cam,camroot

FreeEntity cam
FreeEntity camroot

CreateChaseCam(m\ID)

ShowEntity m\ID
UpdateNormals m\ID
;PositionEntity m\ID,0,-20,0

; Begin main loop

Repeat
	ms=MilliSecs()

	jx=JoyX():jy=JoyY()

	If jx<.001 And jx>-.001
		jx=0
	EndIf

	If jy<.001 And jy>-.001
		jy=0
	EndIf

	If KeyDown(200) Then jy=jy-1
	If KeyDown(208) Then jy=jy+1

	If KeyDown(203) Then jx=jx-1
	If KeyDown(205) Then jx=jx+1


	tpitch#=tpitch+jy
	dpitch#=(tpitch-rpitch#)*.2
	rpitch=rpitch+dpitch

	tturn#=tturn+jx
	dturn#=(tturn-rturn#)*.2
	rturn=rturn+dturn

	tbank#=dturn*45
	dbank#=(tbank-rbank#)*.08
	rbank=rbank+dbank

	RotateEntity m\ID,-rpitch,-rturn,-rbank
	MoveEntity m\ID,0,0,.25

	If JoyDown(1) Or KeyDown(79)
		For h.hardpoint=Each hardpoint

			If h\group=1 And h\recycle=0
				s.shot=New shot

				w.weapon=First weapon
				While h\weapon<>w\number
					w=After w
				Wend

				s\ID=CopyEntity(w\sID,h\ID)
				EntityParent s\ID,0
				s\life=w\life
				h\recycle=w\recycle
			EndIf
		Next
	EndIf

	If JoyDown(2) Or KeyDown(80)
		For h.hardpoint=Each hardpoint

			If h\group=2 And h\recycle=0
				s.shot=New shot

				w.weapon=First weapon
				While h\weapon<>w\number
					w=After w
				Wend

				s\ID=CopyEntity(w\sID,h\ID)
				EntityParent s\ID,0
				s\life=w\life
				h\recycle=w\recycle
			EndIf
		Next
	EndIf

	If JoyDown(3) Or KeyDown(81)
		For h.hardpoint=Each hardpoint

			If h\group=3 And h\recycle=0
				s.shot=New shot

				w.weapon=First weapon
				While h\weapon<>w\number
					w=After w
				Wend

				s\ID=CopyEntity(w\sID,h\ID)
				EntityParent s\ID,0
				s\life=w\life
				h\recycle=w\recycle
			EndIf
		Next
	EndIf

	For s.shot=Each shot
		MoveEntity s\id,0,0,.5
		s\life=s\life-1
		If s\life<50
			EntityAlpha s\id,s\life/50.0
		EndIf
		If s\life<=0
			FreeEntity s\ID
			Delete s
		EndIf
	Next

;	PositionEntity camroot,EntityX(m\ID,1),EntityY(m\ID,1),EntityZ(m\ID,1),1
;	RotateEntity camroot,EntityPitch(m\ID,1),EntityYaw(m\ID,1),EntityRoll(m\ID,1),1

	For c.ChaseCam=Each ChaseCam
		UpdateChaseCam(c)
	Next

	PositionEntity shademesh,EntityX(m\ID,1),.8,EntityZ(m\ID,1),1

	ty#=TerrainY(terra,EntityX(m\ID,1)+VertexX(shadesurf,0),0,EntityZ(m\ID,1)+VertexZ(shadesurf,0))
	If ty=>wy:sv0y=ty:Else:sv0y=wy:EndIf
	ty=TerrainY(terra,EntityX(m\ID,1)+VertexX(shadesurf,1),0,EntityZ(m\ID,1)+VertexZ(shadesurf,1))
	If ty=>wy:sv1y=ty:Else:sv1y=wy:EndIf
	ty=TerrainY(terra,EntityX(m\ID,1)+VertexX(shadesurf,2),0,EntityZ(m\ID,1)+VertexZ(shadesurf,2))
	If ty=>wy:sv2y=ty:Else:sv2y=wy:EndIf
	ty=TerrainY(terra,EntityX(m\ID,1)+VertexX(shadesurf,3),0,EntityZ(m\ID,1)+VertexZ(shadesurf,3))
	If ty=>wy:sv3y=ty:Else:sv3y=wy:EndIf

	VertexCoords shadesurf,0,VertexX(shadesurf,0),sv0y,VertexZ(shadesurf,0)
	VertexCoords shadesurf,1,VertexX(shadesurf,1),sv1y,VertexZ(shadesurf,1)
	VertexCoords shadesurf,2,VertexX(shadesurf,2),sv2y,VertexZ(shadesurf,2)
	VertexCoords shadesurf,3,VertexX(shadesurf,3),sv3y,VertexZ(shadesurf,3)

	If shadowres=1

		ty=TerrainY(terra,EntityX(m\ID,1)+VertexX(shadesurf,4),0,EntityZ(m\ID,1)+VertexZ(shadesurf,4))
		If ty=>wy:sv4y=ty:Else:sv4y=wy:EndIf
		ty=TerrainY(terra,EntityX(m\ID,1)+VertexX(shadesurf,5),0,EntityZ(m\ID,1)+VertexZ(shadesurf,5))
		If ty=>wy:sv5y=ty:Else:sv5y=wy:EndIf
		ty=TerrainY(terra,EntityX(m\ID,1)+VertexX(shadesurf,6),0,EntityZ(m\ID,1)+VertexZ(shadesurf,6))
		If ty=>wy:sv6y=ty:Else:sv6y=wy:EndIf
		ty=TerrainY(terra,EntityX(m\ID,1)+VertexX(shadesurf,7),0,EntityZ(m\ID,1)+VertexZ(shadesurf,7))
		If ty=>wy:sv7y=ty:Else:sv7y=wy:EndIf


		VertexCoords shadesurf,4,VertexX(shadesurf,4),sv4y,VertexZ(shadesurf,4)
		VertexCoords shadesurf,5,VertexX(shadesurf,5),sv5y,VertexZ(shadesurf,5)
		VertexCoords shadesurf,6,VertexX(shadesurf,6),sv6y,VertexZ(shadesurf,6)
		VertexCoords shadesurf,7,VertexX(shadesurf,7),sv7y,VertexZ(shadesurf,7)

	EndIf

	PositionEntity watr,Cos(angle)*10,wy,Sin(angle)*10

	angle=angle+.2:wy=wy+wya

	If wy=>24 Or wy=<8
		wya=-wya
	EndIf

	For e.exhaust=Each exhaust
		sm.smoke=New smoke
		sm\ID=CopyEntity (smokespr,e\ID)
		EntityParent sm\ID,0
		TurnEntity sm\ID,Rnd(-5,5),Rnd(-5,5),Rnd(-5,5)
		sm\life=100
	Next

	UpdateWorld
	RenderWorld

	For h.hardpoint= Each hardpoint
		If h\recycle>0
			h\recycle=h\recycle-1
		EndIf
	Next

	For sm.smoke=Each smoke

		sm\life=sm\life-1
		EntityAlpha sm\ID,sm\life/250.0
		MoveEntity sm\ID,0,0,.1
		If sm\life<=0
			FreeEntity sm\ID
			Delete sm
		EndIf
	Next


	Flip

Until KeyHit(1)

End


Function PurgeHardpoints()

	For h.hardpoint=Each hardpoint
		FreeEntity h\wID
		Delete h
	Next

	For e.exhaust=Each exhaust
		FreeEntity e\ID
		Delete e
	Next

End Function


Function GetHardpoints(newroot)
hps=0
For i = 1 To CountChildren(newroot)
	tempID=GetChild(newroot,i)

	branch=CountChildren(tempID)

	If branch
		GetHardpoints(tempID)
	EndIf

	name$=EntityName$(tempID)

	If Lower$(Left$(name$,9))="hardpoint"
		h.hardpoint=New hardpoint
		h\ID=tempID
		h\weapon=1
		h\group=1
		h\number=Right$(name$,2)
		For w.weapon=Each weapon
			If w\number=h\weapon
				h\wID=CopyEntity(w\ID,h\ID)
			EndIf
		Next
		hps=hps+1
	EndIf

	If Lower$(Left$(name$,7))="exhaust"
		e.exhaust=New exhaust
		e\ID=tempID
	EndIf

Next

bSortHardpoints()

Return hps
End Function


Function bSortHardpoints()

	c.hardpoint=New hardpoint

	For a.hardpoint=Each hardpoint
		b.hardpoint=After a
		If b<>Null
			If a\number>b\number
				c\ID=a\ID
				c\group=a\group
				c\recycle=a\recycle
				c\number=a\number
				c\weapon=a\weapon
				c\wID=a\wID

				a\ID=b\ID
				a\group=b\group
				a\recycle=b\recycle
				a\number=b\number
				a\weapon=b\weapon
				a\wID=b\wID

				b\ID=c\ID
				b\group=c\group
				b\recycle=c\recycle
				b\number=c\number
				b\weapon=c\weapon
				b\wID=c\wID

			EndIf
		EndIf
	Next

	Delete c
End Function

Function ParseScript(name$)

If FileType(name$)=1

	fhandle=ReadFile(name$)
	If fhandle<>1
		While Eof(fhandle)=0
			cmd$=ReadLine$(fhandle)
			If Left$(cmd$,1)<>";" And Len(cmd$)<>0
				parse.scriptentry=New scriptentry
				parse\sdat=cmd$
			EndIf
		Wend
		CloseFile fhandle

		parse.scriptentry=First scriptentry
		While parse<>Null
			If Left$(parse\sdat,1)="@"
			cmd$=Mid$(parse\sdat,2,Len(parse\sdat)-2)
				Select cmd$
					Case "getmodeldata"
					parse=After parse
					Repeat
						m.model=New model
						sep=Instr(parse\sdat,"=")
						del1$=Mid$(parse\sdat,6,sep-6)
						m\number=del1$
						loadname$=Right$(parse\sdat,Len(parse\sdat)-sep)
						m\ID = LoadAnimMesh (loadname$)
						HideEntity m\ID
						parse=After parse
					Until parse\sdat=")"
							
					Case "getweapondata"

					parse=After parse
					Repeat
						w.weapon=New weapon
						cpos=1

						Repeat
							sep1=Instr(parse\sdat,"=",cpos)
							del1$=Mid$(parse\sdat,cpos,sep1-cpos)
							sep2=Instr(parse\sdat,",",cpos)
							del2$=Mid$(parse\sdat,sep1+1,sep2-sep1-1)

							Select del1$
							Case "weapon"
								w\number=del2$

							Case "object"
								w\ID=LoadMesh(del2$)
								HideEntity w\ID

							Case "shot"
								w\sID=LoadMesh(del2$)
								HideEntity w\sID

							Case "life"
								w\life=del2$

							Case "recycle"
								w\recycle=del2$

							End Select
							cpos=sep2+1
						Until sep1=0 Or sep2=0
						parse=After parse
					Until parse\sdat=")"



				End Select
			EndIf
			parse=After parse
		Wend
	Else
		RuntimeError"Script File "+Chr$(34)+name$+Chr$(34)+" is not readable."
	EndIf

Else
	RuntimeError"Script File "+Chr$(34)+name$+Chr$(34)+" is not a file."
EndIf	

End Function

Function BuildShadowMesh()

; Shadow ASCII art! :o>

;-1     1
;   0---1
;   |  /|
;   | / |
;   |/  |
; 1 3---2

;-1,-1
; 1,-1
; 1, 1
;-1, 1

shademesh=CreateMesh()
shadesurf=CreateSurface(shademesh)
shadev0=AddVertex(shadesurf,-1, 0,-1, 0, 0, 1)
shadev1=AddVertex(shadesurf, 1, 0,-1, 1, 0, 1)
shadev2=AddVertex(shadesurf, 1, 0, 1, 1, 1, 1)
shadev3=AddVertex(shadesurf,-1, 0, 1, 0, 1, 1)

shadet0=AddTriangle(shadesurf,shadev0,shadev3,shadev1)
shadet1=AddTriangle(shadesurf,shadev3,shadev2,shadev1)


EntityColor shademesh,0,0,0
shadetex=LoadTexture ("shadow.bmp",2)
EntityTexture shademesh,shadetex
EntityAlpha shademesh,.8

End Function


Function BuildShadowMesh2()

; Shadow ASCII art! :o>

;-1         1
;   2---1---0
;   |  / \  |
;   | /   \ |
;   |/     \|
;   4-------3
;   |\     /|
;   | \   / |
;  |  \ /  |
; 1 7---6---5

;-1,-1
; 0,-1
; 1,-1
;-1, 0
; 1, 0
;-1, 1
; 0, 1
; 1, 1

shademesh=CreateMesh()
shadesurf=CreateSurface(shademesh)
shadev0=AddVertex(shadesurf,-2, 0,-2, 0, 0, 1)
shadev1=AddVertex(shadesurf, 0, 0,-2,.5, 0, 1)
shadev2=AddVertex(shadesurf, 2, 0,-2, 1, 0, 1)
shadev3=AddVertex(shadesurf,-2, 0, 0, 0,.5, 1)
shadev4=AddVertex(shadesurf, 2, 0, 0, 1,.5, 1)
shadev5=AddVertex(shadesurf,-2, 0, 2, 0, 1, 1)
shadev6=AddVertex(shadesurf, 0, 0, 2,.5, 1, 1)
shadev7=AddVertex(shadesurf, 2, 0, 2, 1, 1, 1)

shadet0=AddTriangle(shadesurf,shadev0,shadev3,shadev1)
shadet1=AddTriangle(shadesurf,shadev4,shadev2,shadev1)
shadet2=AddTriangle(shadesurf,shadev4,shadev1,shadev3)
shadet3=AddTriangle(shadesurf,shadev3,shadev5,shadev6)
shadet4=AddTriangle(shadesurf,shadev6,shadev7,shadev4)
shadet5=AddTriangle(shadesurf,shadev6,shadev4,shadev3)


EntityColor shademesh,0,0,0
shadetex=LoadTexture ("shadow.bmp",2)
EntityTexture shademesh,shadetex
EntityAlpha shademesh,.8

End Function

Function BuildWorld()

	;"Lights"
	lightpivot=CreatePivot()
	light=CreateLight(2,lightpivot)
	PositionEntity light,0,32,0
	PointEntity light,lightpivot

	;"Camera"

;	CreateChaseCam(m\ID)

	camroot=CreatePivot()
	cam=CreateCamera(camroot)
	PositionEntity camroot,0,64,0
	RotateEntity camroot,0,20,0

	;"Everything Else" in one huge mess :o>
	terrapivot=CreatePivot()
	terra=LoadTerrain("landblur.bmp",terrapivot)
	ScaleEntity terra,8,32,8
	MoveEntity terra,-256,0,-256
	TerrainShading terra,True
	TerrainDetail terra,1500,True
	terratexture1=LoadTexture("stone47.bmp",1)
	ScaleTexture terratexture1,8,8
	EntityTexture terra,terratexture1,0,2

	watr=CreatePlane (1)
	watrtex=LoadTexture ("water09.bmp")
	ScaleTexture watrtex,64,64
	EntityTexture watr,watrtex
	PositionEntity watr,0,8,0

	; Load in scenery

	cdir$=CurrentDir$()
	ChangeDir "objects"

	house12=LoadMesh("house12.x")
	HideEntity house12

	ChangeDir cdir$

	; Load in sprite effects

	smokespr=LoadSprite("textures/smokespr.bmp")
	ScaleSprite smokespr,.1,.1
	HideEntity smokespr

	; Build run-down houses
	For i=1 To 12
		thouse12=CopyEntity(house12)
		Repeat
			x=Rnd(-256,256):z=Rnd(-256,256)
			ty=TerrainY(terra,x,0,z)
		Until ty>24

		PositionEntity thouse12,x,ty,z
		RotateEntity thouse12,0,Rnd(0,360),0
	Next

End Function

Function OptionsMenu()

f=0:fa=10:item=1

Repeat
UpdateWorld:RenderWorld
RotateEntity m\ID,30,180,0
PositionEntity m\ID,0,64,3

lne=1

If item=lne
	Color f,f,f
	Text 0,lne*12,"G  Craft: "+m\number:lne=lne+1
Else
	Color 255,255,255
	Text 0,lne*12,"G  Craft: "+m\number:lne=lne+1
EndIf


For h.hardpoint=Each hardpoint
	If item=lne
		Color f,f,f
		Text 0,lne*12,h\group+"- Hardpoint "+h\number+": "+h\weapon
		EntityAlpha h\wID,f/255.0
	Else
		Color 255,255,255
		Text 0,lne*12,h\group+"- Hardpoint "+h\number+": "+h\weapon
		EntityAlpha h\wID,1
	EndIf
	lne=lne+1
Next

f=f+fa

If f<10 Or f>230 Then fa=-fa

If KeyHit(200) And item>1
	item=item-1
EndIf

If KeyHit(208) And item<lne-1
	item=item+1
EndIf

If KeyHit(205)
	If item=1
		HideEntity m\ID
		m=After m
		If m=Null
			m=First model
		EndIf
		ShowEntity m\ID
		PurgeHardpoints()
		hps=GetHardpoints(m\ID)
	Else
		hpnum=item-1
		For h.hardpoint=Each hardpoint
			If h\number=hpnum
				h\weapon=h\weapon+1
				If h\weapon>wps
					h\weapon=1
				EndIf
				w.weapon=First weapon
				While w\number<>h\weapon
					w=After w
				Wend
				FreeEntity h\wID
				h\wID=CopyEntity(w\ID,h\ID)
			EndIf
		Next
	EndIf
EndIf	

If KeyHit(203)
	If item=1
		HideEntity m\ID
		m=Before m
		If m=Null
			m=Last model
		EndIf
		ShowEntity m\ID
		PurgeHardpoints()
		hps=GetHardpoints(m\ID)
	Else
		hpnum=item-1
		For h.hardpoint=Each hardpoint
			If h\number=hpnum
				h\weapon=h\weapon-1
				If h\weapon<1
					h\weapon=wps
				EndIf
				w.weapon=First weapon
				While w\number<>h\weapon
					w=After w
				Wend
				FreeEntity h\wID
				h\wID=CopyEntity(w\ID,h\ID)
			EndIf
		Next
	EndIf
EndIf	

If KeyHit(79) ; Keypad "1"
	hpnum=item-1
	For h.hardpoint=Each hardpoint
		If h\number=hpnum
			h\group=1
		EndIf
	Next
EndIf

If KeyHit(80) ; Keypad "2"
	hpnum=item-1
	For h.hardpoint=Each hardpoint
		If h\number=hpnum
			h\group=2
		EndIf
	Next
EndIf

If KeyHit(81) ; Keypad "3"
	hpnum=item-1
	For h.hardpoint=Each hardpoint
		If h\number=hpnum
			h\group=3
		EndIf
	Next
EndIf


Flip
Until KeyHit(1)

For h.hardpoint=Each hardpoint
		EntityAlpha h\wID,1
Next

End Function

Function CreateChaseCam.ChaseCam( entity )
	c.ChaseCam=New ChaseCam
	c\entity=entity
	c\camera=CreateCamera()
	CameraRange c\camera,.1,250
	CameraFogMode c\camera,1
	CameraFogRange c\camera,200,250

;	CameraZoom c\camera,2
	
	c\target=CreatePivot(entity)
	PositionEntity c\target,0,.5,-1
;	EntityType c\target,TYPE_TARGET
	
	c\heading=CreatePivot(entity)
	PositionEntity c\heading,0,0,20
;	c\sky=CopyEntity( sky )
	Return c
End Function

Function UpdateChaseCam( c.ChaseCam )

	dx#=EntityX(c\target,True)-EntityX(c\camera,True)
	dy#=EntityY(c\target,True)-EntityY(c\camera,True)
	dz#=EntityZ(c\target,True)-EntityZ(c\camera,True)
	
	TranslateEntity c\camera,dx*.1,dy*.1,dz*.1
	
	PointEntity c\camera,c\heading

	PositionEntity c\target,0,0,0
	ResetEntity c\target
	PositionEntity c\target,0,.5,-1

End Function