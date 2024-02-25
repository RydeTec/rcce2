;Tools
Global RootDir$ = "..\..\"
Global LogMode = 1; (0 = standard logging, 1 = debug mode)
ChangeDir RootDir$

Global AH_Loca$ = "..\Realm Crafter 1\"
Global AH_Appb$ = "RCSTD"
;Include "antihack.bb"
; Loads Antihack module :)

Const testing=True

;BF_Start(5378)
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
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    ;setup for maximized/fullscreen window;
	;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;	Global GFX_WINDOW_WIDTH = 800GetSystemMetrics(0)
;	Global GFX_WINDOW_HEIGHT =600;GetSystemMetrics(1)
;	Global GFX_WINDOW_RESOLUTION = 0
;	Global GFX_WINDOW_MODE = 0
;	Global GFX_WINDOW_RESIZE = False
;	Global GFX_WINDOW_BORDER = False
;	Global GFX_WINDOW_CAPTION$ = "Realm Crafter Terrain Editor"
;	Global GFX_WINDOW_VERSION = 0.0
;	Global WINDOW_RESIZEMETHOD = 0
;	Global RENDERMODE = 1

    If GetSystemMetrics(0)<800 Then RuntimeError "RCTE requires a minimun of 800X600 desktop resolution"
    
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
    Include "..\Modules\f-ui.bb"
	AppTitle( GFX_WINDOW_CAPTION$, "Really quit?" )
    SetBuffer BackBuffer()

    If GetSystemMetrics(0)=>1024 Then 
	 	FUI_Initialise(1024, 768, 0, 2, False, True, "Realm Crafter Terrain Editor")
    	w=1024:h=768                                                                               ;  
    Else 
	 	FUI_Initialise(1024, 768, 0, 2, False, True, "Realm Crafter Terrain Editor")
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
    CameraRange Cam,1,MAXCAMRANGE#
    CameraClsMode Cam,True,True
    AmbientLight 255,255,255
	Include "..\modules\rcte_gui_shell_FUI.bb"
    ChangeDir thispath$



Const min_brush_size=1
Const max_brush_size=80
Dim GUI_HANDLE_TEMPORARY_IMAGE(6)
thispath$=CurrentDir()

	;Include "modules\encrypt_b3d.bb"
Include "..\modules\rcenet.bb"
Include "..\modules\language.bb"
Include "..\modules\safeloads.bb"
Include "..\modules\spells.bb"
Include "..\Modules\environment.bb"
Include "..\Modules\rctrees.bb"
Include "..\Modules\Logging.bb"
Include "..\Modules\Items.bb"
Include "..\Modules\Inventories.bb"
Include "..\Modules\Actors.bb"
Include "..\Modules\ServerAreas.bb"
Include "..\Modules\ClientAreasTE.bb"
Include "..\Modules\RottParticles.bb"
Include "..\Modules\media.bb"
Include "..\Modules\MediaDialogs.bb"
Include "..\modules\weld.bb"
	
Include "..\modules\Utility.bb"

If FileType("Data\rcte\rcte_loading.jpg")=1 Then 
templogo=loadimage_safe("Data\rcte\rcte_loading.jpg")
ResizeImage templogo,GraphicsWidth()/1.5,GraphicsHeight()/1.5
DrawBlock templogo,GraphicsWidth()/6,GraphicsHeight()/6
Flip(True)
FreeImage templogo
EndIf

Global CURRENTLY_SELECTED_TERRAINEDIT=1
;#Region Global Variables Setup
Dim GUI_ARRAY_SEASONCOLOR( 11, 2 )
;HERE WELL CONVERT OUR TREE SEASON DATA TO THIS FORMAT
 For i=0 To 11
  GUI_ARRAY_SEASONCOLOR( i,0 )= season_red(i)
  GUI_ARRAY_SEASONCOLOR( i,1 )= season_green(i)
  GUI_ARRAY_SEASONCOLOR( i,2 )= season_blue(i)  
 Next

;Local Media 
Dim MeshNames$(65534)
Type MESHFOLDER
	Field MeshName$
	Field meshid
	Field FOLDERNAME$
End Type
Global mf.MESHFOLDER
Type meshfolder_check
	Field MeshName$
	Field FOLDERNAME$
End Type
Global mfc.meshfolder_check
Global ModelFolder$=""



Global guipath$="DATA\RCTE\"
Global maindir$

; Path to executable
maindir$=thispath$
ChangeDir thispath$
Global loadworkpath$ = ""
Global modelpath$="DATA\RCTE\RCTE_MODELS\"
Global savepath$="DATA\RCTE\RCTE_SAVED\"
Global exportpath$="DATA\MESHES\RCTE\"
Global texturepath$="DATA\RCTE\RCTE_TEXTURES\"
Global areapath$="DATA\AREAS\"
Global landscape1,Highvert#,LowVert#,range#,mark1#,mark2#
Global mlook=-1
Global oldbb_speed#
Global ShowUndo=1

Global SkyDome=LoadMesh_safe("DATA\RCTE\SKY.B3D")
Global Ruler=LoadMesh_safe("DATA\RCTE\RULER.B3D")
EntityFX SkyDome,1+8
Global Skydometex=LoadTexture_safe("DATA\RCTE\SD_PC03.JPG",1+8)
EntityTexture SkyDome,Skydometex
EntityOrder SkyDome,1500

;FPS
Global GFX_VARIABLE_VSYNC = True
Global GUI_VARIABLE_CAMERASPEED# = 1.0
Global DEBUGMODE=False




Global fogfarnow#=1500
Tree_changeweather(w_wind)
Type TEScenery
	 Field name$
	 Field id
End Type
Global tesc.TEScenery
Global maxscenery
Dim rand_map(128,128)

;loadmodeldata()

Global ThisSC_mesh
Dim sceneryinviewid(12)
Dim sceneryinviewname$(12)
Global RANDIMAGE
Global scaleTimer#=MilliSecs()
Global scaletimer_inc#=100
Global sceneryoffset
Global sceneryname$
Global sceneryID
Global exitloader%
Global system_message$
Global system_messagetimer#

If FileType ("DATA\RCTE")<>2 Then CreateDir ("DATA\RCTE")
If FileType ("DATA\RCTE\RCTE_TEXTURES")<>2 Then CreateDir ("DATA\RCTE\RCTE_TEXTURES")
If FileType ("DATA\RCTE\RCTE_SAVED")<>2 Then CreateDir ("DATA\RCTE\RCTE_SAVED")
If FileType ("DATA\MESHES\RCTE")<>2 Then CreateDir ("DATA\MESHES\RCTE")


;lightmapping settings
Global LmAmbient_terr=65
Global lmblur_terr=1

Global Terrainscale#=1.0
Global AutoMinSlope=1
Global AutoMaxSlope=1
Global AutoMaxHeight=1
Global AutoMinHeight=1
Global AutoDensity=1
; ------------MODEL DROPPING Data
Type dropmodel
    Field shadowent
    Field permapick
	Field ent
	Field id
	Field ModelFileName$
	Field modelfilepath$
	Field x#,y#,z#
	Field pitch#,yaw#,roll#
	Field sx#,sy#,sz#
	Field Alpha#
	Field rcTe$
	Field AnimationMode
	Field SceneryID
	Field Lightmap$
	Field TextureID
	Field CatchRain
	Field Collides
	
End Type

Global dm.dropmodel
Global Mc_onoff=0
Global Mc_sx#,mc_sy#,mc_sz#
Global mc_id
Global mc_rcte$
Global Mc_pitch#,mc_yaw#,mc_roll#

Global droptimer# ;prevents painting models too fast
Type vertexupd
     Field commandindex
     Field surface,index,x#,y#,z#
     Field u#,v#,w#,coordset
     Field r#,g#,b#,a#
End Type

Type autoundo
	Field ent
End Type
Global au.autoundo

Global selected_dropmodel
Global selectedmodel$,selectedmodelpath$,modelfilepath$



; ---------------Texture settings
Dim DTexMinSlope(6)
Dim DTexMaxSlope(6)
Dim DTexMinHeight(6)
Dim DTexMaxHeight(6)

Global texminheight,texmaxheight
Global texminslope,texmaxslope

	 DTexMinSlope(6)=80
	 DTexMaxSlope(6)=100
	 DTexMinHeight(6)=1
	 DTexMaxHeight(6)=100

	 DTexMinSlope(5)=69
	 DTexMaxSlope(5)=79
	 DTexMinHeight(5)=1
	 DTexMaxHeight(5)=100

	 DTexMinSlope(4)=58
	 DTexMaxSlope(4)=68
	 DTexMinHeight(4)=1
	 DTexMaxHeight(4)=100

	 DTexMinSlope(3)=0
	 DTexMaxSlope(3)=35
	 DTexMinHeight(3)=1
	 DTexMaxHeight(3)=100

	 DTexMinSlope(2)=47
	 DTexMaxSlope(2)=57
	 DTexMinHeight(2)=1
	 DTexMaxHeight(2)=100

	 DTexMinSlope(1)=36
	 DTexMaxSlope(1)=46
	 DTexMinHeight(1)=1
	 DTexMaxHeight(1)=100
	

;Global light=CreateLight() :RotateEntity light,30,0,0
Global light=CreatePivot() 
 skyen=CreateCube()
EntityAlpha skyen,0
 clouden=CreateCube()
EntityAlpha clouden,0
 starsen=CreateCube()
EntityAlpha starsen,0

;-The below Type is used in export
Type opvert
	 Field layer
	 Field v
End Type
Global opvert.opvert
Include "..\modules\removetri.bb"
SeedRnd MilliSecs() 
Dim opsurf(10);optimise surface array.. used to hold surface info

Include "..\modules\b3dfile.bb"  ;used for B3D Saving

Global c_surfs
mamax=100
Dim c_surf(mamax)
Dim c_brush(mamax)
Dim c_tex(mamax)
Dim c_tex_name$(mamax),use_tex$(mamax)
use_tex$(1)="data\rcte\rcte_Textures\tex1.png"
use_tex$(2)="data\rcte\rcte_Textures\tex2.png"
use_tex$(3)="data\rcte\rcte_Textures\tex3.png"
use_tex$(4)="data\rcte\rcte_Textures\tex4.png"
use_tex$(5)="data\rcte\rcte_Textures\tex5.png"
use_tex$(6)="data\rcte\rcte_Textures\tex6.png"

;-----------------------------
;GLOBALS AND CONSTANTS
;color brush
Global c_red#=255.0
Global c_blue#=255.0
Global c_green#=255.0
;gui stuff
Global lockmouse=1
Global brushtype=0
;layer you are working with
Global selectedlayer=1,brushsize=4
Global selected_Segment=0
;selected segments 1=north west, 2 = north east 3=south west 4 = south east 0 = all
Global numlayers=6
Const maxLayers = 6

Dim optimizedLayer(maxLayers)

Global lsegments=250 ; currently this is about the max size due to the 64k Vertices Limit in DX (I guess that was Billy Boy once again who said "Nobody ever need more than 64k Vertices in a Surface"...)
Global lscale#=25
Global fadespeed#=.01
;the layers
Dim loadscale#(6)
Const numterrains=8
Dim l_index(lsegments,lsegments)
Dim l_height(lsegments,lsegments)
Global alpha_Segs
Dim alpha_Record#(numlayers,lsegments,lsegments)
Dim layer(numlayers)
Dim copy_layer(numlayers)
Dim layertexture(numlayers)
Dim layerbrush(numlayers)
Dim layertexscale#(numlayers)
Dim tex_thumb(numlayers)

;######################################
; create indicator quad
Global areaRect = RCTE_CREATEQUAD()
EntityColor areaRect,255,0,0
tempimage=CreateImage(128,128)
SetBuffer(ImageBuffer(tempimage))
Color 0,0,0
Oval 0,0,128,128,True
Color 255,0,0
Oval 0,0,128,128,True
Color 0,0,0
Oval 4,4,120,120,True
SetBuffer BackBuffer()
SaveImage tempimage,"tempimage.bmp"
Global areatexture=LoadTexture("tempimage.bmp",1+4)
DeleteFile "tempimage.bmp"
ScaleMesh areaRect,.4,.4,.4
EntityTexture areaRect,areatexture

Global AreaFlatten=RCTE_CREATEQUAD()

EntityColor AreaFlatten,0,0,255
tempimage2=CreateImage(128,128)
SetBuffer(ImageBuffer(tempimage2))
Color 0,0,255
Rect 0,0,128,128,False
Rect 1,1,126,126,False
Rect 2,2,124,124,False
SetBuffer BackBuffer()
SaveImage tempimage2,"tempimage2.bmp"
thistexture2=LoadTexture("tempimage2.bmp",1+4)
DeleteFile "tempimage2.bmp"
EntityTexture AreaFlatten,thistexture2
ScaleMesh AreaFlatten,1.2,1.2,1.2
HideEntity AreaFlatten
Global FlattenHEIGHT#=-.08
;-------------------------------



;lsegments=1
;createlayers()

Global   MaxHills = 1
Global   MaxVallies = 1 
Global   Hillheights = 1
Global   ValleyLow = 1
Global   HillDiam = 1
Global   ValleyDiam = 1
Global   MaxSmooths = 0

Global Boxmesh=CreateCube()
EntityColor Boxmesh,255,0,0
PositionEntity Boxmesh,-9999,99999,-99999
EntityAlpha Boxmesh,.6
; -------------------model brush list
Type ModelBrush
 Field Name$
 Field Id
 Field MeshScale#
 Field rcte$
 Field sx#,sy#,sz#
End Type

Global paintmodelcount=0
Global ModelBrushScale#=1.0
Global ModelBrushTimer#=MilliSecs()
Global ModelBrushVar#=.1
Global TotalmodelBrush
Global BillBoardMode=0

 Global Current_backup=0   
 Global Selected_Backup=0
 Global Actiontracker=0

;-check settings
ChangeDir thispath$
If FileType ("DATA\RCTE\Settings.dat")=1 Then 
    f=OpenFile("DATA\RCTE\Settings.dat")
    BillBoardMode=ReadInt(F)
    CloseFile(f)
   EndIf
;RuntimeError "hhmmf"
;   GUI_Setstatus(RCBillboardCheck.window,billboardmode)

;######################################
; Main Loop
DebugLog "init media"
initmediadialogs()
DebugLog "clear undos
clearundos
Dim OILcol(1,0)
Dim rotate_hm_buffer(1,1)
newmap(64,0,-1)

use_tex$(1)="TEX1.PNG"
use_tex$(2)="TEX2.PNG"
use_tex$(3)="TEX3.PNG"
use_tex$(4)="TEX4.PNG"
use_tex$(5)="TEX5.PNG"
use_tex$(6)="TEX6.PNG"

assigntexture 1,"tex1.png"
assigntexture 2,"tex2.png"
assigntexture 3,"tex3.png"
assigntexture 4,"tex4.png"
assigntexture 5,"tex5.png"
assigntexture 6,"tex6.png"
assigntexture 1,"null"

Global Holebrush=0
Global OldSeason
OldSeason=currentseason-1
PositionEntity Cam,0,50,-100
Include "..\modules\hmapgen.bb"

;#Region Main Loop
Global totaldrops
totaldrops=0

;Global GameTmr=CreateTimer(25)
sceneryID=-1

;EnableDirectInput 1

For mbr.modelbrush=Each ModelBrush
	Delete mbr
Next
updatemodelbrushlist() 
POSITIONWINDOWS()
InitFPS()
Repeat
	;#Region Rendering	
	Main()  
	CameraProjMode( App\Cam, False )
	CameraProjMode( Cam, True )
;	UnlockBuffer(BackBuffer()) 
;	LockBuffer(FrontBuffer()) 
	UpdateWorld()
;	LockBuffer(BackBuffer())
;	UnlockBuffer(FrontBuffer())
	Buttoncheck()
	RenderWorld( )
	UpdateFPS()
	CameraProjMode( Cam, False )
	CameraProjMode( App\Cam, True )
	FUI_Update( )
	If sysmsg$<>"" And sysmsgtimer#>MilliSecs() Then
		Color( 15, 15, 15 )
		Text GraphicsWidth()/4,GraphicsHeight()/2,sysmsg$          
		Color( 255, 255, 255 )
		Text 75,70,sysmsg$          
	EndIf

	Flip( FPS > 30 )
;#End Region
Until KeyDown(1)
FUI_Destroy( )
End


Function Main()
	;------------------------------------
	If KeyHit(46) Then Createbackup()
	If KeyHit(22) Then UndoMap()
	If KeyHit(19) Then RedoMap()
	If KeyHit(12) Or KeyHit(74) Then Scaleterrain .9
	If KeyHit(13) Or KeyHit(78) Then Scaleterrain 1.1

	;------------------------------------
	;currentseason=gui_getspinnervalue(treeseason.spinner) - 1
	currentseason=Int(FUI_SendMessage(GUI_TREEWIN_SEASON,M_GETVALUE))-1 
	If OldSeason<>currentseason Then 
		OldSeason=currentseason
		TIMAGE = CreateImage( 70, 45 )
		SetBuffer ImageBuffer(timage)
		Color GUI_ARRAY_SEASONCOLOR( currentseason, 0 ), GUI_ARRAY_SEASONCOLOR( currentseason, 1 ), GUI_ARRAY_SEASONCOLOR( currentseason, 2 )
		Rect 0, 0, 70, 45,1
		FUI_DeleteGadget(GUI_TREEWIN_CANVAS)
		GUI_TREEWIN_CANVAS = FUI_ImageBox( GUI_TREEWIN, 125, 90, 68, 45, TIMAGE )
		SetBuffer BackBuffer()
		FreeImage timage 
	EndIf


   

	;------------------------------------
	If KeyDown(57) Then mlook=1 Else mlook=-1
	If mlook=1 Then 
	mouselook(Cam)
	If MouseDown(1) Then MoveEntity Cam, 0, 0, GUI_VARIABLE_CAMERASPEED# / FPS
	If MouseDown(2) Then MoveEntity Cam, 0, 0, -GUI_VARIABLE_CAMERASPEED# / FPS
	EndIf
	mouseclicked=0
	If MouseDown(1) Then mouseclicked=1
	If MouseDown(2) Then mouseclicked=2
	
	;buttons that require mouse held down, not clicked
	If MOUSECLICKED=1 Then 
		If fui_OVERGADGET(GUI_BOTTOMWIN_SCALEDOWN)  Then   
			If FUI_SendMessage(GUI_BOTTOMWIN_SCALEALLTEX,M_GETCHECKED)=True Then  
	
				For sts=1 To numlayers
					layertexscale#(sts)=layertexscale#(sts)-.001
					If layertexscale#(sts)<.001 Then layertexscale#(sts)=.001
					ScaleTexture layertexture(sts),layertexscale#(sts),layertexscale#(sts)
					BrushTexture layerbrush(sts),layertexture(sts)
					PaintMesh layer(sts),layerbrush(sts)
				Next
			Else
				layertexscale#(selectedlayer)=layertexscale#(selectedlayer)-.001
				If layertexscale#(selectedlayer)<.001 Then layertexscale#(selectedlayer)=.001
					ScaleTexture layertexture(selectedlayer),layertexscale#(selectedlayer),layertexscale#(selectedlayer)
					BrushTexture layerbrush(selectedlayer),layertexture(selectedlayer)
					PaintMesh layer(selectedlayer),layerbrush(selectedlayer)
				EndIf
			EndIf
		;-------
		If fui_OVERGADGET(gUI_BOTTOMWIN_SCALEUP)  Then 
			If FUI_SendMessage(GUI_BOTTOMWIN_SCALEALLTEX,M_GETCHECKED) =True 
				For sts=1 To numlayers
					layertexscale#(sts)=layertexscale#(sts)+.001
					ScaleTexture layertexture(sts),layertexscale#(sts),layertexscale#(sts)
					BrushTexture layerbrush(sts),layertexture(sts)
					PaintMesh layer(sts),layerbrush(sts)
				Next
			Else
				layertexscale#(selectedlayer)=layertexscale#(selectedlayer)+.001
				ScaleTexture layertexture(selectedlayer),layertexscale#(selectedlayer),layertexscale#(selectedlayer)
				BrushTexture layerbrush(selectedlayer),layertexture(selectedlayer)
				PaintMesh layer(selectedlayer),layerbrush(selectedlayer)
			EndIf		
		EndIf
		;-------
		If fui_OVERGADGET(GUI_MOVEWIN_MLEFT)   Then movemodel -.0045,0,0
		If fui_OVERGADGET(GUI_MOVEWIN_MRIGHT) Then movemodel .0045,0,0
		If fui_OVERGADGET(GUI_MOVEWIN_MFORWARD)  Then movemodel 0,0,.0045
		If fui_OVERGADGET(GUI_MOVEWIN_MBACK)  Then movemodel 0,0,-.0045
		If fui_OVERGADGET(GUI_MOVEWIN_MUP)  Then movemodel 0,.0045,0
		If fui_OVERGADGET(GUI_MOVEWIN_MDOWN) Then movemodel 0,-.0045,0
		;----------
		If fui_OVERGADGET(GUI_MOVEWIN_TLEFT) Then rotatemodel 0,-.5,0
		If fui_OVERGADGET(GUI_MOVEWIN_TRIGHT) Then rotatemodel 0,.5,0
		If fui_OVERGADGET(GUI_MOVEWIN_TUP) Then rotatemodel -.5,0,0
		If fui_OVERGADGET(GUI_MOVEWIN_TDOWN) Then rotatemodel .5,0,0
		If fui_OVERGADGET(GUI_MOVEWIN_TFORWARD) Then rotatemodel 0,0,-.5
		If fui_OVERGADGET(GUI_MOVEWIN_TBACK) Then rotatemodel 0,0,.5
		;----------
		If fui_OVERGADGET(GUI_MOVEWIN_XZOOMOUT) Then scalemodel .9,1,1
		If fui_OVERGADGET(GUI_MOVEWIN_XZOOMIN) Then  scalemodel 1.1,1,1
		If fui_OVERGADGET(GUI_MOVEWIN_ZZOOMOUT) Then scalemodel 1,1,.9
		If fui_OVERGADGET(GUI_MOVEWIN_ZZOOMIN) Then  scalemodel 1,1,1.1
		If fui_OVERGADGET(GUI_MOVEWIN_YZOOMOUT) Then scalemodel 1,.9,1
		If fui_OVERGADGET(GUI_MOVEWIN_YZOOMIN) Then  scalemodel 1,1.1,1
		If fui_OVERGADGET(GUI_MOVEWIN_XYZOUT) Then   scalemodel .9,.9,.9
		If fui_OVERGADGET(GUI_MOVEWIN_XYZIN) Then    scalemodel 1.1,1.1,1.1
		;--------------------
	
	EndIf

	;------------------------------------
	PositionEntity SkyDome,EntityX(Cam,1),EntityY(Cam,1),EntityZ(Cam,1)
	;------------------------------------
	HideEntity AreaFlatten
	;Mode 1 - Paint.  Mode 2 - Edit Terrain.  Mode 3 - Model
	Select CURRENTLY_SELECTED_TERRAINEDIT
		Case 1
			GUI_VARIABLE_BRUSHSIZE = FUI_SendMessage(GUI_SLIDERWIN_BRUSHSIZE,M_GETVALUE)	
			GUI_VARIABLE_BRUSHSPEED = FUI_SendMessage(GUI_SLIDERWIN_BRUSHSPEED,M_GETVALUE)
			;If the color checkbox is enabled then use color, instead of texture
			If FUI_SendMessage(GUI_MODEPAINT_USECOLOR,M_GETCHECKED) = True Then
				If MouseDown(2) And mlook =-1 Then bringup(brushsize,6)
			Else
				If MouseDown(2) And mlook =-1 Then bringup(brushsize,0)
			EndIf

		;- Terrain Paints
		Case 2
			GUI_VARIABLE_BRUSHSIZE = FUI_SendMessage(GUI_SLIDERWIN_BRUSHSIZE,M_GETVALUE)	
			GUI_VARIABLE_BRUSHSPEED = FUI_SendMessage(GUI_SLIDERWIN_BRUSHSPEED,M_GETVALUE)
			If FUI_SendMessage(GUI_MODEEDIT_LOWER,M_GETCHECKED) = True Then
				If MouseDown(2) And mlook = -1 Then AlterLand( 2*-fadespeed, brushsize )
			
			ElseIf FUI_SendMessage(GUI_MODEEDIT_RAISE,M_GETCHECKED) = True Then
				If MouseDown(2) And mlook=-1 Then AlterLand( 2*fadespeed, brushsize )
			
			ElseIf FUI_SendMessage(GUI_MODEEDIT_PULL,M_GETCHECKED) = True Then
				If MouseDown(2) And mlook=-1 Then bringup( brushsize, 3 )
			
			ElseIf FUI_SendMessage(GUI_MODEEDIT_PUSH,M_GETCHECKED) = True Then
				If MouseDown(2) And mlook=-1 Then bringup( brushsize, 1 )
			
			
			ElseIf FUI_SendMessage(GUI_MODEEDIT_SMOOTH,M_GETCHECKED) = True Then
				If MouseDown(2) And mlook=-1 Then bringup( brushsize, 2 )
			
			ElseIf FUI_SendMessage(GUI_MODEEDIT_MODEL,M_GETCHECKED) = True Then
				If MouseDown(2) And mlook=-1 Then PaintModels( 0 )
			
			ElseIf FUI_SendMessage(GUI_MODEEDIT_DELETE,M_GETCHECKED) = True Then
				If MouseDown(2) And mlook=-1 Then PaintModels( 1 )
			
			ElseIf FUI_SendMessage(GUI_MODEEDIT_FLATTEN,M_GETCHECKED) = True Then 
				Thismz=MouseZSpeed()
				If Thismz<>0 Then FlattenHEIGHT=FlattenHEIGHT+(Sgn(thismz)*2)
				ShowEntity AreaFlatten
				If MouseDown(2) And mlook=-1 Then bringup( brushsize, 33 )
			EndIf
		
		Case 3	
			GUI_VARIABLE_BRUSHSIZE = FUI_SendMessage(GUI_SLIDERWIN_BRUSHSIZE,M_GETVALUE)	
			GUI_VARIABLE_BRUSHSPEED = FUI_SendMessage(GUI_SLIDERWIN_BRUSHSPEED,M_GETVALUE)
			
			HideEntity AreaFlatten
			If MouseHit(2)  Then 
				If Selectmodel()=0 And sceneryID<>-1 Then dropmodel
				If KeyDown(211) Then Deletemodel 101
			EndIf
	
	End Select

	If FUI_SendMessage(GUI_MODEEDIT_FLATTEN,M_GETCHECKED) <> True Then 
		Thismz=Sgn(MouseZSpeed())
		If Thismz<>0 Then
			GUI_VARIABLE_BRUSHSIZE=GUI_VARIABLE_BRUSHSIZE+(Sgn(thismz)*4)
			If GUI_VARIABLE_BRUSHSIZE>max_brush_size Then GUI_VARIABLE_BRUSHSIZE=max_brush_size
			If GUI_VARIABLE_BRUSHSIZE<min_brush_size Then GUI_VARIABLE_BRUSHSIZE=min_brush_size
			FUI_SendMessage( GUI_SLIDERWIN_BRUSHSIZE, M_SETVALUE,GUI_VARIABLE_BRUSHSIZE)
			brushsize = FUI_SendMessage(GUI_SLIDERWIN_BRUSHSIZE,M_GETVALUE)	
			If brushsize>0 Then brushsize=brushsize/5
		EndIf
	EndIf

	;area rect
	camp=0
    camp = CameraPick(Cam,MouseX(),MouseY())
	If camp <> 0 Then
		ShowEntity areaRect
		ShowEntity Ruler
        EntityOrder areaRect,-5000
        px#=PickedX()
        py#=PickedY()
        pz#=PickedZ()
 		PositionEntity areaRect,PX,0,PZ
        PositionEntity AreaFlatten,PX,FlattenHEIGHT,PZ


		offval2#=((Terrainscale-54.0)*(brushsize*.5))*2
		offval#=offval2
;		DebugLog "BRUSHSIZE IS "+brushsize
;		DebugLog "TERRAINSCALE IS "+Terrainscale
;		DebugLog "OFFVAL IS "+OFFVAL 

          

  		LinePick (px-offval),py+250,(pz+offval),0,-500,0
		p1# = PickedY()+.1
        ;-
		LinePick (px+offval),py+250,(pz+offval),0,-500,0
		p2# = PickedY()+.1
        ;-
		LinePick (px+offval),py+250,(pz-offval),0,-500,0
	    p3# = PickedY()+.1
        ;-
		LinePick (px-offval),py+250,(pz-offval),0,-500,0
		p4# = PickedY()+.1
        ;-
        S=GetSurface(areaRect,1)
    
        avgh#=(p1+p2+p3+p4)/4 
 		PositionEntity Ruler,px,avgh,pz

		VertexCoords(s,2,(-brushsize-offval),p4,(-brushsize-offval))
		VertexCoords(s,3,(-brushsize-offval),p1,(brushsize+offval))
		VertexCoords(s,0,(brushsize+offval),p3,(-brushsize-offval))
		VertexCoords(s,1,(brushsize+offval),p2,(brushsize+offval))

        ScaleEntity AreaFlatten,offval2,offval2,offval2,1

	Else
		HideEntity areaRect
		HideEntity AreaFlatten
		HideEntity Ruler
		
	EndIf

		Color 255,0,0
         
		;end are rect
End Function
;#End Regionline


Function assigntexture(ln,texfile$)
	If texfile$="null" Then
		Fui_applytexture(GUI_TPREVWIN_BUTTON,GUI_HANDLE_TEMPORARY_IMAGE( ln-1 ),-1)
		;  RuntimeError GUI_TPREVWIN_BUTTON+" "+GUI_HANDLE_TEMPORARY_IMAGE( ln-1 )
		Return
	EndIf
	
  For i2= Len(texfile$) To 1 Step -1
   If Mid$(texfile$,i2,1)="\" Then
    texfile$=Right$(texfile$,Len(texfile$)-i2)
    Exit
   EndIf
  Next


If FileType(thispath$+texturepath$+texfile$)<>1 Then 
	system_messagetimer#=MilliSecs()+1000
	system_message$="Texture not in Texture Dir."
    RuntimeError texfile$
	Return
EndIf 

use_tex$(ln)=texfile$
If FileType(thispath$+texturepath$+texfile$)<>1 Then RuntimeError ("Texture not found :"+texfile$)
layertexture(ln)=loadtexture_safe(thispath$+texturepath$+texfile$)
layerbrush(ln)=CreateBrush()
ScaleTexture layertexture(ln),.5,.5
layertexscale#(ln)=.5
BrushTexture layerbrush(ln),layertexture(ln)
PaintMesh layer(ln),layerbrush(ln)
If tex_thumb(ln)<>0 Then FreeTexture tex_thumb(ln)
tex_thumb(ln)=loadtexture_safe(thispath$+texturepath$+texfile$)
If GUI_HANDLE_TEMPORARY_IMAGE( ln-1 )<>0 Then FreeTexture GUI_HANDLE_TEMPORARY_IMAGE( ln-1 )
GUI_HANDLE_TEMPORARY_IMAGE( ln-1 )=loadTEXTURE_safe(thispath$+texturepath$+texfile$)


Fui_applytexture(GUI_TPREVWIN_BUTTON,GUI_HANDLE_TEMPORARY_IMAGE( ln-1 ),-1)
Select ln
 Case 1
  Fui_applytexture(GUI_BOTTOMWIN_TEXTHUMB(1),tex_thumb(ln),-1)
 Case 2
  FUI_applytexture(GUI_BOTTOMWIN_TEXTHUMB(2),tex_thumb(ln),-1)
 Case 3
  FUI_applytexture(GUI_BOTTOMWIN_TEXTHUMB(3),tex_thumb(ln),-1)
 Case 4
  Fui_applytexture(GUI_BOTTOMWIN_TEXTHUMB(4),tex_thumb(ln),-1)
 Case 5
  Fui_applytexture(GUI_BOTTOMWIN_TEXTHUMB(5),tex_thumb(ln),-1)
 Case 6
  Fui_applytexture(GUI_BOTTOMWIN_TEXTHUMB(6),tex_thumb(ln),-1)
End Select
End Function  
 

;Function assigntexture(ln,texfile$)
;This function loads a new texture and then 
;paints the proper layer with it.
; layertexture(ln)=loadtexture_safe(texfile$)
; layerbrush(ln)=CreateBrush()
; ScaleTexture layertexture(ln),.1,.1
; layertexscale#(ln)=.1
; BrushTexture layerbrush(ln),layertexture(ln)
; PaintMesh layer(ln),layerbrush(ln)
; tex_thumb(ln)=loadimage_safe(texfile$)
; ResizeImage tex_thumb(ln),64,64
;End Function





Function CreateLayer(segs=64,parent=0,ls#=1)
	DebugLog("Create Layer with " + segs + " segments")
	mesh=CreateMesh( parent )
	surf=CreateSurface( mesh )
    brush=CreateBrush(surf)
	stx#=-.5
	sty#=stx
	stp#=Float(1)/Float(segs)
	y#=sty
	For a=0 To segs
		x#=stx
		v#=a/Float(segs)
		For b=0 To segs
			u#=b/Float(segs)
			l_index(a,b)=AddVertex(surf,x,0,y,u,v)
			VertexColor surf,l_index(a,b),255,255,255,0
			x=x+stp
		Next
		y=y+stp
	Next

	For a=0 To segs-1
		For b=0 To segs-1
			v0=a*(segs+1)+b:v1=v0+1
			v2=(a+1)*(segs+1)+b+1:v3=v2-1
			AddTriangle( surf,v0,v2,v1 )
			AddTriangle( surf,v0,v3,v2 )
		Next
	Next
    
    ScaleMesh mesh,ls#,ls#,ls#
	UpdateNormals mesh 
	Return mesh
End Function




Function setalpha(l,llx,llz,al#,splatter=0)
 ;set new alpha value to a vert on a layer
  s=GetSurface(layer(l),1)
  nv=CountVertices(s)
  thisvert=l_index(llx,llz)
 ;if you try to set alpha to a vert outside fo the max verts in mesh then
 ;return without error, and without alpha change
 If thisvert<nv Then VertexColor s,thisvert,VertexRed(s,thisvert),VertexGreen(s,thisvert),VertexBlue(s,thisvert),al#
 If splatter Then 
  ScaleAlpha(l,llx+1,llz,(-al/2))
  ScaleAlpha(l,llx-1,llz,(-al/2))
 ; scalealpha(l,llx,llz+lsegments,-al)
 ; scalealpha(l,llx,llz-lsegments,-al)
 EndIf
End Function



 
Function ScaleAlpha(l,llx,llz,al#,splatter=0)
 ;set new alpha value to a vert on a layer
 s=GetSurface(layer(l),1)
 nv=CountVertices(s)
 If llx>lsegments Then llx=lsegments
 If llx<0 Then llx=0
 If llz<0 Then llz=0
 If llz>lsegments Then llz=lsegments

 thisvert=l_index(llx,llz)
 ;if you try to set alpha to a vert outside fo the max verts in mesh then
 ;return without error, and without alpha change
 old_al#=VertexAlpha(s,thisvert)
 old_al#=old_al#+al#
 If old_al#<0 Then old_al#=0
 If old_al#>1 Then old_al#=1
 If thisvert<nv Then VertexColor s,thisvert,VertexRed(s,thisvert),VertexGreen(s,thisvert),VertexBlue(s,thisvert),old_al#

End Function




Function loadhmap(texture$,amp#=5)
If FileType(texture$)<>1 Then Return
 texx=loadimage_safe(texture$)
 ResizeImage texx,lsegments+1,lsegments+1

 s=GetSurface(layer(1),1)
 nv=CountVertices(s)
For I=1 To numlayers
	 s=GetSurface(layer(I),1)
	
	 For vz = 0 To lsegments
	 For vx = 0 To lsegments
	     thisvert=l_index(vx,vz)
	     gettexcol(Texx,vx,vz)
	     Cr#=numcolR      
	    If (cr => 0) Then
	If Terrainscale=<1 Then 
	byy# = ((cr - 128) / 255) * amp
	Else
	byy# = (((cr - 128) / 255) * (amp*Terrainscale)*.2)
	
EndIf
;If vz<>lsegments And vx<>lsegments Then 
    VertexCoords S,thisvert,VertexX(s,thisvert),byy,VertexZ(s,thisvert)
;EndIf
     
   End If
Next
Next


Next
For i=1 To numlayers
 centermesh layer(i)
Next
Flopmodels
 For i=1 To numlayers
  UpdateNormals layer(i)
 Next

End Function



Global numcolR#,numcolG,numcolB
Function numcolor(num#)
;convert number to r g b values
  numcolR=num  Shr 16 And %11111111
  numcolG=num Shr 8 And %11111111
  numcolB=num And %11111111
End Function



Function gettexcol(tex,ttx,tty)
; get results from numcolR, numcolG, numcolB
  SetBuffer ImageBuffer(tex)
  LockBuffer ImageBuffer(tex)
  numcolor(ReadPixelFast(ttx,tty))
  UnlockBuffer ImageBuffer(tex)
  SetBuffer BackBuffer()
End Function



;Control
; Camera position, angle values
Global cam_x#,cam_z#,cam_pitch#,cam_yaw#,cam_speed#=.5		; Current
Global dest_cam_x#,dest_cam_z#,dest_cam_pitch#,dest_cam_yaw#	; Destination
Global ent_x#,ent_z#,ent_pitch#,ent_yaw#,ent_speed#=.5		; Current
Global dest_ent_x#,dest_ent_z#,dest_ent_pitch#,dest_ent_yaw#	; Destination

Function mouselook(Lcamera)
lockmouse=1
	; Mouse look
	; ----------

	; Mouse x and y speed
	mxs#=MouseXSpeed()
	mys#=MouseYSpeed()
    If Abs(mxs)>30 Or Abs(mys)>30 Then 
       mxs=5
       mys=5
       EndIf
	
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


;Global MXA#,MYA#
;Function mouselook(c)
; mxa#=mxa-.55*MouseXSpeed()
; mya#=mya+.55*MouseYSpeed()
; If mxa<0 Then mxa=mxa+360
; If mxa>360 Then mxa=mxa-360
; If mya<0 Then mya=mya+360
; If mya>360 Then mya=mya-360
; RotateEntity c,mya,mxa,0
; MoveMouse GraphicsWidth()/2,GraphicsHeight()/2
;End Function




Function bringup(area,style=0,ox=-1,oz=-1)

If mlook=1 Then Return
If style=4  Then 
  AlterLand .25,brushsize
  Return
  EndIf  
If style=5  Then 
 AlterLand -.25,brushsize 
 Return
  EndIf  
If style=300 And droptimer#<MilliSecs() Then 
 droptimer#=MilliSecs()+160
 dropmodel
 Return
 EndIf
If style=301 And droptimer#<MilliSecs() Then 
droptimer#=MilliSecs()+160
  Selectmodel
  Return
  EndIf
If style=302 And droptimer#<MilliSecs()  Then 
droptimer#=MilliSecs()+160
 Deletemodel
 Return
 EndIf 
 fs#=fadespeed#
 area2=area

cp=CameraPick(Cam,MouseX(),MouseY())

If ox<>-1 Then cp=1
 If cp Then 
If ox=-1 Then 
  s=PickedSurface()
  t=PickedTriangle()
  v=TriangleVertex(s,t,1)
EndIf
If ox<>-1 Then v=l_index(ox,oz)
  fxx=-1
  ;lets locate the v vertex in our precaled l_index
  For cvx=0 To lsegments
   For cvz=0 To lsegments

    If l_index(cvx,cvz)=v Then 
     fxx=cvx
     fzz=cvz
      Exit
    EndIf

   Next
  Next

  ;if we did not find the vertex in our l_index .. return
  If fxx=-1 Then Return

  For i= numlayers To 1 Step -1
   s=GetSurface(layer(i),1)
   For skanx=-area To area
    skx=skanx+fxx
    For skanz=-area2 To area2

     skz=skanz+fzz

If area>0 Then 
     If skx>lsegments Then skx=lsegments
     If skz>lsegments Then skz=lsegments
     If skx<0 Then skx=0
     If skz<0 Then skz=0
Else
     If skx>lsegments Then skx=lsegments
     If skz>lsegments Then skz=lsegments
     If skx<0 Then skx=0
     If skz<0 Then skz=0
EndIf
     


	Select style
		Case 0;paint
			;- Paint Terrain
			v=l_index(skx,skz)
			old_al1#=VertexAlpha(s,v)
			distance# = Sqr(((fxx-skx)*(fxx-skx)) + ((fzz-skz)*(fzz-skz)))
			distance=distance/2
			If distance=<(brushsize/2) Then
				If distance#<=.5 Then distance=.5
				;fade all layers out except selected
				;fade selected back to view
				If i>selectedlayer Then  old_al1#=old_al1#-(fs#/(distance)) 
				If i<selectedlayer Then  old_al1#=old_al1#-(fs#/(distance))
				If i=selectedlayer Then   old_al1#=old_al1#+(fs#/(distance))
				If old_al1#<0 Then old_al1#=0
				If old_al1#>.9 Then old_al1#=.9

				VertexColor s,v,VertexRed(s,v),VertexGreen(s,v),VertexBlue(s,v),old_al1#
				VertexColor s,v,VertexRed(s,v),VertexGreen(s,v),VertexBlue(s,v),old_al1#

			EndIf
			
		Case 1 ;smooth up
			distance# = Sqr(((fxx-skx)*(fxx-skx)) + ((fzz-skz)*(fzz-skz)))
			distance=distance/2
			If distance=<(brushsize/2) And skx>0 And skx<(lsegments-1) And skz>0 And skz<(lsegments-1) Then
				If distance#<=.5 Then distance=.5
				v=l_index(skx,skz)
				xdir#=PickedNX()*((.1*fadespeed)*Terrainscale)
				ydir#=PickedNY()*((.1*fadespeed)*Terrainscale)
				zdir#=PickedNZ()*((.1*fadespeed)*Terrainscale)
				VertexCoords s,v,VertexX(s,v)-xdir,VertexY(s,v)-ydir,VertexZ(s,v)-zdir
			EndIf
;   flopmodels
Case 2 ;smooth down
distance# = Sqr(((fxx-skx)*(fxx-skx)) + ((fzz-skz)*(fzz-skz)))
distance=distance/2
If distance=<(brushsize/2) And skx>0 And skx<(lsegments-1) And skz>0 And skz<(lsegments-1) Then
If distance#<=.5 Then distance=.5
   v=l_index(skx,skz)
   h1#=VertexY(s,l_index(skx,skz))
   h2#=VertexY(s,l_index(skx+1,skz))
   h3#=VertexY(s,l_index(skx-1,skz))
   h4#=VertexY(s,l_index(skx,skz+1))
   h5#=VertexY(s,l_index(skx,skz-1))
   nh#=(h1+h2+h3+h4+h5)/5
   VertexCoords s,v,VertexX(s,v),nh,VertexZ(s,v)
EndIf
Case 33 ;flatten
distance# = Sqr(((fxx-skx)*(fxx-skx)) + ((fzz-skz)*(fzz-skz)))
distance=distance/2
If distance=<(brushsize/2) Then
If distance#<=.5 Then distance=.5
   v=l_index(skx,skz)
   VertexCoords s,v,VertexX(s,v),FlattenHEIGHT,VertexZ(s,v)
EndIf
 ; flopmodels
Case 3 ;pull 
distance# = Sqr(((fxx-skx)*(fxx-skx)) + ((fzz-skz)*(fzz-skz)))
distance=distance/2
If distance=<(brushsize/2) And skx>0 And skx<(lsegments-1) And skz>0 And skz<(lsegments-1) Then
If distance#<=.5 Then distance=.5
  v=l_index(skx,skz)
  v2=l_index(skx+fxx,skz+fzz)
  xdir#=PickedNX()*((.1*fadespeed)*Terrainscale)
  ydir#=PickedNY()*((.1*fadespeed)*Terrainscale)
  zdir#=PickedNZ()*((.1*fadespeed)*Terrainscale)
  VertexCoords s,v,VertexX(s,v)+xdir,VertexY(s,v)+ydir,VertexZ(s,v)+zdir
EndIf
 ; flopmodels
		Case 6;colorize
			distance# = Sqr(((fxx-skx)*(fxx-skx)) + ((fzz-skz)*(fzz-skz)))
			distance=distance/2
			If distance=<(brushsize/2) Then
			If distance#<=.5 Then distance=.5
			
			v=l_index(skx,skz)
			vcr#=VertexRed(s,v)     
			vcg#=VertexGreen(s,v)     
			vcb#=VertexBlue(s,v)     
			vca#=VertexAlpha(s,v)     
			newr#=number_fade(vcr,c_red)
			newg#=number_fade(vcg,c_green)
			newb#=number_fade(vcb,c_blue)
			If Holebrush=1 Then 	
				v1=TriangleVertex(s,t,0)
				v2=TriangleVertex(s,t,1)
				v3=TriangleVertex(s,t,2)
				VertexColor s,v1,76,10,100,vca#
				VertexColor s,v2,76,10,100,vca#
				VertexColor s,v3,76,10,100,vca#
			Else
				VertexColor s,v,newr,newg,newb,vca#
			EndIf
			EndIf
End Select

    Next
   Next
  Next

 
  EndIf
End Function



;- Create Layers
Function createlayers(lw=0,sflag=0)
 ;lets see if a saved file is stated
 ;create segments and position them barely away from each other.
 ;layer #6 being the top layer or surface layer

 ;if you have more or less layers that im using in this example you
 ;can just add or remove some texture assignments below

	DebugLog "Create new set of layers with " + numlayers + " layers"
	scaleFac# = lsegments / 64.0
	For i=1 To numlayers ;To 1 Step -1
		layer(i)=CreateLayer(lsegments) ;,0,scaleFac)
		assigntexture(i,use_tex$(i))
	Next
	For i= numlayers+1 To maxLayers ;To 1 Step -1
		layer(i)=CreateLayer(1) ;,0,scaleFac)
		assigntexture(i,use_tex$(i))
	Next
	
	For i=numlayers To 2 Step -1
		EntityFX layer(i),2+32
	Next
	EntityFX layer(1),2
	
	For i=1 To numlayers-1
		EntityParent layer(i),layer(i+1)
	Next
	
	EntityPickMode layer(1),2
	If sflag=0 Then 
		Terrainscale#=1.0
		For i=1 To 55
			Scaleterrain 1.1,1.1
		Next
	EndIf


End Function




Function SaveLandscape(sf$="",EXPORTFLAG=0, encrypt% = 1)
	ExB3D=1;fui_confirm("Include the terrain?","Yes","No")
	
	;For I=1 To numlayers
	;	DebugLog "Used Texture: " + use_tex$(I)
	;Next

	If Upper$(Right$(sf$,4))=".RCT" Or Upper$(Right$(sf$,4))=".DAT" Or Upper$(Right$(sf,4))=".B3D" Then   
		sFileName3$=Left$(sf$,Len(sf$)-4)
	Else
		sFileName3$=sf$
	EndIf

  ; so strip the path
	For i2= Len(sfilename3$) To 1 Step -1
		If Mid$(sfilename3$,i2,1)="\" Then
			sfilename3$=Right$(sfilename3$,Len(sfilename3$)-i2)
			Exit
		EndIf
	Next
	If encrypt = 1
  		part1$=sFileName3$+".eb3d"
	Else
  		part1$=sFileName3$+".b3d"
	EndIf
	AREAFILE$=SFILENAME3$
	SAVEFILE$=SFILENAME3$+".RCT" 
	DebugLog "savefile "+savefile$
	DebugLog "areafile "+areafile$
	Locate 0,200

	ChangeDir thispath$
	DebugLog "B3D WOULD GO HERE " 
	DebugLog thispath$+exportpath$+part1$
	If EXPORTFLAG=1 Then  
		If ExB3D=1 Then ;--
			For i=1 To numlayers
				If FileType(thispath$+texturepath$+use_tex$(i))=1 Then 
					CopyFile thispath$+texturepath$+use_tex$(i),thispath$+exportpath$+use_tex$(i)
				EndIf
			Next
			If FileType(thispath$+exportpath$+part1$)=1 Then DeleteFile (thispath$+exportpath$+part1$)
			WriteBB3D(thispath$+exportpath$+part1$)
		EndIf
		
		If EXPORTFLAG=1 Then thisid=addmeshtodatabase ("RCTE\"+part1$,False)
		;If thisid=-1 And exb3d=1 Then 
		If  ExB3D=1 And encrypt = 1 Then  
			Encrypt_B3D("Data\Meshes\RCTE\" +part1$,False)
		EndIf
		
		 ; so strip the path
		For i2= Len(sfilename3$) To 1 Step -1
			If Mid$(sfilename3$,i2,1)="\" Then
				sfilename3$=Right$(sfilename3$,Len(sfilename3$)-i2)
				Exit
			EndIf
		Next
	EndIf 
	
	If EXPORTFLAG=1 Then 
		If ExB3D=1 Then
		
			dm.dropmodel=New dropmodel
			dm\ModelFileName$=exportpath$+part1$
			dm\modelfilepath$=exportpath$+part1$
			dm\rcTe$=Savefile$
			DebugLog "dm\rcte$ "+dm\rcTe#
			DebugLog "Savefile "+savefile$
			
			dm\id=GetMeshID("RCTE\"+part1$)
			;RuntimeError "on export id for "+"RCTE\"+part1$+" is "+dm\id
			dm\x#=0
			dm\y#=0
			dm\z#=0
			dm\pitch=0
			dm\yaw=0
			dm\roll=0
			dm\sx=1
			dm\sy=1
			dm\sz=1
			dm\ent=layer(1)
			EntityType dm\ent,3
			If Left$(dm\rcTe$,5)="_GRSS" Then EntityType dm\ent,0
	
		EndIf
	EndIf


	If EXPORTFLAG=1 Then 
		ChangeDir thispath$
		SaveAreaRCTE areafile$
		If ExB3D=1 Then Delete Last dropmodel
	EndIf
    savework savefile$
End Function






Function bringup_topdown(x#,z#,slope#=.95,area=4,fs#=.06)
End
;This Function is called when you want to brng a layer into view.
 area2=area
 cp=LinePick(x,200,z,0,-400,0,0.0)


 If cp Then 
  s=PickedSurface()
  t=PickedTriangle()
  v=TriangleVertex(s,t,1)

  fxx=-1
  nx#=PickedNX()
  ny#=PickedNY()
  nz#=PickedNZ()

  If Abs(ny)<slope

  ;lets locate the v vertex in our precaled l_index
  For cvx=0 To lsegments
   For cvz=0 To lsegments
    If l_index(cvx,cvz)=v Then 
     fxx=cvx
     fzz=cvz
     ; Exit
    EndIf
   Next
  Next

  ;if we did not find the vertex in our l_index .. return
  If fxx=-1 Then Return

  For i= numlayers To 1 Step -1
   s=GetSurface(layer(i),1)
   For skanx= 0 To 0 ;-area To area
    skx=skanx
    For skanz= 0 To 0 ;-area2 To area2

     skz=skanz

     If (fxx+skx)>=0 And (fxx+skx)<=lsegments And (fzz+skz)>=0 And (fzz+skz)<=lsegments
      v=l_index(fxx+skx,fzz+skz)
      old_al1#=VertexAlpha(s,v)
      ;fade all layers out except selected
      ;fade selected back to view
distance# = Sqr(((fxx-skx)*(fxx-skx)) + ((fzz-skz)*(fzz-skz)))
distance=distance*2
If distance#<=0 Then distance=0

      If i>selectedlayer Then  old_al1#=old_al1#-((fs#)-distance# )
      If i<selectedlayer Then  old_al1#=old_al1#-((fs#)-distance# )
      If i=selectedlayer Then  old_al1#=old_al1#+((fs#)-distance# )
      If old_al1#<0 Then old_al1#=0
      If old_al1#>1 Then old_al1#=1
If distance#=>(brushsize/2) Then old_al1#=0
     VertexColor s,v,VertexRed(s,v),VertexGreen(s,v),VertexBlue(s,v),old_al1#
     EndIf
    Next
   Next
  Next
 
  EndIf
 EndIf
End Function

;#Region Terrain Modification Functionality
;- Terrain Modification Functionality

Function AlterLand(lmdir#,area=4)
	If mlook=1 Then Return
	
	;This Function i used to terraform land UP and DOWN
	area2=area
	cp=CameraPick(Cam,MouseX(),MouseY())
	If cp Then 
		s=PickedSurface()
		t=PickedTriangle()
		v=TriangleVertex(s,t,1)
		fxx=-1
		fzz=-1
		;lets Locate the v vertex in our precaled l_index
		For cvx=0 To lsegments
			For cvz=0 To lsegments
				If l_index(cvx,cvz)=v Then 
					fxx=cvx
					fzz=cvz
					Exit
				EndIf
			Next
		Next
		For skanx=-area To area
			skx=skanx+fxx
			For skanz=-area2 To area2
				skz=skanz+fzz
				distance# = Sqr(((fxx-skx)*(fxx-skx)) + ((fzz-skz)*(fzz-skz)))
				If skx>lsegments Then skx=lsegments
				If skz>lsegments Then skz=lsegments
				If skx<0 Then skx=0
				If skz<0 Then skz=0
				v=l_index(skx,skz)
				For i= numlayers To 1 Step -1
					s=GetSurface(layer(i),1)
					old_al1#=VertexAlpha(s,v)
					cval#=(lmdir#*(fadespeed#*(100)))-(((brushsize-distance)*-lmdir)*(Terrainscale))
					If lmdir#<0 And cval#>0 Then cval=0
					If lmdir#>0 And cval#<0 Then cval=0
					cval=cval*.1
					VertexCoords s,v,VertexX(s,v),VertexY(s,v)+cval#,VertexZ(s,v)
				Next
			Next
		Next
	EndIf
;flopmodels
End Function

;#End Region


Function savework(SF$="")
SFILENAME3$=SF$

  count=0
  For i2= Len(sfilename3$) To 1 Step -1
   count=count+1
   If Mid$(sfilename3$,i2,1)="." Then
    sfilename3$=Left$(sfilename3$,Len(sfilename3$)-count)
    Exit
    EndIf 
    Next

If sfilename3$<>""
FN$=sFileName3$+".rct"
ChangeDir thispath$+savepath$

sfile=WriteFile (SF$)
DebugLog "FINAL RCT SAVE !!!!!!!!!!!!!!!!!!!!! "+FN$
DebugLog "Number of layers: " + numlayers + " and number of segments: " + lsegments
 WriteInt sfile, numlayers
 WriteInt sfile, lsegments
  For i=1 To numlayers
   WriteString sfile,use_tex$(i)
   WriteFloat sfile,layertexscale(i)
   DebugLog "Saving tex "+use_tex$(i)
   Next 

  For i=1 To numlayers
DebugLog "rct -> starting vertex save for layer "+i
   s=GetSurface(layer(i),1)
   vc=CountVertices(s)-1
    For v=0 To vc
     WriteFloat sfile, VertexX(s,v)
     WriteFloat sfile, VertexY(s,v)
     WriteFloat sfile, VertexZ(s,v)
     WriteInt sfile, VertexRed(s,v)
     WriteInt sfile, VertexGreen(s,v)
     WriteInt sfile, VertexBlue(s,v)
     WriteFloat sfile, VertexAlpha(s,v)
   Next
  Next


;save 
DebugLog "rct -> saving scale "+i
WriteFloat sfile,Terrainscale#
;save autotexture slider values
For li=1 To numlayers
WriteInt  sfile,DTexMinSlope(li)
WriteInt  sfile, DTexMaxSlope(li)
WriteInt  sfile, DTexMinHeight(li)
WriteInt  sfile, DTexMaxHeight(li)
DebugLog "saving minslope "+DTexMinSlope(li)
DebugLog "saving maxslope "+DTexMaxSlope(li)
DebugLog "saving minheight "+DTexMinHeight(li)
DebugLog "saving maxheight "+DTexMaxHeight(li)
Next

;find out how many dropmodels there are
dmc=0
For dm.dropmodel=Each dropmodel
 Dmc=dmc+1
Next
WriteInt sfile,dmc

For dm.dropmodel=Each dropmodel
smm=smm+1
DebugLog "Saving drop model:: "+smm
WriteInt sfile,dm\id
DebugLog "model id "+dm\id
WriteFloat sfile,EntityX(dm\ent)
WriteFloat sfile,EntityY(dm\ent)
WriteFloat sfile,EntityZ(dm\ent)
WriteFloat sfile,EntityPitch(dm\ent)
WriteFloat sfile,EntityYaw(dm\ent)
WriteFloat sfile,EntityRoll(dm\ent)
;-
WriteFloat sfile,dm\sx#
WriteFloat sfile,dm\sy#
WriteFloat sfile,dm\sz#

WriteFloat sfile,dm\Alpha#
WriteString SFILE,dm\rcTe$
Next





CloseFile sfile

system_messagetimer#=MilliSecs()+1000
system_message$="Work Saved."

Else

system_messagetimer#=MilliSecs()+1000
system_message$="Work NOT Saved."
EndIf

End Function


Function LOADWORK(sf$="")
	If sf$="" 
		; New Area Loader
		importmap$ = ""
		ChangeDir thispath$
		importFile = FUI_OpenDialog( "Import RC area file", "Data\Areas", "RC Area (*.dat)|*.dat|RC Old (*.rct)|*.rct|")
		If importFile = True
			importmap$=app\currentfile
			
			If importmap$<>""
				If Instr( Lower$(importmap$), ".dat", 1 ) > 1
					For dm.dropmodel=Each dropmodel
						FreeEntity dm\ent
						Delete dm
					Next
					For au.autoundo=Each autoundo
						Delete au
					Next
					unloadtrees(False)
					loadworkpath$ = ""
					LoadAreaTE(importmap$)
				ElseIf Instr( Lower$(importmap$), ".rct", 1 ) > 1
					sf$ = strip_path (importmap$)
				Else	
					Return
				EndIf
			Else
				Return
			EndIf
		Else
			Return
		EndIf
	EndIf
	
	;Terrain Loader
	ChangeDir thispath$+"DATA\RCTE\RCTE_SAVED\"
	
	If sf$=""
		If loadworkpath$ <> "" 
			sfilename3$=Right$(loadworkpath$, Len(loadworkpath$)-5)
		Else
			RuntimeError "This zone does not use a Realm Crafter Terrain!"
		EndIf
	Else
		sfilename3$=sf$
	EndIf	
		
	If sFileName3$<>""
		FN$=sFileName3$
		;- checkme
		If layer(1) <> 0
			For i=1 To maxLayers
				FreeTexture layertexture(i)
				FreeBrush layerbrush(i)
				ClearSurface GetSurface(layer(i),1)
				FreeEntity layer(i)
			Next
			layer(1) = 0
		EndIf
		
		If sf$<>""
			
			For dm.dropmodel=Each dropmodel
				FreeEntity dm\ent
				Delete dm
			Next
			For au.autoundo=Each autoundo
				Delete au
			Next
			unloadtrees(False)
		EndIf
		
		If fn$<>"" Then 
			If FileType(fn$)=1 Then 
				;sets get segment size
				checkf=ReadFile(fn$)
				numlayers=ReadInt(checkf)
				lsegments=ReadInt(checkf)
				For i=1 To numlayers
					use_tex$(i)=ReadString(checkf)
					loadscale#(i)=ReadFloat#(checkf)
					DebugLog "loaded scale is "+loadscale#(i)
				Next
				For i = numlayers + 1 To maxLayers
					FUI_HideGadget(GUI_BOTTOMWIN_TEXTHUMB(i))
				Next
				CloseFile checkf
			EndIf
		EndIf
		createlayers(1,1)
		
		For i=1 To numlayers
			layertexscale#(i)=loadscale#(i)
			BrushTexture layerbrush(i),layertexture(i)
			ScaleTexture layertexture(i),layertexscale#(i),layertexscale#(i)
			PaintMesh layer(i),layerbrush(i)
		Next
		
		lfile=ReadFile (fn$)
		numlayers=ReadInt(lfile)
		lsegments=ReadInt(lfile)
		
		For i=1 To numlayers
			junk$=ReadString$(lfile)
			jnk#=ReadFloat(lfile)
		Next
		
		For i=1 To numlayers
			s=GetSurface(layer(i),1)
			vc=CountVertices(s)-1
			For v=0 To vc
				cx#=ReadFloat(lfile)
				cy#=ReadFloat(lfile)
				cz#=ReadFloat(lfile)
				
				cr=ReadInt(lfile)
				cg=ReadInt(lfile)
				cb=ReadInt(lfile)
				
				ca#=ReadFloat(lfile)
				
				VertexCoords s,v,cx,cy,cz
				VertexColor s,v,cr,cg,cb,ca#
				VertexCoords s,v,VertexX(s,v),VertexY(s,v),VertexZ(S,v)
				
			Next
			
			UpdateNormals layer(i)
		Next
		;load terrain scale
		Terrainscale#=ReadFloat(lfile)
		;read autotexture settings
		For li=1 To numlayers
			DTexMinSlope(li)=ReadInt(lfile)
			DTexMaxSlope(li)=ReadInt(lfile)
			DTexMinHeight(li)=ReadInt(lfile)
			DTexMaxHeight(li)=ReadInt(lfile)
		Next
		
		If sf$=""
			CloseFile lfile
		Else ; Old Area Loader
			
			numdm=ReadInt (lfile)
			DebugLog "Nums of "+numdm
			For i=1 To numdm
				allok=0
				DebugLog "LOADING ::: " +i
				thisid=ReadInt(lfile)
				goforit=1
				ChangeDir thispath$
				thismesh=getmesh(thisid)
				thismeshname$=getmeshname$(thisid)
				If thismesh=0 Then goforit=0
				
				DebugLog "model id "+thisid
				DebugLog "model filepath "+thismeshname$
				DebugLog "Go forit value "+goforit
				
				dm.dropmodel=New dropmodel
				dm\ModelFileName$=thismeshname$
				dm\id=thisid
				DebugLog "loaded id = " +dm\id
				
				If goforit=1 Then dm\ent=thismesh
				;EntityAutoFade dm\ent,100,120
				DebugLog dm\ModelFileName$
				dm\modelfilepath$=dm\ModelFileName$
				dm\x# = ReadFloat(lfile)
				dm\y# = ReadFloat(lfile)
				dm\z# = ReadFloat(lfile)
				
				dm\pitch# = ReadFloat(lfile)
				dm\yaw# = ReadFloat(lfile)
				dm\roll# = ReadFloat(lfile)
				
				dm\sx# = ReadFloat(lfile)
				dm\sy# = ReadFloat(lfile)
				dm\sz# = ReadFloat(lfile)
				
				
				dm\Alpha#=ReadFloat(lfile)
				dm\rcTe$=ReadString(LFILE)
				
				DebugLog "==========="+getmeshname$(dm\id)+"========================"
				DebugLog "XYZ "+dm\x+" "+dm\y+" "+dm\z
				DebugLog "PYR "+dm\pitch+" "+dm\yaw+" "+dm\roll
				DebugLog "SCALE "+dm\sx+" "+dm\sy+" "+dm\sz
				
				If goforit=1 Then PositionEntity dm\ent,dm\x,dm\y,dm\z
				If goforit=1 Then ScaleEntity dm\ent,dm\sx,dm\sy,dm\sz
				If goforit=1 Then RotateEntity dm\ent,dm\pitch,dm\yaw,dm\roll
				If goforit=1 Then EntityFX dm\ent,2
				
				If goforit=0 Then Delete dm
			Next
			
			CloseFile lfile
			
			;If BillBoardMode=1 Then 
			;	CameraRange gui_Cam,0.001,.001
			;	For dm.dropmodel=Each dropmodel
			;		If Left$(dm\rcTe$,5)<>"_GRSS" 
						;dm\ent=billboardentity(dm\ent,256)
			;		EndIf
			;	Next
			;	CameraRange gui_Cam,1,11
			;EndIf
		EndIf	
	EndIf
End Function



Function RC_CenterMesh (entity) 
	FitMesh entity, -(MeshWidth (entity) / 2), -(MeshHeight (entity) / 2), -(MeshDepth (entity) / 2), MeshWidth (entity), MeshHeight (entity), MeshDepth (entity) 
End Function



Function Load_height_map(sf$="")
 ; File Requester

 ; (for a loader filerequester use:)
 ;     sAFilter$ = "Blitz3D (*.B3D)" + Chr(0) + "*.B3D" + Chr(0) + "3D Studio (*.3DS)" + Chr(0) + "*.3DS" + Chr(0) + "DirectX7 (*.X)" + Chr(0) + "*.X" + Chr(0) + "All Files (*.*)" + Chr(0) + "*.*" + Chr(0)
 ;     sFileName$ = DLLGetOpenFileName$("Please select a Mesh File...","",sAFilter$,OFN_HIDEREADONLY Or OFN_FILEMUSTEXIST)
 ; for more info about the blitzsys read the blitzsys docs from the original distribution
 ;
 ;sAFilter$ = "Load heightmap (*.BMP)" + Chr(0) + "*.BMP" + Chr(0) + "Jpeg (*.JPG)" + Chr(0) + "*.JPG" +Chr(0)+"PNG (*.PNG)" + Chr(0) + "*.PNG" + Chr(0)+"All Files (*.*)" + Chr(0) + "*.*" + Chr(0)
 ;sFileName3$ = DLLGetopenFileName$("Load heightmap (*.BMP).","",sAFilter$,OFN_HIDEREADONLY Or OFN_FILEMUSTEXIST ) ;Or OFN_OVERWRITEPROMPT)
If FileType (sf$) <> 1 Then 
Return
 Else
sfilename3$=sf$
loadhmap sfilename3$,fadespeed#*200
 For i=1 To numlayers
  UpdateNormals layer(i)
 Next
 Return
 EndIf

End Function

Function smoothall()
s=GetSurface(layer(1),1)
 For sx1=0 To lsegments 
  For sz1=0 To lsegments 
   v1=l_index(sx1,sz1) 
   h1#=VertexY(s,l_index(sx1,sz1))
If sx1<>lsegments h2#=VertexY(s,l_index(sx1+1,sz1)) Else h2#= VertexY(s,l_index(sx1,sz1))
If sx1<>0 Then h3#=VertexY(s,l_index(sx1-1,sz1)) Else h3#=VertexY(s,l_index(sx1,sz1))
If sz1<>lsegments Then  h4#=VertexY(s,l_index(sx1,sz1+1)) Else h4#=VertexY(s,l_index(sx1,sz1))
If sz1<>0  Then  h5#=VertexY(s,l_index(sx1,sz1-1)) Else h5#=VertexY(s,l_index(sx1,sz1))
  nh#=(h1+h2+h3+h4+h5)/5
 For i=1 To numlayers
  s2=GetSurface(layer(i),1)
  VertexCoords s2,v1,VertexX(s2,v1),nh#,VertexZ(s2,v1)
  VertexCoords s2,v2,VertexX(s2,v2),nh#,VertexZ(s2,v2)
  VertexCoords s2,v3,VertexX(s2,v3),nh#,VertexZ(s2,v3)
  Next
Next
Next

For i=1 To numlayers
centermesh layer(i)
Next

End Function

Function smoothalpha()

 For sx1=1 To lsegments-1 
  For sz1=1 To lsegments-1 
   v1=l_index(sx1,sz1) 
   s=GetSurface(layer(selectedlayer),1)
   h1#=VertexAlpha(s,l_index(sx1,sz1))
   h2#=VertexAlpha(s,l_index(sx1+1,sz1))
   h3#=VertexAlpha(s,l_index(sx1-1,sz1))
   h4#=VertexAlpha(s,l_index(sx1,sz1+1))
   h5#=VertexAlpha(s,l_index(sx1,sz1-1))

   h6#=VertexAlpha(s,l_index(sx1+1,sz1+1))
   h7#=VertexAlpha(s,l_index(sx1-1,sz1-1))
   h8#=VertexAlpha(s,l_index(sx1-1,sz1+1))
   h9#=VertexAlpha(s,l_index(sx1+1,sz1-1))




   nh#=(h1+h2+h3+h4+h5+h6+h7+h8+h9)/9
; For i=1 To numlayers
 ; s2=GetSurface(layer(i),1)
  VertexColor s,v1,VertexRed(s,v1),VertexGreen(s,v1),VertexBlue(s,v1),nh#
Next
Next
End Function


Function Load_new_tex(sf$)
 ; File Requester

 ; (for a loader filerequester use:)
 ;     sAFilter$ = "Blitz3D (*.B3D)" + Chr(0) + "*.B3D" + Chr(0) + "3D Studio (*.3DS)" + Chr(0) + "*.3DS" + Chr(0) + "DirectX7 (*.X)" + Chr(0) + "*.X" + Chr(0) + "All Files (*.*)" + Chr(0) + "*.*" + Chr(0)
 ;     sFileName$ = DLLGetOpenFileName$("Please select a Mesh File...","",sAFilter$,OFN_HIDEREADONLY Or OFN_FILEMUSTEXIST)
 ; for more info about the blitzsys read the blitzsys docs from the original distribution
 ;
 ;sAFilter$ = "Load/Replace Texture (*.BMP)" + Chr(0) + "*.BMP" + Chr(0) + "*.JPG" + Chr(0) + "*.JPG" +Chr(0)+"*.PNG" + Chr(0) + "*.PNG" + Chr(0)+"All Files (*.*)" + Chr(0) + "*.*" + Chr(0)
 ;sFileName3$ = DLLGetopenFileName$("Load/Replace Texture (*.BMP).","",sAFilter$,OFN_HIDEREADONLY Or OFN_FILEMUSTEXIST ) ;Or OFN_OVERWRITEPROMPT)
sfilename3$=sf$
 If sFileName3$<>""
;strip path
     ; so strip the path
  For i2= Len(sfilename3$) To 1 Step -1
   If Mid$(sfilename3$,i2,1)="\" Then
    sfilename3$=Right$(sfilename3$,Len(sfilename3$)-i2)
    Exit
   EndIf
  Next
If FileType(thispath$+texturepath$+sfilename3$)<>1 Then 
system_messagetimer#=MilliSecs()+1000
system_message$="Texture not in Texture Dir."
Return
EndIf

 ;loadmap just applies a heightmap to the layers

   use_tex$(selectedlayer)=Lower(sfilename3$)
   FreeBrush layerbrush(selectedlayer)
	FreeTexture Int use_tex$(selectedlayer)
   assigntexture(selectedlayer,sfilename3$)
 Else
 Return
 EndIf
End Function


Function Load_model()

Mc_pitch=0
mc_yaw=0
mc_roll=0
Mc_sx=1
mc_sy=1
mc_sz=1



End Function

;- ChooseNewMap
Function ChooseNewMap()
	TWidth = 250
	THeight = 125 ;230
 	GUI_CONTROLSWINDOW = FUI_Window( 0, 0, TWidth, THeight, "New Map", 0, 1, 1 )
		GUI_CONTROLSWINDOW_LABEL1 = FUI_Label( GUI_CONTROLSWINDOW, 10, 10, "What type of terrain would you like to create?" )
   	    GUI_CONTROLSWINDOW_LABEL2 = FUI_Label( GUI_CONTROLSWINDOW, 10, 70, "")
		GUI_CONTROLSWINDOW_BUTTON_SMALL = FUI_Button( GUI_CONTROLSWINDOW, 10, 35, 230, 20, "Small High Detail Terrain (32x32)" )	
		GUI_CONTROLSWINDOW_BUTTON_NORMAL = FUI_Button( GUI_CONTROLSWINDOW, 10, 70, 230, 20, "Normal High Detail Terrain (64x64)" )		
;		GUI_CONTROLSWINDOW_BUTTON_5TEXTURE = FUI_Button( GUI_CONTROLSWINDOW, 10, 105, 230, 20, "5 Texture, Larger Terrain (84x84)" )
;		GUI_CONTROLSWINDOW_BUTTON_4TEXTURE = FUI_Button( GUI_CONTROLSWINDOW, 10, 140, 230, 20, "4 Texture, Larger Terrain (94x94)" )
;		GUI_CONTROLSWINDOW_BUTTON_3TEXTURE = FUI_Button( GUI_CONTROLSWINDOW, 10, 175, 230, 20, "3 Texture, Larger Terrain (108x108)" )
	FUI_ModalWindow( GUI_CONTROLSWINDOW )
	FUI_CenterWindow( GUI_CONTROLSWINDOW )
	TClose = False
	Repeat
		 For e.Event = Each Event
		 	Select e\EventID
		 		Case GUI_CONTROLSWINDOW
		 			Select e\EventData
		 				Case "Closed"
		 					TClose = True
                            Return  -1
		 			End Select
		 		Case GUI_CONTROLSWINDOW_BUTTON_SMALL
		 			tClose = True
					FUI_DeleteGadget( GUI_CONTROLSWINDOW )
                    Return  0
		 		Case GUI_CONTROLSWINDOW_BUTTON_NORMAL
		 			tClose = True
					FUI_DeleteGadget( GUI_CONTROLSWINDOW )
                    Return  1
		 		Case GUI_CONTROLSWINDOW_BUTTON_5TEXTURE
		 			tClose = True
					FUI_DeleteGadget( GUI_CONTROLSWINDOW )
                    Return  2
		 		Case GUI_CONTROLSWINDOW_BUTTON_4TEXTURE
		 			tClose = True
					FUI_DeleteGadget( GUI_CONTROLSWINDOW )
                    Return  3
		 		Case GUI_CONTROLSWINDOW_BUTTON_3TEXTURE
		 			tClose = True
					FUI_DeleteGadget( GUI_CONTROLSWINDOW )
                    Return  4
					
		 	End Select
		 	Delete e
		 Next
		 If KeyHit( 1 ) Then TClose = True
		 ;#Region Rendering	
			CameraProjMode( App\Cam, False )
			CameraProjMode( Cam, True )
			RenderWorld( )
			CameraProjMode( Cam, False )
			CameraProjMode( App\Cam, True )
			FUI_Update( )
			RenderWorld( )
			Flip( GFX_VARIABLE_VSYNC )
		;#End Region
		If TClose = True Then
			Exit
		EndIf
	Forever
	FUI_DeleteGadget( GUI_CONTROLSWINDOW )
	FlushKeys( )
	FlushMouse( )
End Function

Function newmap(sg%,flagit=0,ask=1)
	If ask=1 Then 
		yn = Fui_Confirm("New Map?","Yes","No")
	Else
		yn = 1
	EndIf
	
	If yn Then 
		EntityAlpha Boxmesh,0
		ChangeDir thispath$
		totaldrops=0
		
		For dm.dropmodel=Each dropmodel
			FreeEntity dm\ent
			Delete dm
		Next
		
		For au.autoundo=Each autoundo
			Delete au
		Next
		
		UNLOADAREA()
		If flagit=0 And layer(1) <> 0
			For i=1 To maxLayers
				FreeTexture layertexture(i)
				FreeBrush layerbrush(i)
				ClearSurface GetSurface(layer(i),1)
				FreeEntity layer(i)
			Next
			layer(1) = 0
		EndIf
		
		lsegments=sg
		createlayers()
		PositionEntity Cam,10,20,0
		EntityPickMode layer(1),2
	
	EndIf
End Function



Function UpdateNormalsA(mesh)
For s=1 To CountSurfaces(mesh) 
surf=GetSurface(mesh,s) 
For v=0 To CountVertices(surf)-1 

nx#=0.0 
ny#=0.0 
nz#=0.0 
triscount=0 

For t=0 To CountTriangles(surf)-1 

a=TriangleVertex(surf,t,0) 
b=TriangleVertex(surf,t,1) 
c=TriangleVertex(surf,t,2) 
If a=v Or b=v Or c=v 

Ax#=VertexX(surf,a) 
Ay#=VertexY(surf,a) 
Az#=VertexZ(surf,a) 
TFormPoint Ax,Ay,Az,mesh,0 
Ax=TFormedX() 
Ay=TFormedY() 
Az=TFormedZ() 

Bx#=VertexX(surf,B) 
By#=VertexY(surf,B) 
Bz#=VertexZ(surf,B) 
TFormPoint Bx,By,Bz,mesh,0 
Bx=TFormedX() 
By=TFormedY() 
Bz=TFormedZ() 

Cx#=VertexX(surf,C) 
Cy#=VertexY(surf,C) 
Cz#=VertexZ(surf,C) 
TFormPoint Cx,Cy,Cz,mesh,0 
Cx=TFormedX() 
Cy=TFormedY() 
Cz=TFormedZ() 

ux#=Bx-Ax 
uy#=By-Ay 
uz#=Bz-Az 
vx#=Cx-Ax 
vy#=Cy-Ay 
vz#=Cz-Az 

x#=(uy*vz)-(uz*vy) 
y#=(uz*vx)-(ux*vz) 
z#=(ux*vy)-(uy*vx) 

nx=nx+x 
ny=ny+y 
nz=nz+z 
triscount=triscount+1 

EndIf 

Next 

l#=Sqr(nx^2+ny^2+nz^2) 
nx=nx/l 
ny=ny/l 
nz=nz/l 
TFormVector nx,ny,nz,0,mesh 
VertexNormal surf,v,TFormedX(),TFormedY(),TFormedZ() 

Next 
Next 
End Function 





Function number_fade(fromn,ton)
If fromn<ton Then fromn=fromn+(25*fadespeed)
If fromn>ton Then fromn=fromn-(25*fadespeed)
Return fromn
End Function 




Function WriteBB3D( f_name$)
Delete Each opvert
;pre optimization here for holes
	For i = numlayers To 1 Step -1
		optimizedLayer(i) = optimizelayer(i)
	Next

	file=WriteFile( f_name$ )

	b3dSetFile( file )
	
	b3dBeginChunk( "BB3D" )
		b3dWriteInt( 1 )	;version

		b3dBeginChunk( "TEXS" ) ; list all textures used by the mesh

	For i=1 To numlayers

			c_s= GetSurface(layer(i),1)
			c_b=GetSurfaceBrush( c_s )
			c_t=GetBrushTexture( c_b )
			c_tn$=Lower$(TextureName$( c_t)); this returns the full path!
			; so strip the path
			If c_tn$<>""
				For i2= Len(c_tn$) To 1 Step -1
					If Mid$(c_tn$,i2,1)="\" Then
						c_tn$=Right$(c_tn$,Len(c_tn$)-i2)
						Exit
					EndIf
				Next
			EndIf
			;- Export Write Fix for color and texture index
			utex$=c_tn
			b3dWriteString( utex$) 	;texture file
			b3dWriteInt( 1 )					;flags
			b3dWriteInt( 2)        			    ;blend 2
			b3dWriteFloat( 0 )					;x in tex 0 (hu?)
			b3dWriteFloat( 0 )					;y in tex 0
			b3dWriteFloat(1.0/ layertexscale(i))					;x scale 1
			b3dWriteFloat(1.0/ layertexscale(i))					;y scale 1
			b3dWriteFloat( 0 )					;rotation 0

	Next 
	b3dEndChunk()	;end of tex

	
		   
		    b3dBeginChunk( "BRUS" )
			b3dWriteInt( 1 )					;0 textures per brush

	For i=1 To  numlayers
	
			b3dWriteString( "brush_"+(i-missed) )		;name
			b3dWriteFloat( 0 )					;red     !! fixed from 0
			b3dWriteFloat( 0 )					;green     !! fixed from 0
			b3dWriteFloat( 0 )					;blue     !! fixed from 0
			b3dWriteFloat( 1 )					;alpha
			b3dWriteFloat( 0 )				;shininess

			If i=1 Then 
				b3dWriteInt( 1 )				;blend
				b3dWriteInt( 2)					;FX
			Else
				b3dWriteInt( 1 )					;blend
				b3dWriteInt( 34 )					;FX
			EndIf
 			b3dWriteInt( i-1)	;used texture index ?
			
	Next
	b3dEndChunk()	;end of BRUS chunk

	layercnt = 0
	For i=numlayers To 1 Step -1
		thismesh = optimizedLayer(i); optimizelayer(i)
		If thismesh <> 0
			missed = 0
			For k = 2 To i-1
				If optimizedLayer(k) = 0 Then missed = missed + 1
			Next
			DebugLog("Write Layer("+(i-missed)+") to the mesh")
			b3dBeginChunk( "NODE" )
			b3dWriteString( "layer"+(i-missed))
			b3dWriteFloat( 0 )	;x_pos
			b3dWriteFloat( 0 )	;y_pos
			b3dWriteFloat( 0 )	;y_pos
			b3dWriteFloat( 1 )	;x_scale
			b3dWriteFloat( 1 )	;y_scale
			b3dWriteFloat( 1 )	;z_scale
			b3dWriteFloat( 1 )	;rot_w
			b3dWriteFloat( 0 )	;rot_x
			b3dWriteFloat( 0 )	;rot_y
			b3dWriteFloat( 0 )	;rot_z
			b3dBeginChunk( "MESH" )
			DebugLog( "Segments: " + lsegments + " and layer("+i+") is " + layer(i) )
;				If lsegments > 64
;					thismesh = layer(i)
;				Else
;					thismesh = optimizelayer(i)
;				EndIf
			WriteMESH thismesh,i
			s1=GetSurface(thismesh,1)
			tbrush=GetSurfaceBrush(s1)
			FreeBrush tbrush
			ClearSurface s1
			FreeEntity thismesh
			layercnt = layercnt + 1
			b3dEndChunk()	;end of MESH
		EndIf
Next

For i= layercnt To 1 Step -1
 		    b3dEndChunk()	;end of NODE chunk
Next
;

	b3dEndChunk()	;end of BB3D chunk
	
	CloseFile file
End Function

Function WriteMESH(omesh,mi)
	
;	b3dBeginChunk( "MESH" )

		b3dWriteInt(-1); GetSurfaceBrush(GetSurface(mesh,1)) )				;no 'entity' brush
;For mi=starter To ender Step -1

	n_surfs=CountSurfaces( omesh)

		b3dBeginChunk( "VRTS" )
			b3dWriteInt( 2 )			;flags - 0=no normal/color
			b3dWriteInt( 1 )			;0 tex_coord sets
			b3dWriteInt( 2 )			;0 coords per set
			
;			For k=1 To n_surfs
				surf=GetSurface( omesh,1 )
				n_verts=CountVertices( surf )-1
				
				For j=0 To n_verts
					b3dWriteFloat( VertexX( surf,j ) )
					b3dWriteFloat( VertexY( surf,j ))
					b3dWriteFloat( VertexZ( surf,j ) )
					
					b3dWriteFloat( VertexRed(surf,j)/255)
					b3dWriteFloat( VertexGreen(surf,j)/255)
					b3dWriteFloat( VertexBlue(surf,j)/255)

					b3dWriteFloat( VertexAlpha( surf,j))

					
					b3dWriteFloat( VertexU#( surf,j,0 ) )
					b3dWriteFloat( VertexV#( surf,j,0 ) )

									Next
;			Next
		b3dEndChunk()	;end of VRTS chunk
		
		first_vert=0
;		For k=1 To n_surfs
			surf=GetSurface( omesh,1 )
			n_tris=CountTriangles( surf )-1
			
			b3dBeginChunk( "TRIS" )

				b3dWriteInt(mi-1)		;brush for these triangles
				
				For j=0 To n_tris
					b3dWriteInt( first_vert+TriangleVertex( surf,j,0 ) )
					b3dWriteInt( first_vert+TriangleVertex( surf,j,1 ) )
					b3dWriteInt( first_vert+TriangleVertex( surf,j,2 ) )
				Next
				
			b3dEndChunk()	;end of TRIS chunk
			
			first_vert=first_vert+CountVertices( surf )
			
	;Next mi
		
;	b3dEndChunk()	;end of MESH chunk
	
End Function

Function optimizelayer(ch)
;
	For I=1 To numlayers
		OS=GetSurface(layer(I),1)
		NV=CountTriangles(OS)-1
		For OV=0 To NV
			v_cr1=VertexRed(Os,TriangleVertex(os,oV,0))
			v_cg1=VertexGreen(Os,TriangleVertex(os,oV,0))
			v_cb1=VertexBlue(Os,TriangleVertex(os,oV,0))
			
			v_cr2=VertexRed(Os,TriangleVertex(os,oV,1))
			v_cg2=VertexGreen(Os,TriangleVertex(os,oV,1))
			v_cb2=VertexBlue(Os,TriangleVertex(os,oV,1))
			
			v_cr3=VertexRed(Os,TriangleVertex(os,oV,2))
			v_cg3=VertexGreen(Os,TriangleVertex(os,oV,2))
			v_cb3=VertexBlue(Os,TriangleVertex(os,oV,2))

			; We found a HOLE!
			If v_cr1=76  And v_cr2=76 And v_cr3=76 And v_cb1=100 And v_cb2=100 And v_cb3=100 And v_cg1=10 And v_cg2=10 And v_cg3=10 Then 
				If check_opvert_dupe(i,TriangleVertex(os,ov,0))=-1 Then 
					;ok its not in list.. add it
;					For ii=1 To numlayers
;						add_opvert ii,TriangleVertex(os,ov,0)
;						add_opvert ii,TriangleVertex(os,ov,1)
;						add_opvert ii,TriangleVertex(os,ov,2)
;					Next
						add_opvert ch,TriangleVertex(os,ov,0)
						add_opvert ch,TriangleVertex(os,ov,1)
						add_opvert ch,TriangleVertex(os,ov,2)
					;ADD CURRENT LAYER VERTS TO LIST       
				EndIf
			EndIf
		Next
	Next
	;lets go through all layers "above this one" and check for alpha=1
	If ch<>1  Then ; and ch <> numlayers then
		For I=ch+1 To numlayers
			OS=GetSurface(layer(I),1)
			NV=CountTriangles(OS)-1
			For OV=0 To NV
				v_a00#=VertexAlpha(Os,TriangleVertex(os,oV,0))
				v_a11#=VertexAlpha(Os,TriangleVertex(os,oV,1))
				v_a22#=VertexAlpha(Os,TriangleVertex(os,oV,2))
				If V_A00#=>.99 And V_A11#=>.99 And V_A22#=>.99 Then  ; V_A00#=>.9 And V_A11#=>.9 And V_A22#=>.9 Then  
					;MAKE SURE IT DOESNT ALREADY EXIST
					If check_opvert_dupe(ch,TriangleVertex(os,ov,0))=-1 Then 
						;ok its not in list.. add it
						add_opvert ch,TriangleVertex(os,ov,0)
						;ADD CURRENT LAYER VERTS TO LIST       
					EndIf
				EndIf
			Next
		Next
	EndIf
	
	landscape2=CreateMesh()
	child=layer(ch)
	;  For su=1 To CountSurfaces(child)
	surf=GetSurface(child,1)
	brush=GetSurfaceBrush(surf)   
	surf2=CreateSurface(landscape2,brush)
	

	numVertices = 0
	For tri=0 To CountTriangles(surf)-1  
	
		;;;;;;;;;
		v_r1#=VertexRed(surf,TriangleVertex(Surf,tri,0) )
		v_g1#=VertexGreen(surf,TriangleVertex(Surf,tri,0)) 
		v_b1#=VertexBlue(surf,TriangleVertex(Surf,tri,0) )   
		
		v_r2#=VertexRed(surf,TriangleVertex(Surf,tri,1) )
		v_g2#=VertexGreen(surf,TriangleVertex(Surf,tri,1)) 
		v_b2#=VertexBlue(surf,TriangleVertex(Surf,tri,1) )  
		
		v_r3#=VertexRed(surf,TriangleVertex(Surf,tri,2) )
		v_g3#=VertexGreen(surf,TriangleVertex(Surf,tri,2)) 
		v_b3#=VertexBlue(surf,TriangleVertex(Surf,tri,2) )
		;;;;;;;;;;;
		
		
		v_x0#=VertexX(surf,TriangleVertex(surf,tri,0)) 
		v_x1#=VertexX(surf,TriangleVertex(surf,tri,1))
		v_x2#=VertexX(surf,TriangleVertex(surf,tri,2))
		
		v_y0#=VertexY(surf,TriangleVertex(surf,tri,0))
		v_y1#=VertexY(surf,TriangleVertex(surf,tri,1))
		v_y2#=VertexY(surf,TriangleVertex(surf,tri,2))
		
		v_z0#=VertexZ(surf,TriangleVertex(surf,tri,0))
		v_z1#=VertexZ(surf,TriangleVertex(surf,tri,1))
		v_z2#=VertexZ(surf,TriangleVertex(surf,tri,2))
		
		v_u0#=VertexU(surf,TriangleVertex(surf,tri,0))
		v_u1#=VertexU(surf,TriangleVertex(surf,tri,1))
		v_u2#=VertexU(surf,TriangleVertex(surf,tri,2))
		
		v_v0#=VertexV(surf,TriangleVertex(surf,tri,0))
		v_v1#=VertexV(surf,TriangleVertex(surf,tri,1))
		v_v2#=VertexV(surf,TriangleVertex(surf,tri,2))
		
		v_a0#=VertexAlpha(surf,TriangleVertex(surf,tri,0))
		v_a1#=VertexAlpha(surf,TriangleVertex(surf,tri,1))
		v_a2#=VertexAlpha(surf,TriangleVertex(surf,tri,2))
	
			 
		If ch=1 Then 
			v_a0#=1.0
			v_a1#=1.0
			v_a2#=1.0
		EndIf
		
		If v_a0<>0 Or v_a1<>0 Or v_a2<>0 And check_opvert_dupe(ch,TriangleVertex(surf,tri,0))=-1 Then 
			v0=AddVertex(surf2,v_x0,v_y0,v_z0,v_u0,v_v0)
			v1=AddVertex(surf2,v_x1,v_y1,v_z1,v_u1,v_v1)
			v2=AddVertex(surf2,v_x2,v_y2,v_z2,v_u2,v_v2)
			AddTriangle(surf2,v0,v1,v2)
			
			VertexColor surf2, v0,v_r1,v_g1,v_b1,v_a0
			VertexColor surf2, v1,v_r2,v_g2,v_b2,v_a1
			VertexColor surf2, v2,v_r3,v_g3,v_b3,v_a2
			numVertices = numVertices + 3
		EndIf
	Next
           
	If numVertices = 0
		Delete Each opvert
		FreeBrush brush
		FreeEntity landscape2
		Return 0
	EndIf

 	weld(landscape2)

	;ScaleMesh landscape2,lscale,lscale,lscale
	UpdateNormals landscape2
	Delete Each opvert
	Return landscape2
 End Function

Function autotexture()
 yn=fui_confirm("Autotexture ?","Yes","No")
 If yn<>1 Then Return
 Createbackup
;reset terrain alphas
For i=2 To numlayers
 s=GetSurface (layer(i),1)
 For v=0 To CountVertices(s)-1
   VertexColor s,v,VertexRed(s,v),VertexGreen(s,v),VertexBlue(s,v),0
  Next
UpdateNormals layer(i)
Next
;updatemeshes()


oldbsize=brushsize
old_selectedlayer=selectedlayer
oldfade#=fadespeed
;first find highest and lowest vertecis
;next get max and min height of terrain
minh#=100000000
maxh#=-10000000
s=GetSurface(layer(1),1)
 For I=0 To CountVertices(s)-1
  If VertexY(s,i)<minh# Then minh#=VertexY(s,i)
  If VertexY(s,i)>maxh# Then maxh#=VertexY(s,i)
  Next
hrange#=(maxh#-minh#)
hstep#=(hrange#*.01)

DebugLog("Measures - MinH: " + minh + " and MaxH: " + maxh + " and Step: " + hstep + " for range: " + hrange )
For i=2 To numlayers 

H1#=minh+(HStep#*(DTexMinHeight(i))-1)
H2#=minh+(hstep#*DTexMaxHeight(i))

DebugLog( "Autotexture Layer("+i+") with heightrange: ("+H1+","+H2+")")
;ok get slope values
;0=flat  and 1=angled
Minslope#=DTexMinSlope(i)*.01
Maxslope#=DTexMaxSlope(i)*.01

selectedlayer=i
s=GetSurface(layer(i),1)
For ax=1 To lsegments 
For az=1 To lsegments 
aaa=l_index(ax,az)
aaa1=l_index(ax-1,az)
aaa2=l_index(ax,az-1)
aaa3=l_index(ax-1,az-1)


;vx# = VertexX(S,aaa)
;vy# = VertexY(S,aaa)
;vz# = VertexZ(S,aaa)
;
;tx1# = vx - VertexX(S,aaa1)
;ty1# = vy - VertexY(S,aaa1)
;tz1# = vz - VertexZ(S,aaa1)
;tx2# = vx - VertexX(S,aaa2)
;ty2# = vy - VertexY(S,aaa2)
;tz2# = vz - VertexZ(S,aaa2)
;
;nx1# = ty1 * tz2 - tz1 * ty2
;ny1# = tz1 * tx2 - tx1 * tz2
;nz1# = tx1 * ty2 - ty1 * tx2
;length# = Sqr(nx1*nx1 + ny1*ny1 + nz1*nz1)
;If length = 0 Then length = 1
;nx1 = nx1 / length
;ny1 = ny1 / length
;nz1 = nz1 / length
;
;vx# = VertexX(S,aaa3)
;vy# = VertexY(S,aaa3)
;vz# = VertexZ(S,aaa3)
;
;tx1# = vx - VertexX(S,aaa2)
;ty1# = vy - VertexY(S,aaa2)
;tz1# = vz - VertexZ(S,aaa2)
;tx2# = vx - VertexX(S,aaa1)
;ty2# = vy - VertexY(S,aaa1)
;tz2# = vz - VertexZ(S,aaa1)
;
;nx2# = ty1 * tz2 - tz1 * ty2
;ny2# = tz1 * tx2 - tx1 * tz2
;nz2# = tx1 * ty2 - ty1 * tx2
;length# = Sqr(nx2*nx2 + ny2*ny2 + nz2*nz2)
;If length = 0 Then length = 1
;nx2 = nx2 / length
;ny2 = ny2 / length
;nz2 = nz2 / length

;vny1# = Sqr(1 - nx1*nx1 - nz1*nz1) ;( nx1 + nz1 ) *.707
;vny2# = Sqr(1 - nx2*nx2 - nz2*nz2);( nx2 + nz2 ) * .707

;slopeval#=1-Abs((ny1#+ny2#)*.5) ;/90.0
;DebugLog(" Slopeval : " + slopeval + " at normals: (" + ny1 + "," + ny2 + ")" )

vny1#=(VertexNZ(S,aaa)+VertexNX(S,aaa))*.6
vny2#=(VertexNZ(S,aaa1)+VertexNX(S,aaa1))*.6
vny3#=(VertexNZ(S,aaa2)+VertexNX(S,aaa2))*.6
vny4#=(VertexNZ(S,aaa3)+VertexNX(S,aaa3))*.6
slopeval#=Abs((vny1#+vny2#+vny3#+vny4)*.35)
;DebugLog(" Slopeval : " + slopeval )



vrty# = (VertexY(S,aaa) + VertexY(S,aaa1) + VertexY(S,aaa2) + VertexY(S,aaa3)) * 0.25
If H1 > H2 Or MinSlope > MaxSlope Then Goto continue

;Slopeval#=dp#;Abs(VertexNZ(S,AAA)*1.1)
If slopeval#=<0 Then slopeval=0.1
 If vrty#=>h1# And vrty#=<h2# Then 
 If minslope#<=slopeval# And maxslope#>=slopeval# Then
  bringup 1,0,ax,az

  EndIf
EndIf

.continue
Next
Next
  Next

selectedlayer=old_selectedlayer
fadespeed#=oldfade#
End Function

Function add_opvert(layer,V)
 opvert.opvert=New opvert
  opvert\layer=layer
  opvert\v=V
End Function

Function check_opvert_dupe(layer,V)
 For opvert.opvert=Each opvert
   If opvert\layer=layer And opvert\v=V Then 
   Return 1
   EndIf
   Next
   Return -1
End Function



Function dropmodel(hx#=0.001,hz#=0.001,undoit=0,mode=0)
If totaldrops>3500 Then 
    sysmsg$="You have maximum objects in the area, you cannot drop any more." 
    sysmsgtimer#=MilliSecs()+5000
	Return
	EndIf
If mlook= 1 Then Return
EntityPickMode layer(1),2
ChangeDir thispath$
If Mc_onoff=1 Then 
thismodel=getmesh(mc_id)
Else
thismodel=getmesh(sceneryID)
EndIf
;EntityAutoFade thismodel,100,120
If thismodel=0 Then 
FreeEntity thismodel
Return
EndIf
If hx#=0.001 And hz#=0.001 Then 
 CameraPick Cam,MouseX(),MouseY()
Else
LinePick hx,10000,hz,0,-20000,hz
EndIf

s=PickedSurface()
tri=PickedTriangle()
v=TriangleVertex(s,tri,1)
col=(VertexRed(s,v))
dpx#=PickedX()
dpy#=PickedY()
dpz#=PickedZ()

If s Then 

PositionEntity thismodel,dpx#,dpy#,dpz#
; EntityColor thismodel,col,col,col
;ok just make an instance of this model in our list
dm.dropmodel=New dropmodel
dm\ent=thismodel
totaldrops=totaldrops+1
 EntityFX dm\ent,2
 For ccount=1 To CountChildren(dm\ent)
  If EntityClass (GetChild(dm\ent,ccount))="Mesh" Then  EntityFX GetChild(dm\ent,ccount),2
 Next

dm\Alpha#=1
selected_dropmodel=dm\ent


If undoit=1 Then 
au.autoundo=New autoundo
au\ent=thismodel
EndIf
 
dm\ModelFileName$=sceneryname$
dm\modelfilepath$=sceneryname$
dm\id=sceneryID
dm\x#=dpx#
dm\y#=dpy#
dm\z#=dpz#
dm\pitch=EntityPitch(thismodel)
dm\yaw=EntityYaw(thismodel)
dm\roll=EntityRoll(thismodel)
If Mc_onoff=1 Then 
 dm\rcTe$=mc_rcte$
 dm\id=mc_id
 dm\sx=Mc_sx
 dm\sy=mc_sy
 dm\sz=mc_sz
 dm\pitch=Mc_pitch
 dm\yaw=mc_yaw
 dm\roll=mc_roll
 RotateEntity thismodel,Mc_pitch,mc_yaw,mc_roll
 ScaleEntity thismodel,Mc_sx,mc_sy,mc_sz
Else
dm\sx= LoadedMeshScales#(dm\id)
dm\sy= LoadedMeshScales#(dm\id)
dm\sz= LoadedMeshScales#(dm\id)
If  Instr(Lower$(sceneryname$),"rctree")=0  And Instr(Lower$(sceneryname$),"rcrock")=0 Then 
dm\sx#=dm\sx*.05
dm\sy#=dm\sy*.05
dm\sz#=dm\sz*.05
Else
EndIf
ScaleEntity thismodel,dm\sx,dm\sy,dm\sz
;m\ent=lod_model(dm)
EndIf



Else
FreeEntity thismodel
Return -1
EndIf
DebugLog "x "+dm\x +" y "+dm\y+" z "+dm\z
DebugLog "pitch "+dm\pitch +" yaw "+dm\yaw+" roll "+dm\roll
DebugLog "sx "+dm\sx +" sy "+dm\sy +" sz" +dm\sz

	If BillBoardMode=1 Then 
	CameraRange gui_Cam,0.001,.001
		 ;dm\ent=billboardentity(dm\ent,256)
	CameraRange gui_Cam,1,11
	EndIf


updatedropmodels()
End Function


Function updatedropmodels()
For dm.dropmodel=Each dropmodel
 If dm\ent = selected_dropmodel Then 
   EntityAlpha Boxmesh,.6

   If dm\permapick=1 Then 
   FitEntity Boxmesh,dm\ent,1
   Else
   FitEntity Boxmesh,dm\ent,0
   EndIf
  Else 
 EndIf 
Next
End Function

Function Scaleterrain(tsdir#,flagit=0)
If tsdir#>1 And Terrainscale=>85 Then Return ;was 85
If tsdir#<1 And Terrainscale=56 Then Return
If tsdir#>1 Then Terrainscale=Terrainscale+1 Else Terrainscale=Terrainscale-1


For i=numlayers To 1 Step -1
ScaleMesh layer(i),tsdir#,tsdir#,tsdir#
Next


For dm.dropmodel=Each dropmodel

dm\sx#=dm\sx#*tsdir
dm\sy#=dm\sy#*tsdir
dm\sz#=dm\sz#*tsdir

ScaleEntity dm\ent,dm\sx,dm\sy,dm\sz


dm\x=EntityX(dm\ent,1)
dm\y=EntityY(dm\ent,1)
dm\z=EntityZ(dm\ent,1)

dm\x=dm\x*tsdir
dm\y=dm\y*tsdir
dm\z=dm\z*tsdir


PositionEntity dm\ent,dm\x,dm\y,dm\z

Next

End Function 



Function Selectmodel()

If FUI_SendMessage(GUI_MODEMODEL_MODELPICK,M_GETCHECKED) = True Then
 For dm.dropmodel=Each dropmodel
    	EntityPickMode  dm\ent,2 
	    For ii=1 To CountChildren(dm\ent)
	    If EntityClass (GetChild(dm\ent,ii))="Mesh" Then EntityPickMode  GetChild(dm\ent,ii),2
        Next
    Next
 EndIf

 CameraPick Cam,MouseX(),MouseY()

  For dm.dropmodel=Each dropmodel
  If dm\permapick=0 Then EntityPickMode  dm\ent,0 
  Next


 For dm.dropmodel=Each dropmodel
 If PickedEntity()=dm\ent Then 
 If dm\permapick=0 Then 
 selected_dropmodel=dm\ent 
 updatedropmodels()
 Return 1
 EndIf 
 EndIf

For i=1 To CountChildren (dm\ent)
 If PickedEntity()=GetChild(dm\ent,i) Then 
  For rt.tree=Each tree
    If rt\mainent=dm\ent Then 
      selected_dropmodel=dm\ent 
      updatedropmodels()
;      sceneryid=dm\id
      Mc_onoff=0
    Return 1
    EndIf 
   Next
  EndIf

Next
Next
Return 0
End Function 

Function Deletemodel(id=-1)

If id <>-1 Then 
  For dm.dropmodel=Each dropmodel
   If selected_dropmodel=dm\ent Then 
         FreeEntity dm\ent
         EntityAlpha Boxmesh,0
         Delete dm
         totaldrops=totaldrops-1
         Return 
         EndIf
     Next
EndIf
           
 For dm.dropmodel=Each dropmodel
  EntityPickMode  dm\ent,2
 Next

 CameraPick Cam,MouseX(),MouseY()
 For dm.dropmodel=Each dropmodel
 If dm\permapick=0 Then EntityPickMode  dm\ent,0

 If PickedEntity()=dm\ent Then 
  For rt.tree=Each tree
    If rt\mainent=dm\ent Then Delete rt
    Next
  FreeEntity dm\ent
  Delete dm
  Return
  EndIf

For i=1 To CountChildren (dm\ent)
 If PickedEntity()=GetChild(dm\ent,i) Then 
  For rt.tree=Each tree
    If rt\mainent=dm\ent Then Delete rt
    Next
  FreeEntity dm\ent
  Delete dm
  Return
  EndIf
Next

 Next

 For dm.dropmodel=Each dropmodel
  If dm\permapick=0 Then EntityPickMode  dm\ent,0
 Next

End Function 

Function scalemodel(smx#,smy#,smz#)
If scaleTimer#<MilliSecs() Then 
scaleTimer#=MilliSecs()+(scaletimer_inc#*(2.0-(fadespeed#*20)))
 For dm.dropmodel=Each dropmodel
 If dm\ent = selected_dropmodel Then 
     EntityAlpha Boxmesh,.6
     FitEntity Boxmesh,dm\ent
     If dm\sx#*smx#>0 Then dm\sx#=dm\sx#*smx#
     If dm\sy#*smy#>0 Then  dm\sy#=dm\sy#*smy
     If dm\sz#*smz#>0 Then  dm\sz#=dm\sz#*smz
     If dm\sx#<0 Then dm\sx#=.1
     If dm\sy#<0 Then  dm\sy#=.1
     If dm\sz#<0 Then  dm\sz#=.1
     ScaleEntity dm\ent,dm\sx,dm\sy,dm\sz
    EndIf
  Next
EndIf
End Function


Function Flopmodels()
For dm.dropmodel=Each dropmodel
If dm\permapick=0 Then EntityPickMode dm\ent,0
Next

For dm.dropmodel=Each dropmodel
 TranslateEntity dm\ent,0,10000,0
 LinePick EntityX(dm\ent,1),EntityY(dm\ent,1),EntityZ(dm\ent,1),0,-20000,0
 PositionEntity dm\ent,EntityX(dm\ent),PickedY()-.1,EntityZ(dm\ent)
 If Left$(dm\rcTe$,5)="_GRSS" Then  TranslateEntity dm\ent,0,-.5,0
 If Left$(dm\rcTe$,5)="_TREE" Then  TranslateEntity dm\ent,0,-2,0
 dm\x=EntityX(dm\ent)
 dm\y=EntityY(dm\ent)
 dm\z=EntityZ(dm\ent)
 ColorEntity(dm\ent)
Next
End Function


Function updatemodelcolors()
Return
For dm.dropmodel=Each dropmodel
 UpdateNormals dm\ent
EntityFX dm\ent,2

 LinePick EntityX(dm\ent,1),EntityY(dm\ent,1)+10,EntityZ(dm\ent,1),0,-150,0
 s=PickedSurface()
 tri=PickedTriangle()
 v=TriangleVertex(s,tri,1)
 col=(VertexRed(s,v)+VertexGreen(s,v)+VertexBlue(s,v))
 If col>3 Then col=col/3
 If col<LmAmbient_mod Then col=LmAmbient_mod

ss=CountSurfaces(dm\ent)
For sss=1 To ss
 s=GetSurface(dm\ent,sss)
 For v=0 To CountVertices(s)-1
  VertexColor s,v,col,col,col
  Next
  Next

Next
 End Function

Function movemodel(smx#,smy#,smz#)

smx=smx*GUI_VARIABLE_CAMERASPEED# / FPS
smy=smy*GUI_VARIABLE_CAMERASPEED# / FPS
smz=smz*GUI_VARIABLE_CAMERASPEED# / FPS

 For dm.dropmodel=Each dropmodel
 If dm\ent = selected_dropmodel Then 
   EntityAlpha Boxmesh,.6
   FitEntity Boxmesh,dm\ent
  EndIf
   If selected_dropmodel=dm\ent Then 
    TranslateEntity dm\ent,smx*(Terrainscale*.2),smy*(Terrainscale*.2),smz*(Terrainscale*.2)
    dm\x=EntityX(dm\ent,1)
    dm\y=EntityY(dm\ent,1)
    dm\z=EntityZ(dm\ent,1)
EndIf
  Next
End Function

Function rotatemodel(smx#,smy#,smz#)
DebugLog "model rotated "+smx#+" "+smy#+" "+smz
 For dm.dropmodel=Each dropmodel
 If dm\ent = selected_dropmodel Then 
   EntityAlpha Boxmesh,.6
   FitEntity Boxmesh,dm\ent
  EndIf
   If selected_dropmodel=dm\ent Then 
    TurnEntity dm\ent,smx,smy,smz
    dm\pitch#=EntityPitch(dm\ent)
    dm\yaw#=EntityYaw(dm\ent)
    dm\roll#=EntityRoll(dm\ent)
EndIf
  Next
End Function

Function copymodel()
 For dm.dropmodel=Each dropmodel
   If selected_dropmodel=dm\ent Then 

system_message$="Model Copied"
system_messagetimer#=MilliSecs()+400
selectedmodel$=dm\ModelFileName$
selectedmodelpath$=dm\modelfilepath$
       mc_id=dm\id 
   DebugLog "id copy "+dm\id+" to "+mc_id
       sceneryID=mc_id
       Mc_onoff=1
       Mc_sx#=dm\sx
       mc_sy#=dm\sy
       mc_sz#=dm\sz

       If Mc_sx=<0 Then Mc_sx=.1
       If mc_sy=<0 Then mc_sy=.1
       If mc_sz=<0 Then mc_sz=.1

       mc_rcte$=dm\rcTe$
       Mc_pitch#=dm\pitch
       mc_yaw#=dm\yaw
       mc_roll#=dm\roll
      EndIf
Next
End Function

Function Automodel()
Createbackup
RemoveUndoData()
;first get area of terrain
aW#=MeshWidth(layer(1))-1
aD#=MeshDepth(layer(1))-1
aw2#=(aw/2)+1
ad2#=(ad/2)+1
stepval#=aw#/(AutoDensity) ;desity step
stepval=stepval+Rnd((-stepval*.05),(stepval*.05))
xi#=-aw2
zi#=ad2
;next get max and min height of terrain
minh#=100000000
maxh#=-10000000
s=GetSurface(layer(1),1)
 For I=0 To CountVertices(s)-1
  If VertexY(s,i)<minh# Then minh#=VertexY(s,i)
  If VertexY(s,i)>maxh# Then maxh#=VertexY(s,i)
  Next
hrange#=(maxh#-minh#)
hstep#=(hrange#*.01)
H1#=minh+(HStep#*(AutoMinHeight-1))
H2#=minh+(hstep#*AutoMaxHeight)
;ok get slope values
;0=flat  and 1=angled
Minslope#=AutoMinSlope*.01
Maxslope#=AutoMaxSlope*.01

While xi#<aw2
While zi#<ad2
Doit=Rand(0,200)
 If doit<=AutoDensity Then 
  LinePick xi,10000,zi,0,-20000,0
   If PickedY()>=h1 And PickedY()<=h2 Then 
slopeval#=Abs(PickedNX())+Abs(PickedNZ())/2
;slopeval#=Abs(PickedNX(s,aaa))+Abs(PickedNZ(s,aaa))+Abs(PickedNY(s,aaa))/3

  If minslope#<=slopeval# And maxslope#>=slopeval# Then
   dropmodel xi,zi,1
   rotatemodel Rand(-5,5),Rand(-180,180),Rand(-5,5)
   smx#=Rnd(.7,1.2)
   smy#=Rnd(.7,1.2)
   smz#=Rnd(.7,1.2)
     
   scalemodel smx#,smy#,smz#
EndIf
EndIf
EndIf
zi=zi+stepval#
DebugLog "zi= "+zi+" xi= "+xi
Wend
zi=-ad2
xi=xi+stepval#
Wend

End Function

Function RemoveUndoData()
For au.autoundo=Each autoundo
Delete au
Next

End Function

Function Undo()
For au.autoundo=Each autoundo
 For dm.dropmodel=Each dropmodel
 If au\ent=dm\ent Then  
  totaldrops=totaldrops-1
   FreeEntity dm\ent
   Delete dm
  EndIf
  Next
 Delete au
Next
End Function



Function lightmapmodels()
End Function

Function showlist()
;compile the 12 models viewable in window
For i=1 To 12
 sceneryinviewname$(i)=""
 sceneryinviewid(i)=-1 
Next

count=0
count2=0
For scc.TEscenery=Each TEScenery
count=count+1
If count=>sceneryoffset And count2<12 Then 
 count2=count2+1
 sceneryinviewid(count2)=scc\id
 sceneryinviewname$(count2)=scc\name$
  For i2= Len(sceneryinviewname$(count2)) To 1 Step -1
   If Mid$(sceneryinviewname$(count2),i2,1)="\" Then
    sceneryinviewname$(count2)=Right$(sceneryinviewname$(count2),Len(sceneryinviewname$(count2))-i2)
    Exit
   EndIf
  Next
If Len(sceneryinviewname$(count2))>17 Then sceneryinviewname$(count2)=Mid$(sceneryinviewname$(count2),1,17)
EndIf
 Next
End Function


Function SaveAreaRCTE(Name$)
	DebugLog "Save Area RCTE with NumLayers = " + numlayers

	ChangeDir thispath$
	fileExists = FileType(thispath$+"data\areas\"+Name$+".dat")
	
	For scn.scenery=Each scenery
		Delete scn
	Next
	
	For dm.dropmodel=Each dropmodel
		EntityType dm\ent,3
		scn.scenery=New scenery
		scn\en=dm\ent
		scn\rcte$=dm\rcTe$
		scn\meshid=dm\id
		scn\scalex#=dm\sx
		scn\scaley#=dm\sy
		scn\scalez#=dm\sz
		If fileExists = 1 
			SCn\AnimationMode = dm\AnimationMode
			SCn\SceneryID = dm\SceneryID
			SCn\TextureID = dm\TextureID
			SCn\CatchRain = dm\CatchRain
			SCn\Lightmap$ = dm\Lightmap$
			SCn\Collides = dm\Collides
		Else	
			SCn\Collides = 3
			If Left$(dm\rcTe$,5)="_GRSS" Then SCn\Collides = 0
			SCn\TextureID=65535
		EndIf
		If dm.dropmodel=Last dropmodel Then 
			scn\rcte$="_TRRN"+dm\rcTe$
			SCn\TextureID=65535
		EndIf
	Next
	DebugLog "saving area "+Name$
	If Name$="" Then RuntimeError "Cannot export. Invalid name"
	
	; Save client stuff
	ChangeDir thispath$
	If fileExists=1
		DeleteFile (thispath$+"data\areas\"+Name$+".dat")
	EndIf
	SaveAreaTE(Name$)
	
	; Save server stuff
	ChangeDir thispath$
	If FileType(thispath$+"data\server data\areas\"+Name$+".dat")=0
		DefaultArea.area=servercreatearea()
		Defaultarea\name$=Name$
		ServerSaveArea(DefaultArea)
		ServerUnloadArea(DefaultArea)
	EndIf
	
	
	Delete Each area
	For scn.scenery=Each scenery
		Delete scn
	Next

End Function

Function Edgemap(lmdir#=8,area=6)

 
For i= numlayers To 1 Step -1

For skanx1=2 To lsegments
For skanz1=2 To lsegments

If skanx1=4 Or skanx1=lsegments-4 Or skanz1=4 Or skanz1=lsegments-4 Then 

fxx=skanx1
fzz=skanz1
;here we alterland on this vert
For i= numlayers To 1 Step -1
s=GetSurface(layer(i),1)


For skanx=-area To area
skx=skanx+fxx
For skanz=-area2 To area2
skz=skanz+fzz
distance# = Sqr(((fxx-skx)*(fxx-skx)) + ((fzz-skz)*(fzz-skz)))
     If skx>lsegments Then skx=lsegments
     If skz>lsegments Then skz=lsegments
     If skx<0 Then skx=0
     If skz<0 Then skz=0

 v=l_index(skx,skz)
 old_al1#=VertexAlpha(s,v)
;distance=distance
;If distance#<=.5 Then distance=.5

cval#=(lmdir#*(.001*(20)))-(((4-distance)*-lmdir)*(.001))


If lmdir#<0 And cval#>0 Then cval=0
If lmdir#>0 And cval#<0 Then cval=0
cval=cval*40
 VertexCoords s,v,VertexX(s,v),VertexY(s,v)+cval#,VertexZ(s,v)

 Next
 Next

Next


EndIf

 Next
 Next
Next



Flopmodels
End Function

Function treesettings()
seas_id=currentseason
End Function




Function  FUIGet_Texture(id )
    ChangeDir thispath$
	tFile = FUI_OpenDialog( "Load Texture", "Data\RCTE\RCTE_Textures", "Jpeg (*.jpg)|*.jpg|PNG (*.png)|*.png|Bitmap (*.bmp)|*.bmp")
	If tFile = True Then

	;strip path
    ;so strip the path
    sfilename3$=app\currentfile
    For i2= Len(sfilename3$) To 1 Step -1
     If Mid$(sfilename3$,i2,1)="\" Then
     sfilename3$=Right$(sfilename3$,Len(sfilename3$)-i2)
     Exit
    EndIf
   Next
   DebugLog "copied file "+thispath$+texturepath$+sfilename3$
   CopyFile App\CurrentFile,thispath$+texturepath$+sfilename3$
   ChangeDir thispath$
   assigntexture id,sfilename3$
   Else
   ChangeDir thispath$
   Return -1
   EndIf
   ChangeDir thispath$
End Function






;TreeGrass 0 - 1, Sway 0 - 2, Evergreen 0 - 1
Function TreeGrassWindow( TreeGrass = 0, Sway = 0, Evergreen = 0 )
seas_id=currentseason
thisrtce$="_TREE00"
For dm.dropmodel=Each dropmodel
 If dm\ent=selected_dropmodel Then 
   thisrcte$=dm\rcTe$
   EndIf
Next
primary$=Left$(thisrcte$,5)
swingsty=Int(Mid$(thisrcte$,6,1))
evergrn=Int(Mid$(thisrcte$,7,1))
If primary$="_TREE" Then TreeGrass=0 Else TreeGrass=1
Sway=swingsty
Evergreen=evergrn

        TIMAGE = CreateImage( 70, 45 )
		SetBuffer ImageBuffer(timage)
		Color GUI_ARRAY_SEASONCOLOR( seas_id, 0 ), GUI_ARRAY_SEASONCOLOR( seas_id, 1 ), GUI_ARRAY_SEASONCOLOR( seas_id, 2 )
		Rect 0, 0, 70, 45,1
		SetBuffer BackBuffer()
        FUI_DeleteGadget gui_treewin_canvas 
		GUI_TREEWIN_CANVAS = FUI_ImageBox( GUI_TREEWIN, 125, 90, 68, 45, TIMAGE )
        FreeImage timage 

		If TreeGrass = 0 Then
	        FUI_SendMessage(GUI_TREEWIN_TREESET,M_SETCHECKED,False)
    	    FUI_SendMessage(GUI_TREEWIN_GRASSSET,M_SETCHECKED,True)
		ElseIf TreeGrass = 1 
	        FUI_SendMessage(GUI_TREEWIN_TREESET,M_SETCHECKED,True)
    	    FUI_SendMessage(GUI_TREEWIN_GRASSSET,M_SETCHECKED,False)
		EndIf
		If Sway = 1 Then
	        FUI_SendMessage(GUI_TREEWIN_SWAYTOP,M_SETCHECKED,False)
	        FUI_SendMessage(GUI_TREEWIN_SWAYMID,M_SETCHECKED,False)
	        FUI_SendMessage(GUI_TREEWIN_SWAYLOW,M_SETCHECKED,True)
		ElseIf Sway = 0
	        FUI_SendMessage(GUI_TREEWIN_SWAYTOP,M_SETCHECKED,False)
	        FUI_SendMessage(GUI_TREEWIN_SWAYMID,M_SETCHECKED,True)
	        FUI_SendMessage(GUI_TREEWIN_SWAYLOW,M_SETCHECKED,False)
		ElseIf Sway = 2
	        FUI_SendMessage(GUI_TREEWIN_SWAYTOP,M_SETCHECKED,True)
	        FUI_SendMessage(GUI_TREEWIN_SWAYMID,M_SETCHECKED,False)
	        FUI_SendMessage(GUI_TREEWIN_SWAYLOW,M_SETCHECKED,False)
		EndIf
		If Evergreen = 1 Then
	        FUI_SendMessage(GUI_TREEWIN_SEASON,M_SETCHECKED,False)
	        FUI_SendMessage(GUI_TREEWIN_COLOR,M_SETCHECKED,True)
		ElseIf Evergreen = 0
	        FUI_SendMessage(GUI_TREEWIN_SEASON,M_SETCHECKED,True)
	        FUI_SendMessage(GUI_TREEWIN_COLOR,M_SETCHECKED,False)
		EndIf
 	    TCancel = False
	    TValue = 0

 For i=0 To 11
  season_red(i)=GUI_ARRAY_SEASONCOLOR( i, 0 )
  season_green(i)=GUI_ARRAY_SEASONCOLOR( i,1 )
  season_blue(i)=GUI_ARRAY_SEASONCOLOR( i,2 )  
 Next
 FUI_SendMessage( GUI_TREEWIN, M_OPEN )
End Function

Function v_distance#( x#, y#, z#, x2#, y2#, z2# )
	value#=Sqr((x#-x2#)*(x#-x2#)+(y#-y2#)*(y#-y2#)+(z#-z2#)*(z#-z2#))
	
	Return value#
End Function

Function lambertlight(entity,LX#,LY#,LZ#,Lr,Lg,Lb)
radius#=999999999999999
flag=1 

 count=CountSurfaces(entity) 
 For n=1 To count
  surf=GetSurface(entity,n) 
  Vcount=CountVertices(surf)-1 
  For v=0 To Vcount          
  TFormPoint(VertexX#(surf,vcount),VertexY#(surf,vcount),VertexZ#(surf,vcount),entity,0)
        xd#=TFormedX()+EntityX(entity,1)-LX#
        yd#=TFormedY()+EntityY(entity,1)-LY#
        zd#=TFormedZ()+EntityZ(entity,1)-LZ#
        mag#=Sqr(xd#*xd# + yd#*yd# + zd#*zd#)
   If flag=1
    va#=VertexAlpha(surf,v) 
   EndIf    
   vr=Lr-(Lr/radius)*mag#
   vg=Lg-(Lg/radius)*mag#
   vb=Lb-(Lb/radius)*mag# 
       
   If vr>255 Then vr=255
   If vg>255 Then vg=255
   If vb>255 Then vb=255 
 
   vx#=VertexNX(surf,v)
   vy#=VertexNY(surf,v)
   vz#=VertexNZ(surf,v)
   mag#=1/mag#
   LnY#=yd#*mag#*-1
   LnX#=xd#*mag#*-1
   LnZ#=zd#*mag#*-1
    
   Kh#=((vx)*LnX+(vy)*LnY+(vz)*LnZ)

  VertexColor surf,v,vr*Kh#+vcr,vg*Kh#+vcg,vb*Kh#+vcb,va#

 
  Next   
 Next 
FreeEntity lightpiv
FreeEntity lightcube
End Function


Function lightmapit2(CAMERA,modelshadows=1)
	updatetrees(Cam)
	updatetrees(Cam)
	updatetrees(Cam)
	updatetrees(Cam)
	
	If FUI_SendMessage(GUI_MODEMODEL_MODELPICK,M_GETCHECKED)=True Then 
		For dm.dropmodel=Each dropmodel
			If Left$(dm\rcTe$,5)<>"_GRSS"  Then 
				EntityPickMode  dm\ent,2,True
				For ccount=1 To CountChildren(dm\ent)
					EntityPickMode GetChild(dm\ent,ccount),2
				Next
			EndIf
		Next
	EndIf
;update normals so it all lights properly
For i=1 To numlayers
	UpdateNormals layer(i)
Next

;minc is the min gamma setting for lightmap
minc=LmAmbient_terr
;if prev we had a dx light.. we can remove it
;If light Then 
 ;FreeEntity light
 ;light=0
 ;EndIf

;max gamma setting
c_red=255.0
c_green=255.0
c_blue=255.0

EntityPickMode layer(1),2,True
 s=GetSurface(layer(1),1)
 nverts=CountVertices(s)

 tempcube=CreateCube()
 temppivot=CreateCube()
ScaleEntity tempcube,.1,.1,.1
ScaleEntity temppivot,.1,.1,.1
EntityRadius TEMPCUBE,.1*Terrainscale
EntityRadius TEMPPIVOT,.1*Terrainscale
 PositionEntity tempcube,EntityX(CAMERA),EntityY(CAMERA),EntityZ(CAMERA)
 EntityPickMode tempcube,2
 EntityPickMode temppivot,2
 For i=0 To nverts-1
 PositionEntity temppivot,VertexX(s,i),VertexY(s,i)+(.00001*Terrainscale),VertexZ(s,i)
 PositionEntity light,VertexX(s,i),VertexY(s,i)+(.00001*Terrainscale),VertexZ(s,i)
 PointEntity temppivot,tempcube
 PointEntity tempcube,temppivot
 iv=1
 If EntityVisible(tempCUBE,tempPIVOT) Then iv=0




lightdist#=5.0
cmag#=EntityX(CAMERA)*EntityX(CAMERA)+EntityY(CAMERA)*EntityY(CAMERA)+EntityZ(CAMERA)*EntityZ(CAMERA)
cam_nx#=(EntityX(CAMERA)/cmag#)
cam_ny#=(EntityY(CAMERA)/cmag#)
cam_nz#=(EntityZ(CAMERA)/cmag#)


 vnx#=VertexNX(S,I)
 vny#=VertexNY(S,I)
 vnz#=VertexNZ(S,I)

;If terrainscale#=<1 Then 
;dp# =(cam_nx * vnX# + cam_nY# * vny# + cam_nz * vnZ#)*6455*(lightdist#*.01)
;Else
dp# =(cam_nx * vnX# + cam_nY# * vny# + cam_nz * vnZ#)*(6455*Terrainscale)*((lightdist#*.05))
If dp>255 Then dp=255
;EndIf
If dp<minc Then dp=minc
;dp#=300*val#
VertexColor s,i,dp,dp,dp,VertexAlpha(s,i)
      If iv=1 Then 
      VertexColor s,i,minc,minc,minc,VertexAlpha(s,i)
      Else
      EndIf
       Next

For sI=2 To NUMLAYERS
 s2=GetSurface(layer(si),1)
  For i=0 To nverts-1
   VertexColor s2,i,VertexRed(s,i),VertexGreen(s,i),VertexBlue(s,i),VertexAlpha(s2,i)
   Next
Next
;smooth it out
For Rep#=0 To lmblur_terr
For smx=1 To lsegments-1
For smy=1 To lsegments-1

For L=1 To numlayers
s=GetSurface(layer(l),1)

r1#=VertexRed(s,L_index(smx,smy))
r2#=VertexRed(s,L_index(smx+1,smy))
r3#=VertexRed(s,L_index(smx-1,smy))
r4#=VertexRed(s,L_index(smx,smy+1))
r5#=VertexRed(s,L_index(smx,smy-1))

newr#=(r1+r2+r3+r4+r5)/5

g1#=VertexGreen(s,L_index(smx,smy))
g2#=VertexGreen(s,L_index(smx+1,smy))
g3#=VertexGreen(s,L_index(smx-1,smy))
g4#=VertexGreen(s,L_index(smx,smy+1))
g5#=VertexGreen(s,L_index(smx,smy-1))

newg#=(g1+g2+g3+g4+g5)/5

b1#=VertexBlue(s,L_index(smx,smy))
b2#=VertexBlue(s,L_index(smx+1,smy))
b3#=VertexBlue(s,L_index(smx-1,smy))
b4#=VertexBlue(s,L_index(smx,smy+1))
b5#=VertexBlue(s,L_index(smx,smy-1))

newb#=(b1+b2+b3+b4+b5)/5
If newr<minc Then newr=minc
If newg<minc Then newg=minc
If newb<minc Then newb=minc

VertexColor s,l_index(smx,smy),NEWR,newg#,newb#,VertexAlpha(s,l_index(smx,smy))
Next
Next
Next
Next


FreeEntity tempcube
FreeEntity temppivot

 For dm.dropmodel=Each dropmodel
  If dm\permapick=0 Then EntityPickMode  dm\ent,0
  For ccount=1 To CountChildren(dm\ent)
   If dm\permapick=0 Then EntityPickMode GetChild(dm\ent,ccount),0
  Next
  Next


 For dm.dropmodel=Each dropmodel
  ColorEntity(dm\ent)
 Next

 End Function 



Function RandTerrain()
 yn=Fui_confirm("Randomize ?","Yes","No")
 If yn<>1 Then Return
 Createbackup
 SeedRnd MilliSecs()
RANDIMAGE = PerlinHeightMap(Chr$(Rand(65,90))+Chr$(Rand(65,90))+Chr$(Rand(65,90))+Chr$(Rand(65,90))+Chr$(Rand(65,90)), 256, 256, Rand(10,100), Rnd(.1,.9), Rnd(0.0,1.0),Rnd(0.0,1.0))
resz=Rand(25,64)

If Rand(0,3)=2 Then 
ResizeImage (RANDIMAGE,resz,resz)
ResizeImage (RANDIMAGE,256,256)
EndIf

If Rand(0,3)=1 Then flattenimage(RANDIMAGE,Rand(150,200),Rand(50,90))
ed=Rand(0,5)
If ed=0 Then Edgestozero(RANDIMAGE)
If ed=2 Then EdgeWALL(RANDIMAGE)

SetBuffer BackBuffer()
;save it
FreeImage tempimage
ResizeImage RANDIMAGE,256,256
SaveImage RANDIMAGE,thispath$+savepath$+"RNDHM.BMP"
SetBuffer BackBuffer()
FreeImage RANDIMAGE
loadhmap thispath$+savepath$+"RNDHM.BMP",Rand(1,20)
For i=1 To Rand(0,2)
smoothall
Next
Color 200,200,200
End Function


Function TERRTOHM()
    ChangeDir thispath$
	tFile = FUI_SaveDialog( "Save Heightmap", "Data\RCTE\RCTE_Textures", "Bitmap (*.bmp)|*.bmp")
    texture$=app\currentfile
    YN=1
    If FileType(texture$)=1 Or FileType(texture$)+".bmp"=1 Then 
    YN=fui_confirm("overwrite ?","Yes","No")
    EndIf

    If YN=1 Then
    If Lower$(Right$(texture$,3))<>"bmp" Then texture$=texture$+".bmp"
    sfilename3$=texture$
    Else
    Return
    EndIf
    sfilename3=texture$

S=GetSurface(layer(1),1)
TEMPIMAGE=CreateImage(lsegments+1,lsegments+1)
SetBuffer ImageBuffer(tempimage)
minh#=100000000
maxh#=-10000000
For i=0 To CountVertices(s)-1
  If VertexY(s,i)<minh# Then minh#=VertexY(s,i)
  If VertexY(s,i)>maxh# Then maxh#=VertexY(s,i)
  Next
hrange=Int((maxh#-minh#))
For i=0 To lsegments 
 For Ii=0 To lsegments
	  thisy#=VertexY(s,l_index(i,ii))+Abs(minh)
	  perc#=(thisy / hrange)*100
	  hc=Int((255*perc#)/100)
      DebugLog hc
	  Color hc,hc,hc
	  Plot i,ii          
	Next
	Next
SetBuffer BackBuffer()

ResizeImage tempimage,255,255
ScaleImage tempimage,1,-1
RotateImage tempimage,90
ChangeDir thispath$
RotateImage tempimage,90
ScaleImage tempimage,-1,1
ResizeImage tempimage,255,255
SaveImage tempimage,sfilename3$
FreeImage tempimage
ChangeDir thispath$
End Function

Function copyalpha()
alpha_Segs=lsegments
For i=1 To numlayers
 s=GetSurface(layer(i),1)
For vx=0 To lsegments
 For vz=0 To lsegments
 alpha_Record(i,vx,vz)=VertexAlpha(s,l_index(vx,vz))
 Next
Next
Next
End Function

Function pastealpha()
copyx=0:copyz=0
diff=((lsegments/25)-(alpha_Segs/25))
difdir=Sgn((lsegments/25)-(alpha_Segs/25))
If Sgn(DIFF)=1 Then 

For i=1 To numlayers
copyx=0:copyz=0
copycountx=0:copycountz=0
s=GetSurface(layer(i),1)
 For vx=0 To lsegments
  For vz=0 To lsegments
  VertexColor s,l_index(vx,vz), VertexRed(s,l_index(vx,vz))   ,VertexGreen(s,l_index(vx,vz))  ,VertexBlue(s,l_index(vx,vz))  ,alpha_Record(i,copyx,copyz)
		copycountz=copycountz+1
		 If copycountz>diff Then 
		  copycountz=0
		  copyz=copyz+1
		  EndIf
 Next
		copycountz=copycountz=0
		  copyz=0
copycountx=copycountx+1
 If copycountx>diff Then 
  copycountx=0
  copyx=copyx+1
  EndIf
Next
Next
 ElseIf Sgn(DIFF)=-1
DIFF=Abs(DIFF)
For i=1 To numlayers
copyx=0:copyz=0
copycountx=0:copycountz=0
s=GetSurface(layer(i),1)

 For vx=0 To alpha_Segs
  For vz=0 To alpha_Segs
  VertexColor s,l_index(COPYX,COPYZ), VertexRed(s,l_index(COPYX,COPYZ))   ,VertexGreen(s,l_index(COPYX,COPYZ))  ,VertexBlue(s,l_index(COPYX,COPYZ))  ,alpha_Record(i,Vx,Vz)
		copycountz=copycountz+1
		 If copycountz>diff Then 
		  copycountz=0
		  copyz=copyz+1
		  EndIf
 Next
		copycountz=copycountz=0
		  copyz=0
copycountx=copycountx+1
 If copycountx>diff Then 
  copycountx=0
  copyx=copyx+1
  EndIf
Next
Next
Else 
For i=1 To numlayers
s=GetSurface(layer(i),1)
 For vx=0 To lsegments
  For vz=0 To lsegments
  VertexColor s,l_index(vx,vz), VertexRed(s,l_index(vX,vZ))   ,VertexGreen(s,l_index(vX,vZ))  ,VertexBlue(s,l_index(COPYX,COPYZ))  ,alpha_Record(i,Vx,Vz)
   Next
  Next
 Next
EndIf
End Function

Function blurtex(tex)
 texh=TextureHeight(tex)
 texw=TextureWidth(tex)
SetBuffer TextureBuffer(tex)
LockBuffer TextureBuffer(tex)
For i=0 To texw-1
 For ii=0 To texh-1
	If  (i)>0 And (i)<=255 And (ii)>0 And (ii)<=255 Then 

argb=ReadPixelFast(i,ii)
thisred=(ARGB Shr 16) And $ff 
thisgreen=(ARGB Shr 8) And $ff 
thisblue=ARGB And $ff

argb=ReadPixelFast(i,ii+1)
thisred1=(ARGB Shr 16) And $ff 
thisgreen1=(ARGB Shr 8) And $ff 
thisblue1=ARGB And $ff

argb=ReadPixelFast(i,ii-1)
thisred2=(ARGB Shr 16) And $ff 
thisgreen2=(ARGB Shr 8) And $ff 
thisblue2=ARGB And $ff

argb=ReadPixelFast(i+1,ii)
thisred3=(ARGB Shr 16) And $ff 
thisgreen3=(ARGB Shr 8) And $ff 
thisblue3=ARGB And $ff

argb=ReadPixelFast(i-1,ii)
thisred4=(ARGB Shr 16) And $ff 
thisgreen4=(ARGB Shr 8) And $ff 
thisblue4=ARGB And $ff

newred=(thisred+thisred1+thisred2+thisred3+thisred4)/5
newgreen=(thisred+thisgreen1+thisgreen2+thisgreen3+thisgreen4)/5
newblue=(thisblue+thisblue1+thisblue2+thisblue3+thisblue4)/5


 WritePixelFast i,ii,255 Shl 24 Or newRed Shl 16 Or newGreen Shl 8 Or newBlue Shl 0
EndIf
Next
Next
UnlockBuffer TextureBuffer(tex)
SetBuffer BackBuffer()
  End Function


Function bloomtex(tex)
 texh=TextureHeight(tex)
 texw=TextureWidth(tex)
SetBuffer TextureBuffer(tex)
LockBuffer TextureBuffer(tex)
For i=0 To texw-1
 For ii=0 To texh-1
	If  (i)>0 And (i)<=255 And (ii)>0 And (ii)<=255 Then 

argb=ReadPixelFast(i,ii)
thisred=((ARGB Shr 16) And $ff) 
thisgreen=((ARGB Shr 8) And $ff) 
thisblue=(ARGB And $ff)

argb=ReadPixelFast(i,ii+1)
thisred1=((ARGB Shr 16) And $ff) 
thisgreen1=((ARGB Shr 8) And $ff) 
thisblue1=(ARGB And $ff)

argb=ReadPixelFast(i,ii-1)
thisred2=((ARGB Shr 16) And $ff) 
thisgreen2=((ARGB Shr 8) And $ff) 
thisblue2=(ARGB And $ff)

argb=ReadPixelFast(i+1,ii)
thisred3=((ARGB Shr 16) And $ff) 
thisgreen3=((ARGB Shr 8) And $ff) 
thisblue3=(ARGB And $ff)

argb=ReadPixelFast(i-1,ii)
thisred4=((ARGB Shr 16) And $ff) 
thisgreen4=((ARGB Shr 8) And $ff) 
thisblue4=(ARGB And $ff)

newred=(thisred+thisred1+thisred2+thisred3+thisred4)/5
newgreen=(thisred+thisgreen1+thisgreen2+thisgreen3+thisgreen4)/5
newblue=(thisblue+thisblue1+thisblue2+thisblue3+thisblue4)/5
newred=thisRed+(newred/25)
newgreen=thisgreen+(newgreen/25)
newblue=thisBlue+(newblue/25)

If newred<0 Then newred=0
If newblue<0 Then newblue=0
If newgreen<0 Then newgreen=0

If newred>255 Then newred=255
If newblue>255 Then newblue=255
If newgreen>255 Then newgreen=255

 WritePixelFast i,ii,255 Shl 24 Or newRed Shl 16 Or newGreen Shl 8 Or newBlue Shl 0
EndIf
Next
Next
UnlockBuffer TextureBuffer(tex)
SetBuffer BackBuffer()
  End Function



Function reducetex(tex)
 texh=TextureHeight(tex)
 texw=TextureWidth(tex)

SetBuffer TextureBuffer(tex)
LockBuffer TextureBuffer(tex)
For i=0 To texw-1
 For ii=0 To texh-1
	If  (i)>0 And (i)<=255 And (ii)>0 And (ii)<=255 Then 

argb=ReadPixelFast(i,ii)
thisred=  (ARGB Shr 16) And $ff 
thisgreen=(ARGB Shr 8) And $ff 
thisblue=ARGB And $ff

nc=0
ncc=0

For id=0 To 256 

ncc=ncc+1
If ncc=19 Then 
 ncc=0
 nc=nc+1
 EndIf

If thisred=id    Then newred=nc
If thisgreen=id  Then newgreen=nc
If thisblue=id   Then newblue=nc
Next

 WritePixelFast i,ii,255 Shl 24 Or newRed Shl 16 Or newGreen Shl 8 Or newBlue Shl 0

EndIf
Next
Next

UnlockBuffer TextureBuffer(tex)
SetBuffer BackBuffer()
  End Function



Function RotateTex(tEX)
 texh=TextureHeight(tEX)
 texw=TextureWidth(tEX)
 SetBuffer TextureBuffer(tEX)
 LockBuffer(TextureBuffer(tEX))
Dim rotate_hm_buffer(texh,texw)
For i=0 To texh-1
For ii=texh-1 To 0 Step -1
argb=ReadPixelFast(i,ii)
rotate_hm_buffer(bx,by)=argb
bx=bx+1
Next
bx=0
by=by+1
Next

For i=0 To texh-1
For ii=0 To texh-1
WritePixelFast i,ii,rotate_hm_buffer(i,ii)
Next
Next
Dim rotate_hm_buffer(1,1)
UnlockBuffer TextureBuffer(tEX)
SetBuffer BackBuffer()

 End Function 

Function NegativeTex(tex)
 texh=TextureHeight(tex)
 texw=TextureWidth(tex)
SetBuffer TextureBuffer(tex)
LockBuffer(TextureBuffer(tex))
For i = 0 To texw-1
	For j = 0 To texh-1
		col = ReadPixelFast(i,j) And $FFFFFF
		redlevel = 255-((col Shr 16) And $FF)
		greenlevel = 255-((col Shr 8) And $FF)
		bluelevel = 255-(col And $FF)
		argb = (bluelevel Or (greenlevel Shl 8) Or (redlevel Shl 16) Or (255 Shl 24))
		WritePixelFast i,j,argb
	Next
Next
UnlockBuffer(TextureBuffer(tex))
SetBuffer BackBuffer()
End Function


Function setholecolor()

FUI_SendMessage(GUI_MODEPAINT_USECOLOR, M_SETCHECKED ,True)
FUI_SendMessage(GUI_SLIDERWIN_BRUSHSPEED,M_SETVALUE,100)
FUI_SendMessage(GUI_SLIDERWIN_BRUSHSIZE,M_SETVALUE,20)
holebrush=1
c_red=76
c_blue=10
c_green=100
End Function


Function DropFloaters()
   EntityPickMode layer(1),2
   For dm.dropmodel=Each dropmodel
    LP=LinePick (EntityX(dm\ent,1),EntityY(dm\ent,1)+.1,EntityZ(dm\ent,1),0,3000,0)
    If lp Then PositionEntity dm\ent,EntityX(dm\ent),PickedY(),EntityZ(dm\ent)
    Next        
End Function

Function particlemap(randimage,seed, particles, clusters)
width=ImageWidth(randimage)
height=ImageHeight(randimage)
SetBuffer ImageBuffer(randimage)
Local m, n, x, z
Local y, disp = 25      
SeedRnd(seed)

For i=0 To 128
For ii=0 To 128
rand_map(i,ii)=0
Next
Next
      For m = 0 To clusters - 1
  
      x = Rand(0, Width - 1) 
      z = Rand(0, Height - 1)
	
      For n = 0 To particles - 1
	
        If ((x < Width) And (z < Height) And (x >= 0) And (z >= 0))          
	      y = rand_map(x, z) + disp
	      rand_map(x, z) = y
          Color y,y,y
          Plot x,z
	    EndIf
	
	    Select Rand(0, 3)
          Case 0
            x =x+ 1
          Case 1
            x =x- 1
          Case 2
            z =z+ 1
          Case 3
            z =z- 1
        End Select
      Next
    Next



SetBuffer BackBuffer()
End Function


Function createHillMap(image,seed, numhills, maxhsize)
SetBuffer ImageBuffer(image)

 width=ImageWidth(image)
 height=ImageHeight(image)

    Local i, n, x, z, centrex, centrez, radius, startx, endx, startz, endz, ipos
    Local y
  
    SeedRnd(seed)

For i=0 To 128
For ii=0 To 128
rand_map(i,ii)=0
Next
Next
      
  
    For n = 1 To numhills

	  centrex = Rand(0, Width - 1)
	  centrez = Rand(0, Height - 1)
	  radius = Rand(0, Abs(maxhsize))

      startx = centrex - radius
      If startx < 0
	    startx = 0
      EndIf

      endx = centrex + radius
	  If endx > Width
	    endx = Width
      EndIf

      startz = centrez - radius
      If startz < 0
	    startz = 0
      EndIf

      endz = centrez + radius
      If endz > Height 
	    endz = Height
      EndIf

      For x = startx To endx - 1
        For z = startz To endz - 1
	   	  y = (radius * radius) - (((x - centrex) * (x - centrex)) + ((z - centrez) * (z - centrez)))
	      If y > 0.0
          rand_map(x, z) =rand_map(x,z)+ y
            
          Color rand_map(x, z),rand_map(x, z),rand_map(x, z)
          Plot x,z

	      EndIf
	    Next
	  Next
    
    Next
  SetBuffer BackBuffer()
  End Function

Function smoothimagefast(image) 
 width=ImageWidth(image)
 height=ImageHeight(image)
ResizeImage image,width*.3,height*.3
ResizeImage image,width,height
End Function


Function flattenimage(image,valuehigh,valuelow)
 
    SetBuffer ImageBuffer(image)
    width=ImageWidth(image)
    height=ImageHeight(image)
    Local x, z
    Local y
  
    For x = 0 To Width - 1
      For z = 0 To Height - 1
        GetColor(x,z)
        y = (ColorRed())
        If y>valuehigh Then y=valuehigh
        If y<valuelow Then y=valuelow
        Color y,y,y       
        Plot x,z 
      Next
    Next
    SetBuffer BackBuffer()

End Function


Function makeCoast(image,exten,depth)
    SetBuffer TextureBuffer(image)
    width=TextureWidth(image)
    height=TextureHeight(image)
    tempimage=CreateImage(width,height)
    CopyRect 0,0,width,height,0,0,TextureBuffer(image),ImageBuffer(tempimage) 

    Color 13,13,13
    SetBuffer ImageBuffer(tempimage)
    Rect 0,0,width-1,height-1
    Color 0,0,0
    Oval 42,42,width-43,height-43
For i=0 To height-1
    nclr=Rand(depth,depth+5)
    Color nclr,nclr,nclr
    Line 0,i,Rand(exten*.5,exten*2),i 
    Line i,0,i,Rand(exten*.5,exten*2) 
    Line width-1,i,width-Rand(exten*.5,exten*2),i 
    Line i,height-1,i,height-Rand(exten*.5,exten*2) 
Next 
;   Color 0,0,0
 ;  Oval 12,12,width-13,height-13
   CopyRect 0,0,width,height,0,0,ImageBuffer(tempimage),TextureBuffer(image) 
   FreeImage tempimage
   SetBuffer BackBuffer()
 End Function   

Function PIterrain(image,seed)
SeedRnd seed
SetBuffer ImageBuffer(image)
    Sinwave=0
    Coswave=0
    CosDir=Rand(1,4)
    Sindir=Rand(1,4)
    width=ImageWidth(image)
    height=ImageHeight(image)
    amp=Rand(100,1000)
    For i=0 To width
     Sinwave=sinwave+(Rand(0,10))*sindir    
     If sinwave>360 Then 
        sindir=-1
        sinwave=360
        EndIf   
     If sinwave<0 Then 
        sindir=1
        sinwave=0
        EndIf   
      If Rand(20,40)=20 Then sindir=sindir*-1 
    For ii=0 To height
     Coswave=Coswave+(Rand(1,11))*cosdir
     If coswave>360 Then 
        cosdir=Rand(-1,-4)
        coswave=360
        EndIf   
     If coswave<0 Then 
        cosdir=Rand(1,4)
        coswave=0
        EndIf   
      If Rand(1,20)=10 Then cosdir=cosdir*-1 

    newcolor=((coswave)*amp)/3.14
    Color newcolor,newcolor,newcolor
    Plot i,ii
    Next
    Next

;ResizeImage image,Rand(64,100),Rand(64,100)
;ResizeImage image,width,height
SetBuffer BackBuffer()
End Function

Function Edgestozero(image)
    SetBuffer ImageBuffer(image)
    width=ImageWidth(image)
    height=ImageHeight(image)
    Color 0,0,0
    Line 0,0,WIDTH-1,0     
    Line 0,1,WIDTH-1,1     
    Line 0,2,WIDTH-1,2     
    Line 0,3,WIDTH-1,3     
    
    Line 0,HEIGHT-1,WIDTH-1,HEIGHT-1     
    Line 0,HEIGHT-2,WIDTH-1,HEIGHT-2     
    Line 0,HEIGHT-3,WIDTH-1,HEIGHT-3     
    Line 0,HEIGHT-4,WIDTH-1,HEIGHT-4     
    
    Line 0,0,0,HEIGHT-1     
    Line 1,0,1,HEIGHT-1     
    Line 2,0,2,HEIGHT-1    
    Line 3,0,3,HEIGHT-1     
    
    Line WIDTH-1,0,WIDTH-1,HEIGHT-1     
    Line WIDTH-2,0,WIDTH-2,HEIGHT-1     
    Line WIDTH-3,0,WIDTH-3,HEIGHT-1    
    Line WIDTH-4,0,WIDTH-4,HEIGHT-1     
SetBuffer BackBuffer()
End Function    

Function EdgeWALL(image)
    SetBuffer ImageBuffer(image)
    width=ImageWidth(image)
    height=ImageHeight(image)
    Color 255,255,255
    Line 0,0,WIDTH-1,0     
    Line 0,1,WIDTH-1,1     
    Line 0,2,WIDTH-1,2     
    Line 0,3,WIDTH-1,3     
    
    Line 0,HEIGHT-1,WIDTH-1,HEIGHT-1     
    Line 0,HEIGHT-2,WIDTH-1,HEIGHT-2     
    Line 0,HEIGHT-3,WIDTH-1,HEIGHT-3     
    Line 0,HEIGHT-4,WIDTH-1,HEIGHT-4     
    
    Line 0,0,0,HEIGHT-1     
    Line 1,0,1,HEIGHT-1     
    Line 2,0,2,HEIGHT-1    
    Line 3,0,3,HEIGHT-1     
    
    Line WIDTH-1,0,WIDTH-1,HEIGHT-1     
    Line WIDTH-2,0,WIDTH-2,HEIGHT-1     
    Line WIDTH-3,0,WIDTH-3,HEIGHT-1    
    Line WIDTH-4,0,WIDTH-4,HEIGHT-1     
SetBuffer BackBuffer()
End Function    


Function Createbackup()
   savework("BackupPoint"+Str$(Current_backup)+".rct")
   If Selected_Backup<Current_backup Then Current_backup=Selected_Backup
   Selected_Backup=Current_backup+1
   Current_backup=Current_backup+1
  End Function
  
Function UndoMap()
   If Selected_Backup>0 Then Selected_Backup=Selected_Backup-1
   ChangeDir(thispath$)
    sysmsg$="Attemping Undo" 
    sysmsgtimer#=MilliSecs()+3000
	If FileType ("DATA\RCTE\RCTE_SAVED\"+"BackupPoint"+Str$(Selected_Backup)+".rct")=1 Then  LOADWORK("BackupPoint"+Str$(Selected_Backup)+".rct")
 End Function

Function RedoMap()
   Selected_Backup=Selected_Backup+1
   If Selected_Backup>Current_backup Then Selected_Backup=Current_backup
   ChangeDir (thispath$)
   If FileType ("DATA\RCTE\RCTE_SAVED\"+"BackupPoint"+Str$(Selected_Backup)+".rct")=1 Then
		LOADWORK("BackupPoint"+Str$(Selected_Backup)+".rct")
    EndIf
 End Function

Function clearundos()
ChangeDir thispath$+savepath$
myDir=ReadDir(thispath$+savepath$) 
Repeat 
file$=NextFile$(myDir) 
If file$="" Then Exit 
If Upper$(Left$(FILE$,11))="BACKUPPOINT" Then DeleteFile file$
Forever 
CloseDir myDir  
ChangeDir thispath$
End Function

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;                 Functions                   ;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


Function vertexcoords2(surface,index,x#,y#,z#)
Local t.vertexupd
t.vertexupd=New vertexupd
t\commandindex=1
t\surface=surface
t\index=index
t\x=x
t\y=y
t\z=z
End Function


Function updatemeshes()
Return
Local t.vertexupd
Local dogrouped=1
For t.vertexupd=Each vertexupd
Select t\commandindex
Case 1
VertexCoords t\surface,t\index,t\x,t\y,t\z
Case 2
VertexTexCoords t\surface,t\index,t\u,t\v,t\w,t\coordset
Case 3
VertexColor t\surface,t\index,t\r,t\g,t\b,t\a
End Select
Delete t
Next
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


Function strip_path$(strings$)
	;strip path of the texfile
	For i2= Len(strings$) To 1 Step -1
		If Mid$(strings$,i2,1)="\"
			strings$=Right$(strings$,Len(strings$)-i2)
			Exit
		EndIf
	Next
    Return strings$
End Function

Function Buttoncheck()


If KeyDown(201) Then  FlattenHEIGHT=FlattenHEIGHT+1
If KeyDown(209) Then  FlattenHEIGHT=FlattenHEIGHT-1

GUI_VARIABLE_CAMERASPEED#=FUI_SendMessage(GUI_SLIDERWIN_CAMERASPEED,M_GETVALUE)
;------------------------------------
NEWCAMRANGE#=FUI_SendMessage(GUI_SLIDERWIN_CAMERARANGE,M_GETVALUE)	
If CAMRANGE#<>NEWCAMRANGE# Then 
CAMRANGE#=NEWCAMRANGE#
CameraRange Cam,1,CAMRANGE#
CameraFogMode Cam,True
CameraFogRange Cam,1,CAMRANGE
CameraFogColor Cam,200,200,200
EndIf
;------------------------------------
If oldbb_speed#>0 Then 
fadespeed#=oldbb_speed#
EndIf 
oldbb_speed#=-1.0
;------------------------------------
brushsize = FUI_SendMessage(GUI_SLIDERWIN_BRUSHSIZE,M_GETVALUE)	
If brushsize>0 Then brushsize=brushsize/5
fadespeed = FUI_SendMessage(GUI_SLIDERWIN_BRUSHSPEED,M_GETVALUE)
fadespeed=fadespeed*.001
;------------------------------------

If FUI_SendMessage(GUI_MENUEDIT_PAINTHOLE,m_getchecked)=False Then  
Holebrush=0
Else
Holebrush=1
setholecolor
EndIf
;----------------------------------------
For e.Event = Each event
Select e\EventID
Case GUI_MENUFILE_Exit
yn=Fui_confirm("Quit program","Yes","No")
If yn=1 Then 
ChangeDir thispath$
f=WriteFile("DATA\RCTE\Settings.dat")
WriteInt (f,BillBoardMode)
CloseFile(f)
ClearWorld(True,True)
EndGraphics()
End 
EndIf
;-------
Case GUI_MENUFILE_SaveHmap
TERRTOHM()
Case GUI_MENUFILE_COLORMAP
VertexAlpha_texture()
Case GUI_MENUFILE_GUECOLORMAP
VertexAlpha_texture(1)
Case GUI_MODEMODEL_TREE
FUI_SendMessage(GUI_TREEWIN,M_OPEN)
Case GUI_TREEWIN_COLOR
tColor = FUI_ColorDialog( tRed, tGreen, tBlue )
If tColor = True Then
TRed = FUI_GetRed( App\CurrentRGB )
TGreen = FUI_GetGreen( App\CurrentRGB )
TBlue = FUI_GetBlue( App\CurrentRGB )

TIMAGE = CreateImage( 70, 45 )
SetBuffer ImageBuffer(timage)
Color tred,tgreen,tblue
Rect 0, 0, 70, 45,1
FUI_DeleteGadget(GUI_TREEWIN_CANVAS)
GUI_TREEWIN_CANVAS = FUI_ImageBox( GUI_TREEWIN, 125, 90, 68, 45, TIMAGE )
SetBuffer BackBuffer()
FreeImage timage 

tID = Int(FUI_SendMessage(GUI_TREEWIN_SEASON,M_GETVALUE))-1 
GUI_ARRAY_SEASONCOLOR( tID, 0 ) = tRed
GUI_ARRAY_SEASONCOLOR( tID, 1 ) = tGreen
GUI_ARRAY_SEASONCOLOR( tID, 2 ) = tBlue
For i=0 To 11
season_red(i)=GUI_ARRAY_SEASONCOLOR( i, 0 )
season_green(i)=GUI_ARRAY_SEASONCOLOR( i,1 )
season_blue(i)=GUI_ARRAY_SEASONCOLOR( i,2 )  
Next

EndIf

;-------
Case GUI_TOPWIN_RESETCAM
PositionEntity Cam, 0, 180, 0
RotateEntity Cam,0,0,0
TurnEntity Cam, 90, 0, 0
;-------
Case GUI_TOPWIN_RESETFLAT
FlattenHEIGHT=0
;-------
Case GUI_MODEEDIT_HIDEMODELS
For dm.dropmodel=Each dropmodel
HideEntity dm\ent
Next
;-------
Case GUI_MODEEDIT_showMODELS
For dm.dropmodel=Each dropmodel
ShowEntity dm\ent
Next
;-------
Case  GUI_MODEMODEL_LOAD
ChangeDir THISPATH$
CameraProjMode Cam, False
CameraProjMode App\Cam, True
Tmodel = ChooseMeshDialog(MeshDialog_All,"",75,125)
DebugLog "tmodel is "+TMODEL
;tModel = ChooseMeshDialog( 0 )
If tModel <> -1 Then
Mc_pitch=0
mc_yaw=0
mc_roll=0
Mc_sx=1
mc_sy=1
mc_sz=1
Mc_onoff=0
sceneryID=TMODEL
sceneryname$=getmeshname(tmodel)
selected$=sceneryname$ 
DebugLog "scenery name is "+sceneryname$
EndIf
;-------
Case gui_menuedit_lightmap
FUI_SendMessage(gui_lightwin,m_open)         	
Case Gui_Lightwin_cancel
FUI_SendMessage(gui_lightwin,m_close)         	
Case Gui_Lightwin_ok
FUI_SendMessage(gui_lightwin,m_close)         	
LmAmbient_terr= FUI_SendMessage(gui_lightwin_ambient,m_getvalue)
lmblur_terr= FUI_SendMessage(gui_lightwin_smooth,m_getvalue)
LmAmbient_terr=LmAmbient_terr*2
lightmapit2(Cam)


;-------
Case GUI_MODEPAINT_GETCOLOR
tColor = FUI_ColorDialog( tRed, tGreen, tBlue )
If tColor = True Then
c_red = FUI_GetRed( App\CurrentRGB )
c_green = FUI_GetGreen( App\CurrentRGB )
c_blue = FUI_GetBlue( App\CurrentRGB )
EndIf  
;-------
Case GUI_MBWIN_ADD
For dm.dropmodel = Each dropmodel
If dm\ent=selected_dropmodel And Trim$(getmeshname(dm\id))<>""
mbr.modelbrush=New ModelBrush
mbr\id=dm\id
mbr\name$=getmeshname(mbr\id)
If Len(mbr\name$)>2 Then 
mbr\name$=Left$(mbr\name$,Len(mbr\name$)-1)
EndIf
mbr\rcte$=dm\rcTe$
mbr\sx=dm\sx
mbr\sy=dm\sy
mbr\sz=dm\sz
updatemodelbrushlist() 
EndIf
Next 

;-------
Case GUI_MBWIN_ADD 
checked.window=Null 
For mbr.modelbrush=Each ModelBrush
Delete mbr
Next
updatemodelbrushlist() 

;-------
Case GUI_MBWIN_LOAD 
ChangeDir thispath$
tFile = FUI_OpenDialog( "Load model brush list", "Data\RCTE\RCTE_SAVED", "Model brush (*.mbr)|*.mbr")
If tFile = True Then
map$=app\currentfile
If FileType(map$)=1 Then LoadModelBrush map$
EndIf 
;-------
Case GUI_MBWIN_SAVE
map$=""
ChangeDir thispath$
tFile = FUI_SaveDialog( "Save model brush list", "Data\RCTE\RCTE_SAVED", "Model brush (*.mbr)|*.mbr")
If tFile = True Then
map$=app\currentfile
If FileType (map$)=1 Then 
yn=fui_confirm("overwrite file ?","Yes","No") 
If yn=1 Then savemodelbrush map$
Else 
If map$<>"" Then 
	If Instr( Lower$(map$), ".mbr", 1 ) > 1 Then
DebugLog "saving : "+map$
savemodelbrush (map$)
Else
map$ = map$ + ".mbr"
savemodelbrush (map$)
EndIf
EndIf
EndIf
EndIf
;-------
Case GUI_MBWIN_CLEAR
FUI_DeleteGadget GUI_MBWIN_LISTBOX:FUI_Update( )
FUI_Update()
GUI_MBWIN_LISTBOX = FUI_ListBox( GUI_MBWIN, 225, 25, GUI_VARIABLE_LEFTBAR_WIDTH + 20, 175, False, True )
TotalmodelBrush=0
Delete Each ModelBrush
;-------
Case GUI_HELP_ABOUT
aboutwindow()
;-------
Case GUI_HELP_CONTROLS
controlswindow()
;-------
Case GUI_TREEWIN_CANCEL
FUI_SendMessage(GUI_TREEWIN,M_CLOSE)
;-------
Case GUI_MODEMODEL_TREE
FUI_SendMessage(GUI_TREEWIN,M_OPEN)
;-------
Case GUI_TREEWIN_OK
;TreeGrass = 0, Sway = 0, Evergreen = 0
If FUI_SendMessage(GUI_TREEWIN_TREESET,M_GETCHECKED)=True Then 
TreeGrass = 0
ElseIf FUI_SendMessage(GUI_TREEWIN_GRASSSET,M_GETCHECKED)=True Then 
TreeGrass = 1
EndIf

If FUI_SendMessage(GUI_TREEWIN_SWAYTOP,M_GETCHECKED)=True Then 
Sway = 1
ElseIf  FUI_SendMessage(GUI_TREEWIN_SWAYMID,M_GETCHECKED)=True Then 
Sway = 0
ElseIf  FUI_SendMessage(GUI_TREEWIN_SWAYLOW,M_GETCHECKED)=True Then
Sway = 2
EndIf

If FUI_SendMessage(GUI_TREEWIN_EVERGREEN,M_GETCHECKED)=True Then
Evergreen = 1
ElseIf FUI_SendMessage(GUI_TREEWIN_SEASONAL,M_GETCHECKED)=True Then
Evergreen = 0
EndIf

FlushKeys( )
FlushMouse( )
If treegrass=0 Then prim$="_TREE" Else prim$="_GRSS"
temprcte$=prim$+Str$(sway)+Str$(evergreen)
For dm.dropmodel=Each dropmodel
If dm\ent=selected_dropmodel Then 
dm\rcTe$=temprcte$
EndIf
Next
FUI_SendMessage(GUI_TREEWIN,M_CLOSE)
;-------
Case GUI_MENUBACKUP_CREATE
Createbackup()
;-------
Case GUI_MENUBACKUP_UNDO
UndoMap()
;-------
Case GUI_MENUBACKUP_REDO
	RedoMap()
;-------
Case GUI_MENUFILE_OPEN    
	
	LOADWORK()
	EntityAlpha SkyEN, 0
	EntityAlpha CloudEN, 0
	EntityAlpha StarsEN, 0
	assigntexture 1,"null"
	ChangeDir thispath$

; ----------
;Case GUI_MENUFILE_SAVE
;	savemap$=""
;	ChangeDir thispath$
;	tFile = FUI_SaveDialog( "Save RCTE map", "Data\RCTE\RCTE_SAVED", "RCTE Map (*.rct)|*.rct")
;	If tFile = True
;		savemap$=app\currentfile
;		If savemap$<>""
;			If Instr( Lower$(savemap$), ".rct", 1 ) > 1 Then
;				DebugLog "saving : "+savemap$
;				SaveLandscape (savemap$, 0, 0)
;			Else
;				savemap$ = savemap$ + ".rct"
;				SaveLandscape (savemap$, 0, 0)
;			EndIf
;		EndIf
;	EndIf
;	ChangeDir thispath$

;- Zone Export
;Case GUI_MENUFILE_EXPORT
Case GUI_MENUFILE_SAVE	
	Exportmap$=""
	ChangeDir thispath$
	tFile = FUI_SaveDialog( "Export map To RC area", "Data\Areas", "RC Area (*.dat)|*.dat")
	If tFile = True Then
		doEncrypt = FUI_Confirm("Do you want to encrypt the terrain?","Yes","No") ; 1 for Yes, -1 for No
		exportmap$=app\currentfile
		If exportmap$<>""
			If Instr( Lower$(exportmap$), ".dat", 1 ) > 1 Then
				DebugLog "exporting : "+exportmap$
				SaveLandscape (exportmap$,1, doEncrypt)
			Else
				exportmap$ = exportmap$ + ".dat"
				DebugLog "exporting : "+exportmap$
				SaveLandscape (exportmap$,1, doEncrypt)
			EndIf
		EndIf
	EndIf
	ChangeDir thispath$

;- New Map
Case GUI_MENUFILE_NEW
	selectedlayer = 1
	Res = ChooseNewMap()
	For i = 4 To 6
	    FUI_ShowGadget(GUI_BOTTOMWIN_TEXTHUMB(i))
	Next
	numlayers = 6
	
	use_tex$(1)="TEX1.PNG"
	use_tex$(2)="TEX2.PNG"
	use_tex$(3)="TEX3.PNG"
	assigntexture 1,"tex1.png"
	assigntexture 2,"tex2.png"
	assigntexture 3,"tex3.png"
	
	; The layers are done with the 90% usage assumption -> its assumed that textures not not be used on more than 90% of the terrain on average, beside the base texture that uses 100%
	Select Res
		Case -1
			; No new map
			Return
			
		Case 0
			; 32x32
			newmap( 32 )
			use_tex$(4)="TEX4.PNG"
			use_tex$(5)="TEX5.PNG"
			use_tex$(6)="TEX6.PNG"
			assigntexture 4,"tex4.png"
			assigntexture 5,"tex5.png"
			assigntexture 6,"tex6.png"
			
		Case 1
			; 64x64
			newmap( 64 )
			use_tex$(4)="TEX4.PNG"
			use_tex$(5)="TEX5.PNG"
			use_tex$(6)="TEX6.PNG"
			assigntexture 4,"tex4.png"
			assigntexture 5,"tex5.png"
			assigntexture 6,"tex6.png"
			
		Case 2
			; 84x84, 5 textures
			numlayers = 5
	    	FUI_HideGadget(GUI_BOTTOMWIN_TEXTHUMB(6))
			newmap( 84 )
			use_tex$(4)="TEX4.PNG"
			use_tex$(5)="TEX5.PNG"
			assigntexture 4,"tex4.png"
			assigntexture 5,"tex5.png"
			
		Case 3
			; 94x94, 4 textures
			numlayers = 4
	    	FUI_HideGadget(GUI_BOTTOMWIN_TEXTHUMB(6))
	    	FUI_HideGadget(GUI_BOTTOMWIN_TEXTHUMB(5))
			newmap( 94 )
			use_tex$(4)="TEX4.PNG"
			assigntexture 4,"tex4.png"
			
		Case 4
			; 108x108, 3 textures
			numlayers = 3
	    	FUI_HideGadget(GUI_BOTTOMWIN_TEXTHUMB(6))
	    	FUI_HideGadget(GUI_BOTTOMWIN_TEXTHUMB(5))
	   		FUI_HideGadget(GUI_BOTTOMWIN_TEXTHUMB(4))
			newmap( 108 )
	End Select
	
	ChangeDir thispath$               


;-------
Case GUI_MODEMODEL_THICKEN
NatureDensity(1) 
;-------
Case GUI_MODEMODEL_THIN
NatureDensity(-1) 
;-------
Case GUI_MODEMODEL_SCALEUP
NatureScale(1) 
;-------
Case GUI_MODEMODEL_SCALEDOWN
NatureScale(-1) 
;-------
Case GUI_MENUEDIT_RANDOM
SeedRnd MilliSecs()
RandTerrain()
;-------
Case GUI_MENUEDIT_SMOOTH
smoothall()
;-------
Case GUI_MENUEDIT_HEIGHTMAP
map$=""
ChangeDir thispath$
tFile = FUI_OpenDialog( "Load Heightmap", "Data\RCTE\RCTE_TEXTURES", "Bitmap (*.bmp)|*.bmp|Jpeg (*.jpg)|*.jpg|PNG (*.png)|*.png")
If tFile = True Then
map$=app\currentfile
If FileType (map$) =1 Then loadhmap map$
EndIf
ChangeDir thispath$                   

;-------
Case GUI_MENUEDIT_DROPMODELS
Createbackup()
Flopmodels()
;-------
Case GUI_MODEMODEL_DELETE
Deletemodel(101)
;-------
Case GUI_MODEMODEL_COPY
copymodel()
;-------
Case GUI_BOTTOMWIN_AUTOTEXTURE
autotexture()				
;-------
Case GUI_MENUEDIT_SCALEDOWN
Scaleterrain .9
;-------
Case GUI_MENUEDIT_SCALEUP
Scaleterrain 1.1
;-----
;ElseIf checked.window=lightmapok.window Then 
;    checked.window=Null
;    gui_deletewindow(lightmapoptions.window)
;	lmambient_terr= gui_getspinnervalue(lightmapambient.spinner)
;	lmblur_terr= gui_getspinnervalue(lightmapsmooth.spinner)
;	lmambient_terr=lmambient_terr*2
;	lightmapit2(CAM)
;-----
;Global GUI_BOTTOMWIN_MINHEIGHT 
;Global GUI_BOTTOMWIN_MAXHEIGHT 
;Global GUI_BOTTOMWIN_MINSLOPE  
;Global GUI_BOTTOMWIN_MAXSLOPE

Case GUI_BOTTOMWIN_MINSLOPE
	DTexMinSlope(selectedlayer) =   FUI_SendMessage(GUI_BOTTOMWIN_MINSLOPE,M_GETVALUE)

Case GUI_BOTTOMWIN_MAXSLOPE
	DTexMaxSlope(selectedlayer) =   FUI_SendMessage(GUI_BOTTOMWIN_MAXSLOPE,M_GETVALUE)

Case GUI_BOTTOMWIN_MINHEIGHT
	DTexMinHeight(selectedlayer) =  FUI_SendMessage(GUI_BOTTOMWIN_MINHEIGHT,M_GETVALUE)

Case GUI_BOTTOMWIN_MAXHEIGHT
	DTexMaxHeight(selectedlayer) =  FUI_SendMessage(GUI_BOTTOMWIN_MAXHEIGHT,M_GETVALUE)


Case GUI_BOTTOMWIN_TEXTHUMB(1)
	DTexMinSlope(selectedlayer) =   FUI_SendMessage(GUI_BOTTOMWIN_MINSLOPE,M_GETVALUE)
	DTexMaxSlope(selectedlayer) =   FUI_SendMessage(GUI_BOTTOMWIN_MAXSLOPE,M_GETVALUE)
	DTexMinHeight(selectedlayer) =  FUI_SendMessage(GUI_BOTTOMWIN_MINHEIGHT,M_GETVALUE)
	DTexMaxHeight(selectedlayer) =  FUI_SendMessage(GUI_BOTTOMWIN_MAXHEIGHT,M_GETVALUE)
    selectedlayer=1 
    DebugLog "over 1"
    FUI_SendMessage(GUI_BOTTOMWIN_MINSLOPE,M_SETVALUE,DTexMinSlope(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MAXSLOPE,M_SETVALUE,DTexMaxSlope(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MINHEIGHT,M_SETVALUE,DTexMinHeight(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MAXHEIGHT,M_SETVALUE,DTexMaxHeight(selectedlayer))
    assigntexture 1,"null"
;-----
Case GUI_BOTTOMWIN_TEXTHUMB(2)
	DTexMinSlope(selectedlayer) =   FUI_SendMessage(GUI_BOTTOMWIN_MINSLOPE,M_GETVALUE)
	DTexMaxSlope(selectedlayer) =   FUI_SendMessage(GUI_BOTTOMWIN_MAXSLOPE,M_GETVALUE)
	DTexMinHeight(selectedlayer) =  FUI_SendMessage(GUI_BOTTOMWIN_MINHEIGHT,M_GETVALUE)
	DTexMaxHeight(selectedlayer) =  FUI_SendMessage(GUI_BOTTOMWIN_MAXHEIGHT,M_GETVALUE)
    selectedlayer=2 
    DebugLog "over 2"
    FUI_SendMessage(GUI_BOTTOMWIN_MINSLOPE,M_SETVALUE,DTexMinSlope(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MAXSLOPE,M_SETVALUE,DTexMaxSlope(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MINHEIGHT,M_SETVALUE,DTexMinHeight(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MAXHEIGHT,M_SETVALUE,DTexMaxHeight(selectedlayer))
    assigntexture 2,"null"
;-----
Case GUI_BOTTOMWIN_TEXTHUMB(3)
	DTexMinSlope(selectedlayer) =   FUI_SendMessage(GUI_BOTTOMWIN_MINSLOPE,M_GETVALUE)
	DTexMaxSlope(selectedlayer) =   FUI_SendMessage(GUI_BOTTOMWIN_MAXSLOPE,M_GETVALUE)
	DTexMinHeight(selectedlayer) =  FUI_SendMessage(GUI_BOTTOMWIN_MINHEIGHT,M_GETVALUE)
	DTexMaxHeight(selectedlayer) =  FUI_SendMessage(GUI_BOTTOMWIN_MAXHEIGHT,M_GETVALUE)
    selectedlayer=3 
    DebugLog "over 3"
    FUI_SendMessage(GUI_BOTTOMWIN_MINSLOPE,M_SETVALUE,DTexMinSlope(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MAXSLOPE,M_SETVALUE,DTexMaxSlope(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MINHEIGHT,M_SETVALUE,DTexMinHeight(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MAXHEIGHT,M_SETVALUE,DTexMaxHeight(selectedlayer))
    assigntexture 3,"null"
;-----
Case GUI_BOTTOMWIN_TEXTHUMB(4)
	DTexMinSlope(selectedlayer) =   FUI_SendMessage(GUI_BOTTOMWIN_MINSLOPE,M_GETVALUE)
	DTexMaxSlope(selectedlayer) =   FUI_SendMessage(GUI_BOTTOMWIN_MAXSLOPE,M_GETVALUE)
	DTexMinHeight(selectedlayer) =  FUI_SendMessage(GUI_BOTTOMWIN_MINHEIGHT,M_GETVALUE)
	DTexMaxHeight(selectedlayer) =  FUI_SendMessage(GUI_BOTTOMWIN_MAXHEIGHT,M_GETVALUE)
    selectedlayer=4 
    DebugLog "over 4"
    FUI_SendMessage(GUI_BOTTOMWIN_MINSLOPE,M_SETVALUE,DTexMinSlope(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MAXSLOPE,M_SETVALUE,DTexMaxSlope(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MINHEIGHT,M_SETVALUE,DTexMinHeight(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MAXHEIGHT,M_SETVALUE,DTexMaxHeight(selectedlayer))
    assigntexture 4,"null"
;-----
Case GUI_BOTTOMWIN_TEXTHUMB(5)
	DTexMinSlope(selectedlayer) =   FUI_SendMessage(GUI_BOTTOMWIN_MINSLOPE,M_GETVALUE)
	DTexMaxSlope(selectedlayer) =   FUI_SendMessage(GUI_BOTTOMWIN_MAXSLOPE,M_GETVALUE)
	DTexMinHeight(selectedlayer) =  FUI_SendMessage(GUI_BOTTOMWIN_MINHEIGHT,M_GETVALUE)
	DTexMaxHeight(selectedlayer) =  FUI_SendMessage(GUI_BOTTOMWIN_MAXHEIGHT,M_GETVALUE)
    selectedlayer=5 
    DebugLog "over 5"
    FUI_SendMessage(GUI_BOTTOMWIN_MINSLOPE,M_SETVALUE,DTexMinSlope(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MAXSLOPE,M_SETVALUE,DTexMaxSlope(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MINHEIGHT,M_SETVALUE,DTexMinHeight(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MAXHEIGHT,M_SETVALUE,DTexMaxHeight(selectedlayer))
    assigntexture 5,"null"
;-----
Case GUI_BOTTOMWIN_TEXTHUMB(6)
	DTexMinSlope(selectedlayer) =   FUI_SendMessage(GUI_BOTTOMWIN_MINSLOPE,M_GETVALUE)
	DTexMaxSlope(selectedlayer) =   FUI_SendMessage(GUI_BOTTOMWIN_MAXSLOPE,M_GETVALUE)
	DTexMinHeight(selectedlayer) =  FUI_SendMessage(GUI_BOTTOMWIN_MINHEIGHT,M_GETVALUE)
	DTexMaxHeight(selectedlayer) =  FUI_SendMessage(GUI_BOTTOMWIN_MAXHEIGHT,M_GETVALUE)
    selectedlayer=6 
    DebugLog "over 6"
    FUI_SendMessage(GUI_BOTTOMWIN_MINSLOPE,M_SETVALUE,DTexMinSlope(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MAXSLOPE,M_SETVALUE,DTexMaxSlope(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MINHEIGHT,M_SETVALUE,DTexMinHeight(selectedlayer))
    FUI_SendMessage(GUI_BOTTOMWIN_MAXHEIGHT,M_SETVALUE,DTexMaxHeight(selectedlayer))
    assigntexture 6,"null"
;-------
Case GUI_TPREVWIN_BUTTON
    FUIGet_Texture(selectedlayer)
;-------
Case GUI_PED_PAINTMODE
    If CURRENTLY_SELECTED_TERRAINEDIT<>1 Then Createbackup()
    EntityAlpha Boxmesh,0
	CURRENTLY_SELECTED_TERRAINEDIT = 1
    showmode(1)
;-------
Case GUI_PED_EDITMODE
    If CURRENTLY_SELECTED_TERRAINEDIT<>2 Then Createbackup()
    EntityAlpha Boxmesh,0
	CURRENTLY_SELECTED_TERRAINEDIT = 2
    showmode(2)
;-------
Case GUI_PED_MODELMODE
    If CURRENTLY_SELECTED_TERRAINEDIT<>3 Then Createbackup()
    EntityAlpha Boxmesh,0
	CURRENTLY_SELECTED_TERRAINEDIT = 3
    showmode(3)


Case GUI_MBWIN_CLOSE
     FUI_SendMessage( GUI_MBWIN, M_CLOSE )
Case GUI_TOPWIN_MBRUSH
      FUI_SendMessage( GUI_MBWIN, M_OPEN )
Case GUI_TOPWIN_MOVEMENT
 FUI_SendMessage(gui_movewin,m_open)

End Select
Delete E
Next
End Function

Function RCTE_CREATEQUAD(segs=1,double=False,parent=0)
	mesh=CreateMesh( parent )
	surf=CreateSurface( mesh )
	stx#=-.5
	sty#=stx
	stp#=Float(1)/Float(segs)
	y#=sty
	For a=0 To segs
		x#=stx
		v#=a/Float(segs)
		For b=0 To segs
			u#=b/Float(segs)
			AddVertex(surf,x,0,y,u,v) ; swap these for a different start orientation
			x=x+stp
		Next
		y=y+stp
	Next
	For a=0 To segs-1
		For b=0 To segs-1
			v0=a*(segs+1)+b:v1=v0+1
			v2=(a+1)*(segs+1)+b+1:v3=v2-1
			AddTriangle( surf,v0,v2,v1 )
			AddTriangle( surf,v0,v3,v2 )
		Next
	Next
	UpdateNormals mesh
	If double=True Then EntityFX mesh,16
    ScaleMesh mesh,1,1,1
	Return mesh
End Function

Function FitEntity(EN1,EN2,cl=0)
 FreeEntity Boxmesh
 Boxmesh=CreateCube()
 If cl=0 Then 
 EntityColor Boxmesh,255,0,0
 Else
 EntityColor Boxmesh,0,0,255
 EndIf
 PositionEntity Boxmesh,-9999,99999,-99999
 EntityAlpha Boxmesh,.6
 EN1=Boxmesh

    vx# = GetMatElement#(EN2, 0, 0)
	vy# = GetMatElement#(EN2, 0, 1)
	vz# = GetMatElement#(EN2, 0, 2)
	XScale# = Sqr#(vx# * vx# + vy# * vy# + vz# * vz#)
	vx# = GetMatElement#(EN2, 1, 0)
	vy# = GetMatElement#(EN2, 1, 1)
 	vz# = GetMatElement#(EN2, 1, 2)
	YScale# = Sqr#(vx# * vx# + vy# * vy# + vz# * vz#)
	vx# = GetMatElement#(EN2, 2, 0)
	vy# = GetMatElement#(EN2, 2, 1)
	vz# = GetMatElement#(EN2, 2, 2)
	ZScale# = Sqr#(vx# * vx# + vy# * vy# + vz# * vz#)

Vy1#=Highvert(EN2)
Vy2#=LowVert(EN2)
Vx1#=LeftVert(EN2)
Vx2#=RightVert(EN2)
Vz1#=Backvert(en2)
Vz2#=Frontvert(en2)
wide#=Vx2#-vx1#
tall#=Vy2#-vy1#
deep#=Vz2#-vz1#

FitMesh en1,vx1,vy1,vz1,wide,tall,deep
RotateEntity en1,EntityPitch(en2,1),EntityYaw(en2,1),EntityRoll(en2,1)
ScaleEntity en1,xscale,yscale,zscale
PositionEntity en1,EntityX(en2,1),EntityY(en2,1),EntityZ(en2,1)
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






Function updatemodelbrushlist()
FUI_DeleteGadget GUI_MBWIN_LISTBOX:FUI_Update( )
GUI_MBWIN_LISTBOX = FUI_ListBox( GUI_MBWIN, 225, 25, GUI_VARIABLE_LEFTBAR_WIDTH + 20, 175, False, True )
TotalmodelBrush=0
For mbr.modelbrush=Each ModelBrush
     TotalmodelBrush=TotalmodelBrush+1
     FUI_ListBoxItem( GUI_MBWIN_LISTBOX, mbr\name$,False,True)
  Next
End Function

Function PaintModels(flag)

If ModelBrushTimer#>MilliSecs() Then Return
ModelBrushTimer#=MilliSecs()+(200-(Float(FUI_SendMessage(GUI_SLIDERWIN_BRUSHSPEED,M_GETVALUE))))

oldsceneryid=sceneryID
oldmconoff=Mc_onoff
Mc_onoff=0
 ;center point
thisv=Terrainscale-55
bsz#=Float(brushsize)*.5
moffval#=((bsz)*(thisv))
 
m_minx#=(-moffval)*1.2
m_maxx#=moffval*2
m_minz#=(-moffval)*1.2
m_maxz#=moffval*2

  mbcp=0
  mbcp=CameraPick (cam,MouseX(),MouseY())
  If mbcp Then 
If flag=0 Then 
  ;create scale factor
  thisscalestart#=Float(FUI_SendMessage(GUI_MBWIN_SCALE,M_GETVALUE))*.01
  thisscalevar#=Float(FUI_SendMessage(GUI_MBWIN_SCALEV,M_GETVALUE))*.01
  ;choose random model from our list
  thism=Rand(1,totalmodelbrush)
  mbrc=0
  For mbr.modelbrush=Each modelbrush
   mbrc=mbrc+1
   If mbrc=thism Then
    Sceneryid=mbr\id
    success=dropmodel (0,1000,0)  
    If success<>-1 Then 
      thisdm.dropmodel=Last dropmodel
      thisdm\sx=(mbr\sx*thisscalestart#)*thisscalevar#
      thisdm\sy=(mbr\sy*thisscalestart#)*thisscalevar#
      thisdm\sz=(mbr\sz*thisscalestart#)*thisscalevar#
      ScaleEntity thisdm\ent,thisdm\sx,thisdm\sy,thisdm\sz
      thisdm\rcte$=mbr\rcte$
      TurnEntity thisdm\ent,0,Rand(-180,180),0
      this_x#=Rnd(m_minx,m_maxx)  
      this_z#=Rnd(m_minz,m_maxz)  
      LinePick PickedX()+this_x,PickedY()+10,PickedZ()+this_z,0,-1000,0
      PositionEntity thisdm\ent,PickedX(),PickedY()-.1,PickedZ()
      ColorEntity thisdm\ent
      EntityAlpha Boxmesh,0
      paintmodelcount=paintmodelcount+1
      If paintmodelcount=100 Then
        paintmodelcount=0
        Createbackup()
        EndIf
    EndIf
    EndIf
  Next
ElseIf flag =1 
temppiv=CreatePivot()
EntityAlpha Boxmesh,0
PositionEntity temppiv,PickedX(),PickedY()+.2,PickedZ()
For dm.dropmodel=Each dropmodel
 If EntityDistance (dm\ent,temppiv)<(moffval*6) Then 
         FreeEntity dm\ent
         Delete dm
         totaldrops=totaldrops-1
         EndIf
		Next
FreeEntity tempppiv
EndIf
 EndIf

End Function

Function savemodelbrush(mfilename$)
f=WriteFile(mfilename$)
 WriteInt f,Int(FUI_SendMessage(GUI_MBWIN_SCALE,M_GETVALUE))
 WriteInt f,Int(FUI_SendMessage(GUI_MBWIN_SCALEV,M_GETVALUE))
 thiscount=0
 For mbr.modelbrush=Each ModelBrush
   thiscount=thiscount+1 
   Next
   WriteInt f,thiscount
 For mbr.modelbrush=Each ModelBrush
   WriteString f,MBR\Name$
   WriteInt  f,MBR\Id
   WriteString f,MBR\rcte$
   WriteFloat f,mbr\sx#
   WriteFloat f,mbr\sy#
   WriteFloat f,mbr\sz#
   Next
CloseFile F
End Function

Function LoadModelBrush(mfilename$)
Delete Each ModelBrush
DebugLog "LOADING BRUSH"
f=OpenFile(mfilename$)
 VAL1=ReadInt(F)
 VAL2=ReadInt(F)
 FUI_SendMessage(GUI_MBWIN_SCALE,M_SETVALUE,VAL1)
 FUI_SendMessage(GUI_MBWIN_SCALEV,M_SETVALUE,VAL2)
 totalof=ReadInt(f)
DebugLog "TOTAL OF IS "+TOTALOF
For i=1 To totalof
  mbr.modelbrush=New ModelBrush
  mbr\name$=ReadString$(f) 
  mbr\id=ReadInt(f)
  mbr\rcte$=ReadString$(f)
  mbr\sx#=ReadFloat(f)
  mbr\sy#=ReadFloat(f)
  mbr\sz#=ReadFloat(f)
Next
CloseFile F
updatemodelbrushlist()
End Function

Function ColorEntity(Ent)
 LinePick EntityX(ent,1),EntityY(ent,1)-10,EntityZ(ent,1),0,-100,0 
 S=PickedSurface()
 t=PickedTriangle()
 EntityFX ent,1
 If s Then 

 vr1=VertexRed(s,TriangleVertex(s,t,0))
 vr2=VertexRed(s,TriangleVertex(s,t,1))
 vr3=VertexRed(s,TriangleVertex(s,t,2))

 vg1=VertexGreen(s,TriangleVertex(s,t,0))
 vg2=VertexGreen(s,TriangleVertex(s,t,1))
 vg3=VertexGreen(s,TriangleVertex(s,t,2))

 vb1=VertexBlue(s,TriangleVertex(s,t,0))
 vb2=VertexBlue(s,TriangleVertex(s,t,1))
 vb3=VertexBlue(s,TriangleVertex(s,t,2))

 vr4=(vr1+vr2+vr3)*.33
 vg4=(vg1+vg2+vg3)*.33
 vb4=(vb1+vb2+vb3)*.33

 EntityColor ent,vr4,vg4,vb4 
 EndIf

End Function 

;gui_setgroup(effectgrasscheck.window,21)
;gui_setgroup(effecttreecheck.window,21)
Function NatureScale(flag)
If flag=1 Then gsval#=1.1 Else gsval#=.9
For Dm.Dropmodel=Each dropmodel
 If FUI_SendMessage(GUI_MODEMODEL_GRASSCHECK,M_GETCHECKED)=True Then 
	 If Left$(dm\rcte$,5)="_GRSS" Then 
      dm\sx=dm\sx*gsval
      dm\sy=dm\sy*gsval
      dm\sz=dm\sz*gsval
      ScaleEntity dm\ent,dm\sx,dm\sy,dm\sz 
	 EndIf
 ElseIf FUI_SendMessage(GUI_MODEMODEL_TREECHECK,M_GETCHECKED)=True Then 
	 If Left$(dm\rcte$,5)="_TREE" Then 
      dm\sx=dm\sx*gsval
      dm\sy=dm\sy*gsval
      dm\sz=dm\sz*gsval
      ScaleEntity dm\ent,dm\sx,dm\sy,dm\sz 
	 EndIf
 EndIf
Next 
End Function

Function NatureDensity(flag)
createbackup()
EntityPickMode layer(1),2
For dm.dropmodel=Each dropmodel
aok=0
If flag=1 Then 

 If FUI_SendMessage(GUI_MODEMODEL_GRASSCHECK,M_GETCHECKED)=True Then 
		 If Left$(dm\rcTe$,5)="_GRSS" Then  
	        densx#=Rand(-3,3)
	        densz#=Rand(-3,3)
	        aok=1
            yoff#=.5
          EndIf
		 Else
  	     If Left$(dm\rcTe$,5)="_TREE" Then  
	        densx#=Rand(-10,10)
	        densz#=Rand(-10,10)
	        aok=1
            yoff#=2
	      EndIf  
		EndIf

    ThisDx#=EntityX(dm\ent,1)+densx#
    ThisDz#=EntityZ(dm\ent,1)+densz#
    DEN=LinePick (thisdx,EntityY(dm\ent,1)+200,thisdz,0,-1000,0 )
    If Rand(1,100)>20 Then aok=0
    If DEN<>0 And AOK=1 Then 
	    newy#=PickedY()-yoff#
        dm2.dropmodel=New dropmodel
        dm2\shadowent=dm\shadowent
        dm2\ent=CopyEntity(dm\ent)
        dm2\id=dm\id
        dm2\modelfilename$=dm\ModelFileName$
        dm2\modelfilepath$=dm\modelfilepath$
        dm2\x#=  EntityX(dm\ent,1)+densx#
        dm2\y#=newy#
        dm2\z#= EntityZ(dm\ent,1)+densz#
        dm2\sx=dm\sx            
        dm2\sy=dm\sy            
        dm2\sz=dm\sz            
        dm2\alpha#=dm\Alpha#
        dm2\rcte$=dm\rcTe$   
        PositionEntity dm2\ent,thisdx,newy#,thisdz 
        TurnEntity DM2\ENT,0,Rand(-180,180),0 
        ColorEntity dm2\ent
      EndIf 

        Else ;flag <>1

     If FUI_SendMessage(GUI_MODEMODEL_GRASSCHECK,M_GETCHECKED)=True Then 
 		          If Left$(dm\rcte$,5)="_GRSS" Then  
                      delme=Rand(1,10)
                      If delme=<2 Then 
				         FreeEntity dm\ent
				         EntityAlpha boxmesh,0
				         Delete dm
				         totaldrops=totaldrops-1
                      EndIf
                      EndIf
                   Else  
 		          If Left$(dm\rcte$,5)="_TREE" Then  
                      delme=Rand(1,10)
                      If delme=<2 Then 
				         FreeEntity dm\ent
				         EntityAlpha Boxmesh,0
				         Delete dm
				         totaldrops=totaldrops-1
                      EndIf
                      EndIf

       EndIf 
       EndIf
Next

End Function

Function VertexAlpha_texture(flags=0)
oldcamx#=EntityX(Cam,1)
oldcamy#=EntityY(Cam,1)
oldcamz#=EntityZ(Cam,1)
oldcam_pitch#=EntityPitch(Cam,1)
oldcam_yaw#=EntityYaw(Cam,1)
oldcam_roll#=EntityRoll(Cam,1)

savework "tempsave.rct"
    texture$=""
    ChangeDir thispath$
	tFile = FUI_SaveDialog( "Save Texture", "Data\RCTE\RCTE_Textures", "Bitmap (*.bmp)|*.bmp")
    texture$=app\currentfile
    YN=1
    If FileType(texture$)=1 Or FileType(texture$)+".bmp"=1 Then 
    YN=fui_confirm("overwrite ?","Yes","No")
    EndIf
  If texture$<>"" And YN=1
  If Lower$(Right$(texture$,3))<>"bmp" Then texture$=texture$+".bmp"
  sfilename3$=texture$
  Else
  Return
  EndIf

 For dm.dropmodel=Each dropmodel
 HideEntity dm\ent
 Next
 HideEntity Boxmesh
 HideEntity areaRect
 HideEntity Ruler

YN=fui_confirm("Clear Layers ?","Yes","No")


For i=0 To lsegments
 For ii=0 To lsegments
  For l=1 To 6
    s=GetSurface(layer(l),1)
    v=l_index(i,ii)
    VertexCoords(s,v,VertexX(s,v),0,VertexZ(s,v))
  Next
  Next
Next

  For l=1 To 6
  FitMesh layer(l),-.5,-.5,-.5,1,1,1,True

  ScaleMesh layer(l), 20.0,  1.0, 15.0

  PositionEntity layer(l), 0,0,0
  Next

temppiv=CreatePivot()
CameraFogMode Cam,False
PositionEntity temppiv,0,0,0;VertexX(s,32),0,VertexZ(s,32)
PositionEntity Cam,0,0,0;VertexX(s,32),22,VertexZ(s,32)
TranslateEntity Cam,0,10,0
PointEntity Cam,temppiv

            CameraRange app\cam,0,0
			CameraProjMode( App\Cam, False )
			CameraProjMode( Cam, True )
 	        UnlockBuffer(BackBuffer()) 
    	    LockBuffer(FrontBuffer()) 
        	UpdateWorld()
 	        LockBuffer(BackBuffer()) 
    	    UnlockBuffer(FrontBuffer()) 
            Buttoncheck()
			RenderWorld( )
			CameraProjMode( Cam, False )
			CameraProjMode( App\Cam, True )
  		    FUI_Update( )
;			Flip(0)


;UpdateWorld()
;RenderWorld()

ChangeDir thispath$
TEMPIMG=CreateImage(1024,768)
CopyRect 0,0,1024,768,0,0,BackBuffer(),ImageBuffer(tempimg)
ResizeImage tempimg,1024,1024
If flags=0 Then 
ScaleImage tempimg,1,-1
Else
RotateImage TEMPIMG,90
EndIf
ChangeDir thispath$
SaveImage tempimg,sfilename3$

;assign the texture to layer 1
CameraRange app\Cam, 0.9, 10
;NOW LETS SET ALPHA TO 0 ON ALL LAYERS BUT 1
LOADWORK("tempsave.rct")

assigntexture(1,strip_path(sfilename3$))
;scale texture to cover all of the mesh

ScaleTexture layertexture(1),1.0,1.0
layertexscale#(1)=1.0
For i=0 To lsegments
For ii=0 To lsegments
   v=l_index(i,ii)
     For l=1 To 6
     s=GetSurface(layer(l),1)
     If l=1 Then 
     VertexColor s,v,255,255,255,1.0
     Else
    	 If yn=1 Then 
	     VertexColor s,v,255,255,255,0
    	 Else
	     VertexColor s,v,VertexRed(s,v),VertexGreen(s,v),VertexBlue(s,v),VertexAlpha(s,v)
	     EndIf
     EndIf   
     Next
   Next
Next

For dm.dropmodel=Each dropmodel
 ShowEntity dm\ent
 Next


 ShowEntity Boxmesh
 ShowEntity areaRect
 ShowEntity Ruler

 CameraFogMode Cam,True
 PositionEntity Cam,oldcamx,oldcamy,oldcamz
 RotateEntity Cam,oldcam_pitch,oldcam_yaw,oldpitch_roll
  
End Function 

Function GetMeshID(Name$)
    Name$=Upper$(Name$)
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

 ;BF_encrypt(Thisbank,ts)
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
