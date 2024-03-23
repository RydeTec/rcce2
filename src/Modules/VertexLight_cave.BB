


Type LightType;
	Field LightID;
	Field R#, G#, B#
	Field Range#
	Field Class
	Field X#, Y#, Z#
	Field XR#, YR#, ZR#
End Type

Type VertexLightType;this is the light data for vertices
	Field MeshID;what mesh it belongs to
	Field LightID;what light illuminates the Mesh
	Field R#, G#, B#;the amount this light illuminates this vertex
	Field SurfaceID;Surface of Mesh the light relates to
	Field VertexID;Vertex the light relates to
End Type

Global LPick=0
Global VertexLightAmbienceR# = 0;makes sure light never goes below ambience
Global VertexLightAmbienceG# = 0
Global VertexLightAmbienceB# = 0

Function SetVertexLightAmbience( Mesh, R#, G#, B# )
	For i=1 To CountSurfaces( Mesh )
		s = GetSurface( Mesh, i )
		For v=0 To CountVertices( s )-1
			VertexColor s, v, R#, G#, B#
		Next
	Next
	VertexLightAmbienceR# = R#
	VertexLightAmbienceG# = G#
	VertexLightAmbienceB# = B#
End Function

Function CreateVertexLight( LightID, R#, G#, B#, Range#, Class, X#, Y#, Z#, XR#, YR#, ZR# )
	;create a new light
	VLight.LightType = New LightType
	VLight\LightID = LightID
	VLight\R# = R#
	VLight\G# = G#
	VLight\B# = B#
	VLight\Range# = Range#
	VLight\Class = Class
	VLight\X# = X#
	VLight\Y# = Y#
	VLight\Z# = Z#
	VLight\XR# = XR#
	VLight\YR# = YR#
	VLight\ZR# = ZR#
End Function



Function VertexLightMesh( Mesh, LightID, LightX#, LightY#, LightZ#, R#, G#, B#, Range#, AmbientLocked =1,osx#,osy#,osz#,checkdist=1)
    vx# = GetMatElement#(Mesh, 0, 0)
	vy# = GetMatElement#(Mesh, 0, 1)
	vz# = GetMatElement#(Mesh, 0, 2)
	XScale# = Sqr(vx# * vx# + vy# * vy# + vz# * vz#)
	vx# = GetMatElement#(Mesh, 1, 0)
	vy# = GetMatElement#(Mesh, 1, 1)
	vz# = GetMatElement#(Mesh, 1, 2)
	YScale# = Sqr(vx# * vx# + vy# * vy# + vz# * vz#)
	vx# = GetMatElement#(Mesh, 2, 0)
	vy# = GetMatElement#(Mesh, 2, 1)
	vz# = GetMatElement#(Mesh, 2, 2)
	ZScale# = Sqr(vx# * vx# + vy# * vy# + vz# * vz#)
	
	 ;test the distance of entire mesh first
     v_distance#( EntityX(mesh,1), EntityY(mesh,1), EntityZ(mesh,1), Lightx#, Lighty#, Lightz# )
 	 If MeshWidth(mesh)*xscale>MeshHeight(mesh)*yscale Then 
	  If MeshWidth(mesh)*xscale>MeshDepth(mesh)*zscale Then mrad#=MeshWidth(mesh)*xscale Else mrad#=MeshDepth(mesh)*zscale
	 Else
	  If MeshHeight(mesh)*yscale>MeshDepth(mesh)*zscale Then mrad#=MeshHeight(mesh)*yscale Else mrad#=MeshDepth(mesh)*zscale
	 EndIf
	
	If mdist#>(Range+mrad#) And checkdist=1 Then Return
	Local dist#
	Local DummyLight = CreateCube()
	Local DummyTarget = CreateCube()
	
	If R#<VertexLightAmbienceR# Then R# = VertexLightAmbienceR#
	If G#<VertexLightAmbienceG# Then G# = VertexLightAmbienceG#
	If B#<VertexLightAmbienceB# Then B# = VertexLightAmbienceB#
	
	Local RPer# = R# / 256
	Local GPer# = G# / 256
	Local BPer# = B# / 256
	
	
	
	ScaleEntity DummyTarget, .005, .005, .005
	EntityPickMode DummyTarget, 2
	
	ScaleEntity DummyLight, .01, .01, .01
	PositionEntity DummyLight, LightX#, Lighty#, LightZ#
	
	For i=1 To CountSurfaces( Mesh )
		s = GetSurface( Mesh, i )
		For v=0 To CountVertices( s )-1
			            TFormPoint(VertexX#(s,v),VertexY#(s,v),VertexZ#(s,v),mesh,0)
			dist# = v_distance#( TFormedX(), TFormedY(), TFormedZ(), Lightx#, Lighty#, Lightz# )
			If (dist# <= Range#) Then
				
				;TFORMING WILL ALLOW PROPER LIGHTMAPPING ON SCALED ENTITTIES
				PositionEntity DummyTarget, TFormedX(), TFormedY(), TFormedZ()
				
				PointEntity DummyLight, DummyTarget
				;                 dummytarget2=CopyMesh(dummytarget)
				;PositionEntity DummyTarget2, TFormedX(), TFormedY(), TFormedZ()
				               
				LPick = EntityPick( DummyLight, 1000000 )
				
				If LPick = DummyTarget
					VertexLight.VertexLightType = New VertexLightType
					VertexLight\MeshID = Mesh
					VertexLight\LightID = LightID
					VertexLight\R# = R#-( RPer# * Dist# )
					VertexLight\G# = G#-( RPer# * Dist# )
					VertexLight\B# = B#-( RPer# * Dist# )
					VertexLight\SurfaceID = i
					VertexLight\VertexID = v
					
					r2=VertexRed(s,v)
					g2=VertexBlue(s,v)
					b2=VertexGreen(s,v)
					
					R1#=r# - (RPer# * (Dist#-(range/2)))
					G1#=G# - (GPer# * (Dist#-(range/2)))
					B1#=B# - (BPer# * (Dist#-(range/2)))
					finalr#=(r1 + r2) *.5
					finalg#=(g1 + g2) *.5
					finalb#=(b1 + b2) *.5


					
					If finalR#<VertexLightAmbienceR# Then finalR# = VertexLightAmbienceR#
					If finalG#<VertexLightAmbienceG# Then finalG# = VertexLightAmbienceG#
					If finalB#<VertexLightAmbienceB# Then finalB# = VertexLightAmbienceB#
					
					If finalR#>255 Then finalR# = 255
					If finalG#>255 Then finalG# = 255
					If finalB#>255 Then finalB# = 255
					
					
					VertexColor s, v,finalr#,finalg#,finalb#
					
				EndIf
			EndIf
		Next
	Next
	
	UpdateNormals Mesh
	
	FreeEntity DummyLight
	FreeEntity DummyTarget
	
	
End Function

Function v_distance#( x#, y#, z#, x2#, y2#, z2# )
	value#=Sqr((x#-x2#)*(x#-x2#)+(y#-y2#)*(y#-y2#)+(z#-z2#)*(z#-z2#))
	
	Return value#
End Function