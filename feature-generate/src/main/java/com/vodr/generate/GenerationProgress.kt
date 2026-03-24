package com.vodr.generate

enum class GenerationPhase(
    val progress: Float,
    val title: String,
    val description: String,
) {
    IDLE(
        progress = 0f,
        title = "Ready",
        description = "Choose a book and generation mode to prepare a listening queue.",
    ),
    PREPARING_INPUT(
        progress = 0.1f,
        title = "Preparing document",
        description = "Opening the source file and validating the import.",
    ),
    PARSING_DOCUMENT(
        progress = 0.3f,
        title = "Parsing text",
        description = "Extracting readable text from the selected book.",
    ),
    SEGMENTING_CONTENT(
        progress = 0.5f,
        title = "Segmenting chapters",
        description = "Splitting the book into chapter-sized listening units.",
    ),
    RESOLVING_PROVIDERS(
        progress = 0.7f,
        title = "Checking providers",
        description = "Resolving the best available AI path without breaking offline mode.",
    ),
    BUILDING_QUEUE(
        progress = 0.9f,
        title = "Building playback",
        description = "Generating chapter titles and the final listening queue.",
    ),
    READY(
        progress = 1f,
        title = "Ready to play",
        description = "Your book is prepared and can open directly in the player.",
    )
}
