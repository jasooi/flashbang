package com.flashbang.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun repo(file: File): SettingsRepository =
        SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `defaults - vibration on, overlay opt-in off`() = runTest {
        val repo = repo(tmp.newFolder().resolve("s1.preferences_pb"))
        assertTrue(repo.vibrationEnabled.first())
        assertFalse(repo.overlayBlockingOptedIn.first())
    }

    @Test
    fun `vibration round trip`() = runTest {
        val repo = repo(tmp.newFolder().resolve("s2.preferences_pb"))
        repo.setVibrationEnabled(false)
        assertFalse(repo.vibrationEnabledNow())
        repo.setVibrationEnabled(true)
        assertTrue(repo.vibrationEnabledNow())
    }

    @Test
    fun `overlay opt-in round trip`() = runTest {
        val repo = repo(tmp.newFolder().resolve("s3.preferences_pb"))
        repo.setOverlayBlockingOptedIn(true)
        assertTrue(repo.overlayBlockingOptedInNow())
    }
}
