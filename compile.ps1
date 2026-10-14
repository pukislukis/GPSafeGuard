# Windows PowerShell 5.1+. Run from any directory.
# build-version.txt records the LAST SUCCESSFUL version, not the next attempt.
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$statePath = Join-Path $root 'build-version.txt'
$lockPath = Join-Path $root '.compile.lock'
$lock = $null
$exitCode = 1

try {
    # Serialize builds so two invocations cannot publish the same version.
    $lock = [System.IO.File]::Open($lockPath, [System.IO.FileMode]::OpenOrCreate,
        [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    $maven = Get-Command 'mvn.cmd' -ErrorAction Stop
    $null = Get-Command 'java.exe' -ErrorAction Stop
    $lastVersion = '1.0.0'
    if (Test-Path -LiteralPath $statePath) {
        $lastVersion = [System.IO.File]::ReadAllText($statePath).Trim()
    }
    if ($lastVersion -notmatch '^(\d+)\.(\d+)\.(\d+)$') {
        throw "Invalid build-version.txt: '$lastVersion'. Expected major.minor.patch."
    }
    $major = $Matches[1]
    $minor = $Matches[2]
    $patch = [long]::Parse($Matches[3])
    if ($patch -eq [long]::MaxValue) { throw 'Patch version has reached its maximum value.' }
    $nextVersion = "$major.$minor.$($patch + 1)"
    Write-Host "Building GPSafeGuard $nextVersion (last successful: $lastVersion)..."

    Push-Location -LiteralPath $root
    try {
        & $maven.Source --batch-mode --no-transfer-progress "-Drevision=$nextVersion" '-Dmaven.test.skip=true' clean package
        $mavenExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($mavenExitCode -ne 0) {
        throw "Maven failed (exit $mavenExitCode). Version remains $lastVersion; next attempt is $nextVersion."
    }
    $jarPath = Join-Path $root "target\GPSafeGuard-$nextVersion.jar"
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw "Build output missing: $jarPath. Version was not advanced."
    }
    # Keep successful JARs outside target so the next 'clean' preserves them.
    $output = Join-Path $root 'builds'
    $null = [System.IO.Directory]::CreateDirectory($output)
    $publishedJar = Join-Path $output "GPSafeGuard-$nextVersion.jar"
    [System.IO.File]::Copy($jarPath, $publishedJar, $true)
    $temporaryState = "$statePath.tmp"
    [System.IO.File]::WriteAllText($temporaryState, "$nextVersion`r`n", [System.Text.Encoding]::ASCII)
    if (Test-Path -LiteralPath $statePath) {
        $backupState = "$statePath.bak"
        [System.IO.File]::Replace($temporaryState, $statePath, $backupState)
        Remove-Item -LiteralPath $backupState -ErrorAction SilentlyContinue
    } else {
        [System.IO.File]::Move($temporaryState, $statePath)
    }
    Write-Host "BUILD SUCCESS: $publishedJar" -ForegroundColor Green
    Write-Host "Next successful build version: $major.$minor.$($patch + 2)"
    $exitCode = 0
} catch {
    Write-Host "BUILD FAILED: $($_.Exception.Message)" -ForegroundColor Red
} finally {
    if ($null -ne $lock) {
        $lock.Dispose()
        Remove-Item -LiteralPath $lockPath -ErrorAction SilentlyContinue
    }
}
exit $exitCode
