//! `Items.dat` — the item definition table (`Items.bb:338` `LoadItems`).
//!
//! Unlike the indexed media catalogs, this is a flat sequence of
//! variable-length records read until EOF. Each record must be walked in full
//! (strings + an `ItemType`-conditional tail) to find the next one. We keep only
//! the fields the client needs for display (name, type, value, thumbnail) but
//! still parse every field so record boundaries stay aligned.
//!
//! Record layout (`Items.bb:348-408`), all little-endian, strings = 4-byte LE
//! length + bytes:
//! `id i16 · Name str · ExclRace str · ExclClass str · Script str · SMethod str
//!  · ItemType u8 · Value i32 · Mass i16 · TakesDamage u8 · ThumbnailTexID i16
//!  · Gubbins 6×i16 · MMeshID i16 · FMeshID i16 · SlotType i16 · Stackable u8
//!  · 40×(attr i16) · {weapon|armour|potion|image tail} · MiscData str`.

use crate::reader::{BlitzReader, ReadError};

/// One item definition (the subset the client displays).
#[derive(Debug, Clone, PartialEq)]
pub struct ItemDef {
    pub id: u16,
    pub name: String,
    /// 1=Weapon 2=Armour 3=Ring 4=Potion 5=Ingredient 6=Image 7=Other.
    pub item_type: u8,
    /// Equipment slot type (`Inventories.bb`: 1 Weapon, 2 Shield, 3 Hat,
    /// 4 Chest, 5 Hand, 6 Belt, 7 Legs, 8 Feet, 9 Ring, 10 Amulet, 11 Backpack).
    pub slot_type: i16,
    /// Mesh catalog ids for the equipped/world model (male `mmesh`, female
    /// `fmesh`). 65535 = none. The weapon's `mmesh` attaches at the `R_Hand`
    /// joint.
    pub mmesh: u16,
    pub fmesh: u16,
    pub value: i32,
    pub thumbnail_tex_id: i16,
    pub stackable: bool,
    /// Item weight (`Mass`), for the inventory tooltip.
    pub mass: i16,
    /// Base melee/ranged damage for weapons (item_type 1), else 0.
    pub weapon_damage: i16,
    /// Weapon type for weapons (item_type 1): 1 OneHand, 2 TwoHand, 3 Ranged
    /// (`Items.bb` `W_*`). 0 for non-weapons. Drives the ranged attack-range gate.
    pub weapon_wtype: i16,
    /// Weapon reach in world units for weapons (item_type 1), else 0. Ranged
    /// weapons attack at `range - 0.5` when their item-health is > 0
    /// (`ClientCombat.bb:38-42`).
    pub weapon_range: f32,
    /// Armour level for armour (item_type 2), else 0.
    pub armour_level: i16,
    /// Texture catalog id of the full-size image for image items (item_type 6),
    /// shown in the `WItemWindow` popup on use. -1 for non-image items.
    pub image_id: i16,
}

/// All item definitions from `Items.dat`, in file order.
#[derive(Debug, Clone, Default)]
pub struct ItemCatalog {
    pub items: Vec<ItemDef>,
}

impl ItemCatalog {
    /// Parse a whole `Items.dat`. Stops cleanly at EOF or the first record that
    /// fails to decode (a truncated/corrupt tail), keeping what parsed — same
    /// posture as the engine's `LoadItems`.
    pub fn parse(data: &[u8]) -> ItemCatalog {
        let mut r = BlitzReader::new(data);
        let mut items = Vec::new();
        while !r.eof() {
            match Self::parse_record(&mut r) {
                Ok(item) => items.push(item),
                Err(_) => break,
            }
        }
        ItemCatalog { items }
    }

    fn parse_record(r: &mut BlitzReader) -> Result<ItemDef, ReadError> {
        // ReadShort is signed 16-bit (matches the engine's LoadItems); a
        // negative id means a corrupt/misaligned record — stop here.
        let id = r.read_short()?;
        if id < 0 {
            return Err(ReadError::UnexpectedEof { offset: 0, needed: 0, available: 0 });
        }
        let name = r.read_string(256)?;
        let _excl_race = r.read_string(256)?;
        let _excl_class = r.read_string(256)?;
        let _script = r.read_string(1024)?;
        let _smethod = r.read_string(1024)?;
        let item_type = r.read_byte()?;
        let value = r.read_int()?;
        let mass = r.read_short()?;
        let _takes_damage = r.read_byte()?;
        let thumbnail_tex_id = r.read_short()?;
        for _ in 0..6 {
            r.read_short()?; // Gubbins[0..5]
        }
        let mmesh = r.read_short()?;
        let fmesh = r.read_short()?;
        let slot_type = r.read_short()?;
        let stackable = r.read_byte()? != 0;
        for _ in 0..40 {
            r.read_short()?; // Attributes\Value[0..39]
        }
        // ItemType-conditional tail (Items.bb:378-404).
        let mut weapon_damage = 0i16;
        let mut weapon_wtype = 0i16;
        let mut weapon_range = 0.0f32;
        let mut armour_level = 0i16;
        let mut image_id = -1i16;
        match item_type {
            1 => {
                // Weapon: damage, dtype, wtype, rangedProjectile (4×i16),
                // range f32, rangedAnimation string.
                weapon_damage = r.read_short()?; // damage
                let _dtype = r.read_short()?; // damage type
                weapon_wtype = r.read_short()?; // weapon type (W_Ranged = 3)
                let _ranged_proj = r.read_short()?; // ranged projectile id
                weapon_range = r.read_float()?; // reach in world units
                r.read_string(256)?; // ranged animation
            }
            2 => {
                armour_level = r.read_short()?; // ArmourLevel
            }
            4 | 5 => {
                r.read_short()?; // EatEffectsLength (Potion / Ingredient)
            }
            6 => {
                image_id = r.read_short()?; // ImageID (texture catalog id)
            }
            _ => {} // Ring (3), Other (7): no tail
        }
        let _misc = r.read_string(4096)?;
        Ok(ItemDef {
            id: id as u16,
            name,
            item_type,
            slot_type,
            mmesh: mmesh as u16,
            fmesh: fmesh as u16,
            value,
            thumbnail_tex_id,
            stackable,
            mass,
            weapon_damage,
            weapon_wtype,
            weapon_range,
            armour_level,
            image_id,
        })
    }

    /// Look up an item by id (linear; build a map for hot paths).
    pub fn get(&self, id: u16) -> Option<&ItemDef> {
        self.items.iter().find(|i| i.id == id)
    }

    /// The equipment slot index this item equips into, or `None` if it can't be
    /// equipped. See [`equip_slot`].
    pub fn equip_slot(&self, id: u16) -> Option<u8> {
        self.get(id).and_then(|i| equip_slot(i.item_type, i.slot_type))
    }

    /// The item's display name, or a `#<id>` placeholder if unknown.
    pub fn name_or_id(&self, id: u16) -> String {
        self.get(id).map(|i| i.name.clone()).unwrap_or_else(|| format!("#{id}"))
    }
}

/// Map an item's (item_type, slot_type) to the equipment slot index it equips
/// into (`Inventories.bb` `SlotsMatch` / `SlotI_*`), or `None` if it can't be
/// worn. Returns the *first* slot of multi-slot types (ring → 8, amulet → 12);
/// the server's swap moves any currently-equipped item back to the backpack.
pub fn equip_slot(item_type: u8, slot_type: i16) -> Option<u8> {
    match item_type {
        1 => Some(0), // Weapon → SlotI_Weapon
        2 => match slot_type {
            // Armour, by slot type → Shield/Hat/Chest/Hand/Belt/Legs/Feet.
            2 => Some(1),
            3 => Some(2),
            4 => Some(3),
            5 => Some(4),
            6 => Some(5),
            7 => Some(6),
            8 => Some(7),
            _ => None,
        },
        3 => match slot_type {
            9 => Some(8),   // Ring → first ring slot (SlotI_Ring1)
            10 => Some(12), // Amulet → first amulet slot (SlotI_Amulet1)
            _ => None,
        },
        _ => None, // Potion / Ingredient / Image / Other
    }
}

/// Display name of an equipment slot index (0..13), or `None` for a backpack
/// slot (14..45). Mirrors the `SlotI_*` layout in `Inventories.bb`.
pub fn equip_slot_name(slot: u8) -> Option<&'static str> {
    Some(match slot {
        0 => "Weapon",
        1 => "Shield",
        2 => "Hat",
        3 => "Chest",
        4 => "Hand",
        5 => "Belt",
        6 => "Legs",
        7 => "Feet",
        8..=11 => "Ring",
        12 | 13 => "Amulet",
        _ => return None,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Encode a Blitz file string: 4-byte LE length + bytes.
    fn bstr(out: &mut Vec<u8>, s: &str) {
        out.extend_from_slice(&(s.len() as i32).to_le_bytes());
        out.extend_from_slice(s.as_bytes());
    }

    /// Build one minimal Other-type (no tail) record.
    fn record(id: u16, name: &str, value: i32, stackable: bool) -> Vec<u8> {
        let mut o = Vec::new();
        o.extend_from_slice(&(id as i16).to_le_bytes());
        bstr(&mut o, name);
        bstr(&mut o, ""); // race
        bstr(&mut o, ""); // class
        bstr(&mut o, ""); // script
        bstr(&mut o, ""); // smethod
        o.push(7); // ItemType = Other (no conditional tail)
        o.extend_from_slice(&value.to_le_bytes());
        o.extend_from_slice(&0i16.to_le_bytes()); // mass
        o.push(0); // takesdamage
        o.extend_from_slice(&55i16.to_le_bytes()); // thumbnail
        for _ in 0..6 {
            o.extend_from_slice(&0i16.to_le_bytes()); // gubbins
        }
        o.extend_from_slice(&0i16.to_le_bytes()); // mmesh
        o.extend_from_slice(&0i16.to_le_bytes()); // fmesh
        o.extend_from_slice(&0i16.to_le_bytes()); // slottype
        o.push(stackable as u8);
        for _ in 0..40 {
            o.extend_from_slice(&5000i16.to_le_bytes()); // attrs (value 0)
        }
        bstr(&mut o, ""); // miscdata
        o
    }

    #[test]
    fn parse_two_other_items() {
        let mut data = record(0, "Bread", 5, true);
        data.extend(record(3, "Iron Key", 0, false));
        let cat = ItemCatalog::parse(&data);
        assert_eq!(cat.items.len(), 2);
        assert_eq!(cat.get(0).unwrap().name, "Bread");
        assert!(cat.get(0).unwrap().stackable);
        assert_eq!(cat.get(0).unwrap().value, 5);
        assert_eq!(cat.get(0).unwrap().thumbnail_tex_id, 55);
        assert_eq!(cat.get(3).unwrap().name, "Iron Key");
        assert!(!cat.get(3).unwrap().stackable);
        assert_eq!(cat.name_or_id(3), "Iron Key");
        assert_eq!(cat.name_or_id(99), "#99");
    }

    #[test]
    fn parse_weapon_record_tail() {
        // A weapon record (ItemType=1) has an 8+4 byte numeric tail + a string,
        // so the walker must consume it to align the next record.
        let mut o = Vec::new();
        o.extend_from_slice(&5i16.to_le_bytes());
        bstr(&mut o, "Sword");
        for _ in 0..4 {
            bstr(&mut o, "");
        }
        o.push(1); // Weapon
        o.extend_from_slice(&100i32.to_le_bytes());
        o.extend_from_slice(&3i16.to_le_bytes()); // mass
        o.push(1); // takesdamage
        o.extend_from_slice(&12i16.to_le_bytes()); // thumbnail
        for _ in 0..6 {
            o.extend_from_slice(&0i16.to_le_bytes());
        }
        o.extend_from_slice(&77i16.to_le_bytes()); // mmesh
        o.extend_from_slice(&0i16.to_le_bytes()); // fmesh
        o.extend_from_slice(&1i16.to_le_bytes()); // slottype (Weapon)
        o.push(0);
        for _ in 0..40 {
            o.extend_from_slice(&5000i16.to_le_bytes());
        }
        // weapon tail: damage,dtype,wtype,proj (4×i16), range f32, anim string.
        // Use a ranged bow (wtype 3) with reach 20.0 to exercise field retention.
        o.extend_from_slice(&7i16.to_le_bytes()); // damage
        o.extend_from_slice(&0i16.to_le_bytes()); // dtype
        o.extend_from_slice(&3i16.to_le_bytes()); // wtype = W_Ranged
        o.extend_from_slice(&0i16.to_le_bytes()); // ranged projectile
        o.extend_from_slice(&20f32.to_le_bytes()); // range
        bstr(&mut o, "");
        bstr(&mut o, ""); // miscdata
        // a trailing second item to prove alignment held
        o.extend(record(6, "Apple", 1, true));

        let cat = ItemCatalog::parse(&o);
        assert_eq!(cat.items.len(), 2);
        assert_eq!(cat.get(5).unwrap().name, "Sword");
        assert_eq!(cat.get(5).unwrap().item_type, 1);
        assert_eq!(cat.get(5).unwrap().mmesh, 77); // weapon mesh id parsed
        assert_eq!(cat.get(5).unwrap().slot_type, 1);
        assert_eq!(cat.get(5).unwrap().weapon_damage, 7);
        assert_eq!(cat.get(5).unwrap().weapon_wtype, 3); // W_Ranged retained
        assert_eq!(cat.get(5).unwrap().weapon_range, 20.0); // reach retained
        assert_eq!(cat.get(6).unwrap().name, "Apple");
    }

    #[test]
    fn slot_names() {
        assert_eq!(equip_slot_name(0), Some("Weapon"));
        assert_eq!(equip_slot_name(7), Some("Feet"));
        assert_eq!(equip_slot_name(8), Some("Ring"));
        assert_eq!(equip_slot_name(11), Some("Ring"));
        assert_eq!(equip_slot_name(12), Some("Amulet"));
        assert_eq!(equip_slot_name(14), None); // backpack
        assert_eq!(equip_slot_name(45), None);
    }

    #[test]
    fn equip_slot_mapping() {
        assert_eq!(equip_slot(1, 1), Some(0)); // Weapon → slot 0
        assert_eq!(equip_slot(2, 2), Some(1)); // Shield armour → slot 1
        assert_eq!(equip_slot(2, 4), Some(3)); // Chest armour → slot 3
        assert_eq!(equip_slot(2, 8), Some(7)); // Feet armour → slot 7
        assert_eq!(equip_slot(3, 9), Some(8)); // Ring → first ring slot 8
        assert_eq!(equip_slot(3, 10), Some(12)); // Amulet → first amulet slot 12
        // Non-equippable types.
        assert_eq!(equip_slot(4, 0), None); // Potion
        assert_eq!(equip_slot(7, 0), None); // Other
        assert_eq!(equip_slot(2, 99), None); // armour with an unknown slot type
    }
}
