Strict
Include "Modules\IO\File.bb"

// Will manage the reading and writing of Areas/*.dat files
// Server Data/Areas/Ownerships?
// Server Data/Areas/*.dat

Type ClientAreaSoundData
    Field X#
    Field Y#
    Field Z#
    Field Radius#
    Field SoundID%
    Field MusicID%
    Field RepeatTime%
    Field Volume#
End Type

Type ClientAreaTerrainData
    Field BaseTexID%
    Field DetailTexID%
    Field GridSize%
    Field Points#[65535]
    Field X#
    Field Y#
    Field Z#
    Field Pitch#
    Field Yaw#
    Field Roll#
    Field ScaleX#
    Field ScaleY#
    Field ScaleZ#
    Field DetailTexScale#
    Field Detail%
    Field Morph%
    Field Shading%
End Type

Type ClientAreaEmitterData
    Field ConfigName$
    Field TexID%
    Field X#
    Field Y#
    Field Z#
    Field Pitch#
    Field Yaw#
    Field Roll#
End Type

Type ClientAreaCollisionData
    Field X#
    Field Y#
    Field Z#
    Field Pitch#
    Field Yaw#
    Field Roll#
    Field ScaleX#
    Field ScaleY#
    Field ScaleZ#
End Type

Type ClientAreaWaterData
    Field TexID%
    Field TexScale#
    Field X#
    Field Y#
    Field Z#
    Field ScaleX#
    Field ScaleZ#
    Field Red%
    Field Green%
    Field Blue%
    Field Opacity%
End Type

Type ClientAreaSceneryData
    Field MeshID%
    Field X#
    Field Y#
    Field Z#
    Field Pitch#
    Field Yaw#
    Field Roll#
    Field ScaleX#
    Field ScaleY#
    Field ScaleZ#
    Field AnimationMode%
    Field SceneryID%
    Field TextureID%
    Field CatchRain%
    Field Collides%
    Field Lightmap$
    Field RCTE$
    Field CastShadow%
    Field ReceiveShadow%
    Field RenderRange%
End Type

Type ClientAreaData
    Field Name$
    Field LoadingTexID%
    Field LoadingMusicID%
    Field SkyTexID%
    Field CloudTexID%
    Field StormCloudTexID%
    Field StarsTexID%
    Field FogR%
    Field FogG%
    Field FogB%
    Field FogNear#
    Field FogFar#
    Field MapTexID%
    Field Outdoors%
    Field AmbientR%
    Field AmbientG%
    Field AmbientB%
    Field DefaultLightPitch#
    Field DefaultLightYaw#
    Field SlopeRestrict#

    Field Sceneries%
    Field SceneryData.BBList

    Field Waters%
    Field WaterData.BBList

    Field ColBoxes%
    Field ColBoxData.BBList

    Field Emitters%
    Field EmitterData.BBList

    Field Terrains%
    Field TerrainData.BBList

    Field Sounds%
    Field SoundData.BBList

    Method Create.ClientAreaData()
        self\SceneryData = CreateList()
        self\WaterData = CreateList()
        self\ColBoxData = CreateList()
        self\EmitterData = CreateList()
        self\TerrainData = CreateList()
        self\SoundData = CreateList()

        return self
    End Method
End Type

Type ClientAreasDataManager
    Field CurrentArea.ClientAreaData

    Method Create.ClientAreasDataManager()
        self\CurrentArea = new ClientAreaData()

        return self
    End Method

    Method Load(areaName$, obfuscated=False)
        if (obfuscated)
            ClientAreasDataManager::ReadObfuscated(self, areaName$)
        else
            ClientAreasDataManager::ReadFast(self, areaName$)
        end if
    End Method

    Method Save(areaName$, obfuscated=False)
        if (obfuscated)
            ClientAreasDataManager::WriteObfuscated(self, areaName$)
        else
            ClientAreasDataManager::WriteFast(self, areaName$)
        end if
    End Method

    Method ReadObfuscated(areaName$)
        Local file.File = new File("Data\Areas\" + areaName$ + ".dat")

        self\CurrentArea = new ClientAreaData()

        self\CurrentArea\Name = areaName$

        self\CurrentArea\LoadingTexID = File::readShort(file)
        self\CurrentArea\LoadingMusicID = File::readShort(file)
        self\CurrentArea\SkyTexID = File::readShort(file)
        self\CurrentArea\CloudTexID = File::readShort(file)
        self\CurrentArea\StormCloudTexID = File::readShort(file)
        self\CurrentArea\StarsTexID = File::readShort(file)

        self\CurrentArea\FogR = File::readByte(file)
        self\CurrentArea\FogG = File::readByte(file)
        self\CurrentArea\FogB = File::readByte(file)
        self\CurrentArea\FogNear# = File::readFloat(file)
        self\CurrentArea\FogFar# = File::readFloat(file)

        self\CurrentArea\MapTexID = File::readShort(file)
        self\CurrentArea\Outdoors = File::readByte(file)
        self\CurrentArea\AmbientR = File::readByte(file)
        self\CurrentArea\AmbientG = File::readByte(file)
        self\CurrentArea\AmbientB = File::readByte(file)
        self\CurrentArea\DefaultLightPitch# = File::readFloat(file)
        self\CurrentArea\DefaultLightYaw# = File::readFloat(file)
        self\CurrentArea\SlopeRestrict# = File::readFloat(file)

        self\CurrentArea\Sceneries = File::readShort(file)
        for i = 1 to self\CurrentArea\Sceneries
            Local sceneryData.ClientAreaSceneryData = new ClientAreaSceneryData()

            sceneryData\MeshID = File::readShort(file)
            sceneryData\X = File::readFloat(file)
            sceneryData\Y = File::readFloat(file)
            sceneryData\Z = File::readFloat(file)
            sceneryData\Pitch = File::readFloat(file)
            sceneryData\Yaw = File::readFloat(file)
            sceneryData\Roll = File::readFloat(file)
            sceneryData\ScaleX = File::readFloat(file)
            sceneryData\ScaleY = File::readFloat(file)
            sceneryData\ScaleZ = File::readFloat(file)
            sceneryData\AnimationMode = File::readByte(file)
            sceneryData\SceneryID = File::readByte(file)
            sceneryData\TextureID = File::readShort(file)
            sceneryData\CatchRain = File::readByte(file)
            sceneryData\Collides = File::readByte(file)
            sceneryData\Lightmap = File::readString(file)
            sceneryData\RCTE = File::readString(file)
            sceneryData\CastShadow = File::readByte(file)
            sceneryData\ReceiveShadow = File::readByte(file)
            sceneryData\RenderRange = File::readByte(file)

            ListAdd(self\CurrentArea\SceneryData, sceneryData)
        next

        self\CurrentArea\Waters = File::readShort(file)
        for i = 1 to self\CurrentArea\Waters
            Local waterData.ClientAreaWaterData = new ClientAreaWaterData()

            waterData\TexID = File::readShort(file)
            waterData\TexScale = File::readFloat(file)
            waterData\X = File::readFloat(file)
            waterData\Y = File::readFloat(file)
            waterData\Z = File::readFloat(file)
            waterData\ScaleX = File::readFloat(file)
            waterData\ScaleZ = File::readFloat(file)
            waterData\Red = File::readByte(file)
            waterData\Green = File::readByte(file)
            waterData\Blue = File::readByte(file)
            waterData\Opacity = File::readByte(file)

            ListAdd(self\CurrentArea\WaterData, waterData)
        next

        self\CurrentArea\ColBoxes = File::readShort(file)
        for i = 1 to self\CurrentArea\ColBoxes
            Local colBoxData.ClientAreaCollisionData = new ClientAreaCollisionData()

            colBoxData\X = File::readFloat(file)
            colBoxData\Y = File::readFloat(file)
            colBoxData\Z = File::readFloat(file)
            colBoxData\Pitch = File::readFloat(file)
            colBoxData\Yaw = File::readFloat(file)
            colBoxData\Roll = File::readFloat(file)
            colBoxData\ScaleX = File::readFloat(file)
            colBoxData\ScaleY = File::readFloat(file)
            colBoxData\ScaleZ = File::readFloat(file)

            ListAdd(self\CurrentArea\ColBoxData, colBoxData)
        next

        self\CurrentArea\Emitters = File::readShort(file)
        for i = 1 to self\CurrentArea\Emitters
            Local emitterData.ClientAreaEmitterData = new ClientAreaEmitterData()

            emitterData\ConfigName = File::readString(file)
            emitterData\TexID = File::readShort(file)
            emitterData\X = File::readFloat(file)
            emitterData\Y = File::readFloat(file)
            emitterData\Z = File::readFloat(file)
            emitterData\Pitch = File::readFloat(file)
            emitterData\Yaw = File::readFloat(file)
            emitterData\Roll = File::readFloat(file)

            ListAdd(self\CurrentArea\EmitterData, emitterData)
        next

        self\CurrentArea\Terrains = File::readShort(file)
        for i = 1 to self\CurrentArea\Terrains
            Local terrainData.ClientAreaTerrainData = new ClientAreaTerrainData()

            terrainData\BaseTexID = File::readShort(file)
            terrainData\DetailTexID = File::readShort(file)
            terrainData\GridSize = File::readInt(file)
            for j = 0 to terrainData\GridSize
                for k = 0 to terrainData\GridSize
                    terrainData\Points[j + k * terrainData\GridSize] = File::readFloat(file)
                next
            next
            terrainData\X = File::readFloat(file)
            terrainData\Y = File::readFloat(file)
            terrainData\Z = File::readFloat(file)
            terrainData\Pitch = File::readFloat(file)
            terrainData\Yaw = File::readFloat(file)
            terrainData\Roll = File::readFloat(file)
            terrainData\ScaleX = File::readFloat(file)
            terrainData\ScaleY = File::readFloat(file)
            terrainData\ScaleZ = File::readFloat(file)
            terrainData\DetailTexScale = File::readFloat(file)
            terrainData\Detail = File::readInt(file)
            terrainData\Morph = File::readByte(file)
            terrainData\Shading = File::readByte(file)

            ListAdd(self\CurrentArea\TerrainData, terrainData)
        next

        self\CurrentArea\Sounds = File::readShort(file)
        for i = 1 to self\CurrentArea\Sounds
            Local soundData.ClientAreaSoundData = new ClientAreaSoundData()

            soundData\X = File::readFloat(file)
            soundData\Y = File::readFloat(file)
            soundData\Z = File::readFloat(file)
            soundData\Radius = File::readFloat(file)
            soundData\SoundID = File::readShort(file)
            soundData\MusicID = File::readShort(file)
            soundData\RepeatTime = File::readInt(file)
            soundData\Volume = File::readByte(file)

            ListAdd(self\CurrentArea\SoundData, soundData)
        next

        File::Close(file)
        Delete(file)
    End Method

    Method WriteObfuscated(areaName$)
        Local file.File = new File("Data\Areas\" + areaName$ + ".dat")

        File::writeShort(file, self\CurrentArea\LoadingTexID)
        File::writeShort(file, self\CurrentArea\LoadingMusicID)
        File::writeShort(file, self\CurrentArea\SkyTexID)
        File::writeShort(file, self\CurrentArea\CloudTexID)
        File::writeShort(file, self\CurrentArea\StormCloudTexID)
        File::writeShort(file, self\CurrentArea\StarsTexID)

        File::writeByte(file, self\CurrentArea\FogR)
        File::writeByte(file, self\CurrentArea\FogG)
        File::writeByte(file, self\CurrentArea\FogB)
        File::writeFloat(file, self\CurrentArea\FogNear#)
        File::writeFloat(file, self\CurrentArea\FogFar#)

        File::writeShort(file, self\CurrentArea\MapTexID)
        File::writeByte(file, self\CurrentArea\Outdoors)
        File::writeByte(file, self\CurrentArea\AmbientR)
        File::writeByte(file, self\CurrentArea\AmbientG)
        File::writeByte(file, self\CurrentArea\AmbientB)
        File::writeFloat(file, self\CurrentArea\DefaultLightPitch#)
        File::writeFloat(file, self\CurrentArea\DefaultLightYaw#)
        File::writeFloat(file, self\CurrentArea\SlopeRestrict#)

        File::writeShort(file, self\CurrentArea\Sceneries)
        for i = 1 to self\CurrentArea\Sceneries
            Local sceneryData.ClientAreaSceneryData = ListAt(self\CurrentArea\SceneryData, i-1)

            File::writeShort(file, sceneryData\MeshID)
            File::writeFloat(file, sceneryData\X)
            File::writeFloat(file, sceneryData\Y)
            File::writeFloat(file, sceneryData\Z)
            File::writeFloat(file, sceneryData\Pitch)
            File::writeFloat(file, sceneryData\Yaw)
            File::writeFloat(file, sceneryData\Roll)
            File::writeFloat(file, sceneryData\ScaleX)
            File::writeFloat(file, sceneryData\ScaleY)
            File::writeFloat(file, sceneryData\ScaleZ)
            File::writeByte(file, sceneryData\AnimationMode)
            File::writeByte(file, sceneryData\SceneryID)
            File::writeShort(file, sceneryData\TextureID)
            File::writeByte(file, sceneryData\CatchRain)
            File::writeByte(file, sceneryData\Collides)
            File::writeString(file, sceneryData\Lightmap)
            File::writeString(file, sceneryData\RCTE)
            File::writeByte(file, sceneryData\CastShadow)
            File::writeByte(file, sceneryData\ReceiveShadow)
            File::writeByte(file, sceneryData\RenderRange)
        next

        File::writeShort(file, self\CurrentArea\Waters)
        for i = 1 to self\CurrentArea\Waters
            Local waterData.ClientAreaWaterData = ListAt(self\CurrentArea\WaterData, i-1)

            File::writeShort(file, waterData\TexID)
            File::writeFloat(file, waterData\TexScale)
            File::writeFloat(file, waterData\X)
            File::writeFloat(file, waterData\Y)
            File::writeFloat(file, waterData\Z)
            File::writeFloat(file, waterData\ScaleX)
            File::writeFloat(file, waterData\ScaleZ)
            File::writeByte(file, waterData\Red)
            File::writeByte(file, waterData\Green)
            File::writeByte(file, waterData\Blue)
            File::writeByte(file, waterData\Opacity)
        next

        File::writeShort(file, self\CurrentArea\ColBoxes)
        for i = 1 to self\CurrentArea\ColBoxes
            Local colBoxData.ClientAreaCollisionData = ListAt(self\CurrentArea\ColBoxData, i-1)

            File::writeFloat(file, colBoxData\X)
            File::writeFloat(file, colBoxData\Y)
            File::writeFloat(file, colBoxData\Z)
            File::writeFloat(file, colBoxData\Pitch)
            File::writeFloat(file, colBoxData\Yaw)
            File::writeFloat(file, colBoxData\Roll)
            File::writeFloat(file, colBoxData\ScaleX)
            File::writeFloat(file, colBoxData\ScaleY)
            File::writeFloat(file, colBoxData\ScaleZ)
        next

        File::writeShort(file, self\CurrentArea\Emitters)
        for i = 1 to self\CurrentArea\Emitters
            Local emitterData.ClientAreaEmitterData = ListAt(self\CurrentArea\EmitterData, i-1)

            File::writeString(file, emitterData\ConfigName)
            File::writeShort(file, emitterData\TexID)
            File::writeFloat(file, emitterData\X)
            File::writeFloat(file, emitterData\Y)
            File::writeFloat(file, emitterData\Z)
            File::writeFloat(file, emitterData\Pitch)
            File::writeFloat(file, emitterData\Yaw)
            File::writeFloat(file, emitterData\Roll)
        next

        File::writeShort(file, self\CurrentArea\Terrains)
        for i = 1 to self\CurrentArea\Terrains
            Local terrainData.ClientAreaTerrainData = ListAt(self\CurrentArea\TerrainData, i-1)

            File::writeShort(file, terrainData\BaseTexID)
            File::writeShort(file, terrainData\DetailTexID)
            File::writeInt(file, terrainData\GridSize)
            for j = 0 to terrainData\GridSize
                for k = 0 to terrainData\GridSize
                    File::writeFloat(file, terrainData\Points[j + k * terrainData\GridSize])
                next
            next
            File::writeFloat(file, terrainData\X)
            File::writeFloat(file, terrainData\Y)
            File::writeFloat(file, terrainData\Z)
            File::writeFloat(file, terrainData\Pitch)
            File::writeFloat(file, terrainData\Yaw)
            File::writeFloat(file, terrainData\Roll)
            File::writeFloat(file, terrainData\ScaleX)
            File::writeFloat(file, terrainData\ScaleY)
            File::writeFloat(file, terrainData\ScaleZ)
            File::writeFloat(file, terrainData\DetailTexScale)
            File::writeInt(file, terrainData\Detail)
            File::writeByte(file, terrainData\Morph)
            File::writeByte(file, terrainData\Shading)
        next

        File::writeShort(file, self\CurrentArea\Sounds)
        for i = 1 to self\CurrentArea\Sounds
            Local soundData.ClientAreaSoundData = ListAt(self\CurrentArea\SoundData, i-1)

            File::writeFloat(file, soundData\X)
            File::writeFloat(file, soundData\Y)
            File::writeFloat(file, soundData\Z)
            File::writeFloat(file, soundData\Radius)
            File::writeShort(file, soundData\SoundID)
            File::writeShort(file, soundData\MusicID)
            File::writeInt(file, soundData\RepeatTime)
            File::writeByte(file, soundData\Volume)
        next

        File::Close(file)
        Delete(file)
    End Method

    Method ReadFast(areaName$)
        Local file.File = new File("Data\Areas\" + areaName$ + ".dat")

        self\CurrentArea = new ClientAreaData()

        self\CurrentArea\Name = areaName$

        self\CurrentArea\LoadingTexID = Int(File::readLine(file))
        self\CurrentArea\LoadingMusicID = Int(File::readLine(file))
        self\CurrentArea\SkyTexID = Int(File::readLine(file))
        self\CurrentArea\CloudTexID = Int(File::readLine(file))
        self\CurrentArea\StormCloudTexID = Int(File::readLine(file))
        self\CurrentArea\StarsTexID = Int(File::readLine(file))

        self\CurrentArea\FogR = Int(File::readLine(file))
        self\CurrentArea\FogG = Int(File::readLine(file))
        self\CurrentArea\FogB = Int(File::readLine(file))
        self\CurrentArea\FogNear# = Float(File::readLine(file))
        self\CurrentArea\FogFar# = Float(File::readLine(file))

        self\CurrentArea\MapTexID = Int(File::readLine(file))
        self\CurrentArea\Outdoors = Int(File::readLine(file))
        self\CurrentArea\AmbientR = Int(File::readLine(file))
        self\CurrentArea\AmbientG = Int(File::readLine(file))
        self\CurrentArea\AmbientB = Int(File::readLine(file))
        self\CurrentArea\DefaultLightPitch# = Float(File::readLine(file))
        self\CurrentArea\DefaultLightYaw# = Float(File::readLine(file))
        self\CurrentArea\SlopeRestrict# = Float(File::readLine(file))

        self\CurrentArea\Sceneries = Int(File::readLine(file))
        for i = 1 to self\CurrentArea\Sceneries
            Local sceneryData.ClientAreaSceneryData = new ClientAreaSceneryData()

            sceneryData\MeshID = Int(File::readLine(file))
            sceneryData\X = Float(File::readLine(file))
            sceneryData\Y = Float(File::readLine(file))
            sceneryData\Z = Float(File::readLine(file))
            sceneryData\Pitch = Float(File::readLine(file))
            sceneryData\Yaw = Float(File::readLine(file))
            sceneryData\Roll = Float(File::readLine(file))
            sceneryData\ScaleX = Float(File::readLine(file))
            sceneryData\ScaleY = Float(File::readLine(file))
            sceneryData\ScaleZ = Float(File::readLine(file))
            sceneryData\AnimationMode = Int(File::readLine(file))
            sceneryData\SceneryID = Int(File::readLine(file))
            sceneryData\TextureID = Int(File::readLine(file))
            sceneryData\CatchRain = Int(File::readLine(file))
            sceneryData\Collides = Int(File::readLine(file))
            sceneryData\Lightmap = File::readLine(file)
            sceneryData\RCTE = File::readLine(file)
            sceneryData\CastShadow = Int(File::readLine(file))
            sceneryData\ReceiveShadow = Int(File::readLine(file))
            sceneryData\RenderRange = Int(File::readLine(file))

            ListAdd(self\CurrentArea\SceneryData, sceneryData)
        next

        self\CurrentArea\Waters = Int(File::readLine(file))
        for i = 1 to self\CurrentArea\Waters
            Local waterData.ClientAreaWaterData = new ClientAreaWaterData()

            waterData\TexID = Int(File::readLine(file))
            waterData\TexScale = Float(File::readLine(file))
            waterData\X = Float(File::readLine(file))
            waterData\Y = Float(File::readLine(file))
            waterData\Z = Float(File::readLine(file))
            waterData\ScaleX = Float(File::readLine(file))
            waterData\ScaleZ = Float(File::readLine(file))
            waterData\Red = Int(File::readLine(file))
            waterData\Green = Int(File::readLine(file))
            waterData\Blue = Int(File::readLine(file))
            waterData\Opacity = Int(File::readLine(file))

            ListAdd(self\CurrentArea\WaterData, waterData)
        next

        self\CurrentArea\ColBoxes = Int(File::readLine(file))
        for i = 1 to self\CurrentArea\ColBoxes
            Local colBoxData.ClientAreaCollisionData = new ClientAreaCollisionData()

            colBoxData\X = Float(File::readLine(file))
            colBoxData\Y = Float(File::readLine(file))
            colBoxData\Z = Float(File::readLine(file))
            colBoxData\Pitch = Float(File::readLine(file))
            colBoxData\Yaw = Float(File::readLine(file))
            colBoxData\Roll = Float(File::readLine(file))
            colBoxData\ScaleX = Float(File::readLine(file))
            colBoxData\ScaleY = Float(File::readLine(file))
            colBoxData\ScaleZ = Float(File::readLine(file))

            ListAdd(self\CurrentArea\ColBoxData, colBoxData)
        next

        self\CurrentArea\Emitters = Int(File::readLine(file))
        for i = 1 to self\CurrentArea\Emitters
            Local emitterData.ClientAreaEmitterData = new ClientAreaEmitterData()

            emitterData\ConfigName = File::readLine(file)
            emitterData\TexID = Int(File::readLine(file))
            emitterData\X = Float(File::readLine(file))
            emitterData\Y = Float(File::readLine(file))
            emitterData\Z = Float(File::readLine(file))
            emitterData\Pitch = Float(File::readLine(file))
            emitterData\Yaw = Float(File::readLine(file))
            emitterData\Roll = Float(File::readLine(file))

            ListAdd(self\CurrentArea\EmitterData, emitterData)
        next

        self\CurrentArea\Terrains = Int(File::readLine(file))
        for i = 1 to self\CurrentArea\Terrains
            Local terrainData.ClientAreaTerrainData = new ClientAreaTerrainData()

            terrainData\BaseTexID = Int(File::readLine(file))
            terrainData\DetailTexID = Int(File::readLine(file))
            terrainData\GridSize = Int(File::readLine(file))
            for j = 0 to terrainData\GridSize
                for k = 0 to terrainData\GridSize
                    terrainData\Points[j + k * terrainData\GridSize] = File::readFloat(file)
                next
            next
            terrainData\X = Float(File::readLine(file))
            terrainData\Y = Float(File::readLine(file))
            terrainData\Z = Float(File::readLine(file))
            terrainData\Pitch = Float(File::readLine(file))
            terrainData\Yaw = Float(File::readLine(file))
            terrainData\Roll = Float(File::readLine(file))
            terrainData\ScaleX = Float(File::readLine(file))
            terrainData\ScaleY = Float(File::readLine(file))
            terrainData\ScaleZ = Float(File::readLine(file))
            terrainData\DetailTexScale = Float(File::readLine(file))
            terrainData\Detail = Int(File::readLine(file))
            terrainData\Morph = Int(File::readLine(file))
            terrainData\Shading = Int(File::readLine(file))

            ListAdd(self\CurrentArea\TerrainData, terrainData)
        next

        self\CurrentArea\Sounds = Int(File::readLine(file))
        for i = 1 to self\CurrentArea\Sounds
            Local soundData.ClientAreaSoundData = new ClientAreaSoundData()

            soundData\X = Float(File::readLine(file))
            soundData\Y = Float(File::readLine(file))
            soundData\Z = Float(File::readLine(file))
            soundData\Radius = Float(File::readLine(file))
            soundData\SoundID = Int(File::readLine(file))
            soundData\MusicID = Int(File::readLine(file))
            soundData\RepeatTime = Int(File::readLine(file))
            soundData\Volume = Int(File::readLine(file))

            ListAdd(self\CurrentArea\SoundData, soundData)
        next

        File::Close(file)
        Delete(file)
    End Method

    Method WriteFast(areaName$)
        Local file.File = new File("Data\Areas\" + areaName$ + ".dat")

        File::writeLine(file, self\CurrentArea\LoadingTexID)
        File::writeLine(file, self\CurrentArea\LoadingMusicID)
        File::writeLine(file, self\CurrentArea\SkyTexID)
        File::writeLine(file, self\CurrentArea\CloudTexID)
        File::writeLine(file, self\CurrentArea\StormCloudTexID)
        File::writeLine(file, self\CurrentArea\StarsTexID)

        File::writeLine(file, self\CurrentArea\FogR)
        File::writeLine(file, self\CurrentArea\FogG)
        File::writeLine(file, self\CurrentArea\FogB)
        File::writeLine(file, self\CurrentArea\FogNear#)
        File::writeLine(file, self\CurrentArea\FogFar#)

        File::writeLine(file, self\CurrentArea\MapTexID)
        File::writeLine(file, self\CurrentArea\Outdoors)
        File::writeLine(file, self\CurrentArea\AmbientR)
        File::writeLine(file, self\CurrentArea\AmbientG)
        File::writeLine(file, self\CurrentArea\AmbientB)
        File::writeLine(file, self\CurrentArea\DefaultLightPitch#)
        File::writeLine(file, self\CurrentArea\DefaultLightYaw#)
        File::writeLine(file, self\CurrentArea\SlopeRestrict#)

        File::writeLine(file, self\CurrentArea\Sceneries)
        for i = 1 to self\CurrentArea\Sceneries
            Local sceneryData.ClientAreaSceneryData = ListAt(self\CurrentArea\SceneryData, i-1)

            File::writeLine(file, sceneryData\MeshID)
            File::writeLine(file, sceneryData\X)
            File::writeLine(file, sceneryData\Y)
            File::writeLine(file, sceneryData\Z)
            File::writeLine(file, sceneryData\Pitch)
            File::writeLine(file, sceneryData\Yaw)
            File::writeLine(file, sceneryData\Roll)
            File::writeLine(file, sceneryData\ScaleX)
            File::writeLine(file, sceneryData\ScaleY)
            File::writeLine(file, sceneryData\ScaleZ)
            File::writeLine(file, sceneryData\AnimationMode)
            File::writeLine(file, sceneryData\SceneryID)
            File::writeLine(file, sceneryData\TextureID)
            File::writeLine(file, sceneryData\CatchRain)
            File::writeLine(file, sceneryData\Collides)
            File::writeLine(file, sceneryData\Lightmap)
            File::writeLine(file, sceneryData\RCTE)
            File::writeLine(file, sceneryData\CastShadow)
            File::writeLine(file, sceneryData\ReceiveShadow)
            File::writeLine(file, sceneryData\RenderRange)
        next

        File::writeLine(file, self\CurrentArea\Waters)
        for i = 1 to self\CurrentArea\Waters
            Local waterData.ClientAreaWaterData = ListAt(self\CurrentArea\WaterData, i-1)

            File::writeLine(file, waterData\TexID)
            File::writeLine(file, waterData\TexScale)
            File::writeLine(file, waterData\X)
            File::writeLine(file, waterData\Y)
            File::writeLine(file, waterData\Z)
            File::writeLine(file, waterData\ScaleX)
            File::writeLine(file, waterData\ScaleZ)
            File::writeLine(file, waterData\Red)
            File::writeLine(file, waterData\Green)
            File::writeLine(file, waterData\Blue)
            File::writeLine(file, waterData\Opacity)
        next

        File::writeLine(file, self\CurrentArea\ColBoxes)
        for i = 1 to self\CurrentArea\ColBoxes
            Local colBoxData.ClientAreaCollisionData = ListAt(self\CurrentArea\ColBoxData, i-1)

            File::writeLine(file, colBoxData\X)
            File::writeLine(file, colBoxData\Y)
            File::writeLine(file, colBoxData\Z)
            File::writeLine(file, colBoxData\Pitch)
            File::writeLine(file, colBoxData\Yaw)
            File::writeLine(file, colBoxData\Roll)
            File::writeLine(file, colBoxData\ScaleX)
            File::writeLine(file, colBoxData\ScaleY)
            File::writeLine(file, colBoxData\ScaleZ)
        next

        File::writeLine(file, self\CurrentArea\Emitters)
        for i = 1 to self\CurrentArea\Emitters
            Local emitterData.ClientAreaEmitterData = ListAt(self\CurrentArea\EmitterData, i-1)

            File::writeLine(file, emitterData\ConfigName)
            File::writeLine(file, emitterData\TexID)
            File::writeLine(file, emitterData\X)
            File::writeLine(file, emitterData\Y)
            File::writeLine(file, emitterData\Z)
            File::writeLine(file, emitterData\Pitch)
            File::writeLine(file, emitterData\Yaw)
            File::writeLine(file, emitterData\Roll)
        next

        File::writeLine(file, self\CurrentArea\Terrains)
        for i = 1 to self\CurrentArea\Terrains
            Local terrainData.ClientAreaTerrainData = ListAt(self\CurrentArea\TerrainData, i-1)

            File::writeLine(file, terrainData\BaseTexID)
            File::writeLine(file, terrainData\DetailTexID)
            File::writeLine(file, terrainData\GridSize)
            for j = 0 to terrainData\GridSize
                for k = 0 to terrainData\GridSize
                    File::writeLine(file, terrainData\Points[j + k * terrainData\GridSize])
                next
            next
            File::writeLine(file, terrainData\X)
            File::writeLine(file, terrainData\Y)
            File::writeLine(file, terrainData\Z)
            File::writeLine(file, terrainData\Pitch)
            File::writeLine(file, terrainData\Yaw)
            File::writeLine(file, terrainData\Roll)
            File::writeLine(file, terrainData\ScaleX)
            File::writeLine(file, terrainData\ScaleY)
            File::writeLine(file, terrainData\ScaleZ)
            File::writeLine(file, terrainData\DetailTexScale)
            File::writeLine(file, terrainData\Detail)
            File::writeLine(file, terrainData\Morph)
            File::writeLine(file, terrainData\Shading)
        next

        File::writeLine(file, self\CurrentArea\Sounds)
        for i = 1 to self\CurrentArea\Sounds
            Local soundData.ClientAreaSoundData = ListAt(self\CurrentArea\SoundData, i-1)

            File::writeLine(file, soundData\X)
            File::writeLine(file, soundData\Y)
            File::writeLine(file, soundData\Z)
            File::writeLine(file, soundData\Radius)
            File::writeLine(file, soundData\SoundID)
            File::writeLine(file, soundData\MusicID)
            File::writeLine(file, soundData\RepeatTime)
            File::writeLine(file, soundData\Volume)
        next

        File::Close(file)
        Delete(file)
    End Method

    Method Remove(areaName$)
        
    End Method

End Type

