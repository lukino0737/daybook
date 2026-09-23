package dev.lukino.daybook

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.lukino.daybook.ai.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AiSettingsTest {
    @Test fun keyIsEncryptedReopensAndCanBeReplacedOrDeleted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "ai-test-${UUID.randomUUID()}"
        val store = AiSettingsStore(context, name)
        val key = "fictional-not-a-real-key"
        try {
            assertFalse(store.config().configured)
            store.save(key, "deepseek-flash", false)
            assertTrue(store.config().configured)
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            assertTrue(prefs.all.values.none { it.toString().contains(key) })
            assertEquals(key, AiSettingsStore(context, name).key())
            store.save(null, "deepseek-v4-pro", true)
            assertEquals(key, store.key()); assertTrue(store.config().thinking)
            store.save("another-fictional-key", "deepseek-flash", false)
            assertEquals("another-fictional-key", store.key())
            store.delete()
            assertEquals("", store.key()); assertFalse(store.config().configured)
        } finally { store.delete(); context.deleteSharedPreferences(name) }
    }
}
