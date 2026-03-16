plugins {
	id("org.jetbrains.kotlin.jvm") version "2.3.0"
}

val kattonVersion = "1.0.0"
val fabricApiVersion = "0.143.2+26.1"

repositories {
	mavenLocal()
	mavenCentral()
	maven("https://maven.fabricmc.net/")
	maven("https://libraries.minecraft.net")
	maven(url = uri(rootDir.resolve("../../build/repo")))
}

dependencies {
	implementation("top.katton:katton-fabric:$kattonVersion")
	compileOnly(fileTree("lib") {
		include("*.jar")
		exclude("katton-*.jar")
		exclude("fabric-1.0.0.jar")
		exclude("fabric-api-*.jar")
	})
	compileOnly("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
	compileOnly("com.mojang:brigadier:1.3.10")
}

sourceSets {
	kotlin {
		main {
			kotlin.srcDir("client_scripts")
				.srcDir("server_scripts")
		}
	}
}

kotlin {
	jvmToolchain(25)
}
