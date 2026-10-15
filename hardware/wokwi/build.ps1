param(
    [string]$Target = "all"
)

$ErrorActionPreference = "Stop"

# Locate arduino-cli
$cli = (Get-Command arduino-cli -ErrorAction SilentlyContinue).Source
if (-not $cli -and (Test-Path "C:\Program Files\Arduino CLI\arduino-cli.exe")) {
    $cli = "C:\Program Files\Arduino CLI\arduino-cli.exe"
}
if (-not $cli) {
    Write-Error "arduino-cli not found. Ensure Arduino CLI is installed."
    exit 1
}

# Locate wokwi-cli
$wokwiCli = (Get-Command wokwi-cli -ErrorAction SilentlyContinue).Source
if (-not $wokwiCli -and (Test-Path "$env:USERPROFILE\.wokwi\bin\wokwi-cli.exe")) {
    $wokwiCli = "$env:USERPROFILE\.wokwi\bin\wokwi-cli.exe"
}

$fqbn = "arduino:avr:nano:cpu=atmega328old"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Build-Prototype($protoDir) {
    $name = Split-Path -Leaf $protoDir
    Write-Host "=== Building $name ===" -ForegroundColor Cyan
    
    # 1. Custom chip compilation if present
    $chipC = Join-Path $protoDir "74hc138.chip.c"
    $chipWasm = Join-Path $protoDir "74hc138.chip.wasm"
    if ((Test-Path $chipC) -and $wokwiCli) {
        Write-Host "Compiling custom chip to WASM..."
        & $wokwiCli chip compile $chipC -o $chipWasm
    }
    
    # 2. Arduino sketch compilation
    $mainIno = Join-Path $protoDir "main.ino"
    if (Test-Path $mainIno) {
        $buildDir = Join-Path $protoDir "build"
        New-Item -ItemType Directory -Path $buildDir -Force | Out-Null
        
        $tempDir = Join-Path $env:TEMP "wokwi_build_$name\main"
        if (Test-Path $tempDir) { Remove-Item -Recurse -Force (Split-Path -Parent $tempDir) }
        New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
        
        Copy-Item (Join-Path $protoDir "*") "$tempDir\" -Exclude "build", "*.wasm"
        
        Write-Host "Compiling sketch with $fqbn..."
        & $cli compile --fqbn $fqbn --output-dir "$buildDir" "$tempDir"
        
        # Maintain wokwi.ino.hex backwards compatibility alias if needed
        if (Test-Path (Join-Path $buildDir "main.ino.hex")) {
            Copy-Item (Join-Path $buildDir "main.ino.hex") (Join-Path $buildDir "wokwi.ino.hex") -Force
            Copy-Item (Join-Path $buildDir "main.ino.elf") (Join-Path $buildDir "wokwi.ino.elf") -Force
        }
        
        Remove-Item -Recurse -Force (Split-Path -Parent $tempDir)
        Write-Host "Build complete: $buildDir" -ForegroundColor Green
    }
}

if ($Target -eq "all") {
    Get-ChildItem -Directory "$scriptDir\P*" | ForEach-Object { Build-Prototype $_.FullName }
} else {
    $targetDir = Join-Path $scriptDir $Target
    if (Test-Path $targetDir) {
        Build-Prototype $targetDir
    } else {
        Write-Error "Prototype directory $Target not found!"
    }
}
