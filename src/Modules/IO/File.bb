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
        If (NOT File::exists(self))
            Return ""
        End If

        if (self\stream = Null)
            self\stream = ReadFile(self\uri)
        end if

        return ReadLine(self\stream)
    End Method

    Method readShort()
        If (NOT File::exists(self))
            Return 0
        End If

        if (self\stream = Null)
            self\stream = ReadFile(self\uri)
        end if

        return ReadShort(self\stream)
    End Method

    Method readByte()
        If (NOT File::exists(self))
            Return 0
        End If

        if (self\stream = Null)
            self\stream = ReadFile(self\uri)
        end if

        return ReadByte(self\stream)
    End Method

    Method readFloat#()
        If (NOT File::exists(self))
            Return 0.0
        End If

        if (self\stream = Null)
            self\stream = ReadFile(self\uri)
        end if

        return ReadFloat(self\stream)
    End Method

    Method readInt()
        If (NOT File::exists(self))
            Return 0
        End If

        if (self\stream = Null)
            self\stream = ReadFile(self\uri)
        end if

        return ReadInt(self\stream)
    End Method

    Method readString$()
        If (NOT File::exists(self))
            Return ""
        End If

        if (self\stream = Null)
            self\stream = ReadFile(self\uri)
        end if

        return ReadString(self\stream)
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

    Method writeString(string$)
        if (self\stream = Null)
            self\stream = WriteFile(self\uri)
        end if

        WriteString(self\stream, string)
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

    Method remove()
        DeleteFile(self\uri)
    End Method

    Method exists()
        return NOT FileType(self\uri) = 0
    End Method

    Method isEnd()
        if (self\stream = Null)
            self\stream = ReadFile(self\uri)
        end if
        
        return EOF(self\stream)
    End Method
End Type