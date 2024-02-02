.lib "SQLDLL.dll"

SQLStart%(Host$, User$, Pass$, Dbname$, Port%)
SQLMakeInstance%(Container%)
BBThreadComplete%(thread%)
BBFreeThread(thread%)
BBRetNowt%(thread%)

BBMakeContainer%()
BBDestroyContainer(bbc%)
BBSetInt(bbc%,Index%,i%)
BBSetStr(bbc%,Index%,v$)
BBSetFloat(bbc%,Index%,v#)
BBGetInt%(bbc%,Index%)
BBGetStr$(bbc%,Index%)
BBGetFloat#(bbc%,Index%)