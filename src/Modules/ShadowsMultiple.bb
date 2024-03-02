; Include file for FastExt library [Multiple Shadows]
; (c) 2006-2010 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com


Const ShadowObjectsMax% = 2048
Const ShadowGroupID% = 1000
Const ShadowGroupHiddenID% = $7FFFFFFF

Type ShadowObject
	Field entity%
	Field showAuto%
	Field showAlways%
	Field enabled%
	Field autofade%
	Field attached%
End Type

Type Shadow
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
	Field castersGroupID%
	Field castersCount%
	Field casters.ShadowObject[ShadowObjectsMax]
	Field receiversGroupID%
	Field receiversCount%
	Field receivers.ShadowObject[ShadowObjectsMax]
	Field mode%
	Field index%
	Field blurAlpha#
	Field blurPasses%
	Field blurRadius#
	Field blurQuality%
	Field update%
	Field updateFirst%
End Type

Global ShadowMatrix.Matrix3D = New Matrix3D
Global ShadowLightDefault% = 0
Global ShadowCameraDefault% = 0
Global ShadowIndexDefault% = 0
Global ShadowGroupIDCount% = ShadowGroupID



Function CreateShadow.Shadow (quality%=1, mode%=1)
	Local t#, w%, Shadow.Shadow, i%, index%

	If ShadowLightDefault=0 Then
		ShadowLightDefault = CreatePivot()
		RotateEntity ShadowLightDefault, 90, 0, 0
	EndIf
	
	Shadow = New Shadow
	Shadow\castersGroupID = ShadowGroupIDCount   :   ShadowGroupIDCount = ShadowGroupIDCount + 1
	Shadow\castersCount = 0
	Shadow\receiversGroupID =  ShadowGroupIDCount  :   ShadowGroupIDCount = ShadowGroupIDCount + 1
	Shadow\receiversCount = 0
	Shadow\enabled = 1
	Shadow\light = 0
	Shadow\offsetX = 0
	Shadow\offsetY = 0
	Shadow\offsetZ = 0
	Shadow\fadeTexture = 0
	Shadow\cameraFov = 5
	ShadowColor Shadow, 255, 255, 255
	ShadowPower Shadow, 0.5
	ShadowRange Shadow, 50
	Shadow\mode = mode
	Shadow\blurAlpha = 0
	Shadow\blurPasses = 4
	Shadow\blurRadius = 0.1
	Shadow\blurQuality = 0
	Shadow\update = 1
	Shadow\updateFirst = 0
	
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
		CameraProjMode ShadowCameraDefault, 0
	EndIf

	SetBuffer TextureBuffer(Shadow\texture)
		Color 255, 255, 255
		Rect 0, 0, Shadow\texSize, Shadow\texSize,1
	SetBuffer BackBuffer()
	ScaleTexture Shadow\texture,1.0,1.0
	Shadow\texBlend = FETOP_PROJECT3D1 Or (D3DTOP_MODULATE Shl 8) Or D3DTOP_MODULATE
	TextureBlend Shadow\texture, Shadow\texBlend
	
	ShadowLight Shadow, ShadowLightDefault
	Shadow\index = ShadowIndexDefault
	;TextureIndex Shadow\texture, Shadow\index
	ShadowIndexDefault = ShadowIndexDefault + 1

	Return Shadow
End Function


Function ShowShadow% (ShadowIn.Shadow)	
	Local tmp%
	tmp = ShadowIn\enabled
	ShadowIn\enabled = 1
	Return tmp
End Function


Function HideShadow% (ShadowIn.Shadow)
	Local tmp%
	tmp = ShadowIn\enabled
	ShadowIn\enabled = 0
	Return tmp
End Function


Function DebugShadow% (ShadowIn.Shadow, x%=0, y%=0, width%=256, height%=256)
	If ShadowIn\texture<>0 Then CopyRectStretch 0,0, TextureWidth(ShadowIn\texture),TextureHeight(ShadowIn\texture), x, y, width, height, TextureBuffer(ShadowIn\texture), BackBuffer()
End Function


Function FreeShadows% ()
	Local Shadow.Shadow
	
	For Shadow = Each Shadow
		ClearGroup Shadow\castersGroupID
		ClearGroup Shadow\receiversGroupID
		If Shadow\texture<>0 Then
			SetBuffer TextureBuffer(Shadow\texture)
				Color 255, 255, 255
				Rect 0, 0, Shadow\texSize, Shadow\texSize,1
			SetBuffer BackBuffer()
			TextureBlend Shadow\texture, Shadow\texBlend And $FFFF
			;TextureIndex Shadow\texture, 0
			FreeTexture Shadow\texture
		EndIf
		Delete Shadow
	Next
	
	If ShadowLightDefault<>0 Then
		FreeEntity ShadowLightDefault
		ShadowLightDefault = 0
	EndIf
	If ShadowCameraDefault<>0 Then
		FreeEntity ShadowCameraDefault
		ShadowCameraDefault = 0
	EndIf

	ShadowIndexDefault = 0
	ShadowGroupIDCount = ShadowGroupID
	SetBuffer BackBuffer()
End Function


Function UpdateShadows% (camera%, tween#=1.0)
	Local j%, i.ShadowObject, fog%, blend%, t#, Shadow.Shadow
	Local RenderMask0%, RenderMask1%, RenderMask2%
	Local OverwriteFX0%, OverwriteFX1%
	Local attachedCount%
	Local oldBuffer% = 0
	Local tmpBuffer%
	Local tmpUpdate%
	
	If  ShadowCameraDefault<>0 Then
		OverwriteFX0 = GetOverwriteFX(0) : OverwriteFX1 = GetOverwriteFX(1)
		OverwriteFX 0, $FFFFFFF7
		fog = FogMode(1)
		
		For Shadow = Each Shadow
			If Shadow\enabled<>0 And Shadow\castersCount>0 And Shadow\texture<>0 And Shadow\light<>0 Then
				If Shadow\updateFirst=0 Then
					Shadow\updateFirst = 1
					tmpUpdate = 1
				Else
					tmpUpdate = Shadow\update
				EndIf
				If tmpUpdate<>0 Then
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
	
					TextureBlend Shadow\texture, Shadow\texBlend And $FFFF
	
					If Shadow\mode<>0 Then
						 tmpBuffer = SetBuffer (TextureBuffer(Shadow\texture))
					Else
						 tmpBuffer = SetBuffer (BackBuffer())
					EndIf
					If oldBuffer=0 Then oldBuffer=tmpBuffer
					
					CameraViewport ShadowCameraDefault, 0, 0, Shadow\texSize, Shadow\texSize
					ClsColor 255, 255, 255 : Cls 1,1
	
					If Shadow\receiversCount>0 Then
						;
						; render receivers
						;
						attachedCount = 0
						For j = 0 To Shadow\receiversCount-1
							i = Shadow\receivers[j]
							i\attached = 0
							If i\enabled<>0 Then
								If EntityInView( i\entity, ShadowCameraDefault )<>0 Or i\showAlways<>0 Then
									If i\showAuto<>0 Then ShowEntity i\entity
									i\autofade = EntityAutoFadeMode (i\entity, 0)
									i\attached = 1
									attachedCount = attachedCount + 1
								EndIf			
							EndIf
						Next
						If attachedCount>0 Then
							CameraFogColor ShadowCameraDefault, 255, 255, 255
							RenderGroup Shadow\receiversGroupID, ShadowCameraDefault, 0, tween
							For j = 0 To Shadow\receiversCount-1
								i = Shadow\receivers[j]
								If i\attached<>0 Then
									If i\showAuto<>0 Then HideEntity i\entity
									EntityAutoFadeMode i\entity, i\autofade
								EndIf
								i\attached = 0
							Next
						EndIf
					EndIf
	
					;
					; render casters
					;
					attachedCount = 0
					For j = 0 To Shadow\castersCount-1
						i = Shadow\casters[j]
						i\attached = 0
						If i\enabled<>0 Then
							If EntityInView( i\entity, ShadowCameraDefault )<>0 Or i\showAlways<>0 Then
								If i\showAuto<>0 Then ShowEntity i\entity
								i\autofade = EntityAutoFadeMode (i\entity, 0)
								i\attached = 1
								attachedCount = attachedCount + 1
							EndIf			
						EndIf
					Next	
	
					If attachedCount>0 Then
						CameraFogColor ShadowCameraDefault, Shadow\powerR * Shadow\power, Shadow\powerG * Shadow\power, Shadow\powerB * Shadow\power
						RenderGroup Shadow\castersGroupID, ShadowCameraDefault, 0, tween
						For j = 0 To Shadow\castersCount-1
							i = Shadow\casters[j]
							If i\attached<>0 Then
								If i\showAuto<>0 Then HideEntity i\entity
								EntityAutoFadeMode i\entity, i\autofade
							EndIf
							i\attached = 0
						Next
						
						SetViewMatrixCurrent Shadow\index
						CopyProjMatrix ShadowMatrix
							ShadowMatrix\m41 = ShadowMatrix\m41 + Shadow\offsetX
							ShadowMatrix\m42 = ShadowMatrix\m42 + Shadow\offsetY
							ShadowMatrix\m43 = ShadowMatrix\m43 + Shadow\offsetZ
						SetProjMatrix Shadow\index, ShadowMatrix
	
						If Shadow\blurAlpha>0 Then
							CustomPostprocessBlur Shadow\blurAlpha, Shadow\blurPasses, Shadow\blurRadius, Shadow\blurQuality
							;RenderPostprocess FE_Blur			
						EndIf
	
						Color 255,255,255
						Rect 0, 0, Shadow\texSize, Shadow\texSize, 0
						If Shadow\fadeTexture<>0
							CustomPostprocessOverlay 1, 0, 255, 255, 255, Shadow\fadeTexture
							;RenderPostprocess FE_Overlay
						EndIf
					EndIf
			
					; rect for debug
					;	Color 0,0,0
					;	Rect Shadow\texSize/4,Shadow\texSize/4,Shadow\texSize/2,Shadow\texSize/2,0
					;	Rect 1,1,Shadow\texSize-2,Shadow\texSize-2,0
	
					If Shadow\mode=0 Then CopyRect 0,0, Shadow\texSize,Shadow\texSize, 0,0, BackBuffer(), TextureBuffer(Shadow\texture)
					TextureBlend Shadow\texture, Shadow\texBlend
				EndIf
			Else
				If Shadow\texture<>0 Then
					oldBuffer = SetBuffer (TextureBuffer(Shadow\texture))
						Color 255, 255, 255
						Rect 0, 0, Shadow\texSize, Shadow\texSize,1
					SetBuffer BackBuffer()
				EndIf
			EndIf
		Next

		If oldBuffer<>0 Then SetBuffer oldBuffer
		FogMode fog
		OverwriteFX OverwriteFX0, OverwriteFX1
	EndIf	
End Function



Function ShadowLight% (ShadowIn.Shadow, light_entity%=-1, typ%=0, fov#=90.0)
	Local tmp%, i%, res%
	If light_entity=-1 Then
		Return ShadowIn\light
	Else
		tmp = ShadowIn\light
		If light_entity=0 Then light_entity = ShadowLightDefault
		ShadowIn\light = light_entity
		ShadowIn\lightType = typ
		ShadowIn\lightFov = fov
		Return tmp
	EndIf
End Function


Function ShadowOffset (ShadowIn.Shadow, x#=0, y#=0, z#=0)
	ShadowIn\offsetX = x
	ShadowIn\offsetY = y
	ShadowIn\offsetZ = z
End Function


Function ShadowFade% (ShadowIn.Shadow, fadeTexture%=-1)
	Local tmp%
	If fadeTexture=-1 Then
		Return ShadowIn\fadeTexture
	Else
		tmp = ShadowIn\fadeTexture
		ShadowIn\fadeTexture = fadeTexture
		Return tmp
	EndIf
End Function


Function ShadowColor# (ShadowIn.Shadow, r#=255, g#=255, b#=255)
	Local tmp#

	If r<0 Then r=0 : If r>255 Then r=255
	If g<0 Then g=0 : If g>255 Then g=255
	If b<0 Then b=0 : If b>255 Then b=255
	tmp = r : If g>tmp Then tmp=g : If b>tmp Then tmp=b
	
	If tmp=0 Then
		r = 255 : g = 255 : b = 255 : tmp = 255
	EndIf
	tmp = 255 / tmp

	ShadowIn\powerR = r * tmp
	ShadowIn\powerG = g * tmp
	ShadowIn\powerB = b * tmp
End Function


Function ShadowPower# (ShadowIn.Shadow, power#=-1)
	Local tmp#
	If power=-1 Then
		Return ShadowIn\power
	Else
		tmp = ShadowIn\power
		If power<0 Then power=0
		If power>1 Then power=1
		ShadowIn\power = 1 - power
		Return tmp
	EndIf
End Function


Function ShadowRange# (ShadowIn.Shadow, range#=-1)
	Local tmp#
	If range=-1 Then
		Return ShadowIn\range
	Else
		tmp = ShadowIn\range
		If range<1 Then range=1
		ShadowIn\range= range
		Return tmp
	EndIf
End Function


Function ShadowBlur% (ShadowIn.Shadow, alpha#=1.0, passes%=4, radius#=0.1, quality%=0)
	ShadowIn\blurAlpha = alpha
	ShadowIn\blurPasses = passes
	ShadowIn\blurRadius = radius
	ShadowIn\blurQuality = quality
End Function


Function ShadowTexture% (ShadowIn.Shadow)
	Return ShadowIn\texture
End Function


Function ShadowUpdate% (ShadowIn.Shadow, enable%=-1)
	Local tmp% = ShadowIn\update
	If enable<>-1 Then ShadowIn\update = enable
	Return tmp
End Function


Function CreateShadowCaster% (ShadowIn.Shadow, caster_entity%, showAuto%=0, showAlways%=0)
	Local i.ShadowObject, class$, j%
	If caster_entity<>0 Then
		class = EntityClass(caster_entity)
		If class<>"Light" And class<>"Camera" And class<>"Mirror" And class<>"Listener" Then
			j = FindShadowCaster(ShadowIn, caster_entity)
			If j=-1 And ShadowIn\castersCount<ShadowObjectsMax Then
				i = New ShadowObject
				i\showAuto = showAuto
				i\showAlways = showAlways
				i\entity = caster_entity
				i\enabled = 1
				GroupAttach ShadowIn\castersGroupID, caster_entity
				ShadowIn\casters[ ShadowIn\castersCount ] = i
				ShadowIn\castersCount = ShadowIn\castersCount + 1
				Return 1	
			EndIf
		EndIf		
	EndIf
	Return 0	
End Function


Function ShowShadowCaster% (ShadowIn.Shadow, caster_entity%)
	Local i.ShadowObject, j%, tmp%
	If caster_entity<>0 Then
		j = FindShadowCaster(ShadowIn, caster_entity)
		If j<>-1 Then
			i = ShadowIn\casters[j]
			tmp = i\enabled
			i\enabled = 1
			GroupChange ShadowIn\castersGroupID, i\entity
			Return tmp
		EndIf		
	EndIf
	Return 0
End Function


Function HideShadowCaster% (ShadowIn.Shadow, caster_entity%)
	Local i.ShadowObject, j%, tmp%
	If caster_entity<>0 Then
		j = FindShadowCaster(ShadowIn, caster_entity)
		If j<>-1 Then
			i = ShadowIn\casters[j]
			tmp = i\enabled
			i\enabled = 0
			GroupChange ShadowGroupHiddenID, i\entity
			Return tmp
		EndIf		
	EndIf
	Return 0
End Function


Function FreeShadowCaster% (ShadowIn.Shadow, caster_entity%)
	Local i.ShadowObject, j%
	If caster_entity<>0 Then
		j = FindShadowCaster(ShadowIn, caster_entity)
		If j<>-1 Then
			i = ShadowIn\casters[j]
			GroupDetach ShadowIn\castersGroupID, i\entity
			If j<>(ShadowIn\castersCount-1) Then ShadowIn\casters[j] = ShadowIn\casters[ ShadowIn\castersCount-1 ]
			ShadowIn\castersCount = ShadowIn\castersCount-1
			Delete i
			Return 1	
		EndIf		
	EndIf
	Return 0
End Function


Function FindShadowCaster% (ShadowIn.Shadow, caster_entity%)
	Local i.ShadowObject, j%
	Local result% = -1
	If ShadowIn\castersCount>0
		For j = 0 To ShadowIn\castersCount-1
			i = ShadowIn\casters[j]
			If i\entity=caster_entity Then
				result = j
				Exit
			EndIf
		Next	
	EndIf
	Return result
End Function


Function AttachShadowReceiver% (ShadowIn.Shadow, receiver_entity%, showAuto%=0, showAlways%=0)
	Local i.ShadowObject, class$, j%
	If receiver_entity<>0 Then
		class = EntityClass(receiver_entity)
		If class<>"Light" And class<>"Camera" And class<>"Mirror" And class<>"Listener" Then
			j = FindShadowReceiver(ShadowIn, receiver_entity)
			If j=-1 And ShadowIn\receiversCount<ShadowObjectsMax Then
				i = New ShadowObject
				i\showAuto = showAuto
				i\showAlways = showAlways
				i\entity = receiver_entity
				i\enabled = 1
				GroupAttach ShadowIn\receiversGroupID, receiver_entity
				ShadowIn\receivers[ ShadowIn\receiversCount ] = i
				ShadowIn\receiversCount = ShadowIn\receiversCount + 1
				Return 1	
			EndIf
		EndIf		
	EndIf
	Return 0	
End Function


Function ShowShadowReceiver% (ShadowIn.Shadow, receiver_entity%)
	Local i.ShadowObject, j%, tmp%
	If receiver_entity<>0 Then
		j = FindShadowReceiver(ShadowIn, receiver_entity)
		If j<>-1 Then
			i = ShadowIn\receivers[j]
			tmp = i\enabled
			i\enabled = 1
			GroupChange ShadowIn\receiversGroupID, i\entity
			Return tmp
		EndIf		
	EndIf
	Return 0
End Function


Function HideShadowReceiver% (ShadowIn.Shadow, receiver_entity%)
	Local i.ShadowObject, j%, tmp%
	If receiver_entity<>0 Then
		j = FindShadowReceiver(ShadowIn, receiver_entity)
		If j<>-1 Then
			i = ShadowIn\receivers[j]
			tmp = i\enabled
			i\enabled = 0
			GroupChange ShadowGroupHiddenID, i\entity
			Return tmp
		EndIf		
	EndIf
	Return 0
End Function


Function FreeShadowReceiver% (ShadowIn.Shadow, receiver_entity%)
	Local i.ShadowObject, j%
	If receiver_entity<>0 Then
		j = FindShadowReceiver(ShadowIn, receiver_entity)
		If j<>-1 Then
			i = ShadowIn\receivers[j]
			GroupDetach ShadowIn\receiversGroupID, i\entity
			If j<>(ShadowIn\receiversCount-1) Then ShadowIn\receivers[j] = ShadowIn\receivers[ ShadowIn\receiversCount-1 ]
			ShadowIn\receiversCount = ShadowIn\receiversCount-1
			Delete i
			Return 1	
		EndIf		
	EndIf
	Return 0
End Function


Function FindShadowReceiver% (ShadowIn.Shadow, receiver_entity%)
	Local i.ShadowObject, j%
	Local result% = -1
	If ShadowIn\receiversCount>0
		For j = 0 To ShadowIn\receiversCount-1
			i = ShadowIn\receivers[j]
			If i\entity=receiver_entity Then
				result = j
				Exit
			EndIf
		Next	
	EndIf
	Return result
End Function