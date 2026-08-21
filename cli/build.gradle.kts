plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.85")
    implementation("org.json:json:20240303")
}

application {
    mainClass.set("dev.extos.cli.MainKt")
}
