Strict

Include "Modules\IO\File.bb"

Global GLOBAL_DEFAULT_IMAGE$
Type Image.File
    Field img.BBImage
    Field defaultUri$
    Field loaded

    Method create.Image(uri$, defaultUri="")
        self = Recast.Image(File::create(self, uri))
        self\defaultUri = defaultUri
        self\loaded = false
        return self
    End Method

    Method load.BBImage()
        self\img = LoadImage(self\uri)
        self\loaded = true

        return self\img
    End Method

    Method unload()
        FreeImage(self\img)
        self\loaded = false
    End Method

    Method setDefault(uri$)
        if (self = Null)
            GLOBAL_DEFAULT_IMAGE = uri
        Else
            self\defaultUri = uri
        End If
    End Method
End Type