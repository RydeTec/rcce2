; (c) Graham Kennedy


Include "mazelib.bb"

Graphics 800,600

	maze_startup("mazeblocks.png",16,16)

	mwidth = 32; 	set a variable for the width 
	mheight = 32;	and the height of the maze


	SetBuffer BackBuffer()
;	Cls
;	m.maze = maze_create(mwidth,mheight,MilliSecs(),0)
;	maze_display(m,0)
;	Text 500,0,"Finished"

	
	
	finished = 0
	x = 0
	y = 0
;	maze_drawincell(x,y,255,255,255)
	
;	Flip
	
	a$="n"
	k = Asc(a$)
	
	While Not finished
		Cls
		
		If a$ = "n" Then
			If m.maze <> Null Then maze_Delete(m)
			m.maze = maze_create(mwidth,mheight,MilliSecs(),0)
			x = 0
			y = 0		
		End If
		
		If a$="r" And x <> mwidth-1 And y <> mheight-1 Then
			maze_clearRoute(m)
			maze_solve(m,x,y,mwidth-1,mheight-1)
		End If
		
		If a$="s" Then maze_save(m,"maze.maz")
		If a$="l" Then
			maze_delete(m)
			m = maze_load("maze.maz")
		End If
		
		If a$ = "c" Then maze_clearroute(m)
		
		If k = 28 And maze_NorthOpen(m,x,y) Then
			y=y-1
		End If

		If k = 29 And maze_SouthOpen(m,x,y) Then
			y=y+1
		End If

		If k = 30 And maze_EastOpen(m,x,y) Then
			x=x+1
		End If

		If k = 31 And maze_WestOpen(m,x,y) Then
			x=x-1
		End If
		
		maze_display(m,1)
		maze_drawincell(x,y,255,255,255)
		Text 520,0, "Use arrow keys to move"
		Text 520,12,"X = exit"
		Text 520,24,"N = New maze"
		Text 520,36,"R = Show me the route"
		Text 520,48,"C = Clear the route"
		Text 520,60,"S = Save Maze"
		Text 520,72,"L = Load Maze"
		Text 520,86,"<enter> = snapshot screen"

		Flip

		If k = 13 Then
			SaveBuffer(FrontBuffer(),"maze.bmp") 
		End If

		k = WaitKey()
		a$ = Lower$(Chr$(k))
		If a$ = "x" Then finished=1

	Wend
		
	
	
	maze_delete(m)
;Wend

maze_shutdown()

End