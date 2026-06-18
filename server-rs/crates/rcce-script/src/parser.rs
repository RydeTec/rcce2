//! Recursive-descent parser for RSL → [`Program`]. Keywords are matched
//! case-insensitively (RSL identifiers are case-insensitive). Statements are
//! newline-terminated. Covers the constructs the shipped scripts use: `Using`,
//! `Function`/`End Function`, assignment, paren calls, `If/ElseIf/Else/EndIf`,
//! `While/Wend`, `For/To/Next`, `Return`, and the Blitz operator precedence.

use crate::ast::*;
use crate::lexer::{lex, Tok, Token};

pub fn parse(src: &str) -> Result<Program, String> {
    let tokens = lex(src)?;
    let mut p = Parser { toks: tokens, pos: 0 };
    p.program()
}

struct Parser {
    toks: Vec<Token>,
    pos: usize,
}

impl Parser {
    fn peek(&self) -> &Tok {
        &self.toks[self.pos.min(self.toks.len() - 1)].kind
    }
    fn line(&self) -> u32 {
        self.toks[self.pos.min(self.toks.len() - 1)].line
    }
    fn advance(&mut self) -> Tok {
        let t = self.toks[self.pos.min(self.toks.len() - 1)].kind.clone();
        if self.pos < self.toks.len() - 1 {
            self.pos += 1;
        }
        t
    }
    /// True if the current token is the identifier `kw` (case-insensitive).
    fn is_kw(&self, kw: &str) -> bool {
        matches!(self.peek(), Tok::Ident(s) if s.eq_ignore_ascii_case(kw))
    }
    fn eat_kw(&mut self, kw: &str) -> bool {
        if self.is_kw(kw) {
            self.advance();
            true
        } else {
            false
        }
    }
    fn expect(&mut self, t: &Tok) -> Result<(), String> {
        if self.peek() == t {
            self.advance();
            Ok(())
        } else {
            Err(format!("line {}: expected {:?}, found {:?}", self.line(), t, self.peek()))
        }
    }
    /// Skip any run of statement terminators.
    fn skip_newlines(&mut self) {
        while matches!(self.peek(), Tok::Newline) {
            self.advance();
        }
    }

    fn program(&mut self) -> Result<Program, String> {
        let mut uses = Vec::new();
        let mut globals = Vec::new();
        let mut functions = Vec::new();
        self.skip_newlines();
        while !matches!(self.peek(), Tok::Eof) {
            if self.eat_kw("Using") {
                if let Tok::Str(s) = self.peek().clone() {
                    self.advance();
                    uses.push(s);
                }
                self.skip_newlines();
            } else if self.is_kw("Function") {
                functions.push(self.function()?);
                self.skip_newlines();
            } else {
                // File-scope declaration (Dim / Global / Const / assignment).
                globals.push(self.statement()?);
                self.skip_newlines();
            }
        }
        Ok(Program { uses, globals, functions })
    }

    fn function(&mut self) -> Result<Function, String> {
        self.eat_kw("Function");
        let name = self.ident()?;
        self.expect(&Tok::LParen)?;
        let mut params = Vec::new();
        if !matches!(self.peek(), Tok::RParen) {
            loop {
                let pname = self.ident()?;
                // Optional default value (`Param$=""`, `Param%=0`).
                let default = if matches!(self.peek(), Tok::Eq) {
                    self.advance();
                    Some(self.expr()?)
                } else {
                    None
                };
                params.push(Param { name: pname, default });
                if matches!(self.peek(), Tok::Comma) {
                    self.advance();
                } else {
                    break;
                }
            }
        }
        self.expect(&Tok::RParen)?;
        let body = self.block(&["End"])?;
        self.eat_kw("End");
        self.eat_kw("Function");
        Ok(Function { name, params, body })
    }

    /// Parse statements until a terminator keyword (one of `stops`) is the next
    /// token (not consumed).
    fn block(&mut self, stops: &[&str]) -> Result<Vec<Stmt>, String> {
        let mut stmts = Vec::new();
        loop {
            self.skip_newlines();
            if matches!(self.peek(), Tok::Eof) {
                break;
            }
            if stops.iter().any(|k| self.is_kw(k)) {
                break;
            }
            stmts.push(self.statement()?);
        }
        Ok(stmts)
    }

    fn statement(&mut self) -> Result<Stmt, String> {
        // Consume declaration modifiers (`Local x = …`, `Global y`, `Const Z = …`).
        while self.is_kw("Local") || self.is_kw("Global") || self.is_kw("Const") {
            self.advance();
        }
        if self.is_kw("Return") {
            self.advance();
            // Optional return expression (none if newline/terminator follows).
            if matches!(self.peek(), Tok::Newline | Tok::Eof) {
                return Ok(Stmt::Return(None));
            }
            let e = self.expr()?;
            return Ok(Stmt::Return(Some(e)));
        }
        if self.is_kw("If") {
            return self.if_stmt();
        }
        if self.is_kw("While") {
            self.advance();
            let cond = self.expr()?;
            let body = self.block(&["Wend"])?;
            self.eat_kw("Wend");
            return Ok(Stmt::While { cond, body });
        }
        if self.is_kw("For") {
            self.advance();
            let var = self.ident()?;
            self.expect(&Tok::Eq)?;
            let from = self.expr()?;
            if !self.eat_kw("To") {
                return Err(format!("line {}: expected To in For", self.line()));
            }
            let to = self.expr()?;
            let body = self.block(&["Next"])?;
            self.eat_kw("Next");
            return Ok(Stmt::For { var, from, to, body });
        }
        if self.is_kw("Repeat") {
            self.advance();
            let body = self.block(&["Until"])?;
            self.eat_kw("Until");
            let cond = self.expr()?;
            return Ok(Stmt::Repeat { body, cond });
        }
        if self.is_kw("Select") {
            self.advance();
            let subject = self.expr()?;
            let mut cases = Vec::new();
            let mut default = None;
            loop {
                self.skip_newlines();
                if self.is_kw("Case") {
                    self.advance();
                    let mut vals = vec![self.expr()?];
                    while matches!(self.peek(), Tok::Comma) {
                        self.advance();
                        vals.push(self.expr()?);
                    }
                    let body = self.block(&["Case", "Default", "End"])?;
                    cases.push((vals, body));
                } else if self.is_kw("Default") {
                    self.advance();
                    default = Some(self.block(&["Case", "End"])?);
                } else {
                    break;
                }
            }
            self.eat_kw("End");
            self.eat_kw("Select");
            return Ok(Stmt::Select { subject, cases, default });
        }
        if self.is_kw("Dim") {
            self.advance();
            let name = self.ident()?;
            let dims = if matches!(self.peek(), Tok::LParen) {
                self.call_args()?
            } else {
                Vec::new()
            };
            return Ok(Stmt::Dim { name, dims });
        }

        // Identifier-led: assignment or call.
        let name = self.ident()?;
        match self.peek() {
            Tok::Eq => {
                self.advance();
                let value = self.expr()?;
                Ok(Stmt::Assign { name, value })
            }
            Tok::LParen => {
                let args = self.call_args()?;
                // `name(indices) = value` is an array-element assignment.
                if matches!(self.peek(), Tok::Eq) {
                    self.advance();
                    let value = self.expr()?;
                    Ok(Stmt::SetArray { name, indices: args, value })
                } else {
                    Ok(Stmt::Call { name, args })
                }
            }
            // Bare command (no parens, no args) — e.g. `DoEvents`.
            Tok::Newline | Tok::Eof => Ok(Stmt::Call { name, args: Vec::new() }),
            other => Err(format!("line {}: unexpected {:?} after identifier '{name}'", self.line(), other)),
        }
    }

    fn if_stmt(&mut self) -> Result<Stmt, String> {
        self.eat_kw("If");
        let cond = self.expr()?;
        let had_then = self.eat_kw("Then");
        // Single-line form `If cond Then stmt [Else stmt]` (no `EndIf`): a
        // statement follows `Then` on the same line.
        if had_then && !matches!(self.peek(), Tok::Newline | Tok::Eof) {
            let then_stmt = self.statement()?;
            let els = if self.eat_kw("Else") {
                Some(vec![self.statement()?])
            } else {
                None
            };
            return Ok(Stmt::If { cond, then: vec![then_stmt], elifs: Vec::new(), els });
        }
        // Block form (`If … [Then]⏎ … [ElseIf…] [Else…] EndIf`).
        let then = self.block(&["ElseIf", "Else", "EndIf"])?;
        let mut elifs = Vec::new();
        while self.is_kw("ElseIf") {
            self.advance();
            let c = self.expr()?;
            self.eat_kw("Then");
            let b = self.block(&["ElseIf", "Else", "EndIf"])?;
            elifs.push((c, b));
        }
        let els = if self.eat_kw("Else") {
            Some(self.block(&["EndIf"])?)
        } else {
            None
        };
        self.eat_kw("EndIf");
        Ok(Stmt::If { cond, then, elifs, els })
    }

    fn call_args(&mut self) -> Result<Vec<Expr>, String> {
        self.expect(&Tok::LParen)?;
        let mut args = Vec::new();
        if !matches!(self.peek(), Tok::RParen) {
            loop {
                args.push(self.expr()?);
                if matches!(self.peek(), Tok::Comma) {
                    self.advance();
                } else {
                    break;
                }
            }
        }
        self.expect(&Tok::RParen)?;
        Ok(args)
    }

    fn ident(&mut self) -> Result<String, String> {
        match self.advance() {
            Tok::Ident(s) => Ok(s),
            other => Err(format!("line {}: expected identifier, found {:?}", self.line(), other)),
        }
    }

    // --- Expression parsing (Blitz precedence, low → high). ---
    fn expr(&mut self) -> Result<Expr, String> {
        self.or_expr()
    }
    fn or_expr(&mut self) -> Result<Expr, String> {
        let mut lhs = self.and_expr()?;
        while self.is_kw("Or") {
            self.advance();
            let rhs = self.and_expr()?;
            lhs = bin(BinOp::Or, lhs, rhs);
        }
        Ok(lhs)
    }
    fn and_expr(&mut self) -> Result<Expr, String> {
        let mut lhs = self.cmp_expr()?;
        while self.is_kw("And") {
            self.advance();
            let rhs = self.cmp_expr()?;
            lhs = bin(BinOp::And, lhs, rhs);
        }
        Ok(lhs)
    }
    fn cmp_expr(&mut self) -> Result<Expr, String> {
        let mut lhs = self.add_expr()?;
        loop {
            let op = match self.peek() {
                Tok::Eq => BinOp::Eq,
                Tok::Ne => BinOp::Ne,
                Tok::Lt => BinOp::Lt,
                Tok::Gt => BinOp::Gt,
                Tok::Le => BinOp::Le,
                Tok::Ge => BinOp::Ge,
                _ => break,
            };
            self.advance();
            let rhs = self.add_expr()?;
            lhs = bin(op, lhs, rhs);
        }
        Ok(lhs)
    }
    fn add_expr(&mut self) -> Result<Expr, String> {
        let mut lhs = self.mul_expr()?;
        loop {
            let op = match self.peek() {
                Tok::Plus => BinOp::Add,
                Tok::Minus => BinOp::Sub,
                _ => break,
            };
            self.advance();
            let rhs = self.mul_expr()?;
            lhs = bin(op, lhs, rhs);
        }
        Ok(lhs)
    }
    fn mul_expr(&mut self) -> Result<Expr, String> {
        let mut lhs = self.unary()?;
        loop {
            let op = match self.peek() {
                Tok::Star => BinOp::Mul,
                Tok::Slash => BinOp::Div,
                _ if self.is_kw("Mod") => BinOp::Mod,
                _ => break,
            };
            self.advance();
            let rhs = self.unary()?;
            lhs = bin(op, lhs, rhs);
        }
        Ok(lhs)
    }
    fn unary(&mut self) -> Result<Expr, String> {
        if matches!(self.peek(), Tok::Minus) {
            self.advance();
            return Ok(Expr::Unary { op: UnOp::Neg, rhs: Box::new(self.unary()?) });
        }
        // Leading unary `+` is a no-op (identity) in Blitz — shipped content uses
        // it (e.g. `+ Name + "..."` in SendMail.rsl/UpdateMail.rsl). Accept and
        // discard it so those scripts parse; `+num`/`+str` both yield the operand.
        if matches!(self.peek(), Tok::Plus) {
            self.advance();
            return self.unary();
        }
        if self.is_kw("Not") {
            self.advance();
            return Ok(Expr::Unary { op: UnOp::Not, rhs: Box::new(self.unary()?) });
        }
        self.primary()
    }
    fn primary(&mut self) -> Result<Expr, String> {
        match self.peek().clone() {
            Tok::Int(n) => { self.advance(); Ok(Expr::Int(n)) }
            Tok::Float(f) => { self.advance(); Ok(Expr::Float(f)) }
            Tok::Str(s) => { self.advance(); Ok(Expr::Str(s)) }
            Tok::LParen => {
                self.advance();
                let e = self.expr()?;
                self.expect(&Tok::RParen)?;
                Ok(e)
            }
            Tok::Ident(name) => {
                self.advance();
                if matches!(self.peek(), Tok::LParen) {
                    let args = self.call_args()?;
                    Ok(Expr::Call { name, args })
                } else {
                    Ok(Expr::Var(name))
                }
            }
            other => Err(format!("line {}: unexpected {:?} in expression", self.line(), other)),
        }
    }
}

fn bin(op: BinOp, lhs: Expr, rhs: Expr) -> Expr {
    Expr::Binary { op, lhs: Box::new(lhs), rhs: Box::new(rhs) }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_default_script() {
        let src = r#"Using "RC_Core.rcm"
; Default server script
Function Main()
	Return
End Function

Function Examine()
	Player = Actor()
	Target = ContextActor()
	Output(Actor(), "This is a " + Name(Target))
End Function
"#;
        let prog = parse(src).unwrap();
        assert_eq!(prog.uses, vec!["RC_Core.rcm"]);
        assert_eq!(prog.functions.len(), 2);
        assert_eq!(prog.functions[0].name, "Main");
        assert_eq!(prog.functions[0].body, vec![Stmt::Return(None)]);
        let ex = &prog.functions[1];
        assert_eq!(ex.name, "Examine");
        // Player = Actor()
        assert_eq!(
            ex.body[0],
            Stmt::Assign { name: "Player".into(), value: Expr::Call { name: "Actor".into(), args: vec![] } }
        );
        // Output(Actor(), "This is a " + Name(Target))
        match &ex.body[2] {
            Stmt::Call { name, args } => {
                assert_eq!(name, "Output");
                assert_eq!(args.len(), 2);
                assert!(matches!(&args[1], Expr::Binary { op: BinOp::Add, .. }));
            }
            s => panic!("expected call, got {s:?}"),
        }
    }

    #[test]
    fn parses_if_elseif_else() {
        let src = r#"Function F()
	If a = 1 Then
		Output(p, "one")
	ElseIf a > 2
		Output(p, "big")
	Else
		Output(p, "other")
	EndIf
End Function
"#;
        let prog = parse(src).unwrap();
        match &prog.functions[0].body[0] {
            Stmt::If { cond, then, elifs, els } => {
                assert!(matches!(cond, Expr::Binary { op: BinOp::Eq, .. }));
                assert_eq!(then.len(), 1);
                assert_eq!(elifs.len(), 1);
                assert!(els.is_some());
            }
            s => panic!("expected If, got {s:?}"),
        }
    }

    #[test]
    fn parses_while_and_for() {
        let src = r#"Function F()
	While n < 10
		n = n + 1
	Wend
	For i = 0 To 5
		Output(p, i)
	Next
End Function
"#;
        let prog = parse(src).unwrap();
        assert!(matches!(prog.functions[0].body[0], Stmt::While { .. }));
        assert!(matches!(prog.functions[0].body[1], Stmt::For { .. }));
    }

    #[test]
    fn precedence_and_unary() {
        // 1 + 2 * 3 → 1 + (2*3); Not a And b → (Not a) And b
        let src = "Function F()\n\tx = 1 + 2 * 3\n\ty = Not a And b\nEnd Function\n";
        let prog = parse(src).unwrap();
        let f = &prog.functions[0];
        match &f.body[0] {
            Stmt::Assign { value: Expr::Binary { op: BinOp::Add, rhs, .. }, .. } => {
                assert!(matches!(**rhs, Expr::Binary { op: BinOp::Mul, .. }));
            }
            s => panic!("got {s:?}"),
        }
        match &f.body[1] {
            Stmt::Assign { value: Expr::Binary { op: BinOp::And, lhs, .. }, .. } => {
                assert!(matches!(**lhs, Expr::Unary { op: UnOp::Not, .. }));
            }
            s => panic!("got {s:?}"),
        }
    }

    #[test]
    fn parse_error_is_reported_not_panicked() {
        assert!(parse("Function F(\nEnd Function").is_err());
    }

    #[test]
    fn leading_unary_plus_is_a_noop() {
        // Blitz tolerates a leading `+` (identity); shipped SendMail.rsl /
        // UpdateMail.rsl use `+ Name + "..."`. The Rust parser used to reject it
        // ("unexpected Plus in expression"), so those scripts failed to load.
        let src = "Function F()\n\tx = + a + \"hi\"\n\ty = +5\nEnd Function\n";
        let prog = parse(src).expect("leading unary + must parse");
        let f = &prog.functions[0];
        // `+ a + "hi"` → the leading + is discarded, leaving `a + "hi"` (an Add).
        assert!(matches!(f.body[0], Stmt::Assign { value: Expr::Binary { op: BinOp::Add, .. }, .. }));
        // `+5` → just the operand `5` (the + is a no-op, not a Unary node).
        assert!(matches!(f.body[1], Stmt::Assign { value: Expr::Int(5), .. }));
    }
}
