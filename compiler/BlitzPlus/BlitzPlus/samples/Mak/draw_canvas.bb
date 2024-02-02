; EDITED YET AGAIN - 27th...

; WaitEvent () timeout value -- could be controlled by user to
; allow more time for other apps as they see fit...

; List of events...

Const EVENT_KeyDown		= $101		; Key pressed
Const EVENT_KeyUp		= $102		; Key released
Const EVENT_KeyChar		= $103		; Key generated ASCII character
Const EVENT_MouseDown	= $201		; Mouse button pressed
Const EVENT_MouseUp		= $202		; Mouse button released
Const EVENT_MouseMove	= $203		; Mouse moved
Const EVENT_Gadget		= $401		; Gadget clicked
Const EVENT_Move		= $801		; Window moved
Const EVENT_Size		= $802		; Window resized
Const EVENT_Close		= $803		; Window closed
Const EVENT_Menu		= $1001		; Menu item selected

; Create a main window...

window = CreateWindow ("CrapDraw (right mouse erases)", 100, 100, 640, 480)

If window

	; Create a menu, attached to window ('&' underlines next letter)...
	
	menu = CreateMenu ("&File", 0, WindowMenu (window))
	
	; Create menu items in menu...
	
	CreateMenu ("&Open file...", 1, menu)
	CreateMenu ("&Save", 2, menu)
	CreateMenu ("Sav&e as...", 3, menu)
	CreateMenu ("&About", 4, menu)
	CreateMenu ("E&xit", 5, menu)
	
	; Update menu entries...
	
	UpdateWindowMenu window

	; Create a tab area the size of the main window area...
	
	tabs = CreateTabber (0, 0, ClientWidth (window), ClientHeight (window) - 25, window)
	
	; Lock the tab area's edges to the window (left, right, top, bottom)...
	
	SetGadgetLayout tabs, 1, 1, 1, 1

	; Add some tabs...

	AddGadgetItem tabs, "Normal mode", 1
	AddGadgetItem tabs, "Calm mode"
	AddGadgetItem tabs, "Emergency mode"
	
	; Create some stuff at the bottom...
	
	clear = CreateButton ("Clear all", 0, ClientHeight (window) - 25, 100, 25, window)
	SetGadgetLayout clear, 1, 0, 0, 1
	
	; Create a panel...
	
	panel = CreatePanel (0, 0, ClientWidth (tabs), ClientHeight (tabs), tabs)
	SetPanelColor panel, 64, 96, 180
	SetGadgetLayout panel, 1, 1, 1, 1
	
	canvas = CreateCanvas (10, 10, ClientWidth (panel) - 20, ClientHeight (panel) - 20, panel)
	SetGadgetLayout canvas,1,1,1,1
	SetBuffer CanvasBuffer (canvas)
	
	; Main event loop...
	
	Repeat

		; Wait for a window event...
			
		e = WaitEvent ()
		
		; Process the event...
		
		Select e
		
			Case EVENT_MouseDown
				drawing = EventData ()

			Case EVENT_MouseUp
				drawing = False
				
			Case EVENT_Gadget

				Select EventSource ()
				
					Case tabs

						; Which tab...?
						
						Select SelectedGadgetItem (tabs)
							Case 0
								SetPanelColor panel, 64, 96, 180
							Case 1
								SetPanelColor panel, 32, 96, 64
							Case 2
								SetPanelColor panel, 255, 0, 0
						End Select
					
					Case clear
						Cls
						FlipCanvas canvas
						
				End Select
				
			Case EVENT_Close
				End
				
			Case EVENT_Menu
			
				 ; Get selected menu number (via EventData)...
				
				Select EventData ()
					Case 1
						file$=RequestFile$( "File to open...","" )
					Case 2
						Notify "File saved!"
					Case 3
						file$=RequestFile$( "File to save as...","" )
					Case 4
						Notify "About..." + Chr (10) + Chr (10) + "This is a cool test program by" + Chr (10) + "BlitzSupport, Public Domain 2002."
					Case 5
						End
				End Select
			
			Case EVENT_KeyDown
				If EventData () = 1 Then End

		End Select

		Select drawing
			Case 1
				Color Rnd (100, 255), Rnd (100, 255), Rnd (100, 255)
				Rect MouseX(canvas),MouseY(canvas),4,4
				FlipCanvas (canvas)		
			Case 2
				Color 0, 0, 0
				Rect MouseX(canvas),MouseY(canvas),4,4
				FlipCanvas (canvas)		
		End Select

	Forever

Else
	Notify ("Couldn't create program window!", True)
	End
EndIf