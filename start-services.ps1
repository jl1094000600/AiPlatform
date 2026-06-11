# AI Platform 一键启动脚本
# 启动 Redis、Chroma向量库、BGEM3模型服务

param(
    [switch]$SkipRedis,
    [switch]$SkipChroma,
    [switch]$SkipEmbedding
)

$ErrorActionPreference = "Continue"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# 颜色定义
function Write-ColorOutput($Message, $Color = "White") {
    Write-Host $Message -ForegroundColor $Color
}

# 检测端口是否被占用
function Test-PortInUse {
    param([int]$Port)
    $result = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    return $null -ne $result
}

# 启动Redis
function Start-RedisService {
    Write-ColorOutput "`n=== 启动 Redis ===" "Cyan"

    if (Test-PortInUse -Port 6379) {
        Write-ColorOutput "Redis 已在运行 (端口6379)" "Yellow"
        return $true
    }

    $redisPath = $null
    $redisPaths = @(
        "C:\Program Files\Redis\redis-server.exe",
        "C:\Redis\redis-server.exe",
        "redis-server.exe"
    )

    foreach ($path in $redisPaths) {
        if ($path -eq "redis-server.exe") {
            $found = Get-Command redis-server.exe -ErrorAction SilentlyContinue
            if ($found) { $redisPath = "redis-server.exe"; break }
        } elseif (Test-Path $path) {
            $redisPath = $path
            break
        }
    }

    if ($redisPath) {
        Start-Process -FilePath $redisPath -WindowStyle Hidden
        Start-Sleep -Seconds 2
        if (Test-PortInUse -Port 6379) {
            Write-ColorOutput "Redis 启动成功 (端口6379)" "Green"
            return $true
        }
    }

    Write-ColorOutput "Redis 未安装或启动失败，请手动启动" "Red"
    return $false
}

# 启动Chroma向量库
function Start-ChromaService {
    Write-ColorOutput "`n=== 启动 Chroma 向量库 ===" "Cyan"

    if (Test-PortInUse -Port 9000) {
        Write-ColorOutput "Chroma 已在运行 (端口9000)" "Yellow"
        return $true
    }

    $chromaPath = $null
    $chromaPaths = @(
        "$ScriptDir\chroma",
        "$ScriptDir\embedding-service\chroma",
        "C:\Chroma",
        "chroma"
    )

    foreach ($path in $chromaPaths) {
        $exePath = Join-Path $path "chromadb.exe"
        $cliPath = Join-Path $path "chromadb"
        if (Test-Path $exePath) { $chromaPath = $exePath; break }
        if (Test-Path $cliPath) { $chromaPath = "python -m chromadb"; break }
    }

    if ($chromaPath) {
        Push-Location (Split-Path -Parent $chromaPath)
        Start-Process -FilePath $chromaPath -WindowStyle Hidden -ArgumentList "--host localhost --port 8000"
        Pop-Location
        Start-Sleep -Seconds 3

        # Chroma可能运行在不同端口，检查9000
        if (Test-PortInUse -Port 9000) {
            Write-ColorOutput "Chroma 启动成功 (端口9000)" "Green"
            return $true
        }

        # 尝试使用Docker
        $docker = Get-Command docker -ErrorAction SilentlyContinue
        if ($docker) {
            Write-ColorOutput "尝试使用 Docker 启动 Chroma..." "Yellow"
            docker run -d --name chroma -p 9000:8000 chromadb/chroma
            Start-Sleep -Seconds 5
            if (docker ps | Select-String "chroma") {
                Write-ColorOutput "Chroma(Docker) 启动成功 (端口9000)" "Green"
                return $true
            }
        }
    }

    Write-ColorOutput "Chroma 启动失败，请手动启动或安装 Docker" "Red"
    return $false
}

# 启动BGEM3 Embedding服务
function Start-EmbeddingService {
    Write-ColorOutput "`n=== 启动 BGEM3 Embedding 服务 ===" "Cyan"

    if (Test-PortInUse -Port 8000) {
        Write-ColorOutput "Embedding服务已在运行 (端口8000)" "Yellow"
        return $true
    }

    $embeddingDir = Join-Path $ScriptDir "embedding-service"
    $venvPython = Join-Path $embeddingDir ".venv\Scripts\python.exe"

    if (-not (Test-Path $venvPython)) {
        Write-ColorOutput "Python虚拟环境未找到: $venvPython" "Red"
        Write-ColorOutput "请先运行: cd $embeddingDir && python -m venv .venv && .venv\Scripts\pip install -r requirements.txt" "Yellow"
        return $false
    }

    # 设置模型路径环境变量
    $env:BGE_M3_MODEL_PATH = "BAAI/bge-m3"
    $env:BGE_M3_DEVICE = "cpu"

    $logFile = Join-Path $ScriptDir "logs\embedding-service.log"
    $logDir = Split-Path -Parent $logFile
    if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

    Start-Process -FilePath $venvPython `
        -ArgumentList "-m uvicorn app.main:app --host 0.0.0.0 --port 8000" `
        -WorkingDirectory $embeddingDir `
        -RedirectStandardOutput $logFile `
        -WindowStyle Hidden

    Write-ColorOutput "正在启动BGEM3模型服务..." "Yellow"
    Write-ColorOutput "日志文件: $logFile" "Gray"

    # 等待服务启动
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 2
        if (Test-PortInUse -Port 8000) {
            Write-ColorOutput "BGEM3 Embedding服务启动成功 (端口8000)" "Green"
            return $true
        }
    }

    Write-ColorOutput "BGEM3 Embedding服务启动超时，请检查日志: $logFile" "Red"
    return $false
}

# 主流程
Write-ColorOutput "========================================" "Cyan"
Write-ColorOutput "   AI Platform 服务启动脚本" "White"
Write-ColorOutput "   Redis: 6379 | Chroma: 9000 | Embedding: 8000" "Gray"
Write-ColorOutput "========================================" "Cyan"

$results = @{}

if (-not $SkipRedis) {
    $results.Redis = Start-RedisService
}

if (-not $SkipChroma) {
    $results.Chroma = Start-ChromaService
}

if (-not $SkipEmbedding) {
    $results.Embedding = Start-EmbeddingService
}

# 检查服务健康状态
Write-ColorOutput "`n========================================" "Cyan"
Write-ColorOutput "   服务状态检查" "White"
Write-ColorOutput "========================================" "Cyan"

$allHealthy = $true
foreach ($service in @("Redis", "Chroma", "Embedding")) {
    $port = @{Redis=6379; Chroma=9000; Embedding=8000}[$service]
    if (Test-PortInUse -Port $port) {
        Write-ColorOutput "[$service] 运行中 (端口$port)" "Green"
    } else {
        Write-ColorOutput "[$service] 未运行 (端口$port)" "Red"
        $allHealthy = $false
    }
}

if ($allHealthy) {
    Write-ColorOutput "`n所有服务已启动!" "Green"
} else {
    Write-ColorOutput "`n部分服务启动失败，请检查上述错误信息" "Yellow"
}

# 输出API地址
Write-ColorOutput "`n可用API地址:" "Cyan"
Write-ColorOutput "  Redis:    redis://localhost:6379" "White"
Write-ColorOutput "  Chroma:   http://localhost:9000" "White"
Write-ColorOutput "  Embedding: http://localhost:8000" "White"
Write-ColorOutput "  Backend:  http://localhost:8080" "White"
