#[derive(Clone, Debug, PartialEq)]
pub struct HarnessSnapshot {
    pub selected_row: usize,
    pub camera_yaw: [f32; 2],
    pub reduced_motion: bool,
}

impl Default for HarnessSnapshot {
    fn default() -> Self {
        Self {
            selected_row: 0,
            camera_yaw: [0.0; 2],
            reduced_motion: false,
        }
    }
}

#[derive(Clone, Copy, Debug)]
pub enum InputAction {
    SelectNext,
    SelectPrevious,
    OrbitLeft(usize),
    OrbitRight(usize),
    SetReducedMotion(bool),
}

impl HarnessSnapshot {
    pub fn apply(&mut self, action: InputAction) {
        match action {
            InputAction::SelectNext => self.selected_row = (self.selected_row + 1).min(99_999),
            InputAction::SelectPrevious => {
                self.selected_row = self.selected_row.saturating_sub(1);
            }
            InputAction::OrbitLeft(viewport) => self.camera_yaw[viewport] -= 0.25,
            InputAction::OrbitRight(viewport) => self.camera_yaw[viewport] += 0.25,
            InputAction::SetReducedMotion(value) => self.reduced_motion = value,
        }
    }
}

#[must_use]
pub fn camera_angle(frame: u64, viewport: usize, reduced_motion: bool, yaw_offset: f32) -> f32 {
    let animation = if reduced_motion {
        0.0
    } else {
        frame as f32 * 0.006
    };
    animation + viewport as f32 * 1.4 + yaw_offset
}
