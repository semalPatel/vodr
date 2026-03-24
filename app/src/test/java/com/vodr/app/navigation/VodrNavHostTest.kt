package com.vodr.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class VodrNavHostTest {
    @Test
    fun `nav host exposes library generate player and settings routes with library start destination`() {
        assertEquals(
            listOf(
                VodrRoute.Library.route,
                VodrRoute.Generate.route,
                VodrRoute.Player.route,
                VodrRoute.Settings.route,
            ),
            VodrNavRoutes.routes,
        )
        assertEquals(VodrRoute.Library.route, VodrNavRoutes.startDestination)
    }
}
