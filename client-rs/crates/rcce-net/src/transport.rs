//! Transport seam — abstracts the ENet link so backends are interchangeable:
//!
//! * **FFI backend** (`rcenet-ffi-probe::FfiTransport`, current): wraps the
//!   shipped 32-bit `RCEnet.dll`. Proven to handshake with the live server.
//! * **Pure-Rust backend** (future): a fork-matched ENet (the shipped DLL uses
//!   a non-stock 8-byte header — see the port plan), letting the client go
//!   64-bit and fully cross-platform as a drop-in swap.
//!
//! The trait deals only in framed messages — a 1-byte type plus payload — which
//! is exactly what the DLL puts on the wire (`Data[0]=MessageType`). Payload
//! field encoding (big-endian, per `RCE_StrFromInt$`) is the caller's job.

/// A message received from the server.
#[derive(Debug, Clone)]
pub struct RecvMessage {
    /// The 1-byte message type (a `P_*` constant).
    pub msg_type: u8,
    /// Opaque sender/connection id reported by the backend.
    pub connection: i32,
    /// Payload bytes (without the leading type byte).
    pub data: Vec<u8>,
}

/// Errors a transport can surface.
#[derive(Debug, thiserror::Error)]
pub enum TransportError {
    /// Connect did not complete; the inner code is the backend's return value
    /// (e.g. RCEnet's -2 = no VERIFY_CONNECT, -4 = no peer, -1 = host create).
    #[error("connect failed (code {0})")]
    ConnectFailed(i32),
    /// Backend setup/marshalling failure (DLL load, missing symbol, bad string).
    #[error("transport backend error: {0}")]
    Backend(String),
}

/// An ENet-style reliable/unreliable message transport to the game server.
pub trait Transport {
    /// Connect to `host:port`, completing the handshake. Returns an opaque
    /// destination handle to pass back to [`send`](Self::send) /
    /// [`disconnect`](Self::disconnect).
    fn connect(&mut self, host: &str, port: u16) -> Result<i32, TransportError>;

    /// Send a message: `msg_type` byte + `payload`. `reliable` selects the
    /// reliable channel (1) vs unreliable (2).
    fn send(&mut self, dest: i32, msg_type: u8, payload: &[u8], reliable: bool);

    /// Pump the network and drain all messages received since the last poll.
    fn poll(&mut self) -> Vec<RecvMessage>;

    /// Disconnect a peer handle.
    fn disconnect(&mut self, dest: i32);
}
