; Globals
Global Radar_Update_Time# = 600
Global Radar_Timer# = MilliSecs()
Global Radar_BorderColor_Red = 120
Global Radar_BorderColor_Green = 120
Global Radar_BorderColor_Blue = 120
Global Radar_BorderWidth = 8
Global Radar_LastX#, Radar_LastY#
Global RadarPath$ = "\Data\Areas\Radar\"
Global Radar_Ent1, Radar_Tex1, Radar_BorderEnt
Global Radar_Ent2, Radar_Tex2, Radar_BorderTex
Global Radar_PlayerEnt, Radar_PlayerTex
Global Radar_FColor = 42 ; Fog grey color
Global Radar_WorldSize#
Global Radar_TexSize = 512
Global Radar_Width#, Radar_Height#
Global Radar_ThisPath$ = CurrentDir$()
If FileType(Radar_ThisPath$ + RadarPath$) <> 2 Then CreateDir(Radar_ThisPath$ + RadarPath$)

; Load radar data for a zone
Function Load_Radar(AreaName$, X#, Y#, Width#, Height#, Interior, BorderTex$ = " ", PlayerTex$ = " ")

	HideEntity GY_Cam
	HideEntity Cam
	Unload_Radar()
	Radar_Width# = Width#
	Radar_Height# = Height#

	; Remove path
	For i2 = Len(AreaName$) To 1 Step -1
		If Mid$(AreaName$, i2, 1) = "\"
			AreaName$ = Right$(AreaName$, Len(AreaName$) - i2)
			Exit
		EndIf
	Next

	; Remove extension
	count = 0
	For i2 = Len(areaname$) To 1 Step -1
		count = count + 1
		If Mid$(areaname$, i2, 1) = "." Then AreaName$ = Left$(AreaName$, Len(AreaName$) - count)
	Next

	; Create zone image
	Radar_Camera = CreateCamera()
	CameraRange Radar_Camera, 1, 100000000
	PositionEntity Radar_Camera, 0, 0, 0
	North_Piv = CreatePivot()
	South_Piv = CreatePivot()
	East_Piv = CreatePivot()
	West_Piv = CreatePivot()
	TranslateEntity North_Piv, 1, 5000, 1
	TranslateEntity South_Piv, 1, 5000, 1
	TranslateEntity East_Piv, 1, 5000, 1
	TranslateEntity West_Piv, 1, 5000, 1
	SetPickModes()
	; Raise camera until the entire map is in view
	While AllClear = False
		UpdateWorld()
		NP = LinePick(EntityX(North_Piv, 1), EntityY(North_Piv, 1), EntityZ(North_Piv, 1), 0, -15000, 0)
		SP = LinePick(EntityX(South_Piv, 1), EntityY(South_Piv, 1), EntityZ(South_Piv, 1), 0, -15000, 0)
		EP = LinePick(EntityX(East_Piv, 1), EntityY(East_Piv, 1), EntityZ(East_Piv, 1), 0, -15000, 0)
		WP = LinePick(EntityX(West_Piv, 1), EntityY(West_Piv, 1), EntityZ(West_Piv, 1), 0, -15000, 0)
		If NP = 0 And SP = 0 And EP = 0 And WP = 0 Then AllClear = True
		TranslateEntity Radar_Camera, 0, 10, 0
		TranslateEntity North_Piv, 0, 0, 10
		TranslateEntity South_Piv, 0, 0, -10
		TranslateEntity East_Piv, 10, 0, 0
		TranslateEntity West_Piv, -10, 0, 0
	Wend

	; At correct height, get world size (by recording camera height)
    Piv = CreatePivot()
    PointEntity Radar_Camera, Piv
    FreeEntity Piv
    TurnEntity Radar_Camera, 0, 0, -180
	Radar_WorldSize# = EntityY#(Radar_Camera, 1) * 1.7

	; Make scenery fullbright
	For Sc.Scenery = Each Scenery
		EntityFX Sc\EN, 1 + 8
	Next

	UpdateWorld()
	RenderWorld()
	TempImage = CreateImage(GraphicsWidth(), GraphicsHeight())
  	CopyRect 0, 0, GraphicsWidth(), GraphicsHeight(), 0, 0, BackBuffer(), ImageBuffer(TempImage)
 	ResizeImage TempImage, Radar_TexSize, Radar_TexSize
	Radar_Tex1 = CreateTexture(Radar_TexSize, Radar_TexSize, 1)
	CopyRect 0, 0, Radar_TexSize, Radar_TexSize, 0, 0, ImageBuffer(TempImage), TextureBuffer(Radar_Tex1)

	; Restore scenery FX
	For Sc.Scenery = Each Scenery
		EntityFX Sc\EN, 0
	Next

	FreeEntity North_Piv
	FreeEntity South_Piv
	FreeEntity East_Piv
	FreeEntity West_Piv
	FreeEntity Radar_Camera

	; Create new fog texture
	If FileType (radar_thispath$+radarpath$+areaname$+".rdr") <> 1
		Radar_Tex2 = Create_Radar_Fog(Interior)
	; Load existing fog texture if available
	Else
		Radar_Tex2 = Load_Radar_Fog(Radar_ThisPath$ + RadarPath$ + AreaName$ + ".rdr")
		If Radar_Tex2 = -1 Then Radar_Tex2 = Create_Radar_Fog(Interior)
	EndIf

	; Create quad meshes and apply textures
	Radar_Ent1 = Radar_CreateQuadr(GY_Cam)
	Radar_Ent2 = Radar_CreateQuadr(GY_Cam)
	Radar_BorderEnt = Radar_CreateQuadr(GY_Cam)
	Radar_PlayerEnt = Radar_CreateQuadr(GY_Cam)
	EntityOrder Radar_Ent1,      -3005
	EntityOrder Radar_Ent2,      -3007
	EntityOrder Radar_BorderEnt, -3008
	EntityOrder Radar_PlayerEnt, -3009

	EntityTexture Radar_Ent1, Radar_Tex1
	EntityTexture Radar_Ent2, Radar_Tex2

	; Load border texture if available, otherwise create default one
	If BorderTex$ <> " " And FileType("Data\Textures\" + BorderTex$) = 1
		Radar_BorderTex = LoadTexture("Data\Textures\" + BorderTex$, 1 + 4)
	Else
		Radar_CreateBorderTex()
	EndIf

	; Load player texture if available, otherwise create default one
	If PlayerTex$ <> " " And FileType("Data\Textures\" + PlayerTex$) = 1
		Radar_PlayerTex = LoadTexture("Data\Textures\" + PlayerTex$, 1 + 4)
	Else
		Radar_CreatePlayerTex()
	EndIf

	EntityTexture Radar_BorderEnt, Radar_BorderTex
	EntityTexture Radar_PlayerEnt, Radar_PlayerTex
	EntityParent Radar_Ent2, Radar_Ent1
	EntityParent Radar_BorderEnt, Radar_Ent1
	EntityParent Radar_PlayerEnt, Radar_Ent1

	; Place and scale it
	ShowEntity Cam
	ShowEntity GY_Cam
	ScaleEntity Radar_Ent1, Width# * 20.0, Height# * 15.0, 1.0
	PositionEntity Radar_Ent1, (X# * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0

End Function

; Creates the default fog texture
Function Create_Radar_Fog(Interior)

	RFTex = CreateTexture(Radar_TexSize, Radar_TexSize, 1 + 2 + 4)
	If Interior = True
		SetBuffer TextureBuffer(RFTex)
		LockBuffer TextureBuffer(RFTex)
		For fx = 0 To Radar_TexSize - 1
			For fy = 0 To Radar_TexSize - 1
				c = Rand(Radar_FColor - 20, Radar_FColor + 10)
				WritePixelFast fx, fy, 255 Shl 24 Or c Shl 16 Or c Shl 8 Or c Shl 0
			Next
		Next
		UnlockBuffer TextureBuffer(RFTex)
		SetBuffer BackBuffer()
	EndIf
	Return RFTex

End Function

; Loads a radar fog texture from file
Function Load_Radar_Fog(R_FN$)

	RFTex = Create_Radar_Fog(False)
	SetBuffer TextureBuffer(RFTex)
	LockBuffer TextureBuffer(RFTex)
	RF_Load = ReadFile(R_FN$)
	For fx = 0 To Radar_TexSize - 1
		For fy = 0 To Radar_TexSize - 1
			WritePixelFast fx, fy, ReadInt(RF_Load)
		Next
	Next
	UnlockBuffer TextureBuffer(RFTex)
	SetBuffer BackBuffer()
	CloseFile RF_Load
	Return RFTex

End Function

Function Save_Radar_Fog(areaname$)
;remove path
  For i2= Len(areaname$) To 1 Step -1
   If Mid$(areaname$,i2,1)="\" Then
    areaname$=Right$(areaname$,Len(areaname$)-i2)
    Exit
   EndIf
  Next

;remove extension
	count=0
	  For i2= Len(areaname$) To 1 Step -1
	count=count+1
	   If Mid$(areaname$,i2,1)="." Then
	    areaname$=Left$(areaname$,Len(areaname$)-count)
	    EndIf 
	Next
	SetBuffer TextureBuffer(radar_tex2)
	LockBuffer TextureBuffer(radar_tex2)
	 sf_radar=WriteFile(radar_thispath$+radarpath$+areaname$+".rdr")
	  For fx=0 To Radar_TexSize-1
	   For fy=0 To Radar_TexSize-1
	     CC=ReadPixelFast(fx,fy)
	       WriteInt sf_radar, CC
	    Next
	    Next
	UnlockBuffer TextureBuffer(radar_tex2)
   SetBuffer BackBuffer()
End Function


Function Position_Radar(X#,Y#,width#,height#)
	 PositionEntity Radar_ent1, (X# * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0
	 ScaleEntity Radar_ent1, Width# * 20.0, Height# * 15.0, 1.0
     radar_width#=width#
     radar_height#=height#
End Function

Function Show_Radar()
	 ShowEntity radar_Ent1
End Function
			
Function Hide_Radar()
	 HideEntity Radar_ent1
End Function
				
Function Reset_Radar()
    SetBuffer TextureBuffer(radar_tex2)
	LockBuffer TextureBuffer(radar_Tex2)
	   For fx=0 To Radar_TexSize-1
	   For fy=0 To Radar_TexSize-1
	c=Rand(radar_fcolor-20,radar_fcolor+10)
 	             WritePixelFast fx,fy,255 Shl 24 Or c Shl 16 Or c Shl 8 Or c Shl 0
	        Next
	         Next
  	 UnlockBuffer TextureBuffer(radar_Tex2)
     SetBuffer BackBuffer()  
End Function
					
Function Clear_Radar()
    SetBuffer TextureBuffer(radar_tex2)
	LockBuffer TextureBuffer(radar_Tex2)
	   For fx=0 To Radar_TexSize-1
	   For fy=0 To Radar_TexSize-1
 	             WritePixelFast fx,fy,0 Shl 24 Or 0 Shl 16 Or 0 Shl 8 Or 0 Shl 0
	        Next
	         Next
  	 UnlockBuffer TextureBuffer(radar_Tex2)
     SetBuffer BackBuffer()  
End Function

						
Function Unload_Radar()
	If Radar_tex1<>0 Then 
	    FreeTexture Radar_tex1 
	    radar_Tex1 = 0
        EndIf
	If Radar_tex2<>0 Then 
        FreeTexture Radar_tex2  
        radar_Tex2 = 0
        EndIf
    If radar_bordertex<>0 Then 
 		FreeTexture radar_bordertex
		radar_bordertex=0
        EndIf
    If radar_playertex<>0 Then 
		FreeTexture radar_playertex
		radar_playertex=0
        EndIf
	If Radar_ent2<>0 Then 
		FreeEntity Radar_ent2 
		radar_Ent2 = 0
        EndIf
    If radar_borderent<>0 Then 
 		FreeEntity radar_borderent
        radar_borderent=0
        EndIf   
    If radar_playerent<>0 Then 
        FreeEntity radar_playerent
        radar_playerent=0
        EndIf 
 	If Radar_ent1<>0 Then 
		FreeEntity Radar_ent1 
	    radar_Ent1 = 0
        EndIf 
End Function

						
Function UpdateRadar(ent,areashow=50)
;make sure the entity has moved.. otherwise do not update radar
If radar_lastx#=EntityX(ent,1) And radar_lasty#=EntityZ(ent,1) Then Return
radar_lastx#=EntityX(ent,1):radar_lasty#=EntityZ(ent,1)

	plrx# = (Radar_TexSize / (Radar_WorldSize / -EntityX#(ent, 1)))
	plry# = (Radar_TexSize / (Radar_WorldSize / EntityZ#(ent, 1)))

fromfx=plrx#+(Radar_TexSize/2)-areashow
tofx=plrx#+(Radar_TexSize/2)+areashow
fromfy=plry#+(Radar_TexSize/2)-areashow
tofy=plry#+(Radar_TexSize/2)+areashow
xmid=plrx#+(Radar_TexSize/2)
yMid=plry#+(Radar_TexSize/2)

;make sure certain time has elapsed.. otherwise do not update radar fog
If MilliSecs() - radar_timer# > radar_update_time#
	radar_timer#=MilliSecs()
	;update!
	SetBuffer TextureBuffer(radar_tex2)
	LockBuffer TextureBuffer(radar_tex2)
	c=0
	maxdist# = areashow * areashow
	For fx = fromfx To tofx
		For fy= fromfy To tofy
			If fx>0 And fx<radar_texsize And fy>0 And fy<radar_Texsize
				distance# = (xmid-fx)*(xmid-fx) + (ymid-fy)*(ymid-fy)
				If distance# < maxdist# Then WritePixelFast fx,fy, 1 Shl 24 Or c Shl 16 Or c Shl 8 Or c Shl 0
			EndIf 
		Next
	Next

	UnlockBuffer TextureBuffer(radar_tex2)
    SetBuffer BackBuffer()
EndIf

	; Position player marker
 	PercX# = (((Float#(FromFX) + (Float#(AreaShow / 2))) / Float#(Radar_TexSize)) * 100.0) * 0.01
	PercY# = (((Float#(FromFY) + (Float#(AreaShow / 2))) / Float#(Radar_TexSize)) * 100.0) * 0.01
    PositionEntity Radar_PlayerEnt, PercX# - 0.45, 0.45 - PercY#, 0.0

End Function

Function radar_createbordertex()
;genreric border. you can load an image with the bordertex$ field in LOAD_RADAR
radar_bordertex=CreateTexture(radar_texsize,radar_texsize,1+4)
SetBuffer  TextureBuffer(radar_bordertex)
LockBuffer (TextureBuffer(radar_bordertex))
   For fx=0 To TextureWidth(radar_bordertex)-1
    For fy=0 To TextureHeight(radar_bordertex)-1
	     If (fx<radar_borderwidth Or fx>(TextureWidth(radar_bordertex)-(radar_borderwidth+1))) Or (fy<radar_borderwidth Or fy>(TextureHeight(radar_bordertex)-(1+radar_borderwidth)) ) 
 	             WritePixelFast fx,fy,255 Shl 24 Or radar_bordercolor_red Shl 16 Or radar_bordercolor_green Shl 8 Or radar_bordercolor_blue Shl 0
                 border=1
                 Else
	             WritePixelFast fx,fy,0 Shl 24 Or 0 Shl 16 Or 0 Shl 8 Or 0 Shl 0
                 EndIf
 	         Next
	         Next
UnlockBuffer TextureBuffer(radar_bordertex)
SetBuffer BackBuffer()
End Function

Function radar_createplayertex()
;genreric border. you can load an image with the playertex field in LOAD_RADAR
radar_playertex=CreateTexture(radar_texsize,radar_texsize,1+4)
SetBuffer  TextureBuffer(radar_playertex)
LockBuffer (TextureBuffer(radar_playertex))
   For fx=0 To radar_texsize-1
    For fy=0 To radar_texsize-1
               If fx>(radar_texsize/2)-10 And fx<(radar_texsize/2)+10 And fy>(radar_texsize/2)-10 And fy<(radar_texsize/2)+10 Then
                        cc=cc+1
 	             WritePixelFast fx,fy,255 Shl 24 Or radar_bordercolor_red Shl 16 Or radar_bordercolor_green Shl 8 Or radar_bordercolor_blue Shl 0
                   Else
  	             WritePixelFast fx,fy,0 Shl 24 Or 0 Shl 16 Or 0 Shl 8 Or 0 Shl 0
                  EndIf
 	         Next
	         Next

UnlockBuffer TextureBuffer(radar_playertex)
SetBuffer BackBuffer()
End Function



Function Radar_CreateQuadr(P = 0)
;Copy of GY_CREATEQUAD.  keeping module independent
	EN = CreateMesh()
	s = CreateSurface(EN)
	v1 = AddVertex(s, 0.0, -1.0, 0.0, 0.0, 1.0)
	v2 = AddVertex(s, 1.0, -1.0, 0.0, 1.0, 1.0)
	v3 = AddVertex(s, 1.0,  0.0, 0.0, 1.0, 0.0)
	v4 = AddVertex(s, 0.0,  0.0, 0.0, 0.0, 0.0)
	AddTriangle s, v3, v2, v1
	AddTriangle s, v4, v3, v1
	EntityParent(EN, P)
	EntityFX(EN, 1 + 8)
	Return EN

End Function