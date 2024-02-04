;Tools
Global AH_Loca$ = "..\Realm Crafter 1\"
Global AH_Appb$ = "RCSTD"
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

    If GetSystemMetrics(0)<800 Then RuntimeError "RC Architect requires a minimun of 800X600 desktop resolution"
    
	;#Region Add/Subtract/Other Value Variables
	Const GUI_VARIABLE_TOPBAR_HEIGHT = 41
	Const GUI_VARIABLE_RIGHTBAR_WIDTH = 140
	Const GUI_VARIABLE_LEFTBAR_WIDTH = 140
	Const GUI_VARIABLE_BOTTOMBAR_HEIGHT = 50
	Const GUI_VARIABLE_GADGET_X = 60
	Const GUI_VARIABLE_GADGET_W = 65
	Const GUI_VARIABLE_GADGET_W2 = 80
    ;#End Region

    ;initialize fui
    Include "..\modules\safeloads.bb"
    Include "..\Modules\f-ui.bb"
	AppTitle( GFX_WINDOW_CAPTION$, "Really quit?" )
    SetBuffer BackBuffer()

    If GetSystemMetrics(0)=>1024 Then 
	 	FUI_Initialise(1024, 768, 0, 2, False, True, "Realm Crafter Architect")
    	w=1024:h=768                                                                               ;  
    Else 
	 	FUI_Initialise(1024, 768, 0, 2, False, True, "Realm Crafter Architect")
    	w=800:h=600                                                                               ;  
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

    Global CAMRANGE#=1000.0
    Global MAXCAMRANGE#=5000.0
    Global Cam=CreateCamera()  

    ClearTextureFilters()
	TextureFilter "_M",1+4
	TextureFilter "_A",1+2
    CameraRange Cam,1,20000
    CameraClsColor Cam,40,40,40
    CameraClsMode Cam,True,True
	Include "..\modules\architect_gui_shell_FUI.bb"
    ChangeDir thispath$

Global NEWLIGHTRANGE
Global	new_ambientred=0
Global	new_ambientGREEN=0
Global	new_ambientBLUE=0

Global	new_lightred=255
Global 	new_lightgreen=255
Global 	new_lightblue=255

If FileType("Data\architect\ar_loading.jpg")=1 Then 
templogo=loadimage_Safe("Data\architect\ar_loading.jpg")
;ResizeImage templogo,GraphicsWidth()/1.5,GraphicsHeight()/1.5
DrawBlock templogo,0,0
Flip
EndIf

AppTitle "RC Architect","Are you sure ? "
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

Global variable_camspeed=1
Global rescale_amount#=.05
; Media 

Global datadir$="DATA\ARCHITECT\"
Global meshdir$="DATA\MESHES\"
Global texdir$="DATA\TEXTURES\"
Global SAVEPATH$="Data\ARCHITECT\SAVES\"
Include "..\modules\inc_csg.bb"

If FileType("Data\Meshes\Architect")<>2 Then CreateDir("Data\Meshes\Architect")
If FileType("Data\Architect")<>2 Then CreateDir("Data\Architect")
If FileType(SAVEPATH$)<>2 Then CreateDir(SAVEPATH$)





Include "..\Modules\media.bb"
Include "..\modules\B3dFILE.bb"
Include "..\modules\Scene_Export.bb"
Include "..\modules\Fix_Sandwichtris.bb"
Include "..\modules\VertexLight.bb"
Include "..\Modules\MediaDialogs.bb"
Include "..\modules\wiremesh.bb"
Global old_Ambientred,old_ambientgreen,old_ambientblue
Global old_Lightred,old_Lightgreen,old_Lightblue
Global Old_LightRange
Global lightball=CreateSphere()
EntityAlpha lightball,.5

Global ObjectCube=CreateCube()
EntityColor ObjectCube,255,0,0
EntityAlpha ObjectCube,0

Global Set_alpha=100
Global areaRect = CreateWireMesh()
Global SnapSize=2
Global ScaleSize#=SnapSize*.1
Global GridTexture=loadtexture_safe("Data\architect\Gui_GRID.BMP",1+4+8)
Global GridEn=CreatePlane()
EntityFX griden,1+4+8

ScaleTexture GridTexture, snapsize*8, snapsize*8
EntityTexture griden,gridtexture
EntityPickMode griden,2


Global XScaleFinal# = 1.0
Global YScaleFinal# = 1.0
Global ZScaleFinal# = 1.0

Global MoveSnapLocked=1
Global Selected_Model
Global Selected_MeshID

Type DropModel
 Field Ent
 Field MeshID
 Field X#,Y#,Z#
 Field Pitch#,Yaw#,Roll#
 Field Sx#,Sy#,Sz#
 ;for lightsource
 Field islight
 Field cr,cg,cb
 Field range
End Type

Type CopiedDropModel
 Field Ent
 Field MeshID
 Field X#,Y#,Z#
 Field Pitch#,Yaw#,Roll#
 Field Sx#,Sy#,Sz#
 ;for lightsource
 Field islight
 Field cr,cg,cb
 Field range
End Type

Global ActionType
 Const A_Move=1
 Const A_Scale=2
 Const A_Rotate=3
 ActionType=A_Move

    CameraClsMode cam,True,True
    AmbientLight 255,255,255
    PositionEntity cam,0,120,0
    RotateEntity cam,0,0,0
	TurnEntity cam, 90, 0, 0


;main loop
movetimer#=MilliSecs()
Global prevcam=CreateCamera()
CameraClsColor prevcam,180,180,180
CameraViewport prevcam,GraphicsWidth()*.02,GraphicsHeight()*.02,GraphicsWidth()*.2,GraphicsHeight()*.2
PositionEntity prevcam,-10000,-999999,-999999
HideEntity prevcam
Global previtem=CreateCube()
Global prevtimer#,prevshown
PositionEntity  previtem,-10000,-999999,-999900
FreeImage templogo

ChangeDir thispath$
;RuntimeError CurrentDir$()
;Dim MeshNames$(65534)
;Type MESHFOLDER
; Field MeshName$
; Field meshid
; Field FOLDERNAME$
; End Type
;Global mf.meshfolder
;Type meshfolder_check
; Field MeshName$
; Field FOLDERNAME$
; End Type
;Global mfc.meshfolder_check
;Global ModelFolder$=""

initmediadialogs()



					TIMAGE = CreateImage( 50, 20 )
					SetBuffer ImageBuffer(timage)
					Color new_lightred,new_lightgreen,new_lightblue
					Rect 0, 0, 50, 20,1
			        FUI_DeleteGadget(GUI_RIGHTWIN_LGTCanvas)
			        GUI_RIGHTWIN_LGTCanvas = FUI_ImageBox( GUI_RIGHTWIN, 5, 70, 50, 20,TIMAGE)
					SetBuffer BackBuffer()
			        FreeImage timage 
			
Repeat 
   
   

If KeyHit(209) Then 
    If EntityY(griden)=0 Then 
	TranslateEntity griden,0,-snapsize,0
	TranslateEntity griden,0,-snapsize,0
	TranslateEntity griden,0,-snapsize,0
	TranslateEntity griden,0,-snapsize,0
    Else
	TranslateEntity griden,0,-snapsize,0
    EndIf
	EndIf

If KeyHit(201) Then 
    If EntityY(griden)=(-snapsize*4) Then 
	TranslateEntity griden,0,snapsize,0
	TranslateEntity griden,0,snapsize,0
	TranslateEntity griden,0,snapsize,0
	TranslateEntity griden,0,snapsize,0
    Else
	TranslateEntity griden,0,snapsize,0
    EndIf
	EndIf

If KeyHit(211) Then deletemodel()
If KeyHit(59) Then 
          showmode 1
		  ;gui_setstatus(ActionMove.window,1)
 		  ;gui_setstatus(ActionScale.window,0)
		  ;gui_setstatus(ActionRotate.window,0)
          ;ActionType=A_Move
		  EndIf

If KeyHit(60) Then 
          showmode 2
		  ;gui_setstatus(ActionMove.window,0)
 		  ;gui_setstatus(ActionScale.window,1)
		  ;gui_setstatus(ActionRotate.window,0)
          ;ActionType=A_Scale
		 EndIf

If KeyHit(61) Then 
          showmode 3
		  ;gui_setstatus(ActionMove.window,0)
 		  ;gui_setstatus(ActionScale.window,0)
		  ;gui_setstatus(ActionRotate.window,1)
          ;ActionType=A_Rotate
		 EndIf
 
If KeyDown(57) Then mlook=1 Else mlook=-1


If movetimer#<MilliSecs() Then
	If ActionType=A_Move Then

				If KeyDown(203) Then  movemodel(-snapsize,0,0):movetimer#=MilliSecs()+100
				If KeyDown(205) Then  movemodel(snapsize,0,0):movetimer#=MilliSecs()+100
				;Up Down
				If KeyDown(200) Then movemodel(0,0,snapsize):movetimer#=MilliSecs()+100
				If KeyDown(208) Then movemodel(0,0,-snapsize):movetimer#=MilliSecs()+100
				;AZ
				If KeyDown(30) Then movemodel(0,snapsize,0):movetimer#=MilliSecs()+100
				If KeyDown(44) Then movemodel(0,-snapsize,0):movetimer#=MilliSecs()+100

		ElseIf ActionType=A_Scale

                If KeyHit(28) Then SCALEMODEL(0,0,0,True)
				If KeyDown(205) Then  scalemodel(ScaleSize#,0,0):movetimer#=MilliSecs()+100
				If KeyDown(203) Then  scalemodel(-ScaleSize#,0,0):movetimer#=MilliSecs()+100
				;Up Down
				If KeyDown(200) Then scalemodel(0,0,ScaleSize#):movetimer#=MilliSecs()+100
				If KeyDown(208) Then scalemodel(0,0,-ScaleSize#):movetimer#=MilliSecs()+100
				;AZ
 
				If KeyDown(30) Then scalemodel(0,ScaleSize#,0):movetimer#=MilliSecs()+100
				If KeyDown(44) Then scalemodel(0,-ScaleSize#,0):movetimer#=MilliSecs()+100
		
		ElseIf ActionType=A_Rotate
            If KeyHit(28) Then rotatemodel(0,0,0,True)
				If KeyDown(200) Then  rotatemodel(10,0,0):movetimer#=MilliSecs()+100
				If KeyDown(208) Then  rotatemodel(-10,0,0):movetimer#=MilliSecs()+100
				;Up Down
				If KeyDown(30) Then rotatemodel(0,0,10):movetimer#=MilliSecs()+100
				If KeyDown(44) Then rotatemodel(0,0,-10):movetimer#=MilliSecs()+100
				;AZ
				If KeyDown(203) Then rotatemodel(0,10,0):movetimer#=MilliSecs()+100
				If KeyDown(205) Then rotatemodel(0,-10,0):movetimer#=MilliSecs()+100
            Else
				;camera Movement	
				;Left Right
				If KeyDown(203) Then TranslateEntity cam, -snapsize, 0, 0
				If KeyDown(205) Then TranslateEntity cam, snapsize, 0, 0
				;Up Down
				If KeyDown(200) Then TranslateEntity cam, 0, 0, snapsize
				If KeyDown(208) Then TranslateEntity cam, 0, 0, -snapsize
				;AZ
				If KeyDown(44) Then TranslateEntity cam, 0,snapsize, 0
				If KeyDown(30) Then TranslateEntity cam, 0, -snapsize, 0

				EndIf

If mlook=1 Then 
 If MouseDown(1) Or KeyDown(16) Then MoveEntity cam,0,0,variable_camspeed
 If MouseDown(2) Or KeyDown(17) Then MoveEntity cam,0,0,-variable_camspeed
 MOUSELOOK(cam)
EndIf
EndIf


If MouseHit(2) = 1 Then
	If mlook=1 Then 
	MoveEntity CAM,0,0,-variable_camspeed
	 Else
	tEn2 = ThisCameraPick()
	If DROPTIMER#<MilliSecs() Then 
	DROPTIMER#=MilliSecs()+100

If FUI_SendMessage(GUI_RIGHTWIN_DROPLIGHT,M_GETCHECKED)=1 Then 
Rng=NEWLightRange
llR= NEW_LIGHTRED
llG= NEW_LIGHTGREEN
llB= NEW_LIGHTBLUE
		dropmodel(1,llr,llg,llb,rng)
     Else

    dropmodel()
 EndIf
	EndIf	
EndIf
			tEn = tEn2
			EndIf

			
;WireFrame True

			CameraProjMode( App\Cam, False )
			CameraProjMode( Cam, True )
 	        UnlockBuffer(BackBuffer()) 
    	    LockBuffer(FrontBuffer()) 
        	UpdateWorld()
 	        LockBuffer(BackBuffer()) 
    	    UnlockBuffer(FrontBuffer()) 
            Updatedropmodels()
            buttonchecks()
			RenderWorld( )
			CameraProjMode( Cam, False )
			CameraProjMode( App\Cam, True )
  		    FUI_Update( )
                If sysmsg$<>"" And sysmsgtimer#>MilliSecs() Then
 				   Color( 15, 15, 15 )
    	           Text GraphicsWidth()/4,GraphicsHeight()/2,sysmsg$          
			  	   Color( 255, 255, 255 )
                   Text 75,70,sysmsg$          
                   EndIf

			Flip(0)

Forever

Function Thiscamerapick()
 Return CameraPick(cam,MouseX(),MouseY())
End Function


Function Updatedropmodels()
EntityAlpha objectcube,0
EntityAlpha lightball,0
For dm.dropmodel=Each dropmodel
		 If dm\ent = selected_model Then 
;For grp.grouping=Each grouping
; BOX_SELECT(grp\ENT,grp\SX,grp\SY,grp\SZ,grp\islight)
; Next
; BOX_SELECT(dm\ENT,dm\SX,dm\SY,dm\SZ,dm\islight)
		  If dm\islight=True Then 
		   PositionEntity lightball,EntityX(dm\ent,1),EntityY(dm\ent,1),EntityZ(dm\ent,1)
		   ScaleEntity lightball,dm\range,dm\range,dm\range
		   EntityAlpha lightball,.55
		   EntityColor lightball,dm\cr,dm\cg,dm\cb
		   EntityFX lightball,1+16
            EntityAlpha objectcube,0
           Else
             EntityAlpha lightball,0 
	   			FitEntity(ObjectCube,dm\ENT)
    	        EntityAlpha objectcube,.45         

		   EndIf
		  Else 
	 EndIf 
Next
End Function



Function DropModel(islight=0,lr=0,lg=0,lb=0,range=0)
EntityPickMode griden,2
DebugLog "dropping "+Chr$(Rand(65,128))
cp=thiscamerapick()
If  KeyDown(29) Or KeyDown(157) Then
	dropem=1
	Else
	dropem=0
	EndIf

If cp<>0 Then 
 For dd.dropmodel=Each dropmodel
	 If dd\ent=cp Then 
		If dropem=0 Then 
		   selected_model=dd\ent
		   If islight=0 Then Return
		   EndIf
	    EndIf
  Next
  EndIf

If dropem=1 Then 
  d.copieddropmodel=First copieddropmodel
  If d<>Null And islight<>1 Then 
   If d\islight=0 Then 
      DebugLog "d= "+d\meshid
  	  scc.dropmodel=New dropmodel
      SCC\ENT= getmesh(d\meshid,True)
      thisscale#=LoadedMeshScales#(d\meshid)*rescale_amount#
      FitMesh scc\ent,-MeshWidth(d\ent)*.5,MeshHeight(d\ent),-MeshDepth(d\ent)*.5,MeshWidth(d\ent),-MeshHeight(d\ent),MeshDepth(d\ent)
;     ScaleMesh scc\ent,thisscale#,thisscale#,thisscale# 
 	  SCC\MESHID=d\meshid
 	  scc\sx#=d\sx#
	  scc\sy#=d\sy#
 	  scc\sz#=d\sz#
      scc\pitch=d\pitch
      scc\yaw=d\yaw
      scc\roll=d\roll
      If d\ent Then 
      scc\pitch=EntityPitch(d\ent,1)
      scc\yaw=EntityYaw(d\ent,1)
      scc\roll=EntityRoll(d\ent,1)
      EndIf
 	  ScaleEntity scc\ent,scc\sx,scc\sy,scc\sz
 	  RotateEntity scc\ent,scc\sx,scc\sy,scc\sz
      EndIf
 Else
     DebugLog "null "+SELECTED_MESHID
   	  scc.dropmodel=New dropmodel
  	  SCC\MESHID=selected_meshid
      If islight=0 Then 
	      SCC\ENT= GetMesh(selected_meshid,True)
          thisscale#=LoadedMeshScales#(selected_meshid)*rescale_amount#
          ScaleMesh scc\ent,thisscale#,thisscale#,thisscale# 
          DebugLog "getmodel id is "+selected_meshid
          DebugLog "getmodel handle is "+scc\ent 
;    	  EntityFX SCC\ENT,1+2
          FitEntity(ObjectCube,SCC\ENT)
          EntityAlpha objectcube,.45 
       Else
			scc\ent=CreateCube()       
            EntityPickMode scc\ent,2
            ScaleMesh scc\ent,3,3,3
            scc\islight=1
            EntityFX SCC\ENT,1
    		FitEntity(ObjectCube,SCC\ENT)
           EntityAlpha objectcube,.45         
           EntityColor scc\ent,lr,lg,lb
            scc\range=range
            DebugLog "Light dropped with range "+range
            scc\cr=lr
            scc\cg=lg
            scc\cb=lb
          EndIf
 	  scc\sx#=1
	  scc\sy#=1
 	  scc\sz#=1
      scc\pitch=0
      scc\yaw=0
      scc\roll=0
  EndIf 
  selected_model=scc\ent
 
;

If islight=1 Then 
	 placemodel(PickedX(),PickedY(),PickedZ())
	 ScaleEntity scc\ent,1,1,1
     EntityAlpha lightball,.55 
	 Else
	 placemodel(PickedX(),EntityY(griden),PickedZ())
     EntityAlpha lightball,0 
     EntityPickMode scc\ent,2
	 RotateEntity scc\ent,scc\pitch,scc\yaw,scc\roll
	 ScaleEntity scc\ent,scc\sx,scc\sy,scc\sy
	 EndIf
     Return scc\ent
EndIf  
EntityPickMode griden,0
End Function      



Function DeleteModel()
 For d.Dropmodel=Each Dropmodel
     If d\ent=SELECTED_model Then 
        FreeEntity d\ent
         totaldrops=totaldrops-1 
         Delete d
dm.Dropmodel=First Dropmodel
If dm<>Null Then selected_model=dm\ent
          Return
          EndIf
          Next
End Function



Function Copymodel()
For cme.copieddropmodel=Each copieddropmodel
 Delete cme
 Next
 For d.dropmodel=Each dropmodel
     If d\ent=SELECTED_model Then 
   	  Scc.copieddropmodel=New copieddropmodel
      SCC\ENT=d\ent
  	  SCC\MESHID=d\meshid
	  scc\sx#=d\sx#
	  scc\sy#=d\sy#
 	  scc\sz#=d\sz#
      scc\islight=d\islight
   If D\ENT Then 
      DebugLog "rotations copied" 
      scc\pitch=EntityPitch(d\ent,1)
      scc\yaw=EntityYaw(d\ent,1)
      scc\roll=EntityRoll(d\ent,1)
     EndIf
     EndIf
     Next 

End Function

Function Box_Select(ENT,SX#=1,SY#=1,SZ#=1,FLAG=1,CR=255,CG=0,CB=0)
If MeshWidth(ent)*sx>MeshDepth(ent)*sz Then 
  If MeshWidth(ent)*sx>MeshHeight(ent)*sy Then 
    ssize#=MeshWidth(Ent)*sx
     Else
	   ssize#=MeshHeight(Ent)*sy
       EndIf
        Else
        If MeshDepth(ent)*sx>MeshHeight(ent)*sy Then 
 	       ssize#=MeshDepth(Ent)*sz
            Else          
    	       ssize#=MeshHeight(Ent)*sy
               EndIf
               EndIf

Select FLAG
 Case 0
ssize=ssize*.5
;MeshDepth(ent))*SZ#)
Wx1#=EntityX(ent,1)-ssize
wy1#=EntityY(ent,1)
wz1#=EntityZ(ent,1)-ssize

wx2#=EntityX(ent,1)+ssize
wy2#=EntityY(ent,1)+(ssize*1.5)
wz2#=EntityZ(ent,1)+ssize
 Case 1
Wx1#=EntityX(ent,1)-((MeshWidth(ent))*SX#)
wy1#=EntityY(ent,1)-((MeshHeight(ent))*SY#)
wz1#=EntityZ(ent,1)-((MeshDepth(ent))*SZ#)

wx2#=EntityX(ent,1)+((MeshWidth(ent))*SX#)
wy2#=EntityY(ent,1)+((MeshHeight(ent))*SY#)
wz2#=EntityZ(ent,1)+((MeshDepth(ent))*SZ#)

End Select

	Color CR,CG,CB
	WireLine3D(areaRect,WX2,WY1,WZ1,WX2,WY2,WZ1)
	WireLine3D(areaRect,WX2,WY2,WZ1,WX1,WY2,WZ1)
	WireLine3D(areaRect,WX1,WY2,WZ1,WX1,WY1,WZ1)
	WireLine3D(areaRect,WX1,WY1,WZ1,WX2,WY1,WZ1)

	WireLine3D(areaRect,WX2,WY1,WZ2,WX2,WY2,WZ2)
	WireLine3D(areaRect,WX2,WY2,WZ2,WX1,WY2,WZ2)
	WireLine3D(areaRect,WX1,WY2,WZ2,WX1,WY1,WZ2)
	WireLine3D(areaRect,WX1,WY1,WZ2,WX2,WY1,WZ2)

	WireLine3D(areaRect,WX2,WY1,WZ2,WX2,WY1,WZ1)
	WireLine3D(areaRect,WX1,WY2,WZ2,WX1,WY2,WZ1)
	WireLine3D(areaRect,WX2,WY2,WZ2,WX2,WY2,WZ1)
	WireLine3D(areaRect,WX1,WY1,WZ2,WX1,WY1,WZ1)

End Function


Function scalemodel(smx#,smy#,smz#,reset=False)
If  MoveSnapLocked<>1 Then 
 smx#=Sgn(smx)*.0001
 smy#=Sgn(smy)*.0001
 smz#=Sgn(smz)*.0001
EndIf

 For dm.dropmodel=Each dropmodel
   If selected_model=dm\ent Then 
If reset=False Then 
	If dm\islight=0 Then 
		If dm\sx#+smx#>0 Then  dm\sx#=dm\sx#+smx#
		If dm\sy#+smy#>0 Then  dm\sy#=dm\sy#+smy#
		If dm\sz#+smz#>0 Then  dm\sz#=dm\sz#+smz#

		If dm\sx#+smx#<0 Then dm\sx#=.1
		If dm\sy#+smy#<0 Then  dm\sy#=.1
		If dm\sz#+smz#<0 Then  dm\sz#=.1
        ScaleEntity dm\ent,dm\sx,dm\sy,dm\sz
     Else
        If smy#>0 Or smx#>0 Or smz>0 Then dm\range=dm\range+2
        If smy#<0 Or smx<0 Or smz<0 Then dm\range=dm\range-2
        If dm\range<1 Then dm\range=1
     EndIf

			XScalefinal# = dm\sx
			YScalefinal# = dm\sy
			ZScalefinal# = dm\sz
Else
            ScaleEntity dm\ent,1,1,1
			XScalefinal# = dm\sx
			YScalefinal# = dm\sy
			ZScalefinal# = dm\sz
dm\sx=1
dm\sy=1
dm\sz=1

EndIf
 fitentity objectcube,dm\ent

EndIf

Next 
End Function


Function movemodel(mx#,my#,mz#)
If  MoveSnapLocked<>1 Then 
 mx#=(Sgn(mx#)*.1)*Variable_Camspeed
 my#=(Sgn(my#)*.1)*Variable_Camspeed
 mz#=(Sgn(mz#)*.1)*Variable_Camspeed
EndIf

 For dm.dropmodel=Each dropmodel
   If selected_model=dm\ent Then 
     TranslateEntity dm\ent,mx,my,mz
     EndIf
  Next
End Function

Function reform_model(refs)
 For dm.dropmodel=Each dropmodel
   If selected_model=dm\ent Then 
      mw#=nearestpower2(MeshWidth(dm\ent),refs)
      mh#=nearestpower2(MeshHeight(dm\ent),refs)
      md#=nearestpower2(MeshDepth(dm\ent),refs)
      FitMesh  dm\ent,-mw*.5,mh,-md#*.5,mw,-mh,md
     EndIf
  Next
End Function


Function rotatemodel(smx#,smy#,smz#,reset=False)

DebugLog "model rotated "+smx#+" "+smy#+" "+smz
 For dm.dropmodel=Each dropmodel
   If dm\ent=selected_model Then 
If reset=False Then 
    TurnEntity dm\ent,smx,smy,smz
    dm\pitch#=EntityPitch(dm\ent)
    dm\yaw#=EntityYaw(dm\ent)
    dm\roll#=EntityRoll(dm\ent)
    RotateEntity dm\ent,dm\pitch,dm\yaw,dm\roll
Else
    RotateEntity dm\ent,0,0,0
    dm\pitch#=EntityPitch(dm\ent)
    dm\yaw#=EntityYaw(dm\ent)
    dm\roll#=EntityRoll(dm\ent)
EndIf
 fitentity Objectcube,dm\ent

EndIf

  Next

End Function




Function placemodel(px#,py#,pz#,islight=0)
 For dm.dropmodel=Each dropmodel
   If selected_model=dm\ent Then 
		If  MoveSnapLocked=1 Then 
	 	  px2#=nearestpower2(px#,MeshWidth(dm\ent));snapsize)
		  py2#=py#;nearestpower2(py#,snapsize)
		  pz2#=nearestpower2(pz#,MeshDepth(dm\ent));snapsize)
          PositionEntity dm\ent,px2,py2,pz2
          Return
          EndIf
        

     cp=thiscamerapick()
 	 PositionEntity dm\ent,PickedX(),PickedY(),PickedZ()

    
     EndIf
   Next
End Function

Function NearestPower2(N#,snapper#=2)
wasneg=0
If Sgn(n#)=-1 Then 
   n#=n#*-1
   Wasneg=1
   EndIf
n2#=Int(n#/snapper)
newn#=n2#*snapper
If wasneg=1 Then 
		Return -newn#
 		Else
		Return newn#
		EndIf
End Function


;Control
;camera position, angle values
Global cam_x#,cam_z#,cam_pitch#,cam_yaw#,cam_speed#=.5		; Current
Global dest_cam_x#,dest_cam_z#,dest_cam_pitch#,dest_cam_yaw#	; Destination
Global ent_x#,ent_z#,ent_pitch#,ent_yaw#,ent_speed#=.5		; Current
Global dest_ent_x#,dest_ent_z#,dest_ent_pitch#,dest_ent_yaw#	; Destination

Function mouselook(Lcamera)
	; Mouse look
	; ----------

	; Mouse x and y speed
	mxs=MouseXSpeed()
	mys=MouseYSpeed()
	
	; Mouse shake (total mouse movement)
	mouse_shake=Abs(((mxs+mys)/2)/1000.0)

	; Destination camera angle x and y values
	dest_cam_yaw#=dest_cam_yaw#-mxs
	dest_cam_pitch#=dest_cam_pitch#+mys

	; Current camera angle x and y values
	cam_yaw=cam_yaw+((dest_cam_yaw-cam_yaw)/5)
	cam_pitch=cam_pitch+((dest_cam_pitch-cam_pitch)/5)
	
	RotateEntity Lcamera,cam_pitch#,cam_yaw#,0
	;RotateEntity camera,mxs,mys,0
		
	; Rest mouse position to centre of screen
	MoveMouse 400,300

		; Move camera using movement values
	MoveEntity Lcamera,x#,y#,z#
		
End Function	


Function ButtonChecks() 
 If Int(FUI_SendMessage(GUI_RIGHTWIN_SNAPSIZE,M_GETVALUE))<>snapsize Then 
    SnapSize=Int(FUI_SendMessage(GUI_RIGHTWIN_SNAPSIZE,M_GETVALUE))
    ScaleSize#=SnapSize*.1
    ScaleTexture GridTexture, snapsize*8, snapsize*8
    EntityTexture griden,gridtexture
    EntityPickMode griden,2
  EndIf

 newLightrange=Int(FUI_SendMessage(GUI_RIGHTWIN_RANGE,M_gETVALUE))
 If Newlightrange<>Old_lightrange Then 
  old_lightrange=newlightrange 
  EndIf
  
If set_alpha<>Int(FUI_SendMessage(GUI_RIGHTWIN_CAMSPEED,M_GETVALUE)) Then 
   set_alpha=Int(FUI_SendMessage(GUI_RIGHTWIN_CAMSPEED,M_GETVALUE))
   Variable_Camspeed=Int(FUI_SendMessage(GUI_RIGHTWIN_CAMSPEED,M_GETVALUE))
   EndIf

If FUI_SendMessage(GUI_RIGHTWIN_ACTIONMOVE,M_GETCHECKED)=1 Then ActionType=A_Move
If FUI_SendMessage(GUI_RIGHTWIN_ACTIONSCALE,M_GETCHECKED)=1 Then ActionType=A_Scale
If FUI_SendMessage(GUI_RIGHTWIN_ACTIONROTATE,M_GETCHECKED)=1 Then ActionType=A_rotate
MoveSnapLocked = FUI_SendMessage(gui_rightwin_snaptogrid,M_GETCHECKED) 

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
;-------
Case GUI_HELP_CONTROLS
controlswindow()
;-------
Case GUI_RIGHTWIN_ACTIONX_NEG
    Select Actiontype
         Case A_Move
				movemodel(-snapsize,0,0):movetimer#=MilliSecs()+100
         Case A_Scale
                scalemodel(-ScaleSize#,0,0):movetimer#=MilliSecs()+100
         Case A_Rotate
                rotatemodel(-10,0,0):movetimer#=MilliSecs()+100
         End Select
Case GUI_RIGHTWIN_REFORMMODEL
    yn=fui_confirm("Reform selected","Yes","No")
    If yn=1 Then 
     reform_model(snapsize)
    EndIf
Case GUI_RIGHTWIN_SCALEXYZ_NEG
 scalemodel(-ScaleSize#,-ScaleSize#,-ScaleSize#):movetimer#=MilliSecs()+100
Case GUI_RIGHTWIN_SCALEXYZ_POS
 scalemodel(ScaleSize#,ScaleSize#,ScaleSize#):movetimer#=MilliSecs()+100
Case GUI_RIGHTWIN_COMBINEMODELS
    yn=fui_confirm("Combine all?","Yes","No")
   If yn=1 Then 
  totalmesh=CreateMesh()
  For dm.dropmodel=Each dropmodel
       If dm\islight=0 Then 
          DebugLog "added item to export"
           ScaleMesh dm\ent,dm\sx,dm\sy,dm\sz
           RotateMesh dm\ent,EntityPitch(dm\ent,True),EntityYaw(dm\ent,True),EntityRoll(dm\ent,True)
           PositionMesh dm\ent,EntityX(dm\ent,True),EntityY(dm\ent,True),EntityZ(dm\ent,True)
           AddMesh dm\ent,totalmesh
           HideEntity dm\ent
           EndIf
 	     Next
         scc.dropmodel=New dropmodel
         scc\ent=totalmesh
         scc\meshid=-1
         scc\x=EntityX(totalmesh,1)
         scc\y=EntityY(totalmesh,1)
         scc\z=EntityZ(totalmesh,1)
         scc\pitch=EntityPitch(totalmesh,1)
         scc\yaw=EntityYaw(totalmesh,1)
         scc\roll=EntityRoll(totalmesh,1)
         scc\sx=1.0
         scc\sy=1.0
         scc\sz=1.0
         scc\islight=0
         scc\range=10
         scc\cr=255
         scc\cg=255
         scc\cb=255
         EntityPickMode scc\ent,2
         EndIf

Case GUI_RIGHTWIN_RESETCAM
	PositionEntity cam, 0, 120, 0
    RotateEntity cam,0,0,0
	TurnEntity cam, 90, 0, 0

Case GUI_RIGHTWIN_GRIDDOWN
    If EntityY(griden)=0 Then 
	TranslateEntity griden,0,-snapsize,0
	TranslateEntity griden,0,-snapsize,0
	TranslateEntity griden,0,-snapsize,0
	TranslateEntity griden,0,-snapsize,0
    Else
	TranslateEntity griden,0,-snapsize,0
	EndIf

Case GUI_RIGHTWIN_GRIDUP
    If EntityY(griden)=(-snapsize*4) Then 
	TranslateEntity griden,0,snapsize,0
	TranslateEntity griden,0,snapsize,0
	TranslateEntity griden,0,snapsize,0
	TranslateEntity griden,0,snapsize,0
    Else
	TranslateEntity griden,0,snapsize,0
	EndIf

Case GUI_RIGHTWIN_LIGHTMAP
	Lightmapit()   
Case GUI_RIGHTWIN_ACTIONX_POS
    Select Actiontype
         Case A_Move
				movemodel(snapsize,0,0):movetimer#=MilliSecs()+100
         Case A_Scale
                scalemodel(ScaleSize#,0,0):movetimer#=MilliSecs()+100
         Case A_Rotate
                rotatemodel(10,0,0):movetimer#=MilliSecs()+100
         End Select
Case GUI_RIGHTWIN_ACTIONY_NEG
    Select Actiontype
         Case A_Move
				movemodel(0,-snapsize,0):movetimer#=MilliSecs()+100
         Case A_Scale
                scalemodel(0,-ScaleSize#,0):movetimer#=MilliSecs()+100
         Case A_Rotate
                rotatemodel(0,-10,0):movetimer#=MilliSecs()+100
         End Select
Case GUI_RIGHTWIN_ACTIONY_POS
    Select Actiontype
         Case A_Move
				movemodel(0,snapsize,0):movetimer#=MilliSecs()+100
         Case A_Scale
                scalemodel(0,ScaleSize#,0):movetimer#=MilliSecs()+100
         Case A_Rotate
                rotatemodel(0,10,0):movetimer#=MilliSecs()+100
         End Select
Case GUI_RIGHTWIN_ACTIONZ_NEG
    Select Actiontype
         Case A_Move
				movemodel(0,0,-snapsize):movetimer#=MilliSecs()+100
         Case A_Scale
                scalemodel(0,0,-ScaleSize#):movetimer#=MilliSecs()+100
         Case A_Rotate
                rotatemodel(0,0,-10):movetimer#=MilliSecs()+100
         End Select
Case GUI_RIGHTWIN_ACTIONZ_POS
    Select Actiontype
         Case A_Move
				movemodel(0,0,snapsize):movetimer#=MilliSecs()+100
         Case A_Scale
                scalemodel(0,0,ScaleSize#):movetimer#=MilliSecs()+100
         Case A_Rotate
                rotatemodel(0,0,10):movetimer#=MilliSecs()+100
         End Select

Case GUI_RIGHTWIN_UPDATELIGHT
       UPDATELIGHT()
Case GUI_MENUMODE_ACTIONS
     showmode 1 
Case GUI_MENUMODE_MODELS
     showmode 2 
Case GUI_MENUMODE_LIGHTS
     showmode 3 
Case GUI_RIGHTWIN_COPYMODEL
         Copymodel()
Case GUI_RIGHTWIN_DELETEMODEL
       deletemodel()
Case GUI_RIGHTWIN_SELECTMODEL
   ChangeDir THISPATH$
   CameraProjMode Cam, False
   CameraProjMode App\Cam, True
   Tmodel = ChooseMeshDialog(MeshDialog_All,"",75,125)
   DebugLog "tmodel is "+TMODEL
   ;tModel = ChooseMeshDialog( 0 )
    If tModel <> -1 Then
		For cme.copieddropmodel=Each copieddropmodel
		Delete cme
		Next
        Mlid=tmodel
  	    sceneryid=Mlid
        selected_meshid=mlid
        SCENERYNAME$=getmeshname$(mlid) 
    DebugLog "scenery name is "+sceneryname$
	EndIf


Case GUI_MENUFILE_EXPORT
    Exportmap$=""
    ChangeDir thispath$
	tFile = FUI_SaveDialog( "Export map To RC area", "Data\Meshes\Architect\", "RC Model (*.eb3d)|*.eb3d")
	If tFile = True Then
    exportmap$=app\currentfile
       If exportmap$<>""
 	   If Instr( exportmap$, ".eb3d", 1 ) > 1 Then
       DebugLog "exporting : "+exportmap$
	   exportwork (exportmap$)
	   Else
	   exportmap$ = exportmap$ + ".eb3d"
       DebugLog "exporting : "+exportmap$
	   exportwork (exportmap$)
	   EndIf
       EndIf
       EndIf
       ChangeDir thispath$
 
Case GUI_MENUFILE_NEW
       yn=Fui_Confirm("New Scene ?","Yes","No")
       If yn=1 Then newscene() 
;-------
Case GUI_MENUFILE_OPEN
    map$=""
    ChangeDir thispath$
	tFile = FUI_OpenDialog( "Load architect map", "Data\ARCHITECT\Saves\","ACT Map (*.act)|*.act")
	If tFile = True Then
    map$=app\currentfile
       If FileType (map$)=1 Then 
          loadwork map$
     EndIf
     EndIf
     ChangeDir thispath$

Case GUI_MENUFILE_SAVE
     savemap$=""
     ChangeDir thispath$
     tFile = FUI_SaveDialog( "Save architect map", "Data\ARCHITECT\Saves\", "ACT Map (*.act)|*.act")
 	 If tFile = True Then
     savemap$=APP\CURRENTFILE
       If savemap$<>"" Then 
	   If Instr( Lower$(savemap$), "act", 1 ) > 1 Then
                        DebugLog "saving : "+savemap$
						savework (savemap$)
						Else
						savemap$ = savemap$ + ".act"
						savework (savemap$)
						EndIf
                        EndIf
                        EndIf
 	                    ChangeDir thispath$

Case GUI_RIGHTWIN_AMBCOLOR
					tColor = FUI_ColorDialog( tRed, tGreen, tBlue )
					If tColor = True Then
						new_ambientred = FUI_GetRed( App\CurrentRGB )
						new_ambientGreen = FUI_GetGreen( App\CurrentRGB )
						new_ambientBLUE = FUI_GetBlue( App\CurrentRGB )

						TIMAGE = CreateImage( 50, 20 )
						SetBuffer ImageBuffer(timage)
						Color new_ambientred,new_ambientgreen,new_ambientblue
						Rect 0, 0, 50, 20,1
				        FUI_DeleteGadget(GUI_RIGHTWIN_AMBCanvas)
				        GUI_RIGHTWIN_AMBCanvas = FUI_ImageBox( GUI_RIGHTWIN, 5, 5, 50, 20,TIMAGE)
						SetBuffer BackBuffer()
				        FreeImage timage 
                      EndIf

Case GUI_RIGHTWIN_LGTCOLOR
					tColor = FUI_ColorDialog( tRed, tGreen, tBlue )
					If tColor = True Then
						new_lightred = FUI_GetRed( App\CurrentRGB )
						new_lightGreen = FUI_GetGreen( App\CurrentRGB )
						new_lightBLUE = FUI_GetBlue( App\CurrentRGB )

					TIMAGE = CreateImage( 50, 20 )
					SetBuffer ImageBuffer(timage)
					Color new_lightred,new_lightgreen,new_lightblue
					Rect 0, 0, 50, 20,1
			        FUI_DeleteGadget(GUI_RIGHTWIN_LGTCanvas)
			        GUI_RIGHTWIN_LGTCanvas = FUI_ImageBox( GUI_RIGHTWIN, 5, 70, 50, 20,TIMAGE)
					SetBuffer BackBuffer()
			        FreeImage timage 
					EndIf
;-----------------


  End Select
  Delete E
Next
End Function



Function savework(SF$="")
FN$=sf$
sfile=WriteFile (fn$)
;find out how many dropmodels there are
dmc=0

For dm.dropmodel=Each dropmodel
dmc=dmc+1
Next

WriteInt sfile,dmc

For dm.dropmodel=Each dropmodel
smm=smm+1
DebugLog "Saving drop model:: "+smm
WriteInt sfile,dm\meshid
WriteInt sfile,dm\islight
WriteInt sfile,dm\range
WriteFloat sfile,EntityX(dm\ent)
WriteFloat sfile,EntityY(dm\ent)
WriteFloat sfile,EntityZ(dm\ent)
WriteFloat sfile,EntityPitch(dm\ent)
WriteFloat sfile,EntityYaw(dm\ent)
WriteFloat sfile,EntityRoll(dm\ent)
WriteFloat sfile,dm\sx#
WriteFloat sfile,dm\sy#
WriteFloat sfile,dm\sz#
WriteByte sfile,dm\cr
WriteByte sfile,dm\cg
WriteByte sfile,dm\cb
Next

CloseFile sfile

ChangeDir thispath$

End Function


Function loadwork(ff$)
newscene()
fn$=ff$
ChangeDir thispath$

If FileType (fn$)<>1 Then Return -1

lf=ReadFile(fn$)
             ChangeDir thispath$
Numb=ReadInt(lf)
 For i=1 To numb
scc.dropmodel=New dropmodel
;	Meshname$=ReadString(lf) 

	Meshid=ReadInt(lf) 
    
	islight=ReadInt(lf)
    dmrange=ReadInt(lf)
	dx#=ReadFloat(lf)
	dy#=ReadFloat(lf)
	dz#=ReadFloat(lf)
	dpitch#=ReadFloat(lf)
	dyaw#=ReadFloat(lf)
	droll#=ReadFloat(lf)
	dssx#=ReadFloat(lf)
	dssy#=ReadFloat(lf)
	dssz#=ReadFloat(lf)
    dcr=ReadByte(lf)    
    dcg=ReadByte(lf)
    dcb=ReadByte(lf)
  If islight=1 Then 
  			 scc\ent=CreateCube()       
             ScaleMesh scc\ent,3,3,3
             EntityColor scc\ent,dcr,dcg,dcb
             EntityPickMode scc\ent,2
             scc\islight=1
             EntityFX SCC\ENT,1
             EntityColor scc\ent,dcr,dcg,dcb
             scc\range=dmrange
             scc\cr=dcr 
             scc\cg=dcg 
             scc\cb=dcb 
             PositionEntity scc\ent,dx,dy,dz
  Else 
  	        SCC\ENT= GetMesh(meshid,True)
            thisscale#=LoadedMeshScales#(meshid)*rescale_amount#
            ScaleMesh scc\ent,thisscale#,thisscale#,thisscale# 
             scc\meshid=meshid
             EntityPickMode scc\ent,2
             scc\islight=0
             scc\range=range
             PositionEntity scc\ent,dx,dy,dz
             RotateEntity scc\Ent,dpitch#,dyaw#,droll#
             ScaleEntity scc\ent,dssx,dssy,dssz
             scc\sx=dssx 
             scc\sy=dssy 
             scc\sz=dssz
   EndIf 
UpdateWorld()
RenderWorld()
Flip()
Next          
ChangeDir thispath$
End Function 


Function strip_path$(sfilename3$)
;strip path
  For i2= Len(sfilename3$) To 1 Step -1
   If Mid$(sfilename3$,i2,1)="\" Then
    sfilename3$=Right$(sfilename3$,Len(sfilename3$)-i2)
    Exit
   EndIf
  Next
Return sfilename3$
End Function

Function removeextension$(sfilename3$)
	count=0
	  For i2= Len(sfilename3$) To 1 Step -1
	count=count+1
	   If Mid$(sfilename3$,i2,1)="." Then
	    sfilename3$=Left$(sfilename3$,Len(sfilename3$)-count)
	    EndIf 
	Next
	  FN$=sFileName3$
  	 Return fn$
End Function

Function NewScene()
;Delete Each grouping
 For scc.dropmodel=Each dropmodel
   FreeEntity scc\ent
   Delete scc
   Next
End Function


Function ExportWork(fn$)
If Lower(Right$(fn$,4))<>"eb3d" Then fn$=fn$+".eb3d"
ChangeDir THISPATH$
;thisfold$=removeextension$(fn$)+"\"
savework("Data\Architect\SAVES\exported.act")
ChangeDir thispath$
destmesh=CreateMesh()
For dm.dropmodel=Each dropmodel
If dm\islight=0 Then 
DebugLog "added item to export"
ScaleMesh dm\ent,dm\sx,dm\sy,dm\sz
RotateMesh dm\ent,EntityPitch(dm\ent,True),EntityYaw(dm\ent,True),EntityRoll(dm\ent,True)
PositionMesh dm\ent,EntityX(dm\ent,True),EntityY(dm\ent,True),EntityZ(dm\ent,True)
AddMesh dm\ent,destmesh
EndIf
Next

;destmesh=Remove_Coincident_Tris(destmesh)
;weld destmesh
UpdateNormals destmesh
THISFOLD$=STRIP_PATH$(FN$)
;If FileType("Data\MESHES\Architect\"+Thisfold$)<>2 Then CreateDir("Data\MESHES\Architect\"+Thisfold$)
standmesh destmesh
ScaleMesh destmesh,2.6,2.6,2.6
savemultimesh destmesh,"Data\MESHES\Architect\"+thisfold$,"Data\MESHES\Architect\"
FreeEntity destmesh
newscene()
ChangeDir thispath$
loadwork("data\architect\saves\exported.act")
ChangeDir thispath$
encrypt_b3d("Data\MESHES\Architect\"+thisfold$)
ChangeDir thispath$

thisid=addmeshtodatabase ("Architect\"+thisfold$,False)
;If thisid=-1 Then  Encrypt_B3D(thispath$+"Data\Meshes\Architect\" +thisfold$)

End Function 




Function lightmapit() 
EntityPickMode griden,0

r= NEW_AMBIENTRED
g= NEW_AMBIENTGREEN
b= NEW_AMBIENTBLUE

Delete Each VertexLighttype

nobjects=0
nlights=0
For d.dropmodel=Each dropmodel
 If d\islight<>1 Then 
	nobjects=nobjects+1
 EntityFX d\ent,1+2
 EntityPickMode d\ent,2
 SetVertexLightAmbience( d\ent, R, G, B )
Else
nlights=nlights+1
EntityPickMode d\ent,0
 EndIf
Next
lcount=0
For l.dropmodel=Each dropmodel
		If l\islight=1 Then 
        thislight=thislight+1
DebugLog "Lightmapping, light found"
        thisobj=0 
			For d.dropmodel=Each dropmodel
               thisobj=thisobj+1 
				 If d\islight=0 Then 

				 lcount=lcount+1
RenderWorld()
	Color 5,5,5
	Text 1,1,"Lighting Object # "+thisobj+" out of "+nobjects
	Text 16,16,"Using light # "+thislight+" out of "+nlights
	Color 200,200,200
	Text 0,0,"Lighting Object # "+thisobj+" out of "+nobjects
	Text 15,15,"Using light # "+thislight+" out of "+nlights
Flip()
Cls
vertexLightMesh( D\ent, D\ent, EntityX(l\ent,1),EntityY(l\ent,1), EntityZ(l\ent,1),l\cr,l\cg, l\cb, l\Range, 1,d\sx,d\sy,d\sz )
                     EndIf
					Next
                   EndIf
                 Next

For d.dropmodel=Each dropmodel
 EntityPickMode d\ent,2 
 If d\islight=1 Then EntityPickMode d\ent,2
Next
End Function




Function FitEntity(EN1,EN2)
FreeEntity objectcube
objectcube=CreateCube()
en1=objectcube
EntityColor Objectcube,255,0,0
    vx# = GetMatElement#(en2, 0, 0)
	vy# = GetMatElement#(en2, 0, 1)
	vz# = GetMatElement#(en2, 0, 2)
	XScale# = Sqr#(vx# * vx# + vy# * vy# + vz# * vz#)+.1
	vx# = GetMatElement#(en2, 1, 0)
	vy# = GetMatElement#(en2, 1, 1)
	vz# = GetMatElement#(en2, 1, 2)
	YScale# = Sqr#(vx# * vx# + vy# * vy# + vz# * vz#)+.1
	vx# = GetMatElement#(en2, 2, 0)
	vy# = GetMatElement#(en2, 2, 1)
	vz# = GetMatElement#(en2, 2, 2)
	ZScale# = Sqr#(vx# * vx# + vy# * vy# + vz# * vz#)+.1

Vy1#=highvert(en2)
Vy2#=Lowvert(en2)
Vx1#=leftvert(en2)
Vx2#=Rightvert(en2)
Vz1#=Backvert(en2)
Vz2#=Frontvert(en2)
wide#=Vx2#-vx1#
tall#=Vy2#-vy1#
deep#=Vz2#-vz1#



FitMesh en1,vx1,vy1,vz1,wide,tall,deep
RotateEntity en1,EntityPitch(en2,1),EntityYaw(en2,1),EntityRoll(en2,1)
ScaleMesh en1,xscale,yscale,zscale
PositionEntity en1,EntityX(en2,1),EntityY(en2,1),EntityZ(en2,1)
EntityAlpha en1,.45
ShowEntity en1
End Function

Function Highvert(ent)
 maxvh#=-100000000
 SN=CountSurfaces(ent)
 For ss=1 To SN 
 	s=GetSurface(ent,ss)
	 For v=0 To CountVertices(s)-1
		 If VertexY(s,v)>maxvh# Then maxvh#=VertexY(s,v)
		 Next
	 Next
Return maxvh#
End Function

Function LowVert(ent)
 maxvh#=100000000
 SN=CountSurfaces(ent)
 For ss=1 To SN 
 	s=GetSurface(ent,ss)
	 For v=0 To CountVertices(s)-1
		 If VertexY(s,v)<maxvh# Then maxvh#=VertexY(s,v)
		 Next
	 Next
Return maxvh#
End Function

Function LeftVert(ent)
 maxvh#=100000000
 SN=CountSurfaces(ent)
 For ss=1 To SN 
 	s=GetSurface(ent,ss)
	 For v=0 To CountVertices(s)-1
		 If VertexX(s,v)<maxvh# Then maxvh#=VertexX(s,v)
		 Next
	 Next
Return maxvh#
End Function

Function RightVert(ent)
 maxvh#=-100000000
 SN=CountSurfaces(ent)
 For ss=1 To SN 
 	s=GetSurface(ent,ss)
	 For v=0 To CountVertices(s)-1
		 If VertexX(s,v)>maxvh# Then maxvh#=VertexX(s,v)
		 Next
	 Next
Return maxvh#
End Function

Function FrontVert(ent)
 maxvh#=-100000000
 SN=CountSurfaces(ent)
 For ss=1 To SN 
 	s=GetSurface(ent,ss)
	 For v=0 To CountVertices(s)-1
		 If VertexZ(s,v)>maxvh# Then maxvh#=VertexZ(s,v)
		 Next
	 Next
Return maxvh#
End Function

Function BackVert(ent)
 maxvh#=100000000
 SN=CountSurfaces(ent)
 For ss=1 To SN 
 	s=GetSurface(ent,ss)
	 For v=0 To CountVertices(s)-1
		 If VertexZ(s,v)<maxvh# Then maxvh#=VertexZ(s,v)
		 Next
	 Next
Return maxvh#
End Function



Function StandMesh (entity) 
FitMesh entity, -(MeshWidth (entity) / 2), (MeshHeight (entity) ), -(MeshDepth (entity) / 2), MeshWidth (entity), -MeshHeight(ENTITY), MeshDepth (entity) 
End Function






Function get_path$(sfilename3$)
;strip path
a=0
  For i2= Len(sfilename3$) To 1 Step -1
a=a+1   
   If Mid$(sfilename3$,i2,1)="\" Then
    sfilename3$=Left$(sfilename3$,Len(sfilename3$)-a)
    Return sfilename3$ 
    Exit
   EndIf
  Next
Return ""
End Function

Function UPDATELIGHT()
found=0
 For d.dropmodel=Each dropmodel
   If d\ent=selected_model And d\islight=1 Then 

Rng=NEWLightRange
R= NEW_LIGHTRED
G= NEW_LIGHTGREEN
B= NEW_LIGHTBLUE
    d\range=rng
    d\cr=r
    d\cg=g
    d\cb=b
    EntityColor d\ent,r,g,b
Exit
   EndIf
Next
End Function




Function GetMeshID(Name$)
    name$=Upper$(name$)
	Local Locked = False, i, MeshName$
    UNLOCKMESHES()
	If LockedMeshes <> 0
		Locked = True
	Else
		LockMeshes()
	EndIf

	For i = 0 To 65534
		MeshName$ = GetMeshName$(i)
		If Len(MeshName$) > 1
			If Upper$(Left$(MeshName$, Len(MeshName$)-1)) = Name$
				If Not Locked Then UnlockMeshes()
				Return i
			EndIf
		EndIf
	Next
	If Not Locked Then UnlockMeshes()
	Return -1

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
