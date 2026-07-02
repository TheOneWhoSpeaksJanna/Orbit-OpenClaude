# Orbit-AI

On-device Android workspace for AI agents. Self-contained runtime — no Termux required.

## Features

- **Self-contained POSIX runtime** — BusyBox bundled in APK (~1MB) provides `sh`, `cp`, `mv`, `tar`, `grep`, `wget`, and 40+ other tools without needing Termux.
- **Multiple agent presets** — Choose from different AI agent configurations shipped as APK flavors: `normal`, `opencode`, `openclaude`, `claudecode`, `codex`. Each with its own app label and agent defaults.
- **Agent installation from assets or GitHub** — Agents are bundled as tarballs in APK assets or downloaded from GitHub Releases on first setup.
- **Package manager** — Built-in package installer downloads and manages runtimes (node, python, git) with registry-based tracking.
- **Local command execution** — Agents run locally on-device via a shell-based command runner with isolated runtime environment.
- **7 supported AI providers** — Claude, OpenAI, Gemini, OpenRouter, DeepSeek, Groq, and Ollama (local). Configure API keys in-app; all endpoints are centralized in `core/config/ApiConfig.kt`.
- **Optional Shizuku elevation** — Root-level commands via Shizuku when available.
- **Auto-updates** — In-app update system that checks GitHub Releases for new APKs and installs them via FileProvider.
- **Logging** — All logs written to `/storage/emulated/0/omniclaw_logs/` (or app-private fallback); crashes also saved to `Downloads/omniclaw_logs/`.

## APK Flavors

The project builds 5 product flavors, each targeting a different agent ecosystem:

| Flavor       | App Label        | Agent Preset   | Fallback GitHub repo |
|--------------|------------------|----------------|----------------------|
| `normal`     | Orbit AI         | Default        | `Gitlawb/openclaude` |
| `opencode`   | OpenCode         | OpenCode       | (npm only — no fallback) |
| `openclaude` | OpenClaude       | OpenClaude     | `Gitlawb/openclaude` |
| `claudecode` | Claude Code      | Claude Code    | (npm only — no fallback) |
| `codex`      | Codex            | Codex          | (npm only — no fallback) |

Build a specific flavor:
```bash
./gradlew assembleOpenclaudeDebug
./gradlew assembleOpenclaudeRelease
```

The CI workflow builds all 5 flavors and creates a GitHub Release with 5 APK artifacts.

## Configurable Build Constants

The following Gradle project properties override baked-in defaults — set them
in `~/.gradle/gradle.properties` (per-user) or via `-P<key>=<value>` (per-build)
without editing source:

| Property | Default | Description |
|----------|---------|-------------|
| `orbit.openRouterReferrerUrl` | `https://github.com/TheOneWhoSpeaksJanna/Orbit-AI` | Sent as `HTTP-Referer` to OpenRouter |
| `orbit.openRouterAppTitle`    | `Orbit AI` | Sent as `X-Title` to OpenRouter |
| `orbit.agentFallbackRepoUrl`  | `https://github.com/Gitlawb/openclaude.git` | GitHub repo used as fallback when APK assets don't bundle an agent tarball |

Per-flavor overrides for `AGENT_FALLBACK_REPO_URL` are set in `app/build.gradle.kts`.

## Architecture

```
app/src/main/java/com/omniclaw/
├── core/
│   ├── config/        # FlavorConfig, ApiConfig (centralized API URLs/endpoints)
│   ├── logging/       # FileLogger (writes to omniclaw_logs/)
│   └── di/            # Dependency injection
├── ui/
│   ├── screens/       # Compose UI screens (Chat, Setup, Settings, Dashboard, Providers)
│   └── viewmodels/    # ViewModels for reactive state
├── data/
│   ├── api/
│   │   ├── providers/ # One provider class per AI service (Claude, OpenAI, Gemini, OpenRouter, DeepSeek, Groq, Ollama)
│   │   └── tools/     # Tool-call implementations
│   ├── local/
│   │   ├── runtime/   # PackageInstaller, OrbitRuntimeManager (BusyBox bootstrap)
│   │   ├── runner/    # LocalCommandRunner (sh-based execution)
│   │   ├── entity/    # Room entities
│   │   └── dao/       # Room DAOs
│   └── repository/    # Repository implementations
└── domain/            # Domain models, interfaces
```

The bundled package registry lives at `app/src/main/assets/packages.default.json`
and is copied to `orbit_runtime/registry/packages.json` on first launch. Edit
the runtime copy to pin newer package versions without an app update.

## Runtime

Orbit-AI does **not** depend on Termux. The app includes a BusyBox ARM64 binary extracted at setup time, providing a POSIX environment with `sh`, `cp`, `mv`, `tar`, `grep`, `wget`, `sed`, `awk`, and more. The runtime directory lives at:

```
/data/data/<package>/files/orbit_runtime/
├── bin/          # BusyBox + wrappers + agent entry points
├── tmp/
├── packages/     # Installed runtimes (node, python, git, ...)
├── downloads/
├── agents/       # Extracted agent code
├── logs/
├── registry/     # packages.json (editable runtime copy of the bundled registry)
└── environments/
```

PATH is automatically set to include `orbit_runtime/bin/` for all local command execution. System shell + chmod paths are resolved via `ANDROID_ROOT` so the app works on custom ROMs that mount `/system` elsewhere.

## Setup Flow

1. App launches → Setup Wizard
2. User configures agent type and API keys (if using API-based agents)
3. For local agents: BusyBox is installed from APK assets → agent tarball is extracted or downloaded
4. Agent wrapper scripts are created in `orbit_runtime/bin/`
5. Chat interface opens — messages are piped to the local agent process

## Logging

Logs are written to `/storage/emulated/0/omniclaw_logs/` when "All files access" is granted (Settings → Apps → Orbit-AI → All files access). Without this permission, logs fall back to the app's private external files directory.

- Daily log files: `app_YYYY-MM-DD.log`
- Crash reports: `crash_YYYYMMDD_HHmmss.log`
- Old logs are auto-cleaned (7 daily logs, 10 crash reports retained)

## Building

Prerequisites: Android SDK, JDK 17+, Gradle (wrapped).

```bash
git clone https://github.com/TheOneWhoSpeaksJanna/Orbit-AI.git
cd Orbit-AI
./gradlew assembleNormalDebug
```

For a release build, configure signing in `app/build.gradle.kts` or use CI.

## CI/CD

The GitHub Actions workflow (`.github/workflows/build.yml`):
- Builds all 5 flavors on push to `main` and on pull requests
- Runs unit tests on pull requests
- Uploads APKs as CI artifacts (30-day retention)
- Auto-creates a GitHub Release with all APKs on push to `main`

## Security Notes

- API keys (Claude, OpenAI, Gemini, OpenRouter, DeepSeek, Groq) are stored in user preferences — not hardcoded. Ollama uses no auth; its key slot stores an optional base URL.
- All AI provider URLs live in `core/config/ApiConfig.kt` — no scattered URL string literals.
- Agent code runs in-app with the same UID as the app itself.
- BusyBox wrappers use `#!/system/bin/sh` shebang (resolved via `ANDROID_ROOT`) to avoid external shell dependencies.
- Fork-friendly build constants (`orbit.*` Gradle properties) let you rebrand without touching source.
