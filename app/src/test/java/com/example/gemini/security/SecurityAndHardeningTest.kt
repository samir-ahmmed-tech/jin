package com.example.gemini.security

import com.example.gemini.data.ssh.SshConnectionStatus
import com.example.gemini.data.ssh.TermuxSshManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecurityAndHardeningTest {

    @Test
    fun testNetworkSecurityConfigExistsAndRestrictsCleartext() {
        val candidatePaths = listOf(
            File("app/src/main/res/xml/network_security_config.xml"),
            File("src/main/res/xml/network_security_config.xml")
        )
        val file = candidatePaths.firstOrNull { it.exists() }
        assertTrue("network_security_config.xml must exist in res/xml", file != null && file.exists())
        val content = file!!.readText()
        assertTrue("Default base-config must disallow cleartext traffic", content.contains("cleartextTrafficPermitted=\"false\""))
        assertTrue("Domain config must allow localhost for local bridge/daemons", content.contains("localhost"))
        assertTrue("Domain config must allow 127.0.0.1 for local bridge/daemons", content.contains("127.0.0.1"))
    }

    @Test
    fun testSshManagerDisconnectResetsStatus() {
        TermuxSshManager.disconnect()
        assertEquals(SshConnectionStatus.DISCONNECTED, TermuxSshManager.connectionStatus.value)
    }
}
