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

    @Test
    fun `release qualifiers and build metadata follow expected precedence`() {
        assertTrue(VersionConstraint.matches("1.0.0", ">1.0.0-rc1"))
        assertTrue(VersionConstraint.matches("1.0.0-final", "1.0.0"))
        assertTrue(VersionConstraint.matches("1.0.0+build.42", "1.0.0"))
        assertFalse(VersionConstraint.matches("1.0.0-beta", ">=1.0.0"))
    }

    @Test
    fun `empty alternatives cannot turn an invalid constraint into a wildcard`() {
        assertFalse(VersionConstraint.matches("1.0.0", ">=2.0.0 ||"))
        assertFalse(VersionConstraint.matches("1.0.0", ","))
    }
}
