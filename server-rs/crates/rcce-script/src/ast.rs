//! RSL abstract syntax tree.

/// A whole script file: its `Using` imports and its functions.
#[derive(Clone, Debug, PartialEq)]
pub struct Program {
    pub uses: Vec<String>,
    /// File-scope declarations (`Dim`/`Global`/`Const` before the functions).
    pub globals: Vec<Stmt>,
    pub functions: Vec<Function>,
}

/// A function parameter with an optional default value (`Param$=""`).
#[derive(Clone, Debug, PartialEq)]
pub struct Param {
    pub name: String,
    pub default: Option<Expr>,
}

/// A `Function Name(params) … End Function`.
#[derive(Clone, Debug, PartialEq)]
pub struct Function {
    pub name: String,
    pub params: Vec<Param>,
    pub body: Vec<Stmt>,
}

/// A statement.
#[derive(Clone, Debug, PartialEq)]
pub enum Stmt {
    /// `name = expr`.
    Assign { name: String, value: Expr },
    /// A call used as a statement (e.g. `Output(Actor(), "hi")`).
    Call { name: String, args: Vec<Expr> },
    /// Array-element assignment `name(indices…) = value`. (Array storage is
    /// deferred; this evaluates the rhs/indices for side effects.)
    SetArray { name: String, indices: Vec<Expr>, value: Expr },
    /// `If cond [Then] … [ElseIf cond …] [Else …] EndIf`.
    If {
        cond: Expr,
        then: Vec<Stmt>,
        elifs: Vec<(Expr, Vec<Stmt>)>,
        els: Option<Vec<Stmt>>,
    },
    /// `While cond … Wend`.
    While { cond: Expr, body: Vec<Stmt> },
    /// `For v = from To to … Next`.
    For {
        var: String,
        from: Expr,
        to: Expr,
        body: Vec<Stmt>,
    },
    /// `Repeat … Until cond`.
    Repeat { body: Vec<Stmt>, cond: Expr },
    /// `Select subject  Case v[,v]… …  [Default …]  End Select`.
    Select {
        subject: Expr,
        cases: Vec<(Vec<Expr>, Vec<Stmt>)>,
        default: Option<Vec<Stmt>>,
    },
    /// `Dim name(dims…)` array declaration.
    Dim { name: String, dims: Vec<Expr> },
    /// `Return [expr]`.
    Return(Option<Expr>),
}

/// An expression.
#[derive(Clone, Debug, PartialEq)]
pub enum Expr {
    Int(i64),
    Float(f64),
    Str(String),
    /// A variable read.
    Var(String),
    /// A function/command call.
    Call { name: String, args: Vec<Expr> },
    Unary { op: UnOp, rhs: Box<Expr> },
    Binary { op: BinOp, lhs: Box<Expr>, rhs: Box<Expr> },
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum UnOp {
    Neg,
    Not,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum BinOp {
    Add,
    Sub,
    Mul,
    Div,
    Mod,
    Eq,
    Ne,
    Lt,
    Gt,
    Le,
    Ge,
    And,
    Or,
}
