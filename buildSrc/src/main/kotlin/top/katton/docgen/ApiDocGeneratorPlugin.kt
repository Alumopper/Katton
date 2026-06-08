package top.katton.docgen

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.io.File
import javax.inject.Inject

class ApiDocGeneratorPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "kattonApiDocs",
            ApiDocsExtension::class.java,
            project.objects,
            project.layout
        )

        project.tasks.register("generateApiDocs", GenerateApiDocsTask::class.java) { task ->
            task.group = JavaBasePlugin.DOCUMENTATION_GROUP
            task.description = "Generate VitePress-ready API docs from Kotlin KDoc comments."
            task.outputDir.set(extension.outputDir)
            task.locales.set(extension.locales)
            task.defaultLocale.set(extension.defaultLocale)
            task.moduleSpecs.set(project.provider {
                extension.modules.map { module ->
                    ApiModuleSnapshot(
                        name = module.name,
                        displayName = module.displayName.orNull ?: module.name,
                        sourceRoots = module.sourceRoots.files
                            .filter(File::exists)
                            .sortedBy(File::getAbsolutePath)
                    )
                }
            })
        }
    }
}

abstract class ApiDocsExtension @Inject constructor(
    objects: ObjectFactory,
    layout: ProjectLayout
) {
    val outputDir: DirectoryProperty = objects.directoryProperty().convention(layout.buildDirectory.dir("docs"))

    val locales: ListProperty<String> = objects.listProperty(String::class.java).convention(listOf("en", "zh"))

    val defaultLocale: Property<String> = objects.property(String::class.java).convention("en")

    val modules: NamedDomainObjectContainer<ApiDocModuleSpec> =
        objects.domainObjectContainer(ApiDocModuleSpec::class.java) { name ->
            objects.newInstance(ApiDocModuleSpec::class.java, name)
        }

    fun module(name: String, action: Action<in ApiDocModuleSpec>) {
        action.execute(modules.maybeCreate(name))
    }
}

abstract class ApiDocModuleSpec @Inject constructor(
    private val moduleName: String,
    objects: ObjectFactory
) : Named {
    override fun getName(): String = moduleName

    val displayName: Property<String> = objects.property(String::class.java).convention(moduleName)

    val sourceRoots: ConfigurableFileCollection = objects.fileCollection()
}

abstract class GenerateApiDocsTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:org.gradle.api.tasks.Input
    abstract val locales: ListProperty<String>

    @get:org.gradle.api.tasks.Input
    abstract val defaultLocale: Property<String>

    @get:org.gradle.api.tasks.Internal
    abstract val moduleSpecs: ListProperty<ApiModuleSnapshot>

    @org.gradle.api.tasks.TaskAction
    fun generate() {
        val generator = ApiDocGenerator(project.projectDir, logger)
        generator.generate(outputDir.get().asFile, moduleSpecs.get(), locales.get(), defaultLocale.get())
    }
}

data class ApiModuleSnapshot(
    val name: String,
    val displayName: String,
    val sourceRoots: List<File>
)
