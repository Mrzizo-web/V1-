package com.example.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminPinHasherTest {
    @Test
    fun pinIsStoredAsSaltedHashAndVerifies() {
        val encoded = AdminPinHasher.hash("5831")
        assertNotEquals("5831", encoded)
        assertTrue(AdminPinHasher.isHash(encoded))
        assertTrue(AdminPinHasher.verify("5831", encoded))
        assertFalse(AdminPinHasher.verify("5832", encoded))
        assertFalse(AdminPinHasher.verify("", encoded))
    }

    @Test
    fun hashesUseIndependentRandomSalts() {
        assertNotEquals(AdminPinHasher.hash("5831"), AdminPinHasher.hash("5831"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonNumericPin() {
        AdminPinHasher.hash("12ab")
    }
}
