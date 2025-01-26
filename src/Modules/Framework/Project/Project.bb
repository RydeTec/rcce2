Strict

Include "Modules\IO\Filesystem.bb"
Include "Modules\IO\File.bb"
Include "Modules\IO\Managers\OptionsDataManager.bb"
Include "Modules\IO\Managers\MiscDataManager.bb"
Include "Modules\IO\Managers\GameDataManager.bb"
Include "Modules\IO\Managers\ClientAreasDataManager.bb"

; Should eventually remove globals
Global GameDir$
Global GameName$ = ""
Global UpdateGame$ = ""
Global UpdateMusic = False

Const PROJECT_VERSION% = 20250126

Type Project
    Field rootDir$
    Field name$
    Field version%
    Field projectSettings.File

    ; Options
    Field updateGame$
    Field updateMusic%

    Method create.Project(rootDir$)
        self\rootDir = rootDir

        return self
    End Method

    Method verify()
        if (NOT Filesystem::dirExists(Null, self\rootDir + "Data")) return false
        return true
    End Method

    Method load()
        ChangeDir(self\rootDir)

        Local gameDataManager.GameDataManager = new GameDataManager()
        GameDataManager::Load(gameDataManager)

        self\name = gameDataManager\miscData\GameName
        self\updateGame = gameDataManager\miscData\GameUpdate
        self\updateMusic = gameDataManager\miscData\GameMusicUpdate
        self\version = gameDataManager\miscData\GameVersion

        Delete(gameDataManager)

        Local miscData.MiscDataManager = new MiscDataManager()
        MiscDataManager::Load(miscData)
        if (miscData\versionData\version > 20240115)
            self\version = miscData\versionData\version
        end if
        Delete miscData

        ; Backwards compatibility
        GameDir = self\rootDir
        GameName = self\name
        UpdateGame = self\updateGame
        UpdateMusic = self\updateMusic
    End Method

    Method save()
        Local gameDataManager.GameDataManager = new GameDataManager()
        GameDataManager::Load(gameDataManager)

        gameDataManager\miscData\GameName = self\name
        gameDataManager\miscData\GameUpdate = self\updateGame
        gameDataManager\miscData\GameMusicUpdate = self\updateMusic

        GameDataManager::Save(gameDataManager)
        Delete(gameDataManager)

        Local miscData.MiscDataManager = new MiscDataManager()
        MiscDataManager::Load(miscData)
        miscData\versionData\version = self\version
        MiscDataManager::Save(miscData)
        Delete miscData
    End Method

    Method needsMigrations()
        if (self\version < PROJECT_VERSION)
            return true
        end if

        return false
    End Method

    Method isFutureVersion()
        if (self\version > PROJECT_VERSION)
            return true
        end if

        return false
    End Method

    Method migrate()
        while (Project::needsMigrations(self))
            select self\version
                case 20250126
                    DebugLog "Current version is up to date."
                case 20250122
                    DebugLog "Running migration 20250122..."

                    local clientAreasDataManager.ClientAreasDataManager = new ClientAreasDataManager()
                    Local Dir.BBDir = ReadDir("Data\Areas")
                    Local File$ = NextFile$(Dir)
                    While File$ <> ""
                        If FileType("Data\Areas\" + File$) = 1
                            File$ = Replace$(File$, ".dat", "") : File$ = Replace$(File$, ".DAT", "") : File$ = Replace$(File$, ".Dat", "")
                            DebugLog "Migrating area " + File$ + "..."
                            ClientAreasDataManager::Load(clientAreasDataManager, File$, True)
                            ClientAreasDataManager::Save(clientAreasDataManager, File$)
                        EndIf
                        File$ = NextFile$(Dir)
                    Wend
                    CloseDir Dir
                    Delete clientAreasDataManager

                    self\version = 20250126
                case 20250115
                    DebugLog "Running migration 20250115..."

                    local gameDataManager.GameDataManager = new GameDataManager()
                    GameDataManager::Load(gameDataManager, True)
                    GameDataManager::Save(gameDataManager)
                    Delete gameDataManager

                    self\version = 20250122
                case 20240115
                    DebugLog "Running migration 20240115..."

                    // version.dat will now house the project version moving forward
                    local miscData.MiscDataManager = new MiscDataManager()
                    MiscDataManager::Load(miscData, True)
                    MiscDataManager::Save(miscData)
                    Delete miscData

                    self\version = 20250115
                default
                    DebugLog "Running initial migration..."

                    local options.OptionsDataManager = new OptionsDataManager()
                    OptionsDataManager::Load(options, True)
                    OptionsDataManager::Save(options)
                    Delete options


                    // Convert all Areas/*.dat
                    // What are the .rdr files?
                    // Convert Emitter Configs/*.rpc files
                    // Convert Game Data/*.dat
                        // Hosts.dat is fine
                        // Misc.dat is fine
                        // patchversion.dat is fine
                        // web.dat is fine
                    // Server Data/Areas/Ownerships?
                    // Server Data/Areas/*.dat
                    // Server Data/*.dat


                    self\version = 20240115
            end select
        wend

        Project::save(self)
    End Method
End Type