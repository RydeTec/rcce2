;Tools
Global RootDir$ = "..\"
Global LogMode = 1; (0 = standard logging, 1 = debug mode)
Global AH_Loca$ = "..\Realm Crafter 1\"
Global AH_Appb$ = "RCSTD"
;Include "antihack.bb"
; Loads Antihack module :)


Include "..\modules\safeloads.bb"
Include "..\Modules\f-ui.bb"
Include "..\Modules\media.bb"
Include "..\modules\B3dfile.bb"

Include "..\modules\rock_Export.bb"

Include "..\modules\Utility.bb"



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

    If GetSystemMetrics(0)<800 Then RuntimeError "RC Rocks requires a minimun of 800X600 desktop resolution"
    
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
	AppTitle( GFX_WINDOW_CAPTION$, "Really quit?" )
    SetBuffer BackBuffer()

    If GetSystemMetrics(0)=>1024 Then 
	 	FUI_Initialise(1024, 768, 0, 2, False, True, "Realm Crafter rock editor")
    	w=1024:h=768                                                                               ;  
    Else 
	 	FUI_Initialise(1024, 768, 0, 2, False, True, "Realm Crafter rock editor")
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

    Global Cam=CreateCamera()  
    ClearTextureFilters()
	TextureFilter "_M",1+4
	TextureFilter "_A",1+2
    CameraRange Cam,1,20000
    CameraClsColor Cam,40,40,40
    CameraClsMode Cam,True,True
	Include "..\modules\rcrocks_gui_shell_FUI.bb"
    ChangeDir thispath$
    positionwindows()




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



	
	

AppTitle( "RC ROCK EDITOR", "Really quit?" )


Global savepath$="DATA\Meshes\RCROCKS\"
If FileType (THISPATH$+savepath$)<>2 Then CreateDir THISPATH$+savepath$
If FileType (THISPATH$+DEPATH$)<>2 Then CreateDir THISPATH$+DEPATH$

;#Region Global Variables Setup
	Global GFX_VARIABLE_VSYNC = True
	Global GUI_VARIABLE_CAMERASPEED# = 120.0
;#End Region


	Global RPIVOT = CreatePivot( )
	PositionEntity Cam, -.5, .1, -11
	CameraClsColor Cam, 6, 9, 20
    TurnEntity RPIVOT, 40, 0, 0
	EntityParent(Cam,RPIVOT)






;**** Internal data ****
Global texscale#=1.0,oldtexscale#=-2.0
Global rocktexture,oldrocktexture
Global rocktexturename$
Global myrock
Dim minirock(1000)
Global minicount
Dim vpos#(6,3)
Global minsides=4
Global maxsides=12
Global rockshine#,oldrockshine#
   myrock=RandomRock(minsides,.5,.5,.5)
light = CreateLight():RotateEntity light,45,0,0
TextureFilter "",1+8
AmbientLight 100,100,100
;#Region Main Loop

InitFPS()

Repeat

	texscale#=Float(Int(FUI_SendMessage(GUI_RIGHTWIN_TEXSCALE,M_GETVALUE))*.01)
	rockshine#=Float(Int(FUI_SendMessage(GUI_RIGHTWIN_SHINE,M_GETVALUE))*.01)
	
	If rockshine#<>oldrockshine#
		EntityShininess myrock,rockshine#
		oldrockshine#=rockshine#
	EndIf
	
	If rocktexture<>oldrocktexture
		EntityTexture myrock,rocktexture
		oldrocktexture=rocktexture
	EndIf
	If texscale#<>oldtexscale# Then 
		If rocktexture Then 
			oldtexscale#=texscale#
			ScaleTexture rocktexture,texscale#,texscale#
		EndIf
	EndIf


		
	;If KeyHit( 3 ) Then NewTreeWindow( )
	If KeyHit( 2 ) Then GFX_VARIABLE_VSYNC = Not GFX_VARIABLE_VSYNC
	
	
	
	
	;#Region Camera Movement	
	;Left Right
	If KeyDown(203) Then TurnEntity RPIVOT, 0, -GUI_VARIABLE_CAMERASPEED#/FPS, 0
	If KeyDown(205) Then TurnEntity RPIVOT, 0, GUI_VARIABLE_CAMERASPEED#/FPS, 0
	;Up Down
	If KeyDown(200) Then TurnEntity RPIVOT, -GUI_VARIABLE_CAMERASPEED#/FPS, 0, 0
	If KeyDown(208) Then TurnEntity RPIVOT, GUI_VARIABLE_CAMERASPEED#/FPS, 0, 0
	;AZ
	If KeyDown(30) Then MoveEntity Cam, 0, 0, GUI_VARIABLE_CAMERASPEED#/FPS
	If KeyDown(44) Then MoveEntity Cam, 0, 0, -GUI_VARIABLE_CAMERASPEED#/FPS
	
	CameraProjMode( App\Cam, False )
	CameraProjMode( Cam, True )
;	UnlockBuffer(BackBuffer()) 
;	LockBuffer(FrontBuffer()) 
	UpdateWorld()
;	LockBuffer(BackBuffer()) 
;	UnlockBuffer(FrontBuffer()) 
	buttoncheck()
	RenderWorld( )
	UpdateFPS()
	CameraProjMode( Cam, False )
	CameraProjMode( App\Cam, True )
	FUI_Update( )
	Flip(0)

	
		
Until quitme=1
End
	





Function RandomRock(sides=6,rndx#,rndy#,rndz#,rscalex#=1.0,rscaley#=1.0,rscalez#=1.0,breakup=0,texture=0,recursize=0,fragarea#=4)
thisrock=CreateSphere(sides)
EntityPickMode thisrock,2
ScaleMesh thisrock,rscalex#,rscaley#,rscalez#
RotateMesh thisrock,Rand(-180,180),Rand(-180,180),Rand(-180,180)

as1=GetSurface(thisrock,1)
 Dim  vpos#(CountVertices(as1)-1,3)
For n=0 To CountVertices(as1)-1
	vpos(n,0)=VertexX(as1,n)
	vpos(n,1)=VertexY(as1,n)
	vpos(n,2)=VertexZ(as1,n)
	vpos(n,3)=0
Next

For n=0 To CountVertices(as1)-1  
DebugLog minicount
SeedRnd MilliSecs()
; change these to make it more or less messy


xm#=Rnd(0,rndx#*1.0)
ym#=Rnd(0,rndy#*1.0)
zm#=Rnd(0,rndz#*1.0)
	For nn=0 To CountVertices(as1)-1

	
	; if the vert has not been monkeyed with monkey away
	If vpos(nn,3)=0
		If vpos(n,0)=vpos(nn,0) And vpos(n,1)=vpos(nn,1) And vpos(n,2)=vpos(nn,2)
			VertexCoords as1,nn,(vpos(nn,0)+xm),(vpos(nn,1)+ym),(vpos(nn,2)+zm)
			vpos(nn,3)=1
			EndIf
		EndIf
		
	Next
Next
UpdateNormals thisrock
If texture<>0 Then EntityTexture thisrock,texture
;diff#=Rnd(.8,.81)
If breakup<>0 Then 
;ScaleMesh thisrock,rscalex#*diff,rscaley#*diff,rscalez#*diff
;otherrock=CreateMesh()
EndIf
  For i=1 To breakup
minicount=minicount+1
thisc=minicount
SeedRnd MilliSecs()
    minirock(minicount)=randomrock(sides,Rnd(.001,rndx#),Rnd(.001,rndx#),Rnd(.001,rndx#),rscalex#,rscaley#,rscalez#,0,texture,fragarea#)
    diff#=Rnd(.8,1.1)
    ScaleMesh minirock(minicount),rscalex#*diff,rscaley#*diff,rscalez#*diff
    xdir#=Rand(-1,1)
    zdir#=Rand(-1,1)
    offx#=MeshWidth(thisrock)*diff*Rnd(.02,.7)*xdir
    offz#=MeshDepth(thisrock)*diff*Rnd(.02,.7)*zdir
    RotateMesh minirock(minicount),Rnd(-180,180),Rnd(-180,180),Rnd(-180,180)
    LinePick offx,50,offz,0,-200,0
    PositionMesh minirock(minicount),EntityX(thisrock,1)+((offx)),0,EntityZ(thisrock,1)+((offz))
    EntityPickMode minirock(minicount),2
    rscaleny#=(rscalex#+rscaley#+rscalez#)*.33
For  rcu=1 To recursize 
minicount=minicount+1
SeedRnd MilliSecs()

 

fsides=4
If sides<4 Then fsides=sides
    minirock(minicount)=randomrock(fsides,Rnd(.01,rndx#),Rnd(.01,rndx#),Rnd(.01,rndx#),rscaleny#,rscaleny#,rscaleny#,0,texture,fragarea#)
    diff#=Rnd(.04,.25)
    ScaleMesh minirock(minicount),rscaleny#*diff,rscaleny#*diff,rscaleny#*diff
    xdir#=Rand(-1,1)
    zdir#=Rand(-1,1)
    xpos=Rand(0,1):If xpos=0 Then xpos=-1
    ypos=Rand(0,1):If ypos=0 Then ypos=-1
    xdir#=Float(xdir#*xpos)
    ydir#=Float(zdir#*ypos)

    offx#=MeshWidth(thisrock)*diff*Rnd(3.0,fragarea#)*xdir
    offz#=MeshDepth(thisrock)*diff*Rnd(3.0,fragarea#)*ydir

    RotateMesh minirock(minicount),Rnd(-180,180),Rnd(-180,180),Rnd(-180,180)
    offy#=MeshWidth(minirock(thisc))*.5
    PositionMesh minirock(minicount),EntityX(minirock(thisc))+offx,0, EntityZ(minirock(thisc))+offz
    Next
  Next
If breakup<>0 Then 
EntityPickMode thisrock,2
EndIf
Return thisrock
End Function

Function readjust(THISROCK,rndx#=.5,rndy#=0,Rndz#=.5)
SN=CountSurfaces(THISROCK)
For SC=1 To SN
as1=GetSurface(thisrock,SC)
 Dim  vpos#(CountVertices(as1)-1,3)
For n=0 To CountVertices(as1)-1
	vpos(n,0)=VertexX(as1,n)
	vpos(n,1)=VertexY(as1,n)
	vpos(n,2)=VertexZ(as1,n)
	vpos(n,3)=0
Next

For n=0 To CountVertices(as1)-1  
SeedRnd MilliSecs()
; change these to make it more or less messy

xm#=Rnd(-rndx#,rndx#)
ym#=Rnd(-rndy#,rndy#)
zm#=Rnd(-rndz#,rndz#)


	For nn=0 To CountVertices(as1)-1
	
	; if the vert has not been monkeyed with monkey away
	If vpos(nn,3)=0
		If vpos(n,0)=vpos(nn,0) And vpos(n,1)=vpos(nn,1) And vpos(n,2)=vpos(nn,2)
			VertexCoords as1,nn,(vpos(nn,0)+xm),(vpos(nn,1)+ym),(vpos(nn,2)+zm)
			vpos(nn,3)=1
			EndIf
		EndIf
		
	Next
Next
Next
UpdateNormals thisrock
End Function


Type Triangle							; This type is for the Remove_Coincident_Tris() function.
		
		Field Surface						; Pointer to the surface this triangle is in.
		Field Index							; The index number for this triangle in the specified surface.
		
	End Type 	


; -------------------------------------------------------------------------------------------------------------------
; This function detects all front-to-front facing tris in a mesh, rebuilds the mesh without them, and returns a
; pointer to the new mesh.
;
; In other words, if two triangles face eachother in a mesh, and both triangles touch at 3 vertices, then both
; will be removed.  If the faces face in the same direction they will not be removed.
;
; Note that it is possible for some vertices to be left around orphaned with no triangle connected to them if all
; triangles that were connected to them were coincident with other triangles, but that shouldn't cause any problems,
; as they won't be visible.
; -------------------------------------------------------------------------------------------------------------------
Function Remove_Coincident_Tris(ThisMesh)

	; Find all the coincident tris.

		Surfaces = CountSurfaces(ThisMesh)

		; Loop through each surface of this mesh.
		For Surface_Index_1 = 1 To Surfaces

			Surface1 = GetSurface(ThisMesh, Surface_Index_1)
			Tris = CountTriangles(Surface1)

			; Loop through each triangle in this surface.
			For Tri_Index_1 = 0 To (Tris-1)
				
				; This triangle has not yet been found to be coincident with any other.
				Coincident = False
				
				; Loop through every triangle after this triangle in this surface.
				For Tri_Index_2 = (Tri_Index_1+1) To (Tris-1)
			
					; If these triangles are coincident...
					If TrisCoincident(Surface1, Tri_Index_1, Surface1, Tri_Index_2)
				
						; Mark this triangle as having been found to be coincident.
						Coincident = True
				
						; Mark both triangles for removal.
						ThisTriangle.Triangle = New Triangle
						ThisTriangle\Surface = Surface1
						ThisTriangle\Index 	 = Tri_Index_1
					
						ThisTriangle.Triangle = New Triangle
						ThisTriangle\Surface = Surface1
						ThisTriangle\Index 	 = Tri_Index_2
	
						; Exit the Tri_Index_2 loop.
						Exit
					
					EndIf
						
				Next

		
				; If we found the first triangle to be coincident with another already, don't look for any more
				; triangles coincident with this one.
				If Not Coincident

					; Otherwise, we failed to find a coincident triangle in the same surface as the triangle we're
					; testing, so look in all the other surfaces.

					; Loop through every triangle in every surface after this surface.
					For Surface_Index_2 = Surface_Index_1+1 To Surfaces

						Surface2 = GetSurface(ThisMesh, Surface_Index_2)
						Tris2 = CountTriangles(Surface2)

						For Tri_Index_2 = 0 To Tris2-1
	
							; If these triangles are coincident...
							If TrisCoincident(Surface1, Tri_Index_1, Surface2, Tri_Index_2)
				
								; Mark this triangle as having been found to be coincident.
								Coincident = True
				
								; Mark both triangles for removal.
								ThisTriangle.Triangle = New Triangle
								ThisTriangle\Surface = Surface1
								ThisTriangle\Index 	 = Tri_Index_1
					
								ThisTriangle.Triangle = New Triangle
								ThisTriangle\Surface = Surface2
								ThisTriangle\Index 	 = Tri_Index_2
	
								; Exit the Tri_Index_2 loop.
								Exit
					
							EndIf

						Next						

						; If we found the first triangle to be coincident with another already, exit Surface_Index_2 loop.
						If Coincident Then Exit
		
					Next

				EndIf

			Next 
		
		Next
	
	
	; Delete all the coincident tris by constructing a new mesh without those tris from the old mesh.

		; Create a new mesh.
		NewMesh = CreateMesh()	

		; Loop through each surface of the mesh.
		For Surface_Index = 1 To Surfaces

			; Get the pointer to the this surface and the number of vertices in it.
			SrcSurface = GetSurface(ThisMesh, Surface_Index)
			Tris = CountTriangles(SrcSurface)
	
			; Create a new surface in the destination mesh to hold the copy of this surface's data.
			DestSurface = CreateSurface(NewMesh)
			
			; Copy all the vertices from the source surface to the destination surface.
			SrcVerts = CountVertices(SrcSurface)
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
				AddVertex(DestSurface, Vx#, Vy#, Vz#, Vu#, Vv#, Vw#)
				VertexNormal(DestSurface, VertLoop, Vnx#, Vny#, Vnz#)
				VertexColor(DestSurface, VertLoop, Vr, Vg, Vb) 
	
			Next

			; Copy all triangles from the source surface to the destination surface.	
			SrcTris  = CountTriangles(SrcSurface)
			For TriLoop = 0 To SrcTris-1
	
				Copy_Tri = True
				
				For ThisTri.Triangle = Each Triangle
									
					; If this triangle is a coincident triangle that should be removed...
					If (ThisTri\Surface = SrcSurface) And (ThisTri\Index = TriLoop) 
						
						; Do not copy the triangle.
						Copy_Tri = False
						
						; Exit ThisTri loop early.
						Exit
						
					EndIf
						
				Next
	
	
				; If it's okay to copy this triangle...
				If Copy_Tri			
		
					V0 = TriangleVertex(SrcSurface, TriLoop, 0)
					V1 = TriangleVertex(SrcSurface, TriLoop, 1)
					V2 = TriangleVertex(SrcSurface, TriLoop, 2)
					AddTriangle(DestSurface, V0, V1, V2)
		
				EndIf
	
			Next
		
		Next


	; Delete the old mesh.
;	FreeEntity ThisMesh
		
	; Delete all the temporary coincident triangle data.		
	Delete Each Triangle

	; Return the pointer to the new mesh.
	Return NewMesh
	
	
End Function 


; -------------------------------------------------------------------------------------------------------------------
; This function returns the squared distance between two vertices.
; -------------------------------------------------------------------------------------------------------------------
Function VertexDist#(Surface1, Vert1, Surface2, Vert2)

	V1x# = VertexX#(Surface1, Vert1)
	V1y# = VertexY#(Surface1, Vert1)
	V1z# = VertexZ#(Surface1, Vert1)
	
	V2x# = VertexX#(Surface2, Vert2)
	V2y# = VertexY#(Surface2, Vert2)
	V2z# = VertexZ#(Surface2, Vert2)
	
	Return (V1x#-V2x#)*(V1x#-V2x#) + (V1y#-V2y#)*(V1y#-V2y#) + (V1z#-V2z#)*(V1z#-V2z#)	
	
End Function	


; -------------------------------------------------------------------------------------------------------------------
; This function returns true if two triangles are coincident.  (Occupy the same space.)
;
; Epsilon is the distance by which the vertices of the triangles can be seperated and still be considered coincident.
; -------------------------------------------------------------------------------------------------------------------
Function TrisCoincident(Surface1, Tri_Index_1, Surface2, Tri_Index_2, Epsilon#=0.001)
		

	; Square the epsilon so that we can use squared distances for speed in comparisons.	
	Epsilon# = Epsilon#*Epsilon# 	
		
		
	; Store the indices of the vertices which make up the triangles.
	T1_Vert0 = TriangleVertex(Surface1, Tri_Index_1, 0)		
	T1_Vert1 = TriangleVertex(Surface1, Tri_Index_1, 1)
	T1_Vert2 = TriangleVertex(Surface1, Tri_Index_1, 2)
			
	T2_Vert0 = TriangleVertex(Surface2, Tri_Index_2, 0)		
	T2_Vert1 = TriangleVertex(Surface2, Tri_Index_2, 1)
	T2_Vert2 = TriangleVertex(Surface2, Tri_Index_2, 2)


	; Check to see if all three vertices of these triangles are coincident.

		Coincident = True
			
		; Check to see if vertex 0 of triangle 1 is coincident with any of the vertices in triangle 2.
		If VertexDist#(Surface1, T1_Vert0, Surface2, T2_Vert0) > Epsilon#
			If VertexDist#(Surface1, T1_Vert0, Surface2, T2_Vert1) > Epsilon#
				If VertexDist#(Surface1, T1_Vert0, Surface2, T2_Vert2) > Epsilon#
					Coincident = False
				EndIf
			EndIf
		EndIf
		
		; If the first test passed...
		If Coincident
				
			; Check to see if vertex 1 of triangle 1 is coincident with any of the vertices in triangle 2.
			If VertexDist#(Surface1, T1_Vert1, Surface2, T2_Vert0) > Epsilon#
				If VertexDist#(Surface1, T1_Vert1, Surface2, T2_Vert1) > Epsilon#
					If VertexDist#(Surface1, T1_Vert1, Surface2, T2_Vert2) > Epsilon#
						Coincident = False
					EndIf
				EndIf
			EndIf

			; If the second test passed...						
			If Coincident
					
				; Check to see if vertex 2 of triangle 1 is coincident with any of the vertices in triangle 2.
				If VertexDist#(Surface1, T1_Vert2, Surface2, T2_Vert0) > Epsilon#
					If VertexDist#(Surface1, T1_Vert2, Surface2, T2_Vert1) > Epsilon#
						If VertexDist#(Surface1, T1_Vert2, Surface2, T2_Vert2) > Epsilon#
							Coincident = False
						EndIf
					EndIf
				EndIf
				
			EndIf
														
		EndIf	

	
	; Return whether the triangles were coincident or not.
	Return Coincident

			
End Function

Function ExportWork(smesh,fn$)
ChangeDir thispath$
centermesh smesh
ScaleMesh smesh,10,10,10
savemultimesh smesh,fn$,(thispath$+savepath$)
ChangeDir thispath$
fn2$=strip_path$(fn$)
ChangeDir thispath$
DebugLog("Added rock as " + "RCrocks\"+fn2$ )
thisid=addmeshtodatabase ("RCrocks\"+fn2$,False)
;If thisid=-1 Then  Encrypt_B3D("Data\Meshes\rcrocks\" +fn2$)
ChangeDir thispath$
ScaleMesh smesh,.1,.1,.1


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

Function CenterMesh (entity)
	FitMesh entity, -(MeshWidth (entity) / 2), -(MeshHeight (entity) / 2), -(MeshDepth (entity) / 2), MeshWidth (entity), MeshHeight (entity), MeshDepth (entity)
End Function

Function BUTTONCHECK()
		MOUSEPRESSED1 = MouseHit(1)
		
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
		
Case GUI_RIGHTWIN_GENERATE
                           FreeEntity myrock   
                           If ROCKTEXTURENAME$ Then ROCKTEXTURE=loadtexture_safe(ROCKTEXTURENAME$)
                             smoothness=GETSLIDERVAL(GUI_RIGHTWIN_SMOOTH)    
                             If GETSLIDERVAL(GUI_RIGHTWIN_DEFORM) < 1 Then 
                               rough#=0 
                                Else 
                               ROUGH#=Float(GETSLIDERVAL(GUI_RIGHTWIN_DEFORM)*.01)    
                               EndIf
                             area#= Float(GETSLIDERVAL(GUI_RIGHTWIN_FRAGMENTAREA))
                             SEGS=(GETSLIDERVAL(GUI_RIGHTWIN_SEGMENTS))    
                             FRAGS=(GETSLIDERVAL(gUI_RIGHTWIN_FRAGMENTS))    
                             scalex#=Float(GETSLIDERVAL(GUI_RIGHTWIN_SCALEX)*.01)    
                             scaley#=Float(GETSLIDERVAL(GUI_RIGHTWIN_SCALEY)*.01)    
                             scalez#=Float(GETSLIDERVAL(GUI_RIGHTWIN_SCALEZ)*.01)    
                             minicount=0
                             myrock=RandomRock(smoothness,rough#,rough#,rough#,scalex#,scaley#,scalez#,segs,rocktexture,frags,area#)
							 For i=1 To 1000
								If minirock(i)<>0 Then 
							 	 DebugLog "added mesh " +i
								 AddMesh minirock(i),myrock
								 FreeEntity minirock(i)
								 minirock(i)=0
							 	 EndIf
							 	Next

 	 			             FitMesh MYROCK,-8,-8,-8,8,8,8,True
                             centermesh myrock 
							 EntityShininess myrock,rockshine#
                             UpdateNormals myrock                                                  
                             If rocktexture<>0 Then EntityTexture myrock,rocktexture

Case GUI_MENUFILE_EXPORT
    Exportmap$=""
    ChangeDir thispath$
	tFile = FUI_SaveDialog( "Export rock", "Data\Meshes\RCrocks\", "RC Model (*.b3d)|*.b3d|RC Model (*.eb3d)|*.eb3d", 2)
	If tFile = True Then
		exportmap$=app\currentfile
		If exportmap$<>""
				Select app\currentIndex
					Case 1
						; unencrypted
						If Instr( exportmap$, ".b3d", 1 ) > 1 Then
							myrock2=remove_coincident_tris(myrock)   
							If rocktexture<>0 Then EntityTexture myrock2,rocktexture
							exportwork  myrock2,exportmap$
							FreeEntity myrock2 
						Else
							exportmap$ = exportmap$ + ".b3d"
							myrock2=remove_coincident_tris(myrock)   
							If rocktexture<>0 Then EntityTexture myrock2,rocktexture
							exportwork  myrock2,exportmap$
							FreeEntity myrock2 
						EndIf
		
					Case 2
						; encrypted
			   
					   If Instr( exportmap$, ".eb3d", 1 ) > 1 Then
									   myrock2=remove_coincident_tris(myrock)   
									   If rocktexture<>0 Then EntityTexture myrock2,rocktexture
									   exportwork  myrock2,exportmap$
									   FreeEntity myrock2 
									   encrypt_b3d(exportmap$)
					   Else
					   exportmap$ = exportmap$ + ".eb3d"
									   myrock2=remove_coincident_tris(myrock)   
									   If rocktexture<>0 Then EntityTexture myrock2,rocktexture
									   exportwork  myrock2,exportmap$
									   FreeEntity myrock2 
									   encrypt_b3d(exportmap$)
					   EndIf
				End Select
			EndIf
	EndIf
	ChangeDir thispath$


   Case gui_rightwin_changetexture
    map$=""
    ChangeDir thispath$
	tFile = FUI_OpenDialog( "Load texture", "Data\meshes\rcrocks\","Bmp image (*.bmp)|*.bmp|png image (*.png)|*.png|jpg image (*.jpg)|*.jpg")
	If tFile = True Then
    map$=app\currentfile
    If FileType(map$)=1 Then      
  			        tImage = loadimage_Safe( map$ )
                    rocktexture=loadtexture_safe(map$)
                    rocktexturename$=map$
					ResizeImage tImage, 100, 100
				        FUI_DeleteGadget(GUI_RIGHTWIN_TextureCanvas)
						GUI_RIGHTWIN_TextureCanvas = FUI_ImageBox( GUI_RIGHTWIN, 20, 50, 100, 100,TIMAGE)
						SetBuffer BackBuffer()
    			        FreeImage timage 
     EndIf
     EndIf
     ChangeDir thispath$
  End Select
  Delete E
Next



End Function 

Function GETSLIDERVAL(GAD)
Return Int(FUI_SendMessage(GAD,M_GETVALUE))
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
