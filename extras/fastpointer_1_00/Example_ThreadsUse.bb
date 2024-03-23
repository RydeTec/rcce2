;
; Example of use FastPointer library.
; (c) 2008-2009 created by MixailV aka Monster^Sage [monster-sage@mail.ru]  http://www.fastlibs.com
;
; Attention! Functions only for the advanced users, use them very accurately.
; If you do not understand how it works, do not use it!
;


Global ThreadVar = 0


; function for thread (with single integer param - it is obligatory)
; don't load meshes in thread, if you use RenderWorld function in main loop
Function ThreadFunction ( StartValue% = 0 )
	ThreadVar = StartValue
	Repeat
		ThreadVar = ThreadVar+1
		Delay 100
	Forever
End Function


; get pointer to thread function
ThreadFunctionPointer = FunctionPointer()
	Goto skip
	ThreadFunction()		; <<< get pointer from this function
	.skip


; create scene objects
Graphics3D 800,600,0,2
SetFont LoadFont("Tahoma",15)
Camera = CreateCamera()  :  PositionEntity Camera, 0, 0, -3.5
Light = CreateLight()
Cube = CreateCube()


; create thread
Thread = CreateThread (ThreadFunctionPointer, 100)


; main loop
While Not KeyHit(1)
	TurnEntity Cube, 0.2, 0.3, 0.4
	RenderWorld
	Text 10, 10, "ThreadVar value: "+Str(ThreadVar)
	Flip
Wend


; free thread
If IsThread(Thread) Then FreeThread(Thread)


WaitKey()
End