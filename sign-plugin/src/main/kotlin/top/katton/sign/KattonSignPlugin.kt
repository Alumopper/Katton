package top.katton.sign

import org.gradle.api.Plugin
import org.gradle.api.Project

class KattonSignPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("generateKattonSigningKey", GenerateKattonSigningKeyTask::class.java) { task ->
            task.group = "katton"
            task.description = "Generates an Ed25519 key pair for signing Katton script packs."
            task.privateKeyFile.convention(
                project.providers.gradleProperty("kattonPrivateKey")
                    .map { project.layout.projectDirectory.file(it) }
                    .orElse(project.layout.projectDirectory.file("katton-signing-key.pem"))
            )
            task.publicKeyFile.convention(
                project.providers.gradleProperty("kattonPublicKey")
                    .map { project.layout.projectDirectory.file(it) }
                    .orElse(project.layout.projectDirectory.file("katton-signing-key.pub"))
            )
        }

        project.tasks.register("signKattonPack", KattonSignPackTask::class.java) { task ->
            task.group = "katton"
            task.description = "Signs a Katton script pack and writes signature metadata to manifest.json."
            task.packDir.convention(project.providers.gradleProperty("kattonPackDir").map { project.layout.projectDirectory.dir(it) })
            task.privateKeyFile.convention(project.providers.gradleProperty("kattonPrivateKey").map { project.layout.projectDirectory.file(it) })
            task.publicKeyFile.convention(project.providers.gradleProperty("kattonPublicKey").map { project.layout.projectDirectory.file(it) })
            task.scope.convention(project.providers.gradleProperty("kattonScope").orElse("world"))
            task.keyId.convention(project.providers.gradleProperty("kattonKeyId").orElse("default"))
        }
    }
}
