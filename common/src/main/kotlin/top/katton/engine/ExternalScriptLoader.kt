package top.katton.engine

import top.katton.api.LOGGER
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

/**
 * Loads script files from the game runtime directory.
 *
 * Expected structure:
 * <gameDir>/scripts/<pack>/server_scripts/**/*.kt
 * <gameDir>/scripts/<pack>/client_scripts/**/*.kt
 */
object ExternalScriptLoader {

    private val SCRIPT_DIR = "scripts"
    private val SERVER_SCRIPT = "server_scripts"
    private val CLIENT_SCRIPT = "client_scripts"

    /**
     * Collects all server script file paths from the given game directory.
     */
    @JvmStatic
    fun collectServerScripts(gameDir: Path?): List<String> {
        return collectScripts(gameDir, SERVER_SCRIPT, "server")
    }

    /**
     * Collects all client script file paths from the given game directory.
     *
     * Expected structure: <gameDir>/scripts/<pack>/client_scripts/.kt
     */
    @JvmStatic
    fun collectClientScripts(gameDir: Path?): List<String> {
        return collectScripts(gameDir, CLIENT_SCRIPT, "client")
    }

    private fun collectScripts(gameDir: Path?, subfolder: String, label: String): List<String> {
        if (gameDir == null) return emptyList()

        val scriptsRoot = gameDir.resolve(SCRIPT_DIR)
        if (!Files.isDirectory(scriptsRoot)) return emptyList()

        val result = mutableListOf<String>()

        Files.list(scriptsRoot).use { namespaces ->
            namespaces
                .filter { Files.isDirectory(it) }
                .forEach { namespaceDir ->
                    val scriptSubDir = namespaceDir.resolve(subfolder)
                    if (!Files.isDirectory(scriptSubDir)) return@forEach

                    Files.walk(scriptSubDir).use { files ->
                        val matched = files
                            .filter { Files.isRegularFile(it) }
                            .filter { it.fileName.toString().endsWith(".kt") }
                            .map { it.toAbsolutePath().normalize().toString() }
                            .sorted()
                            .collect(Collectors.toList())
                        result.addAll(matched)
                    }
                }
        }

        if (result.isNotEmpty()) {
            LOGGER.info("Discovered {} external {} scripts from runtime scripts folder", result.size, label)
        }
        return result
    }
}
