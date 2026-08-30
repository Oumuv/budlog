FROM eclipse-temurin:8-jre-jammy@sha256:d53aa7811eba390450721b1037978605992f5d9467c4af629384f23a49f78436

RUN groupadd --system budlog \
    && useradd --system --uid 10001 --gid budlog --home-dir /app --shell /usr/sbin/nologin budlog

WORKDIR /app

USER budlog
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/budlog-server.jar"]
