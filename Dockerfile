FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y \
    wget gnupg ca-certificates \
    fonts-liberation libasound2 libatk-bridge2.0-0 libatk1.0-0 libatspi2.0-0 \
    libcups2 libdbus-1-3 libdrm2 libgbm1 libgtk-3-0 libnspr4 libnss3 \
    libwayland-client0 libxcomposite1 libxdamage1 libxfixes3 libxkbcommon0 \
    libxrandr2 xdg-utils \
    && wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - \
    && echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google-chrome.list \
    && apt-get update && apt-get install -y google-chrome-stable \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* \
    && rm -rf /tmp/* \
    && rm -rf /var/tmp/* \
    && rm -rf /var/cache/apt/archives/*

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/data \
    && mkdir -p /tmp/.X11-unix \
    && chmod 1777 /tmp/.X11-unix \
    && mkdir -p /dev/shm \
    && chmod 1777 /dev/shm

# Configure shared memory for Chrome
VOLUME /dev/shm

# Create a startup script that clears memory before running
RUN echo '#!/bin/sh\n\
# Clear system caches and free memory\n\
sync\n\
echo 3 > /proc/sys/vm/drop_caches 2>/dev/null || true\n\
\n\
# Clean up temp directories\n\
rm -rf /tmp/.org.chromium.Chromium.* 2>/dev/null || true\n\
rm -rf /tmp/.X11-unix/* 2>/dev/null || true\n\
rm -rf /tmp/chrome* 2>/dev/null || true\n\
\n\
# Start the application\n\
exec java $JAVA_OPTS -jar /app/app.jar\n\
' > /app/startup.sh && chmod +x /app/startup.sh

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m \
    -XX:MetaspaceSize=96m -XX:MaxMetaspaceSize=192m \
    -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -XX:+UseCompressedOops \
    -XX:+UseCompressedClassPointers \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/data/heap_dump.hprof \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["/app/startup.sh"]
