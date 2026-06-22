package com.omniclaw.domain.models

data class DetailedModelInfo(
    val id: String,
    val name: String,
    val promptPrice: String,
    val completionPrice: String,
    val contextLength: Long
) {
    val formattedPromptPrice: String
        get() = if (promptPrice == "0" || promptPrice.toDoubleOrNull() == 0.0) "Free" else "$${promptPrice}/1M tokens"

    val formattedCompletionPrice: String
        get() = if (completionPrice == "0" || completionPrice.toDoubleOrNull() == 0.0) "Free" else "$${completionPrice}/1M tokens"

    val contextDisplay: String
        get() = when {
            contextLength >= 1_000_000 -> "${contextLength / 1000}K"
            contextLength >= 1000 -> "${contextLength / 1000}K"
            else -> "$contextLength"
        }
}
