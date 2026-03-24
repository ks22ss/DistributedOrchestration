plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.grpc.netty.shaded)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.prometheus)
    implementation(libs.resilience4j.spring.boot3)
    implementation(libs.opentelemetry.spring.boot.starter)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.grpc.inprocess)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
