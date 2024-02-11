;--------------------------------------------------------------------------
;                                                                
;                                                        
;  (press ESC to quit)
;--------------------------------------------------------------------------
 Const dwidth = 1024                           ;display width
Const dheight = 768                            ;display height
truc=LoadSound("sounds\mtruc.wav")                     ;load some sound
times=LoadFont( "Times",64,1,1)                ;load some font 
SetFont times                                  ;use that font
fh=FontHeight();/3                              ;get height of font
fw=FontWidth()/3                               ;    width
rap$=" "+"Blitz Basic !"                       ;first char must be a space
;         rap$ = " "+Input$("Text : ")         ;alternatively input text to use
            n = Len(rap)-1                     ;number of objects
     Const a# = .2                             ;move factor  0=none -> 1=too much
     Const b# = 1.66                           ;elasticity
      Const k = 64                             ;minimum space in between objects
   Global px# = 0                              ;store previous obj x value
   Global py# = 0                              ;   "        "      y value

   Global alt = 0                              ;altern flag 
Type element                                   ;all obj are of type element
  Field lt$                                    ;letter to be drawn
  Field x#,y#                                  ;drawing coords
  Field vx#,vy#                                ;coords offset (from previous x,y to current)
  Field c                                      ;collision
End Type

For i=0 To n                                   ;
  obj.element=New element                      ;create an obj
  obj\lt$=Mid$(rap,i+1,1)                      ;assign a letter
  obj\x=dwidth/2+10*i                          ;initial x position
  obj\y=dheight/2                              ;initial y position
  obj\vx=0                                     ;no move no offset
  obj\vy=0                                     ;
  obj\c=0  
Next

;--------------------------------------------------------------------------
Graphics dwidth,dheight                        ;enter graphic mode
SetBuffer BackBuffer()                         ;use double buffer

While Not KeyDown(1)                           ;while not ESC
  Cls                                          ;clear screen
  obj=First element                            ;go to first obj
  obj\x=MouseX()                               ;this one is the reference one
  obj\y=MouseY()                               ;    and inherits mouse values directly
  px=obj\x : py=obj\y                          ;store value for next obj

  obj=After obj                                 ;go next one

  While obj<>Null                              ;while there are objs remaining
    obj\vx=(obj\vx+(px+k-obj\x)*a)/b           ;calculate new offset
    obj\vy=(obj\vy+(py-obj\y)*a)/b         
    obj\x=obj\x+obj\vx                         ;calculate new end value
    obj\y=obj\y+obj\vy
    result=RectsOverlap(obj\x,obj\y,fw,fh,px,py,fw,fh)    ;test if it collide previous one
    If result=1 And obj\c=0 Then obj\c=1:PlaySound truc   ;if yes if it's new play a sound
    If result=0 And obj\c=1 Then obj\c=0       ;if no but it was before reset flag to 0              
    px=obj\x : py=obj\y                        ;store value for next obj
    Text obj\x,obj\y,obj\lt,1                  ;draw it 
    obj=After obj                               ;go next one
  Wend

  Flip                                         ;swap drawbuffers
  Delay 20                                     ;change this value according to your computer speed
Wend
;--------------------------------------------------------------------------
Delete Each element                            ;free element memory
EndGraphics                                    ;quit graphic mode
End                                            ;bye
;--------------------------------------------------------------------------