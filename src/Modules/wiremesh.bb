Type WireMesh
	Field Entity
End Type

Function DrawWireMeshes(Camera,Tween#=-1)
	For W.WireMesh = Each WireMesh
		EntityAlpha W\Entity,1
	Next
	
	CameraClsMode Camera,1,1
	WireFrame True
	
	If Tween <> -1 Then
		RenderWorld Tween
	Else
		RenderWorld
	EndIf
	
	WireFrame False
	CameraClsMode Camera,0,0
	
	For W.WireMesh = Each WireMesh
		EntityAlpha W\Entity,0
	Next
End Function

Function CreateWireCube()
	M=CreateMesh()
	S = CreateSurface(M)
	;outer edges
	WireLine3D S,-1,-1,-1,-1,1,-1
	WireLine3D S,1,-1,-1,1,1,-1
	WireLine3D S,1,-1,1,1,1,1
	WireLine3D S,-1,-1,1,-1,1,1
	
	;top edges
	WireLine3D S,1,1,1,-1,1,1
	WireLine3D S,1,1,-1,-1,1,-1
	WireLine3D S,-1,1,-1,-1,1,1
	WireLine3D S,1,1,-1,1,1,1
	
	; bottom edges
	WireLine3D S,1,-1,1,-1,-1,1
	WireLine3D S,1,-1,-1,-1,-1,-1
	WireLine3D S,-1,-1,-1,-1,-1,1
	WireLine3D S,1,-1,-1,1,-1,1

	EntityFX M,1+2+16
	EntityAlpha M,0
	W.WireMesh = New WireMesh
	W\Entity = M
	Return M
End Function

Function CreateWireSphere(Segments=8)
	M = CreateMesh()
	S = CreateSurface(M)
	Dummy = CreateSphere(Segments)
	DS = GetSurface(Dummy,1)
	For N = 0 To CountTriangles(DS)-1
		V1 = TriangleVertex(DS,N,0)
		V2 = TriangleVertex(DS,N,1)
		V3 = TriangleVertex(DS,N,2)
		If V1 < Segments*2 Then WireLine3D S,VertexX(DS,V1),VertexY(DS,V1),VertexZ(DS,V1),VertexX(DS,V2),VertexY(DS,V2),VertexZ(DS,V2)
		WireLine3D S,VertexX(DS,V2),VertexY(DS,V2),VertexZ(DS,V2),VertexX(DS,V3),VertexY(DS,V3),VertexZ(DS,V3)
	Next
	FreeEntity Dummy
	EntityFX M,1+2+16
	EntityAlpha M,0
	W.WireMesh = New WireMesh
	W\Entity = M
	Return M
End Function

Function CreateWireCylinder(Segments=8)
	M = CreateMesh()
	S = CreateSurface(M)
	AngleStep# = 360.0/Segments
	While N < 360
		WireLine3D S,Sin(N),1,Cos(N),Sin(N),-1,Cos(N)
		WireLine3D S,Sin(N),1,Cos(N),Sin(N+AngleStep),1,Cos(N+AngleStep)
		WireLine3D S,Sin(N),-1,Cos(N),Sin(N+AngleStep),-1,Cos(N+AngleStep)
		N = N + AngleStep
	Wend
	EntityFX M,1+2+16
	EntityAlpha M,0
	W.WireMesh = New WireMesh
	W\Entity = M
	Return M
End Function

Function CreateWireRing(Segments=8)
	M = CreateMesh()
	S = CreateSurface(M)
	AngleStep# = 360.0/Segments
	While N < 360
		WireLine3D S,Sin(N),0,Cos(N),Sin(N+AngleStep),0,Cos(N+AngleStep)
		N = N + AngleStep
	Wend
	EntityFX M,1+2+16
	EntityAlpha M,0
	W.WireMesh = New WireMesh
	W\Entity = M
	Return M
End Function

Function CreateWireBevel(Segments=8,OuterRadius#=1,InnerRadius#=.5)
	M = CreateMesh()
	S = CreateSurface(M)
	AngleStep# = 360.0/Segments
	While N < 360
		WireLine3D S,Sin(N)*OuterRadius,1,Cos(N)*OuterRadius,Sin(N)*InnerRadius,-1,Cos(N)*InnerRadius
		WireLine3D S,Sin(N)*OuterRadius,1,Cos(N)*OuterRadius,Sin(N+AngleStep)*OuterRadius,1,Cos(N+AngleStep)*OuterRadius
		WireLine3D S,Sin(N)*InnerRadius,-1,Cos(N)*InnerRadius,Sin(N+AngleStep)*InnerRadius,-1,Cos(N+AngleStep)*InnerRadius
		N = N + AngleStep
	Wend
	EntityFX M,1+2+16
	EntityAlpha M,0
	W.WireMesh = New WireMesh
	W\Entity = M
	Return M
End Function

Function CreateWireMesh()
	M = CreateMesh()
	W.WireMesh = New WireMesh
	W\Entity = M
	EntityAlpha M,0
	EntityFX M,1+2+16
	CreateSurface(M)
	Return M
End Function

Function WireLine3D(S,X#,Y#,Z#,X2#,Y2#,Z2#)
	For w.WireMesh = Each WireMesh
		If S = W\Entity Then
			S = GetSurface(W\Entity,1)
			Exit
		ElseIf S = GetSurface(W\Entity,1) Then
			Exit
		EndIf
	Next
	V=AddVertex(S,X,Y,Z)
	AddVertex(S,X2,Y2,Z2)
	AddVertex(S,X2,Y2,Z2)
	r = ColorRed()
	g = ColorGreen()
	b = ColorBlue()
	For n = 0 To 2
		VertexColor S,V+N,r,g,b,255
	Next
	AddTriangle S,V,V+1,V+2
End Function

Function ClearLines(Mesh)
	ClearSurface(GetSurface(Mesh,1))
End Function