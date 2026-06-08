//! `Interface.dat` — the in-game HUD layout the real Blitz client loads
//! (`Interface.bb` `LoadInterfaceSettings`). All positions are **fractional**
//! (0..1 of screen width/height), so the Rust client can place its HUD exactly
//! where Client.exe does.
//!
//! Each `InterfaceComponent` is `X,Y,Width,Height,Alpha` (f32 LE) + `R,G,B`
//! (u8) = 23 bytes (`ReadInterfaceComponent`). The file is, in order: Chat
//! (+ a u16 texture id), ChatEntry, 40 AttributeDisplays (the vitals bars,
//! indexed by attribute id — 0 = Health), BuffsArea, Radar, Compass,
//! InventoryWindow, InventoryDrop, InventoryEat, InventoryGold, then
//! InventoryButtons[0..=45] (the equipment + backpack slots).

use crate::reader::{BlitzReader, ReadError};

/// One positioned HUD element. Coordinates are fractions of the screen.
#[derive(Debug, Clone, Copy, PartialEq, Default)]
pub struct IComp {
    pub x: f32,
    pub y: f32,
    pub w: f32,
    pub h: f32,
    pub alpha: f32,
    pub rgb: [u8; 3],
}

impl IComp {
    fn read(r: &mut BlitzReader) -> Result<IComp, ReadError> {
        Ok(IComp {
            x: r.read_float()?,
            y: r.read_float()?,
            w: r.read_float()?,
            h: r.read_float()?,
            alpha: r.read_float()?,
            rgb: [r.read_byte()?, r.read_byte()?, r.read_byte()?],
        })
    }

    /// Screen-pixel rect `(x, y, w, h)` for a `(screen_w, screen_h)` viewport.
    pub fn px(&self, sw: f32, sh: f32) -> (f32, f32, f32, f32) {
        (self.x * sw, self.y * sh, self.w * sw, self.h * sh)
    }
}

/// Number of attribute (vitals) bars in the layout.
pub const ATTRIBUTE_DISPLAYS: usize = 40;
/// Equipment + backpack slot components (0..=45).
pub const INVENTORY_BUTTONS: usize = 46;

/// The parsed `Interface.dat` HUD layout.
#[derive(Debug, Clone)]
pub struct InterfaceLayout {
    pub chat: IComp,
    pub chat_texture: u16,
    pub chat_entry: IComp,
    /// Per-attribute vitals bar positions; index 0 = Health (HealthStat).
    pub attributes: Vec<IComp>,
    pub buffs: IComp,
    pub radar: IComp,
    pub compass: IComp,
    pub inventory_window: IComp,
    pub inventory_drop: IComp,
    pub inventory_eat: IComp,
    pub inventory_gold: IComp,
    pub inventory_buttons: Vec<IComp>,
}

impl InterfaceLayout {
    pub fn parse(data: &[u8]) -> Result<InterfaceLayout, ReadError> {
        let mut r = BlitzReader::new(data);
        let chat = IComp::read(&mut r)?;
        let chat_texture = r.read_short_u()?;
        let chat_entry = IComp::read(&mut r)?;
        let mut attributes = Vec::with_capacity(ATTRIBUTE_DISPLAYS);
        for _ in 0..ATTRIBUTE_DISPLAYS {
            attributes.push(IComp::read(&mut r)?);
        }
        let buffs = IComp::read(&mut r)?;
        let radar = IComp::read(&mut r)?;
        let compass = IComp::read(&mut r)?;
        let inventory_window = IComp::read(&mut r)?;
        let inventory_drop = IComp::read(&mut r)?;
        let inventory_eat = IComp::read(&mut r)?;
        let inventory_gold = IComp::read(&mut r)?;
        let mut inventory_buttons = Vec::with_capacity(INVENTORY_BUTTONS);
        for _ in 0..INVENTORY_BUTTONS {
            inventory_buttons.push(IComp::read(&mut r)?);
        }
        Ok(InterfaceLayout {
            chat,
            chat_texture,
            chat_entry,
            attributes,
            buffs,
            radar,
            compass,
            inventory_window,
            inventory_drop,
            inventory_eat,
            inventory_gold,
            inventory_buttons,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn comp_bytes(out: &mut Vec<u8>, x: f32, y: f32, w: f32, h: f32, a: f32, rgb: [u8; 3]) {
        for f in [x, y, w, h, a] {
            out.extend_from_slice(&f.to_le_bytes());
        }
        out.extend_from_slice(&rgb);
    }

    #[test]
    fn parses_full_layout() {
        let mut data = Vec::new();
        comp_bytes(&mut data, 0.01, 0.7, 0.3, 0.2, 0.85, [255, 255, 255]); // chat
        data.extend_from_slice(&7u16.to_le_bytes()); // chat texture
        comp_bytes(&mut data, 0.01, 0.92, 0.3, 0.03, 1.0, [0, 0, 0]); // chat entry
        for i in 0..ATTRIBUTE_DISPLAYS {
            comp_bytes(&mut data, 0.02, 0.02 + i as f32 * 0.001, 0.2, 0.02, 0.85, [255, 0, 0]);
        }
        for _ in 0..(3 + 4) {
            // buffs, radar, compass, inv window/drop/eat/gold
            comp_bytes(&mut data, 0.5, 0.5, 0.1, 0.1, 1.0, [0, 0, 0]);
        }
        for _ in 0..INVENTORY_BUTTONS {
            comp_bytes(&mut data, 0.0, 0.0, 0.05, 0.1, 1.0, [0, 0, 0]);
        }

        let l = InterfaceLayout::parse(&data).expect("parse");
        assert_eq!(l.chat_texture, 7);
        assert_eq!(l.attributes.len(), 40);
        assert_eq!(l.inventory_buttons.len(), 46);
        // Health bar (index 0) at the expected fractional spot.
        assert!((l.attributes[0].x - 0.02).abs() < 1e-6);
        assert_eq!(l.attributes[0].rgb, [255, 0, 0]);
        // px() converts fractions to a 1600x900 viewport.
        let (px, _, pw, _) = l.attributes[0].px(1600.0, 900.0);
        assert!((px - 32.0).abs() < 0.1 && (pw - 320.0).abs() < 0.1);
    }

    #[test]
    fn rejects_truncated() {
        assert!(InterfaceLayout::parse(&[0u8; 10]).is_err());
    }
}
