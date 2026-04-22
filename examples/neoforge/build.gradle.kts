import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

plugins {
	id("org.jetbrains.kotlin.jvm") version "2.3.0"
}

val kattonVersion = "0.1.3b1"
//val clientScriptsTargetDir = file("G:\\AST\\kts4mc-template-1.21.11\\fabric\\run\\resourcepacks\\example\\assets\\test\\client_scripts")
//val serverScriptsTargetDir = file("G:\\AST\\kts4mc-template-1.21.11\\fabric\\run\\saves\\新的世界\\datapacks\\qwq\\data\\test\\scripts")
val clientScriptsTargetDir: File? = file("D:\\.minecraft\\resourcepacks\\katton_example\\assets\\test\\client_scripts")
val serverScriptsTargetDir: File? = file("D:\\.minecraft\\saves\\katton (1)\\datapacks\\test\\data\\test\\scripts")
val gClientScriptsTargetDir: File? = null
val gServerScriptsTargetDir: File? = null

repositories {
	mavenLocal()
	mavenCentral()
	maven("https://maven.fabricmc.net/")
	maven("https://libraries.minecraft.net")
	maven("https://nexus.mcfpp.top/repository/maven-public/")
}

dependencies {
	implementation("top.katton:katton-common:$kattonVersion")
	implementation("top.katton:katton-neoforge:$kattonVersion")
	compileOnly(fileTree("lib") {
		include("*.jar")
	})
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

fun syncDirectoryAsHardLinks(sourceDir: File, targetDir: File) {
	val sourceRoot = sourceDir.toPath().absolute().normalize()
	val targetRoot = targetDir.toPath().absolute().normalize()

	require(sourceRoot.exists() && sourceRoot.isDirectory()) {
		"Source directory does not exist: ${sourceRoot.pathString}"
	}

	targetRoot.createDirectories()

	sourceRoot.walk().forEach { sourcePath ->
		if (sourcePath == sourceRoot) return@forEach

		val relativePath = sourcePath.relativeTo(sourceRoot)
		val targetPath = targetRoot.resolve(relativePath.pathString)

		when {
			sourcePath.isDirectory() -> targetPath.createDirectories()
			sourcePath.isRegularFile() -> {
				targetPath.parent?.createDirectories()
				if (targetPath.exists()) {
					if (Files.isSameFile(sourcePath, targetPath)) return@forEach
					targetPath.deleteExisting()
				}
				Files.createLink(targetPath, sourcePath)
			}
		}
	}

	fun pruneStaleEntries(targetPath: Path) {
		if (!targetPath.exists()) return

		targetPath.listDirectoryEntries().forEach { child ->
			val relativePath = child.relativeTo(targetRoot)
			val sourcePath = sourceRoot.resolve(relativePath.pathString)

			if (!sourcePath.exists()) {
				if (child.isDirectory()) {
					child.toFile().deleteRecursively()
				} else {
					child.deleteExisting()
				}
				return@forEach
			}

			if (child.isDirectory()) {
				pruneStaleEntries(child)
				if (child.listDirectoryEntries().isEmpty() && !sourcePath.isDirectory()) {
					child.toFile().deleteRecursively()
				}
			}
		}
	}

	pruneStaleEntries(targetRoot)
}

tasks.register("copyGlobalClientScripts") {
	group = "distribution"
	description = "Mirrors client_scripts to the configured target path using hard links."
	doLast {
		gClientScriptsTargetDir?.let{ syncDirectoryAsHardLinks(file("global_client_scripts"), it) }
	}
}

tasks.register("copyGlobalServerScripts") {
	group = "distribution"
	description = "Mirrors client_scripts to the configured target path using hard links."
	doLast {
		gServerScriptsTargetDir?.let{ syncDirectoryAsHardLinks(file("global_server_scripts"), it) }
	}
}

tasks.register("copyClientScripts") {
	group = "distribution"
	description = "Mirrors client_scripts to the configured target path using hard links."
	doLast {
		clientScriptsTargetDir?.let{ syncDirectoryAsHardLinks(file("client_scripts"), it)}
	}
}

tasks.register("copyServerScripts") {
	group = "distribution"
	description = "Mirrors server_scripts to the configured target path using hard links."
	doLast {
		serverScriptsTargetDir?.let{ syncDirectoryAsHardLinks(file("server_scripts"), it) }
	}
}

tasks.register("copyGameScripts") {
	group = "distribution"
	description = "Mirrors client_scripts and server_scripts contents to their configured target paths."
	dependsOn("copyClientScripts", "copyServerScripts", "copyGlobalClientScripts", "copyGlobalServerScripts")
}
