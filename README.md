# video-platform

Subida multipart de video directa a S3 + transcodificacion asincrona con FFmpeg.

- **Backend**: Spring Boot 3 (API de URLs prefirmadas, metadatos, publicacion de jobs).
- **Frontend**: Angular 20 (galeria de videos + subida multipart en paralelo con reintentos, reproduccion HLS).
- **Worker**: Spring Boot + FFmpeg sobre SQS, ECS Fargate Spot con escala a **0**.
- **Datos**: Aurora Serverless v2 (Postgres) con `min_capacity = 0`.
- **Infra**: Terraform (S3, SQS+DLQ, IAM minimo privilegio, ECR, ECS, ALB, CloudFront+OAC, VPC sin NAT).

```
video-platform/
├─ backend/     Spring Boot API  (Maven)
├─ worker/      Worker FFmpeg     (Maven)
├─ frontend/    Angular 20 (+ Dockerfile nginx para el stack completo)
├─ infra/       Terraform
└─ docker-compose.yml  Floci + Postgres + backend + worker + frontend (entorno local completo)
```

## Requisitos

- JDK 21
- Node 20+ (probado con Node 22)
- Docker (para el entorno local)
- Terraform 1.6+ (solo para desplegar en AWS)

## Compilar

```bash
# Backend + worker (usa el Maven Wrapper incluido)
./mvnw clean package

# Frontend
cd frontend && npm ci && npm run build
```

## Probar en local

### Opcion A - arranque rapido (H2 en memoria, sin AWS)

```bash
java -jar backend/target/backend-0.0.1-SNAPSHOT.jar
# perfil por defecto: local  (H2 + endpoint AWS en http://localhost:4566)
curl http://localhost:8080/actuator/health
```

`GET /api/videos` funciona. `/api/uploads/init` necesita S3 (usa la Opcion B).

### Opcion B - flujo completo local con Floci + Postgres

[Floci](https://floci.io) es un emulador AWS libre (MIT), drop-in de LocalStack en
`http://localhost:4566`. Con `docker compose` se levanta **todo** (emulador, base de
datos, backend y worker):

```bash
docker compose up -d --build
```

Esto arranca:
- `floci` en `:4566` (S3 + SQS),
- `floci-init`, que crea los buckets `video-raw`/`video-processed` (con CORS para el navegador) y las colas `transcode-queue`/`transcode-dlq`,
- `postgres`,
- `backend` en `http://localhost:8080` (perfil `docker`),
- `worker` (FFmpeg) consumiendo la cola,
- `frontend` (Angular compilado + nginx) en **http://localhost:8081**, con `/api` proxado al backend.

Abre `http://localhost:8081`:
- **Mis videos** (galeria): rejilla de tarjetas con miniatura, estado y duracion; filtros
  `ALL`/`READY`/`PROCESSING`/`FAILED`; auto-refresco mientras hay jobs en curso; clic en
  un video `READY` abre un reproductor HLS (hls.js) en modal. Cada tarjeta permite
  **Eliminar** (borra original + HLS/miniatura de S3 y la fila) y **Reintentar** en videos
  `FAILED`/`QUEUED` (los vuelve a encolar).
- **Subir**: selecciona un archivo, barra de progreso y espera a `READY`.

Las peticiones a `/api` envian la cabecera `X-User-Id: demo` (perfil local), de modo que
la galeria muestra los mismos videos que crean los scripts e2e.

### Cola y tiempos

- **QUEUED** dura segundos: el worker usa *long polling* y reclama el job en cuanto llega
  el mensaje. Solo se prolonga si el worker esta ocupado con otro video (procesa de uno en
  uno) o si hay mas videos que workers.
- **PROCESSING** escala con la **duracion** del video, no con el tamaño: genera HLS de
  1080p/720p/480p + miniatura en un unico ffmpeg. Referencia local: un video de ~28 min
  (~300 MB) tarda ~10 min.
- El mensaje de SQS se publica con `@TransactionalEventListener(AFTER_COMMIT)`, es decir
  **despues** de confirmar `status = QUEUED`. Publicarlo dentro de la transaccion causaba
  una carrera: el worker recibia el mensaje antes del commit, no veia el `QUEUED` y lo
  descartaba, dejando el video atascado en QUEUED.

Alternativa: frontend en modo dev con recarga en caliente (proxy `/api` -> `localhost:8080`):

```bash
cd frontend && npm start   # http://localhost:4200
```

### Flujo end-to-end automatico

Genera un video de prueba con el FFmpeg del propio worker y ejecuta init -> partes ->
complete -> esperar `READY`:

```bash
mkdir -p .tmp
docker run --rm --entrypoint ffmpeg -v "${PWD}/.tmp:/out" video-platform/worker:local \
  -f lavfi -i "testsrc=size=640x360:rate=15:duration=6" -c:v libx264 -pix_fmt yuv420p -y /out/sample.mp4

pwsh ./scripts/e2e.ps1            # Windows
# o:  bash scripts/e2e.sh         # (equivalente en bash)
```

Resultado esperado: `estado: READY` y las URLs HLS/miniatura bajo
`http://localhost:4566/video-processed/videos/<id>/...`. El video de prueba no tiene
audio, por lo que el worker inserta una pista silenciosa para uniformar el HLS.


### Probar la API por linea de comandos

```bash
# 1) iniciar subida
curl -s -X POST http://localhost:8080/api/uploads/init \
  -H 'Content-Type: application/json' -H 'X-User-Id: demo' \
  -d '{"filename":"sample.mp4","sizeBytes":10485760,"contentType":"video/mp4"}'

# 2) pedir URL prefirmada de una parte (usa el videoId devuelto)
curl -s "http://localhost:8080/api/uploads/<VIDEO_ID>/parts?numbers=1" -H 'X-User-Id: demo'

# 3) subir la parte directamente a S3 (usa la url del paso 2)
curl -s -X PUT --upload-file sample.mp4 "<PRESIGNED_URL>" -D -   # captura el ETag

# 4) completar (encola el job)
curl -s -X POST http://localhost:8080/api/uploads/<VIDEO_ID>/complete \
  -H 'Content-Type: application/json' -H 'X-User-Id: demo' \
  -d '{"parts":[{"partNumber":1,"eTag":"<ETAG>"}]}'

# 5) seguir el estado hasta READY
curl -s http://localhost:8080/api/videos/<VIDEO_ID> -H 'X-User-Id: demo'
```

En el perfil `local`/`docker` la autenticacion JWT esta desactivada y el usuario se
toma de la cabecera `X-User-Id` (por defecto `dev-user`).

## Desplegar en AWS

```bash
# 1) construir y publicar imagenes
aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin <ACCOUNT>.dkr.ecr.eu-west-1.amazonaws.com
docker build -t video-platform/backend backend/
docker build -t video-platform/worker  worker/
docker tag video-platform/backend:latest <BACKEND_ECR>:latest
docker tag video-platform/worker:latest  <WORKER_ECR>:latest
docker push <BACKEND_ECR>:latest && docker push <WORKER_ECR>:latest

# 2) infraestructura
cd infra
cp terraform.tfvars.example terraform.tfvars   # edita jwt_issuer, app_origin...
terraform init
terraform apply
```

Salidas utiles: `alb_dns_name`, `cloudfront_domain`, `transcode_queue_url`,
`worker_repository_url`, `aurora_endpoint`.

## Decisiones de coste

| Decision | Efecto |
|---|---|
| Subida directa a S3 con URLs prefirmadas | El backend no transporta video |
| Worker Fargate **Spot** con `desiredCount = 0` | Sin coste cuando no hay videos |
| Autoscaling guiado por `ApproximateNumberOfMessagesVisible` de SQS | Escala desde cero (metrica externa a ECS) |
| Aurora Serverless v2 `min_capacity = 0` | BD apagada en reposo |
| VPC Endpoints (S3 Gateway gratis + Interfaces) | **Sin NAT Gateway** (ahorro ~32 USD/mes) |
| CloudFront + OAC sobre bucket privado | Entrega cacheada, bucket no publico |
| S3 lifecycle: abortar multipart a 3 dias, expirar original a 30 | Limpia residuos y almacenamiento |
| KMS con Bucket Keys | Reduce ~99% el coste de peticiones KMS |

## Seguridad

- Sin credenciales estaticas: el backend y el worker usan su rol IAM de tarea.
- Password de BD gestionada por Aurora en Secrets Manager.
- Roles IAM de minimo privilegio y separados (backend != worker).
- S3: Block Public Access, cifrado KMS, `DenyInsecureTransport`, CORS solo del origen de la app con `ExposeHeaders: ETag`.
- SQS con cifrado KMS y DLQ (`maxReceiveCount = 3`).
- `prod` exige JWT (`JWT_ISSUER`); `local`/`docker` usan cabecera `X-User-Id`.

## Arquitectura de subida

1. `POST /api/uploads/init` -> crea `Video(UPLOADING)` + `CreateMultipartUpload`.
2. `GET  /api/uploads/{id}/parts?numbers=...` -> URLs prefirmadas (TTL 15 min).
3. El navegador hace `PUT` de cada parte directo a S3 (4 en paralelo, con reintentos).
4. `POST /api/uploads/{id}/complete` -> `CompleteMultipartUpload` + publica en SQS -> `Video(QUEUED)`.
5. El worker reclama el job de forma atomica (`QUEUED -> PROCESSING`), descarga,
   genera HLS (1080p/720p/480p) + miniatura, sube a `processed/` y marca `READY`.
6. `GET /api/videos/{id}` expone `hlsManifestUrl` (CloudFront) y `thumbnailUrl`.
7. `POST /api/videos/{id}/retry` reencola un video `FAILED`/`QUEUED`;
   `DELETE /api/videos/{id}` borra original + salidas en S3 y la fila.
