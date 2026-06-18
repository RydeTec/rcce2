//! Pure language builtins — the Blitz string/number functions RSL scripts use
//! (`Len`, `Mid`, `Left`, `Right`, `Str`, `Int`, `Float`, `Chr`, `Asc`,
//! `Upper`, `Lower`, `Trim`, `Instr`, `Replace`, `Abs`). These need no game
//! state, so a [`Host`](crate::Host) implementation can delegate here first and
//! only handle the genuine `BVM_*` game commands itself.
//!
//! Blitz strings are **1-based**; the index math mirrors that exactly.

use crate::interp::Value;

/// Evaluate a pure builtin by name (case-insensitive). Returns `None` if `name`
/// is not a builtin (the caller then tries its game commands).
pub fn builtin(name: &str, args: &[Value]) -> Option<Value> {
    let s = |i: usize| args.get(i).map(|v| v.to_string_value()).unwrap_or_default();
    let n = |i: usize| args.get(i).map(|v| v.to_int()).unwrap_or(0);

    Some(match name.to_lowercase().as_str() {
        "len" => Value::Int(s(0).chars().count() as i64),
        "upper" => Value::Str(s(0).to_uppercase()),
        "lower" => Value::Str(s(0).to_lowercase()),
        "trim" => Value::Str(s(0).trim().to_string()),
        "str" => Value::Str(args.first().map(|v| v.to_string_value()).unwrap_or_default()),
        "int" => Value::Int(args.first().map(|v| v.to_int()).unwrap_or(0)),
        "float" => Value::Float(args.first().map(|v| v.to_float()).unwrap_or(0.0)),
        "abs" => {
            let v = args.first().cloned().unwrap_or(Value::Int(0));
            match v {
                Value::Float(f) => Value::Float(f.abs()),
                other => Value::Int(other.to_int().abs()),
            }
        }
        "chr" => {
            let c = char::from_u32(n(0) as u32).unwrap_or('\0');
            Value::Str(c.to_string())
        }
        "asc" => {
            let st = s(0);
            Value::Int(st.chars().next().map(|c| c as i64).unwrap_or(0))
        }
        "left" => {
            let st = s(0);
            let count = n(1).max(0) as usize;
            Value::Str(st.chars().take(count).collect())
        }
        "right" => {
            let st = s(0);
            let chars: Vec<char> = st.chars().collect();
            let count = (n(1).max(0) as usize).min(chars.len());
            Value::Str(chars[chars.len() - count..].iter().collect())
        }
        "mid" => {
            // Mid(s, start[, count]) — 1-based start.
            let chars: Vec<char> = s(0).chars().collect();
            let start = n(1);
            if start < 1 {
                return Some(Value::Str(String::new()));
            }
            let begin = (start as usize - 1).min(chars.len());
            let end = if args.len() >= 3 {
                (begin + n(2).max(0) as usize).min(chars.len())
            } else {
                chars.len()
            };
            Value::Str(chars[begin..end].iter().collect())
        }
        "instr" => {
            // Instr(s, sub[, start]) — 1-based result, 0 if not found.
            let hay = s(0);
            let needle = s(1);
            let start = if args.len() >= 3 { n(2).max(1) as usize } else { 1 };
            let hay_chars: Vec<char> = hay.chars().collect();
            if start > hay_chars.len() && !needle.is_empty() {
                return Some(Value::Int(0));
            }
            let from: String = hay_chars[(start - 1).min(hay_chars.len())..].iter().collect();
            match from.find(&needle) {
                // `find` returns a byte offset; convert to a 1-based char index.
                Some(byte_off) => {
                    let char_off = from[..byte_off].chars().count();
                    Value::Int((start + char_off) as i64)
                }
                None => Value::Int(0),
            }
        }
        "replace" => Value::Str(s(0).replace(&s(1), &s(2))),
        _ => return None,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn call(name: &str, args: &[Value]) -> Value {
        builtin(name, args).unwrap()
    }

    #[test]
    fn len_upper_lower_trim() {
        assert_eq!(call("Len", &[Value::Str("hello".into())]), Value::Int(5));
        assert_eq!(call("Upper", &[Value::Str("aB".into())]), Value::Str("AB".into()));
        assert_eq!(call("Lower", &[Value::Str("aB".into())]), Value::Str("ab".into()));
        assert_eq!(call("Trim", &[Value::Str("  x  ".into())]), Value::Str("x".into()));
    }

    #[test]
    fn mid_left_right_are_one_based() {
        let s = Value::Str("RealmCrafter".into());
        assert_eq!(call("Left", &[s.clone(), Value::Int(5)]), Value::Str("Realm".into()));
        assert_eq!(call("Right", &[s.clone(), Value::Int(7)]), Value::Str("Crafter".into()));
        assert_eq!(call("Mid", &[s.clone(), Value::Int(6)]), Value::Str("Crafter".into()));
        assert_eq!(call("Mid", &[s, Value::Int(1), Value::Int(5)]), Value::Str("Realm".into()));
    }

    #[test]
    fn instr_one_based_and_miss() {
        let s = Value::Str("a,b,c".into());
        assert_eq!(call("Instr", &[s.clone(), Value::Str(",".into())]), Value::Int(2));
        assert_eq!(call("Instr", &[s.clone(), Value::Str(",".into()), Value::Int(3)]), Value::Int(4));
        assert_eq!(call("Instr", &[s, Value::Str("z".into())]), Value::Int(0));
    }

    #[test]
    fn chr_asc_str_int_replace() {
        assert_eq!(call("Chr", &[Value::Int(65)]), Value::Str("A".into()));
        assert_eq!(call("Asc", &[Value::Str("A".into())]), Value::Int(65));
        assert_eq!(call("Str", &[Value::Int(42)]), Value::Str("42".into()));
        assert_eq!(call("Int", &[Value::Str("17x".into())]), Value::Int(17));
        assert_eq!(call("Replace", &[Value::Str("a-b-c".into()), Value::Str("-".into()), Value::Str("+".into())]), Value::Str("a+b+c".into()));
    }

    #[test]
    fn unknown_is_none() {
        assert!(builtin("GiveItem", &[]).is_none());
    }
}
