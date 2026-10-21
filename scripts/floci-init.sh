#!/bin/sh
set -e

ENDPOINT="${AWS_ENDPOINT_URL:-http://floci:4566}"
AWS="aws --endpoint-url $ENDPOINT"

echo "Esperando a floci en $ENDPOINT ..."
i=0
until $AWS s3api list-buckets >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 60 ]; then
    echo "floci no respondio a tiempo" >&2
    exit 1
  fi
  sleep 1
done

echo "Creando buckets S3"
$AWS s3 mb s3://video-raw >/dev/null 2>&1 || true
$AWS s3 mb s3://video-processed >/dev/null 2>&1 || true

# CORS para que el navegador suba con PUT prefirmado (necesita leer el ETag)
# y reproduzca el HLS/miniatura directamente desde S3.
CORS='{"CORSRules":[{"AllowedOrigins":["*"],"AllowedMethods":["GET","PUT","POST","HEAD"],"AllowedHeaders":["*"],"ExposeHeaders":["ETag"],"MaxAgeSeconds":3000}]}'
echo "Configurando CORS en los buckets"
$AWS s3api put-bucket-cors --bucket video-raw --cors-configuration "$CORS"
$AWS s3api put-bucket-cors --bucket video-processed --cors-configuration "$CORS"

echo "Creando colas SQS"
$AWS sqs create-queue --queue-name transcode-dlq >/dev/null 2>&1 || true

DLQ_ARN=$($AWS sqs get-queue-attributes \
  --queue-url "$ENDPOINT/000000000000/transcode-dlq" \
  --attribute-names QueueArn \
  --query 'Attributes.QueueArn' \
  --output text)

$AWS sqs create-queue --queue-name transcode-queue \
  --attributes "{\"VisibilityTimeout\":\"3600\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}" >/dev/null

echo "Listo: buckets video-raw/video-processed y colas transcode-queue/transcode-dlq"
