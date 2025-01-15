Strict

Include "Modules\IO\Filesystem.bb"
Include "Modules\IO\File.bb"
Include "Modules\IO\Managers\OptionsDataManager.bb"

; Should eventually remove globals
Global GameDir$
Global GameName$ = ""
Global UpdateGame$ = ""
Global UpdateMusic = False

Const PROJECT_VERSION% = 20240115

Type Project
    Field rootDir$
    Field name$
    Field version$
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
        File::writeLine(self\projectSettings, self\version)

        File::close(self\projectSettings)
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
                case 20240115
                    DebugLog "Current version is up to date."
                default
                    DebugLog "Migrating options.dat..."

                    local options.OptionsDataManager = new OptionsDataManager()
                    OptionsDataManager::Load(options, True)
                    OptionsDataManager::Save(options)


                    // Convert options.dat
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
                    // controls.dat
                    // Last Username.dat is probably fine
                    // Version.dat can probably be deleted


                    self\version = 20240115
            end select
        wend

        Project::save(self)
    End Method
End Type