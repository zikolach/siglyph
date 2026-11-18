plugins {
    kotlin("jvm") version "2.4.21"
    application
}
repositories { mavenLocal(); mavenCentral() }
dependencies {
    implementation("io.github.zikolach:siglyph-terminal-jvm_3:${providers.gradleProperty("siglyphVersion").get()}")
}
kotlin {
    jvmToolchain(21)
    sourceSets.main {
        kotlin.srcDir(".")
        kotlin.include("DxApp.kt", "SiglyphDsl.kt")
    }
}
application { mainClass.set("DxAppKt") }
