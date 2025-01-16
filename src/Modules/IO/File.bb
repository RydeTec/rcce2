Strict

Type File
    Field uri$
    Field stream.BBStream

    Method create.File(uri$)
        self\uri = uri

        return self
    End Method

    Method close()
        if (NOT self\stream = Null)
            CloseFile(self\stream)
            self\stream = Null
        end if
    End Method

    Method readLine$()
        if (self\stream = Null)
            self\stream = ReadFile(self\uri)
        end if

        return ReadLine(self\stream)
    End Method

    Method readShort()
        if (self\stream = Null)
            self\stream = ReadFile(self\uri)
        end if

        return ReadShort(self\stream)
    End Method

    Method readByte()
        if (self\stream = Null)
            self\stream = ReadFile(self\uri)
        end if

        return ReadByte(self\stream)
    End Method

    Method readFloat#()
        if (self\stream = Null)
            self\stream = ReadFile(self\uri)
        end if

        return ReadFloat(self\stream)
    End Method

    Method readInt()
        if (self\stream = Null)
            self\stream = ReadFile(self\uri)
        end if

        return ReadInt(self\stream)
    End Method

    Method remove()
        DeleteFile(self\uri)
    End Method

    Method writeLine(string$)
        if (self\stream = Null)
            self\stream = WriteFile(self\uri)
        end if

        WriteLine(self\stream, string)
    End Method

    Method writeShort(value%)
        if (self\stream = Null)
            self\stream = WriteFile(self\uri)
        end if

        WriteShort(self\stream, value)
    End Method

    Method writeByte(value%)
        if (self\stream = Null)
            self\stream = WriteFile(self\uri)
        end if

        WriteByte(self\stream, value)
    End Method

    Method writeFloat(value#)
        if (self\stream = Null)
            self\stream = WriteFile(self\uri)
        end if

        WriteFloat(self\stream, value)
    End Method

    Method writeInt(value%)
        if (self\stream = Null)
            self\stream = WriteFile(self\uri)
        end if

        WriteInt(self\stream, value)
    End Method

    Method seekFile(position%)
        if (self\stream = Null)
            self\stream = ReadFile(self\uri)
        end if

        SeekFile(self\stream, position)
    End Method

    Method seekLine(position%)
        if (self\stream = Null)
            self\stream = ReadFile(self\uri)
        end if

        Local currentLine% = 1
        While currentLine < position
            ReadLine(self\stream)
            currentLine = currentLine + 1
        Wend
    End Method

    Method isEnd()
        if (self\stream = Null)
            return true
        end if
        
        return EOF(self\stream)
    End Method
End Type