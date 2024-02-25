Global thispath$ = CurrentDir$()
If Right$(thispath$,1)<>"\" Then thispath$ = thispath$+"\"
ChangeDir thispath$


	Global GUI_TOPWIN = FUI_Window( 0, 0, App\W, GUI_VARIABLE_TOPBAR_HEIGHT, "", 0, 2 )
    Global GUI_TOPWIN_MENUFILE = FUI_MenuTitle( GUI_TOPWIN, "File" )
		Global GUI_MENUFILE_OPEN     = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Open", "" )
		Global GUI_MENUFILE_SAVE     = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Save", "" )
		Global GUI_MENUFILE_EXPORT   = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Export", "" )
		Global GUI_MENUFILE_NEW      = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "New", "" )
	  	Global GUI_MENUFILE_Exit     = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Exit", "" )
        Global GUI_TOPWIN_MENUHELP = FUI_MenuTitle( GUI_TOPWIN, "Help" )
		Global GUI_MENUHELP_CONTROL     = FUI_MenuItem( GUI_TOPWIN_MENUHELP, "Controls", "" )
        Global GUI_RIGHTWIN = FUI_Window( App\W - GUI_VARIABLE_RIGHTBAR_WIDTH, GUI_VARIABLE_TOPBAR_HEIGHT - 1, GUI_VARIABLE_LEFTBAR_WIDTH, app\h, "", 0, 0 )

       Global GUI_RIGHTWIN_TRUNKSCALE  = FUI_Radio( GUI_RIGHTWIN, 5, 10,  "Trunk Scale", 0 ,1)
       Global GUI_RIGHTWIN_TRUNKSLIDEX  = FUI_Radio( GUI_RIGHTWIN, 5, 25,  "Trunk Slide X", 0 ,1)
       Global GUI_RIGHTWIN_TRUNKSLIDEY  = FUI_Radio( GUI_RIGHTWIN, 5, 40, "Trunk Slide Y", 0 ,1)
       Global GUI_RIGHTWIN_TRUNKSLIDEZ  = FUI_Radio( GUI_RIGHTWIN, 5, 55, "Trunk Slide Z", 0 ,1)

	   Global GUI_RIGHTWIN_BARKCanvas = FUI_ImageBox( GUI_RIGHTWIN, 5, 80, 100, 100)
	   Global GUI_RIGHTWIN_BarkTexture = FUI_Button( GUI_RIGHTWIN, 5, 190, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Select bark texture", 0, 0 )
	   Global GUI_RIGHTWIN_BarkUV_NEG = FUI_Button( GUI_RIGHTWIN, 5, 215, 40, 20, "UV -", 0, 0 )
	   Global GUI_RIGHTWIN_BarkUV_POS = FUI_Button( GUI_RIGHTWIN, 93, 215, 40, 20, "UV +", 0, 0 )

  	   Global GUI_RIGHTWIN_Label1= FUI_Label( GUI_RIGHTWIN, 5, 245, " Branch / Leaf options " )

	   Global GUI_RIGHTWIN_LeafCanvas  = FUI_ImageBox( GUI_RIGHTWIN, 5, 270, 100, 100)
	   Global GUI_RIGHTWIN_LeafTexture = FUI_Button( GUI_RIGHTWIN, 5, 380, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 16, "Select leaf texture", 0, 0 )
	   Global GUI_RIGHTWIN_LeafShape = FUI_Button( GUI_RIGHTWIN, 5, 400, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 16, "Select shape", 0, 0 )

	   Global GUI_RIGHTWIN_PrevLeaf = FUI_Button( GUI_RIGHTWIN, 110, 270, 20,20, "-", 0, 0 )
	   Global GUI_RIGHTWIN_NEXTLeaf = FUI_Button( GUI_RIGHTWIN, 110, 300, 20,20, "+", 0, 0 )

       Global GUI_RIGHTWIN_EFFECTALL  = FUI_Radio( GUI_RIGHTWIN, 5, 420,  "Effect all", 0 ,2)
       Global GUI_RIGHTWIN_UPRIGHT  = FUI_Radio( GUI_RIGHTWIN, 5, 435,  "Upright", 1 ,1)
       Global GUI_RIGHTWIN_RANDOM  = FUI_Radio( GUI_RIGHTWIN, 5, 450, "Random", 0 ,1)



	   Global GUI_RIGHTWIN_LEFT = FUI_Button( GUI_RIGHTWIN, 5, 470, 60,16, "Left", 0, 0 )
	   Global GUI_RIGHTWIN_RIGHT = FUI_Button( GUI_RIGHTWIN, 70, 470, 60,16, "Right", 0, 0 )

	   Global GUI_RIGHTWIN_Up = FUI_Button( GUI_RIGHTWIN, 5, 490, 60,16, "Up", 0, 0 )
	   Global GUI_RIGHTWIN_Down = FUI_Button( GUI_RIGHTWIN, 70, 490, 60,16, "Down", 0, 0 )

	   Global GUI_RIGHTWIN_Forward = FUI_Button( GUI_RIGHTWIN, 5, 510, 60,16, "Forward", 0, 0 )
	   Global GUI_RIGHTWIN_Back = FUI_Button( GUI_RIGHTWIN, 70, 510, 60,16, "Back", 0, 0 )

	   Global GUI_RIGHTWIN_PitchNeg = FUI_Button( GUI_RIGHTWIN, 5, 530, 60,16, "Pitch -", 0, 0 )
	   Global GUI_RIGHTWIN_PitchPos = FUI_Button( GUI_RIGHTWIN, 70, 530, 60,16, "Pitch +", 0, 0 )

	   Global GUI_RIGHTWIN_YawNeg = FUI_Button( GUI_RIGHTWIN, 5, 550, 60,16, "Yaw -", 0, 0 )
	   Global GUI_RIGHTWIN_YawPos = FUI_Button( GUI_RIGHTWIN, 70, 550, 60,16, "Yaw +", 0, 0 )

	   Global GUI_RIGHTWIN_RollNeg = FUI_Button( GUI_RIGHTWIN, 5, 570, 60,16, "Roll -", 0, 0 )
	   Global GUI_RIGHTWIN_RollPos = FUI_Button( GUI_RIGHTWIN, 70, 570, 60,16, "Roll +", 0, 0 )

	   Global GUI_RIGHTWIN_ScaleNeg = FUI_Button( GUI_RIGHTWIN, 5, 590, 60,16, "Scale -", 0, 0 )
	   Global GUI_RIGHTWIN_ScalePos = FUI_Button( GUI_RIGHTWIN, 70, 590, 60,16, "Scale +", 0, 0 )

	   Global GUI_RIGHTWIN_CopyLeaf  = FUI_Button( GUI_RIGHTWIN, 5, 620, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Copy leaf", 0, 0 )
	   Global GUI_RIGHTWIN_PaintLeaf = FUI_Button( GUI_RIGHTWIN, 5, 650, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Paint leaf", 0, 0 )
	   Global GUI_RIGHTWIN_DeleteLeaf = FUI_Button( GUI_RIGHTWIN, 5, 690, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Delete leaf", 0, 0 )








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
    FlushMouse()
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


Function FUI_Choose(msg$,ch1$,ch2$,ch3$,ch4$)
    FlushMouse()
	TWidth = 200
	THeight = 200
 	GUI_CONTROLSWINDOW = FUI_Window( 0, 0, TWidth, THeight, "Choose", 0, 1, 1 )
		GUI_CONTROLSWINDOW_LABEL1 = FUI_Label( GUI_CONTROLSWINDOW, 10, 10, msg$ )
   	    GUI_CONTROLSWINDOW_LABEL2 = FUI_Label( GUI_CONTROLSWINDOW, 10, 70, "")
		GUI_CONTROLSWINDOW_BUTTON_1 = FUI_Button( GUI_CONTROLSWINDOW, 20, 35, 160, 20, ch1$ )		
		GUI_CONTROLSWINDOW_BUTTON_2 = FUI_Button( GUI_CONTROLSWINDOW, 20, 60, 160, 20, ch2$ )
		GUI_CONTROLSWINDOW_BUTTON_3 = FUI_Button( GUI_CONTROLSWINDOW, 20, 85, 160, 20, ch3$ )		
		GUI_CONTROLSWINDOW_BUTTON_4 = FUI_Button( GUI_CONTROLSWINDOW, 20, 110, 160, 20, ch4$ )
		GUI_CONTROLSWINDOW_BUTTON_cancel = FUI_Button( GUI_CONTROLSWINDOW, 20, 135, 160, 20, "Cancel" )		
	FUI_ModalWindow( GUI_CONTROLSWINDOW )
	FUI_CenterWindow( GUI_CONTROLSWINDOW )
    FUI_SendMessage(gui_controlswindow,m_open)
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
		 		Case GUI_CONTROLSWINDOW_BUTTON_1
		 			tClose = True
					FUI_DeleteGadget( GUI_CONTROLSWINDOW )
                    Return  1
		 		Case GUI_CONTROLSWINDOW_BUTTON_2
		 			tClose = True
					FUI_DeleteGadget( GUI_CONTROLSWINDOW )
                    Return  2
		 		Case GUI_CONTROLSWINDOW_BUTTON_3
		 			tClose = True
					FUI_DeleteGadget( GUI_CONTROLSWINDOW )
                    Return  3
		 		Case GUI_CONTROLSWINDOW_BUTTON_4
		 			tClose = True
					FUI_DeleteGadget( GUI_CONTROLSWINDOW )
                    Return  4
		 		Case GUI_CONTROLSWINDOW_BUTTON_cancel
		 			tClose = True
					FUI_DeleteGadget( GUI_CONTROLSWINDOW )
                    Return  0
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
		GUI_CONTROLSWINDOW_LABEL1 = FUI_Label( GUI_CONTROLSWINDOW, 10, 10, "Programmer/Author   : Jeff Frazier  " )
		GUI_CONTROLSWINDOW_LABEL2 = FUI_Label( GUI_CONTROLSWINDOW, 10, 30, "__________________________________" )
		GUI_CONTROLSWINDOW_LABEL3 = FUI_Label( GUI_CONTROLSWINDOW, 10, 50, "Http://www.realmcrafter.com" )
   	    GUI_CONTROLSWINDOW_LABEL4 = FUI_Label( GUI_CONTROLSWINDOW, 10, 70, "__________________________________" )
   	    GUI_CONTROLSWINDOW_LABEL5 = FUI_Label( GUI_CONTROLSWINDOW, 10, 90, "ARROW KEYS  = Rotate camera")
   	    GUI_CONTROLSWINDOW_LABEL6 = FUI_Label( GUI_CONTROLSWINDOW, 10, 110, "A / Z                  = Move camera")		
   	    GUI_CONTROLSWINDOW_LABEL7 = FUI_Label( GUI_CONTROLSWINDOW, 10, 130, "MOUSEWHEEL  = Select leaf chunk")		
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
		GUI_ABOUTWINDOW_LABEL1 = FUI_Label( GUI_ABOUTWINDOW, 10, 10, "Realm Crafter Architect" )
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