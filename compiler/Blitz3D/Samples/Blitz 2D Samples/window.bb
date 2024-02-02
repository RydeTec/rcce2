;Setting up graphics stuff for the demo down below :)
Graphics 1024,768:SetBuffer BackBuffer()


;Warpy Windows!
;DO NOT EDIT THIS CODE UNLESS YOU WANT TO :D
;Usage:
;To create a new window:
;New_window(x,y,width,height,title)
;Pretty self-explanatory :)
;X is the left edge of the window and Y is the top edge, not the middle!
;
windowdata=ReadFile("window.txt")
windowbmppath$=ReadString(windowdata)
Global outsidemask_r=Int(ReadString(windowdata))
Global outsidemask_g=Int(ReadString(windowdata))
Global outsidemask_b=Int(ReadString(windowdata))
Global insidemask_r=Int(ReadString(windowdata))
Global insidemask_g=Int(ReadString(windowdata))
Global insidemask_b=Int(ReadString(windowdata))
Global windowbmp=LoadAnimImage(windowbmppath,32,32,0,9)
HandleImage windowbmp,0,0

Type window
Field x,y,width,height,image,title$
End Type
Global win.window

Function New_window(x,y,width,height,title$)
win=New window
temp_warpy_window_bg=CreateImage(width,height)
temp_warpy_window_fg=CreateImage(width,height)
temp_warpy_window=CreateImage(width,height)
win\image=temp_warpy_window
win\x=x
win\y=y
win\width=width
win\height=height
win\title=title
SetBuffer ImageBuffer(temp_warpy_window_bg)
ClsColor insidemask_r,insidemask_g,insidemask_b
Cls
For x=1 To width Step 32
	For y=1 To height Step 32
		DrawBlock windowbmp,x,y,4
	Next
Next
SetBuffer ImageBuffer(temp_warpy_window_fg)
ClsColor insidemask_r,insidemask_g,insidemask_b
Cls
For x=1 To width Step 32
	DrawBlock windowbmp,x,0,1
	DrawBlock windowbmp,x,height-32,7
Next
For y=1 To height Step 32
	DrawBlock windowbmp,0,y,3
	DrawBlock windowbmp,width-32,y,5
Next
DrawBlock windowbmp,0,0,0
DrawBlock windowbmp,width-32,0,2
DrawBlock windowbmp,0,height-32,6
DrawBlock windowbmp,width-32,height-32,8
;change BackBuffer() To FrontBuffer() If you don't use the Back Buffer
MaskImage temp_warpy_window_bg,insidemask_r,insidemask_g,insidemask_b
MaskImage temp_warpy_window_fg,insidemask_r,insidemask_g,insidemask_b
SetBuffer ImageBuffer(temp_warpy_window)
ClsColor outsidemask_r,outsidemask_g,outsidemask_b
Cls
DrawImage temp_warpy_window_bg,0,0
DrawImage temp_warpy_window_fg,0,0
oldr=ColorRed():oldg=ColorGreen():oldb=ColorBlue()
Color 0,0,0
Text width/2-Len(title)*FontWidth()/2,10,title$
Color oldr,oldg,oldb
MaskImage win\image,outsidemask_r,outsidemask_g,outsidemask_b
SetBuffer BackBuffer()
FreeImage temp_warpy_window_bg
FreeImage temp_warpy_window_fg
End Function

Function draw_window()
	For win=Each window
		DrawImage win\image,win\x,win\y
		If MouseDown(1)
			mx=MouseX():my=MouseY()
			If mx>win\x And mx<win\x+win\width And my>win\y And my<win\y+win\height
				win\x=MouseX()-win\width/2
				win\y=MouseY()-win\height/2
;FUNKY SHUDDERING WINDOW CODE! uncomment and click on a window to see! :)
				win\width=win\width+Rnd(-5,5)
				win\height=win\height+Rnd(-5,5)
				win\title="Area: "+Str$(win\width*win\height)
				redraw_window(win)
			EndIf
		EndIf
	Next
End Function

Function redraw_window(win.window)
width=win\width
height=win\height
x=win\x
y=win\y
temp_warpy_window_bg=CreateImage(width,height)
temp_warpy_window_fg=CreateImage(width,height)
temp_warpy_window=CreateImage(width,height)
FreeImage win\image
win\image=temp_warpy_window
SetBuffer ImageBuffer(temp_warpy_window_bg)
ClsColor insidemask_r,insidemask_g,insidemask_b
Cls
For x=1 To width Step 32
	For y=1 To height Step 32
		DrawBlock windowbmp,x,y,4
	Next
Next
SetBuffer ImageBuffer(temp_warpy_window_fg)
ClsColor insidemask_r,insidemask_g,insidemask_b
Cls
For x=1 To width Step 32
	DrawBlock windowbmp,x,0,1
	DrawBlock windowbmp,x,height-32,7
Next
For y=1 To height Step 32
	DrawBlock windowbmp,0,y,3
	DrawBlock windowbmp,width-32,y,5
Next
DrawBlock windowbmp,0,0,0
DrawBlock windowbmp,width-32,0,2
DrawBlock windowbmp,0,height-32,6
DrawBlock windowbmp,width-32,height-32,8
;change BackBuffer() To FrontBuffer() If you don't use the Back Buffer
MaskImage temp_warpy_window_bg,insidemask_r,insidemask_g,insidemask_b
MaskImage temp_warpy_window_fg,insidemask_r,insidemask_g,insidemask_b
SetBuffer ImageBuffer(temp_warpy_window)
ClsColor outsidemask_r,outsidemask_g,outsidemask_b
Cls
DrawImage temp_warpy_window_bg,0,0
DrawImage temp_warpy_window_fg,0,0
oldr=ColorRed():oldg=ColorGreen():oldb=ColorBlue()
Color 0,0,0
Text width/2-Len(win\title)*FontWidth()/2,10,win\title$
Color oldr,oldg,oldb
MaskImage win\image,outsidemask_r,outsidemask_g,outsidemask_b
SetBuffer BackBuffer()
FreeImage temp_warpy_window_bg
FreeImage temp_warpy_window_fg
End Function

Function Delete_window(win.window)
	FreeImage win\image
	Delete win
End Function

;Short demo
New_window(320,240,96,96,"Window 1")
New_window(20,40,128,200,"Window 2")
pointer=LoadImage("pointer.bmp")
While Not KeyHit(1)
	ClsColor r,g,b
	r=r+1
	g=g-1
	b=b+2
	If r<0 Then r=255
	If r>255 Then r=0
	If g<0 Then g=255
	If g>255 Then g=0
	If b<0 Then b=255
	If b>255 Then b=0
	Cls
	draw_window()
	DrawImage pointer,MouseX(),MouseY()
	Flip
Wend