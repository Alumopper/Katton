package top.katton.engine

import kotlin.test.Test
import kotlin.test.assertSame

class ScriptEngineClassLoaderTest {
    @Test
    fun `unrelated context loader falls back to Katton host loader`() {
        val unrelatedLoader = object : ClassLoader(null) {}

        assertSame(
            ScriptEngine::class.java.classLoader,
            ScriptEngine.selectScriptHostClassLoader(unrelatedLoader)
        )
    }

    @Test
    fun `compatible context loader is retained`() {
        val compatibleLoader = object : ClassLoader(ScriptEngine::class.java.classLoader) {}

        assertSame(
            compatibleLoader,
            ScriptEngine.selectScriptHostClassLoader(compatibleLoader)
        )
    }
}
