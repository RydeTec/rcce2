//! Lexer for RSL (RealmCrafter Scripting Language) — see `docs/scripting/
//! language.md`. RSL is a Blitz-like imperative DSL: `Function`/`End Function`,
//! implicit sigil-typed variables (`$`/`%`/`#`), case-insensitive identifiers,
//! `;` line comments, and **line-oriented** statements (a newline ends a
//! statement). Word operators (`And`/`Or`/`Not`/`Mod`/`Shl`/`Shr`/`Xor`) and
//! keywords are lexed as identifiers and recognised by the parser.
//!
//! Type sigils are stripped here: RSL is dynamically typed at runtime (a `Value`
//! is int/float/string and `+` concatenates when either side is a string), so a
//! sigil is only an authoring hint — `Name$` and `Name` are the same variable.

/// A lexical token with its source line (1-based, for error messages).
#[derive(Clone, Debug, PartialEq)]
pub struct Token {
    pub kind: Tok,
    pub line: u32,
}

/// Token kinds.
#[derive(Clone, Debug, PartialEq)]
pub enum Tok {
    /// Identifier (sigil stripped, original case preserved; compare case-insensitively).
    Ident(String),
    Int(i64),
    Float(f64),
    Str(String),
    LParen,
    RParen,
    Comma,
    Plus,
    Minus,
    Star,
    Slash,
    Eq,
    Lt,
    Gt,
    Le,
    Ge,
    Ne,
    /// End of a statement (one or more newlines / a `:` separator collapse to this).
    Newline,
    Eof,
}

/// Tokenize `src`. Returns `Err(message)` on an unterminated string or a stray
/// character. A trailing [`Tok::Eof`] always closes the stream.
pub fn lex(src: &str) -> Result<Vec<Token>, String> {
    let bytes = src.as_bytes();
    let mut i = 0;
    let mut line = 1u32;
    let mut out: Vec<Token> = Vec::new();

    // Helper to push, collapsing consecutive newlines into one.
    let push_newline = |out: &mut Vec<Token>, line: u32| {
        if !matches!(out.last().map(|t| &t.kind), Some(Tok::Newline) | None) {
            out.push(Token { kind: Tok::Newline, line });
        }
    };

    while i < bytes.len() {
        let c = bytes[i];
        match c {
            // Whitespace (not newline).
            b' ' | b'\t' | b'\r' => i += 1,
            // Newline → statement separator.
            b'\n' => {
                push_newline(&mut out, line);
                line += 1;
                i += 1;
            }
            // `:` is also a statement separator in Blitz/RSL.
            b':' => {
                push_newline(&mut out, line);
                i += 1;
            }
            // Line comment, or a `!`-prefixed compiler directive (`!COMPILE`) —
            // both run to end of line and are ignored.
            b';' | b'!' => {
                while i < bytes.len() && bytes[i] != b'\n' {
                    i += 1;
                }
            }
            // String literal.
            b'"' => {
                i += 1;
                let start = i;
                while i < bytes.len() && bytes[i] != b'"' {
                    if bytes[i] == b'\n' {
                        return Err(format!("unterminated string at line {line}"));
                    }
                    i += 1;
                }
                if i >= bytes.len() {
                    return Err(format!("unterminated string at line {line}"));
                }
                let s = std::str::from_utf8(&bytes[start..i]).unwrap_or("").to_string();
                out.push(Token { kind: Tok::Str(s), line });
                i += 1; // closing quote
            }
            // Number (int or float). Leading digit or `.digit`.
            b'0'..=b'9' => {
                let start = i;
                let mut is_float = false;
                while i < bytes.len() && (bytes[i].is_ascii_digit() || bytes[i] == b'.') {
                    if bytes[i] == b'.' {
                        is_float = true;
                    }
                    i += 1;
                }
                let text = std::str::from_utf8(&bytes[start..i]).unwrap_or("");
                let kind = if is_float {
                    Tok::Float(text.parse().map_err(|_| format!("bad number '{text}' at line {line}"))?)
                } else {
                    Tok::Int(text.parse().map_err(|_| format!("bad number '{text}' at line {line}"))?)
                };
                out.push(Token { kind, line });
            }
            // Identifier / keyword.
            c if c == b'_' || c.is_ascii_alphabetic() => {
                let start = i;
                while i < bytes.len() && (bytes[i] == b'_' || bytes[i].is_ascii_alphanumeric()) {
                    i += 1;
                }
                let name = std::str::from_utf8(&bytes[start..i]).unwrap_or("").to_string();
                // Strip an optional trailing type sigil.
                if i < bytes.len() && matches!(bytes[i], b'$' | b'%' | b'#') {
                    i += 1;
                }
                out.push(Token { kind: Tok::Ident(name), line });
            }
            // Operators / punctuation.
            b'(' => { out.push(Token { kind: Tok::LParen, line }); i += 1; }
            b')' => { out.push(Token { kind: Tok::RParen, line }); i += 1; }
            b',' => { out.push(Token { kind: Tok::Comma, line }); i += 1; }
            b'+' => { out.push(Token { kind: Tok::Plus, line }); i += 1; }
            b'-' => { out.push(Token { kind: Tok::Minus, line }); i += 1; }
            b'*' => { out.push(Token { kind: Tok::Star, line }); i += 1; }
            b'/' => { out.push(Token { kind: Tok::Slash, line }); i += 1; }
            b'=' => { out.push(Token { kind: Tok::Eq, line }); i += 1; }
            b'<' => {
                if i + 1 < bytes.len() && bytes[i + 1] == b'=' {
                    out.push(Token { kind: Tok::Le, line }); i += 2;
                } else if i + 1 < bytes.len() && bytes[i + 1] == b'>' {
                    out.push(Token { kind: Tok::Ne, line }); i += 2;
                } else {
                    out.push(Token { kind: Tok::Lt, line }); i += 1;
                }
            }
            b'>' => {
                if i + 1 < bytes.len() && bytes[i + 1] == b'=' {
                    out.push(Token { kind: Tok::Ge, line }); i += 2;
                } else {
                    out.push(Token { kind: Tok::Gt, line }); i += 1;
                }
            }
            other => return Err(format!("unexpected character {:?} at line {line}", other as char)),
        }
    }
    out.push(Token { kind: Tok::Eof, line });
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn kinds(src: &str) -> Vec<Tok> {
        lex(src).unwrap().into_iter().map(|t| t.kind).collect()
    }

    #[test]
    fn function_skeleton() {
        let k = kinds("Function Main()\n\tReturn\nEnd Function\n");
        assert_eq!(
            k,
            vec![
                Tok::Ident("Function".into()),
                Tok::Ident("Main".into()),
                Tok::LParen,
                Tok::RParen,
                Tok::Newline,
                Tok::Ident("Return".into()),
                Tok::Newline,
                Tok::Ident("End".into()),
                Tok::Ident("Function".into()),
                Tok::Newline,
                Tok::Eof,
            ]
        );
    }

    #[test]
    fn comments_and_using_are_handled() {
        let k = kinds("Using \"RC_Core.rcm\"\n; a comment\nReturn\n");
        assert_eq!(
            k,
            vec![
                Tok::Ident("Using".into()),
                Tok::Str("RC_Core.rcm".into()),
                Tok::Newline,
                Tok::Ident("Return".into()),
                Tok::Newline,
                Tok::Eof,
            ]
        );
    }

    #[test]
    fn sigils_are_stripped_from_identifiers() {
        let k = kinds("PlayerName$ = Name(Target)");
        assert_eq!(
            k,
            vec![
                Tok::Ident("PlayerName".into()),
                Tok::Eq,
                Tok::Ident("Name".into()),
                Tok::LParen,
                Tok::Ident("Target".into()),
                Tok::RParen,
                Tok::Eof,
            ]
        );
    }

    #[test]
    fn numbers_and_concatenation() {
        assert_eq!(
            kinds("Dist# = 4.5 + 10"),
            vec![
                Tok::Ident("Dist".into()),
                Tok::Eq,
                Tok::Float(4.5),
                Tok::Plus,
                Tok::Int(10),
                Tok::Eof,
            ]
        );
    }

    #[test]
    fn comparison_operators() {
        assert_eq!(
            kinds("a <= b <> c >= d < e > f"),
            vec![
                Tok::Ident("a".into()), Tok::Le,
                Tok::Ident("b".into()), Tok::Ne,
                Tok::Ident("c".into()), Tok::Ge,
                Tok::Ident("d".into()), Tok::Lt,
                Tok::Ident("e".into()), Tok::Gt,
                Tok::Ident("f".into()), Tok::Eof,
            ]
        );
    }

    #[test]
    fn colon_and_blank_lines_collapse_to_one_newline() {
        // `a : b` is two statements; multiple blank lines collapse.
        assert_eq!(
            kinds("a\n\n\nb : c"),
            vec![
                Tok::Ident("a".into()),
                Tok::Newline,
                Tok::Ident("b".into()),
                Tok::Newline,
                Tok::Ident("c".into()),
                Tok::Eof,
            ]
        );
    }

    #[test]
    fn string_with_concatenation_from_a_real_script_line() {
        let k = kinds("Output(Actor(), \"This is a \" + Name(Target))");
        assert!(k.contains(&Tok::Str("This is a ".into())));
        assert!(k.contains(&Tok::Ident("Output".into())));
    }

    #[test]
    fn unterminated_string_errors() {
        assert!(lex("x = \"oops\n").is_err());
    }
}
