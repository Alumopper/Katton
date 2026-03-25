plugins {
	id("org.jetbrains.kotlin.jvm") version "2.3.0"
}

val kattonVersion = "0.1.0"
val fabricApiVersion = "0.144.0+26.1"

repositories {
	mavenLocal()
	mavenCentral()
	maven("https://maven.fabricmc.net/")
	maven("https://libraries.minecraft.net")
	maven("https://nexus.mcfpp.top/repository/maven-public/")
}

dependencies {
	implementation("top.katton:katton-common:$kattonVersion")
	implementation("top.katton:katton-fabric:$kattonVersion")
	compileOnly(fileTree("lib") {
		include("*.jar")
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
