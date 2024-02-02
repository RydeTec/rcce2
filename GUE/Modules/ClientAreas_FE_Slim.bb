;LOD Ramoida (Set view distances here)
Const BuildingMinViewDistance# = 230.0 ; If distance < this value will be Visible, else will autofade slowly
Const BuildingMaxViewDistance# = 250.0 ; If distance > this value will be 100% invisible
Const GrassMinViewDistance# = 75.0
Const GrassMaxViewDistance# = 90.0
Const RunicMinViewDistance# = 5.0
Const RunicMaxViewDistance# = 10.0

Const ParticleMaxViewDistance# = 10.0

;&&&&&&&&&&&&&&& Refractive water terrier
Function RenderWater(Camera)
      For W.Water = Each Water

        ; CameraFogMode Camera,0   
         ;HideEntity CameraSprite
            
        ; ShowClipplane W\WaterClipplane => 
			AlignClipplane W\WaterClipplane, W\EN

			;EntityAlpha W\EN,0.25 => sinon impose un alpha au plan d'eau
			EntityTexture W\EN, FoamTexture
      Next
       
      SetBuffer TextureBuffer(RefractTexture)
     ; CameraViewport Camera, 0,0, TextureWidth(RefractTexture), TextureHeight(RefractTexture)
	 ; CameraViewport Camera, 0,0, 1, 1
	
	;CameraViewport Camera, 0, 0, TextureWidth(RefractTexture) , TextureHeight(RefractTexture)
  ;  ScaleEntity Camera,1,Float(GraphicsHeight())/Float(GraphicsWidth()),1  
;	
	;ShowEntity(Camera)    
      RenderWorld 
	
	HideEntity(Camera)
	


		; render world in backBuffer (set refractions texture to water plane)
     
      For W.Water = Each Water
      		;	If CameraUnderWater = True 
			;	;ShowEntity CameraSprite
			;	CameraFogMode Camera,1
			;	CameraFogRange Camera,0.5,35
			;	CameraFogColor Camera,20,20,40
			;Else
				;HideEntity CameraSprite
				;CameraFogMode Camera,1
				;CameraFogRange Camera,20,250
				;CameraFogColor Camera,190,200,220
			;EndIf
      
         
         HideClipplane W\WaterClipplane
         
			EntityTexture W\EN, BumpTexture, (BumpTextureFrame Mod 32), 0
			EntityTexture W\EN, RefractTexture, 0, 1
			;EntityAlpha W\EN,1 => sinon impose un alpha au plan d'eau
			EntityColor W\EN,25,30,35 ; => renforce le r�alisme mais bleu par d�faut
	
			SetBuffer BackBuffer()
			
			
			ShowEntity(Camera)
		;	CameraViewport Camera, 0,0, GraphicsWidth(), GraphicsHeight()
		;	ScaleEntity Camera,1,1,1		; ����������� ��������� ������
			;RenderWorld  ;=> fait gagner 10 FPS de +
            Next  
End Function

Function LoadShadowOptions()
    ;Adding shadows to options menu Cysis145
	F = ReadFile("Data\Options.dat")
		Width = ReadShort(F)
		Height = ReadShort(F)
		Depth = ReadByte(F)
		AA = ReadByte(F)
		DefaultVolume# = ReadFloat#(F)
		GrassEnabled = ReadByte(F)
		AnisotropyLevel = ReadByte(F)
		FullScreen = ReadByte(F)
		VSync = ReadByte(F)
		Bloom = ReadByte(F)
		Rays = ReadByte(F)
		AWater = ReadByte(F)
		ShadowC = ReadByte(F)
		ShadowQ = ReadByte(F)
		ShadowR = ReadByte(F)
	CloseFile(F)
	Select ShadowQ
		Case 0
			CreateShadow 0
		Case 1
			CreateShadow 1
		Case 2
			CreateShadow 2
	End Select
	
	Select ShadowR
		Case 0
			ShadowRange 50
		Case 1
			ShadowRange 75
		Case 2
			ShadowRange 100
		Case 3
			ShadowRange 130
	End Select
	
	;Season = GetSeason()
	; Dusk
	;If TimeH = SeasonDuskH(Season)
	;	ShadowPower 0.0  ;Set Shadow Opacity
	; Dawn
	;ElseIf TimeH = SeasonDawnH(Season)
	;	ShadowPower 0.0  ;Set Shadow Opacity
	;EndIf

	ShadowPower 0.4  ;Set Shadow Opacity
	ShadowColor 255, 255, 255
	;ShadowLight DefaultLight\EN
	ShadowTexture = ShadowTexture() ; Gets shadow map
	
	;Shadow Fading
	FadeOutTexture = LoadTexture("Data\Textures\Shadows\fade.png", 59)	
	ShadowFade FadeOutTexture
End Function

Function CameraRefractiveWater(Camera)
    ;&&&&& Camera refractive water terrier
	CameraSprite = CreateSprite(Camera)
	PositionEntity CameraSprite,0,0,0.11
	EntityColor CameraSprite,20,20,40
    EntityAlpha CameraSprite,0.35
End Function

Function WaterTexture(W)
    ;&&& Water edit terrier 
    ;BumpTexA = LoadAnimTexture("Data\Textures\Water\water_anim.jpg", 9, 64, 64, 0, 32) ;I'm using the FastExt demo bump. If you are using your own, you may need to change the parameters. 
    ;TextureBlend BumpTexA, FE_BUMPLUM 
    ; bump
    
    BumpTexture = LoadAnimTexture ( "Data\Textures\Water\water_anim.jpg", 9, 64, 64, 0, 32 )
    TextureBlend BumpTexture, FE_BUMP
    ;ScaleTexture BumpTexture,0.012,0.012	
                                ; <<<< 	����� ����� ��� ����� 
    FoamTexture = LoadTexture ( "Data\Textures\Water\foam.png", 1+2 )
    TextureBlend FoamTexture, 1
    ScaleTexture FoamTexture,30,30
        
    ScaleTexture BumpTexture,W\TexScale#, W\TexScale#
    ;ScaleTexture FoamTexture,W\TexScale#, W\TexScale#

        W\WaterClipplane = CreateClipplane (  WaterPlane  )               
    ;ClipPivotUp = CreatePivot()  :  RotateEntity ClipPivotUp,0,0,180   :   PositionEntity ClipPivotUp, 0, 0.05, 0
        
    AlignClipplane W\WaterClipplane, W\EN
End Function

Function FreeSceneryShadow(S)
    ;Shadow
    FreeShadowCaster% (S\EN)
    ;FreeShadowReceiver% (S\EN)
End Function

Function FreeShadows(ShadowTexture)
    ;Shadow
	FreeShadows
	FreeTexture ShadowTexture
End Function