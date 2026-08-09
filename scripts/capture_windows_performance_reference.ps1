[CmdletBinding()]
param(
    [string]$OutputPath,
    [string]$BenchmarkStoragePath,
    [string]$GpuBackend,
    [string]$GpuAdapterName,
    [string]$GpuAdapterDriverVersion,
    [string]$CapturedAtUtc,
    [string]$RustToolchain = "1.85.0",
    [switch]$SelfTest
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$CaptureCommands = @(
    "Get-CimInstance Win32_OperatingSystem",
    "Get-CimInstance Win32_Processor | Select-Object -First 1",
    "Get-CimInstance Win32_ComputerSystem",
    "Get-CimInstance Win32_VideoController | match exact harness adapter name and driver version; require one result",
    "Get-CimInstance Win32_Battery",
    "Get-ItemProperty HKLM:\SYSTEM\CurrentControlSet\Enum\DISPLAY\*\*\Device Parameters -Name EDID",
    "Get-ItemProperty HKCU:\Control Panel\Desktop\WindowMetrics -Name AppliedDPI",
    "Get-Volume -DriveLetter <benchmark-drive>",
    "Get-Partition -DriveLetter <benchmark-drive>",
    "Get-Disk -Number <benchmark-disk-number>",
    "powercfg /getactivescheme",
    "rustup run <toolchain> rustc -Vv"
)

function Assert-HarnessGpuBackend([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -match "^(auto|detect|detected|guess|unknown|unavailable)$") {
        throw "GpuBackend must be the explicit backend reported by the benchmark harness; it is never guessed by this capture."
    }
}

function Assert-UtcTimestamp([string]$Value) {
    $parsed = [DateTimeOffset]::MinValue
    $styles = [Globalization.DateTimeStyles]::AssumeUniversal -bor [Globalization.DateTimeStyles]::AdjustToUniversal
    $valid = [DateTimeOffset]::TryParseExact(
        $Value, "yyyy-MM-dd'T'HH:mm:ss'Z'", [Globalization.CultureInfo]::InvariantCulture,
        $styles, [ref]$parsed
    )
    if (-not $valid -or $parsed.ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss'Z'") -ne $Value) {
        throw "CapturedAtUtc must be a real canonical UTC timestamp such as 2026-07-21T12:00:00Z."
    }
}

function Select-HarnessGpu($Adapters, [string]$Name, [string]$DriverVersion) {
    if ([string]::IsNullOrWhiteSpace($Name) -or [string]::IsNullOrWhiteSpace($DriverVersion)) {
        throw "GpuAdapterName and GpuAdapterDriverVersion must come from the measurement harness."
    }
    $matches = @($Adapters | Where-Object { ([string]$_.Name) -eq $Name -and ([string]$_.DriverVersion) -eq $DriverVersion })
    if ($matches.Count -ne 1) {
        throw "Harness adapter identity must match exactly one Win32_VideoController; observed $($matches.Count) matches."
    }
    return $matches[0]
}

function Resolve-Rustup {
    $command = Get-Command rustup.exe -ErrorAction SilentlyContinue
    if ($null -ne $command) { return $command.Source }
    $candidate = Join-Path ([Environment]::GetFolderPath("UserProfile")) ".cargo\bin\rustup.exe"
    if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    throw "rustup.exe was not found on PATH or in the current user's standard .cargo\\bin directory."
}

function Invoke-NativeCapture([string]$Executable, [string]$Arguments, [string]$WorkingDirectory) {
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $Executable
    $start.Arguments = $Arguments
    $start.WorkingDirectory = $WorkingDirectory
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $start
    if (-not $process.Start()) { throw "Failed to start native capture command." }
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    return [ordered]@{ exit_code = $process.ExitCode; stdout = $stdout; stderr = $stderr }
}

function Get-DisplayEdidSummary {
    $records = @()
    try {
        $records = @(Get-ItemProperty -Path "Registry::HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Enum\DISPLAY\*\*\Device Parameters" -Name EDID -ErrorAction Stop)
    } catch {
        return [ordered]@{
            available = $false
            record_count = 0
            physical_size = "unavailable (EDID not accessible)"
            limitation = "Display EDID was not accessible; no registry contents were retained."
        }
    }
    $validSizes = @()
    foreach ($record in $records) {
        [byte[]]$edid = $record.EDID
        if ($null -ne $edid -and $edid.Length -ge 23 -and $edid[21] -gt 0 -and $edid[22] -gt 0) {
            $validSizes += ("{0} cm x {1} cm (EDID base block)" -f $edid[21], $edid[22])
        }
    }
    $physical = if ($validSizes.Count -eq 1) { $validSizes[0] } elseif ($validSizes.Count -gt 1) { $validSizes -join "; " } else { "unavailable (EDID has no usable physical-size bytes)" }
    return [ordered]@{
        available = ($records.Count -gt 0)
        record_count = $records.Count
        physical_size = $physical
        limitation = "Only EDID availability, record count, and physical-size bytes were retained; identifiers and raw EDID bytes were excluded."
    }
}

function Get-BenchmarkStorageBinding([string]$PathValue) {
    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        throw "BenchmarkStoragePath is required and must already exist."
    }
    $item = Get-Item -LiteralPath $PathValue -Force
    $fullPath = $item.FullName
    $root = [System.IO.Path]::GetPathRoot($fullPath)
    if ($root -notmatch "^([A-Za-z]):\\$") {
        throw "BenchmarkStoragePath must resolve to a local drive-letter path, not UNC or a device namespace."
    }
    $driveLetter = $Matches[1].ToUpperInvariant()
    $volume = Get-Volume -DriveLetter $driveLetter
    if ($volume.FileSystem -ne "NTFS") {
        throw "BenchmarkStoragePath must reside on NTFS; observed '$($volume.FileSystem)'."
    }
    $partition = Get-Partition -DriveLetter $driveLetter
    $disk = Get-Disk -Number $partition.DiskNumber
    $mediaType = "unavailable"
    $mediaTypeSource = "Win32_DiskDrive fallback"
    try {
        $physicalMatches = @(Get-PhysicalDisk -ErrorAction Stop | Where-Object { ([string]$_.DeviceId) -eq ([string]$disk.Number) })
        if ($physicalMatches.Count -eq 1 -and -not [string]::IsNullOrWhiteSpace([string]$physicalMatches[0].MediaType) -and ([string]$physicalMatches[0].MediaType) -ne "Unspecified") {
            $mediaType = [string]$physicalMatches[0].MediaType
            $mediaTypeSource = "Get-PhysicalDisk DeviceId matched to Get-Disk Number"
        }
    } catch {
        # The documented Win32 fallback below remains available on systems
        # where Storage Spaces cmdlets cannot provide a physical association.
    }
    if ($mediaType -eq "unavailable") {
        $win32Disk = Get-CimInstance Win32_DiskDrive -Filter "Index = $($disk.Number)" | Select-Object -First 1
        if ($null -ne $win32Disk -and -not [string]::IsNullOrWhiteSpace([string]$win32Disk.MediaType)) {
            $mediaType = [string]$win32Disk.MediaType
        }
    }
    $pathHasher = [System.Security.Cryptography.SHA256]::Create()
    try {
        $pathBytes = [System.Text.Encoding]::UTF8.GetBytes($fullPath)
        $pathSha256 = ([System.BitConverter]::ToString($pathHasher.ComputeHash($pathBytes))).Replace("-", "").ToLowerInvariant()
    } finally {
        $pathHasher.Dispose()
    }
    return [ordered]@{
        working_path = $fullPath
        path_sha256 = $pathSha256
        drive_letter = $driveLetter
        filesystem = [string]$volume.FileSystem
        partition_number = [int]$partition.PartitionNumber
        disk_number = [int]$disk.Number
        disk_model = [string]$disk.FriendlyName
        disk_media_type = $mediaType
        disk_media_type_source = $mediaTypeSource
        disk_bus_type = [string]$disk.BusType
        disk_size_bytes = [int64]$disk.Size
        binding = "path_sha256=$pathSha256; drive=$($driveLetter):; filesystem=$($volume.FileSystem); disk=$($disk.Number); partition=$($partition.PartitionNumber); model=$($disk.FriendlyName)"
    }
}

function Invoke-SelfTest {
    Assert-HarnessGpuBackend "wgpu-dx12-from-harness"
    Assert-UtcTimestamp "2026-07-21T12:00:00Z"
    try {
        Assert-UtcTimestamp "2026-02-30T12:00:00Z"
        throw "Self-test accepted an impossible calendar timestamp."
    } catch {
        if ($_.Exception.Message -notmatch "real canonical UTC timestamp") { throw }
    }
    $duplicateAdapters = @(
        [pscustomobject]@{ Name = "same"; DriverVersion = "1" },
        [pscustomobject]@{ Name = "same"; DriverVersion = "1" }
    )
    try {
        Select-HarnessGpu $duplicateAdapters "same" "1"
        throw "Self-test accepted an ambiguous harness adapter identity."
    } catch {
        if ($_.Exception.Message -notmatch "exactly one") { throw }
    }
    $forbidden = @("Win32_UserAccount", "Win32_NetworkLoginProfile", "Get-Credential", "Get-ChildItem Env:", "Get-Clipboard")
    $joined = $CaptureCommands -join "`n"
    foreach ($needle in $forbidden) {
        if ($joined.Contains($needle)) {
            throw "Self-test found forbidden secret-bearing capture command: $needle"
        }
    }
    if ($joined -match "(?i)(reboot|restart-computer|clear.*cache|rammap)") {
        throw "Self-test found a reboot or cache-reset action in the capture commands."
    }
    Write-Output "Windows performance reference capture self-test: passed"
}

if ($SelfTest) {
    Invoke-SelfTest
    exit 0
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    throw "OutputPath is required."
}
if (Test-Path -LiteralPath $OutputPath) {
    throw "OutputPath already exists; candidate evidence is never overwritten."
}
Assert-HarnessGpuBackend $GpuBackend
Assert-UtcTimestamp $CapturedAtUtc

$storage = Get-BenchmarkStorageBinding $BenchmarkStoragePath
$os = Get-CimInstance Win32_OperatingSystem
$cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
$computer = Get-CimInstance Win32_ComputerSystem
$gpu = Select-HarnessGpu @(Get-CimInstance Win32_VideoController) $GpuAdapterName $GpuAdapterDriverVersion
$batteries = @(Get-CimInstance Win32_Battery -ErrorAction SilentlyContinue)
$edid = Get-DisplayEdidSummary
$dpi = Get-ItemProperty -Path "Registry::HKEY_CURRENT_USER\Control Panel\Desktop\WindowMetrics" -Name AppliedDPI -ErrorAction Stop
$powerPlan = (& powercfg /getactivescheme | Out-String).Trim()
$rustup = Resolve-Rustup
if ($RustToolchain -notmatch "^[A-Za-z0-9._-]+$") {
    throw "RustToolchain contains unsupported characters."
}
$rustResult = Invoke-NativeCapture $rustup ("run {0} rustc -Vv" -f $RustToolchain) $storage.working_path
if ($rustResult.exit_code -ne 0) {
    throw "rustup run $RustToolchain rustc -Vv failed with exit $($rustResult.exit_code): $($rustResult.stderr)"
}
$rustLines = @($rustResult.stdout -split "`r?`n")
$rustRelease = ($rustLines | Where-Object { $_ -match "^release:" } | Select-Object -First 1) -replace "^release:\s*", ""
$rustHost = ($rustLines | Where-Object { $_ -match "^host:" } | Select-Object -First 1) -replace "^host:\s*", ""
$rustLlvm = ($rustLines | Where-Object { $_ -match "^LLVM version:" } | Select-Object -First 1) -replace "^LLVM version:\s*", ""
$powerSource = if ($batteries.Count -eq 0) { "AC desktop workstation (no Win32_Battery present)" } else { "battery-capable workstation; AC state not asserted by this capture" }
$architecture = if ([Environment]::Is64BitOperatingSystem) { "x86_64" } else { "x86" }
$storageEvidence = [ordered]@{}
foreach ($key in $storage.Keys) {
    if ($key -ne "working_path") { $storageEvidence[$key] = $storage[$key] }
}

$profile = [ordered]@{
    capture_utc = $CapturedAtUtc
    capture_kind = "read-only-candidate-profile"
    commands = $CaptureCommands
    observed = [ordered]@{
        os_name = [string]$os.Caption
        os_version = [string]$os.Version
        os_build = [string]$os.BuildNumber
        kernel_version = [string]$os.Version
        architecture = $architecture
        cpu_model = ([string]$cpu.Name).Trim()
        physical_cores = [int]$cpu.NumberOfCores
        logical_processors = [int]$cpu.NumberOfLogicalProcessors
        ram_bytes = [int64]$computer.TotalPhysicalMemory
        gpu_model = ([string]$gpu.Name).Trim()
        gpu_backend = $GpuBackend
        gpu_driver_version = [string]$gpu.DriverVersion
        storage_model = $storage.disk_model
        storage_media_type = "$($storage.disk_media_type); source=$($storage.disk_media_type_source); bus=$($storage.disk_bus_type)"
        storage_device_bytes = $storage.disk_size_bytes
        filesystem = $storage.filesystem
        display_width_pixels = [int]$gpu.CurrentHorizontalResolution
        display_height_pixels = [int]$gpu.CurrentVerticalResolution
        display_refresh_hz = [int]$gpu.CurrentRefreshRate
        applied_dpi = [int]$dpi.AppliedDPI
        display_physical_size = $edid.physical_size
        power_plan = $powerPlan
        power_source = $powerSource
        rustc = "rustc $rustRelease, host $rustHost, LLVM $rustLlvm"
        storage_path_binding = $storage.binding
    }
    capture_details = [ordered]@{
        gpu_backend_source = "required command-line value copied from the measurement harness; not inferred from GPU or driver"
        display_edid = $edid
        benchmark_storage = $storageEvidence
        cold_cache_control = [ordered]@{
            status = "not-performed"
            reboot_performed = $false
            cache_reset_performed = $false
            reason = "Profile capture is read-only. Any cold-run procedure requires a separately approved protocol."
        }
    }
    limitations = @(
        "Candidate evidence only; this script does not approve the machine, fixtures, protocols, budgets, or aggregate record.",
        "GPU backend is harness-supplied and was not guessed from the adapter, driver, or operating system.",
        $edid.limitation,
        "The benchmark storage path is bound to an observed NTFS volume, partition, disk number, and model; disk serial numbers and volume unique identifiers were not collected.",
        "No reboot, filesystem-cache reset, benchmark harness validation, performance run, or memory sample was performed.",
        "No account objects, environment-variable values, network profiles, credentials, clipboard, file contents, raw EDID data, or raw benchmark path were retained."
    )
}

$parent = Split-Path -Parent ([System.IO.Path]::GetFullPath($OutputPath))
if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
    throw "OutputPath parent directory must already exist."
}
$json = $profile | ConvertTo-Json -Depth 8
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$target = [System.IO.Path]::GetFullPath($OutputPath)
$temporary = Join-Path $parent ("." + [System.IO.Path]::GetFileName($target) + ".stage-" + [Guid]::NewGuid().ToString("N"))
$bytes = $utf8NoBom.GetBytes($json + "`n")
$stream = $null
try {
    $stream = New-Object System.IO.FileStream($temporary, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Flush($true)
    $stream.Dispose()
    $stream = $null
    [System.IO.File]::Move($temporary, $target)
} catch {
    if ($null -ne $stream) { $stream.Dispose() }
    if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    throw
}
Write-Output "Windows performance reference candidate profile captured: $OutputPath"
Write-Output "approval status: pending human review"
