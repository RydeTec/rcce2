Strict
EnableGC

// Project Manager guards every project-entry path with Project::verify before
// calling Project::load. A Data directory alone is not enough: load reads the
// required Data\Game Data\Misc.dat settings file immediately.
Function ProjectVerificationRequiresMiscSettingsFile%()
    Local F.BBStream = ReadFile("Modules\Framework\Project\Project.bb")
    Local Line$
    Local Stage%

    If F = Null Then F = ReadFile("..\Modules\Framework\Project\Project.bb")
    If F = Null Then F = ReadFile("..\..\Modules\Framework\Project\Project.bb")
    If F = Null Then Return False

    While Not Eof(F)
        Line$ = Trim$(ReadLine$(F))
        Select Stage
            Case 0
                If Line$ = "Method verify()" Then Stage = 1
            Case 1
                If Line$ = "if (NOT Filesystem::dirExists(Null, self\rootDir + \"Data\")) return false" Then Stage = 2
            Case 2
                If Line$ = "if (NOT Filesystem::fileExists(Null, self\rootDir + \"Data\Game Data\Misc.dat\")) return false" Then Stage = 3
                If Line$ = "return true"
                    CloseFile F
                    Return False
                EndIf
            Case 3
                If Line$ = "return true"
                    CloseFile F
                    Return True
                EndIf
        End Select
    Wend

    CloseFile F
    Return False
End Function

Test testProjectVerificationRequiresDataAndMiscSettings()
    Assert(ProjectVerificationRequiresMiscSettingsFile%() = True)
End Test
