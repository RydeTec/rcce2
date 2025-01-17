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

// Combat
Type CombatData
    Field CombatDelay%
    Field DamageInfoStyle%
End Type

// Hosts
Type HostsData
    Field ServerHost$
    Field UpdateHost$
    Field NewAccounts%
End Type

// Fixed Attributes
Type FixedAttributesData
    Field HealthStat%
    Field EnergyStat%
    Field BreathStat%
    Field StrengthStat%
    Field SpeedStat%
End Type

// Gubbins
Type GubbinsData
    Field GubbinNames$[6]
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
    Field combatData.CombatData
    Field hostsData.HostsData
    Field fixedAttributesData.FixedAttributesData
    Field gubbinsData.GubbinsData

    Method create.GameDataManager()
        self\animationsData = new AnimationsData()
        self\combatData = new CombatData()
        self\hostsData = new HostsData()
        self\fixedAttributesData = new FixedAttributesData()
        self\gubbinsData = new GubbinsData()

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

        // Combat
        Local combatFile.File = new File("Data\Game Data\Combat.dat")

        self\combatData\CombatDelay = File::readShort(combatFile)
        self\combatData\DamageInfoStyle = File::readByte(combatFile)

        File::close(combatFile)
        Delete(combatFile)

        // Hosts
        Local hostsFile.File = new File("Data\Game Data\Hosts.dat")

        self\hostsData\ServerHost = File::readLine(hostsFile)
        self\hostsData\UpdateHost = File::readLine(hostsFile)
        self\hostsData\NewAccounts = Int(File::readLine(hostsFile))

        File::close(hostsFile)
        Delete(hostsFile)

        // Fixed Attributes
        Local fixedAttributesFile.File = new File("Data\Game Data\Fixed Attributes.dat")

        self\fixedAttributesData\HealthStat = File::readShort(fixedAttributesFile)
        self\fixedAttributesData\EnergyStat = File::readShort(fixedAttributesFile)
        self\fixedAttributesData\BreathStat = File::readShort(fixedAttributesFile)
        self\fixedAttributesData\StrengthStat = File::readShort(fixedAttributesFile)
        self\fixedAttributesData\SpeedStat = File::readShort(fixedAttributesFile)

        File::close(fixedAttributesFile)
        Delete(fixedAttributesFile)

        // Gubbins
        Local gubbinsFile.File = new File("Data\Game Data\Gubbins.dat")

        for i = 0 to 5
            self\gubbinsData\GubbinNames$[i] = File::readString(gubbinsFile)
        next

        File::close(gubbinsFile)
        Delete(gubbinsFile)
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

        // Combat
        Local combatFile.File = new File("Data\Game Data\Combat.dat")

        File::writeShort(combatFile, self\combatData\CombatDelay)
        File::writeByte(combatFile, self\combatData\DamageInfoStyle)

        File::close(combatFile)
        Delete(combatFile)

        // Hosts
        Local hostsFile.File = new File("Data\Game Data\Hosts.dat")

        File::writeLine(hostsFile, self\hostsData\ServerHost)
        File::writeLine(hostsFile, self\hostsData\UpdateHost)
        File::writeLine(hostsFile, self\hostsData\NewAccounts)

        File::close(hostsFile)
        Delete(hostsFile)

        // Fixed Attributes
        Local fixedAttributesFile.File = new File("Data\Game Data\Fixed Attributes.dat")

        File::writeShort(fixedAttributesFile, self\fixedAttributesData\HealthStat)
        File::writeShort(fixedAttributesFile, self\fixedAttributesData\EnergyStat)
        File::writeShort(fixedAttributesFile, self\fixedAttributesData\BreathStat)
        File::writeShort(fixedAttributesFile, self\fixedAttributesData\StrengthStat)
        File::writeShort(fixedAttributesFile, self\fixedAttributesData\SpeedStat)

        File::close(fixedAttributesFile)
        Delete(fixedAttributesFile)

        // Gubbins
        Local gubbinsFile.File = new File("Data\Game Data\Gubbins.dat")

        for i = 0 to 5
            File::writeString(gubbinsFile, self\gubbinsData\GubbinNames$[i])
        next

        File::close(gubbinsFile)
        Delete(gubbinsFile)
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

        // Combat
        Local combatFile.File = new File("Data\Game Data\Combat.dat")

        self\combatData\CombatDelay = Int(File::readLine(combatFile))
        self\combatData\DamageInfoStyle = Int(File::readLine(combatFile))

        File::close(combatFile)
        Delete(combatFile)

        // Hosts
        Local hostsFile.File = new File("Data\Game Data\Hosts.dat")

        self\hostsData\ServerHost = File::readLine(hostsFile)
        self\hostsData\UpdateHost = File::readLine(hostsFile)
        self\hostsData\NewAccounts = Int(File::readLine(hostsFile))

        File::close(hostsFile)
        Delete(hostsFile)

        // Fixed Attributes
        Local fixedAttributesFile.File = new File("Data\Game Data\Fixed Attributes.dat")

        self\fixedAttributesData\HealthStat = Int(File::readLine(fixedAttributesFile))
        self\fixedAttributesData\EnergyStat = Int(File::readLine(fixedAttributesFile))
        self\fixedAttributesData\BreathStat = Int(File::readLine(fixedAttributesFile))
        self\fixedAttributesData\StrengthStat = Int(File::readLine(fixedAttributesFile))
        self\fixedAttributesData\SpeedStat = Int(File::readLine(fixedAttributesFile))

        File::close(fixedAttributesFile)
        Delete(fixedAttributesFile)

        // Gubbins
        Local gubbinsFile.File = new File("Data\Game Data\Gubbins.dat")

        for i = 0 to 5
            self\gubbinsData\GubbinNames$[i] = File::readLine(gubbinsFile)
        next

        File::close(gubbinsFile)
        Delete(gubbinsFile)
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

        // Combat
        Local combatFile.File = new File("Data\Game Data\Combat.dat")

        File::writeLine(combatFile, self\combatData\CombatDelay)
        File::writeLine(combatFile, self\combatData\DamageInfoStyle)

        File::close(combatFile)
        Delete(combatFile)

        // Hosts
        Local hostsFile.File = new File("Data\Game Data\Hosts.dat")

        File::writeLine(hostsFile, self\hostsData\ServerHost)
        File::writeLine(hostsFile, self\hostsData\UpdateHost)
        File::writeLine(hostsFile, self\hostsData\NewAccounts)

        File::close(hostsFile)
        Delete(hostsFile)

        // Fixed Attributes
        Local fixedAttributesFile.File = new File("Data\Game Data\Fixed Attributes.dat")

        File::writeLine(fixedAttributesFile, self\fixedAttributesData\HealthStat)
        File::writeLine(fixedAttributesFile, self\fixedAttributesData\EnergyStat)
        File::writeLine(fixedAttributesFile, self\fixedAttributesData\BreathStat)
        File::writeLine(fixedAttributesFile, self\fixedAttributesData\StrengthStat)
        File::writeLine(fixedAttributesFile, self\fixedAttributesData\SpeedStat)

        File::close(fixedAttributesFile)
        Delete(fixedAttributesFile)

        // Gubbins
        Local gubbinsFile.File = new File("Data\Game Data\Gubbins.dat")

        for i = 0 to 5
            File::writeLine(gubbinsFile, self\gubbinsData\GubbinNames$[i])
        next

        File::close(gubbinsFile)
        Delete(gubbinsFile)
    End Method

End Type