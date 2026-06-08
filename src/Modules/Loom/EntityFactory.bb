Strict

// =============================================================================
// Loom/EntityFactory.bb -- create new entities from the Loom UI
// =============================================================================
//
// Wraps GUE's existing Create* constructors so the Browser's "+ New" button
// can spawn a fresh actor / item / spell / zone / faction / anim set with
// sensible defaults, focus it for editing, and mark its kind dirty so the
// Save button appears.
//
// Why a free-function module (and not a Type with Methods like the other
// Loom UI modules)? EntityFactory holds no state -- it's pure dispatch
// from (kind, threads*) -> "create + focus + dirty". Stateless helpers are
// the project's canonical shape for "free functions are fine" per the
// BlitzForge skill (see Theme.bb for the established pattern).
//
// Constructors used (from GUE's data modules):
//   actor    -> CreateActor.Actor()       (Actors.bb:541)
//   item     -> CreateItem.Item()         (Items.bb:193)
//   spell    -> CreateSpell.Spell()       (Spells.bb:24)
//   zone     -> ServerCreateArea.Area()   (ServerAreas.bb:107) + UniqueZoneName
//   animset  -> CreateAnimSet()           (Animations.bb:69)   (returns slot ID)
//   faction  -> first empty FactionNames$ slot + SetFactionName (Actors.bb)
//
// Per-kind dirty-flag globals are the same set Composer.bb writes to; the
// Loom.bb declarations re-export the SaveX globals so we can flip them
// here without an extra getter/setter pair.


// =============================================================================
// EntityFactory_Create -- dispatch on kind, return True if an entity was
// created. Out-params: focusKind / focusID get the new entity's identity so
// the caller can Threads::focus on it.
//
// BlitzForge doesn't have true out-params; the caller passes a Threads
// pointer and we call Threads::focus directly. That's also the right
// behavior -- creation should always focus the new entity for editing.
// =============================================================================
Function EntityFactory_Create%(kind$, threads.Threads)
    If kind = "actor"   Then Return EntityFactory_CreateActor(threads)
    If kind = "item"    Then Return EntityFactory_CreateItem(threads)
    If kind = "spell"   Then Return EntityFactory_CreateSpell(threads)
    If kind = "zone"    Then Return EntityFactory_CreateZone(threads)
    If kind = "faction" Then Return EntityFactory_CreateFaction(threads)
    If kind = "animset" Then Return EntityFactory_CreateAnimSet(threads)

    WriteLog(LoomLog, "EntityFactory: unknown kind '" + kind + "'")
    Return False
End Function


// -----------------------------------------------------------------------------
// Per-kind creators. Each returns True on success, False on cap (e.g. no
// free slot in a fixed-size array). On success: focuses the new entity,
// flips the kind's *Saved global to False (dirty), logs the new ID.
// -----------------------------------------------------------------------------

Function EntityFactory_CreateActor%(threads.Threads)
    Local A.Actor = CreateActor()
    If A = Null
        WriteLog(LoomLog, "EntityFactory: CreateActor returned Null (ActorList full?)")
        Return False
    EndIf
    A\Race$ = "New"
    A\Class$ = "Actor"
    A\XPMultiplier = 1
    A\Aggressiveness = 0
    A\Genders = 0
    Threads::focus(threads, "actor", A\ID)
    ActorsSaved = False
    Timeline_RecordCreate("actor", A\ID, A\Race$ + " [" + A\Class$ + "]")
    WorldCache_Invalidate()
    Toast_Show("Created actor " + A\Race$ + " [" + A\Class$ + "]", "success")
    WriteLog(LoomLog, "EntityFactory: created actor #" + Str(A\ID))
    Return True
End Function


Function EntityFactory_CreateItem%(threads.Threads)
    Local I.Item = CreateItem()
    If I = Null
        WriteLog(LoomLog, "EntityFactory: CreateItem returned Null (ItemList full?)")
        Return False
    EndIf
    I\Name$ = "New Item"
    Threads::focus(threads, "item", I\ID)
    ItemsSaved = False
    Timeline_RecordCreate("item", I\ID, I\Name$)
    WorldCache_Invalidate()
    Toast_Show("Created item " + I\Name$, "success")
    WriteLog(LoomLog, "EntityFactory: created item #" + Str(I\ID))
    Return True
End Function


Function EntityFactory_CreateSpell%(threads.Threads)
    Local S.Spell = CreateSpell()
    If S = Null
        WriteLog(LoomLog, "EntityFactory: CreateSpell returned Null (SpellsList full?)")
        Return False
    EndIf
    // CreateSpell defaults Name to "New ability" -- keep as-is for muscle-
    // memory parity with GUE's behavior.
    Threads::focus(threads, "spell", S\ID)
    SpellsSaved = False
    Timeline_RecordCreate("spell", S\ID, S\Name$)
    WorldCache_Invalidate()
    Toast_Show("Created spell " + S\Name$, "success")
    WriteLog(LoomLog, "EntityFactory: created spell #" + Str(S\ID))
    Return True
End Function


Function EntityFactory_CreateZone%(threads.Threads)
    Local A.Area = ServerCreateArea()
    If A = Null
        WriteLog(LoomLog, "EntityFactory: ServerCreateArea returned Null")
        Return False
    EndIf
    A\Name$ = EntityFactory_UniqueZoneName$("New Zone")
    Threads::focus(threads, "zone", Handle(A))
    ZoneSaved = False
    Timeline_RecordCreate("zone", Handle(A), A\Name$)
    WorldCache_Invalidate()
    Toast_Show("Created zone " + A\Name$, "success")
    WriteLog(LoomLog, "EntityFactory: created zone " + A\Name$)
    Return True
End Function


Function EntityFactory_CreateFaction%(threads.Threads)
    // Find first empty FactionNames$ slot. The array is Dim'd at 99 so
    // indices 0..99 = 100 slots total.
    Local slot% = -1
    Local i% = 0
    For i = 0 To 99
        If FactionNames$(i) = ""
            slot = i
            Exit
        EndIf
    Next
    If slot = -1
        WriteLog(LoomLog, "EntityFactory: faction roster full (100 slots used)")
        Return False
    EndIf

    // SetFactionName lives in Actors.bb (non-Strict) so this Strict file
    // can write the global without hitting the Dim-inside-Method trap.
    SetFactionName(slot, "New Faction")
    Threads::focus(threads, "faction", slot)
    FactionsSaved = False
    Timeline_RecordCreate("faction", slot, "New Faction")
    WorldCache_Invalidate()
    Toast_Show("Created faction (slot " + Str(slot) + ")", "success")
    WriteLog(LoomLog, "EntityFactory: created faction slot " + Str(slot))
    Return True
End Function


Function EntityFactory_CreateAnimSet%(threads.Threads)
    // CreateAnimSet returns the assigned ID (or -1 on cap). The instance
    // is already inserted into AnimList by the constructor.
    Local newID% = CreateAnimSet()
    If newID = -1
        WriteLog(LoomLog, "EntityFactory: CreateAnimSet returned -1 (AnimList full)")
        Return False
    EndIf
    Threads::focus(threads, "animset", newID)
    AnimsSaved = False
    Timeline_RecordCreate("animset", newID, "New Animation Set")
    WorldCache_Invalidate()
    Toast_Show("Created animation set", "success")
    WriteLog(LoomLog, "EntityFactory: created animset #" + Str(newID))
    Return True
End Function


// -----------------------------------------------------------------------------
// EntityFactory_UniqueZoneName -- return a name with " 2" / " 3" / ...
// appended until no existing zone matches. ServerSaveArea uses Name$ as the
// filename, so a duplicate would silently overwrite an existing zone file.
//
// The dedup logic itself lives in NameUtil.bb (pure + unit-tested); here we
// just build the set of existing zone names from the live Area list and
// hand it to Loom_NextUniqueName.
// -----------------------------------------------------------------------------
Function EntityFactory_UniqueZoneName$(base$)
    Local setStr$ = ""
    For A.Area = Each Area
        setStr = Loom_AddNameToSet$(setStr, A\Name$)
    Next
    Return Loom_NextUniqueName$(base, setStr)
End Function


Function EntityFactory_ZoneNameExists%(name$)
    Local upr$ = Upper$(name)
    For A.Area = Each Area
        If Upper$(A\Name$) = upr Then Return True
    Next
    Return False
End Function


// =============================================================================
// EntityFactory_Duplicate -- dispatch on kind, allocate a copy of the
// source entity, focus the duplicate so the user can immediately edit.
// Mirrors EntityFactory_Create's contract: True on success, False on cap.
//
// Per-kind copy helpers live in the data modules (non-Strict so they can
// directly assign Dim'd globals + Type Fields). Zone duplication is
// deferred -- copying every portal / spawn / trigger / waypoint is non-
// trivial and the underlying ServerCreateArea + per-field copy path is
// its own sub-iteration.
// =============================================================================
Function EntityFactory_Duplicate%(kind$, refID%, threads.Threads)
    If kind = "actor"   Then Return EntityFactory_DuplicateActor(refID, threads)
    If kind = "item"    Then Return EntityFactory_DuplicateItem(refID, threads)
    If kind = "spell"   Then Return EntityFactory_DuplicateSpell(refID, threads)
    If kind = "animset" Then Return EntityFactory_DuplicateAnimSet(refID, threads)
    If kind = "faction" Then Return EntityFactory_DuplicateFaction(refID, threads)
    If kind = "zone"   Then Return EntityFactory_DuplicateZone(refID, threads)
    WriteLog(LoomLog, "EntityFactory: duplicate unknown kind '" + kind + "'")
    Return False
End Function


// -----------------------------------------------------------------------------
// EntityFactory_DuplicateZone -- clone an Area via DuplicateAreaTemplate
// (which copies every portal / spawn / trigger / waypoint), then
// re-uniqueify the name so it doesn't collide with the source's .dat
// at save time. ServerSaveArea uses A\Name$ as the filename, so a
// duplicate with the same name as the source would silently overwrite
// the source's on-disk state on the next save.
// -----------------------------------------------------------------------------
Function EntityFactory_DuplicateZone%(srcHandle%, threads.Threads)
    Local Src.Area = Object.Area(srcHandle)
    If Src = Null
        Toast_Show("Duplicate failed (stale zone handle)", "danger")
        Return False
    EndIf

    Local Dst.Area = DuplicateAreaTemplate(Src)
    If Dst = Null
        Toast_Show("Duplicate failed (ServerCreateArea returned Null)", "danger")
        Return False
    EndIf

    // DuplicateAreaTemplate set Dst\Name$ to src.Name + " (copy)". If
    // that collides with an existing zone (e.g. duplicating something
    // already named "Foo (copy)"), append a numeric suffix.
    Dst\Name$ = EntityFactory_UniqueZoneName$(Dst\Name$)

    Threads::focus(threads, "zone", Handle(Dst))
    ZoneSaved = False
    WorldCache_Invalidate()
    Timeline_RecordCreate("zone", Handle(Dst), Dst\Name$)
    Toast_Show("Duplicated zone " + Dst\Name$, "success")
    WriteLog(LoomLog, "EntityFactory: duplicated zone " + Src\Name$ + " -> " + Dst\Name$)
    Return True
End Function


Function EntityFactory_DuplicateActor%(srcID%, threads.Threads)
    Local newID% = DuplicateActorTemplate(srcID)
    If newID = -1
        Toast_Show("Duplicate failed (ActorList full or stale)", "danger")
        Return False
    EndIf
    Threads::focus(threads, "actor", newID)
    ActorsSaved = False
    WorldCache_Invalidate()
    Local newA.Actor = ActorList(newID)
    Local label$ = ""
    If newA <> Null Then label = newA\Race$ + " [" + newA\Class$ + "]"
    Timeline_RecordCreate("actor", newID, label)
    Toast_Show("Duplicated actor", "success")
    WriteLog(LoomLog, "EntityFactory: duplicated actor #" + Str(srcID) + " -> #" + Str(newID))
    Return True
End Function


Function EntityFactory_DuplicateItem%(srcID%, threads.Threads)
    Local newID% = DuplicateItemTemplate(srcID)
    If newID = -1
        Toast_Show("Duplicate failed (ItemList full or stale)", "danger")
        Return False
    EndIf
    Threads::focus(threads, "item", newID)
    ItemsSaved = False
    WorldCache_Invalidate()
    Local newI.Item = ItemList(newID)
    Local label$ = ""
    If newI <> Null Then label = newI\Name$
    Timeline_RecordCreate("item", newID, label)
    Toast_Show("Duplicated item", "success")
    WriteLog(LoomLog, "EntityFactory: duplicated item #" + Str(srcID) + " -> #" + Str(newID))
    Return True
End Function


Function EntityFactory_DuplicateSpell%(srcID%, threads.Threads)
    Local newID% = DuplicateSpellTemplate(srcID)
    If newID = -1
        Toast_Show("Duplicate failed (SpellsList full or stale)", "danger")
        Return False
    EndIf
    Threads::focus(threads, "spell", newID)
    SpellsSaved = False
    WorldCache_Invalidate()
    Local newS.Spell = SpellsList(newID)
    Local label$ = ""
    If newS <> Null Then label = newS\Name$
    Timeline_RecordCreate("spell", newID, label)
    Toast_Show("Duplicated spell", "success")
    WriteLog(LoomLog, "EntityFactory: duplicated spell #" + Str(srcID) + " -> #" + Str(newID))
    Return True
End Function


Function EntityFactory_DuplicateAnimSet%(srcID%, threads.Threads)
    Local newID% = DuplicateAnimSetTemplate(srcID)
    If newID = -1
        Toast_Show("Duplicate failed (AnimList full or stale)", "danger")
        Return False
    EndIf
    Threads::focus(threads, "animset", newID)
    AnimsSaved = False
    WorldCache_Invalidate()
    Timeline_RecordCreate("animset", newID, "Duplicated animation set")
    Toast_Show("Duplicated animation set", "success")
    WriteLog(LoomLog, "EntityFactory: duplicated animset #" + Str(srcID) + " -> #" + Str(newID))
    Return True
End Function


Function EntityFactory_DuplicateFaction%(srcID%, threads.Threads)
    If srcID < 0 Or srcID > 99 Then Return False
    If FactionNames$(srcID) = "" Then Return False

    // Find first empty slot
    Local slot% = -1
    Local i% = 0
    For i = 0 To 99
        If FactionNames$(i) = ""
            slot = i
            Exit
        EndIf
    Next
    If slot = -1
        Toast_Show("Duplicate failed (faction roster full)", "danger")
        Return False
    EndIf

    Local newName$ = FactionNames$(srcID) + " (copy)"
    SetFactionName(slot, newName)
    Threads::focus(threads, "faction", slot)
    FactionsSaved = False
    WorldCache_Invalidate()
    Timeline_RecordCreate("faction", slot, newName)
    Toast_Show("Duplicated faction", "success")
    WriteLog(LoomLog, "EntityFactory: duplicated faction slot " + Str(srcID) + " -> slot " + Str(slot))
    Return True
End Function


// =============================================================================
// EntityFactory_Delete -- dispatch on kind, free the entity from in-memory
// state, mark the kind dirty, clear focus. Returns True on success.
//
// The delete-from-disk is INDEPENDENT of the in-memory free: the bulk
// serializers (SaveActors / SaveItems / ...) write the full list every
// time, so a freed slot stops getting written on the next Save. Only zones
// need an explicit file delete since each zone has its own .dat.
//
// Reference cleanup: stale references (an Actor pointing at a deleted
// Faction, a Zone portal pointing at a deleted Zone) become broken-ref
// chips in the Composer (red border, "(broken ...)" text). The Validation
// Ribbon surfaces the total broken-ref count so dangling references stay
// visible without forcing a sweep at delete-time -- some deletions are
// intentional and the cleanup is a separate user decision.
// =============================================================================
Function EntityFactory_Delete%(kind$, refID%, threads.Threads)
    If kind = "actor"   Then Return EntityFactory_DeleteActor(refID, threads)
    If kind = "item"    Then Return EntityFactory_DeleteItem(refID, threads)
    If kind = "spell"   Then Return EntityFactory_DeleteSpell(refID, threads)
    If kind = "zone"    Then Return EntityFactory_DeleteZone(refID, threads)
    If kind = "faction" Then Return EntityFactory_DeleteFaction(refID, threads)
    If kind = "animset" Then Return EntityFactory_DeleteAnimSet(refID, threads)
    WriteLog(LoomLog, "EntityFactory: delete unknown kind '" + kind + "'")
    Return False
End Function


Function EntityFactory_DeleteActor%(refID%, threads.Threads)
    Local label$ = Threads::lookupName(threads, "actor", refID)
    If DeleteActorTemplate(refID) = False
        WriteLog(LoomLog, "EntityFactory: delete actor #" + Str(refID) + " -- not found")
        Return False
    EndIf
    ActorsSaved = False
    Timeline_RecordDelete("actor", refID, label)
    WorldCache_Invalidate()
    Toast_Show("Deleted actor " + label, "danger")
    Threads::focus(threads, "", 0)
    Threads::clearStack(threads)
    WriteLog(LoomLog, "EntityFactory: deleted actor #" + Str(refID))
    Return True
End Function


Function EntityFactory_DeleteItem%(refID%, threads.Threads)
    Local label$ = Threads::lookupName(threads, "item", refID)
    If DeleteItemTemplate(refID) = False
        WriteLog(LoomLog, "EntityFactory: delete item #" + Str(refID) + " -- not found")
        Return False
    EndIf
    ItemsSaved = False
    Timeline_RecordDelete("item", refID, label)
    WorldCache_Invalidate()
    Toast_Show("Deleted item " + label, "danger")
    Threads::focus(threads, "", 0)
    Threads::clearStack(threads)
    WriteLog(LoomLog, "EntityFactory: deleted item #" + Str(refID))
    Return True
End Function


Function EntityFactory_DeleteSpell%(refID%, threads.Threads)
    Local label$ = Threads::lookupName(threads, "spell", refID)
    If DeleteSpellTemplate(refID) = False
        WriteLog(LoomLog, "EntityFactory: delete spell #" + Str(refID) + " -- not found")
        Return False
    EndIf
    SpellsSaved = False
    Timeline_RecordDelete("spell", refID, label)
    WorldCache_Invalidate()
    Toast_Show("Deleted spell " + label, "danger")
    Threads::focus(threads, "", 0)
    Threads::clearStack(threads)
    WriteLog(LoomLog, "EntityFactory: deleted spell #" + Str(refID))
    Return True
End Function


Function EntityFactory_DeleteAnimSet%(refID%, threads.Threads)
    Local label$ = Threads::lookupName(threads, "animset", refID)
    If DeleteAnimSetTemplate(refID) = False
        WriteLog(LoomLog, "EntityFactory: delete animset #" + Str(refID) + " -- not found")
        Return False
    EndIf
    AnimsSaved = False
    Timeline_RecordDelete("animset", refID, label)
    WorldCache_Invalidate()
    Toast_Show("Deleted animation set " + label, "danger")
    Threads::focus(threads, "", 0)
    Threads::clearStack(threads)
    WriteLog(LoomLog, "EntityFactory: deleted animset #" + Str(refID))
    Return True
End Function


Function EntityFactory_DeleteFaction%(refID%, threads.Threads)
    If refID < 0 Or refID > 99 Then Return False
    If FactionNames$(refID) = "" Then Return False
    Local label$ = FactionNames$(refID)
    SetFactionName(refID, "")     // empty string = vacant slot per LoadFactions semantics
    FactionsSaved = False
    Timeline_RecordDelete("faction", refID, label)
    WorldCache_Invalidate()
    Toast_Show("Deleted faction " + label, "danger")
    Threads::focus(threads, "", 0)
    Threads::clearStack(threads)
    WriteLog(LoomLog, "EntityFactory: deleted faction slot " + Str(refID))
    Return True
End Function


Function EntityFactory_DeleteZone%(refID%, threads.Threads)
    Local A.Area = Object.Area(refID)
    If A = Null
        WriteLog(LoomLog, "EntityFactory: delete zone -- stale handle")
        Return False
    EndIf

    // Delete the on-disk .dat first (the in-memory Free will null the
    // handle and we can't get the Name$ after that).
    Local zoneName$ = A\Name$
    Local datPath$ = "Data\Server Data\Areas\" + zoneName + ".dat"
    If FileType(datPath) = 1
        DeleteFile datPath
        WriteLog(LoomLog, "EntityFactory: deleted zone .dat at " + datPath)
    EndIf

    ServerUnloadArea(A)            // frees the Area + per-area ServerWater chain
    ZoneSaved = True               // no other-zone changes pending purely from this op
    Timeline_RecordDelete("zone", refID, zoneName)
    WorldCache_Invalidate()
    Toast_Show("Deleted zone " + zoneName, "danger")
    Threads::focus(threads, "", 0)
    Threads::clearStack(threads)
    WriteLog(LoomLog, "EntityFactory: deleted zone " + zoneName)
    Return True
End Function
