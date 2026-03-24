import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.protobuf) apply false
}

allprojects {
    group = "org.example.distributedorchestration"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension>("java") {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    plugins.withId("org.springframework.boot") {
        val otelAgent = rootProject.layout.projectDirectory.file("common/lib/opentelemetry-javaagent.jar").asFile
        tasks.named<BootRun>("bootRun") {
            jvmArgs = listOf("-javaagent:${otelAgent.absolutePath}")
            environment("OTEL_SERVICE_NAME", project.name)
            environment(
                "OTEL_EXPORTER_OTLP_ENDPOINT",
                findProperty("otel.otlp.endpoint")?.toString() ?: "http://localhost:4317",
            )
            environment("OTEL_EXPORTER_OTLP_PROTOCOL", "grpc")
            environment("OTEL_TRACES_EXPORTER", "otlp")
            environment("OTEL_METRICS_EXPORTER", "none")
            environment("OTEL_LOGS_EXPORTER", "none")
        }
    }
}
