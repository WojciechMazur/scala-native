# Diagnostic watchdog: when the sbt output log stops growing while a native
# test binary is alive, dump the stacks of every thread of that binary.
param(
  [Parameter(Mandatory = $true)][string]$LogFile,
  [Parameter(Mandatory = $true)][string]$OutDir,
  [int]$StallSeconds = 300,
  [int]$MaxDumps = 3
)

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$arch = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'arm64' } else { 'x64' }
$cdb = Join-Path ${env:ProgramFiles(x86)} "Windows Kits\10\Debuggers\$arch\cdb.exe"
$lldb = if ($env:LLVM_BIN) { Join-Path $env:LLVM_BIN 'lldb.exe' } else { $null }
"watchdog start arch=$arch cdb=$(Test-Path $cdb) lldb=$($lldb -and (Test-Path $lldb))" |
  Out-File -Append (Join-Path $OutDir 'watchdog.txt')

$dumps = @{}
while ($true) {
  Start-Sleep -Seconds 20
  if (-not (Test-Path $LogFile)) { continue }
  $idle = ((Get-Date) - (Get-Item $LogFile).LastWriteTime).TotalSeconds
  $procs = Get-Process | Where-Object { $_.Name -like '*-test' -or $_.Name -like '*-test-out' }
  foreach ($p in $procs) {
    $n = [int]$dumps[$p.Id]
    if ($idle -lt $StallSeconds * ($n + 1) -or $n -ge $MaxDumps) { continue }
    $dumps[$p.Id] = $n + 1
    $stamp = Get-Date -Format 'HHmmss'
    $base = Join-Path $OutDir "stacks-$($p.Name)-$($p.Id)-$n-$stamp"
    "dump pid=$($p.Id) name=$($p.Name) idle=$([int]$idle)s n=$n path=$($p.Path)" |
      Out-File -Append (Join-Path $OutDir 'watchdog.txt')
    # Tail of the sbt output: shows the test that was running.
    Get-Content $LogFile -Tail 40 | Out-File "$base-sbt-tail.txt"
    if (Test-Path $cdb) {
      $cmds = '.symfix+ C:\symcache; .sympath+ ' + (Split-Path $p.Path) + '; .reload; ~*kn 100; q'
      $job = Start-Job -ScriptBlock { param($c, $id, $cm) & $c -pv -p $id -c $cm 2>&1 } -ArgumentList $cdb, $p.Id, $cmds
      if (Wait-Job $job -Timeout 300) { Receive-Job $job | Out-File "$base-cdb.txt" } else { Stop-Job $job; "cdb timed out" | Out-File "$base-cdb.txt" }
    }
    if ($lldb -and (Test-Path $lldb)) {
      $job = Start-Job -ScriptBlock { param($l, $id) & $l -p $id --batch -o 'thread backtrace all' -o 'detach' 2>&1 } -ArgumentList $lldb, $p.Id
      if (Wait-Job $job -Timeout 300) { Receive-Job $job | Out-File "$base-lldb.txt" } else { Stop-Job $job; "lldb timed out" | Out-File "$base-lldb.txt" }
    }
  }
}
