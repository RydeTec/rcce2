; Example of double bufferiing
; An easy to use and important feature in Blitz
; for more examples and lots of help visit www.blitzbasic.com
; or visit http://users.breathemail.net/georgebray

Graphics 640,480 ; goes into graphics mode & sets the resolution

SetBuffer FrontBuffer() ; draw to the front buffer
Text 0,0,"This drawn to the front buffer"
Text 50,50,"Press spacebar..."
Text 50,100,"Press ecape to exit."

SetBuffer BackBuffer() ; draw to the back buffer
Text 0,0,"This drawn to the back buffer"
Text 50,50,"Press spacebar again..."
Text 50,100,"Press ecape to exit."

While Not KeyDown(1) ;keep looping until ESC pressed


	WaitKey ;wait for any keypress
	

	Flip ;swap front and back buffers
	
Wend ; continue until progrgram ends