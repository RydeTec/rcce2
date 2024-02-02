;A program To demonstrate Object behaviour inside a restricted space
;The box is used as the enviroment, the goal is to make various objects bounce around the box.
;
;Setup stuff.
Graphics3D 800,600,0,1
SetBuffer BackBuffer()
Global cam = CreateCamera ()
CameraViewport cam, 0, 0, GraphicsWidth (), GraphicsHeight ()
light = CreateLight ()
;create the cube
mybox=CreateCube ()
;adjust to resnoble size
ScaleMesh mybox,50,50,50
;move the camera to the middle of the box
MoveEntity Cam,0,0,-65
;Flip the mesh so I can see inside the box and things collide with the walls.
;IMPORTANT NOTE: Once you have done this the box becomes a POLYGON and NOT a CUBE.
;This is important for collisions.
FlipMesh mybox
UpdateNormals mybox
;Setup collision constants (as per tutorial)
Const CUBE_COL=1
Const SPHERE_COL=2
;create the sphere.
sphere=CreateSphere ()
ScaleMesh sphere,2,2,2
;put the sphere in the box just in front of the camera
PositionEntity sphere,0,0,30
;setup collision types.
EntityType mybox,CUBE_COL
EntityType sphere,SPHERE_COL
Collisions SPHERE_COL,CUBE_COL,2,2
;Collisions NEWCOL,CUBE_COL,2,1
Collisions sphere_col,Sphere_col,1,1
;Give the entitys some colour to make them easier to see.
EntityColor sphere,0,0,255
EntityColor mybox,128,128,128
sphere_radius#=2
EntityRadius sphere,sphere_radius#
;Main loop
While Not KeyHit(1)

;allow camera control

If KeyDown(200) Then
MoveEntity cam,0,0,1
EndIf

If KeyDown(208) Then
MoveEntity cam,0,0,-1
EndIf

If KeyDown(203) Then
TurnEntity cam,0,1.0,0
EndIf

If KeyDown(205) Then
TurnEntity cam,0,-1.0,0
EndIf

;Move the sphere 0.5 a unit each loop 
MoveEntity sphere,0.2,0.5,0.3





;test if the sphere collided with the cube.
If EntityCollided(sphere,CUBE_COL) Then
AlignToVector sphere, CollisionNX (mybox, 1), CollisionNY (mybox, 1), CollisionNZ (mybox, 1), 1
EndIf
;update and render the world and display it

UpdateWorld
RenderWorld
Flip

;keep going until escape pressed.
Wend
End