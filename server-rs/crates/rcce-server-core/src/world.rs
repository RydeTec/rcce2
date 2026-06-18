//! Live-world primitives shared across the server. Currently the runtime-id
//! allocator; the per-area actor registries build on this as the world
//! simulation lands.

/// Allocates unique 16-bit runtime ids for spawned actors — parity with
/// `AssignRuntimeID` (`Server.bb:951`): a rolling cursor over a 65535-slot table
/// (`RuntimeIDList`, valid ids `0..=65534`), skipping occupied slots and
/// wrapping. The client identifies every in-world actor by this id on the wire
/// (`P_NewActor`, `P_StandardUpdate`, …).
#[derive(Debug)]
pub struct RuntimeIdAllocator {
    occupied: Vec<bool>,
    cursor: u32,
}

/// Highest valid runtime id (`If LastRuntimeID > 65534 Then LastRuntimeID = 0`).
const MAX_RUNTIME_ID: u32 = 65534;

impl Default for RuntimeIdAllocator {
    fn default() -> Self {
        Self::new()
    }
}

impl RuntimeIdAllocator {
    pub fn new() -> Self {
        let mut occupied = vec![false; (MAX_RUNTIME_ID + 1) as usize];
        // Reserve runtime id 0 as a "none" sentinel and so actor handles are
        // always non-zero — RSL scripts gate on `If Actor() > 0`, which would
        // wrongly reject a real actor whose runtime id was 0.
        occupied[0] = true;
        Self { occupied, cursor: 1 }
    }

    /// Allocate the next free runtime id. If the table is full (65535 actors)
    /// it reuses the cursor slot, matching the Blitz fallback (the server logs +
    /// reuses rather than failing).
    pub fn alloc(&mut self) -> u16 {
        let start = self.cursor;
        let mut tried = 0u32;
        while self.occupied[self.cursor as usize] {
            self.advance();
            tried += 1;
            if tried > MAX_RUNTIME_ID + 1 {
                self.cursor = start; // full — reuse
                break;
            }
        }
        let id = self.cursor as u16;
        self.occupied[id as usize] = true;
        self.advance();
        id
    }

    /// Release a runtime id when its actor leaves the world.
    pub fn free(&mut self, id: u16) {
        if let Some(slot) = self.occupied.get_mut(id as usize) {
            *slot = false;
        }
    }

    /// Whether `id` is currently allocated.
    pub fn is_allocated(&self, id: u16) -> bool {
        self.occupied.get(id as usize).copied().unwrap_or(false)
    }

    fn advance(&mut self) {
        self.cursor += 1;
        if self.cursor > MAX_RUNTIME_ID {
            self.cursor = 0;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn allocates_sequentially_from_one() {
        // Id 0 is reserved (the "none" sentinel), so allocation starts at 1.
        let mut a = RuntimeIdAllocator::new();
        assert!(a.is_allocated(0));
        assert_eq!(a.alloc(), 1);
        assert_eq!(a.alloc(), 2);
        assert_eq!(a.alloc(), 3);
        assert!(a.is_allocated(1) && a.is_allocated(2) && a.is_allocated(3));
    }

    #[test]
    fn freed_ids_are_released() {
        let mut a = RuntimeIdAllocator::new();
        let id1 = a.alloc(); // 1
        let id2 = a.alloc(); // 2
        a.free(id1);
        a.free(id2);
        assert!(!a.is_allocated(id1));
        assert!(!a.is_allocated(id2));
        // 0 stays reserved.
        assert!(a.is_allocated(0));
    }

    #[test]
    fn freed_slot_is_reused_on_wrap() {
        let mut a = RuntimeIdAllocator::new();
        for _ in 0..5 {
            a.alloc();
        }
        a.free(2);
        // Force the cursor around the whole table; the only free slot is 2.
        let mut got = None;
        for _ in 0..(MAX_RUNTIME_ID as usize + 2) {
            // free everything we get except track when 2 comes back
            let id = a.alloc();
            if id == 2 {
                got = Some(id);
                break;
            }
            a.free(id); // keep the table sparse so the loop terminates
        }
        assert_eq!(got, Some(2));
    }
}
