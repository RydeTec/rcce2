Strict

// =============================================================================
// Loom/TextureCatalog.bb -- catalog of every defined texture in
// Data\Game Data\Textures.dat, browseable as first-class entities.
// =============================================================================
//
// Why this exists: entity fields (Item\ThumbnailTexID, Spell\ThumbnailTexID,
// Actor\MaleFaceIDs[]/FemaleFaceIDs[]/MaleBodyIDs[]/FemaleBodyIDs[]/BloodTexID)
// reference textures by integer ID, but until now Loom had NO surface to
// browse what textures exist or see who references each. GUE has the
// classic "Texture Dialog" pop-up, but no project-level overview.
//
// This module makes textures first-class threadable entities -- click
// a card to see usage, click an Item's ThumbnailTexID chip to jump to
// the texture (follow-up iter), use Ctrl+K to find textures by
// filename.
//
// Catalog shape: at boot we walk Data\Game Data\Textures.dat IDs 0..65534
// (the engine's hard ceiling). For each non-empty slot the index file's
// 4-byte DataAddress is non-zero -- we allocate one TextureEntry per
// hit with the basename and flags byte. ~70 textures in the shipped
// project; the 65535-ID walk takes <50ms with the file locked open
// (LockTextures + UnlockTextures).
//
// Architecture mirrors ScriptsCatalog (iter 60): Strict module + Type
// pool + free-function init. Same pattern lets Browser/Composer/Palette
// integrate it the same way they integrated scripts.


// ---- Constants -------------------------------------------------------------
Const TEXTURES_MAX_ID = 65534


// ---- Type ------------------------------------------------------------------
Type TextureEntry
    Field ID%           // engine-side texture ID (matches storage in entity fields)
    Field Filename$     // basename relative to Data\Textures\
    Field Flags%        // wrap/anim flag byte (rarely user-visible)
    Field Index%        // 0-based catalog index for Threads::focus refID
End Type


// ---- Module state ----------------------------------------------------------
Global TexturesTotalCount% = 0


// =============================================================================
// Textures_Init -- walk every slot in Data\Game Data\Textures.dat,
// allocate a TextureEntry for each populated one. Called once from
// Loom.bb after Scripts_Init.
//
// Failure modes:
//   - Index file missing: catalog stays empty (fresh project shape).
//   - LockTextures returns 0 (open failed): silent; same as missing file.
// =============================================================================
Function Textures_Init()
    If LockTextures() = 0 Then Return

    Local idx% = 0
    Local id% = 0
    For id = 0 To TEXTURES_MAX_ID
        // GetTextureName$ returns "" for empty slots; the locked-file
        // path inside Media.bb keeps the open stream warm so this
        // 65535-call loop is fast.
        Local raw$ = GetTextureName$(id)
        If raw <> ""
            // Strip the trailing Chr(Flags) byte that GetTextureName
            // appends. Without it we'd render the byte as a stray
            // control char in the card label.
            Local nameLen% = Len(raw) - 1
            If nameLen < 0 Then nameLen = 0
            Local name$ = Left$(raw, nameLen)
            Local flags% = Asc(Right$(raw, 1))

            Local te.TextureEntry = New TextureEntry()
            te\ID = id
            te\Filename = name
            te\Flags = flags
            te\Index = idx
            idx = idx + 1
        EndIf
    Next
    UnlockTextures()
    TexturesTotalCount = idx
End Function


// =============================================================================
// Textures_GetByIndex.TextureEntry -- O(N) walk used by Threads::focus
// dispatch ("texture" kind, refID = Index).
// =============================================================================
Function Textures_GetByIndex.TextureEntry(idx%)
    For te.TextureEntry = Each TextureEntry
        If te\Index = idx Then Return te
    Next
    Return Null
End Function


// =============================================================================
// Textures_GetByID.TextureEntry -- lookup by engine-side ID (not Index).
// Used by reverse-ref scanners and by future "click an Item's
// ThumbnailTexID chip" flows.
// =============================================================================
Function Textures_GetByID.TextureEntry(id%)
    For te.TextureEntry = Each TextureEntry
        If te\ID = id Then Return te
    Next
    Return Null
End Function
