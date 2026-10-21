#!/usr/bin/env bash
# End-to-end: init -> subir partes -> complete -> esperar READY
set -euo pipefail

FILE="${1:-$(cd "$(dirname "$0")/.." && pwd)/.tmp/sample.mp4}"
BASE="${2:-http://localhost:8080}"
USER="${3:-demo}"

if [[ ! -f "$FILE" ]]; then
  echo "No existe '$FILE'. Genera uno con:" >&2
  echo "  mkdir -p .tmp && docker run --rm --entrypoint ffmpeg -v \"\${PWD}/.tmp:/out\" video-platform/worker:local -f lavfi -i testsrc=size=640x360:rate=15:duration=6 -c:v libx264 -pix_fmt yuv420p -y /out/sample.mp4" >&2
  exit 1
fi

SIZE=$(wc -c < "$FILE" | tr -d ' ')
echo "Fichero: $FILE ($SIZE bytes)"

INIT=$(curl -sS -X POST "$BASE/api/uploads/init" \
  -H "X-User-Id: $USER" -H 'Content-Type: application/json' \
  -d "{\"filename\":\"$(basename "$FILE")\",\"sizeBytes\":$SIZE,\"contentType\":\"video/mp4\"}")

VIDEO_ID=$(echo "$INIT" | grep -o '"videoId":"[^"]*"' | cut -d'"' -f4)
PART_SIZE=$(echo "$INIT" | grep -o '"partSize":[0-9]*' | cut -d':' -f2)
TOTAL_PARTS=$(echo "$INIT" | grep -o '"totalParts":[0-9]*' | cut -d':' -f2)
echo "init -> videoId=$VIDEO_ID partSize=$PART_SIZE totalParts=$TOTAL_PARTS"

PARTS_JSON="["
for ((n = 1; n <= TOTAL_PARTS; n++)); do
  URL=$(curl -sS "$BASE/api/uploads/$VIDEO_ID/parts?numbers=$n" -H "X-User-Id: $USER" \
    | grep -o '"url":"[^"]*"' | head -1 | cut -d'"' -f4)
  START=$(( (n - 1) * PART_SIZE ))
  LEN=$(( SIZE - START )); (( LEN > PART_SIZE )) && LEN=$PART_SIZE
  ETAG=$(dd if="$FILE" bs=1 skip="$START" count="$LEN" 2>/dev/null \
    | curl -sS -X PUT "$URL" --data-binary @- -D - -o /dev/null \
    | grep -i '^etag:' | tr -d '\r' | sed -E 's/.*"(.*)".*/\1/')
  echo "parte $n/$TOTAL_PARTS subida -> ETag=$ETAG"
  [[ $n -gt 1 ]] && PARTS_JSON+=","
  PARTS_JSON+="{\"partNumber\":$n,\"eTag\":\"$ETAG\"}"
done
PARTS_JSON+="]"

COMPLETE=$(curl -sS -X POST "$BASE/api/uploads/$VIDEO_ID/complete" \
  -H "X-User-Id: $USER" -H 'Content-Type: application/json' \
  -d "{\"parts\":$PARTS_JSON}")
echo "complete -> status=$(echo "$COMPLETE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)"

STATUS=""
for _ in $(seq 1 60); do
  sleep 3
  V=$(curl -sS "$BASE/api/videos/$VIDEO_ID" -H "X-User-Id: $USER")
  STATUS=$(echo "$V" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
  echo "estado: $STATUS"
  case "$STATUS" in READY | FAILED | ABORTED) break ;; esac
done

if [[ "$STATUS" != "READY" ]]; then
  echo "El video termino en estado $STATUS: $(echo "$V" | grep -o '"errorMessage":"[^"]*"' | cut -d'"' -f4)" >&2
  exit 1
fi

echo "OK -> HLS: $(echo "$V" | grep -o '"hlsManifestUrl":"[^"]*"' | cut -d'"' -f4)"
echo "OK -> Thumbnail: $(echo "$V" | grep -o '"thumbnailUrl":"[^"]*"' | cut -d'"' -f4)"
echo "OK -> Duracion: $(echo "$V" | grep -o '"durationSeconds":[0-9]*' | cut -d':' -f2)s"
