param([switch]$Check)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $root
$config = Get-Content -LiteralPath "$PSScriptRoot/settings.json" -Raw | ConvertFrom-Json
if ($config.ngrokUrl -notmatch '^https://[a-zA-Z0-9.-]+$') { throw 'ngrokUrl must be an HTTPS hostname with no path.' }
foreach ($exe in @($config.node, $config.ngrok, $config.hermes)) {
    if (-not (Test-Path -LiteralPath $exe)) { throw "Executable missing: $exe" }
}
$env:HERMES_EXECUTABLE = $config.hermes
$receiverConfig = & $config.node "$PSScriptRoot/preflight.js"
if ($LASTEXITCODE -ne 0) { throw 'Invalid receiver configuration; loopback HOST and nonzero PORT required.' }
$port = ($receiverConfig | ConvertFrom-Json).port
if ($Check) { Write-Output "Preflight passed. Receiver port: $port"; exit 0 }

# Closing the supervisor (including a crash or Task Scheduler stop) kills only
# its owned child processes and their descendants. No orphan worker survives.
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class ForwarderJob {
 [DllImport("kernel32.dll", CharSet=CharSet.Unicode)] public static extern IntPtr CreateJobObject(IntPtr a, string n);
 [DllImport("kernel32.dll")] public static extern bool SetInformationJobObject(IntPtr h, int c, IntPtr p, uint s);
 [DllImport("kernel32.dll", SetLastError=true)] public static extern bool AssignProcessToJobObject(IntPtr h, IntPtr p);
 [DllImport("kernel32.dll")] public static extern bool CloseHandle(IntPtr h);
 [StructLayout(LayoutKind.Sequential)] public struct Basic { public long a,b; public uint flags; public UIntPtr c,d; public uint e; public UIntPtr f; public uint g,h; }
 [StructLayout(LayoutKind.Sequential)] public struct IO { public ulong a,b,c,d,e,f; }
 [StructLayout(LayoutKind.Sequential)] public struct Extended { public Basic basic; public IO io; public UIntPtr a,b,c,d; }
 public static IntPtr Create() {
  var h=CreateJobObject(IntPtr.Zero,null); var v=new Extended(); v.basic.flags=0x2000;
  int size=Marshal.SizeOf(v); var p=Marshal.AllocHGlobal(size);
  try { Marshal.StructureToPtr(v,p,false); if(h==IntPtr.Zero || !SetInformationJobObject(h,9,p,(uint)size)) throw new Exception("Cannot create process job"); }
  finally { Marshal.FreeHGlobal(p); } return h;
 }
}
'@
$mutex = New-Object Threading.Mutex($false, 'Local\NotificationForwarderLaptop')
$owned = $false
$job = [IntPtr]::Zero
$services = @()
$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
function Write-Status([string]$message) {
    $file = Join-Path $logDir 'supervisor.log'
    if ((Test-Path $file) -and (Get-Item $file).Length -gt 1MB) { Move-Item -LiteralPath $file -Destination "$file.1" -Force }
    Add-Content -LiteralPath $file -Value "$(Get-Date -Format o) $message"
}
try {
    try { $owned = $mutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $owned = $true }
    if (-not $owned) { Write-Output 'Already running.'; exit 0 }
    # Refuse to add a worker alongside an already-running manual receiver.
    $probe = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $port)
    $probe.Server.ExclusiveAddressUse = $true
    try { $probe.Start() } finally { $probe.Stop() }
    $job = [ForwarderJob]::Create()
    $services = @(
        @{name='receiver'; exe=$config.node; arguments=('"{0}"' -f (Join-Path $root 'server.js')); process=$null; next=[DateTime]::MinValue},
        @{name='ngrok'; exe=$config.ngrok; arguments="http http://127.0.0.1:$port --url=$($config.ngrokUrl)"; process=$null; next=[DateTime]::MinValue},
        @{name='worker'; exe=$config.node; arguments=('"{0}" --watch' -f (Join-Path $root 'hermes-worker.js')); process=$null; next=[DateTime]::MinValue}
    )
    $healthFailures = 0
    Write-Status "supervisor started pid=$PID"
    while ($true) {
        foreach ($service in $services) {
            if ($service.process -and $service.process.HasExited) {
                Write-Status "$($service.name) exited code=$($service.process.ExitCode); retry in 15 seconds"
                $service.process.Dispose(); $service.process = $null
                $service.next = (Get-Date).AddSeconds(15)
            }
            if (-not $service.process -and (Get-Date) -ge $service.next) {
                # Drain and discard child output: lifecycle logs never contain notification content.
                $info = New-Object Diagnostics.ProcessStartInfo
                $info.FileName = $service.exe; $info.Arguments = $service.arguments
                $info.WorkingDirectory = $root; $info.UseShellExecute = $false
                $info.CreateNoWindow = $true; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
                $child = New-Object Diagnostics.Process
                $child.StartInfo = $info
                try {
                    [void]$child.Start()
                    if (-not [ForwarderJob]::AssignProcessToJobObject($job, $child.Handle)) { $code = [Runtime.InteropServices.Marshal]::GetLastWin32Error(); $child.Kill(); throw "Cannot own child process: $code" }
                    $child.BeginOutputReadLine(); $child.BeginErrorReadLine()
                    $service.process = $child
                    Write-Status "$($service.name) started pid=$($child.Id)"
                } catch {
                    $child.Dispose(); $service.next = (Get-Date).AddSeconds(15)
                    Write-Status "$($service.name) launch failed ($($_.Exception.GetType().Name)); retry in 15 seconds"
                }
            }
        }
        try {
            $health = Invoke-RestMethod "http://127.0.0.1:$port/health" -TimeoutSec 3
            if ($health.ok -ne $true) { throw 'Unhealthy' }
            $healthFailures = 0
        } catch { $healthFailures++ }
        if ($healthFailures -ge 3 -and $services[0].process -and -not $services[0].process.HasExited) {
            Write-Status 'receiver health failed three times; restarting receiver'
            $services[0].process.Kill(); $healthFailures = 0
        }
        Start-Sleep -Seconds 5
    }
} finally {
    if ($job -ne [IntPtr]::Zero) { [void][ForwarderJob]::CloseHandle($job) }
    if ($owned) { $mutex.ReleaseMutex() }
    $mutex.Dispose()
}
