AppTitle "ENGINE v0.3 (PRESS MOUSE BUTTON TO EXIT POST-DEBUG INFO)"

Include "includes/math.h"
Include "includes/cloudfx.h"
Include "includes/types.h"
Include "includes/script.h"
Include "includes/loader.h"

Include "includes/plyfire.h"
Include "includes/points.h"

Include "levels/level1.bb"

Graphics 640,480
SetBuffer BackBuffer()

Dim mapxy_data(32000)

;player settings
Global ply_speed=4
Global ply_y=390
Global ply_x=288
Global ply_roll
Global ply_rollspeed=2
Global ply_points=0
Global ply_lives=3

;envorment settings
Global landspeed=1
Global scrollmapy=0
Global linestepper=0
Global offsetmapx=-192/2
Global mapend

;path
Global tstoffset
Global axp
Global ayp

;temporary alien veriables
Global layer0_aliens=14
Global layer0_aliens_speed=2
Global layer0_aliens_counter
Global layer0_aliens_space=128
Global fooboo

;loaded graphical data
Global tileset=LoadAnimImage("sprites/maptiles/sand.bmp",64,64,0,44)
Global plyset=LoadAnimImage("sprites/player/plyshp01a.bmp",64,64,0,15)
Global cloud_fximg=LoadAnimImage("sprites/fx/clouds.bmp",132,133,0,5)
Global plyfire=LoadAnimImage("sprites/player/std_fire.bmp",16,16,0,7)
Global hudimg=LoadImage("sprites/menu/hud.bmp")
Global fontnumbers=LoadAnimImage("sprites/fonts/numbers_ingame.bmp",8,7,0,10)

;buffers
Global hudbufferimg=CreateImage(ImageWidth(hudimg),ImageHeight(hudimg)) 

;masks
MaskImage hudbufferimg,255,0,255
MaskImage hudimg,255,0,255 ; kludge



temp()
loadenermy()
loadpath("path/sqr.arp")
init_clouds()
init_aliens()

Global al_image=findnme("AverBio") ; init the first alien (testing)

While Not KeyDown(1)

	scrollmap()
	cloudfx_bottomlayer()	
	plyctrl()
	alnctrl()	
	DrawImage plyset,ply_x,ply_y,6+ply_roll
	update_bullets()
	offsetmapx=-ply_x/4-96/2
	cloudfx_toplayer()
	For cnx=0 To ply_bullets
		For alcn=0 To layer0_aliens
			If mneinfo_layer0(alcn)\dead=0
				al_hit=ImagesOverlap(al_image,mneinfo_layer0(alcn)\x,mneinfo_layer0(alcn)\y,plyfire,fwd_plybullet(cnx,1),fwd_plybullet(cnx,2))
				If al_hit=1 Then mneinfo_layer0(alcn)\dead=1 : addpoints(100)
			EndIf
		Next
	Next

	DrawImage hudimg,0,456
	
	;If MouseHit(1)

		drawpoints(76,465,ply_points)
		drawpoints(273,465,ply_lives)
	;EndIf

	Flip

	; SNAPSHOT PLEASE
	If KeyDown(88)
		SaveBuffer(FrontBuffer(),"snapshot.bmp")
	EndIf

	Cls : VWait
Wend

End

;
; Alien Control
;
Function alnctrl()

For alcn=0 To layer0_aliens
	If mneinfo_layer0(alcn)\path_lcounter=>pathinfo\steps-4
		mneinfo_layer0(alcn)\path_lcounter=0
	EndIf
	getpath(mneinfo_layer0(alcn)\path_lcounter)

	mneinfo_layer0(alcn)\x=axp
	mneinfo_layer0(alcn)\y=ayp
	mneinfo_layer0(alcn)\path_lcounter=mneinfo_layer0(alcn)\path_lcounter+4
	If mneinfo_layer0(alcn)\dead=0
		DrawImage al_image,mneinfo_layer0(alcn)\x,mneinfo_layer0(alcn)\y,fooboo
	EndIf
;	Text 0,0,"("+axp+")("+ayp+")"+(fooboo Mod layer0_aliens_space)
Next 
	fooboo=fooboo+1 
	If fooboo>18 Then fooboo=0

End Function

Function init_aliens()

	For alcn=0 To layer0_aliens
		mneinfo_layer0(alcn)\path_lcounter=mneinfo_layer0(alcn)\path_lcounter+(layer0_aliens_space*alcn)
	Next

End Function 
;
; Collisions
;

;
; Find An Alien
;
Function findnme(askname$)

	While askname$<>nmeinfo\name$
		nmeinfo=After nmeinfo
		Print nmeinfo\name$
	Wend

	Return nmeinfo\image
End Function

;
; Get Path X,Y
;
Function getpath(offset)
	;Print offset
	axp=PeekShort(pathinfo\memptr,  pathinfo\dataoffset+offset)
 	ayp=PeekShort(pathinfo\memptr,2+pathinfo\dataoffset+offset)
	;Print axp+" "+pathinfo\dataoffset+" "+offset+" "+pathinfo\steps
End Function

;
; Player Control 
;
Function plyctrl()

	not_roll=False
	; left
	If KeyDown(203)
		ply_x=ply_x-ply_speed
		ply_roll=ply_roll-ply_rollspeed
		ply_x=Min(ply_x,2)
		not_roll=True
	EndIf
	; right
	If KeyDown(205)
	    ply_x=ply_x+ply_speed
		ply_roll=ply_roll+ply_rollspeed
		ply_x=Max(ply_x,574)
		not_roll=True
	EndIf
	; up
	If KeyDown(200)
		ply_y=ply_y-ply_speed
		ply_y=Min(ply_y,0)
	EndIf
	; down
	If KeyDown(208)
		ply_y=ply_y+ply_speed
		ply_y=Max(ply_y,400)
	EndIf
	; fire
	If KeyHit(157)
		shoot_bullets()
	EndIf
		
	; roll back
	If not_roll=False
		If ply_roll>0 Then ply_roll=ply_roll-ply_rollspeed
		If ply_roll<0 Then ply_roll=ply_roll+ply_rollspeed
	EndIf

	ply_roll=Min(ply_roll,-5)
	ply_roll=Max(ply_roll,5)		

End Function

;
; Scroll Map
;
Function scrollmap()

	x=832 : y=430

 	mapstart=linestepper+mapend-125
	
	For cntiles=mapend+linestepper To mapstart Step -1
		tile=mapxy_data(cntiles)
		
		DrawImage tileset,x+offsetmapx,y+scrollmapy,tile

		x=x-64
		If x<0 
			x=832 : y=y-64
		EndIf
	Next
	
	scrollmapy=scrollmapy+landspeed
	If scrollmapy>64
		scrollmapy=0
		linestepper=linestepper-14
	EndIf
	
	
End Function


Function temp()

	Repeat
		Read x : If x=-1 Then Return 		
		mapxy_data(cn)=x-1
		cn=cn+1 : mapend=cn
	Until x=-1

End Function