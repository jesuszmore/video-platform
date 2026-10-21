# End-to-end: init -> subir partes -> complete -> esperar READY
param(
  [string]$File = "$PSScriptRoot/../.tmp/sample.mp4",
  [string]$Base = "http://localhost:8080",
  [string]$User = "demo"
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path $File)) {
  throw "No existe '$File'. Genera uno con:`n  docker run --rm --entrypoint ffmpeg -v `${PWD}/.tmp:/out video-platform/worker:local -f lavfi -i testsrc=size=640x360:rate=15:duration=6 -c:v libx264 -pix_fmt yuv420p -y /out/sample.mp4"
}
$headers = @{ 'X-User-Id' = $User }
$bytes = [System.IO.File]::ReadAllBytes($File)
Write-Host "Fichero: $File ($($bytes.Length) bytes)"

$init = Invoke-RestMethod -Method Post -Uri "$Base/api/uploads/init" -Headers $headers -ContentType 'application/json' `
  -Body (@{ filename = (Split-Path $File -Leaf); sizeBytes = $bytes.Length; contentType = 'video/mp4' } | ConvertTo-Json)
Write-Host "init -> videoId=$($init.videoId) partSize=$($init.partSize) totalParts=$($init.totalParts)"

$etags = @()
for ($n = 1; $n -le $init.totalParts; $n++) {
  $url = (Invoke-RestMethod -Method Get -Uri "$Base/api/uploads/$($init.videoId)/parts?numbers=$n" -Headers $headers)[0].url
  $start = ($n - 1) * $init.partSize
  $len = [Math]::Min($init.partSize, $bytes.Length - $start)
  $chunk = New-Object byte[] $len
  [Array]::Copy($bytes, $start, $chunk, 0, $len)
  $resp = Invoke-WebRequest -Method Put -Uri $url -Body $chunk -UseBasicParsing
  $etag = ($resp.Headers['ETag'] | Select-Object -First 1).Trim('"')
  Write-Host "parte $n/$($init.totalParts) subida -> ETag=$etag"
  $etags += @{ partNumber = $n; eTag = $etag }
}

$complete = Invoke-RestMethod -Method Post -Uri "$Base/api/uploads/$($init.videoId)/complete" -Headers $headers -ContentType 'application/json' `
  -Body (@{ parts = $etags } | ConvertTo-Json -Depth 5)
Write-Host "complete -> status=$($complete.status)"

$v = $null
for ($i = 0; $i -lt 60; $i++) {
  Start-Sleep -Seconds 3
  $v = Invoke-RestMethod -Method Get -Uri "$Base/api/videos/$($init.videoId)" -Headers $headers
  Write-Host "estado: $($v.status)"
  if ($v.status -in @('READY', 'FAILED', 'ABORTED')) { break }
}
if ($v.status -ne 'READY') { throw "El video termino en estado $($v.status): $($v.errorMessage)" }
Write-Host "OK -> HLS: $($v.hlsManifestUrl)"
Write-Host "OK -> Thumbnail: $($v.thumbnailUrl)"
Write-Host "OK -> Duracion: $($v.durationSeconds)s"
