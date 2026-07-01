//! RCCE2 server-side ENet **host**.
//!
//! The client (`client-rs`) uses the vendored RCCE2 ENet fork (`enet-sys`) in
//! *connect* mode — one outbound peer. The authoritative server is the mirror
//! image: a *listening host* bound to a port, accepting up to `max_peers`
//! inbound client connections, pumped from a single-threaded tick loop exactly
//! as `Server.bb` pumps `RCE_Update()` each frame.
//!
//! Because the transport is the **same vendored C fork** the live clients
//! already speak (8-byte fork header and all), a client cannot tell a Rust host
//! from the Blitz `RCEnet.dll` host at the wire level — the de-risk that makes
//! the whole port viable.
//!
//! This crate is intentionally thin: it owns the host handle, tracks connected
//! peers behind stable [`PeerId`]s, and surfaces a poll→`Vec<ServerEvent>` /
//! `send` API. Packet *decoding* (RCCE message framing, `P_*` dispatch) lives
//! one layer up; this crate moves opaque byte payloads.

use std::collections::HashMap;
use std::os::raw::c_void;

use enet_sys::{
    enet_host_create, enet_host_destroy, enet_host_flush, enet_host_service, enet_initialize,
    enet_packet_create, enet_packet_destroy, enet_peer_disconnect, enet_peer_send, ENetAddress,
    ENetEvent, ENetHost, ENetPeer, ENET_EVENT_TYPE_CONNECT, ENET_EVENT_TYPE_DISCONNECT,
    ENET_EVENT_TYPE_RECEIVE, ENET_PACKET_FLAG_RELIABLE,
};

/// ENet's `ENET_HOST_ANY` — bind on every local interface.
const ENET_HOST_ANY: u32 = 0;

/// Stable, host-assigned identifier for a connected client peer.
///
/// The raw `*mut ENetPeer` is reused by ENet across reconnects, so we never
/// hand it to callers; a monotonically increasing `PeerId` is the public handle
/// and the map back to the live pointer is kept private to this crate.
#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)]
pub struct PeerId(pub u32);

/// One thing that happened on the host during a [`EnetHostServer::poll`].
#[derive(Clone, Debug)]
pub enum ServerEvent {
    /// A new client finished the ENet handshake.
    Connect(PeerId),
    /// A reliable/unreliable payload arrived from a connected client.
    Receive {
        peer: PeerId,
        channel: u8,
        data: Vec<u8>,
    },
    /// A client disconnected (kick, quit, timeout, or network loss).
    Disconnect(PeerId),
}

/// Ensure `enet_initialize()` runs exactly once for the process.
fn ensure_enet_initialized() {
    use std::sync::Once;
    static INIT: Once = Once::new();
    INIT.call_once(|| unsafe {
        // The fork returns 0 on success. A failure here is fatal at boot, but
        // we surface it lazily via a null host from `bind` rather than panic in
        // a static initializer.
        let _ = enet_initialize();
    });
}

/// A listening ENet host owning every connected client peer.
///
/// Single-threaded by construction (the raw ENet handles are not `Send`); the
/// server tick loop owns exactly one of these and pumps it each frame.
pub struct EnetHostServer {
    host: *mut ENetHost,
    /// `PeerId` → live ENet peer pointer (for `send`).
    peers: HashMap<PeerId, *mut ENetPeer>,
    /// ENet peer pointer (as `usize`) → `PeerId` (for inbound event lookup).
    by_ptr: HashMap<usize, PeerId>,
    next_id: u32,
}

impl EnetHostServer {
    /// Bind a listening host on `port`, accepting up to `max_peers` clients.
    ///
    /// Returns `None` if the port is unavailable or ENet failed to create the
    /// host (the caller logs and exits, matching `Server.bb`'s
    /// "Could not open port" path).
    pub fn bind(port: u16, max_peers: usize) -> Option<Self> {
        ensure_enet_initialized();
        // A non-null bind address is what makes this a *server* host (vs. the
        // client's null-address connect host). Host order for `port`, matching
        // the client transport and the ENet API.
        let addr = ENetAddress {
            host: ENET_HOST_ANY,
            port,
        };
        // 0 bandwidth limits = unthrottled, same as `RCE_StartHost`'s defaults.
        let host = unsafe { enet_host_create(&addr, max_peers, 0, 0) };
        if host.is_null() {
            return None;
        }
        Some(Self {
            host,
            peers: HashMap::new(),
            by_ptr: HashMap::new(),
            next_id: 1,
        })
    }

    /// Number of currently connected client peers.
    pub fn peer_count(&self) -> usize {
        self.peers.len()
    }

    /// Service the host and drain every pending event.
    ///
    /// Blocks up to `timeout_ms` for the *first* event (so an idle server can
    /// park instead of busy-spinning), then drains the rest non-blocking. Pass a
    /// small timeout (a few ms) from the tick loop so simulation still runs each
    /// frame.
    pub fn poll(&mut self, timeout_ms: u32) -> Vec<ServerEvent> {
        let mut out = Vec::new();
        let mut timeout = timeout_ms;
        loop {
            let mut ev: ENetEvent = unsafe { std::mem::zeroed() };
            let r = unsafe { enet_host_service(self.host, &mut ev, timeout) };
            // After the first (possibly blocking) service, drain non-blocking.
            timeout = 0;
            if r <= 0 {
                // 0 = nothing within the timeout; <0 = transient service error.
                break;
            }
            if let Some(event) = self.translate(&ev) {
                out.push(event);
            }
        }
        out
    }

    /// Convert a raw ENet event into a [`ServerEvent`], updating peer bookkeeping.
    fn translate(&mut self, ev: &ENetEvent) -> Option<ServerEvent> {
        match ev.type_ {
            t if t == ENET_EVENT_TYPE_CONNECT => {
                let id = self.register_peer(ev.peer);
                Some(ServerEvent::Connect(id))
            }
            t if t == ENET_EVENT_TYPE_RECEIVE => {
                let id = *self.by_ptr.get(&(ev.peer as usize))?;
                let data = unsafe { copy_packet(ev.packet) };
                unsafe { enet_packet_destroy(ev.packet) };
                Some(ServerEvent::Receive {
                    peer: id,
                    channel: ev.channel_id,
                    data,
                })
            }
            t if t == ENET_EVENT_TYPE_DISCONNECT => {
                let id = self.unregister_peer(ev.peer)?;
                Some(ServerEvent::Disconnect(id))
            }
            _ => None,
        }
    }

    fn register_peer(&mut self, peer: *mut ENetPeer) -> PeerId {
        let id = PeerId(self.next_id);
        self.next_id += 1;
        self.peers.insert(id, peer);
        self.by_ptr.insert(peer as usize, id);
        id
    }

    fn unregister_peer(&mut self, peer: *mut ENetPeer) -> Option<PeerId> {
        let id = self.by_ptr.remove(&(peer as usize))?;
        self.peers.remove(&id);
        Some(id)
    }

    /// Send a payload to one connected peer. Returns `false` if the peer is gone
    /// or ENet rejected the packet (caller drops, never panics — wire-facing).
    pub fn send(&self, peer: PeerId, channel: u8, data: &[u8], reliable: bool) -> bool {
        let Some(&p) = self.peers.get(&peer) else {
            return false;
        };
        let flags = if reliable { ENET_PACKET_FLAG_RELIABLE } else { 0 };
        unsafe {
            let packet = enet_packet_create(data.as_ptr() as *const c_void, data.len(), flags);
            if packet.is_null() {
                return false;
            }
            // On success ENet takes ownership of `packet`; on failure we still
            // own it and must destroy it to avoid a leak.
            if enet_peer_send(p, channel, packet) < 0 {
                enet_packet_destroy(packet);
                return false;
            }
        }
        true
    }

    /// Request a graceful disconnect of a peer (acknowledged by the client).
    pub fn disconnect(&self, peer: PeerId) {
        if let Some(&p) = self.peers.get(&peer) {
            unsafe { enet_peer_disconnect(p, 0) };
        }
    }

    /// Flush queued outgoing packets immediately (call once at end of tick).
    pub fn flush(&self) {
        unsafe { enet_host_flush(self.host) };
    }
}

// SAFETY: `EnetHostServer` owns its raw ENet handles exclusively and is only
// ever accessed through `&mut self` from a single owning thread. It is never
// shared (`&`) across threads; moving ownership to a dedicated server thread is
// sound because no other thread retains a pointer into the same host. (ENet
// itself is not thread-safe for concurrent access to one host — which we never
// do.) This `Send` impl exists so the tick loop can run on its own thread.
unsafe impl Send for EnetHostServer {}

impl Drop for EnetHostServer {
    fn drop(&mut self) {
        if !self.host.is_null() {
            unsafe { enet_host_destroy(self.host) };
            self.host = std::ptr::null_mut();
        }
    }
}

/// Copy an inbound ENet packet's bytes into an owned `Vec` before it is
/// destroyed. `data`/`data_length` describe the payload; a zero-length or null
/// payload yields an empty `Vec`.
unsafe fn copy_packet(packet: *const enet_sys::ENetPacket) -> Vec<u8> {
    if packet.is_null() {
        return Vec::new();
    }
    let p = &*packet;
    if p.data.is_null() || p.data_length == 0 {
        return Vec::new();
    }
    std::slice::from_raw_parts(p.data, p.data_length).to_vec()
}
