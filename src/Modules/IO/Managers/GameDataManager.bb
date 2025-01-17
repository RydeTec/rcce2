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

// Interface
Type InterfaceComponentData
    Field X#
    Field Y#
    Field Width#
    Field Height#
    Field Alpha#
    Field R%
    Field G%
    Field B%
    Field Texture%
End Type

Type InterfaceData
    Field InterfaceComponents.BBList

    Method create.InterfaceData()
        self\InterfaceComponents = CreateList()

        return self
    End Method
End Type

// Misc
Type MiscData
    Field GameName$
    Field GameUpdate$
    Field GameMusicUpdate%
    Field GameVersion%
End Type

// Money
Type MoneyData
    Field Money1$
    Field Money2$
    Field Money2x%
    Field Money3$
    Field Money3x%
    Field Money4$
    Field Money4x%
End Type

Type OtherData

End Type

Type PatchVersionData

End Type

Type RCTEData

End Type

Type SunsData 

End Type

Type WebData

End Type

Type GameDataManager

    Field animationsData.AnimationsData
    Field combatData.CombatData
    Field hostsData.HostsData
    Field fixedAttributesData.FixedAttributesData
    Field gubbinsData.GubbinsData
    Field interfaceData.InterfaceData
    Field miscData.MiscData
    Field moneyData.MoneyData

    Method create.GameDataManager()
        self\animationsData = new AnimationsData()
        self\combatData = new CombatData()
        self\hostsData = new HostsData()
        self\fixedAttributesData = new FixedAttributesData()
        self\gubbinsData = new GubbinsData()
        self\interfaceData = new InterfaceData()
        self\miscData = new MiscData()
        self\moneyData = new MoneyData()

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

        // Interface
        Local interfaceFile.File = new File("Data\Game Data\Interface.dat")

        local currentInterfaceComponent% = 0
        while (NOT File::isEnd(interfaceFile))
            Local interfaceComponent.InterfaceComponentData = new InterfaceComponentData()

            interfaceComponent\X = File::readFloat(interfaceFile)
            interfaceComponent\Y = File::readFloat(interfaceFile)
            interfaceComponent\Width = File::readFloat(interfaceFile)
            interfaceComponent\Height = File::readFloat(interfaceFile)
            interfaceComponent\Alpha = File::readFloat(interfaceFile)
            interfaceComponent\R = File::readByte(interfaceFile)
            interfaceComponent\G = File::readByte(interfaceFile)
            interfaceComponent\B = File::readByte(interfaceFile)

            if (currentInterfaceComponent = 0)
                interfaceComponent\Texture = File::readShort(interfaceFile)
            end if

            ListAdd(self\interfaceData\InterfaceComponents, interfaceComponent)

            currentInterfaceComponent = currentInterfaceComponent + 1
        wend

        File::close(interfaceFile)
        Delete(interfaceFile)

        // Misc
        Local miscFile.File = new File("Data\Game Data\Misc.dat")

        self\miscData\GameName = File::readLine(miscFile)
        self\miscData\GameUpdate = File::readLine(miscFile)
        self\miscData\GameMusicUpdate = Int(File::readLine(miscFile))
        self\miscData\GameVersion = Int(File::readLine(miscFile))

        File::close(miscFile)
        Delete(miscFile)

        // Money
        Local moneyFile.File = new File("Data\Game Data\Money.dat")

        self\moneyData\Money1 = File::readString(moneyFile)
        self\moneyData\Money2 = File::readString(moneyFile)
        self\moneyData\Money2x = Int(File::readShort(moneyFile))
        self\moneyData\Money3 = File::readString(moneyFile)
        self\moneyData\Money3x = Int(File::readShort(moneyFile))
        self\moneyData\Money4 = File::readString(moneyFile)
        self\moneyData\Money4x = Int(File::readShort(moneyFile))

        File::close(moneyFile)
        Delete(moneyFile)
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

        // Interface
        Local interfaceFile.File = new File("Data\Game Data\Interface.dat")

        local maxInterfaceComponents% = ListSize(self\interfaceData\InterfaceComponents) - 1

        for i = 0 to maxInterfaceComponents
            Local interfaceComponent.InterfaceComponentData = ListAt(self\interfaceData\InterfaceComponents, i)

            File::writeFloat(interfaceFile, interfaceComponent\X)
            File::writeFloat(interfaceFile, interfaceComponent\Y)
            File::writeFloat(interfaceFile, interfaceComponent\Width)
            File::writeFloat(interfaceFile, interfaceComponent\Height)
            File::writeFloat(interfaceFile, interfaceComponent\Alpha)
            File::writeByte(interfaceFile, interfaceComponent\R)
            File::writeByte(interfaceFile, interfaceComponent\G)
            File::writeByte(interfaceFile, interfaceComponent\B)

            if (i = 0)
                File::writeShort(interfaceFile, interfaceComponent\Texture)
            end if
        next

        File::close(interfaceFile)
        Delete(interfaceFile)

        // Misc
        Local miscFile.File = new File("Data\Game Data\Misc.dat")

        File::writeLine(miscFile, self\miscData\GameName)
        File::writeLine(miscFile, self\miscData\GameUpdate)
        File::writeLine(miscFile, self\miscData\GameMusicUpdate)
        File::writeLine(miscFile, self\miscData\GameVersion)

        File::close(miscFile)
        Delete(miscFile)

        // Money
        Local moneyFile.File = new File("Data\Game Data\Money.dat")

        File::writeString(moneyFile, self\moneyData\Money1)
        File::writeString(moneyFile, self\moneyData\Money2)
        File::writeShort(moneyFile, self\moneyData\Money2x)
        File::writeString(moneyFile, self\moneyData\Money3)
        File::writeShort(moneyFile, self\moneyData\Money3x)
        File::writeString(moneyFile, self\moneyData\Money4)
        File::writeShort(moneyFile, self\moneyData\Money4x)

        File::close(moneyFile)
        Delete(moneyFile)
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

        // Interface
        Local interfaceFile.File = new File("Data\Game Data\Interface.dat")

        local currentInterfaceComponent% = 0
        while (NOT File::isEnd(interfaceFile))
            Local interfaceComponent.InterfaceComponentData = new InterfaceComponentData()

            interfaceComponent\X = Float(File::readLine(interfaceFile))
            interfaceComponent\Y = Float(File::readLine(interfaceFile))
            interfaceComponent\Width = Float(File::readLine(interfaceFile))
            interfaceComponent\Height = Float(File::readLine(interfaceFile))
            interfaceComponent\Alpha = Float(File::readLine(interfaceFile))
            interfaceComponent\R = Int(File::readLine(interfaceFile))
            interfaceComponent\G = Int(File::readLine(interfaceFile))
            interfaceComponent\B = Int(File::readLine(interfaceFile))

            if (currentInterfaceComponent = 0)
                interfaceComponent\Texture = Int(File::readLine(interfaceFile))
            end if

            ListAdd(self\interfaceData\InterfaceComponents, interfaceComponent)

            currentInterfaceComponent = currentInterfaceComponent + 1
        wend

        File::close(interfaceFile)
        Delete(interfaceFile)

        // Misc
        Local miscFile.File = new File("Data\Game Data\Misc.dat")

        self\miscData\GameName = File::readLine(miscFile)
        self\miscData\GameUpdate = File::readLine(miscFile)
        self\miscData\GameMusicUpdate = Int(File::readLine(miscFile))
        self\miscData\GameVersion = Int(File::readLine(miscFile))

        File::close(miscFile)
        Delete(miscFile)

        // Money
        Local moneyFile.File = new File("Data\Game Data\Money.dat")

        self\moneyData\Money1 = File::readLine(moneyFile)
        self\moneyData\Money2 = File::readLine(moneyFile)
        self\moneyData\Money2x = Int(File::readLine(moneyFile))
        self\moneyData\Money3 = File::readLine(moneyFile)
        self\moneyData\Money3x = Int(File::readLine(moneyFile))
        self\moneyData\Money4 = File::readLine(moneyFile)
        self\moneyData\Money4x = Int(File::readLine(moneyFile))

        File::close(moneyFile)
        Delete(moneyFile)
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

        // Interface
        Local interfaceFile.File = new File("Data\Game Data\Interface.dat")

        local maxInterfaceComponents% = ListSize(self\interfaceData\InterfaceComponents) - 1

        for i = 0 to maxInterfaceComponents
            Local interfaceComponent.InterfaceComponentData = ListAt(self\interfaceData\InterfaceComponents, i)

            File::writeLine(interfaceFile, interfaceComponent\X)
            File::writeLine(interfaceFile, interfaceComponent\Y)
            File::writeLine(interfaceFile, interfaceComponent\Width)
            File::writeLine(interfaceFile, interfaceComponent\Height)
            File::writeLine(interfaceFile, interfaceComponent\Alpha)
            File::writeLine(interfaceFile, interfaceComponent\R)
            File::writeLine(interfaceFile, interfaceComponent\G)
            File::writeLine(interfaceFile, interfaceComponent\B)

            if (i = 0)
                File::writeLine(interfaceFile, interfaceComponent\Texture)
            end if
        next

        File::close(interfaceFile)
        Delete(interfaceFile)

        // Misc
        Local miscFile.File = new File("Data\Game Data\Misc.dat")

        File::writeLine(miscFile, self\miscData\GameName)
        File::writeLine(miscFile, self\miscData\GameUpdate)
        File::writeLine(miscFile, self\miscData\GameMusicUpdate)
        File::writeLine(miscFile, self\miscData\GameVersion)

        File::close(miscFile)
        Delete(miscFile)

        // Money
        Local moneyFile.File = new File("Data\Game Data\Money.dat")

        File::writeLine(moneyFile, self\moneyData\Money1)
        File::writeLine(moneyFile, self\moneyData\Money2)
        File::writeLine(moneyFile, self\moneyData\Money2x)
        File::writeLine(moneyFile, self\moneyData\Money3)
        File::writeLine(moneyFile, self\moneyData\Money3x)
        File::writeLine(moneyFile, self\moneyData\Money4)
        File::writeLine(moneyFile, self\moneyData\Money4x)

        File::close(moneyFile)
        Delete(moneyFile)
    End Method

End Type