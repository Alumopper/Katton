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
    fun generate(outputDir: File, modules: List<ApiModuleSnapshot>, locales: List<String>, defaultLocale: String) {
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        val localeCodes = normalizeLocales(locales, defaultLocale)
        val defaultLocaleCode = normalizeLocaleCode(defaultLocale).takeIf { it.isNotBlank() } ?: localeCodes.first()

        writeThemeFiles(outputDir)

        val disposable = Disposer.newDisposable("katton-api-docs")
        try {
            val psiFactory = createPsiFactory(disposable)
            val pages = modules.flatMap { module ->
                collectModulePages(module, psiFactory, localeCodes.toSet())
            }

            localeCodes.forEach { locale ->
                writeApiIndex(outputDir, pages, locale, defaultLocaleCode)
                writeSidebarFile(outputDir, pages, modules, locale, defaultLocaleCode)
                modules.forEach { module ->
                    val modulePages = pages.filter { it.moduleName == module.name }
                    writeModuleIndex(outputDir, module, modulePages, locale, defaultLocaleCode)
                    modulePages.forEach { page ->
                        writePage(outputDir, page, locale, defaultLocaleCode)
                    }
                }
            }

            logger.lifecycle("Generated ${pages.size} API documentation page(s) for ${localeCodes.size} locale(s) in ${outputDir.absolutePath}")
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun normalizeLocales(locales: List<String>, defaultLocale: String): List<String> {
        val normalized = (listOf(defaultLocale) + locales)
            .mapNotNull { locale -> normalizeLocaleCode(locale).takeIf { it.isNotBlank() } }
            .filter { it.isNotBlank() }
            .distinct()
        return normalized.ifEmpty { listOf("en", "zh") }
    }

    private fun normalizeLocaleCode(locale: String): String {
        val normalized = locale.trim().lowercase(Locale.ROOT)
        require(normalized.isBlank() || normalized.matches(LOCALE_CODE_PATTERN)) {
            "Invalid API docs locale '$locale'. Use lowercase language tags such as 'en', 'zh', or 'en-us'."
        }
        return normalized
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

    private fun collectModulePages(
        module: ApiModuleSnapshot,
        psiFactory: KtPsiFactory,
        localizedLocales: Set<String>
    ): List<ApiPage> {
        val pages = mutableListOf<ApiPage>()
        module.sourceRoots.forEach { sourceRoot ->
            sourceRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .sortedBy { it.invariantPath() }
                .forEach { file ->
                    val page = parsePage(module, sourceRoot, file, psiFactory, localizedLocales)
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
        psiFactory: KtPsiFactory,
        localizedLocales: Set<String>
    ): ApiPage? {
        val content = file.readText().replace("\r\n", "\n")
        val ktFile = psiFactory.createFile(file.name, content)
        val declarations = ktFile.declarations
            .mapNotNull { declaration ->
                extractDeclaration(declaration, file, content, parentPath = emptyList(), localizedLocales)
            }

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
        parentPath: List<String>,
        localizedLocales: Set<String>
    ): ApiDeclaration? {
        if (declaration !is KtNamedDeclaration) {
            return null
        }
        if (declaration.hasModifier(KtTokens.PRIVATE_KEYWORD) || declaration.name.isNullOrBlank()) {
            return null
        }

        val children = when (declaration) {
            is KtClassOrObject -> collectChildren(
                declaration.getBody(),
                file,
                content,
                parentPath + declaration.name.orEmpty(),
                localizedLocales
            )
            else -> emptyList()
        }

        val docs = parseKDoc(extractKDocText(declaration, content), localizedLocales)
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
        parentPath: List<String>,
        localizedLocales: Set<String>
    ): List<ApiDeclaration> {
        if (body == null) {
            return emptyList()
        }
        return body.declarations
            .mapNotNull { child -> extractDeclaration(child, file, content, parentPath, localizedLocales) }
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

    private fun parseKDoc(kdocText: String?, localizedLocales: Set<String>): ParsedKDoc {
        if (kdocText == null) {
            return ParsedKDoc.EMPTY
        }

        val lines = kdocText
            .removePrefix("/**")
            .removeSuffix("*/")
            .lines()
            .map { line -> line.trim().removePrefix("*").trim() }

        val descriptionLines = mutableListOf<String>()
        val localizedDescriptionLines = linkedMapOf<String, MutableList<String>>()
        val tags = linkedMapOf<String, MutableList<KDocTagEntry>>()
        var currentTag: String? = null
        var currentName: String? = null
        var currentLocale: String? = null
        var currentDescriptionLocale: String? = null
        val currentValues = linkedMapOf<String?, StringBuilder>()

        fun appendCurrentValue(value: String) {
            val builder = currentValues.getOrPut(currentLocale) { StringBuilder() }
            if (builder.isNotEmpty()) {
                builder.append('\n')
            }
            builder.append(value)
        }

        fun flushTag() {
            val tagName = currentTag ?: return
            val defaultValue = currentValues[null]?.toString()?.trim()?.normalizeBlankLines().orEmpty()
            val localizedValues = currentValues
                .filterKeys { it != null }
                .mapKeys { (locale, _) -> locale.orEmpty() }
                .mapValues { (_, value) -> value.toString().trim().normalizeBlankLines() }
                .filterValues { it.isNotBlank() }
            tags.getOrPut(tagName) { mutableListOf() }.add(
                KDocTagEntry(
                    name = currentName,
                    value = defaultValue,
                    localizedValues = localizedValues
                )
            )
            currentTag = null
            currentName = null
            currentLocale = null
            currentValues.clear()
        }

        fun appendLocalizedValue(locale: String, value: String) {
            if (currentTag != null) {
                currentLocale = locale
                appendCurrentValue(value)
            } else {
                currentDescriptionLocale = locale
                localizedDescriptionLines.getOrPut(locale) { mutableListOf() } += value
            }
        }

        fun localeMarker(line: String): Pair<String, String>? {
            val match = LOCALIZED_KDOC_MARKER_PATTERN.matchEntire(line) ?: return null
            val locale = match.groupValues[1].lowercase(Locale.ROOT)
            if (locale !in localizedLocales) {
                return null
            }
            return locale to match.groupValues[2].trim()
        }

        lines.forEach { rawLine ->
            val line = rawLine.trimStart()
            val marker = localeMarker(line)
            if (marker != null) {
                appendLocalizedValue(marker.first, marker.second)
            } else if (line.startsWith("@")) {
                val match = Regex("@([A-Za-z]+)\\s*(\\S+)?\\s*(.*)").matchEntire(line)
                if (match != null) {
                    val tag = match.groupValues[1]
                    val name = match.groupValues[2].ifBlank { null }
                    val remainder = match.groupValues[3].trim()
                    val locale = tag.lowercase(Locale.ROOT).takeIf { it in localizedLocales }
                    if (locale != null) {
                        val localizedValue = listOfNotNull(name, remainder).joinToString(" ").trim()
                        appendLocalizedValue(locale, localizedValue)
                    } else {
                        flushTag()
                        currentDescriptionLocale = null
                        currentTag = tag
                        currentName = when (tag) {
                            "return", "receiver" -> null
                            else -> name
                        }
                        currentLocale = null
                        appendCurrentValue(
                            when (tag) {
                                "return", "receiver" -> listOfNotNull(name, remainder).joinToString(" ").trim()
                                else -> remainder
                            }
                        )
                    }
                } else {
                    descriptionLines += line
                }
            } else if (currentTag != null) {
                appendCurrentValue(rawLine.trim())
            } else if (currentDescriptionLocale != null) {
                localizedDescriptionLines.getOrPut(currentDescriptionLocale.orEmpty()) { mutableListOf() } += rawLine
            } else {
                descriptionLines += rawLine
            }
        }
        flushTag()

        val description = descriptionLines.joinToString("\n").trim().normalizeBlankLines()
        val localizedDescriptions = localizedDescriptionLines
            .mapValues { (_, lines) -> lines.joinToString("\n").trim().normalizeBlankLines() }
            .filterValues { it.isNotBlank() }
        val summary = description
            .split(Regex("\\n\\s*\\n"))
            .firstOrNull()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return ParsedKDoc(
            summary = summary,
            description = description,
            localizedDescriptions = localizedDescriptions,
            tags = tags.mapValues { (_, entries) -> entries.filterNot { it.isEmpty() } }
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

    private fun writeApiIndex(outputDir: File, pages: List<ApiPage>, locale: String, defaultLocale: String) {
        val byModule = pages.groupBy { it.moduleName }
        val labels = localeLabels(locale)
        val apiDir = outputDir.resolve(apiContentRoot(locale, defaultLocale))
        apiDir.mkdirs()
        val content = buildString {
            appendLine("---")
            appendLine("title: ${labels.apiDocsTitle}")
            appendLine("outline: false")
            appendLine("---")
            appendLine()
            appendLine("# ${labels.apiDocsTitle}")
            appendLine()
            appendLine(labels.apiDocsIntro)
            appendLine()
            appendLine("## ${labels.modulesTitle}")
            appendLine()
            byModule.toSortedMap().forEach { (module, modulePages) ->
                appendLine("- [$module](./$module/index.md) (${modulePages.size} page(s))")
            }
            appendLine()
            appendLine("## ${labels.generatedFilesTitle}")
            appendLine()
            appendLine(labels.generatedFilesIntro(apiContentRoot(locale, defaultLocale), sidebarFileName(locale, defaultLocale)))
        }
        apiDir.resolve("index.md").writeText(content)
    }

    private fun writeSidebarFile(
        outputDir: File,
        pages: List<ApiPage>,
        modules: List<ApiModuleSnapshot>,
        locale: String,
        defaultLocale: String
    ) {
        val vitepressDir = outputDir.resolve(".vitepress")
        vitepressDir.mkdirs()
        val routeRoot = apiRouteRoot(locale, defaultLocale)
        val labels = localeLabels(locale)
        val content = buildString {
            appendLine("import type { DefaultTheme } from 'vitepress'")
            appendLine()
            appendLine("const apiSidebar: DefaultTheme.SidebarMulti = {")
            appendLine("  '$routeRoot': [")
            appendLine("    {")
            appendLine("      text: '${escapeTsString(labels.apiSidebarText)}',")
            appendLine("      link: '$routeRoot',")
            appendLine("      items: [")
            modules.forEachIndexed { index, module ->
                val modulePages = pages.filter { it.moduleName == module.name }
                appendLine("        {")
                appendLine("          text: '${escapeTsString(module.displayName)}',")
                appendLine("          link: '${routeRoot}${module.name}/',")
                appendLine("          collapsed: false,")
                appendLine("          items: [")
                modulePages.forEachIndexed { pageIndex, page ->
                    val suffix = if (pageIndex == modulePages.lastIndex) "" else ","
                    appendLine("            { text: '${escapeTsString(page.title)}', link: '${page.vitePressRoute(locale, defaultLocale)}' }$suffix")
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
        vitepressDir.resolve(sidebarFileName(locale, defaultLocale)).writeText(content)
    }

    private fun writeModuleIndex(
        outputDir: File,
        module: ApiModuleSnapshot,
        pages: List<ApiPage>,
        locale: String,
        defaultLocale: String
    ) {
        val labels = localeLabels(locale)
        val moduleDir = outputDir.resolve("${apiContentRoot(locale, defaultLocale)}/${module.name}")
        moduleDir.mkdirs()
        val content = buildString {
            appendLine("---")
            appendLine("title: ${module.displayName} ${labels.apiSuffix}")
            appendLine("outline: false")
            appendLine("---")
            appendLine()
            appendLine("# ${module.displayName} ${labels.apiSuffix}")
            appendLine()
            appendLine(labels.moduleIntro(module.name))
            appendLine()
            if (module.sourceRoots.isNotEmpty()) {
                appendLine("## ${labels.sourceRootsTitle}")
                appendLine()
                module.sourceRoots.sortedBy { it.invariantPath() }.forEach { sourceRoot ->
                    appendLine("- `${projectRelativePath(sourceRoot)}`")
                }
                appendLine()
            }
            appendLine("## ${labels.pagesTitle}")
            appendLine()
            pages.forEach { page ->
                val localizedPage = page.localized(locale, defaultLocale)
                val relativeLink = Path.of(apiContentRoot(locale, defaultLocale), module.name, "index.md")
                    .parent
                    .relativize(Path.of(page.localizedOutputPath(locale, defaultLocale)))
                    .invariantSeparatorsPathString
                val summarySuffix = localizedPage.summary?.let { " - ${escapeInline(it)}" }.orEmpty()
                appendLine("- [${page.title}](./${relativeLink})${summarySuffix}")
            }
        }
        moduleDir.resolve("index.md").writeText(content)
    }

    private fun writePage(outputDir: File, page: ApiPage, locale: String, defaultLocale: String) {
        val localizedPage = page.localized(locale, defaultLocale)
        val target = outputDir.resolve(page.localizedOutputPath(locale, defaultLocale))
        target.parentFile.mkdirs()

        val flattened = localizedPage.flattenedDeclarations()
        val content = buildString {
            appendLine("---")
            appendLine("title: ${localizedPage.title}")
            appendLine("outline: [2, 2]")
            appendLine("---")
            appendLine()
            appendLine("<ApiDocPage")
            appendLine("  title=\"${escapeHtmlAttribute(localizedPage.title)}\"")
            appendLine("  module=\"${escapeHtmlAttribute(localizedPage.moduleDisplayName)}\"")
            appendLine("  module-key=\"${escapeHtmlAttribute(localizedPage.moduleName.slugify())}\"")
            appendLine("  package-name=\"${escapeHtmlAttribute(localizedPage.packageName)}\"")
            appendLine("  source-file=\"${escapeHtmlAttribute(localizedPage.relativeSourcePath)}\"")
            appendLine(">")
            appendLine(localizedPage.summary ?: localeLabels(locale).generatedFromKDoc(localizedPage.relativeSourcePath))
            appendLine("</ApiDocPage>")
            appendLine()

            appendLine("<ApiMembersList items-json='${escapeHtmlAttribute(membersJson(flattened))}' />")
            appendLine()

            localizedPage.declarations.forEach { declaration ->
                renderDeclaration(this, localizedPage, declaration, 2, locale)
            }
        }

        target.writeText(content)
    }

    private fun renderDeclaration(
        builder: StringBuilder,
        page: ApiPage,
        declaration: ApiDeclaration,
        headingLevel: Int,
        locale: String
    ) {
        val labels = localeLabels(locale)
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

        renderTagTable(builder, labels.parametersTitle, declaration.docs.tags["param"], labels.parameterLabel, labels.descriptionLabel)
        renderTagTable(builder, labels.propertiesTitle, declaration.docs.tags["property"], labels.propertyLabel, labels.descriptionLabel)
        renderSingleTag(builder, labels.returnsTitle, declaration.docs.tags["return"]?.firstOrNull()?.value)
        renderSingleTag(builder, labels.receiverTitle, declaration.docs.tags["receiver"]?.firstOrNull()?.value)
        renderTagTable(builder, labels.throwsTitle, declaration.docs.tags["throws"], labels.exceptionLabel, labels.descriptionLabel)
        renderTagTable(builder, labels.seeAlsoTitle, declaration.docs.tags["see"], labels.referenceLabel, labels.descriptionLabel)

        if (declaration.children.isNotEmpty()) {
            declaration.children.forEach { child ->
                renderDeclaration(builder, page, child, headingLevel + 1, locale)
            }
        }

        builder.appendLine("</ApiMemberCard>")
        builder.appendLine()
    }

    private fun renderTagTable(
        builder: StringBuilder,
        title: String,
        entries: List<KDocTagEntry>?,
        label: String,
        descriptionLabel: String
    ) {
        if (entries.isNullOrEmpty()) {
            return
        }
        builder.appendLine("### $title")
        builder.appendLine()
        builder.appendLine("| $label | $descriptionLabel |")
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
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("|", "\\|")
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

    private fun apiContentRoot(locale: String, defaultLocale: String): String =
        if (locale == defaultLocale) "api" else "$locale/api"

    private fun apiRouteRoot(locale: String, defaultLocale: String): String =
        if (locale == defaultLocale) "/api/" else "/$locale/api/"

    private fun sidebarFileName(locale: String, defaultLocale: String): String =
        if (locale == defaultLocale) "api-sidebar.ts" else "api-sidebar-$locale.ts"
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

    fun localized(locale: String, fallbackLocale: String): ApiPage {
        val localizedDeclarations = declarations.map { it.localized(locale, fallbackLocale) }
        return copy(
            declarations = localizedDeclarations,
            summary = localizedDeclarations.firstNotNullOfOrNull { it.docs.summary }
        )
    }

    fun localizedOutputPath(locale: String, defaultLocale: String): String =
        if (locale == defaultLocale) relativeOutputPath else "$locale/$relativeOutputPath"

    fun vitePressRoute(locale: String, defaultLocale: String): String =
        "/" + localizedOutputPath(locale, defaultLocale).removeSuffix(".md").removeSuffix("/index")
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
    }.ifBlank { name.lowercase(Locale.ROOT) }

    fun flattenedChildren(): List<ApiDeclaration> = children.flatMap { child ->
        listOf(child) + child.flattenedChildren()
    }

    fun localized(locale: String, fallbackLocale: String): ApiDeclaration = copy(
        docs = docs.localized(locale, fallbackLocale),
        children = children.map { it.localized(locale, fallbackLocale) }
    )
}

private data class ParsedKDoc(
    val summary: String?,
    val description: String,
    val localizedDescriptions: Map<String, String>,
    val tags: Map<String, List<KDocTagEntry>>
) {
    fun isEmpty(): Boolean = summary == null && description.isBlank() && localizedDescriptions.isEmpty() && tags.isEmpty()

    fun localized(locale: String, fallbackLocale: String): ParsedKDoc {
        val localizedDescription = localizedDescriptions[locale]
            ?: localizedDescriptions[fallbackLocale]
            ?: description
        val localizedTags = tags.mapValues { (_, entries) ->
            entries.map { it.localized(locale, fallbackLocale) }
                .filterNot { it.isEmpty() }
        }.filterValues { it.isNotEmpty() }
        val localizedSummary = localizedDescription
            .split(Regex("\\n\\s*\\n"))
            .firstOrNull()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return copy(
            summary = localizedSummary,
            description = localizedDescription,
            tags = localizedTags
        )
    }

    companion object {
        val EMPTY = ParsedKDoc(summary = null, description = "", localizedDescriptions = emptyMap(), tags = emptyMap())
    }
}

private data class KDocTagEntry(
    val name: String?,
    val value: String,
    val localizedValues: Map<String, String>
) {
    fun isEmpty(): Boolean = value.isBlank() && localizedValues.isEmpty()

    fun localized(locale: String, fallbackLocale: String): KDocTagEntry = copy(
        value = localizedValues[locale] ?: localizedValues[fallbackLocale] ?: value,
        localizedValues = emptyMap()
    )
}

private val LOCALIZED_KDOC_MARKER_PATTERN = Regex("%([A-Za-z][A-Za-z0-9-]*)\\s*(.*)")

private val LOCALE_CODE_PATTERN = Regex("[a-z][a-z0-9-]*")

private data class ApiDocLabels(
    val apiDocsTitle: String,
    val apiDocsIntro: String,
    val modulesTitle: String,
    val generatedFilesTitle: String,
    val apiSidebarText: String,
    val apiSuffix: String,
    val sourceRootsTitle: String,
    val pagesTitle: String,
    val parametersTitle: String,
    val parameterLabel: String,
    val descriptionLabel: String,
    val propertiesTitle: String,
    val propertyLabel: String,
    val returnsTitle: String,
    val receiverTitle: String,
    val throwsTitle: String,
    val exceptionLabel: String,
    val seeAlsoTitle: String,
    val referenceLabel: String,
    val generatedFilesIntro: (String, String) -> String,
    val moduleIntro: (String) -> String,
    val generatedFromKDoc: (String) -> String
)

private fun localeLabels(locale: String): ApiDocLabels = when (locale) {
    "zh" -> ApiDocLabels(
        apiDocsTitle = "API 文档",
        apiDocsIntro = "这些页面由 Kotlin KDoc 注释生成，可直接复制到 VitePress 文档工作区。",
        modulesTitle = "模块",
        generatedFilesTitle = "生成的 VitePress 文件",
        apiSidebarText = "API",
        apiSuffix = "API",
        sourceRootsTitle = "源码根目录",
        pagesTitle = "页面",
        parametersTitle = "参数",
        parameterLabel = "参数",
        descriptionLabel = "说明",
        propertiesTitle = "属性",
        propertyLabel = "属性",
        returnsTitle = "返回值",
        receiverTitle = "接收者",
        throwsTitle = "异常",
        exceptionLabel = "异常",
        seeAlsoTitle = "另请参阅",
        referenceLabel = "引用",
        generatedFilesIntro = { apiRoot, sidebar ->
            "复制 `build/docs/$apiRoot` 到 VitePress 内容目录，然后复制 `build/docs/.vitepress/theme` 和 `build/docs/.vitepress/$sidebar` 到 VitePress 配置目录。"
        },
        moduleIntro = { module -> "由模块 `$module` 生成。" },
        generatedFromKDoc = { sourcePath -> "由 `${sourcePath}` 中的 Kotlin KDoc 生成。" }
    )
    else -> ApiDocLabels(
        apiDocsTitle = "API Docs",
        apiDocsIntro = "These pages are generated from Kotlin KDoc comments and are ready to copy into a VitePress docs workspace.",
        modulesTitle = "Modules",
        generatedFilesTitle = "Generated VitePress Files",
        apiSidebarText = "API",
        apiSuffix = "API",
        sourceRootsTitle = "Source Roots",
        pagesTitle = "Pages",
        parametersTitle = "Parameters",
        parameterLabel = "Parameter",
        descriptionLabel = "Description",
        propertiesTitle = "Properties",
        propertyLabel = "Property",
        returnsTitle = "Returns",
        receiverTitle = "Receiver",
        throwsTitle = "Throws",
        exceptionLabel = "Exception",
        seeAlsoTitle = "See Also",
        referenceLabel = "Reference",
        generatedFilesIntro = { apiRoot, sidebar ->
            "Copy `build/docs/$apiRoot` into your VitePress content tree, then copy `build/docs/.vitepress/theme` and `build/docs/.vitepress/$sidebar` into your VitePress configuration."
        },
        moduleIntro = { module -> "Generated from module `$module`." },
        generatedFromKDoc = { sourcePath -> "Generated from Kotlin KDoc in `$sourcePath`." }
    )
}

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
.api-doc-page__module-badge[data-module='paper'] { border-color: rgba(230, 126, 34, 0.34); color: #f0a04b; background: rgba(230, 126, 34, 0.1); }

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
  align-items: center;
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
.api-member-card__pill--module[data-module='paper'] { border-color: rgba(230, 126, 34, 0.35); color: #f0a04b; background: rgba(230, 126, 34, 0.1); }

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
