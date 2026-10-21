FROM eclipse-temurin:21-jre-alpine

# FFmpeg incluye libx264 y ffprobe
RUN apk add --no-cache ffmpeg

WORKDIR /app
COPY target/worker-*.jar app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
