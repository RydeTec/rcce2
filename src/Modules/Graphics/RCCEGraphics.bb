Strict

Include "Modules\Traits\Dim\ScaleTrait.bb"

Type RCCEGraphics
    Field scale.ScaleTrait
    Field mode%

    Field defaultBuffer.BBBuffer
    Field currentBuffer.BBBuffer

    Method create.RCCEGraphics(width, height, depth, mode)
        self\scale = new ScaleTrait(width, height, depth)
        self\mode = mode

        return self
    End Method

    Method init()
        Graphics3D(self\scale\x, self\scale\y, self\scale\z, self\mode)
        RCCEGraphics::resetBuffer(self)
    End Method

    Method resetBuffer()
        if (self\defaultBuffer = Null)
            self\defaultBuffer = BackBuffer()
        end if
        RCCEGraphics::setBuffer(self, self\defaultBuffer)
    End Method

    Method setBuffer(buffer.BBBuffer)
        SetBuffer(buffer)
        self\currentBuffer = buffer
    End Method

    Method push()
        flip()
    End Method
End Type