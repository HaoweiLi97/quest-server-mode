[CmdletBinding()]
param(
    [string]$SdkPath = $(
        if ($env:ANDROID_HOME) { $env:ANDROID_HOME }
        elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
        else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    ),
    [string]$BuildToolsVersion = '36.0.0'
)

$ErrorActionPreference = 'Stop'
$d8 = Join-Path $SdkPath "build-tools\$BuildToolsVersion\d8.bat"
if (-not (Test-Path -LiteralPath $d8)) { throw "Android D8 not found: $d8" }
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) { throw 'Install JDK 11 or newer and add javac to PATH.' }
$build = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Path $build -Force | Out-Null
$source = Join-Path $PSScriptRoot 'src\DisplayPanel.java'
& javac --release 8 -Xlint:-options -d $build $source
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
& $d8 --output $build (Join-Path $build 'DisplayPanel.class')
if ($LASTEXITCODE -ne 0) { throw 'DEX compilation failed.' }
$destination = Join-Path $PSScriptRoot 'quest-display-panel.dex'
Copy-Item -LiteralPath (Join-Path $build 'classes.dex') -Destination $destination -Force
Write-Output "Built $destination"
Get-FileHash -LiteralPath $destination -Algorithm SHA256 | Format-List
