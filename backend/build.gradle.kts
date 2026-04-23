plugins {
    java
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
    // jOOQ コードジェネレーター用プラグイン（Read Model の型安全 SQL を生成）
    id("nu.studer.jooq") version "10.0"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot スターター
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-restclient")

    // データベース
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // jOOQ（Read Model の型安全 SQL アクセス用）
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    // jOOQ コードジェネレーター classpath — 実行時ランタイムと同一バージョンに揃えないと
    // meta.jaxb の互換エラー（NoSuchMethodError 等）が発生するため、明示的にバージョン固定
    jooqGenerator("org.jooq:jooq-codegen:3.20.8")
    jooqGenerator("org.jooq:jooq-meta:3.20.8")
    jooqGenerator("org.jooq:jooq:3.20.8")
    jooqGenerator("org.postgresql:postgresql")

    // JWT（JJWT ライブラリ）
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Google OAuth（ID トークン検証用）
    implementation("com.google.api-client:google-api-client:2.7.2")

    // テスト
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ========================================
// jOOQ コードジェネレーター設定
// ========================================
// Read Model テーブルから型安全な Java クラスを生成する
// 前提: Docker の PostgreSQL (localhost:5433/kintai) が起動し、Flyway マイグレーション済みであること
// 生成コマンド: ./gradlew generateJooq
// 生成先: build/generated-src/jooq/main/（nu.studer.jooq プラグインが source set に自動追加）
// IDE で生成クラスが認識されない場合は Gradle プロジェクトを Reload する
// ========================================
jooq {
    // Spring Boot 4.0.2 BOM に従うバージョンを明示
    version.set("3.20.8")

    configurations {
        create("main") {
            // DB 接続なしでもビルドを通せるよう、コンパイル時の自動生成は無効化
            // 必要な時だけ明示的に ./gradlew generateJooq を実行する運用
            generateSchemaSourceOnCompilation.set(false)

            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.WARN
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    // 接続情報は Gradle プロジェクトプロパティで上書き可能
                    // 本番・別環境では ~/.gradle/gradle.properties（git 管理外）で
                    //   jooq.jdbc.url=...
                    //   jooq.jdbc.user=...
                    //   jooq.jdbc.password=...
                    // のように設定する
                    url = project.findProperty("jooq.jdbc.url") as String?
                        ?: "jdbc:postgresql://localhost:5433/kintai"
                    user = project.findProperty("jooq.jdbc.user") as String? ?: "kintai"
                    password = project.findProperty("jooq.jdbc.password") as String? ?: "kintai"
                }
                generator.apply {
                    name = "org.jooq.codegen.JavaGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        // 生成対象は Read Model 5 テーブル + employees（ShiftFinder の JOIN 用）
                        includes = listOf(
                            "attendance_summaries",
                            "monthly_attendance_summaries",
                            "weekly_schedule_summaries",
                            "shift_pattern_summaries",
                            "department_attendance_stats",
                            "employees"
                        ).joinToString("|")
                        // Flyway 履歴テーブルは除外
                        excludes = "flyway_schema_history"
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isImmutablePojos = false
                        isFluentSetters = true
                        isJavaTimeTypes = true
                    }
                    target.apply {
                        packageName = "com.example.kintai.attendance.infrastructure.jooq.generated"
                        directory = "build/generated-src/jooq/main"
                    }
                    strategy.name = "org.jooq.codegen.DefaultGeneratorStrategy"
                }
            }
        }
    }
}
