# Java Compiler Integration for Script Packs

## Goal
Allow `.java` files in Katton script packs to be compiled and available for Kotlin scripts to import, enabling use of Java utility classes alongside `.kt` script code with hot-reload support.

## Constraints
- **Unidirectional only**: Java classes may be imported by Kotlin scripts, but Java classes must NOT reference Kotlin script classes. This eliminates the two-pass compilation requirement.
- Java compilation uses `javax.tools.JavaCompiler` (JDK built-in, no extra dependency).
- Compiled Java classes are packaged into an in-memory or temp `.jar` and added to the Kotlin compilation classpath.
- Hot reload must clear Java compilation cache along with Kotlin source cache.

## Design

### Compilation Flow
```
ScriptPackManager.collectScriptFiles()
  → collects .kt files (existing)
  → collects .java files (new)

ScriptEngine.buildSourceCompilationPlan()
  → compileJavaSources(javaFiles, gameClasspath) → temp jar
  → add temp jar to classpathJars
  → existing Kotlin compilation proceeds as before
```

### Key Implementation Points

1. **ScriptPackManager** — add `.java` file collection alongside `.kt`
   - `collectScriptFiles()` or new `collectJavaFiles()` walks directory for `*.java`

2. **ScriptEngine** — compile Java before Kotlin
   ```kotlin
   private fun compileJavaSources(
       javaFiles: List<Path>,
       classpathJars: List<Path>
   ): Path? {
       val compiler = ToolProvider.getSystemJavaCompiler()
       val tempDir = Files.createTempDirectory("katton-java-")
       val options = listOf(
           "-classpath", classpathJars.joinToString(File.pathSeparator),
           "-d", tempDir.toString()
       )
       val units = fileManager.getJavaFileObjectsFromPaths(javaFiles)
       val task = compiler.getTask(null, fileManager, null, options, null, units)
       if (!task.call()) return null
       return jarPack(tempDir)  // pack .class files into a jar
   }
   ```

3. **ClassLoader consistency** — satisfied automatically because:
   - Kotlin's `importScripts` + `updateClasspath` resolves the temp jar at compile time
   - `KJvmCompiledScript.getClass()` uses a ClassLoader that includes all classpath entries
   - Java classes are on the same classpath as Kotlin-compiled classes

4. **Hot reload** — temp jar directory cleared on `beginReload()`

### What Not to Do
- Do NOT try to discover `@ServerScriptEntrypoint` in Java classes (irrelevant for unidirectional)
- Do NOT attempt two-pass compilation (Java → Kotlin → Java)
- Do NOT persist Java compilation artifacts to the cache directory (temp folder is fine for reload semantics)

### Files to Modify
| File | Change |
|------|--------|
| `common/.../pack/ScriptPackManager.kt` | Add Java file collection |
| `common/.../pack/ScriptPackTypes.kt` | Add `javaFiles` field to `ScriptPack` data class |
| `common/.../engine/ScriptEngine.kt` | Add `compileJavaSources()`, integrate into `buildSourceCompilationPlan()` |
| `common/.../pack/ScriptPackManager.kt` | SHA-256 hash must include Java files |

### Estimated Effort
- Core implementation: ~100 lines of Kotlin
- Testing: manual verification with a simple Java helper class
