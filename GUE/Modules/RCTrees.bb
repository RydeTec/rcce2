;##############################################################################################################################
; Realm Crafter version 1.10																									
; Copyright (C) 2007 Solstar Games, LLC. All rights reserved																	
; contact@solstargames.com																																																		
;																																																																#
; Programmer: Rob Williams																										
; Program: Realm Crafter Actors module
;																																
;This is a licensed product:
;BY USING THIS SOURCECODE, YOU ARE CONFIRMING YOUR ACCEPTANCE OF THE SOFTWARE AND AGREEING TO BECOME BOUND BY THE TERMS OF 
;THIS AGREEMENT. IF YOU DO NOT AGREE TO BE BOUND BY THESE TERMS, THEN DO NOT USE THE SOFTWARE.
;																		
;Licensee may NOT: 
; (i)   create any derivative works of the Engine, including translations Or localizations, other than Games;
; (ii)  redistribute, encumber, sell, rent, lease, sublicense, Or otherwise transfer rights To the Engine; or
; (iii) remove Or alter any trademark, logo, copyright Or other proprietary notices, legends, symbols Or labels in the Engine.
; (iv)   licensee may Not distribute the source code Or documentation To the engine in any manner, unless recipient also has a 
;       license To the Engine.													
; (v)  use the Software to develop any software or other technology having the same primary function as the Software, 
;       including but not limited to using the Software in any development or test procedure that seeks to develop like 
;       software or other technology, or to determine if such software or other technology performs in a similar manner as the
;       Software																																
;##############################################################################################################################
;Tree and Grass System
;created and coded by Jeff Frazier (rifraf) 2005-2006
;Uses
;LOADTREE(Tfile$,evergreen,ConvertEnt=0,swingstyle=0)
; used  Thisentity=Loadtree("",false,ent,1)
;    TFILE$ is the filename to load from file.  (unused in RC). use ""
;    EVERGREEN true if your tree has color and you do not want it to change colors with season
;    ConvertENT = entity that you will convert to a tree.  RC uses this when loading an area
;    SwingStyle = How branches sway.  0=from their center 1=from top 2=from bottom
;    after you load a tree, you can use any entity/mesh command on the resulting entity.
;LOADGRASS() same as load tree but preps model/mesh for grass optimizing
;DROPTREE(ent,x,z)
;   uses  Droptree (thisentity,x,z)
;drops a tree at x,z  at a picked Y height
;SETTREEPICKMODE(pickmode,leaves)
;    changes all trees to a given pickmode
;    pickmode, if leaves param = true then all leaves for that tree get 
;    the pickmode set as well otherwise, just the trunk gets set
;DELETETREE(entity)
; uses.  Deletetree (thistree)
;	deletes a given tree
;UNLOADTREES()
;	removes ALL loaded trees and grass
;TREE_SETSEASON(season_id)
;	changes season and colors trees/grass to that seasons color
;COUNTREEES()
;	returns number of trees used
;TREE_SETVALUES()
;   loads tree season colors from file, and sets pre-made wind settings
;   for each weather type
;TREE_CHANGEWEATHER(weatherid)
;   changes wind settings to the weather id specified
;CLUMP_GRASS()
;   Takes all the textures from all the grass, reassigns brush properties so that
;   only one texture handle per texture per brush is used. for easy animation
;UPDATETREES(entity)
;   uses Upatadetrees(camera)
; 	this function animates and hides/shows trees and grass as needed
;   the entity param is used to let the update know what entity do do
;   distance checks from.. normally this should be your game camera.
;New Functions
;TREE_AUTOFADE(near#,far#,grassortrees)
;grassortree = true to set grass, =false to set trees
;sets foliage to autofade.  Default autofade
;on grass is set up in the clump_grass() function. using the 
;GrassFadenear, and GrassFadeFar globals
;also if you set trees to fade you will remove vertex lighting from the
;trunks

;FUNCTIONS USED INTERNALLY BY THIS SYSTEM
;nearest_tree(), centermesh(), standmesh(), hangmesh(), distance(), xform()


Global TREE_SEASON=1,TREE_OLDSEASON=1,TREE_WEATHER=2,TREE_OLDWEATHER=0
Global TreeSwayMax_X#,TreeSwayMax_Y#,TreeSwayMax_Z#
Global SwayPower_X#,SwayPower_Y#,SwayPower_Z#
Global seasoncolor_file$=CurrentDir$()+"\Data\Game Data\RCTE.dat"

Global treeautofade=False
Global GrassFadeNear#=50.0
Global GrassFadefar#=60.0
Global GrassSwayPower=13

Global TreeChange=75
Global TreeAnimationRange=200
Global TreeDisplayRange=220
Global Max_Grass_Clumps=100 
Dim weather_wind_swaymax#(7,3)
Dim weather_wind_swayspeed#(7,3)
Dim season_wind_swayspeed#(7,3)

Dim season_wind_swaymax#(7)
Dim grassCLUMP(max_grass_clumps)

Dim season_red(11)
Dim season_green(11)
Dim season_blue(11)

Dim lcos#(3700)
Dim lsin#(3700)
For i=0 To 3600
	lsin#(i) = Sin(Float(i)/10.0)
	lcos#(i) = Cos(Float(i)/10.0)
Next

Const Maxbranches=400

Type RcGrass
 Field ent
 Field surface
 Field brush
 Field tex
 Field texname$
 Field evergreen
End Type

Type GrassTextures
 Field Brush
 Field Texname$
 Field tex
End Type
Global GRASSPOS=0
Global GRASSDIR=5

Type Tree
 Field MainEnt,Mainsurf,MainBrush,Maintex
 Field MaxBranches
 Field Branchent[Maxbranches]
 Field Branchsurf[maxbranches]
 Field Branchbrush[Maxbranches]
 Field BranchTex[Maxbranches]
 Field SwayDir_x[maxbranches],SwayDir_y[maxbranches],SwayDir_z[maxbranches]
 Field SwayValue_x#[maxbranches],SwayValue_y#[maxbranches],SwayValue_z#[maxbranches]
 Field SwayPower_x#[maxbranches],SwayPower_y#[maxbranches],SwayPower_z#[maxbranches]
 Field Color_Red,Color_Blur,Color_Green
 Field distance#,inview
 Field Animateme
 Field Evergreen
; Field isgrass
 Field scalex#,scaley#,scalez#
 Field swingstyle
End Type

tree_Setvalues()
Tree_changeweather(w_sun)
tree_setseason 1

Function LoadTree(Tfile$,evergreen,ConvertEnt=0,swingstyle=0)
	If FileType(tfile$)<>1 And convertent=0 Then Return -1
	bcount=0
	If convertent=0 Then 
	   DUMMY=LoadMesh(TFILE$)
	 Else
	   DUMMY=convertent
EndIf


Firstmain=0
 RT.tree=New Tree
   RSC=CountSurfaces(dummy)
     For SC=1 To RSC
          Surf=GetSurface(dummy,sc)
            tb=GetSurfaceBrush(surf) 
            tt=GetBrushTexture(tb) 
            tn$=TextureName$(tt) 
              

           If Instr(tn$,"branch")= 0 Then dosomthing=1
           If Instr(tn$,"branch")> 0 Then dosomthing=2
             vc=CountVertices(surf)
             tc=CountTriangles(surf)

            If dosomthing=1 Then
             If firstmain=0 Then    
             RT\MainENT=CreateMesh()
             RT\MainSurf=CreateSurface(rt\mainent)
             RT\maintex=tt
             RT\mainbrush=tb
             BrushShininess  rt\mainbrush,0
             BrushFX rt\mainbrush,2
             firstmain=1 
             Else
            ; dosomthing=-1
             ;Limbent=CreateMesh()
             ;PaintMesh limbent,rt\mainbrush
             ;AddMesh limbent,rt\mainent
            ; FreeEntity limbent
             EndIf              

           
 
            Else
             bcount=bcount+1       
             RT\Branchent[bcount]=CreateMesh()
             RT\branchsurf[bcount]=CreateSurface(rt\branchent[bcount]) 
             RT\Branchtex[bcount]=tt
             RT\Branchbrush[bcount]=tb
             BrushShininess  rt\branchbrush[bcount],0
             If swingstyle=0  Then  
             BrushFX rt\branchbrush[bcount],16+2
             Else
             BrushFX rt\branchbrush[bcount],16+2
             EndIf

            TextureFilter rt\branchtex[bcount],1+4
            BrushTexture rt\branchbrush[bcount],rt\BranchTex[bcount]
            EndIf   


    For tri=0 To tc-1
    coloroff=Rand(-65,5)
    coloroff2=Rand(-65,5)
    coloroff3=Rand(-65,5)
    v_r1#=VertexRed(surf,TriangleVertex(Surf,tri,0))+coloroff
    v_g1#=VertexGreen(surf,TriangleVertex(Surf,tri,0))+coloroff
    v_b1#=VertexBlue(surf,TriangleVertex(Surf,tri,0) )+coloroff
    v_r2#=VertexRed(surf,TriangleVertex(Surf,tri,1) )+coloroff2
    v_g2#=VertexGreen(surf,TriangleVertex(Surf,tri,1))+coloroff2
    v_b2#=VertexBlue(surf,TriangleVertex(Surf,tri,1) )+coloroff2
    v_r3#=VertexRed(surf,TriangleVertex(Surf,tri,2) )+coloroff3
    v_g3#=VertexGreen(surf,TriangleVertex(Surf,tri,2))+coloroff3
    v_b3#=VertexBlue(surf,TriangleVertex(Surf,tri,2) )+coloroff3

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
            If dosomthing=1 Then
     v0=AddVertex(RT\mainsurf,v_x0,v_y0,v_z0,v_u0,v_v0)
     v1=AddVertex(RT\mainsurf,v_x1,v_y1,v_z1,v_u1,v_v1)
     v2=AddVertex(RT\mainsurf,v_x2,v_y2,v_z2,v_u2,v_v2)
     AddTriangle(RT\mainsurf,v0,v1,v2)

    VertexColor RT\mainsurf, v0,v_r1,v_g1,v_b1,v_a0
    VertexColor RT\mainsurf, v1,v_r2,v_g2,v_b2,v_a1
    VertexColor RT\mainsurf, v2,v_r3,v_g3,v_b3,v_a2
              ElseIf dosomthing=2
    v0=AddVertex(RT\branchsurf[bcount],v_x0,v_y0,v_z0,v_u0,v_v0)
    v1=AddVertex(RT\branchsurf[bcount],v_x1,v_y1,v_z1,v_u1,v_v1)
    v2=AddVertex(RT\branchsurf[bcount],v_x2,v_y2,v_z2,v_u2,v_v2)
    AddTriangle(RT\branchsurf[bcount],v0,v1,v2)
    VertexColor RT\branchsurf[bcount], v0,v_r1,v_g1,v_b1
    VertexColor RT\branchsurf[bcount], v1,v_r2,v_g2,v_b2
    VertexColor RT\branchsurf[bcount], v2,v_r3,v_g3,v_b3
               EndIf
               Next 

            If dosomthing=1 Then
             PaintSurface RT\mainsurf,RT\mainbrush
             UpdateNormals rt\mainent
             ElseIf dosomthing=2
             PaintSurface RT\BranchSurf[bcount],RT\BranchBrush[bcount]
             UpdateNormals rt\branchent[bcount]
             EndIf
             Next

RT\MaxBranches=Bcount
If swingstyle=0 Then 
 rt\swingstyle=1
 Else
 rt\swingstyle=4
 EndIf

 For i=1 To bcount
  EntityParent RT\Branchent[i],RT\mainent
  RT\swaydir_x[i]=Rand(1,2)
  RT\swaydir_y[i]=Rand(1,2)
  RT\swaydir_z[i]=Rand(1,2)
  rt\swaypower_Y#[i]=Rnd(SwayPower_Y#/3,SwayPower_Y#)
  rt\swaypower_X[i]=Rnd(SwayPower_X#/3,SwayPower_X#)
  rt\swaypower_Z[i]=Rnd(SwayPower_Z#/3,SwayPower_Z#)

S=GetSurface(RT\BRANCHENT[I],1)
 tvY#=VertexY(S,1) 
 tvx#=VertexX(S,1) 
 tvz#=VertexZ(S,1) 
Select swingstyle
 Case 0 ;sway from center
  centermesh RT\BRANCHENT[I]
 Case 1; sway from top
  Hangmesh RT\BRANCHENT[I]
 Case 2; sway from base/bottom
  Standmesh RT\BRANCHENT[I]
  End Select
 tvY2#=VertexY(S,1)
 tvx2#=VertexX(S,1)
 tvz2#=VertexZ(S,1)

newx#=tvx#-tvx2#
newz#=tvz#-tvz2#
newy#=tvy#-tvy2#
TranslateEntity RT\BRANCHENT[I],newx,newy,newz
Next

   FreeEntity dummy
   RT\EVERGREEN=evergreen
   Return RT\MAINENT

End Function



Function updatetrees(from_ENT,tdelta#=1.0)
tdelta#=tdelta*3
updategrass(tdelta#,Rnd(SwayPower_X#/3,SwayPower_X#)*GrassSwayPower)

;match tree system to RC engine
Tree_season=currentseason
Tree_weather=currentweather
TreeDisplayrange=fogfarnow+20
Treeanimationrange=fogfarnow*.6

;change wind patterns to match the weather
If tree_oldweather<>tree_weather Then 
 tree_oldweather=tree_weather
 Tree_changeweather(tree_weather)
EndIf

;recolor trees for season changes if needed
If TREE_OLDSEASON<>TREE_SEASON Then 
tree_setseason(tree_season)
EndIf

trnd=Rand(1,10000)
treecamx#=EntityX(from_ent,1)
treecamy#=EntityY(from_ent,1)
treecamz#=EntityZ(from_ent,1)

rval1=Rand(1,TreeChange)
rval2=Rand(1,TreeChange)
rval3=Rand(1,TreeChange)


For rt.tree=Each tree

newswayx#=Rnd(SwayPower_X#/3,SwayPower_X#)
newswayy#=Rnd(SwayPower_Y#/3,SwayPower_Y#)
newswayz#=Rnd(SwayPower_Z#/3,SwayPower_Z#)

cansee=False
If trnd<250 Then RT\Distance#=DISTANCE(RT\MAINENT,FROM_ENT)
If EntityInView(RT\MAINENT,FROM_ENT)  Then  rt\inview=1  Else rt\inview=0
If RT\Distance<=TreeDisplayRange And rt\inview=1 Then 

ShowEntity RT\MAINENT

If RT\Distance<=TreeAnimationRange Then 

For bn=1 To rt\maxbranches

   If RT\SWINGSTYLE =1  Then 
xdiff# = EntityX(rt\branchent[bn],1)-treecamx#
ydiff# = EntityY(rt\branchent[bn],1)-treecamy#
zdiff# = EntityZ(rt\branchent[bn],1)-treecamz#
pitch# = ATan2(ydiff#,rt\distance#)
yaw#   = ATan2(xdiff#,-zdiff#)
RotateEntity rt\branchent[bn],pitch#,yaw#,0
     Else
       RotateEntity rt\brancHent[bn],0,0,0  
        EndIf

;;X AXIS
  If TreeSwayMax_X#>0 Then
              If rt\swaydir_X[bn]=1 Then 
                rt\swayvalue_X[bn]=rt\swayvalue_X[bn]+rt\swaypower_X[bn] 
                If rt\swayvalue_X[bn]>TreeSwayMax_X# Then 
                              rt\swaydir_X[bn]=2
                              EndIf                            
                              sx#=rt\swayvalue_X[bn]
              Else
                rt\swayvalue_X[bn]=rt\swayvalue_X[bn]-rt\swaypower_X[bn] 
                If rt\swayvalue_X[bn]<-TreeSwayMax_X# Then 
                rt\swaydir_X[bn]=1
               EndIf  
               sx#=rt\swayvalue_X[bn]
               EndIf
              If rval1=TreeChange-TreeChange/2 Then rt\swaypower_X#[bn]=newswayx#
  EndIf

;;Y AXIS
  If TreeSwayMax_Y#>0 Then
              If rt\swaydir_Y[bn]=1 Then 
                rt\swayvalue_Y[bn]=rt\swayvalue_Y[bn]+rt\swaypower_Y[bn] 
                If rt\swayvalue_Y[bn]>TreeSwayMax_Y# Then 
              rt\swaydir_Y[bn]=2
                    EndIf 
 sy#=rt\swayvalue_Y[bn]
              Else
                rt\swayvalue_Y[bn]=rt\swayvalue_Y[bn]-rt\swaypower_Y[bn]  
                If rt\swayvalue_Y[bn]<-TreeSwayMax_Y# Then 
                  rt\swaydir_Y[bn]=1
                  EndIf
                  sy#=rt\swayvalue_Y[bn]
             EndIf
              If rval2=TreeChange-TreeChange/2 Then rt\swaypower_Y#[bn]=newswayy#
  EndIf

;;Z AXIS
  If TreeSwayMax_Z#>0 Then
              If rt\swaydir_Z[bn]=1 Then 
                rt\swayvalue_Z[bn]=rt\swayvalue_Z[bn]+rt\swaypower_Z[bn] 
                If rt\swayvalue_Z[bn]>TreeSwayMax_Z# Then 
                    rt\swaydir_Z[bn]=2
                EndIf
  sz#=rt\swayvalue_z[bn]
                Else
                rt\swayvalue_Z[bn]=rt\swayvalue_Z[bn]-rt\swaypower_Z[bn]  
                If rt\swayvalue_Z[bn]<-TreeSwayMax_Z# Then 
                   rt\swaydir_Z[bn]=1
                   EndIf  
sz#=rt\swayvalue_Z[bn]
              EndIf
              If rval3=TreeChange-TreeChange/2 Then rt\swaypower_Z#[bn]=newswayz#
  EndIf
    RotateEntity RT\branchent[bn], EntityPitch(RT\branchent[bn],1)+((sx/rt\swingstyle)*tdelta), EntityYaw(RT\branchent[bn],1)+((sy/rt\swingstyle)*tdelta), EntityRoll(RT\branchent[bn],1)+((sz/rt\swingstyle)*tdelta),1

Next

Else ;outside of animation range but still inside of viewrange
  For bn=1 To rt\maxbranches
   If RT\SWINGSTYLE =1 Then 
xdiff# = EntityX(rt\branchent[bn],1)-treecamx#
ydiff# = EntityY(rt\branchent[bn],1)-treecamy#
zdiff# = EntityZ(rt\branchent[bn],1)-treecamz#
pitch# = ATan2(ydiff#,rt\distance#)
yaw#   = ATan2(xdiff#,-zdiff#)
RotateEntity rt\branchent[bn],pitch#,yaw#,0
;RotateEntity rt\branchent[bn],treecampitch#,treecamyaw#,treecamroll#
     Else
       RotateEntity rt\brancHent[bn],0,0,0  
        EndIf
        Next 

    EndIf;animation
Else
      If rt\distance#>treedisplayrange*.03  HideEntity RT\MAINENT
  EndIf;view range
Next

End Function


Function Droptree(dtree,DTX#,DTZ#,isgrass=0)
 settreepickmode 0
 If LinePick (DTX,20000,DTZ,0,-40000,0 ) Then
 PositionEntity dtree,dtx,PickedY()-.2,dtz
 Settreepickmode 2
 Return 1
 Else 
 Return -1
 EndIf
End Function

Function SetTreePickmode(pmode)
 For dtp.tree=Each tree
  EntityPickMode dtp\mainent,pmode
   For Pickm=1 To dtp\maxbranches
    EntityPickMode dTp\Branchent[pickm],pmode
    Next
   Next
End Function

Function CenterMesh (entity) 
FitMesh entity, -(MeshWidth (entity) / 2), -(MeshHeight (entity) / 2), -(MeshDepth (entity) / 2), MeshWidth (entity), MeshHeight (entity), MeshDepth (entity) 
End Function

Function StandMesh (entity) 
FitMesh entity, -(MeshWidth (entity) / 2), (MeshHeight (entity) ), -(MeshDepth (entity) / 2), MeshWidth (entity), -MeshHeight(ENTITY), MeshDepth (entity) 
End Function

Function HangMesh (entity) 
FitMesh entity, -(MeshWidth (entity) / 2), 0, -(MeshDepth (entity) / 2), MeshWidth (entity), -MeshHeight (entity), MeshDepth (entity) 
End Function




Function distance(entity1,entity2)
Return Sqr#((EntityX#(entity1,1) - EntityX#(entity2,1))^2 + (EntityZ#(entity1,1) - EntityZ#(entity2,1))^2)
End Function

Function Deletetree(dtree)
For rt.tree=Each tree
 If rt\mainent=dtree Then
  FreeEntity rt\mainent
  Delete rt
  Exit
  EndIf
Next
End Function

Function UnloadTrees(deltree=True)
 For Rt.Tree=Each tree
  If deltree=True Then  FreeEntity RT\MainEnt
   Delete rt
   Next

For RtG.RCGRASS=Each rcgrass
  If deltree=True Then FreeEntity RTg\ent
  Delete rtg
  Next

For gt.GrassTextures=Each grasstextures
;  FreeBrush gt\Brush
;  FreeTexture gt\tex
  DebugLog "freed a grasstexture"
  Delete gt
Next

End Function



Function Tree_SetSeason(sn)
	TREE_OLDSEASON=sn
	TREE_SEASON=sn
	For RT.TREE=Each TREE
 
 If rt\evergreen=False Then 
	rr=Rand(-20,20)
	rg=Rand(-20,20)
	rb=Rand(-20,20)
		 For BN=1 To RT\MAXBRANCHES
			;color leaves to season if season doesnt match
 		     For BranchVert=0 To CountVertices(RT\Branchsurf[bn])-1
				VertexColor rt\branchsurf[bn],branchvert,season_red(tree_season)+rr+Rand(-10,10),season_green(tree_season)+rg+Rand(-10,10),season_blue(tree_season)+rb+Rand(-10,10)
			  Next
				Next
			    EndIf
				Next 
For rcg.rcgrass=Each rcgrass
 If rcg\evergreen<>1 Then 
	rr=Rand(-20,20)
	rg=Rand(-20,20)
	rb=Rand(-20,20)
    colorgrass rcg\ent,season_red(tree_season)+rr+Rand(-10,10),season_green(tree_season)+rg+Rand(-10,10),season_blue(tree_season)+rb+Rand(-10,10)
 EndIf
Next

End Function

Function CountTrees()
count=0
	For rt.tree=Each tree
		count=count+1
		Next
	Return count
End Function


Function fps()
oldtime=fpstime
fpstime=MilliSecs()
elapsed=fpstime-oldtime
If Not elapsed elapsed=1
time=Int(MilliSecs()/1000)
If time>lastupdate
	lastupdate=time
	oldfps=1000/elapsed
	EndIf
Return oldfps	
End Function

Function LoadGrass(Tfile$,evergreen,convertent,swimgst)
If FileType(tfile$)<>1 And convertent=0 Then Return -1
;RC integration portion
TreeDisplayrange=fogfarnow*1.3
Treeanimationrange=fogfarnow*.8
RTG.RCGRASS=New RCGRASS
If CONVERTENT<>0 Then 
 RTG\ENT=convertent
Else
 RTG\ENT=LoadMesh(tfile$)
EndIf
RTG\EVERGREEN=evergreen
RTG\Surface=GetSurface(RTG\ENT,1)
RTG\Brush=GetSurfaceBrush(RTG\surface)
RTG\TEX=GetBrushTexture(rtg\brush)
RTG\TexNAME$=TextureName (rtg\tex)
Return rtg\ent
End Function



Function tree_setvalues()
weather_wind_swaymax#(W_SUN,1)=3
weather_wind_swayspeed#(W_SUN,1)=0.05
weather_wind_swaymax#(W_SUN,2)=3
weather_wind_swayspeed#(W_SUN,2)=0.05
weather_wind_swaymax#(W_SUN,3)=3
weather_wind_swayspeed#(W_SUN,3)=0.05

weather_wind_swaymax#(W_RAIN,1)=3
weather_wind_swayspeed#(W_RAIN,1)=.1
weather_wind_swaymax#(W_RAIN,2)=4
weather_wind_swayspeed#(W_RAIN,2)=.05
weather_wind_swaymax#(W_RAIN,3)=2
weather_wind_swayspeed#(W_RAIN,3)=.14

weather_wind_swaymax#(W_SNOW,1)=3
weather_wind_swayspeed#(W_SNOW,1)=0.07
weather_wind_swaymax#(W_SNOW,2)=2
weather_wind_swayspeed#(W_SNOW,2)=0.07
weather_wind_swaymax#(W_SNOW,3)=2
weather_wind_swayspeed#(W_SNOW,3)=0.07

weather_wind_swaymax#(W_FOG,1)=4
weather_wind_swayspeed#(W_FOG,1)=0.05
weather_wind_swaymax#(W_FOG,2)=4
weather_wind_swayspeed#(W_FOG,2)=0.05
weather_wind_swaymax#(W_FOG,3)=3
weather_wind_swayspeed#(W_FOG,3)=0.05


weather_wind_swaymax#(W_STORM,1)=5
weather_wind_swayspeed#(W_STORM,1)=.3
weather_wind_swaymax#(W_STORM,2)=4
weather_wind_swayspeed#(W_STORM,2)=.3
weather_wind_swaymax#(W_STORM,3)=7
weather_wind_swayspeed#(W_STORM,3)=.4


weather_wind_swaymax#(W_WIND,1)=6
weather_wind_swayspeed#(W_WIND,1)=.2
weather_wind_swaymax#(W_WIND,2)=6
weather_wind_swayspeed#(W_WIND,2)=.1
weather_wind_swaymax#(W_WIND,3)=7
weather_wind_swayspeed#(W_WIND,3)=.2

;load season settings here
If FileType(seasoncolor_file$)<>1 Then 
;create the file, and input defualts

 cfile=WriteFile(seasoncolor_file$)
For i=0 To 11
  scr=(40+Rand(-40,40))
  scg=(150+Rand(-40,10))
  scb=(30+Rand(-30,50))
  WriteInt cfile,scr
  WriteInt cfile,scg
  WriteInt cfile,scb
  season_red(i)=scr
  season_green(i)=scg
  season_blue(i)=scb
 Next
CloseFile cfile
Else
 cfile=ReadFile(seasoncolor_file$)
  For i=0 To 11
   scr=ReadInt(cfile)
   scg=ReadInt(cfile)
   scb=ReadInt(cfile)
  season_red(i)=scr
  season_green(i)=scg
  season_blue(i)=scb
 Next
CloseFile cfile
EndIf
End Function

Function Tree_Changeweather(sid)
TreeSwayMax_X#=weather_wind_swaymax#(sid,1)
TreeSwayMax_Y#=weather_wind_swaymax#(sid,2)
TreeSwayMax_Z#=weather_wind_swaymax#(sid,3)
SwayPower_X#=weather_wind_swayspeed#(sid,1)
SwayPower_Y#=weather_wind_swayspeed#(sid,2)
SwayPower_Z#=weather_wind_swayspeed#(sid,3)
End Function

Function clumpgrass(NotusedAnymore=0)
; go through all grass. and copy each unique texture
; to the grasstexture list
For rcg.rcgrass=Each rcgrass
  aok=1
  For gt.grasstextures=Each grasstextures
    If gt\texname$=rcg\texname$ Then aok=0
    Next
   If aok=1 Then 
    gt.grasstextures=New grasstextures
    gt\texname=rcg\texname
    s=GetSurface(rcg\ent,1)
    gt\brush=GetSurfaceBrush(s)
    gt\tex=GetBrushTexture(gt\brush)
    ;BrushFX gt\brush,1
    BrushTexture gt\brush,gt\tex
    FreeBrush rcg\brush
    FreeTexture rcg\tex
   EndIf 
 Next
;go through the texture list and paint
;each grass item with the brush of the same
;texturname.  reducing brush reference to min.

For gt.grasstextures=Each grasstextures
DebugLog "brush created"
 For rcg.rcgrass=Each rcgrass
    If rcg\texname=gt\texname Then PaintMesh rcg\ent,gt\brush
    EntityPickMode rcg\ent,0
    EntityAutoFade rcg\ent,GrassFadeNear#,GrassFadefar#
    UpdateNormals rcg\ent
    ;EntityFX rcg\ent,1
    Next
Next
;make sure they get seasoned
lightmapgrass()

End Function


Function xForm(entity,sxp#=1,syp#=1,szp#=1)
	Vx# = GetMatElement(Entity, 0, 0)
	Vy# = GetMatElement(Entity, 0, 1)
	Vz# = GetMatElement(Entity, 0, 2)	

	sx#=Sqr(Vx#*Vx# + Vy#*Vy# + Vz#*Vz#)
	sy#=Sqr(Vy#*Vy# + Vz#*Vz# + Vx#*Vx#)
	sz#=Sqr(Vz#*Vz# + Vx#*Vx# + Vy#*Vy#)
	
	px#=EntityX(entity) * sxp
	py#=EntityY(entity) * syp
	pz#=EntityZ(entity) * szp

	For i=1 To CountChildren(entity)
		child=GetChild(entity,i)
		xForm(child,sx,sy,sz)
	Next

	ScaleEntity entity,1,1,1
	ScaleMesh entity,sx,sy,sz
	PositionEntity entity,px,py,pz
End Function


Function Tree_Hideall(treesorgrass)
For rt.tree=Each tree
  HideEntity rt\mainent
   For i=1 To rt\maxbranches
     HideEntity rt\branchent[i]
      Next
Next
End Function


Function Tree_Showall(treesorgrass)
For rt.tree=Each tree
   ShowEntity rt\mainent
   For i=1 To rt\maxbranches
     ShowEntity rt\branchent[i]
      Next
      Next
End Function

Function  tree_autofade(near#,far#,GRASSFLAG=1)
If grassflag<>1 Then TREEAUTOFADE=True
For rt.tree=Each tree
    For bn=1 To rt\maxbranches
        EntityAutoFade rt\branchent[bn],near#,far#
        ;EntityFX rt\branchent[bn],1
        Next
        EntityAutoFade rt\mainent,near#,far#
Next
End Function

Function ColorGrass(Ent,amb_r=0,amb_b=0,amb_g=0)
 LinePick EntityX(ent,1),EntityY(ent,1)+10,EntityZ(ent,1),0,-100,0 
 S=PickedSurface()
 t=PickedTriangle()
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

If amb_r=0 Then
 vr4=(vr1+vr2+vr3)*.32
Else
 vr4=(amb_r+vr1+vr2+vr3)*.25
EndIf

If amgr=0 Then
 vg4=(vg1+vg2+vg3)*.32
Else
 vg4=(amb_g+vg1+vg2+vg3)*.25
EndIf

If amb_b=0 Then
 vb4=(vb1+vb2+vb3)*.32
Else
 vb4=(amb_b+vb1+vb2+vb3)*.25
EndIf

 EntityColor ent,vr4+Rand(-10,10),vg4+Rand(-10,10),vb4+Rand(-10,10)
 EndIf

End Function 

Function TransTex(texture,angle#,scale#=1)
	;ScaleTexture texture,scale,scale
	RotateTexture texture,angle#
	x#=Cos(angle)/scale/2
	y#=Sin(angle)/scale/2
	PositionTexture texture,(x-.5)-y-y,(y-.51)+x
End Function

Function updategrass(Gdelta#=1.0,Grasswind#=1.0)
GrassPos=GrassPos+((GrassDir*gdelta)*grasswind)
If GrassPos>=3600 Then
 GrassPos=0
 EndIf
thisval#=(lCos(grasspos)*.015)*360
For Gt.Grasstextures=Each GrassTextures
 transTex gt\tex,thisval
 Next
End Function 

Function Lightmapgrass()
  For rcg.rcgrass=Each rcgrass
    ;EntityFX rcg\ent,1
    If rcg\evergreen<>1 Then
    colorgrass rcg\ent,season_red(tree_season)+rr+Rand(-10,10),season_green(tree_season)+rg+Rand(-10,10),season_blue(tree_season)+rb+Rand(-10,10)
    Else
    colorgrass rcg\ent,0,0,0
    EndIf
  Next
End Function