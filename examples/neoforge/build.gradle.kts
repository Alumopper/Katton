plugins {
	id("org.jetbrains.kotlin.jvm") version "2.3.0"
}

val clientScriptsTargetDir = file("CHANGE_ME/client_scripts")
val serverScriptsTargetDir = file("CHANGE_ME/server_scripts")

repositories {
	mavenCentral()
	maven {
		url = uri("https://libraries.minecraft.net")
	}
}

dependencies {
	// Add all jars in the lib/ directory as implementation dependencies
	val libTree = fileTree("lib") { include("*.jar") }
//	println("Using \n${libTree.joinToString("\n") { it.absolutePath }}")
	implementation(libTree)
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

tasks.register<Copy>("copyClientScripts") {
	group = "distribution"
	description = "Copies the contents of client_scripts to the configured target path."
	from("client_scripts")
	into(clientScriptsTargetDir)
	includeEmptyDirs = false
}

tasks.register<Copy>("copyServerScripts") {
	group = "distribution"
	description = "Copies the contents of server_scripts to the configured target path."
	from("server_scripts")
	into(serverScriptsTargetDir)
	includeEmptyDirs = false
}

tasks.register("copyGameScripts") {
	group = "distribution"
	description = "Copies client_scripts and server_scripts contents to their configured target paths."
	dependsOn("copyClientScripts", "copyServerScripts")
}
