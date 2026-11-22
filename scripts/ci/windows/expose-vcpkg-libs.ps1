# Exposes vcpkg static libraries to Scala Native builds
param([Parameter(Mandatory)][string]$VcpkgLibs)
$ErrorActionPreference = 'Stop'

# javalib uses @link("zlib"), zlib 1.3+ installs the static library as zs.lib or libzs.lib
$libDir = Join-Path $VcpkgLibs 'lib'
$zlib = Join-Path $libDir 'zlib.lib'
if (-not (Test-Path -LiteralPath $zlib)) {
  $source = 'zs.lib', 'libzs.lib' |
    ForEach-Object { Join-Path $libDir $_ } |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1
  if (-not $source) {
    Get-ChildItem -LiteralPath $libDir | ForEach-Object { Write-Host $_.Name }
    Write-Error "No static zlib library in $libDir"
  }
  Copy-Item -LiteralPath $source -Destination $zlib
}

"SCALANATIVE_INCLUDE_DIRS=$VcpkgLibs\include" >> $env:GITHUB_ENV
"SCALANATIVE_LIB_DIRS=$libDir" >> $env:GITHUB_ENV
