; Include file for FastExt library [Simple Shadows]
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com

Const ShadowGroupID% = 1000
Const ShadowGroupHiddenID% = $7FFFFFFF

Type ShadowCaster
	Field entity%
	Field showAuto%
	Field showAlways%
	Field enabled%
	Field autofade%
	Field attached%
End Type

Type ShadowReceiver
	Field entity%
	Field showAuto%
	Field showAlways%
	Field enabled%
	Field autofade%
	Field attached%
End Type

Type Shadow
	Field castersGroupID%
	Field castersCount%
	Field receiversGroupID%
	Field receiversCount%
	Field cameraFov#
	Field light%
	Field lightType%
	Field lightFov#
	Field power#
	Field powerR#
	Field powerG#
	Field powerB#
	Field powerColor%
	Field range#
	Field quality%
	Field texture%
	Field texSize%
	Field texBlend%
	Field enabled%
	Field offsetX#
	Field offsetY#
	Field offsetZ#
	Field fadeTexture%
	Field mode%
	Field blurAlpha#
	Field blurPasses%
	Field blurRadius#
	Field blurQuality%
End Type

Global ShadowMatrix.Matrix3D = New Matrix3D
Global ShadowLightDefault% = 0
Global ShadowCameraDefault% = 0
Global ShadowDefault.Shadow = New Shadow
Global ShadowInitFlag% = 0



Function CreateShadow (quality%=1, mode%=1)
	Local t#, w%, Shadow.Shadow, i%
	If ShadowInitFlag = 0 Then
		ShadowInitFlag = 1
	
		If ShadowLightDefault=0 Then
			ShadowLightDefault = CreatePivot()
			RotateEntity ShadowLightDefault, 90, 0, 0
		EndIf
		
		Shadow = ShadowDefault
		Shadow\castersGroupID = ShadowGroupID
		Shadow\receiversGroupID = ShadowGroupID + 1
		Shadow\castersCount = 0
		Shadow\receiversCount = 0
		Shadow\enabled = 1
		Shadow\light = 0
		Shadow\lightFov = 90
		Shadow\offsetX = 0
		Shadow\offsetY = 0
		Shadow\offsetZ = 0
		Shadow\fadeTexture = 0
		Shadow\cameraFov = 5
		ShadowColor 255, 255, 255
		ShadowPower 0.5
		ShadowRange 50
		Shadow\mode = mode
		Shadow\blurAlpha = 0
		Shadow\blurPasses = 4
		Shadow\blurRadius = 0.1
		Shadow\blurQuality = 0
		
		Select quality
			Case 0
				Shadow\texSize = 512
				Shadow\quality = 0
			Case 1
				Shadow\texSize = 1024
				Shadow\quality = 1
			Case 2
				Shadow\texSize = 2048
				Shadow\quality = 2
			Default
				Shadow\texSize = 512
				Shadow\quality = 0
		End Select
		
		If Shadow\mode=0 Then
			w = GraphicsHeight()
			If GraphicsHeight()>GraphicsWidth() Then w = GraphicsWidth()
			While Shadow\texSize>w : Shadow\texSize = Shadow\texSize Shr 1 : Wend
			Shadow\texture = CreateTexture(Shadow\texSize, Shadow\texSize, 1+16+32+256 )	
		Else
			If Shadow\texSize>GfxDriverCapsEx\TextureMaxWidth Then Shadow\texSize = GfxDriverCapsEx\TextureMaxWidth
			If Shadow\texSize>GfxDriverCapsEx\TextureMaxHeight Then Shadow\texSize = GfxDriverCapsEx\TextureMaxHeight
			Shadow\texture = CreateTexture(Shadow\texSize, Shadow\texSize, 1+16+32+256+FE_RENDER+FE_ZRENDER )
		EndIf

		If ShadowCameraDefault=0 Then
			ShadowCameraDefault = CreateCamera() 
			t =  Tan ( Shadow\cameraFov / 2.0 )
			CameraRange ShadowCameraDefault, 0.1 / t, 1000.0 / t
			CameraZoom ShadowCameraDefault, 1.0 / t
			CameraFogMode ShadowCameraDefault, 1
			CameraFogRange ShadowCameraDefault, 0, 0.01
			CameraFogColor ShadowCameraDefault, Shadow\powerColor, Shadow\powerColor, Shadow\powerColor
			CameraClsColor ShadowCameraDefault, 255, 255, 255
			CameraClsMode ShadowCameraDefault, 0, 0
			CameraViewport ShadowCameraDefault, 0, 0, Shadow\texSize, Shadow\texSize
			CameraProjMode ShadowCameraDefault, 0
		EndIf
	
		SetBuffer TextureBuffer(Shadow\texture)
			Color 255, 255, 255
			Rect 0, 0, Shadow\texSize, Shadow\texSize,1
		SetBuffer BackBuffer()
		ScaleTexture Shadow\texture, 1.0, 1.0
		Shadow\texBlend = FETOP_PROJECT3D1 Or (D3DTOP_MODULATE Shl 8) Or D3DTOP_MODULATE
		TextureBlend Shadow\texture, Shadow\texBlend
		
		TextureIndex Shadow\texture, 0
		ShadowLight ShadowLightDefault
	EndIf	
End Function


Function ShowShadow% ()	
	Local tmp%
	tmp = ShadowDefault\enabled
	ShadowDefault\enabled = 1
	Return tmp
End Function


Function HideShadow% ()
	Local tmp%
	tmp = ShadowDefault\enabled
	ShadowDefault\enabled = 0
	Return tmp
End Function


Function DebugShadow% (x%=0, y%=0, width%=256, height%=256)
	If ShadowDefault\texture<>0 Then CopyRectStretch 0,0, TextureWidth(ShadowDefault\texture),TextureHeight(ShadowDefault\texture), x, y, width, height, TextureBuffer(ShadowDefault\texture), BackBuffer()
End Function


Function FreeShadows% ()
	Local Shadow.Shadow = ShadowDefault
	If ShadowInitFlag<>0 Then
		ShadowInitFlag = 0
		ClearGroup Shadow\castersGroupID
		ClearGroup Shadow\receiversGroupID
		If Shadow\texture<>0 Then
			SetBuffer TextureBuffer(Shadow\texture)
				Color 255, 255, 255
				Rect 0, 0, Shadow\texSize, Shadow\texSize,1
			SetBuffer BackBuffer()
			TextureBlend Shadow\texture, Shadow\texBlend And $FFFF
			TextureIndex Shadow\texture, 0
			FreeTexture Shadow\texture
		EndIf
		If ShadowLightDefault<>0 Then
			FreeEntity ShadowLightDefault
			ShadowLightDefault = 0
		EndIf
		If ShadowCameraDefault<>0 Then
			FreeEntity ShadowCameraDefault
			ShadowCameraDefault = 0
		EndIf
		SetBuffer BackBuffer()
	EndIf
End Function


Function UpdateShadows% (camera%, tween#=1.0)
	Local j%, c.ShadowCaster, fog%, blend%, t#, r.ShadowReceiver, oldBuffer%, attachedCount%
	Local Shadow.Shadow = ShadowDefault
	Local renderFlag% = 0

	If ShadowInitFlag<>0 And Shadow\enabled<>0 And Shadow\castersCount>0 Then
		;
		; shadow-camera A.I.
		;
		If Shadow\lightType=0
			PositionEntity ShadowCameraDefault, EntityX(camera,1), EntityY(camera,1), EntityZ(camera,1), 1
			RotateEntity ShadowCameraDefault, EntityPitch(Shadow\light,1), EntityYaw(Shadow\light,1), EntityRoll(Shadow\light,1), 1
			MoveEntity ShadowCameraDefault, 0, 0,  -Shadow\range / Tan ( Shadow\cameraFov / 2.0 )
			t =  Tan ( Shadow\cameraFov / 2.0 )
		Else
			PositionEntity ShadowCameraDefault, EntityX(Shadow\light,1), EntityY(Shadow\light,1), EntityZ(Shadow\light,1), 1
			RotateEntity ShadowCameraDefault, EntityPitch(Shadow\light,1), EntityYaw(Shadow\light,1), EntityRoll(Shadow\light,1), 1
			t =  Tan ( Shadow\lightFov / 2.0 )
		EndIf
		CameraRange ShadowCameraDefault, 0.1 / t, 1000.0 / t
		CameraZoom ShadowCameraDefault, 1.0 / t
					
		fog = FogMode(1)
		TextureBlend Shadow\texture, Shadow\texBlend And $FFFF
		
		If Shadow\mode<>0 Then
			oldBuffer = SetBuffer (TextureBuffer(Shadow\texture))
		Else
			oldBuffer = SetBuffer (BackBuffer())
		EndIf
		
		ShadowsOverwriteFX0 = GetOverwriteFX(0) : ShadowsOverwriteFX1 = GetOverwriteFX(1)	; save fx
		OverwriteFX 0, $FFFFFFF7
		ClsColor 255, 255, 255 : Cls 1, 1

		If Shadow\receiversCount>0 Then
			;
			; render receivers (fill zbuffer)
			;
			attachedCount = 0
			For r = Each ShadowReceiver
				r\attached = 0
				If r\enabled<>0 Then
					If EntityInView( r\entity, ShadowCameraDefault )<>0 Or r\showAlways<>0 Then
						If r\showAuto<>0 Then ShowEntity r\entity
						r\autofade = EntityAutoFadeMode (r\entity, -1)
						EntityAutoFadeMode r\entity, 0
						r\attached = 1
						attachedCount = attachedCount + 1
					EndIf
				EndIf
			Next

			If attachedCount>0 Then
				CameraFogColor ShadowCameraDefault, 255, 255, 255
				RenderGroup Shadow\receiversGroupID, ShadowCameraDefault, 0, tween
				For r = Each ShadowReceiver
					If r\attached<>0 Then
						If r\showAuto<>0 Then HideEntity r\entity
						EntityAutoFadeMode r\entity, r\autofade
					EndIf
					r\attached = 0
				Next
			EndIf
		EndIf

		;
		; render casters
		;
		attachedCount = 0
		For c = Each ShadowCaster
			c\attached = 0
			If c\enabled<>0 Then
				If EntityInView( c\entity, ShadowCameraDefault )<>0 Or c\showAlways<>0  Then
					If c\showAuto<>0 Then ShowEntity c\entity
					c\autofade = EntityAutoFadeMode (c\entity, -1)
					EntityAutoFadeMode c\entity, 0
					c\attached = 1
					attachedCount = attachedCount + 1
				EndIf
			EndIf
		Next

		If attachedCount>0 Then
			CameraFogColor ShadowCameraDefault, Shadow\powerR * Shadow\power, Shadow\powerG * Shadow\power, Shadow\powerB * Shadow\power
			RenderGroup Shadow\castersGroupID, ShadowCameraDefault, 0, tween
			For c = Each ShadowCaster
				If c\attached<>0 Then
					If c\showAuto<>0 Then HideEntity c\entity
					EntityAutoFadeMode c\entity, c\autofade
				EndIf
				c\attached = 0
			Next
			
			SetViewMatrixCurrent 0
			CopyProjMatrix ShadowMatrix
				ShadowMatrix\m41 = ShadowMatrix\m41 + Shadow\offsetX
				ShadowMatrix\m42 = ShadowMatrix\m42 + Shadow\offsetY
				ShadowMatrix\m43 = ShadowMatrix\m43 + Shadow\offsetZ
			SetProjMatrix 0, ShadowMatrix
			
			If Shadow\blurAlpha>0 Then
				CustomPostprocessBlur Shadow\blurAlpha, Shadow\blurPasses, Shadow\blurRadius, Shadow\blurQuality
				RenderPostprocess FE_Blur			
			EndIf

			Color 255, 255, 255
			Rect 0, 0, Shadow\texSize, Shadow\texSize, 0
			If Shadow\fadeTexture<>0
				CustomPostprocessOverlay 1, 0, 255, 255, 255, Shadow\fadeTexture
				RenderPostprocess FE_Overlay
			EndIf
		EndIf

		OverwriteFX ShadowsOverwriteFX0, ShadowsOverwriteFX1						; restore fog mode
		FogMode fog

		; rect for debug
		;	Color 0,0,0
		;	Rect Shadow\texSize/4,Shadow\texSize/4,Shadow\texSize/2,Shadow\texSize/2,0
		;	Rect 1,1,Shadow\texSize-2,Shadow\texSize-2,0
	
		If Shadow\mode=0 Then  CopyRect 0, 0,  Shadow\texSize, Shadow\texSize,  0, 0,  BackBuffer(), TextureBuffer(Shadow\texture)
		SetBuffer oldBuffer
		TextureBlend Shadow\texture, Shadow\texBlend
	Else
		If Shadow\texture<>0 Then
			oldBuffer = SetBuffer (TextureBuffer(Shadow\texture))
			Color 255, 255, 255
			Rect 0, 0, Shadow\texSize, Shadow\texSize, 1
			SetBuffer oldBuffer
		EndIf
	EndIf
End Function


Function ShadowLight% (light_entity%=-1, typ%=0, fov#=90.0)
	Local tmp%, i%, res%
	If light_entity=-1 Then
		Return ShadowDefault\light
	Else
		tmp = ShadowDefault\light
		If light_entity=0 Then light_entity = ShadowLightDefault
		ShadowDefault\light = light_entity
		ShadowDefault\lightType = typ
		ShadowDefault\lightFov = fov
		Return tmp
	EndIf
End Function


Function ShadowOffset (x#=0, y#=0, z#=0)
	ShadowDefault\offsetX = x
	ShadowDefault\offsetY = y
	ShadowDefault\offsetZ = z
End Function


Function ShadowFade% (fadeTexture%=-1)
	Local tmp%
	If fadeTexture=-1 Then
		Return ShadowDefault\fadeTexture
	Else
		tmp = ShadowDefault\fadeTexture
		ShadowDefault\fadeTexture = fadeTexture
		Return tmp
	EndIf
End Function


Function ShadowColor# (r#=255, g#=255, b#=255)
	Local tmp#

	If r<0 Then r=0 : If r>255 Then r=255
	If g<0 Then g=0 : If g>255 Then g=255
	If b<0 Then b=0 : If b>255 Then b=255
	tmp = r : If g>tmp Then tmp=g : If b>tmp Then tmp=b
	
	If tmp=0 Then
		r = 255 : g = 255 : b = 255 : tmp = 255
	EndIf
	tmp = 255 / tmp

	ShadowDefault\powerR = r * tmp
	ShadowDefault\powerG = g * tmp
	ShadowDefault\powerB = b * tmp
End Function


Function ShadowPower# (power#=-1)
	Local tmp#
	If power=-1 Then
		Return ShadowDefault\power
	Else
		tmp = ShadowDefault\power
		If power<0 Then power=0
		If power>1 Then power=1
		ShadowDefault\power = 1 - power
		Return tmp
	EndIf
End Function


Function ShadowRange# (range#=-1)
	Local tmp#
	If range=-1 Then
		Return ShadowDefault\range
	Else
		tmp = ShadowDefault\range
		If range<1 Then range=1
		ShadowDefault\range= range
		Return tmp
	EndIf
End Function


Function ShadowBlur% (alpha#=1.0, passes%=4, radius#=0.1, quality%=0)
	ShadowDefault\blurAlpha = alpha
	ShadowDefault\blurPasses = passes
	ShadowDefault\blurRadius = radius
	ShadowDefault\blurQuality = quality
End Function


Function ShadowTexture% ()
	Return ShadowDefault\texture
End Function


Function CreateShadowCaster% (caster_entity%, showAuto%=0, showAlways%=0)
	Local i.ShadowCaster, class$
	If caster_entity<>0 Then
		class = EntityClass(caster_entity)
		If class<>"Light" And class<>"Camera" And class<>"Mirror" And class<>"Listener" Then
			For i = Each ShadowCaster
				If i\entity=caster_entity Then Return 0
			Next
			i = New ShadowCaster
			i\showAuto = showAuto
			i\showAlways = showAlways
			i\entity = caster_entity
			i\enabled = 1
			GroupAttach ShadowDefault\castersGroupID, caster_entity
			ShadowDefault\castersCount = ShadowDefault\castersCount + 1
			Return 1
		EndIf		
	EndIf
	Return 0	
End Function


Function ShowShadowCaster% (caster_entity%)
	Local i.ShadowCaster, tmp%
	If caster_entity<>0 Then
		f = 0
		For i = Each ShadowCaster
			If i\entity=caster_entity Then
				tmp = i\enabled
				i\enabled = 1
				GroupChange ShadowDefault\castersGroupID, i\entity
				Return tmp
			EndIf
		Next
	EndIf
	Return 0
End Function


Function HideShadowCaster% (caster_entity%)
	Local i.ShadowCaster, tmp%
	If caster_entity<>0 Then
		For i = Each ShadowCaster
			If i\entity=caster_entity Then
				tmp = i\enabled
				i\enabled = 0
				GroupChange ShadowGroupHiddenID, i\entity
				Return tmp
			EndIf
		Next
	EndIf
	Return 0
End Function


Function FreeShadowCaster% (caster_entity%)
	Local i.ShadowCaster
	If caster_entity<>0 Then
		For i = Each ShadowCaster
			If i\entity=caster_entity Then
				GroupDetach ShadowDefault\castersGroupID, caster_entity
				ShadowDefault\castersCount = ShadowDefault\castersCount - 1
				Delete i
				Return 1
			EndIf
		Next
	EndIf
	Return 0
End Function


Function FindShadowCaster% (caster_entity%)
	Local i.ShadowCaster
	If caster_entity<>0 Then
		For i = Each ShadowCaster
			If i\entity=caster_entity Then Return 1
		Next
	EndIf
	Return -1
End Function


Function AttachShadowReceiver% (receiver_entity%, showAuto%=0, showAlways%=0)
	Local i.ShadowReceiver, class$
	If receiver_entity<>0 Then
		class = EntityClass(receiver_entity)
		If class<>"Light" And class<>"Camera" And class<>"Mirror" And class<>"Listener" Then
			For i = Each ShadowReceiver
				If i\entity=receiver_entity Then Return 0
			Next
			i = New ShadowReceiver
			i\showAuto = showAuto
			i\showAlways = showAlways
			i\entity = receiver_entity
			i\enabled = 1
			GroupAttach ShadowDefault\receiversGroupID, receiver_entity
			ShadowDefault\receiversCount = ShadowDefault\receiversCount+1
			Return 1
		EndIf		
	EndIf
	Return 0	
End Function


Function ShowShadowReceiver% (receiver_entity%)
	Local i.ShadowReceiver, tmp%
	If receiver_entity<>0 Then
		f = 0
		For i = Each ShadowReceiver
			If i\entity=receiver_entity Then
				tmp = i\enabled
				i\enabled = 1
				GroupChange ShadowDefault\receiversGroupID, i\entity
				Return tmp
			EndIf
		Next
	EndIf
	Return 0
End Function


Function HideShadowReceiver% (receiver_entity%)
	Local i.ShadowReceiver, tmp%
	If receiver_entity<>0 Then
		For i = Each ShadowReceiver
			If i\entity=receiver_entity Then
				tmp = i\enabled
				i\enabled = 0
				GroupChange ShadowGroupHiddenID, i\entity
				Return tmp
			EndIf
		Next
	EndIf
	Return 0
End Function


Function FreeShadowReceiver% (receiver_entity%)
	Local i.ShadowReceiver
	If receiver_entity<>0 Then
		For i = Each ShadowReceiver
			If i\entity=receiver_entity Then
				GroupDetach ShadowDefault\receiversGroupID, receiver_entity
				ShadowDefault\receiversCount = ShadowDefault\receiversCount-1
				Delete i
				Return 1
			EndIf
		Next
	EndIf
	Return 0
End Function


Function FindShadowReceiver% (receiver_entity%)
	Local i.ShadowReceiver
	If receiver_entity<>0 Then
		For i = Each ShadowReceiver
			If i\entity=receiver_entity Then Return 1
		Next
	EndIf
	Return -1
End Function