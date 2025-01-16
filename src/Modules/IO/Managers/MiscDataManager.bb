Strict
Include "Modules\IO\File.bb"

Type ControlsData
    Field Key_Forward
    Field Key_Back
    Field Key_TurnRight
    Field Key_TurnLeft
    Field Key_FlyUp
    Field Key_FlyDown
    Field Key_Run
    Field Key_ChangeViewMode
    Field Key_CameraRight
    Field Key_CameraLeft
    Field Key_CameraIn
    Field Key_CameraOut
    Field Key_Jump
    Field InvertAxis1
    Field InvertAxis3
    Field Key_Attack
    Field Key_AlwaysRun
    Field Key_CycleTarget
    Field Key_MoveTo
    Field Key_TalkTo
    Field Key_Select
End Type

Type LastUsernameData
    Field username$
    Field passwordEncrypted$
End Type

Type VersionData
    Field version%
End Type

Type MiscDataManager
    Field versionData.VersionData
    Field lastUsernameData.LastUsernameData
    Field controlsData.ControlsData

    Method create.MiscDataManager()
        self\versionData = new VersionData()
        self\lastUsernameData = new LastUsernameData()
        self\controlsData = new ControlsData()

        return self
    End Method

    Method Load(obfuscated=False)
        if (obfuscated)
            MiscDataManager::ReadObfuscated(self)
        else
            MiscDataManager::ReadFast(self)
        end if
    End Method

    Method Save(obfuscated=False)
        if (obfuscated)
            MiscDataManager::WriteObfuscated(self)
        else
            MiscDataManager::WriteFast(self)
        end if
    End Method

    Method ReadObfuscated()
        Local versionFile.File = new File("Data\Version.dat")
        self\versionData\version = Int(File::readLine(versionFile))
        File::close(versionFile)

        Local controlsFile.File = new File("Data\Controls.dat")
        self\controlsData\Key_Forward = File::readInt(controlsFile)
        self\controlsData\Key_Back = File::readInt(controlsFile)
        self\controlsData\Key_TurnRight = File::readInt(controlsFile)
        self\controlsData\Key_TurnLeft = File::readInt(controlsFile)
        self\controlsData\Key_FlyUp = File::readInt(controlsFile)
        self\controlsData\Key_FlyDown = File::ReadInt(controlsFile)
        self\controlsData\Key_Run = File::readInt(controlsFile)
        self\controlsData\Key_ChangeViewMode = File::readInt(controlsFile)
        self\controlsData\Key_CameraRight = File::readInt(controlsFile)
        self\controlsData\Key_CameraLeft = File::readInt(controlsFile)
        self\controlsData\Key_CameraIn = File::readInt(controlsFile)
        self\controlsData\Key_CameraOut = File::readInt(controlsFile)
        self\controlsData\Key_Jump = File::readInt(controlsFile)
        self\controlsData\InvertAxis1 = File::readByte(controlsFile) - 1
        self\controlsData\InvertAxis3 = File::readByte(controlsFile) - 1
        self\controlsData\Key_Attack = File::readInt(controlsFile)
        self\controlsData\Key_AlwaysRun = File::readInt(controlsFile)
        self\controlsData\Key_CycleTarget = File::readInt(controlsFile)
        self\controlsData\Key_MoveTo = File::readInt(controlsFile)
        self\controlsData\Key_TalkTo = File::readInt(controlsFile)
        self\controlsData\Key_Select = File::readInt(controlsFile)
        File::close(controlsFile)

        Local lastUsernameFile.File = new File("Data\Last Username.dat")
        self\lastUsernameData\username = File::readLine(lastUsernameFile)
        self\lastUsernameData\passwordEncrypted = File::readLine(lastUsernameFile)
        File::close(lastUsernameFile)
    End Method

    Method WriteObfuscated()
        Local versionFile.File = new File("Data\Version.dat")
        File::writeLine(versionFile, self\versionData\version)
        File::close(versionFile)

        Local controlsFile.File = new File("Data\Controls.dat")
        File::writeInt(controlsFile, self\controlsData\Key_Forward)
        File::writeInt(controlsFile, self\controlsData\Key_Back)
        File::writeInt(controlsFile, self\controlsData\Key_TurnRight)
        File::writeInt(controlsFile, self\controlsData\Key_TurnLeft)
        File::writeInt(controlsFile, self\controlsData\Key_FlyUp)
        File::writeInt(controlsFile, self\controlsData\Key_FlyDown)
        File::writeInt(controlsFile, self\controlsData\Key_Run)
        File::writeInt(controlsFile, self\controlsData\Key_ChangeViewMode)
        File::writeInt(controlsFile, self\controlsData\Key_CameraRight)
        File::writeInt(controlsFile, self\controlsData\Key_CameraLeft)
        File::writeInt(controlsFile, self\controlsData\Key_CameraIn)
        File::writeInt(controlsFile, self\controlsData\Key_CameraOut)
        File::writeInt(controlsFile, self\controlsData\Key_Jump)
        File::writeByte(controlsFile, self\controlsData\InvertAxis1 + 1)
        File::writeByte(controlsFile, self\controlsData\InvertAxis3 + 1)
        File::writeInt(controlsFile, self\controlsData\Key_Attack)
        File::writeInt(controlsFile, self\controlsData\Key_AlwaysRun)
        File::writeInt(controlsFile, self\controlsData\Key_CycleTarget)
        File::writeInt(controlsFile, self\controlsData\Key_MoveTo)
        File::writeInt(controlsFile, self\controlsData\Key_TalkTo)
        File::writeInt(controlsFile, self\controlsData\Key_Select)
        File::close(controlsFile)

        Local lastUsernameFile.File = new File("Data\Last Username.dat")
        File::writeLine(lastUsernameFile, self\lastUsernameData\username)
        File::writeLine(lastUsernameFile, self\lastUsernameData\passwordEncrypted)
        File::close(lastUsernameFile)
    End Method

    Method ReadFast()
        Local versionFile.File = new File("Data\Version.dat")
        self\versionData\version = Int(File::readLine(versionFile))
        File::close(versionFile)

        Local controlsFile.File = new File("Data\Controls.dat")
        self\controlsData\Key_Forward = Int(File::readLine(controlsFile))
        self\controlsData\Key_Back = Int(File::readLine(controlsFile))
        self\controlsData\Key_TurnRight = Int(File::readLine(controlsFile))
        self\controlsData\Key_TurnLeft = Int(File::readLine(controlsFile))
        self\controlsData\Key_FlyUp = Int(File::readLine(controlsFile))
        self\controlsData\Key_FlyDown = Int(File::readLine(controlsFile))
        self\controlsData\Key_Run = Int(File::readLine(controlsFile))
        self\controlsData\Key_ChangeViewMode = Int(File::readLine(controlsFile))
        self\controlsData\Key_CameraRight = Int(File::readLine(controlsFile))
        self\controlsData\Key_CameraLeft = Int(File::readLine(controlsFile))
        self\controlsData\Key_CameraIn = Int(File::readLine(controlsFile))
        self\controlsData\Key_CameraOut = Int(File::readLine(controlsFile))
        self\controlsData\Key_Jump = Int(File::readLine(controlsFile))
        self\controlsData\InvertAxis1 = Int(File::readLine(controlsFile)) - 1
        self\controlsData\InvertAxis3 = Int(File::readLine(controlsFile)) - 1
        self\controlsData\Key_Attack = Int(File::readLine(controlsFile))
        self\controlsData\Key_AlwaysRun = Int(File::readLine(controlsFile))
        self\controlsData\Key_CycleTarget = Int(File::readLine(controlsFile))
        self\controlsData\Key_MoveTo = Int(File::readLine(controlsFile))
        self\controlsData\Key_TalkTo = Int(File::readLine(controlsFile))
        self\controlsData\Key_Select = Int(File::readLine(controlsFile))
        File::close(controlsFile)

        Local lastUsernameFile.File = new File("Data\Last Username.dat")
        self\lastUsernameData\username = File::readLine(lastUsernameFile)
        self\lastUsernameData\passwordEncrypted = File::readLine(lastUsernameFile)
        File::close(lastUsernameFile)
    End Method

    Method WriteFast()
        Local versionFile.File = new File("Data\Version.dat")
        File::writeLine(versionFile, self\versionData\version)
        File::close(versionFile)

        Local controlsFile.File = new File("Data\Controls.dat")
        File::writeLine(controlsFile, self\controlsData\Key_Forward)
        File::writeLine(controlsFile, self\controlsData\Key_Back)
        File::writeLine(controlsFile, self\controlsData\Key_TurnRight)
        File::writeLine(controlsFile, self\controlsData\Key_TurnLeft)
        File::writeLine(controlsFile, self\controlsData\Key_FlyUp)
        File::writeLine(controlsFile, self\controlsData\Key_FlyDown)
        File::writeLine(controlsFile, self\controlsData\Key_Run)
        File::writeLine(controlsFile, self\controlsData\Key_ChangeViewMode)
        File::writeLine(controlsFile, self\controlsData\Key_CameraRight)
        File::writeLine(controlsFile, self\controlsData\Key_CameraLeft)
        File::writeLine(controlsFile, self\controlsData\Key_CameraIn)
        File::writeLine(controlsFile, self\controlsData\Key_CameraOut)
        File::writeLine(controlsFile, self\controlsData\Key_Jump)
        File::writeLine(controlsFile, self\controlsData\InvertAxis1 + 1)
        File::writeLine(controlsFile, self\controlsData\InvertAxis3 + 1)
        File::writeLine(controlsFile, self\controlsData\Key_Attack)
        File::writeLine(controlsFile, self\controlsData\Key_AlwaysRun)
        File::writeLine(controlsFile, self\controlsData\Key_CycleTarget)
        File::writeLine(controlsFile, self\controlsData\Key_MoveTo)
        File::writeLine(controlsFile, self\controlsData\Key_TalkTo)
        File::writeLine(controlsFile, self\controlsData\Key_Select)
        File::close(controlsFile)

        Local lastUsernameFile.File = new File("Data\Last Username.dat")
        File::writeLine(lastUsernameFile, self\lastUsernameData\username)
        File::writeLine(lastUsernameFile, self\lastUsernameData\passwordEncrypted)
        File::close(lastUsernameFile)
    End Method

End Type