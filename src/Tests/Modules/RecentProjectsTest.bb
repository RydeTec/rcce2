Strict
EnableGC

Type Project
    Field rootDir$
End Type

Include "Modules\Project Manager\RecentProjects.bb"

Function CreateProject.Project(rootDir$)
    Local prj.Project = New Project()
    prj\rootDir = rootDir$
    Return prj
End Function

Test testRecentProjectsPromotePreservesHistoryOrder()
    Local recentProjects.BBList = CreateList()
    Local current.Project = CreateProject("C:\Projects\Current\")
    Local older.Project = CreateProject("C:\Projects\Older\")
    Local oldest.Project = CreateProject("C:\Projects\Oldest\")

    ListAdd(recentProjects, older)
    ListAdd(recentProjects, oldest)

    RecentProjectsPromote(recentProjects, current)

    Assert(ListSize(recentProjects) = 3)
    Local firstProject.Project = ListAt(recentProjects, 0)
    Local secondProject.Project = ListAt(recentProjects, 1)
    Local thirdProject.Project = ListAt(recentProjects, 2)
    Assert(firstProject\rootDir = current\rootDir)
    Assert(secondProject\rootDir = older\rootDir)
    Assert(thirdProject\rootDir = oldest\rootDir)

    FreeList(recentProjects)
    Delete Each Project
End Test

Test testRecentProjectsPromoteDeduplicatesNormalizedPaths()
    Local recentProjects.BBList = CreateList()
    Local remembered.Project = CreateProject("C:\Projects\Current")
    Local duplicate.Project = CreateProject("c:/projects/current/")
    Local other.Project = CreateProject("C:\Projects\Other")

    ListAdd(recentProjects, duplicate)
    ListAdd(recentProjects, other)

    RecentProjectsPromote(recentProjects, remembered)

    Assert(ListSize(recentProjects) = 2)
    Local firstProject.Project = ListAt(recentProjects, 0)
    Local secondProject.Project = ListAt(recentProjects, 1)
    Assert(firstProject\rootDir = remembered\rootDir)
    Assert(secondProject\rootDir = other\rootDir)

    FreeList(recentProjects)
    Delete Each Project
End Test

Test testRecentProjectsRemoveByRootDirUsesNormalizedPaths()
    Local recentProjects.BBList = CreateList()

    ListAdd(recentProjects, CreateProject("C:\Projects\One"))
    ListAdd(recentProjects, CreateProject("C:\Projects\Two\"))

    RecentProjectsRemoveByRootDir(recentProjects, "c:/projects/two")

    Assert(ListSize(recentProjects) = 1)
    Local firstProject.Project = ListAt(recentProjects, 0)
    Assert(firstProject\rootDir = "C:\Projects\One")

    FreeList(recentProjects)
    Delete Each Project
End Test

Test testRecentProjectsTrimMatchesSavedHistoryLimit()
    Local recentProjects.BBList = CreateList()

    For i = 0 To 11
        ListAdd(recentProjects, CreateProject("C:\Projects\" + Str$(i)))
    Next

    RecentProjectsTrim(recentProjects, 9)

    Assert(ListSize(recentProjects) = 9)
    Local firstProject.Project = ListAt(recentProjects, 0)
    Local ninth.Project = ListAt(recentProjects, 8)
    Assert(firstProject\rootDir = "C:\Projects\0")
    Assert(ninth\rootDir = "C:\Projects\8")

    FreeList(recentProjects)
    Delete Each Project
End Test
