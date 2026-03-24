package com.vodr.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryViewModelTest {
    @Test
    fun reportUnsupportedSelection_setsUserVisibleError() {
        val viewModel = LibraryViewModel(
            importDocumentUseCase = ImportDocumentUseCase(
                repository = InMemoryDocumentMetadataRepository(),
            ),
        )

        viewModel.reportUnsupportedSelection()

        assertEquals(
            "Unsupported file. Please select a PDF or EPUB document.",
            viewModel.state.value.errorMessage,
        )
    }
}
