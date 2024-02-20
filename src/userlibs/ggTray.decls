;Declarations file for ggTray.dll
;
; an individual event is in the following format:
;	EVENT_TYPE|MOUSEX|MOUSEY
;
; a list of events is a series of the individual event format seperated by newlines
;
; EVENT_TYPE can be the following:
;	1 - left click
;	2 - left double click
;	3 - right click
;	4 - right double click
;	5 - middle click
;	6 - middle double click
;
;

.lib "ggTray.dll"

; attaches the passed window handle to the tray icon and initializes the icon info
ggTrayCreate%(hWnd%):"_ggTrayCreate@4"

; shows the icon in the task tray
ggTrayShowIcon%():"_ggTrayShowIcon@0"

; hides the icon in the task tray
ggTrayHideIcon%():"_ggTrayHideIcon@0"

; sets the tooltip for the icon
ggTraySetToolTip%(cToolTip$):"_ggTraySetToolTip@4"

; gets the tooltip of the icon
ggTrayGetToolTip$():"_ggTrayGetToolTip@0"

; return 1 if visible, 0 if not
ggTrayIsVisible%():"_ggTrayIsvisible@0"

; sets the icon used based on the file passed.  1 if successful, 0 if not.
ggTraySetIconFromFile%(cIcoFile$):"_ggTraySetIconFromFile@4"

; sets the icon used based on an icon handle.  1 if successful, 0 if not.
ggTraySetIconFromHandle%(hIcon%):"_ggTraySetIconFromHandle@4"

; destroys everything setup with ggTrayCreate and removes the icon
ggTrayDestroy%():"_ggTrayDestroy@0"

; clears all events from the queue
ggTrayClearEvents():"_ggTrayClearEvents@0"

; clears the passed event from the queue (one-based)
ggTrayClearEvent(nEvent%):"_ggTrayClearEvent@4"

; returns a CRLF delimited list of events and clears the queue
ggTrayReadEvents$():"_ggTrayReadEvents@0"

; returns the passed event and removes it from the queue
ggTrayReadEvent$(nEvent%):"_ggTrayReadEvent@4"

; returns a CRLF delimited list of events but does not clear the queue
ggTrayPeekEvents$():"_ggTrayPeekEvents@0"

; returns the passed event but does not remove it from the queue
ggTrayPeekEvent$(nEvent%):"_ggTrayPeekEvent@4"

; returns the event # of the first left click encountered
ggTrayPeekLeftClick%():"_ggTrayPeekLeftClick@0"

; returns the event # of the first left double click encountered
ggTrayPeekLeftDblClick%():"_ggTrayPeekLeftDblClick@0"

; returns the event # of the first right click encountered
ggTrayPeekRightClick%():"_ggTrayPeekRightClick@0"

; returns the event # of the first right double click encountered
ggTrayPeekRightDblClick%():"_ggTrayPeekRightDblClick@0"

; returns the mouse X of the event passed
ggTrayEventMouseX%(nEvent%):"_ggTrayEventMouseX@4"

; returns the mouse Y of the event passed
ggTrayEventMouseY%(nEvent%):"_ggTrayEventMouseY@4"
