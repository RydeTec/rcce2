/* Loom — Main app
   State machine for the prototype:
   - selectedId: the focused entity (entity in scene or via thread/palette)
   - paletteOpen, walkInOpen, conscienceOpen, atlasOpen
   - currentHistoryIdx: where in the session timeline we are
   - showThreads: tweak
   - aesthetic: tweak (immersion level)
*/

const { useState, useEffect, useRef, useMemo } = React;

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "showThreads": true,
  "showLabels": true,
  "aesthetic": "balanced",
  "atlasVisible": true,
  "viewMode": "zone"
}/*EDITMODE-END*/;

function BrandGlyph() {
  /* A simple woven-thread "L" mark - three threads meeting */
  return (
    <svg viewBox="0 0 22 22" width="22" height="22">
      <defs>
        <linearGradient id="glyph-arc" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#a8dcff" />
          <stop offset="100%" stopColor="#2b78c7" />
        </linearGradient>
      </defs>
      <path d="M3,3 L3,19 L19,19" fill="none" stroke="url(#glyph-arc)" strokeWidth="2.4" strokeLinecap="round" />
      <path d="M3,3 Q11,11 19,19" fill="none" stroke="var(--brass-500)" strokeWidth="1" strokeLinecap="round" strokeDasharray="2 3" opacity="0.8" />
      <circle cx="3" cy="3" r="2" fill="#a8dcff" />
      <circle cx="19" cy="19" r="2" fill="var(--brass-500)" />
      <circle cx="3" cy="19" r="1.5" fill="var(--parchment-100)" />
    </svg>
  );
}

function App() {
  const [tw, setTweak] = useTweaks(TWEAK_DEFAULTS);

  const [selectedId, setSelectedId] = useState("actor.goblin_shaman");
  const [hoverId, setHoverId] = useState(null);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [walkInOpen, setWalkInOpen] = useState(false);
  const [conscienceOpen, setConscienceOpen] = useState(false);
  const [currentHistoryIdx, setCurrentHistoryIdx] = useState(WORLD.history.length - 1);
  const [activeZone, setActiveZone] = useState("zone.hollows_edge");
  const [showAtlas, setShowAtlas] = useState(true);
  const [atlasUserToggled, setAtlasUserToggled] = useState(false);

  /* Auto-hide atlas when an entity is selected (unless user has explicitly toggled it). */
  const atlasVisible = atlasUserToggled ? showAtlas : (showAtlas && !selectedId);

  const stageRef = useRef(null);

  /* Most recent ref-bind for the goblin shaman */
  const recentRef = "spell.firebolt";

  /* Keyboard shortcuts ---------------------------------------- */
  useEffect(() => {
    const onKey = (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") { e.preventDefault(); setPaletteOpen(o => !o); }
      else if (e.key === "Escape") {
        if (paletteOpen) setPaletteOpen(false);
        else if (walkInOpen) setWalkInOpen(false);
        else if (conscienceOpen) setConscienceOpen(false);
        else if (selectedId) setSelectedId(null);
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [paletteOpen, walkInOpen, conscienceOpen, selectedId]);

  const onSelect = (id) => { setSelectedId(id); };
  const onJump = (id) => {
    /* If jumping to a zone, change active zone instead of selecting it. */
    if (kindOf(id) === "zone") {
      setActiveZone(id);
      setSelectedId(null);
    } else {
      setSelectedId(id);
    }
  };

  const findings = WORLD.findings;
  const dangerCount = findings.filter(f => f.severity === "danger").length;
  const warnCount = findings.filter(f => f.severity === "warn").length;
  const infoCount = findings.filter(f => f.severity === "info").length;

  const zone = WORLD.zones.find(z => z.id === activeZone);

  /* Aesthetic class for body ---------------------------- */
  const aestheticClass = `aesthetic-${tw.aesthetic}`;

  return (
    <div className={`loom ${aestheticClass}`}>
      {/* ===== TOP: Atlas / Brand strip ===== */}
      <div className="atlas-strip">
        <div className="brand-mark">
          <span className="glyph"><BrandGlyph /></span>
          LOOM
          <span className="brand-byline">for RCCE</span>
        </div>
        <div className="breadcrumb">
          <span className="sep">›</span>
          <span className="crumb" onClick={() => { setActiveZone("zone.hollows_edge"); setSelectedId(null); }}>{zone ? zone.name : "—"}</span>
          {selectedId && (() => {
            const e = findEntity(selectedId);
            if (!e) return null;
            return <><span className="sep">›</span><span className="crumb current">{e.name}</span></>;
          })()}
        </div>
        <div className="atlas-spacer" />
        <button className="atlas-button" onClick={() => { setShowAtlas(a => !a); setAtlasUserToggled(true); }}>
          {atlasVisible ? "Hide atlas" : "Show atlas"}
        </button>
        <button className="atlas-button" onClick={() => setPaletteOpen(true)}>
          ⌕ Find <kbd>⌘K</kbd>
        </button>
        <button className="walk-in-pill" onClick={() => setWalkInOpen(true)} title="Spawn into the zone you're editing">
          <svg width="10" height="10" viewBox="0 0 10 10"><path d="M2,1 L8,5 L2,9 Z" fill="currentColor" /></svg>
          <span>Walk in</span>
        </button>
        <button className="live-indicator" title="Connected to test server">
          <span className="live-pulse" />
          <span>LIVE</span>
          <span className="live-host">test.embergloom.local</span>
        </button>
      </div>

      {/* ===== Validation conscience ===== */}
      <div className="conscience">
        {dangerCount > 0 && (
          <div className="conscience-item" onClick={() => setConscienceOpen(o => !o)}>
            <span className="dot danger" />
            <span className="count">{dangerCount}</span>
            <span>broken refs</span>
          </div>
        )}
        {warnCount > 0 && (
          <div className="conscience-item" onClick={() => setConscienceOpen(o => !o)}>
            <span className="dot warn" />
            <span className="count">{warnCount}</span>
            <span>warnings</span>
          </div>
        )}
        {infoCount > 0 && (
          <div className="conscience-item" onClick={() => setConscienceOpen(o => !o)}>
            <span className="dot info" />
            <span className="count">{infoCount}</span>
            <span>hints · balance review</span>
          </div>
        )}
        <div className="conscience-item">
          <span className="dot info" />
          <span className="count">3</span>
          <span>unsaved entities — <span style={{ color: "var(--arcane-300)" }}>autosaved 14s ago</span></span>
        </div>
        <div className="conscience-spacer" />
        <div className="conscience-meter">
          <span>WORLD HEALTH</span>
          <div className="health-bar" style={{ "--health-clip": "18%" }} />
          <span style={{ color: "var(--success)", fontWeight: 600 }}>82%</span>
        </div>
      </div>

      {/* ===== WORLD STAGE ===== */}
      <div className="world-stage" ref={stageRef}>
        <WorldScene
          zone={zone}
          selectedId={selectedId}
          onSelect={onSelect}
          hoverId={hoverId}
          setHoverId={setHoverId}
          pendingEdit={recentRef}
        />

        {tw.showThreads && (
          <ThreadsOverlay
            selectedId={selectedId}
            stageRef={stageRef}
            onJump={onJump}
            recentRef={recentRef}
          />
        )}

        {atlasVisible && <AtlasOverlay activeZone={activeZone} onPickZone={onJump} />}

        {/* Walk-in CTA moved into top strip */}

        {/* Composer */}
        {selectedId && (
          <Composer entityId={selectedId} recentRef={recentRef} onJump={onJump} onClose={() => setSelectedId(null)} />
        )}

        {/* Conscience detail */}
        <ConscienceDetail open={conscienceOpen} onClose={() => setConscienceOpen(false)} onJump={onJump} findings={findings} />

        {/* Empty-state floor hint when nothing selected */}
        {!selectedId && (
          <div className="floor-hint">
            <div className="eyebrow">Hollow's Edge</div>
            <h1 className="floor-title">A clearing with a cottage and a goblin shaman.</h1>
            <p className="floor-sub">
              Click an entity to pull it to focus. Click a thread to follow it.
              Press <kbd>⌘K</kbd> to search the world.
            </p>
          </div>
        )}
      </div>

      {/* ===== TIMELINE ===== */}
      <Timeline
        history={WORLD.history}
        currentIdx={currentHistoryIdx}
        onScrub={setCurrentHistoryIdx}
        onJumpEntity={onJump}
      />

      {/* ===== Command palette ===== */}
      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} onJump={onJump} />

      {/* ===== Walk-in playtest ===== */}
      <WalkInModal open={walkInOpen} onClose={() => setWalkInOpen(false)} />

      {/* ===== Tweaks panel ===== */}
      <TweaksPanel>
        <TweakSection label="Display" />
        <TweakToggle label="Show threads on selection" value={tw.showThreads}
                     onChange={(v) => setTweak('showThreads', v)} />
        <TweakSection label="Aesthetic immersion" />
        <TweakRadio label="Style" value={tw.aesthetic}
                    options={[
                      { value: "tool",     label: "Tool" },
                      { value: "balanced", label: "Balanced" },
                      { value: "in-world", label: "In-world" },
                    ]}
                    onChange={(v) => setTweak('aesthetic', v)} />
        <TweakSection label="Try a flow" />
        <TweakButton label="Focus goblin shaman"  onClick={() => setSelectedId("actor.goblin_shaman")} />
        <TweakButton label="Tune Firebolt"        onClick={() => setSelectedId("spell.firebolt")} />
        <TweakButton label="Inspect Forest Tribe" onClick={() => setSelectedId("fac.forest_tribe")} />
        <TweakButton label="Open command palette" onClick={() => setPaletteOpen(true)} secondary />
        <TweakButton label="Open world conscience" onClick={() => setConscienceOpen(true)} secondary />
        <TweakButton label="Walk in (playtest)"   onClick={() => setWalkInOpen(true)} secondary />
      </TweaksPanel>
    </div>
  );
}

window.App = App;
