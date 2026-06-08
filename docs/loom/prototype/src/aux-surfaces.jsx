/* Loom — Auxiliary surfaces:
   - CommandPalette (cmd-K global find-anywhere)
   - ConscienceDetail (validation drawer)
   - Timeline (session history strip)
   - WalkInModal (playtest preview)
*/

const { useState: useStateAux, useEffect: useEffectAux, useMemo: useMemoAux } = React;

/* ============ COMMAND PALETTE ============ */
const PALETTE_KIND_ICON = { actor: "◈", item: "⊟", spell: "✦", faction: "❖", emitter: "❀", anim: "▷", zone: "▥" };

function CommandPalette({ open, onClose, onJump }) {
  const [q, setQ] = useStateAux("");
  const [active, setActive] = useStateAux(0);

  useEffectAux(() => { if (open) { setQ(""); setActive(0); } }, [open]);

  const all = useMemoAux(() => {
    return [
      ...WORLD.zones.map(z => ({ ...z, kind: "zone",    zone: z.name })),
      ...WORLD.actors.map(a => ({ ...a, kind: "actor",  zone: WORLD.zones.find(z => z.id === a.in)?.name })),
      ...WORLD.spells.map(s => ({ ...s, kind: "spell" })),
      ...WORLD.items.map(i => ({ ...i, kind: "item" })),
      ...WORLD.factions.map(f => ({ ...f, kind: "faction" })),
      ...WORLD.emitters.map(e => ({ ...e, kind: "emitter" })),
      ...WORLD.animSets.map(a => ({ ...a, kind: "anim" })),
    ];
  }, []);

  const filtered = useMemoAux(() => {
    if (!q) return all.slice(0, 20);
    const lc = q.toLowerCase();
    return all.filter(e => e.name.toLowerCase().includes(lc) || e.id.toLowerCase().includes(lc)).slice(0, 60);
  }, [q, all]);

  /* Group results by kind */
  const groups = useMemoAux(() => {
    const g = {};
    filtered.forEach((e, i) => {
      if (!g[e.kind]) g[e.kind] = [];
      g[e.kind].push({ ...e, _idx: i });
    });
    return g;
  }, [filtered]);

  useEffectAux(() => {
    if (!open) return;
    const onKey = (e) => {
      if (e.key === "Escape") onClose();
      else if (e.key === "ArrowDown") { e.preventDefault(); setActive(a => Math.min(a + 1, filtered.length - 1)); }
      else if (e.key === "ArrowUp")   { e.preventDefault(); setActive(a => Math.max(a - 1, 0)); }
      else if (e.key === "Enter") {
        const target = filtered[active];
        if (target) { onJump(target.id); onClose(); }
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, filtered, active, onClose, onJump]);

  if (!open) return null;

  const KIND_LABEL = { zone: "Zones", actor: "Actors", spell: "Spells", item: "Items", faction: "Factions", emitter: "Particles", anim: "Animation sets" };
  const KIND_ORDER = ["zone", "actor", "spell", "item", "faction", "emitter", "anim"];

  return (
    <div className="palette-veil" onClick={onClose}>
      <div className="palette slab slab-brass fade-in-down" onClick={e => e.stopPropagation()}>
        <div className="palette-input-row">
          <span className="icon">⌕</span>
          <input className="palette-input" autoFocus
                 placeholder="Find anywhere — zone, actor, item, spell, particle, script…"
                 value={q} onChange={e => { setQ(e.target.value); setActive(0); }} />
          <div className="palette-hint-keys">
            <kbd>↑</kbd><kbd>↓</kbd> nav
            <kbd>↵</kbd> open
            <kbd>esc</kbd> close
          </div>
        </div>
        <div className="palette-list scroll">
          {filtered.length === 0 && (
            <div style={{ padding: "32px 20px", textAlign: "center", color: "var(--fg-dim)", fontFamily: "var(--font-serif)", fontStyle: "italic" }}>
              Nothing in the world matches that thread.
            </div>
          )}
          {KIND_ORDER.map(k => {
            const items = groups[k];
            if (!items || items.length === 0) return null;
            return (
              <div key={k} className="palette-group">
                <div className="palette-group-head">{KIND_LABEL[k]} · {items.length}</div>
                {items.map(it => (
                  <div key={it.id}
                       className={`palette-item ${it._idx === active ? "active" : ""}`}
                       onClick={() => { onJump(it.id); onClose(); }}
                       onMouseEnter={() => setActive(it._idx)}>
                    <span className="palette-item-kind">{PALETTE_KIND_ICON[it.kind]}</span>
                    <span className="palette-item-name">{it.name}</span>
                    {it.zone && <span className="palette-item-zone">{it.zone}</span>}
                    <span className="palette-item-meta">{it.id}</span>
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

/* ============ CONSCIENCE detail drawer ============ */
function ConscienceDetail({ open, onClose, onJump, findings }) {
  if (!open) return null;
  const grouped = { danger: [], warn: [], info: [] };
  findings.forEach(f => grouped[f.severity].push(f));
  return (
    <div className="conscience-detail slab slab-brass fade-in-down">
      <div className="conscience-detail-head">
        <div className="conscience-detail-title">World Conscience</div>
        <button className="btn btn-ghost" onClick={onClose}>Close</button>
      </div>
      {["danger", "warn", "info"].map(sev => {
        const items = grouped[sev];
        if (items.length === 0) return null;
        const SEV_LABEL = { danger: "Broken", warn: "Warnings", info: "Hints" };
        const SEV_ICON  = { danger: "!", warn: "⚠", info: "i" };
        return (
          <div key={sev}>
            <div style={{
              padding: "10px 18px 6px",
              fontFamily: "var(--font-sans)", fontSize: 9.5, fontWeight: 600,
              letterSpacing: "0.24em", textTransform: "uppercase",
              color: "var(--brass-500)",
              borderTop: "1px solid rgba(255,255,255,0.04)",
            }}>
              {SEV_LABEL[sev]} · {items.length}
            </div>
            {items.map(f => (
              <div key={f.id} className="finding-row" onClick={() => f.jump && onJump(f.jump)}>
                <div className={`finding-icon ${f.severity}`}>{SEV_ICON[f.severity]}</div>
                <div>
                  <div className="finding-body">{f.message}</div>
                  <div className="finding-meta">
                    <span>{f.kind.replace(/_/g, " ")}</span>
                    {f.entity && <><span>·</span><span className="mono">{f.entity}</span></>}
                  </div>
                </div>
                {f.jump ? <button className="finding-jump">Jump</button> : <span style={{ fontSize: 10, color: "var(--fg-dim)", fontFamily: "var(--font-mono)" }}>asset</span>}
              </div>
            ))}
          </div>
        );
      })}
    </div>
  );
}

/* ============ TIMELINE ============ */
function Timeline({ history, currentIdx, onScrub, onJumpEntity }) {
  return (
    <div className="timeline">
      <div className="timeline-label">
        <div className="eyebrow">Session</div>
        <div className="count">{history.length} edits · 41 min</div>
      </div>
      <div className="timeline-track">
        {history.map((h, i) => {
          const isPast = i < currentIdx;
          const isCurrent = i === currentIdx;
          const heightPct = h.hero ? 88 : (h.count > 1 ? 70 : 54);
          return (
            <div key={i}
                 className={`timeline-tick ${isCurrent ? "current" : ""} ${isPast ? "past" : ""} ${h.hero ? "hero" : ""}`}
                 onClick={() => onScrub(i)}>
              <div className="bar" style={{ height: `${heightPct}%` }} />
              <div className="timeline-tick-label">
                <span className="t">{h.t}</span>
                <span className="action">{h.action}{h.count > 1 ? ` · ${h.count}` : ""}</span>
                {h.note}
                <div style={{ marginTop: 6, fontFamily: "var(--font-mono)", fontSize: 9.5, color: "var(--brass-500)", letterSpacing: "0.1em" }}>
                  → <span onClick={(e) => { e.stopPropagation(); onJumpEntity(h.entity); }} style={{ cursor: "pointer", textDecoration: "underline dotted" }}>{h.entity}</span>
                </div>
              </div>
            </div>
          );
        })}
      </div>
      <div className="timeline-actions">
        <button className="btn btn-ghost" title="Undo last edit">↶</button>
        <button className="btn btn-ghost" title="Redo">↷</button>
        <button className="btn btn-ghost" title="Branch a what-if from here">⎘</button>
      </div>
    </div>
  );
}

/* ============ WALK-IN PLAYTEST ============ */
function WalkInModal({ open, onClose }) {
  if (!open) return null;
  return (
    <div className="walkin-veil fade-in" onClick={onClose}>
      <div className="walkin-frame" onClick={e => e.stopPropagation()}>
        <div className="walkin-actions">
          <button className="btn btn-ghost" onClick={onClose}>Back to editor</button>
          <button className="btn btn-brass">Save edit while in-world</button>
        </div>
        {/* The "first-person" forest view: warped scene */}
        <svg viewBox="0 0 1600 900" preserveAspectRatio="xMidYMid slice">
          <defs>
            <radialGradient id="walkin-sky" cx="50%" cy="20%" r="80%">
              <stop offset="0%" stopColor="#4d5a82" />
              <stop offset="50%" stopColor="#1a1f3a" />
              <stop offset="100%" stopColor="#08090f" />
            </radialGradient>
            <radialGradient id="walkin-ground" cx="50%" cy="100%" r="80%">
              <stop offset="0%" stopColor="#28371d" />
              <stop offset="100%" stopColor="#08090f" />
            </radialGradient>
            <radialGradient id="walkin-light" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stopColor="#e6c87a" stopOpacity="0.5" />
              <stop offset="100%" stopColor="#e6c87a" stopOpacity="0" />
            </radialGradient>
            <radialGradient id="walkin-staff" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stopColor="#fff" />
              <stop offset="30%" stopColor="#ff8a3c" />
              <stop offset="100%" stopColor="#ff8a3c" stopOpacity="0" />
            </radialGradient>
          </defs>
          {/* Sky */}
          <rect width="1600" height="450" fill="url(#walkin-sky)" />
          {/* Stars */}
          <circle cx="200" cy="120" r="1" fill="#fff" />
          <circle cx="400" cy="80"  r="1.5" fill="#d4ecff" />
          <circle cx="700" cy="160" r="1" fill="#fff" />
          <circle cx="950" cy="100" r="1" fill="#fff" />
          <circle cx="1200" cy="180" r="1.5" fill="#d4ecff" />
          <circle cx="1400" cy="90" r="1" fill="#fff" />
          {/* Moon */}
          <circle cx="1180" cy="180" r="36" fill="#f6efdc" opacity="0.85" />
          <circle cx="1170" cy="174" r="36" fill="#0a0c14" opacity="0.4" />
          {/* Distant treeline silhouette */}
          <path d="M0,470 L80,440 L160,470 L240,420 L320,460 L400,430 L480,465 L560,440 L640,460 L720,420 L800,450 L880,420 L960,460 L1040,420 L1120,460 L1200,430 L1280,465 L1360,425 L1440,460 L1520,430 L1600,460 L1600,500 L0,500 Z" fill="#0a0e1a" />
          {/* Ground */}
          <rect y="490" width="1600" height="410" fill="url(#walkin-ground)" />
          {/* Nearer trees (sides) */}
          <g>
            <line x1="150" y1="500" x2="150" y2="400" stroke="#1c1208" strokeWidth="8" />
            <path d="M150,400 L80,490 L120,490 L60,560 L120,560 L40,650 L260,650 L180,560 L240,560 L180,490 L220,490 Z" fill="#1a2814" stroke="#0c1408" strokeWidth="1" />
            <line x1="1450" y1="500" x2="1450" y2="380" stroke="#1c1208" strokeWidth="8" />
            <path d="M1450,380 L1380,490 L1420,490 L1360,560 L1420,560 L1340,650 L1560,650 L1480,560 L1540,560 L1480,490 L1520,490 Z" fill="#1a2814" stroke="#0c1408" strokeWidth="1" />
          </g>
          {/* Cottage in middle distance */}
          <g transform="translate(880,520)">
            <ellipse cx="0" cy="80" rx="180" ry="20" fill="url(#walkin-light)" />
            <path d="M-100,40 L-100,-20 L100,-20 L100,40 Z" fill="#6a563b" stroke="#2c2418" strokeWidth="2" />
            <path d="M-130,-20 L0,-110 L130,-20 Z" fill="#3a2418" stroke="#1c0e08" strokeWidth="2" />
            <rect x="-18" y="0" width="36" height="40" fill="#1c0e08" />
            <rect x="-72" y="-12" width="24" height="22" fill="#e6c87a" />
            <rect x="48" y="-12" width="24" height="22" fill="#e6c87a" />
          </g>
          {/* Goblin shaman in foreground (the actor we just placed) */}
          <g transform="translate(620,690) scale(4.5)">
            <ellipse cx="0" cy="4" rx="14" ry="4" fill="#000" opacity="0.6" />
            <path d="M-12,2 L-9,-22 L9,-22 L12,2 Z" fill="#3a4a2a" stroke="#1f2914" strokeWidth="1" />
            <circle cx="0" cy="-26" r="6" fill="#5d8a3a" stroke="#2c4316" strokeWidth="0.8" />
            <path d="M5,-28 L9,-30 L7,-24 Z" fill="#5d8a3a" stroke="#2c4316" strokeWidth="0.6" />
            <path d="M-5,-28 L-9,-30 L-7,-24 Z" fill="#5d8a3a" stroke="#2c4316" strokeWidth="0.6" />
            <circle cx="-2" cy="-26" r="0.8" fill="#ffec80" />
            <circle cx="2"  cy="-26" r="0.8" fill="#ffec80" />
            <line x1="14" y1="6" x2="11" y2="-28" stroke="#3a2618" strokeWidth="1.6" />
            <circle cx="11" cy="-29" r="2.4" fill="#d8d1c2" />
            <circle cx="11" cy="-29" r="5" fill="url(#walkin-staff)">
              <animate attributeName="r" values="5;8;5" dur="1.8s" repeatCount="indefinite" />
            </circle>
          </g>
          {/* Floating crosshair (player's reticle) */}
          <g transform="translate(800,450)">
            <circle r="14" fill="none" stroke="#fff" strokeOpacity="0.4" strokeWidth="1" />
            <line x1="-22" y1="0" x2="-8" y2="0" stroke="#fff" strokeOpacity="0.4" />
            <line x1="22" y1="0" x2="8" y2="0" stroke="#fff" strokeOpacity="0.4" />
            <line x1="0" y1="-22" x2="0" y2="-8" stroke="#fff" strokeOpacity="0.4" />
            <line x1="0" y1="22" x2="0" y2="8" stroke="#fff" strokeOpacity="0.4" />
          </g>
          {/* Tooltip on the goblin */}
          <g transform="translate(640,560)">
            <rect x="-110" y="-30" width="220" height="60" rx="2" fill="rgba(8,9,15,0.92)" stroke="var(--brass-700)" strokeWidth="0.8" />
            <line x1="-110" y1="-30" x2="110" y2="-30" stroke="var(--brass-500)" strokeWidth="0.4" />
            <text x="-100" y="-12" fontFamily="var(--font-display-chiseled)" fontSize="14" fill="#fff" letterSpacing="2">GOBLIN SHAMAN</text>
            <text x="-100" y="3" fontFamily="var(--font-sans)" fontSize="10" fill="var(--health-glow)">HOSTILE · LV 9</text>
            <text x="-100" y="18" fontFamily="var(--font-mono)" fontSize="9" fill="var(--arcane-300)">⌥ click to inspect · ✦ casts Firebolt</text>
          </g>
        </svg>
        {/* HUD overlay */}
        <div className="walkin-hud">
          <div className="walkin-hud-corner" style={{ top: 16, left: 16 }}>
            <div style={{ fontFamily: "var(--font-display-chiseled)", fontSize: 13, letterSpacing: 2, color: "var(--brass-300)" }}>HOLLOW'S EDGE</div>
            <div style={{ fontFamily: "var(--font-mono)", fontSize: 10, color: "var(--brass-500)" }}>18:32 · clear · 0.31 mi from spawn</div>
          </div>
          <div className="walkin-hud-corner" style={{ top: 16, right: 16, textAlign: "right" }}>
            <div style={{ fontFamily: "var(--font-mono)", fontSize: 10, color: "var(--success)" }}>● LIVE · test.embergloom.local</div>
            <div style={{ fontFamily: "var(--font-mono)", fontSize: 10, color: "var(--brass-500)" }}>this build · v0.4.2-alpha</div>
          </div>
          <div className="walkin-hud-corner" style={{ bottom: 16, left: 16 }}>
            <div style={{ display: "flex", gap: 4 }}>
              <div style={{ padding: "3px 8px", border: "1px solid var(--brass-700)", background: "rgba(0,0,0,0.6)", fontFamily: "var(--font-mono)", fontSize: 10, color: "var(--parchment-200)" }}>HP <span style={{ color: "var(--health-glow)" }}>120</span>/120</div>
              <div style={{ padding: "3px 8px", border: "1px solid var(--brass-700)", background: "rgba(0,0,0,0.6)", fontFamily: "var(--font-mono)", fontSize: 10, color: "var(--parchment-200)" }}>MP <span style={{ color: "var(--arcane-300)" }}>80</span>/80</div>
            </div>
          </div>
          <div className="walkin-hud-corner" style={{ bottom: 16, right: 16, fontFamily: "var(--font-mono)", fontSize: 10, color: "var(--brass-500)" }}>
            WASD walk · E inspect · ⌥E pop edit
          </div>
        </div>
      </div>
    </div>
  );
}

window.CommandPalette = CommandPalette;
window.ConscienceDetail = ConscienceDetail;
window.Timeline = Timeline;
window.WalkInModal = WalkInModal;
