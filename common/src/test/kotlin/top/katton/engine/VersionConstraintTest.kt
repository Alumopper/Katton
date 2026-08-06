package top.katton.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionConstraintTest {
    @Test
    fun `matches exact comparison range and alternatives`() {
        assertTrue(VersionConstraint.matches("1.7.0", "*"))
        assertTrue(VersionConstraint.matches("1.7.0", "1.7.0"))
        assertTrue(VersionConstraint.matches("2.3.1", ">=2.0 <3.0"))
        assertTrue(VersionConstraint.matches("6.0.0-beta", "<6.0.0 || >=7.0"))
        assertFalse(VersionConstraint.matches("1.6.9", ">=1.7.0"))
    }
}
