Global thispath$ = SystemProperty$("appdir")
If Lower$(Right$(thispath$,4))="bin\"
; Running from the IDE
thispath$ = CurrentDir$()
End If
If Right$(thispath$,1)<>"\" Then thispath$ = thispath$+"\"
ChangeDir thispath$

If testing=True Then 
ChangeDir thispath$
Else
ChangeDir thispath$+"data\default project\"
EndIf

 ;main top menu
	Global GUI_TOPWIN                = FUI_Window( 0, 0, App\W, GUI_VARIABLE_TOPBAR_HEIGHT, "", 0, 2 )
    Global GUI_TOPWIN_MENUFILE       = FUI_MenuTitle( GUI_TOPWIN, "File" )
		Global GUI_MENUFILE_OPEN     = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Open", "" )
		Global GUI_MENUFILE_SAVE     = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Save", "" )
		Global GUI_MENUFILE_EXPORT   = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Export", "" )
		Global GUI_MENUFILE_NEW      = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "New", "" )
	  	Global GUI_MENUFILE_Exit     = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Exit", "" )
    Global GUI_TOPWIN_MENUSHAPE      = FUI_MenuTitle( GUI_TOPWIN, "Edit" )
		Global GUI_MENUSHAPE_NEW     = FUI_MenuItem( GUI_TOPWIN_MENUSHAPE, "New shape", "")
		Global GUI_MENUSHAPE_CARVE   = FUI_MenuItem( GUI_TOPWIN_MENUSHAPE, "Carve", "C")
		Global GUI_MENUSHAPE_COMBINE = FUI_MenuItem( GUI_TOPWIN_MENUSHAPE, "Combine", "B")
		Global GUI_MENUSHAPE_LEVEL = FUI_MenuItem( GUI_TOPWIN_MENUSHAPE, "Level", "L")
		Global GUI_MENUSHAPE_UNDO    = FUI_MenuItem( GUI_TOPWIN_MENUSHAPE, "Undo", "U")
    Global GUI_TOPWIN_MENUALTER      = FUI_MenuTitle( GUI_TOPWIN, "Alter shape" )
		Global GUI_MENUALTER_LEFT    = FUI_MenuItem( GUI_TOPWIN_MENUALTER,  "Roll left", "Left arrow")
		Global GUI_MENUALTER_RIGHT    = FUI_MenuItem( GUI_TOPWIN_MENUALTER, "Roll right", "Right Arrow")
		Global GUI_MENUALTER_FORWARD   = FUI_MenuItem( GUI_TOPWIN_MENUALTER,"Roll forward", "Up arrow")
		Global GUI_MENUALTER_BACK    = FUI_MenuItem( GUI_TOPWIN_MENUALTER,  "Roll back", "Down arrow")
		Global GUI_MENUALTER_SUP    = FUI_MenuItem( GUI_TOPWIN_MENUALTER,   "Scale up", "+")
		Global GUI_MENUALTER_SDOWN    = FUI_MenuItem( GUI_TOPWIN_MENUALTER, "Scale down", "-")

    Global GUI_TOPWIN_MENUABOUT      = FUI_MenuTitle( GUI_TOPWIN, "About" )
		Global GUI_MENUABOUT_INFO    = FUI_MenuItem( GUI_TOPWIN_MENUABOUT, "Info", "")

  	Global GUI_RIGHTWIN = FUI_Window( App\W - GUI_VARIABLE_RIGHTBAR_WIDTH, GUI_VARIABLE_TOPBAR_HEIGHT - 1, GUI_VARIABLE_LEFTBAR_WIDTH, app\h, "", 0, 0 )
   		Global GUI_RIGHTWIN_AMBCanvas = FUI_ImageBox( GUI_RIGHTWIN, 5, 5, 50, 20)
		Global GUI_RIGHTWIN_AMBCOLOR  = FUI_Button( GUI_RIGHTWIN, 5, 30, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Pick ambient color", 0, 0 )
   		Global GUI_RIGHTWIN_LGTCanvas = FUI_ImageBox( GUI_RIGHTWIN, 5, 70, 50, 20)
		Global GUI_RIGHTWIN_LGTCOLOR  = FUI_Button( GUI_RIGHTWIN, 5, 95, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Pick light color", 0, 0 )
		Global GUI_RIGHTWIN_RANGEL    = FUI_Label( GUI_RIGHTWIN, 5, 130, "Light range" )
 		Global GUI_RIGHTWIN_RANGE     = FUI_Slider( GUI_RIGHTWIN, 5, 150, 130, 12,70,200,70)
		Global GUI_RIGHTWIN_RESETLIGHT  = FUI_Button( GUI_RIGHTWIN, 5, 180, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Reset lightmap", 0,0)
		Global GUI_RIGHTWIN_LIGHTMAP    = FUI_Button( GUI_RIGHTWIN, 5, 210, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Lightmap Cave", 0, 0)
		Global GUI_RIGHTWIN_RESETCAMERA = FUI_Button( GUI_RIGHTWIN, 5, 240, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Reset camera", 0, 0)
   		Global GUI_RIGHTWIN_TEXTURE = FUI_Button( GUI_RIGHTWIN, 6, 270, 127, 125,"",0,0)
Dim Actionbutton(4)
Dim Roombutton(6)
Dim Tunnelbutton(4)
Dim Objectbutton(2)
btsize=42
	Global GUI_SHAPEWIN=FUI_Window( 0, app\h-140, 450, 140, "", 0, 0 )
    For i=1 To 2
     Objectbutton(i)=FUI_Button( GUI_Shapewin, 5+(i-1)*(btsize-1), 5, btsize,btsize, "", 0,0)
    Next

    For i=1 To 6
     Tunnelbutton(i)=FUI_Button( GUI_Shapewin, 5+(i-1)*(btsize-1), 4+(btsize*1), btsize,btsize, "", 0,0)
    Next

    For i=1 To 6
     Roombutton(i)=FUI_Button( GUI_Shapewin, 5+(i-1)*(btsize-1), 3+(btsize*2), btsize,btsize, "", 0,0)
    Next

    COUNT=0
    For i=1 To 3
         If I<>2 Then  
		 COUNT=COUNT+1
         ACTIONbutton(COUNT)=FUI_Button( GUI_Shapewin, 300+(i-1)*(btsize-1), 5, btsize,btsize, "", 0,0)
         EndIf
    Next

    I=1
    For COUNT=3 To 5
     ACTIONbutton(COUNT)=FUI_Button( GUI_Shapewin, 300+(i-1)*(btsize-1), 3+(btsize*2), btsize,btsize, "", 0,0)
    I=I+1
    Next

Global GUI_ABOUTWIN=FUI_Window( 0, 0, 256, 256, "", 0, 1,1 )
Global GUI_ABOUT_L1  = FUI_Label( GUI_ABOUTWIN, 5, 5, "Programmer/Author   : Jeff Frazier" )
Global GUI_ABOUT_L2  = FUI_Label( GUI_ABOUTWIN, 5, 15,"__________________________________")
Global GUI_ABOUT_L3  = FUI_Label( GUI_ABOUTWIN, 5, 35,"Http://www.realmcrafter.com")
Global GUI_ABOUT_L4  = FUI_Label( GUI_ABOUTWIN, 5, 55,"Http://www.solstargames.com")
Global GUI_ABOUT_L5  = FUI_Label( GUI_ABOUTWIN, 5, 75,"CONTROLS.......")
Global GUI_ABOUT_L6  = FUI_Label( GUI_ABOUTWIN, 5, 95,"U = undo :  C = Carve : B = Combine")
Global GUI_ABOUT_L7  = FUI_Label( GUI_ABOUTWIN, 5,115,"L = Level")
Global GUI_ABOUT_L8  = FUI_Label( GUI_ABOUTWIN, 5,135,"Hold spacebar and mouselook to place shape")
Global GUI_ABOUT_L9  = FUI_Label( GUI_ABOUTWIN, 5,155,"Hold Alt and mouselook to preview placement")
Global GUI_ABOUT_L0  = FUI_Label( GUI_ABOUTWIN, 5,175,"Ctrl+Left Mouse = Drop Light")

FUI_ModalWindow(GUI_ABOUTWIN )
FUI_CenterWindow(GUI_ABOUTWIN )
FUI_SendMessage(GUI_ABOUTWIN,M_cLOSE)
applyimagestobuttons()

Function Perc#(pv#,ofv#)
 V1#=ofv/pv
 Return v1
End Function 


Function PositionWindows()
            ;keep gadget windows in frame of main window
               
             If GFX_WINDOW_MODE <> 1

				FUI_SendMessage( GUI_TOPWIN, M_SETSIZE_W, App\W )

 	    	    FUI_SendMessage( GUI_RIGHTWIN, M_SETPOS_X,app\w-(GUI_VARIABLE_LEFTBAR_WIDTH))
 	    	    FUI_SendMessage( GUI_RIGHTWIN, M_SETSIZE_H,app\H)

 	    	    FUI_SendMessage( GUI_SHAPEWIN, M_SETPOS_Y,app\h-(140))


                Else ; else this is fullscreen

                FUI_SendMessage( GUI_TOPWIN, M_SETSIZE_W, gfx_window_width )

 	    	    FUI_SendMessage( GUI_RIGHTWIN, M_SETPOS_X,gfx_window_width-(GUI_VARIABLE_LEFTBAR_WIDTH))
 	    	    FUI_SendMessage( GUI_RIGHTWIN, M_SETSIZE_H,gfx_window_HEIGHT)

 	    	    FUI_SendMessage( GUI_SHAPEWIN, M_SETPOS_Y,gfx_window_HEIGHT-(120))

                EndIf
End Function 

Function FUI_ApplyTexture(ID,TEX,scale#=.75)
    ScaleTexture tex,scale,scale
	gad.button = Object.button( ID )
	If gad <> Null
        EntityTexture gad\mesh,tex,0,1          
	EndIf
End Function 

Function FUI_Notify(msg$,YS$="OK")
	TWidth = 300
	THeight = 100
 	GUI_CONTROLSWINDOW = FUI_Window( 0, 0, TWidth, THeight, "Notify", 0, 1, 1 )
		GUI_CONTROLSWINDOW_LABEL1 = FUI_Label( GUI_CONTROLSWINDOW, 10, 10, msg$ )
   	    GUI_CONTROLSWINDOW_LABEL2 = FUI_Label( GUI_CONTROLSWINDOW, 10, 70, "")
		GUI_CONTROLSWINDOW_BUTTON_OK = FUI_Button( GUI_CONTROLSWINDOW, 10, 35, 80, 20, "OK" )		
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




Function APPLYIMAGESTOBUTTONS()
If FileType("Data\RCcaves\Carve_Icon.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Carve_Icon.jpg")
  Fui_applyTEXTURE(Actionbutton(1),img,.62)
  FreeTexture img
EndIf
;--
If FileType("Data\RCcaves\Combine_Icon.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\COmbine_Icon.jpg")
  fui_applytexture(actionbutton(2),img,.62)
  FreeTexture img
EndIf
;--
If FileType("Data\RCcaves\Level_Icon.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Level_Icon.jpg")
  fui_applytexture(actionbutton(3),img,.62)
  FreeTexture img
EndIf
;--
If FileType("Data\RCcaves\Undo_Icon.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Undo_Icon.jpg")
  fui_applytexture(actionbutton(4),img,.62)
  FreeTexture img
EndIf
;--
If FileType("Data\RCcaves\Compass_Icon.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Compass_Icon.jpg")
  fui_applytexture(actionbutton(5),img,.62)
  FreeTexture img
EndIf
;--


If FileType("Data\RCcaves\Object_Icon1.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Object_Icon1.jpg")
  fui_applytexture(objectbutton(1),img,.62)
  FreeImage img
EndIf
;--
If FileType("Data\RCcaves\Object_Icon2.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Object_Icon2.jpg")
  fui_applytexture(objectbutton(2),img,.62)
  FreeImage img
EndIf
;-----------------------
;----------------------
If FileType("Data\RCcaves\Room_Icon1.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Room_Icon1.jpg")
  fui_applytexture(roombutton(1),img,.62)
  FreeImage img
EndIf
;--
If FileType("Data\RCcaves\Room_Icon2.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Room_Icon2.jpg")
  fui_applytexture(roombutton(2),img,.62)
  FreeImage img
EndIf
;--
If FileType("Data\RCcaves\Room_Icon3.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Room_Icon3.jpg")
  fui_applytexture(roombutton(3),img,.62)
  FreeImage img
EndIf
;--
If FileType("Data\RCcaves\Room_Icon4.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Room_Icon4.jpg")
  fui_applytexture(roombutton(4),img,.62)
  FreeImage img
EndIf
;--
If FileType("Data\RCcaves\Room_Icon5.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Room_Icon5.jpg")
  fui_applytexture(roombutton(5),img,.62)
  FreeImage img
EndIf
;--
If FileType("Data\RCcaves\Room_Icon6.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Room_Icon6.jpg")
  fui_applytexture(roombutton(6),img,.62)
  FreeImage img
EndIf
;--

;---------------
;---------------
If FileType("Data\RCcaves\Tunnel_Icon1.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Tunnel_Icon1.jpg")
  fui_applytexture(Tunnelbutton(1),img,.62)
  FreeImage img
EndIf
;--
If FileType("Data\RCcaves\Tunnel_Icon2.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Tunnel_Icon2.jpg")
  fui_applytexture(Tunnelbutton(2),img,.62)
  FreeImage img
EndIf
;--
If FileType("Data\RCcaves\Tunnel_Icon3.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Tunnel_Icon3.jpg")
  fui_applytexture(Tunnelbutton(3),img,.62)
  FreeImage img
EndIf
;--
If FileType("Data\RCcaves\Tunnel_Icon4.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Tunnel_Icon4.jpg")
  fui_applytexture(Tunnelbutton(4),img,.62)
  FreeImage img
EndIf
;--
If FileType("Data\RCcaves\Tunnel_Icon5.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Tunnel_Icon5.jpg")
  fui_applytexture(Tunnelbutton(5),img,.62)
  FreeImage img
EndIf
;--
If FileType("Data\RCcaves\Tunnel_Icon6.jpg")=1 Then 
  Img=LoadTexture("Data\RCcaves\Tunnel_Icon6.jpg")
  fui_applytexture(Tunnelbutton(6),img,.62)
  FreeImage img
EndIf
End Function 