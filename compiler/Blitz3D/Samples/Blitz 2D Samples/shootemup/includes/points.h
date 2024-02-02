Function addpoints(amount)

	ply_points=ply_points+amount

End Function

Function drawpoints(x,y,value)
	
;	SetBuffer ImageBuffer(hudbufferimg)
;	DrawImage hudimg,0,0 
	
	For f=1 To Len(value) 
		vl=Mid$(value,f,1)
		DrawImage fontnumbers,x+xpf,y,vl
		xpf=xpf+8
	Next 

;	SetBuffer BackBuffer()

End Function