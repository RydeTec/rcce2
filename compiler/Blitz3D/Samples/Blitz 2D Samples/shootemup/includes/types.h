; Notes: 
; This looks more complex than it actually is, think of it as just one
; huge database.

; map
Type map_inf
	Field name$			; name of map
	Field map_version	; version of map editor it was written with
	Field map_width		; map width in tiles
	Field map_height	; map height in tiles
	Field screens_x		;
	Field screens_y		;
	Field tilesizex		;
	Field tilesizey		;
	Field layers		; number of layers
	Field mapoffset		; direct map offset position
	Field mapsize		; 
End Type

; path
Type path_inf
	Field name$			; name of path
	Field xpos			; start position
	Field ypos			; start position
	Field memptr		;*memory pointer to path data	
	Field dataoffset	; direct offset to the path data
	Field steps			; number of samples of input taken
						; this tells us how far we can count up to with the counter
						; before zeroing the counter back to the start of the path.
	Field spacer		; space between each object
End Type

; enermy
Type nme_inf
	Field name$			; name of alien
	Field filename$		; Filename
	Field style			; 1 = ground , 2 = sky
	Field moveable      ; 1 = moves on path , 2 = moves down with map speed at x
	Field canshot		; 1 = can fire at player , else 0
	Field shottype		; 1 = any direction, 2 homing ( more to be added here )
	Field energypershot ; how much energy it's shot subtracks from players strength.
	Field x				; location of this image x
	Field y				; location of this image y
	Field path_location ; stores orginal counter
	Field path_lcounter	; location counter +speed
	Field damage		; amount of hits before destoryed
	Field dead			; 1 = dead, 0 alive
	Field image			; pointer to the loadimage memory allocation
	Field frames		; number of anim frames
End Type

; play offence
Type ply_gun
	Field backward_shot
	Field side_shot
	Field twoway_shot
End Type

Global plygun.ply_gun=New ply_gun
Global mapinfo.map_inf=New map_inf
Global pathinfo.path_inf=New path_inf
Global nmeinfo.nme_inf=New nme_inf

;Global nmeinfo.nme_inf
Dim mneinfo_layer0.nme_inf(255)

For mneincn=0 To 255
	mneinfo_layer0.nme_inf(mneincn)=New nme_inf
Next
 