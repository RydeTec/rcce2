//! FFI to the vendored RCCE2 ENet fork (`vendor/`), compiled from the exact C
//! source the server's `RCEnet.dll` was built from — wire-compatible by
//! construction (including the fork's `[sessionID u32 LE][peerID u16 BE]
//! [sentTime u16 BE]` header). Builds 64-bit + cross-platform (`vendor/unix.c`).
//!
//! Signatures match THIS fork (verified against `vendor/host.c`), which differs
//! from stock ENet: `enet_host_create` takes 4 args (no channelLimit) and
//! `enet_host_connect` takes 3 (no connect-data). The fork has no
//! `enet_linked_version`.

use std::os::raw::{c_char, c_int, c_void};

pub mod transport;
pub use transport::EnetTransport;

/// Opaque ENet host handle.
#[repr(C)]
pub struct ENetHost {
    _private: [u8; 0],
}
/// Opaque ENet peer handle.
#[repr(C)]
pub struct ENetPeer {
    _private: [u8; 0],
}

#[repr(C)]
pub struct ENetAddress {
    pub host: u32,
    pub port: u16,
}

#[repr(C)]
pub struct ENetEvent {
    pub type_: c_int,
    pub peer: *mut ENetPeer,
    pub channel_id: u8,
    pub data: u32,
    pub packet: *mut ENetPacket,
}

#[repr(C)]
pub struct ENetPacket {
    pub reference_count: usize,
    pub flags: u32,
    pub data: *mut u8,
    pub data_length: usize,
    pub free_callback: *mut c_void,
}

pub const ENET_EVENT_TYPE_NONE: c_int = 0;
pub const ENET_EVENT_TYPE_CONNECT: c_int = 1;
pub const ENET_EVENT_TYPE_DISCONNECT: c_int = 2;
pub const ENET_EVENT_TYPE_RECEIVE: c_int = 3;

pub const ENET_PACKET_FLAG_RELIABLE: u32 = 1;

extern "C" {
    pub fn enet_initialize() -> c_int;
    pub fn enet_deinitialize();
    pub fn enet_host_create(
        address: *const ENetAddress,
        peer_count: usize,
        incoming_bandwidth: u32,
        outgoing_bandwidth: u32,
    ) -> *mut ENetHost;
    pub fn enet_host_destroy(host: *mut ENetHost);
    pub fn enet_host_connect(
        host: *mut ENetHost,
        address: *const ENetAddress,
        channel_count: usize,
    ) -> *mut ENetPeer;
    pub fn enet_host_service(host: *mut ENetHost, event: *mut ENetEvent, timeout: u32) -> c_int;
    pub fn enet_host_flush(host: *mut ENetHost);
    pub fn enet_peer_send(peer: *mut ENetPeer, channel: u8, packet: *mut ENetPacket) -> c_int;
    pub fn enet_peer_disconnect_now(peer: *mut ENetPeer, data: u32);
    /// Graceful disconnect: queues a disconnect that is acknowledged by the peer
    /// (reliable), unlike `_now`. Service the host afterward until the DISCONNECT
    /// event arrives so the server clears the session before we exit (CBT login).
    pub fn enet_peer_disconnect(peer: *mut ENetPeer, data: u32);
    pub fn enet_peer_reset(peer: *mut ENetPeer);
    pub fn enet_packet_create(
        data: *const c_void,
        data_length: usize,
        flags: u32,
    ) -> *mut ENetPacket;
    pub fn enet_packet_destroy(packet: *mut ENetPacket);
    pub fn enet_address_set_host(address: *mut ENetAddress, host_name: *const c_char) -> c_int;
}

/// Init/deinit smoke test — confirms the vendored C built, linked, and runs.
pub fn smoke() -> i32 {
    unsafe {
        let r = enet_initialize();
        enet_deinitialize();
        r
    }
}
