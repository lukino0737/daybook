package dev.lukino.daybook

import android.app.NotificationManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Uses platform APIs only so the same test APK can seed the actual v0.5 release. */
class V06UpgradeTest {
    @Test fun actualV05DataAndAppearanceSurviveV06() {
        val args = InstrumentationRegistry.getArguments()
        val mode = args.getString("daybookUpgrade")
        assumeTrue(mode == "seed" || mode == "verify")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(args.getString("expectedVersion"), context.packageManager.getPackageInfo(context.packageName, 0).versionName)
        val snapshot = File(context.filesDir, "v06-upgrade-evidence.json")
        val entries = "id,kind,title,note,date,time,completed,createdAt,updatedAt,tags,reminderAt,reminderDeliveredFor"
        val memos = "id,body,date,reminderAt,reminderDeliveredFor,createdAt,updatedAt"
        val ids = (1..4).map { "00000000-0000-0000-0000-00000000060$it" }
        SQLiteDatabase.openDatabase(context.getDatabasePath("daybook.db").path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            fun rows(table: String, columns: String): String = db.rawQuery("SELECT $columns FROM $table ORDER BY id", null).use { cursor ->
                val result = JSONArray()
                while (cursor.moveToNext()) {
                    val row = JSONArray()
                    for (i in 0 until cursor.columnCount) row.put(if (cursor.isNull(i)) JSONObject.NULL else cursor.getString(i))
                    result.put(row)
                }
                result.toString()
            }
            fun appearance(): String = File(context.filesDir, "appearance").walkTopDown().filter { it.isFile }.sortedBy { it.name }
                .joinToString("|") { it.name + ":" + MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { b -> "%02x".format(b) } }
            fun channels(): String = context.getSystemService(NotificationManager::class.java).notificationChannels.sortedBy { it.id }
                .joinToString("|") { listOf(it.id, it.importance, it.sound, it.shouldVibrate(), it.vibrationPattern?.joinToString(",")).joinToString(":") }
            if (mode == "seed") {
                assertEquals(4, db.version)
                assertFalse("Keep an unfinished upgrade snapshot", snapshot.exists())
                db.beginTransaction()
                try {
                    listOf("TASK", "EVENT", "NOTE").forEachIndexed { index, kind ->
                        db.execSQL("INSERT INTO entries ($entries) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            arrayOf(ids[index], kind, "v06升级验收$kind", "虚构正文\n保留换行", "2099-12-31", "18:30", if (index == 0) 1 else 0,
                                1000, 2000, "[\"验收\"]", if (index == 0) "2099-12-30T09:00" else null, if (index == 0) "2099-12-30T09:00" else null))
                    }
                    db.execSQL("INSERT INTO memos ($memos) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(ids[3], "v06升级验收便签\n虚构样例", "2099-12-31", "2099-12-31T09:00", null, 3000, 4000))
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                snapshot.writeText(JSONObject().put("entries", rows("entries", entries)).put("memos", rows("memos", memos))
                    .put("appearance", appearance()).put("channels", channels()).toString())
            } else {
                // Opening the release application initializes Room and runs its real migration.
                val app = context.applicationContext as DaybookApplication
                kotlinx.coroutines.runBlocking { app.repository.all() }
                assertEquals(6, db.version)
                val expected = JSONObject(snapshot.readText())
                assertEquals(expected.getString("entries"), rows("entries", entries))
                assertEquals(expected.getString("memos"), rows("memos", memos))
                assertEquals(expected.getString("appearance"), appearance())
                assertEquals(expected.getString("channels"), channels())
                db.rawQuery("SELECT COUNT(*) FROM reminders", null).use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
                for (table in listOf("entries", "memos")) db.rawQuery("SELECT COUNT(*) FROM $table WHERE blocks != '[]'", null).use {
                    it.moveToFirst(); assertEquals(0, it.getInt(0))
                }
                ids.take(3).forEach { db.delete("entries", "id = ?", arrayOf(it)) }
                db.delete("memos", "id = ?", arrayOf(ids[3]))
                assertTrue(snapshot.delete())
            }
        }
    }
}
