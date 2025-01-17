Strict
Include "Modules\IO\File.bb"

// Will manage the reading and writing of Game Data/*.dat files

// Animations
Type Animation
	Field Name$
	Field ID
	Field AnimName$[149], AnimStart[149], AnimEnd[149], AnimSpeed#[149]
End Type

Type AnimationsData
    Field Animations.BBList

    Method create.AnimationsData()
        self\Animations = CreateList()

        return self
    End Method
End Type

Type CombatData

End Type

Type FixedAttributesData

End Type

Type GubbinsData

End Type

Type HostsData

End Type

Type InterfaceData

End Type

Type MeshesData

End Type

Type MiscData

End Type

Type MoneyData

End Type

Type MusicData 

End Type

Type OtherData

End Type

Type PatchVersionData

End Type

Type RCTEData

End Type

Type SoundsData

End Type

Type SunsData 

End Type

Type TexturesData

End Type

Type WebData

End Type

Type xMeshesData

End Type

Type GameDataManager

    Field animationsData.AnimationsData

    Method create.GameDataManager()
        self\animationsData = new AnimationsData()

        return self
    End Method

    Method Load(obfuscated=True)
        if (obfuscated)
            GameDataManager::ReadObfuscated(self)
        else
            GameDataManager::ReadFast(self)
        end if
    End Method

    Method Save(obfuscated=True)
        if (obfuscated)
            GameDataManager::WriteObfuscated(self)
        else
            GameDataManager::WriteFast(self)
        end if
    End Method

    Method ReadObfuscated()
        // Animations
        Local animationsFile.File = new File("Data\Game Data\Animations.dat")

        Local Sets% = 0
        while (NOT File::isEnd(animationsFile))
            Local animSet.Animation = new Animation()

            animSet\ID = File::readShort(animationsFile)
            animSet\Name = File::readString(animationsFile)
        
            for i = 0 to 149
                animSet\AnimName$[i] = File::readString(animationsFile)
                animSet\AnimStart[i] = File::readShort(animationsFile)
                animSet\AnimEnd[i] = File::readShort(animationsFile)
                animSet\AnimSpeed#[i] = File::readFloat(animationsFile)
            next

            ListAdd(self\animationsData\Animations, animSet)

            Sets = Sets + 1
        wend

        File::close(animationsFile)
        Delete(animationsFile)
    End Method

    Method WriteObfuscated()
        // Animations
        Local animationsFile.File = new File("Data\Game Data\Animations.dat")
        Local maxAnimSets% = ListSize(self\animationsData\Animations) - 1

        For i = 0 to maxAnimSets
            Local animSet.Animation = ListAt(self\animationsData\Animations, i)

            File::writeShort(animationsFile, animSet\ID)
            File::writeString(animationsFile, animSet\Name)

            for j = 0 to 149
                File::writeString(animationsFile, animSet\AnimName$[j])
                File::writeShort(animationsFile, animSet\AnimStart[j])
                File::writeShort(animationsFile, animSet\AnimEnd[j])
                File::writeFloat(animationsFile, animSet\AnimSpeed#[j])
            next
        Next

        File::close(animationsFile)
        Delete(animationsFile)


    End Method

    Method ReadFast()
        // Animations
        Local animationsFile.File = new File("Data\Game Data\Animations.dat")

        Local Sets% = 0
        while (NOT File::isEnd(animationsFile))
            Local animSet.Animation = new Animation()

            animSet\ID = Int(File::readLine(animationsFile))
            animSet\Name = File::readLine(animationsFile)
        
            for i = 0 to 149
                animSet\AnimName$[i] = File::readLine(animationsFile)
                animSet\AnimStart[i] = Int(File::readLine(animationsFile))
                animSet\AnimEnd[i] = Int(File::readLine(animationsFile))
                animSet\AnimSpeed#[i] = Float(File::readLine(animationsFile))
            next

            ListAdd(self\animationsData\Animations, animSet)

            Sets = Sets + 1
        wend

        File::close(animationsFile)
        Delete(animationsFile)
    End Method

    Method WriteFast()
        // Animations
        Local animationsFile.File = new File("Data\Game Data\Animations.dat")
        Local maxAnimSets% = ListSize(self\animationsData\Animations) - 1

        For i = 0 to maxAnimSets
            Local animSet.Animation = ListAt(self\animationsData\Animations, i)

            File::writeLine(animationsFile, animSet\ID)
            File::writeLine(animationsFile, animSet\Name)

            for j = 0 to 149
                File::writeLine(animationsFile, animSet\AnimName$[j])
                File::writeLine(animationsFile, animSet\AnimStart[j])
                File::writeLine(animationsFile, animSet\AnimEnd[j])
                File::writeLine(animationsFile, animSet\AnimSpeed#[j])
            next
        Next

        File::close(animationsFile)
        Delete(animationsFile)
    End Method

End Type