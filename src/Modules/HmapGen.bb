


Dim himg(0)
Dim hcol(0,0,0)
Dim TempArr(0,0)



Const HMAPGEN_DEBUG = False
Function SmoothImage(image,times)
imgw=ImageWidth(image)
imgh=ImageHeight(image)
        SetBuffer ImageBuffer(image)
		LockBuffer ImageBuffer(image)
        For i=1 To times
		For X = 1 To imgh-2
			For Z = 1 To imgw-2
				ARGB = ReadPixelFast(X,Z)
				hcol1 =(ARGB Shr 16) And $ff 
				ARGB = ReadPixelFast(X-1,Z)
 			    hcol2 =(ARGB Shr 16) And $ff 
				ARGB = ReadPixelFast(X+1,Z)
 			    hcol3 =(ARGB Shr 16) And $ff 
				ARGB = ReadPixelFast(X,Z-1)
 			    hcol4 =(ARGB Shr 16) And $ff 
				ARGB = ReadPixelFast(X,Z+1)
 			    hcol5 =(ARGB Shr 16) And $ff 
                newh=(hcol1+hcol2+hcol3+hcol4+hcol5)*.2
	            WritePixelFast x,z,255 Shl 24 Or newh Shl 16 Or newh Shl 8 Or newh Shl 0
             Next
            Next
            Next
          UnlockBuffer ImageBuffer(image)
          SetBuffer BackBuffer()
End Function
               
Function PerlinHeightMap%(Seed$, SizeX%, SizeZ%, MountainPx%, Steepness#, Contrast#, Roughness#)

	If HMAPGEN_DEBUG Then GenStart = MilliSecs()
	
	TFormFilter True

	;check and/or adjust vars
	If (SizeX <= 0) Or (SizeZ <= 0) Then Return 0
	If (MountainPx < 1) Then MountainPx = 1
	If (Steepness < 0.0) Then Steepness = 0.0
	If (Steepness > 1.0) Then Steepness = 1.0
	If (Contrast < 0.0) Then Contrast = 0.0
	If (Contrast > 1.0) Then Contrast = 1.0
	If (Roughness < 0.0) Then Roughness = 0.0
	If (Roughness > 1.0) Then Roughness = 1.0
	
	;calculate seed and set it
	For L = 1 To Len(Seed)
		RSeed = RSeed + Asc(Mid(Seed,L,1))
	Next
	SeedRnd RSeed

	;calculate start sizes and passes
	StartSizeZ = Int( Float(SizeZ) / Float(MountainPX) )
	StartSizeX = Int( Float(SizeX) / Float(MountainPX) )
	Passes = Ceil( Log(SizeX)/Log(StartSizeX) )
	If Ceil( Log(SizeZ)/Log(StartSizeZ) ) > Passes Then Passes = Ceil( Log(SizeZ)/Log(StartSizeZ) )

	Passes = Ceil( Log(SizeX / StartSizeX) / Log(2.0) + 1 )
	If Ceil( Log(SizeZ / StartSizeZ) / Log(2.0) + 1 ) > Passes Then Passes = Ceil( Log(SizeZ / StartSizeZ) / Log(2.0) + 1 )

	;If HMAPGEN_DEBUG Then print "StartSizeX / StartSizeZ -> " + StartSizeX + " / " + StartSizeZ
	If HMAPGEN_DEBUG Then print "Passes -> " + Passes
	
	;dim arrays
	Dim himg(Passes)
	Dim hcol(Passes, SizeX-1, SizeZ-1)

	;set create sizes
	CreateSizeX = StartSizeX
	CreateSizeZ = StartSizeZ

	;for every pass
	For i = 1 To Passes
		If HMAPGEN_DEBUG Then print "State -> Creating Pass " + i + " with Size " + CreateSizeX + " | " + CreateSizeZ
		;create a pass image
		himg(i) = CreateImage(CreateSizeX, CreateSizeZ)
		;create and fill the temp array with random height values
		Dim TempArr(CreateSizeX-1, CreateSizeZ-1)
		
		For X = 0 To CreateSizeX-1
			For Z = 0 To CreateSizeZ-1
				TempArr(X,Z) = Rand(0, 255)
			Next
		Next
		
		;write heights to the image
		LockBuffer ImageBuffer(himg(i))
		For X = 0 To CreateSizeX-1
			For Z = 0 To CreateSizeZ-1
				C = (1-Steepness)*SmoothTempArr(X,Z, CreateSizeX,CreateSizeZ) + (Steepness)*TempArr(X, Z)
				ARGB = 255*$1000000 + C*$10000 + C*$100 + C
				WritePixelFast(X, Z, ARGB, ImageBuffer(himg(i)))
			Next
		Next
		UnlockBuffer ImageBuffer(himg(i))
		
		;resize image
		ResizeImage(himg(i), SizeX, SizeZ)

		;the next image will be double size
		CreateSizeX = CreateSizeX *2
		CreateSizeZ = CreateSizeZ *2
	Next
	
	;read heights and put them into an array
	For i = 1 To Passes
		LockBuffer ImageBuffer(himg(i))
		For X = 0 To SizeX-1
			For Z = 0 To SizeZ-1
				ARGB = ReadPixelFast(X,Z,ImageBuffer(himg(i)))
				hcol(i,X,Z) = -128 + (ARGB And $FF)
			Next
		Next
		UnlockBuffer ImageBuffer(himg(i))
		FreeImage himg(i)
	Next

	;compile all passes' values into one array
	If HMAPGEN_DEBUG Then print "State -> Adjusting Passes"
	For i = 1 To Passes
		For X = 0 To SizeX-1
			For Z = 0 To SizeZ-1
				hcol(0,X,Z) = hcol(0,X,Z) + hcol(i,X,Z) /Float(i^(2.5-2.5*Roughness)); /Float(Passes)
			Next
		Next
	Next

	;adjust contrast
	For X = 0 To SizeX-1
		For Z = 0 To SizeZ-1
			hcol(0,X,Z) = hcol(0,X,Z) / ((1-Contrast)*4.0+0.01)
		Next
	Next

	;create heightmap and write values to it
	If HMAPGEN_DEBUG Then print "State -> Generating Heightmap"
	himg(0) = CreateImage(SizeX, SizeZ)
	LockBuffer ImageBuffer(himg(0))
	For X = 0 To SizeX-1
		For Z = 0 To SizeZ-1
			hcol(0,X,Z) = hcol(0,X,Z) + 128
			If hcol(0,X,Z) < 0 Then hcol(0,X,Z) = 0
			If hcol(0,X,Z) > 255 Then hcol(0,X,Z) = 255
			ARGB = 255*$1000000 + hcol(0,X,Z)*$10000 + hcol(0,X,Z)*$100 + hcol(0,X,Z)
			WritePixelFast(X,Z,ARGB,ImageBuffer(himg(0)))
		Next
	Next
	UnlockBuffer ImageBuffer(himg(0))

	;clear arrays
	FinalImg = himg(0)
	Dim himg(0)
	Dim hcol(0,0,0)
	Dim TempArr(0,0)
	
	;return the complete heightmap
	If HMAPGEN_DEBUG Then print("Done! Gen Time -> " + (MilliSecs()-GenStart) + " ms")
	Return FinalImg
	
End Function

Function SmoothTempArr(X,Z,MaxX,MaxZ)
	Local Corner1=128, Corner2=128, Corner3=128, Corner4=128
	Local Neighb1=128, Neighb2=128, Neighb3=128, Neighb4=128
	Local Self=128

	;Corners
	CX=X-1 : CZ=Z-1
	If (CX => 0) And (CZ => 0) And (CX < MaxX) And (CZ < MaxZ) Then Corner1 = TempArr(CX,CZ)

	CX=X+1 : CZ=Z-1
	If (CX => 0) And (CZ => 0) And (CX < MaxX) And (CZ < MaxZ) Then Corner2 = TempArr(CX,CZ)

	CX=X+1 : CZ=Z+1
	If (CX => 0) And (CZ => 0) And (CX < MaxX) And (CZ < MaxZ) Then Corner3 = TempArr(CX,CZ)

	CX=X-1 : CZ=Z+1
	If (CX => 0) And (CZ => 0) And (CX < MaxX) And (CZ < MaxZ) Then Corner4 = TempArr(CX,CZ)

	;Neighbors
	CX=X+1 : CZ=Z
	If (CX => 0) And (CZ => 0) And (CX < MaxX) And (CZ < MaxZ) Then Neighb1 = TempArr(CX,CZ)

	CX=X-1 : CZ=Z
	If (CX => 0) And (CZ => 0) And (CX < MaxX) And (CZ < MaxZ) Then Neighb2 = TempArr(CX,CZ)

	CX=X : CZ=Z+1
	If (CX => 0) And (CZ => 0) And (CX < MaxX) And (CZ < MaxZ) Then Neighb3 = TempArr(CX,CZ)

	CX=X : CZ=Z-1
	If (CX => 0) And (CZ => 0) And (CX < MaxX) And (CZ < MaxZ) Then Neighb4 = TempArr(CX,CZ)

	;Self
	CX=X : CZ=Z
	If (CX => 0) And (CZ => 0) And (CX < MaxX) And (CZ < MaxZ) Then Self = TempArr(CX,CZ)

	Return Int( Float(Self)/4.0 + Float(Neighb1+Neighb2+Neighb3+Neighb4)/8.0 + Float(Corner1+Corner2+Corner3+Corner4)/16.0 )
	
End Function
; ************************************************************





;HMap = PerlinHeightMap(Chr$(Rand(65,90))+Chr$(Rand(65,90))+Chr$(Rand(65,90))+Chr$(Rand(65,90))+Chr$(Rand(65,90)), 128, 128, Rand(10,100), Rnd(.1,.9), Rnd(0.0,1.0),Rnd(0.0,1.0))