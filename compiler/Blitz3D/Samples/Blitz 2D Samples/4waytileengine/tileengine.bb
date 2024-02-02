; ** WARNING:  I did this engine in 800x600 **
; ** The code is set up to change this but you must have even numbered **
; ** demonsions of tiles **
; ** 640x480  32x32 tile would fit evenly **
; ** 800x600 32x32 would not fit evenly, I use 40x40 tiles for this res **
; ** Make sure you understand this carefully :-) **

; ** this is a summary of scancodes put into variables **
; ** just load it and you will see **
; ** you need this include file for engine to work **
; ** No biggy, if you dont want to use it, you can change the keys **
; ** theirs only up, down, left, right, and esc keys ** 
Include "scancodes.bbh"
; ** Set up Screen video mode variables **
Const SCREEN_WIDTH=800
Const SCREEN_HEIGHT=600
Const COLOR_DEPTH=16
; ** I set this as, a full screen of tiles **
; ** at 800x600, 40x40 tiles, theirs 20 on x axes, 15 on y **
; ** theirfore, 20*4 would be 4 screens on x axes, left, right **
; ** 15*4 would be 4 screens on y axes, up, down ""
; ** Total screens is 16 btw **
; ** I don't think anyone would make a tile map with half a screen? ** :)
; ** If so Im sorry, this was the easiest way to code this **
; ** The number of screens(screens is 1 visible screen) on X & Y axes **
NUM_X_TILE_SCREENS=4
NUM_Y_TILE_SCREENS=4

; ** Each tiles width and height **
TILE_WIDTH=40
TILE_HEIGHT=40
; ** The number of Visible Screen Tiles on X & Y axes
; ** assuming 800x600 screen **
; *** x-20  y-15 ***
NUM_X_TILES=SCREEN_WIDTH / TILE_WIDTH
NUM_Y_TILES=SCREEN_HEIGHT / TILE_HEIGHT

; ** The total number of tiles on X & Y axes **
TOTAL_NUM_X_TILES= NUM_X_TILES * NUM_X_TILE_SCREENS
TOTAL_NUM_Y_TILES= NUM_Y_TILES * NUM_Y_TILE_SCREENS

; ******************************   TURN GRAPHICS MODE ON   *****************************
Graphics SCREEN_WIDTH,SCREEN_HEIGHT,COLOR_DEPTH
SetBuffer BackBuffer()

; ** Load the tileset **
; ** this is the file i use to load it **
; ** note:  this file was test, it's screwy, but it works **
; ** I have 7 total images in the file, or the way this engine works:
; ** 0 is a blank tile 40x40, and 1-6 is the actual tiles **
; ** '0' in the function, is the starting tile **
TILE_IMAGE=LoadAnimImage("tiles.bmp",TILE_WIDTH,TILE_HEIGHT,0,7)
; ** Map Speed  NOTE:  Use numbers that 40, can divide evenly: 1,2,4,8,10,20,etc **
; ** If you don't it will be choppy. remember, 40 is the tile width & height **
; ** In this example **
MapSpeed=4
; ** the dat file of information on what number image to display **
; ** I use a small data convertor program i made to convert numbers **
; ** into a dat file that I use to load for placement of tiles ** 
; ** the dat file I get my numbers from **
MapDataFile$="test1.dat" 

; ** This is part of the tile scrolling engine, must be dimensioned with same name **

; ** This function is used inside of the 'tile_engine' function **
; ** Add 1 to Y for extra line in x & y axes for scroll engine **
Dim ArrayGrid(TOTAL_NUM_X_TILES+1,TOTAL_NUM_Y_TILES+1)

; ** Grab data from dat file **
; ** this is my way of grabbing map data **
	file=ReadFile(MapDataFile$)
	For y=1 To TOTAL_NUM_Y_TILES; ** Each tiles width and height **
		For x=1 To TOTAL_NUM_X_TILES
		ArrayGrid(x,y)=ReadLine(file)
		Next
	Next
	CloseFile file

; ** This is a must if the engine is going to work correctly **
; ** Their must be an outer layer in the array of blank tiles **
; ** The engine draws off the screen 1 tile, in 4 directions **
; ** If your at the bottom, then you need an extra blank line **
; ** So you can see the true bottom line of the array **
; ** Works for x & y **
; ** I dont use the x's '0' & the y's '0' coordinates in the array **
; ** mainly cause the way tile studio does things, and I dont like using '0' **
; ** the last array #, is plused by 1, even in the 'DIM' of the array above **
For x=0 To TOTAL_NUM_X_TILES+1
	ArrayGrid(x,0)=0
	ArrayGrid(x,TOTAL_NUM_Y_TILES+1)=0
Next
For y=0 To TOTAL_NUM_Y_TILES+1
	ArrayGrid(0,y)=0
	ArrayGrid(TOTAL_NUM_X_TILES+1,y)=0
Next
	
; minus the number of y tiles, keep from going out of array bounds **
; ** Start Screen at bottom of array **
; ** the -1 is for the extra line drawn off bottom of screen **
; ** the y axes scroll value for the shooter, the map scrolls down ** 
SCROLL_X_PIXEL=0
SCROLL_Y_PIXEL=0

; *** starting place in the array ***
; ** This position is at the bottom left corner of array **
POS_Y_ARRAY=(TOTAL_NUM_Y_TILES - NUM_Y_TILES)
POS_X_ARRAY=0

; **********************************   M A I N   L O O P   ************************************

While KeyDown(ESC_KEY)=0
	Cls

;	Old position of tile_engine function

; ***************** SCROLLING UP OR DOWN *********************

; ** Scroll Map 'Down' to go 'Up' **
		If KeyDown(UP_KEY)=1
			Speed=MapSpeed
			SCROLL_Y_PIXEL=SCROLL_Y_PIXEL+Speed
			If SCROLL_Y_PIXEL>=TILE_HEIGHT
				SCROLL_Y_PIXEL=0  
				POS_Y_ARRAY=POS_Y_ARRAY-1 
			End If 
		End If 
; ** Scroll Map 'Up' to go 'Down' **
		If KeyDown(DOWN_KEY)=1
			Speed=-MapSpeed
			SCROLL_Y_PIXEL=SCROLL_Y_PIXEL+Speed
			If SCROLL_Y_PIXEL<=0
				SCROLL_Y_PIXEL=TILE_HEIGHT
				POS_Y_ARRAY=POS_Y_ARRAY+1
			End If 
		End If 

; ***************** SCROLLING LEFT OR RIGHT *******************

; ** Scroll Map 'Right' to go 'Left' **
		If KeyDown(LEFT_KEY)=1
			Speed=MapSpeed
			SCROLL_X_PIXEL=SCROLL_X_PIXEL+Speed
			If SCROLL_X_PIXEL>=TILE_WIDTH
				SCROLL_X_PIXEL=0  
				POS_X_ARRAY=POS_X_ARRAY-1 
			End If 
		End If 
; ** Scroll Map 'Left' to go 'Right' **
		If KeyDown(RIGHT_KEY)=1
			Speed=-MapSpeed
			SCROLL_X_PIXEL=SCROLL_X_PIXEL+Speed
			If SCROLL_X_PIXEL<=0
				SCROLL_X_PIXEL=TILE_WIDTH
				POS_X_ARRAY=POS_X_ARRAY+1
			End If 
		End If 

; ***********************  STAY WITHIN ARRAY and DRAWING LIMITS  **************************

; *** Beginning X coordinates ***
		If POS_X_ARRAY<1
			POS_X_ARRAY=1
			SCROLL_X_PIXEL=TILE_WIDTH
		End If  
; *** Ending X coordinates ***
		If POS_X_ARRAY>(TOTAL_NUM_X_TILES-NUM_X_TILES)
			POS_X_ARRAY=(TOTAL_NUM_X_TILES-NUM_X_TILES)
			SCROLL_X_PIXEL=0
		End If 

; *** Beginning Y coordinates ***
		If POS_Y_ARRAY<1
			POS_Y_ARRAY=1
			SCROLL_Y_PIXEL=TILE_HEIGHT
		End If  
; *** Ending Y coordinates ***
		If POS_Y_ARRAY>(TOTAL_NUM_Y_TILES-NUM_Y_TILES)
			POS_Y_ARRAY=(TOTAL_NUM_Y_TILES-NUM_Y_TILES)
			SCROLL_Y_PIXEL=0
		End If 
	Tile_Engine(TILE_IMAGE,TILE_WIDTH,TILE_HEIGHT,NUM_X_TILES,NUM_Y_TILES,SCROLL_X_PIXEL,SCROLL_Y_PIXEL,POS_X_ARRAY,POS_Y_ARRAY)

	Flip
Wend


; *******************************  SCROLLING ENGINE FUNCTION  *********************************
; *** Basically, this engine has a fault, you have to input a blank array, or put ****
; *** A blank tile as the first one, which actually helps code this engine better ***
; *** This engine draws 1 tile off the screen in all directions, left,right,up,down ***
; *** That way, when your going up, and want to go down, the tile will already be their ***
; *** It helps to cut a lot more unneccessary coding, but downfall, a blank tile in anim. ***
; *** And thats about it ***

Function Tile_Engine(TILE_IMAGE,TILE_WIDTH,TILE_HEIGHT,NUM_X_TILES,NUM_Y_TILES,SCROLL_X_PIXEL,SCROLL_Y_PIXEL,POS_X_ARRAY,POS_Y_ARRAY)

Local steptileX,steptileY,steparrayX,steparrayY
	steptileX=-2
	steptileY=-2
	steparrayX=-1
	steparrayY=-1
; ** Ok, this is an odd way of looping, remake the way you like **

; ** Loops 17 times gets y0 and lasty+1 coordinates
	While steptileY<(NUM_Y_TILES)
		steptileY=steptileY+1
		steparrayY=steparrayY+1
; ** Loops 22 times ** gets x0 and lastx+1 coordinates
		While steptileX<(NUM_X_TILES)
			steptileX=steptileX+1
			steparrayX=steparrayX+1
			DrawImage TILE_IMAGE,(steptileX*TILE_WIDTH)+SCROLL_X_PIXEL,(steptileY*TILE_HEIGHT)+SCROLL_Y_PIXEL,ArrayGrid(steparrayX+POS_X_ARRAY,steparrayY+POS_Y_ARRAY)
		Wend
		steparrayX=-1
		steptileX=-2
	Wend

End Function