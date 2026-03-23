package top.katton.docgen

import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeAlias
import java.io.File
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.invariantSeparatorsPathString

internal class ApiDocGenerator(
    private val projectDir: File,
    private val logger: Logger
) {
    fun generate(outputDir: File, modules: List<ApiModuleSnapshot>) {
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        writeThemeFiles(outputDir)

        val disposable = Disposer.newDisposable("katton-api-docs")
        try {
            val psiFactory = createPsiFactory(disposable)
            val pages = modules.flatMap { module ->
                collectModulePages(module, psiFactory)
            }

            writeApiIndex(outputDir, pages)
            writeSidebarFile(outputDir, pages, modules)
            modules.forEach { module ->
                val modulePages = pages.filter { it.moduleName == module.name }
                writeModuleIndex(outputDir, module, modulePages)
                modulePages.forEach { page ->
                    writePage(outputDir, page)
                }
            }

            logger.lifecycle("Generated ${pages.size} API documentation page(s) in ${outputDir.absolutePath}")
        } finally {
            Disposer.dispose(disposable)
        }
    }

    @OptIn(K1Deprecation::class)
    private fun createPsiFactory(disposable: org.jetbrains.kotlin.com.intellij.openapi.Disposable): KtPsiFactory {
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
            put(CommonConfigurationKeys.MODULE_NAME, "katton-api-docs")
            put(JVMConfigurationKeys.NO_JDK, true)
            addJvmClasspathRoot(KotlinToJVMBytecodeCompiler::class.java.protectionDomain.codeSource.location.toURI().let(::File))
        }
        val environment = KotlinCoreEnvironment.createForProduction(
            disposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
        return KtPsiFactory(environment.project, false)
    }

    private fun collectModulePages(module: ApiModuleSnapshot, psiFactory: KtPsiFactory): List<ApiPage> {
        val pages = mutableListOf<ApiPage>()
        module.sourceRoots.forEach { sourceRoot ->
            sourceRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .sortedBy { it.invariantPath() }
                .forEach { file ->
                    val page = parsePage(module, sourceRoot, file, psiFactory)
                    if (page != null) {
                        pages += page
                    }
                }
        }
        return pages.sortedBy { it.relativeOutputPath }
    }

    private fun parsePage(
        module: ApiModuleSnapshot,
        sourceRoot: File,
        file: File,
        psiFactory: KtPsiFactory
    ): ApiPage? {
        val content = file.readText().replace("\r\n", "\n")
        val ktFile = psiFactory.createFile(file.name, content)
        val declarations = ktFile.declarations
            .mapNotNull { declaration -> extractDeclaration(declaration, file, content, parentPath = emptyList()) }

        if (declarations.isEmpty()) {
            return null
        }

        val relativeSourcePath = projectRelativePath(file)
        val relativeFromSourceRoot = sourceRoot.toPath().relativize(file.toPath()).invariantSeparatorsPathString
        val outputPath = buildString {
            append("api/")
            append(module.name)
            append('/')
            append(relativeFromSourceRoot.removeSuffix(".kt"))
            append(".md")
        }

        val packageName = ktFile.packageFqName.asString()
        val title = file.nameWithoutExtension
        val summary = declarations.firstNotNullOfOrNull { it.docs.summary }

        return ApiPage(
            moduleName = module.name,
            moduleDisplayName = module.displayName,
            title = title,
            packageName = packageName,
            relativeSourcePath = relativeSourcePath,
            relativeOutputPath = outputPath,
            declarations = declarations,
            summary = summary
        )
    }

    private fun extractDeclaration(
        declaration: KtDeclaration,
        file: File,
        content: String,
        parentPath: List<String>
    ): ApiDeclaration? {
        if (declaration !is KtNamedDeclaration) {
            return null
        }
        if (declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) || declaration.name.isNullOrBlank()) {
            return null
        }

        val children = when (declaration) {
            is KtClassOrObject -> collectChildren(declaration.getBody(), file, content, parentPath + declaration.name.orEmpty())
            else -> emptyList()
        }

        val docs = parseKDoc(extractKDocText(declaration, content))
        if (docs.isEmpty() && children.isEmpty()) {
            return null
        }

        val name = declaration.name.orEmpty()
        val annotationTexts = declaration.annotationEntries.map { it.text.normalizeWhitespace() }
        return ApiDeclaration(
            name = name,
            path = parentPath + name,
            kind = declarationKind(declaration),
            signature = declarationSignature(declaration, annotationTexts),
            docs = docs,
            children = children
        )
    }

    private fun collectChildren(
        body: KtClassBody?,
        file: File,
        content: String,
        parentPath: List<String>
    ): List<ApiDeclaration> {
        if (body == null) {
            return emptyList()
        }
        return body.declarations
            .mapNotNull { child -> extractDeclaration(child, file, content, parentPath) }
    }

    private fun declarationKind(declaration: KtDeclaration): String = when (declaration) {
        is KtNamedFunction -> "Function"
        is KtProperty -> "Property"
        is KtTypeAlias -> "Type Alias"
        is KtObjectDeclaration -> if (declaration.isCompanion()) "Companion Object" else "Object"
        is KtClass -> when {
            declaration.isInterface() -> "Interface"
            declaration.isEnum() -> "Enum Class"
            declaration.isAnnotation() -> "Annotation Class"
            declaration.hasModifier(KtTokens.VALUE_KEYWORD) -> "Value Class"
            declaration.hasModifier(KtTokens.DATA_KEYWORD) -> "Data Class"
            declaration.hasModifier(KtTokens.SEALED_KEYWORD) -> "Sealed Class"
            else -> "Class"
        }
        else -> "Declaration"
    }

    private fun declarationSignature(declaration: KtDeclaration, annotations: List<String>): String {
        val body = when (declaration) {
            is KtNamedFunction -> functionSignature(declaration)
            is KtProperty -> propertySignature(declaration)
            is KtTypeAlias -> declarationBodyText(declaration).substringBefore('=').normalizeWhitespace()
            is KtClassOrObject -> classHeader(declaration)
            else -> declarationBodyText(declaration).lineSequence().firstOrNull().orEmpty().normalizeWhitespace()
        }
        return (annotations + body).joinToString("\n")
    }

    private fun functionSignature(function: KtNamedFunction): String {
        val modifiers = function.modifierList?.text.orEmpty().normalizeWhitespace().takeIf { it.isNotBlank() }
        val receiver = function.receiverTypeReference?.text?.let { "$it." }.orEmpty()
        val typeParameters = function.typeParameterList?.text?.plus(" ").orEmpty()
        val parameters = function.valueParameters.joinToString(", ") { parameterSignature(it) }
        val returnType = function.typeReference?.text?.let { ": $it" }.orEmpty()
        return listOfNotNull(modifiers, "fun", "$typeParameters$receiver${function.name}($parameters)$returnType")
            .joinToString(" ")
            .normalizeWhitespace()
    }

    private fun parameterSignature(parameter: KtParameter): String {
        val prefix = buildString {
            if (parameter.hasValOrVar()) {
                append(if (parameter.isMutable) "var " else "val ")
            }
            if (parameter.isVarArg) {
                append("vararg ")
            }
        }
        val type = parameter.typeReference?.text?.let { ": $it" }.orEmpty()
        val defaultValue = parameter.defaultValue?.text?.normalizeWhitespace()?.let { " = $it" }.orEmpty()
        return "$prefix${parameter.name}$type$defaultValue".trim()
    }

    private fun propertySignature(property: KtProperty): String {
        val modifiers = property.modifierList?.text.orEmpty().normalizeWhitespace().takeIf { it.isNotBlank() }
        val receiver = property.receiverTypeReference?.text?.let { "$it." }.orEmpty()
        val keyword = if (property.isVar) "var" else "val"
        val type = property.typeReference?.text?.let { ": $it" }.orEmpty()
        return listOfNotNull(modifiers, "$keyword $receiver${property.name}$type")
            .joinToString(" ")
            .normalizeWhitespace()
    }

    private fun classHeader(declaration: KtClassOrObject): String {
        return declarationBodyText(declaration)
            .substringBefore('{')
            .substringBefore("=")
            .normalizeWhitespace()
    }

    private fun declarationBodyText(declaration: KtDeclaration): String {
        return declaration.text
            .replace(Regex("^/\\*\\*.*?\\*/\\s*", setOf(RegexOption.DOT_MATCHES_ALL)), "")
            .replace(Regex("^(?:@[A-Za-z0-9_$.()\", =]+\\s*)+", setOf(RegexOption.MULTILINE)), "")
            .trimStart()
    }

    private fun parseKDoc(kdocText: String?): ParsedKDoc {
        if (kdocText == null) {
            return ParsedKDoc.EMPTY
        }

        val lines = kdocText
            .removePrefix("/**")
            .removeSuffix("*/")
            .lines()
            .map { line -> line.trim().removePrefix("*").trim() }

        val descriptionLines = mutableListOf<String>()
        val tags = linkedMapOf<String, MutableList<KDocTagEntry>>()
        var currentTag: String? = null
        var currentName: String? = null
        val currentValue = StringBuilder()

        fun flushTag() {
            val tagName = currentTag ?: return
            tags.getOrPut(tagName) { mutableListOf() }.add(
                KDocTagEntry(
                    name = currentName,
                    value = currentValue.toString().trim().normalizeBlankLines()
                )
            )
            currentTag = null
            currentName = null
            currentValue.setLength(0)
        }

        lines.forEach { rawLine ->
            val line = rawLine.trimStart()
            if (line.startsWith("@")) {
                flushTag()
                val match = Regex("@([A-Za-z]+)\\s*(\\S+)?\\s*(.*)").matchEntire(line)
                if (match != null) {
                    val tag = match.groupValues[1]
                    val name = match.groupValues[2].ifBlank { null }
                    val remainder = match.groupValues[3].trim()
                    currentTag = tag
                    currentName = when (tag) {
                        "return", "receiver" -> null
                        else -> name
                    }
                    currentValue.append(
                        when (tag) {
                            "return", "receiver" -> listOfNotNull(name, remainder).joinToString(" ").trim()
                            else -> remainder
                        }
                    )
                } else {
                    descriptionLines += line
                }
            } else if (currentTag != null) {
                if (currentValue.isNotEmpty()) {
                    currentValue.append('\n')
                }
                currentValue.append(rawLine.trim())
            } else {
                descriptionLines += rawLine
            }
        }
        flushTag()

        val description = descriptionLines.joinToString("\n").trim().normalizeBlankLines()
        val summary = description
            .split(Regex("\\n\\s*\\n"))
            .firstOrNull()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return ParsedKDoc(
            summary = summary,
            description = description,
            tags = tags.mapValues { (_, entries) -> entries.filter { it.value.isNotBlank() } }
                .filterValues { it.isNotEmpty() }
        )
    }

    private fun extractKDocText(declaration: KtNamedDeclaration, content: String): String? {
        declaration.docComment?.text?.let { return it }

        val declarationText = declaration.text.trimStart()
        if (declarationText.startsWith("/**")) {
            val commentEnd = declarationText.indexOf("*/")
            if (commentEnd >= 0) {
                return declarationText.substring(0, commentEnd + 2)
            }
        }

        val declarationOffset = declaration.textOffset
        val prefix = content.substring(0, declarationOffset)
        val trimmedPrefix = prefix.trimEnd()
        if (!trimmedPrefix.endsWith("*/")) {
            return null
        }

        val commentStart = trimmedPrefix.lastIndexOf("/**")
        if (commentStart < 0) {
            return null
        }

        val trailingText = trimmedPrefix.substring(commentStart)
        if (!trailingText.startsWith("/**")) {
            return null
        }

        return trimmedPrefix.substring(commentStart)
    }

    private fun writeThemeFiles(outputDir: File) {
        val themeDir = outputDir.resolve(".vitepress/theme")
        val componentDir = themeDir.resolve("components")
        componentDir.mkdirs()

        themeDir.resolve("index.ts").writeText(themeIndexSource())
        componentDir.resolve("ApiDocPage.vue").writeText(apiDocPageComponent())
        componentDir.resolve("ApiMembersList.vue").writeText(apiMembersListComponent())
        componentDir.resolve("ApiMemberCard.vue").writeText(apiMemberCardComponent())
    }

    private fun writeApiIndex(outputDir: File, pages: List<ApiPage>) {
        val byModule = pages.groupBy { it.moduleName }
        val apiDir = outputDir.resolve("api")
        apiDir.mkdirs()
        val content = buildString {
            appendLine("---")
            appendLine("title: API Docs")
            appendLine("outline: false")
            appendLine("---")
            appendLine()
            appendLine("# API Docs")
            appendLine()
            appendLine("These pages are generated from Kotlin KDoc comments and are ready to copy into a VitePress docs workspace.")
            appendLine()
            appendLine("## Modules")
            appendLine()
            byModule.toSortedMap().forEach { (module, modulePages) ->
                appendLine("- [$module](./$module/index.md) (${modulePages.size} page(s))")
            }
            appendLine()
            appendLine("## Generated VitePress Files")
            appendLine()
            appendLine("Copy `build/docs/api` into your VitePress content tree, then copy `build/docs/.vitepress/theme` and `build/docs/.vitepress/api-sidebar.ts` into your VitePress configuration.")
        }
        apiDir.resolve("index.md").writeText(content)
    }

    private fun writeSidebarFile(outputDir: File, pages: List<ApiPage>, modules: List<ApiModuleSnapshot>) {
        val vitepressDir = outputDir.resolve(".vitepress")
        vitepressDir.mkdirs()
        val content = buildString {
            appendLine("import type { DefaultTheme } from 'vitepress'")
            appendLine()
            appendLine("const apiSidebar: DefaultTheme.SidebarMulti = {")
            appendLine("  '/api/': [")
            appendLine("    {")
            appendLine("      text: 'API',")
            appendLine("      link: '/api/',")
            appendLine("      items: [")
            modules.forEachIndexed { index, module ->
                val modulePages = pages.filter { it.moduleName == module.name }
                appendLine("        {")
                appendLine("          text: '${escapeTsString(module.displayName)}',")
                appendLine("          link: '/api/${module.name}/',")
                appendLine("          collapsed: false,")
                appendLine("          items: [")
                modulePages.forEachIndexed { pageIndex, page ->
                    val suffix = if (pageIndex == modulePages.lastIndex) "" else ","
                    appendLine("            { text: '${escapeTsString(page.title)}', link: '${page.vitePressRoute()}' }$suffix")
                }
                appendLine("          ]")
                appendLine("        }${if (index == modules.lastIndex) "" else ","}")
            }
            appendLine("      ]")
            appendLine("    }")
            appendLine("  ]")
            appendLine("}")
            appendLine()
            appendLine("export default apiSidebar")
        }
        vitepressDir.resolve("api-sidebar.ts").writeText(content)
    }

    private fun writeModuleIndex(outputDir: File, module: ApiModuleSnapshot, pages: List<ApiPage>) {
        val moduleDir = outputDir.resolve("api/${module.name}")
        moduleDir.mkdirs()
        val content = buildString {
            appendLine("---")
            appendLine("title: ${module.displayName} API")
            appendLine("outline: false")
            appendLine("---")
            appendLine()
            appendLine("# ${module.displayName} API")
            appendLine()
            appendLine("Generated from module `${module.name}`.")
            appendLine()
            if (module.sourceRoots.isNotEmpty()) {
                appendLine("## Source Roots")
                appendLine()
                module.sourceRoots.sortedBy { it.invariantPath() }.forEach { sourceRoot ->
                    appendLine("- `${projectRelativePath(sourceRoot)}`")
                }
                appendLine()
            }
            appendLine("## Pages")
            appendLine()
            pages.forEach { page ->
                val relativeLink = Path.of("api", module.name, "index.md").parent.relativize(Path.of(page.relativeOutputPath)).invariantSeparatorsPathString
                val summarySuffix = page.summary?.let { " - ${escapeInline(it)}" }.orEmpty()
                appendLine("- [${page.title}](./${relativeLink})${summarySuffix}")
            }
        }
        moduleDir.resolve("index.md").writeText(content)
    }

    private fun writePage(outputDir: File, page: ApiPage) {
        val target = outputDir.resolve(page.relativeOutputPath)
        target.parentFile.mkdirs()

        val flattened = page.flattenedDeclarations()
        val content = buildString {
            appendLine("---")
            appendLine("title: ${page.title}")
            appendLine("outline: [2, 2]")
            appendLine("---")
            appendLine()
            appendLine("<ApiDocPage")
            appendLine("  title=\"${escapeHtmlAttribute(page.title)}\"")
            appendLine("  module=\"${escapeHtmlAttribute(page.moduleDisplayName)}\"")
            appendLine("  module-key=\"${escapeHtmlAttribute(page.moduleName.slugify())}\"")
            appendLine("  package-name=\"${escapeHtmlAttribute(page.packageName)}\"")
            appendLine("  source-file=\"${escapeHtmlAttribute(page.relativeSourcePath)}\"")
            appendLine(">")
            appendLine(page.summary ?: "Generated from Kotlin KDoc in `${page.relativeSourcePath}`.")
            appendLine("</ApiDocPage>")
            appendLine()

            appendLine("<ApiMembersList items-json='${escapeHtmlAttribute(membersJson(flattened))}' />")
            appendLine()

            page.declarations.forEach { declaration ->
                renderDeclaration(this, page, declaration, 2)
            }
        }

        target.writeText(content)
    }

    private fun renderDeclaration(
        builder: StringBuilder,
        page: ApiPage,
        declaration: ApiDeclaration,
        headingLevel: Int
    ) {
        val heading = "#".repeat(headingLevel.coerceIn(2, 6))
        builder.appendLine("$heading ${declaration.path.joinToString(".")}")
        builder.appendLine()
        builder.appendLine("<ApiMemberCard")
        builder.appendLine("  id=\"${declaration.anchor()}\"")
        builder.appendLine("  name=\"${escapeHtmlAttribute(declaration.path.joinToString("."))}\"")
        builder.appendLine("  kind=\"${escapeHtmlAttribute(declaration.kind)}\"")
        builder.appendLine("  kind-key=\"${escapeHtmlAttribute(declaration.kind.slugify())}\"")
        builder.appendLine("  module=\"${escapeHtmlAttribute(page.moduleDisplayName)}\"")
        builder.appendLine("  module-key=\"${escapeHtmlAttribute(page.moduleName.slugify())}\"")
        builder.appendLine(">")
        builder.appendLine()

        builder.appendLine("```kotlin")
        builder.appendLine(declaration.signature)
        builder.appendLine("```")
        builder.appendLine()

        if (declaration.docs.description.isNotBlank()) {
            builder.appendLine(declaration.docs.description)
            builder.appendLine()
        }

        renderTagTable(builder, "Parameters", declaration.docs.tags["param"], "Parameter")
        renderTagTable(builder, "Properties", declaration.docs.tags["property"], "Property")
        renderSingleTag(builder, "Returns", declaration.docs.tags["return"]?.firstOrNull()?.value)
        renderSingleTag(builder, "Receiver", declaration.docs.tags["receiver"]?.firstOrNull()?.value)
        renderTagTable(builder, "Throws", declaration.docs.tags["throws"], "Exception")
        renderTagTable(builder, "See Also", declaration.docs.tags["see"], "Reference")

        if (declaration.children.isNotEmpty()) {
            declaration.children.forEach { child ->
                renderDeclaration(builder, page, child, headingLevel + 1)
            }
        }

        builder.appendLine("</ApiMemberCard>")
        builder.appendLine()
    }

    private fun renderTagTable(
        builder: StringBuilder,
        title: String,
        entries: List<KDocTagEntry>?,
        label: String
    ) {
        if (entries.isNullOrEmpty()) {
            return
        }
        builder.appendLine("### $title")
        builder.appendLine()
        builder.appendLine("| $label | Description |")
        builder.appendLine("| --- | --- |")
        entries.forEach { entry ->
            val name = entry.name?.let { "`$it`" } ?: "-"
            builder.appendLine("| $name | ${entry.value.escapeTable()} |")
        }
        builder.appendLine()
    }

    private fun renderSingleTag(builder: StringBuilder, title: String, value: String?) {
        if (value.isNullOrBlank()) {
            return
        }
        builder.appendLine("### $title")
        builder.appendLine()
        builder.appendLine(value)
        builder.appendLine()
    }

    private fun projectRelativePath(file: File): String {
        return projectDir.toPath().relativize(file.toPath()).invariantSeparatorsPathString
    }

    private fun membersJson(declarations: List<ApiDeclaration>): String {
        return declarations.joinToString(prefix = "[", postfix = "]") { declaration ->
            "{" +
                "\"label\":\"${escapeJson(declaration.path.joinToString("."))}\"," +
                "\"href\":\"#${escapeJson(declaration.anchor())}\"," +
                    "\"kind\":\"${escapeJson(declaration.kind)}\"," +
                    "\"kindKey\":\"${escapeJson(declaration.kind.slugify())}\"" +
            "}"
        }
    }

    private fun String.escapeTable(): String =
        replace("|", "\\|")
            .replace("\n", "<br>")
            .trim()

    private fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ").trim()

    private fun String.normalizeBlankLines(): String =
        lines()
            .dropWhile(String::isBlank)
            .dropLastWhile(String::isBlank)
            .joinToString("\n")
            .replace(Regex("\\n{3,}"), "\n\n")

    private fun File.invariantPath(): String = toPath().invariantSeparatorsPathString

    private fun escapeHtmlAttribute(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun escapeInline(value: String): String = value.replace('\n', ' ')

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

    private fun escapeTsString(value: String): String = value.replace("'", "\\'")
}

private data class ApiPage(
    val moduleName: String,
    val moduleDisplayName: String,
    val title: String,
    val packageName: String,
    val relativeSourcePath: String,
    val relativeOutputPath: String,
    val declarations: List<ApiDeclaration>,
    val summary: String?
) {
    fun flattenedDeclarations(): List<ApiDeclaration> = declarations.flatMap { declaration ->
        listOf(declaration) + declaration.flattenedChildren()
    }

    fun vitePressRoute(): String = "/" + relativeOutputPath.removeSuffix(".md").removeSuffix("/index")
}

private data class ApiDeclaration(
    val name: String,
    val path: List<String>,
    val kind: String,
    val signature: String,
    val docs: ParsedKDoc,
    val children: List<ApiDeclaration>
) {
    fun anchor(): String = path.joinToString("-") { segment ->
        segment.slugify()
    }.ifBlank { name.lowercase() }

    fun flattenedChildren(): List<ApiDeclaration> = children.flatMap { child ->
        listOf(child) + child.flattenedChildren()
    }
}

private data class ParsedKDoc(
    val summary: String?,
    val description: String,
    val tags: Map<String, List<KDocTagEntry>>
) {
    fun isEmpty(): Boolean = summary == null && description.isBlank() && tags.isEmpty()

    companion object {
        val EMPTY = ParsedKDoc(summary = null, description = "", tags = emptyMap())
    }
}

private data class KDocTagEntry(
    val name: String?,
    val value: String
)

private fun String.slugify(): String =
    lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')

private fun themeIndexSource(): String = """
import type { Theme } from 'vitepress'
import DefaultTheme from 'vitepress/theme'
import ApiDocPage from './components/ApiDocPage.vue'
import ApiMembersList from './components/ApiMembersList.vue'
import ApiMemberCard from './components/ApiMemberCard.vue'

const theme: Theme = {
  ...DefaultTheme,
  enhanceApp(ctx) {
    DefaultTheme.enhanceApp?.(ctx)
    ctx.app.component('ApiDocPage', ApiDocPage)
        ctx.app.component('ApiMembersList', ApiMembersList)
    ctx.app.component('ApiMemberCard', ApiMemberCard)
  },
}

export default theme
""".trimIndent()

private fun apiDocPageComponent(): String = """
<template>
  <section class="api-doc-page">
    <div class="api-doc-page__header">
            <div class="api-doc-page__badges">
                <span class="api-doc-page__module-badge" :data-module="moduleKey">{{ module }}</span>
            </div>
      <h1 class="api-doc-page__title">{{ title }}</h1>
      <div class="api-doc-page__meta">
        <span class="api-doc-page__chip">{{ packageName }}</span>
        <span class="api-doc-page__chip">{{ sourceFile }}</span>
      </div>
    </div>
    <div class="api-doc-page__body">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  module: string
    moduleKey: string
  packageName: string
  sourceFile: string
}>()
</script>

<style scoped>
.api-doc-page {
    --api-bg: #0d1117;
    --api-panel: #161b22;
    --api-border: #30363d;
    --api-text: #c9d1d9;
    --api-muted: #8b949e;
    --api-accent: #58a6ff;
    margin: 0 0 1.5rem;
    padding: 1.25rem;
    border: 1px solid var(--api-border);
    border-radius: 12px;
    background: linear-gradient(180deg, rgba(22, 27, 34, 0.96) 0%, rgba(13, 17, 23, 0.98) 100%);
}

.api-doc-page__header {
    padding-bottom: 0.25rem;
}

.api-doc-page__badges {
    margin-bottom: 0.55rem;
}

.api-doc-page__module-badge {
    display: inline-flex;
    align-items: center;
    padding: 0.28rem 0.72rem;
    border: 1px solid #30363d;
    border-radius: 999px;
    background: rgba(88, 166, 255, 0.14);
    color: #c9d1d9;
    font-size: 0.78rem;
    font-weight: 600;
    letter-spacing: 0.04em;
    text-transform: uppercase;
}

.api-doc-page__module-badge[data-module='common'] { border-color: rgba(88, 166, 255, 0.35); color: #79c0ff; background: rgba(88, 166, 255, 0.12); }
.api-doc-page__module-badge[data-module='fabric'] { border-color: rgba(242, 201, 76, 0.34); color: #f2cc60; background: rgba(242, 201, 76, 0.1); }
.api-doc-page__module-badge[data-module='neoforge'] { border-color: rgba(63, 185, 80, 0.34); color: #7ee787; background: rgba(63, 185, 80, 0.1); }

.api-doc-page__title {
  margin: 0;
  color: var(--api-text);
    font-size: clamp(1.9rem, 3.2vw, 2.6rem);
    line-height: 1.1;
}

.api-doc-page__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.55rem;
  margin-top: 1rem;
}

.api-doc-page__chip {
    padding: 0.38rem 0.7rem;
    border: 1px solid var(--api-border);
    border-radius: 999px;
    background: rgba(110, 118, 129, 0.08);
  color: var(--api-muted);
  font-size: 0.82rem;
}

.api-doc-page__body {
  margin-top: 1rem;
  color: var(--api-text);
}

.api-doc-page :deep(p),
.api-doc-page :deep(li),
.api-doc-page :deep(td),
.api-doc-page :deep(th) {
  color: var(--api-text);
}

.api-doc-page :deep(code) {
    color: #79c0ff;
}

@media (max-width: 640px) {
  .api-doc-page {
    padding: 1.1rem;
        border-radius: 10px;
  }
}
</style>
""".trimIndent()

private fun apiMembersListComponent(): String = """
<template>
    <nav class="api-members-list" aria-label="Members navigation">
        <div class="api-members-list__header">
            <p class="api-members-list__title">Members</p>
            <p class="api-members-list__subtitle">Jump directly to declarations on this page.</p>
        </div>
        <ul class="api-members-list__grid">
            <li v-for="item in parsedItems" :key="item.href" class="api-members-list__item">
                <a :href="item.href" class="api-members-list__link">
                    <span class="api-members-list__label">{{ item.label }}</span>
                    <span class="api-members-list__kind" :data-kind="item.kindKey">{{ item.kind }}</span>
                </a>
            </li>
        </ul>
    </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type Item = {
    label: string
    href: string
    kind: string
    kindKey: string
}

const props = defineProps<{
    itemsJson: string
}>()

const parsedItems = computed<Item[]>(() => {
    try {
        return JSON.parse(props.itemsJson) as Item[]
    } catch {
        return []
    }
})
</script>

<style scoped>
.api-members-list {
    margin: 1.25rem 0 1.75rem;
    padding: 1rem;
    border: 1px solid #30363d;
    border-radius: 12px;
    background: #161b22;
}

.api-members-list__header {
    margin-bottom: 0.9rem;
}

.api-members-list__title {
    margin: 0;
    color: #f0f6fc;
    font-size: 1rem;
    font-weight: 600;
}

.api-members-list__subtitle {
    margin: 0.3rem 0 0;
    color: #8b949e;
    font-size: 0.86rem;
}

.api-members-list__grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    gap: 0.75rem;
    margin: 0;
    padding: 0;
    list-style: none;
}

.api-members-list__item {
    margin: 0;
}

.api-members-list__link {
    display: flex;
    justify-content: space-between;
    gap: 0.8rem;
    align-items: center;
    padding: 0.75rem 0.9rem;
    border: 1px solid #30363d;
    border-radius: 10px;
    background: rgba(13, 17, 23, 0.86);
    color: #c9d1d9;
    text-decoration: none;
    transition: border-color 0.15s ease, background-color 0.15s ease;
}

.api-members-list__link:hover {
    border-color: #58a6ff;
    background: rgba(17, 24, 39, 0.96);
}

.api-members-list__label {
    font-weight: 500;
    word-break: break-word;
}

.api-members-list__kind {
    flex-shrink: 0;
    padding: 0.2rem 0.48rem;
    border: 1px solid #30363d;
    border-radius: 999px;
    color: #8b949e;
    background: rgba(110, 118, 129, 0.08);
    font-size: 0.78rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
}

.api-members-list__kind[data-kind='function'] { border-color: rgba(88, 166, 255, 0.35); color: #79c0ff; background: rgba(88, 166, 255, 0.1); }
.api-members-list__kind[data-kind='property'] { border-color: rgba(242, 201, 76, 0.35); color: #f2cc60; background: rgba(242, 201, 76, 0.1); }
.api-members-list__kind[data-kind='data-class'] { border-color: rgba(188, 140, 255, 0.35); color: #d2a8ff; background: rgba(188, 140, 255, 0.12); }
.api-members-list__kind[data-kind='class'] { border-color: rgba(139, 148, 158, 0.35); color: #c9d1d9; background: rgba(139, 148, 158, 0.1); }
.api-members-list__kind[data-kind='object'] { border-color: rgba(63, 185, 80, 0.35); color: #7ee787; background: rgba(63, 185, 80, 0.1); }
.api-members-list__kind[data-kind='interface'] { border-color: rgba(56, 139, 253, 0.35); color: #58a6ff; background: rgba(56, 139, 253, 0.1); }
.api-members-list__kind[data-kind='enum-class'],
.api-members-list__kind[data-kind='annotation-class'],
.api-members-list__kind[data-kind='value-class'],
.api-members-list__kind[data-kind='sealed-class'],
.api-members-list__kind[data-kind='type-alias'],
.api-members-list__kind[data-kind='companion-object'] { border-color: rgba(210, 153, 34, 0.35); color: #e3b341; background: rgba(210, 153, 34, 0.1); }
</style>
""".trimIndent()

private fun apiMemberCardComponent(): String = """
<template>
  <article :id="id" class="api-member-card">
    <header class="api-member-card__header">
      <div class="api-member-card__meta">
                <span class="api-member-card__name">{{ name }}</span>
                <span class="api-member-card__pill api-member-card__pill--module" :data-module="moduleKey">{{ module }}</span>
                <span class="api-member-card__pill api-member-card__pill--kind" :data-kind="kindKey">{{ kind }}</span>
      </div>
    </header>
    <div class="api-member-card__body">
      <slot />
    </div>
  </article>
</template>

<script setup lang="ts">
defineProps<{
  id: string
  name: string
  kind: string
    kindKey: string
  module: string
    moduleKey: string
}>()
</script>

<style scoped>
.api-member-card {
    margin: 1rem 0 1.4rem;
    padding: 1rem 1.05rem;
    border: 1px solid #30363d;
    border-radius: 12px;
    background: #161b22;
}

.api-member-card__header {
    margin-bottom: 0.8rem;
}

.api-member-card__name {
    color: #f0f6fc;
    font-weight: 600;
}

.api-member-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.api-member-card__pill {
    padding: 0.34rem 0.65rem;
  border-radius: 999px;
    border: 1px solid #30363d;
    background: rgba(110, 118, 129, 0.08);
    color: #8b949e;
  font-size: 0.78rem;
}

.api-member-card__pill--kind {
    color: #58a6ff;
}

.api-member-card__pill--module[data-module='common'] { border-color: rgba(88, 166, 255, 0.35); color: #79c0ff; background: rgba(88, 166, 255, 0.12); }
.api-member-card__pill--module[data-module='fabric'] { border-color: rgba(242, 201, 76, 0.35); color: #f2cc60; background: rgba(242, 201, 76, 0.1); }
.api-member-card__pill--module[data-module='neoforge'] { border-color: rgba(63, 185, 80, 0.35); color: #7ee787; background: rgba(63, 185, 80, 0.1); }

.api-member-card__pill--kind[data-kind='function'] { border-color: rgba(88, 166, 255, 0.35); color: #79c0ff; background: rgba(88, 166, 255, 0.1); }
.api-member-card__pill--kind[data-kind='property'] { border-color: rgba(242, 201, 76, 0.35); color: #f2cc60; background: rgba(242, 201, 76, 0.1); }
.api-member-card__pill--kind[data-kind='data-class'] { border-color: rgba(188, 140, 255, 0.35); color: #d2a8ff; background: rgba(188, 140, 255, 0.12); }
.api-member-card__pill--kind[data-kind='class'] { border-color: rgba(139, 148, 158, 0.35); color: #c9d1d9; background: rgba(139, 148, 158, 0.1); }
.api-member-card__pill--kind[data-kind='object'] { border-color: rgba(63, 185, 80, 0.35); color: #7ee787; background: rgba(63, 185, 80, 0.1); }
.api-member-card__pill--kind[data-kind='interface'] { border-color: rgba(56, 139, 253, 0.35); color: #58a6ff; background: rgba(56, 139, 253, 0.1); }
.api-member-card__pill--kind[data-kind='enum-class'],
.api-member-card__pill--kind[data-kind='annotation-class'],
.api-member-card__pill--kind[data-kind='value-class'],
.api-member-card__pill--kind[data-kind='sealed-class'],
.api-member-card__pill--kind[data-kind='type-alias'],
.api-member-card__pill--kind[data-kind='companion-object'] { border-color: rgba(210, 153, 34, 0.35); color: #e3b341; background: rgba(210, 153, 34, 0.1); }

.api-member-card__body {
    color: #c9d1d9;
}

.api-member-card :deep(.language-kotlin) {
    margin: 0 0 1rem;
    border: 1px solid #30363d;
    border-radius: 10px;
}

.api-member-card :deep(h3),
.api-member-card :deep(h4),
.api-member-card :deep(h5) {
  margin-top: 1rem;
    color: #f0f6fc;
}

.api-member-card :deep(table) {
  display: table;
  width: 100%;
}

.api-member-card :deep(th),
.api-member-card :deep(td),
.api-member-card :deep(p),
.api-member-card :deep(li) {
    color: #c9d1d9;
}

.api-member-card :deep(code) {
    color: #79c0ff;
}

@media (max-width: 640px) {
  .api-member-card {
    padding: 1rem;
        border-radius: 10px;
  }
}
</style>
""".trimIndent()