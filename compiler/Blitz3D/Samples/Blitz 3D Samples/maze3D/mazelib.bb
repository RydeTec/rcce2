; maze generator library
; (c) Graham Kennedy (Blitztastic) 2001
; version 1.0 - Original build
; version 1.1 - Various fixes
; version 1.2 - Replaced global array with independent type/bank
;             - Packaged it into a more standard 'library' approach
; version 1.3 - Added more 'friendly functions'
; version 1.4 - Added maze solver code (currently for perfect mazes only)
;
; Still to Do:
; More error checking, eg. save successful, load successful etc.
;
; Any comments / suggestions please email blitztastic@bigfoot.com
; This library is free to use in your software, but I'd like it if you could include
; an acknowledgement somewhere, or better still a copy.
; ---------------------------------------------------------------
; Maze generation library
; Generates a 'perfect' maze, ie a maze with only one solution between any 
; two points. A non-perfect maze, ie. one with loops can be created by setting the 
; crossover parameter to > 0 when creating the maze
;
; User Types
; Maze
;   contains the width/height of the maze, as well as the data, you should only
;   need to create a variable of this type to hold it, all access is performed by
;   functions.

; User commands
; maze_startup(filename$,imagewidth,imageheight)
;  Initialises the maze library, call this after your graphics command but
;  before you use any other maze commands
;  parameters:
;		filename = name of a graphics file for displaying the maze
;		imagewidth  = required width for a displayed cell
;	    imageheight = required height for a displayed cell

; maze_shutdown()
;  Shuts down and cleans up the maze library, use it before the program terminates

; maze_create(width,height,seed,crossovers)
;  This will create a new maze.
;  Parameters:
;		width 		= the width of the maze to create
; 		height 		= the height of the maze to create
;	    seed   		= a seed to create the maze randomly, entering the same
;				 	  seed to multiple create's should create the same maze
;		crossovers  = the number of crossovers you would like to try and create
;					  in the maze, specifying 0 will create a perfect maze.
;  Return:
;	    a type variable of type 'MAZE'

; maze_display(m.maze)
;  display a maze on screen using a very basic method, modify for use in your app
;  parameters:
;		The maze variable of the maze you want to display

; maze_getcell(m.maze,x,y)
;  gets the value of the cell in the maze at position x, y
;  Parameters :
;		m.maze  = maze to examine
;		x		= x position
;	    y		= y position
;  Returns :
;		integer value at that cell, encoded as bits
;		???1 = North open
;		??1? = East Open
;		?1?? = South Open
;	    1??? = West open
;   eg. if 3 was returned (ie. ??11) north and east are open
;		

; maze_setcell(m.maze,x,y,value)
;	Sets the value of the cell in the maze at position x,y
;   Parameters:
;		m.maze  = maze to examine
;		x		= x position
;	    y		= y position
;  		Value	integer value to store at that cell, encoded as bits
;		???1 = North open
;		??1? = East Open
;		?1?? = South Open
;	    1??? = West open
;   eg. if 3 was stored (ie. ??11) north and east are open

; maze_DirOpen(m.maze,x,y,direction)
;   Indicates if a given direction is 'open' in a cell
;	Parameters:
;		m.maze	= maze to examine
;		x		= x position
;		y		= y position
;		direction= direction to look
;						0 - north
;						1 - east
;						2 - south
;						3 - west
;	Returns:
; 		true if the direction is open, otherwise false

; maze_NorthOpen(m.maze,x,y)
; maze_EastOpen(m.maze,x,y)
; maze_SouthOpen(m.maze,x,y)
; maze_WestOpen(m.maze,x,y)
;	Same functionality as maze_diropen, but in friendlier terms.

; maze_solve(m.maze,sx,sy,fx,fy)
; 	creates a path from one point of the maze to another (currently only works on perfect mazes)
; 	parameters:
;		m.maze 	= the maze to solve
;		sx		= the x position to start from
;		sy		= the y position to start from
;		fx		= the x position to finish at
;		fy		= the y position to finish at
;	returns:
;		a string containing the directions to move eg. "0112123" = North,East,East,South,East,South,West

; maze_save(m.maze,filename$)
;	Saves the current maze (& route information if available) to disk
;   parameters:
;		m.maze	  = maze to save
;		filename$ = name of the file to save it as

; maze_Load(filename$)
;	Saves the current maze (& route information if available) to disk
;   parameters:
;		filename$ = name of the file to LOAD
;	returns:
;		a maze type (same as create)

.maze_endofintro

; the main structure for a maze, use 'maze_create' to create one of these
Type maze
	Field width,height
	Field buffer
	Field crossing
End Type

; internal representation of a maze cell, don't use in your code
Type maze_loc
	Field x,y
End Type

; internal control structure, don't use in your code
Type maze_control
	Field imgname$
	Field imgwidth, imgheight
	Field img
	Field count
End Type

Function maze_getcell(m.maze,x,y)
	Return PeekByte(m\buffer,y*m\width+x)
End Function

Function maze_setcell(m.maze,x,y,value)
	PokeByte m\buffer,y*m\width+x,value
End Function

Function maze_GetDir(m.maze,x,y,dir)
	Return (maze_getcell(m,x,y) And (2^dir))
End Function

Function maze_SetDir(m.maze,x,y,dir,value)
	c = maze_getcell(m,x,y) And (255-(2^dir))
	If value = 1 Then
		c = c Or (2^dir)
	End If
	maze_setcell(m,x,y,c)
End Function

Function maze_DirOpen(m.maze,x,y,dir)
	Return (maze_getDir(m,x,y,dir) = (2^dir))
End Function

Function maze_NorthOpen(m.maze,x,y)
	Return maze_diropen(m,x,y,0)
End Function

Function maze_EastOpen(m.maze,x,y)
	Return maze_diropen(m,x,y,1)
End Function

Function maze_SouthOpen(m.maze,x,y)
	Return maze_diropen(m,x,y,2)
End Function

Function maze_WestOpen(m.maze,x,y)
	Return maze_diropen(m,x,y,3)
End Function

; display the maze on screen
Function maze_display(m.maze,route)

	l.maze_loc = New maze_loc
	For x = 0 To m\width - 1
		For y = 0 To m\height - 1
			l\x = x
			l\y = y
			maze_drawcell(m,l)
			If route Then
				If (maze_getcell(m,x,y) And 240) > 0 Then
					maze_drawincell(x,y,0,255,0)
				End If
			End If
		Next
	Next
	Delete l

End Function

; draw a single maze cell
Function maze_drawcell(m.maze,l.maze_loc)
	
	mc.maze_control = First maze_control 
	If mc <> Null Then
	
		x=l\x*mc\imgwidth
		y=l\y*mc\imgheight
	
		p = maze_getcell(m,l\x,l\y) Mod 16
	
		DrawImage mc\img,x,y,p
	End If
	
End Function

Function maze_drawIncell(px,py,r,g,b)
	
	mc.maze_control = First maze_control 
	If mc <> Null Then
	
		x=px*mc\imgwidth
		y=py*mc\imgheight
		
		Color r,g,b
		Rect x+2,y+2,mc\imgwidth-4,mc\imgheight-4,1
	End If
	
End Function



; create a new maze, the setting the seed rather than using millisecs()
; will allow the same maze to be generated a number of times
; crossovers indicates how many extra links to put into the maze, this
; creates loops in the maze.
Function maze_Create.maze(width,height,seed,crossovers)
	m.maze = New maze
	
	m\width = width
	m\height = height
	m\crossing = (crossovers > 0)
	
	m\buffer = CreateBank(width * height)	
	mc.maze_control = First maze_control
	If mc <> Null Then
		mc\count = mc\count + 1
	End If
	
	maze_generate(m,crossovers,seed)
	
	Return m
End Function

; delete an individual maze
Function maze_Delete(m.maze)

	mc.maze_control = First maze_control
	If mc <> Null Then
		mc\count = mc\count + 1
	End If

	FreeBank m\buffer
	Delete m  
End Function

; initialisation routine
; call at the start of the program, but after the graphics command
Function maze_startup(filename$,imgwidth,imgheight)
	mc.maze_control = New maze_control
	mc\imgwidth = imgwidth
	mc\imgheight = imgheight
	mc\imgname$ = filename$
;	mc\img = LoadAnimImage(filename$,32,32,0,16)
	mc\img = LoadAnimImage(filename$,32,32,0,16)
	ResizeImage mc\img,imgwidth,imgheight

End Function

; cleanup routine, call at the end of the program to clear
; up any mazes left hanging round
Function maze_shutdown()
	For m.maze = Each maze
		maze_delete(m)
	Next
	
	mc.maze_control = First maze_control
	If mc <> Null Then
		FreeImage mc\img
		Delete mc
	End If
	
End Function


;Function maze_drawcell2(l.maze_loc)
;	x=l\x*imgsize
;	y=l\y*imgsize
;	os = imgsize / 4
;	
;	p = maze(l\x,l\y) Mod 16
;	
;	DrawImage img,x,y,p
;	Rect x+os,y+os,os,os,1
;	
;	
;End Function

; calculate offset direction
Function maze_dircalc(l.maze_loc,dir,r.maze_loc)
	Select dir
		Case 0
			dx = 0
			dy = -1
		Case 1
			dx = 1
			dy = 0
		Case 2
			dx = 0
			dy = 1
		Case 3
			dx = -1
			dy = 0
	End Select
	
	r\x = l\x+dx
	r\y = l\y+dy
	
End Function	

;Invert offset direction
Function maze_Invdir(dir)
	Return (dir + 2) Mod 4
End Function


; flag xxxxxxx1 = new dir must be empty cell
;      xxxxxx1x = new dir must be 'used' cell
;      xxxxx1xx = new dir must not have an opening already
Function maze_validdirs(m.maze,l.maze_loc,flags)

;	DrawImage img,x,y,p

	c = 0
;	mc = First(maze_control)
;	If mc <> Null Then

		r.maze_loc = New maze_loc
		For i = 0 To 3
			maze_dircalc(l,i,r)
			
			If (r\x >= 0) And (r\x < m\width) And (r\y >= 0) And (r\y < m\height) Then
				If (flags And 1) Then
					If maze_getcell(m,r\x,r\y) > 0 Then c=c+1
				End If
				
				If (flags And 2) Then 
					If maze_getcell(m,r\x,r\y) = 0 Then c=c+1				
				End If
				
				If (flags And 4) Then
					If (maze_getcell(m,l\x,l\y) And (2^i)) =0 Then c = c + 1
				End If
			End If
	
		Next 
		Delete r
		
;	End If

	
	Return c

End Function

; flag xxxxxxx1 = new dir must be empty cell
;      xxxxxx1x = new dir must be 'used' cell
;      xxxxx1xx = new dir must not have an opening already
Function maze_dir(m.maze,l.maze_loc,flags,r.maze_loc)


	safe = 0
	result = -1
	
	If maze_validdirs(m,l,flags) = 0 Then result = 0
	
	While result = -1
	
;		d=(Rnd(1)*4)-.5
		d=Rand(0,3)
		safe = safe + 1
		If safe > 100 Then Stop
			
		maze_dircalc(l,d,r)	
		
		If (r\x >= 0) And (r\x < m\width) And (r\y >= 0) And (r\y < m\height) Then
			If (flags And 1) Then
				If maze_getcell(m,r\x,r\y) > 0 Then result = 1
			End If
			
			If (flags And 2) Then 
				If maze_getcell(m,r\x,r\y) = 0 Then result = 1		
			End If

			If (flags And 4) Then
				If (maze_getcell(m,l\x,l\y) And (2^d)) =0 Then result = 1
			End If
			
		End If
		
	Wend
			
	If result = 0 Then			
		Return -1
	Else
		Return d
	End If

End Function

Function maze_generate(m.maze,crossovers,seed)
	SeedRnd seed
	finished = 0
	
	For x = 0 To m\width-1
		For y = 0 To m\height -1
			maze_setcell(m,x,y,0) 
		Next
	Next 
	
	l.maze_loc = New maze_loc
	r.maze_loc = New maze_loc
	b.maze_loc = New maze_loc
	b\x = 0
	b\y = 0
	l\x=b\x
	l\y=b\y

	maze_setcell(m,l\x,l\y,16) 
	While Not finished
		; move to new location
		If maze_dir(m,l,2,r) > -1 Then
			l\x = r\x
			l\y = r\y
		Else
			newstart = 0
			While Not newstart
				b\x = b\x + 1
				
				If b\x = m\width Then
					b\x = 0
					b\y = b\y + 1
					If b\y = m\height Then
						finished = 1
						newstart = 1
					End If
				End If
				
				If Not finished Then
					If maze_getcell(m,b\x,b\y) = 0 Then 
						newstart = 1
					End If
				End If
			Wend
	
			l\x = b\x
			l\y = b\y
		End If
		
		If Not finished Then
			d = maze_dir(m,l,1,r)
			If d > -1 Then
				maze_setcell(m,l\x,l\y,maze_getcell(m,l\x,l\y) Or (2^d))
				maze_setcell(m,r\x,r\y,maze_getcell(m,r\x,r\y) Or (2^((d + 2) Mod 4)))	
;				maze_drawcell(m,r)
			End If
		End If
	
	Wend
		
	maze_setcell(m,0,0,maze_getcell(m,0,0) - 16)
	
	
	; generate crossovers
	i = 0
	While i < crossovers
		fails = 0
		Repeat
			l\x = Rnd(1,m\width-2)
			l\y = Rnd(1,m\height-2)
			d = maze_dir(m,l,4,r)
			If d > -1 Then
				m0 = maze_getcell(m,l\x,l\y)
				m1 = maze_getcell(m,r\x,r\y)

				If (((m0 And 1)=1)+((m0 And 2)=2)+((m0 And 4)=4)+((m0 And 8)=8) > 1) Or (((m1 And 1)=1)+((m1 And 2)=2)+((m1 And 4)=4)+((m1 And 8)=8) > 1) Then
					d = -1
				Else
					If d = 0 Or d=2 Then
						If (((m0 And 2) = 2) And ((m1 And 2) = 2)) Or (((m0 And 8) = 8) And ((m1 And 8) = 8)) Then
							d = -1
						End If
					Else
						If (((m0 And 1) = 1) And ((m1 And 1) = 1)) Or (((m0 And 4) = 4) And ((m1 And 4) = 4)) Then
							d = -1
						End If				
					End If
				End If
			End If
			If d = -1 Then fails = fails + 1
		Until d > -1 Or fails > 50
		
		If d > -1 Then
				maze_setcell(m,l\x,l\y,maze_getcell(m,l\x,l\y) Or (2^d))
				maze_setcell(m,r\x,r\y,maze_getcell(m,r\x,r\y) Or (2^((d + 2) Mod 4)))	
		End If
	
		i = i + 1
	Wend
	
	
	Delete l
	Delete r
	Delete b

End Function

Type maze_node
	Field x,y
	Field dir,tries
	Field edir
End Type

Function maze_clearRoute(m.maze)
	; clear out route information
	For x = 0 To m\width-1
		For y = 0 To m\height-1
			maze_setcell(m,x,y,(maze_getcell(m,x,y) And 15))
		Next
	Next
End Function

Function maze_allSolve(m.maze,fx,fy)
	maze_clearroute(m.maze)
	For x = 0 To m\width-1
		For y = 0 To m\height-1
			If (x <> fx Or y <> fy) And ((maze_getcell(m,x,y) And 240) = 0) Then
				maze_solve(m,x,y,fx,fy)
			End If
		Next
	Next
End Function

; solve the maze using a fairly simple "wall hugger" style process
Function maze_solve(m.maze,sx,sy,fx,fy)
 If Not m\crossing Then
	; create a workspace same size as the map.
	count = 1
	
	n.maze_node = New maze_node
	n\x = sx
	n\y = sy
	n\dir = -1
	n\tries = 0
	n\edir = 16
	finished = 0
	
	
	l.maze_loc = New maze_loc
	r.maze_loc = New maze_loc
	
	While (n\x <> fx Or n\y <> fy) And (finished = 0)

	
		n\tries = n\tries + 1
		n\dir = (n\dir + 1) Mod 4
		If n\tries > 4 Then
			Delete n
			n = Last maze_node
		Else
			If maze_diropen(m,n\x,n\y,n\dir) And n\dir <> n\edir Then
				count = count + 1
				; that dir is open	
				l\x = n\x
				l\y = n\y
				dir = n\dir
				maze_dircalc(l,n\dir,r)
				n = New maze_node
				n\x = r\x
				n\y = r\y
				n\dir = dir
				n\tries = 0
				n\edir = maze_invdir(dir)
			End If
		End If
	Wend
	
	Delete l
	Delete r
	
	
	;create final codestring & inscribe on maze
	cs$ = ""
	For n = Each maze_node
		maze_setcell(m,n\x,n\y,(maze_getcell(m,n\x,n\y) And 15) Or (2^(n\dir+4)))
		cs$ = cs$ + Str$(n\dir)
	Next

	; clear the workspace at the end
	Delete Each maze_node
  End If
  Return cs$
End Function

Function Maze_Save(m.maze,filename$)
	; Open a file to write to
	fileout = WriteFile(filename$)
	WriteInt fileout,m\width
	WriteInt fileout,m\height
	WriteInt fileout,m\crossing
	WriteBytes m\buffer,fileout,0,m\width*m\height
	CloseFile fileout
End Function

Function Maze_Load.maze(filename$)
	m.maze = New maze
	filein = ReadFile(filename$)
	m\width = ReadInt( filein)
	m\height = ReadInt( filein)
	m\crossing = ReadInt(filein)
	m\buffer = CreateBank(m\width*m\height)
	ReadBytes m\buffer,filein,0,m\width*m\height
	
	Return m

End Function