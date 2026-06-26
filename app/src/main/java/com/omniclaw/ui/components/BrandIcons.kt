package com.omniclaw.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.omniclaw.R

/**
 * Official brand icons for all supported AI providers.
 * Each icon is the official SVG converted to Android VectorDrawable.
 */
object BrandIcons {

    @Composable
    fun Claude(): ImageVector = vectorResource(R.drawable.ic_claude)

    @Composable
    fun OpenAI(): ImageVector = vectorResource(R.drawable.ic_openai)

    @Composable
    fun Gemini(): ImageVector = vectorResource(R.drawable.ic_gemini)

    @Composable
    fun OpenRouter(): ImageVector = vectorResource(R.drawable.ic_openrouter)

    @Composable
    fun DeepSeek(): ImageVector = vectorResource(R.drawable.ic_deepseek)

    @Composable
    fun Groq(): ImageVector = vectorResource(R.drawable.ic_groq)

    @Composable
    fun Ollama(): ImageVector = vectorResource(R.drawable.ic_ollama)

    @Composable
    fun GitHub(): ImageVector = vectorResource(R.drawable.ic_github)
}
