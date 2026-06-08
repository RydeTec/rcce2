Strict

// =============================================================================
// Loom/BrokenRefs.bb -- modal that enumerates every broken reference
// =============================================================================
//
// The Validation Conscience Ribbon (Ribbon.bb) shows a roll-up count of
// broken references. This modal expands that count into a one-row-per-
// broken-ref list with **click-to-jump** to the source entity so the user
// can actually fix the dangling reference.
//
// Activation: click the broken-ref count chip in the Ribbon (when count
// > 0). Closes on Esc or click-outside-modal, just like the other
// Loom modals (palette, timeline).
//
// What "broken" means here matches Ribbon's recomputeCache:
//   Actor . DefaultFaction        invalid index OR slot empty
//   Actor . MAnimationSet         non-zero ID that doesn't resolve
//   Actor . FAnimationSet         non-zero ID that doesn't resolve
//   Zone  . PortalLinkArea$[i]    non-empty name that doesn't resolve
//
// We re-scan on every open (the underlying data could have changed since
// the user last opened the modal); cheap at our scale and keeps the
// surface honest. Cap at BROKENREFS_MAX_ENTRIES so a fundamentally
// broken project doesn't render thousands of rows.
//
// Architecture: Type with Methods (modal owns its own keyboard pump +
// list scroll state). Holds a Threads reference for jump dispatch. The
// Composer ref isn't needed -- fixing the broken ref is a separate user
// action (jump to source -> edit field via the existing chip / picker
// flow).


Const BROKENREFS_MAX_ENTRIES = 250
Const BROKENREFS_MODAL_W     = 900
Const BROKENREFS_MODAL_H     = 480
Const BROKENREFS_PAD         = 16
Const BROKENREFS_ROW_H       = 24
Const BROKENREFS_HEADER_H    = 32
Const BROKENREFS_HINT_H      = 24


// -----------------------------------------------------------------------------
// BrokenRef -- one diagnostic. Allocated by rebuild, freed by clearList.
// -----------------------------------------------------------------------------
Type BrokenRef
    Field SourceKind$        // entity kind that holds the broken ref
    Field SourceRefID%       // entity ID
    Field SourceLabel$       // cached display name at scan time
    Field FieldDesc$         // field name + indexer if any (e.g. "portal[3]")
    Field BadValue$          // string representation of the bad reference
    Field Diagnosis$         // human-friendly explanation
    Field Severity$          // "error" / "warning" / "info" -- drives left-rail color
    Field Category$          // "broken-ref" / "empty-field" / "playability" /
                             // "weapon-config" / "spell-config" / "orphan-zone"
                             // Used for grouping in the modal.
End Type


// =============================================================================
// BrokenRefs -- enumerate-and-jump modal.
// =============================================================================
Type BrokenRefs
    Field threads.Threads

    Field open%
    Field entryCount%
    Field scrollOffset%
    Field categoryFilter$    // "" = all; otherwise the Category to show
    // Deferred row-jump. A row click can't close the modal inline: closeModal
    // clears the BrokenRef pool via `For r = Each BrokenRef : Delete r`, and
    // the click fires from drawOneEntry WHILE drawEntries is iterating that
    // same pool -- deleting it mid-iteration corrupts the iterator and crashes
    // ("Stack overflow!"). So the click only records the target here; the
    // close + focus happen after the draw loop returns.
    Field pendingJump%
    Field pendingJumpKind$
    Field pendingJumpRefID%


    Method create.BrokenRefs(threads.Threads)
        self\threads = threads
        self\open = False
        self\entryCount = 0
        self\scrollOffset = 0
        self\categoryFilter = ""
        Return self
    End Method


    Method isOpen%()
        Return self\open
    End Method


    Method openModal()
        self\open = True
        self\scrollOffset = 0
        self\categoryFilter = ""
        self\pendingJump = False
        BrokenRefs::rebuild(self)
        FlushKeys
        // Eat the opening click so the modal's "click outside closes"
        // check doesn't fire on the same frame (MouseHit cache makes
        // all surfaces see the same click; see ImageCache.bb).
        Loom_ConsumeClick()
        WriteLog(LoomLog, "BrokenRefs: open (" + Str(self\entryCount) + " entries)")
    End Method


    Method closeModal()
        self\open = False
        BrokenRefs::clearList(self)
        WriteLog(LoomLog, "BrokenRefs: close")
    End Method


    Method clearList()
        Local r.BrokenRef
        For r = Each BrokenRef
            Delete r
        Next
        self\entryCount = 0
    End Method


    // -------------------------------------------------------------------------
    // rebuild -- scan every entity, emit one BrokenRef per dangling field.
    // Mirrors Ribbon::recomputeCache's checks; kept independent rather than
    // shared because each surface needs slightly different output (count vs
    // per-ref detail).
    //
    // Counters live on self\* because Strict rejects reassigning Method
    // Locals from inside nested For/If blocks.
    // -------------------------------------------------------------------------
    Method rebuild()
        BrokenRefs::clearList(self)

        // Actors -- broken refs (factions, anim sets) + content checks +
        // missing-mesh (base body meshes only -- iterating all appearance
        // arrays would dwarf the modal). Also missing-sound checks
        // against the SoundCatalog for speech slots.
        For Ac.Actor = Each Actor
            If self\entryCount >= BROKENREFS_MAX_ENTRIES Then Exit
            BrokenRefs::scanActor(self, Ac)
            BrokenRefs::scanActorContent(self, Ac)
            BrokenRefs::scanActorAssets(self, Ac)
            BrokenRefs::scanActorSounds(self, Ac)
        Next

        // Items -- empty name, weapon w/ 0 damage, asset checks (thumb +
        // M/F mesh), broken script binding
        Local It.Item
        For ai% = 0 To 65534
            If self\entryCount >= BROKENREFS_MAX_ENTRIES Then Exit
            It = ItemList(ai)
            If It <> Null
                BrokenRefs::scanItemContent(self, It)
                BrokenRefs::scanItemAssets(self, It)
                BrokenRefs::scanItemScripts(self, It)
            EndIf
        Next

        // Spells -- empty name, missing script + thumbnail asset check +
        // broken script binding
        Local Sp.Spell
        For asi% = 0 To 65534
            If self\entryCount >= BROKENREFS_MAX_ENTRIES Then Exit
            Sp = SpellsList(asi)
            If Sp <> Null
                BrokenRefs::scanSpellContent(self, Sp)
                BrokenRefs::scanSpellAssets(self, Sp)
                BrokenRefs::scanSpellScripts(self, Sp)
            EndIf
        Next

        // Zones -- broken portal refs + orphan zone checks + broken
        // script bindings across all 5 script-string families.
        For Ar.Area = Each Area
            If self\entryCount >= BROKENREFS_MAX_ENTRIES Then Exit
            BrokenRefs::scanZone(self, Ar)
            BrokenRefs::scanZoneContent(self, Ar)
            BrokenRefs::scanZoneScripts(self, Ar)
        Next

        // Orphan asset scans -- scripts / textures / meshes / sounds
        // in their respective catalogs that no entity references.
        // Designers use these to prune dead assets that accumulate
        // across project iterations. Each scan respects the global
        // entry-count cap; orphan-script runs first since it has the
        // smallest catalog typically.
        BrokenRefs::scanOrphanScripts(self)
        BrokenRefs::scanOrphanTextures(self)
        BrokenRefs::scanOrphanMeshes(self)
        BrokenRefs::scanOrphanSounds(self)

        // Clamp scroll
        If self\scrollOffset >= self\entryCount Then self\scrollOffset = self\entryCount - 1
        If self\scrollOffset < 0 Then self\scrollOffset = 0
    End Method


    // -------------------------------------------------------------------------
    // scanActorAssets -- check that the actor's primary mesh references
    // resolve to files on disk. Only checks MeshIDs[0] + MeshIDs[1]
    // (base male + female bodies); checking every gubbin + hair/beard/
    // face/body would balloon the modal for projects with intentional
    // empty slots.
    // -------------------------------------------------------------------------
    Method scanActorAssets(Ac.Actor)
        Local label$ = Ac\Race$ + " [" + Ac\Class$ + "]"
        If Ac\MeshIDs[0] > 0 And BrokenRefs::meshFileExists(self, Ac\MeshIDs[0]) = False
            BrokenRefs::emitFull(self, "actor", Ac\ID, label, "MeshIDs[0] (male base)", Str(Ac\MeshIDs[0]), "mesh ID has no file on disk", "warning", "missing-mesh")
        EndIf
        If Ac\MeshIDs[1] > 0 And BrokenRefs::meshFileExists(self, Ac\MeshIDs[1]) = False
            BrokenRefs::emitFull(self, "actor", Ac\ID, label, "MeshIDs[1] (female base)", Str(Ac\MeshIDs[1]), "mesh ID has no file on disk", "warning", "missing-mesh")
        EndIf
        If Ac\BloodTexID > 0 And BrokenRefs::textureFileExists(self, Ac\BloodTexID) = False
            BrokenRefs::emitFull(self, "actor", Ac\ID, label, "BloodTexID", Str(Ac\BloodTexID), "texture ID has no file on disk", "warning", "missing-texture")
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // scanItemAssets -- check Item's headline visual refs.
    // -------------------------------------------------------------------------
    Method scanItemAssets(It.Item)
        If It\ThumbnailTexID > 0 And BrokenRefs::textureFileExists(self, It\ThumbnailTexID) = False
            BrokenRefs::emitFull(self, "item", It\ID, It\Name$, "ThumbnailTexID", Str(It\ThumbnailTexID), "texture ID has no file on disk", "warning", "missing-texture")
        EndIf
        If It\MMeshID > 0 And BrokenRefs::meshFileExists(self, It\MMeshID) = False
            BrokenRefs::emitFull(self, "item", It\ID, It\Name$, "MMeshID", Str(It\MMeshID), "mesh ID has no file on disk", "warning", "missing-mesh")
        EndIf
        If It\FMeshID > 0 And BrokenRefs::meshFileExists(self, It\FMeshID) = False
            BrokenRefs::emitFull(self, "item", It\ID, It\Name$, "FMeshID", Str(It\FMeshID), "mesh ID has no file on disk", "warning", "missing-mesh")
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // scanSpellAssets -- check Spell's thumbnail.
    // -------------------------------------------------------------------------
    Method scanSpellAssets(Sp.Spell)
        If Sp\ThumbnailTexID > 0 And BrokenRefs::textureFileExists(self, Sp\ThumbnailTexID) = False
            BrokenRefs::emitFull(self, "spell", Sp\ID, Sp\Name$, "ThumbnailTexID", Str(Sp\ThumbnailTexID), "texture ID has no file on disk", "warning", "missing-texture")
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // textureFileExists -- True if GetTextureName$(ID) resolves to a real
    // file under Data\Textures\. The cached lookup name has a trailing
    // flag byte (per Media.bb's GetTexture); strip it.
    // -------------------------------------------------------------------------
    Method textureFileExists%(ID%)
        Local NameAndFlags$ = GetTextureName$(ID)
        If NameAndFlags = "" Then Return False
        Local NameLen% = Len(NameAndFlags) - 1
        If NameLen < 1 Then Return False
        Local Name$ = Left$(NameAndFlags, NameLen)
        If Name = "" Then Return False
        If FileType("Data\Textures\" + Name) = 1 Then Return True
        Return False
    End Method


    // -------------------------------------------------------------------------
    // meshFileExists -- True if GetMeshNameClean$(ID) resolves to a file
    // under Data\Meshes\.
    // -------------------------------------------------------------------------
    Method meshFileExists%(ID%)
        Local Name$ = GetMeshNameClean$(ID)
        If Name = "" Then Return False
        If FileType("Data\Meshes\" + Name) = 1 Then Return True
        Return False
    End Method


    // -------------------------------------------------------------------------
    // scanActorContent -- non-reference checks. Severity warning (not error)
    // since these don't crash the engine but degrade gameplay.
    // -------------------------------------------------------------------------
    Method scanActorContent(Ac.Actor)
        Local label$ = Ac\Race$ + " [" + Ac\Class$ + "]"

        // Playable actor but no animation set -- player char will T-pose.
        If Ac\Playable = True
            If Ac\MAnimationSet = 0 And Ac\Genders <> 2
                BrokenRefs::emitFull(self, "actor", Ac\ID, label, "MAnimationSet", "0", "playable male has no anim set", "warning", "playability")
            EndIf
            If Ac\FAnimationSet = 0 And Ac\Genders <> 1
                BrokenRefs::emitFull(self, "actor", Ac\ID, label, "FAnimationSet", "0", "playable female has no anim set", "warning", "playability")
            EndIf
        EndIf

        // Race + Class both empty -- unidentifiable actor template
        If Ac\Race$ = "" And Ac\Class$ = ""
            BrokenRefs::emitFull(self, "actor", Ac\ID, "(unnamed)", "Race+Class", "(both empty)", "actor has no Race or Class identifier", "warning", "empty-field")
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // scanItemContent -- content rules for Item templates.
    // -------------------------------------------------------------------------
    Method scanItemContent(It.Item)
        // Empty name -- item won't be findable in palette / search
        If It\Name$ = ""
            BrokenRefs::emitFull(self, "item", It\ID, "(unnamed)", "Name", "(empty)", "item has no Name", "warning", "empty-field")
        EndIf

        // Weapon (ItemType=1) with 0 WeaponDamage -- weapon is decorative
        If It\ItemType = 1 And It\WeaponDamage = 0
            BrokenRefs::emitFull(self, "item", It\ID, It\Name$, "WeaponDamage", "0", "weapon does no damage", "warning", "weapon-config")
        EndIf

        // Item has a script reference (Bound name) but no method -- nothing
        // will fire on use. Only flag when Script is set.
        If It\Script$ <> "" And It\SMethod$ = ""
            BrokenRefs::emitFull(self, "item", It\ID, It\Name$, "SMethod", "(empty)", "item has Bound script but no Method to call", "warning", "spell-config")
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // scanSpellContent -- content rules for Spell templates.
    // -------------------------------------------------------------------------
    Method scanSpellContent(Sp.Spell)
        If Sp\Name$ = ""
            BrokenRefs::emitFull(self, "spell", Sp\ID, "(unnamed)", "Name", "(empty)", "spell has no Name", "warning", "empty-field")
        EndIf

        // Spell with no script -- casting does nothing
        If Sp\Script$ = ""
            BrokenRefs::emitFull(self, "spell", Sp\ID, Sp\Name$, "Script", "(empty)", "spell has no Script bound -- cast does nothing", "warning", "spell-config")
        EndIf

        // Script bound but method empty
        If Sp\Script$ <> "" And Sp\SMethod$ = ""
            BrokenRefs::emitFull(self, "spell", Sp\ID, Sp\Name$, "SMethod", "(empty)", "spell has Bound script but no Method", "warning", "spell-config")
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // scanZoneContent -- orphan zone (no portals, no spawns, no triggers
    // = nothing to do there).
    // -------------------------------------------------------------------------
    Method scanZoneContent(Ar.Area)
        Local hasPortal% = False
        Local hasSpawn%  = False
        Local hasTrigger% = False
        Local i%
        For i = 0 To 99
            If Ar\PortalName$[i] <> "" Then hasPortal = True : Exit
        Next
        For i = 0 To 999
            If Ar\SpawnActor[i] > 0 Then hasSpawn = True : Exit
        Next
        For i = 0 To 149
            If Ar\TriggerScript$[i] <> "" Then hasTrigger = True : Exit
        Next

        If hasPortal = False And hasSpawn = False And hasTrigger = False
            BrokenRefs::emitFull(self, "zone", Handle(Ar), Ar\Name$, "(contents)", "0 portals, 0 spawns, 0 triggers", "orphan zone -- nothing for a player to do here", "info", "orphan-zone")
        EndIf
    End Method


    Method scanActor(Ac.Actor)
        Local label$ = Ac\Race$ + " [" + Ac\Class$ + "]"

        // DefaultFaction
        If Ac\DefaultFaction < 0 Or Ac\DefaultFaction > 99
            BrokenRefs::emit(self, "actor", Ac\ID, label, "DefaultFaction", Str(Ac\DefaultFaction), "faction index out of range")
        Else If Ac\DefaultFaction > 0 And FactionNames$(Ac\DefaultFaction) = ""
            BrokenRefs::emit(self, "actor", Ac\ID, label, "DefaultFaction", Str(Ac\DefaultFaction), "faction slot is empty (deleted?)")
        EndIf

        // MAnimationSet
        If Ac\MAnimationSet <> 0
            If BrokenRefs::animSetExists(self, Ac\MAnimationSet) = False
                BrokenRefs::emit(self, "actor", Ac\ID, label, "MAnimationSet", Str(Ac\MAnimationSet), "anim set ID doesn't resolve")
            EndIf
        EndIf

        // FAnimationSet
        If Ac\FAnimationSet <> 0
            If BrokenRefs::animSetExists(self, Ac\FAnimationSet) = False
                BrokenRefs::emit(self, "actor", Ac\ID, label, "FAnimationSet", Str(Ac\FAnimationSet), "anim set ID doesn't resolve")
            EndIf
        EndIf
    End Method


    Method scanZone(Ar.Area)
        Local p% = 0
        For p = 0 To 99
            If Ar\PortalLinkArea$[p] <> ""
                If BrokenRefs::zoneExists(self, Ar\PortalLinkArea$[p]) = False
                    Local fieldDesc$ = "portal[" + Str(p) + "]"
                    If Ar\PortalName$[p] <> "" Then fieldDesc = fieldDesc + " (" + Ar\PortalName$[p] + ")"
                    BrokenRefs::emit(self, "zone", Handle(Ar), Ar\Name$, fieldDesc, Ar\PortalLinkArea$[p], "target zone name doesn't resolve")
                EndIf
            EndIf
        Next
    End Method


    Method emit(sourceKind$, sourceRefID%, sourceLabel$, fieldDesc$, badValue$, diagnosis$)
        // Back-compat shape: existing scanActor / scanZone callers use this
        // shorter signature and emit broken-ref category at error severity.
        BrokenRefs::emitFull(self, sourceKind, sourceRefID, sourceLabel, fieldDesc, badValue, diagnosis, "error", "broken-ref")
    End Method


    // -------------------------------------------------------------------------
    // scanItemScripts -- emit broken-script issues when an item's Script
    // field points at a .rsl that doesn't exist in the catalog. Empty
    // Script field is fine (item just has no right-click handler).
    // -------------------------------------------------------------------------
    Method scanItemScripts(It.Item)
        If It\Script$ = "" Then Return
        If Scripts_GetByName(It\Script$) = Null
            BrokenRefs::emitFull(self, "item", It\ID, It\Name$, "Script", It\Script$, "script file not found in Data\Server Data\Scripts\", "warning", "broken-script")
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // scanSpellScripts -- same for spells. Empty Script is technically
    // an empty-field issue (spell-config severity) which scanSpellContent
    // already emits; here we only flag non-empty + non-resolving.
    // -------------------------------------------------------------------------
    Method scanSpellScripts(Sp.Spell)
        If Sp\Script$ = "" Then Return
        If Scripts_GetByName(Sp\Script$) = Null
            BrokenRefs::emitFull(self, "spell", Sp\ID, Sp\Name$, "Script", Sp\Script$, "script file not found in Data\Server Data\Scripts\", "warning", "broken-script")
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // scanZoneScripts -- walk every script-string field on the zone
    // (Entry / Exit / 150 triggers / 1000 spawn x 3 families) and flag
    // any that don't resolve. Capped at 25 emits per zone so a heavily-
    // broken zone doesn't flood the modal -- the rest are still listed
    // as "(+ N more in this zone)".
    // -------------------------------------------------------------------------
    Method scanZoneScripts(Ar.Area)
        Local emits% = 0
        Local maxPerZone% = 25

        If Ar\EntryScript$ <> "" And Scripts_GetByName(Ar\EntryScript$) = Null
            BrokenRefs::emitFull(self, "zone", Handle(Ar), Ar\Name$, "EntryScript", Ar\EntryScript$, "script file not found", "warning", "broken-script")
            emits = emits + 1
        EndIf
        If Ar\ExitScript$ <> "" And Scripts_GetByName(Ar\ExitScript$) = Null
            BrokenRefs::emitFull(self, "zone", Handle(Ar), Ar\Name$, "ExitScript", Ar\ExitScript$, "script file not found", "warning", "broken-script")
            emits = emits + 1
        EndIf

        // Triggers (150)
        Local ti% = 0
        For ti = 0 To 149
            If emits >= maxPerZone Then Exit
            If Ar\TriggerScript$[ti] <> "" And Scripts_GetByName(Ar\TriggerScript$[ti]) = Null
                BrokenRefs::emitFull(self, "zone", Handle(Ar), Ar\Name$, "TriggerScript[" + Str(ti) + "]", Ar\TriggerScript$[ti], "script file not found", "warning", "broken-script")
                emits = emits + 1
            EndIf
        Next

        // SpawnScript / SpawnActorScript / SpawnDeathScript (1000 x 3)
        Local si% = 0
        For si = 0 To 999
            If emits >= maxPerZone Then Exit
            If Ar\SpawnScript$[si] <> "" And Scripts_GetByName(Ar\SpawnScript$[si]) = Null
                BrokenRefs::emitFull(self, "zone", Handle(Ar), Ar\Name$, "SpawnScript[" + Str(si) + "]", Ar\SpawnScript$[si], "script file not found", "warning", "broken-script")
                emits = emits + 1
            EndIf
            If Ar\SpawnActorScript$[si] <> "" And Scripts_GetByName(Ar\SpawnActorScript$[si]) = Null
                BrokenRefs::emitFull(self, "zone", Handle(Ar), Ar\Name$, "SpawnActorScript[" + Str(si) + "]", Ar\SpawnActorScript$[si], "script file not found", "warning", "broken-script")
                emits = emits + 1
            EndIf
            If Ar\SpawnDeathScript$[si] <> "" And Scripts_GetByName(Ar\SpawnDeathScript$[si]) = Null
                BrokenRefs::emitFull(self, "zone", Handle(Ar), Ar\Name$, "SpawnDeathScript[" + Str(si) + "]", Ar\SpawnDeathScript$[si], "script file not found", "warning", "broken-script")
                emits = emits + 1
            EndIf
        Next
    End Method


    // -------------------------------------------------------------------------
    // scanOrphanScripts -- emit one issue per ScriptFile that no entity
    // references. Counts a script as referenced if ANY Item, Spell, or
    // Area script-string field's normalized name matches it. Heavy walk
    // (catalog * (items + spells + zones * 5_families * 1000_slots)) so
    // we early-out the inner search per script.
    // -------------------------------------------------------------------------
    Method scanOrphanScripts()
        For sf.ScriptFile = Each ScriptFile
            If self\entryCount >= BROKENREFS_MAX_ENTRIES Then Return
            If BrokenRefs::scriptIsReferenced(self, sf) = False
                BrokenRefs::emitFull(self, "script", sf\Index, sf\Name$ + ".rsl", "(referenced by)", "0 entities", "orphan script -- no entity binds to it; consider deleting", "info", "orphan-script")
            EndIf
        Next
    End Method


    // -------------------------------------------------------------------------
    // scriptIsReferenced -- True iff any Item/Spell/Area script-string
    // field's normalized name matches the given ScriptFile. Returns on
    // first hit to keep the walk cheap.
    // -------------------------------------------------------------------------
    Method scriptIsReferenced%(sf.ScriptFile)
        Local key$ = Scripts_NormalizeName$(sf\Name$)

        For It.Item = Each Item
            If It\Script$ <> "" And Scripts_NormalizeName$(It\Script$) = key Then Return True
        Next
        For Sp.Spell = Each Spell
            If Sp\Script$ <> "" And Scripts_NormalizeName$(Sp\Script$) = key Then Return True
        Next
        For Ar.Area = Each Area
            If Ar\EntryScript$ <> "" And Scripts_NormalizeName$(Ar\EntryScript$) = key Then Return True
            If Ar\ExitScript$  <> "" And Scripts_NormalizeName$(Ar\ExitScript$)  = key Then Return True
            Local ti% = 0
            For ti = 0 To 149
                If Ar\TriggerScript$[ti] <> "" And Scripts_NormalizeName$(Ar\TriggerScript$[ti]) = key Then Return True
            Next
            Local si% = 0
            For si = 0 To 999
                If Ar\SpawnScript$[si] <> "" And Scripts_NormalizeName$(Ar\SpawnScript$[si]) = key Then Return True
                If Ar\SpawnActorScript$[si] <> "" And Scripts_NormalizeName$(Ar\SpawnActorScript$[si]) = key Then Return True
                If Ar\SpawnDeathScript$[si] <> "" And Scripts_NormalizeName$(Ar\SpawnDeathScript$[si]) = key Then Return True
            Next
        Next

        Return False
    End Method


    // -------------------------------------------------------------------------
    // scanActorSounds -- emit missing-sound issues when an actor's
    // speech-slot IDs point at sounds not in the catalog. Empty (id=0)
    // slots are normal (actor just has fewer voice clips); non-zero
    // IDs that don't resolve indicate referencing a deleted asset.
    //
    // Per-actor cap 10 emits so an actor with all 32 slots broken
    // doesn't flood; surplus rolls into the global "+N more" pattern.
    // -------------------------------------------------------------------------
    Method scanActorSounds(Ac.Actor)
        Local label$ = Ac\Race$ + " [" + Ac\Class$ + "]"
        Local emits% = 0
        Local maxPer% = 10
        Local si% = 0
        For si = 0 To 15
            If emits >= maxPer Then Exit
            If Ac\MSpeechIDs[si] > 0 And Sounds_GetByID(Ac\MSpeechIDs[si]) = Null
                BrokenRefs::emitFull(self, "actor", Ac\ID, label, "MSpeechIDs[" + Str(si) + "]", Str(Ac\MSpeechIDs[si]), "sound ID not in catalog", "warning", "missing-sound")
                emits = emits + 1
            EndIf
            If Ac\FSpeechIDs[si] > 0 And Sounds_GetByID(Ac\FSpeechIDs[si]) = Null
                BrokenRefs::emitFull(self, "actor", Ac\ID, label, "FSpeechIDs[" + Str(si) + "]", Str(Ac\FSpeechIDs[si]), "sound ID not in catalog", "warning", "missing-sound")
                emits = emits + 1
            EndIf
        Next
    End Method


    // -------------------------------------------------------------------------
    // scanOrphanTextures -- emit info-severity issues per TextureEntry
    // that no entity references. Walk shape matches scanOrphanScripts.
    // -------------------------------------------------------------------------
    Method scanOrphanTextures()
        For te.TextureEntry = Each TextureEntry
            If self\entryCount >= BROKENREFS_MAX_ENTRIES Then Return
            If BrokenRefs::textureIsReferenced(self, te) = False
                BrokenRefs::emitFull(self, "texture", te\Index, te\Filename$ + " #" + Str(te\ID), "(referenced by)", "0 entities", "orphan texture -- safe to drop from the catalog", "info", "orphan-texture")
            EndIf
        Next
    End Method


    Method textureIsReferenced%(te.TextureEntry)
        Local id% = te\ID
        For It.Item = Each Item
            If It\ThumbnailTexID = id Then Return True
            If It\ImageID = id Then Return True
        Next
        For Sp.Spell = Each Spell
            If Sp\ThumbnailTexID = id Then Return True
        Next
        Local actorIdx% = 0
        For actorIdx = 0 To 65535
            Local Ac.Actor = ActorList(actorIdx)
            If Ac <> Null
                If Ac\BloodTexID = id Then Return True
                Local ai% = 0
                For ai = 0 To 4
                    If Ac\MaleFaceIDs[ai]   = id Then Return True
                    If Ac\FemaleFaceIDs[ai] = id Then Return True
                    If Ac\MaleBodyIDs[ai]   = id Then Return True
                    If Ac\FemaleBodyIDs[ai] = id Then Return True
                Next
            EndIf
        Next
        Return False
    End Method


    // -------------------------------------------------------------------------
    // scanOrphanMeshes -- same shape; checks Item.MMeshID/FMeshID +
    // Actor.MeshIDs[0..7] + BeardIDs + MaleHair/FemaleHair.
    // -------------------------------------------------------------------------
    Method scanOrphanMeshes()
        For mh.MeshEntry = Each MeshEntry
            If self\entryCount >= BROKENREFS_MAX_ENTRIES Then Return
            If BrokenRefs::meshIsReferenced(self, mh) = False
                BrokenRefs::emitFull(self, "mesh", mh\Index, mh\Filename$ + " #" + Str(mh\ID), "(referenced by)", "0 entities", "orphan mesh -- safe to drop from the catalog", "info", "orphan-mesh")
            EndIf
        Next
    End Method


    Method meshIsReferenced%(mh.MeshEntry)
        Local id% = mh\ID
        For It.Item = Each Item
            If It\MMeshID = id Then Return True
            If It\FMeshID = id Then Return True
        Next
        Local actorIdx% = 0
        For actorIdx = 0 To 65535
            Local Ac.Actor = ActorList(actorIdx)
            If Ac <> Null
                Local mi% = 0
                For mi = 0 To 7
                    If Ac\MeshIDs[mi] = id Then Return True
                Next
                Local bi% = 0
                For bi = 0 To 4
                    If Ac\BeardIDs[bi]      = id Then Return True
                    If Ac\MaleHairIDs[bi]   = id Then Return True
                    If Ac\FemaleHairIDs[bi] = id Then Return True
                Next
            EndIf
        Next
        Return False
    End Method


    // -------------------------------------------------------------------------
    // scanOrphanSounds -- same shape; checks Actor.MSpeechIDs[0..15] +
    // FSpeechIDs[0..15]. Items + Spells don't reference sounds.
    // -------------------------------------------------------------------------
    Method scanOrphanSounds()
        For sd.SoundEntry = Each SoundEntry
            If self\entryCount >= BROKENREFS_MAX_ENTRIES Then Return
            If BrokenRefs::soundIsReferenced(self, sd) = False
                BrokenRefs::emitFull(self, "sound", sd\Index, sd\Filename$ + " #" + Str(sd\ID), "(referenced by)", "0 entities", "orphan sound -- safe to drop from the catalog", "info", "orphan-sound")
            EndIf
        Next
    End Method


    Method soundIsReferenced%(sd.SoundEntry)
        Local id% = sd\ID
        Local actorIdx% = 0
        For actorIdx = 0 To 65535
            Local Ac.Actor = ActorList(actorIdx)
            If Ac <> Null
                Local si% = 0
                For si = 0 To 15
                    If Ac\MSpeechIDs[si] = id Then Return True
                    If Ac\FSpeechIDs[si] = id Then Return True
                Next
            EndIf
        Next
        Return False
    End Method


    Method emitFull(sourceKind$, sourceRefID%, sourceLabel$, fieldDesc$, badValue$, diagnosis$, severity$, category$)
        Local r.BrokenRef = New BrokenRef()
        r\SourceKind = sourceKind
        r\SourceRefID = sourceRefID
        r\SourceLabel = sourceLabel
        r\FieldDesc = fieldDesc
        r\BadValue = badValue
        r\Diagnosis = diagnosis
        r\Severity = severity
        r\Category = category
        self\entryCount = self\entryCount + 1
    End Method


    Method animSetExists%(id%)
        Local As.AnimSet
        For As = Each AnimSet
            If As\ID = id Then Return True
        Next
        Return False
    End Method


    Method zoneExists%(name$)
        Local upr$ = Upper$(name)
        Local Ar.Area
        For Ar = Each Area
            If Upper$(Ar\Name$) = upr Then Return True
        Next
        Return False
    End Method


    // -------------------------------------------------------------------------
    // renderAndUpdate -- per-frame paint + input. Returns True when the
    // modal consumed input so the outer Loom frame skips its own Esc.
    // -------------------------------------------------------------------------
    Method renderAndUpdate%(sw%, sh%)
        If self\open = False Then Return False

        BrokenRefs::pumpKeyboard(self)
        If self\open = False Then Return True

        // Dim background, draw centered modal
        LoomFill(0, 0, sw, sh, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)

        Local mx% = MouseX()
        Local my% = MouseY()
        Local clicked% = Loom_MouseClicked()

        Local modalX% = (sw - BROKENREFS_MODAL_W) / 2
        Local modalY% = (sh - BROKENREFS_MODAL_H) / 3

        LoomShadowCard(modalX, modalY, BROKENREFS_MODAL_W, BROKENREFS_MODAL_H)
        LoomFill(modalX, modalY, BROKENREFS_MODAL_W, BROKENREFS_MODAL_H, LOOM_STONE_850_R, LOOM_STONE_850_G, LOOM_STONE_850_B)
        LoomBorder(modalX, modalY, BROKENREFS_MODAL_W, BROKENREFS_MODAL_H, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
        LoomBorder(modalX + 1, modalY + 1, BROKENREFS_MODAL_W - 2, BROKENREFS_MODAL_H - 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        LoomFill(modalX, modalY, BROKENREFS_MODAL_W, 3, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)

        // Header in display font. Title broadened from "Broken References" to
        // "Issues" now that the modal surfaces multiple categories of
        // validation (broken refs / empty fields / playability gaps /
        // weapon config / spell config / orphan zones).
        Local headerTxt$ = "ISSUES  |  " + Str(self\entryCount)
        If self\entryCount >= BROKENREFS_MAX_ENTRIES Then headerTxt = headerTxt + "+ (capped)"
        If self\categoryFilter <> ""
            headerTxt = headerTxt + "  |  filter: " + self\categoryFilter
        EndIf
        LoomTheme_UseDisplay()
        LoomText(modalX + BROKENREFS_PAD, modalY + 6, headerTxt, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
        LoomTheme_UseBody()

        // Category filter chip row -- one chip per category we've
        // emitted. Click cycles between filter-this-category and
        // filter-clear (All). The header text always reflects the
        // active filter so designers see the scope at a glance.
        // drawCategoryChips returns the bottom Y of the (possibly
        // multi-row) chip strip; the list starts below it.
        Local chipsY% = modalY + BROKENREFS_HEADER_H - 4
        Local chipsBottom% = BrokenRefs::drawCategoryChips(self, modalX + BROKENREFS_PAD, chipsY, mx, my, clicked)

        // Footer position -- the list must stop above it.
        Local hy% = modalY + BROKENREFS_MODAL_H - BROKENREFS_HINT_H - 4

        // Entries list flows from below the chips to just above the footer.
        Local listY% = chipsBottom + 10
        Local listH% = (hy - 8) - listY
        If listH < BROKENREFS_ROW_H Then listH = BROKENREFS_ROW_H

        // Mouse-wheel scroll (row-indexed scrollOffset, same as arrow keys).
        Local wheel% = Loom_MouseWheel()
        If wheel <> 0
            self\scrollOffset = self\scrollOffset - wheel
            If self\scrollOffset < 0 Then self\scrollOffset = 0
            If self\scrollOffset >= self\entryCount Then self\scrollOffset = self\entryCount - 1
            If self\scrollOffset < 0 Then self\scrollOffset = 0
            Loom_ConsumeWheel()
        EndIf

        BrokenRefs::drawEntries(self, modalX, listY, listH, mx, my, clicked)
        LoomHRule(modalX + BROKENREFS_PAD, hy - 2, BROKENREFS_MODAL_W - BROKENREFS_PAD * 2, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
        LoomText(modalX + BROKENREFS_PAD, hy + 4, "Click a row to jump to the source  |  wheel / arrows scroll  |  Esc to close", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)

        // Deferred row-jump (set by drawOneEntry). Safe to closeModal now --
        // drawEntries' `For r = Each BrokenRef` loop has finished, so clearing
        // the pool here can't corrupt an in-flight iterator. Focus AFTER close.
        If self\pendingJump = True
            Local jk$ = self\pendingJumpKind
            Local jid% = self\pendingJumpRefID
            self\pendingJump = False
            BrokenRefs::closeModal(self)
            Threads::focus(self\threads, jk, jid)
            WriteLog(LoomLog, "BrokenRefs: jumped to " + jk + "#" + Str(jid))
            Return True
        EndIf

        If clicked = True
            If mx < modalX Or mx >= modalX + BROKENREFS_MODAL_W Or my < modalY Or my >= modalY + BROKENREFS_MODAL_H
                BrokenRefs::closeModal(self)
            EndIf
        EndIf

        Return True
    End Method


    Method drawEntries(modalX%, listY%, listH%, mx%, my%, clicked%)
        // listH is computed by the caller from the actual chip-strip
        // bottom so the list never overlaps the (possibly multi-row)
        // chips above or the footer below.
        Local rowsVisible% = listH / BROKENREFS_ROW_H
        If rowsVisible < 1 Then rowsVisible = 1
        Local rx% = modalX + BROKENREFS_PAD
        Local rw% = BROKENREFS_MODAL_W - BROKENREFS_PAD * 2

        If self\entryCount = 0
            LoomText(rx, listY + 12, "No issues. Project is clean.", LOOM_SUCCESS_R, LOOM_SUCCESS_G, LOOM_SUCCESS_B)
            Return
        EndIf

        Local skipped% = 0
        Local shown% = 0
        Local r.BrokenRef
        For r = Each BrokenRef
            // Apply category filter (if any). Entries not matching the
            // active filter are entirely invisible (no scroll cost too --
            // skipped doesn't increment).
            If self\categoryFilter <> "" And r\Category <> self\categoryFilter Then Continue
            If skipped < self\scrollOffset
                skipped = skipped + 1
            Else
                If shown >= rowsVisible Then Exit
                Local ry% = listY + shown * BROKENREFS_ROW_H
                BrokenRefs::drawOneEntry(self, r, rx, ry, rw, mx, my, clicked)
                shown = shown + 1
            EndIf
        Next

        // If filter is active and no rows shown, surface a hint.
        If shown = 0 And self\categoryFilter <> ""
            LoomText(rx, listY + 12, "No issues in category " + Chr(34) + self\categoryFilter + Chr(34) + ". Click filter chip again to clear.", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // drawCategoryChips -- one chip per BrokenRef category we've emitted
    // this rebuild. Click toggles filter (on first click sets, second
    // click clears). Each chip shows the category name + entry count.
    // Active filter highlighted in danger color.
    //
    // Categories surfaced in the order they're typically encountered.
    // Empty categories (count=0 because the project doesn't have that
    // issue type) are still rendered as "(0)" stone-grey so the chip
    // bar layout stays stable across sessions.
    // -------------------------------------------------------------------------
    Method drawCategoryChips%(chipsX%, chipsY%, mx%, my%, clicked%)
        // Unified chip list: index 0 = "all", 1..13 = the categories. Full
        // category names (matches the design); each chip is a pill with a
        // count badge. Rows are JUSTIFIED to the full strip width -- the
        // leftover horizontal space is distributed as equal inter-chip gaps
        // so every row except the last spans edge-to-edge (the reported
        // "filters don't fill the top row" fix).
        Local cats$[13]
        cats[0]  = "broken-ref"
        cats[1]  = "broken-script"
        cats[2]  = "missing-texture"
        cats[3]  = "missing-mesh"
        cats[4]  = "missing-sound"
        cats[5]  = "empty-field"
        cats[6]  = "playability"
        cats[7]  = "weapon-config"
        cats[8]  = "spell-config"
        cats[9]  = "orphan-zone"
        cats[10] = "orphan-script"
        cats[11] = "orphan-texture"
        cats[12] = "orphan-mesh"

        Local lbl$[13]
        Local cat$[13]
        Local cnt%[13]
        Local cw%[13]
        Local crow%[13]
        Local i%

        lbl[0] = "all"
        cat[0] = ""
        cnt[0] = self\entryCount
        For i = 1 To 13
            cat[i] = cats[i - 1]
            lbl[i] = cats[i - 1]
            cnt[i] = BrokenRefs::countCategory(self, cats[i - 1])
        Next

        Local ch% = 22
        Local padX% = 12
        Local badgeGap% = 6
        // Natural width of each chip: padding + label + gap + count badge + padding.
        For i = 0 To 13
            cw[i] = padX + StringWidth(lbl[i]) + badgeGap + (StringWidth(Str(cnt[i])) + 12) + padX
        Next

        Local baseGap% = 8
        Local availW%    = BROKENREFS_MODAL_W - BROKENREFS_PAD * 2
        Local availRight% = chipsX + availW

        // Greedy wrap into rows (record each chip's row index).
        Local penX% = chipsX
        Local rowN% = 0
        For i = 0 To 13
            If i > 0
                If penX + cw[i] > availRight
                    rowN = rowN + 1
                    penX = chipsX
                EndIf
            EndIf
            crow[i] = rowN
            penX = penX + cw[i] + baseGap
        Next
        Local lastRow% = rowN

        // Draw row by row. Method-scope accumulators (reassigned inside the
        // loops) per the Strict nested-block-reassignment rule.
        Local r2%
        Local n%
        Local sumW%
        Local gap%
        Local dx%
        Local dy%
        Local k%
        For r2 = 0 To lastRow
            n = 0
            sumW = 0
            For k = 0 To 13
                If crow[k] = r2
                    n = n + 1
                    sumW = sumW + cw[k]
                EndIf
            Next
            // Justify every row except the last to fill availW.
            gap = baseGap
            If r2 < lastRow
                If n > 1
                    gap = (availW - sumW) / (n - 1)
                    If gap < baseGap Then gap = baseGap
                EndIf
            EndIf
            dx = chipsX
            dy = chipsY + r2 * (ch + 5)
            For k = 0 To 13
                If crow[k] = r2
                    BrokenRefs::drawOneChip(self, lbl[k], cat[k], cnt[k], dx, dy, cw[k], ch, mx, my, clicked)
                    dx = dx + cw[k] + gap
                EndIf
            Next
        Next

        // Bottom Y of the chip strip. The caller starts the entries list
        // below this so the (possibly multi-row) chips never overlap it.
        Return chipsY + (lastRow + 1) * (ch + 5)
    End Method


    // drawOneChip -- one filter pill: rounded-look fill + border, the
    // category label, and a count badge. Handles hover/active styling and
    // click (toggle filter; the "all" chip -- catName="" -- clears it).
    Method drawOneChip(label$, catName$, count%, x%, y%, w%, h%, mx%, my%, clicked%)
        Local hov%    = (mx >= x And mx < x + w And my >= y And my < y + h)
        Local active% = (self\categoryFilter = catName)

        // Pill background + label drawn together per branch (avoids the
        // Strict nested-block Local-reassignment trap on a shared colour var):
        // active = brass, hover = arcane, idle = stone (dimmer + greyed label
        // when the category is empty so zero-count chips recede).
        If active = True
            LoomFill(x, y, w, h, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            LoomText(x + 12, y + 4, label, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        Else If hov = True
            LoomFill(x, y, w, h, LOOM_ARCANE_700_R, LOOM_ARCANE_700_G, LOOM_ARCANE_700_B)
            LoomText(x + 12, y + 4, label, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        Else If count = 0 And catName <> ""
            LoomFill(x, y, w, h, LOOM_STONE_800_R, LOOM_STONE_800_G, LOOM_STONE_800_B)
            LoomText(x + 12, y + 4, label, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
        Else
            LoomFill(x, y, w, h, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
            LoomText(x + 12, y + 4, label, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf
        LoomBorder(x, y, w, h, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)

        // Count badge -- darker inset rect with the number, right after the
        // label. Reads as a pill-on-pill like the design's count chips.
        Local bw% = StringWidth(Str(count)) + 12
        Local bx% = x + 12 + StringWidth(label) + 6
        Local by% = y + 3
        Local bh% = h - 6
        LoomFill(bx, by, bw, bh, LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B)
        LoomText(bx + bw / 2, by + 1, Str(count), LOOM_PARCHMENT_200_R, LOOM_PARCHMENT_200_G, LOOM_PARCHMENT_200_B, 1)

        // Click: "all" clears the filter; a category toggles (only if it has
        // entries -- empty categories aren't actionable).
        If hov = True And clicked = True
            If catName = ""
                self\categoryFilter = ""
                self\scrollOffset = 0
            Else If count > 0
                If active = True
                    self\categoryFilter = ""
                Else
                    self\categoryFilter = catName
                EndIf
                self\scrollOffset = 0
            EndIf
        EndIf
    End Method


    // countCategory -- O(entries) walk; cheap since BROKENREFS_MAX_ENTRIES
    // = 250. Could cache per-rebuild but the per-frame cost is trivial.
    Method countCategory%(catName$)
        Local n% = 0
        Local r.BrokenRef
        For r = Each BrokenRef
            If r\Category = catName Then n = n + 1
        Next
        Return n
    End Method


    Method drawOneEntry(r.BrokenRef, rx%, ry%, rw%, mx%, my%, clicked%)
        Local hovered% = (mx >= rx And mx < rx + rw And my >= ry And my < ry + BROKENREFS_ROW_H)
        If hovered = True
            LoomFill(rx, ry, rw, BROKENREFS_ROW_H, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
        EndIf

        // Severity left-rail (4px) -- red error / orange warning / blue info.
        // Defaults to red for back-compat entries that pre-date the
        // severity field (Severity = "" reads as error). Avoids reassigning
        // Method-scope Locals from inside nested If branches per the
        // Strict-mode trap; instead each branch fills the rail directly.
        If r\Severity = "warning"
            LoomFill(rx, ry, 4, BROKENREFS_ROW_H, LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
        Else If r\Severity = "info"
            LoomFill(rx, ry, 4, BROKENREFS_ROW_H, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else
            LoomFill(rx, ry, 4, BROKENREFS_ROW_H, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
        EndIf

        // Source: kind + label (shifted right past the rail)
        LoomText(rx + 12, ry + 4, r\SourceKind + "  " + r\SourceLabel, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

        // Field + bad value (colored by severity, same conditional)
        Local mid$ = r\FieldDesc + " = " + Chr(34) + BrokenRefs::truncate(self, r\BadValue, 20) + Chr(34)
        If r\Severity = "warning"
            LoomText(rx + 260, ry + 4, mid, LOOM_WARNING_R, LOOM_WARNING_G, LOOM_WARNING_B)
        Else If r\Severity = "info"
            LoomText(rx + 260, ry + 4, mid, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else
            LoomText(rx + 260, ry + 4, mid, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
        EndIf

        // Diagnosis on the right
        LoomText(rx + rw - StringWidth(r\Diagnosis) - 12, ry + 4, r\Diagnosis, LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)

        If hovered And clicked
            // Defer -- do NOT closeModal here (it Deletes the BrokenRef pool
            // we're mid-iterating in drawEntries). Record the target; the
            // outer renderAndUpdate closes + focuses after the loop.
            self\pendingJump = True
            self\pendingJumpKind = r\SourceKind
            self\pendingJumpRefID = r\SourceRefID
        EndIf
    End Method


    Method pumpKeyboard()
        If KeyHit(1)
            BrokenRefs::closeModal(self)
            Return
        EndIf
        If KeyHit(200) And self\scrollOffset > 0
            self\scrollOffset = self\scrollOffset - 1
        EndIf
        If KeyHit(208)
            self\scrollOffset = self\scrollOffset + 1
            If self\scrollOffset >= self\entryCount Then self\scrollOffset = self\entryCount - 1
            If self\scrollOffset < 0 Then self\scrollOffset = 0
        EndIf
    End Method


    Method truncate$(s$, maxLen%)
        If Len(s) <= maxLen Then Return s
        Return Left$(s, maxLen - 2) + ".."
    End Method
End Type
