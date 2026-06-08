<!-- body { color:black background-color:white } a:link{ color:#0070FF } a:visited{ color:#0070FF } --> RealmCrafter: Community Edition Documentation

**Logging.bb**

This module owns the engine's logging and durable-file-write primitives. Two surfaces matter to most callers:

1. **`WriteLog`** for emitting timestamped lines to a log file, with an optional copy to the DEBUG log when `LogMode > 0`.
2. **`SafeWriteOpen` / `SafeWriteCommit` / `SafeWriteAbort`** for atomic writes to persistent data files. **Any module that persists state to disk must save through these helpers**, never via direct `WriteFile`. The atomic-write pattern preserves a `.bak` copy across the promote step, so a crash, power loss, or disk-full mid-write cannot leave a half-written production file. See [SafeWriteTest.bb](../../src/Tests/Modules/SafeWriteTest.bb) for pinned behavior.

A third surface, **`ReadBoundedString$`**, complements the save side: it caps the per-call read size so a corrupted on-disk file with a wild length prefix cannot hang the server at boot. Use it wherever you'd previously call `ReadString$` against an admin-editable data file.

This module contains the following globals:

*   [DebugLogHandle](#GDebugLogHandle)

This module contains the following types:

*   [LogFile](#TLogFile)

This module contains the following functions:

*   [SafeWriteOpen$](#FSafeWriteOpen)
*   [SafeWriteCommit%](#FSafeWriteCommit)
*   [SafeWriteAbort](#FSafeWriteAbort)
*   [ReadBoundedString$](#FReadBoundedString)
*   [StartLog](#FStartLog)
*   [WriteLog](#FWriteLog)
*   [StopLog](#FStopLog)
*   [CloseAllLogs](#FCloseAllLogs)

  

* * *

  

**DebugLogHandle (global)**

Cached handle for the `DEBUG` log file. `WriteLog` used to call `StartLog + StopLog` per entry under `LogMode > 0`, which opens, writes one line, and closes the file every call — a serious I/O load. The handle is now held for the life of the process. Sentinel values: `0` means not yet opened, `-1` means a previous open failed (so `WriteLog` doesn't keep retrying).

  

* * *

  

**LogFile (type)**

Wraps an open log file handle so multiple log files can be tracked and closed at shutdown by `CloseAllLogs`.

  

* * *

  
  
  

**SafeWriteOpen$(FinalPath$)**

Return value: A temporary path (`FinalPath$ + ".tmp"`) the caller writes into.

Parameters:

*   _FinalPath$_ — Production path the save will eventually occupy.

This function returns the path to use for `WriteFile`; the caller writes all its data into that temp path, then calls `SafeWriteCommit` to promote the temp into production. The returned path is deterministic so abort paths can target it.

```basic
Local TempPath$ = SafeWriteOpen$(FinalPath$)
Local F = WriteFile(TempPath$)
; ... WriteX(F, ...) ...
SafeWriteCommit%(TempPath$, FinalPath$, F)
```

  

* * *

  

**SafeWriteCommit%(TempPath$, FinalPath$, F)**

Return value: `True` on success, `False` on any failure (in which case the production file is the previous version and the temp has been cleaned up).

Parameters:

*   _TempPath$_ — Path returned by `SafeWriteOpen`.
*   _FinalPath$_ — Production path the temp is being promoted into.
*   _F_ — File handle to close. Pass `0` if the caller already closed it.

Steps: closes the temp handle, refuses to promote an empty temp (zero bytes means the write path failed silently), demotes any existing production file to `.bak`, copies the temp into production, deletes the temp. If the promote fails catastrophically, rolls back from `.bak` and returns `False`.

  

* * *

  

**SafeWriteAbort(TempPath$)**

Return value: None.

Parameters:

*   _TempPath$_ — The temp path produced by `SafeWriteOpen`.

Cleans up the temp file when the caller decides not to commit — e.g. a serialization error mid-write that leaves the temp in an inconsistent state. The production file is untouched.

  

* * *

  

**ReadBoundedString$(F, MaxLen)**

Return value: The string read, or `""` if `F = 0`, the length prefix is out of range, or EOF was reached before the full length was consumed.

Parameters:

*   _F_ — File handle (matches the type returned by `ReadFile`).
*   _MaxLen_ — Maximum length to accept in bytes.

Peeks the 4-byte length prefix as an `Int`, refuses lengths outside `[0, MaxLen]`, then reads the bytes manually. On a bad length it logs once via `MainLog` and returns `""`. Use this everywhere you would have used `ReadString$` against an admin-editable data file (`Items.dat`, `Spells.dat`, `Accounts.dat`, `Superglobals.dat`, `Environment.dat`, `AnimSets.dat`, `Projectiles.dat`). The cap should be chosen by field role — 256 for short identifiers, 1024 for script paths, 4096 for free-form per-record state.

  

* * *

  

**StartLog(Logname$, Append)**

Return value: File handle, or `0` on failure.

Parameters:

*   _Logname$_ — Base name of the log file. `Data\Logs\` is prepended automatically and `.txt` appended.
*   _Append_ — `True` (default) to append to an existing file; `False` to truncate.

Creates the `Data\Logs\` directory if missing, opens the requested log, registers a `LogFile` record so `CloseAllLogs` can drop it cleanly at shutdown.

  

* * *

  

**WriteLog(LogHandle, Dat$, Timestamp, Datestamp)**

Return value: None.

Parameters:

*   _LogHandle_ — File handle returned by `StartLog`. `0` is a valid sentinel meaning "log path not available"; the line is then only emitted to the DEBUG log if `LogMode > 0`.
*   _Dat$_ — The message body.
*   _Timestamp_ — `True` (default) to prefix `[HH:MM:SS]`.
*   _Datestamp_ — `True` to prefix `[YYYY-MM-DD]`. Default `False`.

Writes one line to the file and, when `LogMode > 0`, also emits a copy to the DEBUG log via the cached `DebugLogHandle`. Every soft-fail recovery path in the engine ends in a `WriteLog` call so live worlds leave a trail for forensics.

  

* * *

  

**StopLog(LogHandle)**

Return value: None.

Parameters:

*   _LogHandle_ — File handle previously returned by `StartLog`.

Removes the matching `LogFile` record and closes the file handle.

  

* * *

  

**CloseAllLogs()**

Return value: None.

Parameters: None.

Closes every open `LogFile` and the cached `DebugLogHandle`. Called from the server's shutdown sequence (see [Server.bb](../../src/Server.bb) `Shutdown()`).

  

* * *

  
  
  
