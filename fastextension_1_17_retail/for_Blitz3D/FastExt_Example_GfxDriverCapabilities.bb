; Example of use FastExt library
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


; Пример получения информации о поддержке видео-картой операций смешивания, Bump, кол-во клип-плейнов и т.п.
; Get videocard capabilities example (info about blends, bump, clipplanes hardware support)


;
; Run in DEBUG mode !!!
;


Include "include\FastExt.bb"		; <<<<    Include FastExt.bb file



Graphics3D 800,600,0,2



	InitExt					; <<<<    Initialize library after Graphics3D function



	Dim D3DTEXOPCAPS$(24)
		D3DTEXOPCAPS(0) = "D3DTEXOPCAPS_DISABLE"
		D3DTEXOPCAPS(1) = "D3DTEXOPCAPS_SELECTARG1"
		D3DTEXOPCAPS(2) = "D3DTEXOPCAPS_SELECTARG2"
		D3DTEXOPCAPS(3) = "D3DTEXOPCAPS_MODULATE"
		D3DTEXOPCAPS(4) = "D3DTEXOPCAPS_MODULATE2X"
		D3DTEXOPCAPS(5) = "D3DTEXOPCAPS_MODULATE4X"
		D3DTEXOPCAPS(6) = "D3DTEXOPCAPS_ADD"
		D3DTEXOPCAPS(7) = "D3DTEXOPCAPS_ADDSIGNED"
		D3DTEXOPCAPS(8) = "D3DTEXOPCAPS_ADDSIGNED2X"
		D3DTEXOPCAPS(9) = "D3DTEXOPCAPS_SUBTRACT"
		D3DTEXOPCAPS(10) = "D3DTEXOPCAPS_ADDSMOOTH"
		D3DTEXOPCAPS(11) = "D3DTEXOPCAPS_BLENDDIFFUSEALPHA"
		D3DTEXOPCAPS(12) = "D3DTEXOPCAPS_BLENDTEXTUREALPHA"
		D3DTEXOPCAPS(13) = "D3DTEXOPCAPS_BLENDFACTORALPHA"
		D3DTEXOPCAPS(14) = "D3DTEXOPCAPS_BLENDTEXTUREALPHAPM"
		D3DTEXOPCAPS(15) = "D3DTEXOPCAPS_BLENDCURRENTALPHA"
		D3DTEXOPCAPS(16) = "D3DTEXOPCAPS_PREMODULATE"
		D3DTEXOPCAPS(17) = "D3DTEXOPCAPS_MODULATEALPHA_ADDCOLOR"
		D3DTEXOPCAPS(18) = "D3DTEXOPCAPS_MODULATECOLOR_ADDALPHA"
		D3DTEXOPCAPS(19) = "D3DTEXOPCAPS_MODULATEINVALPHA_ADDCOLOR"
		D3DTEXOPCAPS(20) = "D3DTEXOPCAPS_MODULATEINVCOLOR_ADDALPHA"
		D3DTEXOPCAPS(21) = "D3DTEXOPCAPS_BUMPENVMAP"
		D3DTEXOPCAPS(22) = "D3DTEXOPCAPS_BUMPENVMAPLUMINANCE"
		D3DTEXOPCAPS(23) = "D3DTEXOPCAPS_DOTPRODUCT3"
		
	Dim D3DPBLENDCAPS$(13)
		D3DPBLENDCAPS(0) = "D3DPBLENDCAPS_ZERO"
		D3DPBLENDCAPS(1) = "D3DPBLENDCAPS_ONE"
		D3DPBLENDCAPS(2) = "D3DPBLENDCAPS_SRCCOLOR"
		D3DPBLENDCAPS(3) = "D3DPBLENDCAPS_INVSRCCOLOR"
		D3DPBLENDCAPS(4) = "D3DPBLENDCAPS_SRCALPHA"
		D3DPBLENDCAPS(5) = "D3DPBLENDCAPS_INVSRCALPHA"
		D3DPBLENDCAPS(6) = "D3DPBLENDCAPS_DESTALPHA"
		D3DPBLENDCAPS(7) = "D3DPBLENDCAPS_INVDESTALPHA"
		D3DPBLENDCAPS(8) = "D3DPBLENDCAPS_DESTCOLOR"
		D3DPBLENDCAPS(9) = "D3DPBLENDCAPS_INVDESTCOLOR"
		D3DPBLENDCAPS(10) = "D3DPBLENDCAPS_SRCALPHASAT"
		D3DPBLENDCAPS(11) = "D3DPBLENDCAPS_BOTHSRCALPHA"
		D3DPBLENDCAPS(12) = "D3DPBLENDCAPS_BOTHINVSRCALPHA"	

	Dim D3DPTEXTURECAPS$(12)
		D3DPTEXTURECAPS(0) = "D3DPTEXTURECAPS_PERSPECTIVE"
		D3DPTEXTURECAPS(1) = "D3DPTEXTURECAPS_POW2"
		D3DPTEXTURECAPS(2) = "D3DPTEXTURECAPS_ALPHA"
		D3DPTEXTURECAPS(3) = "D3DPTEXTURECAPS_TRANSPARENCY"
		D3DPTEXTURECAPS(4) = "D3DPTEXTURECAPS_BORDER"
		D3DPTEXTURECAPS(5) = "D3DPTEXTURECAPS_SQUAREONLY"
		D3DPTEXTURECAPS(6) = "D3DPTEXTURECAPS_TEXREPEATNOTSCALEDBYSIZE"
		D3DPTEXTURECAPS(7) = "D3DPTEXTURECAPS_ALPHAPALETTE"
		D3DPTEXTURECAPS(8) = "D3DPTEXTURECAPS_NONPOW2CONDITIONAL"
		D3DPTEXTURECAPS(9) = "D3DPTEXTURECAPS_PROJECTED"
		D3DPTEXTURECAPS(10) = "D3DPTEXTURECAPS_CUBEMAP"
		D3DPTEXTURECAPS(11) = "D3DPTEXTURECAPS_COLORKEYBLEND"





	DebugLog "BrushBlendsSrc (EntityBlendsSrc): " + Hex(GfxDriverCapsEx\BrushBlendsSrc)
	mask = 1
	For i=0 To 12
		If (GfxDriverCapsEx\BrushBlendsSrc And mask)<>0 Then
			DebugLog "	+ " + D3DPBLENDCAPS(i)
		Else
			DebugLog "	- " + D3DPBLENDCAPS(i)
		EndIf
		mask = mask Shl 1
	Next
	DebugLog " "
	
	DebugLog "BrushBlendsDest (EntityBlendsDest): " + Hex(GfxDriverCapsEx\BrushBlendsDest)
	mask = 1
	For i=0 To 12
		If (GfxDriverCapsEx\BrushBlendsDest And mask)<>0 Then
			DebugLog "	+ " + D3DPBLENDCAPS(i)
		Else
			DebugLog "	- " + D3DPBLENDCAPS(i)
		EndIf
		mask = mask Shl 1
	Next
	DebugLog " "
	
	DebugLog "TextureCaps: " + Hex(GfxDriverCapsEx\TextureCaps)
	mask = 1
	For i=0 To 11
		If (GfxDriverCapsEx\TextureCaps And mask)<>0 Then
			DebugLog "	+ " + D3DPTEXTURECAPS(i)
		Else
			DebugLog "	- " + D3DPTEXTURECAPS(i)
		EndIf
		mask = mask Shl 1
	Next
	DebugLog " "
	
	DebugLog "TextureBlends: " + Hex(GfxDriverCapsEx\TextureBlends)
	mask = 1
	For i=0 To 23
		If (GfxDriverCapsEx\TextureBlends And mask)<>0 Then
			DebugLog "	+ " + D3DTEXOPCAPS(i)
		Else
			DebugLog "	- " + D3DTEXOPCAPS(i)
		EndIf
		mask = mask Shl 1
	Next
	DebugLog " "

	DebugLog "TextureMaxStages: " 	+ GfxDriverCapsEx\TextureMaxStages
	DebugLog "TextureMaxWidth: " 		+GfxDriverCapsEx\TextureMaxWidth
	DebugLog "TextureMaxHeight: " 		+GfxDriverCapsEx\TextureMaxHeight
	DebugLog "TextureMaxAspectRatio: " 	+GfxDriverCapsEx\TextureMaxAspectRatio
	DebugLog "ClipplanesMax: " 		+GfxDriverCapsEx\ClipplanesMax
	DebugLog "LightsMax: " 			+GfxDriverCapsEx\LightsMax
	DebugLog "Bump: "	 			+GfxDriverCapsEx\Bump
	DebugLog "BumpLum: " 			+GfxDriverCapsEx\BumpLum
	DebugLog "AnisotropyMax: "		+GfxDriverCapsEx\AnisotropyMax
	
	
	
	SetFont LoadFont("",16)
	Print "See Debug log"
	WaitKey()