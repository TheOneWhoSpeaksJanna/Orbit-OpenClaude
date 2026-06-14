# Orbit AI Workspace

Orbit AI is an Android application that serves as a powerful workspace and command center. It features an interactive chat interface backed by AI providers (such as Gemini 1.5 Pro) and a local Termux command execution layer.

Built natively in Kotlin using Jetpack Compose, the project implements Clean Architecture and follows modern Android development standards.

## Features

- **Dashboard**: Track your recent projects and AI sessions.
- **AI Workspace**: A robust chat and prompt environment to interact with large language models.
- **Provider Abstraction**: A layered design capable of bridging different AI APIs. (Currently equipped with a Gemini API provider scaffold).
- **Termux Execution Layer**: A safe, on-device terminal runner for executing local infrastructure commands or parsing scripts asynchronously.
- **Settings & Preferences**: Persisted token management and appearance options using local DataStore and Room Database.
- **Clean Architecture & M3 Design**: Code separated into UI (`presentation`), logic (`domain`), and integrations (`data`). Designed with the official Material 3 Compose library.

## Tech Stack

- **UI**: Jetpack Compose, Material 3
- **Language**: Kotlin
- **Persistence**: Room Database (SQLite), DataStore
- **Networking**: Retrofit, OkHttp, Moshi
- **Architecture**: MVVM, Clean Architecture
- **Dependency Injection**: Manual Constructor Injection (Via `AppContainer`)
- **Build System**: Gradle (Kotlin DSL properties)

## Project Initialization & Setup

### Requirements

- Android Studio Koala (or newer)
- JDK 17
- Android SDK version 34 (or newer)

### Build Steps

1. **Clone the repository**:
   ```bash
   git clone <repo-url>
   cd orbit-ai
   ```

2. **Setup Secrets (Important)**:
   In the root directory of the project, you need to create a `.env` file to contain any environment variables not committed to git.
   Copy the example file:
   ```bash
   cp .env.example .env
   ```

3. **Build via Gradle**:
   Use standard Gradle commands or sync via Android Studio:
   ```bash
   ./gradlew assembleDebug
   ```

4. **Run**:
    Install the generated `app-debug.apk` onto an emulator or Android device:
   ```bash
   ./gradlew installDebug
   ```

## Configuring AI Providers

Inside the application, open the **Settings** screen (gear icon in the top right corner of the Dashboard) to input your API Key for Gemini or other model providers.

## Architecture

The project is structured under `com.example` (with the Application ID pointing to a designated workspace name).

- `core.di`: Shared dependencies and the main application container.
- `data`:
  - `api`: Network configurations (e.g. `GeminiApiProvider.kt`)
  - `local`: Room DAOs, Entities, Databases, and Termux implementation.
  - `repository`: Single-source-of-truth implementations.
- `domain`:
  - `api`, `model`, `repository`: Interfaces and business models describing the behavior.
- `presentation`:
  - `navigation`: Route controllers.
  - `screens`: Pure Compose UI logic.
  - `viewmodels`: Architecture components tying UI actions to domain requests.
