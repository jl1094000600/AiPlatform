param(
    [string]$ModelPath = $env:BGE_M3_MODEL_PATH,
    [int]$Port = 8000,
    [string]$HostName = "0.0.0.0",
    [string]$Device = $env:BGE_M3_DEVICE
)

if (-not $ModelPath) {
    $ModelPath = "BAAI/bge-m3"
}

$env:BGE_M3_MODEL_PATH = $ModelPath
if ($Device) {
    $env:BGE_M3_DEVICE = $Device
}

python -m uvicorn app.main:app --host $HostName --port $Port

