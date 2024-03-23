
Global c_surfs
Dim c_surf(200)
Dim c_brush(200)
Dim c_tex(200)
Dim c_tex_name$(200)

Function savemultimesh(mesh,fn$)
c_surfs=CountSurfaces(mesh)
For i=1 To c_surfs
 c_surf(i)= GetSurface(mesh,i)
 c_brush(i)=GetSurfaceBrush( c_surf(i) )
 c_tex(i)=GetBrushTexture( c_brush(i) )
 c_tex_name$(i)=Lower$(TextureName$(c_tex(i))) ; Full (!) Texture Path
 curdir$=Lower$(CurrentDir$()) 
 c_tex_name$(i)= Replace$(c_tex_name$(i),curdir$,"") ;<<<<<<<<<<<<<<<<<<<
; Print c_tex_name$(i)
; If c_tex_name$(i)="" Then Print "Error: Surface No."+i+" has no Texture"
; If FileType(c_tex_name$(i))<>1 Then Print "Warning: Surface No."+i+" uses nonexistant Texture ("+c_tex_name$(i)+")."
Next

WriteBB3D( fn$,mesh )

For i=1 To c_surfs
 FreeBrush c_brush(i); release memory
 FreeTexture c_tex(i)
Next
End Function



Function WriteBB3D( f_name$,mesh )
	DebugLog("Write File is: " + f_name)
	file=WriteFile( f_name$ )

	b3dSetFile( file )
	
	b3dBeginChunk( "BB3D" )
		b3dWriteInt( 1 )	;version

		b3dBeginChunk( "TEXS" ) ; list all textures used by the mesh
		For i=1 To c_surfs
			b3dWriteString( c_tex_name$(i) ) 	;texture file
;strip path here
dupe$=c_tex_name$(i)
;strip path
      ;so strip the path
	  For i2= Len(dupe$) To 1 Step -1
	   If Mid$(dupe$,i2,1)="\" Then
	    dupe$=Right$(dupe$,Len(dupe)-i2)
	    Exit
	   EndIf
	  Next
cnn=Instr(dupe$,"branch")
If cnn=1 Then 
cnn=1+4 
;cnn=1+2 
tscale#=1
Else 
cnn=1
tscale#=barkscale#
EndIf	
  	        b3dWriteInt( cnn )					;flags
			b3dWriteInt( 2 )					;blend
			b3dWriteFloat( 0 )					;x in tex 0 (hu?)
			b3dWriteFloat( 0 )					;y in tex 0
			b3dWriteFloat( 1/tscale# )					;x scale 1
			b3dWriteFloat( 1/tscale# )					;y scale 1
			b3dWriteFloat( 0 )					;rotation 0
			
		Next
		b3dEndChunk()	;end of TEXS chunk

		
		For i=1 To c_surfs
			b3dBeginChunk( "BRUS" ) ; describe all brushes used by the mesh
		
			b3dWriteInt( 1 )					;number of textures per brush ; (eg 2 with lightmap)
			b3dWriteString( "brush"+(i-1) )		;brushname
			b3dWriteFloat( .09+(i*.01))					;red
			b3dWriteFloat( .09+(i*.01))					;green
			b3dWriteFloat( .09+(i*.01))					;blue
			b3dWriteFloat( 1 )					;alpha
			b3dWriteFloat( 0)					;shininess
cnn=Instr(dupe$,"branch")
If cnn=0 Then 
cns=2 
cb=1
Else 
cns=2+16
cb=1
EndIf

			b3dWriteInt( cb )					;blendmode
			b3dWriteInt( cns )					;FX
			b3dWriteInt( i-1 )					;used texture index 
;			b3dWriteInt( ? )					;additional texture index (eg lightmap), but here we only use 1 (see above)

			b3dEndChunk()	;end of BRUS chunk
		Next
		
CNTSU=CNTSU+1

		b3dBeginChunk( "NODE" )
		b3dWriteString( "branch")
			b3dWriteFloat( 0 )	;x_pos
			b3dWriteFloat( 0 )	;y_pos
			b3dWriteFloat( 0 )	;z_pos
			b3dWriteFloat( 1 )	;x_scale
			b3dWriteFloat( 1 )	;y_scale
			b3dWriteFloat( 1 )	;z_scale
			b3dWriteFloat( 1 )	;rot_w
			b3dWriteFloat( 0 )	;rot_x
			b3dWriteFloat( 0 )	;rot_y
			b3dWriteFloat( 0 )	;rot_z
			WriteMESH( mesh )
		b3dEndChunk()	;end of NODE chunk
		
	b3dEndChunk()	;end of BB3D chunk
	
	CloseFile file
End Function

Function WriteMESH( mesh )

	n_surfs=CountSurfaces( mesh )
	
	b3dBeginChunk( "MESH" )
		b3dWriteInt( -1 )				;no 'entity' brush -1
		
		b3dBeginChunk( "VRTS" )
			b3dWriteInt( 2 )			;flags - 0=no normal/color
			b3dWriteInt( 1 )			;number of tex_coord sets (eg: 2 with lightmap)
			b3dWriteInt( 2 )			;coords per set (u,v,w?) 2 with uv, 3 with uvw
			
			For k=1 To n_surfs
				surf=GetSurface( mesh,k )
				n_verts=CountVertices( surf )-1
				
 For j=0 To n_verts
					b3dWriteFloat( VertexX( surf,j ) )
					b3dWriteFloat( VertexY( surf,j ))
					b3dWriteFloat( VertexZ( surf,j ) )
					
                    b3dWriteFloat( VertexRed(surf,j)/255)
					b3dWriteFloat( VertexGreen(surf,j)/255)
					b3dWriteFloat( VertexBlue(surf,j)/255)

					b3dWriteFloat( 1.0)

					
					b3dWriteFloat( VertexU#( surf,j,0 ) )
					b3dWriteFloat( VertexV#( surf,j,0 ) )					

					
				Next


			Next
		b3dEndChunk()	;end of VRTS chunk
		
		first_vert=0
		For k=1 To n_surfs
			surf=GetSurface( mesh,k )
			n_tris=CountTriangles( surf )-1
			
			b3dBeginChunk( "TRIS" )
				b3dWriteInt( k-1 )		;brush for these triangles (surf -1 !!!)
				
				For j=0 To n_tris
					b3dWriteInt( first_vert+TriangleVertex( surf,j,0 ) )
					b3dWriteInt( first_vert+TriangleVertex( surf,j,1 ) )
					b3dWriteInt( first_vert+TriangleVertex( surf,j,2 ) )
				Next
				
			b3dEndChunk()	;end of TRIS chunk
			
			first_vert=first_vert+CountVertices( surf )
			
		Next
		
	b3dEndChunk()	;end of MESH chunk
	
End Function