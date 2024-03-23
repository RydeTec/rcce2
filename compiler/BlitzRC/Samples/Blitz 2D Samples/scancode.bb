Graphics 800,600


While KeyDown(1)=0
 For a=0 To 255
Text 50,50,"Press the key that you need the scan code for..."
	If KeyDown(a)<>0 Then
		Cls
		Text 100,100,"Scan code for this key is "+Str$(a)
   EndIf 
 Next
Wend