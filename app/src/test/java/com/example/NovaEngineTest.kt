package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ActionType
import com.example.data.model.ToolStatus
import com.example.domain.assistant.NovaEngine
import com.example.domain.tool.PhoneToolsManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NovaEngineTest {

    private lateinit var toolsManager: PhoneToolsManager
    private lateinit var engine: NovaEngine

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        toolsManager = PhoneToolsManager(context)
        engine = NovaEngine(toolsManager)
    }

    @Test
    fun testOpenYouTubeVariants() = runBlocking {
        val variants = listOf("Open YouTube", "YT kholo", "YouTube open koro", "YouTube चालू করো", "Launch YouTube")
        for (cmd in variants) {
            val response = engine.processUserCommand(cmd, null, emptyList())
            assertNotNull("Response should not be null for $cmd", response.toolResult)
            assertTrue("Should target YouTube for $cmd", response.replyText.contains("YouTube", ignoreCase = true))
        }
    }

    @Test
    fun testOpenWhatsAppVariants() = runBlocking {
        val variants = listOf("WhatsApp kholo", "WhatsApp khol", "Open WhatsApp")
        for (cmd in variants) {
            val response = engine.processUserCommand(cmd, null, emptyList())
            assertNotNull("Response should not be null for $cmd", response.toolResult)
            assertTrue("Should target WhatsApp for $cmd", response.replyText.contains("WhatsApp", ignoreCase = true))
        }
    }

    @Test
    fun testSilentModeVariants() = runBlocking {
        val variants = listOf("Phone silent koro", "Put phone on silent", "Silent mode on", "Phone ta silent kore dao")
        for (cmd in variants) {
            val response = engine.processUserCommand(cmd, null, emptyList())
            assertNotNull("Response should not be null for $cmd", response.toolResult)
            assertEquals("Silent Mode", response.toolResult?.toolName)
        }
    }

    @Test
    fun testFlashlightVariants() = runBlocking {
        val response = engine.processUserCommand("Light on kor", null, emptyList())
        assertNotNull(response.toolResult)
        assertEquals("Flashlight", response.toolResult?.toolName)
    }

    @Test
    fun testBrightness50Percent() = runBlocking {
        val response = engine.processUserCommand("Brightness 50% kore dao", null, emptyList())
        assertNotNull(response.toolResult)
        assertEquals("Done.", response.replyText)
        assertEquals("Brightness", response.toolResult?.toolName)
    }

    @Test
    fun testConsequentialWhatsAppMessageRequiresConfirmation() = runBlocking {
        val cmd = "Rahul ke WhatsApp e message koro ami 10 minute e ashchi"
        val response = engine.processUserCommand(cmd, null, emptyList())
        assertNotNull("Must generate pending confirmation", response.pendingConfirmation)
        assertEquals(ActionType.SEND_WHATSAPP, response.pendingConfirmation?.actionType)
        assertEquals("Rahul", response.pendingConfirmation?.recipient)
        assertTrue(response.pendingConfirmation?.content?.contains("ami 10 minute e ashchi") == true)
    }

    @Test
    fun testConsequentialPhoneCallRequiresConfirmation() = runBlocking {
        val cmd = "Call Rahul"
        val response = engine.processUserCommand(cmd, null, emptyList())
        assertNotNull("Must generate pending confirmation for phone call", response.pendingConfirmation)
        assertEquals(ActionType.MAKE_CALL, response.pendingConfirmation?.actionType)
        assertEquals("Rahul", response.pendingConfirmation?.recipient)
    }

    @Test
    fun testPowerOffShutdownPermissionFallback() = runBlocking {
        val variants = listOf("Turn off my phone.", "Phone off koro.", "Shutdown my phone.", "Restart my phone.")
        for (cmd in variants) {
            val response = engine.processUserCommand(cmd, null, emptyList())
            assertEquals(
                "I don't have permission to power off the phone directly. I can open the power menu/settings for you.",
                response.replyText
            )
        }
    }

    @Test
    fun testMultiStepYouTubeAndMusic() = runBlocking {
        val cmd = "Open YouTube and play music."
        val response = engine.processUserCommand(cmd, null, emptyList())
        assertNotNull(response.toolResult)
        assertEquals(ToolStatus.EXECUTED, response.toolResult?.status)
        assertTrue(response.replyText.contains("YouTube is open"))
    }

    @Test
    fun testBengaliVolumeCommands() = runBlocking {
        val cmd = "ভলিউম কমিয়ে দাও"
        val response = engine.processUserCommand(cmd, null, emptyList())
        assertNotNull(response.toolResult)
        assertEquals("Volume", response.toolResult?.toolName)
    }

    @Test
    fun testQuickSettingsAndDisplaySettings() = runBlocking {
        val qsResponse = engine.processUserCommand("Quick Settings kholo", null, emptyList())
        assertNotNull(qsResponse.toolResult)
        assertEquals("Quick Settings", qsResponse.toolResult?.toolName)

        val dsResponse = engine.processUserCommand("Display Settings kholo", null, emptyList())
        assertNotNull(dsResponse.toolResult)
        assertEquals("Display Settings", dsResponse.toolResult?.toolName)
    }

    @Test
    fun testWhatsAppMessagePathao() = runBlocking {
        val response = engine.processUserCommand("WhatsApp message pathao", null, emptyList())
        assertNotNull(response.pendingConfirmation)
        assertEquals(ActionType.SEND_WHATSAPP, response.pendingConfirmation?.actionType)
    }
}
