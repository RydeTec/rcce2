from __future__ import annotations

from pathlib import Path
import shutil
import subprocess
import unittest


class WindowsCaptureScriptTest(unittest.TestCase):
    def test_native_powershell_self_test(self) -> None:
        powershell = shutil.which("powershell.exe")
        if powershell is None:
            self.skipTest("native Windows PowerShell is unavailable")
        script = Path(__file__).resolve().parents[1] / "capture_windows_performance_reference.ps1"
        windows_script = subprocess.run(
            ["wslpath", "-w", str(script)], check=True, capture_output=True, text=True
        ).stdout.strip()
        completed = subprocess.run(
            [
                powershell,
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                windows_script,
                "-SelfTest",
            ],
            capture_output=True,
            text=True,
        )
        self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)
        self.assertIn("Windows performance reference capture self-test: passed", completed.stdout)


if __name__ == "__main__":
    unittest.main()
