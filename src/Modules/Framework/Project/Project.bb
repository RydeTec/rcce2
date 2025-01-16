Strict

Include "Modules\IO\Filesystem.bb"
Include "Modules\IO\File.bb"
Include "Modules\IO\Managers\OptionsDataManager.bb"
Include "Modules\IO\Managers\MiscDataManager.bb"

; Should eventually remove globals
Global GameDir$
Global GameName$ = ""
Global UpdateGame$ = ""
Global UpdateMusic = False

Const PROJECT_VERSION% = 20250115

Type Project
    Field rootDir$
    Field name$
    Field version%
    Field projectSettings.File

    ; Options
    Field updateGame
    Field updateMusic

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

        if (self\projectSettings = Null)
            self\projectSettings = new File("Data\Game Data\Misc.dat")
        end if

        self\name = File::readLine(self\projectSettings)
        self\updateGame = Int(File::readLine(self\projectSettings))
        self\updateMusic = Int(File::readLine(self\projectSettings))
        self\version = Int(File::readLine(self\projectSettings))

        File::close(self\projectSettings)

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
        if (self\projectSettings = Null)
            self\projectSettings = new File("Data\Game Data\Misc.dat")
        end if

        File::writeLine(self\projectSettings, self\name)
        File::writeLine(self\projectSettings, self\updateGame)
        File::writeLine(self\projectSettings, self\updateMusic)

        File::close(self\projectSettings)

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
                case 20250115
                    DebugLog "Current version is up to date."
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