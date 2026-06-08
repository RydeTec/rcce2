//! `FfiTransport` — the [`Transport`] backend that wraps the shipped 32-bit
//! `RCEnet.dll` (a custom ENet fork). MUST be built for `i686-pc-windows-msvc`.
//!
//! ABI is **cdecl** (`BBDECL = extern "C" __declspec(dllexport)`, no `/Gz`,
//! undecorated exports). Receive iteration mirrors the DLL's stateful queue
//! (`RCEnet/main.cpp`): `RCE_Update` pumps; `RCE_MoveToFirstMessage` points at
//! the front; the `RCE_GetMessage*` getters read it; `RCE_AreMoreMessage`
//! pops the front and advances (returns 0 when the queue drains). `RCE_FSend`
//! prepends the type byte itself, so we pass payload + payload-length.

use std::ffi::CString;
use std::os::raw::{c_char, c_int};

use libloading::{Library, Symbol};
use rcce_net::{RecvMessage, Transport, TransportError};

type FnConnect = unsafe extern "C" fn(
    *const c_char,
    c_int,
    c_int,
    *const c_char,
    *const c_char,
    *const c_char,
    c_int,
) -> c_int;
type FnFSend = unsafe extern "C" fn(c_int, c_int, *const c_char, c_int, c_int);
type FnVoid = unsafe extern "C" fn();
type FnRetI = unsafe extern "C" fn() -> c_int;
type FnGetData = unsafe extern "C" fn(*mut c_char);
type FnDisc = unsafe extern "C" fn(c_int);

/// Loaded `RCEnet.dll` plus its resolved entry points. Holds the `Library` so
/// the copied function pointers stay valid for the transport's lifetime.
pub struct FfiTransport {
    _lib: Library,
    f_connect: FnConnect,
    f_fsend: FnFSend,
    f_update: FnVoid,
    f_move_first: FnRetI,
    f_are_more: FnRetI,
    f_get_type: FnRetI,
    f_get_conn: FnRetI,
    f_get_data: FnGetData,
    f_msg_len: FnRetI,
    f_disconnect: FnDisc,
}

impl FfiTransport {
    /// Load `RCEnet.dll` from `dll_path` and resolve its exports.
    pub fn load(dll_path: &str) -> Result<Self, TransportError> {
        unsafe {
            let lib = Library::new(dll_path)
                .map_err(|e| TransportError::Backend(format!("load {dll_path}: {e}")))?;
            macro_rules! sym {
                ($t:ty, $name:expr) => {{
                    let s: Symbol<$t> = lib.get($name).map_err(|e| {
                        TransportError::Backend(format!(
                            "symbol {}: {e}",
                            String::from_utf8_lossy($name)
                        ))
                    })?;
                    *s
                }};
            }
            let t = FfiTransport {
                f_connect: sym!(FnConnect, b"RCE_Connect\0"),
                f_fsend: sym!(FnFSend, b"RCE_FSend\0"),
                f_update: sym!(FnVoid, b"RCE_Update\0"),
                f_move_first: sym!(FnRetI, b"RCE_MoveToFirstMessage\0"),
                f_are_more: sym!(FnRetI, b"RCE_AreMoreMessage\0"),
                f_get_type: sym!(FnRetI, b"RCE_GetMessageType\0"),
                f_get_conn: sym!(FnRetI, b"RCE_GetMessageConnection\0"),
                f_get_data: sym!(FnGetData, b"RCE_GetMessageData\0"),
                f_msg_len: sym!(FnRetI, b"RCE_MessageLength\0"),
                f_disconnect: sym!(FnDisc, b"RCE_Disconnect\0"),
                _lib: lib,
            };
            Ok(t)
        }
    }
}

impl Transport for FfiTransport {
    fn connect(&mut self, host: &str, port: u16) -> Result<i32, TransportError> {
        let c_host =
            CString::new(host).map_err(|e| TransportError::Backend(e.to_string()))?;
        let c_name = CString::new("rustclient").unwrap();
        let c_data = CString::new("").unwrap();
        let c_log = CString::new(r"Data\Logs\rust_client_connection.txt").unwrap();
        // RCE_Connect blocks up to 5s for VERIFY_CONNECT and, on success, sends
        // the type-0 NewClient packet itself. Returns the peer handle (>0).
        let ret = unsafe {
            (self.f_connect)(
                c_host.as_ptr(),
                port as c_int,
                0,
                c_name.as_ptr(),
                c_data.as_ptr(),
                c_log.as_ptr(),
                0,
            )
        };
        if ret > 0 {
            Ok(ret)
        } else {
            Err(TransportError::ConnectFailed(ret))
        }
    }

    fn send(&mut self, dest: i32, msg_type: u8, payload: &[u8], reliable: bool) {
        // The DLL prepends the type byte; we pass payload + its length.
        let ptr = if payload.is_empty() {
            std::ptr::NonNull::<u8>::dangling().as_ptr() as *const c_char
        } else {
            payload.as_ptr() as *const c_char
        };
        unsafe {
            (self.f_fsend)(
                dest,
                msg_type as c_int,
                ptr,
                reliable as c_int,
                payload.len() as c_int,
            );
        }
    }

    fn poll(&mut self) -> Vec<RecvMessage> {
        let mut out = Vec::new();
        unsafe {
            (self.f_update)();
            if (self.f_move_first)() == 1 {
                loop {
                    let msg_type = (self.f_get_type)() as u8;
                    let connection = (self.f_get_conn)();
                    let len = (self.f_msg_len)().max(0) as usize;
                    let mut data = vec![0u8; len];
                    if len > 0 {
                        (self.f_get_data)(data.as_mut_ptr() as *mut c_char);
                    }
                    out.push(RecvMessage {
                        msg_type,
                        connection,
                        data,
                    });
                    // Pops the just-read message and advances; 0 = queue empty.
                    if (self.f_are_more)() == 0 {
                        break;
                    }
                }
            }
        }
        out
    }

    fn disconnect(&mut self, dest: i32) {
        unsafe { (self.f_disconnect)(dest) }
    }
}
