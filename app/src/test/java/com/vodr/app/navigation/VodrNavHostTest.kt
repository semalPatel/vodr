package com.vodr.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class VodrNavHostTest {
    @Test
    fun `nav host exposes library generate player and settings routes with library start destination`() {
        assertEquals(
            listOf(
                VodrNavRoutes.libraryRoute,
                VodrNavRoutes.generateRoute,
                VodrNavRoutes.playerRoute,
                VodrNavRoutes.settingsRoute,
            ),
            VodrNavRoutes.routes,
        )
        assertEquals(VodrNavRoutes.libraryRoute, VodrNavRoutes.startDestination)
    }
}
