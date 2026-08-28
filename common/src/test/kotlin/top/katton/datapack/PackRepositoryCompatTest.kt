package top.katton.datapack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackRepositoryCompatTest {
    @Test
    fun `uses vanilla single argument signature`() {
        val repository = VanillaStyleRepository()

        PackRepositoryCompat.setSelected(repository, listOf("vanilla", "katton"))

        assertEquals(listOf("vanilla", "katton"), repository.capturedIds)
    }

    @Test
    fun `uses Paper overload and retains required packs`() {
        val repository = PaperStyleRepository()

        PackRepositoryCompat.setSelected(repository, listOf("vanilla", "katton"))

        assertEquals(listOf("vanilla", "katton"), repository.capturedIds)
        assertTrue(repository.includeRequired)
    }
}

class VanillaStyleRepository {
    var capturedIds: Collection<String> = emptyList()

    fun setSelected(ids: Collection<String>) {
        capturedIds = ids
    }
}

class PaperStyleRepository {
    var capturedIds: Collection<String> = emptyList()
    var includeRequired: Boolean = false

    fun setSelected(ids: Collection<String>, includeRequired: Boolean) {
        capturedIds = ids
        this.includeRequired = includeRequired
    }
}
