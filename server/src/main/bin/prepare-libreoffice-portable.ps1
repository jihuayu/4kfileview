param(
    [Parameter(Mandatory = $true)][string]$DownloadUrl,
    [Parameter(Mandatory = $true)][string]$ArchiveName,
    [Parameter(Mandatory = $true)][string]$ExpectedSha256,
    [Parameter(Mandatory = $true)][string]$VendorDir
)

$ErrorActionPreference = 'Stop'

$AppDir = Join-Path $VendorDir 'LibreOfficePortable'
$AppInfo = Join-Path $AppDir 'App\AppInfo\appinfo.ini'
$CacheDir = if ($env:FOURKFILEVIEW_VENDOR_CACHE) {
    $env:FOURKFILEVIEW_VENDOR_CACHE
} elseif ($env:KKFILEVIEW_VENDOR_CACHE) {
    $env:KKFILEVIEW_VENDOR_CACHE
} else {
    Join-Path $env:USERPROFILE '.cache\4kfileview\vendor'
}
$ArchivePath = Join-Path $CacheDir $ArchiveName

function Get-Sha256([string]$Path) {
    return (Get-FileHash -Path $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Test-Archive {
    if (-not (Test-Path -LiteralPath $ArchivePath -PathType Leaf)) {
        return $false
    }
    return (Get-Sha256 $ArchivePath) -eq $ExpectedSha256.ToLowerInvariant()
}

if (Test-Path -LiteralPath $AppInfo -PathType Leaf) {
    Write-Host "LibreOfficePortable already prepared at $AppDir"
    exit 0
}

if (-not (Test-Archive)) {
    New-Item -ItemType Directory -Force -Path $CacheDir | Out-Null
    $TempArchive = "$ArchivePath.tmp"
    Remove-Item -LiteralPath $TempArchive -Force -ErrorAction SilentlyContinue
    Write-Host "Downloading $ArchiveName"
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $TempArchive
    Move-Item -LiteralPath $TempArchive -Destination $ArchivePath -Force
}

if (-not (Test-Archive)) {
    Write-Error "Checksum mismatch for $ArchivePath. Expected $ExpectedSha256, actual $(Get-Sha256 $ArchivePath)."
}

New-Item -ItemType Directory -Force -Path $VendorDir | Out-Null
Remove-Item -LiteralPath $AppDir -Recurse -Force -ErrorAction SilentlyContinue

$Arguments = @('/S', "/DESTINATION=$VendorDir")
Write-Host "Installing $ArchiveName to $VendorDir"
$Process = Start-Process -FilePath $ArchivePath -ArgumentList $Arguments -Wait -PassThru
if ($Process.ExitCode -ne 0) {
    Write-Error "PortableApps installer exited with code $($Process.ExitCode)."
}

if (-not (Test-Path -LiteralPath $AppInfo -PathType Leaf)) {
    Write-Error "LibreOfficePortable installation did not produce $AppInfo."
}

Write-Host "LibreOfficePortable prepared at $AppDir"
