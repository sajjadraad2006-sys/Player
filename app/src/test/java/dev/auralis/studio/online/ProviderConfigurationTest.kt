package dev.auralis.studio.online

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigurationTest {
    @Test fun `Audius public read catalog works without optional rate limit key`() {
        assertTrue(AudiusClient("").isConfigured)
    }

    @Test fun `Jamendo remains disabled without client id`() {
        assertFalse(JamendoClient("").isConfigured)
    }
}
