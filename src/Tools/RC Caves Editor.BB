;Tools
Global RootDir$ = "..\..\"
Global LogMode = 1; (0 = standard logging, 1 = debug mode)
ChangeDir RootDir$

    ;;;;;;;;;;;;;;;;;;;;;;;;;;; 
    ;setup for variable window;
    ;;;;;;;;;;;;;;;;;;;;;;;;;;;
	Global GFX_WINDOW_WIDTH = GetSystemMetrics(0)
	Global GFX_WINDOW_HEIGHT = GetSystemMetrics(1)
	Global GFX_WINDOW_RESOLUTION = 16
	Global GFX_WINDOW_MODE = 0
	Global GFX_WINDOW_RESIZE = False
	Global GFX_WINDOW_BORDER = False
	Global GFX_WINDOW_CAPTION$ = "Realm crafter cave editor"
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
	 	FUI_Initialise(1024, 768, 0, 2, False, True, "Realm Crafter Cave Editor")
    	w=1024:h=768                                                                               ;  
	    Else
	 	FUI_Initialise(1024, 768, 0, 2, False, True, "Realm Crafter Cave Editor")
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

Global cam=CreateCamera()
TextureFilter "M_",1+4
Include "..\modules\CAVE_gui_shell_FUI.bb"
Global gui_leftmouse,gui_rightmouse


Global temppivot
Global old_Ambientred,old_ambientgreen,old_ambientblue
Global old_Lightred,old_Lightgreen,old_Lightblue
Global newlightrange,Old_LightRange
Global new_ambientred=80,new_ambientblue=80,new_ambientgreen=80
Global new_lightred=250,new_lightblue=250,new_lightgreen=250
Global lightball=CreateSphere()
Global setscale#=1.0
Global Cavesize=300
Global showcompass=-1
Global Leftv_main#, Rightv_Main#,UpV_main#,Downv_main#,frontv_main#,backv_main#
EntityAlpha lightball,.5
PositionEntity lightball,100000,10000,100000

Type UndoCave
 Field EnT
 End Type
Global undo.UndoCave
Const maxundo=15

  

Global ObjectCube=CreateCube()
EntityColor ObjectCube,255,0,0
EntityAlpha ObjectCube,0

Type DropModel
 Field Ent
 Field MeshID
 Field X#,Y#,Z#
 Field Pitch#,Yaw#,Roll#
 Field Sx#,Sy#,Sz#
 Field islight
 Field cr,cg,cb
 Field range
End Type
Global selected_model=-1
Global cube,cube2,br2,tex2

;ChangeDir thispath$

Const testing=True
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
Global DEBUGMODE=False

Include "..\modules\safeloads.bb"
Include "..\modules\weld.bb"
Include "..\modules\VertexLight_cave.bb"
Include "..\modules\smoothuv.bb"
Include "..\modules\Media.bb"
Include "..\modules\B3dFILE.bb"
Include "..\modules\Scene_Export.bb"

If FileType ("DATA\RCCAVES")<>2 Then CreateDir ("DATA\RCCAVES")
If FileType ("DATA\RCCAVES\TEXTURES")<>2 Then CreateDir ("DATA\RCCAVES\TEXTURES")
If FileType ("DATA\RCCAVES\SHAPES")<>2 Then CreateDir ("DATA\RCCAVES\SHAPES")
If FileType ("DATA\MESHES\RCCAVES")<>2 Then CreateDir ("DATA\MESHES\RCCAVES")

;Include "toolsourse\
;Here are the globals
Global csgscale#=1,csgpiv ;The world scale (1- Normal, 2-2x Normal)
Global csgmesh[2] ;Different surface holders for different opereations
Global compass=LoadMesh_Safe("Data\rccaves\c_compass.3ds")
Global compasspiv=CreatePivot()
HideEntity compass
;-----------------------

Type CSGVectorType
 Field x#
 Field y#
 Field z#

 Field u# ;Texture Coords !
 Field v#
 Field w#
End Type

;-----------------------

Type CSGTriangleTYPE

 Field id
 Field tarid            ;See Split
 Field del              ;Can be deleted
 Field mindex           ;Mesh Index (NOT a Handle)
 Field sindex						;Surface Index			
 Field tindex 					;Triangle Index

 Field v1x#           ;Edge Vertex 1
 Field v1y#
 Field v1z#
 Field u1#
 Field v1#
 Field w1#

 Field v2x#           ;Edge Vertex 2
 Field v2y#
 Field v2z#
 Field u2#
 Field v2#
 Field w2#

 Field v3x#           ;Edge Vertex 3
 Field v3y#
 Field v3z#
 Field u3#
 Field v3#
 Field w3#

 Field mix#   ;Middle Vector from all Edge Vertices
 Field miy# 
 Field miz#

 Field normx# ;Normal of Triangle
 Field normy# 
 Field normz# 

End Type

;###############################################################################################
CaveEdit()
End

;###############################################################################################

Function CSG(mesh1,mesh2,mode=2)

 ;MR 02.11.2003

 ;mode 1 - Union, mode 2 - Subtraction, mode 3 - Intersection

 Local x1#,y1#,z1#
 Local x2#,y2#,z2#
 Local x3#,y3#,z3#

 Local vi1,vi2,vi3    ;Only VertexIndex

 ;-------------------------------------------------------------------------------------- Memory Position and Angles

 Local ex#,ey#,ez#

 ex=EntityX(mesh1,1)
 ey=EntityY(mesh1,1)
 ez=EntityZ(mesh1,1)

 Local epitch#,eyaw#,eroll#

 epitch=EntityPitch(mesh1,1)
 eyaw  =EntityYaw  (mesh1,1)
 eroll =EntityRoll (mesh1,1)

 ;--------------------------------------------------------------------------------------

 Local curmesh
 Local mindex
 Local surf,sindex
 Local SurfaceBrush
 Local tindex
 Local idstate=0
 Local t.CSGTriangleTYPE
 Local t1.CSGTriangleTYPE
 Local t2.CSGTriangleTYPE

 Local ve1.CSGVectorType
 Local ve2.CSGVectorType
 Local ve3.CSGVectorType

 For mindex = 1 To 4

 If mindex = 1 Then curmesh = mesh1
 If mindex = 2 Then curmesh = mesh2
 If mindex = 3 Then curmesh = mesh1
 If mindex = 4 Then curmesh = mesh2

 ;Now copy all 4 Meshes to Buffer

 For sindex = 1 To CountSurfaces(curmesh)

  ;DebugLog "sindex "+sindex

	surf = GetSurface(curmesh,sindex)
	For tindex = 0 To CountTriangles(surf)-1
   
   ;---------------------------------------
  
	 vi1 = TriangleVertex(surf,tindex,0)
	 vi2 = TriangleVertex(surf,tindex,1)
	 vi3 = TriangleVertex(surf,tindex,2)

   ve1=CSGGetVertexRealPosition(curmesh,surf,vi1)
   ve2=CSGGetVertexRealPosition(curmesh,surf,vi2)
   ve3=CSGGetVertexRealPosition(curmesh,surf,vi3)

   t=CSGTriAdd(ve1,ve2,ve3)

	 t\mindex = mindex
	 t\sindex = sindex 
	 t\tindex = tindex
	 t\tarid = -1

   ;---------------------------------------

	Next ;Triangles
 Next ;Surfaces
 Next ;1-4

 CSGTriRemoveDel

 ;--------------------------------------------------------------------------------------

 ;Create the reuse objects
 csgmesh[0] = CreateMesh()
; ds=CountSurfaces(mesh1)
 ;   temps=GetSurface(mesh2,1)
  ;     tempb=GetSurfaceBrush(temps)
   ;    ttex=GetBrushTexture(tempb)
    ;   ttn1$=TextureName(ttex)
     ;  FreeBrush tempb 
      ; FreeTexture ttex   

;   oldsurf=-1
 ;  For dsc=1 To ds
  ;     temps=GetSurface(mesh1,dsc)
   ;    tempb=GetSurfaceBrush(temps)
    ;   ttex=GetBrushTexture(tempb)
     ;  ttn$=TextureName(ttex)
      ; If Instr(ttn1$,ttn$) Then oldsurf=temps 
       ;FreeTexture ttex
       ;FreeBrush tempb
       ;Next



; If oldsurf=-1 Then 
 surf = CreateSurface(csgmesh[0])
; Else
; surf=oldsurf
; EndIf 

 vi1 = AddVertex(surf,-1,0,1)
 vi2 = AddVertex(surf,1,0,1)
 vi3 = AddVertex(surf,1,0,-1)
 AddTriangle(surf,vi1,vi2,vi3)

 ;----------------------------------------

 csgmesh[1] = CreateMesh()
 surf = CreateSurface(csgmesh[1])
 vi1 = AddVertex(surf,-1,0,1)
 vi2 = AddVertex(surf,1,0,1)
 vi3 = AddVertex(surf,1,0,-1)
 AddTriangle(surf,vi1,vi2,vi3)

 ;----------------------------------------

 csgmesh[2] = CreateCube()
 ScaleMesh csgmesh[2],100000*csgscale,0,100000*csgscale ;<--- mesh vertices must be in range of float !

 ;----------------------------------------

 ;Time to split the a polys
 For t1.CSGTriangleTYPE = Each CSGTriangleTYPE
 If t1\mindex = 2
	For t2.CSGTriangleTYPE = Each CSGTriangleTYPE
	If t2\mindex = 1 And t2\tarid <> t1\id
	 If t2\del = 0
	  If CSGTrisIntersect(t1,t2) = 1
	 	 CSGSplit t1,t2
	  EndIf
	 EndIf
	EndIf
	Next
 EndIf
 Next

 ;----------------------------------------

 ;Now split the b poly's
 For t1.CSGTriangleTYPE = Each CSGTriangleTYPE
 If t1\mindex = 3
	For t2.CSGTriangleTYPE = Each CSGTriangleTYPE
	If t2\mindex = 2 And t2\tarid <> t1\id
	 If t2\del = 0
	  If CSGTrisIntersect(t1,t2) = 1
		 CSGSplit t1,t2
	  EndIf
	 EndIf
	EndIf
	Next
 EndIf
 Next

 ;----------------------------------------

 ;Step 2 of reuse
 surf = GetSurface(csgmesh[0],1)
 AddTriangle(surf,2,1,0)

 FreeEntity csgmesh[1]
 FreeEntity csgmesh[2]

 csgpiv = CreatePivot()

 ;----------------------------------------

 ;Setting a flag to delete all the triangles without normals
 For t1.CSGTriangleTYPE = Each CSGTriangleTYPE
  If t1\normx = 0.0 And t1\normy = 0.0 And t1\normz = 0.0 ;And t1\del = 0
   ;DebugLog "NORM=0 : "+t1\normx+" "+t1\normy+" "+t1\normz+" MIndex="+t1\mindex
   Delete t1
  EndIf
 Next

 ;----------------------------------------

 Local epsilon#=0.0000001

 ;MeshA
 For t1.CSGTriangleTYPE = Each CSGTriangleTYPE
 If t1\mindex = 1 And t1\del = 0
  cosangle# = -1
  inter = 0
  intermode = 0 ;1 - inside, 2 - shared, 3 - Not inside
  quickdist# = 100000

	For t2.CSGTriangleTYPE = Each CSGTriangleTYPE
	 If t2\mindex = 4
	  res = CSGRayIntersect(t2,t1)
	  If res = 1
	   intx# = PickedX()
	   inty# = PickedY()
	   intz# = PickedZ()
	   dist# = Sqr((intx-t1\mix)*(intx-t1\mix)+(inty-t1\miy)*(inty-t1\miy)+(intz-t1\miz)*(intz-t1\miz))
	   If dist < quickdist
	    quickdist = dist
	    inter = 1
	    cosangle# = (t1\normx*t2\normx)+(t1\normy*t2\normy)+(t1\normz*t2\normz)
	   EndIf
	  EndIf
	 EndIf
	Next
	
	If inter = 1
	 If cosangle > 0 Then intermode = 1
	 If cosangle < 0 Then intermode = 3
	 If quickdist < epsilon ;If the triangle is shared
	  intermode = 2
	 EndIf
	Else
	 intermode = 3
	EndIf

  Select mode
  Case  2
	 If intermode = 1 Or intermode = 2
	  t1\del = 1
	 EndIf
  Case 1
	 If intermode = 1 Or intermode = 2
	  t1\del = 1
	 EndIf
  Case  3
	 If intermode = 3 Or intermode = 2
	  t1\del = 1
	 EndIf
  End Select

  If t1\del=1 Then Delete t1

 EndIf ;Mesh A
 Next ;Tri

 ;----------------------------------------

 ;MeshB
 For t.CSGTriangleTYPE = Each CSGTriangleTYPE
 If t\mindex = 2 And t\del = 0
  cosangle = -1
  inter = 0
  intermode = 0;1 - inside, 2 - shared, 3 - not inside
  quickdist# = 100000
	For tr.CSGTriangleTYPE = Each CSGTriangleTYPE
	 If tr\mindex = 3
	  res = CSGRayIntersect(tr,t)
	  If res = 1
	   intx# = PickedX()
	   inty# = PickedY()
	   intz# = PickedZ()
	   dist# = Sqr((intx-t\mix)*(intx-t\mix)+(inty-t\miy)*(inty-t\miy)+(intz-t\miz)*(intz-t\miz))
	   If dist < quickdist
	    quickdist = dist
	    inter = 1
	    cosangle# = (t\normx*tr\normx)+(t\normy*tr\normy)+(t\normz*tr\normz)
	   EndIf
	  EndIf
	 EndIf
	Next
	
	If inter = 1
	 If cosangle > 0 Then intermode = 1
	 If cosangle < 0 Then intermode = 3
	 If quickdist < epsilon
	  intermode = 2
	 EndIf
	Else
	 intermode = 3
	EndIf
	
  If mode = 2
	 If intermode = 3 Or intermode = 2
	  t\del = 1
	 EndIf
  ElseIf mode = 1
	 If intermode = 1
	  t\del = 1
	 EndIf
  ElseIf mode = 3
	 If intermode = 3
	  t\del = 1
	 EndIf
  EndIf

  If t\del=1 Then Delete t

 EndIf
 Next

 ;----------------------------------------

 FreeEntity csgpiv
 FreeEntity csgmesh[0]

 ;----------------------------------------

 ;Now delete the polys that don't belong(loose polys)

 CSGClearlTris 1 
 CSGClearlTris 2

 ;----------------------------------------------- New Mesh

 Local mesh=CSGMakeNewMesh()
 
 CSGAddTheTriangles mesh,1

 ;Now add Mesh 2 to Mesh 1
 If mode = 2
  CSGAddTheTriangles mesh,2,True ;Flips all the triangles in a mesh
 Else
  CSGAddTheTriangles mesh,2,False
 EndIf

 ;----------------------------------------------- Repaint !

 CSGRepaint mesh,mesh1,mesh2

 ;----------------------------------------------- Reposition Mesh

 PositionMesh   mesh,-ex,-ey,-ez
 PositionEntity mesh, ex, ey, ez

 RotateMesh mesh,      0,-eyaw,0
 RotateMesh mesh,-epitch,    0,0
 RotateMesh mesh,      0,    0,-eroll

 RotateEntity mesh,epitch,eyaw,eroll

 ;-----------------------------------------------

 Delete Each CSGTriangleTYPE ;Free the whole Triangle Collection

 Return mesh

End Function

;###############################################################################################

Function CSGVec.CSGVectorType(x#,y#,z#,u#,v#,w#) 

 ;MR 25.01.2003 

 ;Simple Return a Vector as type :-)

 Local ve.CSGVectorType = New CSGVectorType

 ve\x=x#
 ve\y=y#
 ve\z=z#

 ve\u=u#
 ve\v=v#
 ve\w=w#

 Return ve

End Function

;###############################################################################################

Function CSGTriAdd.CSGTriangleTYPE(v1.CSGVectorType,v2.CSGVectorType,v3.CSGVectorType)

 ;MR 02.11.2003

 Local newid=0
 Local tr.CSGTriangleTYPE = Last CSGTriangleTYPE
 If tr<>Null Then newid = tr\id + 1

 ;------------------------------------------

  t.CSGTriangleTYPE = New CSGTriangleTYPE

  t\id = newid

 ;------------------------------------------ Copy Parameters V1,V2,V3

	t\v1x = v1\x
	t\v1y = v1\y
	t\v1z = v1\z

	t\v2x = v2\x
	t\v2y = v2\y
	t\v2z = v2\z

	t\v3x = v3\x
	t\v3y = v3\y
	t\v3z = v3\z

 ;------------------------------------------ Memory UVW for Vertex V1,V2,V3

	t\u1 = v1\u
	t\v1 = v1\v
	t\w1 = v1\w

	t\u2 = v2\u
	t\v2 = v2\v
	t\w2 = v2\w

	t\u3 = v3\u
	t\v3 = v3\v
	t\w3 = v3\w

 ;------------------------------------------ Middle

  t\mix = (t\v1x + t\v2x + t\v3x) / 3.0
  t\miy = (t\v1y + t\v2y + t\v3y) / 3.0
  t\miz = (t\v1z + t\v2z + t\v3z) / 3.0

 ;------------------------------------------ Normal

  Local ax#,ay#,az#
  Local bx#,by#,bz#

  ax#=t\v2x-t\v1x
  ay#=t\v2y-t\v1y
  az#=t\v2z-t\v1z
	
  bx#=t\v3x-t\v2x
  by#=t\v3y-t\v2y
  bz#=t\v3z-t\v2z

  t\normx=(ay#*bz#)-(az#*by#)
  t\normy=(az#*bx#)-(ax#*bz#)
  t\normz=(ax#*by#)-(ay#*bx#)

  Local normlen# = Sqr#((t\normx*t\normx)+(t\normy*t\normy)+(t\normz*t\normz))
  If normlen > 0.0
   t\normx = t\normx/normlen 
   t\normy = t\normy/normlen 
   t\normz = t\normz/normlen
  Else
   t\del = 1
  EndIf

 ;------------------------------------------

 Return t

End Function

;###############################################################################################
	
Function CSGTrisIntersect(t1.CSGTriangleTYPE,t2.CSGTriangleTYPE)

 ;MR 19.01.2003

 ;if one triangle in another triangle

 Local surf

 surf = GetSurface(csgmesh[0],1) ;Dummy Mesh with one triangle :-)
 

 VertexCoords(surf,0,t1\v1x,t1\v1y,t1\v1z)
 VertexCoords(surf,1,t1\v2x,t1\v2y,t1\v2z)
 VertexCoords(surf,2,t1\v3x,t1\v3y,t1\v3z)

 surf = GetSurface(csgmesh[1],1)

 VertexCoords(surf,0,t2\v1x,t2\v1y,t2\v1z)
 VertexCoords(surf,1,t2\v2x,t2\v2y,t2\v2z)
 VertexCoords(surf,2,t2\v3x,t2\v3y,t2\v3z)


 Return MeshesIntersect(csgmesh[0],csgmesh[1])

End Function

;###############################################################################################

Function CSGRayIntersect(t1.CSGTriangleTYPE,t2.CSGTriangleTYPE)

 ;MR 02.11.2003

 ;i think it used to find the triangles that can be deleted

 Local surf = GetSurface(csgmesh[0],1) ;Dummy Mesh with one triangle :-)

 VertexCoords(surf,0,t1\v1x,t1\v1y,t1\v1z)
 VertexCoords(surf,1,t1\v2x,t1\v2y,t1\v2z)
 VertexCoords(surf,2,t1\v3x,t1\v3y,t1\v3z)

 Local piv = csgpiv
 RotateEntity piv,0,0,0 ;!?
 PositionEntity(piv,t2\mix  ,t2\miy  ,t2\miz)
 AlignToVector (piv,t2\normx,t2\normy,t2\normz,3)
 MoveEntity piv,0,0,100000.0*csgscale ;<--- !

 EntityPickMode csgmesh[0],2

 Local distx# = EntityX(piv)-t2\mix
 Local disty# = EntityY(piv)-t2\miy
 Local distz# = EntityZ(piv)-t2\miz
 Local picked = LinePick(t2\mix,t2\miy,t2\miz,distx,disty,distz)

 EntityPickMode csgmesh[0],0

 If picked Then
  Return 1
 Else
  Return 0
 EndIf

End Function

;###############################################################################################

Function CSGSplit(t1.CSGTriangleTYPE,t2.CSGTriangleTYPE)

 ;MR 02.11.2003

 ;if CSGTrisIntersect = true than split it to new triangles

 ;now the new triangles are in menory triangle type collection !

 If t1 = Null Or t2 = Null Then Return 0

;------------------------------------------------------------

 Local newvx#[3]
 Local newvy#[3]
 Local newvz#[3]

 Local newu#[3]
 Local newv#[3]
 Local neww#[3]

 Local v.CSGVectorType 

 Local edge1,edge2,edge3

 Local epsilon#=0.000001 

;------------------------------------------------------------

 Local cube = csgmesh[2]
 EntityPickMode cube,2
 PositionEntity cube,t1\mix,t1\miy,t1\miz
 RotateEntity cube,0,0,0
 AlignToVector(cube,t1\normx,t1\normy,t1\normz,2)

;------------------------------------------------------------

 ;Edge1 to 2

 distx# = t2\v1x-t2\v2x
 disty# = t2\v1y-t2\v2y
 distz# = t2\v1z-t2\v2z

 picked = LinePick(t2\v2x,t2\v2y,t2\v2z,distx,disty,distz)
 If picked = cube
  newvx[1] = PickedX()
  newvy[1] = PickedY()
  newvz[1] = PickedZ()

  v=CSGPickedUVW(t2)

  newu[1]=v\u
  newv[1]=v\v
  neww[1]=v\w
 
  distx# = newvx[1] - t2\v2x
  disty# = newvy[1] - t2\v2y
  distz# = newvz[1] - t2\v2z
  cdist# = Sqr(distx*distx + disty*disty + distz*distz)
  If cdist <= epsilon
   edge1 = False
  Else
   edge1 = True
  EndIf

 EndIf

;------------------------------------------------------------

 ;Edge2 to 3

 distx# = t2\v2x-t2\v3x
 disty# = t2\v2y-t2\v3y
 distz# = t2\v2z-t2\v3z

 picked = LinePick(t2\v3x,t2\v3y,t2\v3z,distx,disty,distz)
 If picked = cube
  newvx[2] = PickedX()
  newvy[2] = PickedY()
  newvz[2] = PickedZ()

  v=CSGPickedUVW(t2)

  newu[2]=v\u
  newv[2]=v\v
  neww[2]=v\w

  distx# = newvx[2] - t2\v3x
  disty# = newvy[2] - t2\v3y
  distz# = newvz[2] - t2\v3z
  cdist# = Sqr(distx*distx + disty*disty + distz*distz)
  If cdist <= epsilon
   edge2 = False
  Else
   edge2 = True
  EndIf

 EndIf

;------------------------------------------------------------

 ;Edge3 to 1

 distx# = t2\v3x-t2\v1x
 disty# = t2\v3y-t2\v1y
 distz# = t2\v3z-t2\v1z

 picked = LinePick(t2\v1x,t2\v1y,t2\v1z,distx,disty,distz)
 If picked = cube
  newvx[3] = PickedX()
  newvy[3] = PickedY()
  newvz[3] = PickedZ()

  v=CSGPickedUVW(t2)

  newu[3]=v\u
  newv[3]=v\v
  neww[3]=v\w

  distx# = newvx[3] - t2\v1x
  disty# = newvy[3] - t2\v1y
  distz# = newvz[3] - t2\v1z
  cdist# = Sqr(distx*distx + disty*disty + distz*distz)
  If cdist <= epsilon
   edge3 = False
  Else
   edge3 = True
  EndIf

 EndIf

;------------------------------------------------------------

 EntityPickMode cube,0

 If edge1=0 And edge2=0 And edge3=0 Then Return 0

;------------------------------------------------------------

 Local v1.CSGVectorType = New CSGVectorType
 Local v2.CSGVectorType = New CSGVectorType
 Local v3.CSGVectorType = New CSGVectorType
 Local v4.CSGVectorType = New CSGVectorType
 Local v5.CSGVectorType = New CSGVectorType

 Local mindex = t2\mindex
 Local sindex = t2\sindex
 Local tm.CSGTriangleTYPE

;------------------------------------------------------------

 If edge1 And edge2

  t2\del = 1

  v1 = CSGVec(t2\v1x,t2\v1y,t2\v1z,t2\u1,t2\v1,t2\w1)
  v2 = CSGVec(newvx[1],newvy[1],newvz[1],newu[1],newv[1],neww[1])
  v3 = CSGVec(t2\v2x,t2\v2y,t2\v2z,t2\u2,t2\v2,t2\w2)
  v4 = CSGVec(newvx[2],newvy[2],newvz[2],newu[2],newv[2],neww[2])
  v5 = CSGVec(t2\v3x,t2\v3y,t2\v3z,t2\u3,t2\v3,t2\w3)

  tm = CSGTriAdd(v1,v2,v5)
  tm\mindex= mindex
  tm\sindex= sindex
  tm\tarid = t1\id

  tm = CSGTriAdd(v2,v4,v5)
  tm\mindex= mindex
  tm\sindex= sindex
  tm\tarid = t1\id

  tm = CSGTriAdd(v2,v3,v4)
  tm\mindex= mindex
  tm\sindex= sindex
  tm\tarid = t1\id

 ElseIf edge1 And edge3

  t2\del = 1

  v1 = CSGVec(t2\v1x,t2\v1y,t2\v1z,t2\u1,t2\v1,t2\w1)
  v2 = CSGVec(newvx[1],newvy[1],newvz[1],newu[1],newv[1],neww[1])
  v3 = CSGVec(t2\v2x,t2\v2y,t2\v2z,t2\u2,t2\v2,t2\w2)
  v4 = CSGVec(t2\v3x,t2\v3y,t2\v3z,t2\u3,t2\v3,t2\w3)
  v5 = CSGVec(newvx[3],newvy[3],newvz[3],newu[3],newv[3],neww[3])

  tm = CSGTriAdd(v1,v2,v5) ;1,2,5
  tm\mindex= mindex
  tm\sindex= sindex
  tm\tarid = t1\id

  tm = CSGTriAdd(v2,v4,v5)
  tm\mindex= mindex
  tm\sindex= sindex
  tm\tarid = t1\id

  tm = CSGTriAdd(v2,v3,v4)
  tm\mindex= mindex
  tm\sindex= sindex
  tm\tarid = t1\id

 ElseIf edge2 And edge3

  t2\del = 1

  v1 = CSGVec(t2\v1x,t2\v1y,t2\v1z,t2\u1,t2\v1,t2\w1)
  v2 = CSGVec(t2\v2x,t2\v2y,t2\v2z,t2\u2,t2\v2,t2\w2)
  v3 = CSGVec(newvx[2],newvy[2],newvz[2],newu[2],newv[2],neww[2])
  v4 = CSGVec(t2\v3x,t2\v3y,t2\v3z,t2\u3,t2\v3,t2\w3)
  v5 = CSGVec(newvx[3],newvy[3],newvz[3],newu[3],newv[3],neww[3])

  tm = CSGTriAdd(v1,v2,v5)
  tm\mindex= mindex
  tm\sindex= sindex
  tm\tarid = t1\id

  tm = CSGTriAdd(v2,v3,v5)
  tm\mindex= mindex
  tm\sindex= sindex
  tm\tarid = t1\id

  tm = CSGTriAdd(v3,v4,v5)
  tm\mindex= mindex
  tm\sindex= sindex
  tm\tarid = t1\id

 ElseIf edge2

  t2\del = 1

  v1 = CSGVec(t2\v1x,t2\v1y,t2\v1z,t2\u1,t2\v1,t2\w1)
  v2 = CSGVec(t2\v2x,t2\v2y,t2\v2z,t2\u2,t2\v2,t2\w2)
  v3 = CSGVec(newvx[2],newvy[2],newvz[2],newu[2],newv[2],neww[2])
  v4 = CSGVec(t2\v3x,t2\v3y,t2\v3z,t2\u3,t2\v3,t2\w3)

  tm = CSGTriAdd(v1,v2,v3)
  tm\mindex= mindex
  tm\sindex= sindex
  tm\tarid = t1\id

  tm = CSGTriAdd(v1,v3,v4)
  tm\mindex= mindex
  tm\sindex= sindex
  tm\tarid = t1\id

 ElseIf edge1

  t2\del = 1

  v1 = CSGVec(t2\v1x,t2\v1y,t2\v1z,t2\u1,t2\v1,t2\w1)
  v2 = CSGVec(newvx[1],newvy[1],newvz[1],newu[1],newv[1],neww[1]) 
  v3 = CSGVec(t2\v2x,t2\v2y,t2\v2z,t2\u2,t2\v2,t2\w2)
  v4 = CSGVec(t2\v3x,t2\v3y,t2\v3z,t2\u3,t2\v3,t2\w3)

  tm = CSGTriAdd(v1,v2,v4)
  tm\mindex= mindex
  tm\sindex= sindex
  tm\tarid = t1\id

  tm = CSGTriAdd(v2,v3,v4)
  tm\mindex= mindex
  tm\sindex= sindex
  tm\tarid = t1\id

 ElseIf edge3

  t2\del = 1

  v1 = CSGVec(t2\v1x,t2\v1y,t2\v1z,t2\u1,t2\v1,t2\w1)
  v2 = CSGVec(t2\v2x,t2\v2y,t2\v2z,t2\u2,t2\v2,t2\w2)
  v3 = CSGVec(t2\v3x,t2\v3y,t2\v3z,t2\u3,t2\v3,t2\w3)
  v4 = CSGVec(newvx[3],newvy[3],newvz[3],newu[3],newv[3],neww[3])

  tm = CSGTriAdd(v1,v2,v4)
  tm\mindex= mindex
  tm\sindex= sindex
  tm\tarid = t1\id

  tm = CSGTriAdd(v2,v3,v4)
  tm\mindex= mindex
  tm\sindex= sindex
  tm\tarid = t1\id

 EndIf

 If t2\del=1 Then Delete t2

End Function

;###############################################################################################

Function CSGTriRemoveDel()

 ;MR 19.01.2003

 ;-----------------------------------------------

 For t.CSGTriangleTYPE = Each CSGTriangleTYPE
  If t\del=1 Then Delete t  
 Next

End Function

;###############################################################################################

Function CSGMakeNewMesh()

 ;MR 26.01.2003

 ;----------------------------------------------- New Mesh

 Local m=CreateMesh()

 ;----------------------------------------------- Find Max Surfaces for Mesh 1

 Local smax1=0

 For t.CSGTriangleTYPE = Each CSGTriangleTYPE
  If t\mindex=1 Then
   If t\sindex>smax1 Then smax1=t\sindex  
  EndIf
 Next

 ;----------------------------------------------- Find Max Surfaces for Mesh 2

 Local smax2=0

 For t.CSGTriangleTYPE = Each CSGTriangleTYPE
  If t\mindex=2 Then
   If t\sindex>smax2 Then smax2=t\sindex  
  EndIf
 Next

 ;----------------------------------------------- Add Max Surface Index from Mesh 1 to Mesh 2

 For t.CSGTriangleTYPE = Each CSGTriangleTYPE
  If t\mindex=2 Then
   t\sindex=t\sindex+smax1
  EndIf
 Next

 ;----------------------------------------------- Create all Surfaces

 Local smax=smax1+smax2 ;all needed surfaces

 Local si

 Local surf

 For si=1 To smax

  surf=CreateSurface(m)
 
 Next

 ;-----------------------------------------------

 Return m

End Function

;###############################################################################################

Function CSGAddTheTriangles(m,mindex,FlipTriangles=0)

 ;MR 02.11.2003

 ;----------------------------------------------- Bring Triangle to Surface

 Local v1,v2,v3 ;VertexIndex

 Local surf=0

 Local si

 ;-----------------------------------------------

 For si=1 To CountSurfaces(m)

  For t.CSGTriangleTYPE = Each CSGTriangleTYPE
   If t\del=0 And t\mindex=mindex And t\sindex=si Then

    surf=GetSurface(m,si)  

    v1=AddVertex(surf,t\v1x,t\v1y,t\v1z)
    v2=AddVertex(surf,t\v2x,t\v2y,t\v2z)
    v3=AddVertex(surf,t\v3x,t\v3y,t\v3z)

    VertexNormal surf,v1,t\normx,t\normy,t\normz 
    VertexNormal surf,v2,t\normx,t\normy,t\normz 
    VertexNormal surf,v3,t\normx,t\normy,t\normz 

    VertexTexCoords surf,v1,t\u1,t\v1,t\w1 ;Set UV Coords !
    VertexTexCoords surf,v2,t\u2,t\v2,t\w2 
    VertexTexCoords surf,v3,t\u3,t\v3,t\w3 

    If FlipTriangles=0 Then
     AddTriangle surf,v1,v2,v3
    Else
     AddTriangle surf,v3,v2,v1
    EndIf

    Delete t ;Remove the Triangle from Collection !

   EndIf  
  Next

 Next ;All Surfaces

 ;-----------------------------------------------

 UpdateNormals m

End Function

;###############################################################################################

Function CSGRepaint(meshnew,mesh1,mesh2)

 ;MR 02.11.2003

 ;Repaint all Surfaces :-)

 ;----------------------------------------------------------------------------------------------

  Local cc1=0 ;New SurfaceCount !

  Local c1

  ;Paint New Surfaces from Mesh A  

  If CountSurfaces (mesh1)=>1 Then 
   For c1=1 To CountSurfaces(mesh1)
    cc1=cc1+1
   
    If CountSurfaces(meshnew)=>cc1 Then 
     PaintSurface GetSurface(meshnew,cc1),GetSurfaceBrush(GetSurface(mesh1,c1))
    EndIf 

   Next
  EndIf ;CountSurfaces=>1

 ;----------------------------------------------------------------------------------------------

  ;Paint New Surfaces from Mesh B  

  If CountSurfaces(mesh2)=>1 Then 
   For c1=1 To CountSurfaces(mesh2)
    cc1=cc1+1
   
    If CountSurfaces(meshnew)=>cc1 Then 
     PaintSurface GetSurface(meshnew,cc1),GetSurfaceBrush(GetSurface(mesh2,c1))
    EndIf 

   Next
  EndIf ;CountSurfaces=>1

 ;----------------------------------------------------------------------------------------------

End Function

;###############################################################################################

Function CSGClearLTris(mindex)

 ;MR 26.01.2003

 ;Clears the triangles that have no partners
 Local epsilon#=0.000001 

 Local count=0
 Local full
 Local car

 Local distx#
 Local disty#
 Local distz#
 Local cdist#

 Local vax#
 Local vay#
 Local vaz#

 Local vbx#
 Local vby#
 Local vbz#

 For t.CSGTriangleTYPE = Each CSGTriangleTYPE
  If t\mindex = mindex And t\del = 0
   count=0
   For full = 0 To 2
    Select full
    Case 0
     vax#=t\v1x
     vay#=t\v1y
     vaz#=t\v1z
    Case 1
     vax#=t\v2x
     vay#=t\v2y
     vaz#=t\v2z
    Case 2
     vax#=t\v3x
     vay#=t\v3y
     vaz#=t\v3z
    End Select

 	  For tom.CSGTriangleTYPE = Each CSGTriangleTYPE
 	   If t\id<>tom\id And tom\del = 0 And tom\mindex = mindex
	    cdist = 0
	    For car = 0 To 2
       Select car
       Case 0
        vbx#=tom\v1x
        vby#=tom\v1y
        vbz#=tom\v1z
       Case 1
        vbx#=tom\v2x
        vby#=tom\v2y
        vbz#=tom\v2z
       Case 2
        vbx#=tom\v3x
        vby#=tom\v3y
        vbz#=tom\v3z
       End Select
		   distx# = vbx - vax
		   disty# = vby - vay
		   distz# = vbz - vaz 
		   cdist# = Sqr(distx*distx + disty*disty + distz*distz)
		   If cdist <= epsilon Then Exit
	    Next
 	    If cdist >= 0.0 Then count = count + 1:Exit
	   EndIf
	  Next
   Next

   If count < 3
    Delete t
   EndIf
  EndIf
 Next

End Function

;############################################################################################################

Function CSGGetVertexRealPosition.CSGVectorType(entity,surf,vindex)

 Local pivot1=CreatePivot()
 Local pivot2=CreatePivot(pivot1)

 Local vreal.CSGVectorType =New CSGVectorType

 PositionEntity pivot1,0,0,0
 PositionEntity pivot2,VertexX(surf,vindex),VertexY(surf,vindex),VertexZ(surf,vindex)
 RotateEntity pivot1,EntityPitch(entity,True),EntityYaw(entity,True),EntityRoll(entity,True)
 
 vreal\x=EntityX(pivot2,True)+EntityX(entity,True)
 vreal\y=EntityY(pivot2,True)+EntityY(entity,True)
 vreal\z=EntityZ(pivot2,True)+EntityZ(entity,True)

 vreal\u=VertexU(surf,vindex)
 vreal\v=VertexV(surf,vindex)
 vreal\w=VertexW(surf,vindex)

 FreeEntity pivot2
 FreeEntity pivot1

 Return vreal

End Function

;#################################################################################################### UVW

Function CSGPickedUVW.CSGVectorType(t2.CSGTriangleTYPE)

  ;MR 25.01.2003

  Local ve.CSGVectorType = New CSGVectorType

  Local px#=PickedX()
  Local py#=PickedY()
  Local pz#=PickedZ() 

  Local pnx#=t2\normx
  Local pny#=t2\normy
  Local pnz#=t2\normz

  ;DebugLog "Picked XYZ "+px+" "+py+" "+pz
		
		; Select which component of xyz coordinates to ignore
		Local coords = 3

		If Abs(PNX) > Abs(PNY)
			If Abs(PNX)>Abs(PNZ) Then coords = 1
		Else
			If Abs(PNY)>Abs(PNZ) Then coords = 2
		EndIf

    ;DebugLog "coords "+coords
		
		Local a0#,a1#,b0#,b1#,c0#,c1#
		
		If (coords = 3)

 		  ;xy components

			; edge 1
			a0# = t2\v2x - t2\v1x
			a1# = t2\v2y - t2\v1y
		
			; edge 2
			b0# = t2\v3x - t2\v1x
			b1# = t2\v3y - t2\v1y

			; picked offset from triangle vertex 1
			c0# = px - t2\v1x
			c1# = py - t2\v1y
		Else		
			If (coords = 2)
				; xz components

				; edge 1
				a0# = t2\v2x - t2\v1x
				a1# = t2\v2z - t2\v1z
		
				; edge 2
				b0# = t2\v3x - t2\v1x
				b1# = t2\v3z - t2\v1z

				; picked offset from triangle vertex 1
				c0# = px - t2\v1x
				c1# = pz - t2\v1z
			Else
				; yz components

				; edge 1
				a0# = t2\v2y - t2\v1y
				a1# = t2\v2z - t2\v1z
		
				; edge 2
				b0# = t2\v3y - t2\v1y
				b1# = t2\v3z - t2\v1z

				; picked offset from triangle vertex 1
				c0# = py - t2\v1y
				c1# = pz - t2\v1z
			End If
		End If
						
		;
		; u and v are offsets from vertex 0 along edge 0 and edge 1
		; using these it is possible to calculate the Texture UVW coordinates
		; of the picked XYZ location
		;
		; a0*u + b0*v = c0
		; a1*u + b1*v = c1
		;
		; solve equation (standard equation with 2 unknown quantities)
		; check a math book to see why the following is true
		;
		Local u# = (c0*b1 - b0*c1) / (a0*b1 - b0*a1)
		Local v# = (a0*c1 - c0*a1) / (a0*b1 - b0*a1)

    ;DebugLog "U "+u+" V "+v
				
		; Calculate picked uvw's 
		ve\u = (t2\u1 + ((t2\u2 - t2\u1) * u) + ((t2\u3 - t2\u1) * v))
		ve\v = (t2\v1 + ((t2\v2 - t2\v1) * u) + ((t2\v3 - t2\v1) * v))
		ve\w = (t2\w1 + ((t2\w2 - t2\w1) * u) + ((t2\w3 - t2\w1) * v))
		
    ;DebugLog "U "+ve\u+" V "+ve\v

 Return ve
		
End Function

;###############################################################################################

Function CaveEdit()
temppivot=CreatePivot()
AmbientLight 200,200,200
ChangeDir thispath$
 AppTitle "RC Cave Editor","Really quit ?"

 ;---------------------------------------------------------------------------------------

 ChangeDir thispath$
 texture=loadTexture_safe("data\rccaves\textures\cdefault.png")
 brush=CreateBrush(255,255,255)
 Image=loadtexture_safe("data\rccaves\textures\cdefault.png") 
 BrushTexture brush,texture
 fui_applytexture(GUI_RIGHTWIN_TEXTURE,image,1)
 FreeTexture image

 cube = CreateCube()
 ScaleMesh cube,Cavesize,Cavesize,Cavesize
 For s=1 To CountSurfaces(cube)
  PaintSurface GetSurface(cube,s),brush
 Next 
 UC.UNDOCAVE=New UndoCave
 UC\ENT = CreateCube()
 ScaleMesh UC\ENT,Cavesize,Cavesize,Cavesize
 For s=1 To CountSurfaces(UC\ENT)
  PaintSurface GetSurface(UC\ENT,s),brush
 Next 
 EntityPickMode uc\ent,0
 HideEntity uc\ent
 FreeBrush brush
 FreeTexture texture
Leftv_main#=LeftVert(cube)
Rightv_Main#=RightVert(cube)
UpV_main#=Highvert(cube)
Downv_main#=LowVert(cube)
frontv_main#=FrontVert(cube)
backv_main#=BackVert(cube)


 ;---------------------------------------------------------------------------------------

 ChangeDir thispath$
 tex2=LoadTexture_safe("data\rccaves\textures\cdefault.png");CreateTexture(64,64)
 ScaleTexture tex2,.1,.1
 SetBuffer BackBuffer()
 br2=CreateBrush()
 BrushTexture br2,tex2

  ChangeDir thispath$
 cube2 = LoadMesh_Safe("DATA\RCCAVES\shapes\tunnel1.3ds")
 EntityOrder cube2,-1
 ScaleMesh cube2,.2,.2,.2
 For s=1 To CountSurfaces(cube2)
  surf=GetSurface(cube2,s)
  PaintSurface surf,br2
 Next 
 EntityBlend cube2, 3 

 ;---------------------------------------------------------------------------------------

; cam = CreateCamera()
 PositionEntity compass,EntityX(Cam,1)-5,EntityY(cam,1)-4,EntityZ(Cam,1)+10
 CameraFogMode cam,True
 CameraFogRange cam,250,900
 CameraFogColor cam,250,250,250
 MoveEntity cam,0,10,-520
 PointEntity cam,cube2
 CameraClsColor cam,0,0,80 
 CameraRange cam,1,2000

 ;---------------------------------------------------------------------------------------

;Local light=CreateLight()
;TurnEntity light,0,20,20
;LightRange light,50
;PositionEntity light,0,50,-50

 ;---------------------------------------------------------------------------------------

 UpdateNormals cube
 UpdateNormals cube2
 EntityAlpha cube2,.3

 ;---------------------------------------------------------------------------------------

 Local wire=0
 Local Mode=2
 tempimage=CreateImage (512,512)

 Color 255,255,255
   EntityPickMode cube,2
   PositionEntity cube2,EntityX(cam,1),EntityY(cam,1),EntityZ(cam,1)   
   CameraPick cam,GraphicsWidth()/2,(GraphicsHeight()/2)
   PositionEntity temppivot,PickedX(),PickedY(),PickedZ()
   PointEntity cube2,temppivot
   PositionEntity cube2,PickedX(),PickedY(),PickedZ()   
   EntityPickMode cube,0



 Repeat
If MouseHit(1) Then gui_leftmouse=1
If MouseHit(2) Then gui_rightmouse=1
  buttoncheck()

   If (KeyDown(56)) Then
   mouselook(cam)
   ElseIf KeyDown(57) Then 
   EntityPickMode cube,2
   PositionEntity cube2,EntityX(cam,1),EntityY(cam,1),EntityZ(cam,1)   
   CameraPick cam,GraphicsWidth()/2,(GraphicsHeight()/2)
   PositionEntity temppivot,PickedX(),PickedY(),PickedZ()
   PointEntity cube2,temppivot
   PositionEntity cube2,PickedX(),PickedY(),PickedZ()   
   EntityPickMode cube,0
   mouselook (cam)
   EndIf



;  If KeyHit(31) Then ;S = Screenshot
;   SaveBuffer FrontBuffer(),"screenshot.bmp"
;  EndIf

  If KeyHit(17) ;W
   wire = 1 - wire
   WireFrame wire
  EndIf
 
   
    vx# = GetMatElement#(cube2, 0, 0)
	vy# = GetMatElement#(cube2, 0, 1)
	vz# = GetMatElement#(cube2, 0, 2)
	XScale# = Sqr#(vx# * vx# + vy# * vy# + vz# * vz#)+.1
	vx# = GetMatElement#(cube2, 1, 0)
	vy# = GetMatElement#(cube2, 1, 1)
	vz# = GetMatElement#(cube2, 1, 2)
	YScale# = Sqr#(vx# * vx# + vy# * vy# + vz# * vz#)+.1
	vx# = GetMatElement#(cube2, 2, 0)
	vy# = GetMatElement#(cube2, 2, 1)
	vz# = GetMatElement#(cube2, 2, 2)
	ZScale# = Sqr#(vx# * vx# + vy# * vy# + vz# * vz#)+.1
CUBE3=CopyMesh(CUBE2)
ScaleMesh cube3,xscale,yscale,zscale
RotateMesh cube3,EntityPitch(cube2,True),EntityYaw(cube2,True),EntityRoll(cube2,True)
PositionMesh cube3,EntityX(cube2,True),EntityY(cube2,True),EntityZ(cube2,True)
  lv#=leftvert(cube3)
  rv#=rightvert(cube3)
  fv#=frontvert(cube3)
  bv#=backvert(cube3)
  upv#=highvert(cube3)
  downv#=LOWvert(cube3)
  FreeEntity cube3
If lv<=Leftv_main# Or rv>=rightv_main Or upv>=upv_main Or downv<=downv_main Or fv>=frontv_main Or bv<=backv_main Then 
 EntityColor cube2,255,0,0
 Else
 EntityColor cube2,255,255,255
EndIf

  
  PositionEntity compasspiv,EntityX(Cam,1),EntityY(cam,1),EntityZ(Cam,1)
  RotateEntity compasspiv,EntityPitch(Cam,1),EntityYaw(cam,1),EntityRoll(Cam,1)
  MoveEntity compasspiv,0,0,10
  PositionEntity compass,EntityX(compasspiv,1),EntityY(compasspiv,1),EntityZ(compasspiv,1)

			CameraProjMode( App\Cam, False )
			CameraProjMode( Cam, True )
 	        UnlockBuffer(BackBuffer()) 
    	    LockBuffer(FrontBuffer()) 
        	UpdateWorld()
 	        LockBuffer(BackBuffer()) 
    	    UnlockBuffer(FrontBuffer()) 
            buttoncheck()
			RenderWorld( )
			CameraProjMode( Cam, False )
			CameraProjMode( App\Cam, True )
  		    FUI_Update( )
            Flip(0)
 Forever

End Function

;#################################################################################################### 

Function CSGCountAllTriangles(mesh) ;only for example

 ;without child entitys

 Local si

 If mesh=0 Then Return 0

 Local c=0

 For si=1 To CountSurfaces(mesh)
  c=c+CountTriangles(GetSurface(mesh,si))
 Next 

 Return c

End Function

;#################################################################################################### 

Function CSGAddMyMeshCubeX(m,x1#,y1#,z1#,x2#,y2#,z2#,cx,cy,cz,br_top,br_bottom,br_left,br_right,br_front,br_back,o_top=1,o_bottom=1,o_left=1,o_right=1,o_front=1,o_back=1)

 ;MR 31.10.2002

 ;Create a Cube centered

 ;m       =Entity Handle 0=Create a new one :-)
 ;x1,x2   =From X1 To X2
 ;cx,cy,cy=Center  
 ;br_     =Brush  Handle
 ;o_      =Optional 1=Create 0=No Create  
 
  Local w#,h#,d#

 ;w#=witdh  (X)
 ;h#=height (Y)
 ;d#=depth  (Z)

  w#=x2#-x1#
  h#=y2#-y1#
  d#=z2#-z1#

  ;--------------------- Center ?

  If cx=1 Then 
   x1=x1-w/2.0
   x2=x2-w/2.0
  EndIf

  If cy=1 Then 
   y1=y1-h/2.0
   y2=y2-h/2.0
  EndIf

  If cz=1 Then 
   z1=z1-d/2.0
   z2=z2-d/2.0
  EndIf

  ;-----------------------------

  If m=0 Then
	 m=CreateMesh()
  EndIf

  ;-----------------------------

	;top face
	If o_top=1 Then 
	 s=CreateSurface( m , Br_Top)
	 AddVertex s,x1,y2,z2,0,1:AddVertex s,x2,y2,z2,0,0
	 AddVertex s,x2,y2,z1,1,0:AddVertex s,x1,y2,z1,1,1
	 AddTriangle s,0,1,2:AddTriangle s,0,2,3
  EndIf

	;bottom face	
  If o_bottom=1 Then
	 s=CreateSurface( m , Br_Bottom)
	 AddVertex s,x1,y1,z1,1,0:AddVertex s,x2,y1,z1,1,1
	 AddVertex s,x2,y1,z2,0,1:AddVertex s,x1,y1,z2,0,0
	 AddTriangle s,0,1,2:AddTriangle s,0,2,3
  EndIf

	;left face
  If o_left=1 Then
	 s=CreateSurface( m , Br_Left)
	 AddVertex s,x1,y2,z2,0,0:AddVertex s,x1,y2,z1,1,0
	 AddVertex s,x1,y1,z1,1,1:AddVertex s,x1,y1,z2,0,1
	 AddTriangle s,0,1,2:AddTriangle s,0,2,3
  EndIf 

	;right face
  If o_right=1 Then
	 s=CreateSurface( m , Br_Right)
	 AddVertex s,x2,y2,z1,0,0:AddVertex s,x2,y2,z2,1,0
	 AddVertex s,x2,y1,z2,1,1:AddVertex s,x2,y1,z1,0,1
	 AddTriangle s,0,1,2:AddTriangle s,0,2,3
  EndIf

	;front face
  If o_front=1 Then
	 s=CreateSurface( m , Br_Front)
	 AddVertex s,x1,y2,z1,0,0:AddVertex s,x2,y2,z1,1,0
	 AddVertex s,x2,y1,z1,1,1:AddVertex s,x1,y1,z1,0,1
	 AddTriangle s,0,1,2:AddTriangle s,0,2,3
  EndIf

	;back face
  If o_back=1 Then
	 s=CreateSurface( m , Br_Back)
	 AddVertex s,x2,y2,z2,0,0:AddVertex s,x1,y2,z2,1,0
	 AddVertex s,x1,y1,z2,1,1:AddVertex s,x2,y1,z2,0,1
	 AddTriangle s,0,1,2:AddTriangle s,0,2,3
  EndIf

  UpdateNormals m
	  
  ;EntityPickMode m,2 ;Poly

  Return m  

End Function


Global cam_x#,cam_z#,cam_pitch#,cam_yaw#,cam_speed#=.5		; Current
Global dest_cam_x#,dest_cam_z#,dest_cam_pitch#,dest_cam_yaw#	; Destination
Global ent_x#,ent_z#,ent_pitch#,ent_yaw#,ent_speed#=.5		; Current
Global dest_ent_x#,dest_ent_z#,dest_ent_pitch#,dest_ent_yaw#	; Destination

Function mouselook(Lcamera)
  If MouseDown(1) Then MoveEntity lcamera,0,0,2
  If MouseDown(2) Then MoveEntity lcamera,0,0,-2
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
	MoveMouse 400,300
	MoveEntity Lcamera,x#,y#,z#
		
End Function	

Function buttoncheck()
bflag=0
  If KeyHit(46) Then bflag=1
  If KeyHit(48) Then bflag=2
  If KeyHit(38) Then bflag=3
  If KeyHit(22) Then bflag=4

  If KeyHit(203) Then bflag=203 ;left
  If KeyHit(205) Then bflag=205 ;right
  If KeyHit(200) Then bflag=200 ;up
  If KeyHit(208) Then bflag=208 ;down

  If KeyHit(12) Then bflag=100 ;scale down
  If KeyHit(74) Then bflag=100 ;scale down

  If KeyHit(13) Then bflag=101 ;scale up
  If KeyHit(78) Then bflag=100 ;scale up
  

Select bflag
 Case 1
      If EntityX(cube2,1)=0 And EntityY(cube2,1)=0 And EntityZ(cube2,1)=0 Then 
         Else
         For dm.dropmodel=Each dropmodel
             EntityPickMode dm\ent,0
         Next
      ScaleMesh cube2,setscale,setscale,setscale
      s=GetSurface(cube2,1)
      v=CountVertices(s)-1
      For newuv=0 To v
          uu#=VertexU(s,newuv)*-setscale
          vv#=VertexV(s,newuv)*-setscale
          VertexTexCoords s,newuv,uu,vv
      Next
	  mode=2
	  ee = MilliSecs()
      uc.undocave=New undocave
      uc\ent=cube
      HideEntity uc\ent
	  man = csg(cube,cube2,Mode) ;1 Add , 2 Sub , 3 Diff
	  ee = MilliSecs() -ee
	  cube = man
	  FlushKeys
	  UpdateNormals cube
	  AutoSmooth(cube,.25)
      countundos()               
	  EntityPickMode cube,2
	  PositionEntity cube2,EntityX(cam,1),EntityY(cam,1),EntityZ(cam,1)   
	  CameraPick cam,GraphicsWidth()/2,(GraphicsHeight()/2)
	  PositionEntity temppivot,PickedX(),PickedY(),PickedZ()
	  PointEntity cube2,temppivot
	  PositionEntity cube2,PickedX(),PickedY(),PickedZ()   
	  EntityPickMode cube,0
	  ScaleMesh cube2,1/setscale,1/setscale,1/setscale
      For dm.dropmodel=Each dropmodel
	      EntityPickMode dm\ent,2
      Next
     EndIf
 Case 2
               If EntityX(cube2,1)=0 And EntityY(cube2,1)=0 And EntityZ(cube2,1)=0 Then 
               Else 
               For dm.dropmodel=Each dropmodel
               EntityPickMode dm\ent,0
               Next
               ScaleMesh cube2,setscale,setscale,setscale
               checked.window=Null
 			   mode=1
			   ee = MilliSecs()
			   man = csg(cube,cube2,Mode) ;1 Add , 2 Sub , 3 Diff
			   ee = MilliSecs() -ee
               uc.undocave=New undocave
               uc\ent=cube
               HideEntity uc\ent
			   cube = man
			   FlushKeys
			   UpdateNormals cube
      		   AutoSmooth(cube,.25)
               countundos() 
 			   EntityPickMode cube,2
			   PositionEntity cube2,EntityX(cam,1),EntityY(cam,1),EntityZ(cam,1)   
			   CameraPick cam,GraphicsWidth()/2,(GraphicsHeight()/2)
			   PositionEntity temppivot,PickedX(),PickedY(),PickedZ()
			   PointEntity cube2,temppivot
			   PositionEntity cube2,PickedX(),PickedY(),PickedZ()   
			   EntityPickMode cube,0
			   ScaleMesh cube2,1/setscale,1/setscale,1/setscale
               For dm.dropmodel=Each dropmodel
               EntityPickMode dm\ent,2
               Next
              EndIf
 Case 3
    RotateEntity cube2,0,EntityYaw(cube2,1),EntityRoll(cube2,1)
 Case 4
                For UC.UNDOCAVE=Each undocave
                    If uc.undocave=Last undocave Then 
                         FreeEntity Cube
                         Cube=uc\ent
                         ShowEntity cube
                         Delete uc
                     EndIf
                  Next 
 Case 203
                TurnEntity cube2,0,0,22.5 ;left
 Case 205
                TurnEntity cube2,0,0,-22.5 ;right
 Case 200
                TurnEntity cube2,-22.5,0,0 ;back
 Case 208
                TurnEntity cube2,22.5,0,0 ;forward
        Case 101
                setscale=setscale+.1 
                If setscale>2 Then setscale=2
                ScaleEntity cube2,setscale#,setscale#,setscale#
;-----------------
        Case 100 
                setscale=setscale-.1 
                If setscale<.2 Then setscale=.2
                ScaleEntity cube2,setscale#,setscale#,setscale#

End Select

 newlightrange=FUI_SendMessage(GUI_RIGHTWIN_RANGE,m_getvalue)
 If newlightrange<>Old_LightRange Then 
  Old_LightRange=newlightrange 
  EndIf
 

	If new_lightred<>old_Lightred Or new_lightgreen<>old_Lightgreen Or  new_lightblue<>old_Lightblue Then 
		old_Lightred=new_lightred 
		old_Lightgreen=new_lightgreen
		old_Lightblue=new_lightblue
		TIMAGE = CreateImage( 50, 20 )
		SetBuffer ImageBuffer(timage)
		Color new_lightred,new_lightgreen,new_lightblue
		Rect 0, 0, 50, 20,1
        FUI_DeleteGadget(GUI_RIGHTWIN_LGTCanvas)
        GUI_RIGHTWIN_LGTCanvas = FUI_ImageBox( GUI_RIGHTWIN, 5, 70, 50, 20,TIMAGE)
		SetBuffer BackBuffer()
        FreeImage timage 
	EndIf

	If new_ambientred<>old_Ambientred Or new_ambientgreen<>old_ambientgreen Or  new_ambientblue<>old_ambientblue Then 
		old_Ambientred=new_ambientred
		old_ambientgreen=new_ambientgreen
		old_ambientblue=new_ambientblue
		TIMAGE = CreateImage( 50, 20 )
		SetBuffer ImageBuffer(timage)
		Color new_ambientred,new_ambientgreen,new_ambientblue
		Rect 0, 0, 50, 20,1
        FUI_DeleteGadget(GUI_RIGHTWIN_AMBCanvas)
        GUI_RIGHTWIN_AMBCanvas = FUI_ImageBox( GUI_RIGHTWIN, 5, 5, 50, 20,TIMAGE)
		SetBuffer BackBuffer()
        FreeImage timage 
	EndIf


If MouseHit(2) And  (KeyDown(29) Or KeyDown(157)) Then
  Rng = FUI_SendMessage(GUI_RIGHTWIN_RANGE,m_getvalue)
  llR = new_lightred
  llG = new_lightgreen
  llB = new_lightblue
  DropModel(1,llr,llg,llb,rng,1)
 EndIf


For e.Event = Each event
  Select e\EventId
   Case GUI_MENUABOUT_INFO 
     FUI_SendMessage(GUI_ABOUTWIN,M_OPEN)
   Case gui_MENUFILE_Exit
   yn=Fui_confirm("Quit program","Yes","No")
   If yn=1 Then 
    ChangeDir thispath$
    ClearWorld(True,True)
    EndGraphics()
    End 
    EndIf
;-------
 Case GUI_MENUSHAPE_CARVE, Actionbutton(1)  ;BFLAG 1
      If EntityX(cube2,1)=0 And EntityY(cube2,1)=0 And EntityZ(cube2,1)=0 Then 
         Else
         For dm.dropmodel=Each dropmodel
             EntityPickMode dm\ent,0
         Next
      ScaleMesh cube2,setscale,setscale,setscale
      s=GetSurface(cube2,1)
      v=CountVertices(s)-1
      For newuv=0 To v
          uu#=VertexU(s,newuv)*-setscale
          vv#=VertexV(s,newuv)*-setscale
          VertexTexCoords s,newuv,uu,vv
      Next
	  mode=2
	  ee = MilliSecs()
      uc.undocave=New undocave
      uc\ent=cube
      HideEntity uc\ent
	  man = csg(cube,cube2,Mode) ;1 Add , 2 Sub , 3 Diff
	  ee = MilliSecs() -ee
	  cube = man
	  FlushKeys
	  UpdateNormals cube
	  AutoSmooth(cube,.25)
      countundos()               
	  EntityPickMode cube,2
	  PositionEntity cube2,EntityX(cam,1),EntityY(cam,1),EntityZ(cam,1)   
	  CameraPick cam,GraphicsWidth()/2,(GraphicsHeight()/2)
	  PositionEntity temppivot,PickedX(),PickedY(),PickedZ()
	  PointEntity cube2,temppivot
	  PositionEntity cube2,PickedX(),PickedY(),PickedZ()   
	  EntityPickMode cube,0
	  ScaleMesh cube2,1/setscale,1/setscale,1/setscale
      For dm.dropmodel=Each dropmodel
	      EntityPickMode dm\ent,2
      Next
     EndIf
;-----------------
 	   Case GUI_RIGHTWIN_RESETLIGHT
           sc=CountSurfaces(cube)
           For si=1 To sc                
               s=GetSurface(cube,si)
               For v=0 To CountVertices(s)-1
                   VertexColor s,v,255,255,255
               Next
           Next  
;-----------------
	   Case GUI_RIGHTWIN_RESETCAMERA
	        checked.window=Null
            resetcam()
;-----------------
        Case OBJECTBUTTON(1)
                changeshape("Spike_Single.3ds")
;-----------------
        Case OBJECTBUTTON(2)
                changeshape("Spike_Group.3ds")
;-----------------
        Case TUNNELBUTTON(1)
                changeshape("tunnel1.3ds")
;-----------------
        Case TUNNELBUTTON(2)
                changeshape("tunnel2.3ds")
;-----------------
        Case TUNNELBUTTON(3)
                changeshape("tunnel_long.3ds")
;-----------------
        Case TUNNELBUTTON(4)
                changeshape("tunnel_spiral.3ds")
;-----------------
        Case TUNNELBUTTON(5)
                changeshape("tunnel_turn.3ds")
;-----------------
        Case TUNNELBUTTON(6)
                changeshape("tunnel_stairs.3ds")
;-----------------
        Case ROOMBUTTON(1)
                changeshape("room_plain.3ds")
;-----------------
        Case ROOMBUTTON(2)
                changeshape("room_donut.3ds")
;-----------------
        Case ROOMBUTTON(3)
                changeshape("room_fat.3ds")
;-----------------
        Case ROOMBUTTON(4)
                changeshape("room_odd.3ds")
;-----------------
        Case ROOMBUTTON(5)
                changeshape("room_spiral.3ds")
;-----------------
        Case ROOMBUTTON(6)
                changeshape("room_big.3ds")
;-----------------
		Case GUI_RIGHTWIN_LIGHTMAP
  			    lightmapit()   
                SmoothVLIGHT(cube)
;-----------------
       Case ACTIONBUTTON(3),GUI_MENUSHAPE_LEVEL ;BFLAG 3
                RotateEntity cube2,0,EntityYaw(cube2,1),EntityRoll(cube2,1)
;-----------------
        Case GUI_MENUALTER_SUP 
                setscale=setscale+.1 
                If setscale>2 Then setscale=2
                ScaleEntity cube2,setscale#,setscale#,setscale#
;-----------------
        Case GUI_MENUALTER_SDOWN 
                setscale=setscale-.1 
                If setscale<.2 Then setscale=.2
                ScaleEntity cube2,setscale#,setscale#,setscale#
;-----------------
        Case GUI_MENUALTER_FORWARD
                TurnEntity cube2,22.5,0,0
;-----------------
        Case GUI_MENUALTER_BACK
                TurnEntity cube2,-22.5,0,0
;-----------------
        Case GUI_MENUALTER_LEFT
                TurnEntity cube2,0,0,22.5
        Case GUI_MENUALTER_RIGHT
                TurnEntity cube2,0,0,-22.5
;-----------------
	 	Case actionbutton(5)
                showcompass=showcompass*-1
                If showcompass=1 Then ShowEntity compass Else HideEntity compass 		        
;-----------------
        Case actionbutton(4), GUI_MENUSHAPE_UNDO ;blfag 4  
                For UC.UNDOCAVE=Each UndoCave
                    If uc.undocave=Last UndoCave Then 
                         FreeEntity cube
                         cube=uc\ent
                         ShowEntity cube
                         Delete uc
                     EndIf
                  Next 
;-----------------
        Case GUI_MENUSHAPE_NEW
                shape$=""
                ChangeDir thispath$
				tFile = FUI_OpenDialog( "Load shape geometry", "Data\RCCAVES\Shapes\", "3DS (*.3ds)|*.3ds|Blitz basic format (*.b3d)|*.b3d|Direct X (*.x)|*.x")
				If tFile = True Then
			    shape$=app\currentfile
                If FileType (shape$)=1 Then 
	                FreeEntity cube2
					cube2 = LoadMesh(shape$);LoadMesh("DATA\RCCAVES\roomtool.3ds")
        	        ScaleMesh cube2,.2,.2,.2
            	    ScaleEntity cube2,setscale#,setscale#,setscale#
         	    	For s=1 To CountSurfaces(cube2)
					 surf=GetSurface(cube2,s)
					 PaintSurface surf,br2
					 Next 
            	    EntityAlpha cube2,.3
                	EntityBlend cube2, 3 
                EndIf 
                ChangeDir thispath$
                EntityPickMode cube,2
                PositionEntity cube2,EntityX(cam,1),EntityY(cam,1),EntityZ(cam,1)   
                CameraPick cam,GraphicsWidth()/2,(GraphicsHeight()/2)
                PositionEntity temppivot,PickedX(),PickedY(),PickedZ()
                PointEntity cube2,temppivot
                PositionEntity cube2,PickedX(),PickedY(),PickedZ()   
                EntityPickMode cube,0
                EndIf
;-----------------
		Case GUI_RIGHTWIN_TEXTURE
                ChangeDir thispath$
				tFile = FUI_OpenDialog( "Load texture", "Data\RCCAVES\Textures\", "png (*.png)|*.png|bmp (*.bmp)|*.bmp|jpg (*.jpg)|*.jpg")
                If tfile = True Then 
                newtex$=app\currentfile
                If FileType (newtex$)=1 Then 
                retexture(newtex$)
                EndIf
			    EntityPickMode cube,2
			    PositionEntity cube2,EntityX(cam,1),EntityY(cam,1),EntityZ(cam,1)   
			    CameraPick cam,GraphicsWidth()/2,(GraphicsHeight()/2)
			    PositionEntity temppivot,PickedX(),PickedY(),PickedZ()
			    PointEntity cube2,temppivot
			    PositionEntity cube2,PickedX(),PickedY(),PickedZ()   
			    EntityPickMode cube,0
			    EndIf
;-----------------
          Case actionbutton(2),GUI_MENUSHAPE_COMBINE ;bflag2
               If EntityX(cube2,1)=0 And EntityY(cube2,1)=0 And EntityZ(cube2,1)=0 Then 
               Else 
               For dm.dropmodel=Each DropModel
               EntityPickMode dm\ent,0
               Next
               ScaleMesh cube2,setscale,setscale,setscale
               checked.window=Null
 			   mode=1
			   ee = MilliSecs()
			   man = CSG(cube,cube2,Mode) ;1 Add , 2 Sub , 3 Diff
			   ee = MilliSecs() -ee
               uc.undocave=New UndoCave
               uc\ent=cube
               HideEntity uc\ent
			   cube = man
			   FlushKeys
			   UpdateNormals cube
      		   AutoSmooth(cube,.25)
               CountUndos() 
 			   EntityPickMode cube,2
			   PositionEntity cube2,EntityX(cam,1),EntityY(cam,1),EntityZ(cam,1)   
			   CameraPick cam,GraphicsWidth()/2,(GraphicsHeight()/2)
			   PositionEntity temppivot,PickedX(),PickedY(),PickedZ()
			   PointEntity cube2,temppivot
			   PositionEntity cube2,PickedX(),PickedY(),PickedZ()   
			   EntityPickMode cube,0
			   ScaleMesh cube2,1/setscale,1/setscale,1/setscale
               For dm.dropmodel=Each DropModel
               EntityPickMode dm\ent,2
               Next
              EndIf
;-----------------
	    Case GUI_MENUFILE_NEW     
        newwork()

;-----------------
       Case GUI_MENUFILE_SAVE
       oldpath$=CurrentDir()
       rd$=""

  	   tFile = FUI_SaveDialog( "Load saved geometry", "Data\RCCAVES\", "Blitz basic format (*.b3d)|*.b3d")
       If tfile=True Then rd$ = app\currentfile
       rd$=strip_path$(rd$) 
       ChangeDir oldpath$ 

       If rd$<>"" Then 
       If Len(rd$)<3 Then 
               FUI_Notify("Invalid Filename")
               Return  
               EndIf
       If Right$(rd$,4)<> ".b3d" Then rd$=rd$+".b3d"
       If FileType(CurrentDir()+"Data\RCCAVES\"+rd$)=1 Then 
            FCheck=Fui_Confirm("Overwrite File?","Yes","No")
            If Fcheck=1 Then  SAVEWORK(rd$) 
    	    ChangeDir thispath$
        Else
		  SAVEWORK(rd$) 
       EndIf         
       EndIf
;-----------------
       Case GUI_MENUFILE_OPEN
       oldpath$=CurrentDir()
       rd$=""
  	   tFile = FUI_OpenDialog( "Saved Cave", "Data\RCCAVES\", "Blitz basic format (*.b3d)|*.b3d")
       If tfile=True Then 
       rd$=app\currentfile
       rd$=strip_path$(rd$) 
       ChangeDir oldpath$ 
       If rd$<>"" Then 
       If Len(rd$)<3 Then 
               fui_Notify("Invalid Filename","Ok")
               Return  
               EndIf
       If Right$(rd$,4)<>".b3d" Then rd$=rd$+".b3d"
       loadwork(rd$) 
       EndIf 
      EndIf
;-----------------
       Case GUI_MENUFILE_EXPORT
       If CountSurfaces(cube)=>2 Then 
       oldpath$=CurrentDir()
       If FileType(Thispath$+"Data\Meshes\RCCAVES\")<>2 Then CreateDir (Thispath$+"Data\Meshes\RCCAVES\")
       rd$=""
  	   tFile = FUI_SaveDialog( "Export Cave", Thispath$+"Data\Meshes\RCCAVES\", "Blitz basic format (*.b3d)|*.b3d")
       If tfile=True Then 
       rd$=app\currentfile
       rd$=strip_path$(rd$) 
       ChangeDir oldpath$ 
       If rd$="" Then Return
       If Len(rd$)<3 Then 
               Fui_Notify("Invalid Filename","Ok")
               EndIf
       If Right$(rd$,4)<> ".b3d" Then rd$=rd$+".b3d"
       If FileType(CurrentDir()+"Data\Meshes\RCCAVES\"+rd$)=1 Then 
            FCheck=fui_Confirm("Overwrite File?","Yes","No")
            If Fcheck=1 Then  ExportWork(rd$) 
    	    ChangeDir thispath$
        Else
		  ExportWork(rd$) 
       EndIf         
       EndIf
       Else  
        FUI_NOTIFY("You must make a change in order to export")
       EndIf
;new_lightred new_ambientred
Case GUI_RIGHTWIN_AMBCOLOR
					tColor = FUI_ColorDialog( tRed, tGreen, tBlue )
					If tColor = True Then
						new_ambientred = FUI_GetRed( App\CurrentRGB )
						new_ambientgreen = FUI_GetGreen( App\CurrentRGB )
						new_ambientblue = FUI_GetBlue( App\CurrentRGB )

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
						new_lightgreen = FUI_GetGreen( App\CurrentRGB )
						new_lightblue = FUI_GetBlue( App\CurrentRGB )

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

;####################################################################################################

Function retexture(texF$)
 ChangeDir thispath$
 Image=loadTEXTURE_safe(texF$)
; BrushTexture brush,texture
 Fui_applyTEXTURE(GUI_RIGHTWIN_TEXTURE,image,1)
 FreeTexture image

 ;---------------------------------------------------------------------------------------

 ChangeDir thispath$
 FreeBrush br2
 FreeTexture tex2 
 tex2=LoadTexture_safe(texF$);CreateTexture(64,64)
 ScaleTexture tex2,.1,.1
 SetBuffer BackBuffer()
 br2=CreateBrush()
 BrushTexture br2,tex2
 For s=1 To CountSurfaces(cube2)
  surf=GetSurface(cube2,s)
  PaintSurface surf,br2
 Next 
End Function 
 
Function DropModel(islight=0,lr=0,lg=0,lb=0,range=0,dropem=1)
 For dd.dropmodel=Each dropmodel
  EntityPickMode dd\ent,2
  Next
islight=1
EntityPickMode cube,2
cp=thiscamerapick()
If cp<>0 Then 
 For dd.dropmodel=Each dropmodel
	 If dd\ent=cp Then 
		   selected_model=dd\ent
		   If dd\islight=1 Then 
                EntityPickMode cube,0
                FreeEntity dd\ent
                Delete dd
		        Return
                EndIf
		   EndIf
  Next
  EndIf

If dropem=1 Then 
   	  scc.dropmodel=New dropmodel
  	  SCC\MESHID=selected_meshid
			scc\ent=CreateCube()       
            EntityPickMode scc\ent,2
            ScaleMesh scc\ent,4,4,4
            scc\islight=1
            EntityFX SCC\ENT,1
   			;FitEntity(ObjectCube,SCC\ENT)
;            EntityAlpha objectcube,.45         
            EntityColor scc\ent,lr,lg,lb
            scc\range=range
            DebugLog "Light dropped with range "+range
            scc\cr=lr
            scc\cg=lg
            scc\cb=lb
  	        scc\sx#=1
  	        scc\sy#=1
 	        scc\sz#=1
            scc\pitch=0
            scc\yaw=0
            scc\roll=0
            selected_model=scc\ent
  	        placemodel(PickedX(),PickedY(),PickedZ())
  	 ScaleEntity scc\ent,1,1,1
     EntityAlpha lightball,.55 
     EntityPickMode cube,0
     Return scc\ent
     EndIf 

End Function      

Function placemodel(px#,py#,pz#,islight=0)
 For dm.dropmodel=Each dropmodel
   If selected_model=dm\ent Then 
    
     cp=thiscamerapick()
 	 PositionEntity dm\ent,PickedX(),PickedY(),PickedZ()
     PointEntity dm\ent,cam
     MoveEntity dm\ent,0,0,20
     EndIf
   Next
End Function


Function Thiscamerapick()
 Return CameraPick(cam,MouseX(),MouseY())
End Function



Function lightmapit() 
CameraFogMode cam,0


r=new_ambientred
g=new_ambientGREEN
b=new_ambientblue

EntityFX cube,1+2
EntityPickMode cube,2
SetVertexLightAmbience( cube, R, G, B )
Delete Each VertexLighttype

nlights=0
For d.dropmodel=Each dropmodel
		nlights=nlights+1
		EntityPickMode d\ent,0
   	    Next

lcount=0
For l.dropmodel=Each dropmodel
        thislight=thislight+1
	    DebugLog "Lightmapping, light found"
        RenderWorld()
        Color 255,255,255
    	DebugLog  "Using light # "+thislight+" out of "+nlights
        Flip()
        Cls
        vertexLightMesh(cube,1,EntityX(l\ent,1),EntityY(l\ent,1), EntityZ(l\ent,1),l\cr,l\cg, l\cb,l\range,1,0,0,0,0 )
		Next

For d.dropmodel=Each dropmodel
 EntityPickMode d\ent,2 
 If d\islight=1 Then EntityPickMode d\ent,2
Next
EntityPickMode cube,0
End Function

Function FitEntity(EN1,EN2)
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
;smoothvlight(cube)
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

Function SmoothVLIGHT(MESH)
 nums=CountSurfaces(MESH)
 For sc=1 To nums
   surf=GetSurface(MESH,sc)
   trinum=CountTriangles(surf)-1
   For T=1 To trinum
     r1=VertexRed(surf,TriangleVertex (surf,t,0))     
     g1=VertexGreen(surf,TriangleVertex (surf,t,0))
     b1=VertexBlue(surf,TriangleVertex (surf,t,0))

     r2=VertexRed(surf,TriangleVertex (surf,t,1))     
     g2=VertexGreen(surf,TriangleVertex (surf,t,1))
     b2=VertexBlue(surf,TriangleVertex (surf,t,1))

     r3=VertexRed(surf,TriangleVertex (surf,t,2))     
     g3=VertexGreen(surf,TriangleVertex (surf,t,2))
     b3=VertexBlue(surf,TriangleVertex (surf,t,2))

    newr=(r1+r2+r3)/3
    newg=(g1+g2+g3)/3
    newb=(b1+b2+b3)/3
    For i=0 To 2
      VertexColor surf,TriangleVertex (surf,t,i),newr,newg,newb
      VertexColor surf,TriangleVertex (surf,t,i),newr,newg,newb
      VertexColor surf,TriangleVertex (surf,t,i),newr,newg,newb
      Next
  Next
Next
End Function

Function ExportWork(fn$)
If Lower(Right$(fn$,3))<>"b3d" Then fn$=fn$+".b3d"
;thisfold$=removeextension$(fn$)+"\"
;savework("Data\Architect\SAVES\exported.act")
ChangeDir thispath$
destmesh=cave_copymesh(cube);CreateMesh()
;For dm.dropmodel=Each dropmodel
;If dm\islight=0 Then 
;DebugLog "added item to export"
;ScaleMesh dm\ent,dm\sx,dm\sy,dm\sz
;RotateMesh dm\ent,EntityPitch(dm\ent,True),EntityYaw(dm\ent,True),EntityRoll(dm\ent,True)
;PositionMesh dm\ent,EntityX(dm\ent,True),EntityY(dm\ent,True),EntityZ(dm\ent,True)
;AddMesh dm\ent,destmesh
;EndIf
;Next

UpdateNormals destmesh
weld (destmesh)
THISFOLD$=strip_path$(fn$)
;centermesh destmesh
ScaleMesh destmesh,3,3,3
savemultimesh (destmesh,"Data\MESHES\RCCAVES\"+thisfold$,"Data\MESHES\RCCAVES\",7)
ChangeDir thispath$

;UNREM THIS FOR ADDING TO DATABASE
thisid=addmeshtodatabase ("RCCAVES\"+thisfold$,False)
If thisid=-1 Then  Encrypt_B3D(thispath$+"Data\Meshes\RCCAVES\" +thisfold$)
FreeEntity destmesh
fui_notify("Cave exported. ","Ok")
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

Function CenterMesh (entity) 
FitMesh entity, -(MeshWidth (entity) / 2), (MeshHeight (entity)/2 ), -(MeshDepth (entity) / 2), MeshWidth (entity), -MeshHeight(ENTITY), MeshDepth (entity) 
End Function

Function cave_copymesh(child,startsurf=2)

    landscape2=CreateMesh()
 ;  For su=1 To CountSurfaces(child)
    surfnum=CountSurfaces(child)
    For i=startsurf To surfnum

    surf=GetSurface(child,i)
    brush=GetSurfaceBrush(surf)   
    surf2=CreateSurface(landscape2,brush)
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

         

      v0=AddVertex(surf2,v_x0,v_y0,v_z0,v_u0,v_v0)
      v1=AddVertex(surf2,v_x1,v_y1,v_z1,v_u1,v_v1)
      v2=AddVertex(surf2,v_x2,v_y2,v_z2,v_u2,v_v2)
      AddTriangle(surf2,v0,v1,v2)

      VertexColor surf2, v0,v_r1,v_g1,v_b1,v_a0
      VertexColor surf2, v1,v_r2,v_g2,v_b2,v_a1
      VertexColor surf2, v2,v_r3,v_g3,v_b3,v_a2

    Next
   Next    

  UpdateNormals landscape2
  Return landscape2

 End Function

Function SAVEWORK(SF$)
ChangeDir thispath$
THISFOLD$=strip_path$(SF$)
;mysave=cave_copymesh(cube,1)
savemultimesh (cube,"Data\RCCAVES\"+thisfold$,"Data\RCCAVES\",7)
FreeEntity mysave
lightf$=Left$(thisfold$,Len(thisfold$)-3)
lightf$=lightf$+"LGT"
ChangeDir thispath$
sfl=WriteFile("Data\RCCAVES\"+lightf$)

 
 
For dm.dropmodel=Each DropModel

   WriteInt sfl,dm\islight
   WriteInt sfl,dm\meshid

   WriteFloat sfl,EntityX(dm\ent,1)   
   WriteFloat sfl,EntityY(dm\ent,1)   
   WriteFloat sfl,EntityZ(dm\ent,1)   

   WriteFloat sfl,dm\sx   
   WriteFloat sfl,dm\sy   
   WriteFloat sfl,dm\sz  

   WriteFloat sfl,EntityPitch(dm\ent,1)   
   WriteFloat sfl,EntityYaw(dm\ent,1)   
   WriteFloat sfl,EntityRoll(dm\ent,1)   

   WriteInt sfl,dm\CR
   WriteInt sfl,dm\Cg
   WriteInt sfl,dm\Cb

   WriteInt sfl,dm\range

Next

CloseFile sfl

ChangeDir thispath$
fui_notify("Cave Saved.")

End Function 

Function loadwork(lf$)
ChangeDir thispath$
THISFOLD$=strip_path$(lf$)
YN=fui_confirm("Load Cave ?","Yes","No")
If yn=0 Then Return

If FileType ("Data\RCCAVES\"+thisfold$)<>1 Then Return
FreeEntity cube
cube=LoadMesh("Data\RCCAVES\"+thisfold$)
UpdateNormals (cube)

;cube_old=CopyMesh(cube)
For uc.undocave=Each UndoCave
HideEntity uc\ent
Next


lightf$=Left$(thisfold$,Len(thisfold$)-3)
lightf$=lightf$+"LGT"
For dm.dropmodel=Each DropModel
 FreeEntity dm\ent
 Delete dm
 Next

ChangeDir thispath$
If FileType ("Data\RCCAVES\"+lightf$)<>1 Then Return
ll=ReadFile("Data\RCCAVES\"+lightf$)
 While Not Eof(ll)
 islight=ReadInt(ll)
 meshid=ReadInt(ll)

 ex#=ReadFloat(ll)
 ey#=ReadFloat(ll)
 ez#=ReadFloat(ll)

 esx#=ReadFloat(ll)
 esy#=ReadFloat(ll)
 esz#=ReadFloat(ll)

 epit#=ReadFloat(ll)
 eyaw#=ReadFloat(ll)
 erol#=ReadFloat(ll)

 er=ReadInt(ll)
 eg=ReadInt(ll)
 eb=ReadInt(ll)
 
  range=ReadInt(ll)

 If islight=1 Then 
  dm.dropmodel=New DropModel
  dm\ent=CreateCube():ScaleEntity dm\ent,4,4,4 
  PositionEntity dm\ent,ex,ey,ez
  dm\islight=1
  dm\x=ex 
  dm\y=ey 
  dm\z=ez 
  dm\pitch=epit#
  dm\yaw=epit#
  dm\roll=epit#
  dm\sx=esx 
  dm\sx=esy 
  dm\sx=esz 
  dm\range=range
  dm\cr=er
  dm\cg=eg
  dm\cb=eb
  RotateEntity dm\ent,epit,eyaw,eroll
  EntityColor dm\ent,er,eg,eb
 EndIf
 Wend
 If MeshWidth(cube)>300 Then Cavesize=600 Else Cavesize=300
 resetcam
Leftv_main#=LeftVert(cube)
Rightv_Main#=RightVert(cube)
UpV_main#=Highvert(cube)
Downv_main#=LowVert(cube)
frontv_main#=FrontVert(cube)
backv_main#=BackVert(cube)
 lightmapit()   

End Function

Function newwork()

YN=fui_confirm("New cave ?","Yes","No")
If yn<>1 Then Return
ms=fui_confirm("Rock Size ?","Small","Large")
If ms=1 Then Cavesize=300 Else Cavesize=600

 FreeEntity cube
 For uc.undocave=Each UndoCave
   FreeEntity uc\ent
   Delete uc
  Next

For dm.dropmodel=Each DropModel
 FreeEntity dm\ent
 Delete dm
 Next 
ChangeDir thispath$

 texture=loadTexture_safe("data\rccaves\textures\cdefault.png")
 brush=CreateBrush(255,255,255)
 Image=loadtexture_safe("data\rccaves\textures\cdefault.png") 
 BrushTexture brush,texture
 fui_applytexture(GUI_RIGHTWIN_TEXTURE,image,1)

 FreeTexture image

 cube = CreateCube()
 ScaleMesh cube,Cavesize,Cavesize,Cavesize
 For s=1 To CountSurfaces(cube)
  PaintSurface GetSurface(cube,s),brush
 Next 
 UC.UNDOCAVE=New UndoCave
 UC\ENT = CreateCube()
 ScaleMesh UC\ENT,Cavesize,Cavesize,Cavesize
 For s=1 To CountSurfaces(UC\ENT)
  PaintSurface GetSurface(UC\ENT,s),brush
 Next 
 EntityPickMode uc\ent,0
 FreeBrush brush
 FreeTexture texture
 HideEntity uc\ent
resetcam()
Leftv_main#=LeftVert(cube)
Rightv_Main#=RightVert(cube)
UpV_main#=Highvert(cube)
Downv_main#=LowVert(cube)
frontv_main#=FrontVert(cube)
backv_main#=BackVert(cube)

End Function 

Function changeshape(name$)
                ChangeDir thispath$
                shape$="Data\RCCAVES\Shapes\"+name$
                If FileType (shape$)=1 Then 
                FreeEntity cube2
				cube2 = LoadMesh(shape$);LoadMesh("DATA\RCCAVES\roomtool.3ds")
                ScaleMesh cube2,.2,.2,.2
                ScaleEntity cube2,setscale#,setscale#,setscale#
 		    For s=1 To CountSurfaces(cube2)
				  surf=GetSurface(cube2,s)
				  PaintSurface surf,br2
				 Next 
                EntityAlpha cube2,.3
                EntityBlend cube2, 3 
                EndIf 
               ChangeDir thispath$
   EntityPickMode cube,2
   PositionEntity cube2,EntityX(cam,1),EntityY(cam,1),EntityZ(cam,1)   
   CameraPick cam,GraphicsWidth()/2,(GraphicsHeight()/2)
   PositionEntity temppivot,PickedX(),PickedY(),PickedZ()
   PointEntity cube2,temppivot
   PositionEntity cube2,PickedX(),PickedY(),PickedZ()   
   EntityPickMode cube,0
End Function

Function CountUndos()
For UC.undocave=Each undocave
EntityPickMode uc\ent,0
HideEntity uc\ent
count=count+1
Next
If count>maxundo Then 
 uc1.undocave = First undocave
 FreeEntity uc1\ent
 Delete uc1
EndIf
End Function

Function resetcam()
 PositionEntity cam,0,0,0
 RotateEntity cam,0,0,0
 MoveEntity cam,0,10,-(cavesize*1.6)
; PointEntity cam,cube2
 CameraClsColor cam,0,0,80 
 CameraRange cam,1,2000
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
