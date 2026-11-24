# 착착 백엔드 실행 이미지. jar는 GitHub Actions의 ./gradlew build 결과를 그대로 넣는다 (이미지 안에서 빌드하지 않음).
# 서버가 ARM(t4g)이라 --platform linux/arm64로 빌드한다. RUN이 없어서 다른 아키텍처에서도 에뮬레이션 없이 빨리 만든다.
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY build/libs/app.jar app.jar

# 메모리 2GB 서버에 Caddy와 함께 돌린다. 힙 상한을 정해 두어 서버가 메모리 부족으로 멈추지 않게 한다
ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx768m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

# 루트가 아닌 사용자로 실행한다
USER 65534:65534

# 8080: API, 9090: actuator(health, prometheus). 9090은 서버 밖으로 열지 않는다
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
