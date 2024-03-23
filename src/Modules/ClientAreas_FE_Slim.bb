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

Function LODShadows(S, ShadowTexture)
    ;;LOD RAMOIDA
	;			S\SceneryType$ = "" ;initiate the variable just in case Ramoida
	;			If Instr(Name$, "RCTE\") Or  Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\CITYPATHS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFSCAFFOLD\") Or  Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\ICE CAVES\") Or  Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\CAVE\") Or Instr(Name$, "AAAAAAAM\OLD_MINE\OLD_MINE\")
	;				S\SceneryType$ = "T" ;If it is Terrain, mark it as "T" ;This will be used at Client.bb
	;			;;BUILDINGS
	;		    ElseIf Instr(Name$, "BUILDINGS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\ALKERZ\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\DONE\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\DRAGON GATE\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\MIDDEL EAST\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\NEWBUILDINGS2\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\RUINED_BRIDGE\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\TREE BASE\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\TROPICAL\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\WAGONS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\WARTORN\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\CASTLE2\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\FARM\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\MARKET STALLS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\NEW BUILDINGS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\RESPAWN POINT\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\SNOW PILES\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\DOCKS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\FIRT06\")  Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\FOUNTAIN\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\STATUES\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\VILLAGE\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\ALKERZARK CENTER CHURCH\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\ANCIENT_RUINS03\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\ARTERIA3D_TROPICALPACK\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\ELVENCITY_2010_UPDATE_GENERIC\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\OLD PORTAL\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\RUINED_BRIDGE\") Or Instr(Name$, "AAAAAAAM\CATAPULTS-SRC\") Or Instr(Name$, "AAAAAAAM\STONE BRIDGE\") Or Instr(Name$, "AAAAAAAM\WAGONS\") Or Instr(Name$, "AAAAAAAM\WINDMILL\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\NICE RS\") Or Instr(Name$, "RCTREES\") Or Instr(Name$, "AAAAAAAM\SPIKES\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\")
	;				S\SceneryType$ = "B" 
	;				EntityAutoFade S\EN,BuildingMinViewDistance#,BuildingMaxViewDistance#;If it is an object set autofade
	;			;GRASS
	;			ElseIf Instr(Name$, "GRASS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\FOREST GRASSS\")  Or Instr(Name$, "AAAAAAAM\CRATES3D\CRATES\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\BARRELS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\HANGING ANIMALS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\BANNERS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\LAMP POST\") Or Instr(Name$, "ACTORS\HORSE AND CHART\") Or Instr(Name$, "ACTORS\NPC'S\ALKERZARK SOLDIERS\")
	;				S\SceneryType$ = "G"
	;				EntityAutoFade S\EN, GrassMinViewDistance#, GrassMaxViewDistance#
	;			;;RUNIC
	;			ElseIf Instr(Name$, "EFFECTS\RUNICPATH\")
	;				S\SceneryType$ = "Z"
	;				EntityAutoFade S\EN, RunicMinViewDistance#, RunicMaxViewDistance#
	;			EndIf
	;	
	;			;Shadows file trackers				
	;			;Recievers
	;			If Instr(Name$, "RCTE\") Or Instr(Name$, "Decles\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\ALKERZ\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\DRAGON GATE\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\TRACK 1.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\TRACK 2.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\TRACK END.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\TRACK SLIGHT TURN.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\TRACK STRAIGHT END.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\TRACK STRAIGHT.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\TRACK TIGHT TURN.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\NEWBUILDINGS2\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\RUINED_BRIDGE\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\TREE BASE\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\TROPICAL\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\WAGONS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\FARM\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\MARKET STALLS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\NEW BUILDINGS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\RESPAWN POINT\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\VIKINGPACK2\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\CITYPATHS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\DOCKS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\FOUNTAIN\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\STATUES\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\VILLAGE\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\ALKERZARK CENTER CHURCH\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\ANCIENT_RUINS03\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\ARTERIA3D_TROPICALPACK\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\ELVENCITY_2010_UPDATE_GENERIC\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\OLD PORTAL\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\RUINED_BRIDGE\")  Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\NICE RS\")  Or Instr(Name$, "AAAAAAAM\CATAPULTS-SRC\") Or Instr(Name$, "AAAAAAAM\PEASANT_HOUSE\") Or Instr(Name$, "AAAAAAAM\STONE BRIDGE\") Or Instr(Name$, "AAAAAAAM\WAGONS\") Or Instr(Name$, "AAAAAAAM\WINDMILL\") Or Instr(Name$, "AAAFROMDEMO\") Or Instr(Name$, "AAAM") Or Instr(Name$, "ATREEMAGIK\") Or Instr(Name$, "B3D ACTORS\") Or Instr(Name$, "BRIDGES\") Or Instr(Name$, "ITEMS\") Or Instr(Name$, "MYMODLES\") Or Instr(Name$, "MYWEAPONS\") Or Instr(Name$, "RCTREES\") Or Instr(Name$, "SHADRE\") Or Instr(Name$, "TREEMAGIK\") Or Instr(Name$, "AAAAAAAM\SPIKES\") Or Instr(Name$, "CHARACTER SET\")
	;			 	;Any models in the rcte folder becomes a receiver
	;				EntityTexture S\EN, ShadowTexture, 0, 2
	;				;AttachShadowReceiver% (S\EN) ; removes incorrect shadows (this was disabled as it was massively impacting on performance of around 30 fps)
	;			EndIf
	;			;Casters
	;			If Instr(Name$, "AAAAAAAAAAAAMMMMMM2\ALKERZ\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\BONES DINO\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\DONE\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\CLIFF SUPPORT.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\CRANE.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\MINE CART.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\PROP SET1.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\PROP SET2.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\SHAFT SUPPORT.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\STATUE.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\STONE CARVING.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\FROZENREEFMINES\TRACK STOPPER.B3D") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\DRAGO RUINS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\DRAGON GATE\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\MIDDEL EAST\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\NEWBUILDINGS2\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\RUINED_BRIDGE\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\TROPICAL\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\WAGONS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\WARTORN\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\CASTLE2\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\LAMP POST\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\MARKET STALLS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\NEW BUILDINGS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\RESPAWN POINT\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\VIKINGPACK2\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\DOCKS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\FIRT06\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\FOUNTAIN\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\STATUES\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\VILLAGE\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\ALKERZARK CENTER CHURCH\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\ANCIENT_RUINS03\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\ARTERIA3D_TROPICALPACK\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\ELVENCITY_2010_UPDATE_GENERIC\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\ROOTS\") Or Instr(Name$, "AAAAAAAAAAAAMMMMMM2\AMMMMM222\AMMMMM1\AMMMM\RUINED_BRIDGE\") Or Instr(Name$, "AAAAAAAM\CATAPULTS-SRC\") Or Instr(Name$, "AAAAAAAM\CRATES3D\") Or Instr(Name$, "AAAAAAAM\LOGS\") Or Instr(Name$, "AAAAAAAM\PEASANT_HOUSE\") Or Instr(Name$, "AAAAAAAM\STONE BRIDGE\") Or Instr(Name$, "AAAAAAAM\WAGONS") Or Instr(Name$, "AAAAAAAM\WINDMILL") Or Instr(Name$, "AAAM\") Or Instr(Name$, "AFROM RANDOM\") Or Instr(Name$, "ANIMAL PACKS\") Or Instr(Name$, "ATREEMAGIK\") Or Instr(Name$, "B3D ACTORS\") Or Instr(Name$, "BRIDGES\") Or Instr(Name$, "DUNGEON PACK 1\") Or Instr(Name$, "ITEMS\") Or Instr(Name$, "MY MODLES\") Or Instr(Name$, "MYWEAPONS\") Or Instr(Name$, "RCTREES\")
	;				CreateShadowCaster% (S\EN)
	;			EndIf
				
    ; NewLOD cysis145 [011]
    S\SceneryType$ = "" 
    If DisplayItems = False
        ;Always Renders
        If S\RenderRange = 0
            S\SceneryType$ = "T"
        ;Short Range
        ElseIf S\RenderRange = 1
            S\SceneryType$ = "G"
            EntityAutoFade S\EN, GrassMinViewDistance#, GrassMaxViewDistance#
        ;Long Range
        ElseIf S\RenderRange =2
            S\SceneryType$ = "B" 
            EntityAutoFade S\EN, BuildingMinViewDistance#, BuildingMaxViewDistance#
        EndIf
    EndIf


	; Scenery Shadows cysis145 [010]
	If S\ReceiveShadow And DisplayItems = False 
		EntityTexture S\EN, ShadowTexture, 0, 2 
  	    AttachShadowReceiver S\EN ;helps remove incorrect shadowing 
	EndIf

	If S\CastShadow And DisplayItems = False 
		CreateShadowCaster S\EN 
	EndIf 
End Function