Global thispath$ = CurrentDir$()
If Right$(thispath$,1)<>"\" Then thispath$ = thispath$+"\"


    ;main top menu
	Global GUI_TOPWIN = FUI_Window( 0, 0, App\W, GUI_VARIABLE_TOPBAR_HEIGHT, "", 0, 2 )
    Global GUI_TOPWIN_MENUFILE = FUI_MenuTitle( GUI_TOPWIN, "File" )
		Global GUI_MENUFILE_OPEN     = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Open", "" )
		Global GUI_MENUFILE_SAVE     = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Save", "" )
		Global GUI_MENUFILE_EXPORT   = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Export", "" )
		Global GUI_MENUFILE_NEW      = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "New", "" )
	  	Global GUI_MENUFILE_Exit     = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Exit", "" )
    Global GUI_TOPWIN_MENUMODE = FUI_MenuTitle( GUI_TOPWIN, "Modes" )
		Global GUI_MENUMODE_ACTIONS  = FUI_MenuItem( GUI_TOPWIN_MENUMODE, "Actions", "F1" )
		Global GUI_MENUMODE_MODELS  = FUI_MenuItem( GUI_TOPWIN_MENUMODE, "Models", "F2" )
		Global GUI_MENUMODE_LIGHTS  = FUI_MenuItem( GUI_TOPWIN_MENUMODE, "Lights", "F3" )
    Global GUI_RIGHTWIN = FUI_Window( App\W - GUI_VARIABLE_RIGHTBAR_WIDTH, GUI_VARIABLE_TOPBAR_HEIGHT - 1, GUI_VARIABLE_LEFTBAR_WIDTH, app\h, "", 0, 0 )

	Global GUI_RIGHTWIN_CAMSPEEDL= FUI_Label( GUI_RIGHTWIN, 5, app\h-140, "Camera speed" )
	Global GUI_RIGHTWIN_CAMSPEED = FUI_Slider( GUI_RIGHTWIN, 5, APP\H-125, 130, 16,1,20,4)

   ;mode actions
       Global GUI_RIGHTWIN_ACTIONMOVE  = FUI_Radio( GUI_RIGHTWIN, 5, 5,  "Action Move", 1 ,1)
       Global GUI_RIGHTWIN_ACTIONSCALE  = FUI_Radio( GUI_RIGHTWIN, 5, 20,  "Action Scale", 0 ,1)
       Global GUI_RIGHTWIN_ACTIONROTATE  = FUI_Radio( GUI_RIGHTWIN, 5, 35,  "Action Rotate", 0 ,1)
       ;---
  	   Global GUI_RIGHTWIN_SCALEXYZL= FUI_Label( GUI_RIGHTWIN, 5, 70, "Scale XYZ" )
       Global GUI_RIGHTWIN_sCALEXYZ_POS = FUI_Button(GUI_RIGHTWIN, 90, 68, 16, 16, "+" )
       Global GUI_RIGHTWIN_sCALEXYZ_NEG = FUI_Button(GUI_RIGHTWIN, 70, 68, 16, 16, "-" )
       ;----
  	   Global GUI_RIGHTWIN_ACTIONXL= FUI_Label( GUI_RIGHTWIN, 5, 100, "Action X Axis" )
       Global GUI_RIGHTWIN_ACTIONX_POS = FUI_Button(GUI_RIGHTWIN, 30, 120, 20, 20, "+" )
       Global GUI_RIGHTWIN_ACTIONX_NEG = FUI_Button(GUI_RIGHTWIN, 55, 120, 20, 20, "-" )
       ;----
  	   Global GUI_RIGHTWIN_ACTIONYL= FUI_Label( GUI_RIGHTWIN, 5, 150, "Action Y Axis" )
       Global GUI_RIGHTWIN_ACTIONY_POS = FUI_Button(GUI_RIGHTWIN, 30, 170, 20, 20, "+" )
       Global GUI_RIGHTWIN_ACTIONY_NEG = FUI_Button(GUI_RIGHTWIN, 55, 170, 20, 20, "-" )
       ;----
  	   Global GUI_RIGHTWIN_ACTIONZL= FUI_Label( GUI_RIGHTWIN, 5, 200, "Action Z Axis" )
       Global GUI_RIGHTWIN_ACTIONZ_POS = FUI_Button(GUI_RIGHTWIN, 30, 220, 20, 20, "+" )
       Global GUI_RIGHTWIN_ACTIONZ_NEG = FUI_Button(GUI_RIGHTWIN, 55, 220, 20, 20, "-" )
       ;----
       Global GUI_RIGHTWIN_GRIDUP   = FUI_Button(GUI_RIGHTWIN, 5, 260, 60, 16, "Grid up" )
       Global GUI_RIGHTWIN_GRIDDOWN = FUI_Button(GUI_RIGHTWIN, 70, 260, 60, 16, "Grid down" )
       ;----
       Global GUI_RIGHTWIN_COPYMODEL    = FUI_Button(GUI_RIGHTWIN, 4, 300, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 16, "Copy model" )
       Global GUI_RIGHTWIN_DeleteMODEL   = FUI_Button(GUI_RIGHTWIN, 4, 320, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 16, "Delete model" )
       ;----
       Global GUI_RIGHTWIN_SNAPTOGRID = FUI_CheckBox( GUI_RIGHTWIN, 5, 360, "Snap to grid ", 1 )
   ;mode MODELS
       Global GUI_RIGHTWIN_SELECTMODEL   = FUI_Button(GUI_RIGHTWIN, 4, 10, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 16, "Select Model" )
       Global GUI_RIGHTWIN_COMBINEMODELS = FUI_Button(GUI_RIGHTWIN, 4, 35, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 16, "Combine all models" )
       Global GUI_RIGHTWIN_SNAPSIZEL     = FUI_Label( GUI_rightWIN, 5, 85, "Snap size" )
       Global GUI_RIGHTWIN_SNAPSIZE      = FUI_Spinner( GUI_RIGHTWIN, 5, 100,GUI_VARIABLE_RIGHTBAR_WIDTH - 10 , 20, 2, 32, 2, 2, DTYPE_INTEGER, "" )
       Global GUI_RIGHTWIN_REFORMMODEL   = FUI_Button(GUI_RIGHTWIN, 4, 140, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 16, "Reform model" )
   ;mode lights
   		Global GUI_RIGHTWIN_AMBCanvas = FUI_ImageBox( GUI_RIGHTWIN, 5, 5, 50, 20)
		Global GUI_RIGHTWIN_AMBCOLOR  = FUI_Button( GUI_RIGHTWIN, 5, 30, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Pick ambient color", 0, 0 )
   		Global GUI_RIGHTWIN_LGTCanvas = FUI_ImageBox( GUI_RIGHTWIN, 5, 70, 50, 20)
		Global GUI_RIGHTWIN_LGTCOLOR  = FUI_Button( GUI_RIGHTWIN, 5, 95, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Pick light color", 0, 0 )
		Global GUI_RIGHTWIN_RANGEL    = FUI_Label( GUI_RIGHTWIN, 5, 130, "Light range" )
 		Global GUI_RIGHTWIN_RANGE         = FUI_Slider( GUI_RIGHTWIN, 5, 150, 130, 12,10,550,25)
        Global GUI_RIGHTWIN_UPDATELIGHT   = FUI_Button(GUI_RIGHTWIN, 4, 180, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 16, "Update light" )
        Global GUI_RIGHTWIN_LIGHTMAP   = FUI_Button(GUI_RIGHTWIN, 4, 205, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 16, "Lightmap scene" )
        Global GUI_RIGHTWIN_DROPLIGHT = FUI_CheckBox( GUI_RIGHTWIN, 5, 235, "Place light ", 0 )
        ;;;
     	Global GUI_RIGHTWIN_RESETCAM = FUI_Button( GUI_rightwin, 4, app\h-100, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Reset camera", 0, 0 )
  	;HELP
    Global GUI_TOPWIN_HELP = FUI_MenuTitle( GUI_TOPWIN, "Help" )
		Global GUI_HELP_CONTROLS = FUI_MenuItem( GUI_TOPWIN_HELP, "Controls" )
		Global GUI_HELP_ABOUT    = FUI_MenuItem( GUI_TOPWIN_HELP, "About" )
     
  
       showmode 1



;test mode
;Global CAM=CreateCamera()
;While Not KeyDown(1)
         ;test buttons ect. 
;		 For e.Event = Each Event
;		 	Select e\EventID
;                 Case GUI_HELP_CONTROLS
;                      Controlswindow()
;				 Case GUI_MODEMODEL_TREE
;                      FUI_SendMessage( GUI_TREEWIN, M_OPEN )
;                 Case GUI_HELP_ABOUT
;                      Aboutwindow()
;                 Case GUI_MBRUSH_CLOSE
;                      FUI_SendMessage( GUI_MBWIN, M_CLOSE )
;                 Case GUI_TOPWIN_MBRUSH
;                      FUI_SendMessage( GUI_MBWIN, M_OPEN )
;                 Case GUI_TOPWIN_MOVEMENT
;                     FUI_SendMessage( GUI_MOVEWIN ,M_OPEN )
;                 Case GUI_PED_EDITMODE
;                  showmode(2)
;                 Case GUI_PED_MODELMODE
;                  showmode(3)
;                 Case GUI_PED_PAINTMODE
;                  showmode(1)
;            End Select                   
; 	 	 Delete e
;         Next 

;			CameraProjMode( Cam, False )
;			CameraProjMode( App\Cam, True )
; 		    FUI_Update( )
;	 		CameraViewport( Cam, 0, 0, App\W, App\H )
;			RenderWorld( )
;               If sysmsg$<>"" And sysmsgtimer#>MilliSecs() Then
; 				   Color( 15, 15, 15 )
;    	           Text 76,71,sysmsg$          
;			  	   Color( 255, 255, 255 )
;                 Text 75,70,sysmsg$          
;                  EndIf
;			Flip( GFX_VARIABLE_VSYNC )
;        Cls( )
;         positionwindows()
		;#End Region
;      Wend


Function PositionWindows()
            ;keep gadget windows in frame of main window
               
             If GFX_WINDOW_MODE <> 1

;				FUI_SendMessage( GUI_TOPWIN, M_SETSIZE_W, App\W )

;	    	    FUI_SendMessage( GUI_RIGHTWIN, M_SETPOS_X,app\w-(GUI_VARIABLE_LEFTBAR_WIDTH))
; 	    	    FUI_SendMessage( GUI_RIGHTWIN, M_SETSIZE_H,app\H)

; 	    	    FUI_SendMessage( GUI_SHAPEWIN, M_SETPOS_Y,app\h-(140))


;                Else ; else this is fullscreen

;                FUI_SendMessage( GUI_TOPWIN, M_SETSIZE_W, gfx_window_width )

; 	    	    FUI_SendMessage( GUI_RIGHTWIN, M_SETPOS_X,gfx_window_width-(GUI_VARIABLE_LEFTBAR_WIDTH))
; 	    	    FUI_SendMessage( GUI_RIGHTWIN, M_SETSIZE_H,gfx_window_HEIGHT)

; 	    	    FUI_SendMessage( GUI_SHAPEWIN, M_SETPOS_Y,gfx_window_HEIGHT-(120))

                EndIf
End Function 


Function ShowMode(Mode) 
;1=actions
       ;Sub content on mode window : actions
       FUI_HideGadget(GUI_RIGHTWIN_ACTIONMOVE)
       FUI_HideGadget(GUI_RIGHTWIN_ACTIONScale)
       FUI_HideGadget(GUI_RIGHTWIN_ACTIONrotate)
       FUI_HideGadget(GUI_RIGHTWIN_scalexyzL)
       FUI_HideGadget(GUI_RIGHTWIN_scalexyz)
       FUI_HideGadget(GUI_RIGHTWIN_sCALEXYZ_POS)
       FUI_HideGadget(GUI_RIGHTWIN_sCALEXYZ_NEG)
       FUI_HideGadget(GUI_RIGHTWIN_ActionXL)
       FUI_HideGadget(GUI_RIGHTWIN_ActionX_Pos)
	   FUI_HideGadget(GUI_RIGHTWIN_ActionX_Neg)
       FUI_HideGadget(GUI_RIGHTWIN_ActionYL)
       FUI_HideGadget(GUI_RIGHTWIN_ActionY_Pos)
	   FUI_HideGadget(GUI_RIGHTWIN_ActionY_Neg)
       FUI_HideGadget(GUI_RIGHTWIN_ActionZL)
       FUI_HideGadget(GUI_RIGHTWIN_ActionZ_Pos)
	   FUI_HideGadget(GUI_RIGHTWIN_ActionZ_Neg)
       FUI_HideGadget(GUI_RIGHTWIN_GRIDUP)
       FUI_HideGadget(GUI_RIGHTWIN_GRIDDOWN)
       FUI_HideGadget(GUI_RIGHTWIN_COPYMODEL)
       FUI_HideGadget(GUI_RIGHTWIN_DELETEMODEL)
       FUI_HideGadget(GUI_RIGHTWIN_SNAPTOGRID)
       ;;;
       FUI_HideGadget(GUI_RIGHTWIN_SELECTMODEL)
       FUI_HideGadget(GUI_RIGHTWIN_COMBINEMODELS)
       FUI_HideGadget(GUI_RIGHTWIN_SNAPSIZEL)
       FUI_HideGadget(GUI_RIGHTWIN_SNAPSIZE)
       FUI_HideGadget(GUI_RIGHTWIN_REFORMMODEL)
       ;;; 
       FUI_HideGadget(GUI_RIGHTWIN_AMBCanvas)
       FUI_HideGadget(GUI_RIGHTWIN_AMBColor)
       FUI_HideGadget(GUI_RIGHTWIN_LGTCanvas)
       FUI_HideGadget(GUI_RIGHTWIN_LGTcolor)
       FUI_HideGadget(GUI_RIGHTWIN_RangeL)
       FUI_HideGadget(GUI_RIGHTWIN_Range)
       FUI_HideGadget(GUI_RIGHTWIN_UpdateLight)
       FUI_HideGadget(GUI_RIGHTWIN_Lightmap)
       FUI_HideGadget(GUI_RIGHTWIN_Droplight)


Select mode
 Case 1
       FUI_HideGadget(GUI_RIGHTWIN_scalexyzL)
       FUI_ShowGadget(GUI_RIGHTWIN_ACTIONMOVE)
       FUI_ShowGadget(GUI_RIGHTWIN_ACTIONScale)
       FUI_ShowGadget(GUI_RIGHTWIN_ACTIONrotate)
       FUI_ShowGadget(GUI_RIGHTWIN_scalexyz)
       FUI_ShowGadget(GUI_RIGHTWIN_sCALEXYZ_POS)
       FUI_ShowGadget(GUI_RIGHTWIN_sCALEXYZ_NEG)
       FUI_ShowGadget(GUI_RIGHTWIN_ActionXL)
       FUI_ShowGadget(GUI_RIGHTWIN_ActionX_Pos)
	   FUI_ShowGadget(GUI_RIGHTWIN_ActionX_Neg)
       FUI_ShowGadget(GUI_RIGHTWIN_ActionYL)
       FUI_ShowGadget(GUI_RIGHTWIN_ActionY_Pos)
	   FUI_ShowGadget(GUI_RIGHTWIN_ActionY_Neg)
       FUI_ShowGadget(GUI_RIGHTWIN_ActionZL)
       FUI_ShowGadget(GUI_RIGHTWIN_ActionZ_Pos)
	   FUI_ShowGadget(GUI_RIGHTWIN_ActionZ_Neg)
       FUI_ShowGadget(GUI_RIGHTWIN_GRIDUP)
       FUI_ShowGadget(GUI_RIGHTWIN_GRIDDOWN)
       FUI_ShowGadget(GUI_RIGHTWIN_COPYMODEL)
       FUI_ShowGadget(GUI_RIGHTWIN_DELETEMODEL)
       FUI_ShowGadget(GUI_RIGHTWIN_SNAPTOGRID)
 Case 2
       FUI_ShowGadget(GUI_RIGHTWIN_SELECTMODEL)
       FUI_ShowGadget(GUI_RIGHTWIN_COMBINEMODELS)
       FUI_ShowGadget(GUI_RIGHTWIN_SNAPSIZEL)
       FUI_ShowGadget(GUI_RIGHTWIN_SNAPSIZE)
       FUI_ShowGadget(GUI_RIGHTWIN_REFORMMODEL)
 Case 3
       FUI_ShowGadget(GUI_RIGHTWIN_AMBCanvas)
       FUI_ShowGadget(GUI_RIGHTWIN_AMBColor)
       FUI_ShowGadget(GUI_RIGHTWIN_LGTCanvas)
       FUI_ShowGadget(GUI_RIGHTWIN_LGTcolor)
       FUI_ShowGadget(GUI_RIGHTWIN_RangeL)
       FUI_ShowGadget(GUI_RIGHTWIN_Range)
       FUI_ShowGadget(GUI_RIGHTWIN_UpdateLight)
       FUI_ShowGadget(GUI_RIGHTWIN_Lightmap)
       FUI_ShowGadget(GUI_RIGHTWIN_Droplight)
End Select 
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
   	    GUI_CONTROLSWINDOW_LABEL6 = FUI_Label( GUI_CONTROLSWINDOW, 10, 110, "Control  +  Right Mouse button = Drop Item")		
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