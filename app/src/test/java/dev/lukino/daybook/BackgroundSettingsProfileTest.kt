package dev.lukino.daybook

import dev.lukino.daybook.reminder.BackgroundSettingsProfile
import org.junit.Assert.*
import org.junit.Test

class BackgroundSettingsProfileTest {
    @Test fun knownFamiliesHaveGuidanceIncludingSubBrands() {
        for (brand in listOf("Xiaomi", "Redmi", "POCO", "Huawei", "Honor", "OPPO", "OnePlus", "realme", "vivo", "iQOO", "Samsung")) {
            val profile = BackgroundSettingsProfile.forDevice("unknown", brand)
            assertTrue(brand, profile.instruction.contains("Daybook"))
            if (brand != "Samsung") assertTrue(brand, profile.components.isNotEmpty())
        }
    }
    @Test fun honorBrandTakesPriorityOverLegacyHuaweiManufacturer() {
        val profile = BackgroundSettingsProfile.forDevice("HUAWEI", "HONOR")
        assertEquals("com.hihonor.systemmanager", profile.components.first().first)
    }
    @Test fun samsungAndUnknownDevicesDoNotRequireAnAutostartSwitch() {
        val samsung = BackgroundSettingsProfile.forDevice("Samsung", "Samsung")
        assertTrue(samsung.instruction.contains("休眠"))
        assertTrue(samsung.components.isEmpty())
        val generic = BackgroundSettingsProfile.forDevice("Google", "Pixel")
        assertTrue(generic.instruction.contains("若手机提供"))
        assertTrue(generic.components.isEmpty())
    }
}
