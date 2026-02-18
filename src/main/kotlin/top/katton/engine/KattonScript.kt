package top.katton.engine

import net.minecraft.resources.Identifier
import top.katton.Katton
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript

/**
 * KattonScript represents a compiled script, containing the dependencies of the script.
 *
 */
data class KattonScript(

    /**
     * the compiled script to be executed
     */
    val script: KJvmCompiledScript,

    /**
     * identifier stored in minecraft' resource system
     */
    val identifier: String,

    val sourceCode: SourceCode,

    /**
     * Dependence scripts that need to be imported before executing this script
     */
    val dependencies: Set<KattonScript> = setOf(),

    /**
     * Raw import statements in this script
     */
    val rawImport: Set<String> = setOfNotNull(),

    /**
     * Exported members of the script
     */
    var exported: MutableSet<String> = mutableSetOf(),

    /**
     * The package of the script main class. Null if the class is in the default package.
     */
    var scriptPackage: String? = null,

    /**
     * The internal name of the script main class. Normally xxx_kt.
     */
    var scriptMainClassInternalName: String = ""
)