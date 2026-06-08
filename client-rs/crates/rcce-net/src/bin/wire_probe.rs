//! Wire-level diagnostic: wraps the UDP socket so every datagram sent/received
//! during the ENet handshake is hex-dumped. Splits the interop blocker:
//!
//! * TX bytes but ZERO RX  → server never accepted our connect (it didn't
//!   recognize our handshake, or never received it).
//! * TX and RX bytes, but rusty_enet yields no Connect → it received a reply
//!   but couldn't parse it → protocol-layout divergence (compare the bytes to
//!   stock ENet 1.3.17 `protocol.h`).
//!
//!     cargo run -p rcce-net --bin wire-probe -- 127.0.0.1:25000 6

use std::io::{self, ErrorKind};
use std::net::{SocketAddr, UdpSocket};
use std::time::{Duration, Instant};

use rusty_enet::{Event, Host, HostSettings, PacketReceived, Socket, SocketOptions, MTU_MAX};

use rcce_net::CONNECT_CHANNELS;

/// A `Socket` that delegates to a `UdpSocket` and hex-dumps all traffic.
struct LoggingSocket {
    inner: UdpSocket,
    tx: usize,
    rx: usize,
}

fn hexdump(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect::<Vec<_>>().join(" ")
}

impl Socket for LoggingSocket {
    type Address = SocketAddr;
    type Error = io::Error;

    fn init(&mut self, _opts: SocketOptions) -> Result<(), io::Error> {
        self.inner.set_nonblocking(true)?;
        self.inner.set_broadcast(true)?;
        Ok(())
    }

    fn send(&mut self, address: SocketAddr, buffer: &[u8]) -> Result<usize, io::Error> {
        self.tx += 1;
        println!("[wire] TX #{:>3} -> {address}  {}B  [{}]", self.tx, buffer.len(), hexdump(buffer));
        match self.inner.send_to(buffer, address) {
            Ok(n) => Ok(n),
            Err(e) if e.kind() == ErrorKind::WouldBlock => Ok(0),
            Err(e) => Err(e),
        }
    }

    fn receive(
        &mut self,
        buffer: &mut [u8; MTU_MAX],
    ) -> Result<Option<(SocketAddr, PacketReceived)>, io::Error> {
        match self.inner.recv_from(buffer) {
            Ok((n, addr)) => {
                self.rx += 1;
                println!("[wire] RX #{:>3} <- {addr}  {n}B  [{}]", self.rx, hexdump(&buffer[..n]));
                Ok(Some((addr, PacketReceived::Complete(n))))
            }
            Err(e) if e.kind() == ErrorKind::WouldBlock => Ok(None),
            Err(e) => Err(e),
        }
    }
}

fn main() {
    let addr: SocketAddr = std::env::args()
        .nth(1)
        .unwrap_or_else(|| "127.0.0.1:25000".to_string())
        .parse()
        .expect("usage: wire-probe <ip:port> [secs]");
    let run_secs: u64 = std::env::args().nth(2).and_then(|s| s.parse().ok()).unwrap_or(6);

    let bind = if addr.ip().is_loopback() { "127.0.0.1:0" } else { "0.0.0.0:0" };
    let inner = UdpSocket::bind(bind).expect("bind");
    println!("[wire] local {} -> target {addr}, {run_secs}s", inner.local_addr().unwrap());

    let mut host = Host::new(
        LoggingSocket { inner, tx: 0, rx: 0 },
        HostSettings { peer_limit: 1, ..Default::default() },
    )
    .expect("host");
    host.connect(addr, CONNECT_CHANNELS, 0).expect("connect");

    let mut connected = false;
    let deadline = Instant::now() + Duration::from_secs(run_secs);
    'outer: while Instant::now() < deadline {
        loop {
            match host.service() {
                Ok(Some(Event::Connect { .. })) => {
                    connected = true;
                    println!("[wire] ✓ rusty_enet reports CONNECT");
                }
                Ok(Some(_)) => {}
                Ok(None) => break,
                Err(e) => {
                    println!("[wire] ! service error: {e}");
                    break 'outer;
                }
            }
        }
        std::thread::sleep(Duration::from_millis(10));
    }

    println!("\n[wire] connected={connected}");
    println!("[wire] Interpretation: if TX>0 and RX=0, the server didn't accept/receive our connect.");
}
