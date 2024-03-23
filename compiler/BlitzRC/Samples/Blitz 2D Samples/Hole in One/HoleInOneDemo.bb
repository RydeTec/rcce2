;**************************************
;*      HOLE IN ONE DEMO V 0.98A      *
;*     WRITTEN BY DAVE KIRK (GFK)     *
;**************************************

AppTitle "Hole In One"

Global VER$ = "V0.98a"
Global Screenwidth = 640
Global Screenheight = 480
Global Menuwidth = 800
Global MenuHeight = 600
Global Screendepth = 16

Global HolePath$ = "Courses\"
Global GraphicPath$ = "Graphics\"
Global SoundPath$ = "Sounds\"

Global ZoneLib%,NumZones				; Variables for ZoneLib (front-end)

Global GameType							; 1 Practice, 2 Tournament, 3 Skins
Global NumPlayers						; Number of human players (1 or 2)
Global AutoClub							; Auto club select 0 or 1
Global GameRes							; 1 640x480, 0 800x600
Global CourseNo							; Selected course number

Global Hole								; number of hole being played

Global MenuBKG							; Menu background image

Global DemoOver							; Demo Over Image
Global GameClock 						; Synchronise display
Global FPS								; Max frame rate
Global Tile								; Map tiles
Global TileMask							; RGB mask for map tiles
Global SmallTile						; Smaller map tiles (for hole overview)
Global Golfer							; Golfer sprites
Global Pin								; Pin sprites
Global PinX,PinY						; Pin position
Global PinImage							; Animated pin frame number
Global Pointer							; Mouse pointer sprites
Global Ball								; Ball sprites
Global MousePtr							; Selected mouse pointer image
Global Pnum								; Number of player taking shot
Global MapWidth,MapHeight,MapX,MapY		; Map size and offset to draw from
Global Xoff,Yoff						; offset from whole tile position
Global Club								; Club overlay sprites
Global ShowInfo							; Should screen info be displayed or not?
Global ShowClub							; Should club choice be displayed?
Global ShowCourse						; Should course name/hole be diaplyed?
Global Caddy							; Caddy sprites
Global CaddyFrame						; Animated Caddy frame number
Global CaddyDelay						; Clock to slow down caddy animation
Global Bird								; Bird sprites
Global BirdX,BirdY,BirdFrame			; Bird position and frame
Global BirdXSpeed,BirdYSpeed			; BirdX/Y axis movement
Global BirdDelay						; Clock to slow down bird animation
Global PowerMeter						; Power Meter Sprite
Global Indicator						; Power Indicator Sprite
Global ShowPowerMeter					; Should power meter be displayed when NOT taking shot?
Global PW								; Power Indicator position
Global Power							; Power % (0 - 100)
Global HookSlice#						; Amount of hook/slice on ball
Global PlayerImage						; First image of golfer
Global Hstep#,Vstep#,BallHeight#		; Ball distance & height/step
Global LastShot							; TEMPORARY - DISTANCE OF LAST SHOT
Global Splash							; Splash sprites
Global Splashframe						; Splash frame counter
Global SplashCount						; Delay for splash animation
Global Angle#							; Angle of ball movement
Global BallMoveCount					; Allow ball to move from trees without collision detection
Global Marker							; Marker if player behind tree
Global MarkerFrame						; Marker frame number
Global MarkerDelay						; Slow down marker animation
Global BallInHole						; Frame of ball falling into hole (2-5)
Global Bag								; Image of buttons for club choice
Global BagShown							; True or False
Global BagOffset						; Position of bag as it slides onto screen
Global LiePic							; Ball lie sprites
Global FlagPtr							; Flag pointer sprite (if flag is off screen)
Global FlagPtrOffset					; 0 or 1 - 1 = show pointer
Global FlagPtrDelay						; Delay flag pointer animation
Global HoleMap							; Hole Map sprite
Global HoleMapSmall						; Hole Map Sprite (50% size of main map)
Global FlashCount						; Number of frames FlashMessage has been on screen for
Global SkinPot							; Amount of money in pot (skins game)
Global GameOver							; Is game finished? (true or false)
Global Theme							; Theme number (1 to 4)
Global StoreName$						; Holds Player 2 name is 1PvsCPU game
Global GreenViews						; 1 = view greens if ball is close, 0 if not.
Global Progress							; Game progress (0, 1, 2, 3, 4)
Global LastGameType						; Last chosen game type before starting game


Global MGameType,MPNum,MHole,MCourseNo,MTheme,MSkinPot,MAutoClub,MNumPlayers,MCPUSkill
Dim MPname$(2),MScore(2)					; Show saved game data in front-end

Dim MarkerX(5)
Dim MarkerY(5)
Global CPUMarker						; Marker which CPU player is aiming for
Global CPUSkill							; Skill of CPU player (0 - Amateur, 1 - Pro)

Global FPLAYER$
Global FTILE$							; -----\
Global FBALL$							;       \
Global FBIRD$							;        +--- Theme filenames
Global FSPLASH$							;        |
Global FLIEPIC$							;       /
Global FTILEMASK$						; -----/

Global FSBIRD1$
Global FSTREE$
Global FSGLASS$
Global FSSPLASH$
Global FSINCBIRD$
Global FSINCSHUTTER$
Global FSINCSHEEP$

Global SMusic							; Front end music
Global AMusic							; Music (ChannelID)
Global SSwing							; Swing sound
Global SPutter							; Putter sound
Global SBird1							; Bird sound
Global ABird1							; Bird sound (Channel ID)
Global SHole							; Ball in hole sound
Global SBounce							; Bounce sound
Global STree							; Tree branch sound
Global TreePlaying						; Is treesound playing??
Global SSplash							; Splash sound
Global SGlass							; Glass break sound
Global SIncShutter						; Shutter sound (incidental)
Global SIncSheep						; Sheep sound (incidental)
Global SIncBird							; Bird sound (Incidental)
Global SClap1							; Small applause
Global SClap2							; Normal applause
Global SClap3							; Big applause


Global SmallFont						; ---\
Global MidFont							;    --- Fonts (Arial in varying sizes)
Global LargeFont						; ---/


Dim GolfBag$(13)
Dim GolfYards%(13)
Dim Stroke%(2)							; Number of stroke about to be taken
Dim PName$(2)							; Player names
Dim PlayerX(2),PlayerY(2)				; Player position
Dim BallX#(2),BallY#(2)					; Ball position
Dim ClubChoice(2)						; Selected club (0-13)
Dim Score(2)							; Player Score(Pnum)
Dim Course$(99)
Dim HolePar(18)


;SET UP GAME
Theme = 1
GameType = 1
NumPlayers = 1
AutoClub = 1
Greenviews=1
GameRes = 1
CourseNo = 1

PName(1) = "Player 1"
PName(2) = "Player 2"
Pnum = 1
ShowInfo = 1
ShowClub = 1
ShowCourse = 1
ShowPowerMeter = 1
PlayerImage=0
Graphics menuwidth,menuheight,screendepth

.frontend
Delay 500
EndGraphics
Graphics Menuwidth,Menuheight,screendepth

Dim map(1,1)
MapX=0 : MapY=0
FPS=30

If CPUSkill = 1
	CPUSkill = 0
	GameType = 5
EndIf
For N = 1 To 2
	ClubChoice(N) = 0
	stroke(N) = 1
	Score(N) = 0
Next

;MENU STUFF!!!

SetBuffer BackBuffer()

LoadFonts
StartMusic
LoadMenuGraphics
ReadCourses
ReadCFG

If LastGameType <>0 Then GameType = LastGameType

ZLReserveZone 20

Repeat
	t = MilliSecs()
	DrawMenu(1)
	HighlightButton
	DrawMenuMouse

		MenuToolTips
	If MouseDown(1)
		CheckButtons
		If ZLMouseZone() = 10 Then Goto Game
	EndIf
	While MilliSecs()-t < 1000/50
	Wend
	Flip
Until False

ShowGameOver
EndGraphics
End

.game

StartGame





;MAIN LOOP HERE!

SetBuffer BackBuffer()

If autoclub = 1 Then AutoChooseClub
FlashCount = 0
Repeat
	GameClock=MilliSecs()
	If Pnum = 2 And gametype = 4 Then HitBall
	CentreScreen BallX(Pnum),BallY(Pnum)
	If GetLie(BallX(Pnum),BallY(Pnum))=6 Then ClubChoice(Pnum) = 13 : BagShown = False
	DrawBall
	DrawCaddy
	DrawPlayer(True)
	DrawPin
	DrawBird
	ScreenInfo
	DrawBallLie
	DrawGolfBag
	DrawMouse
	CheckKeys
	CheckMouse
	IncidentalSound
	While MilliSecs()-GameClock<(1000/FPS)
	Wend
	Flip
	If KeyDown(1)
		If YNRequest$("Really quit? (Y/N)") = "Y"
			EndGraphics
			End
		EndIf
	EndIf
Until False

	flashcount = 46
	CentreScreen BallX(Pnum),BallY(Pnum)
	DrawBall
	DrawCaddy
	DrawPlayer(False)
	DrawPin
	DrawBird
	Flip
	SetBuffer FrontBuffer()
	TileImage Tile,0,0,224
Function YNRequest$(TXT$)
	While KeyDown(1)
	Wend
	flashcount = 46
	CentreScreen BallX(Pnum),BallY(Pnum)
	DrawBall
	DrawCaddy
	DrawPlayer(False)
	DrawPin
	DrawBird
	Flip
	SetBuffer FrontBuffer()
	TileImage Tile,0,0,224
	SetFont MidFont
	ShadowText screenwidth/2,screenheight/2,TXT,True,True
	While KeyDown(21) = 0 Or KeyDown(49)=0
		If KeyDown(21) Then T$ = "Y" : Exit
		If KeyDown(49) Then T$ = "N" : Exit
	Wend
	FlushKeys
	Return T$
End Function


Function StartMusic()
	DecodeFile "Mus.bin",SoundPath
	SMusic = LoadSound(Soundpath + "temp.bin")
	DeleteFile soundpath + "temp.bin"

	LoopSound SMusic
	AMusic = PlaySound(SMusic)
End Function

Function ReadCourses()
	N = 1
	Path = ReadDir(holepath)
	a$=NextFile(path)
	While A$<>""
		If FileType(holepath+a$)=2
			If Len(a$)>5
				Course(n) = a$
				n=n+1 : If n>99 Then n = 99
			EndIf
		EndIf
		a$=NextFile(Path)
	Wend
	CloseDir Path
End Function

Function StartGame()
	GameOver = False
	StopChannel AMusic
	LastGameType = GameType
	If GameType = 5
		GameType = 4
		CPUSkill = 1
	Else
		CPUSkill = 0
	EndIf
	If GameType = 4 Then NumPlayers = 2 : StoreName = Pname(2) : Pname(2) = "CPU"
	PNum = 1
	Hole = 1
	SkinPot = 3000
	ZLFreeZones
	FreeSound SMusic
	FreeImage MenuBKG
	FreeImage Pointer
	Delay 500

	GetThemeFilenames
	EndGraphics
	Graphics screenwidth,screenheight,16
	LoadFonts
	DisplayLoader
	LoadTiles
	LoadGraphics
	LoadSounds
	DefineClubs
	ReadHolePar
	LoadHole
	FindHole
	FindPlayerStart
	GenerateOverView
	LoopSound SBird1
	ABird1 = PlaySound(SBird1)
End Function

Function UpdateScore()
	If gametype = 1 Or gametype = 2 Or gametype = 4
		Score(1) = Score(1) + stroke(1)-HolePar(Hole)
		Score(2) = Score(2) + stroke(2)-HolePar(Hole)
	EndIf
	If gametype = 3
		If stroke(1)<stroke(2) Then score(1) = score(1) + SkinPot :skinpot = 0
		If stroke(2)<stroke(1) Then score(2) = score(2) + SkinPot : skinpot = 0
		If stroke(1)=stroke(2) And Hole = 18
			Score(1) = Score(1)+ Int(SkinPot/2)
			Score(2) = Score(2)+ Int(SkinPot/2)
		EndIf
	EndIf
End Function

Function NextHole()
	GameOver = False
	FlashCount = 0
	UpdateScore
	DisplayScoreboard
	Hole = Hole + 1
	If hole < 7 Then SkinPot = SkinPot + 3000
	If hole>6 And hole<13 Then SkinPot = SkinPot + 6000
	If hole>12 And hole < 19 Then skinpot = skinpot + 9000
	BallInHole = 0
	ClubChoice(1) = 0
	ClubChoice(2) = 0
	If Hole = 19 Then GameOver = True : StopChannel ABird1
	If Hole <19
		stroke(1) = 1
		stroke(2) = 1
		LoadHole
		FindHole
		FindPlayerStart
		GenerateOverView
		If AutoClub = 1 Then AutoChooseClub
	EndIf
End Function
Function HighlightButton()
	Color 255,255,0
	Select ZLMouseZone()
		Case 3
			Rect 280,390,240,24,False
		Case 6
			Rect 280,420,240,24,False
		Case 10
			Rect 280,500,240,24,False
		Case 11
			Rect 280,550,240,24,False
		Case 14
			Rect 280,450,240,24,False
		
		End Select
End Function

Function MenuToolTips()
	Select ZLMouseZone()
		Case 1
			ToolTip MouseX(),MouseY()+16,"Game type - NOT AVAILABLE IN DEMO"
		Case 2
			ToolTip MouseX(),MouseY()+16,"Number of human players - NOT AVAILABLE IN DEMO"
		Case 3
			ToolTip MouseX(),MouseY()+16,"Toggle automatic club selection"
		Case 4
			ToolTip MouseX(),MouseY()+16,"Course selection - NOT AVAILABLE IN DEMO"
		Case 5
		
			If Progress < 4
				ToolTip MouseX(),MouseY()+16,"Course theming.  NOT AVAILABLE IN DEMO"
			Else
				ToolTip MouseX(),MouseY()+16,"Course theming - NOT AVAILABLE IN DEMO"
			EndIf				
		Case 6
			ToolTip MouseX(),MouseY()+16,"Game screen resolution"
		Case 10
			ToolTip MouseX(),MouseY()+16,"Start game with chosen settings"
		Case 11
			ToolTip MouseX(),MouseY()+16,"Leave the program and return to Windows"
		Case 12
			ToolTip MouseX(),MouseY()+16,"Name of player 1 - NOT AVAILABLE IN DEMO"
		Case 13
			ToolTip MouseX(),MouseY()+16,"Name of player 2 - NOT AVAILABLE IN DEMO"
		Case 14
			ToolTip MouseX(),MouseY()+16,"Fix camera to green when ball in range"
	End Select
End Function

Function DrawMenu(Menu)
	DrawBlock MenuBkg,0,0
	For n = 1 To 20
		ZLKillZone n
	Next
	If Menu = 1
		SetFont Smallfont
		ShadowText 775,110,Ver$,True,False
		If GameType = 4 Or gametype = 5 Then NumPlayers = 1
		If GameType = 3 Then NumPlayers = 2
		SetFont midfont
		Select GameType
			Case 1
				drawbutton 1,400,170,"Practice Game"
		End Select

		
		If gametype <> 2
			Select NumPlayers
				Case 1
					drawbutton 2,400,200,"1 Player"
					drawbutton 12,400,250,pname(1)
					ZLKillZone 13
	
			End Select
			Select AutoClub
				Case 1
					drawbutton 3,400,390,"Auto Club Select On"
				Case 0
					drawbutton 3,400,390,"Auto Club Select Off"	
			End Select
			drawbutton 4,400,330,Course$(CourseNo)
			Select Theme
				Case 1
					drawbutton 5,400,360,"Classic Golf"
			End Select
		EndIf
		Select GreenViews
			Case 1
				drawbutton 14,400,450,"Green Cameras On"
			Case 0
				drawbutton 14,400,450,"Green Cameras Off"
		End Select

		Select GameRes
			Case 0
				DrawButton 6,400,420,"800 x 600 Resolution"
			Case 1
				DrawButton 6,400,420,"640 x 480 Resolution"
		End Select
		DrawButton 10,400,500,"Start Game"
		DrawButton 11,400,550,"Exit to Windows"
	EndIf
End Function

Function CheckButtons()
	While MouseDown(1)
	Wend
	N = ZLMouseZone()
	
	If N = 3
		AutoClub = 1-AutoClub
	EndIf
	If N = 6
		GameRes = 1 - GameRes
		If GameRes = 0 : Screenwidth = 800 : Screenheight = 600 : EndIf
		If GameRes = 1 : Screenwidth = 640 : Screenheight = 480 : EndIf
	EndIf
	If N = 11
		StopChannel AMusic
		ShowGameOver
		EndGraphics
		End
	EndIf
	
	If N = 14
		Greenviews = 1 - Greenviews
	EndIf
	WriteCFG
End Function


Function DrawButton(zone,x,y,TXT$)
	Color 0,0,255
	Rect x-120,y,240,24,True
	Color 255,255,255
	Line x-120,y,x+120,y
	Line x-120,y,x-120,y+24
	Color 0,0,0
	Line x+120,y+24,x+120,y
	Line x+120,y+24,x-120,y+24
	Text x+1,y+13,txt,True,True
	Color 255,255,0
	Text x,y+12,txt,True,True
	ZLSetZone zone,x-120,y,x+120,y+24
End Function

Function MenuHeading(TXT$)
	SetFont LargeFont
	Color 0,0,0
	x = 780 - StringWidth(txt)
	Text x+2,82,txt,False,True
	Color 255,255,0
	Text x,80,txt,False,True
End Function

Function DrawMenuMouse()
	DrawImage Pointer,MouseX(),MouseY(),0
End Function

Function LoadMenuGraphics()
	DecodeFile "Pointer.bin",graphicpath
	Pointer=LoadAnimImage(graphicpath + "temp.bin",16,16,0,11)
	MaskImage pointer,255,0,0

	DecodeFile "menubkg.bin",graphicpath
	MenuBkg = LoadImage(graphicpath + "temp.bin")

	DeleteFile graphicpath + "temp.Bin"
End Function

Function DrawFlagPtr()
	XP = 0 : YP = 0 : FlagFrame = 0
	X = LocalX(PinX)
	Y = LocalY(PinY)
	If X < 0 Or Y < 0 Or X > screenwidth Or Y > Screenheight
		If X < 0
			XP = 12
			YP = Y
			If YP < 12 Then YP = 12
			If YP > Screenheight - 12 Then YP = screenheight-12
		EndIf
		If X > Screenwidth
			XP = Screenwidth - 12
			YP = Y
			If YP < 12 Then YP = 12
			If YP > Screenheight - 12 Then YP = screenheight-12
		EndIf
		If Y < 0
			XP = X
			YP = 12
			If XP < 12 Then XP = 12
			If XP > Screenwidth-12 Then XP = screenwidth-12
		EndIf
		If Y > Screenheight - 12
			XP = X
			YP = Screenheight - 12
			If XP < 12 Then XP = 12
			If XP > Screenwidth-12 Then XP = screenwidth-12
		EndIf
		
		If YP = Screenheight-12 And XP<>12 And XP <>screenwidth-12 Then FlagFrame = 0
		If YP = Screenheight-12 And XP = Screenwidth-12 Then FlagFrame = 1
		If XP = Screenwidth-12 And YP<>12 And YP <> screenheight-12 Then FlagFrame = 2
		If XP = screenwidth-12 And YP = 12 Then FlagFrame = 3
		If YP = 12 And XP <> 12 And XP <> screenwidth-12 Then FlagFrame = 4
		If XP = 12 And YP = 12 Then FlagFrame = 5
		If XP = 12 And YP <>12 And YP <> Screenheight-12 Then FlagFrame = 6
		If xp = 12 And YP = Screenheight-12 Then FlagFrame = 7
				
		DrawImage FlagPtr,xp,yp,FlagFrame
		
	EndIf
End Function

Function GreenContour()
	If gametype = 4 And pnum = 2 Then Goto Skip
	X = Int(BallX(Pnum)/16)
	Y = Int(BallY(Pnum)/16)
	If X<0 Or x>mapwidth Or Y<0 Or y>mapheight Then Goto skip
	T = Map(X,Y)
	AngleStep# = 0.5
	A = Int(Angle)
	If T = 216				; DOWN
		If A>180 And A<360
			Angle = Angle + AngleStep
		EndIf
		If A<180 And A>0
			Angle = Angle - AngleStep
		EndIf	
	EndIf
	
	If T = 217				; DOWN-RIGHT
		If A>45 And A<225
			Angle = Angle - AngleStep
		EndIf
		If A>225 Or A<45
			Angle = Angle + AngleStep
		EndIf
	EndIf
	
	If T = 218				; RIGHT
		If A>90 And A<270
			Angle = Angle - AngleStep
		EndIf
		If A>270 Or A<90
			Angle = Angle + AngleStep
		EndIf
	EndIf
		
	If T = 219				; UP-RIGHT
		If A>135 And A<315
			Angle=Angle - AngleStep
		EndIf
		If A>315 Or A<135
			Angle=Angle + AngleStep
		EndIf
	EndIf
	
	If T = 220				; UP
		If A>180 And A<360
			Angle = Angle - AngleStep
		EndIf
		If A<180 Or A<0
			Angle = Angle + AngleStep
		EndIf
	EndIf
	
	If T = 221				; UP-LEFT
		If A>225 Or A<45
			Angle = Angle - AngleStep
		EndIf
		If A>45 And A<225
			Angle = Angle + AngleStep
		EndIf
	EndIf
	
	If T = 222				; LEFT
		If A>270 Or A<90
			Angle = Angle - AngleStep
		EndIf
		If A>90 And A<270
			Angle = Angle + AngleStep
		EndIf
	EndIf
	
	If T = 223				; DOWN-LEFT
		If A>315 Or A<135
			Angle = Angle - AngleStep
		EndIf
		If A>135 And A<315
			Angle = Angle + AngleStep
		EndIf
	EndIf
.skip
End Function


Function ToolTip(X,Y,Txt$)
	SetFont SmallFont
	W = StringWidth(Txt$)+ 8
	H = StringHeight(Txt$)+ 4
	If X < 0 Then X = 0
	If Y < 0 Then Y = 0
	If X + W > MenuWidth Then X = Menuwidth - W
	If Y + H > MenuHeight Then Y = MenuHeight - H
	Color 255,255,255
	Rect x,y,w,h,True
	Color 0,0,0
	Rect x,y,w,h,False	
	Text x+(w/2),y+(h/2),Txt,True,True
End Function

Function ReadCFG()
End Function

Function WriteCFG()
End Function

Function FlashMessage(Txt$)
	If NumPlayers = 2
		FlashCount = FlashCount + 1
		If FlashCount >45 Then FlashCount = 45
		If FlashCount <45 And FlashCount > 0
			SetFont MidFont
			Color 0,0,0
			Text screenwidth/2,(screenheight/3)-(FlashCount/2),Txt$,True,True
			Color 255,255,0
			Text (screenwidth/2)-1,(screenheight/3)-1-(FlashCount/2),Txt$,True,True
		EndIf
	EndIf
End Function

Function DisplayLoader()
	SetBuffer FrontBuffer()
	Cls
	Color 0,128,0
	SetFont largefont
	Text screenwidth/2,screenheight/2,"Loading...",True,True
	SetBuffer BackBuffer()
End Function

Function DrawGolfBag()
	If BagShown = True
			DrawBlock Bag,40,screenheight+BagOffset
			If BagOffset = -25
				X = 16 : Y = screenheight-25-ImageHeight(Bag)
				If MouseX()>X And MouseX()<X+ImageWidth(Bag)
					If MouseY()>Y And MouseY()<Y+ImageHeight(Bag)
						Color 255,255,0
						Rect X,Y+1+(24*((MouseY()-y)/24)),48,24,False
					EndIf
				EndIf
			EndIf
			BagOffset = BagOffset - 48 : If BagOffset < -25 Then BagOffset = -25
	EndIf
End Function

Function AutoChooseClub()
	ClubChoice(Pnum) = 0
	D = YardsToHole(PlayerX(Pnum),PlayerY(Pnum),PinX,PinY)
	For N = 0 To 12
		If D<GolfYards(N) Then ClubChoice(Pnum) = N	
	Next
End Function

Function ClubSelect()
	X = 16 : Y = screenheight-25-ImageHeight(Bag)
	If MouseX()>X And MouseX()<X+ImageWidth(Bag)
		If MouseY()>Y And MouseY()<Y+ImageHeight(Bag)
			ClubChoice(Pnum) = (MouseY()-y)/24
			BagShown = False
			While MouseDown(1)
			Wend
		EndIf
	EndIf
End Function

Function CheckMouse()
	If MouseDown(1) And BagShown = True Then ClubSelect
	If MouseDown(1) And mouseptr = 9 Then HitBall
	C = RectsOverlap(MouseX(),MouseY(),1,1,6,screenheight-58,ImageWidth(club),ImageHeight(club))
	If MouseDown(2) And c=0 Then BagShown = False : scrollmap
	If MouseDown(2) And c <>0 Then BagShown = True : BagOffset = 340
End Function

Function ClubRatio#(Clubno)
	Select Clubno
		Case 0 : Ratio#=1.00	; Driver
		Case 1 : Ratio#=0.95	; 3 wood
		Case 2 : Ratio#=0.90	; 5 wood
		Case 3 : Ratio#=0.84	; 2 iron
		Case 4 : Ratio#=0.81	; 3 iron
		Case 5 : Ratio#=0.74	; 4 iron
		Case 6 : Ratio#=0.67	; 5 iron
		Case 7 : Ratio#=0.59	; 6 iron
		Case 8 : Ratio#=0.49	; 7 iron
		Case 9 : Ratio#=0.43	; 8 iron
		Case 10: Ratio#=0.39	; 9 iron
		Case 11: Ratio#=0.31	; PW
		Case 12: Ratio#=0.16	; SW
		Case 13: Ratio#=0.20	; Putter
	End Select
	Return Ratio#
End Function
Function HitBall()
	mouseptr = 0
	Angle = GetAngle(LocalX(PlayerX(Pnum)),LocalY(PlayerY(Pnum)),MouseX(),MouseY())
	PW = 0
	Power = 0
	HookSlice = 0
	Splashframe=-1
	BallMoveCount = 0
	Hstep = 0
	Vstep = 0
	BallHeight = 0
	BallInHole = 0
	TreePlaying = 0
	While MouseDown(1)
	Wend
.SetPower
	StepSize = 2
	While MouseDown(1) = 0
		GameClock = MilliSecs()
		PW = PW + StepSize
		If PW = 102 Then PW = 100 : StepSize = -2
		If PW = 0 Then PW = 2 : StepSize = 2
		CentreScreen BallX(Pnum),BallY(Pnum)
		DrawBall
		DrawCaddy
		DrawPlayer(False)
		DrawPin
		DrawBird
		ScreenInfo
		DrawBallLie
		While MilliSecs()-GameClock<(1000/FPS)
		Wend
		Flip		
		If MouseDown(1)
			Power = PW
			If ClubChoice(Pnum)<>13
				Goto SetHS
			Else
				Goto GotStats
			EndIf
		EndIf
	Wend
	
	
	Goto ShotCancelled
.SetHS
	While MouseDown(1)
	Wend
	While PW>-18
		GameClock = MilliSecs()
		PW = PW - 2
		CentreScreen BallX(Pnum),BallY(Pnum)
		DrawBall
		DrawCaddy
		DrawPlayer(False)
		DrawPin
		DrawBird
		ScreenInfo
		DrawBallLie
		DrawImage indicator,Sin(0-(Power*2.15))*59+(screenwidth-74),Cos(0-(Power*2.15))*59+(screenheight-74)
		While MilliSecs()-GameClock<(1000/FPS)
		Wend
		Flip		
		If MouseDown(1) And PW <14 And PW >-14 Then HookSlice = (PW/10.0)/2 : Goto GotStats
		If MouseDown(1) And PW <-13 Then Goto ShotCancelled	
	Wend
	If PW<-13 Then Goto ShotCancelled
.gotstats
	While MouseDown(1)
	Wend
	If ClubChoice(Pnum) = 13 Then PlaySound SPutter
	MoveBall
	LastShot = GetDistance(PlayerX(Pnum),PlayerY(Pnum),BallX(Pnum),BallY(Pnum))/8
	If GetLie(BallX(Pnum),BallY(Pnum))<>7
		RefreshStats
		MovePlayerToBall
		GetNextPlayer
		If autoclub = 1 Then AutoChooseClub
	Else
		If AllPlayersInHole() = True
			NextHole()
		Else
			RefreshStats
			BL = getlie(BallX(pnum),BallY(pnum))
			MovePlayerToBall
			GetNextPlayer
			If autoclub = 1 Then AutoChooseClub
		EndIf
	EndIf
		
.ShotCancelled
	Power = 0 : PW = 0
	HookSlice = 0
	While MouseDown(1)
	Wend
End Function

Function AllPlayersInHole()
	If NumPlayers = 2
		If GetLie(BallX(1),BallY(1)) = 7 And GetLie(BallX(2),BallY(2)) = 7
			Val = True
		Else
			Val = False	
		EndIf
		If Val = True
			FirstOnNextTee
		EndIf
	Else	
		Val = True
	EndIf
	Return Val
End Function

Function FirstOnNextTee()
	PNum = 1
	If Stroke(2)<Stroke(1) Then PNum = 2
	If Stroke(1)=Stroke(2)
		If Score(2)<Score(1)
			Pnum = 2
		EndIf
	EndIf
End Function

Function GetNextPlayer()
	FlashCount = 0
End Function

Function AdjustShotPower()
	C = GetLie(BallX(Pnum),BallY(Pnum))
	If C = 8 Or C = 9
		Offset = 4
		While C = 8 Or C = 9
			C = GetLie(BallX(Pnum),BallY(Pnum)+offset)
			Offset = Offset + 4
		Wend
	EndIf
	
	If ClubChoice(Pnum) = 0 And stroke(pnum) <> 1
		If c =1 Or c = 2 Or c = 4 Then Hstep = Hstep * 0.4
	EndIf
	
	If ClubChoice(Pnum) = 1 Or ClubChoice(Pnum) = 2
		If c = 1 Or c = 2 Or c = 4 Then hstep = hstep * 0.7
	EndIf
	If ClubChoice(Pnum) > 2 And ClubChoice(Pnum) < 8
	If c = 1 Then hstep = hstep * 0.7
	If c = 2 Then hstep = hstep * 0.9
	If c = 4 Then hstep = hstep * 0.8
	EndIf
	If ClubChoice(Pnum) > 7 And ClubChoice(Pnum) < 10
		If c = 1 Then hstep = hstep * 0.9
		If c = 4 Then hstep = hstep * 0.9
	EndIf
	If ClubChoice(Pnum) = 11
		If c = 4 Then hstep = hstep * 0.8
	EndIf
	If ClubChoice(Pnum) = 13
		If C = 1 Or c = 2 Then hstep = hstep * 0.2
		If c = 3 Then hstep = hstep * 0.9
		If c = 4 Then hstep = hstep * 0.3
	EndIf
End Function

Function RefreshStats()
	BL = GetLie(BallX(Pnum),BallY(Pnum))
	If BL <>7
		stroke(pnum) = stroke(pnum) + 1
	EndIf
	HookSlice = 0
	Power = 0
	PW = 0
	If gametype = 4
		If pnum = 2
			D = YardsToHole(BallX(pnum),BallY(pnum),MarkerX(CPUMarker),MarkerY(CPUMarker))
			G = GetLie(BallX(pnum),BallY(Pnum))
			If D < 30
				If G = 3 Or G = 6
					If MarkerX(CPUMarker+1)<>0
						CPUMarker = CPUMarker + 1
					EndIf
				EndIf
			EndIf
		EndIf
	EndIf
End Function

Function MovePlayerToBall()
	PlayerX(Pnum) = BallX(Pnum)
	PlayerY(Pnum) = BallY(Pnum)
	PlayerImage = 0
End Function

Function Collide()
	BL = GetLie(BallX(Pnum),BallY(Pnum)+BallHeight)
	SL = GetLie(BallX(Pnum),BallY(Pnum))
	If BallHeight = 0
		If BL = 1
			hstep=hstep-0.1
		EndIf
		If BL = 2
			hstep=hstep-0.06
		EndIf
		If BL = 3
			hstep=hstep-0.02
		EndIf
		If BL = 4
			hstep=hstep-0.2
		EndIf
		If BL = 5
			hstep = 0.2
			BallY(Pnum)=BallY(Pnum)+8
		EndIf
		If BL = 7
			If Hstep < 2 And Hstep > .99 Then Angle = Rnd(Angle-20,Angle+20)
			If Hstep > 1.99 And Hstep < 4 Then Vstep = -2
			If Hstep < 1.5 Then BallInHole = 2 : hstep = 0 : vstep = 0 : ballheight = 0
		EndIf
		If BL = 10 And splashframe = -1
			Splashframe = 0
			hstep = 0
			vstep = 0
		EndIf
	EndIf
	
	If BL = 8
		If ballheight>-18 And BallHeight < 0
			Angle = Int(Rnd(0,359))
			If TreePlaying = 0 Then ATree = PlaySound(SBounce) : Treeplaying = 1
		EndIf

	EndIf
	If BL = 9
		If ballheight >-72 And BallHeight < 0
			If GetLie(BallX(Pnum),BallY(Pnum))=8
				hstep=1
				Angle = Int(Rnd(0,359))
				vstep = 1
				If TreePlaying = 0 Then ATree = PlaySound(Stree) : Treeplaying = 1
			EndIf
		EndIf
	EndIf
	End Function

Function GetLie(X,Y)
	XT = Int(X/16)
	YT = Int(Y/16)
	If XT< 0 Or YT< 0 Or XT > mapwidth Or YT > mapheight Then Goto fin
	T = map(XT,YT)
	X = X Mod 16
	Y = Y Mod 16

	SetBuffer ImageBuffer(TileMask,T)
	GetColor x,y
	SetBuffer BackBuffer()
	If T>-1 And T < 21
		If ColorRed() > 128 :  BallLie = 1 : EndIf
		If ColorGreen() > 128 : BallLie = 2 : EndIf
	EndIf
	If T>20 And T < 41
		If ColorRed() > 128 Then BallLie = 2
		If ColorGreen() > 128 Then BallLie = 3
	EndIf
	If T>40 And T < 61
		If ColorRed() > 128 Then BallLie = 3
		If ColorGreen() > 128 Then BallLie = 6
	EndIf
	If T>60 And T < 90
		If ColorRed() > 128 Then BallLie = 1
		If ColorGreen() > 128 Then BallLie = 10
		If ColorBlue() > 128 Then BallLie = 5
	EndIf
	If T>89 And T < 119
		If ColorRed() > 128 Then BallLie = 1
		If ColorGreen() > 128 Then BallLie = 4
		If ColorBlue() > 128 Then BallLie = 5
	EndIf
	If T = 119
		If ColorRed() > 128 Then BallLie = 6
		If ColorGreen() > 128 Then BallLie = 7
	EndIf
	If T = 120
		BallLie = 2
	EndIf
	If T = 121
		BallLie = 3
	EndIf
	If T = 122
		BallLie = 6
	EndIf
	If T = 123
		BallLie = 10
	EndIf
	If T = 124
		BallLie = 4
	EndIf
	If T>124 And T < 155
		If ColorRed() > 128 Then BallLie = 1
		If ColorGreen() > 128 Then BallLie = 8
		If ColorBlue() > 128 Then BallLie = 9
	EndIf
	If T>154 And T < 185
		If ColorRed() > 128 Then BallLie = 2
		If ColorGreen() > 128 Then BallLie = 8
		If ColorBlue() > 128 Then BallLie = 9
	EndIf
	If T>184 And T < 215
		If ColorRed() > 128 Then BallLie = 3
		If ColorGreen() > 128 Then BallLie = 8
		If ColorBlue() > 218 Then BallLie = 9
	EndIf
	If T = 215
		BallLie = 3
	EndIf
	If T>215 And T < 224
		BallLie = 6
	EndIf
	Return BallLie
.fin
End Function

Function DisplayScoreboard()
	GameSaved = True
.RefreshScoreboard
	SetBuffer BackBuffer()
	flashcount = 46
	CentreScreen BallX(Pnum),BallY(Pnum)
	DrawBall
	DrawCaddy
	DrawPin
	DrawBird
	Flip
	SetBuffer FrontBuffer()
	TileImage Tile,0,0,224
	If Hole < 18
		SetFont SmallFont
		If GameSaved = False
			ShadowText screenwidth/2,screenheight-8,"Press F1 to save",True,True
		Else
			ShadowText screenwidth/2,screenheight-8,"***Hole In One Demo***",True,True
		EndIf
	EndIf
	If gametype = 1
		SetFont midfont
		ShadowText screenwidth/2,5,Course(CourseNo),True,False
		ShadowText screenwidth/2,24,"Player scores after hole "+Str$(Hole),True,False
		For n = 1 To numplayers
			ShadowText screenwidth/3,(screenheight/2)+((n-1)*20),Pname(n),False,False
			ShadowText ((screenwidth/3)*2)-StringWidth(stringscore$(score(n))),(screenheight/2)+((n-1)*20),stringscore$(score(n)),False,False
		Next
	EndIf

	If Hole = 5
		ShadowText screenwidth/2,(screenheight*0.75)+30,"End of demo!",True,False
	EndIf
	ShadowText screenwidth/2,screenheight-32,"Click to continue...",True,False
	While MouseDown(1) : Wend
	While MouseDown(1) = 0
	Wend
	If Hole = 5
		ShowGameOver
		EndGraphics
		End
	EndIf
End Function

Function ShowGameOver()
	FlushKeys
	DecodeFile "DemoOver.bin",graphicpath
	DemoOver = LoadImage(graphicpath + "temp.bin")
	DeleteFile graphicpath + "temp.bin"

	SetBuffer FrontBuffer()
	DrawBlock DemoOver,0,0
	WaitKey
End Function

Function ShadowText(x,y,Txt$,CentreX,CentreY)
	Color 0,0,0
	Text x+1,y+1,txt,centrex,centrey
	Color 255,255,0
	Text x,y,txt,centrex,centrey
End Function

Function StringScore$(N)
	If n = 0 Then T$ = "L"
	If n <0 Then T$ = Str$(N)
	If N > 0 Then T$ = "+" + Str$(n)
	Return T$
End Function

Function MoveBall()
	Hstep = ((25/100.0)*Power)*ClubRatio(ClubChoice(Pnum))
	AdjustShotPower
	If ClubChoice(Pnum)= 13
		Vstep = 0
	Else
		Vstep = 0-((12/100.0)*Power)
	EndIf
	StartPlayerImage = PlayerImage
	If ClubChoice(Pnum)<>13
		While PlayerImage < StartPlayerImage + 15
			GameClock = MilliSecs()
			CentreScreen BallX(Pnum),BallY(Pnum)
			DrawBall
			DrawCaddy
			DrawPlayer(False)
			DrawPin
			DrawBird
			ScreenInfo
			If PlayerImage = StartPlayerImage + 12
				PlaySound SSwing
				
			EndIf
			While MilliSecs()-GameClock<(1000/FPS)
			Wend
			Flip
			playerimage=playerimage+1
		Wend
	EndIf
	While Hstep > 0
		BallMoveCount = BallMoveCount + 1
		GameClock = MilliSecs()
		CentreScreen BallX(Pnum),BallY(Pnum)
		DrawBall
		DrawCaddy
		DrawPlayer(False)
		DrawPin
		DrawSplash
		DrawBird
		ScreenInfo
		
		If BallHeight <> 0 Then Vstep = Vstep + 0.3 : Angle = Angle + HookSlice
		BallHeight = BallHeight + Vstep
		If BallHeight > 0
			BallHeight = 0 : HookSlice = 0
			BL = GetLie(BallX(Pnum),BallY(Pnum))
		
			If BL = 10 Then	ASplash = PlaySound(SSplash)
			If BL > 0 And BL < 10 Then ABounce = PlaySound(SBounce)
			If BL = 0 Then AGlass = PlaySound(SGlass): Hstep = 0 : vstep = 0
			
			Hstep = Hstep * 0.5
			Vstep = 0 - (vstep*0.30)
			DiminishBounce(BL)
			If vstep >-1
				vstep = 0 : BallHeight = 0
			EndIf
		EndIf
		If vstep = 0
			If GetLie(BallX(Pnum),BallY(Pnum)) = 6
				Hstep = Hstep - 0.025
			Else
				Hstep = Hstep - 0.03
			EndIf
			GreenContour
		EndIf
		If clubchoice(pnum) <> 13 And BallMoveCount>6 Then Collide()
		If clubchoice(pnum) = 13 Then Collide()
		
		BallX(Pnum) = Sin(Angle)*Hstep + BallX(Pnum)
		BallY(Pnum) = Cos(Angle)*Hstep + BallY(Pnum)
		While MilliSecs()-GameClock<(1000/FPS)
		Wend
		Flip
		If ClubChoice(Pnum) = 13
			If playerimage > startplayerimage + 16 
				playerimage = startplayerimage+16
			EndIf
		Else
			playerimage=playerimage+1
			If playerimage > startplayerimage+21
				playerimage = startplayerimage+21
			EndIf
		EndIf		
	Wend
	BallMoveCount = 0
	SFMSG = Int(Rnd(1,5))
	If BallInHole<>0 Then PlayHoleOutSound
	While BallMoveCount < 60
		BallMoveCount = BallMoveCount + 1
		GameClock = MilliSecs()
		CentreScreen BallX(Pnum),BallY(Pnum)
		DrawBall
		DrawCaddy
		DrawPlayer(False)
		DrawPin
		DrawSplash
		DrawBird
		ScreenInfo
		ShotFinishedMessage SFMSG,BallMoveCount/2
		If BallInHole<>0 Then BallX(Pnum) = PinX : BallY(Pnum) = PinY : BallInHole = BallInHole + 1
		If BallInHole = 4 Then AHole = PlaySound(SHole)
		If BallInHole>5 Then BallInHole = 5
		While MilliSecs()-GameClock<(1000/FPS)
		Wend
		Flip
	Wend
	If BallInHole <>0 Then Goto fin
	BL = GetLie(BallX(Pnum),BallY(Pnum))
	If BL = 10
		BallX(Pnum) = PlayerX(Pnum) : BallY(Pnum) = PlayerY(Pnum)
		stroke(pnum) = stroke(pnum) + 1
	EndIf
	If BallX(Pnum)<0 Or BallY(Pnum)<0 Or BallX(Pnum)>Mapwidth*16 Or BallY(Pnum)>Mapheight*16
		BallX(Pnum) = PlayerX(Pnum) : BallY(Pnum) = PlayerY(Pnum)
		stroke(pnum) = stroke(pnum) + 1
	EndIf
	.fin
	BallHeight = 0
End Function

Function ShotFinishedMessage(N,YOffset)
	BL = GetLie(BallX(Pnum),BallY(Pnum))
	X = BallX(Pnum) : Y = BallY(Pnum)
	While BL = 8 Or BL = 9
		Offset = offset - 4
		BL = GetLie(BallX(Pnum)-offset,BallY(Pnum))
	Wend
	If X<0 Or Y < 0 Or X > mapwidth*16 Or Y > mapheight*16
		Select N
			Case 1
				T$ = "I think that one went into a low orbit..."
			Case 2
				T$ = "That's gone out of bounds."
			Case 3
				T$ = "Ball lost.  That's going to cost you a one-stroke penalty."
		End Select
	EndIf
	If BL = 1		; ROUGH
		Select N
			Case 1
				T$ = "Play for a better lie"
			Case 2
				T$ = "Not good"
		End Select	
	EndIf

	If BL = 2		; S/ROUGH
		Select N
			Case 1
				T$ = "That could have been worse..."
		End Select
	EndIf

	If BL = 3		; FAIRWAY
		Select N
			Case 1
				T$ = "That's a good lie!"
		End Select
	EndIf
	
	If BL = 4		; SAND
		Select N
			Case 1
				T$ = "You'll need a short club to get out of that one!"
			Case 2
				T$ = "Plugged!"
		End Select
	EndIf

	If BL = 6		; GREEN
		Y = YardsToHole(BallX(Pnum),BallY(Pnum),PinX,PinY)
		If Y > 25 Then T$ = "That's left a long putt..."
	EndIf

	If BL = 7		; HOLE
		S = stroke(pnum)-HolePar(Hole)
		If S = -3 Then T$ = "Albatross!  Great shot!"
		If S = -2 Then T$ = "Eagle!  Good shot!"
		If S = -1 Then T$ = "Birdie!  Well done!"
		If S = 0 Then T$ = "Par!  Well played."
		If S = 1 Then T$ = "Bogey.  Unfortunate!"
		If S = 2 Then T$ = "Double-bogey.  Bad luck!"
		If S = 3 Then T$ = "Triple-bogey!  Too bad..."
		If S > 4 And N = 1 Then T$ = "Looks like you're getting all the bad luck today..."
		If Stroke(Pnum) = 1 Then T$ = "Hole in one!  Congratulations!"
	EndIf

	If BL = 10		; WATER
		Select N
			Case 1
				T$ = "Ball lost!"
			Case 2
				T$ = "That'll cost you a one-stroke penalty!"
			Case 3
				T$ = "Ever thought of taking up fishing?"
		End Select
	EndIf

	SetFont MidFont
	Color 0,0,0
	Text screenwidth/2,(screenheight/3)-yoffset,T$,True,True
	Color 255,255,0
	Text (screenwidth/2)-1,(screenheight/3)-1-yoffset,T$,True,True
End Function

Function PlayholeOutSound()
	SC = Stroke(Pnum)-Holepar(Hole)

	If SC < -1 Then PlaySound Sclap3
	If SC = -1 Or SC = 0 Then PlaySound Sclap2
	If SC > 0 And SC < 3 Then PlaySound SClap1

End Function

Function DiminishBounce(BL)
	If BL = 1			; ROUGH
		Hstep = Hstep * 0.75
		Vstep = 0
	EndIf
	If BL = 2			; S/ROUGH
		Hstep = Hstep * 0.5
		Vstep = 0
	EndIf
	If BL = 4			; SAND
		Hstep = hstep * 0.1
		Vstep = 0
	EndIf
End Function

Function ScreenInfo()
	DrawClub
	
	DrawPowerIndicator(PW)

	DrawOverView
	DrawFlagPtr

	SetFont MidFont

	ShadowText 16,5,Pname(Pnum),False,False	
	
	If Hole = 1 Then T$ = Str$(Hole)+"st"
	If Hole = 2 Then T$ = Str$(Hole)+"nd"
	If Hole = 3 Then T$ = Str$(Hole)+"rd"
	If Hole > 3 Then T$ = Str$(Hole)+"th"
	T$ = T$ + " at " + Course(CourseNo) + ", par "+Str$(HolePar(Hole))
	ShadowText screenwidth/2,5,T,True,False
	
	SetFont SmallFont

	T$ = Str$(YardsToHole(PlayerX(Pnum),PlayerY(Pnum),PinX,PinY)) + " yards to hole"
	ShadowText 16,32,T,False,True

	T$ = "Stroke " + Str$(stroke(pnum))
	ShadowText 16,44,T,False,True

	If gametype <>3
		T$ = Score(Pnum)
		If Score(Pnum) = 0 Then T$ = "Level par"
		If score(pnum) > 0 Then T$ = "+" + Str$(score(pnum))
	EndIf
	If gametype = 3
	SetFont MidFont
		ShadowText screenwidth/2,screenheight-24,"Pot: $"+Str$(SkinPot),True,False
		SetFont SmallFont
		T$ = "$" + Score(Pnum)
	EndIf
	ShadowText 16,56,T,False,True
End Function

Function YardsToHole(x1,y1,x2,y2)
	Return Int(Sqr((Abs(x1-x2)^2) + (Abs(y1-y2)^2))/8)
End Function

Function CentreScreen(X,Y)
	If KeyDown(35) Then X = PinX : Y = PinY
	If greenviews = 1
		If yardstohole(ballx(pnum),bally(pnum),pinx,piny)<(screenheight/16)-5 Then X = PinX : Y = PinY
	EndIf
	X = X-(ScreenWidth/2)
	Y = Y-(ScreenHeight/2)
	If x < 0 Then X = 0
	If y < 0 Then y = 0
	If x > (mapwidth*16)-Screenwidth Then X = (mapwidth*16)-Screenwidth
	If y > (mapheight*16)-Screenheight Then Y = (mapheight*16)-screenheight
	mapx = x/16
	mapy = y/16
	xoff = x Mod 16
	yoff = y Mod 16
	DrawMap mapx,mapy,xoff,yoff
End Function

Function CheckKeys()
	
	If KeyDown(31); DISPLAY SCORECARD (S)
		If gametype > 1 Or numplayers = 2
			While KeyDown(31)
				GameClock=MilliSecs()
				CentreScreen BallX(Pnum),BallY(Pnum)
				DrawBall
				DrawCaddy
				DrawPlayer(False)
				DrawPin
				DrawBird
				ScreenInfo
				DrawBallLie
				SetFont midfont
					ShadowText screenwidth/2,(screenheight/2)-20,"Player Scores",True,False
				For n = 1 To numplayers
					ShadowText screenwidth/3,(screenheight/2)+((n-1)*20),Pname(n),False,False
					If GameType = 3
						ShadowText ((screenwidth/3)*2)-StringWidth("$"+Str$(score(n))),(screenheight/2)+((n-1)*20),"$"+Str$(score(n)),False,False
					Else
						ShadowText ((screenwidth/3)*2)-StringWidth(stringscore$(score(n))),(screenheight/2)+((n-1)*20),stringscore$(score(n)),False,False
					EndIf
				Next
				While MilliSecs()-GameClock<(1000/FPS)
				Wend
				Flip
			Wend
		EndIf
	EndIf
	
	If KeyDown(35); DISPLAY HOLE (H)
		While KeyDown(35)
			GameClock=MilliSecs()
			CentreScreen PinX,PinY
			DrawBall
			DrawCaddy
			DrawPlayer(False)
			DrawPin
			DrawBird
			ScreenInfo
			DrawBallLie
			While MilliSecs()-GameClock<(1000/FPS)
			Wend
			Flip
		Wend
	EndIf
End Function

Function ScrollMap()
	While MouseDown(2)
		If MouseDown(1)
			Speed = 2
		Else
			Speed = 1
		EndIf
		Select mouseptr
			Case 1 : mapy=mapy-speed
			Case 2 : mapx=mapx+speed : mapy=mapy-speed
			Case 3 : mapx=mapx+speed
			Case 4 : mapx=mapx+speed : mapy=mapy+speed
			Case 5 : mapy=mapy+speed
			Case 6 : mapx=mapx-speed : mapy=mapy+speed
			Case 7 : mapx=mapx-speed
			Case 8 : mapx=mapx-speed : mapy=mapy-speed
		End Select
		If mapx<0 Then mapx=0
		If mapy<0 Then mapy=0
		If mapx>mapwidth-(screenwidth/16) Then mapx = mapwidth-(screenwidth/16)
		If mapy>mapheight-(screenheight/16) Then mapy = mapheight-(screenheight/16)
		GameClock=MilliSecs()
		DrawMap Mapx,Mapy,xoff,yoff
		DrawBall
		DrawCaddy
		DrawPlayer(True)
		DrawPin
		DrawBird
		ScreenInfo
		DrawBallLie
		ToolTip MouseX()+8,MouseY()+8,Str$(YardsToHole(LocalX(PlayerX(Pnum)),LocalY(PlayerY(Pnum)),MouseX(),MouseY()))+" yds"
		DrawMouse
		While MilliSecs()-GameClock<(1000/FPS)
		Wend
		Flip
	Wend
End Function

Function DefineClubs()
	Restore ClubNameData 
	For n = 0 To 13
		Read GolfBag(n)
	Next
	Restore ClubDistances
	For n = 0 To 13
		Read GolfYards(n)
	Next

End Function

Function DrawPowerIndicator(p)
	p=0-Int(p*2.15)
	If ClubChoice(Pnum) < 13
		DrawImage PowerMeter,screenwidth-74,screenheight-74,0
	Else
		DrawImage PowerMeter,screenwidth-74,screenheight-74,1
	EndIf
	DrawImage Indicator,Sin(p)*59+(screenwidth-74),Cos(p)*59+(screenheight-74)
End Function

Function DrawBallLie()
	C = GetLie(BallX(Pnum),BallY(Pnum))
	If C = 8 Or C = 9
		Offset = 4
		While C = 8 Or C = 9
			C = GetLie(BallX(Pnum),BallY(Pnum)+offset)
			Offset = Offset + 4
		Wend
	EndIf
	If C = 1 Then pic = 1
	If C = 2 Then pic = 2
	If C = 3 Then pic = 3
	If C = 4 Then Pic = 5
	If C = 6 Then Pic = 4
	If stroke(pnum) = 1 Then pic = 0 ; Ball is on tee - first shot
	DrawImage LiePic,screenwidth-74,screenheight-74,pic
End Function

Function DrawBird()
	SeedRnd MilliSecs()
	If BirdX = 0 And BirdY = 0
		n = Int(Rnd(0,200))		
		If n = 100
			BirdXSpeed = Int(Rnd(0,1))
			If BirdXSpeed = 0 Then BirdXSpeed = -5 : BirdX = (mapx*16)+screenwidth+12
			If BirdXSpeed = 1 Then BirdXSpeed = 5 : BirdX = (mapx*16)-12
			BirdY = Rnd(mapy*16,(mapy*16)+screenheight)
			BirdYSpeed = Int(Rnd(0,2))-1
		EndIf
	Else
		If birdxspeed>0
			DrawImage Bird,LocalX(BirdX),LocalY(BirdY),BirdFrame
		Else
			DrawImage Bird,LocalX(BirdX),LocalY(BirdY),BirdFrame+4
		EndIf
		L = GetLie(BirdX,BirdY+150)
		If L <>8 And L <>9
			Color 0,0,0
			Rect LocalX(BirdX),LocalY(BirdY)+150,3,1,True
		EndIf
		BirdX = BirdX + BirdXSpeed
		If BirdX > (mapx*16)+screenwidth+12 Then BirdX = 0 : BirdY = 0 : BirdYSpeed = 0 : BirdXSpeed = 0
		If BirdX < (mapx*16)-12 Then BirdX = 0 : BirdY = 0 : BirdYSpeed = 0: BirdXSpeed = 0
		
		BirdDelay = BirdDelay + 1
		BirdY = BirdY + BirdYSpeed
		If BirdDelay = 3 Then BirdDelay = 0 : BirdFrame = BirdFrame + 1
		If BirdFrame = 4 Then BirdFrame = 0
	EndIf
End Function
Function DrawSplash()
	If SplashFrame<>-1
		SplashCount = Splashcount + 1
		If SplashCount = 2
			SplashCount = 0
			Splashframe = Splashframe + 1
			If Splashframe>8 Then SplashFrame = -1
		EndIf
		
		If splashframe<>-1 Then DrawImage Splash,LocalX(BallX(Pnum)),LocalY(BallY(Pnum)),SplashFrame
	EndIf
End Function

Function DrawCaddy()
	Lie = GetLie(PlayerX(Pnum)+16,PlayerY(Pnum)-8)
	If Lie <8
		If GetDistance(PlayerX(Pnum),PlayerY(Pnum),PinX,PinY)>128
			If CaddyFrame = 0
				n = Int(Rnd(0,400))
				If n = 100 Then CaddyFrame = 1
				If n = 200 Then CaddyFrame = 13
			EndIf
			DrawImage Caddy,LocalX(PlayerX(Pnum))+16,LocalY(PlayerY(Pnum))-8,CaddyFrame
			If CaddyFrame<>0
			CaddyDelay = CaddyDelay + 1
				If CaddyDelay = 2 Then CaddyDelay = 0 : CaddyFrame = CaddyFrame + 1
				If CaddyFrame = 12 Then CaddyFrame = 0
				If CaddyFrame = 33 Then CaddyFrame = 0
			EndIf
		EndIf
	
	EndIf
End Function

Function DrawClub()
	SetFont SmallFont
	If ClubChoice(Pnum) < 3 Then DrawImage club,40,screenheight-8,0
	If ClubChoice(Pnum) > 2 And ClubChoice(Pnum) < 13 Then DrawImage club,40,screenheight-8,1
	If ClubChoice(Pnum) = 13 Then DrawImage club,40,screenheight-8,2
	Color 0,0,0 : Text 40,screenheight-32,GolfBag(ClubChoice(Pnum)),True,True
	Text 40,screenheight-20,GolfYards(ClubChoice(Pnum))+" yds",True,True
	Color 255,255,0 : Text 39,screenheight-33,GolfBag(ClubChoice(Pnum)),True,True
	Text 39,screenheight-21,GolfYards(ClubChoice(Pnum))+" yds",True,True
End Function

Function DrawPin()
	If GetDistance(BallX(Pnum),BallY(Pnum),PinX,PinY)>128
		DrawImage pin,LocalX(PinX),LocalY(PinY),pinimage
	EndIf
	PinImage = PinImage+1
	If PinImage = 12 Then PinImage = 0
End Function

Function DrawBall()
	BL = GetLie(BallX(Pnum),BallY(Pnum)+BallHeight)
	SL = GetLie(BallX(Pnum),BallY(Pnum))
	If  BL = 10 And BallHeight = 0 Then Goto NoBall
	If BallHeight>-72 And BL = 9 And SL > 7 And SL < 10 Then Goto NoBall
	If BallHeight>-18 And BL = 8 And SL = 8 Then Goto NoBall
	If SL <>8 And SL <>9 And SL <>7 Then DrawImage ball,LocalX(BallX(Pnum)),LocalY(BallY(Pnum)),1
	If BallInHole<2
		DrawImage ball,LocalX(BallX(Pnum)),LocalY(BallY(Pnum)+BallHeight),0
	Else
		DrawImage ball,LocalX(BallX(Pnum)),LocalY(BallY(Pnum)),BallInHole
	EndIf
.noBall
	If numplayers = 2
		If Pnum=1
			op = 2
		Else
			op = 1
		EndIf
		OL = GetLie(BallX(op),BallY(op))
		If OL<7
			DrawImage ball,LocalX(BallX(op)),LocalY(BallY(op)),1
			If OL = 6
				DrawImage ball,LocalX(BallX(op)),LocalY(BallY(op)),4
			Else
				DrawImage ball,LocalX(BallX(op)),LocalY(BallY(op)),0
			EndIf
		EndIf
	EndIf
End Function

Function DrawPlayer(moveable)
	Lie = GetLie(PlayerX(Pnum),PlayerY(Pnum))
	If Lie <>8 And Lie <>9
		If mouseptr=9
			If moveable = True
		 		Angle = GetAngle(LocalX(PlayerX(Pnum)),LocalY(PlayerY(Pnum)),MouseX(),MouseY())
				If angle >-1 And angle < 23 : PlayerImage = 44 : EndIf
				If angle > 22 And angle < 69 : PlayerImage = 110 : EndIf
				If angle > 68 And angle < 113 : PlayerImage = 0 : EndIf
				If angle > 112 And angle < 159 : PlayerImage = 88 : EndIf
				If angle > 158 And angle < 203 : PlayerImage = 66 : EndIf
				If angle > 202 And angle < 249 : PlayerImage = 132 : EndIf
				If angle > 248 And angle < 293 : PlayerImage = 22 : EndIf
				If angle > 292 And angle < 339 : PlayerImage = 154 : EndIf
				If angle > 338 And angle < 360 : PlayerImage = 44 : EndIf
			EndIf
			DrawImage golfer,LocalX(PlayerX(Pnum)),LocalY(PlayerY(Pnum)),playerimage
		Else	
			DrawImage golfer,LocalX(PlayerX(Pnum)),LocalY(PlayerY(Pnum)),playerimage
			DisplayPlayerName
		EndIf
	Else
		MarkerDelay = MarkerDelay+1
		If MarkerDelay = 3
			MarkerDelay = 0
			MarkerFrame = 1 - MarkerFrame
		EndIf
		DrawImage Marker,LocalX(PlayerX(Pnum)),LocalY(PlayerY(Pnum)),MarkerFrame
	EndIf
	FlashMessage Pname(Pnum) + " to play"
End Function

Function GetAngle(x1#,y1#,x2#,y2#)
	Angle = ATan2((x2-x1),(y2-y1))
	If angle < 0 Then angle = 360+Angle
	Return Angle
End Function

Function DisplayPlayerName()
	SetFont smallfont
	If PlayerY(Pnum)>24
		Color 0,0,0 : Text LocalX(PlayerX(Pnum)),LocalY(PlayerY(Pnum))-30,PName(Pnum),True,False
		Color 255,255,0 : Text LocalX(PlayerX(Pnum))-1,LocalY(PlayerY(Pnum))-31,PName(Pnum),True,False
	Else
		Color 0,0,0 : Text LocalX(PlayerX(Pnum)),LocalY(PlayerY(Pnum))+4,PName(Pnum),True,False
		Color 255,255,0 : Text LocalX(PlayerX(Pnum))+1,LocalY(PlayerY(Pnum))+3,PName(Pnum),True,False
	EndIf
End Function

Function LoadHole()
	LoadMap HolePath$ + Course(CourseNo) + "\Hole"+Trim(Str$(Hole))+".bin"
End Function

Function ReadHolePar()
	File = ReadFile(HolePath$ + Course(CourseNo) + "\Course.bin")
	For N = 1 To 18
		HolePar(n) = ReadShort(file)
	Next
	CloseFile File
	End Function

Function LoadFonts()
	SmallFont = LoadFont("Arial",12,True,False,False)
	MidFont = LoadFont("Arial",20,True,False,False)
	LargeFont = LoadFont("Arial",55,True,False,False)
End Function

Function FindPlayerStart()
	For xx=0 To mapwidth
	For YY = 0 To mapheight
		If map(xx,yy)=215
			x1=xx*16
			y1=yy*16
			Goto Marker2
		EndIf
	Next
	Next
.Marker2
	For xx=mapwidth To 0 Step -1
	For YY = mapheight To 0 Step -1
		If map(xx,yy)=215
			x2=xx*16
			y2=yy*16
			Goto Done
		EndIf
	Next
	Next
.done
	If x2<x1
		Temp = x2
		X2=x1
		x1=temp
	EndIf
	If y2<y1
		Temp = y2
		y2=y1
		y1=temp
	EndIf
	
	For N = 1 To 2
		PlayerX(N) = x1+((x2-x1)/2)+8
		PlayerY(N) = y1+((y2-y1)/2)+8
		BallX(N) = PlayerX(N)
		BallY(N) = PlayerY(N)
	Next
End Function

Function DrawMouse()
	mouseptr=0
	HandleImage pointer,0,0
	If BagShown <> True
		If GetDistance(LocalX(PlayerX(Pnum)),LocalY(PlayerY(Pnum)),MouseX(),MouseY())<180 Then mouseptr=9
		If MouseX()=>16 And MouseX()=<screenwidth-16 And MouseY()=<16 Then mouseptr=1
		If MouseX()=>screenwidth-16 And MouseY()=<16 Then mouseptr=2
		If MouseX()=>screenwidth-16 And MouseY()=>16 And MouseY()=<screenheight-16 Then mouseptr=3
		If MouseX()=>screenwidth-16 And MouseY()=>screenheight-16 Then mouseptr=4
		If MouseX()=>16 And MouseX()=<screenwidth-16 And MouseY()=>screenheight-16 Then mouseptr=5
		If MouseX()=<16 And MouseY()=>screenheight-16 Then mouseptr=6
		If MouseX()=<16 And MouseY()=>16 And MouseY()=<screenheight-16 Then mouseptr=7
		If MouseX()=<16 And MouseY()=<16 Then mouseptr=8
		If mouseptr = 0 Or mouseptr = 9 And MouseDown(2) Then mouseptr = 10
		If mouseptr = 0 Then HandleImage pointer,0,0
		If mouseptr = 1 Then HandleImage pointer,8,0
		If mouseptr = 2 Then HandleImage pointer,15,0
		If mouseptr = 3 Then HandleImage pointer,15,8
		If mouseptr = 4 Then HandleImage pointer,15,15
		If mouseptr = 5 Then HandleImage pointer,8,15
		If mouseptr = 6 Then HandleImage pointer,0,15
		If mouseptr = 7 Then HandleImage pointer,0,8
		If mouseptr = 8 Then HandleImage pointer,0,0
		If mouseptr = 9 Then HandleImage pointer,8,8
		If mouseptr = 10 Then HandleImage pointer,8,8
	EndIf
	DrawImage pointer,MouseX(),MouseY(),mouseptr
End Function

Function GetDistance(x1%,y1%,x2%,y2%)
	O=Abs(x1-x2)^2
	A=Abs(y1-y2)^2
	D=Sqr(O+A)
	Return D
End Function

Function GetThemeFilenames()
	Select Theme
		Case 1	; CLASSIC
			FPLAYER = "Golfer.bin"
			FTILE = "Tiles.bin"
			FBALL = "Ball.bin"
			FBIRD = "Bird.bin"
			FSPLASH = "Splash.bin"
			FLIEPIC = "Lie.bin"
			FTILEMASK = "Mask.Bin"
				FSBIRD1 = "Bird1.bin"
				FSTREE = "Trees.bin"
				FSGLASS = "Glassbreak1.bin"
				FSSPLASH = "Splash.bin"
				FSINCBIRD = "Bird2.bin"
				FSINCSHUTTER = "shutter.bin"
				FSINCSHEEP = "sheep.bin"
	End Select
End Function

Function LoadGraphics()
	DecodeFile FPLAYER$,graphicpath
	Golfer = LoadAnimImage(GraphicPath + "Temp.bin",24,24,0,176)
	MaskImage golfer,0,0,255
	HandleImage golfer,12,21
	
	DecodeFile "Flag.bin",graphicpath
	Pin = LoadAnimImage(graphicpath + "temp.bin",24,24,0,12)
	HandleImage Pin,12,21
	
	DecodeFile "Pointer.bin",graphicpath
	Pointer=LoadAnimImage(graphicpath + "temp.bin",16,16,0,11)
	MaskImage pointer,255,0,0
	
	DecodeFile FBALL$,graphicpath
	Ball = LoadAnimImage(graphicpath + "temp.bin",3,3,0,6)
	MaskImage Ball,40,128,40
	HandleImage Ball,1,2
	
	DecodeFile "Clubs.bin",graphicpath
	Club = LoadAnimImage(graphicpath + "temp.bin",68,51,0,3)
	HandleImage Club,34,50
	
	DecodeFile "Caddy.bin",graphicpath
	Caddy = LoadAnimImage(graphicpath + "temp.bin",24,24,0,34)
	MaskImage Caddy,255,0,0
	HandleImage Caddy,12,21
	
	DecodeFile FBIRD$,graphicpath
	Bird = LoadAnimImage(graphicpath + "temp.bin",11,6,0,8)
	MaskImage Bird,255,0,0
	HandleImage Bird,5,3
	
	DecodeFile "Power.bin",graphicpath
	PowerMeter = LoadAnimImage(graphicpath + "temp.bin",128,129,0,2)
	MaskImage PowerMeter,0,0,255
	MidHandle PowerMeter
	
	DecodeFile "Indicator.bin",graphicpath
	Indicator = LoadImage(graphicpath + "temp.bin")
	MaskImage Indicator,0,0,255
	HandleImage Indicator,5,5
	
	DecodeFile FSPLASH$,graphicpath
	Splash = LoadAnimImage(graphicpath + "temp.bin",16,16,0,9)
	HandleImage Splash,7,10
	
	DecodeFile "Marker.bin",graphicpath
	Marker = LoadAnimImage(graphicpath + "temp.bin",24,24,0,2)
	HandleImage Marker,12,12
	MaskImage Marker,255,0,0
	
	DecodeFile "Golfbag.bin",graphicpath
	Bag = LoadImage(graphicpath + "temp.bin")
	HandleImage Bag,ImageWidth(Bag)/2,ImageHeight(Bag)-1
	
	DecodeFile FLIEPIC$,graphicpath
	LiePic = LoadAnimImage(graphicpath + "temp.bin",63,63,0,6)
	MidHandle Liepic
	MaskImage Liepic,0,0,255
	
	DecodeFile "FlagPtr.bin",graphicpath
	FlagPtr = LoadAnimImage(graphicpath + "temp.bin",16,16,0,8)
	MidHandle FlagPtr
	MaskImage FlagPtr,0,0,255
	
	DeleteFile graphicpath + "temp.bin"
End Function

Function DecodeFile(Filename$,Path$)
	filename = path + filename
	FileBuffer = CreateBank(FileSize(Filename))
	File = OpenFile(Filename)
	Size = FileSize(Filename)
	ReadBytes FileBuffer,File,0,Size
	CloseFile File
	For N = 0 To Size - 4 Step 4
		NewVal = 255-PeekByte(FileBuffer,N)
		PokeByte FileBuffer,N,NewVal
	Next
	File=WriteFile(path + "Temp.bin")
	WriteBytes FileBuffer,File,0,Size
	CloseFile File
	FreeBank FileBuffer
End Function
Function LoadSounds()
	DecodeFile "Driver.bin",SoundPath
	SSwing = LoadSound(Soundpath + "temp.bin")
	DecodeFile "Putter.bin",SoundPath
	SPutter = LoadSound(Soundpath + "temp.bin")
	DecodeFile FSBIRD1$,SoundPath
	SBird1 = LoadSound(SoundPath + "temp.bin")
	DecodeFile "Inhole.bin",SoundPath
	SHole = LoadSound(SoundPath + "temp.bin")
	DecodeFile "greenbounce.bin",SoundPath
	SBounce = LoadSound(SoundPath + "temp.bin")
	DecodeFile FSSPLASH$,SoundPath
	SSplash = LoadSound(SoundPath + "temp.bin")
	DecodeFile FSTREE$,SoundPath
	STree = LoadSound(SoundPath + "temp.bin")
	DecodeFile FSGLASS$,SoundPath
	SGlass = LoadSound(SoundPath + "temp.bin")

	DecodeFile FSINCBIRD$,SoundPath
	SIncBird = LoadSound(SoundPath + "temp.bin")
	DecodeFile FSINCSHUTTER$,SoundPath
	SIncShutter = LoadSound(SoundPath + "temp.bin")
	DecodeFile FSINCSHEEP$,SoundPath
	SIncSheep = LoadSound(SoundPath + "temp.bin")

	DecodeFile "ApplauseSmall.bin",SoundPath
	SClap1 = LoadSound(Soundpath + "temp.bin")
	DecodeFile "Applause.bin",SoundPath
	SClap2 = LoadSound(Soundpath + "temp.bin")
	DecodeFile "ApplauseBig.bin",SoundPath
	SClap3 = LoadSound(Soundpath + "temp.bin")

	DeleteFile soundpath + "temp.bin"
	
	Select Theme
	Case 1
		SoundVolume SBird1,1
		SoundVolume SIncBird,0.5
		SoundVolume SIncShutter,0.1
		SoundVolume SIncSheep,0.1
	End Select
End Function

Function IncidentalSound()
	n = Int(Rnd(0,2000))
	If n = 60 Then PlaySound SIncBird
	If n = 61 Then PlaySound SIncShutter
	If n = 62 Then PlaySound SIncSheep
End Function

Function FreeGameStuff()
	FreeSound Sswing
	FreeSound SPutter
	FreeSound SBird1
	FreeSound SHole
	FreeSound SBounce
	FreeSound Ssplash
	FreeSound Sglass
	FreeSound Sincbird
	FreeSound Sinchsutter
	FreeSound Sincsheep
	
	FreeImage Golfer
	FreeImage Pin
	FreeImage Pointer
	FreeImage Ball
	FreeImage Club
	FreeImage Caddy
	FreeImage Bird
	FreeImage PowerMeter
	FreeImage Indicator
	FreeImage Splash
	FreeImage Marker
	FreeImage Bag
	FreeImage LiePic
	FreeImage FlagPtr
	FreeImage HoleMap
	FreeImage HoleMapSmall
End Function

Function LoadTiles()
	DecodeFile FTILE$,graphicpath
	Tile = LoadAnimImage(GraphicPath + "temp.bin",16,16,0,229)
	MaskImage tile,255,255,255
	SmallTile = LoadAnimImage(GraphicPath + "temp.bin",16,16,0,225)
	ShrinkTiles
	DecodeFile FTILEMASK$,graphicpath
	TileMask = LoadAnimImage(GraphicPath + "temp.bin",16,16,0,224)
	DeleteFile graphicpath + "temp.bin"
End Function

Function ShrinkTiles()
	TFormFilter True
	ScaleImage SmallTile,0.0625,0.0625
End Function

Function LoadMap(Filename$)
	FILE = ReadFile(Filename$)
	mapwidth = ReadShort(FILE)
	mapheight = ReadShort(FILE)
	Dim map(mapwidth,mapheight)
	For x = 0 To mapwidth
		For y = 0 To mapheight
			map(x,y)=ReadShort(FILE)
		Next
	Next
	
	CloseFile FILE
End Function

Function IdentifyMarkers()
	CPUMarker = 1
	For n = 1 To 5
		MarkerX(n) = 0
		MarkerY(n) = 0
	Next
	For N = 1 To 5
		For XX = 0 To mapwidth
			For yy = 0 To mapheight
				If map(xx,yy) = N+224
					MarkerX(n) = XX*16
					MarkerY(n) = YY*16
					map(xx,yy) = map(xx-1,yy)
				EndIf
			Next
		Next
		If MarkerX(N) = 0
			MarkerX(n) = PinX
			MarkerY(n) = PinY
			Exit
		EndIf
	Next
End Function

Function FindHole()
	SeedRnd MilliSecs()
	x = 0 : y = 0
	While map(x,y)<>122
	x=Int(Rnd(0,mapwidth))
	y=Int(Rnd(0,mapheight))
	Wend
	
	While map(x+hstep,y+vstep)<>122
		hstep=Int(Rnd(-10,10))
		vstep=Int(Rnd(-10,10))
	Wend
	
	map(x+hstep,y+vstep)=119
	PinX=((x+hstep)*16)+7
	PinY=((Y+hstep)*16)+11
	
	IdentifyMarkers
	
End Function

Function GenerateOverView()
	If holemap <>0 Then FreeImage holemap : FreeImage holemapsmall
	HoleMap = CreateImage (mapwidth,mapheight)
	SetBuffer ImageBuffer(Holemap)
	Color 0,0,255
	Rect 0,0,ImageWidth(holemap),ImageHeight(holemap),True
	xtile = 0
	ytile = 0
	Color 0,0,0
	For YY = 0 To ImageHeight(HoleMap)-1
		For XX = 0 To ImageWidth(HoleMap)-1
				DrawImage SmallTile,xx,yy,map(xx,yy)
		Next
	Next
	MaskImage holemap,0,0,255
	Color 0,0,0
	Rect 0,0,ImageWidth(HoleMap),ImageHeight(HoleMap),False
	
	HandleImage HoleMap,ImageWidth(Holemap),0
	
	HoleMapSmall = CopyImage(HoleMap)
	TFormFilter False
	ScaleImage HoleMapSmall,0.65,0.65
	SetBuffer ImageBuffer(HoleMapSmall)
	Rect 0,0,ImageWidth(HoleMapSmall),ImageHeight(HoleMapSmall),False
	SetBuffer BackBuffer()	
End Function

Function DrawOverView()
	If KeyDown(50)
		DrawImage HoleMap,screenwidth-8,8
		X# = screenwidth-8-ImageWidth(HoleMap)
		Y# = 8
		PX = x+(PlayerX(Pnum)/16);*2
		PY = y+(PlayerY(Pnum)/16);*2
		HX = x+(PinX/16);*2
		HY = y+(PinY/16);*2
		Color 0,0,0
		Oval PX-4,PY-4,8,8,True
		Oval HX-4,HY-4,8,8,True
		Text PX-StringWidth(PName(Pnum))-4,PY,PName(Pnum),False,True
		Text HX-StringWidth("Hole")-4,HY,"Hole",False,True
		Color 255,255,0
		Oval PX-3,PY-3,6,6,True
		Text PX-StringWidth(PName(Pnum))-5,PY-1,PName(Pnum),False,True
		Text HX-StringWidth("Hole")-5,HY-1,"Hole",False,True
		Color 255,0,0
		Oval HX-3,HY-3,6,6,True	
	Else
		DrawImage HoleMapSmall,screenwidth-8,8

		;DRAW HOLE POSITION IN OVERVIEW
		X = screenwidth-8-ImageWidth(HoleMapSmall)
		Y = 8
		HX = X + ((pinx/16)*0.65)
		HY = Y + ((piny/16)*0.65)
		Color 0,0,0
		Rect HX,HY,2,2

		;DRAW 'PLAYER!'
		Color 0,0,0
		PMX# = X + ((playerx(pnum)/16)*0.65)
		PMY# = Y + ((playery(pnum)/16)*0.65)
		Line PMX,PMY,PMX,PMY-3

		; DRAW BALL IN HOLE OVERVIEW
		BX = X + ((BallX(pnum)/16)*0.65)
		BY = Y + (((BallY(pnum)+BallHeight)/16)*0.65)
		SX = X + ((BallX(pnum)/16)*0.65)
		SY = Y + ((BallY(pnum)/16)*0.65)
		If SX>X And SX <X+ImageWidth(HoleMapSmall)
			If SY > Y And SY < Y+ImageHeight(HoleMapSmall)
				Color 0,0,0 : Rect SX,SY,1,1
			EndIf
		EndIf
		If BX>X And BX <X+ImageWidth(HoleMapSmall)
			If BY > Y And BY < Y+ImageHeight(HoleMapSmall)
				Color 255,255,255 : Rect BX,BY,1,1
			EndIf
		EndIf
		If mouseptr = 9
		Angle = GetAngle(LocalX(PlayerX(Pnum)),LocalY(PlayerY(Pnum)),MouseX(),MouseY())
		Line SX,SY,Sin(Angle) * 10 + SX,Cos(Angle) * 10 + SY
		EndIf
		
		
	EndIf
End Function

Function DrawMap(x,y,xs,ys)
	Cls
	xtile = x
	ytile = y

	For yy = 0 - ys To screenheight-ys+16 Step 16
		For xx = 0 - xs To screenwidth-xs Step 16
			If xtile<mapwidth+1 And ytile <mapheight+1
				DrawBlock Tile,xx,yy,map(xtile,ytile)
			EndIf
			xtile = xtile + 1
		Next
		xtile = xtile - (screenwidth/16)-1
		ytile = ytile + 1
	Next
End Function

Function LocalX(x)
	Return x-(mapx*16)-xoff
End Function

Function LocalY(y)
	Return y-(mapy*16)-yoff
End Function

;ZONELIB FUNCTIONS
Function ZLReserveZone(Num%)
	ZoneLib = CreateBank(Num*8)
	NumZones = Num
End Function

Function ZLSetZone(Zone,X1,Y1,X2,Y2)
	M = (Zone-1)*8
	PokeShort ZoneLib,M,X1
	PokeShort ZoneLib,M+2,Y1
	PokeShort ZoneLib,M+4,X2
	PokeShort ZoneLib,M+6,Y2	
End Function

Function ZLKillZone(Zone)
	M = (Zone-1)*8
	PokeShort ZoneLib,M,0
	PokeShort ZoneLib,M+2,0
	PokeShort ZoneLib,M+4,0
	PokeShort ZoneLib,M+6,0	
End Function

Function ZLFreeZones()
	FreeBank ZoneLib
End Function

Function ZLMouseZone()
	ZoneNum = 0
	For N = 1 To NumZones
		M = (N-1)*8
		X1 = PeekShort(ZoneLib,M)
		Y1 = PeekShort(ZoneLib,M+2)
		X2 = PeekShort(ZoneLib,M+4)
		Y2 = PeekShort(ZoneLib,M+6)
		If MouseX()>X1
			If MouseX()<X2
				If MouseY()>Y1
					If MouseY()<Y2
						ZoneNum = N
					EndIf
				EndIf
			EndIf
		EndIf
	Next
	Return ZoneNum
End Function

.ClubNameData
	Data "Driver","3 Wood","5 Wood"
	Data "2 Iron", "3 Iron", "4 Iron"
	Data "5 Iron", "6 Iron", "7 Iron"
	Data "8 Iron", "9 Iron", "Pitching Wedge"
	Data "Sand Wedge", "Putter"
	
.ClubDistances
	Data 304,289,274
	Data 258,247,227,204,182,151,132,119
	Data 97, 50, 40