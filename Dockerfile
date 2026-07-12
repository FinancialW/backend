# 차트 이미지의 한글 렌더링을 위해 나눔 폰트 필수
FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-nanum \
    && rm -rf /var/lib/apt/lists/*

COPY build/libs/financial-0.0.1-SNAPSHOT.jar /app/financial.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/financial.jar"]
