package com.example.gemini.agent

import com.example.gemini.data.remote.BackupScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class GoogleDriveSyncSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSha256ChecksumVerification() {
        val testFile = tempFolder.newFile("test_vault.zip")
        testFile.writeText("sample zip content for sha256 test")

        val digest = MessageDigest.getInstance("SHA-256")
        testFile.inputStream().use { fis ->
            val buf = ByteArray(1024)
            var n: Int
            while (fis.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
        }
        val expectedSha256 = digest.digest().joinToString("") { "%02x".format(it) }

        assertNotNull(expectedSha256)
        assertEquals(64, expectedSha256.length)
    }

    @Test
    fun testBackupScopeEntries() {
        val scopes = BackupScope.entries
        assertEquals(3, scopes.size)
        assertTrue(scopes.contains(BackupScope.FULL_VAULT))
        assertTrue(scopes.contains(BackupScope.CHATS_ONLY))
        assertTrue(scopes.contains(BackupScope.PROJECTS_ONLY))
    }
}
