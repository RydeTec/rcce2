;
; Example of use FastPointer library.
; (c) 2008-2009 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Attention! Functions only for the advanced users, use them very accurately.
; If you do not understand how it works, do not use it!
;



; create scene objects
Graphics3D 800,600,0,2
SetFont LoadFont("Tahoma",15)
Camera = CreateCamera()  :  PositionEntity Camera, 0, 0, -3.5
Light = CreateLight()
Global Cube = CreateCube()



; function for thread (with single integer param - it is obligatory)
; don't load meshes in thread, if you use RenderWorld function in main loop
Function ThreadFunction ( entity%=0 )
	Repeat
		TurnEntity entity, 0.5, 0.3, 0.4
		Delay 10
	Forever
End Function


; get pointer to thread function
ThreadFunctionPointer = FunctionPointer()
	Goto skip
	ThreadFunction()		; <<< get pointer from this function
	.skip


; create thread
Thread = CreateThread (ThreadFunctionPointer, Cube)


; main loop
While Not KeyHit(1)
	RenderWorld
	Text 10,10,"You can control entity from threads!"
	Flip 0
Wend


; freel thread
If IsThread(Thread) Then FreeThread(Thread)


WaitKey()
End