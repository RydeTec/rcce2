; verified 1.48 4/18/2001
	
Const width=640,height=480
Const map_width=96,map_height=48

Global tiles
Dim map(map_width,map_height)

Graphics 640,480
SetBuffer BackBuffer()

CreateTiles()
CreateMap()
stars=LoadImage( "graphics/stars.bmp" )
max_x=map_width*32-width
max_y=map_height*32-height

While Not KeyDown(1)

	If KeyDown( 203 )
		scroll_x=scroll_x-8:If scroll_x<0 Then scroll_x=0
	Else If KeyDown( 205 )
		scroll_x=scroll_x+8:If scroll_x>max_x Then scroll_x=max_x
	EndIf
	If KeyDown( 200 )
		scroll_y=scroll_y-8:If scroll_y<0 Then scroll_y=0
	Else If KeyDown( 208 )
		scroll_y=scroll_y+8:If scroll_y>max_y Then scroll_y=max_y
	EndIf
	
	Cls
	TileBlock stars,-scroll_x/2,-scroll_y/2
	RenderMap( scroll_x,scroll_y )
	Flip
Wend

End

Function RenderMap( x_offset,y_offset )
	ty=y_offset/32
	sy=-(y_offset Mod 32)
	While sy<height
		ty=ty Mod map_height
		tx=x_offset/32
		sx=-(x_offset Mod 32)
		While sx<width
			DrawImage tiles,sx,sy,map( tx Mod map_width,ty )
			tx=tx+1:sx=sx+32
		Wend
		ty=ty+1:sy=sy+32
	Wend
End Function

Function CreateMap()
	Color 255,255,255
	Rect 0,0,map_width,map_height
	Color 0,0,0
	Rect 1,1,map_width-2,map_height-2
	Color 255,255,255
	Text map_width/2,map_height/4,"YEEEHHHAAA!",1,1
	Text map_width/2,map_height/2,"ALLRIGHTY!",1,1
	Text map_width/2,map_height*3/4,"TILEMAPDEMO",1,1
	For x=0 To map_width-1
		For y=0 To map_height-1
			GetColor x,y
			If ColorRed()>0 Then map(x,y)=0 Else map(x,y)=1
		Next
	Next
End Function

Function CreateTiles()
	tiles=CreateImage( 32,32,2 )
	
	Color 128,128,128
	Rect 0,0,32,32
	Color 192,192,192
	Rect 0,0,31,1:Rect 0,0,1,31
	Color 32,32,32
	Rect 31,1,1,31:Rect 1,31,31,1
	
	GrabImage tiles,0,0,0
	
	Color 0,0,0
	Rect 0,0,32,32
	For k=7 To 31 Step 16
		Color 192,192,192
		Rect k,0,1,32
		Color 32,32,32
		Rect k+1,0,1,32
	Next
	For k=7 To 31 Step 16
		Color 192,192,192
		Rect 0,k,32,1
		Color 32,32,32
		Rect 0,k+1,32,1
	Next
	
	GrabImage tiles,0,0,1
	
End Function