FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY target/backend-*.jar app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
