;Tools
Global RootDir$ = "..\..\"
Global LogMode = 1; (0 = standard logging, 1 = debug mode)
ChangeDir RootDir$

Global AH_Loca$ = "..\Realm Crafter 1\"
Global AH_Appb$ = "RCSTD"
Include "..\modules\Utility.bb"
;Include "antihack.bb"
; Loads Antihack module :)


Const testing=False
BF_Start(5378)
    ;;;;;;;;;;;;;;;;;;;;;;;;;;; 
    ;setup for variable window;
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;
	Global GFX_WINDOW_WIDTH = GetSystemMetrics(0)
	Global GFX_WINDOW_HEIGHT = GetSystemMetrics(1)
	Global GFX_WINDOW_RESOLUTION = 16
	Global GFX_WINDOW_MODE = 0
	Global GFX_WINDOW_RESIZE = False
	Global GFX_WINDOW_BORDER = False
	Global GFX_WINDOW_CAPTION$ = ""
	Global GFX_WINDOW_VERSION = 1.0
	Global WINDOW_RESIZEMETHOD = 1
	Global RENDERMODE = 1

    If GetSystemMetrics(0)<1024 Then RuntimeError "RC Trees requires a minimun of 1024X768 desktop resolution"
    
	;#Region Add/Subtract/Other Value Variables
	Const GUI_VARIABLE_TOPBAR_HEIGHT = 41
	Const GUI_VARIABLE_RIGHTBAR_WIDTH = 140
	Const GUI_VARIABLE_LEFTBAR_WIDTH = 140
	Const GUI_VARIABLE_BOTTOMBAR_HEIGHT = 50
	Const GUI_VARIABLE_GADGET_X = 60
	Const GUI_VARIABLE_GADGET_W = 65
	Const GUI_VARIABLE_GADGET_W2 = 80
    ;#End Region
	
	Global GUI_VARIABLE_CAMERASPEED#=1
	Global GUI_VARIABLE_MODIFICATIONSPEED# = 60

	Global actionReady, lastAction ; only allow 1 action per 250ms

	Global lastWKey

    ;initialize fui
    Include "..\modules\safeloads.bb"
    Include "..\Modules\f-ui.bb"
	AppTitle( GFX_WINDOW_CAPTION$, "Really quit?" )
    SetBuffer BackBuffer()

    If GetSystemMetrics(0)=>1024 Then 
	 	FUI_Initialise(1024, 768, 0, 2, False, True, "Realm Crafter tER")
    	w=1024:h=768                                                                               ;  
    Else 
      RuntimeError "RC Trees requires a minimun of 1024X768 desktop resolution"
    EndIf

    ;this next bit is the only way to use fui fullscreen, and still use the windows os dialogs ; 
    w=1024:h=768                                                                               ;  
    JF_VISIBLE		= $10000000                                                                ;
    JF_STYLE		= -16                                                                      ;
    SetWindowLong(APP\HWND, jf_STYLE, jf_VISIBLE)                                              ;
    MoveWindow(APP\HWND, (GetSystemMetrics(0) - w) / 2, (GetSystemMetrics(1) - H) / 2, W, h, 1);
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;   
    Cls
    Flip
	FUI_HideMouse( )

    Global Cam=CreateCamera()  
	wORLDPIVOT = CreatePivot( )
    EntityParent Cam,worldpivot

    ClearTextureFilters()
	TextureFilter "_M",1+4
	TextureFilter "_A",1+2
    CameraRange Cam,1,2000
    CameraClsMode Cam,True,True
	Include "..\modules\RCTREE_gui_shell_FUI.bb"
    ChangeDir thispath$    
;#Region Load Modules
	Include "..\Modules\media.bb"
    
ChangeDir thispath$
If testing=False Then 
;;;;;;; NEW ADDITIONS VIA SOLSTAR REQUESTw
;#Region Load Modules
;Include "Modules\rc_registry.bb"
If FileType("Data\Selected.dat")=1 Then 
   f=OpenFile("Data\Selected.dat")
    selectedproject$=ReadString$(f)
    CloseFile f
	Else
	RuntimeError "No project selected, please run RC Project Manager"
    EndIf
   If FileType ("Projects\"+selectedproject$+"\")<>2 Then 
 	RuntimeError "Poject folder does not exist, please run RC Project Manager"
    EndIf 
    ChangeDir "Projects\"+selectedproject$+"\"
EndIf
thispath$=CurrentDir() 



;regeion
;FTE INFO
Global msgtimer#
Global msgtxt$
;msgtimer#=MilliSecs()+5000
;msgtxt$="Welcome to the FTE DEMO!"

Dim treerecord#(3,10,3)
Dim treerecord2#(3,10,3)
Global datapath$=thispath$+"DATA\RCTREES\"
Global branchpath$=Thispath$+"DATA\RCTREES\TEXTURES\"
Global SHAPEpath$=Thispath$+"DATA\RCTREES\SHAPES\"
Global EXPORTpath$=Thispath$+"DATA\MESHES\RCTREES\"
If FileType (datapath$)<>2 Then CreateDir datapath$
If FileType (branchpath$)<>2 Then CreateDir branchpath$
If FileType (SHAPEpath$)<>2 Then CreateDir SHAPEpath$
If FileType (EXPORTpath$)<>2 Then CreateDir EXPORTpath$

Global leafscale#=6.0
Global op_mesh,op_brush,op_surface
Global barkscale#=1.0
Global maxtrunks=1
Dim trunk(3)
Global selectcycle=0

Include "..\modules\b3dfile.bb"
Include "..\modules\rcsavemesh.bb"


Type leaf
 Field ent
 Field scale#
 Field brush
 Field shapefile$
 End Type

Global leaf.leaf
Global selected.leaf

Type branch
 Field ent
 End Type
Global br.branch

Global mainshape$="quadleaf.3ds"

Global actionmode=4
Global oldaction=4
Global paintstyle=0
Global leaftexname$="branch.png"
Global barktexname$="bark.png"
Dim tRing(0)
Dim bRing(0)

;columngrid=LoadTexture("grid.bmp",9)



;randomize_tree
wkey=0



	GFX_WINDOW_CAPTION$ = "Realm Crafter Tree Editor"
	AppTitle( GFX_WINDOW_CAPTION$, "Really quit?" )

	


If FileType(branchpath$+"bark.png")<>1 Then RuntimeError ("The Bark texture is missing : Bark.png")
If FileType(branchpath$+"branch.png")<>1 Then RuntimeError ("Default branch texture missing : Branch.png")


                








Global barktex=LoadTexture(branchpath$+"bark.png")
Global ground = LoadMesh_safe(datapath$+"ground.b3d")
;PositionEntity cam,0,1,-11
PositionEntity Cam, 2, 1, -11
ScaleEntity GROUND,2,2,2
PositionEntity ground,40,-5,-50
light=CreateLight()
PositionEntity light,25,40,40
AmbientLight 255,255,255


Global leaftex=LoadTexture(BRANCHPATH$+"branch.png",1+4)

Global skybox=create_skybox()
CameraClsColor cam,60,90,200
CameraRange cam,1,10000

EntityOrder skybox,100000
choosenewmap 2
Global pickoffsetx=0
Global pickoffsety=0

                    ChangeDir branchpath$
					sfilename3$="Bark.PNG"
 	             	load_new_tex 1,sfilename3$
  			        tImage2 = loadimage_Safe(sfilename3$)
					ResizeImage tImage2, 100, 100
				        FUI_DeleteGadget(GUI_RIGHTWIN_barkCanvas)
                        GUI_RIGHTWIN_BarkCanvas = FUI_ImageBox( GUI_RIGHTWIN, 5, 80, 100, 100,Timage2)
						SetBuffer BackBuffer()
    			        FreeImage timage2
                    ChangeDir branchpath$
					sfilename3$="Branch.PNG"
 			        tImage3 = loadimage_Safe(sfilename3$)
					ResizeImage tImage3, 100, 100
                    load_new_tex 0,sfilename3$
				        FUI_DeleteGadget(GUI_RIGHTWIN_LeafCanvas)
	                    GUI_RIGHTWIN_LeafCanvas  = FUI_ImageBox( GUI_RIGHTWIN, 5, 270, 100, 100,timage3)
    			        FreeImage timage3 
 		  		    ChangeDir thispath$

	actionReady = True

	InitFPS()
Repeat

	If MilliSecs() - lastAction > 249 Then actionReady = True

If KeyHit(2) Then thicken()
If KeyHit(3) Then thin()
If KeyHit(4) Then randall()
mouseclicked=-1
mousepressed1=-1
mousepressed2=-1
MOUSEPRESSED1 = MouseDown(1)
MOUSEPRESSED2 = MouseDown(2);gui_rightmouse 
If mousepressed1 Then mouseclicked=1
If mousepressed2 Then mouseclicked=2
C_ALL=FUI_SendMessage(GUI_RIGHTWIN_EFFECTALL,M_GETCHECKED)
If c_all Then 
	If actionmode<>5000 Then 
		FUI_SendMessage(GUI_RIGHTWIN_RANDOM,M_SETCHECKED,0)
		FUI_SendMessage(GUI_RIGHTWIN_UPRIGHT,M_SETCHECKED,0)
		FUI_SendMessage(GUI_RIGHTWIN_TRUNKSCALE,M_SETCHECKED,0)
		FUI_SendMessage(GUI_RIGHTWIN_TRUNKSLIDEX,M_SETCHECKED,0)
		FUI_SendMessage(GUI_RIGHTWIN_TRUNKSLIDEY,M_SETCHECKED,0)
		FUI_SendMessage(GUI_RIGHTWIN_TRUNKSLIDEZ,M_SETCHECKED,0)
		actionmode = 5000 
	EndIf
	Else
	If actionmode=5000 Then actionmode=-10
	;EndIf
EndIf
PositionEntity skybox,EntityX(cam,1),EntityY(cam,1),EntityZ(cam,1)		
checkzmouse
If (mouseclicked=1 And actionReady) Then 
	Select actionmode
		Case 4;add leaf
			add_leaf paintstyle
		Case 2004 ;paint
			 For leaf.leaf=Each leaf
				 If Upper(Left$(leaf\shapefile$,5))<>"PREF_" Then EntityPickMode leaf\ent,2
			 Next
			camp = CameraPick(Cam,MouseX()+pickoffsetx,MouseY()+pickoffsety)
			If camp <> 0 Then
				 For leaf.leaf=Each leaf
					 If Upper(Left$(leaf\shapefile$,5))<>"PREF_" Then  
					   EntityPickMode leaf\ent,0 
					   If PickedEntity()=leaf\ent Then 
						   S= GetSurface(leaf\ent,1)
						   b=CreateBrush()
						   BrushTexture(b,leaftex)
						   PaintMesh leaf\ent,b
						   FreeBrush b
					   EndIf
					 Else
				  EndIf
				Next
			EndIf
			 For leaf.leaf=Each leaf
				 EntityPickMode leaf\ent,0 
			 Next
	End Select
	actionReady = False
	lastAction = MilliSecs()
EndIf

If mousepressed2 And actionReady Then
	Select actionmode
		Case 4;add leaf
			 For leaf.leaf=Each leaf
				  If Upper(Left$(leaf\shapefile$,5))<>"PREF_"  EntityPickMode leaf\ent,2
			 Next
			camp = CameraPick(Cam,MouseX()+pickoffsetx,MouseY()+pickoffsety)
			If camp <> 0 Then
				 For leaf.leaf=Each leaf
				  EntityPickMode leaf\ent,0 
				  If PickedEntity()=leaf\ent Then 
					   FreeBrush brush
					   FreeBrush leaf\brush
					   FreeEntity leaf\ent
					   Delete leaf
   					 EndIf
				 Next
			EndIf
			 For leaf.leaf=Each leaf
				 EntityPickMode leaf\ent,0 
			 Next
	End Select
	actionReady = False
	lastAction = MilliSecs()
EndIf

If MouseDown(1) And actionReady Then 
	Select actionmode 
	Case 0
		slide 0,1,1
	Case 1
		slide 0,.05
	Case 2
		slide 1,.05
	Case 3
		slide 2,.05
	End Select
	
	actionReady = False
	lastAction = MilliSecs() - 150
EndIf

If MouseDown(2) And actionReadyThen 
	Select actionmode 
		Case 0
			 slide 0,-1,1
		Case 1
			slide 0,-.05
		Case 2
			slide 1,-.05
		Case 3
			slide 2,-.05
	End Select
	 For leaf.leaf=Each leaf
		 EntityPickMode leaf\ent,0 
	 Next
	actionReady = False
	lastAction = MilliSecs() - 150
EndIf
Mspx#=MouseXSpeed()
mspy#=MouseYSpeed()
If KeyDown(17) And wkey=0 And MilliSecs() - lastWKey > 249
	wkey=1
	lastWKey = MilliSecs()
EndIf
If leafscale<1 Then leafscale=1
If leafscale>13 Then leafscale=13
If KeyHit(15) Then 
	 paintstyle=paintstyle+1
	 If paintstyle=2 Then paintstyle=0
EndIf
If FUI_SendMessage(GUI_RIGHTWIN_UPRIGHT,M_GETCHECKED)=1 Or FUI_SendMessage(GUI_RIGHTWIN_RANDOM,M_GETCHECKED)=1 Then  
	GUI_CURRENTLY_SELECTED_MAINMODE = 1
	Else
	GUI_CURRENTLY_SELECTED_MAINMODE = 2
EndIf
If FUI_SendMessage(GUI_RIGHTWIN_TRUNKSCALE,M_GETCHECKED)=1 Then
	 FUI_SendMessage(GUI_RIGHTWIN_EFFECTALL,M_SETCHECKED,0)
	 ACTIONMODE=0
ElseIf FUI_SendMessage(GUI_RIGHTWIN_TRUNKSLIDEX,M_GETCHECKED)=1 Then
	 FUI_SendMessage(GUI_RIGHTWIN_EFFECTALL,M_SETCHECKED,0)
	 ACTIONMODE=1
ElseIf FUI_SendMessage(GUI_RIGHTWIN_TRUNKSLIDEY,M_GETCHECKED)=1 Then
	 FUI_SendMessage(GUI_RIGHTWIN_EFFECTALL,M_SETCHECKED,0)
	 ACTIONMODE=2
ElseIf FUI_SendMessage(GUI_RIGHTWIN_TRUNKSLIDEZ,M_GETCHECKED)=1 Then
	 FUI_SendMessage(GUI_RIGHTWIN_EFFECTALL,M_SETCHECKED,0)
	 ACTIONMODE=3
ElseIf FUI_SendMessage(GUI_RIGHTWIN_RANDOM,M_GETCHECKED)=1 Then
	 FUI_SendMessage(GUI_RIGHTWIN_EFFECTALL,M_SETCHECKED,0)
	 actionmode=4
	 paintstyle=0
ElseIf FUI_SendMessage(GUI_RIGHTWIN_UPRIGHT,M_GETCHECKED)=1 Then
	 FUI_SendMessage(GUI_RIGHTWIN_EFFECTALL,M_SETCHECKED,0)
     actionmode=4
	 paintstyle=1
EndIf


	
			If KeyDown(203) Then 
					 RotateEntity worldpivot,EntityPitch(worldpivot),EntityYaw(worldpivot),0 
					 TurnEntity worldpivot,0,-GUI_VARIABLE_MODIFICATIONSPEED# / FPS,0
					 EndIf

			If KeyDown(205) Then  
					 RotateEntity worldpivot,EntityPitch(worldpivot),EntityYaw(worldpivot),0 
					 TurnEntity worldpivot,0,GUI_VARIABLE_MODIFICATIONSPEED# / FPS,0
                     EndIf 			
			;Up Down
			If KeyDown(200) Then  
					 RotateEntity worldpivot,EntityPitch(worldpivot),EntityYaw(worldpivot),0 
					 TurnEntity worldpivot,-GUI_VARIABLE_MODIFICATIONSPEED# / FPS,0,0
                     EndIf 
			If KeyDown(208) Then 
					 RotateEntity worldpivot,EntityPitch(worldpivot),EntityYaw(worldpivot),0 
					 TurnEntity worldpivot,GUI_VARIABLE_MODIFICATIONSPEED# / FPS,0,0
                     EndIf 
			;AZ
			If KeyDown(30) Then 
             If EntityDistance (cam,trunk(1))>3 Then 
  			  MoveEntity Cam, 0, 0, GUI_VARIABLE_MODIFICATIONSPEED# / FPS
              EndIf
              EndIf
			If KeyDown(44) Then 
             If EntityDistance(cam,trunk(1))<50 Then 
			  MoveEntity Cam, 0, 0, -GUI_VARIABLE_MODIFICATIONSPEED# / FPS
              EndIf
              EndIf

					;Left Right
                    If KeyHit(28) Then RotateEntity cam,0,EntityYaw(cam,1),0
                         
					If KeyDown(203) Then TurnEntity worldPIVOT, 0, -GUI_VARIABLE_CAMERASPEED# / FPS,0
					If KeyDown(205) Then TurnEntity worldPIVOT, 0, GUI_VARIABLE_CAMERASPEED# / FPS,0
					;Up Down
					If KeyDown(200) Then TurnEntity worldPIVOT, -GUI_VARIABLE_CAMERASPEED# / FPS, 0,0
					If KeyDown(208) Then TurnEntity worldPIVOT, GUI_VARIABLE_CAMERASPEED# / FPS, 0, 0

				
				

				
			mouseclicked=0	
			CameraProjMode( App\Cam, False )
			CameraProjMode( Cam, True )
; 	        UnlockBuffer(BackBuffer()) 
;    	    LockBuffer(FrontBuffer()) 
        	UpdateWorld()
; 	        LockBuffer(BackBuffer()) 
;    	    UnlockBuffer(FrontBuffer()) 
            buttoncheck()
			RenderWorld( )
			CameraProjMode( Cam, False )
			CameraProjMode( App\Cam, True )
  		    FUI_Update( )
			Flip( FPS > 30 )


			UpdateFPS()
	Until  Quitapp = True
	End



Function Get_FPS( )
		FPS_CurrentTime = MilliSecs()
		If FPS_CurrentTime > FPS_CheckTime Then
			FPS_CheckTime = FPS_CurrentTime + 1000
			FPS_CurrentFPS = FPS_Counter
			FPS_Counter = 0
		Else
			FPS_Counter = FPS_Counter + 1
		End If
		
		Return FPS_CurrentFPS
End Function
	


Function NewTreeWindow()
Return fui_choose("New tree size","Stump","One","Two","Three")
End Function

Function CreateMyCylinder(verticalsegments,ringsegments=0,solid=False,parent=0,offset#=0)
	Local tr,tl,br,bl; 		side of cylinder
	Local ts0,ts1,newts; 	top side vertexs
	Local bs0,bs1,newbs; 	bottom side vertexs
	If verticalsegments<3 Or verticalsegments>100 Then Return 0
	If ringsegments<0 Or ringsegments>100 Then Return 0
	
	trunks=CreateMesh(parent)
	thissurf=CreateSurface(trunks)
EntityFX trunks,2
		If solid=True
		thissidesurf=CreateSurface(trunks)
	EndIf
	div#=Float(360.0/(verticalsegments))

	height#=1.0
	ringSegmentHeight#=(height#*2.0)/(ringsegments+1)
	upos#=1.0
	udiv#=Float(1.0/(verticalsegments))
	vpos#=1.0
	vdiv#=Float(1.0/(ringsegments+1))

	SideRotAngle#=90

	; re-diminsion arrays to hold needed memory.
	; this is used just for helping to build the ring segments...
	Dim tRing(verticalsegments)
	Dim bRing(verticalsegments)
	
	;render end caps if solid
	If solid=True
		XPos#=-Cos(SideRotAngle#)+offset
		ZPos#=Sin(SideRotAngle#)-offset

		ts0=AddVertex(thissidesurf,XPos#,height,ZPos#,XPos#/2.0+0.5,ZPos#/2.0+0.5)
		bs0=AddVertex(thissidesurf,XPos#,-height,ZPos#,XPos#/2.0+0.5,ZPos#/2.0+0.5)

		SideRotAngle#=SideRotAngle#+div#

		XPos#=-Cos(SideRotAngle#)
		ZPos#=Sin(SideRotAngle#)
		
		ts1=AddVertex(thissidesurf,XPos#,height,ZPos#,XPos#/2.0+0.5,ZPos#/2.0+0.5)
		bs1=AddVertex(thissidesurf,XPos#,-height,ZPos#,XPos#/2.0+0.5,ZPos#/2.0+0.5)
		
		For i=1 To (verticalsegments-2)
			SideRotAngle#=SideRotAngle#+div#

			XPos#=-Cos(SideRotAngle#)
			ZPos#=Sin(SideRotAngle#)
			
			newts=AddVertex(thissidesurf,XPos#,height,ZPos#,XPos#/2.0+0.5,ZPos#/2.0+0.5)
			newbs=AddVertex(thissidesurf,XPos#,-height,ZPos#,XPos#/2.0+0.5,ZPos#/2.0+0.5)
			
			AddTriangle(thissidesurf,ts0,ts1,newts)
			AddTriangle(thissidesurf,newbs,bs1,bs0)
		
			If i<(verticalsegments-2)
				ts1=newts
				bs1=newbs
			EndIf
		Next
	EndIf
	
	; -----------------------
	; middle part of cylinder
	thisHeight#=height#
	
	; top ring first		
	SideRotAngle#=90
	XPos#=-Cos(SideRotAngle#)+offset
	ZPos#=Sin(SideRotAngle#)-offset
	thisUPos#=upos#
	thisVPos#=0
	tRing(0)=AddVertex(thissurf,XPos#,thisHeight,ZPos#,thisUPos#,thisVPos#)
nvc#=255-(Rand(0,60))
VertexColor thissurf,tring(0),nvc#,nvc#,nvc#,1
	For i=0 To (verticalsegments-1)
		SideRotAngle#=SideRotAngle#+div#
		XPos#=-Cos(SideRotAngle#)+offset
		ZPos#=Sin(SideRotAngle#)-offset
		thisUPos#=thisUPos#-udiv#
		tRing(i+1)=AddVertex(thissurf,XPos#,thisHeight,ZPos#,thisUPos#,thisVPos#)
nvc#=255-(Rand(0,60))
VertexColor thissurf,tring(i+1),nvc#,nvc#,nvc#,1
	Next	
	
	For ring=0 To (ringsegments)

		; decrement vertical segment
		thisHeight=thisHeight-ringSegmentHeight#
		
		; now bottom ring
		SideRotAngle#=90
		XPos#=-Cos(SideRotAngle#)+offset
		ZPos#=Sin(SideRotAngle#)-offset
		thisUPos#=upos#
		thisVPos#=thisVPos#+vdiv#
		bRing(0)=AddVertex(thissurf,XPos#,thisHeight,ZPos#,thisUPos#,thisVPos#)
nvc#=255-(Rand(0,60))
VertexColor thissurf,bring(0),nvc#,nvc#,nvc#,1

		For i=0 To (verticalsegments-1)
			SideRotAngle#=SideRotAngle#+div#
			XPos#=-Cos(SideRotAngle#)+offset
			ZPos#=Sin(SideRotAngle#)-offset
			thisUPos#=thisUPos#-udiv#
			bRing(i+1)=AddVertex(thissurf,XPos#,thisHeight,ZPos#,thisUPos#,thisVPos#)
nvc#=255-(Rand(0,60))
VertexColor thissurf,bring(i+1),nvc#,nvc#,nvc#,1
		Next
		
		; Fill in ring segment sides with triangles
		For v=1 To (verticalsegments)
			tl=tRing(v)
			tr=tRing(v-1)
			bl=bRing(v)
			br=bRing(v-1)
			
			AddTriangle(thissurf,tl,tr,br)
			AddTriangle(thissurf,bl,tl,br)
		Next
		
		; make bottom ring segmentthe top ring segment for the next loop.
		For v=0 To (verticalsegments)
			tRing(v)=bRing(v)
		Next		
	Next
			
	UpdateNormals trunks
	Return trunks 
End Function

Function createtreequad(Texture,shfile$,scalem#,qx#,qy#,qz#,randit=1,leafkind=0)
Select leafkind
Case 0

If randit=1 Then 
scale#=Rnd(scalem-.02,scalem#+.02)
Else
scale#=scalem#
EndIf

segs#=1

    mesh=CreateMesh( parent )
    brush=CreateBrush()
    BrushFX(brush,4+8+2)
    
    BrushTexture(brush,texture)
    surf=CreateSurface( mesh )
    PaintSurface surf,brush
    l# =-.5
    b# = -.5
    tvc= 0

    ; create all the vertices First
     Repeat
       u# = l + .5
       v# = b + .5
       AddVertex surf,l,0,b,u,1-v
nc#=255-(Rand(0,90))
       VertexColor surf,l,nc#,nc#,nc# 
       tvc=tvc + 1
       l = l + 1/segs
       If l > .501 Then
         l = -.5
         b = b + 1/segs
       End If
     Until b > .5

     ;create polys
     vc# =0
     Repeat
 
       AddTriangle (surf,vc,vc+segs+1,vc+segs+2)
       AddTriangle (surf,vc,vc+segs+2,vc+1)

       vc = vc + 1
       tst# =  ((vc+1) /(segs+1)) -Floor ((vc+1) /(segs+1))

       If (vc > 0) And (tst=0) Then
         vc = vc + 1
       End If

     Until vc=>tvc-segs-2
     UpdateNormals mesh
     ScaleEntity mesh,scale/2,scale/2,scale/2
ydd=Rand(0,1)
xdd=Rand(0,1)
zdd=Rand(0,1)
If ydd=0 Then yd#=45 Else yd#=-45
If xdd=0 Then xd#=45 Else xd#=-45
If zdd=0 Then zd#=45 Else zd#=-45
xd#=Rand(-360,360)
yd#=Rand(-360,360)
zd#=Rand(-360,360)
leaf.leaf=New leaf
leaf\ent=mesh
leaf\scale#=scale
leaf\brush=brush

EntityFX leaf\ent,2+16
EntityParent leaf\ent,trunk(1)

PositionEntity mesh,qx,qy,qz

cd=Rand(-200,0)
s=GetSurface(mesh,1)
For vv=0 To CountVertices(s)-1
VertexColor s,vv,255+cd,255+cd,255+cd,1
Next

UpdateNormals mesh
Return mesh

Case 1 ;quad mesh
If randit=1 Then 
scale#=Rnd(scalem-.02,scalem#+.02)
Else
scale#=scalem#
EndIf

If FileType(shapepath$+shfile$)<>1 Then RuntimeError ("Shape file missing :"+shfile$)
mesh= LoadMesh(shapepath$+shfile$)


If CountSurfaces(mesh)>1 Then RuntimeError "multi surf"
s=GetSurface(mesh,1)

ydd=Rand(0,1)
xdd=Rand(0,1)
zdd=Rand(0,1)
If ydd=0 Then yd#=45 Else yd#=-45
If xdd=0 Then xd#=45 Else xd#=-45
If zdd=0 Then zd#=45 Else zd#=-45
xd#=Rand(-360,360)
yd#=Rand(-360,360)
zd#=Rand(-360,360)
leaf.leaf=New leaf
leaf\ent=mesh

;DebugLog "LOADING BRANCH WITH SHAPE : "+shfile$

leaf\scale#=scale
leaf\brush=GetSurfaceBrush(s)
leaf\shapefile$=shfile$
BrushTexture leaf\brush,texture
PaintMesh mesh,leaf\brush

ScaleEntity leaf\ent,scalem,scalem,scalem
If Upper(Left$(shfile$,5))="PREF_" Then 
EntityFX leaf\ent,2
Else
EntityFX leaf\ent,2+16
EndIf
PositionEntity leaf\ent,qx,qy,qz
EntityParent leaf\ent,trunk(1)

Return mesh
End Select
    
 End Function

Function addbranch(bx#,by#,bz#,bnx#,bny#,bnz#)
End Function

Function slide(dir,dirval#,kind=0,bypass=0,surf=0,tri=0)
If MouseX()>478 And bypass=0 Then Return
Select kind
Case 0;slide

If bypass<>0 Then 
 s=surf
 t=tri
 v=TriangleVertex(s,t,0)
       vy#=VertexY(s,v)
       vx#=VertexY(s,v)
       vz#=VertexY(s,v)

Else
    camp = CameraPick(Cam,MouseX()+pickoffsetx,MouseY()+pickoffsety)

 	 If camp <> 0 Then
      s=PickedSurface()
       t=PickedTriangle()
       v=TriangleVertex(s,t,0)

       vy#=VertexY(s,v)
       vx#=VertexY(s,v)
       vz#=VertexY(s,v)
     Else
      Return
     EndIf

EndIf
       ;find similar verts on y value
        For v=0 To CountVertices(s)-1
         If VertexY(s,v)=vx# Then 
Select dir
Case 0;x 
If KeyDown(29) Or KeyDown(157) Then  
For i=0 To CountVertices(s)-1
         vnx#=VertexX(s,i)+dirval#
         vny#=VertexY(s,i)
         vnz#=VertexZ(s,i)
         VertexCoords(s,i,vnx#,VnY#,vnz#)
         Next
Else
         vnx#=VertexX(s,v)+dirval#
         vny#=VertexY(s,v)
         vnz#=VertexZ(s,v)
          VertexCoords(s,v,vnx#,VnY#,vnz#)
EndIf

Case 1;x 
If KeyDown(29) Or KeyDown(157) Then  
For i=0 To CountVertices(s)-1
         vnx#=VertexX(s,i)
         vny#=VertexY(s,i)+dirval#
         vnz#=VertexZ(s,i)
         VertexCoords(s,i,vnx#,VnY#,vnz#)
         Next
Else
         vnx#=VertexX(s,v)
         vny#=VertexY(s,v)+dirval#
         vnz#=VertexZ(s,v)
          VertexCoords(s,v,vnx#,VnY#,vnz#)
EndIf

Case 2;x 
If KeyDown(29) Or KeyDown(157) Then  
For i=0 To CountVertices(s)-1
         vnx#=VertexX(s,i)
         vny#=VertexY(s,i)
         vnz#=VertexZ(s,i)+dirval#
         VertexCoords(s,i,vnx#,VnY#,vnz#)
         Next
Else
         vnx#=VertexX(s,v)
         vny#=VertexY(s,v)
         vnz#=VertexZ(s,v)+dirval#
          VertexCoords(s,v,vnx#,VnY#,vnz#)
EndIf

End Select
         EndIf

Next
Case 1;scale
If bypass<>0 Then 
 s=surf
 t=tri
 v=TriangleVertex(s,t,0)
       vy#=VertexY(s,v)
       vx#=VertexY(s,v)
       vz#=VertexY(s,v)

Else
    camp = CameraPick(Cam,MouseX()+pickoffsetx,MouseY()+pickoffsety)

 	 If camp <> 0 Then
      s=PickedSurface()
       t=PickedTriangle()
       v=TriangleVertex(s,t,0)

       vy#=VertexY(s,v)
       vx#=VertexY(s,v)
       vz#=VertexY(s,v)
     Else
      Return
     EndIf

EndIf
       ;find similar verts on y value
If KeyDown(29) Or KeyDown(157) Then  
For i=0 To CountVertices(s)-1
         vnx#=(VertexNX(s,I)/120)*dirval
         vny#=(VertexNY(s,I)/120)*dirval
         vnz#=(VertexNZ(s,I)/120)*dirval
         VertexCoords(s,I,VertexX(s,I)+vnx#,VertexY(s,I),VertexZ(s,I)+vnz#)
        Next
Else


        For v=0 To CountVertices(s)-1
         If VertexY(s,v)=vy# Then 
         vnx#=(VertexNX(s,v)/120)*dirval
         vny#=(VertexNY(s,v)/120)*dirval
         vnz#=(VertexNZ(s,v)/120)*dirval

          VertexCoords(s,v,VertexX(s,v)+vnx#,VertexY(s,v),VertexZ(s,v)+vnz#)
         EndIf
         Next
EndIf

End Select

End Function

Function add_leaf(style=0)
;If MouseX()>478 Then Return
Select style
Case 1 ;straight
    camp = CameraPick(Cam,MouseX()+pickoffsetx,MouseY()+pickoffsety)
 	 If camp <> 0 Then
      cent=PickedEntity()
      s=PickedSurface()
      t=PickedTriangle()
      v=TriangleVertex(s,t,0)

        
mesh = createtreequad(leaftex,mainshape$,leafscale,PickedX(),PickedY(),PickedZ(),1,1)
Upper(Left$(leaf\shapefile$,5))

;If Upper(Left$(leaf\shapefile$,5))="PREF_" Then EntityPickMode mesh,2

;RotateEntity mesh,Rand(-180,180),Rand(-180,180),Rand(-180,180)
;MoveEntity mesh,0,0,1

       EndIf
Case 0;pattern

    camp = CameraPick(Cam,MouseX()+pickoffsetx,MouseY()+pickoffsety)
 	 If camp <> 0 Then
      cent=PickedEntity()

;first find picked vert area
 
 s=PickedSurface();GetSurface(trunk(t),1)
 st=PickedTriangle()
 v=TriangleVertex(s,st,0)

 mesh= createtreequad(leaftex,mainshape$,leafscale,VertexX(s,v),VertexY(s,v),VertexZ(s,v),1,1)

 Updown=Rand(-300,300)
 leftright=Rand(-300,300)
 inout=Rand(-300,300)
 RotateEntity mesh,updown,leftright,inout
 If Upper(Left$(leaf\shapefile$,5))="PREF_" Then PointEntity mesh,cam
; If Upper(Left$(leaf\shapefile$,5))="PREF_" Then EntityPickMode mesh,2


 
EndIf
End Select
End Function

Function scale_leaf.leaf(sval#)
If selected<>Null Then 
l.leaf=seelctedleaf.leaf
  l\scale=l\scale+sval#
   If l\scale#<.5 Then l\scale#=.5
   ScaleEntity l\ent,l\scale,l\scale,l\scale
EndIf

;   EndIf
;EndIf
;Next
End Function

Function choosenewmap(override=-1)
 
Local choosen=0
If override<>-1 Then choosen=override
cox=200
coy=200
While choosen=0
If mousepressed1 Then 
 clickx=MouseX()-cox
 clicky=MouseY()-coy
If clickx>162 And clickx<200 And clicky>0 And clicky<40 Then choosen=-1
If clickx>10 And clickx<192 And clicky>48 And clicky<78 Then choosen=1
If clickx>10 And clickx<192 And clicky>88 And clicky<118 Then choosen=2
If clickx>10 And clickx<192 And clicky>128 And clicky<158 Then choosen=3
If clickx>10 And clickx<192 And clicky>168 And clicky<200 Then choosen=4
clickx=-1
clicky=-1
 EndIf
UpdateWorld()
RenderWorld()
 DrawBlock gui,GraphicsWidth()-171,GraphicsHeight()-192
 DrawBlock cgui,cox,coy
Flip()
Wend

If choosen<>-1 And choosen<>0 Then 
 For leaf.leaf=Each leaf
  EntityParent leaf\ent,0
  FreeBrush leaf\brush
  FreeEntity leaf\ent
  Delete leaf
  Next


For i=maxtrunks To 1 Step -1
 FreeEntity trunk(i)
Next

EndIf


Select choosen
; Create Cylinder manually
Case 1
maxtrunks=1
 ; enter how many segments the sphere has
 vsegs=3
 rsegs=1
 trunk(1)=createmycylinder(vsegs,rsegs)
 ;mycylinder=LoadMesh("base_tree.3ds")
 ScaleMesh trunk(1) ,.4,1,.4
 EntityPickMode trunk(1),2
 EntityTexture trunk(1),barktex
Case 2
maxtrunks=1
; enter how many segments the sphere has
 vsegs=6
 rsegs=6
 trunk(1)=createmycylinder(vsegs,rsegs)
 ;mycylinder=LoadMesh("base_tree.3ds")
 ScaleMesh trunk(1) ,.3,5,.3
 EntityPickMode trunk(1),2
 EntityTexture trunk(1),barktex
Case 3
 vsegs=6
 rsegs=6
For i=1 To 2
maxtrunks=2
; enter how many segments the sphere has
  trunk(i)=createmycylinder(vsegs,rsegs,False,0,i-1)
  ;mycylinder=LoadMesh("base_tree.3ds")
  ScaleMesh trunk(i) ,.3,5,.3
  EntityPickMode trunk(i),2
  EntityTexture trunk(i),barktex
 Next
 EntityParent trunk(2),trunk(1)
Case 4
maxtrunks=3
; enter how many segments the sphere has
vsegs=6
rsegs=6
 For i=0 To 2
  trunk(i+1)=createmycylinder(vsegs,rsegs,False,0,i-1)
  ;mycylinder=LoadMesh("base_tree.3ds")
  ScaleMesh trunk(i+1) ,.3,5,.3
  EntityPickMode trunk(i+1),2
  EntityTexture trunk(i+1),barktex
  Next
 EntityParent trunk(2),trunk(1)
 EntityParent trunk(3),trunk(1)

End Select


Return choosen
End Function

Function Load_new_tex(flag=0,sf$="")
sfilename3$=strippath$(sf$)


Select flag
Case 0
FreeTexture leaftex

   leaftex=LoadTexture_Safe(Lower(sfilename3$),4+16)
   leaftexname$=Lower$(sfilename3$)


Case 1 ;bark
   FreeTexture barktex
   barktexname$=Lower(sfilename3$)
   barktex=LoadTexture_safe(Lower(sfilename3$))
   For i=1 To maxtrunks
   EntityTexture trunk(i),barktex
   Next
   For l.leaf=Each leaf
   If Upper(Left$(l\shapefile$,5))="PREF_" Then  
   S= GetSurface(l\ent,1)
   b=CreateBrush()
   BrushTexture(b,barktex)
   PaintMesh l\ent,b
   FreeBrush b
   EndIf
   Next

End Select
; Else
; Return
; EndIf

End Function

Function savework(sw$="")

SFILENAME3$=SW$

  If Upper$(Right$(sFileName3$,3))<>"FTE" Then  
    sFileName3$=sfilename3$+".FTE"
     EndIf
  FN$=sFileName3$



;strip path
     ; so strip the path
	  For i2= Len(fn$) To 1 Step -1
	   If Mid$(fn$,i2,1)="\" Then
	    fn$=Right$(fn$,Len(fn$)-i2)
	    Exit
	   EndIf
	  Next


For l.leaf=Each leaf
ls1=GetSurface(l\ent,1)
b1=GetSurfaceBrush(ls1)
t1=GetBrushTexture(b1)
tn1$=TextureName(t1)
;strip path
     ; so strip the path
	  For i2= Len(tn1$) To 1 Step -1
	   If Mid$(tn1$,i2,1)="\" Then
	    tn1$=Right$(tn1$,Len(tn1$)-i2)
	    Exit
	   EndIf
	  Next
FreeTexture t1
FreeBrush b1
Next


;ok lets count branches
cb=0
 For leaf.leaf=Each leaf
 cb=cb+1
 Next

 ChangeDir BRANCHPATH$
sfile=WriteFile (fn$)
;new
 WriteFloat sfile, barkscale#
 WriteInt sfile, maxtrunks
 WriteInt sfile, cb


   WriteString sfile,barktexname$
   WriteString sfile,leaftexname$

;ok lets save the branch data.
 For i=1 To maxtrunks
   s=GetSurface(trunk(i),1)
   v=CountVertices(s)-1
  For vert=0 To v

  WriteFloat sfile,VertexX(s,vert)
  WriteFloat sfile,VertexY(s,vert)
  WriteFloat sfile,VertexZ(s,vert)
  Next
  Next
;trunks saved..
;now save leaf positions and rotations
For leaf.leaf=Each leaf
s=GetSurface(leaf\ent,1)
b=GetSurfaceBrush(s)
t=GetBrushTexture(b)
lt$=TextureName$(t)
;strip path
     ; so strip the path
	  For i2= Len(Lt$) To 1 Step -1
	   If Mid$(Lt$,i2,1)="\" Then
	    Lt$=Right$(Lt$,Len(Lt$)-i2)
	    Exit
	   EndIf
	  Next

FreeTexture t
FreeBrush n
If FileType(newpath$+lt$)<>1 Then Savetexture  t,newpath$+lt$


  WriteString sfile, lt$
  WriteString sfile,leaf\shapefile
  WriteFloat sfile,EntityX(leaf\ent,1)
  WriteFloat sfile,EntityY(leaf\ent,1)
  WriteFloat sfile,EntityZ(leaf\ent,1)
 
  WriteFloat sfile,EntityPitch(leaf\ent,1)
  WriteFloat sfile,EntityYaw(leaf\ent,1)
  WriteFloat sfile,EntityRoll(leaf\ent,1)
  WriteFloat sfile,leaf\scale#
  ;DebugLog EntityPitch(leaf\ent)+" "+EntityYaw(leaf\ent)+" "+EntityRoll(leaf\ent)

 Next


CloseFile sfile

End Function

Function loadwork(sf$="")
SFILENAME3$=SF$
 If sFileName3$<>""
;strip path
      ;so strip the path
	  For i2= Len(sfilename3$) To 1 Step -1
	   If Mid$(sfilename3$,i2,1)="\" Then
	    sfilename3$=Right$(sfilename3$,Len(sfilename3$)-i2)
	    Exit
	   EndIf
	  Next
EndIf
ChangeDir BRANCHPATH$
If FileType (SFILENAME3$)<>1 Then 
 Return
 EndIf
If sFileName3$<>"" And FileType (sfilename3$)=1
If FileType (SFILENAME3$) <> 1 Then RuntimeError ("Load file not found")
LFILE=ReadFile(SFILENAME3$)
barkscale#=ReadFloat(lfile)
NUMTRUNK=ReadInt(LFILE)
NUMLEAF=ReadInt(LFILE)
trunktext$=ReadString(lfile)
leafText$=ReadString(lfile)
FreeTexture leaftex
If FileType (leaftext$)<>1 Then 
defaulttex(leaftext$)
leaftex=LoadTexture(Lower(leaftext$),4+16)
 leaftexname$=Lower$(leaftext$)
   FreeImage l_prev
   l_prev=defaultimage()
Else
leaftex=LoadTexture(Lower(leaftext$),4+16)
leaftexname$=Lower$(leaftext$)
   FreeImage l_prev
   l_prev=LoadImage(Lower(leafText$))
   ResizeImage l_prev,64,64
EndIf
 FreeTexture barktex
   If FileType (Lower(trunkText$))<>1 Then RuntimeError ("Bark texture not found") 
   barktexname$=Lower(trunkText$)
   barktex=LoadTexture(Lower(trunkText$))
   FreeImage b_prev
b_prev=LoadImage(Lower(trunkText$))
ResizeImage b_prev,64,64
;ok load trunk data
choosenewmap numtrunk+1
;ok lets save the branch data.
For i=1 To maxtrunks
s=GetSurface(trunk(i),1)
v=CountVertices(s)-1
For vert=0 To v
tx#=ReadFloat(lfile)
ty#=ReadFloat(lfile)
tz#=ReadFloat(lfile)
VertexCoords s,vert,tx,ty,tz
Next
UpdateNormals trunk(i)
Next
;ok trunks loaded, now to the leaves
For i=1 To numleaf
	lln$=ReadString(lfile)
	sf$=ReadString (lfile)
	lx#=ReadFloat(lfile)
	ly#=ReadFloat(lfile)
	lz#=ReadFloat(lfile)
	lpit#=ReadFloat(lfile)
	lyaw#=ReadFloat(lfile)
	lrol#=ReadFloat(lfile)
	lscale#=ReadFloat(lfile)
	If FileType (lln$)<>1 Then 
		defaulttex(lln$)
		lt=LoadTexture(Lower(lln$),4+16)
		Else
		lt=LoadTexture(Lln$,1+4)
		EndIf
	mesh=createtreequad (lt,sf$,lscale#,lx#,ly#,lz#,0,1)
	RotateEntity mesh,lpit#,lyaw#,lrol#
	FreeTexture lt
Next
CloseFile lfile
EndIf
ScaleTexture barktex,barkscale#,barkscale#
If timage<>0 Then 
	timage=0
	FreeImage timage
	EndIf
ChangeDir thispath$

  			        tImage = loadimage_Safe(branchpath$+leaftext$)
					ResizeImage tImage, 100, 100
				    FUI_DeleteGadget(GUI_RIGHTWIN_leafcanvas)
                    GUI_RIGHTWIN_leafCanvas = FUI_ImageBox( GUI_RIGHTWIN, 5, 270, 100, 100,Timage)
					SetBuffer BackBuffer()
    			    FreeImage timage 
                    ChangeDir thispath$                  
				    tImage = loadimage_Safe(branchpath$+barktexname$)
		            ResizeImage timage,100,100  
				    FUI_DeleteGadget(GUI_RIGHTWIN_BARKCanvas)
		            GUI_RIGHTWIN_BARKCanvas = FUI_ImageBox( GUI_RIGHTWIN, 5, 80, 100, 100,Timage)
				    SetBuffer BackBuffer()
				    FreeImage timage 
				    ChangeDir thispath$                  

;


End Function

Function export(sf$="")
savework "exported.fte"
;msgtimer#=MilliSecs()+5000
;msgtxt$="Please Register FTE to enable exporting."
;Return
DebugLog("Export Filename is: " + sf$)
If sf$="" Then 
Else
sFileName3$=sf$
EndIf
If sfilename3$="" Then Return
Text 260,240,"EXPORTING...."
Flip

If sFileName3$<>""  Then 
  ; actually we save 2 files, when you enter "test", it will be "test_1.b3d" and "test_2.b3d"
  ; so the Win-Api-built-in overwriting-prompt isn't useful anyway.
  If Upper$(Right$(sFileName3$,4))=".B3D"Then 
  	sFileName3$=Left$(sFileName3$,Len(sFileName3$)-4)
  	EXPFLAG=1
  EndIf

  If Upper$(Right$(sFileName3$,5))=".EB3D" Then 
 	sFileName3$=Left$(sFileName3$,Len(sFileName3$)-5)
  	EXPFLAG=2
  EndIf

newpath$=exportpath$

If FileType(newpath$+barktexname$)<>1 Then Savetexture  barktexname$,newpath$+barktexname$

For l.leaf=Each leaf
ls1=GetSurface(l\ent,1)
b1=GetSurfaceBrush(ls1)
t1=GetBrushTexture(b1)
tn1$=TextureName(t1)
;strip path
     ; so strip the path
	  For i2= Len(tn1$) To 1 Step -1
	   If Mid$(tn1$,i2,1)="\" Then
	    tn1$=Right$(tn1$,Len(tn1$)-i2)
	    Exit
	   EndIf
	  Next
If FileType(newpath$+tn1$)<>1 Then  Savetexture  tn1$,newpath$+tn1$
FreeTexture t1
FreeBrush b1
Next


	If expflag=1 Then 
		mesh=optimizelayer (3)
		loadwork "exported.fte"
		part1$=sFileName3$+".b3d"
		;do not allow export over top of the scenery model
		If Lower$(Right$(part1$,10))="ground.b3d" Then Return
		savemultimesh mesh,PART1$
		If FileType (part1$)<>1 Then RuntimeError ("Error on export")
		tempmm=LoadMesh(part1$)
		standmesh tempmm
		savemultimesh tempmm,PART1$
		FreeEntity tempmm
			
		;tempdir$=CurrentDir$()
		;mesh=optimizelayer (3)
		;savework "exported.fte"
		;loadwork "exported.fte"
	
	ElseIf expflag=2 Then

		mesh=optimizelayer (3)
		loadwork "exported.fte"
		part1$=sFileName3$+".b3d"
		;do not allow export over top of the scenery model
		If Lower$(Right$(part1$,10))="ground.b3d" Then Return
		savemultimesh mesh,PART1$
		If FileType (part1$)<>1 Then RuntimeError ("Error on export")
		tempmm=LoadMesh(part1$)
		part1$=sFileName3$+".eb3d"
		standmesh tempmm
		savemultimesh tempmm,PART1$
		FreeEntity tempmm


;	old X saving
;	mesh=optimizelayer (4)
;	savework "exported.fte"
;	loadwork "exported.fte"
;	part2$=sFileName3$+".x"
;	;savemeshdx9 mesh,part2$
;	FreeEntity junk

	EndIf
	
	FreeEntity mesh
	FreeEntity mesh2


;ChangeDir  tempdir$
;If expflag=1 Then ExecFile thispath$+"view.exe "+part1$
;If expflag=2 Then ExecFile thispath$+"view.exe "+part2$



;tempdir$=CurrentDir()
;savework "exported.fte"

;mesh=optimizelayer (3)
;savemultimesh mesh,part1$
;FreeEntity mesh

;loadwork "exported.fte"
;ExecFile "view.exe "+part1$

ChangeDir thispath$

  EndIf
End Function


Function optimizelayer(layer)
Select layer


Case 0
;;;;;;;;;

op_mesh=CreateMesh()
;op_surface=CreateSurface(op_mesh)
For i=1 To maxtrunks
RotateMesh trunk(i),EntityPitch(trunk(i),True),EntityYaw(trunk(i),True),EntityRoll(trunk(i),True)
PositionMesh trunk(i),EntityX(trunk(i),True),EntityY(trunk(i),True),EntityZ(trunk(i),True)
Next
Return op_mesh


Case 1
op_mesh=CreateMesh()
;op_surface=CreateSurface(op_mesh)

For leaf.leaf=Each leaf
RotateMesh leaf\ent,EntityPitch(leaf\ent,True),EntityYaw(leaf\ent,True),EntityRoll(leaf\ent,True)
ScaleMesh leaf\ent,leaf\scale,leaf\scale,leaf\scale
PositionMesh leaf\ent,EntityX(leaf\ent,True),EntityY(leaf\ent,True),EntityZ(leaf\ent,True)
;AddMesh leaf\ent,destmesh
;mergeMesh(op_mesh,leaf\ent)
AddMesh leaf\ent,op_mesh
Next
EntityFX op_mesh,2
s=GetSurface (op_mesh,1)
For i=0 To CountVertices(s)-1
 r=255-(Rand(0,75))
 VertexColor s,i,r,r,r
Next
ScaleMesh op_mesh,50,50,50
Return op_mesh

Case 3

op_mesh=CreateMesh()
For i=1 To maxtrunks
RotateMesh trunk(i),EntityPitch(trunk(i),True),EntityYaw(trunk(i),True),EntityRoll(trunk(i),True)
PositionMesh trunk(i),EntityX(trunk(i),True),EntityY(trunk(i),True),EntityZ(trunk(i),True)
If FileType(branchpath$+barktexname$)<>1 Then RuntimeError ("Error : Leaf texture not found")
b=LoadBrush(branchpath$+barktexname$)
s=GetSurface(trunk(i),1)
PaintSurface s,b
AddMesh trunk(i),op_mesh
FreeBrush b
;ddMesh trunk(i),op_mesh
Next

For leaf.leaf=Each leaf
RotateMesh leaf\ent,EntityPitch(leaf\ent,True),EntityYaw(leaf\ent,True),EntityRoll(leaf\ent,True)
ScaleMesh leaf\ent,leaf\scale,leaf\scale,leaf\scale
PositionMesh leaf\ent,EntityX(leaf\ent,True),EntityY(leaf\ent,True),EntityZ(leaf\ent,True)
s=GetSurface(leaf\ent,1)
b=GetSurfaceBrush(s)
tex=GetBrushTexture(b)
tn$=TextureName$(tex)
If FileType(tn$)<>1 Then RuntimeError ("Error : Leaf brush texture not found")
b2=LoadBrush(tn$)
PaintMesh leaf\ent,b2
S2=CreateSurface (op_mesh,b)
AddMeshtosurface leaf\ent,op_mesh,s2
FreeTexture tex
FreeBrush b
FreeBrush b2
Next

EntityFX op_mesh,2
s=GetSurface (op_mesh,1)
For i=0 To CountVertices(s)-1
 r=255-(Rand(0,75))
 VertexColor s,i,r,r,r
Next


ScaleMesh op_mesh,50,50,50
Return op_mesh


Case 4

op_mesh=CreateMesh()
For i=1 To maxtrunks
RotateMesh trunk(i),EntityPitch(trunk(i),True),EntityYaw(trunk(i),True),EntityRoll(trunk(i),True)
PositionMesh trunk(i),EntityX(trunk(i),True),EntityY(trunk(i),True),EntityZ(trunk(i),True)
If FileType(branchpath$+barktexname$)<>1 Then RuntimeError ("Error : Bark texture not found")
b=LoadBrush(branchpath$+barktexname$)
s=GetSurface(trunk(i),1)
PaintSurface s,b
AddMesh trunk(i),op_mesh
FreeBrush b
;ddMesh trunk(i),op_mesh
Next

For leaf.leaf=Each leaf
RotateMesh leaf\ent,EntityPitch(leaf\ent,True),EntityYaw(leaf\ent,True),EntityRoll(leaf\ent,True)
ScaleMesh leaf\ent,leaf\scale,leaf\scale,leaf\scale
PositionMesh leaf\ent,EntityX(leaf\ent,True),EntityY(leaf\ent,True),EntityZ(leaf\ent,True)
s=GetSurface(leaf\ent,1)
b=GetSurfaceBrush(s)
tex=GetBrushTexture(b)
If FileType(tn$)<>1 Then RuntimeError ("Error : Bark texture not found")
tn$=TextureName$(tex)
b2=LoadBrush(tn$)
PaintMesh leaf\ent,b2
junk=CopyMesh(leaf\ent) 
PaintMesh junk,b2
FlipMesh junk
AddMesh junk,op_mesh
AddMesh leaf\ent,op_mesh
FreeEntity junk
FreeTexture tex
FreeBrush b
FreeBrush b2
Next

EntityFX op_mesh,2
s=GetSurface (op_mesh,1)
For i=0 To CountVertices(s)-1
 r=255-(Rand(0,75))
 VertexColor s,i,r,r,r
Next


ScaleMesh op_mesh,50,50,50
Return op_mesh
End Select


 
End Function

; -------------------------------------------------------------------------------------------------------------------
; This function copies a mesh with a single surface to a specific surface of another mesh.
;
; The function can optionally offset the mesh within the destination mesh as it copies it.
; The last six parameters may be omitted when calling the function if no transformation is desired.
; -------------------------------------------------------------------------------------------------------------------
Function AddMeshToSurface(SrcMesh, DestMesh, DestSurface, Voffsetx#=0, Voffsety#=0, Voffsetz#=0, Vpitch#=0, Vyaw#=0, Vroll#=0, Vscalex#=1, Vscaley#=1, VscaleZ#=1)

	; Determine if we can optimize the mesh copying.
	TransformVertices = True
	If (Vpitch#=0) And (Vyaw#=0) And (Vroll#=0) 
		TransformVertices = False
	EndIf


	; Make sure there's a surface to copy, because the mesh might be empty.
	If CountSurfaces(SrcMesh) > 0

		SrcSurface = GetSurface(SrcMesh, 1)

		DestVerts  = CountVertices(DestSurface)
		SrcVerts   = CountVertices(SrcSurface)
			
		; If we need to transform the vertices in a complex way...
		If TransformVertices

			; Do slower copy method because we need to rotate the vertices.

			; Create a pivot to do the transformations with.
			ThisPivot = CreatePivot()
			PositionEntity ThisPivot, Voffsetx#, Voffsety#, Voffsetz#, True
			RotateEntity ThisPivot, Vpitch#, Vyaw#, Vroll#, True
			ScaleEntity ThisPivot, Vscalex#, Vscaley#, Vscalez#

			; Copy all the vertices from the source mesh to the destination surface.
			For VertLoop = 0 To SrcVerts-1
					
				Vu#  = VertexU#(SrcSurface, VertLoop)
				Vv#  = VertexV#(SrcSurface, VertLoop)		
				Vw#  = VertexW#(SrcSurface, VertLoop)
				Vr   = VertexRed(SrcSurface, VertLoop)
				Vg   = VertexGreen(SrcSurface, VertLoop)
				Vb   = VertexBlue(SrcSurface, VertLoop)
			
				TFormPoint VertexX#(SrcSurface, VertLoop), VertexY#(SrcSurface, VertLoop), VertexZ#(SrcSurface, VertLoop), ThisPivot, 0
				Vx# = TFormedX#()
				Vy# = TFormedY#()
				Vz# = TFormedZ#()

				TFormNormal VertexNX#(SrcSurface, VertLoop), VertexNY#(SrcSurface, VertLoop), VertexNZ#(SrcSurface, VertLoop), ThisPivot, 0
				Vnx# = TFormedX#()
				Vny# = TFormedY#()
				Vnz# = TFormedZ#()
			
				AddVertex(DestSurface, Vx#, Vy#, Vz#, Vu#, Vv#, Vw#)
				VertexNormal(DestSurface, VertLoop+DestVerts, Vnx#, Vny#, Vnz#)
				VertexColor(DestSurface, VertLoop+DestVerts, Vr, Vg, Vb) 
	
			Next

			FreeEntity ThisPivot

		Else

			; Do the fast copy.
			; Fast copy can do offset and scaling, but not rotation.

			; Copy all the vertices from the source mesh to the destination surface.
			For VertLoop = 0 To SrcVerts-1

					
				Vx#  = VertexX#(SrcSurface, VertLoop)
				Vy#  = VertexY#(SrcSurface, VertLoop)
				Vz#  = VertexZ#(SrcSurface, VertLoop)
				Vu#  = VertexU#(SrcSurface, VertLoop)
				Vv#  = VertexV#(SrcSurface, VertLoop)		
				Vw#  = VertexW#(SrcSurface, VertLoop)
				Vnx# = VertexNX#(SrcSurface, VertLoop)
				Vny# = VertexNY#(SrcSurface, VertLoop)
				Vnz# = VertexNZ#(SrcSurface, VertLoop)						
				Vr   = VertexRed(SrcSurface, VertLoop)
				Vg   = VertexGreen(SrcSurface, VertLoop)
				Vb   = VertexBlue(SrcSurface, VertLoop)
		
				AddVertex(DestSurface, (Vx#*Vscalex#)+Voffsetx#, (Vy#*Vscaley#)+Voffsety#, (Vz#*Vscalez#)+Voffsetz#, Vu#, Vv#, Vw#)
				VertexNormal(DestSurface, VertLoop+DestVerts, Vnx#, Vny#, Vnz#)
				VertexColor(DestSurface, VertLoop+DestVerts, Vr, Vg, Vb) 
	
			Next

		EndIf


		; Copy all triangles from the source surface to the destination surface.	
		SrcTris  = CountTriangles(SrcSurface)
		For TriLoop = 0 To SrcTris-1
	
			V0 = TriangleVertex(SrcSurface, TriLoop, 0)
			V1 = TriangleVertex(SrcSurface, TriLoop, 1)
			V2 = TriangleVertex(SrcSurface, TriLoop, 2)
		
			AddTriangle(DestSurface, V0+DestVerts, V1+DestVerts, V2+DestVerts)
	
		Next
		
	EndIf

			
End Function

Function standall()
Return
For leaf.leaf=Each leaf
   FreeBrush brush
   FreeBrush leaf\brush
   FreeEntity leaf\ent
   Delete leaf
Next
numleaf=Rand(1,10)
rtrunks=Rand(2,4)
choosenewmap rtrunks
rtrunks=rtrunks-1
maxtrunks=rtrunks

For i=1 To rtrunks
s=GetSurface(trunk(i),1)
 For v=0 To CountVertices(s)-1 Step 6
SeedRnd(MilliSecs())
dirvalx#=Rnd(-1,1)
dirvaly#=Rnd(0,0)
dirvalz#=Rnd(-1,1)


gotit=v
       For v2=0 To CountVertices(s)-1
;locate one below it
         If VertexY(s,v2)>VertexY(s,v) Then 
           gotit=v2

           Exit
          EndIf
          Next
sval#=Rnd(-8.5,40.5)
oldx#=VertexX(s,gotit)
oldy#=VertexY(s,V)
oldz#=VertexZ(s,gotit)

       For v2=0 To CountVertices(s)-1
;rand pos
         If VertexY(s,v2)=oldy# Then 
         vnx#=(VertexX(s,v2))+dirvalx
         vny#=(VertexY(s,v2))+dirvaly
         vnz#=(VertexZ(s,v2))+dirvalz
          VertexCoords(s,v2,vnx#,vny,vnz)
;rand scale
         vnx#=(VertexNX(s,v2)/120)*sval
         vny#=(VertexNY(s,v2)/120)*sval
         vnz#=(VertexNZ(s,v2)/120)*sval

         VertexCoords(s,v2,VertexX(s,v2)+vnx#,VertexY(s,v2),VertexZ(s,v2)+vnz#)


         EndIf
        Next


Next
Next

;mesh = createtreequad(leaftex,mainshape$,leafscale,PickedX(),PickedY(),PickedZ(),1,1)
;RotateEntity mesh,Rand(-180,180),Rand(-180,180),Rand(-180,180)

;rand tex
FreeTexture leaftex
leaftex=LoadTexture(branchpath$+"branch"+Rand(1,22)+".png",1+4)
For i=1 To numleaf
;rand leaf shape
thiskind=Rand(1,4)
Select thiskind
 Case 1
 mainshape$="quadleaf.3ds"
 Case 2
 mainshape$="funkyleaf.3ds"
 Case 3
 mainshape$="roundleaf.3ds"
 Case 4
 mainshape$="1polyleaf.3ds"
End Select 

;randscale
thisscale#=Rnd(.5,8)
;rand position
   s=GetSurface(trunk(Rand(1,maxtrunks)),1)
   v=Rand(0,CountVertices(s)-1)


mesh = createtreequad(leaftex,mainshape$,thisscale,VertexX(s,v),VertexY(s,v),VertexZ(s,v),0,1)
RotateEntity mesh,Rnd(-300,300),Rnd(-300,300),Rnd(-300,300)




Next
End Function

;Merge all your entities into one
;matching surfaces.

;Call this function to merge your entities.
Function BEGINMERGE(ent)
	DestMesh=CreateMesh()
	RotateMesh ent,EntityPitch(ent,True),EntityYaw(ent,True),EntityRoll(ent,True)
	PositionMesh ent,EntityX(ent,True),EntityY(ent,True),EntityZ(ent,True)
	AddMesh ent,DestMesh
	
	If CountChildren(ent)>0 Then
		For s=1 To CountChildren(ent)
			MERGEMESH destmesh,GetChild(ent,s)
		Next
	EndIf
	Return destmesh
End Function

Function MERGEMESH(destmesh,ent)
	RotateMesh ent,EntityPitch(ent,True),EntityYaw(ent,True),EntityRoll(ent,True)
	PositionMesh ent,EntityX(ent,True),EntityY(ent,True),EntityZ(ent,True)
	AddMesh ent,destmesh
	If CountChildren(ent)>0 Then
		For s=1 To CountChildren(ent)
			MERGEMESH destmesh,GetChild(ent,s)
		Next
	EndIf
End Function

Function create_skybox()
If FileType(datapath$+"front.jpg")<>1 Then RuntimeError ("Skybox image missing")
If FileType(datapath$+"right.jpg")<>1 Then RuntimeError ("Skybox image missing")
If FileType(datapath$+"back.jpg")<>1 Then RuntimeError ("Skybox image missing")
If FileType(datapath$+"up.jpg")<>1 Then RuntimeError ("Skybox image missing")
If FileType(datapath$+"left.jpg")<>1 Then RuntimeError ("Skybox image missing")
If FileType(datapath$+"down.jpg")<>1 Then RuntimeError ("Skybox image missing")



    mesh = CreateMesh()

    ;front face
    brush = LoadBrush(datapath$+"front.jpg",49)
    surface = CreateSurface(mesh,brush)
    AddVertex surface,-1,+1,-1,0,0
    AddVertex surface,+1,+1,-1,1,0
    AddVertex surface,+1,-1,-1,1,1
    AddVertex surface,-1,-1,-1,0,1
    AddTriangle surface,0,1,2
    AddTriangle surface,0,2,3
    FreeBrush brush

    ;right face
    brush = LoadBrush(datapath$+"Right.jpg",49)
    surface = CreateSurface(mesh,brush)
    AddVertex surface,+1,+1,-1,0,0
    AddVertex surface,+1,+1,+1,1,0
    AddVertex surface,+1,-1,+1,1,1
    AddVertex surface,+1,-1,-1,0,1
    AddTriangle surface,0,1,2
    AddTriangle surface,0,2,3
    FreeBrush brush

    ;back face
    brush = LoadBrush(datapath$+"back.jpg",49)
    surface = CreateSurface(mesh,brush)
    AddVertex surface,+1,+1,+1,0,0
    AddVertex surface,-1,+1,+1,1,0
    AddVertex surface,-1,-1,+1,1,1
    AddVertex surface,+1,-1,+1,0,1
    AddTriangle surface,0,1,2
    AddTriangle surface,0,2,3
    FreeBrush brush
 
    ;left face
    brush = LoadBrush(datapath$+"left.jpg",49)
    surface = CreateSurface(mesh,brush)
    AddVertex surface,-1,+1,+1,0,0
    AddVertex surface,-1,+1,-1,1,0
    AddVertex surface,-1,-1,-1,1,1
    AddVertex surface,-1,-1,+1,0,1
    AddTriangle surface,0,1,2
    AddTriangle surface,0,2,3
    FreeBrush brush

    ;top face
    brush = LoadBrush(datapath$+"up.jpg",49)
    surface = CreateSurface(mesh,brush)
    AddVertex surface,-1,+1,+1,0,1
    AddVertex surface,+1,+1,+1,0,0
    AddVertex surface,+1,+1,-1,1,0
    AddVertex surface,-1,+1,-1,1,1
    AddTriangle surface,0,1,2
    AddTriangle surface,0,2,3
    FreeBrush brush
   
    ;bottom face 
    brush = LoadBrush(datapath$+"down.jpg",49)
    surface = CreateSurface(mesh,brush)
    AddVertex surface,-1,-1,-1,1,0
    AddVertex surface,+1,-1,-1,1,1
    AddVertex surface,+1,-1,+1,0,1
    AddVertex surface,-1,-1,+1,0,0
    AddTriangle surface,0,1,2
    AddTriangle surface,0,2,3
    FreeBrush brush
    
    ScaleMesh mesh,60,60,60
    FlipMesh mesh
    EntityFX mesh,1+8 ; make fullbright
    EntityOrder mesh,1     
    Return mesh
End Function

Function savetexture(txname$,pth$)
;newimage=CreateImage(TextureWidth(tx),TextureHeight(tx))
;CopyRect 0,0,ImageWidth(newimage),ImageHeight(newimage),0,0,TextureBuffer(tx),ImageBuffer(newimage)
;SaveImage newimage,pth$
;[edit] bitz cannot save PNG it was .png but BMP file
;so now im just doing a copyfile
CopyFile BRANCHPATH$+txname$,pth$
End Function

Function changeshape(sf$)

If sf$="" Then 
Return
Else
sfilename3$=sf$
EndIf

;strip path
      ;so strip the path
	  For i2= Len(sfilename3$) To 1 Step -1
	   If Mid$(sfilename3$,i2,1)="\" Then
	    sfilename3$=Right$(sfilename3$,Len(sfilename3$)-i2)
	    Exit
	   EndIf
	  Next

      mainshape$=sfilename3$

End Function


Function copyleaf()
 msgtimer#=MilliSecs()+5000
 msgtxt$="COPYATTEMPT"

	moveleaf.leaf=selected.leaf
    If moveleaf = Null Then Return

 msgtimer#=MilliSecs()+5000
 msgtxt$="SELECTED FOUND"

s=GetSurface(moveleaf\ent,1)
b=GetSurfaceBrush(s)
t=GetBrushTexture(b)

mesh = createtreequad(t,moveleaf\shapefile$,moveleaf\scale#,EntityX(moveleaf\ent,1),EntityY(moveleaf\ent,1),EntityZ(moveleaf\ent,1),1,1)
RotateEntity mesh,EntityPitch(moveleaf\ent,1),EntityYaw(moveleaf\ent,1),EntityRoll(moveleaf\ent,1)
selected.leaf=Last leaf
For l.leaf=Each leaf
 EntityBlend l\ent,1
 If l.leaf=selected.leaf Then EntityBlend l\ent,3
Next
 msgtimer#=MilliSecs()+5000
 msgtxt$="COPY COMPLETE"

End Function 

Function deleteleaf()
For leaf.leaf=Each leaf

If leaf.leaf = selected.leaf Then 
   FreeBrush leaf\brush
   FreeEntity leaf\ent
   Delete leaf
Exit
EndIf
Next
End Function

Function checkzmouse()
lll.leaf=First leaf
If lll.leaf=Null Then Return
Select MouseZSpeed()
Case 1
If selected.leaf=Null Then 
selected.leaf=First leaf
Else
		selected.leaf = After selected.leaf
EndIf

For l.leaf=Each leaf
 EntityBlend l\ent,1
 If l.leaf=selected.leaf Then EntityBlend l\ent,3
Next


Case -1
If selected.leaf=Null Then 
selected.leaf=Last leaf
Else
		selected.leaf = Before selected.leaf
EndIf

For l.leaf=Each leaf
 EntityBlend l\ent,1
 If l.leaf=selected.leaf Then EntityBlend l\ent,3
Next
End Select
End Function

Function defaulttex(fname$)
cimag=CreateImage(128,128)
SetBuffer ImageBuffer(cimag)
For i=0 To 127
For ii=0 To 127
 Color 50,200,80
 Plot i,ii
Next
Next
Text 50,50,"NO TEXTURE"
SetBuffer BackBuffer()
SaveImage cimag,fname$
End Function

Function defaultimage()
ctex=CreateTexture(128,128)
ctex=CreateImage(64,64)
SetBuffer ImageBuffer(ctex)
For i=0 To 64
For ii=0 To 64
 Color 50,200,80
 Plot i,ii
Next
Next
SetBuffer BackBuffer()
Return ctex
End Function

Function StandMesh (entity) 
FitMesh entity, -(MeshWidth (entity) / 2), (MeshHeight (entity) ), -(MeshDepth (entity) / 2), MeshWidth (entity), -MeshHeight(ENTITY), MeshDepth (entity) 
End Function

Function Randomize_Tree()
SeedRnd MilliSecs()*10
;first lets decide how many trunks
max_rand_trunks=Rand(1,3)
choosenewmap max_rand_trunks+1
;record min to max height

For trunkloop=1 To max_rand_trunks
	rss=GetSurface(trunk(trunkloop),1)
	index=1
	;record each layer
	For i=0 To CountVertices(rss)-1 

	If VertexY(rss,i)<>oldverty# Then 
	 oldverty#=VertexY(rss,i)
	 treerecord#(trunkloop,index,1)=VertexY(rss,i)
	 treerecord#(trunkloop,index,2)=index
	 treerecord#(trunkloop,index,3)=i
	 index=index+1
	 EndIf

	Next
	maxof=index
	minh#=-100000
;ok sort from lowest to highest
	For place=1 To maxof
	For sort=1 To maxof
	 If treerecord#(trunkloop,sort,1)<minh# Then 
	    minh#=treerecord#(trunkloop,sort,1)
	    indexval=sort
	  EndIf
	Next
	treerecord2#(trunkloop,place,3)=indexval
	Next
	Next


;the random tree
;first well do girth of each branch
For trunkloop=1 To max_Rand_trunks
  For i=1 To maxof
    ;ok here well just 
Next
Next
 
End Function


Function OLDCHECK(bname$)
Select bname$

Case "paint"
 actionmode=2004
Case "paint2"
 msgtimer#=MilliSecs()+10000
 msgtxt$="PAINTED LEAF"
	If actionmode=5000 Then 
For l.leaf=Each leaf
 If Upper(Left$(l\shapefile$,5))<>"PREF_" Then  
   S= GetSurface(l\ent,1)
   b=CreateBrush()
   BrushTexture(b,leaftex)
   PaintMesh l\ent,b
   FreeBrush b
  EndIf
	Next
	Else

moveleaf.leaf = selected.leaf
If moveleaf <> Null Then  
   S= GetSurface(moveleaf\ent,1)
   b=CreateBrush()
   BrushTexture(b,leaftex)
   PaintMesh moveleaf\ent,b
   FreeBrush b
 EndIf
EndIf

Case "barkuv+"
barkscale#=barkscale#-(.60/FPS)
ScaleTexture barktex,barkscale#,barkscale#
Case "barkuv-"
barkscale#=barkscale#+(.60/FPS)
ScaleTexture barktex,barkscale#,barkscale#


Case "nextleaf+"
l.leaf=First leaf
If l <>Null Then 
If selected.leaf=Null Then selected.leaf=First leaf
If selected.leaf=Last leaf Then 
	selected.leaf=First leaf
		Else
        selected.leaf = After selected.leaf
	EndIf

For l.leaf=Each leaf
 EntityBlend l\ent,1
 If l.leaf=selected.leaf Then EntityBlend l\ent,3
Next
EndIf

Case "nextleaf-"
l.leaf=First leaf
If l <>Null Then 
If selected.leaf=Null Then selected.leaf=Last leaf
If selected.leaf=First leaf Then 
	selected.leaf=Last leaf
		Else
		selected.leaf = Before selected.leaf
	EndIf
For l.leaf=Each leaf
 EntityBlend l\ent,1
 If l.leaf=selected.leaf Then EntityBlend l\ent,3
Next
EndIf


Case "cam+"
 MoveEntity camera,0,0,15.0 / FPS
Case "cam-"
 MoveEntity camera,0,0,-15.0 / FPS


Case "lscale+"
If actionmode=5000 Then 
For l.leaf=Each leaf
 If Upper(Left$(l\shapefile$,5))<>"PREF_" Then  
  l\scale=l\scale+6.0 / FPS
  ScaleEntity  l\ent,l\scale,l\scale,l\scale
 EndIf
Next
Else
If selected<>Null Then 
l.leaf=selected.leaf
  l\scale=l\scale+ 6.0 / FPS
   If l\scale#<.5 Then l\scale#=.5
   ScaleEntity l\ent,l\scale,l\scale,l\scale
EndIf
EndIf


Case "lscale-"
If actionmode=5000 Then 
For l.leaf=Each leaf
 If Upper(Left$(l\shapefile$,5))<>"PREF_" Then  
  l\scale=l\scale-6.0/FPS
 ScaleEntity  l\ent,l\scale,l\scale,l\scale
 EndIf
Next
Else
If selected<>Null Then 
l.leaf=selected.leaf
  l\scale=l\scale+-6.0 / FPS
   If l\scale#<.5 Then l\scale#=.5
   ScaleEntity l\ent,l\scale,l\scale,l\scale
EndIf
EndIf

Case "deleteleaf"
deleteleaf()
Case "copyleaf"
copyleaf()
Case "moveleft"
	If actionmode=5000 Then 
	For l.leaf=Each leaf
 If Upper(Left$(l\shapefile$,5))<>"PREF_" Then  	 TranslateEntity l\ent,-3.0 / FPS,0,0
	Next
	Else
	 moveleaf.leaf=selected.leaf
	If moveleaf <> Null Then  TranslateEntity moveleaf\ent,-3.0 / FPS,0,0
	EndIf
Case "moveright"
	If actionmode=5000 Then 
	For l.leaf=Each leaf
 If Upper(Left$(l\shapefile$,5))<>"PREF_" Then  	 TranslateEntity l\ent,3.0 / FPS,0,0
	Next
	Else
	 moveleaf.leaf=selected.leaf
	If moveleaf <> Null Then  TranslateEntity moveleaf\ent,3.0 / FPS,0,0
	EndIf
Case "moveup"
	If actionmode=5000 Then 
	For l.leaf=Each leaf
 If Upper(Left$(l\shapefile$,5))<>"PREF_" Then  	 TranslateEntity l\ent,0,3.0 / FPS,0
	Next
	Else
	 moveleaf.leaf=selected.leaf
	If moveleaf <> Null Then  TranslateEntity moveleaf\ent,0,3.0 / FPS,0
	EndIf
Case "movedown"
	If actionmode=5000 Then 
	For l.leaf=Each leaf
 If Upper(Left$(l\shapefile$,5))<>"PREF_" Then TranslateEntity l\ent,0,-3.0 / FPS,0
	Next
	Else
	 moveleaf.leaf=selected.leaf
	If moveleaf <> Null Then  TranslateEntity moveleaf\ent,0,-3.0 / FPS,0
	EndIf 
Case "movein"
	If actionmode=5000 Then 
	For l.leaf=Each leaf
 If Upper(Left$(l\shapefile$,5))<>"PREF_" Then  	 TranslateEntity l\ent,0,0,3.0 / FPS
	Next
	Else
	 moveleaf.leaf=selected.leaf
	If moveleaf <> Null Then  TranslateEntity moveleaf\ent,0,0,3.0 / FPS
	EndIf 

Case "moveout"
	If actionmode=5000 Then 
	For l.leaf=Each leaf
 If Upper(Left$(l\shapefile$,5))<>"PREF_" Then  	 TranslateEntity l\ent,0,0,-3.0 / FPS
	Next
	Else
	 moveleaf.leaf=selected.leaf
	 If moveleaf <> Null Then  TranslateEntity moveleaf\ent,0,0,-3.0 / FPS
	EndIf 

Case "turnleft"
	If actionmode=5000 Then 
	For l.leaf=Each leaf
 If Upper(Left$(l\shapefile$,5))<>"PREF_" Then  TurnEntity l\ent,0,0,90.0 / FPS
	Next
	Else
	 moveleaf.leaf=selected.leaf
	If moveleaf <> Null Then  TurnEntity moveleaf\ent,0,0,90.0 / FPS
	EndIf 

Case "turnright"
	If actionmode=5000 Then 
	For l.leaf=Each leaf
 If Upper(Left$(l\shapefile$,5))<>"PREF_" Then  TurnEntity l\ent,0,0,-90.0 / FPS
	Next
	Else
	 moveleaf.leaf=selected.leaf
	If moveleaf <> Null Then  TurnEntity moveleaf\ent,0,0,-90.0 / FPS
    EndIf
Case "turnin"
	If actionmode=5000 Then 
	For l.leaf=Each leaf
 If Upper(Left$(l\shapefile$,5))<>"PREF_" Then   TurnEntity l\ent,90.0 / FPS,0,0
	Next
	Else
	 moveleaf.leaf=selected.leaf
	If moveleaf <> Null Then  TurnEntity moveleaf\ent,90.0 / FPS,0,0
    EndIf
Case "turnout"
	If actionmode=5000 Then 
	For l.leaf=Each leaf
 If Upper(Left$(l\shapefile$,5))<>"PREF_" Then     TurnEntity l\ent,-90.0 / FPS,0,0
	Next
	Else
    moveleaf.leaf=selected.leaf
	If moveleaf <> Null Then  TurnEntity moveleaf\ent,-90.0 / FPS,0,0
    EndIf
Case "turnup"
	If actionmode=5000 Then 
	For l.leaf=Each leaf
    If Upper(Left$(l\shapefile$,5))<>"PREF_" Then   TurnEntity l\ent,0,90.0 / FPS,0
	Next
	Else
	 moveleaf.leaf=selected.leaf
	If moveleaf <> Null Then  TurnEntity moveleaf\ent,0,90.0 / FPS,0
    EndIf
Case "turndown"
	If actionmode=5000 Then 
	For l.leaf=Each leaf
    If Upper(Left$(l\shapefile$,5))<>"PREF_" Then  TurnEntity l\ent,0,-90.0 / FPS,0
	Next
	Else

	moveleaf.leaf=selected.leaf
	If moveleaf <> Null Then  TurnEntity moveleaf\ent,0,-90.0 / FPS,0
     EndIf
       
Case "changeshape"
If actionmode=5000 Then 
 For leaf.leaf=Each leaf
 If Upper(Left$(leaf\shapefile$,5))<>"PREF_" Then  

   px#=EntityX(leaf\ent,1)
   py#=EntityY(leaf\ent,1)
   pz#=EntityZ(leaf\ent,1)

   ppitch#=EntityPitch(leaf\ent,1)
   pyaw#=EntityYaw(leaf\ent,1)
   proll#=EntityRoll(leaf\ent,1)

 If FileType (shapepath$+mainshape$)<>1 Then RuntimeError ("Selected shape file not found")
 mesh= LoadMesh(shapepath$+mainshape$)
 s=GetSurface(mesh,1)
 FreeBrush leaf\brush
 FreeEntity leaf\ent
 leaf\ent=mesh
 leaf\brush=CreateBrush()
 ;leaf\scale#=scale
 leaf\brush=GetSurfaceBrush(s)
 leaf\shapefile$=mainshape$
 BrushTexture leaf\brush,leaftex
 PaintMesh mesh,leaf\brush
 EntityFX leaf\ent,2+16
 EntityParent leaf\ent,trunk(1)

PositionEntity  leaf\ent,px,py,pz  
ScaleEntity leaf\ent,leaf\scale#,leaf\scale#,leaf\scale#
RotateEntity leaf\ent,ppitch,pyaw,proll
EndIf  
 Next
EndIf

    Case "scale-"
leafscale=leafscale-1
If leafscale<1 Then leafscale=1
    Case "scale+"
leafscale=leafscale+1
If leafscale>14 Then leafscale=13

Case "tilt_down"
 RotateEntity world,EntityPitch(world),0,0 
 TurnEntity world,-1,0,0
Case "tilt_up"
 RotateEntity world,EntityPitch(world),0,0 
 TurnEntity world,1,0,0
Case "turn_left"
 RotateEntity world,0,EntityYaw(world),0 
 TurnEntity world,0,-1,0
Case "turn_right"
 RotateEntity world,0,EntityYaw(world),0 
 TurnEntity world,0,1,0

End Select
End Function

Function Thicken()
ChangeDir thispath$
;Type leaf
; Field ent
; Field scale#
; Field brush
; Field shapefile$
; End Type
For l.leaf=Each leaf
 closes=0
;COUNT CLOSE BY LEAVES
 For lc.leaf=Each leaf
  If EntityDistance(lc\ent,l\ent)<=1 Then closes=closes+1
 Next

 If Rand(1,3)>1 And Instr(l\shapefile$,"PREF")=0 And closes<5 Then 
   l2.leaf=New leaf
    l2\shapefile$=l\shapefile#
    l2\ent=LoadMesh(shapepath$+l\shapefile$)  
    l2\scale=l\scale
    l2\scale=l2\scale+(Rnd(-.5,.5))
    L2\BRUSH=L\BRUSH
    If l2\scale<.01 Then l2\scale=.01
    ScaleEntity l2\ent,l2\scale#,l2\scale#,l2\scale#
    PositionEntity l2\ent,EntityX(l\ent,1)+Rnd(-.5,.5),EntityY(l\ent,1)+Rnd(-.5,.5),EntityZ(l\ent,1)+Rnd(-.5,.5)
    RotateEntity l2\ent,Rand(-180,180),Rand(-180,180),Rand(-180,180)
    PaintMesh L2\ENT,L\BRUSH
     EndIf
Next 
End Function 

Function Thin()
For l.leaf=Each leaf
 closes=0
;COUNT CLOSE BY LEAVES
 For lc.leaf=Each leaf
  If EntityDistance(lc\ent,l\ent)<2 Then closes=closes+1
 Next

 If Rand(1,3)>1 And Instr(l\shapefile$,"PREF")=0 And closes=>5 Then  
     FreeEntity L\ENT
     Delete L 
     EndIf
Next 
End Function 

Function RandAll()
For l.leaf=Each leaf
 If  Instr(l\shapefile$,"PREF")=0 
    RotateEntity l\ent,Rand(-180,180),Rand(-180,180),Rand(-180,180)
   EndIf
Next 
End Function 


Function BUTTONCHECK()
;; BUTTONS THAT ARE TO BE HELD DOWN
If MouseDown(1)

If fui_OVERGADGET(GUI_RIGHTWIN_BarkUV_NEG)  Then   
 	barkscale#=barkscale#-.60/FPS
    ScaleTexture barktex,barkscale#,barkscale#
ElseIf fui_OVERGADGET(GUI_RIGHTWIN_BarkUV_pos) Then 
    barkscale#=barkscale#+.60/FPS
    ScaleTexture barktex,barkscale#,barkscale#
ElseIf fui_OVERGADGET(GUI_RIGHTWIN_LEFT) Then 
                 oldcheck "moveleft"
ElseIf fui_OVERGADGET(GUI_RIGHTWIN_RIGHT)Then
                 oldcheck "moveright"
ElseIf fui_OVERGADGET(GUI_RIGHTWIN_UP) Then 
                 oldcheck "moveup"
ElseIf fui_OVERGADGET(GUI_RIGHTWIN_DOWN) Then 
                 oldcheck "movedown"
ElseIf fui_OVERGADGET(GUI_RIGHTWIN_FORWARD) Then 
                 oldcheck "movein"
ElseIf fui_OVERGADGET(GUI_RIGHTWIN_BACK) Then 
                 oldcheck "moveout"
ElseIf fui_OVERGADGET(GUI_RIGHTWIN_PITCHNEG) Then 
                 oldcheck "turnleft"
ElseIf fui_OVERGADGET(GUI_RIGHTWIN_PITCHPOS) Then 
                 oldcheck "turnright"
ElseIf fui_OVERGADGET(GUI_RIGHTWIN_YAWNEG) Then 
                 oldcheck "turnup"
ElseIf fui_OVERGADGET(GUI_RIGHTWIN_YAWPOS) Then 
                 oldcheck "turndown"
ElseIf fui_OVERGADGET(GUI_RIGHTWIN_ROLLNEG) Then 
                 oldcheck "turnin"
ElseIf fui_OVERGADGET(GUI_RIGHTWIN_ROLLPOS) Then 
                 oldcheck "turnout"
ElseIf fui_OVERGADGET(GUI_RIGHTWIN_SCALENEG) Then 
                 oldcheck "lscale-"
ElseIf fui_OVERGADGET(GUI_RIGHTWIN_SCALEPOS) Then 
                 oldcheck "lscale+"
EndIf
EndIf
;;;;;;;

For e.Event = Each event
  Select e\EventId
Case GUI_MENUFILE_Exit
   yn=Fui_confirm("Quit program","Yes","No")
   If yn=1 Then 
   ChangeDir thispath$
   ClearWorld(True,True)
   EndGraphics()
   End 
   EndIf
;-------
Case GUI_HELP_ABOUT
aboutwindow()
Case GUI_MENUHELP_CONTROL
CONTROLSWINDOW()
Case GUI_MENUFILE_NEW
 				SELECTEDTREESIZE = NewTreeWindow( )
				If SELECTEDTREESIZE > 0 Then
			    ChangeDir THISPATH$
                choosenewmap selectedtreesize 
                EndIf  
Case GUI_MENUFILE_OPEN
    map$=""
    ChangeDir thispath$
	tFile = FUI_OpenDialog( "Load RC Tree", branchpath$,"RC Tree (*.fte)|*.fte")
	If tFile = True Then
    map$=app\currentfile
    If FileType(map$)=1 Then      
		loadwork(map$)
     EndIf
     EndIf
     ChangeDir thispath$
Case GUI_MENUFILE_SAVE
    map$=""
    ChangeDir thispath$
	tFile = FUI_SaveDialog( "Save RC Tree", branchpath$,"RC Tree (*.fte)|*.fte")
	If tFile = True Then
    map$=app\currentfile
    If Instr( Lower$(map$), ".fte", 1 ) > 1 Then
    ChangeDir thispath$
	savework(map$)
    Else
    map$=map$+".fte"
    ChangeDir thispath$
	savework(map$)
    EndIf
    EndIf
    ChangeDir thispath$

;- Export Tree
Case GUI_MENUFILE_EXPORT
	Exportmap$=""
	ChangeDir thispath$
	tFile = FUI_SaveDialog( "Export tree", "Data\Meshes\RCtrees\", "RC Model (*.b3d)|*.b3d|RC Model Encrypted (*.eb3d)|*.eb3d", 2)
	If tFile = True Then
		exportmap$=app\currentfile
		If exportmap$<>""
			DebugLog( "Our Path: " + exportmap)
			
			If Upper(Right(exportmap$,3)) <> "B3D"
				Select app\currentindex
					Case 1
						;B3D
						exportmap$ = exportmap$ + ".B3D"
	
					Case 2
						;EB3D
						exportmap$ = exportmap$ + ".EB3D"
						
				End Select
			EndIf
			export(Exportmap$)
			Select app\currentindex
				Case 2
					encrypt_b3d(exportmap$)
					
				Case 1

			End Select
			newn$=strippath$(exportmap$)
			thisid=addmeshtodatabase ("RCTrees\"+newn$,False)
			DebugLog("Added " + Chr(32)+ "RCTrees\"+newn$ + Chr(32)+" to mesh database")
			
;			If Upper(Right(exportmap$,5)) = ".EB3D"
;				export(Exportmap$)
;				encrypt_b3d(exportmap$)
;				newn$=strippath$(exportmap$)
;				thisid=addmeshtodatabase ("RCTrees\"+newn$,False)
;			Else
;				export(Exportmap$)
;				newn$=strippath$(exportmap$)
;				thisid=addmeshtodatabase ("RCTrees\"+newn$,False)
;			EndIf

		EndIf
	EndIf
	ChangeDir thispath$
			
 Case GUI_RIGHTWIN_LEAFSHAPE
    map$=""
    ChangeDir thispath$
	tFile = FUI_OpenDialog( "Load Shape", shapepath$,"Shape model (*.3ds)|*.3ds|Shape model (*.b3d)|*.b3d|Shape model (*.x)|*.x")
	If tFile = True Then
    map$=app\currentfile
    If FileType(map$)=1 Then      
                changeshape(MAP$)
                oldcheck "changeshape"
                mouseclicked=0
     EndIf
     EndIf
     ChangeDir thispath$
 Case GUI_RIGHTWIN_LEAFTEXTURE
    map$=""
    ChangeDir thispath$
	tFile = FUI_OpenDialog( "Load Texture", branchpath$,"Png (*.Png)|*.Png|Bmp (*.bmp)|*.bmp|Jpg (*.jpg)|*.jpg")
	If tFile = True Then
  	  map$=app\currentfile
	    If FileType(map$)=1 Then      
		    sfilename3$=strippath$(map$)
		    ChangeDir branchpath$
		    If FileType(Sfilename3$)<>1 Then CopyFile map$,branchpath$+sfilename3$
            ChangeDir branchpath$
		    load_new_tex 0,sfilename3$
		    tImage = loadimage_Safe(sfilename3$)
            ResizeImage timage,100,100  
		    ChangeDir thispath$
		    FUI_DeleteGadget(GUI_RIGHTWIN_LeafCanvas)
		    GUI_RIGHTWIN_LeafCanvas  = FUI_ImageBox( GUI_RIGHTWIN, 5, 270, 100, 100,Timage)
		    SetBuffer BackBuffer()
		    FreeImage timage 
		    ChangeDir thispath$                  
	    EndIf
    EndIf
    ChangeDir thispath$

 Case GUI_RIGHTWIN_BarkTexture
    map$=""
    ChangeDir thispath$
	tFile = FUI_OpenDialog( "Load Texture", branchpath$,"Png (*.Png)|*.Png|Bmp (*.bmp)|*.bmp|Jpg (*.jpg)|*.jpg")
	If tFile = True Then
  	  map$=app\currentfile
	    If FileType(map$)=1 Then      
		    sfilename3$=strippath$(map$)
		    ChangeDir branchpath$
		    If FileType(Sfilename3$)<>1 Then CopyFile map$,branchpath$+sfilename3$
            ChangeDir branchpath$
		    load_new_tex 1,sfilename3$
		    tImage = loadimage_Safe(sfilename3$)
		    ChangeDir thispath$
            ResizeImage timage,100,100  
		    FUI_DeleteGadget(GUI_RIGHTWIN_BARKCanvas)
            GUI_RIGHTWIN_BARKCanvas = FUI_ImageBox( GUI_RIGHTWIN, 5, 80, 100, 100,Timage)
		    SetBuffer BackBuffer()
		    FreeImage timage 
		    ChangeDir thispath$                  
	    EndIf
    EndIf
    ChangeDir thispath$

Case GUI_RIGHTWIN_PaintLeaf 

 If actionmode=5000 Then 
    For l.leaf=Each leaf
        If Upper(Left$(l\shapefile$,5))<>"PREF_" Then  
           S= GetSurface(l\ent,1)
           b=CreateBrush()
           BrushTexture(b,leaftex)
           PaintMesh l\ent,b
           FreeBrush b
        EndIf
	Next
	Else
        moveleaf.leaf = selected.leaf
        If moveleaf <> Null Then  
           S= GetSurface(moveleaf\ent,1)
           b=CreateBrush()
           BrushTexture(b,leaftex)
           PaintMesh moveleaf\ent,b
           FreeBrush b
        EndIf
   EndIf

Case GUI_RIGHTWIN_COPYLEAF
                 checked.window=Null 
                 oldcheck "copyleaf"
Case GUI_RIGHTWIN_DELETELEAF
                  checked.window=Null 
                  oldcheck "deleteleaf"
Case GUI_RIGHTWIN_NEXTLEAF
                checked.window=Null 
                 oldcheck "nextleaf+"
Case GUI_RIGHTWIN_PREVLEAF
                checked.window=Null 
                 oldcheck "nextleaf-"
  End Select
  Delete E
Next

End Function 



Function strippath$(strings$)
	;strip path of the texfile
	  For i2= Len(strings$) To 1 Step -1
      If Mid$(strings$,i2,1)="\" Then
       strings$=Right$(strings$,Len(strings$)-i2)
       Exit
       EndIf
       Next
       Return strings$
       End Function

Function  Encrypt_B3D$(fname$,newname=False,del_old=False)
;Return
If FileType(fname$)<>1 Then Return -1

 TS=FileSize(fname$) 
 Thisbank=CreateBank(ts)  
 ;load the file to bank
 Fload=ReadFile(fname$)
 offset=0

 While Not Eof(fload)
 b=ReadByte(fload)
 PokeByte (thisbank,offset,b)
 offset=offset+1
 Wend
 CloseFile fload

 BF_encrypt(Thisbank,ts)
 ; this is not used in rcte as the name passed into this function is already .eb3d 
 If newname=False Then
	 newn$=fname$
 Else
	 newn$=""
	 For i=1 To Len(fname$)
		 newn$=newn$+Mid$(fname$,i,1)
			 If i<Len(fname$)-4 Then 
			   If Upper$(Mid$(fname$,i,4))=".B3D" Then 
			   newn$=newn$+"e"
             EndIf 
		 EndIf
	 Next 
 EndIf

;wite to file
; Newf=WriteFile(newn$)
DeleteFile (fname$)
 Fsave=WriteFile(fname$)
 b=PeekByte(thisbank,i)
 WriteBytes thisbank,fsave,0,offset
 CloseFile fsave

; WriteBytes thisbank,newf,0,ts
; CloseFile newf
 FreeBank thisbank
 ;if the del flag is true then remove the old. In rcte this is false 
 ;because we create a new file over the old of the same name. no need to delete
; If del_old=True Then 
 Return newn$

End Function

