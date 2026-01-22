FROM gradle:8.8-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon -x test :agent-crawler:bootJar

FROM ubuntu:22.04 AS pw
ENV DEBIAN_FRONTEND=noninteractive
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl unzip \
 && rm -rf /var/lib/apt/lists/*
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
 && apt-get update && apt-get install -y --no-install-recommends nodejs \
 && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /ms-playwright \
 && npx -y playwright@1.49.0 install chromium \
 && rm -rf /root/.npm /root/.cache

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    fonts-liberation \
    libasound2 \
    libatk-bridge2.0-0 libatk1.0-0 \
    libcups2 \
    libdbus-1-3 \
    libdrm2 \
    libgbm1 \
    libglib2.0-0 \
    libnss3 libnspr4 \
    libx11-6 libx11-xcb1 \
    libxcb1 \
    libxcomposite1 libxdamage1 libxext6 libxfixes3 \
    libxrandr2 \
    libxshmfence1 \
    libxss1 \
    libxtst6 \
    libpango-1.0-0 libpangocairo-1.0-0 \
    libgtk-3-0 \
    xdg-utils \
 && rm -rf /var/lib/apt/lists/*
RUN groupadd -r app && useradd -r -g app -s /usr/sbin/nologin app
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
ENV PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS=1
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
ENV HOME=/tmp
COPY --from=pw /ms-playwright /ms-playwright
WORKDIR /app
COPY --from=build /workspace/agent-crawler/build/libs/*.jar /app/app.jar
RUN chown -R app:app /app /ms-playwright
USER app
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
