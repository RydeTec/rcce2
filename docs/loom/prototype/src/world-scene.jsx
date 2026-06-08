/* Loom — World Scene (Hollow's Edge)
   A stylized 3/4-view forest clearing rendered as SVG. Entities
   are positioned on a ground plane; selecting one highlights it
   and reveals its outgoing threads. */

const { useState: useStateScene, useRef: useRefScene, useEffect: useEffectScene } = React;

/* Where on the canvas each entity lives, plus a depth value used
   for painter's-algorithm sorting. Coordinates are SVG units in a
   1400 × 820 viewBox. */
const SCENE_POSITIONS = {
  "actor.hermit":        { x: 880, y: 470, depth: 470, label: "above" },
  "actor.goblin_scout":  { x: 380, y: 540, depth: 540, label: "above" },
  "actor.goblin_scout_2":{ x: 460, y: 580, depth: 580, label: null },
  "actor.goblin_scout_3":{ x: 290, y: 590, depth: 590, label: null },
  "actor.goblin_shaman": { x: 700, y: 540, depth: 540, label: "above" },
  "actor.dire_wolf":     { x: 980, y: 590, depth: 590, label: "above" },
  "actor.dire_wolf_2":   { x: 1060,y: 550, depth: 550, label: null },
  /* Scenery is not selectable but lives in the same layer ordering */
};

const SCENERY = [
  /* Trees — heavy on the edges, thinning toward the center */
  { kind: "tree", x: 110, y: 380, depth: 380, scale: 1.1, variant: "pine" },
  { kind: "tree", x: 180, y: 460, depth: 460, scale: 1.3, variant: "pine" },
  { kind: "tree", x: 250, y: 520, depth: 520, scale: 1.0, variant: "pine" },
  { kind: "tree", x: 90,  y: 540, depth: 540, scale: 1.5, variant: "pine" },
  { kind: "tree", x: 60,  y: 620, depth: 620, scale: 1.8, variant: "pine" },
  { kind: "tree", x: 220, y: 700, depth: 700, scale: 1.2, variant: "pine" },
  { kind: "tree", x: 1280,y: 410, depth: 410, scale: 1.2, variant: "oak"  },
  { kind: "tree", x: 1220,y: 480, depth: 480, scale: 1.4, variant: "oak"  },
  { kind: "tree", x: 1310,y: 580, depth: 580, scale: 1.6, variant: "oak"  },
  { kind: "tree", x: 1180,y: 660, depth: 660, scale: 1.3, variant: "pine" },
  { kind: "tree", x: 1090,y: 720, depth: 720, scale: 1.1, variant: "pine" },
  { kind: "tree", x: 420, y: 380, depth: 380, scale: 0.8, variant: "pine" },
  { kind: "tree", x: 900, y: 380, depth: 380, scale: 0.9, variant: "pine" },
  { kind: "tree", x: 1060,y: 410, depth: 410, scale: 1.0, variant: "pine" },
  { kind: "tree", x: 340, y: 420, depth: 420, scale: 0.9, variant: "pine" },
  { kind: "tree", x: 680, y: 690, depth: 690, scale: 1.1, variant: "pine" },
  { kind: "tree", x: 820, y: 700, depth: 700, scale: 1.0, variant: "oak" },
  /* Rocks */
  { kind: "rock", x: 360, y: 460, depth: 460, scale: 1.0 },
  { kind: "rock", x: 1010,y: 480, depth: 480, scale: 0.8 },
  { kind: "rock", x: 880, y: 620, depth: 620, scale: 1.2 },
  /* Cottage (Cassian's hermit hut) */
  { kind: "cottage", x: 880, y: 470, depth: 460, scale: 1 },
  /* Ground patches (moss, dirt) - just for visual variety */
  { kind: "moss",  x: 500, y: 560, depth: 555, w: 220, h: 80 },
  { kind: "dirt",  x: 850, y: 580, depth: 575, w: 180, h: 60 },
  /* Trigger volume (an invisible quest trigger near the cottage) */
  { kind: "trigger", x: 820, y: 540, depth: 530, w: 160, h: 80, label: "trg_hermit_meet" },
  /* Light source (warm cottage light) */
  { kind: "light", x: 900, y: 460, depth: 1000, color: "#e6c87a" },
  /* Portals at edges */
  { kind: "portal", x: 60,  y: 540, depth: 800, dest: "zone.ravensreach", label: "West trail → Ravensreach" },
  { kind: "portal", x: 700, y: 750, depth: 900, dest: "zone.black_pine",  label: "South path → Black Pine Hollow" },
];

/* Per-entity sprite rendering (tiny stylized glyphs) ------ */
function EntitySprite({ kind, race, beast, selected }) {
  /* Goblin shaman */
  if (kind === "actor.goblin_shaman") {
    return (
      <g>
        {/* shadow */}
        <ellipse cx="0" cy="3" rx="14" ry="4" fill="#000" opacity="0.5" />
        {/* robe */}
        <path d="M-12,2 L-9,-22 L9,-22 L12,2 Z" fill="#3a4a2a" stroke="#1f2914" strokeWidth="0.8" />
        {/* shoulder shadow */}
        <path d="M-12,2 L-9,-22 L-6,-22 L-9,2 Z" fill="#1f2914" opacity="0.6" />
        {/* head (greenish goblin) */}
        <circle cx="0" cy="-26" r="6" fill="#5d8a3a" stroke="#2c4316" strokeWidth="0.6" />
        {/* big ear */}
        <path d="M5,-28 L9,-30 L7,-24 Z" fill="#5d8a3a" stroke="#2c4316" strokeWidth="0.5" />
        <path d="M-5,-28 L-9,-30 L-7,-24 Z" fill="#5d8a3a" stroke="#2c4316" strokeWidth="0.5" />
        {/* eyes (yellow glint) */}
        <circle cx="-2" cy="-26" r="0.8" fill="#e6c87a" />
        <circle cx="2"  cy="-26" r="0.8" fill="#e6c87a" />
        {/* staff with bone tip */}
        <line x1="14" y1="6" x2="11" y2="-28" stroke="#3a2618" strokeWidth="1.4" strokeLinecap="round" />
        <circle cx="11" cy="-29" r="2.4" fill="#d8d1c2" stroke="#3a2618" strokeWidth="0.5" />
        <circle cx="11" cy="-29" r="3.5" fill="#ff8a3c" opacity="0.4">
          <animate attributeName="r" values="3.5;5;3.5" dur="1.8s" repeatCount="indefinite" />
          <animate attributeName="opacity" values="0.4;0.7;0.4" dur="1.8s" repeatCount="indefinite" />
        </circle>
      </g>
    );
  }
  /* Generic goblin scout */
  if (kind === "actor.goblin_scout" || race === "goblin") {
    return (
      <g>
        <ellipse cx="0" cy="2" rx="10" ry="3" fill="#000" opacity="0.5" />
        <path d="M-8,1 L-6,-16 L6,-16 L8,1 Z" fill="#2c4316" stroke="#161e0a" strokeWidth="0.6" />
        <circle cx="0" cy="-20" r="4.5" fill="#5d8a3a" stroke="#2c4316" strokeWidth="0.5" />
        <path d="M4,-22 L7,-24 L5,-18 Z" fill="#5d8a3a" stroke="#2c4316" strokeWidth="0.4" />
        <path d="M-4,-22 L-7,-24 L-5,-18 Z" fill="#5d8a3a" stroke="#2c4316" strokeWidth="0.4" />
        <circle cx="-1.5" cy="-20" r="0.6" fill="#ffec80" />
        <circle cx="1.5" cy="-20" r="0.6" fill="#ffec80" />
        <line x1="10" y1="4" x2="13" y2="-12" stroke="#cfcabe" strokeWidth="1" strokeLinecap="round" />
      </g>
    );
  }
  /* Dire wolf */
  if (kind === "actor.dire_wolf" || beast) {
    return (
      <g>
        <ellipse cx="0" cy="3" rx="18" ry="4" fill="#000" opacity="0.5" />
        <ellipse cx="0" cy="-6" rx="16" ry="8" fill="#3a3736" stroke="#1c1816" strokeWidth="0.7" />
        <ellipse cx="-14" cy="-9" rx="6" ry="5" fill="#3a3736" stroke="#1c1816" strokeWidth="0.6" />
        <path d="M-19,-10 L-22,-13 L-18,-12 Z" fill="#3a3736" stroke="#1c1816" strokeWidth="0.4" />
        <path d="M-16,-12 L-19,-15 L-15,-14 Z" fill="#3a3736" stroke="#1c1816" strokeWidth="0.4" />
        <circle cx="-16" cy="-9" r="0.7" fill="#ff5b50" />
        <line x1="14" y1="-4" x2="20" y2="-2" stroke="#3a3736" strokeWidth="2" strokeLinecap="round" />
        {/* legs */}
        <line x1="-8" y1="0" x2="-10" y2="6" stroke="#1c1816" strokeWidth="2" strokeLinecap="round" />
        <line x1="8"  y1="0" x2="10"  y2="6" stroke="#1c1816" strokeWidth="2" strokeLinecap="round" />
      </g>
    );
  }
  /* Hermit (human, robed) */
  if (kind === "actor.hermit" || race === "human") {
    return (
      <g>
        <ellipse cx="0" cy="3" rx="13" ry="4" fill="#000" opacity="0.5" />
        <path d="M-11,2 L-9,-24 L9,-24 L11,2 Z" fill="#6a563b" stroke="#2c2418" strokeWidth="0.8" />
        <circle cx="0" cy="-28" r="6" fill="#d6a98a" stroke="#3a2618" strokeWidth="0.5" />
        <path d="M-6,-32 L6,-32 L4,-22 L-4,-22 Z" fill="#3a2418" />
        <line x1="14" y1="6" x2="12" y2="-32" stroke="#3a2418" strokeWidth="1.2" strokeLinecap="round" />
      </g>
    );
  }
  return null;
}

function Tree({ x, y, scale, variant, depth }) {
  const baseY = 0;
  const shadow = (
    <ellipse cx="0" cy={baseY + 2} rx={14 * scale} ry={4 * scale} fill="#000" opacity="0.55" />
  );
  if (variant === "oak") {
    return (
      <g transform={`translate(${x},${y}) scale(${scale})`}>
        {shadow}
        <line x1="0" y1="0" x2="0" y2="-30" stroke="#3a2418" strokeWidth="3.5" />
        <ellipse cx="0" cy="-34" rx="22" ry="20" fill="#3d5a2a" stroke="#1e3014" strokeWidth="1" />
        <ellipse cx="-8" cy="-40" rx="12" ry="10" fill="#4d7036" />
        <ellipse cx="6" cy="-44" rx="9" ry="8" fill="#557a3d" />
      </g>
    );
  }
  return (
    <g transform={`translate(${x},${y}) scale(${scale})`}>
      {shadow}
      <line x1="0" y1="0" x2="0" y2="-22" stroke="#3a2418" strokeWidth="2.4" />
      <path d="M0,-58 L-14,-30 L-8,-30 L-18,-12 L-10,-12 L-20,4 L20,4 L10,-12 L18,-12 L8,-30 L14,-30 Z"
            fill="#2a4319" stroke="#1a2812" strokeWidth="0.8" />
      <path d="M-1,-58 L-14,-30 L-8,-30 L-18,-12 L-10,-12 L-20,4 L0,4 Z" fill="#1f3414" opacity="0.6" />
    </g>
  );
}

function Rock({ x, y, scale }) {
  return (
    <g transform={`translate(${x},${y}) scale(${scale})`}>
      <ellipse cx="0" cy="2" rx="16" ry="4" fill="#000" opacity="0.5" />
      <path d="M-14,2 Q-16,-8 -6,-12 Q4,-18 12,-10 Q18,0 14,3 Z" fill="#4f4b48" stroke="#1f1d1c" strokeWidth="0.8" />
      <path d="M-14,2 Q-16,-8 -6,-12 Q-4,-10 -8,-2 Z" fill="#3a3736" />
    </g>
  );
}

function Cottage({ x, y, scale, glow }) {
  return (
    <g transform={`translate(${x},${y}) scale(${scale})`}>
      {/* warm light pool */}
      {glow && (
        <ellipse cx="0" cy="0" rx="100" ry="40" fill="url(#cottage-light)" opacity="0.85" />
      )}
      <ellipse cx="0" cy="6" rx="42" ry="6" fill="#000" opacity="0.55" />
      {/* base */}
      <path d="M-32,4 L-32,-22 L32,-22 L32,4 Z" fill="#6a563b" stroke="#2c2418" strokeWidth="1" />
      <path d="M-32,4 L-32,-22 L-26,-22 L-26,4 Z" fill="#3a2c18" />
      {/* roof */}
      <path d="M-38,-22 L0,-50 L38,-22 Z" fill="#3a2418" stroke="#1c0e08" strokeWidth="1" />
      <path d="M-38,-22 L0,-50 L-30,-22 Z" fill="#251509" />
      {/* door */}
      <rect x="-5" y="-16" width="10" height="20" fill="#1c0e08" stroke="#3a2418" strokeWidth="0.5" />
      <circle cx="3" cy="-6" r="0.6" fill="#c9a44a" />
      {/* window with warm light */}
      <rect x="-22" y="-16" width="9" height="9" fill="#e6c87a" stroke="#3a2418" strokeWidth="0.6" />
      <line x1="-17.5" y1="-16" x2="-17.5" y2="-7" stroke="#3a2418" strokeWidth="0.4" />
      <line x1="-22" y1="-11.5" x2="-13" y2="-11.5" stroke="#3a2418" strokeWidth="0.4" />
      <rect x="13" y="-16" width="9" height="9" fill="#e6c87a" stroke="#3a2418" strokeWidth="0.6" />
      <line x1="17.5" y1="-16" x2="17.5" y2="-7" stroke="#3a2418" strokeWidth="0.4" />
      <line x1="13" y1="-11.5" x2="22" y2="-11.5" stroke="#3a2418" strokeWidth="0.4" />
      {/* chimney */}
      <rect x="14" y="-44" width="6" height="14" fill="#4f4b48" stroke="#1c1816" strokeWidth="0.5" />
    </g>
  );
}

function PortalMark({ x, y, label, dest }) {
  return (
    <g transform={`translate(${x},${y})`}>
      <ellipse cx="0" cy="2" rx="22" ry="6" fill="#000" opacity="0.6" />
      <ellipse cx="0" cy="-2" rx="20" ry="22" fill="url(#portal-fill)" stroke="var(--arcane-500)" strokeWidth="1.4" opacity="0.9">
        <animate attributeName="opacity" values="0.7;1;0.7" dur="2.4s" repeatCount="indefinite" />
      </ellipse>
      <ellipse cx="0" cy="-2" rx="14" ry="16" fill="none" stroke="var(--arcane-400)" strokeWidth="0.6" strokeDasharray="2 3" />
      <text y="-32" textAnchor="middle" fontFamily="var(--font-sans)" fontSize="11"
            fill="var(--arcane-300)" letterSpacing="1.5" style={{ textTransform: "uppercase" }}>
        {label}
      </text>
    </g>
  );
}

function TriggerVolume({ x, y, w, h, label }) {
  return (
    <g transform={`translate(${x - w/2},${y - h/2})`}>
      <rect x="0" y="0" width={w} height={h} fill="rgba(184,48,42,0.06)" stroke="var(--health-red)"
            strokeWidth="1" strokeDasharray="4 4" rx="2" />
      <text x="6" y="14" fontFamily="var(--font-mono)" fontSize="10" fill="#ff9a93" letterSpacing="0.5">
        ⚠ {label}
      </text>
    </g>
  );
}

function GroundPatch({ x, y, w, h, kind }) {
  const fill = kind === "moss" ? "url(#moss-fill)" : "url(#dirt-fill)";
  return (
    <ellipse cx={x} cy={y} rx={w/2} ry={h/2} fill={fill} opacity="0.7" />
  );
}

/* ========== WorldScene main component ========== */
function WorldScene({ zone, selectedId, onSelect, threadVisibility, hoverId, setHoverId, pendingEdit }) {
  /* Build the entity list for this zone */
  const zoneEntities = WORLD.actors.filter(a => a.in === zone.id);
  /* Build full draw list (scenery + entities) sorted by depth (y) */
  const drawList = [
    ...SCENERY.map(s => ({ ...s, _type: "scenery" })),
    ...zoneEntities.map(a => ({ ...a, _type: "entity", _pos: SCENE_POSITIONS[a.id] || { x: 600, y: 500, depth: 500 } })),
    /* duplicate goblin scouts and wolves for visual richness */
    { _type: "entity", id: "actor.goblin_scout_2", _pos: SCENE_POSITIONS["actor.goblin_scout_2"], race: "goblin", _src: "actor.goblin_scout" },
    { _type: "entity", id: "actor.goblin_scout_3", _pos: SCENE_POSITIONS["actor.goblin_scout_3"], race: "goblin", _src: "actor.goblin_scout" },
    { _type: "entity", id: "actor.dire_wolf_2",    _pos: SCENE_POSITIONS["actor.dire_wolf_2"],    beast: true,    _src: "actor.dire_wolf" },
  ].sort((a, b) => {
    const da = a._pos ? a._pos.depth : a.depth;
    const db = b._pos ? b._pos.depth : b.depth;
    return da - db;
  });

  return (
    <svg viewBox="0 0 1400 820" preserveAspectRatio="xMidYMid slice"
         onClick={() => onSelect(null)}
         style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }}>
      <defs>
        <radialGradient id="sky-glow" cx="50%" cy="0%" r="80%">
          <stop offset="0%" stopColor="#3d4a72" />
          <stop offset="40%" stopColor="#1a1f3a" />
          <stop offset="100%" stopColor="#0a0c14" />
        </radialGradient>
        <radialGradient id="ground" cx="50%" cy="80%" r="80%">
          <stop offset="0%" stopColor="#2c3a22" />
          <stop offset="60%" stopColor="#1c2418" />
          <stop offset="100%" stopColor="#0c100a" />
        </radialGradient>
        <radialGradient id="cottage-light" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="#e6c87a" stopOpacity="0.45" />
          <stop offset="60%" stopColor="#a08236" stopOpacity="0.2" />
          <stop offset="100%" stopColor="#e6c87a" stopOpacity="0" />
        </radialGradient>
        <radialGradient id="portal-fill" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="#a8dcff" stopOpacity="0.6" />
          <stop offset="60%" stopColor="#2b78c7" stopOpacity="0.4" />
          <stop offset="100%" stopColor="#11264f" stopOpacity="0.7" />
        </radialGradient>
        <pattern id="moss-fill" patternUnits="userSpaceOnUse" width="16" height="16">
          <rect width="16" height="16" fill="#2d4318" />
          <circle cx="3" cy="3" r="1.2" fill="#4d7036" />
          <circle cx="11" cy="9" r="1.5" fill="#5d8a3a" />
          <circle cx="8" cy="14" r="1" fill="#3d5a2a" />
        </pattern>
        <pattern id="dirt-fill" patternUnits="userSpaceOnUse" width="16" height="16">
          <rect width="16" height="16" fill="#3a2c18" />
          <circle cx="5" cy="6" r="1" fill="#2c2014" />
          <circle cx="12" cy="11" r="0.8" fill="#4a3c20" />
        </pattern>
        <pattern id="grass" patternUnits="userSpaceOnUse" width="200" height="200">
          <rect width="200" height="200" fill="#1c2418" />
          <circle cx="40" cy="60" r="0.5" fill="#3d5a2a" opacity="0.5" />
          <circle cx="120" cy="120" r="0.5" fill="#3d5a2a" opacity="0.5" />
          <circle cx="80" cy="160" r="0.5" fill="#3d5a2a" opacity="0.5" />
          <circle cx="170" cy="40" r="0.5" fill="#3d5a2a" opacity="0.5" />
        </pattern>
        <filter id="entity-glow">
          <feGaussianBlur stdDeviation="4" result="b" />
          <feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
        <linearGradient id="bottom-vignette" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#000" stopOpacity="0" />
          <stop offset="100%" stopColor="#000" stopOpacity="0.7" />
        </linearGradient>
      </defs>

      {/* Sky */}
      <rect x="0" y="0" width="1400" height="350" fill="url(#sky-glow)" pointerEvents="none" />
      {/* Distant horizon trees (silhouette) */}
      <path d="M0,330 L60,310 L120,330 L180,300 L240,325 L300,295 L360,320 L420,290 L480,325 L540,300 L600,310 L660,290 L720,320 L780,295 L840,315 L900,290 L960,320 L1020,295 L1080,315 L1140,290 L1200,325 L1260,300 L1320,320 L1400,295 L1400,360 L0,360 Z"
            fill="#0a0e18" />
      {/* Mid-distance hills */}
      <path d="M0,360 L100,340 L260,360 L420,335 L600,355 L780,340 L960,360 L1140,335 L1300,355 L1400,340 L1400,400 L0,400 Z"
            fill="#0e1224" />
      {/* Ground plane */}
      <rect x="0" y="380" width="1400" height="440" fill="url(#ground)" pointerEvents="none" />
      <rect x="0" y="380" width="1400" height="440" fill="url(#grass)" opacity="0.3" pointerEvents="none" />

      {/* Clearing — lighter patch */}
      <ellipse cx="700" cy="600" rx="500" ry="180" fill="#28371d" opacity="0.6" />
      <ellipse cx="700" cy="610" rx="380" ry="120" fill="#33442a" opacity="0.4" />

      {/* Path */}
      <path d="M60,540 Q200,530 400,560 Q600,580 760,540 Q900,520 1100,530"
            fill="none" stroke="#3a2c18" strokeWidth="14" opacity="0.5" />
      <path d="M60,540 Q200,530 400,560 Q600,580 760,540 Q900,520 1100,530"
            fill="none" stroke="#5a4428" strokeWidth="8" opacity="0.7" strokeDasharray="2 16" />

      {/* Light pools (cottage glow) */}

      {/* Render the draw list */}
      {drawList.map((it, i) => {
        if (it._type === "scenery") {
          const kind = it.kind;
          if (kind === "tree")   return <Tree key={i} x={it.x} y={it.y} scale={it.scale} variant={it.variant} />;
          if (kind === "rock")   return <Rock key={i} x={it.x} y={it.y} scale={it.scale} />;
          if (kind === "cottage")return <Cottage key={i} x={it.x} y={it.y} scale={it.scale} glow />;
          if (kind === "moss" || kind === "dirt")
            return <GroundPatch key={i} x={it.x} y={it.y} w={it.w} h={it.h} kind={kind} />;
          if (kind === "trigger") return <TriggerVolume key={i} x={it.x} y={it.y} w={it.w} h={it.h} label={it.label} />;
          if (kind === "portal")  return <PortalMark key={i} x={it.x} y={it.y} label={it.label} dest={it.dest} />;
          return null;
        }
        /* entity */
        const id = it.id;
        const isSelected = id === selectedId;
        const isHover = id === hoverId;
        const src = it._src || id;
        const entityData = WORLD.actors.find(a => a.id === src) || {};
        const p = it._pos;
        const showLabel = isSelected || isHover;
        const isDraft = id === "actor.goblin_shaman";
        return (
          <g key={i} transform={`translate(${p.x},${p.y})`}
             onMouseEnter={() => setHoverId(id)}
             onMouseLeave={() => setHoverId(null)}
             onClick={(e) => { e.stopPropagation(); onSelect(src); }}
             style={{ cursor: "pointer" }}>
            {/* selection ring */}
            {isSelected && (
              <>
                <ellipse cx="0" cy="2" rx="28" ry="8" fill="none" stroke="var(--arcane-500)" strokeWidth="1.4">
                  <animate attributeName="rx" values="28;34;28" dur="2.2s" repeatCount="indefinite" />
                  <animate attributeName="opacity" values="1;0.4;1" dur="2.2s" repeatCount="indefinite" />
                </ellipse>
                <ellipse cx="0" cy="2" rx="24" ry="7" fill="none" stroke="var(--arcane-400)" strokeWidth="0.8" strokeDasharray="3 4" />
              </>
            )}
            {isHover && !isSelected && (
              <ellipse cx="0" cy="2" rx="22" ry="6" fill="none" stroke="var(--brass-500)" strokeWidth="1" strokeDasharray="2 3" />
            )}
            {/* sprite */}
            <g style={{ filter: isSelected ? "drop-shadow(0 0 8px rgba(61,166,245,0.6))" : undefined }}>
              <EntitySprite kind={src} race={entityData.race || it.race} beast={it.beast} selected={isSelected} />
            </g>
            {/* draft badge */}
            {isDraft && (
              <g transform="translate(0,-52)">
                <rect x="-22" y="-8" width="44" height="14" rx="2" fill="rgba(184,48,42,0.85)" stroke="var(--health-red)" strokeWidth="0.5" />
                <text x="0" y="2" textAnchor="middle" fontFamily="var(--font-sans)" fontWeight="700"
                      fontSize="9" fill="#fff" letterSpacing="1.5">DRAFT</text>
              </g>
            )}
            {/* label on hover/select */}
            {showLabel && (
              <g transform={`translate(0,${isDraft ? -72 : -52})`}>
                <rect x="-60" y="-12" width="120" height="18" rx="2"
                      fill="rgba(8,9,15,0.92)" stroke="var(--brass-700)" strokeWidth="0.5" />
                <text x="0" y="0" textAnchor="middle" fontFamily="var(--font-display-chiseled)"
                      fontSize="10" fill="var(--parchment-100)" letterSpacing="1.5"
                      style={{ textTransform: "uppercase" }}>
                  {entityData.name || src}
                </text>
              </g>
            )}
          </g>
        );
      })}

      {/* Pending edit shimmer — show that firebolt was just bound */}
      {pendingEdit && selectedId === "actor.goblin_shaman" && (
        <g transform={`translate(${SCENE_POSITIONS["actor.goblin_shaman"].x},${SCENE_POSITIONS["actor.goblin_shaman"].y - 40})`}>
          <circle cx="0" cy="0" r="6" fill="#ff8a3c" opacity="0.4">
            <animate attributeName="r" values="6;26;6" dur="1.8s" repeatCount="indefinite" />
            <animate attributeName="opacity" values="0.8;0;0.8" dur="1.8s" repeatCount="indefinite" />
          </circle>
        </g>
      )}

      {/* Bottom vignette */}
      <rect x="0" y="700" width="1400" height="120" fill="url(#bottom-vignette)" pointerEvents="none" />
    </svg>
  );
}

window.WorldScene = WorldScene;
window.SCENE_POSITIONS = SCENE_POSITIONS;
