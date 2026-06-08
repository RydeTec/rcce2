Strict

// =============================================================================
// Loom/ScriptsCatalog.bb -- catalog of .rsl script files under
// Data\Server Data\Scripts\, browseable as first-class entities in Loom.
// =============================================================================
//
// Why this exists: many entity fields (Item\Script$, Spell\Script$,
// Area\EntryScript$ / ExitScript$ / TriggerScript$[150] / SpawnScript$[1000]
// / SpawnActorScript$[1000] / SpawnDeathScript$[1000]) reference scripts
// by basename, but until now Loom had NO surface where a designer could
// see what scripts exist, let alone preview one or find what references
// it. GUE has the same gap. This module closes it for Loom.
//
// Catalog shape: at boot we scan `Data\Server Data\Scripts\*.rsl` and
// allocate one ScriptFile per match. Each holds the basename (without
// .rsl extension -- matches the reference shape), size in bytes, and
// line count (cheap pre-scan; needed for the composer header).
//
// File content is loaded LAZILY -- only when the composer asks via
// Scripts_GetContent$(name). A first-line read on the catalog scan would
// flatten ~40 files at boot time for content that's never inspected;
// the lazy path keeps cold boot snappy.
//
// Reference matching: entity Script$ fields can be stored with OR
// without the .rsl extension (legacy data drift across projects).
// Scripts_NormalizeName$ strips .rsl from both sides before compare.
//
// Strict module. Pure free-functions + a Type ScriptFile populated at
// boot (same shape as Tools.bb's ToolDef pattern).


// =============================================================================
// Type ScriptFile -- one entry per .rsl in the project's Scripts folder.
// =============================================================================
Type ScriptFile
    Field Name$         // basename without .rsl extension (matches refs)
    Field FullPath$     // absolute path on disk for Read/Open
    Field SizeBytes%    // file size from FileSize() at scan time
    Field LineCount%    // counted at scan time; one ReadLine pass per file
    Field Index%        // 0-based catalog index; used as refID by Threads
End Type


// =============================================================================
// Module state -- the catalog itself is the global ScriptFile type pool;
// we only need a few summary counters for the browser footer / ribbon.
// =============================================================================
Global ScriptsTotalCount% = 0
Global ScriptsScanError$  = ""    // populated if ReadDir fails


// =============================================================================
// Scripts_Init -- scan Data\Server Data\Scripts\ for *.rsl, populate
// the ScriptFile pool. Called once at boot from Loom.bb after the data
// loaders run.
//
// Failure modes:
//   - Scripts dir doesn't exist (fresh project): silent; catalog is empty.
//   - ReadDir returns 0 (permission denied / disk error): logged to
//     ScriptsScanError$ for the ribbon to surface; catalog is empty.
//   - Individual file scan fails: skipped + logged; other files still
//     populate.
// =============================================================================
Function Scripts_Init()
    Local dir$ = "Data\Server Data\Scripts"
    Local D.BBDir = ReadDir(dir$)
    If D = Null
        ScriptsScanError$ = "Could not read " + dir$
        Return
    EndIf

    Local idx% = 0
    Local f$
    Repeat
        f$ = NextFile$(D)
        If f$ <> "" And f$ <> "." And f$ <> ".."
            // Filter to .rsl source files only. .rcscript is the compiled
            // bytecode that the server runtime loads; it's regenerated
            // from .rsl on demand and not a source-of-truth artifact.
            If Right$(Lower$(f$), 4) = ".rsl"
                Local full$ = dir$ + "\" + f$
                If FileType(full) = 1
                    Local sf.ScriptFile = New ScriptFile()
                    sf\Name = Left$(f$, Len(f$) - 4)
                    sf\FullPath = full
                    sf\SizeBytes = FileSize(full)
                    sf\LineCount = Scripts_CountLines(full)
                    sf\Index = idx
                    idx = idx + 1
                EndIf
            EndIf
        EndIf
    Until f$ = ""
    CloseDir(D)

    ScriptsTotalCount = idx
End Function


// =============================================================================
// Scripts_CountLines -- cheap one-pass ReadLine count. Used for the
// composer header "N lines" badge.
// =============================================================================
Function Scripts_CountLines%(path$)
    Local F.BBStream = ReadFile(path)
    If F = Null Then Return 0
    Local n% = 0
    While Not Eof(F)
        ReadLine(F)
        n = n + 1
    Wend
    CloseFile(F)
    Return n
End Function


// =============================================================================
// Scripts_NormalizeName$ -- strip .rsl extension if present, return
// lowercase. Both sides of a reference compare go through this so
// "In-game Commands.rsl" == "In-game Commands" == "in-game commands".
// =============================================================================
Function Scripts_NormalizeName$(name$)
    Local n$ = Lower$(name$)
    If Right$(n, 4) = ".rsl" Then n = Left$(n, Len(n) - 4)
    Return n
End Function


// =============================================================================
// Scripts_GetByIndex.ScriptFile -- O(N) walk to find the i-th entry.
// Used by Threads::focus("script", i) -> composer dispatch. Walks the
// pool in insertion order (which == idx assignment order).
// =============================================================================
Function Scripts_GetByIndex.ScriptFile(idx%)
    For sf.ScriptFile = Each ScriptFile
        If sf\Index = idx Then Return sf
    Next
    Return Null
End Function


// =============================================================================
// Scripts_GetByName.ScriptFile -- case-insensitive lookup by basename,
// extension-tolerant on the query. Used by the broken-ref scanner and
// by future "click a Script chip to focus" flows.
// =============================================================================
Function Scripts_GetByName.ScriptFile(name$)
    Local key$ = Scripts_NormalizeName$(name)
    If key = "" Then Return Null
    For sf.ScriptFile = Each ScriptFile
        If Lower$(sf\Name) = key Then Return sf
    Next
    Return Null
End Function


// =============================================================================
// Scripts_GetContent$ -- read the .rsl file from disk (lazy). Cap at
// SCRIPTS_PREVIEW_MAX_BYTES so a runaway script doesn't blow up the
// composer paint loop. Caller chunks by Chr(10) for line rendering.
// =============================================================================
Const SCRIPTS_PREVIEW_MAX_BYTES% = 65536    // 64KB cap; ~1500 typical lines
Function Scripts_GetContent$(name$)
    Local sf.ScriptFile = Scripts_GetByName(name)
    If sf = Null Then Return ""

    Local F.BBStream = ReadFile(sf\FullPath)
    If F = Null Then Return ""

    Local out$ = ""
    Local bytes% = 0
    While Not Eof(F) And bytes < SCRIPTS_PREVIEW_MAX_BYTES
        Local L$ = ReadLine(F)
        out = out + L + Chr(10)
        bytes = bytes + Len(L) + 1
    Wend
    CloseFile(F)

    If bytes >= SCRIPTS_PREVIEW_MAX_BYTES
        out = out + Chr(10) + "[truncated: " + sf\Name + ".rsl exceeds 64KB preview cap]"
    EndIf
    Return out
End Function


// =============================================================================
// Scripts_FormatSize$ -- humanize FileSize bytes into "1.2KB" / "640B"
// for the composer/card header.
// =============================================================================
Function Scripts_FormatSize$(bytes%)
    If bytes < 1024 Then Return Str(bytes) + "B"
    Local k% = bytes / 1024
    If k < 1024 Then Return Str(k) + "KB"
    Local m% = k / 1024
    Return Str(m) + "MB"
End Function
