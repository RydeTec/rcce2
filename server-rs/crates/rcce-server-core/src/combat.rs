//! Melee damage resolution — parity with `ActorAttack`'s `CombatFormula`
//! branches (`GameServer.bb:409+`). The formula is kept a **pure** function with
//! the random rolls injected, so it is deterministic and unit-testable; the live
//! handler draws the rolls from the server RNG and applies the result.
//!
//! The shipped project (`Misc.dat`) uses `CombatFormula = 1`; formulas 2 and 3
//! are provided too. Formula 4 ("attack script") routes to the BVM engine and is
//! handled there, not here.

/// The random rolls a single melee swing consumes (`Rand(...)` in the Blitz
/// formula). Injected so the formula is deterministic in tests.
#[derive(Clone, Copy, Debug)]
pub struct Rolls {
    /// `Rand(100)` → 1..=100. Hits when `> 10` (≈90%).
    pub to_hit: u32,
    /// `Rand(5, 8)` — strength-vs-weapon swing for formula 1.
    pub roll_5_8: i32,
    /// `Rand(-5, 5)` — base variance.
    pub roll_n5_5: i32,
    /// `Rand(1, 10)` — a `1` is a critical hit (×2 damage).
    pub crit_roll: u32,
}

/// Result of a swing.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum SwingResult {
    /// Missed (the client shows a miss; no HP change).
    Miss,
    /// Hit for this much damage (already armour-reduced, min 1), `crit` flags a
    /// critical (the attacker gets the crit message).
    Hit { damage: i32, crit: bool },
}

/// Inputs describing the attacker/target for a swing.
#[derive(Clone, Copy, Debug)]
pub struct SwingInput {
    pub strength: i32,
    /// Equipped weapon's `WeaponDamage`, or `None` if unarmed / broken weapon.
    pub weapon_damage: Option<i32>,
    /// `GetArmourLevel(target)` — equipped armour points.
    pub armour: i32,
    /// Target's resistance to the inflicted damage type (`100` = neutral).
    pub resistance: i32,
    /// Target's Toughness attribute value, if the project assigns one.
    pub toughness: Option<i32>,
}

/// Resolve one melee swing under `formula` (1, 2, or 3). Mirrors
/// `ActorAttack`'s armour/crit/min-1 steps. Formula 4 (attack script) is not
/// handled here — the caller routes it to the BVM engine.
pub fn melee_swing(formula: u8, input: &SwingInput, rolls: &Rolls) -> SwingResult {
    // `If ToHit > 10` hits; otherwise miss (`Damage = -1`).
    if rolls.to_hit <= 10 {
        return SwingResult::Miss;
    }

    // Base damage depends on the formula.
    let mut damage = match formula {
        1 => match input.weapon_damage {
            Some(wd) => {
                if input.strength < wd {
                    wd - rolls.roll_5_8
                } else if input.strength > wd {
                    wd + rolls.roll_5_8
                } else {
                    wd + rolls.roll_n5_5
                }
            }
            None => input.strength / 8 + rolls.roll_n5_5,
        },
        2 => match input.weapon_damage {
            Some(wd) => wd,
            None => input.strength / 8 + rolls.roll_n5_5,
        },
        3 => match input.weapon_damage {
            Some(wd) => wd * input.strength,
            None => input.strength + rolls.roll_n5_5,
        },
        _ => input.strength / 8 + rolls.roll_n5_5, // unknown → conservative base
    };

    let crit = rolls.crit_roll == 1;
    if crit {
        damage *= 2;
    }

    // Armour: AP = GetArmourLevel + (resistance - 100) + toughness/8.
    let ap = input.armour + (input.resistance - 100) + input.toughness.map_or(0, |t| t / 8);
    damage -= ap;
    if damage < 1 {
        damage = 1;
    }
    SwingResult::Hit { damage, crit }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rolls(to_hit: u32) -> Rolls {
        Rolls { to_hit, roll_5_8: 6, roll_n5_5: 0, crit_roll: 5 }
    }

    #[test]
    fn low_to_hit_is_a_miss() {
        let input = SwingInput { strength: 80, weapon_damage: None, armour: 0, resistance: 100, toughness: None };
        assert_eq!(melee_swing(1, &input, &rolls(10)), SwingResult::Miss);
        assert_eq!(melee_swing(1, &input, &rolls(1)), SwingResult::Miss);
    }

    #[test]
    fn unarmed_formula1_uses_strength_over_8() {
        // strength 80 → 80/8 = 10, +0 variance, no crit, neutral resistance/no armour.
        let input = SwingInput { strength: 80, weapon_damage: None, armour: 0, resistance: 100, toughness: None };
        assert_eq!(melee_swing(1, &input, &rolls(50)), SwingResult::Hit { damage: 10, crit: false });
    }

    #[test]
    fn weapon_stronger_attacker_adds_swing() {
        // weapon 20, strength 50 > 20 → 20 + roll_5_8(6) = 26.
        let input = SwingInput { strength: 50, weapon_damage: Some(20), armour: 0, resistance: 100, toughness: None };
        assert_eq!(melee_swing(1, &input, &rolls(50)), SwingResult::Hit { damage: 26, crit: false });
    }

    #[test]
    fn crit_doubles_then_armour_subtracts() {
        // unarmed 10, crit ×2 = 20, armour 5 + (resistance 100-100=0) = 5 → 15.
        let input = SwingInput { strength: 80, weapon_damage: None, armour: 5, resistance: 100, toughness: None };
        let r = Rolls { to_hit: 50, roll_5_8: 6, roll_n5_5: 0, crit_roll: 1 };
        assert_eq!(melee_swing(1, &input, &r), SwingResult::Hit { damage: 15, crit: true });
    }

    #[test]
    fn damage_floored_at_one() {
        // Tiny damage vs heavy armour → min 1.
        let input = SwingInput { strength: 8, weapon_damage: None, armour: 100, resistance: 100, toughness: None };
        assert_eq!(melee_swing(1, &input, &rolls(50)), SwingResult::Hit { damage: 1, crit: false });
    }

    #[test]
    fn resistance_above_neutral_reduces_damage() {
        // resistance 150 → (150-100)=50 armour-equivalent reduction.
        let input = SwingInput { strength: 80, weapon_damage: Some(40), armour: 0, resistance: 150, toughness: None };
        // strength 80 > 40 → 40 + 6 = 46; minus (0 + 50 + 0) = -4 → floored to 1.
        assert_eq!(melee_swing(1, &input, &rolls(50)), SwingResult::Hit { damage: 1, crit: false });
    }

    #[test]
    fn formula3_multiplies_weapon_by_strength() {
        let input = SwingInput { strength: 5, weapon_damage: Some(10), armour: 0, resistance: 100, toughness: None };
        // 10 * 5 = 50, no crit, no armour.
        assert_eq!(melee_swing(3, &input, &rolls(50)), SwingResult::Hit { damage: 50, crit: false });
    }
}
