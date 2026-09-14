package dev.lukino.daybook

import android.Manifest
import dev.lukino.daybook.ui.*
import org.junit.Assert.*
import org.junit.Test

class PhotoAccessTest {
    @Test fun permissionsMatchEachPlatformAndOnlyRequestImages() {
        assertArrayEquals(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), photoPermissions(26))
        assertArrayEquals(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), photoPermissions(32))
        assertArrayEquals(arrayOf(Manifest.permission.READ_MEDIA_IMAGES), photoPermissions(33))
        assertArrayEquals(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED), photoPermissions(34))
    }
    @Test fun accessHandlesFullPartialDeniedAndRevokedPermissions() {
        for (sdk in listOf(26, 32, 33, 34, 36)) {
            val grants = mutableSetOf<String>()
            assertEquals(PhotoAccess.DENIED, photoAccess(sdk, grants::contains))
            grants += photoPermissions(sdk)[0]
            assertEquals(PhotoAccess.FULL, photoAccess(sdk, grants::contains))
            grants.clear()
            grants += Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            assertEquals(if (sdk >= 34) PhotoAccess.PARTIAL else PhotoAccess.DENIED, photoAccess(sdk, grants::contains))
            grants.clear()
            assertEquals(PhotoAccess.DENIED, photoAccess(sdk, grants::contains))
        }
    }
}
