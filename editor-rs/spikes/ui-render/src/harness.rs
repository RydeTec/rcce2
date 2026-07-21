use serde::{Deserialize, Serialize};
use std::{
    fs, io,
    path::Path,
    time::{Duration, Instant},
};

#[derive(Clone, Debug, Deserialize, Eq, PartialEq, Serialize)]
pub struct TraceConfig {
    pub schema: u32,
    pub seed: u64,
    pub catalog_rows: usize,
    pub viewport_counts: Vec<u8>,
    pub dpi_percent: Vec<u16>,
    pub warmup_frames: u64,
    pub measured_frames: u64,
}

impl TraceConfig {
    #[must_use]
    pub fn standard() -> Self {
        Self {
            schema: 1,
            seed: 1_380_143_941,
            catalog_rows: 100_000,
            viewport_counts: vec![1, 2],
            dpi_percent: vec![100, 150, 200],
            warmup_frames: 300,
            measured_frames: 1_800,
        }
    }

    /// Loads the checked-in trace and rejects malformed or incomplete schedules.
    ///
    /// # Errors
    ///
    /// Returns an I/O or JSON error when the trace cannot be loaded, and `InvalidData` when its
    /// schema or required matrix differs from the decision protocol.
    pub fn load_checked(path: &Path) -> io::Result<Self> {
        let bytes = fs::read(path)?;
        let trace: Self = serde_json::from_slice(&bytes).map_err(io::Error::other)?;
        if trace.schema != 1
            || trace.seed != 1_380_143_941
            || trace.catalog_rows != 100_000
            || trace.viewport_counts != [1, 2]
            || trace.dpi_percent != [100, 150, 200]
            || trace.warmup_frames == 0
            || trace.measured_frames == 0
        {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "trace does not match standard-v1 decision schedule",
            ));
        }
        Ok(trace)
    }
}

#[must_use]
pub fn percentile(samples: &[f64], quantile: f64) -> Option<f64> {
    if samples.is_empty() {
        return None;
    }
    let mut sorted = samples.to_vec();
    sorted.sort_by(f64::total_cmp);
    let q = quantile.clamp(0.0, 1.0);
    let index = ((sorted.len() - 1) as f64 * q).ceil() as usize;
    sorted.get(index).copied()
}

#[must_use]
pub fn physical_extent(points: [f32; 2], pixels_per_point: f32) -> [u32; 2] {
    [
        (points[0] * pixels_per_point).round().max(1.0) as u32,
        (points[1] * pixels_per_point).round().max(1.0) as u32,
    ]
}

#[derive(Clone, Debug, Serialize)]
pub struct FrameSample {
    pub frame: u64,
    pub generation: u64,
    pub viewport_count: u8,
    pub phase: &'static str,
    pub catalog_rows_built: usize,
    pub frame_ms: f64,
    pub cpu_work_ms: f64,
    pub pick_ms: Option<f64>,
}

#[derive(Debug)]
pub struct Recorder {
    start: Instant,
    last_frame: Instant,
    samples: Vec<FrameSample>,
}

impl Default for Recorder {
    fn default() -> Self {
        let now = Instant::now();
        Self {
            start: now,
            last_frame: now,
            samples: Vec::new(),
        }
    }
}

impl Recorder {
    pub fn record(
        &mut self,
        frame: u64,
        generation: u64,
        viewport_count: u8,
        warmup: bool,
        catalog_rows_built: usize,
        work: Duration,
        pick: Option<Duration>,
    ) {
        let now = Instant::now();
        self.samples.push(FrameSample {
            frame,
            generation,
            viewport_count,
            phase: if warmup { "warmup" } else { "measured" },
            catalog_rows_built,
            frame_ms: now.duration_since(self.last_frame).as_secs_f64() * 1_000.0,
            cpu_work_ms: work.as_secs_f64() * 1_000.0,
            pick_ms: pick.map(|value| value.as_secs_f64() * 1_000.0),
        });
        self.last_frame = now;
    }

    #[must_use]
    pub fn len(&self) -> usize {
        self.samples.len()
    }

    #[must_use]
    pub fn is_empty(&self) -> bool {
        self.samples.is_empty()
    }

    /// Writes the raw trace configuration, frame samples, and aggregate summary.
    ///
    /// # Errors
    ///
    /// Returns an I/O error when the output directory or any evidence file cannot be written.
    pub fn write(&self, output: &Path, trace: &TraceConfig) -> io::Result<()> {
        fs::create_dir_all(output)?;
        fs::write(
            output.join("trace-config.json"),
            serde_json::to_vec_pretty(trace).map_err(io::Error::other)?,
        )?;
        fs::write(
            output.join("frames.json"),
            serde_json::to_vec_pretty(&self.samples).map_err(io::Error::other)?,
        )?;

        let measured: Vec<&FrameSample> = self
            .samples
            .iter()
            .filter(|sample| sample.phase == "measured")
            .collect();
        let frame_ms: Vec<f64> = measured.iter().map(|sample| sample.frame_ms).collect();
        let work_ms: Vec<f64> = self
            .samples
            .iter()
            .filter(|sample| sample.phase == "measured")
            .map(|sample| sample.cpu_work_ms)
            .collect();
        let summary = serde_json::json!({
            "schema": 1,
            "sample_count": self.samples.len(),
            "measured_sample_count": measured.len(),
            "elapsed_seconds": self.start.elapsed().as_secs_f64(),
            "frame_ms_p50": percentile(&frame_ms, 0.50),
            "frame_ms_p95": percentile(&frame_ms, 0.95),
            "cpu_work_ms_p50": percentile(&work_ms, 0.50),
            "cpu_work_ms_p95": percentile(&work_ms, 0.95),
        });
        fs::write(
            output.join("summary.json"),
            serde_json::to_vec_pretty(&summary).map_err(io::Error::other)?,
        )
    }
}
