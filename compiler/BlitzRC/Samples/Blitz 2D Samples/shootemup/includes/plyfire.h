;
; An entire fucking fire engine just for firing fuck it all
;


;Graphics 640,480

;Global plyfire=LoadAnimImage("sprites/player/std_fire.bmp",16,16,0,7)

Global ply_bullets=50
Global plyshot_speed=10
Global autofire_speed=6

Global ply_blcn=0
;Global player_x=320
;Global player_y=220

Dim fwd_plybullet(128,2) 
Dim bkd_plybullet(128,2) 
Dim ltp_plybullet(128,2) 
Dim rtp_plybullet(128,2) 
Dim lsd_plybullet(128,2) 
Dim rsd_plybullet(128,2)

;While Not KeyDown(1)
;	
;	If KeyHit(157)
;		shoot_bullets()
;	EndIf
;	; autofire
;	auto=auto+1
;	If(auto Mod autofire_speed)=0
;		shoot_bullets()
;	EndIf
;	
;	update_bullets()
;	Flip :  Cls : VWait
;
;Wend

Function shoot_bullets()

	ply_blcn=ply_blcn+1
	If ply_blcn>ply_bullets-1 Then ply_blcn=1
	fwd_plybullet(ply_blcn,1) = ply_x+22
	fwd_plybullet(ply_blcn,2) = ply_y

	bkd_plybullet(ply_blcn,1) = ply_x+22
	bkd_plybullet(ply_blcn,2) = ply_y+48

	lsd_plybullet(ply_blcn,1) = ply_x+20
	lsd_plybullet(ply_blcn,2) = ply_y+20

	rsd_plybullet(ply_blcn,1) = ply_x+26
	rsd_plybullet(ply_blcn,2) = ply_y+20

	ltp_plybullet(ply_blcn,1) = ply_x+20
	ltp_plybullet(ply_blcn,2) = ply_y+8

	rtp_plybullet(ply_blcn,1) = ply_x+24
	rtp_plybullet(ply_blcn,2) = ply_y+8


End Function

Function update_bullets()
	If ply_blcn>0

		For bul_cn=0 To ply_blcn
			fwd_plybullet(bul_cn,2) = fwd_plybullet(bul_cn,2) - plyshot_speed
			If fwd_plybullet(bul_cn,2)>0
				DrawImage plyfire,fwd_plybullet(bul_cn,1),fwd_plybullet(bul_cn,2),0	
			EndIf 
		Next

		If plygun\backward_shot=1
			For bul_cn=0 To ply_blcn
				bkd_plybullet(bul_cn,2) = bkd_plybullet(bul_cn,2) + plyshot_speed
				If bkd_plybullet(bul_cn,2)>0
					DrawImage plyfire,bkd_plybullet(bul_cn,1),bkd_plybullet(bul_cn,2),1	
				EndIf 
			Next
		EndIf

		If plygun\side_shot=1
			For bul_cn=0 To ply_blcn
				lsd_plybullet(bul_cn,1) = lsd_plybullet(bul_cn,1) - plyshot_speed
				rsd_plybullet(bul_cn,1) = rsd_plybullet(bul_cn,1) + plyshot_speed
				If lsd_plybullet(bul_cn,2)>0			
					DrawImage plyfire,lsd_plybullet(bul_cn,1),lsd_plybullet(bul_cn,2),3	
					DrawImage plyfire,rsd_plybullet(bul_cn,1),rsd_plybullet(bul_cn,2),4	
				EndIf 
			Next
		EndIf

		If plygun\twoway_shot=1
			For bul_cn=0 To ply_blcn
				ltp_plybullet(bul_cn,1) = ltp_plybullet(bul_cn,1) - plyshot_speed
				ltp_plybullet(bul_cn,2) = ltp_plybullet(bul_cn,2) - plyshot_speed
				rtp_plybullet(bul_cn,1) = rtp_plybullet(bul_cn,1) + plyshot_speed
				rtp_plybullet(bul_cn,2) = rtp_plybullet(bul_cn,2) - plyshot_speed

				If lsd_plybullet(bul_cn,2)>0
					DrawImage plyfire,ltp_plybullet(bul_cn,1),ltp_plybullet(bul_cn,2),5	
					DrawImage plyfire,rtp_plybullet(bul_cn,1),rtp_plybullet(bul_cn,2),6	
				EndIf
			Next 
		EndIf
	EndIf

End Function