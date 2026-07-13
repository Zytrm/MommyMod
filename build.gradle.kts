import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.3.21"
    id("net.fabricmc.fabric-loom") version "1.16.2"
    id("com.gradleup.shadow") version "9.4.1"
    id("maven-publish")
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 25
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}

repositories {
    maven { url = uri("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1") }
    maven { url = uri("https://maven.lavalink.dev/releases") }
    maven { url = uri("https://maven.arbjerg.dev/releases") }
    mavenCentral()
    maven { url = uri("https://maven.terraformersmc.com/releases/") }
}

val mediaLibraries by configurations.creating

configurations.named("compileClasspath") {
    extendsFrom(mediaLibraries)
}

configurations.named("runtimeClasspath") {
    extendsFrom(mediaLibraries)
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")

    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    runtimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.2")

    compileOnly("com.terraformersmc:modmenu:${project.property("modmenu_version")}")
    testImplementation(kotlin("test-junit5"))

    mediaLibraries("dev.arbjerg:lavaplayer:2.2.6") {
        exclude(group = "org.slf4j")
    }
    mediaLibraries("dev.lavalink.youtube:v2:1.18.1") {
        exclude(group = "dev.arbjerg", module = "lavaplayer")
        exclude(group = "org.slf4j")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand("version" to project.version, "minecraft_version" to project.property("minecraft_version")!!, "loader_version" to project.property("loader_version")!!, "kotlin_loader_version" to project.property("kotlin_loader_version")!!)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    archiveClassifier.set("thin")
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(mediaLibraries)
    archiveClassifier.set("")
    relocate("dev.lavalink", "com.zytrm.mommymods.libs.devlavalink")
    relocate("com.github.topi314.lavasrc", "com.zytrm.mommymods.libs.lavasrc")
    relocate("org.apache.http", "com.zytrm.mommymods.libs.apachehttp")
    relocate("org.apache.commons", "com.zytrm.mommymods.libs.apachecommons")
    relocate("com.fasterxml.jackson", "com.zytrm.mommymods.libs.jackson")
    relocate("com.grack.nanojson", "com.zytrm.mommymods.libs.nanojson")
    relocate("mozilla.javascript", "com.zytrm.mommymods.libs.mozilla")
    relocate("com.iheartradio", "com.zytrm.mommymods.libs.iheartradio")
    relocate("org.jsoup", "com.zytrm.mommymods.libs.jsoup")
    mergeServiceFiles()
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }
}

loom {
    runConfigs.named("client") {
        isIdeConfigGenerated = true
        vmArg("-XX:+AllowEnhancedClassRedefinition")
    }

    runConfigs.named("server") {
        isIdeConfigGenerated = false
    }
}

afterEvaluate {
    loom.runs.named("client") {
        vmArg("-javaagent:${configurations.compileClasspath.get().find { "sponge-mixin" in it.name }}")
    }
}
