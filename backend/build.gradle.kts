plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.11.0"
}

group = "world.chakchak"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // 웹, 입력 검증
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // 인증, 세션
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")

    // DB
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")

    // 외부 API 호출 (Google Places 등)
    implementation("org.springframework.boot:spring-boot-starter-restclient")

    // 호출 횟수 제한 (토큰 버킷). Caffeine은 오래 안 쓴 사용자의 버킷을 지우는 데 쓴다
    implementation("com.bucket4j:bucket4j_jdk17-core:8.22.0")
    implementation("com.github.ben-manes.caffeine:caffeine")

    // 모니터링
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // API 문서 (Swagger UI)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // 테스트
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-session-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// 코드 포맷은 Google Java Format의 AOSP 스타일(들여쓰기 4칸)을 따른다. `./gradlew build`(check)에서 spotlessCheck가 함께 실행된다.
// 포매터 버전이 다르면 결과가 달라지므로 버전을 고정한다.
spotless {
    java {
        googleJavaFormat("2.0.0").aosp()
    }
}

// 로컬에서는 컴파일 전에 spotlessApply로 포맷을 자동으로 맞춘다. IntelliJ의 빌드·실행·테스트도 Gradle을 거치므로 같이 적용된다.
// CI(GitHub Actions는 환경변수 CI=true를 넣는다)에서는 고치지 않고 spotlessCheck로 검사만 한다.
// 포맷을 안 맞춘 채 push된 코드는 CI에서 실패해야 하기 때문이다.
if (System.getenv("CI") == null) {
    tasks.named("compileJava") { dependsOn("spotlessApply") }
    tasks.named("compileTestJava") { dependsOn("spotlessApply") }
    tasks.named("spotlessCheck") { mustRunAfter("spotlessApply") }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
