;--------------------------------------------------------------------------
;                                                                
;                                                         
; Mandelbrot set Zooming Explorer
;
;images are saved in current directory with name including fractal type, center coordinates 
;and frame number, using ZeroMe() function by Mikkel Lokke 
;
;original algorythm by Mandelbrot :)
;
; press ESC anytime to quit
;
;--------------------------------------------------------------------------
 AppTitle(" - mze - rno 2000 - ")

   Const disp_width# = 640                               ;display width
  Const disp_height# = 480                                ;display height
    Const dispx_ctr# = disp_width/2                       ;center x
    Const dispy_ctr# = disp_height/2                      ;       y

;change pixel size if it's too fast / too slow on your system  (absolute value ok):
  Const def_pix_siz# = disp_width/64                      ;default size of a pixel

; same but for the high quality pic used to choose where to zoom to:
  Const hiq_pix_siz# = 1                                  ;high quality mode pixel size
     Global pix_siz# = def_pix_siz                        ;size of a pixel

;256 is close to the highest value you can use as number of frames in a zoom with this zooming factor (.96)
;because of float number resolution, after a while rounded values errors propagate too much
;if you stop zooming when close to last frame (256th) you'll see how the picture is trashed
        Const fr_max = 256                                ;number of frames in a zoom
       Global fr_cur = 0                                  ;number of frames counter
    Global fr_width# = disp_width/pix_siz                 ;width of frame in 'fzpixels'
   Global fr_height# = disp_height/pix_siz
     Global fr_state = 0                                  ;is frame finished rendering(1) or not (0)
     Global fr_saved = 0                                  ;if frame saved already (1) or not (0)
Global fr_grab_name$ 

   Const def_fx_rng# = 6.4                                ;default starting boundaries range
   Const def_fy_rng# = def_fx_rng*(disp_height/disp_width)
   Const def_fx_ctr# = 0                                  ;default starting center
   Const def_fy_ctr# = 0

      Global fx_rng# = def_fx_rng                         ;boundaries range
      Global fy_rng# = def_fy_rng 
      Global fx_ctr# = def_fx_ctr                         ;center x
      Global fy_ctr# = def_fy_ctr                         ;       y
      Global fx_stp# = fx_rng/fr_width                    ;step to next x
      Global fy_stp# = fy_rng/fr_height                   ;             y
      Global fx_min# = fx_ctr-(fx_rng/2)                  ;first x
      Global fy_min# = fy_ctr-(fy_rng/2)                  ;      y
      Global fx_max# = fx_ctr+(fx_rng/2)                  ;last x
      Global fy_max# = fy_ctr+(fy_rng/2)                  ;     y

      Global fx_cur# = fx_min                             ;current (fractal) x and y
      Global fy_cur# = fy_min

      Global fx_fac# = disp_width/fx_rng                  ;diplaying factor
      Global fy_fac# = disp_height/fy_rng 

      Global fx_ori# = dispx_ctr-(fx_ctr*fx_fac)          ;point to set origin to
      Global fy_ori# = dispy_ctr-(fy_ctr*fy_fac)

      Const def_thr# = 4                                  ;default threshold for mandelbrot set calc
 Const def_iter_max# = 127                                ;default iterations maximum for mandelbrot set

     Global fz_state = 0                                  ;program state
Global fz_prev_state = 0                                  ;previous program state
      Const calc = 0                                      ; state can be one of:
      Const done = 1
      Const zoom = 2
    Const unzoom = 3
       Const hiq = 4
      Const menu = 5

      Global fz_frac = 0                                  ;type of fractal rendered
   Global fz_frac_tn = 2                                  ;number of different types
Const mandelbrot = 0 
   Const tristar = 1
;    Const dragon = 2

           Const zf# = .96                                ;zooming factor

Global mzx#,mzy#                                          ;mouse values 
Graphics disp_width,disp_height                     ;,32  if your card support 32 bits it's faster 
Global fr_grab=CreateImage(disp_width,disp_height)        ;grabbed image

;--------------------------------------------------------------------------
;main loop
;

SetBuffer BackBuffer()                                              ;use doublebuffer
Cls
Color 155,255,202                                         ;text color
Text 0,0,  "mandelbrot zooming explorer - rno 2000"
Text 0,16,"   ESC to quit anytime"
Text 0,32,"During zoom/unzoom"
Text 0,48,"   Spacebar to stop zoom"
Text 0,64,"While stopped (once pic finnished)"
Text 0,80,"   RMB : zoom   LMB : unzoom   S : save pic  T : change fractal"
Text 0,96,"   click then go a few steps away from your monitor :) "

Text 0,112,"press a key....."
Flip                                                      ;show buffer
WaitKey() 
                                          
SetBuffer FrontBuffer()                                   ;use front buffer because pic is gonna be hi q
Cls
Origin fx_ori,fy_ori                 ;sets drawing origin
fz_state=hiq
precision(hiq_pix_siz)
fz_prev_state=zoom
fr_state=calc

While Not KeyDown(1)                                      ;it will never stop (if both you and Windows agree)
 If fr_state=calc                                         ;frame in process
    frame_mandel()
  Else

    Select fz_state                                       ;frame done select what's next 
      Case zoom
        Flip
        fr_cur=fr_cur+1
        upd_values(fx_rng*zf,fy_rng*zf,mzx,mzy)
        fr_state=calc
        If fr_cur=>fr_max
           fz_state=unzoom
        EndIf
      Case unzoom
        Flip
        fr_cur=fr_cur-1
        upd_values(fx_rng/zf,fy_rng/zf,mzx,mzy)
        fr_state=calc
        If fr_cur<=0
           fz_state=zoom
        EndIf
      Case hiq
        GrabImage(fr_grab,fx_min*fx_fac,fy_min*fy_fac)
        SetBuffer BackBuffer()
Origin fx_ori,fy_ori                 ;sets drawing origin
        fr_saved=0
        Repeat 
          mouse()
	      If KeyDown(1) Exit                            
	      If MouseDown(1) Then fz_state=zoom:precision(def_pix_siz):Exit
	      If MouseDown(2) Then fz_state=unzoom:precision(def_pix_siz):Exit
	      If KeyDown(20)                                        ;if T key pressed         
             fz_frac=(fz_frac+1)Mod fz_frac_tn                  ;toggle fractal type
             fr_state=calc
             fr_grab=CreateImage(disp_width,disp_height)        ;create hiq image 
             SetBuffer FrontBuffer()
             Origin fx_ori,fy_ori                 ;sets drawing origin            
             Exit
          EndIf
	      If KeyDown(31) And fr_saved=0                         ;if S key pressed and image not saved yet
             fr_grab_name = "mze"+fz_frac+"_"+fx_ctr+"_"+fy_ctr+"_"+ZeroMe(3,fr_cur)+".bmp"  ;compose a name to save frame with
             SaveImage fr_grab,fr_grab_name                     ;save the frame
             fr_saved=1                                         ;
          EndIf
        Forever
     End Select

     If KeyDown(57)                                       ;if spacebar is pressed
        fz_prev_state=fz_state
        fz_state=hiq
        fr_state=calc
        precision(hiq_pix_siz)

        FreeImage fr_grab
        fr_grab=CreateImage(disp_width,disp_height)       ;create hiq image
        SetBuffer FrontBuffer()
Origin fx_ori,fy_ori                 ;sets drawing origin
     EndIf

 EndIf

Wend
;--------------------------------------------------------------------------
FreeImage fr_grab
EndGraphics
End

;--------------------------------------------------------------------------
; upd_values(x boundaries range#, y bnd range#, x center#, y center#)
; when one of the key values for the frame is changed, used to update the whole set accordingly
;
Function upd_values(new_x_rng#,new_y_rng#,new_fx_tctr#,new_fy_tctr#)
 fx_rng = new_x_rng                                       ;boundaries range x
 fy_rng = new_y_rng                                       ;                 y
 fx_ctr = new_fx_tctr                                     ;center x
 fy_ctr = new_fy_tctr
 fx_stp = fx_rng/fr_width                                 ;step to next x
 fy_stp = fy_rng/fr_height                                ;             y
 fx_min = fx_ctr-(fx_rng/2)                               ;first x
 fy_min = fy_ctr-(fy_rng/2)                               ;      y
 fx_max = fx_ctr+(fx_rng/2)                               ;last x
 fy_max = fy_ctr+(fy_rng/2)                               ;     y
 fx_cur = fx_min                                          ;current x and y = top left
 fy_cur = fy_min
 fx_fac = disp_width/fx_rng                               ;diplaying factor
 fy_fac = disp_height/fy_rng 
 fx_ori = dispx_ctr-(fx_ctr*fx_fac)                       ;point to set origin to
 fy_ori = dispy_ctr-(fy_ctr*fy_fac)
 Origin fx_ori,fy_ori
End Function

;--------------------------------------------------------------------------
; frame_mandel()
; draw a point using iter_mandel color and then go to the next point, reset values if no next point 
;
Function frame_mandel()                                   ;there is no loop first point should be set already
 Select fz_frac
   Case 0
     iter_mandel(fx_cur,fy_cur,def_thr,def_iter_max) 
   Case 2
     iter_dragon_mandel(fx_cur,fy_cur,def_thr,def_iter_max)
   Case 1
     iter_tristar_mandel(fx_cur,fy_cur,def_thr,def_iter_max)  ;so go iterate with that one
 End Select
 Rect fx_cur*fx_fac,fy_cur*fy_fac,pix_siz,pix_siz         ;draw it
 fx_cur=fx_cur+fx_stp                                     ;next column
 If fx_cur=>fx_max                                        ;if last column
    fx_cur=fx_min                                         ;   first column
    fy_cur=fy_cur+fy_stp                                  ;   next row
    If fy_cur>=fy_max                                     ;   if last row
       fy_cur=fy_min                                      ;      first row
       fr_state=done                                      ;      frame is done
    EndIf   
 EndIf
End Function

;--------------------------------------------------------------------------
; iter_mandel(x#, y#, threshold%, iterations maximum%)
; changes current color according to number of iterations needed before threshold for current point
; x and y coords are real coordinates (signed float)
; the higher iteration maximum, the more complex the pic, but calc time rises quite fast
; 
;     Const def_thr = 4
;Const def_iter_max = 96
;
Function iter_mandel(x_mand#,y_mand#,thr#,mxitr#)         ;max number of iterations 
  Local itr = 0                                           ;reset iteration counter
   Local p# = 0                                           ;reset p and q
   Local q# = 0
  Local op# = 0                                           ;will store p and q previous value
  Local oq# = 0          
  While  op+oq<thr And itr < mxitr                        ;while under threshold or iteration maximum
      q = p*q*2 + y_mand                                  ;it's a kind of magic
      p = op-oq + x_mand                                  ;magic
     op = p*p                                             ;mah-gic
     oq = q*q                                             ;maygeeeeeeeeeeeeeeeek 
    itr = itr+1                                           ;( guitar solo )
  Wend                                                    ;ok sorry so next iteration 
  Color  op Shl 2,oq Shl 2,itr Shl 1                      ;iteration # determine color 
End Function 
;
;
Function iter_dragon_mandel(x_mand#,y_mand#,thr#,mxitr#)  ;max number of iterations 
  Local itr = 0                                           ;reset iteration counter
   Local p# = 0.5                                         ;reset p and q
   Local q# = 0
  Local op# = 0.5                                         ;will store p and q previous value
  Local oq# = 0
      While ( (p*p)+(q*q)<thr And itr < mxitr)
        p=(oq-op)*(oq+op) + op
        q=op*oq
        q=q + q - oq
        op=(x_mand * p) + (y_mand * q)
        oq=(y_mand * p) - (x_mand * q)
        itr=itr + 1
      Wend
  Color  0,mxitr-itr,op Shl 2;q Shl 2                      ;iteration # determine color 

End Function 
;
;
Function iter_tristar_mandel(x_mand#,y_mand#,thr#,mxitr#) ;max number of iterations 
  Local itr = 0                                           ;reset iteration counter
   Local p# = 0                                           ;reset p and q
   Local q# = 0
  Local op# = 0                                           ;will store p and q previous value
  Local oq# = 0
      While ( op+oq<thr And itr < mxitr)
         p = p*q*2 + y_mand
         q = op-oq + x_mand
        op = p*p
        oq = q*q
       itr = itr + 1
      Wend
  Color  op Shl 2,itr Shl 1,itr                               ;iteration # determine color 
End Function 

;--------------------------------------------------------------------------
; precision (new pixel size%)
; set new pixel size and update related variables
;
Function precision(new_pix_siz)                         
  pix_siz = new_pix_siz
 fr_width = disp_width/pix_siz
fr_height = disp_height/pix_siz
   fx_stp = fx_rng/fr_width
   fy_stp = fy_rng/fr_height
End Function

;--------------------------------------------------------------------------
; mouse()
; interface to select a point to zoom to, using the image grabed just before looping  
;
Function mouse()
    Cls                                                   ;clear drawbuffer
    DrawBlock fr_grab,fx_min*fx_fac,fy_min*fy_fac         ;blit the hiq frame grabed before
    Color 155,255,202                                     ;change color for text
    mzx=MouseX()-fx_ori                                   ;get mouse value and aply origin offset
    mzy=MouseY()-fy_ori 
	Rect mzx,mzy-3,1,7                                    ;draw a cross there
	Rect mzx-3,mzy,7,1
    mzx=mzx/fx_fac                                        ;get real coordinates of that point
    mzy=mzy/fy_fac 
    Text fx_min*fx_fac,fy_min*fy_fac,mzx+" , "+mzy        ;prints them on top left of screen
    Text fx_min*fx_fac,(fy_max*fy_fac)-16,"T type | LMB zoom | RMB unzoom | S save | ESC quit"
    Select fz_frac
      Case 0
        Text (fx_max*fx_fac)-100,(fy_max*fy_fac)-16,"mandelbrot set"
      Case 1
        Text (fx_max*fx_fac)-100,(fy_max*fy_fac)-16,"dragon mandelbrot"
      Case 2
        Text (fx_max*fx_fac)-100,(fy_max*fy_fac)-16,"tristar mandelbrot"
    End Select
	Flip                                                   
End Function

;--------------------------------------------------------------------------
;ZeroMe() function by Mikkel Lokke                    flameduck@software.dk
;
Function ZeroMe$(zeros,number)
	num$=number
	If Len(num$)=>zeros
		Return number
	Else
		zero$=""
		For i=1 To zeros
			zero$=zero$+"0"
		Next
		zero$=Left$(zero$,zeros-Len(num$))
		zero$=zero$+num$
		Return zero$
	EndIf
End Function
;--------------------------------------------------------------------------