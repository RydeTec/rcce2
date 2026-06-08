Strict

Function NormalizeProjectRoot$(rootDir$)
    Local normalized$ = Replace$(rootDir$, "/", "\")

    While Len(normalized$) > 1 And Right$(normalized$, 1) = "\"
        normalized$ = Left$(normalized$, Len(normalized$) - 1)
    Wend

    Return Lower$(normalized$)
End Function

Function RecentProjectsFindByRootDir%(recentProjects.BBList, rootDir$)
    If recentProjects = Null Then Return -1

    Local normalizedRoot$ = NormalizeProjectRoot$(rootDir$)
    Local count = ListSize(recentProjects) - 1

    For i = 0 To count
        Local prj.Project = ListAt(recentProjects, i)
        If NormalizeProjectRoot$(prj\rootDir) = normalizedRoot$ Then Return i
    Next

    Return -1
End Function

Function RecentProjectsRemoveByRootDir(recentProjects.BBList, rootDir$)
    If recentProjects = Null Then Return

    Local index = RecentProjectsFindByRootDir(recentProjects, rootDir$)
    If index <> -1 Then ListRemove(recentProjects, index)
End Function

Function RecentProjectsPromote(recentProjects.BBList, prj.Project)
    If recentProjects = Null Or prj = Null Then Return

    RecentProjectsRemoveByRootDir(recentProjects, prj\rootDir)
    ListAdd(recentProjects, prj)

    Local lastIndex = ListSize(recentProjects) - 1
    Local promoted.Project = ListAt(recentProjects, lastIndex)

    For i = lastIndex To 1 Step -1
        ListReplace(recentProjects, i, ListAt(recentProjects, i - 1))
    Next

    ListReplace(recentProjects, 0, promoted)
End Function

Function RecentProjectsTrim(recentProjects.BBList, maxEntries%)
    If recentProjects = Null Or maxEntries < 0 Then Return

    While ListSize(recentProjects) > maxEntries
        ListRemove(recentProjects, ListSize(recentProjects) - 1)
    Wend
End Function
