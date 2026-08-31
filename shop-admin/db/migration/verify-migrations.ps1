param(
    [string]$InitDirectory = (Join-Path $PSScriptRoot "..\init"),
    [string]$Manifest = (Join-Path $PSScriptRoot "manifest.sha256")
)

$ErrorActionPreference = "Stop"
$entries = @()
foreach ($line in Get-Content -LiteralPath $Manifest -Encoding UTF8) {
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith("#")) { continue }
    # Accept both literal backslash-t and tab delimiters.
    $parts = $line -split '\\t|`t'
    if ($parts.Count -ne 3) { throw "Invalid manifest row: $line" }
    $entries += [pscustomobject]@{ Version = [int]$parts[0]; FileName = $parts[1]; Sha256 = $parts[2].ToLowerInvariant() }
}

for ($i = 0; $i -lt $entries.Count; $i++) {
    $expected = $i + 1
    if ($entries[$i].Version -ne $expected) { throw "Migration versions are not contiguous: expected $expected, got $($entries[$i].Version)" }
    $path = Join-Path $InitDirectory $entries[$i].FileName
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Manifest file is missing: $($entries[$i].FileName)" }
    $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $entries[$i].Sha256) { throw "SHA-256 mismatch: $($entries[$i].FileName)" }
}

$diskFiles = @(Get-ChildItem -LiteralPath $InitDirectory -Filter "*.sql" -File | Select-Object -ExpandProperty Name)
$manifestFiles = @($entries | Select-Object -ExpandProperty FileName)
$missing = @($diskFiles | Where-Object { $_ -notin $manifestFiles })
$stale = @($manifestFiles | Where-Object { $_ -notin $diskFiles })
if ($missing.Count -gt 0) { throw "SQL files not listed in manifest: $($missing -join ', ')" }
if ($stale.Count -gt 0) { throw "Manifest contains missing SQL files: $($stale -join ', ')" }

Write-Output "Migration verification passed: $($entries.Count) versions."
