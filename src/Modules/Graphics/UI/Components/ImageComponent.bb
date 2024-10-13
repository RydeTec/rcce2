Strict

Include "Modules\Graphics\UI\Components\Component.bb"
Include "Modules\IO\Image.bb"

Type ImageComponent.Component
    Field img.Image

    Method create.ImageComponent(img.Image)
        self\img = img

        return self
    End Method

    Method resize(x%, y%)
        Component::setScale(self, x,y)

        if (NOT self\img\loaded) 
            Image::load(self\img)
        EndIf

        ResizeImage(self\img\img, self\scale\x, self\scale\y)
    End Method

    Method place(x%, y%)
        Component::setPosition(self, x, y)
    End Method

    Method draw()
        if (NOT self\img\loaded) 
            Image::load(self\img)
        EndIf
        
        DrawImage(self\img\img, self\pos\x, self\pos\y)
    End Method
End Type