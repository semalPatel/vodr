package com.vodr.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class VodrNavHostTest {
    @Test
    fun `nav host exposes library generate and player routes with library start destination`() {
        assertEquals(
            listOf(
                VodrNavRoutes.libraryRoute,
                VodrNavRoutes.generateRoute,
                VodrNavRoutes.playerRoute,
            ),
            VodrNavRoutes.routes,
        )
        assertEquals(VodrNavRoutes.libraryRoute, VodrNavRoutes.startDestination)
    }
}
