from __future__ import annotations

from pathlib import Path
import json
import shutil
import subprocess
import tempfile
import unittest


class WindowsCaptureScriptTest(unittest.TestCase):
    def setUp(self) -> None:
        self.powershell = shutil.which("powershell.exe")
        if self.powershell is None:
            self.skipTest("native Windows PowerShell is unavailable")
        self.script = Path(__file__).resolve().parents[1] / "capture_windows_performance_reference.ps1"
        self.windows_script = subprocess.run(
            ["wslpath", "-w", str(self.script)], check=True, capture_output=True, text=True
        ).stdout.strip()

    def test_native_powershell_self_test(self) -> None:
        completed = subprocess.run(
            [
                self.powershell,
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                self.windows_script,
                "-SelfTest",
            ],
            capture_output=True,
            text=True,
        )
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        self.assertIn("Windows performance reference capture self-test: passed", completed.stdout)

    def test_full_native_capture_binds_exact_adapter_and_preserves_existing_output(self) -> None:
        adapter_json = subprocess.run(
            [
                self.powershell,
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "Get-CimInstance Win32_VideoController | Select-Object -First 1 Name,DriverVersion | ConvertTo-Json -Compress",
            ],
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        adapter = json.loads(adapter_json)
        repo = Path("/mnt/c/Users/dyanr/Desktop/rcce2")
        if not repo.is_dir():
            self.skipTest("current Windows workstation repository path is unavailable")
        windows_repo = subprocess.run(
            ["wslpath", "-w", str(repo)], check=True, capture_output=True, text=True
        ).stdout.strip()
        temp_parent = Path("/mnt/c/Users/dyanr/AppData/Local/Temp")
        with tempfile.TemporaryDirectory(prefix="rcce-profile-test-", dir=temp_parent) as directory:
            output = Path(directory) / "profile.json"
            windows_output = subprocess.run(
                ["wslpath", "-w", str(output)], check=True, capture_output=True, text=True
            ).stdout.strip()
            command = [
                self.powershell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", self.windows_script,
                "-OutputPath", windows_output,
                "-BenchmarkStoragePath", windows_repo,
                "-GpuBackend", "test-only-wgpu-dx12",
                "-GpuAdapterName", adapter["Name"],
                "-GpuAdapterDriverVersion", adapter["DriverVersion"],
                "-CapturedAtUtc", "2026-07-21T12:00:00Z",
            ]
            completed = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
            raw = output.read_bytes()
            self.assertFalse(raw.startswith(b"\xef\xbb\xbf"))
            profile = json.loads(raw)
            self.assertEqual(profile["observed"]["gpu_model"], adapter["Name"])
            self.assertEqual(profile["observed"]["gpu_driver_version"], adapter["DriverVersion"])
            self.assertEqual(profile["observed"]["filesystem"], "NTFS")
            self.assertFalse(profile["capture_details"]["cold_cache_control"]["reboot_performed"])
            storage = profile["capture_details"]["benchmark_storage"]
            self.assertNotIn("full_path", storage)
            self.assertNotIn("working_path", storage)
            self.assertRegex(storage["path_sha256"], r"^[0-9a-f]{64}$")
            self.assertNotIn("C:\\Users\\dyanr", profile["observed"]["storage_path_binding"])
            self.assertNotIn("C:\\Users\\dyanr", raw.decode("utf-8"))
            before = raw
            repeated = subprocess.run(command, capture_output=True, text=True)
            self.assertNotEqual(repeated.returncode, 0)
            self.assertEqual(output.read_bytes(), before)

    def test_impossible_calendar_timestamp_is_rejected(self) -> None:
        completed = subprocess.run(
            [
                self.powershell, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", self.windows_script, "-OutputPath", "unused.json",
                "-BenchmarkStoragePath", "C:\\", "-GpuBackend", "test-only-wgpu-dx12",
                "-GpuAdapterName", "unused", "-GpuAdapterDriverVersion", "unused",
                "-CapturedAtUtc", "2026-02-30T12:00:00Z",
            ],
            capture_output=True,
            text=True,
        )
        self.assertNotEqual(completed.returncode, 0)
        self.assertIn("real canonical UTC timestamp", completed.stdout + completed.stderr)


if __name__ == "__main__":
    unittest.main()
