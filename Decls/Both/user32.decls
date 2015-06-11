.lib "user32.dll"

;Mouse
GetMousePosGlobal%( bank* ) : "GetCursorPos"

;Window
ShowWindow%( hWnd, nCmdShow ) : "ShowWindow"
FindWindow%(ClassName$,Caption$):"FindWindowA"
GetWindow%(hwnd,flag):"GetWindow"
GetDesktopWindow%():"GetDesktopWindow"
GetWindowRect%(hwnd,rect*):"GetWindowRect"
GetClientRect%(hwnd,rect*):"GetClientRect"
FlashWindow(hwnd,state):"FlashWindow"
MoveWindow(hwnd,x,y,width,height,repaint):"MoveWindow"
ResizeWindow(hwnd,x,y,width,height,repaint):"MoveWindow"
GetForegroundWindow%():"GetForegroundWindow"
GetWindowText%(hwnd,bank*,len):"GetWindowTextA"
SetWindowPos(hwnd,order,x,y,width,height,param):"SetWindowPos"
IsWindowVisible%(hwnd):"IsWindowVisible"
IsMinimized%(hwnd):"IsIconic"

;Masks
SetWindowRgn%(hWnd%, hRgn%, bRedraw%)

;Misc
GetWindowLong%(hWnd%, nIndex% ):"GetWindowLongA"
SetWindowLong%(hWnd%, nIndex%, lNewLong% ):"SetWindowLongA"

GetSystemMetrics%( nIndex ) : "GetSystemMetrics"
SystemParametersInfo%(uiAction,uiParam,pvParam*,fWinIni):"SystemParametersInfoA"
MessageBox%(hwnd,msg$,title$,style):"MessageBoxA"