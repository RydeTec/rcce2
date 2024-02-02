;	3D Text Demo
;	(c)2001 Rob Pearmain
;	www.peargames.com

;	Why on earth did I write this?

;	I wanted to demonstrate dong Arcade type fonts but using 3D
;	to produce the fonts

;	That's it

;	'*' is a line terminator
;	'.' is total terminator

;	Supports A-Z,0-9,copyright, etc.

Graphics3D 640,480,16
SetBuffer BackBuffer()

camera=CreateCamera()
CameraViewport camera,0,0,640,480
PositionEntity camera,0,0,-500

light=CreateLight()

nPivot = CreatePivot()

nLine=200	

;	Load and create the characters
Restore namedata
lEnd = 0
While lEnd = 0
	nCounter=-200
	Read a$
	While a$ <> "*" And a$ <> "."
		nObject =	LoadMesh(a$+".3ds",nPivot)
		PositionEntity nObject,nCounter,nLine,0
		EntityColor nObject,Rnd(0,255),Rnd(0,255),Rnd(0,255)
		nCounter = nCounter+100
		Read a$
	Wend
	If a$="." Then lEnd=1 Else nLine = nLine - 100
Wend

;	Loop and turn
  While Not KeyHit(1)

	  TurnEntity nPivot,.1,.2,.3
		
      UpdateWorld
      RenderWorld

      Text 320,500,"First Blitz3D Program"

      Flip

  Wend
  End

.namedata
Data "B","L","I","T","Z","3","D","*"
Data "R","O","C","K","S","*"
Data "*"
Data "B","Y","*"
Data "R","O","B","."