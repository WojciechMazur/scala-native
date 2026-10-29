param(
    [Parameter(Mandatory)][string]$Binary,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateRange(1, 10000)][int]$Iterations = 500,
    [ValidateRange(1, 600)][int]$TimeoutSeconds = 60,
    [ValidateSet('baseline', 'diagnostic')][string]$Mode = 'baseline'
)

$ErrorActionPreference = 'Stop'
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$binaryDirectory = Join-Path $OutputDirectory 'binary'
New-Item -ItemType Directory -Force $binaryDirectory | Out-Null
Copy-Item $Binary $binaryDirectory
# Preserve the exact executable and linker symbols before scripted deletes its temp directory.
Get-ChildItem (Split-Path $Binary) -Filter '*.pdb' | Copy-Item -Destination $binaryDirectory
Get-FileHash $Binary | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'binary-sha256.json')

$env:GC_INITIAL_HEAP_SIZE = '4M'
$env:GC_MAXIMUM_HEAP_SIZE = '64M'
if ($Mode -eq 'diagnostic') {
    $env:SCALANATIVE_GC_LOG_LEVEL = 'debug'
    $env:SCALANATIVE_GC_SYNC_WARNING_INTERVAL_MS = '2000'
}
Get-ChildItem Env: | Where-Object Name -Match '^(GC_|SCALANATIVE_)' |
    Sort-Object Name | Format-Table -AutoSize | Out-File (Join-Path $OutputDirectory 'gc-environment.txt')

function Save-Snapshot([Diagnostics.Process]$Target, [string]$Directory, [int]$Index) {
    # Capture well before the GC's own 60-second abort and the fixture's limit.
    try {
        $Target.Threads | ForEach-Object {
            $waitReason = $null
            if ($_.ThreadState -eq 'Wait') { $waitReason = [string]$_.WaitReason }
            [pscustomobject]@{
                id = $_.Id; state = [string]$_.ThreadState; waitReason = $waitReason
                cpuMs = $_.TotalProcessorTime.TotalMilliseconds
                startAddress = $_.StartAddress.ToInt64()
            }
        } | ConvertTo-Json -Depth 4 | Set-Content "$Directory/threads-$Index.json"
        Get-CimInstance Win32_OperatingSystem |
            Select-Object FreePhysicalMemory, TotalVisibleMemorySize, FreeVirtualMemory, TotalVirtualMemorySize |
            ConvertTo-Json | Set-Content "$Directory/system-memory-$Index.json"
    } catch {
        $_ | Out-String | Add-Content "$Directory/diagnostic-errors.txt"
    }
    try {
        $dump = Start-Process -FilePath $env:STRESS_PROCDUMP -ArgumentList @(
            '-accepteula', '-ma', "$($Target.Id)", "`"$Directory/hang-$Index.dmp`""
        ) -RedirectStandardOutput "$Directory/procdump-$Index.stdout.txt" -RedirectStandardError "$Directory/procdump-$Index.stderr.txt" -PassThru
        if (-not $dump.WaitForExit(20000)) {
            $dump.Kill()
            Write-Warning 'ProcDump exceeded 20 seconds'
        } elseif ($dump.ExitCode -ne 0) {
            Write-Warning "ProcDump exited with $($dump.ExitCode)"
        }
    } catch {
        $_ | Out-String | Add-Content "$Directory/diagnostic-errors.txt"
    }
}

$failures = 0
$slowRuns = 0
$results = Join-Path $OutputDirectory 'results.jsonl'
$samples = Join-Path $OutputDirectory 'samples.csv'
'iteration,elapsedMs,cpuMs,workingSetBytes,privateBytes,threads,handles' | Set-Content $samples
for ($iteration = 1; $iteration -le $Iterations; $iteration++) {
    $directory = Join-Path $OutputDirectory ('run-{0:d4}' -f $iteration)
    New-Item -ItemType Directory -Force $directory | Out-Null
    $stdout = Join-Path $directory 'stdout.txt'
    $stderr = Join-Path $directory 'stderr.txt'
    if ($Mode -eq 'diagnostic') { $env:GC_STATS_FILE = Join-Path $directory 'gc-stats' }
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $process = Start-Process -FilePath $Binary -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    $timedOut = $false
    $snapshots = 0
    $peakPrivate = 0L
    $peakWorking = 0L
    try {
        while (-not $process.WaitForExit(250)) {
            $process.Refresh()
            if ($process.HasExited) { break }
            $elapsed = $watch.ElapsedMilliseconds
            $peakPrivate = [Math]::Max($peakPrivate, $process.PrivateMemorySize64)
            $peakWorking = [Math]::Max($peakWorking, $process.WorkingSet64)
            "$iteration,$elapsed,$($process.TotalProcessorTime.TotalMilliseconds),$($process.WorkingSet64),$($process.PrivateMemorySize64),$($process.Threads.Count),$($process.HandleCount)" | Add-Content $samples
            if (($elapsed -ge 10000 -and $snapshots -eq 0) -or ($elapsed -ge 20000 -and $snapshots -eq 1)) {
                # Bound dump volume to the first two slow processes per shard.
                if ($snapshots -eq 0) { $slowRuns++ }
                if ($slowRuns -le 2) { Save-Snapshot $process $directory ($snapshots + 1) }
                $snapshots++
            }
            if ($watch.ElapsedMilliseconds -ge ($TimeoutSeconds * 1000) -and -not $process.HasExited) {
                $timedOut = $true
                $process.Kill($true)
                break
            }
        }
        $process.WaitForExit()
        $exitCode = $process.ExitCode
        $text = Get-Content $stdout -Raw
        $passed = -not $timedOut -and $exitCode -eq 0 -and $text -match 'gc-commix-explicit-collect: OK'
        if (-not $passed) { $failures++ }
        $result = [ordered]@{
            iteration = $iteration; mode = $Mode; pid = $process.Id
            elapsedMs = $watch.ElapsedMilliseconds; cpuMs = $process.TotalProcessorTime.TotalMilliseconds
            exitCode = $exitCode; timedOut = $timedOut; passed = $passed
            peakPrivateBytes = $peakPrivate; peakWorkingSetBytes = $peakWorking
            snapshots = $snapshots; output = $text
        }
        $result | ConvertTo-Json -Compress | Add-Content $results
        Write-Host "Run $iteration/$Iterations passed=$passed timeout=$timedOut exit=$exitCode elapsed=$($watch.ElapsedMilliseconds)ms"
        if (-not $passed) {
            Get-Content $stdout, $stderr | Write-Host
        }
    } finally {
        if (-not $process.HasExited) { $process.Kill($true); $process.WaitForExit() }
        $process.Dispose()
    }
    if ($failures -ge 2) { break }
}
$rows = @(Get-Content $results | ForEach-Object { $_ | ConvertFrom-Json })
$summary = @"
Commix explicit collect ($Mode)
Executions: $($rows.Count), failures: $failures, slow runs (>=10s): $slowRuns
Timeouts: $(@($rows | Where-Object timedOut).Count)
Duration ms: $($rows.elapsedMs | Measure-Object -Minimum -Maximum -Average | Out-String)
"@
$summary | Set-Content (Join-Path $OutputDirectory 'summary.txt')
Write-Host $summary
if ($env:GITHUB_STEP_SUMMARY) { $summary | Add-Content $env:GITHUB_STEP_SUMMARY }
if ($failures -gt 0) { exit 1 }
