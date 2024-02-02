; stars library

; (c) Graham Kennedy 2001




Type star
	Field x#,y#
	Field layer
End Type


Function AddStar(x,y,layer)
	s.star = New star
	
	s\x = x
	s\y = y
	s\layer = layer

End Function

Function MoveStars(dx#,dy#)

	For s.star = Each star
		
		s\x = s\x + (dx / s\layer)
		s\y = s\y + (dy / s\layer)
		
		If s\x < 0 Then 
			s\x = s\x + width
			s\y = Rnd(height)
		End If
		If s\x > width Then 
			s\x = s\x - width
			s\y = Rnd(height)
		End If
		
		If s\y < 0 Then 
			s\y = s\y + height
			s\x = Rnd(width)
		End If
		
		If s\y > height Then 
			s\y = s\y - height
			s\x = Rnd(width)
		End If
		
	Next
End Function

Function ShowStars()
	LockBuffer GraphicsBuffer()
	
	For s.star = Each star
		r = Rnd(255)
		g = Rnd(255)
		b = Rnd(255)
	
		WritePixel s\x,s\y,((r*256)+g)*256+b
	
	Next 
	
	UnlockBuffer GraphicsBuffer()
End Function
	

Function CleanupStars()

	Delete Each star

End Function
