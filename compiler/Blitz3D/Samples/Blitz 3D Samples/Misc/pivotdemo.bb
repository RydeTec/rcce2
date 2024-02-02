;Pivot Illustration

Graphics3D 800,600
;create camera and lights
cam=CreateCamera()
light=CreateLight()
;Create some cubes
MYCUBEA=CreateCube()
PositionEntity MYCUBEA,5,5,30
MYCUBEB=CreateCube()
PositionEntity MYCUBEB,0,0,30
MYCUBEC=CreateCube()
PositionEntity MYCUBEC,-5,0,30
;This creates the Pivot
MYPIV=CreatePivot()
PositionEntity MYPIV,0,0,30
;Make the cubes children of the pivot
EntityParent MYCUBEA,MYPIV
EntityParent MYCUBEB,MYPIV
EntityParent MYCUBEC,MYPIV
;Main loop
For I#=1 To 360
Delay 20 
;Now by Moving and Rotating the Pivot we move and rotate all the cubes.
RotateEntity MYPIV,0,i#,0
TranslateEntity mypiv,0,(I*0.0002),0
UpdateWorld
RenderWorld
Text 0,10,"ROTATE=" +I#
Flip
Next
While Not KeyHit(1)
Wend
End