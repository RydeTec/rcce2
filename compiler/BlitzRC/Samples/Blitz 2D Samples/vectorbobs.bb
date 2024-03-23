; ------------------------------------------------------------------------------
; VectorBobs Demo
; By Dave Reed 
; ------------------------------------------------------------------------------
; Memories of Amiga coding.....
; ------------------------------------------------------------------------------


; A vector in 3D space (x,y,z coords)
; ---------------------------
Type Vector3
	Field x#,y#,z#
End Type

; A 3x3 transformation matrix
; ---------------------------
Type Matrix3
	Field m11#, m21#, m31#,m12#, m22#, m32#,m13#, m23#, m33#
End Type


; Rotation matrices
; --------------------------
matRotX.Matrix3=New Matrix3
matRotY.Matrix3=New Matrix3
matRotZ.Matrix3=New Matrix3

; Temporary vectors
; -------------------------
temp.Vector3=New Vector3
temp2.Vector3=New Vector3

; Array of 512 objects
; ---------------------------
Dim bobs.Vector3(512) 

; Set Initial positions
; ---------------------------
n=0
For i=0 To 19
  For j=0 To 19
	    bobs(n)=New Vector3
		bobs(n)\x=(i*20)-200
		bobs(n)\y=(j*20)-200
		bobs(n)\z=0
	    n=n+1
  Next	
Next

; store number of objects used
; ---------------------------
max=n

; GFX mode
Graphics 512,384,16

; Load the image
Dim imgBall(64)
imgBall(0)=LoadImage("ball.bmp")
MaskImage imgBall(0),0,0,0
HandleImage imgBall(0),16,16

; Create 64 different sized copies
For n=1 To 63
	imgBall(n)=CopyImage(imgBall(n-1))
	ScaleImage imgBall(n),0.98,0.98
	MaskImage imgBall(n),0,0,0
Next


; ------------------------------------------------------------------------------
; Main loop
; ------------------------------------------------------------------------------
While quit=False

	; Create rotation matrices
	angle#=angle#+0.3
	CreateZRotMatrix(matRotZ,angle#)
	angle2#=angle2#+0.178
	CreateYRotMatrix(matRotY,angle2#)
	angle3#=angle3#+0.228
	CreateXRotMatrix(matRotX,angle3#)

	; Increment timer
	t#=t#+2
	
	; Process each bob
	For n=0 To max-1
		; Transform it by each matrix
		TransformVector(bobs(n),temp,matRotZ)
		TransformVector(temp,temp2,matRotY)
		TransformVector(temp2,temp,matRotX)
		
		; Pick an image to used (based on sine(timer))
		p#=n*1.3
		z=32+(Sin(p+t)*31)		
		DrawImage imgBall(z),temp\x+256,temp\y+192	
	Next

	; Flip buffers
	Flip
	SetBuffer(BackBuffer())
	Cls

	; Quit if a key is pressed
	If KeyDown(1) quit=True
Wend
End



; ------------------------------------------------------------------------------
; Create a rotation matrix To rotate about the z-axis
; ------------------------------------------------------------------------------
Function CreateZRotMatrix( m.Matrix3, rot#)
	m\m11=Cos(rot):	m\m21=-Sin(rot): 	m\m31=0
	m\m12=Sin(rot):	m\m22=Cos(rot):		m\m32=0	
	m\m13=0:		m\m23=0: 			m\m33=1
End Function


; ------------------------------------------------------------------------------
; Create a rotation matrix To rotate about the y-axis
; ------------------------------------------------------------------------------
Function CreateYRotMatrix( m.Matrix3, yrot#)
	m\m11=Cos(rot):	m\m21=0: 				m\m31=-Sin(rot)
	m\m12=0:		m\m22=1:				m\m32=0	
	m\m13=Sin(rot):	m\m23=0: 				m\m33=Cos(rot):
End Function


; ------------------------------------------------------------------------------
; Create a rotation matrix To rotate about the x-axis
; ------------------------------------------------------------------------------
Function CreateXRotMatrix( m.Matrix3, rot#)
	m\m11=1:			m\m21=0: 			m\m31=0
	m\m12=0:			m\m22=Cos(rot):		m\m32=-Sin(rot)	
	m\m13=0:			m\m23=Sin(rot): 	m\m33=Cos(rot)
End Function


; ------------------------------------------------------------------------------
; Multiply a vector by a matrix
; ------------------------------------------------------------------------------
Function TransformVector( src.Vector3,dest.Vector3, m.Matrix3)
	dest\x= (src\x*m\m11)+(src\y*m\m21)+(src\z*m\m31)
	dest\y= (src\x*m\m12)+(src\y*m\m22)+(src\z*m\m32)
	dest\z= (src\x*m\m13)+(src\y*m\m23)+(src\z*m\m33)
End Function