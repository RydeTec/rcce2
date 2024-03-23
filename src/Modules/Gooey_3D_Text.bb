; Misc ------------------------------------------------------------------------------------------------------------------------------

Const GY_Letters$ = " ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789,./\'#?<>[]();:!£$%^&*+-@~=_|
;àáâãäçèéêëïñòóôõöùúûüýÿßÆæØøÅåñÑ"

; Types -----------------------------------------------------------------------------------------------------------------------------

Type GY_Font
	Field Texture, Font_Height, Font_Width#
    ; Arrays used for UV lookup after load
    Field SpacingX[256]
    Field OffsetX[256]
    Field OffsetY[256]
End Type

; Arrays used for creation and saving only. not lookup
Dim GY_Font_Spacing(256)
Dim GY_Font_OffsetX(256)
Dim GY_Font_OffsetY(256)

Type GY_Text3D
	Field EN
	Field Font.GY_Font
	Field MaxLength
	Field ScaleX#
End Type

; Functions -------------------------------------------------------------------------------------------------------------------------

; Returns the width of a text gadget in units if a certain string is applied
Function GY_TextWidth#(TextHandle, Dat$)

	T.GY_Text3D = Object.GY_Text3D(EntityName$(TextHandle))
	If T = Null Then Return 0.0

	VX# = 0.0
	For i = 1 To Len(Dat$)
		CharNumber = Instr(GY_Letters$, Mid$(Dat$, i, 1))
		If CharNumber < 1 Then CharNumber = 1
		CharWidth# = Float#(T\Font\SpacingX[CharNumber]) / 512.0
		VX# = VX# + (1.0 / Float#(T\MaxLength)) * (CharWidth# / T\Font\Font_Width#)
	Next

	Return VX# * T\ScaleX#

End Function

; Creates a 3D text line
Function GY_Create3DText(X#, Y#, Width#, Height#, MaxLength, Font, Parent = 0, ZOrder = -1)

	T.GY_Text3D = New GY_Text3D
	T\Font = Object.GY_Font(Font)
	If T\Font = Null Then Delete(T) : Return 0
	T\MaxLength = MaxLength

	T\EN = CreateMesh(Parent)
	Surf = CreateSurface(T\EN)
	PositionEntity(T\EN, (X# * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)
	EntityOrder(T\EN, ZOrder)
	EntityFX(T\EN, 1 + 8)
	EntityTexture(T\EN, T\Font\Texture)

	For i = 0 To MaxLength - 1
		V1 = AddVertex(Surf, Float#(i) / Float#(MaxLength),     -1.0, 0.0)
		V2 = AddVertex(Surf, Float#(i) / Float#(MaxLength),      0.0, 0.0)
		V3 = AddVertex(Surf, Float#(i + 1) / Float#(MaxLength), -1.0, 0.0)
		V4 = AddVertex(Surf, Float#(i + 1) / Float#(MaxLength),  0.0, 0.0)
		AddTriangle(Surf, V4, V3, V1)
		AddTriangle(Surf, V2, V4, V1)
	Next
	ScaleEntity(T\EN, Width# * 20.0, Height# * 15.0, 1.0)
	T\ScaleX# = Width#

	NameEntity(T\EN, Handle(T))
	GY_Set3DText(T\EN, String$(" ", MaxLength))
	Return T\EN

End Function

; Updates the position of a 3D text line
Function GY_Position3DText(TextHandle, X#, Y#)

	T.GY_Text3D = Object.GY_Text3D(EntityName$(TextHandle))
	If T = Null Then Return

	PositionEntity(T\EN, (X# * 20.0) - 10.0, (Y# * -15.0) + 7.5, 10.0)

End Function

; Sets the text for a 3D text line
Function GY_Set3DText(TextHandle, Dat$)

	T.GY_Text3D = Object.GY_Text3D(EntityName$(TextHandle))
	If T = Null Then Return False

	Surf = GetSurface(T\EN, 1)

	If Len(Dat$) < T\MaxLength Then Dat$ = Dat$ + String$(" ", T\MaxLength - Len(Dat$))
	VX# = 0.0
	CharHeight# = Float#(T\Font\Font_Height) / 512.0
	For i = 1 To T\MaxLength
		CharNumber = Instr(GY_Letters$, Mid$(Dat$, i, 1))
		If CharNumber < 1 Then CharNumber = 1

		CharWidth# = Float#(T\Font\SpacingX[CharNumber]) / 512.0
        StartU# = Float#(T\Font\OffsetX[CharNumber]) / 512.0
		EndU# = StartU# + CharWidth#
        StartV# = T\Font\OffsetY[CharNumber] / 512.0
		EndV# = StartV# + CharHeight#

		Vertex = (i - 1) * 4
		VertexTexCoords(Surf, Vertex,     StartU#, EndV#)
		VertexTexCoords(Surf, Vertex + 1, StartU#, StartV#)
		VertexTexCoords(Surf, Vertex + 2, EndU#,   EndV#)
		VertexTexCoords(Surf, Vertex + 3, EndU#,   StartV#)

		VertexCoords(Surf, Vertex, VX#, -1.0, 0.0)
		VertexCoords(Surf, Vertex + 1, VX#, 0.0, 0.0)
		VX# = VX# + (1.0 / Float#(T\MaxLength)) * (CharWidth# / T\Font\Font_Width#)
		VertexCoords(Surf, Vertex + 2, VX#, -1.0, 0.0)
		VertexCoords(Surf, Vertex + 3, VX#, 0.0, 0.0)
	Next

	Return True

End Function

; Frees some 3D text
Function GY_Free3DText(TextHandle, FreeFontAlso = False)

	T.GY_Text3D = Object.GY_Text3D(EntityName$(TextHandle))
	If T = Null Then Return False

	FreeEntity(T\EN)
	If FreeFontAlso = True Then GY_FreeFont(Handle(T\Font))
	Delete(T)

	Return True

End Function

; Frees a font texture
Function GY_FreeFont(FontHandle)

	F.GY_Font = Object.GY_Font(FontHandle)
	If F = Null Then Return False

	FreeTexture(F\Texture)
	Delete(F)

	Return True

End Function

; Loads a font texture from file
Function GY_LoadFont(Name$, MIPMap = False)

	; Load data
	F = ReadFile(Name$ + ".dat")
		If F = 0 Then Return 0
		Fn.GY_Font = New GY_Font
		Fn\Font_Height = ReadShort(F)
		For i = 1 To 256
			Fn\SpacingX[i] = ReadInt(F)
			Fn\OffsetX[i] = ReadInt(F)
			Fn\OffsetY[i] = ReadInt(F)
		Next
	CloseFile(F)

	; Load texture
	If MIPMap
		Fn\Texture = LoadTexture(Name$ + ".bmp", 1 + 4 + 8 + 16 + 32)
	Else
		Fn\Texture = LoadTexture(Name$ + ".bmp", 1 + 4 + 16 + 32)
	EndIf
	If Fn\Texture = 0
		Delete(Fn)
		Return 0
	EndIf

	; Make spaces smaller
	Fn\SpacingX[1] = Fn\SpacingX[1] / 2

	; Find largest character width
	For i = 1 To Len(GY_Letters$)
		CharWidth# = Float#(Fn\SpacingX[i]) / 512.0
		If CharWidth# > Fn\Font_Width# Then Fn\Font_Width# = CharWidth#
	Next

	Return Handle(Fn)

End Function

; Generates a font texture
Function GY_GenerateFont(Name$, Font$, Height, R, G, B, SR = 0, SG = 0, SB = 0, Bold = False, Italic = False, UnderL = False, Shadow = False)

	; Load font
	FontHandle = LoadFont(Font$, Height, Bold, Italic, UnderL)
	SetFont(FontHandle)

	; Find maximum character width/height
	ShadowSize = Height / 12
	CharWidth = 1
	FH = 1
	For Char = 1 To Len(GY_Letters$)

		; Create 1 tile image with 1 char
		Temp_Image = CreateImage(FontWidth() + 25, FontHeight() + 25)
		SetBuffer(ImageBuffer(Temp_Image))
		Color(0, 0, 0)
		Rect(0, 0, ImageWidth(Temp_Image), ImageWidth(Temp_Image))
		Color(R, G, B)
		Text(0, 0, Mid$(GY_Letters$, Char, 1))
		CharWidth = 0
		AvgWidth = 0

		; Determine width/height
		LockBuffer(ImageBuffer(Temp_Image))
		For i = 0 To ImageWidth(Temp_Image) - 1
			For ii = 0 To ImageHeight(Temp_Image) - 1
				ThisPixel = ReadPixelFast(i, ii)
				RCVal = ThisPixel Shr 0 And 255 Shl 0
				If RCVal <> 0 ; TEXT SPOT.. RECORD WIDTH
					If CharWidth < i Then CharWidth = i
					If FH < ii Then FH = ii
				EndIf
			Next
		Next
		UnlockBuffer(ImageBuffer(Temp_Image))

		FreeImage(Temp_Image)
		SetBuffer(BackBuffer())
		Color(0, 0, 0)
		Rect(0, 0, 256, 256)

		If Char = 1
			CharWidth = FontWidth() / 2
		Else
			CharWidth = CharWidth + 4
		EndIf

		FH = FontHeight()
		If Shadow Then FH = FH + ShadowSize + 2
		GY_Font_Spacing(Char) = CharWidth
		If CharWidth > AvgWidth Then AvgWidth = CharWidth

	Next

	; If it's really wide, add extra rows to reduce final texture width
	ImgWidth = 512
	ImgHeight = 512
   
	; Create temporary stuff
	CharsPerRow = Int(Ceil#(Float#(ImgWidth) / Float#(AvgWidth)))
	Image = CreateImage(ImgWidth, ImgHeight)
	OldBuffer = GraphicsBuffer()
	SetBuffer(ImageBuffer(Image))
   
	; Do each letter one at a time
	X = 0
	Y = 0
	For Char = 1 To Len(GY_Letters$)
		; Draw
		If Shadow
			Color(SR, SG, SB)
			Text(X + ShadowSize, Y + ShadowSize, Mid$(GY_Letters$, Char, 1), 0)
		EndIf
		Color(R, G, B)
		Text(X, Y, Mid$(GY_Letters$, Char, 1), 0)

		; Record place in image that character is stored
		GY_Font_OffsetX(Char) = X
		GY_Font_OffsetY(Char) = Y

		; Advance to next letter position
		X = X + GY_Font_Spacing(Char) + 4
		Chc = Chc + GY_Font_Spacing(Char) + 4
		If Chc > (ImgWidth - 72)
			X = 0
			Chc = 0
			Y = Y + Fh
		EndIf
	Next

	; Save
	SaveBuffer(ImageBuffer(Image), Name$ + ".bmp")
	F = WriteFile(Name$ + ".dat")
		WriteShort(F, FH)
		For i = 1 To 256
			WriteInt(F, GY_Font_Spacing(i))
			WriteInt(F, GY_Font_OffsetX(i))
			WriteInt(F, GY_Font_OffsetY(i))
		Next
	CloseFile(F)

	; Free temporary stuff
	SetBuffer(OldBuffer)
	FreeImage(Image)
	FreeFont(FontHandle)

End Function