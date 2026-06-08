/* Loom — Composer
   Right-side panel that shows the focused entity. Sections are
   collapsible; the most recent edit shimmers; references are
   navigable threads. */

const { useState: useStateComp } = React;

/* Rarity color helper -------------------------------------- */
const RARITY_COLOR = {
  common: "var(--rarity-common)",
  uncommon: "var(--rarity-uncommon)",
  rare: "var(--rarity-rare)",
  epic: "var(--rarity-epic)",
  legendary: "var(--rarity-legendary)",
  artifact: "var(--rarity-artifact)",
};

/* A small chip representing a thread to another entity ----- */
function ThreadChip({ id, onJump, recent, broken, hint }) {
  const e = findEntity(id);
  if (!e) return (
    <button onClick={() => {}} className="thread-chip thread-chip-broken">
      <span className="thread-chip-dot" />
      <span className="thread-chip-label">{id} <em>unresolved</em></span>
    </button>
  );
  const k = kindOf(id);
  const KIND_ICON = { actor: "◈", item: "⊟", spell: "✦", faction: "❖", emitter: "❀", anim: "▷", zone: "▥" };
  return (
    <button onClick={() => onJump && onJump(id)}
            className={`thread-chip ${recent ? "thread-chip-recent" : ""} ${broken ? "thread-chip-broken" : ""}`}>
      <span className="thread-chip-kind">{KIND_ICON[k] || "·"}</span>
      <span className="thread-chip-label">{e.name}</span>
      {hint && <span className="thread-chip-hint">{hint}</span>}
      {recent && <span className="thread-chip-pulse" />}
      <span className="thread-chip-arrow">↗</span>
    </button>
  );
}

/* Collapsible section ------------------------------------- */
function Section({ title, count, defaultOpen = true, children, accent, badge }) {
  const [open, setOpen] = useStateComp(defaultOpen);
  return (
    <div className="cmp-section">
      <button className="cmp-section-head" onClick={() => setOpen(o => !o)}>
        <span className="cmp-section-chevron" style={{ transform: open ? "rotate(90deg)" : "rotate(0deg)" }}>▸</span>
        <span className="cmp-section-title" style={{ color: accent }}>{title}</span>
        {typeof count === "number" && <span className="cmp-section-count">{count}</span>}
        {badge && <span className={`cmp-section-badge cmp-section-badge-${badge.tone}`}>{badge.label}</span>}
      </button>
      {open && <div className="cmp-section-body">{children}</div>}
    </div>
  );
}

/* Row primitive ------------------------------------------- */
function Row({ label, children, hint }) {
  return (
    <div className="cmp-row">
      <div className="cmp-row-label">{label}</div>
      <div className="cmp-row-value">{children}</div>
      {hint && <div className="cmp-row-hint">{hint}</div>}
    </div>
  );
}

/* Slider ------------------------------------------------- */
function MiniSlider({ value, max, color, decorate }) {
  const pct = Math.max(0, Math.min(100, (value / max) * 100));
  return (
    <div className="mini-slider">
      <div className="mini-slider-track">
        <div className="mini-slider-fill" style={{ width: `${pct}%`, background: color }} />
        {decorate && <div className="mini-slider-decorate" style={{ left: `${decorate}%` }} />}
      </div>
      <div className="mini-slider-numbers"><span>{value}</span><span className="mini-slider-max">/ {max}</span></div>
    </div>
  );
}

/* ============ Composer for an ACTOR ============ */
function ActorComposer({ actor, recentRef, onJump, inboundRefs }) {
  const faction = findEntity(actor.faction);
  const mesh = actor.mesh;
  const anim = findEntity(actor.anim);
  const refs = actor.refs || [];
  /* Categorize refs */
  const itemRefs = refs.filter(r => kindOf(r) === "item");
  const spellRefs = refs.filter(r => kindOf(r) === "spell");

  return (
    <>
      {/* Header */}
      <div className="cmp-head">
        <div className="cmp-head-eyebrow">
          <span className="cmp-kind">◈ Actor</span>
          {actor.drafted && <span className="cmp-draft-badge">DRAFT · UNSAVED</span>}
        </div>
        <div className="cmp-head-title">{actor.name}</div>
        <div className="cmp-head-sub">
          <span className="mono">{actor.id}</span>
          <span className="cmp-dot">·</span>
          <span>Lv {actor.level}</span>
          <span className="cmp-dot">·</span>
          <span>{actor.race}</span>
          {actor.hostile && <><span className="cmp-dot">·</span><span style={{ color: "var(--health-glow)" }}>Hostile</span></>}
        </div>
      </div>

      {/* Quick portrait preview */}
      <div className="cmp-portrait">
        <div className="cmp-portrait-frame">
          <svg viewBox="-40 -60 80 80" width="100%" height="100%">
            <defs>
              <radialGradient id="portrait-bg" cx="50%" cy="50%" r="50%">
                <stop offset="0%" stopColor="#28371d" />
                <stop offset="100%" stopColor="#0a0c14" />
              </radialGradient>
            </defs>
            <rect x="-40" y="-60" width="80" height="80" fill="url(#portrait-bg)" />
            <g transform="translate(0,12) scale(1.5)">
              <EntitySprite kind={actor.id} race={actor.race} />
            </g>
          </svg>
          <div className="cmp-portrait-marks">
            <span className="cmp-portrait-mark">mesh · {mesh}</span>
            <span className="cmp-portrait-mark">anim · {anim ? anim.name : "—"}</span>
          </div>
        </div>
        <div className="cmp-portrait-actions">
          <button className="btn btn-ghost" onClick={() => onJump && onJump(mesh)}>Replace mesh</button>
          <button className="btn btn-ghost">Preview pose</button>
        </div>
      </div>

      <div className="cmp-divider" />

      <div className="cmp-body scroll">
      {/* Identity */}
      <Section title="Identity" defaultOpen>
        <Row label="Name"><input className="cmp-input" defaultValue={actor.name} /></Row>
        <Row label="Race"><span className="cmp-pill">{actor.race}</span></Row>
        <Row label="Level"><span className="cmp-num">{actor.level}</span></Row>
        <Row label="Faction">
          <ThreadChip id={actor.faction} onJump={onJump} />
        </Row>
      </Section>

      {/* Vitals */}
      <Section title="Vitals" defaultOpen>
        <Row label="Health"><MiniSlider value={actor.hp} max={actor.hp * 1.5} color="linear-gradient(90deg, var(--health-red), var(--health-glow))" /></Row>
        <Row label="Mana"><MiniSlider value={actor.mana} max={Math.max(actor.mana, 1) * 1.5} color="linear-gradient(90deg, var(--arcane-700), var(--arcane-400))" decorate={70} hint="Tuning hint: mana cap" /></Row>
      </Section>

      {/* Wardrobe */}
      <Section title="Wardrobe & Loadout" count={itemRefs.length}>
        <div className="cmp-chip-grid">
          {itemRefs.map(id => (
            <ThreadChip key={id} id={id} onJump={onJump} />
          ))}
        </div>
      </Section>

      {/* Abilities */}
      <Section title="Abilities" count={spellRefs.length}
               badge={recentRef && spellRefs.includes(recentRef) ? { tone: "arcane", label: "JUST BOUND" } : null}>
        <div className="cmp-chip-grid">
          {spellRefs.map(id => (
            <ThreadChip key={id} id={id} onJump={onJump} recent={id === recentRef} />
          ))}
          <button className="cmp-add-chip">+ bind spell</button>
        </div>
        {recentRef && spellRefs.includes(recentRef) && (
          <div className="cmp-hint cmp-hint-arcane">
            <span className="cmp-hint-icon">✦</span>
            <span><b>Firebolt</b> is in the top 6% of level-5 spells for damage. <a onClick={() => onJump("spell.firebolt")}>Open</a> to tune, or <a>balance vs. similar</a>.</span>
          </div>
        )}
      </Section>

      {/* Animation */}
      <Section title="Animation Set" defaultOpen={false}>
        <Row label="Set"><ThreadChip id={actor.anim} onJump={onJump} /></Row>
        <Row label="Idle"><span className="cmp-pill cmp-pill-mono">idle_caster_breathe</span></Row>
        <Row label="Walk"><span className="cmp-pill cmp-pill-mono">walk_robed</span></Row>
        <Row label="Cast"><span className="cmp-pill cmp-pill-mono">cast_two_handed</span></Row>
        <Row label="Death"><span className="cmp-pill cmp-pill-mono">death_collapse</span></Row>
      </Section>

      {/* References */}
      <Section title={`Referenced by`} count={inboundRefs.length} defaultOpen={false}>
        {inboundRefs.length === 0 ? (
          <div className="cmp-empty">No other entity references this actor yet. It is safe to delete or rename.</div>
        ) : (
          <div className="cmp-chip-grid">
            {inboundRefs.map(id => <ThreadChip key={id} id={id} onJump={onJump} hint="uses this" />)}
          </div>
        )}
      </Section>
      </div>

      {/* Footer actions */}
      <div className="cmp-footer">
        <button className="btn btn-arcane">Push to test server</button>
        <button className="btn btn-brass">Save</button>
        <button className="btn btn-ghost">Duplicate</button>
        <button className="btn btn-ghost" style={{ color: "var(--health-glow)" }}>Delete</button>
      </div>
    </>
  );
}

/* ============ Composer for a SPELL ============ */
function SpellComposer({ spell, onJump, inboundRefs }) {
  return (
    <>
      <div className="cmp-head">
        <div className="cmp-head-eyebrow">
          <span className="cmp-kind" style={{ color: "var(--arcane-300)" }}>✦ Spell</span>
          {spell.hot && <span className="cmp-tag cmp-tag-warn">HIGHLY REFERENCED</span>}
          {spell.broken && <span className="cmp-tag cmp-tag-danger">BROKEN REF</span>}
        </div>
        <div className="cmp-head-title">{spell.name}</div>
        <div className="cmp-head-sub">
          <span className="mono">{spell.id}</span>
          <span className="cmp-dot">·</span>
          <span>{spell.school}</span>
          <span className="cmp-dot">·</span>
          <span>Lv {spell.level}</span>
        </div>
      </div>

      <div className="cmp-spell-preview">
        <div className="cmp-spell-canvas">
          <svg viewBox="0 0 240 110" width="100%" height="100%">
            <defs>
              <radialGradient id="spell-bg" cx="50%" cy="50%" r="50%">
                <stop offset="0%" stopColor="#1a1d2a" />
                <stop offset="100%" stopColor="#08090f" />
              </radialGradient>
              <radialGradient id="firebolt-glow" cx="50%" cy="50%" r="50%">
                <stop offset="0%" stopColor="#ffec80" />
                <stop offset="30%" stopColor="#ff8a3c" />
                <stop offset="70%" stopColor="#b8302a" stopOpacity="0.6" />
                <stop offset="100%" stopColor="#b8302a" stopOpacity="0" />
              </radialGradient>
            </defs>
            <rect width="240" height="110" fill="url(#spell-bg)" />
            {/* caster */}
            <g transform="translate(40,80)">
              <ellipse cx="0" cy="3" rx="10" ry="3" fill="#000" opacity="0.6" />
              <path d="M-8,1 L-6,-14 L6,-14 L8,1 Z" fill="#3a4a2a" />
              <circle cx="0" cy="-18" r="4" fill="#5d8a3a" />
              <line x1="10" y1="3" x2="13" y2="-16" stroke="#3a2618" strokeWidth="1" />
              <circle cx="13" cy="-17" r="2" fill="#ff8a3c" />
            </g>
            {/* projectile + trail */}
            <g>
              <circle cx="130" cy="70" r="14" fill="url(#firebolt-glow)">
                <animate attributeName="cx" values="60;200" dur="1.8s" repeatCount="indefinite" />
              </circle>
              <circle cx="130" cy="70" r="3" fill="#fff">
                <animate attributeName="cx" values="60;200" dur="1.8s" repeatCount="indefinite" />
              </circle>
            </g>
            {/* target */}
            <g transform="translate(200,80)">
              <ellipse cx="0" cy="3" rx="12" ry="3" fill="#000" opacity="0.6" />
              <ellipse cx="0" cy="-6" rx="11" ry="6" fill="#3a3736" />
              <ellipse cx="-9" cy="-9" rx="4" ry="3.5" fill="#3a3736" />
              <line x1="9" y1="-4" x2="13" y2="-3" stroke="#3a3736" strokeWidth="1.4" />
            </g>
          </svg>
        </div>
        <div className="cmp-spell-meta">
          <div className="cmp-spell-numbers">
            <div><div className="eyebrow">Damage</div><div className="cmp-num cmp-num-lg">{Math.abs(spell.damage)}</div><div className="hint">{spell.damageType}</div></div>
            <div><div className="eyebrow">Mana</div><div className="cmp-num cmp-num-lg">{spell.manaCost}</div><div className="hint">per cast</div></div>
            <div><div className="eyebrow">Cast</div><div className="cmp-num cmp-num-lg">{spell.cast}s</div><div className="hint">channel</div></div>
            <div><div className="eyebrow">Range</div><div className="cmp-num cmp-num-lg">{spell.range}m</div><div className="hint">max</div></div>
          </div>
        </div>
      </div>

      {/* Balance hint */}
      {spell.hot && (
        <div className="cmp-hint cmp-hint-warn">
          <span className="cmp-hint-icon">⚖</span>
          <span><b>Balance:</b> damage <b>{spell.damage}</b> is in the top 6% of level-{spell.level} spells (median 28). Three player complaints reference this spell. <a>Suggest values</a></span>
        </div>
      )}

      <div className="cmp-body scroll">
      <Section title="Visual & Sound" defaultOpen>
        <Row label="Particle"><ThreadChip id={spell.refs.find(r => kindOf(r) === "emitter")} onJump={onJump} /></Row>
        <Row label="Cast anim"><ThreadChip id={spell.refs.find(r => kindOf(r) === "anim")} onJump={onJump} /></Row>
      </Section>

      <Section title="Script binding" defaultOpen>
        <div className="cmp-script-row">
          <span className="mono cmp-script-name">scripts/spells/firebolt.bb</span>
          <button className="btn btn-ghost">Open externally</button>
        </div>
        <div className="cmp-script-preview">
          <span style={{ color: "var(--brass-500)" }}>function</span> <span style={{ color: "var(--arcane-300)" }}>OnCast</span>(target)<br/>
          &nbsp;&nbsp;<span style={{ color: "var(--brass-500)" }}>dmg</span> = <span style={{ color: "var(--health-glow)" }}>50</span> + caster.lvl * <span style={{ color: "var(--health-glow)" }}>2</span><br/>
          &nbsp;&nbsp;target.<span style={{ color: "var(--arcane-300)" }}>damage</span>(dmg, <span style={{ color: "var(--success)" }}>"fire"</span>)<br/>
          <span style={{ color: "var(--brass-500)" }}>endfunction</span>
        </div>
      </Section>

      <Section title="Referenced by" count={inboundRefs.length} defaultOpen>
        <div className="cmp-chip-grid">
          {inboundRefs.map(id => <ThreadChip key={id} id={id} onJump={onJump} hint="casts this" />)}
        </div>
      </Section>
      </div>

      <div className="cmp-footer">
        <button className="btn btn-arcane">Push to test server</button>
        <button className="btn btn-brass">Save</button>
        <button className="btn btn-ghost">Test cast</button>
      </div>
    </>
  );
}

/* ============ Composer for a FACTION ============ */
function FactionComposer({ faction, onJump, inboundRefs }) {
  return (
    <>
      <div className="cmp-head">
        <div className="cmp-head-eyebrow"><span className="cmp-kind">❖ Faction</span></div>
        <div className="cmp-head-title">{faction.name}</div>
        <div className="cmp-head-sub"><span className="mono">{faction.id}</span></div>
      </div>
      <Section title="Description" defaultOpen>
        <div className="cmp-lore">{faction.description}</div>
      </Section>
      <Section title="Members" count={inboundRefs.filter(r => kindOf(r) === "actor").length} defaultOpen>
        <div className="cmp-chip-grid">
          {inboundRefs.filter(r => kindOf(r) === "actor").map(id =>
            <ThreadChip key={id} id={id} onJump={onJump} />
          )}
        </div>
      </Section>
      <Section title="Reputation matrix" defaultOpen={false}>
        <div className="cmp-rep-matrix">
          {WORLD.factions.map(f => (
            <div key={f.id} className="cmp-rep-row">
              <span className="cmp-rep-name">{f.name}</span>
              <div className="cmp-rep-bar">
                <div className="cmp-rep-fill" style={{
                  width: `${Math.random() * 70 + 15}%`,
                  background: f.color,
                }} />
              </div>
            </div>
          ))}
        </div>
      </Section>
    </>
  );
}

/* ============ Generic dispatch ============ */
function Composer({ entityId, recentRef, onJump, onClose, allRefs }) {
  if (!entityId) return null;
  const e = findEntity(entityId);
  if (!e) return null;
  const k = kindOf(entityId);
  /* Compute inbound refs */
  const inbound = [];
  WORLD.actors.forEach(a => { if ((a.refs || []).includes(entityId) || a.faction === entityId) inbound.push(a.id); });
  WORLD.items.forEach(i  => { if ((i.refs || []).includes(entityId)) inbound.push(i.id); });
  WORLD.spells.forEach(s => { if ((s.refs || []).includes(entityId)) inbound.push(s.id); });

  return (
    <aside className="composer slab slab-brass slide-in-right">
      <div className="composer-grip" onClick={onClose}>×</div>
      {k === "actor"   && <ActorComposer    actor={e}    recentRef={recentRef} onJump={onJump} inboundRefs={inbound} />}
      {k === "spell"   && <SpellComposer    spell={e}              onJump={onJump} inboundRefs={inbound} />}
      {k === "faction" && <FactionComposer  faction={e}            onJump={onJump} inboundRefs={inbound} />}
      {k === "item"    && (
        <>
          <div className="cmp-head">
            <div className="cmp-head-eyebrow"><span className="cmp-kind">⊟ Item</span><span className="cmp-pill" style={{ color: RARITY_COLOR[e.rarity], borderColor: RARITY_COLOR[e.rarity] }}>{e.rarity}</span></div>
            <div className="cmp-head-title">{e.name}</div>
            <div className="cmp-head-sub"><span className="mono">{e.id}</span><span className="cmp-dot">·</span><span>Lv {e.lvl}</span><span className="cmp-dot">·</span><span>{e.slot}</span></div>
          </div>
          <div className="cmp-body scroll">
          <Section title="Appearance" defaultOpen>
            <Row label="Mesh"><span className="cmp-pill cmp-pill-mono">{e.mesh || "—"}</span></Row>
            <Row label="Icon"><span className="cmp-pill cmp-pill-mono">{e.icon || "—"}</span></Row>
          </Section>
          <Section title="Referenced by" count={inbound.length} defaultOpen>
            <div className="cmp-chip-grid">
              {inbound.map(id => <ThreadChip key={id} id={id} onJump={onJump} />)}
            </div>
          </Section>
          </div>
        </>
      )}
      {k === "emitter" && (
        <>
          <div className="cmp-head">
            <div className="cmp-head-eyebrow"><span className="cmp-kind">❀ Particle emitter</span></div>
            <div className="cmp-head-title">{e.name}</div>
            <div className="cmp-head-sub"><span className="mono">{e.id}</span></div>
          </div>
          <div className="cmp-body scroll">
          <Section title="Properties" defaultOpen>
            <Row label="Color"><span className="cmp-color-swatch" style={{ background: e.color }} /></Row>
            <Row label="Lifetime"><span className="cmp-num">{e.lifetime}s</span></Row>
          </Section>
          </div>
        </>
      )}
      {k === "anim" && (
        <>
          <div className="cmp-head">
            <div className="cmp-head-eyebrow"><span className="cmp-kind">▷ Animation set</span></div>
            <div className="cmp-head-title">{e.name}</div>
            <div className="cmp-head-sub"><span className="mono">{e.id}</span><span className="cmp-dot">·</span><span>{e.clips} clips</span></div>
          </div>
          <div className="cmp-body scroll">
          <Section title="Referenced by" count={inbound.length} defaultOpen>
            <div className="cmp-chip-grid">
              {inbound.map(id => <ThreadChip key={id} id={id} onJump={onJump} />)}
            </div>
          </Section>
          </div>
        </>
      )}
    </aside>
  );
}

window.Composer = Composer;
window.ThreadChip = ThreadChip;
