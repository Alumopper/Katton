plugins {
	id("org.jetbrains.kotlin.jvm") version "2.3.0"
}

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