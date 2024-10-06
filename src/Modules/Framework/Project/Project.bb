Strict

Include "Modules\IO\Filesystem.bb"
Include "Modules\IO\File.bb"

; Should eventually remove globals
Global GameDir$
Global GameName$ = ""
Global UpdateGame$ = ""
Global UpdateMusic = False

Type Project
    Field rootDir$
    Field name$
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

        File::close(self\projectSettings)

        ; Backwards compatibility
        GameDir = self\rootDir
        GameName = self\name
        UpdateGame = self\updateGame
        UpdateMusic = self\updateMusic
    End Method
End Type