Strict

// =============================================================================
// Loom/MeshCatalog.bb -- catalog of every defined mesh in
// Data\Game Data\Meshes.dat, browseable as first-class entities.
// =============================================================================
//
// Sibling of TextureCatalog.bb (iter 66). Walks Meshes.dat with the
// engine's LockMeshes/GetMeshName$/UnlockMeshes path. Allocates one
// MeshEntry per defined slot. The composer view reuses the existing
// Loom_DrawMeshPreview widget (iters 40/41) so designers get a full
// 3D orbit/zoom preview of any mesh in the project -- this is the
// piece GUE's classic "Mesh Dialog" never offered.


// ---- Constants -------------------------------------------------------------
Const MESHES_MAX_ID = 65534


// ---- Type ------------------------------------------------------------------
Type MeshEntry
    Field ID%           // engine-side mesh ID
    Field Filename$     // basename relative to Data\Meshes\
    Field IsAnim%       // animation flag byte (1 = animated mesh, 0 = static)
    Field Index%        // 0-based catalog index for Threads::focus refID
End Type


// ---- Module state ----------------------------------------------------------
Global MeshesTotalCount% = 0


// =============================================================================
// Meshes_Init -- walk every slot in Data\Game Data\Meshes.dat, allocate
// a MeshEntry per populated one. Called once from Loom.bb after Textures_Init.
//
// Failure modes:
//   - Index file missing: catalog stays empty (fresh project).
//   - LockMeshes returns 0: silent; same as missing file.
// =============================================================================
Function Meshes_Init()
    If LockMeshes() = 0 Then Return

    Local idx% = 0
    Local id% = 0
    For id = 0 To MESHES_MAX_ID
        Local raw$ = GetMeshName$(id)
        If raw <> ""
            // Strip the trailing Chr(IsAnim) byte GetMeshName$ appends.
            Local nameLen% = Len(raw) - 1
            If nameLen < 0 Then nameLen = 0
            Local name$ = Left$(raw, nameLen)
            Local isAnim% = Asc(Right$(raw, 1))

            Local me.MeshEntry = New MeshEntry()
            me\ID = id
            me\Filename = name
            me\IsAnim = isAnim
            me\Index = idx
            idx = idx + 1
        EndIf
    Next
    UnlockMeshes()
    MeshesTotalCount = idx
End Function


// =============================================================================
// Meshes_GetByIndex.MeshEntry -- O(N) walk used by Threads::focus dispatch.
// =============================================================================
Function Meshes_GetByIndex.MeshEntry(idx%)
    For me.MeshEntry = Each MeshEntry
        If me\Index = idx Then Return me
    Next
    Return Null
End Function


// =============================================================================
// Meshes_GetByID.MeshEntry -- lookup by engine-side ID (not Index).
// Used by reverse-ref scanners.
// =============================================================================
Function Meshes_GetByID.MeshEntry(id%)
    For me.MeshEntry = Each MeshEntry
        If me\ID = id Then Return me
    Next
    Return Null
End Function
