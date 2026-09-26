package top.katton.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AllowDamageEventTest {
    @Test
    fun `damage is allowed without a script handler`() {
        val allowDamage = createAll<String>()

        assertTrue(allowDamage("mob").emptyOrTrue())
    }

    @Test
    fun `damage is denied only by an explicit false handler`() {
        val allowDamage = createAll<String>()
        allowDamage += { false }

        assertFalse(allowDamage("mob").emptyOrTrue())
    }
}
