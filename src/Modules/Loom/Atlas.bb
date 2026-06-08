Strict

// =============================================================================
// Loom/Atlas.bb -- spatial world atlas (zones as nodes, portals as edges)
// =============================================================================
//
// The design's #3 signature surface (README.md): "World atlas - spatial
// overview of every zone, with portals drawn as lines between them."
//
// rcce2 doesn't store world positions for zones (they're a flat list), so
// the atlas layout is DERIVED from the portal-link graph topology via a
// force-directed layout (Fruchterman-Reingold-style spring model).
// Position is recomputed on entry / when nodes are added or removed (cheap
// at our scale -- a few hundred zones tops); cached between frames so
// re-rendering at 60fps doesn't re-solve the graph every frame.
//
// Layout algorithm (simplified FR):
//   For each iteration (up to ATLAS_ITERATIONS, with cooling):
//     - REPULSION: every pair of nodes pushes apart with force k*k/d
//     - ATTRACTION: every edge pulls its endpoints together with d*d/k
//     - Apply accumulated force, clamped to current temperature
//     - Decay temperature
//   Center + normalize to fit viewport.
//
// Activation: Browser exposes a "Card | Atlas" toggle on the Zones tab
// only. When Atlas mode is on, the browser skips its zone card grid and
// asks Atlas::renderAndUpdate to paint the viewport instead.
//
// Architecture: Type with Methods (Atlas owns layout state). Holds a
// Threads reference so node clicks dispatch focus changes consistently
// with the rest of Loom.


// Layout constants
Const ATLAS_ITERATIONS    = 240
Const ATLAS_NODE_R        = 22
Const ATLAS_NODE_PAD      = 6
Const ATLAS_INITIAL_TEMP# = 80.0
Const ATLAS_MIN_TEMP#     = 0.5
Const ATLAS_TEMP_DECAY#   = 0.97
Const ATLAS_VIEWPORT_PAD  = 60


// -----------------------------------------------------------------------------
// AtlasNode -- one zone node. Position + per-iteration force accumulator.
// Allocated by rebuildLayout, freed by clearNodes. Manual lifecycle (no
// EnableGC in Loom modules).
// -----------------------------------------------------------------------------
Type AtlasNode
    Field ZoneHandle%   // Handle(Area)
    Field Label$        // cached A\Name$ for display
    Field X#, Y#        // current world-space position
    Field DX#, DY#      // accumulated displacement this iteration
    Field SpawnCount%   // total defined SpawnActor slots; node size scales
    Field IssueCount%   // # of issues affecting this zone (computed at build)
    Field Outdoors%     // tint hint -- leafy fill for outdoors, stone for indoor
    Field Manual%       // True if user dragged this node; force layout skips it
End Type


// -----------------------------------------------------------------------------
// AtlasEdge -- one directed portal link. Allocated alongside nodes; freed
// alongside nodes.
// -----------------------------------------------------------------------------
Type AtlasEdge
    Field FromHandle%
    Field ToHandle%
End Type


// =============================================================================
// Atlas -- spatial zone-graph view.
// =============================================================================
Type Atlas
    Field threads.Threads

    // Layout state. nodeCount lets us know when zones were added/removed
    // and force a rebuild. minX/maxX/minY/maxY are the layout bounding box
    // computed by recenterLayout; render scales these to the viewport rect.
    Field nodeCount%
    Field minX#, minY#, maxX#, maxY#
    Field temperature#

    // Per-iteration accumulators -- kept as Fields rather than Method
    // Locals because BlitzForge Strict rejects re-assigning a Method
    // Local from inside nested For/If blocks.
    Field iterTemp#

    // Manual-drag state. draggedNode is set when LMB went down inside
    // a node; cleared on LMB release. dragOffsetX/Y is the offset from
    // node center to mouse point at press time so the drag doesn't
    // snap to mouse center.
    Field draggedNode.AtlasNode
    Field dragOffsetX%, dragOffsetY%
    Field dragMoved%      // True if mouse moved during drag; gates the
                          //   save-on-release path so a click-without-drag
                          //   doesn't churn the layout file
    Field lmbPrevDown%    // edge detector for LMB press vs held

    // Viewport pan/zoom state. zoom is a multiplier on the bbox-normalized
    // coordinate (1.0 = fit-all). panX/Y is an additive offset in
    // normalized space (0,0 = centered fit). Mouse wheel zooms about
    // cursor; MMB-drag pans.
    Field viewZoom#
    Field viewPanX#, viewPanY#
    Field panning%
    Field panLastMX%, panLastMY%


    Method create.Atlas(threads.Threads)
        self\threads = threads
        self\nodeCount = 0
        self\minX# = 0.0 : self\minY# = 0.0 : self\maxX# = 1.0 : self\maxY# = 1.0
        self\temperature# = ATLAS_INITIAL_TEMP#
        self\draggedNode = Null
        self\dragMoved = False
        self\lmbPrevDown = False
        self\viewZoom# = 1.0
        self\viewPanX# = 0.0
        self\viewPanY# = 0.0
        self\panning = False
        Return self
    End Method


    // -------------------------------------------------------------------------
    // renderAndUpdate -- per-frame paint + hit-test for the atlas viewport.
    // viewportX/Y/W/H is the rect Browser hands us (i.e. the area BELOW the
    // ribbon/brand/tab/filter strips and ABOVE the bottom footer).
    //
    // If the node pool is empty (first paint, or zones were added/removed
    // and we haven't rebuilt yet), rebuild the layout before drawing.
    // Returns True if a node was clicked this frame (so Browser knows a
    // focus change happened).
    // -------------------------------------------------------------------------
    Method renderAndUpdate%(viewportX%, viewportY%, viewportW%, viewportH%)
        // Detect zone-count change -- a delete or create would invalidate
        // the cached node pool. Cheap O(zones) walk per frame; the layout
        // rebuild itself is the expensive part and only fires on change.
        Local zonesNow% = Atlas::countZones(self)
        If zonesNow <> self\nodeCount
            Atlas::rebuildLayout(self)
        EndIf

        // Refresh per-node IssueCount from the current BrokenRef pool.
        // O(nodes + entries) per frame -- both capped well below 1000.
        // Cheap and lets the issue badges track Issues modal state live
        // without an explicit invalidation hook.
        Atlas::refreshIssueCounts(self)

        // Background -- darker tint than the browser to read as a distinct
        // surface, with a brass border around the viewport rect.
        LoomFill(viewportX, viewportY, viewportW, viewportH, LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B)
        LoomBorder(viewportX, viewportY, viewportW, viewportH, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)

        If self\nodeCount = 0
            LoomTextCentered(viewportX + viewportW / 2, viewportY + viewportH / 2, "No zones yet -- click + New Zone to add one.", LOOM_STONE_300_R, LOOM_STONE_300_G, LOOM_STONE_300_B)
            Return False
        EndIf

        // Title strip
        // Format zoom to one decimal -- "1.5x" reads better than "1.5000000x".
        Local zoomPct% = Int(self\viewZoom# * 100.0)
        LoomText(viewportX + 12, viewportY + 8, "ATLAS  |  " + Str(self\nodeCount) + " zones  |  " + Str(Atlas::countManual(self)) + " pinned  |  zoom " + Str(zoomPct) + "%", LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

        Local mx% = MouseX()
        Local my% = MouseY()
        Local clicked% = Loom_MouseClicked()

        // Viewport pan + zoom -- only when the cursor is in the atlas
        // viewport rect (so wheel/MMB don't fire when the cursor's
        // over the composer or browser nav).
        Local inViewport% = (mx >= viewportX And mx < viewportX + viewportW And my >= viewportY + 28 And my < viewportY + viewportH)
        If inViewport = True
            // Mouse wheel zoom. MouseZ is frame-cached via Loom_MouseWheel.
            Local wheel% = Loom_MouseWheel()
            If wheel <> 0
                // Each tick = 10% zoom. Clamp 0.25..4.0 so designers
                // can't lose the graph or zoom to oblivion.
                self\viewZoom# = self\viewZoom# * (1.0 + Float(wheel) * 0.1)
                If self\viewZoom# < 0.25 Then self\viewZoom# = 0.25
                If self\viewZoom# > 4.0  Then self\viewZoom# = 4.0
                // Atlas is browser-pane so doesn't conflict with the
                // composer scroll, but consuming anyway keeps the
                // facade contract consistent.
                Loom_ConsumeWheel()
            EndIf

            // MMB pan -- edge-detect: MouseDown(3) press initiates,
            // hold updates pan, release ends. Delta is in screen-px,
            // converted to normalized space via viewportW/H.
            Local mmbNow% = MouseDown(3)
            If mmbNow = True And self\panning = False
                self\panning = True
                self\panLastMX = mx
                self\panLastMY = my
            EndIf
            If mmbNow = True And self\panning = True
                Local dx% = mx - self\panLastMX
                Local dy% = my - self\panLastMY
                If viewportW - ATLAS_VIEWPORT_PAD * 2 > 0
                    self\viewPanX# = self\viewPanX# + Float(dx) / Float(viewportW - ATLAS_VIEWPORT_PAD * 2)
                EndIf
                If viewportH - ATLAS_VIEWPORT_PAD * 2 > 0
                    self\viewPanY# = self\viewPanY# + Float(dy) / Float(viewportH - ATLAS_VIEWPORT_PAD * 2)
                EndIf
                self\panLastMX = mx
                self\panLastMY = my
            EndIf
            If mmbNow = False And self\panning = True
                self\panning = False
            EndIf
        EndIf

        // "Reset Positions" button in the title strip's right edge.
        // Clears every node's Manual flag + triggers a fresh force-
        // directed layout. Saves the new (un-pinned) state so the
        // next session also boots without manual positions.
        Local rstBtnW% = 110
        Local rstBtnH% = 18
        Local rstBtnX% = viewportX + viewportW - rstBtnW - 12
        Local rstBtnY% = viewportY + 5
        Local rstHover% = (mx >= rstBtnX And mx < rstBtnX + rstBtnW And my >= rstBtnY And my < rstBtnY + rstBtnH)
        If rstHover = True
            LoomFill(rstBtnX, rstBtnY, rstBtnW, rstBtnH, LOOM_ARCANE_700_R, LOOM_ARCANE_700_G, LOOM_ARCANE_700_B)
            LoomBorder(rstBtnX, rstBtnY, rstBtnW, rstBtnH, LOOM_ARCANE_500_R, LOOM_ARCANE_500_G, LOOM_ARCANE_500_B)
        Else
            LoomFill(rstBtnX, rstBtnY, rstBtnW, rstBtnH, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
            LoomBorder(rstBtnX, rstBtnY, rstBtnW, rstBtnH, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
        EndIf
        LoomText(rstBtnX + 6, rstBtnY + 2, "reset positions", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
        If rstHover = True And clicked = True
            Atlas::resetAllPositions(self)
            ; Consume the click so it doesn't pass through to a node
            ; behind the button.
            Loom_ConsumeClick()
            clicked = False
            WriteLog(LoomLog, "Atlas: reset all positions")
        EndIf

        // Manual drag: detect LMB press inside a node -> begin drag;
        // mouse move -> reposition; release -> commit + save. Edge-
        // detected against lmbPrevDown so the per-frame Loom_MouseClicked
        // signal (one-shot press) can still drive Threads::focus on a
        // click-without-drag.
        Local lmbNow% = MouseDown(1)
        Atlas::pumpDrag(self, viewportX, viewportY + 28, viewportW, viewportH - 28, mx, my, lmbNow)
        self\lmbPrevDown = lmbNow

        // Draw edges first so node disks paint on top of them.
        Atlas::drawEdges(self, viewportX, viewportY + 28, viewportW, viewportH - 28)

        // Suppress the click pass-through to drawNodes when a drag is
        // active (or just finished with movement) -- otherwise the
        // mouse-up at the drag terminus would also focus the zone and
        // surprise the user with a tab switch.
        Local effectiveClick% = clicked
        If self\draggedNode <> Null Or self\dragMoved = True Then effectiveClick = False

        // Draw nodes + hit-test.
        Local hit% = Atlas::drawNodes(self, viewportX, viewportY + 28, viewportW, viewportH - 28, mx, my, effectiveClick)

        // dragMoved is consumed by the click-suppression above; clear
        // it AFTER drawNodes so a fresh frame starts clean. dragMoved
        // is set inside pumpDrag whenever the cursor moves while LMB
        // is held on a node.
        If self\draggedNode = Null Then self\dragMoved = False

        Return hit
    End Method


    // -------------------------------------------------------------------------
    // rebuildLayout -- drop old pool, allocate one AtlasNode per zone, run
    // ATLAS_ITERATIONS force-directed steps, recenter. Cheap at our scale.
    // -------------------------------------------------------------------------
    Method rebuildLayout()
        Atlas::clearNodes(self)
        Atlas::clearEdges(self)
        self\nodeCount = 0

        // Seed nodes -- circular layout to start (avoids the all-same-
        // position singularity that gives FR a zero-vector problem).
        Local zones% = Atlas::countZones(self)
        If zones = 0 Then Return

        Local theta# = 0.0
        Local arc# = 0.0
        If zones > 0 Then arc# = 6.28318 / Float(zones)
        Local r# = 200.0
        For Ar.Area = Each Area
            Local n.AtlasNode = New AtlasNode()
            n\ZoneHandle = Handle(Ar)
            n\Label = Ar\Name$
            n\X# = Cos#(theta * 57.2958) * r
            n\Y# = Sin#(theta * 57.2958) * r
            n\DX# = 0.0
            n\DY# = 0.0
            // Count defined spawn slots up front so we don't re-scan
            // 1000 indices every frame in drawNodes. Atlas rebuilds on
            // zone add / delete; spawn-count changes within a zone
            // require a manual rebuild (acceptable since the at-a-glance
            // signal is for project structure, not live tuning).
            Local sci% = 0
            Local scnt% = 0
            For sci = 0 To 999
                If Ar\SpawnActor[sci] > 0 Then scnt = scnt + 1
            Next
            n\SpawnCount = scnt
            n\Outdoors = Ar\Outdoors
            n\IssueCount = 0   ; populated by Atlas_RefreshIssueCounts after BrokenRefs rebuild
            theta = theta + arc
            self\nodeCount = self\nodeCount + 1
        Next

        // Seed edges -- one per portal whose target name resolves to a
        // zone. Strings the user typed get a soft fail (no edge) rather
        // than a broken edge.
        For Ar.Area = Each Area
            Local p% = 0
            For p = 0 To 99
                If Ar\PortalLinkArea$[p] <> ""
                    Local toHandle% = Atlas::findZoneHandleByName(self, Ar\PortalLinkArea$[p])
                    If toHandle <> 0
                        Local e.AtlasEdge = New AtlasEdge()
                        e\FromHandle = Handle(Ar)
                        e\ToHandle = toHandle
                    EndIf
                EndIf
            Next
        Next

        // Run the force-directed iterations.
        self\temperature# = ATLAS_INITIAL_TEMP#
        Local iter% = 0
        For iter = 0 To ATLAS_ITERATIONS - 1
            Atlas::layoutStep(self)
            self\temperature# = self\temperature# * ATLAS_TEMP_DECAY#
            If self\temperature# < ATLAS_MIN_TEMP# Then self\temperature# = ATLAS_MIN_TEMP#
        Next

        // Overlay any saved manual positions from Data\Loom\atlas.txt.
        // Done AFTER force-directed so saved positions completely
        // override the computed ones for the matching zones.
        Atlas::applySavedLayout(self)

        Atlas::recenterLayout(self)
    End Method


    // -------------------------------------------------------------------------
    // layoutStep -- one Fruchterman-Reingold iteration. Repulsion between
    // every pair, attraction along every edge, displacement clamped to
    // current temperature, applied.
    // -------------------------------------------------------------------------
    Method layoutStep()
        // k = ideal edge length. Bigger k spreads the graph more.
        Local k# = 90.0
        Local k2# = k * k

        // Reset accumulated displacement
        Local n.AtlasNode
        For n = Each AtlasNode
            n\DX# = 0.0
            n\DY# = 0.0
        Next

        // Repulsion -- every pair pushes apart. O(N^2) -- fine for ~hundreds.
        Local n1.AtlasNode
        Local n2.AtlasNode
        For n1 = Each AtlasNode
            For n2 = Each AtlasNode
                If Handle(n1) <> Handle(n2)
                    Local dx# = n1\X# - n2\X#
                    Local dy# = n1\Y# - n2\Y#
                    Local dist# = Sqr#(dx * dx + dy * dy)
                    If dist# < 0.01 Then dist# = 0.01
                    Local force# = k2 / dist#
                    n1\DX# = n1\DX# + (dx / dist#) * force#
                    n1\DY# = n1\DY# + (dy / dist#) * force#
                EndIf
            Next
        Next

        // Attraction -- pull edge endpoints together
        Local e.AtlasEdge
        For e = Each AtlasEdge
            Local na.AtlasNode = Atlas::findNodeByHandle(self, e\FromHandle)
            Local nb.AtlasNode = Atlas::findNodeByHandle(self, e\ToHandle)
            If na <> Null And nb <> Null
                Local dxE# = na\X# - nb\X#
                Local dyE# = na\Y# - nb\Y#
                Local distE# = Sqr#(dxE * dxE + dyE * dyE)
                If distE# < 0.01 Then distE# = 0.01
                Local forceE# = (distE * distE) / k
                na\DX# = na\DX# - (dxE / distE) * forceE
                na\DY# = na\DY# - (dyE / distE) * forceE
                nb\DX# = nb\DX# + (dxE / distE) * forceE
                nb\DY# = nb\DY# + (dyE / distE) * forceE
            EndIf
        Next

        // Apply displacement, clamped by temperature. Skip nodes the
        // user has manually placed -- their position is a deliberate
        // designer choice, not subject to force-directed perturbation.
        Local napp.AtlasNode
        For napp = Each AtlasNode
            If napp\Manual = True Then Continue
            Local d# = Sqr#(napp\DX# * napp\DX# + napp\DY# * napp\DY#)
            If d# < 0.01 Then d# = 0.01
            Local scale# = self\temperature#
            If d# < scale# Then scale# = d#
            napp\X# = napp\X# + (napp\DX# / d#) * scale#
            napp\Y# = napp\Y# + (napp\DY# / d#) * scale#
        Next
    End Method


    // -------------------------------------------------------------------------
    // recenterLayout -- compute the bounding box of all nodes so render can
    // normalize them to the viewport rect. Stored on self.
    // -------------------------------------------------------------------------
    Method recenterLayout()
        self\minX# = 999999.0 : self\minY# = 999999.0
        self\maxX# = -999999.0 : self\maxY# = -999999.0
        Local n.AtlasNode
        For n = Each AtlasNode
            If n\X# < self\minX# Then self\minX# = n\X#
            If n\Y# < self\minY# Then self\minY# = n\Y#
            If n\X# > self\maxX# Then self\maxX# = n\X#
            If n\Y# > self\maxY# Then self\maxY# = n\Y#
        Next
        // Avoid divide-by-zero in render's scale calc.
        If self\maxX# - self\minX# < 1.0 Then self\maxX# = self\minX# + 1.0
        If self\maxY# - self\minY# < 1.0 Then self\maxY# = self\minY# + 1.0
    End Method


    // -------------------------------------------------------------------------
    // worldToScreenX / worldToScreenY -- map a node's world-space position
    // into the viewport rect, keeping aspect ratio.
    // -------------------------------------------------------------------------
    Method worldToScreenX%(vx%, vw%, wx#)
        Local span# = self\maxX# - self\minX#
        Local norm# = (wx# - self\minX#) / span#
        // Apply user zoom + pan. Zoom is about the centre (norm 0.5)
        // so the visible content scales without drifting. Pan adds
        // a normalized offset.
        norm# = (norm# - 0.5) * self\viewZoom# + 0.5 + self\viewPanX#
        Return vx + ATLAS_VIEWPORT_PAD + Int(norm# * Float(vw - ATLAS_VIEWPORT_PAD * 2))
    End Method


    Method worldToScreenY%(vy%, vh%, wy#)
        Local span# = self\maxY# - self\minY#
        Local norm# = (wy# - self\minY#) / span#
        norm# = (norm# - 0.5) * self\viewZoom# + 0.5 + self\viewPanY#
        Return vy + ATLAS_VIEWPORT_PAD + Int(norm# * Float(vh - ATLAS_VIEWPORT_PAD * 2))
    End Method


    // -------------------------------------------------------------------------
    // screenToWorldX / screenToWorldY -- inverse of worldToScreen*. Drag
    // converts screen-space mouse positions back to world-space so the
    // node tracks the cursor regardless of viewport size, zoom, or pan.
    // -------------------------------------------------------------------------
    Method screenToWorldX#(vx%, vw%, sx%)
        Local span# = self\maxX# - self\minX#
        Local inner% = vw - ATLAS_VIEWPORT_PAD * 2
        If inner <= 0 Then Return self\minX#
        Local norm# = Float(sx - vx - ATLAS_VIEWPORT_PAD) / Float(inner)
        // Inverse of worldToScreen's zoom + pan: subtract pan, undo
        // the centre-scaling.
        norm# = norm# - self\viewPanX#
        norm# = (norm# - 0.5) / self\viewZoom# + 0.5
        Return self\minX# + norm# * span#
    End Method


    Method screenToWorldY#(vy%, vh%, sy%)
        Local span# = self\maxY# - self\minY#
        Local inner% = vh - ATLAS_VIEWPORT_PAD * 2
        If inner <= 0 Then Return self\minY#
        Local norm# = Float(sy - vy - ATLAS_VIEWPORT_PAD) / Float(inner)
        norm# = norm# - self\viewPanY#
        norm# = (norm# - 0.5) / self\viewZoom# + 0.5
        Return self\minY# + norm# * span#
    End Method


    // -------------------------------------------------------------------------
    // pumpDrag -- detect LMB press inside a node, track mouse during
    // drag, commit + save on release. Edge-detected against lmbPrevDown
    // so the per-frame state machine doesn't re-fire press logic.
    //
    // Drag behavior:
    //   - Press inside a node: capture as draggedNode + dragOffset
    //   - Hold + move: update node\X#/Y# from screenToWorld(mouse)
    //   - Move during drag: set dragMoved (gates the click-suppress +
    //     save-on-release path)
    //   - Release: if dragMoved, mark node Manual + persist layout
    // -------------------------------------------------------------------------
    Method pumpDrag(vx%, vy%, vw%, vh%, mx%, my%, lmbNow%)
        // Press detection: lmbNow True + previous frame down False
        If lmbNow = True And self\lmbPrevDown = False
            // Walk nodes to find one under the cursor. Stop at first hit.
            Local n.AtlasNode
            For n = Each AtlasNode
                Local sx% = Atlas::worldToScreenX(self, vx, vw, n\X#)
                Local sy% = Atlas::worldToScreenY(self, vy, vh, n\Y#)
                Local dx% = mx - sx
                Local dy% = my - sy
                Local r% = ATLAS_NODE_R     // press radius uses base size; close enough
                If dx * dx + dy * dy < r * r
                    self\draggedNode = n
                    self\dragOffsetX = dx
                    self\dragOffsetY = dy
                    self\dragMoved = False
                    Exit
                EndIf
            Next
        EndIf

        // Held + moving: update node position from cursor (minus offset
        // so the grab point stays under the cursor).
        If lmbNow = True And self\draggedNode <> Null
            Local targetSX% = mx - self\dragOffsetX
            Local targetSY% = my - self\dragOffsetY
            Local newWX# = Atlas::screenToWorldX(self, vx, vw, targetSX)
            Local newWY# = Atlas::screenToWorldY(self, vy, vh, targetSY)
            If newWX# <> self\draggedNode\X# Or newWY# <> self\draggedNode\Y#
                self\draggedNode\X# = newWX#
                self\draggedNode\Y# = newWY#
                self\dragMoved = True
            EndIf
        EndIf

        // Release: if the drag actually moved the node, mark it Manual
        // (force layout skips Manual nodes) and persist.
        If lmbNow = False And self\draggedNode <> Null
            If self\dragMoved = True
                self\draggedNode\Manual = True
                Loom_SaveAtlasLayout()
                WriteLog(LoomLog, "Atlas: drag commit + save")
            EndIf
            self\draggedNode = Null
            // dragMoved gets cleared in renderAndUpdate next frame; it
            // suppresses the click pass-through to drawNodes this frame.
        EndIf
    End Method


    // -------------------------------------------------------------------------
    // drawEdges -- paint a brass line between each connected pair.
    // -------------------------------------------------------------------------
    Method drawEdges(vx%, vy%, vw%, vh%)
        Color LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B
        Local e.AtlasEdge
        For e = Each AtlasEdge
            Local na.AtlasNode = Atlas::findNodeByHandle(self, e\FromHandle)
            Local nb.AtlasNode = Atlas::findNodeByHandle(self, e\ToHandle)
            If na <> Null And nb <> Null
                Local x1% = Atlas::worldToScreenX(self, vx, vw, na\X#)
                Local y1% = Atlas::worldToScreenY(self, vy, vh, na\Y#)
                Local x2% = Atlas::worldToScreenX(self, vx, vw, nb\X#)
                Local y2% = Atlas::worldToScreenY(self, vy, vh, nb\Y#)
                Line x1, y1, x2, y2
            EndIf
        Next
    End Method


    // -------------------------------------------------------------------------
    // drawNodes -- paint each node as a stone disk with a brass ring, label
    // below; hit-test for clicks. Returns True if click landed on a node.
    //
    // Hover highlights with arcane border; the currently focused zone (if
    // any) gets a brass-filled disk so the user can see "I am here."
    // -------------------------------------------------------------------------
    Method drawNodes%(vx%, vy%, vw%, vh%, mx%, my%, clicked%)
        Local hit% = False
        Local n.AtlasNode
        For n = Each AtlasNode
            Local sx% = Atlas::worldToScreenX(self, vx, vw, n\X#)
            Local sy% = Atlas::worldToScreenY(self, vy, vh, n\Y#)

            // Per-node radius scales with spawn density. Small zones
            // stay at the base size (ATLAS_NODE_R); a zone with 100
            // spawns ends up 1.5x as big. sqrt() keeps the visual
            // dynamic range usable -- linear scale would make a 1000-spawn
            // zone obliterate everything else.
            Local r% = ATLAS_NODE_R
            If n\SpawnCount > 0
                Local extra# = Sqr(Float(n\SpawnCount)) * 1.2
                If extra > Float(ATLAS_NODE_R) / 2.0 Then extra = Float(ATLAS_NODE_R) / 2.0
                r = ATLAS_NODE_R + Int(extra)
            EndIf

            Local dx% = mx - sx
            Local dy% = my - sy
            Local dist2% = dx * dx + dy * dy
            Local hovered% = (dist2 < r * r)
            Local focused% = (self\threads\focusKind = "zone" And self\threads\focusID = n\ZoneHandle)

            // Node disk -- approximate circle via successive filled rects.
            // Blitz3D doesn't have a one-shot filled-circle primitive, so
            // for the small radius we use we draw a centered square and
            // round it visually with a 1px ring. Cheap and reads as a node.
            // Drop shadow first for visual lift over the graph edges.
            LoomShadowCard(sx - r, sy - r, r * 2, r * 2)
            If focused = True
                LoomFill(sx - r, sy - r, r * 2, r * 2, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
            Else If hovered = True
                LoomFill(sx - r, sy - r, r * 2, r * 2, LOOM_ARCANE_700_R, LOOM_ARCANE_700_G, LOOM_ARCANE_700_B)
            Else
                // Outdoors zones get a slightly warmer/lighter stone fill
                // so designers can scan the map for "is the forest the
                // big green cluster?" without opening each zone.
                If n\Outdoors = True
                    LoomFill(sx - r, sy - r, r * 2, r * 2, LOOM_STONE_500_R, LOOM_STONE_500_G, LOOM_STONE_500_B)
                Else
                    LoomFill(sx - r, sy - r, r * 2, r * 2, LOOM_STONE_700_R, LOOM_STONE_700_G, LOOM_STONE_700_B)
                EndIf
            EndIf

            // Outer brass ring
            LoomBorder(sx - r, sy - r, r * 2, r * 2, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)

            // Spawn count inside the node -- "12" if non-zero, else
            // blank. Quick "is this zone empty?" cue.
            If n\SpawnCount > 0
                LoomTextCentered(sx, sy - 6, Str(n\SpawnCount), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            EndIf

            // Issue-count badge top-right corner -- only when issues
            // exist. Small danger-red pill with the count. Designers
            // scanning the atlas see broken/dangerous zones immediately.
            If n\IssueCount > 0
                Local badgeW% = 16
                Local badgeX% = sx + r - badgeW
                Local badgeY% = sy - r
                LoomFill(badgeX, badgeY, badgeW, 14, LOOM_DANGER_R, LOOM_DANGER_G, LOOM_DANGER_B)
                LoomBorder(badgeX, badgeY, badgeW, 14, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
                LoomTextCentered(badgeX + badgeW / 2, badgeY + 1, Str(n\IssueCount), LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
            EndIf

            // Pin glyph (top-left corner) for nodes the user has
            // manually positioned. Tiny brass dot + "pin" text so
            // designers see which nodes will resist force-directed
            // perturbation. Double-click the pin to unfreeze.
            Local pinW% = 22
            Local pinX% = sx - r
            Local pinY% = sy - r
            Local pinHovered% = False
            If n\Manual = True
                LoomFill(pinX, pinY, pinW, 11, LOOM_BRASS_700_R, LOOM_BRASS_700_G, LOOM_BRASS_700_B)
                LoomBorder(pinX, pinY, pinW, 11, LOOM_BRASS_500_R, LOOM_BRASS_500_G, LOOM_BRASS_500_B)
                LoomText(pinX + 2, pinY - 1, "pin", LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)
                pinHovered = (mx >= pinX And mx < pinX + pinW And my >= pinY And my < pinY + 11)
                If pinHovered = True And clicked = True
                    // Unfreeze this node: clear Manual, save layout
                    // (writes 0 for this node's Manual column), and
                    // re-run force-directed so it can settle into
                    // an organic position. We don't blank X/Y --
                    // keeps the visual continuity (it drifts from
                    // pinned position rather than teleporting).
                    n\Manual = False
                    Loom_SaveAtlasLayout()
                    Atlas::rebuildLayout(self)
                    Loom_ConsumeClick()
                    hit = True
                    WriteLog(LoomLog, "Atlas: unpinned " + n\Label$)
                EndIf
            EndIf

            // Label below
            Local labelTxt$ = n\Label
            If Len(labelTxt) > 16 Then labelTxt = Left$(labelTxt, 14) + ".."
            LoomTextCentered(sx, sy + r + 4, labelTxt, LOOM_PARCHMENT_100_R, LOOM_PARCHMENT_100_G, LOOM_PARCHMENT_100_B)

            If hovered = True And clicked = True
                Threads::focus(self\threads, "zone", n\ZoneHandle)
                hit = True
                WriteLog(LoomLog, "Atlas: focused zone handle " + Str(n\ZoneHandle))
            EndIf
        Next
        Return hit
    End Method


    // -------------------------------------------------------------------------
    // refreshIssueCounts -- zero each node's IssueCount, then walk the
    // current BrokenRef pool and increment per-node for any entry whose
    // SourceKind = "zone" and SourceRefID matches a node's ZoneHandle.
    // Cheap O(nodes + entries) -- both capped well under 1000.
    // -------------------------------------------------------------------------
    Method refreshIssueCounts()
        Local n.AtlasNode
        For n = Each AtlasNode
            n\IssueCount = 0
        Next

        Local br.BrokenRef
        For br = Each BrokenRef
            If br\SourceKind = "zone"
                // Linear scan to find matching node -- typically <50 nodes
                // so the inner walk is trivial; keeps refresh simple.
                For n = Each AtlasNode
                    If n\ZoneHandle = br\SourceRefID
                        n\IssueCount = n\IssueCount + 1
                        Exit
                    EndIf
                Next
            EndIf
        Next
    End Method


    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    Method countZones%()
        Local c% = 0
        For Ar.Area = Each Area
            c = c + 1
        Next
        Return c
    End Method


    // -------------------------------------------------------------------------
    // countManual -- O(nodes) walk that counts AtlasNodes with Manual=True.
    // Used by the title strip to surface "N pinned" so designers see the
    // current manual-override scope at a glance.
    // -------------------------------------------------------------------------
    Method countManual%()
        Local c% = 0
        Local n.AtlasNode
        For n = Each AtlasNode
            If n\Manual = True Then c = c + 1
        Next
        Return c
    End Method


    // -------------------------------------------------------------------------
    // resetAllPositions -- clear every node's Manual flag, then run a
    // fresh rebuildLayout (force-directed seed + saved-layout overlay).
    // The saved-layout overlay would re-apply any positions from disk;
    // we DELETE the layout file first so this is a true reset rather
    // than a half-revert. Then call Loom_SaveAtlasLayout to write an
    // empty layout file -- so the reset persists across sessions.
    // -------------------------------------------------------------------------
    Method resetAllPositions()
        Local n.AtlasNode
        For n = Each AtlasNode
            n\Manual = False
        Next
        // Also reset zoom + pan so the freshly-laid-out graph fills
        // the viewport again. Designers who clicked "reset positions"
        // implicitly want the full canonical view back.
        self\viewZoom# = 1.0
        self\viewPanX# = 0.0
        self\viewPanY# = 0.0
        // Delete persisted layout so applySavedLayout (called from
        // rebuildLayout) doesn't re-impose the old positions on top
        // of the fresh force-directed pass.
        If FileType("Data\Loom\atlas.txt") = 1
            DeleteFile("Data\Loom\atlas.txt")
        EndIf
        Atlas::rebuildLayout(self)
        Loom_SaveAtlasLayout()
    End Method


    Method findZoneHandleByName%(name$)
        If name = "" Then Return 0
        Local upr$ = Upper$(name)
        For Ar.Area = Each Area
            If Upper$(Ar\Name$) = upr Then Return Handle(Ar)
        Next
        Return 0
    End Method


    Method findNodeByHandle.AtlasNode(h%)
        Local n.AtlasNode
        For n = Each AtlasNode
            If n\ZoneHandle = h Then Return n
        Next
        Return Null
    End Method


    Method clearNodes()
        Local n.AtlasNode
        For n = Each AtlasNode
            Delete n
        Next
    End Method


    Method clearEdges()
        Local e.AtlasEdge
        For e = Each AtlasEdge
            Delete e
        Next
    End Method


    // -------------------------------------------------------------------------
    // applySavedLayout -- called after rebuildLayout's force-directed
    // pass. Reads Data/Loom/atlas.txt (if it exists) and overrides each
    // node's X/Y position (and Manual flag) for matching zone names.
    // Force-directed positions remain for any zone not in the saved file.
    // -------------------------------------------------------------------------
    Method applySavedLayout()
        Local F.BBStream = ReadFile("Data\Loom\atlas.txt")
        If F = Null Then Return

        Local applied% = 0
        While Not Eof(F)
            Local L$ = ReadLine(F)
            // Format: ZoneName | X | Y | Manual
            // Split manually on " | " delimiter.
            If L <> ""
                Local pipe1% = Instr(L, " | ")
                If pipe1 > 0
                    Local name$ = Left$(L, pipe1 - 1)
                    Local rest1$ = Mid$(L, pipe1 + 3)
                    Local pipe2% = Instr(rest1, " | ")
                    If pipe2 > 0
                        Local xS$ = Left$(rest1, pipe2 - 1)
                        Local rest2$ = Mid$(rest1, pipe2 + 3)
                        Local pipe3% = Instr(rest2, " | ")
                        If pipe3 > 0
                            Local yS$ = Left$(rest2, pipe3 - 1)
                            Local manS$ = Mid$(rest2, pipe3 + 3)
                            Local n.AtlasNode
                            For n = Each AtlasNode
                                If n\Label = name
                                    n\X# = Float(xS)
                                    n\Y# = Float(yS)
                                    n\Manual = (manS = "1")
                                    applied = applied + 1
                                    Exit
                                EndIf
                            Next
                        EndIf
                    EndIf
                EndIf
            EndIf
        Wend
        CloseFile(F)

        WriteLog(LoomLog, "Atlas: applied saved layout (" + Str(applied) + " nodes)")
    End Method
End Type


// =============================================================================
// Loom_SaveAtlasLayout / Loom_LoadAtlasLayout -- free functions called
// from inside the Atlas class (after a drag commit) and from Loom.bb's
// boot/shutdown. Stored under Data\Loom\atlas.txt via SafeWriteOpen/Commit
// so a crash mid-write doesn't corrupt the layout file.
//
// File format (one node per line):
//   ZoneName | X | Y | Manual
//   X / Y are floats with default Str formatting.
//   Manual is "0" or "1" (1 = user dragged it; force layout skips).
//
// Non-Atlas-method form so call sites don't need the instance handle.
// Walks the global AtlasNode pool directly.
// =============================================================================
Function Loom_SaveAtlasLayout()
    Local path$ = "Data\Loom\atlas.txt"
    Local tempPath$ = SafeWriteOpen$(path)
    Local F.BBStream = WriteFile(tempPath)
    If F = Null Then Return

    Local n.AtlasNode
    For n = Each AtlasNode
        Local manS$ = "0"
        If n\Manual = True Then manS = "1"
        WriteLine(F, n\Label + " | " + Str(n\X#) + " | " + Str(n\Y#) + " | " + manS)
    Next

    // Close the stream ourselves, then pass 0 as the int F arg to
    // SafeWriteCommit -- it tolerates F=0 (skips its own CloseFile).
    // This avoids the Strict BBStream->Int conversion barrier; the
    // non-Strict Logging.bb signature takes an untyped F.
    CloseFile(F)
    SafeWriteCommit%(tempPath, path, 0)
End Function

