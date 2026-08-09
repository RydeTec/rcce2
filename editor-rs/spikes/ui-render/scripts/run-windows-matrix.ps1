param(
    [Parameter(Mandatory = $true)][string]$Exe,
    [Parameter(Mandatory = $true)][string]$Trace,
    [Parameter(Mandatory = $true)][string]$Root
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $Root | Out-Null

$os = Get-CimInstance Win32_OperatingSystem
$cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
$gpu = Get-CimInstance Win32_VideoController | Select-Object -First 1
$system = Get-CimInstance Win32_ComputerSystem
$powerLines = & "$env:SystemRoot\System32\powercfg.exe" /getactivescheme
$power = ($powerLines | Out-String).Trim()
$dpi = Get-ItemProperty 'HKCU:\Control Panel\Desktop' -Name LogPixels,Win8DpiScaling -ErrorAction SilentlyContinue
$machine = [ordered]@{
    recorded_at_utc = [DateTime]::UtcNow.ToString('o')
    os_caption = $os.Caption
    os_version = $os.Version
    os_build = $os.BuildNumber
    kernel_version = [Environment]::OSVersion.VersionString
    cpu = $cpu.Name
    logical_processors = $cpu.NumberOfLogicalProcessors
    gpu = $gpu.Name
    gpu_driver = $gpu.DriverVersion
    ram_bytes = [uint64]$system.TotalPhysicalMemory
    physical_display = "$($gpu.CurrentHorizontalResolution)x$($gpu.CurrentVerticalResolution) at $($gpu.CurrentRefreshRate) Hz"
    video_mode_description = $gpu.VideoModeDescription
    harness_window = "1024x768 physical pixels"
    windows_log_pixels_registry = $dpi.LogPixels
    windows_win8_dpi_scaling_registry = $dpi.Win8DpiScaling
    winit_native_scale_factor = 1.0
    deterministic_scale_overrides_percent = @(100, 150, 200)
    power_mode = $power
    cache_protocol = "warm process/filesystem cache; cold-cache run not performed"
    trace = $Trace
    executable = $Exe
}
$machine | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $Root 'machine-profile.json')

foreach ($viewport in 1, 2) {
    foreach ($scale in 100, 150, 200) {
        $case = "v${viewport}-dpi${scale}-warm"
        $caseDir = Join-Path $Root $case
        New-Item -ItemType Directory -Force -Path $caseDir | Out-Null
        if (Test-Path (Join-Path $caseDir 'provenance.json')) {
            continue
        }
        $stdout = Join-Path $caseDir 'stdout.txt'
        $stderr = Join-Path $caseDir 'stderr.txt'
        $screenshot = Join-Path $caseDir 'window.png'
        $arguments = @(
            '--trace', $Trace,
            '--viewport-count', "$viewport",
            '--scale-percent', "$scale",
            '--cache-state', 'warm',
            '--output', $caseDir,
            '--screenshot', $screenshot
        )
        $process = Start-Process -FilePath $Exe -ArgumentList $arguments -PassThru `
            -RedirectStandardOutput $stdout -RedirectStandardError $stderr
        $samples = [System.Collections.Generic.List[object]]::new()
        while (-not $process.HasExited) {
            $process.Refresh()
            $samples.Add([pscustomobject]@{
                utc = [DateTime]::UtcNow.ToString('o')
                working_set_bytes = [uint64]$process.WorkingSet64
                peak_working_set_bytes = [uint64]$process.PeakWorkingSet64
                private_memory_bytes = [uint64]$process.PrivateMemorySize64
            })
            Start-Sleep -Milliseconds 100
        }
        $process.WaitForExit()
        $exitCode = $process.ExitCode
        if ($null -eq $exitCode -and (Select-String -Quiet -SimpleMatch '[ui-spike] frames=' $stdout)) {
            $exitCode = 0
        }
        $samples | Export-Csv -NoTypeInformation -Encoding utf8 (Join-Path $caseDir 'memory.csv')
        $steady = $samples | Select-Object -Last 20
        $identity = Get-Content -Raw (Join-Path $caseDir 'environment.json') | ConvertFrom-Json
        $memory = [ordered]@{
            schema = 1
            sample_interval_ms = 100
            sample_count = $samples.Count
            peak_working_set_bytes = ($samples | Measure-Object peak_working_set_bytes -Maximum).Maximum
            steady_working_set_bytes = [uint64](($steady | Measure-Object working_set_bytes -Average).Average)
            process_exit_code = $exitCode
            executable_source_sha256 = $identity.executable_source_sha256
            cargo_lock_sha256 = $identity.cargo_lock_sha256
            trace_sha256 = $identity.trace_sha256
        }
        $memory | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $caseDir 'memory-summary.json')
        if ($exitCode -ne 0) {
            throw "case $case failed with exit code $exitCode"
        }
    }
}
