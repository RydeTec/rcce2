/* Loom — Sample world data
   A small but interconnected RPG world used to demonstrate the
   editor. References between entities are first-class — every
   string in a `.refs` array is a real id you can navigate to. */

const WORLD = {
  project: {
    name: "Embergloom",
    slogan: "An open-source dark fantasy MMORPG",
    version: "0.4.2-alpha",
    server: { host: "test.embergloom.local", port: 7777, online: true, latency: 14 },
    health: 0.82, // 0..1 — feeds the conscience meter
  },

  zones: [
    { id: "zone.hollows_edge", name: "Hollow's Edge",   pos: [0.42, 0.55], biome: "forest",   active: true,  size: "small",  entities: 14, status: "draft" },
    { id: "zone.ravensreach",  name: "Ravensreach",     pos: [0.28, 0.40], biome: "town",     active: false, size: "medium", entities: 87, status: "live" },
    { id: "zone.sunken_keep",  name: "The Sunken Keep", pos: [0.62, 0.30], biome: "dungeon",  active: false, size: "large",  entities: 142, status: "live" },
    { id: "zone.misty_plateau",name: "Misty Plateau",   pos: [0.78, 0.55], biome: "highland", active: false, size: "medium", entities: 41, status: "live" },
    { id: "zone.black_pine",   name: "Black Pine Hollow",pos:[0.55, 0.72], biome: "forest",   active: false, size: "small",  entities: 18, status: "live" },
    { id: "zone.wolfsbarrow",  name: "Wolfsbarrow Caves",pos:[0.18, 0.68], biome: "cave",     active: false, size: "medium", entities: 33, status: "live" },
    { id: "zone.sentinel",     name: "Sentinel Bridge", pos: [0.46, 0.20], biome: "ruins",    active: false, size: "small",  entities: 9,  status: "stub" },
  ],

  portals: [
    { from: "zone.hollows_edge", to: "zone.ravensreach", label: "West trail" },
    { from: "zone.hollows_edge", to: "zone.black_pine",  label: "South path" },
    { from: "zone.ravensreach",  to: "zone.sentinel",    label: "North gate" },
    { from: "zone.ravensreach",  to: "zone.sunken_keep", label: "Catacomb door" },
    { from: "zone.sunken_keep",  to: "zone.misty_plateau",label:"Cliff lift" },
    { from: "zone.wolfsbarrow",  to: "zone.ravensreach", label: "Service tunnel" },
    { from: "zone.black_pine",   to: "zone.wolfsbarrow", label: "Cave mouth", broken: true }, // a broken ref to surface
  ],

  /* ===== ACTORS ===== */
  actors: [
    { id: "actor.hermit",         name: "Old Cassian, the Hermit", level: 24, faction: "fac.veiled", race: "human",  mesh: "mesh.human_old",      anim: "anim.human_idle",   hp: 180, mana: 60,  hostile: false, in: "zone.hollows_edge", refs: ["item.hermit_robe", "item.wooden_staff", "spell.lesser_heal"] },
    { id: "actor.goblin_scout",   name: "Goblin Scout",            level: 6,  faction: "fac.forest_tribe", race: "goblin", mesh: "mesh.goblin_lithe", anim: "anim.goblin_run",  hp: 35,  mana: 0,   hostile: true,  in: "zone.hollows_edge", refs: ["item.rusty_dagger", "item.cloth_scrap"] },
    { id: "actor.goblin_shaman",  name: "Goblin Shaman",           level: 9,  faction: "fac.forest_tribe", race: "goblin", mesh: "mesh.goblin_robed", anim: "anim.humanoid_caster", hp: 50, mana: 80, hostile: true, in: "zone.hollows_edge", refs: ["item.bone_staff", "item.goblin_robe", "spell.heal_ally", "spell.firebolt"], status: "draft", drafted: true },
    { id: "actor.dire_wolf",      name: "Dire Wolf",               level: 8,  faction: "fac.wolves",       race: "beast",  mesh: "mesh.wolf_lg",     anim: "anim.beast_quad", hp: 80, mana: 0, hostile: true, in: "zone.hollows_edge", refs: ["item.wolf_pelt", "item.wolf_fang"] },
    { id: "actor.bandit",         name: "Forest Bandit",           level: 7,  faction: "fac.forest_tribe", race: "human",  mesh: "mesh.human_rough", anim: "anim.humanoid_warrior", hp: 60, mana: 10, hostile: true, in: "zone.black_pine", refs: ["item.shortsword", "item.leather_jerkin"] },
    { id: "actor.merchant",       name: "Quill, Merchant",         level: 12, faction: "fac.crownwatch",   race: "human",  mesh: "mesh.human_robed", anim: "anim.human_idle", hp: 90, mana: 20, hostile: false, in: "zone.ravensreach", refs: ["item.coin_pouch", "item.account_ledger"] },
    { id: "actor.guard_capt",     name: "Captain Ortha",           level: 28, faction: "fac.crownwatch",   race: "human",  mesh: "mesh.human_plate", anim: "anim.humanoid_warrior", hp: 240, mana: 30, hostile: false, in: "zone.ravensreach", refs: ["item.crown_greatsword", "item.crown_plate", "spell.shield_wall"] },
    { id: "actor.crypt_lich",     name: "Lich of the Sunken Keep", level: 42, faction: "fac.undying",      race: "undead", mesh: "mesh.lich",       anim: "anim.humanoid_caster", hp: 720, mana: 480, hostile: true, in: "zone.sunken_keep", refs: ["item.bone_crown", "spell.necrotic_lance", "spell.summon_thrall", "spell.firebolt"] },
  ],

  /* ===== ITEMS ===== */
  items: [
    { id: "item.hermit_robe",     name: "Hermit's Robe",        slot: "chest",     rarity: "uncommon", lvl: 12, mesh: "mesh.robe_grey",    icon: "icon.robe" },
    { id: "item.wooden_staff",    name: "Wooden Staff",         slot: "main_hand", rarity: "common",   lvl: 4,  mesh: "mesh.staff_wood",  icon: "icon.staff" },
    { id: "item.bone_staff",      name: "Bone Staff",           slot: "main_hand", rarity: "uncommon", lvl: 8,  mesh: "mesh.staff_bone",  icon: "icon.staff", refs: ["spell.firebolt"] },
    { id: "item.goblin_robe",     name: "Goblin Shaman Wraps",  slot: "chest",     rarity: "uncommon", lvl: 8,  mesh: "mesh.robe_tribal", icon: "icon.robe" },
    { id: "item.rusty_dagger",    name: "Rusty Dagger",         slot: "main_hand", rarity: "common",   lvl: 2,  mesh: "mesh.dagger_rust", icon: "icon.dagger" },
    { id: "item.cloth_scrap",     name: "Cloth Scrap",          slot: "loot",      rarity: "common",   lvl: 1,  mesh: null,                icon: "icon.cloth" },
    { id: "item.wolf_pelt",       name: "Dire Wolf Pelt",       slot: "loot",      rarity: "common",   lvl: 5,  mesh: "mesh.pelt",         icon: "icon.pelt" },
    { id: "item.wolf_fang",       name: "Dire Wolf Fang",       slot: "loot",      rarity: "uncommon", lvl: 5,  mesh: "mesh.fang",         icon: "icon.fang" },
    { id: "item.shortsword",      name: "Iron Shortsword",      slot: "main_hand", rarity: "common",   lvl: 6,  mesh: "mesh.sword_short",  icon: "icon.sword" },
    { id: "item.leather_jerkin",  name: "Leather Jerkin",       slot: "chest",     rarity: "common",   lvl: 5,  mesh: "mesh.jerkin",       icon: "icon.armor" },
    { id: "item.coin_pouch",      name: "Coin Pouch",           slot: "loot",      rarity: "common",   lvl: 1,  mesh: null,                icon: "icon.coin" },
    { id: "item.account_ledger",  name: "Account Ledger",       slot: "quest",     rarity: "rare",     lvl: 10, mesh: "mesh.book",         icon: "icon.book" },
    { id: "item.crown_greatsword",name: "Crownwatch Greatsword",slot: "main_hand", rarity: "rare",     lvl: 24, mesh: "mesh.sword_great",  icon: "icon.sword" },
    { id: "item.crown_plate",     name: "Crownwatch Plate",     slot: "chest",     rarity: "rare",     lvl: 24, mesh: "mesh.plate_full",   icon: "icon.armor" },
    { id: "item.bone_crown",      name: "Crown of Bone",        slot: "head",      rarity: "epic",     lvl: 40, mesh: "mesh.crown_bone",   icon: "icon.crown" },
    { id: "item.embers_greatsword", name: "Greatsword of Embers", slot: "main_hand", rarity: "epic", lvl: 22, mesh: "mesh.sword_great", icon: "icon.sword", refs: ["spell.firebolt"], drafted: true },
  ],

  /* ===== SPELLS ===== */
  spells: [
    { id: "spell.firebolt",      name: "Firebolt",         school: "fire",   level: 5,  manaCost: 22, cooldown: 0,   damage: 50, damageType: "fire",     cast: 1.4, range: 28, refs: ["emitter.fire_dart", "anim.cast_one_handed", "sound.spell_fire_cast"], hot: true /* flagged: many things reference this */ },
    { id: "spell.heal_ally",     name: "Heal Ally",        school: "holy",   level: 4,  manaCost: 30, cooldown: 6,   damage: -45,damageType: "holy",     cast: 2.0, range: 20, refs: ["emitter.holy_glow", "anim.cast_two_handed", "sound.spell_heal"] },
    { id: "spell.lesser_heal",   name: "Lesser Heal",      school: "holy",   level: 1,  manaCost: 12, cooldown: 0,   damage: -20,damageType: "holy",     cast: 1.6, range: 16, refs: ["emitter.holy_glow_small"] },
    { id: "spell.shield_wall",   name: "Shield Wall",      school: "guard",  level: 12, manaCost: 40, cooldown: 90,  damage: 0,  damageType: "none",     cast: 0.5, range: 0,  refs: ["emitter.shield_burst"] },
    { id: "spell.necrotic_lance",name: "Necrotic Lance",   school: "necro",  level: 18, manaCost: 55, cooldown: 4,   damage: 140,damageType: "necrotic", cast: 2.0, range: 30, refs: ["emitter.necro_lance"] },
    { id: "spell.summon_thrall", name: "Summon Thrall",    school: "necro",  level: 14, manaCost: 80, cooldown: 60,  damage: 0,  damageType: "none",     cast: 3.0, range: 8,  refs: ["actor.thrall_skeleton"], broken: true /* references deleted actor */ },
    { id: "spell.firefall",      name: "Firefall",         school: "fire",   level: 22, manaCost: 120,cooldown: 30,  damage: 220,damageType: "fire",     cast: 2.8, range: 36, refs: ["emitter.fire_rain", "anim.cast_two_handed", "sound.spell_fire_cast"] },
  ],

  /* ===== FACTIONS ===== */
  factions: [
    { id: "fac.forest_tribe",  name: "Forest Tribe",     color: "#5d8a3a", description: "Goblins and human outcasts holding the western woods." },
    { id: "fac.crownwatch",    name: "Crownwatch",       color: "#c9a44a", description: "The standing army of Ravensreach. Order, taxes, parchment." },
    { id: "fac.veiled",        name: "Veiled Druids",    color: "#7a4ec9", description: "Hermits and seers. Will heal you. Will not raise arms." },
    { id: "fac.wolves",        name: "Wolves of Wolfsbarrow", color: "#8a8580", description: "Wild beast pack. Hates everything." },
    { id: "fac.undying",       name: "The Undying",      color: "#a05fd6", description: "Lich-led, soul-bound. The Sunken Keep is theirs." },
  ],

  /* ===== EMITTERS / ASSETS (referenced by spells & items) ===== */
  emitters: [
    { id: "emitter.fire_dart",     name: "Fire Dart",      color: "#ff8a3c", lifetime: 0.6 },
    { id: "emitter.fire_rain",     name: "Fire Rain",      color: "#ff5a2c", lifetime: 2.8 },
    { id: "emitter.holy_glow",     name: "Holy Glow",      color: "#f8ebc6", lifetime: 1.4 },
    { id: "emitter.holy_glow_small",name:"Holy Glow (sm)", color: "#f8ebc6", lifetime: 0.8 },
    { id: "emitter.shield_burst",  name: "Shield Burst",   color: "#3da6f5", lifetime: 0.4 },
    { id: "emitter.necro_lance",   name: "Necro Lance",    color: "#7a4ec9", lifetime: 0.9 },
  ],

  animSets: [
    { id: "anim.humanoid_caster",   name: "Humanoid · Caster",     clips: 18 },
    { id: "anim.humanoid_warrior",  name: "Humanoid · Warrior",    clips: 22 },
    { id: "anim.beast_quad",        name: "Beast · Quadruped",     clips: 12 },
    { id: "anim.goblin_run",        name: "Goblin · Runner",       clips: 14 },
    { id: "anim.human_idle",        name: "Human · Idle",          clips: 8 },
    { id: "anim.cast_one_handed",   name: "Cast · One-handed",     clips: 4 },
    { id: "anim.cast_two_handed",   name: "Cast · Two-handed",     clips: 6 },
  ],

  /* ===== VALIDATION FINDINGS ===== */
  findings: [
    { id: "f1", severity: "danger", kind: "broken_ref",     entity: "spell.summon_thrall", message: "Summon Thrall references deleted actor `thrall_skeleton`", jump: "spell.summon_thrall" },
    { id: "f2", severity: "danger", kind: "broken_ref",     entity: "zone.black_pine",     message: "Portal to Wolfsbarrow Caves cave-mouth — target zone has no matching arrival",    jump: "zone.black_pine" },
    { id: "f3", severity: "danger", kind: "unbound_script", entity: "trigger.gate_2",      message: "Trigger volume `gate_2` in Ravensreach is unbound — script `gate_open.bb` missing", jump: "zone.ravensreach" },
    { id: "f4", severity: "warn",   kind: "missing_mesh",   entity: "item.embers_greatsword",message: "Item `Greatsword of Embers` references mesh `mesh.sword_great_fire` (not in library)", jump: "item.embers_greatsword" },
    { id: "f5", severity: "warn",   kind: "unused_asset",   entity: "mesh.dummy_03",       message: "Mesh `dummy_03.b3d` is in the library but no entity references it",      jump: null },
    { id: "f6", severity: "warn",   kind: "unused_asset",   entity: "sound.placeholder_a", message: "Sound `placeholder_a.ogg` is unused",                                     jump: null },
    { id: "f7", severity: "info",   kind: "balance_hint",   entity: "spell.firebolt",      message: "Firebolt damage is in the top 6% of level-5 spells (50 vs median 28)",   jump: "spell.firebolt" },
  ],

  /* ===== SESSION HISTORY ===== */
  history: [
    { t: "09:12", action: "Placed scenery", count: 12, entity: "zone.hollows_edge", note: "12 conifers placed in the western tree line" },
    { t: "09:18", action: "Set lighting",   count: 1,  entity: "zone.hollows_edge", note: "Time of day → 18:30 dusk" },
    { t: "09:24", action: "Placed cottage", count: 1,  entity: "zone.hollows_edge", note: "Hermit's cottage at (124, 0, -52)" },
    { t: "09:31", action: "Painted spawn",  count: 1,  entity: "actor.dire_wolf",   note: "Dire Wolf spawn zone, 4 individuals" },
    { t: "09:44", action: "Created actor",  count: 1,  entity: "actor.goblin_shaman", note: "New actor: Goblin Shaman (draft)", hero: true },
    { t: "09:46", action: "Set faction",    count: 1,  entity: "actor.goblin_shaman", note: "Faction → Forest Tribe" },
    { t: "09:48", action: "Equipped",       count: 2,  entity: "actor.goblin_shaman", note: "Bone Staff · Goblin Shaman Wraps" },
    { t: "09:51", action: "Bound spell",    count: 1,  entity: "actor.goblin_shaman", note: "Spell → Heal Ally" },
    { t: "09:53", action: "Bound spell",    count: 1,  entity: "actor.goblin_shaman", note: "Spell → Firebolt", current: true },
  ],
};

window.WORLD = WORLD;

/* Helpers --------------------------------------------- */
function findEntity(id) {
  if (!id) return null;
  const all = [
    ...WORLD.zones, ...WORLD.actors, ...WORLD.items,
    ...WORLD.spells, ...WORLD.factions,
    ...WORLD.emitters, ...WORLD.animSets,
  ];
  return all.find(e => e.id === id) || null;
}

function kindOf(id) {
  if (!id) return null;
  const [k] = id.split(".");
  return k;
}

window.findEntity = findEntity;
window.kindOf = kindOf;
