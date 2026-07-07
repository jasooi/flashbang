package com.flashbang.ui.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsTest {

    @Test
    fun `notification permission not required before api 33`() {
        assertFalse(Permissions.notificationPermissionRequired(sdkInt = 26))
        assertFalse(Permissions.notificationPermissionRequired(sdkInt = 32))
    }

    @Test
    fun `notification permission required from api 33`() {
        assertTrue(Permissions.notificationPermissionRequired(sdkInt = 33))
        assertTrue(Permissions.notificationPermissionRequired(sdkInt = 35))
    }
}
