; 3d Maze Demo...
; Blitztastic 2001
; Contact
; blitztastic@bigfoot.com
; www.bigfoot.com/~blitztastic

Graphics3D 640,480

; create a type to hold all the sparks used for the path
Type path
	Field x,y,spark
End Type 

; bring in my maze routine (full version available at my web site)
Include "mazelib.bb"

; maze width and height
Global mwidth=10
Global mheight=10

map=CreateImage(mwidth*16,mheight*16)

; initialise the maze library
maze_startup("mazeblocks.png",16,16)

; generate a new maze
m.maze = maze_create(mwidth,mheight,MilliSecs(),0)

; and generate a solution for it
maze_solve(m,0,mheight-1,mwidth-1,0)



;create cloud planes (ripped straight out of Marks examples)
tex=LoadTexture( "cloud_2.bmp",3 )
ScaleTexture tex,100,100
p=CreatePlane()
EntityTexture p,tex
EntityFX p,1
PositionEntity p,0,8,0
p=CopyEntity( p )
RotateEntity p,0,0,180


x#=0
y#=0
z#=0

; use double buffering
SetBuffer BackBuffer()

; create a spark for the start of the maze, red (ripped from the power fountain demo)
spark=LoadSprite("spark.bmp",1)  
ScaleSprite spark,.7,.7			 
If spark=0 Then End				 
PositionEntity spark,1,0.2,1
EntityColor spark,255,0,0


; create a spark for the end of the maze, green
espark = CopyEntity(spark)
EntityColor espark,0,255,0
PositionEntity espark,(mwidth*2)-1,0.2,(mheight*2)-1

; create a base spark to use when drawing the path hint, blue
rspark = CopyEntity(spark)
EntityColor rspark,0,0,255
PositionEntity rspark,1,0.1,1
HideEntity rspark


; let there be light!
light=CreateLight()
RotateEntity light,90,0,0

; something to hold textures, yeah I want more than one
Dim walltex(10)

walltex(0)=LoadTexture("wall1.png")
walltex(1)=LoadTexture("floor1.png")
;ScaleTexture walltex(1),.01,.01

; create a shiny brush to paint the walls
brush = CreateBrush()
BrushTexture brush,walltex(0)
BrushShininess brush,1

; and another for the floor.
brush2 = CreateBrush()
BrushTexture brush2,walltex(1)


; this creates a wall block, at position x,y the offset is a position inside a 3x3 square
; ie.... each maze cell is 3x3 squares	X1X
; 										2 3
;										X4X
; the blocks at positions 1,2,3 and 4 are not created if that way is open...
; this is a pretty poor way of doing it as there will be a lot of hidden faces around which could slow
; down the render, I don't know if this is true, but there are many better ways, I'll probably experiment
; in the next version
Function CreateWall(x,y,ox,oy,brush)

	cube=CreateCube()
	EntityType cube,2
	ScaleEntity cube,0.5,0.5,0.5
	PositionEntity cube,(x*2+1)+ox,0.5,(mheight*2)-((y*2+1)+oy)
	PaintEntity cube,brush

End Function


; create the floor plane
plane=CreatePlane(2)
PaintEntity plane,brush2

; now lets step through all the cells
For mx= 0 To mwidth-1
	For my = 0 To mheight-1
	
		; create the corner blocks
		createwall(mx,my,-1,-1,brush)
		createwall(mx,my,1,1,brush)
		createwall(mx,my,-1,1,brush)
		createwall(mx,my,1,-1,brush)
	
	
		; if there is a path through this cell, create a guide spark
		If (maze_getcell(m,mx,my) And 240) >0 Then
			pq.path = New path
			pq\x=mx
			pq\y=my	
			pq\spark = CopyEntity(rspark)
			HideEntity pq\spark
			PositionEntity pq\spark,(mx*2)+1,0.1,(mheight*2)-((my*2)+1)
		End If
	
		
		; create blocks to close off any exits which should be closed
		If my =0 And (Not maze_northopen(m,mx,my)) Then 
			createwall(mx,my,0,-1,brush)
		End If
		
		If Not maze_southopen(m,mx,my) Then 
			createwall(mx,my,0,1,brush)
		End If
		
		If  Not maze_eastopen(m,mx,my) Then 
			createwall(mx,my,1,0,brush)
		End If
		
		If mx=0 And (Not maze_westopen(m,mx,my)) Then 
			createwall(mx,my,-1,0,brush)
		End If
	Next
Next


; define starting position
x#=1
z#=1
y#=0.5

; create a player to use in the maze
player = CreateSphere()
ScaleEntity player,0.25,0.25,0.25

PositionEntity player,x,y,z
EntityType player,1
EntityRadius player,0.35

; and slap a camera onto it.
camera=CreateCamera(player)
PositionEntity camera,0,0,-0.5


; create a texture to draw the map overview onto
maptex = CreateTexture(mwidth*16,mheight*16)

; and a funky semi-transparent brush to apply it with
brush3=CreateBrush()
BrushTexture brush3,maptex
ScaleTexture maptex,1.6,1.6
BrushAlpha brush3,0.75

; and create a box to show it on.
mapcube = CreateCube(player)
PaintMesh mapcube,brush3
ScaleMesh mapcube,0.25,0.25,0.0
PositionEntity mapcube,-.85,.65,0.85
EntityFX mapcube,1

; stop walking through walls
Collisions 1,2,2,2

; make sure you are not facing a blank wall when you start
If  Not maze_northopen(m,0,mheight-1) Then TurnEntity player,0,-90,0

; don't show route to start off with
route = 0

sillymap=1

; loop until escape is pressed
While Not KeyDown( 1 )

	; toggle on/off the daft rotation on the map
	If KeyDown(2)  Then 
		If sflag = 0 Then
			sflag=1
			sillymap = 1-sillymap
			If Not sillymap Then RotateEntity mapcube,0,0,0
		End If
	Else
		sflag=0
	End If

	; rotate the map box
	If sillymap Then
		TurnEntity mapcube,1,1,1
	End If

	; move the player if the forward or back keys are pressed
	If KeyDown( 208 )=True Then MoveEntity player,0,0,-0.1
	If KeyDown( 200 )=True Then MoveEntity player,0,0,0.1
	
	; turn the player if the left and right keys are pressed
	If KeyDown( 203 )=True Then TurnEntity player,0,4,0
	If KeyDown( 205 )=True Then TurnEntity player,0,-4,0

	; do the updates
	UpdateWorld
	RenderWorld
	
	; toggle the path when the space key is pressed
	If KeyDown(57) Then 
		If flag = 0 Then
			flag = 1
			route = 1-route
			For pq.path = Each path
				If route Then 
					ShowEntity pq\spark
				Else	
					HideEntity pq\spark
				End If
			Next
		End If
	Else
		flag = 0
	End If

	SetBuffer ImageBuffer(map)
	; display a view of the maze in the top left
	maze_display(m,route)
	
	; draw the players current position in the maze
	maze_drawincell(Int((EntityX(player)-1)/2),(mwidth-1)-Int((EntityZ(player)-1)/2),255,255,255)
	SetBuffer BackBuffer()

	; copy the map into the texture (may be able to draw straight to texture.. dunno)	
	CopyRect 0,0,mwidth*16,mheight*16,0,0,ImageBuffer(map),TextureBuffer(maptex)

	Flip
Wend


; need to work out what needs to be freed manually, this will do for a start
FreeBrush brush
FreeBrush brush2
FreeBrush brush3

; delete the generated maze
maze_delete(m)

; shutdown the maze library
maze_shutdown()

; free off any images used
FreeImage map


End