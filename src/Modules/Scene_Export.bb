 Global c_surfs
Dim c_surf(1000)
Dim c_brush(1000)
Dim c_tex(1000)
Dim c_tex_name$(1000)

Function savemultimesh(mesh,fn$,newdir$,TEXSCALE#=1.0)
	ChangeDir thispath$
	DebugLog "EXPORTED TO "+FN$
	If FileType(newdir$)<>2 Then CreateDir newdir$
	; track down used textures (thanks Mark!)
	c_surfs=CountSurfaces(mesh)
	
	For i=1 To c_surfs
		 c_surf(i)= GetSurface(mesh,i)
		 c_brush(i)=GetSurfaceBrush( c_surf(i))
		 c_tex(i)=GetBrushTexture( c_brush(i))
		 c_tex_name$(i)=Lower$(TextureName$(c_tex(i))) ; Full (!) Texture Path
         If Trim$(c_tex_name$(i))="" Then c_tex_name$(i)="NotFound.jpg" 
		 curdir$=Lower$(CurrentDir$()) 
	Next
	
	WriteBB3D( fn$,mesh,newdir$,TEXSCALE# )
	
	For i=1 To c_surfs
		 FreeBrush c_brush(i); release memory
		 FreeTexture c_tex(i)
	Next
End Function



Function WriteBB3D( f_name$,mesh,tofolder$,texscale2#=1.0 )
	file=WriteFile( f_name$ )
	
	b3dSetFile( file )
	
	b3dBeginChunk( "BB3D" )
	b3dWriteInt( 1 );version
	
	b3dBeginChunk( "TEXS" ) ; list all textures used by the mesh
	For i=1 To c_surfs
		;first surface needs primary texture set as same name as model name. :)
		    ;so strip the path
	     ctex2$=c_tex_name$(i)
		  For i2= Len(ctex2$) To 1 Step -1
		   If Mid$(ctex2$,i2,1)="\" Then
		    ctex2$=Right$(ctex2$,Len(ctex2$)-i2)
		    Exit
		   EndIf
		  Next
		
		
		b3dWriteString( ctex2$ ) ;texture file
        		
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
        
          spot=Instr(".",dupe$)
	  	  If spot Then 
		   folder$=Mid$(dupe$,1,spot-1)
           Else
           folder$=dupe$
           EndIf 

		CopyFile c_tex_name$(i),tofolder$+dupe$
		DebugLog "copied "+c_tex_name$(i)+" To "+tofolder$+dupe$
		;cnn=Instr(dupe$,"Mapcrafter")
		;If cnn=1 Then 

		cnn=1 
		tscale#=TEXSCALE2#
		;Else 
		;cnn=1
		;tscale#=barkscale#
		;EndIf
		          b3dWriteInt( cnn );flags
		b3dWriteInt( 2 );blend was 2
		b3dWriteFloat( 0 );x in tex 0 (hu?)
		b3dWriteFloat( 0 );y in tex 0
		b3dWriteFloat( texscale2# );x scale 1
		b3dWriteFloat( texscale2# );y scale 1
		b3dWriteFloat( 0 );rotation 0
		
	Next
	b3dEndChunk();end of TEXS chunk
	
	
	For i=1 To c_surfs
		b3dBeginChunk( "BRUS" ) ; describe all brushes used by the mesh
		
		b3dWriteInt( 1 );number of textures per brush ; (eg 2 with lightmap)
		b3dWriteString( "_RCDN"+(i-1) );brushname
		b3dWriteFloat( 1);red
		b3dWriteFloat( 1 );green
		b3dWriteFloat( 1 );blue
		b3dWriteFloat( 1 );alpha
		b3dWriteFloat( 0 );shininess
		cns=2 
		
		b3dWriteInt( 0);blendmode
		b3dWriteInt( cns );FX
		b3dWriteInt( i-1 );used texture index 
		;b3dWriteInt( ? );additional texture index (eg lightmap), but here we only use 1 (see above)
		
		b3dEndChunk();end of BRUS chunk
	Next
	
	b3dBeginChunk( "NODE" )
	b3dWriteString( "_RCDN" )
	b3dWriteFloat( 0 );x_pos
	b3dWriteFloat( 0 );y_pos
	b3dWriteFloat( 0 );z_pos
	b3dWriteFloat( 1 );x_scale
	b3dWriteFloat( 1 );y_scale
	b3dWriteFloat( 1 );z_scale
	b3dWriteFloat( 1 );rot_w
	b3dWriteFloat( 0 );rot_x
	b3dWriteFloat( 0 );rot_y
	b3dWriteFloat( 0 );rot_z
	WriteMESH( mesh)
	b3dEndChunk();end of NODE chunk
	
	b3dEndChunk();end of BB3D chunk
	
	CloseFile file
End Function

Function WriteMESH( mesh )
	
	n_surfs=CountSurfaces( mesh )
	
	b3dBeginChunk( "MESH" )
	b3dWriteInt( -1 );no 'entity' brush -1
	
	b3dBeginChunk( "VRTS" )
	b3dWriteInt( 2 );flags - 0=no normal/color
	b3dWriteInt( 1 );number of tex_coord sets (eg: 2 with lightmap)
	b3dWriteInt( 2 );coords per set (u,v,w?) 2 with uv, 3 with uvw
	
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
b3dEndChunk();end of VRTS chunk

first_vert=0
For k=1 To n_surfs
	surf=GetSurface( mesh,k )
	n_tris=CountTriangles( surf )-1
	
	b3dBeginChunk( "TRIS" )
	b3dWriteInt( k-1 );brush for these triangles (surf -1 !!!)
	
	For j=0 To n_tris
		b3dWriteInt( first_vert+TriangleVertex( surf,j,0 ) )
		b3dWriteInt( first_vert+TriangleVertex( surf,j,1 ) )
		b3dWriteInt( first_vert+TriangleVertex( surf,j,2 ) )
	Next
	
	b3dEndChunk();end of TRIS chunk
	
	first_vert=first_vert+CountVertices( surf )
	
Next

b3dEndChunk();end of MESH chunk

End Function