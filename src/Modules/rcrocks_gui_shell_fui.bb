Global thispath$ = SystemProperty$("appdir")
If Lower$(Right$(thispath$,4))="bin\"
; Running from the IDE
thispath$ = CurrentDir$()
End If
If Right$(thispath$,1)<>"\" Then thispath$ = thispath$+"\"

If testing=True Then 
ChangeDir thispath$
Else
ChangeDir thispath$+"data\default project\"
EndIf






    ;main top menu
	Global GUI_TOPWIN = FUI_Window( 0, 0, App\W, GUI_VARIABLE_TOPBAR_HEIGHT, "", 0, 2 )
    Global GUI_TOPWIN_MENUFILE = FUI_MenuTitle( GUI_TOPWIN, "File" )
		Global GUI_MENUFILE_EXPORT   = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Export", "" )
	  	Global GUI_MENUFILE_Exit     = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Exit", "" )
    Global GUI_RIGHTWIN = FUI_Window( App\W - GUI_VARIABLE_RIGHTBAR_WIDTH, GUI_VARIABLE_TOPBAR_HEIGHT - 1, GUI_VARIABLE_LEFTBAR_WIDTH, app\h, "", 0, 0 )
       Global GUI_RIGHTWIN_TEXSCALEL     = FUI_Label( GUI_rightWIN, 5, 10, "Texture scale" )
       Global GUI_RIGHTWIN_TEXSCALE      = FUI_Spinner( GUI_RIGHTWIN, 5, 25,GUI_VARIABLE_RIGHTBAR_WIDTH - 10 , 20, 10, 400, 100, 1, DTYPE_INTEGER, "%" )

	   Global GUI_RIGHTWIN_TextureCanvas = FUI_ImageBox( GUI_RIGHTWIN, 20, 50, 100, 100)
	   Global GUI_RIGHTWIN_ChangeTexture = FUI_Button( GUI_RIGHTWIN, 5, 165, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Select texture", 0, 0 )

  	   Global GUI_RIGHTWIN_SMOOTHL    = FUI_Label( GUI_RIGHTWIN, 5, 200, "Smoothness" )
 	   Global GUI_RIGHTWIN_SMOOTH     = FUI_Slider( GUI_RIGHTWIN, 5, 215, 130, 15,4,10,4)

  	   Global GUI_RIGHTWIN_DEFORML    = FUI_Label( GUI_RIGHTWIN, 5, 235, "Deform" )
 	   Global GUI_RIGHTWIN_DEFORM     = FUI_Slider( GUI_RIGHTWIN, 5, 250, 130, 15,0,250,5)

  	   Global GUI_RIGHTWIN_SegmentsL    = FUI_Label( GUI_RIGHTWIN, 5, 270, "Segments" )
 	   Global GUI_RIGHTWIN_Segments     = FUI_Slider( GUI_RIGHTWIN, 5, 285, 130, 15,0,20,2)

  	   Global GUI_RIGHTWIN_FRAGMENSL    = FUI_Label( GUI_RIGHTWIN, 5, 305, "Fragments" )
 	   Global GUI_RIGHTWIN_FRAGMENTS     = FUI_Slider( GUI_RIGHTWIN, 5, 320, 130, 15,0,10,2)

  	   Global GUI_RIGHTWIN_FRAGMENSAL    = FUI_Label( GUI_RIGHTWIN, 5, 340, "Fragment area" )
 	   Global GUI_RIGHTWIN_FRAGMENTAREA   = FUI_Slider( GUI_RIGHTWIN, 5, 355, 130, 15,1,10,4)

  	   Global GUI_RIGHTWIN_SCALEXL    = FUI_Label( GUI_RIGHTWIN, 5, 375, "Scale X" )
 	   Global GUI_RIGHTWIN_SCALEX   = FUI_Slider( GUI_RIGHTWIN, 5, 390, 130, 15,20,150,70)

  	   Global GUI_RIGHTWIN_SCALEYL    = FUI_Label( GUI_RIGHTWIN, 5, 410, "Scale Y" )
 	   Global GUI_RIGHTWIN_SCALEY   = FUI_Slider( GUI_RIGHTWIN, 5, 425, 130, 15,20,150,80)

  	   Global GUI_RIGHTWIN_SCALEZL    = FUI_Label( GUI_RIGHTWIN, 5, 445, "Scale Z" )
 	   Global GUI_RIGHTWIN_SCALEZ   = FUI_Slider( GUI_RIGHTWIN, 5, 460, 130, 15,20,150,90)

  	   Global GUI_RIGHTWIN_ShineL    = FUI_Label( GUI_RIGHTWIN, 5, 480, "Shine" )
 	   Global GUI_RIGHTWIN_SHINE   = FUI_Slider( GUI_RIGHTWIN, 5, 495, 130, 15,0,100,0)

	   Global GUI_RIGHTWIN_Generate = FUI_Button( GUI_RIGHTWIN, 5, 550, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Generate", 0, 0 )



Function PositionWindows()
            ;keep gadget windows in frame of main window
               
             If GFX_WINDOW_MODE <> 1

				FUI_SendMessage( GUI_TOPWIN, M_SETSIZE_W, App\W )

 	    	    FUI_SendMessage( GUI_RIGHTWIN, M_SETPOS_X,app\w-(GUI_VARIABLE_LEFTBAR_WIDTH))
 	    	    FUI_SendMessage( GUI_RIGHTWIN, M_SETSIZE_H,app\H)

 	    	  


                Else ; else this is fullscreen

                FUI_SendMessage( GUI_TOPWIN, M_SETSIZE_W, gfx_window_width )

 	    	    FUI_SendMessage( GUI_RIGHTWIN, M_SETPOS_X,gfx_window_width-(GUI_VARIABLE_LEFTBAR_WIDTH))
 	    	    FUI_SendMessage( GUI_RIGHTWIN, M_SETSIZE_H,gfx_window_HEIGHT)

 	    	   

                EndIf
End Function 


Function FUI_Confirm(msg$,ys$,no$)
	TWidth = 300
	THeight = 100
 	GUI_CONTROLSWINDOW = FUI_Window( 0, 0, TWidth, THeight, "Confirm", 0, 1, 1 )
		GUI_CONTROLSWINDOW_LABEL1 = FUI_Label( GUI_CONTROLSWINDOW, 10, 10, msg$ )
   	    GUI_CONTROLSWINDOW_LABEL2 = FUI_Label( GUI_CONTROLSWINDOW, 10, 70, "")
		GUI_CONTROLSWINDOW_BUTTON_OK = FUI_Button( GUI_CONTROLSWINDOW, 10, 35, 80, 20, ys$ )		
		GUI_CONTROLSWINDOW_BUTTON_NO = FUI_Button( GUI_CONTROLSWINDOW, 200, 35, 80, 20, no$ )
	FUI_ModalWindow( GUI_CONTROLSWINDOW )
	FUI_CenterWindow( GUI_CONTROLSWINDOW )
    FUI_SendMessage(gui_controlswindow,m_open)
	TClose = False
	Repeat
		 For e.Event = Each Event
		 	Select e\EventID
		 		Case GUI_CONTROLSWINDOW
		 			Select e\EventData
		 				Case "Closed"
		 					TClose = True
                            Return  -1
		 			End Select
		 		Case GUI_CONTROLSWINDOW_BUTTON_OK
		 			tClose = True
					FUI_DeleteGadget( GUI_CONTROLSWINDOW )
                    Return  1
		 		Case GUI_CONTROLSWINDOW_BUTTON_NO
		 			tClose = True
					FUI_DeleteGadget( GUI_CONTROLSWINDOW )
              
                    Return  -1
		 	End Select
		 	Delete e
		 Next
		 If KeyHit( 1 ) Then TClose = True
		 ;#Region Rendering	
			CameraProjMode( App\Cam, False )
			CameraProjMode( Cam, True )
			RenderWorld( )
			CameraProjMode( Cam, False )
			CameraProjMode( App\Cam, True )
			FUI_Update( )
			RenderWorld( )
			Flip( GFX_VARIABLE_VSYNC )
		;#End Region
		If TClose = True Then
			Exit
		EndIf
	Forever
	FUI_DeleteGadget( GUI_CONTROLSWINDOW )
	FlushKeys( )
	FlushMouse( )
End Function

Function ControlsWindow( )
	TWidth = 320
	THeight = 230
 	GUI_CONTROLSWINDOW = FUI_Window( 0, 0, TWidth, THeight, "Controls...", 0, 1, 1 )
		GUI_CONTROLSWINDOW_LABEL1 = FUI_Label( GUI_CONTROLSWINDOW, 10, 10, "Spacebar = Mouselook" )
		GUI_CONTROLSWINDOW_LABEL2 = FUI_Label( GUI_CONTROLSWINDOW, 10, 30, "Left Mouse Button = Activate GUI button" )
		GUI_CONTROLSWINDOW_LABEL3 = FUI_Label( GUI_CONTROLSWINDOW, 10, 50, "Right Mouse Button = Perform GUI action" )
   	    GUI_CONTROLSWINDOW_LABEL4 = FUI_Label( GUI_CONTROLSWINDOW, 10, 70, "PgUp / PgDwn = Adjust grid" )
   	    GUI_CONTROLSWINDOW_LABEL5 = FUI_Label( GUI_CONTROLSWINDOW, 10, 90, "F1  F2  F3 = Select Mode ")
   	    GUI_CONTROLSWINDOW_LABEL6 = FUI_Label( GUI_CONTROLSWINDOW, 10, 110, "Shift  +  Right Mouse button = Drop Item")		
		GUI_CONTROLSWINDOW_BUTTON_OK = FUI_Button( GUI_CONTROLSWINDOW, 10, 170, 230, 30, "OK" )
		
	FUI_ModalWindow( GUI_CONTROLSWINDOW )
	FUI_CenterWindow( GUI_CONTROLSWINDOW )
	TClose = False
	Repeat
		 For e.Event = Each Event
		 	Select e\EventID
		 		Case GUI_CONTROLSWINDOW
		 			Select e\EventData
		 				Case "Closed"
		 					TClose = True
		 			End Select
		 		Case GUI_CONTROLSWINDOW_BUTTON_OK
		 			tClose = True
		 	End Select
		 	Delete e
		 Next
		 If KeyHit( 1 ) Then TClose = True
		 ;#Region Rendering	
			CameraProjMode( App\Cam, False )
			CameraProjMode( Cam, True )
			RenderWorld( )
			CameraProjMode( Cam, False )
			CameraProjMode( App\Cam, True )
			FUI_Update( )
			RenderWorld( )
			Flip( GFX_VARIABLE_VSYNC )
		;#End Region
		If TClose = True Then
			Exit
		EndIf
	Forever
	FUI_DeleteGadget( GUI_CONTROLSWINDOW )
	FlushKeys( )
	FlushMouse( )
End Function


Function AboutWindow( )
	TWidth = 250
	THeight = 230
	
        GUI_ABOUTWINDOW = FUI_Window( 0, 0, TWidth, THeight, "About...", 0, 1, 1 )
		GUI_ABOUTWINDOW_LABEL1 = FUI_Label( GUI_ABOUTWINDOW, 10, 10, "Realm Crafter Stone Editor" )
		GUI_ABOUTWINDOW_LABEL2 = FUI_Label( GUI_ABOUTWINDOW, 10, 35, "Programmer/Author: Jeff Frazier - Rifraf" )
		GUI_ABOUTWINDOW_LABEL3 = FUI_Label( GUI_ABOUTWINDOW, 10, 55, "" )
		GUI_ABOUTWINDOW_LABEL4 = FUI_Label( GUI_ABOUTWINDOW, 10, 80, "")
		GUI_ABOUTWINDOW_LABEL4 = FUI_Label( GUI_ABOUTWINDOW, 10, 100, "http://www.realmcrafter.com" )
		GUI_ABOUTWINDOW_LABEL5 = FUI_Label( GUI_ABOUTWINDOW, 10, 120, "http://www.solstargames.com" )		
		GUI_ABOUTWINDOW_BUTTON_OK = FUI_Button( GUI_ABOUTWINDOW, 10, 170, 230, 30, "OK" )
        FUI_ModalWindow( GUI_ABOUTWINDOW )
  	    FUI_CenterWindow( GUI_ABOUTWINDOW )
		TClose = False
		Repeat
		 For e.Event = Each Event
		 	Select e\EventID
		 		Case GUI_ABOUTWINDOW
		 			Select e\EventData
		 			Case "Closed"
		 			TClose = True
		 			End Select
		 		Case GUI_ABOUTWINDOW_BUTTON_OK
		 			tClose = True
		 	End Select
		 	Delete e
		 Next
		 If KeyHit( 1 ) Then TClose = True
		 ;#Region Rendering	
			CameraProjMode( App\Cam, False )
			CameraProjMode( Cam, True )
			RenderWorld( )
			CameraProjMode( Cam, False )
			CameraProjMode( App\Cam, True )
			FUI_Update( )
			RenderWorld( )
			Flip( GFX_VARIABLE_VSYNC )
		;#End Region
		
		If TClose = True Then
			Exit
		EndIf
	Forever
	FUI_DeleteGadget( GUI_ABOUTWINDOW )
	FlushKeys( )
	FlushMouse( )
End Function