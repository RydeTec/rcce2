; Transparent Sprite demo by Dave Kirk (GFK)
; Use cursor keys to move sprite, ESCAPE to quit.
; Not really fast enough for lots of (or big) sprites, but still pretty effective.

AppTitle "Transparent Sprite Demo"

Graphics 800,600,16,1

SetBuffer BackBuffer()

Global FPS = 50

Global BKG = LoadImage("Background.bmp")
Global IMG = LoadImage("sprite.bmp")

While KeyDown(1) = 0
	t = MilliSecs()
	
	If KeyDown(203) Then X = X - 2
	If KeyDown(205) Then X = X + 2
	If KeyDown(200) Then Y = Y - 2
	If KeyDown(208) Then Y = Y + 2
	
	DrawBlock BKG,0,0
	
	AlphaSprite IMG,X,Y
	
	While MilliSecs()-t < 1000/FPS : Wend
	Flip
Wend

End

Function AlphaSprite(Image%,XP%,YP%)
	AlphaImage = CopyImage(Image)
	W = ImageWidth(AlphaImage)
	H = ImageHeight(AlphaImage)
	
	LockBuffer ImageBuffer(AlphaImage)
	LockBuffer ImageBuffer(BKG)
	For X = 0 To W - 1
		For Y = 0 To H - 1
			CB = ReadPixelFast(XP+X,YP+Y,ImageBuffer(BKG))
			CI = ReadPixelFast(X,Y,ImageBuffer(AlphaImage))
			Col# = Abs(CB - CI)
			Col = col /2
			If CB < CI
				Col = CB + Col
			Else
				Col = CI + Col
			EndIf
			WritePixelFast X,Y,Col,ImageBuffer(AlphaImage)
		Next
	Next
	
	UnlockBuffer ImageBuffer(AlphaImage)
	UnlockBuffer ImageBuffer(BKG)
	
	DrawImage AlphaImage,XP,YP
	FreeImage alphaimage
End Function