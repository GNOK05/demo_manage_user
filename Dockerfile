FROM eclipse-temurin:21-jdk

WORKDIR /app

# Copy the Gradle project and normalize the Windows wrapper for Linux.
COPY . .
RUN sed -i 's/\r$//' gradlew \
    && chmod +x gradlew \
    && ./gradlew clean bootJar -x test --no-daemon

EXPOSE 10000

CMD ["sh", "-c", "java -Dserver.port=${PORT:-10000} -jar build/libs/demo-0.0.1-SNAPSHOT.jar"]
