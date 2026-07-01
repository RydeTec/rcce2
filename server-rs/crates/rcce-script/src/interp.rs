//! Tree-walking interpreter for the RSL [`Program`] AST.
//!
//! RSL is dynamically typed: a [`Value`] is an int, float, or string, and `+`
//! concatenates when either operand is a string (numbers auto-convert). Variable
//! names and function names are case-insensitive. User-defined functions run
//! recursively; every *other* call (the native `BVM_*` commands like `Actor()`,
//! `Output()`, `Name()`, and string builtins) is dispatched to a [`Host`] — the
//! seam where the interpreter calls back into the game (`ServerState`).

use std::collections::HashMap;
use std::time::Instant;

use crate::ast::{BinOp, Expr, Function, Program, Stmt, UnOp};

/// A runtime value.
#[derive(Clone, Debug, PartialEq)]
pub enum Value {
    Int(i64),
    Float(f64),
    Str(String),
}

impl Value {
    pub fn to_int(&self) -> i64 {
        match self {
            Value::Int(n) => *n,
            Value::Float(f) => *f as i64,
            // Blitz parses the leading numeric prefix (`Int("17x")` → 17).
            Value::Str(s) => leading_number(s).parse::<f64>().map(|f| f as i64).unwrap_or(0),
        }
    }
    pub fn to_float(&self) -> f64 {
        match self {
            Value::Int(n) => *n as f64,
            Value::Float(f) => *f,
            Value::Str(s) => leading_number(s).parse().unwrap_or(0.0),
        }
    }
    pub fn to_string_value(&self) -> String {
        match self {
            Value::Int(n) => n.to_string(),
            Value::Float(f) => format_float(*f),
            Value::Str(s) => s.clone(),
        }
    }
    pub fn truthy(&self) -> bool {
        match self {
            Value::Int(n) => *n != 0,
            Value::Float(f) => *f != 0.0,
            Value::Str(s) => !s.is_empty(),
        }
    }
    fn is_float(&self) -> bool {
        matches!(self, Value::Float(_))
    }
}

/// The leading numeric prefix of a string (`[+-]?digits[.digits]`), for the
/// Blitz "parse what you can" string→number coercion.
fn leading_number(s: &str) -> &str {
    let t = s.trim_start();
    let b = t.as_bytes();
    let mut i = 0;
    if i < b.len() && (b[i] == b'-' || b[i] == b'+') {
        i += 1;
    }
    let mut seen_dot = false;
    while i < b.len() {
        match b[i] {
            c if c.is_ascii_digit() => i += 1,
            b'.' if !seen_dot => {
                seen_dot = true;
                i += 1;
            }
            _ => break,
        }
    }
    &t[..i]
}

/// Blitz-ish float formatting for string concatenation (drop a trailing `.0`).
fn format_float(f: f64) -> String {
    if f.fract() == 0.0 && f.abs() < 1e15 {
        format!("{}", f as i64)
    } else {
        format!("{f}")
    }
}

/// The native-command seam. Any call that isn't a user-defined function in the
/// program is routed here — the `BVM_*` game commands and string builtins.
pub trait Host {
    /// Invoke native command `name` (case-insensitive) with `args`; return its
    /// value (`Int(0)` for void commands).
    fn call(&mut self, name: &str, args: &[Value]) -> Value;
}

/// Control-flow result of executing a statement/block.
enum Flow {
    Normal,
    Return(Value),
}

/// Run `function` (by name, case-insensitive) in `program` with `args`,
/// dispatching native calls to `host`. Returns the function's return value
/// (`Int(0)` if it falls off the end or the function is missing).
pub fn run_function(
    program: &Program,
    host: &mut dyn Host,
    function: &str,
    args: Vec<Value>,
) -> Value {
    let funcs: HashMap<String, &Function> = program
        .functions
        .iter()
        .map(|f| (f.name.to_lowercase(), f))
        .collect();
    let mut interp = Interp { funcs, host, steps: 0, start: Instant::now() };
    interp.run(function, args)
}

/// Hard cap on statements executed per script invocation — a global backstop
/// against runaway loops (including `For`/nested loops the per-loop guards
/// don't bound) and deep recursion. Real event-hook scripts execute far fewer.
const STEP_BUDGET: u64 = 500_000;

struct Interp<'a> {
    funcs: HashMap<String, &'a Function>,
    host: &'a mut dyn Host,
    steps: u64,
    /// Monotonic epoch for `MilliSecs()`. Handled in-interpreter rather than via
    /// the [`Host`] so timed busy-waits (`DoEvents`'s `While MilliSecs()-A < x`)
    /// spin on the script's own thread without a channel round-trip per poll,
    /// and so time actually advances (a host returning a constant would loop
    /// forever).
    start: Instant,
}

type Scope = HashMap<String, Value>;

impl Interp<'_> {
    fn run(&mut self, function: &str, args: Vec<Value>) -> Value {
        let Some(f) = self.funcs.get(&function.to_lowercase()).copied() else {
            return Value::Int(0);
        };
        let mut scope = Scope::new();
        for (i, p) in f.params.iter().enumerate() {
            // Use the supplied argument, else the parameter's default, else 0.
            let v = match args.get(i) {
                Some(a) => a.clone(),
                None => match &p.default {
                    Some(e) => self.eval(e, &mut scope),
                    None => Value::Int(0),
                },
            };
            scope.insert(p.name.to_lowercase(), v);
        }
        match self.exec_block(&f.body, &mut scope) {
            Flow::Return(v) => v,
            Flow::Normal => Value::Int(0),
        }
    }

    fn exec_block(&mut self, stmts: &[Stmt], scope: &mut Scope) -> Flow {
        for s in stmts {
            if let Flow::Return(v) = self.exec_stmt(s, scope) {
                return Flow::Return(v);
            }
        }
        Flow::Normal
    }

    fn exec_stmt(&mut self, s: &Stmt, scope: &mut Scope) -> Flow {
        // Global instruction budget — bounds the whole invocation regardless of
        // loop structure. On exhaustion, abort the run (treated as a return).
        self.steps += 1;
        if self.steps > STEP_BUDGET {
            return Flow::Return(Value::Int(0));
        }
        match s {
            Stmt::Assign { name, value } => {
                let v = self.eval(value, scope);
                scope.insert(name.to_lowercase(), v);
                Flow::Normal
            }
            Stmt::Call { name, args } => {
                let _ = self.eval_call(name, args, scope);
                Flow::Normal
            }
            Stmt::If { cond, then, elifs, els } => {
                if self.eval(cond, scope).truthy() {
                    return self.exec_block(then, scope);
                }
                for (c, b) in elifs {
                    if self.eval(c, scope).truthy() {
                        return self.exec_block(b, scope);
                    }
                }
                if let Some(b) = els {
                    return self.exec_block(b, scope);
                }
                Flow::Normal
            }
            Stmt::While { cond, body } => {
                let mut guard = 0u64;
                while self.eval(cond, scope).truthy() {
                    if let Flow::Return(v) = self.exec_block(body, scope) {
                        return Flow::Return(v);
                    }
                    guard += 1;
                    if guard > 100_000 {
                        break; // runaway-loop backstop
                    }
                }
                Flow::Normal
            }
            Stmt::Repeat { body, cond } => {
                let mut guard = 0u64;
                loop {
                    if let Flow::Return(v) = self.exec_block(body, scope) {
                        return Flow::Return(v);
                    }
                    if self.eval(cond, scope).truthy() {
                        break;
                    }
                    guard += 1;
                    if guard > 100_000 {
                        break;
                    }
                }
                Flow::Normal
            }
            Stmt::For { var, from, to, body } => {
                let start = self.eval(from, scope).to_int();
                let end = self.eval(to, scope).to_int();
                let key = var.to_lowercase();
                let mut i = start;
                while i <= end {
                    scope.insert(key.clone(), Value::Int(i));
                    if let Flow::Return(v) = self.exec_block(body, scope) {
                        return Flow::Return(v);
                    }
                    // Re-read the loop var (the body may have changed it).
                    i = scope.get(&key).map(|v| v.to_int()).unwrap_or(i) + 1;
                }
                Flow::Normal
            }
            Stmt::Select { subject, cases, default } => {
                let s = self.eval(subject, scope);
                for (vals, body) in cases {
                    for v in vals {
                        let cv = self.eval(v, scope);
                        if eval_binop(BinOp::Eq, s.clone(), cv).truthy() {
                            return self.exec_block(body, scope);
                        }
                    }
                }
                if let Some(d) = default {
                    return self.exec_block(d, scope);
                }
                Flow::Normal
            }
            Stmt::Dim { .. } => Flow::Normal, // arrays deferred
            Stmt::SetArray { indices, value, .. } => {
                // Array storage deferred; evaluate operands for their side effects.
                for i in indices {
                    self.eval(i, scope);
                }
                self.eval(value, scope);
                Flow::Normal
            }
            Stmt::Return(e) => {
                let v = e.as_ref().map(|e| self.eval(e, scope)).unwrap_or(Value::Int(0));
                Flow::Return(v)
            }
        }
    }

    fn eval(&mut self, e: &Expr, scope: &mut Scope) -> Value {
        match e {
            Expr::Int(n) => Value::Int(*n),
            Expr::Float(f) => Value::Float(*f),
            Expr::Str(s) => Value::Str(s.clone()),
            Expr::Var(name) => scope.get(&name.to_lowercase()).cloned().unwrap_or(Value::Int(0)),
            Expr::Call { name, args } => self.eval_call(name, args, scope),
            Expr::Unary { op, rhs } => {
                let v = self.eval(rhs, scope);
                match op {
                    UnOp::Neg => {
                        if v.is_float() {
                            Value::Float(-v.to_float())
                        } else {
                            Value::Int(-v.to_int())
                        }
                    }
                    UnOp::Not => Value::Int(if v.truthy() { 0 } else { 1 }),
                }
            }
            Expr::Binary { op, lhs, rhs } => {
                let a = self.eval(lhs, scope);
                let b = self.eval(rhs, scope);
                eval_binop(*op, a, b)
            }
        }
    }

    fn eval_call(&mut self, name: &str, args: &[Expr], scope: &mut Scope) -> Value {
        let argv: Vec<Value> = args.iter().map(|a| self.eval(a, scope)).collect();
        // A user-defined function shadows a native command of the same name.
        if self.funcs.contains_key(&name.to_lowercase()) {
            self.run(name, argv)
        } else if name.eq_ignore_ascii_case("millisecs") {
            // Monotonic ms since interpreter start — see `Interp::start`.
            Value::Int(self.start.elapsed().as_millis() as i64)
        } else {
            self.host.call(name, &argv)
        }
    }
}

fn eval_binop(op: BinOp, a: Value, b: Value) -> Value {
    match op {
        BinOp::Add => {
            // String concatenation if either side is a string.
            if matches!(a, Value::Str(_)) || matches!(b, Value::Str(_)) {
                Value::Str(a.to_string_value() + &b.to_string_value())
            } else if a.is_float() || b.is_float() {
                Value::Float(a.to_float() + b.to_float())
            } else {
                Value::Int(a.to_int() + b.to_int())
            }
        }
        BinOp::Sub | BinOp::Mul | BinOp::Div | BinOp::Mod => {
            if a.is_float() || b.is_float() {
                let (x, y) = (a.to_float(), b.to_float());
                Value::Float(match op {
                    BinOp::Sub => x - y,
                    BinOp::Mul => x * y,
                    BinOp::Div => if y == 0.0 { 0.0 } else { x / y },
                    BinOp::Mod => if y == 0.0 { 0.0 } else { x % y },
                    _ => unreachable!(),
                })
            } else {
                let (x, y) = (a.to_int(), b.to_int());
                Value::Int(match op {
                    BinOp::Sub => x - y,
                    BinOp::Mul => x * y,
                    BinOp::Div => if y == 0 { 0 } else { x / y },
                    BinOp::Mod => if y == 0 { 0 } else { x % y },
                    _ => unreachable!(),
                })
            }
        }
        BinOp::Eq | BinOp::Ne | BinOp::Lt | BinOp::Gt | BinOp::Le | BinOp::Ge => {
            let ord = if matches!(a, Value::Str(_)) || matches!(b, Value::Str(_)) {
                a.to_string_value().cmp(&b.to_string_value())
            } else {
                a.to_float().partial_cmp(&b.to_float()).unwrap_or(std::cmp::Ordering::Equal)
            };
            use std::cmp::Ordering::*;
            let truth = match op {
                BinOp::Eq => ord == Equal,
                BinOp::Ne => ord != Equal,
                BinOp::Lt => ord == Less,
                BinOp::Gt => ord == Greater,
                BinOp::Le => ord != Greater,
                BinOp::Ge => ord != Less,
                _ => unreachable!(),
            };
            Value::Int(if truth { 1 } else { 0 })
        }
        BinOp::And => Value::Int(if a.truthy() && b.truthy() { 1 } else { 0 }),
        BinOp::Or => Value::Int(if a.truthy() || b.truthy() { 1 } else { 0 }),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::parser::parse;

    /// A recording host: returns canned values for a few commands and logs
    /// `Output(actor, text)` calls.
    #[derive(Default)]
    struct TestHost {
        outputs: Vec<String>,
    }
    impl Host for TestHost {
        fn call(&mut self, name: &str, args: &[Value]) -> Value {
            match name.to_lowercase().as_str() {
                "actor" => Value::Int(42),
                "contextactor" => Value::Int(7),
                "name" => Value::Str(format!("Actor{}", args.first().map(|v| v.to_int()).unwrap_or(0))),
                "output" => {
                    self.outputs.push(args.get(1).map(|v| v.to_string_value()).unwrap_or_default());
                    Value::Int(0)
                }
                "len" => Value::Int(args.first().map(|v| v.to_string_value().len() as i64).unwrap_or(0)),
                _ => Value::Int(0),
            }
        }
    }

    fn run(src: &str, func: &str, args: Vec<Value>) -> (Value, TestHost) {
        let prog = parse(src).unwrap();
        let mut host = TestHost::default();
        let r = run_function(&prog, &mut host, func, args);
        (r, host)
    }

    #[test]
    fn examine_script_concatenates_and_outputs() {
        let src = r#"Function Examine()
	Player = Actor()
	Target = ContextActor()
	Output(Player, "This is a " + Name(Target))
End Function
"#;
        let (_r, host) = run(src, "Examine", vec![]);
        assert_eq!(host.outputs, vec!["This is a Actor7"]);
    }

    #[test]
    fn arithmetic_and_return() {
        let src = "Function Add(a, b)\n\tReturn a + b\nEnd Function\n";
        let (r, _) = run(src, "Add", vec![Value::Int(3), Value::Int(4)]);
        assert_eq!(r, Value::Int(7));
    }

    #[test]
    fn if_elseif_else_branches() {
        let src = r#"Function Pick(n)
	If n = 1
		Return "one"
	ElseIf n > 5
		Return "big"
	Else
		Return "other"
	EndIf
End Function
"#;
        assert_eq!(run(src, "Pick", vec![Value::Int(1)]).0, Value::Str("one".into()));
        assert_eq!(run(src, "Pick", vec![Value::Int(9)]).0, Value::Str("big".into()));
        assert_eq!(run(src, "Pick", vec![Value::Int(3)]).0, Value::Str("other".into()));
    }

    #[test]
    fn for_loop_sums() {
        let src = "Function Sum(n)\n\ttotal = 0\n\tFor i = 1 To n\n\t\ttotal = total + i\n\tNext\n\tReturn total\nEnd Function\n";
        assert_eq!(run(src, "Sum", vec![Value::Int(5)]).0, Value::Int(15));
    }

    #[test]
    fn while_loop_and_user_call() {
        let src = r#"Function Fact(n)
	r = 1
	i = 1
	While i <= n
		r = r * i
		i = i + 1
	Wend
	Return r
End Function
"#;
        assert_eq!(run(src, "Fact", vec![Value::Int(5)]).0, Value::Int(120));
    }

    #[test]
    fn float_concatenation_drops_trailing_zero() {
        let src = "Function F()\n\tReturn \"d=\" + 4.0\nEnd Function\n";
        assert_eq!(run(src, "F", vec![]).0, Value::Str("d=4".into()));
    }

    #[test]
    fn missing_function_returns_zero_not_panic() {
        let src = "Function Main()\n\tReturn\nEnd Function\n";
        assert_eq!(run(src, "Nonexistent", vec![]).0, Value::Int(0));
    }
}
