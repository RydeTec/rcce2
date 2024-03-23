; Effects Library
; (c)2001 David Bird
; dave@birdie72.freeserve.co.uk

Function CreateReed(length#=5,width#=.1,stps=10)
	mesh=CreateMesh()
	surf=CreateSurface(mesh)
	stpy#=length/Float(stps)
	hwid#=width/2.0
	y#=0
	xred#=.002
	For a=0 To stps
		v#=-a/Float(stps)
		AddVertex surf,-hwid,y,0,0,v
		AddVertex surf,hwid,y,0,1,v
		hwid=hwid-xred
		y=y+stpy
	Next
	cnt=0
	For a=0 To stps-1
		AddTriangle surf,cnt,cnt+3,cnt+1
		AddTriangle surf,cnt,cnt+2,cnt+3
		AddTriangle surf,cnt,cnt+1,cnt+3
		AddTriangle surf,cnt,cnt+3,cnt+2
		cnt=cnt+2
	Next
	UpdateNormals mesh
	Return mesh
End Function

Type reed
	Field ent
	Field phase#
End Type

Function Add_reed(x#,y#,z#,Ln#)
	a.reed=New reed
	a\ent=CopyEntity(G_reed)
	EntityAutoFade a\ent,.1,100
	PositionEntity a\ent,x,y,z
	ScaleEntity a\ent,1,ln,1
	a\phase=Rnd(360)
	Return a\ent
End Function

Function Update_reeds()
	For a.reed=Each reed
		surf=GetSurface(a\ent,1)
		stren=(CountVertices(surf)-1)
		p#=a\phase
		a\phase=a\phase+1
		e=1
		For v=0 To CountVertices(surf)-1
			z#=(v*.005)*Sin(p)
			VertexCoords surf,v,VertexX(surf,v),VertexY(surf,v),z
			e=1-e
			If e=1 Then	p=p+35
		Next
	Next
End Function

Function Erase_reeds()
	For a.reed=Each reed
		If a\ent Then FreeEntity a\ent
	Next
	Delete Each reed
End Function