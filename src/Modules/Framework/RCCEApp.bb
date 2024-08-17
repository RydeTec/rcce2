Strict

Type RCCEApp
    Field title$
    Field rootDir$

    Field semMajor%, semMinor%, semPatch%
    Field preReleasePhase$, preReleaseCount%
    
    Field debug%

    Field width%, height%

    Method create.RCCEApp(title$, rootDir$)
        self\title = title
        self\rootDir = rootDir
        
        self\debug = True

        self\semMajor = 2
        self\semMinor = 0
        self\semPatch = 0
        self\preReleasePhase = "alpha"
        self\preReleaseCount = 3

        return self
    End Method

    Method init(width%, height%)
        self\width = width
        self\height = height

        AppTitle self\title
        
        ChangeDir self\rootDir
        self\rootDir = CurrentDir()
    End Method

    Method version$()
        Local version$ = self\semMajor + "." + self\semMinor + "." + self\semPatch

        If (NOT self\preReleasePhase = 0)
            version = version + "-" + self\preReleasePhase

            If (self\preReleaseCount > 0)
                version = version + "." + self\preReleaseCount
            End If
        End If

        return version
    End Method
End Type