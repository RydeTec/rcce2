//! `EnetTransport` — a [`Transport`] over the vendored ENet fork. Replicates the
//! RCEnet wrapper (`main.cpp`): create a client host, connect, send a type-0
//! NewClient on the CONNECT event, frame every message as `[type][payload]`,
//! reliable→channel 1 / unreliable→channel 2, and drain RECEIVE events on poll.
//!
//! 64-bit and cross-platform; the drop-in replacement for the 32-bit
//! `FfiTransport`. Single-connection: the `dest` handle is ignored (the active
//! peer is tracked internally), so `connect` returns a placeholder `1`.

use std::ffi::CString;
use std::os::raw::c_void;
use std::ptr;
use std::sync::Once;
use std::time::{Duration, Instant};

use rcce_net::{RecvMessage, Transport, TransportError};

use crate::*;

static INIT: Once = Once::new();
fn ensure_init() {
    INIT.call_once(|| unsafe {
        enet_initialize();
    });
}

pub struct EnetTransport {
    host: *mut ENetHost,
    peer: *mut ENetPeer,
}

// SAFETY: `host`/`peer` are owned exclusively by this `EnetTransport` and are
// only ever dereferenced from whichever single thread currently holds the
// value. The transport is *moved* between threads (e.g. handed to a login
// worker and moved back through an mpsc channel), never shared — there is no
// aliasing and no concurrent access. We deliberately do NOT implement `Sync`
// (no `&EnetTransport` is ever sent across threads), only `Send` for the move.
unsafe impl Send for EnetTransport {}

impl Default for EnetTransport {
    fn default() -> Self {
        Self::new()
    }
}

impl EnetTransport {
    pub fn new() -> Self {
        ensure_init();
        EnetTransport {
            host: ptr::null_mut(),
            peer: ptr::null_mut(),
        }
    }

    fn teardown(&mut self) {
        unsafe {
            if !self.peer.is_null() && !self.host.is_null() {
                // Graceful, acknowledged disconnect so the server clears the
                // account's session (LoggedOn) before we exit — otherwise it
                // lingers until the ENet connection timeout (~30 s) and an
                // immediate re-login is rejected with "already online" ('L').
                // `enet_peer_disconnect_now` only sends one unreliable packet, so
                // we use the reliable `enet_peer_disconnect` and pump the host for
                // up to ~1 s waiting for the DISCONNECT acknowledgement.
                enet_peer_disconnect(self.peer, 0);
                let mut ev: ENetEvent = std::mem::zeroed();
                let mut acked = false;
                for _ in 0..20 {
                    let r = enet_host_service(self.host, &mut ev, 50);
                    if r > 0 && ev.type_ == ENET_EVENT_TYPE_DISCONNECT {
                        acked = true;
                        break;
                    }
                    if r < 0 {
                        break;
                    }
                }
                // If the peer never acked (lost packet / server gone), force it
                // so we at least flush a disconnect command out the door.
                if !acked {
                    enet_peer_disconnect_now(self.peer, 0);
                    enet_host_flush(self.host);
                }
            } else if !self.peer.is_null() {
                enet_peer_disconnect_now(self.peer, 0);
            }
            if !self.host.is_null() {
                enet_host_flush(self.host);
                enet_host_destroy(self.host);
            }
        }
        self.host = ptr::null_mut();
        self.peer = ptr::null_mut();
    }

    fn send_raw(&mut self, msg_type: u8, payload: &[u8], reliable: bool) {
        if self.peer.is_null() {
            return;
        }
        let mut buf = Vec::with_capacity(payload.len() + 1);
        buf.push(msg_type);
        buf.extend_from_slice(payload);
        unsafe {
            let flags = if reliable { ENET_PACKET_FLAG_RELIABLE } else { 0 };
            let packet = enet_packet_create(buf.as_ptr() as *const c_void, buf.len(), flags);
            if packet.is_null() {
                return;
            }
            let channel: u8 = if reliable { 1 } else { 2 };
            // enet_peer_send takes ownership on success; on failure we still own it.
            if enet_peer_send(self.peer, channel, packet) < 0 {
                enet_packet_destroy(packet);
            }
        }
    }
}

impl Drop for EnetTransport {
    fn drop(&mut self) {
        self.teardown();
    }
}

impl Transport for EnetTransport {
    fn connect(&mut self, host: &str, port: u16) -> Result<i32, TransportError> {
        // The login flow connects twice (menu → game); start each fresh.
        self.teardown();

        let chost = CString::new(host).map_err(|e| TransportError::Backend(e.to_string()))?;
        let mut addr = ENetAddress { host: 0, port };
        unsafe {
            if enet_address_set_host(&mut addr, chost.as_ptr()) != 0 {
                return Err(TransportError::Backend(format!("cannot resolve {host}")));
            }
            addr.port = port;

            let h = enet_host_create(ptr::null(), 1, 0, 0);
            if h.is_null() {
                return Err(TransportError::ConnectFailed(-1));
            }
            let p = enet_host_connect(h, &addr, 254);
            if p.is_null() {
                enet_host_destroy(h);
                return Err(TransportError::ConnectFailed(-4));
            }

            // Service until the CONNECT event (up to ~5s), as RCE_Connect does.
            let mut connected = false;
            let start = Instant::now();
            while start.elapsed() < Duration::from_secs(5) {
                let mut ev: ENetEvent = std::mem::zeroed();
                let r = enet_host_service(h, &mut ev, 100);
                if r < 0 {
                    break;
                }
                if r > 0 {
                    match ev.type_ {
                        ENET_EVENT_TYPE_CONNECT => {
                            connected = true;
                            break;
                        }
                        ENET_EVENT_TYPE_RECEIVE => {
                            if !ev.packet.is_null() {
                                enet_packet_destroy(ev.packet);
                            }
                        }
                        _ => {}
                    }
                }
            }
            if !connected {
                enet_host_destroy(h);
                return Err(TransportError::ConnectFailed(-2));
            }
            self.host = h;
            self.peer = p;
        }

        // Announce ourselves: type-0 NewClient, empty, reliable (RCE_Connect).
        self.send_raw(0, &[], true);
        unsafe {
            enet_host_flush(self.host);
        }
        Ok(1)
    }

    fn send(&mut self, _dest: i32, msg_type: u8, payload: &[u8], reliable: bool) {
        self.send_raw(msg_type, payload, reliable);
    }

    fn poll(&mut self) -> Vec<RecvMessage> {
        let mut out = Vec::new();
        if self.host.is_null() {
            return out;
        }
        unsafe {
            loop {
                let mut ev: ENetEvent = std::mem::zeroed();
                let r = enet_host_service(self.host, &mut ev, 0);
                if r <= 0 {
                    break;
                }
                match ev.type_ {
                    ENET_EVENT_TYPE_RECEIVE => {
                        if !ev.packet.is_null() {
                            let pkt = &*ev.packet;
                            if !pkt.data.is_null() && pkt.data_length >= 1 {
                                let bytes = std::slice::from_raw_parts(pkt.data, pkt.data_length);
                                out.push(RecvMessage {
                                    msg_type: bytes[0],
                                    connection: 0,
                                    data: bytes[1..].to_vec(),
                                });
                            }
                            enet_packet_destroy(ev.packet);
                        }
                    }
                    ENET_EVENT_TYPE_DISCONNECT => {
                        self.peer = ptr::null_mut();
                    }
                    _ => {}
                }
            }
        }
        out
    }

    fn disconnect(&mut self, _dest: i32) {
        self.teardown();
    }
}
