;
; Cloud Effect Subject To Removal due to lack of functional ALPHA/Tranparency in
; BB2D. may prohibit/hide it under a keycode.
;

Global depth=8,mve

Dim clouds(depth),clouds_speed(depth),clouds_pos(depth),clouds_size(depth) 

;
; Cloud FX
;
Function cloudfx_bottomlayer()

	For cn=0 To depth/2
		clouds_pos(cn)=clouds_pos(cn)+clouds_speed(cn)
		DrawImage cloud_fximg,clouds(cn)+offsetmapx/2,-128+clouds_pos(cn),clouds_size(cn)
		If clouds_pos(cn)>700
			clouds_pos(cn)=0
			clouds_speed(cn)=Rnd(3,7)
			clouds(cn)=Rnd(-176,816)
			clouds_size(cn)=Rnd(0,4)
		EndIf
	Next

End Function

Function cloudfx_toplayer()

	For cn=depth/2+1 To depth
		clouds_pos(cn)=clouds_pos(cn)+clouds_speed(cn)
		DrawImage cloud_fximg,clouds(cn)+offsetmapx/2,-128+clouds_pos(cn),clouds_size(cn)
		If clouds_pos(cn)>700
			clouds_pos(cn)=0
			clouds_speed(cn)=Rnd(3,7)
			clouds(cn)=Rnd(-176,816)
			clouds_size(cn)=Rnd(0,4)
		EndIf
	Next

End Function


;
;init cloud
;
Function init_clouds()
	For cn=0 To depth
		clouds_speed(cn)=Rnd(3,7)
		clouds(cn)=Rnd(-176,816)
		clouds_size(cn)=Rnd(0,4)
	Next
End Function