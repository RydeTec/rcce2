Strict

Type File
    Field uri$
    Field stream.BBStream

    Method create.File(uri$)
        self\uri = uri

        return self
    End Method

    Method close()
        ; `If (NOT self\stream = Null)` accidentally evaluates to the
        ; intended `<> Null` semantics (Null coerces to 0), but reads as
        ; the opposite -- prefer the unambiguous spelling.
        If self\stream <> Null
            CloseFile(self\stream)
            self\stream = Null
        End If
    End Method

    Method readLine$()
        if (self\stream = Null)
            self\stream = ReadFile(self\uri)
        end if

        return ReadLine(self\stream)
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

    Method isEnd()
        if (self\stream = Null)
            return true
        end if
        
        return EOF(self\stream)
    End Method
End Type