Global thispath$ = CurrentDir$()
If Right$(thispath$,1)<>"\" Then thispath$ = thispath$+"\"

    ;main top menu
	Global GUI_TOPWIN = FUI_Window( 0, 0, App\W, GUI_VARIABLE_TOPBAR_HEIGHT, "", 0, 2 )
    ;FILE MENU
    Global GUI_TOPWIN_MENUFILE = FUI_MenuTitle( GUI_TOPWIN, "File" )
		Global GUI_MENUFILE_OPEN     = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Open", "" )
		Global GUI_MENUFILE_SAVE     = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Save", "" )
		;Global GUI_MENUFILE_EXPORT   = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Export", "" )
		Global GUI_MENUFILE_NEW      = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "New", "" )
		Global GUI_MENUFILE_COLORMAP = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Colormap RCTE", "" )
		Global GUI_MENUFILE_GUECOLORMAP = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Colormap Export", "" )
		Global GUI_MENUFILE_SaveHmap = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Save Hmap", "" )
	  	Global GUI_MENUFILE_Exit     = FUI_MenuItem( GUI_TOPWIN_MENUFILE, "Exit", "" )
    ;EDIT MENU
    Global GUI_TOPWIN_MENUEDIT = FUI_MenuTitle( GUI_TOPWIN, "Edit" )
		Global GUI_MENUEDIT_RANDOM      = FUI_MenuItem( GUI_TOPWIN_MENUEDIT, "Random", "")
		Global GUI_MENUEDIT_SMOOTH      = FUI_MenuItem( GUI_TOPWIN_MENUEDIT, "Smooth", "")
	    Global GUI_MENUEDIT_LIGHTMAP    = FUI_MenuItem( GUI_TOPWIN_MENUEDIT, "Lightmap", "")
		Global GUI_MENUEDIT_HEIGHTMAP   = FUI_MenuItem( GUI_TOPWIN_MENUEDIT, "Heightmap", "")
		;Global GUI_MENUEDIT_PAINTHOLE   = FUI_MenuItem( GUI_TOPWIN_MENUEDIT, "Paint Hole", "")
	   	Global GUI_MENUEDIT_SCALEUP     = FUI_MenuItem( GUI_TOPWIN_MENUEDIT, "Scale Up       ( + )", "" )
	   	Global GUI_MENUEDIT_SCALEDOWN   = FUI_MenuItem( GUI_TOPWIN_MENUEDIT, "Scale Down  ( - )", "" )
	   	Global GUI_MENUEDIT_DROPMODELS  = FUI_MenuItem( GUI_TOPWIN_MENUEDIT, "Drop Models", "")
        Global GUI_MENUEDIT_PAINTHOLE   = FUI_CheckBox( GUI_TOPWIN, 7, 0, "Paint hole:", 0 )
    ;BACKUP MENU
    Global GUI_TOPWIN_MENUBACKUP = FUI_MenuTitle( GUI_TOPWIN, "Backup" )
		Global GUI_MENUBACKUP_CREATE      = FUI_MenuItem( GUI_TOPWIN_MENUBACKUP, "(C) reate", "")
		Global GUI_MENUBACKUP_UNDO        = FUI_MenuItem( GUI_TOPWIN_MENUBACKUP, "(U) ndo", "")
		Global GUI_MENUBACKUP_REDO        = FUI_MenuItem( GUI_TOPWIN_MENUBACKUP, "(R) edo", "")
   ;MBRUSH MENU
	  Global  GUI_TOPWIN_MBRUSH = FUI_Button( GUI_TOPWIN, 90, 2, GUI_VARIABLE_RIGHTBAR_WIDTH - 15, 15, "Model brush options", 0, 0 )
	    Global GUI_MBWIN= FUI_Window( 125, 50, 400, 250, "", 0, 1,1 )
		Global GUI_MBWIN_ADD    = FUI_Button( GUI_MBWIN, 5, 15, GUI_VARIABLE_RIGHTBAR_WIDTH - 40, 20, "Add", 0, 0 )
		Global GUI_MBWIN_CLEAR  = FUI_Button( GUI_MBWIN, 110, 15, GUI_VARIABLE_RIGHTBAR_WIDTH - 40, 20, "Clear", 0, 0 )
		Global GUI_MBWIN_SAVE   = FUI_Button( GUI_MBWIN, 5, 45, GUI_VARIABLE_RIGHTBAR_WIDTH - 40, 20, "Save", 0, 0 )
		Global GUI_MBWIN_LOAD   = FUI_Button( GUI_MBWIN, 110, 45, GUI_VARIABLE_RIGHTBAR_WIDTH - 40, 20, "Load", 0, 0 )
		Global GUI_MBWIN_CLOSE   = FUI_Button( GUI_MBWIN, 110, 175, GUI_VARIABLE_RIGHTBAR_WIDTH - 40, 20, "Close", 0, 0 )
	    Global GUI_MBWIN_SCALEV  = FUI_Spinner( GUI_MBWIN, 110, 115, 100, 20, 0, 500, 100, 1, DTYPE_INTEGER, "%" )
	    Global GUI_MBWIN_SCALEL  = FUI_Label( GUI_MBWIN, 5, 85, "SCALE START:" )
	    Global GUI_MBWIN_SCALE   = FUI_Spinner( GUI_MBWIN, 110, 85, 100, 20, 0, 100, 100, 1, DTYPE_INTEGER, "%" )
	    Global GUI_MBWIN_SCALEVL = FUI_Label( GUI_MBWIN, 5, 115,"SCALE VARIANCE:" )
	    Global GUI_MBWIN_LISTL= FUI_Label( GUI_MBWIN, 225, 5, "BRUSH MESH LIST" )
		Global GUI_MBWIN_LISTBOX = FUI_ListBox( GUI_MBWIN, 225, 25, GUI_VARIABLE_LEFTBAR_WIDTH + 20, 175, False, True )

	    FUI_ModalWindow( GUI_TOPWIN_MBRUSH)
	    FUI_CenterWindow( GUI_TOPWIN_MBRUSH)
        FUI_SendMessage( GUI_MBWIN, M_CLOSE )
	;HELP
    Global GUI_TOPWIN_HELP = FUI_MenuTitle( GUI_TOPWIN, "Help" )
		Global GUI_HELP_CONTROLS = FUI_MenuItem( GUI_TOPWIN_HELP, "Controls" )
		Global GUI_HELP_ABOUT    = FUI_MenuItem( GUI_TOPWIN_HELP, "About" )
    ;SUB MENU SELCTION BOX PAINT/EDIT/MODEL window
  	Global GUI_PEDWIN = FUI_Window( App\W - GUI_VARIABLE_RIGHTBAR_WIDTH, GUI_VARIABLE_TOPBAR_HEIGHT - 1, GUI_VARIABLE_LEFTBAR_WIDTH, Int(perc(8.0,app\h)), "", 0, 0 )
		Global GUI_PED_PAINTMODE = FUI_Button( GUI_PEDWIN, 5, 5, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Paint", 0, 1 )
		Global GUI_PED_EDITMODE = FUI_Button( GUI_PEDWIN, 5, 25, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Edit", 0, 1 )
		Global GUI_PED_MODELMODE = FUI_Button( GUI_PEDWIN, 5, 45, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Model", 0, 1 )
    ;MODE WINDOW TO HOLD
  	Global GUI_MODEWIN = FUI_Window( App\W - GUI_VARIABLE_RIGHTBAR_WIDTH,(GUI_VARIABLE_TOPBAR_HEIGHT-2)+Int(perc(8.0,app\h)), GUI_VARIABLE_LEFTBAR_WIDTH, Int(PERC(2.2,app\h)), "", 0, 0 )
       ;Sub content on mode window : EDIT MODE
       Global GUI_MODEEDIT_RAISE = FUI_Radio( GUI_MODEWIN, 5, 10, "Raise land", 0 ,551)
       Global GUI_MODEEDIT_LOWER = FUI_Radio( GUI_MODEWIN, 5, 30, "Lower land", 0 ,551)
       Global GUI_MODEEDIT_PULL = FUI_Radio( GUI_MODEWIN, 5, 50,  "Pull land", 0 ,551)
       Global GUI_MODEEDIT_PUSH = FUI_Radio( GUI_MODEWIN, 5, 70,  "Push land", 0,551 )
       Global GUI_MODEEDIT_SMOOTH = FUI_Radio( GUI_MODEWIN,5, 90,"Smooth land", 0 ,551)
       Global GUI_MODEEDIT_FLATTEN = FUI_Radio( GUI_MODEWIN,5, 110,"Flatten land", 0,551 )
       Global GUI_MODEEDIT_MODEL = FUI_Radio( GUI_MODEWIN,5, 130,"Model brush options", 0,551 )
       Global GUI_MODEEDIT_DELETE = FUI_Radio( GUI_MODEWIN,5, 150,"Delete model brush", 0 ,551)
	   Global GUI_MODEEDIT_HIDEMODELS = FUI_Button( GUI_MODEWIN, 5, 225, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Hide models", 0, 0 )
	   Global GUI_MODEEDIT_SHOWMODELS = FUI_Button( GUI_MODEWIN, 5, 245, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Show models", 0, 0 )
       ;Sub content on mode window : MODEL MODE
	   Global GUI_MODEMODEL_LOAD = FUI_Button( GUI_MODEWIN, 5, 10, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Load model", 0, 0 )
	   Global GUI_MODEMODEL_COPY = FUI_Button( GUI_MODEWIN, 5, 30, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Copy model", 0, 0 )
	   Global GUI_MODEMODEL_DELETE = FUI_Button( GUI_MODEWIN, 5,50, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Delete model", 0, 0 )
	   Global GUI_MODEMODEL_TREE = FUI_Button( GUI_MODEWIN, 5, 70, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Tree options", 0, 0 )
       Global GUI_MODEMODEL_TREECHECK  = FUI_Radio( GUI_MODEWIN, 5, 110, "Tree", 1,552 )
       Global GUI_MODEMODEL_GRASSCHECK  = FUI_Radio( GUI_MODEWIN, 80, 110, "Grass",0, 552 )
       
       Global GUI_MODEMODEL_LABEL1 = FUI_Label( GUI_MODEWIN, 25, 150,"Scale Tree/Grass")
       
	   Global GUI_MODEMODEL_THICKEN = FUI_Button( GUI_MODEWIN, 5, 170, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Thicken", 0, 0 )
	   Global GUI_MODEMODEL_THIN = FUI_Button( GUI_MODEWIN, 5, 190, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Thin", 0, 0 )
	   Global GUI_MODEMODEL_SCALEUP = FUI_Button( GUI_MODEWIN, 5, 210, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Scale up", 0, 0 )
	   Global GUI_MODEMODEL_SCALEDOWN = FUI_Button( GUI_MODEWIN, 5, 230, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Scale down", 0, 0 )
       Global GUI_MODEMODEL_MODELPICK  = FUI_CheckBox( GUI_MODEWIN, 5, 285, "Model picking", 1 )
       ;Sub content on mode window : PAINT MODE
       Global GUI_MODEPAINT_USECOLOR  = FUI_CheckBox( GUI_MODEWIN, 5, 20, "Use color", 0 )
	   Global GUI_MODEPAINT_GETCOLOR = FUI_Button( GUI_MODEWIN, 5, 50, GUI_VARIABLE_RIGHTBAR_WIDTH - 10, 20, "Get color", 0, 0 )
       showmode(3)
       ;MOVEMENT TAB
        ;TIMAGE2 = CreateImage( 16, 90 )
		;SetBuffer ImageBuffer( TIMAGE2 )
		;Color 200, 200, 200
		;Rect 1, 1, 14, 88, 1
		;Color 1, 1, 1
		;Text 16/2 - StringWidth("M")/2, 4, "M"
		;Text 16/2 - StringWidth("o")/2, 14, "o"
		;Text 16/2 - StringWidth("v")/2, 24, "v"
		;Text 16/2 - StringWidth("e")/2, 34, "e"
		;Text 16/2 - StringWidth("m")/2, 44, "m"
		;Text 16/2 - StringWidth("e")/2, 54, "e"
		;Text 16/2 - StringWidth("n")/2, 64, "n"
		;Text 16/2 - StringWidth("t")/2, 74, "t"
		;SetBuffer BackBuffer()
        ;Global GUI_MOVEDOCK = FUI_Window( 0, App\H/2 - 98/2, 22, 150, "TEST", 0, 0, 0 )
  	    ;Global GUI_MOVEDOCK_BTN = FUI_Button( GUI_MOVEDOCK, 0, 0, 22, 150, "", TIMAGE2, 0,1 )
  	    Global GUI_TOPWIN_MOVEMENT = FUI_Button( GUI_TOPWIN, 225, 2, GUI_VARIABLE_RIGHTBAR_WIDTH - 15, 15, "Model move & scale", 0, 0 )
	    Global GUI_MOVEWIN= FUI_Window( 24, 44, 300, 150, "Model movement and scale", 0, 1,1 )
       ;MOVEMENT IMAGES
	   If FileType ("Data\RCTE\Move_Left.png")<>1 Then RuntimeError ("Gui image missing : Move_left.png")
		Global R_leftarrow=LoadTexture("Data\RCTE\Move_Left.png");ResizeImage r_leftarrow,24,24
		If FileType ("Data\RCTE\Move_Right.png")<>1 Then RuntimeError ("Gui image missing : Move_right.png")
		R_rightarrow=LoadTexture("Data\RCTE\Move_Right.png")
		If FileType ("Data\RCTE\Move_up.png")<>1 Then RuntimeError ("Gui image missing : Move_up.png")
		R_uparrow=LoadTexture("Data\RCTE\Move_Up.png")
		If FileType ("Data\RCTE\Move_Down.png")<>1 Then RuntimeError ("Gui image missing : Move_Down.png")
		R_downarrow=LoadTexture("Data\RCTE\Move_Down.png")
		If FileType ("Data\RCTE\Move_Forward.png")<>1 Then RuntimeError ("Gui image missing : Move_Forward.png")
		R_forwardarrow=LoadTexture("Data\RCTE\Move_Forward.png")
		If FileType ("Data\RCTE\Move_Backward.png")<>1 Then RuntimeError ("Gui image missing : Move_Backward.png")
		R_backwardarrow=LoadTexture("Data\RCTE\Move_Backward.png")
		If FileType ("Data\RCTE\ROT_Right.png")<>1 Then RuntimeError ("Gui image missing : ROT_Right.png")
		R_Rot_rightarrow=LoadTexture("Data\RCTE\ROT_Right.png")
		If FileType ("Data\RCTE\ROT_LEFT.png")<>1 Then RuntimeError ("Gui image missing : ROT_LEFT.png")
		R_Rot_leftarrow=LoadTexture("Data\RCTE\ROT_LEFT.png")
		If FileType ("Data\RCTE\ROT_up.png")<>1 Then RuntimeError ("Gui image missing : ROT_UP.png")
		R_Rot_uparrow=LoadTexture("Data\RCTE\ROT_UP.png")
		If FileType ("Data\RCTE\ROT_Down.png")<>1 Then RuntimeError ("Gui image missing : ROT_Down.png")
		R_rot_downarrow=LoadTexture("Data\RCTE\Rot_Down.png")
		If FileType ("Data\RCTE\ROT_RIGHT2.png")<>1 Then RuntimeError ("Gui image missing : ROT_RIGHT2.png")
		R_Rot_rightarrow2=LoadTexture("Data\RCTE\ROT_RIGHT2.png")
		If FileType ("Data\RCTE\ROT_LEFT2.png")<>1 Then RuntimeError ("Gui image missing : ROT_LEFT2.png")
		R_Rot_leftarrow2=LoadTexture("Data\RCTE\ROT_LEFT2.png")
		If FileType ("Data\RCTE\SCALEX_UP.png")<>1 Then RuntimeError ("Gui image missing : SCALEX_UP.png")
		R_scalearrow_Xup=LoadTexture("Data\RCTE\SCALEX_UP.png")
		If FileType ("Data\RCTE\SCALEX_DOWN.png")<>1 Then RuntimeError ("Gui image missing : SCALEX_Down.png")
		R_scalearrow_Xdown=LoadTexture("Data\RCTE\SCALEX_Down.png")
		If FileType ("Data\RCTE\SCALEY_UP.png")<>1 Then RuntimeError ("Gui image missing : SCALEY_UP.png")
		R_scalearrow_Yup=LoadTexture("Data\RCTE\SCALEY_UP.png")
		If FileType ("Data\RCTE\SCALEY_Down.png")<>1 Then RuntimeError ("Gui image missing : SCALEY_Down.png")
		R_scalearrow_Ydown=LoadTexture("Data\RCTE\SCALEY_Down.png")
		If FileType ("Data\RCTE\SCALEZ_UP.png")<>1 Then RuntimeError ("Gui image missing : SCALEZ_UP.png")
		R_scalearrow_Zup=LoadTexture("Data\RCTE\SCALEZ_UP.png")
		If FileType ("Data\RCTE\SCALEZ_Down.png")<>1 Then RuntimeError ("Gui image missing : SCALEZ_Down.png")
		R_scalearrow_Zdown=LoadTexture("Data\RCTE\SCALEZ_Down.png")
		If FileType ("Data\RCTE\SCALEALL_UP.png")<>1 Then RuntimeError ("Gui image missing : SCALEALL_UP.png")
		R_scalearrow_AllUP=LoadTexture("Data\RCTE\SCALEALL_UP.png")
		If FileType ("Data\RCTE\SCALEALL_Down.png")<>1 Then RuntimeError ("Gui image missing : SCALEALL_Down.png")
		R_scalearrow_AllDown=LoadTexture("Data\RCTE\SCALEALL_Down.png")

		imw=24
		imh=24
		;ResizeImage R_leftarrow,imw,imh
		;ResizeImage R_rightarrow,imw,imh
		;ResizeImage R_uparrow,imw,imh
		;ResizeImage R_downarrow,imw,imh
		;ResizeImage R_forwardarrow,imw,imh
		;ResizeImage R_backwardarrow,imw,imh
		;ResizeImage R_rot_leftarrow,imw,imh
		;ResizeImage R_rot_rightarrow,imw,imh
		;ResizeImage R_rot_uparrow,imw,imh
		;ResizeImage R_rot_downarrow,imw,imh
		;ResizeImage R_rot_leftarrow2,imw,imh
		;ResizeImage R_rot_rightarrow2,imw,imh
		;ResizeImage R_scalearrow_Xup,imw,imh
		;ResizeImage R_scalearrow_Xdown,imw,imh
		;ResizeImage R_scalearrow_yup,imw,imh
		;ResizeImage R_scalearrow_ydown,imw,imh
		;ResizeImage R_scalearrow_zup,imw,imh
		;ResizeImage R_scalearrow_zdown,imw,imh
		;ResizeImage R_scalearrow_allup,imw,imh
		;ResizeImage R_scalearrow_alldown,imw,imh
        ;Movement tab page
        Global		  GUI_MOVEWIN_MUP      = FUI_Button( GUI_MOVEWIN, 50, 10, 24, 24, ""):fui_applytexture(GUI_MOVEWIN_MUP,r_uparrow)
        Global        GUI_MOVEWIN_MDOWN    = FUI_Button( GUI_MOVEWIN, 50, 58, 24, 24, ""):fui_applytexture(GUI_MOVEWIN_Mdown,r_downarrow)
        Global        GUI_MOVEWIN_MLEFT    = FUI_Button( GUI_MOVEWIN, 25, 34, 24, 24, ""):fui_applytexture(GUI_MOVEWIN_Mleft,r_leftarrow)
        Global        GUI_MOVEWIN_MRIGHT   = FUI_Button( GUI_MOVEWIN, 75, 34, 24, 24, ""):fui_applytexture(GUI_MOVEWIN_MRIGHT,r_rightarrow)
        Global        GUI_MOVEWIN_MFORWARD = FUI_Button( GUI_MOVEWIN, 125, 10, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_MFORWARD,r_forwardarrow)
        Global        GUI_MOVEWIN_MBACK    = FUI_Button( GUI_MOVEWIN, 125, 58, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_MBACK,r_backwardarrow)

        Global        GUI_MOVEWIN_TUP      = FUI_Button( GUI_MOVEWIN, 50+150, 10+8, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_TUP,r_rot_Rightarrow)
        Global        GUI_MOVEWIN_TDOWN    = FUI_Button( GUI_MOVEWIN, 50+150, 58+8, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_TDOWN,r_rot_Leftarrow)
        Global        GUI_MOVEWIN_TLEFT    = FUI_Button( GUI_MOVEWIN, 25+150, 34+8, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_TLEFT,r_rot_uparrow)
        Global        GUI_MOVEWIN_TRIGHT   = FUI_Button( GUI_MOVEWIN, 75+150, 34+8, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_TRIGHT,r_rot_downarrow)

        Global        GUI_MOVEWIN_TFORWARD  = FUI_Button( GUI_MOVEWIN, 50+120, 8, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_TFORWARD,r_rot_LEFtarrow2)
        Global        GUI_MOVEWIN_TBACK     = FUI_Button( GUI_MOVEWIN, 50+180, 8, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_TBACK,r_rot_RIGHtarrow2)

        Global        GUI_MOVEWIN_XZOOMIN   = FUI_Button( GUI_MOVEWIN, 50-40, 100, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_XZOOMIN,R_SCALEARROW_XUP)         
        Global        GUI_MOVEWIN_XZOOMOUT  = FUI_Button( GUI_MOVEWIN, 75-40, 100, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_XZOOMOUT,R_SCALEARROW_XDOWN)          
        Global        GUI_MOVEWIN_YZOOMIN   = FUI_Button( GUI_MOVEWIN, 125-40, 100, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_YZOOMIN,R_SCALEARROW_YUP)          
        Global        GUI_MOVEWIN_YZOOMOUT  = FUI_Button( GUI_MOVEWIN, 150-40, 100, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_YZOOMOUT,R_SCALEARROW_Ydown)         
        Global        GUI_MOVEWIN_ZZOOMIN   = FUI_Button( GUI_MOVEWIN, 200-40, 100, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_ZZOOMIN,R_SCALEARROW_ZUP)         
        Global        GUI_MOVEWIN_ZZOOMOUT  = FUI_Button( GUI_MOVEWIN, 225-40, 100, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_ZZOOMOUT,R_SCALEARROW_ZDOWN)         
        Global        GUI_MOVEWIN_XYZIN     = FUI_Button( GUI_MOVEWIN, 275-40, 100, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_XYZIN,R_SCALEARROW_ALLUP)         
        Global        GUI_MOVEWIN_XYZOUT    = FUI_Button( GUI_MOVEWIN, 300-40, 100, 24, 24, "" ):fui_applytexture(GUI_MOVEWIN_XYZOUT,R_SCALEARROW_ALLDOWN)         
        FUI_SendMessage( GUI_MOVEWIN, M_CLOSE )

    ;SLIDER ADJUSTMENT WINDOW 
    Sty#=(GUI_VARIABLE_TOPBAR_HEIGHT-2)+Int(perc(8.0,app\h))+Int(PERC(2,app\h))
	Global GUI_SLIDERWIN = FUI_Window( App\W - GUI_VARIABLE_RIGHTBAR_WIDTH,sty, GUI_VARIABLE_LEFTBAR_WIDTH, Int(PERC(4,app\h)), "", 0, 0 )
		Global GUI_SLIDERWIN_BRUSHSIZEL= FUI_Label( GUI_SLIDERWIN, 5, 5, "Brush Size" )
 		Global GUI_SLIDERWIN_BRUSHSIZE = FUI_Slider( GUI_SLIDERWIN, 5, 20, 130, 12,min_brush_size,max_brush_size,12)
		Global GUI_SLIDERWIN_BRUSHSPEEDL= FUI_Label (GUI_SLIDERWIN, 5, 35, "Brush Speed" )
 		Global GUI_SLIDERWIN_BRUSHSPEED = FUI_Slider(GUI_SLIDERWIN,  5, 50, 130, 12,1,100,20)
		Global GUI_SLIDERWIN_CAMERASPEEDL= FUI_Label (GUI_SLIDERWIN, 5, 65, "Camera Speed" )
 		Global GUI_SLIDERWIN_CAMERASPEED = FUI_Slider(GUI_SLIDERWIN, 5, 80, 130, 12, 30,360,150)
		Global GUI_SLIDERWIN_CAMERARANGEL= FUI_Label (GUI_SLIDERWIN, 5, 95, "Camera Range" )
 		Global GUI_SLIDERWIN_CAMERARANGE = FUI_Slider(GUI_SLIDERWIN, 5, 110, 130, 12,100,4000,2000 )
 	    Global  GUI_TOPWIN_RESETCAM = FUI_Button( GUI_TOPWIN, app\w-245, 2, GUI_VARIABLE_RIGHTBAR_WIDTH - 35, 15, "Reset camera", 0, 0 )
 	    Global  GUI_TOPWIN_RESETFLAT = FUI_Button( GUI_TOPWIN, app\w-365, 2, GUI_VARIABLE_RIGHTBAR_WIDTH - 35, 15, "Reset flatten height", 0, 0 )
        FUI_SendMessage(GUI_SLIDERWIN_BRUSHSIZE,M_SETVALUE,12)	
    ;TREE OPTIONS WINDOW 
    Global GUI_TREEWIN= FUI_Window( app\w-(GUI_VARIABLE_RIGHTBAR_WIDTH+205), (App\H/3 - 98/2)+1, 200, 200, "Tree options", 0, 1,1 )
        Global GUI_TREEWIN_TREESET  = FUI_Radio( GUI_TREEWIN, 15, 5,  "Tree", 1 ,554)
        Global GUI_TREEWIN_GRASSSET = FUI_Radio( GUI_TREEWIN, 125, 5, "Grass", 0 ,554)

        Global GUI_TREEWIN_SWAYTOP = FUI_Radio( GUI_TREEWIN, 15, 30, "Sway top", 0 ,555)
        Global GUI_TREEWIN_SWAYMID = FUI_Radio( GUI_TREEWIN, 15, 45, "Sway mid", 1 ,555)
        Global GUI_TREEWIN_SWAYLOW = FUI_Radio( GUI_TREEWIN, 15, 60, "Sway low", 0 ,555)

        Global GUI_TREEWIN_EVERGREEN = FUI_Radio( GUI_TREEWIN, 15, 85, "Evergreen", 1 ,556)
        Global GUI_TREEWIN_SEASONAL  = FUI_Radio( GUI_TREEWIN, 15, 100, "Seasonal", 0 ,556)

 	    Global GUI_TREEWIN_OK = FUI_Button( GUI_TREEWIN, 5, 145, GUI_VARIABLE_RIGHTBAR_WIDTH - 50, 20, "Choose Settings", 0, 0 )
 	    Global GUI_TREEWIN_CANCEL = FUI_Button( GUI_TREEWIN, 103, 145, GUI_VARIABLE_RIGHTBAR_WIDTH - 50, 20, "Cancel", 0, 0 )
 
		Global GUI_TREEWIN_LABEL1 = FUI_Label( GUI_TREEWIN, 125, 30, "Season ID" )
		Global GUI_TREEWIN_SEASON = FUI_Spinner( GUI_TREEWIN, 125, 45, 50, 20, 1, 12, 1, 1, DTYPE_INTEGER, "" )

 	    Global GUI_TREEWIN_COLOR  = FUI_Button( GUI_TREEWIN, 125, 70, GUI_VARIABLE_RIGHTBAR_WIDTH - 90, 15, "Color", 0, 0 )

		TIMAGE = CreateImage( 70, 45 )
		SetBuffer ImageBuffer(timage)
;		Color GUI_ARRAY_SEASONCOLOR( 0, 0 ), GUI_ARRAY_SEASONCOLOR( 0, 1 ), GUI_ARRAY_SEASONCOLOR( 0, 2 )
		Rect 0, 0, 70, 45,1
		SetBuffer BackBuffer()
		Global GUI_TREEWIN_CANVAS = FUI_ImageBox( GUI_TREEWIN, 125, 90, 68, 45, TIMAGE )
        FUI_SendMessage( GUI_TREEWIN, M_CLOSE )
        FreeImage timage 
       ;TEXTURE WINDOW/BOTTOM WINDOW
        Global GUI_BOTTOMWIN=FUI_Window( 0, app\h-perc(8.571,app\h), App\W, perc(8.571,app\h), "", 0, 0 )
        Dim GUI_BOTTOMWIN_TEXTHUMB(6)
	    GUI_BOTTOMWIN_TEXTHUMB(1) = FUI_Button( GUI_BOTTOMWIN, 2, 2, 24, 24, "", 0, 0 )
	    GUI_BOTTOMWIN_TEXTHUMB(2) = FUI_Button( GUI_BOTTOMWIN, 27, 2, 24, 24, "", 0, 0 )
	    GUI_BOTTOMWIN_TEXTHUMB(3) = FUI_Button( GUI_BOTTOMWIN, 52, 2, 24, 24, "", 0, 0 )
	    GUI_BOTTOMWIN_TEXTHUMB(4) = FUI_Button( GUI_BOTTOMWIN, 2, 27, 24, 24, "", 0, 0 )
	    GUI_BOTTOMWIN_TEXTHUMB(5) = FUI_Button( GUI_BOTTOMWIN, 27, 27, 24, 24, "", 0, 0 )
	    GUI_BOTTOMWIN_TEXTHUMB(6) = FUI_Button( GUI_BOTTOMWIN, 52, 27, 24, 24, "", 0, 0 )

		Global GUI_MINHEIGHT_LABEL1 = FUI_Label( GUI_BOTTOMWIN, 100, 2, "Minimum height" )
		Global GUI_MINHEIGHT_LABEL2 = FUI_Label( GUI_BOTTOMWIN, 200, 2, "Maximum height" )
		Global GUI_MINHEIGHT_LABEL3 = FUI_Label( GUI_BOTTOMWIN, 300, 2, "Minimum slope" )
		Global GUI_MINHEIGHT_LABEL4 = FUI_Label( GUI_BOTTOMWIN, 400, 2, "Maximum slope" )

		Global GUI_BOTTOMWIN_MINHEIGHT = FUI_Spinner( GUI_BOTTOMWIN, 100, 25, 75, 20, 1, 100, 1, 1, DTYPE_INTEGER, "" )
		Global GUI_BOTTOMWIN_MAXHEIGHT = FUI_Spinner( GUI_BOTTOMWIN, 200, 25, 75, 20, 1, 100, 1, 1, DTYPE_INTEGER, "" )
		Global GUI_BOTTOMWIN_MINSLOPE  = FUI_Spinner( GUI_BOTTOMWIN, 300, 25, 75, 20, 1, 100, 1, 1, DTYPE_INTEGER, "" )
		Global GUI_BOTTOMWIN_MAXSLOPE  = FUI_Spinner( GUI_BOTTOMWIN, 400, 25, 75, 20, 1, 100, 1, 1, DTYPE_INTEGER, "" )

        Global GUI_BOTTOMWIN_SCALEALLTEX = FUI_CheckBox( GUI_BOTTOMWIN, 500, 2, " Scale all textures ", 0 )
	    Global GUI_BOTTOMWIN_SCALEUP = FUI_Button( GUI_BOTTOMWIN, 500, 25, 70, 24, "Scale up", 0, 0 )
        Global GUI_BOTTOMWIN_SCALEDOWN = FUI_Button( GUI_BOTTOMWIN, 580, 25, 70, 24, "Scale down", 0, 0 )
	 
	    Global GUI_BOTTOMWIN_AUTOTEXTURE = FUI_Button( GUI_BOTTOMWIN, 670, 25, 90, 24, "Autotexture", 0, 0 )
       ;TEXTURE PREVIEW WINDOW/BUTTON
	Global GUI_TPREVWIN=FUI_Window( 0, app\h-perc(3.9,app\h), 100, 100, "", 0, 0 )
	    Global GUI_TPREVWIN_BUTTON = FUI_Button( GUI_TPREVWIN, 10, 10, 80, 80, "", 0, 0 )
       ;LIGHTMAP OPTIONS WINDOW
        Global GUI_LIGHTWIN            = FUI_Window( 300, 65, 250, 125, "Lightmap", 0, 1, 1 )
		Global GUI_LIGHTWIN_OK         = FUI_Button( GUI_LIGHTWIN, 10, 75, 50, 16, "OK" )
		Global GUI_LIGHTWIN_CANCEL     = FUI_Button( GUI_LIGHTWIN, 165, 75, 50, 16, "CANCEL" )
		Global GUI_LIGHTWIN_LABEL1     = FUI_Label( GUI_LIGHTWIN, 10, 5, "Ambient ligth" )
		Global GUI_LIGHTWIN_LABEL2     = FUI_Label( GUI_LIGHTWIN, 165, 5, "Smooth Passes" )
		Global GUI_LIGHTWIN_AMBIENT    = FUI_Spinner( GUI_LIGHTWIN, 10, 30, 75, 20, 0, 255, 50, 1, DTYPE_INTEGER, "" )
	    Global GUI_LIGHTWIN_SMOOTH     = FUI_Spinner( GUI_LIGHTWIN, 165, 30, 75, 20, 0, 20, 1, 1, DTYPE_INTEGER, "" )
       	FUI_SendMessage(gui_lightwin,m_Close)         	
 
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
;  		    FUI_Update( )
;	 		CameraViewport( Cam, 0, 0, App\W, App\H )
;			RenderWorld( )
;                If sysmsg$<>"" And sysmsgtimer#>MilliSecs() Then
; 				   Color( 15, 15, 15 )
;    	           Text 76,71,sysmsg$          
;			  	   Color( 255, 255, 255 )
;                  Text 75,70,sysmsg$          
;                  EndIf
;			Flip( GFX_VARIABLE_VSYNC )
;         Cls( )
;         positionwindows()
		;#End Region
;      Wend


Function AboutWindow( )
	TWidth = 250
	THeight = 230
	
        GUI_ABOUTWINDOW = FUI_Window( 0, 0, TWidth, THeight, "About...", 0, 1, 1 )
		GUI_ABOUTWINDOW_LABEL1 = FUI_Label( GUI_ABOUTWINDOW, 10, 10, "Realm Crafter Terrain Editor" )
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

Function ControlsWindow( )
	TWidth = 320
	THeight = 230
 	GUI_CONTROLSWINDOW = FUI_Window( 0, 0, TWidth, THeight, "Controls...", 0, 1, 1 )
		GUI_CONTROLSWINDOW_LABEL1 = FUI_Label( GUI_CONTROLSWINDOW, 10, 10, "Spacebar = Mouselook" )
		GUI_CONTROLSWINDOW_LABEL2 = FUI_Label( GUI_CONTROLSWINDOW, 10, 25, "Left Mouse Button = Activate GUI button" )
		GUI_CONTROLSWINDOW_LABEL3 = FUI_Label( GUI_CONTROLSWINDOW, 10, 40, "Right Mouse Button = Perform GUI action" )
   	    GUI_CONTROLSWINDOW_LABEL4 = FUI_Label( GUI_CONTROLSWINDOW, 10, 55, "PgUp / PgDwn / Mousewheel = Flatten height" )
   	    GUI_CONTROLSWINDOW_LABEL5 = FUI_Label( GUI_CONTROLSWINDOW, 10, 70, "")
   	    GUI_CONTROLSWINDOW_LABEL6 = FUI_Label( GUI_CONTROLSWINDOW, 10, 85, "")		
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


Function Perc#(pv#,ofv#)
 V1#=ofv/pv
 Return v1
End Function 



Function ShowMode(Mode) 
;1=paint 2=edit 3=model
       ;Sub content on mode window : EDIT MODE
       FUI_HideGadget( GUI_MODEEDIT_RAISE)
       FUI_HideGadget(GUI_MODEEDIT_LOWER)
       FUI_HideGadget(GUI_MODEEDIT_PULL)
       FUI_HideGadget(GUI_MODEEDIT_PUSH)
       FUI_HideGadget(GUI_MODEEDIT_SMOOTH)
       FUI_HideGadget(GUI_MODEEDIT_FLATTEN)
       FUI_HideGadget(GUI_MODEEDIT_MODEL)
       FUI_HideGadget(GUI_MODEEDIT_DELETE)
	   FUI_HideGadget(GUI_MODEEDIT_HIDEMODELS)
	   FUI_HideGadget(GUI_MODEEDIT_SHOWMODELS)
       ;Sub content on mode window : MODEL MODE
	   FUI_HideGadget(GUI_MODEMODEL_LOAD)
	   FUI_HideGadget(GUI_MODEMODEL_COPY)
	   FUI_HideGadget(GUI_MODEMODEL_DELETE)
	   FUI_HideGadget(GUI_MODEMODEL_TREE)
       FUI_HideGadget(GUI_MODEMODEL_TREECHECK)
       FUI_HideGadget(GUI_MODEMODEL_GRASSCHECK)
       FUI_HideGadget(GUI_MODEMODEL_LABEL1)
	   FUI_HideGadget(GUI_MODEMODEL_THICKEN)
	   FUI_HideGadget(GUI_MODEMODEL_THIN)
	   FUI_HideGadget(GUI_MODEMODEL_SCALEUP)
	   FUI_HideGadget(GUI_MODEMODEL_SCALEDOWN)
       FUI_HideGadget(GUI_MODEMODEL_MODELPICK)
       ;Sub content on mode window : PAINT MODE
       FUI_HideGadget(GUI_MODEPAINT_USECOLOR)
	   FUI_HideGadget(GUI_MODEPAINT_GETCOLOR)
Select mode
 Case 1
       ;Sub content on mode window : PAINT MODE
       FUI_ShowGadget(GUI_MODEPAINT_USECOLOR)
	   FUI_ShowGadget(GUI_MODEPAINT_GETCOLOR)
 Case 2
       ;Sub content on mode window : EDIT MODE
       FUI_ShowGadget( GUI_MODEEDIT_RAISE)
       FUI_ShowGadget(GUI_MODEEDIT_LOWER)
       FUI_ShowGadget(GUI_MODEEDIT_PULL)
       FUI_ShowGadget(GUI_MODEEDIT_PUSH)
       FUI_ShowGadget(GUI_MODEEDIT_SMOOTH)
       FUI_ShowGadget(GUI_MODEEDIT_FLATTEN)
       FUI_ShowGadget(GUI_MODEEDIT_MODEL)
       FUI_ShowGadget(GUI_MODEEDIT_DELETE)
	   FUI_ShowGadget(GUI_MODEEDIT_HIDEMODELS)
	   FUI_ShowGadget(GUI_MODEEDIT_SHOWMODELS)
 Case 3
       ;Sub content on mode window : MODEL MODE
	   FUI_ShowGadget(GUI_MODEMODEL_LOAD)
	   FUI_ShowGadget(GUI_MODEMODEL_COPY)
	   FUI_ShowGadget(GUI_MODEMODEL_DELETE)
	   FUI_ShowGadget(GUI_MODEMODEL_TREE)
       FUI_ShowGadget(GUI_MODEMODEL_TREECHECK)
       FUI_ShowGadget(GUI_MODEMODEL_GRASSCHECK)
       FUI_ShowGadget(GUI_MODEMODEL_LABEL1)
	   FUI_ShowGadget(GUI_MODEMODEL_THICKEN)
	   FUI_ShowGadget(GUI_MODEMODEL_THIN)
	   FUI_ShowGadget(GUI_MODEMODEL_SCALEUP)
	   FUI_ShowGadget(GUI_MODEMODEL_SCALEDOWN)
       FUI_ShowGadget(GUI_MODEMODEL_MODELPICK)
End Select 
End Function 

Function FUI_ApplyTexture(ID,TEX,scale#=.75)
    ScaleTexture tex,scale,scale
	gad.button = Object.button( ID )
	If gad <> Null
        EntityTexture gad\mesh,tex,0,1          
	EndIf
End Function 

Function FUI_ApplyImage(ID,img,Texw=32,Texh=32,scale#=.75,removeoldtex=False)
	gad.button = Object.button( ID )
    ;lets remove the old tex
    If removeoldtex Then 
	    s=GetSurface(gad\mesh,1)
    	b=GetSurfaceBrush(s)
	    t=GetBrushTexture(b,1)
	    If t Then FreeTexture t
    EndIf 
   If texw<>-1 Then 
      ResizeImage img,texw,texh
   Else
     texw=ImageWidth(img)
     texh=ImageHeight(img)
   EndIf

   Tex=CreateTexture(texw,texh)
   Gui_copyimage(img,0,0,TextureBuffer(tex))
   Fui_applytexture(id,tex,1.0)
End Function 

Function Gui_CopyImage(temp_image,x1,y1,temp_buffer)
            IMGW=ImageWidth(TEMP_IMAGE)-1
            IMGh=ImageHeight(TEMP_IMAGE)-1
        For i=0 To IMGW  
	        For ii=0 To IMGH  
                 SetBuffer(ImageBuffer(temp_image))
                 ThisPixel=ReadPixel (i,ii)
	               RCVAL=ThisPixel Shr 0 And 255 Shl 0
                      If rcval<>0 Then 
                         SetBuffer temp_buffer
                          WritePixel x1+i,y1+ii,ThisPixel
                           EndIf
                          Next
                         Next
End Function



Function PositionWindows()
            ;keep gadget windows in frame of main window
               
             If GFX_WINDOW_MODE <> 1


 	    	    FUI_SendMessage( GUI_TPREVWIN, M_SETPOS_Y,app\h-perc(3.9,app\h))

 	    	    FUI_SendMessage( GUI_TOPWIN_RESETCAM, M_SETPOS_X,app\w-245)
 	    	    FUI_SendMessage( GUI_TOPWIN_RESETFLAT, M_SETPOS_X,app\w-365)

				FUI_SendMessage( GUI_TOPWIN, M_SETSIZE_W, App\W )
				FUI_SendMessage( GUI_PEDWIN, M_SETSIZE_H, Int(perc(8.0,app\h)))
 	    	    FUI_SendMessage( GUI_PEDWIN, M_SETPOS_X,app\w-(GUI_VARIABLE_LEFTBAR_WIDTH))

				FUI_SendMessage( GUI_MODEWIN, M_SETSIZE_H, Int(perc(2,app\h)))
	 	        FUI_SendMessage( GUI_MODEWIN, M_SETPOS_X,app\w- GUI_VARIABLE_LEFTBAR_WIDTH)
 		        FUI_SendMessage( GUI_MODEWIN, M_SETPOS_Y,(GUI_VARIABLE_TOPBAR_HEIGHT-2)+Int(perc(8.0,app\h)) )

        	    sty#=Int(perc(2,app\h))+(GUI_VARIABLE_TOPBAR_HEIGHT-2)+Int(perc(8.0,app\h))
				FUI_SendMessage( GUI_SLIDERWIN, M_SETSIZE_H, (perc(4.5,app\h)))
	 	        FUI_SendMessage( GUI_SLIDERWIN, M_SETPOS_X,app\w-(GUI_VARIABLE_LEFTBAR_WIDTH))
 		        FUI_SendMessage( GUI_SLIDERWIN, M_SETPOS_Y,STY)

				FUI_SendMessage( GUI_BOTTOMWIN, M_SETSIZE_W, APP\W)
 	        	FUI_SendMessage( GUI_BOTTOMWIN, M_SETPOS_Y,sty+(perc(4.5,app\h)))
				FUI_SendMessage( GUI_BOTTOMWIN, M_SETSIZE_H, app\h-sty+(perc(4.5,app\h)))
    
; 		        FUI_SendMessage( GUI_MOVEDOCK, M_SETPOS_Y,App\H/2 - 98/2)
; 		        FUI_SendMessage( GUI_MOVEWIN, M_SETPOS_Y,App\H/2 - 98/2)

                Else ; else this is fullscreen

 	    	    FUI_SendMessage( GUI_TPREVWIN, M_SETPOS_Y,gfx_window_HEIGHT-perc(3.9,gfx_window_HEIGHT))

                FUI_SendMessage( GUI_TOPWIN, M_SETSIZE_W, gfx_window_width )
				FUI_SendMessage( GUI_PEDWIN, M_SETSIZE_H, Int(perc(8.0,gfx_window_height)))
 	    	    FUI_SendMessage( GUI_PEDWIN, M_SETPOS_X,gfx_window_width-(GUI_VARIABLE_LEFTBAR_WIDTH))

				FUI_SendMessage( GUI_MODEWIN, M_SETSIZE_H, Int(perc(2,gfx_window_height)))
	 	        FUI_SendMessage( GUI_MODEWIN, M_SETPOS_X,gfx_window_width-(GUI_VARIABLE_LEFTBAR_WIDTH))
 		        FUI_SendMessage( GUI_MODEWIN, M_SETPOS_Y,(GUI_VARIABLE_TOPBAR_HEIGHT-2)+Int(perc(8.0,gfx_window_height)) )

        	    sty#=Int(perc(2,gfx_window_height))+(GUI_VARIABLE_TOPBAR_HEIGHT-2)+Int(perc(8.0,gfx_window_height))
				FUI_SendMessage( GUI_SLIDERWIN, M_SETSIZE_H, (perc(4.5,gfx_window_height)))
	 	        FUI_SendMessage( GUI_SLIDERWIN, M_SETPOS_X,gfx_window_width-(GUI_VARIABLE_LEFTBAR_WIDTH))
 		        FUI_SendMessage( GUI_SLIDERWIN, M_SETPOS_Y,STY)

				FUI_SendMessage( GUI_BOTTOMWIN, M_SETSIZE_W, gfx_window_width)
 	        	FUI_SendMessage( GUI_BOTTOMWIN, M_SETPOS_Y,sty+(perc(4.5,gfx_window_height)))
				FUI_SendMessage( GUI_BOTTOMWIN, M_SETSIZE_H, gfx_window_height-sty+(perc(4.5,gfx_window_height)))
    
; 		        FUI_SendMessage( GUI_MOVEDOCK, M_SETPOS_Y,gfx_window_height/2 - 98/2)                
; 		        FUI_SendMessage( GUI_MOVEWIN, M_SETPOS_Y,gfx_window_height/2 - 98/2)

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
