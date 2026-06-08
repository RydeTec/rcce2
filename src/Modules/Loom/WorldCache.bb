Strict

// =============================================================================
// Loom/WorldCache.bb -- per-frame cache of "scan every entity" results
// =============================================================================
//
// PROBLEM
// Multiple surfaces walk the global type pools to derive aggregates:
//   - Ribbon::recomputeCache (every frame): broken-ref count + 6 entity totals
//   - BrokenRefs::rebuild (every open + when zone count changes):
//     enumerates each broken reference
//   - Atlas::countZones (every frame the Atlas is on screen): zone count
//   - Atlas::findZoneHandleByName (per portal during layout rebuild):
//     name -> handle lookup
//
// At small projects (~50-100 entities) all of this is invisible. At 5000+
// actors with 100 portals each, the Ribbon's per-frame nested-loop scan
// becomes a hot path: O(actors * animsets) for the M/F anim resolves,
// O(zones * portals * zones) for the portal target resolves. That's
// hundreds of thousands of iterations 60 times per second.
//
// SOLUTION
// One shared cache, invalidated lazily on mutation. Surfaces read the
// cache; mutation sites (Composer::commitEdit, Composer::toggleRow,
// Composer::discardKind, every EntityFactory_Create*, every
// EntityFactory_Delete*, Palette picker commit) call WorldCache_Invalidate
// after their write completes. The next read recomputes and caches; until
// then it serves the cached value.
//
// Architecture: Type with Methods (the cache is stateful), plus a free-
// function facade so mutation sites don't need an instance ref. Mirrors
// the Timeline / Recents recorder pattern (see ADR 005).
//
// Lifecycle: constructed by Loom.bb; Ribbon::recomputeCache delegates
// to WorldCache::ensureFresh(). On first call: dirty=True, recomputes.
// Subsequent calls return immediately until WorldCache_Invalidate flips
// dirty=True again.


Type WorldCache
    // Dirty bit -- True means cached values are stale and ensureFresh
    // must recompute. False means cached values match the global type
    // pool's current state.
    Field dirty%

    // Cached aggregates
    Field cachedBrokenRefs%
    Field cachedTotalActors%
    Field cachedTotalItems%
    Field cachedTotalSpells%
    Field cachedTotalZones%
    Field cachedTotalFactions%
    Field cachedTotalAnimSets%


    Method create.WorldCache()
        self\dirty = True
        self\cachedBrokenRefs = 0
        self\cachedTotalActors = 0
        self\cachedTotalItems = 0
        self\cachedTotalSpells = 0
        self\cachedTotalZones = 0
        self\cachedTotalFactions = 0
        self\cachedTotalAnimSets = 0
        Return self
    End Method


    // -------------------------------------------------------------------------
    // invalidate -- mark cache stale. Cheap no-op when already dirty.
    // -------------------------------------------------------------------------
    Method invalidate()
        self\dirty = True
    End Method


    // -------------------------------------------------------------------------
    // ensureFresh -- if dirty, walk every entity and update cached values;
    // clear dirty. If clean, return immediately. Idempotent within a frame.
    //
    // Counters mutate on self\* directly (not Method Locals) to dodge the
    // Strict-mode "reassign Method Local from inside nested For/If" trap.
    // -------------------------------------------------------------------------
    Method ensureFresh()
        If self\dirty = False Then Return

        self\cachedBrokenRefs = 0
        self\cachedTotalActors = 0
        self\cachedTotalItems = 0
        self\cachedTotalSpells = 0
        self\cachedTotalZones = 0
        self\cachedTotalFactions = 0
        self\cachedTotalAnimSets = 0

        // Actor totals + broken-ref checks
        For Ac.Actor = Each Actor
            self\cachedTotalActors = self\cachedTotalActors + 1
            If Ac\DefaultFaction < 0 Or Ac\DefaultFaction > 99
                self\cachedBrokenRefs = self\cachedBrokenRefs + 1
            Else If Ac\DefaultFaction > 0 And FactionNames$(Ac\DefaultFaction) = ""
                self\cachedBrokenRefs = self\cachedBrokenRefs + 1
            EndIf
            If Ac\MAnimationSet <> 0
                If WorldCache::animSetExists(self, Ac\MAnimationSet) = False
                    self\cachedBrokenRefs = self\cachedBrokenRefs + 1
                EndIf
            EndIf
            If Ac\FAnimationSet <> 0
                If WorldCache::animSetExists(self, Ac\FAnimationSet) = False
                    self\cachedBrokenRefs = self\cachedBrokenRefs + 1
                EndIf
            EndIf
        Next

        For It.Item = Each Item
            self\cachedTotalItems = self\cachedTotalItems + 1
        Next

        For Sp.Spell = Each Spell
            self\cachedTotalSpells = self\cachedTotalSpells + 1
        Next

        For Ar.Area = Each Area
            self\cachedTotalZones = self\cachedTotalZones + 1
            Local portalIdx% = 0
            For portalIdx = 0 To 99
                If Ar\PortalLinkArea$[portalIdx] <> ""
                    If WorldCache::zoneExists(self, Ar\PortalLinkArea$[portalIdx]) = False
                        self\cachedBrokenRefs = self\cachedBrokenRefs + 1
                    EndIf
                EndIf
            Next
        Next

        Local fi% = 0
        For fi = 0 To 99
            If FactionNames$(fi) <> ""
                self\cachedTotalFactions = self\cachedTotalFactions + 1
            EndIf
        Next

        For As.AnimSet = Each AnimSet
            self\cachedTotalAnimSets = self\cachedTotalAnimSets + 1
        Next

        self\dirty = False
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
    // Accessors -- callers ensureFresh first, then read. Per-attribute
    // getters rather than exposing Fields directly so future cache
    // implementations (e.g. partial invalidation) can change shape.
    // -------------------------------------------------------------------------
    Method brokenRefs%()
        WorldCache::ensureFresh(self)
        Return self\cachedBrokenRefs
    End Method

    Method totalActors%()
        WorldCache::ensureFresh(self)
        Return self\cachedTotalActors
    End Method

    Method totalItems%()
        WorldCache::ensureFresh(self)
        Return self\cachedTotalItems
    End Method

    Method totalSpells%()
        WorldCache::ensureFresh(self)
        Return self\cachedTotalSpells
    End Method

    Method totalZones%()
        WorldCache::ensureFresh(self)
        Return self\cachedTotalZones
    End Method

    Method totalFactions%()
        WorldCache::ensureFresh(self)
        Return self\cachedTotalFactions
    End Method

    Method totalAnimSets%()
        WorldCache::ensureFresh(self)
        Return self\cachedTotalAnimSets
    End Method
End Type


// =============================================================================
// Module-level facade. Same shape as LoomTimeline / LoomRecents (ADR 005).
// Mutation sites call WorldCache_Invalidate without needing the instance.
// =============================================================================
Global LoomWorldCache.WorldCache = Null


Function WorldCache_Invalidate()
    If LoomWorldCache = Null Then Return
    WorldCache::invalidate(LoomWorldCache)
End Function
