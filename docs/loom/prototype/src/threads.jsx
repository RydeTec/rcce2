/* Loom — Threads overlay & Atlas
   Threads: visible reference lines emanating from the selected
   entity in the scene out to floating chip targets, each navigable.
   Atlas: a small zone-map overlay top-left, click to fly to. */

const { useState: useStateT, useRef: useRefT, useEffect: useEffectT } = React;

/* The SVG scene uses a 1400x820 viewBox with preserveAspectRatio="xMidYMid slice".
   To project from scene coords to screen coords inside the stage, we need to know
   the stage's actual width/height and how much of the viewBox is visible. */
function projectFromViewbox(stageRect, viewW, viewH, vx, vy) {
  const stageW = stageRect.width;
  const stageH = stageRect.height;
  /* xMidYMid slice: the viewbox is scaled to *cover* the stage, and centered. */
  const scale = Math.max(stageW / viewW, stageH / viewH);
  const scaledW = viewW * scale;
  const scaledH = viewH * scale;
  const offsetX = (stageW - scaledW) / 2;
  const offsetY = (stageH - scaledH) / 2;
  return { x: offsetX + vx * scale, y: offsetY + vy * scale };
}

/* Static placements for thread targets around the selected entity ---- */
/* These are in screen-space offsets (px) from the entity's screen pos.
   All anchored to the right (chip flows leftward from anchor), placed on
   the left half of the stage to avoid the composer on the right. */
const TARGET_POSITIONS = [
  { dx: -300, dy: -170, anchor: "right" },   // up-left far
  { dx: -310, dy:  -60, anchor: "right" },   // mid-left
  { dx: -290, dy:   60, anchor: "right" },   // low-left
  { dx: -160, dy: -200, anchor: "right" },   // up-left near
  { dx:  -50, dy: -200, anchor: "right" },   // straight up
  { dx: -210, dy:  150, anchor: "right" },   // down-left near
  { dx:  -90, dy:  170, anchor: "right" },   // straight down
];

function ThreadsOverlay({ selectedId, stageRef, onJump, recentRef, hideThreads }) {
  const [stageRect, setStageRect] = useStateT(null);

  useEffectT(() => {
    if (!stageRef.current) return;
    const update = () => {
      const r = stageRef.current.getBoundingClientRect();
      setStageRect({ width: r.width, height: r.height });
    };
    update();
    const ro = new ResizeObserver(update);
    ro.observe(stageRef.current);
    return () => ro.disconnect();
  }, [stageRef]);

  if (!selectedId || !stageRect || hideThreads) return null;

  /* Only show threads for entities placed in the scene */
  const scenePos = SCENE_POSITIONS[selectedId];
  if (!scenePos) return null;

  const entity = findEntity(selectedId);
  if (!entity) return null;

  /* Compute thread items from entity.refs */
  const refsList = (entity.refs || []).slice();
  if (entity.faction) refsList.unshift(entity.faction);
  if (entity.anim) refsList.push(entity.anim);
  /* Cap to TARGET_POSITIONS length */
  const items = refsList.slice(0, TARGET_POSITIONS.length).map((id, i) => {
    const e = findEntity(id);
    const pos = TARGET_POSITIONS[i];
    const broken = !e;
    return { id, name: e ? e.name : id, kind: kindOf(id), broken, pos };
  });

  /* Project entity center to screen */
  const center = projectFromViewbox(stageRect, 1400, 820, scenePos.x, scenePos.y);

  /* For each item, place its target chip at center + offset. Then compute the
     bezier curve from the entity outward to the target. Curve control points
     are biased away from the entity to give a natural arc. */
  const KIND_ICON = { actor: "◈", item: "⊟", spell: "✦", faction: "❖", emitter: "❀", anim: "▷", zone: "▥" };
  const KIND_LABEL = { actor: "ACTOR", item: "ITEM", spell: "SPELL", faction: "FACTION", emitter: "PARTICLE", anim: "ANIM SET", zone: "ZONE" };

  return (
    <>
      {/* SVG line layer */}
      <svg className="threads-svg" viewBox={`0 0 ${stageRect.width} ${stageRect.height}`} preserveAspectRatio="none">
        <defs>
          <radialGradient id="thread-origin" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="var(--arcane-300)" stopOpacity="1" />
            <stop offset="60%" stopColor="var(--arcane-500)" stopOpacity="0.5" />
            <stop offset="100%" stopColor="var(--arcane-500)" stopOpacity="0" />
          </radialGradient>
        </defs>
        {/* origin glow halo */}
        <circle cx={center.x} cy={center.y - 30} r="40" fill="url(#thread-origin)" opacity="0.7">
          <animate attributeName="r" values="40;52;40" dur="3s" repeatCount="indefinite" />
        </circle>
        {items.map((it, i) => {
          const tx = center.x + it.pos.dx;
          const ty = center.y + it.pos.dy;
          /* Bezier control point: pull outward */
          const cpx = (center.x + tx) / 2 + (it.pos.dx > 0 ? 30 : -30);
          const cpy = (center.y + ty) / 2 - 30;
          const d = `M ${center.x} ${center.y - 24} Q ${cpx} ${cpy} ${tx} ${ty}`;
          const isRecent = it.id === recentRef;
          return (
            <g key={i}>
              {/* base path */}
              <path d={d} className={`thread-line ${it.broken ? "broken" : ""} ${isRecent ? "recent" : ""}`}
                    style={{ animationDelay: `${i * 0.12}s` }} />
              {/* endpoint dot at target */}
              <circle cx={tx} cy={ty} r="3" fill={it.broken ? "var(--health-red)" : (isRecent ? "var(--arcane-300)" : "var(--arcane-500)")} />
              {/* endpoint glow */}
              <circle cx={tx} cy={ty} r="6" fill={it.broken ? "var(--health-red)" : "var(--arcane-500)"} opacity="0.4">
                <animate attributeName="r" values="6;9;6" dur="2s" repeatCount="indefinite" begin={`${i * 0.2}s`} />
                <animate attributeName="opacity" values="0.6;0.2;0.6" dur="2s" repeatCount="indefinite" begin={`${i * 0.2}s`} />
              </circle>
            </g>
          );
        })}
      </svg>
      {/* HTML target chips */}
      {items.map((it, i) => {
        const tx = center.x + it.pos.dx;
        const ty = center.y + it.pos.dy;
        const anchor = it.pos.anchor;
        return (
          <button key={i}
                  onClick={() => onJump(it.id)}
                  className={`thread-target ${it.broken ? "broken" : ""} ${it.id === recentRef ? "thread-chip-recent" : ""}`}
                  style={{
                    left: anchor === "left" ? `${tx + 8}px` : "auto",
                    right: anchor === "right" ? `${stageRect.width - tx + 8}px` : "auto",
                    top: `${ty - 12}px`,
                    animation: `fade-in 200ms ${i * 60 + 200}ms both`,
                  }}>
            <span className="thread-target-kind">{KIND_ICON[it.kind]}</span>
            <span>{it.name}</span>
            <span className="label-sub">{KIND_LABEL[it.kind]}</span>
          </button>
        );
      })}
    </>
  );
}

/* ============ ATLAS overlay ============ */
function AtlasOverlay({ activeZone, onPickZone }) {
  return (
    <div className="atlas-overlay slab slab-brass fade-in-down">
      <div className="atlas-overlay-head">
        <div className="atlas-overlay-title">Embergloom · World Map</div>
        <span className="hint">{WORLD.zones.length} zones</span>
      </div>
      <svg className="atlas-svg" viewBox="0 0 280 200">
        <defs>
          <radialGradient id="atlas-bg" cx="50%" cy="50%" r="60%">
            <stop offset="0%" stopColor="#1a1d2e" />
            <stop offset="100%" stopColor="#08090f" />
          </radialGradient>
          <pattern id="atlas-grid" patternUnits="userSpaceOnUse" width="20" height="20">
            <path d="M20 0 L0 0 L0 20" fill="none" stroke="#1a1d2e" strokeWidth="0.5" />
          </pattern>
        </defs>
        <rect width="280" height="200" fill="url(#atlas-bg)" />
        <rect width="280" height="200" fill="url(#atlas-grid)" opacity="0.6" />
        {/* portals */}
        {WORLD.portals.map((p, i) => {
          const from = WORLD.zones.find(z => z.id === p.from);
          const to   = WORLD.zones.find(z => z.id === p.to);
          if (!from || !to) return null;
          return (
            <line key={i}
                  x1={from.pos[0] * 280} y1={from.pos[1] * 200}
                  x2={to.pos[0]   * 280} y2={to.pos[1]   * 200}
                  stroke={p.broken ? "var(--health-red)" : "var(--brass-700)"}
                  strokeWidth={p.broken ? "1.4" : "0.8"}
                  strokeDasharray={p.broken ? "2 3" : "4 4"}
                  opacity="0.7" />
          );
        })}
        {/* zone nodes */}
        {WORLD.zones.map((z, i) => {
          const cx = z.pos[0] * 280;
          const cy = z.pos[1] * 200;
          const active = z.id === activeZone;
          return (
            <g key={z.id} className="atlas-zone" onClick={() => onPickZone(z.id)} transform={`translate(${cx},${cy})`}>
              {active && (
                <circle r="14" fill="none" stroke="var(--arcane-500)" strokeWidth="0.8" strokeDasharray="3 3">
                  <animate attributeName="r" values="14;18;14" dur="2.4s" repeatCount="indefinite" />
                </circle>
              )}
              <circle r={active ? 6 : 4}
                      fill={active ? "var(--arcane-500)" : (z.status === "stub" ? "transparent" : "var(--brass-500)")}
                      stroke={z.status === "stub" ? "var(--stone-500)" : (active ? "var(--arcane-300)" : "var(--brass-700)")}
                      strokeWidth="1"
                      strokeDasharray={z.status === "stub" ? "2 2" : "0"} />
              <text y={active ? -12 : -8} textAnchor="middle"
                    fontFamily="var(--font-sans)" fontSize="9"
                    fill={active ? "var(--parchment-100)" : "var(--fg-muted)"}
                    fontWeight={active ? "600" : "400"}>
                {z.name}
              </text>
              {active && (
                <text y="14" textAnchor="middle"
                      fontFamily="var(--font-mono)" fontSize="7.5"
                      fill="var(--arcane-300)" letterSpacing="0.5">
                  ◀ YOU ARE HERE
                </text>
              )}
            </g>
          );
        })}
      </svg>
      <div className="atlas-overlay-foot">
        <span>● live <span style={{ color: "var(--brass-500)" }}>○ draft</span> <span style={{ color: "var(--stone-400)" }}>◌ stub</span></span>
        <span style={{ color: "var(--health-glow)" }}>— broken portal</span>
      </div>
    </div>
  );
}

window.ThreadsOverlay = ThreadsOverlay;
window.AtlasOverlay = AtlasOverlay;
