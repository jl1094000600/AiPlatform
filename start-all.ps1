# AI Platform local service starter
# Usage:
#   .\start-all.ps1
#   .\start-all.ps1 -SkipRedis -SkipChroma -SkipEmbedding
param(
    [switch]$SkipRedis,
    [switch]$SkipChroma,
    [switch]$SkipEmbedding
)

$ErrorActionPreference = "Continue"
$ScriptDir = $PSScriptRoot
$EmbedDir = Join-Path $ScriptDir "embedding-service"
$LogDir = Join-Path $ScriptDir "logs"

function Write-Color {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Message,
        [string]$Color = "White"
    )
    Write-Host $Message -ForegroundColor $Color
}

function Test-Port {
    param([Parameter(Mandatory = $true)][int]$Port)
    $conn = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1
    return $null -ne $conn
}

function Ensure-LogDir {
    if (-not (Test-Path -LiteralPath $LogDir)) {
        New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
    }
}

function Test-CommandAvailable {
    param([Parameter(Mandatory = $true)][string]$CommandName)
    return $null -ne (Get-Command $CommandName -ErrorAction SilentlyContinue)
}

function Start-Redis {
    Write-Color "Starting Redis..." "Cyan"
    if (Test-Port 6379) {
        Write-Color "Redis is already running (port 6379)." "Yellow"
        return
    }

    if (-not (Test-CommandAvailable "redis-server")) {
        Write-Color "redis-server was not found in PATH. Install Redis or start it manually." "Red"
        return
    }

    Ensure-LogDir
    $logFile = Join-Path $LogDir "redis.log"
    Start-Process -FilePath "redis-server" `
        -ArgumentList "--port 6379" `
        -RedirectStandardOutput $logFile `
        -WindowStyle Hidden

    Start-Sleep -Seconds 2
    if (Test-Port 6379) {
        Write-Color "Redis started (port 6379)." "Green"
    } else {
        Write-Color "Redis did not start. Check $logFile" "Red"
    }
}

function Start-Chroma {
    Write-Color "Starting Chroma..." "Cyan"
    if (Test-Port 9000) {
        Write-Color "Chroma is already running (port 9000)." "Yellow"
        return
    }

    $chromaCommand = $null
    $chromaArgs = $null
    if (Test-CommandAvailable "chromadb") {
        $chromaCommand = "chromadb"
        $chromaArgs = "--host localhost --port 9000"
    } elseif (Test-CommandAvailable "chroma") {
        $chromaCommand = "chroma"
        $chromaArgs = "run --host 0.0.0.0 --port 9000"
    }

    if (-not $chromaCommand) {
        Write-Color "Chroma CLI was not found in PATH. Install Chroma or start it manually." "Red"
        return
    }

    Ensure-LogDir
    $logFile = Join-Path $LogDir "chroma.log"
    Start-Process -FilePath $chromaCommand `
        -ArgumentList $chromaArgs `
        -RedirectStandardOutput $logFile `
        -WindowStyle Hidden

    Start-Sleep -Seconds 3
    if (Test-Port 9000) {
        Write-Color "Chroma started (port 9000)." "Green"
    } else {
        Write-Color "Chroma did not start. Check $logFile" "Red"
    }
}

function Start-Embedding {
    Write-Color "Starting BGEM3 embedding service..." "Cyan"
    if (Test-Port 8500) {
        Write-Color "BGEM3 embedding service is already running (port 8500)." "Yellow"
        return
    }

    $python = Join-Path $EmbedDir ".venv\Scripts\python.exe"
    if (-not (Test-Path -LiteralPath $python)) {
        Write-Color "Python virtual env was not found: $python" "Red"
        Write-Color "Create it under embedding-service or start the embedding service manually." "Red"
        return
    }

    Ensure-LogDir
    $logFile = Join-Path $LogDir "embedding.log"
    if (-not $env:BGE_M3_MODEL_PATH) {
        $env:BGE_M3_MODEL_PATH = "F:\Models\BGEM3"
    }
    if (-not $env:BGE_M3_DEVICE) {
        $env:BGE_M3_DEVICE = "cpu"
    }

    Start-Process -FilePath $python `
        -ArgumentList "-m uvicorn app.main:app --host 0.0.0.0 --port 8500" `
        -WorkingDirectory $EmbedDir `
        -RedirectStandardOutput $logFile `
        -WindowStyle Hidden

    Write-Color "Waiting for BGEM3 embedding service..." "Gray"
    for ($i = 0; $i -lt 60; $i++) {
        if (Test-Port 8500) {
            Write-Color "BGEM3 embedding service started (port 8500)." "Green"
            return
        }
        Start-Sleep -Seconds 1
    }

    Write-Color "BGEM3 embedding service timed out. Check $logFile" "Red"
}

Write-Color "========================================" "Cyan"
Write-Color "   AI Platform service startup" "White"
Write-Color "========================================" "Cyan"

if (-not $SkipRedis) {
    Start-Redis
}
if (-not $SkipChroma) {
    Start-Chroma
}
if (-not $SkipEmbedding) {
    Start-Embedding
}

Write-Color ""
Write-Color "========================================" "Cyan"
Write-Color "   Service status" "White"
Write-Color "========================================" "Cyan"

$services = @(
    @{ Name = "Redis"; Port = 6379 },
    @{ Name = "Chroma"; Port = 9000 },
    @{ Name = "BGEM3"; Port = 8500 }
)

foreach ($svc in $services) {
    $isRunning = Test-Port $svc.Port
    $status = if ($isRunning) { "RUNNING" } else { "STOPPED" }
    $color = if ($isRunning) { "Green" } else { "Red" }
    Write-Color ("[{0}] {1} (port {2})" -f $svc.Name, $status, $svc.Port) $color
}

Write-Color ""
Write-Color "Service URLs:" "Cyan"
Write-Color "  Redis:      redis://localhost:6379" "White"
Write-Color "  Chroma:     http://localhost:9000" "White"
Write-Color "  Embedding:  http://localhost:8500" "White"
Write-Color "  Backend:    http://localhost:8080" "White"
