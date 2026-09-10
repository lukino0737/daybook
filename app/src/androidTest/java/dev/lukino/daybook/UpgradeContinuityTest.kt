package dev.lukino.daybook

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run twice, with a same-signature package replacement between seed and verify. */
class UpgradeContinuityTest {
    @Test fun recordSurvivesPackageReplacement() = runBlocking {
        val mode = InstrumentationRegistry.getArguments().getString("daybookUpgrade")
        assumeTrue("Only runs as the explicit upgrade check", mode == "seed" || mode == "verify")
        val app = ApplicationProvider.getApplicationContext<DaybookApplication>()
        val fixture = Entry(id = "00000000-0000-0000-0000-000000000101", kind = EntryKind.TASK,
            title = "覆盖安装验收样例", note = "仅测试使用，验证后删除", date = "2099-12-31", time = "18:30",
            completed = true, createdAt = 1000, updatedAt = 2000)
        if (mode == "seed") app.repository.save(fixture)
        else {
            assertEquals(fixture, app.repository.all().single { it.id == fixture.id })
            app.repository.delete(fixture.id)
        }
    }
}
