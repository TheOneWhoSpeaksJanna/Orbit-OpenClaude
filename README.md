# Orbit-AI

A portable Android app that turns AI coding agents (OpenClaude, OpenCode, Claude Code, Codex) into pocket-sized, on-device tools. No Termux, no root, no laptop required.

## How it works

Orbit-AI bundles a **complete Alpine Linux environment** inside the APK and runs it via **PRoot** — a user-space `chroot` replacement that needs no root privileges. Agents run inside this Linux environment where they have access to `node`, `npm`, `git`, `gh`, and a standard filesystem layout.

```
┌─────────────────────────────────────────┐
│            Orbit-AI APK                  │
│                                         │
│  ┌─────────────┐  ┌──────────────────┐  │
│  │  Jetpack     │  │  libproot.so     │  │
│  │  Compose UI  │  │  (402KB static)   │  │
│  │  (the app)   │  └────────┬─────────┘  │
│  └──────┬───────┘           │            │
│         │                   ▼            │
│  ┌──────▼───────────────────────────┐    │
│  │     PRoot Alpine Linux           │    │
│  │  ┌─────────────────────────────┐ │    │
│  │  │ /usr/bin/node  /usr/bin/git │ │    │
│  │  │ /usr/bin/npm   /usr/bin/gh  │ │    │
│  │  │ /agents/  /workspace/       │ │    │
│  │  │ /sdcard/ (Android storage)  │ │    │
│  │  └─────────────────────────────┘ │    │
│  └──────────────────────────────────┘    │
│                                         │
│  Bundled assets:                        │
│   • alpine-rootfs.tar.gz (3.9MB)        │
│   • skills/android-environment.md       │
│   • 4 pre-installed agents              │
└─────────────────────────────────────────┘
```

## Features

- **Full Linux environment** — Alpine Linux 3.20 with `node`, `npm`, `git`, `gh` pre-installed. Agents can `apk add` any additional packages they need.
- **4 pre-bundled agents** — OpenClaude, OpenCode, Claude Code, Codex. Ready to use on first launch.
- **7 AI providers** — Claude, OpenAI, Gemini, OpenRouter, DeepSeek, Groq, Ollama. Configure API keys in-app.
- **Shizuku integration** — Agents can execute system-level commands (brightness, WiFi, app launch, screenshots) via Shizuku when available.
- **Android-aware agents** — A built-in skill (`android-environment.md`) tells agents exactly how to use their environment: filesystem layout, Shizuku commands, file access rules, etc.
- **Copy from terminal** — Long-press any terminal output to copy it. Copy icon on every log card.
- **Expert logging** — Every operation logged to file + logcat. Settings → Diagnostics shows the log path.
- **Optimized for low-end devices** — Clean Material 3 UI with no expensive blur effects. Runs smoothly on 2GB RAM devices.

## Architecture

### PRoot Runtime

The core innovation is using **PRoot** instead of wrapper scripts or shared-library hacks:

| Approach | Works? | Why |
|----------|--------|-----|
| Wrapper scripts in filesDir | No | SELinux W^X blocks exec of scripts |
| Termux debs (shared libs) | Fragile | Dependency hell, stale URLs |
| **PRoot + Alpine rootfs** | **Yes** | PRoot is a real binary (exec'd from nativeLibDir), agents run inside a full Linux env |

PRoot uses `ptrace` to intercept syscalls and translate paths — no root, no `chroot`, no `mount` needed. It works on all Android 7+ devices.

### Filesystem Layout

Inside the PRoot environment, agents see:

```
/                    — Alpine Linux rootfs
├── usr/bin/node     — Node.js runtime
├── usr/bin/npm      — npm package manager
├── usr/bin/git      — Git
├── usr/bin/gh       — GitHub CLI
├── agents/          — Agent code (bind-mounted)
├── workspace/       — User workspace (bind-mounted)
├── tmp/             — Temp files (bind-mounted)
├── sdcard/          — Android external storage
├── root/            — Home directory
└── etc/             — Config files (resolv.conf, apk/repositories)
```

### Agent Execution

When you send a message to an agent:

1. `ChatViewModel` calls the agent's wrapper script
2. The wrapper sets `PROOT_LOADER` + `PROOT_NO_SECCOMP` env vars
3. The wrapper execs `libproot.so` with `--rootfs=.../alpine-rootfs`
4. PRoot starts, intercepts syscalls, translates paths
5. Inside the container: `/usr/bin/node /agents/openclaude/cli.js`
6. Agent runs with full Linux compatibility
7. Output is captured and returned to the UI

## APK Flavors

| Flavor | Agent | App Label |
|--------|-------|-----------|
| `normal` | Default | Orbit AI |
| `openclaude` | OpenClaude | Orbit + OpenClaude |
| `opencode` | OpenCode | Orbit + OpenCode |
| `claudecode` | Claude Code | Orbit + Claude Code |
| `codex` | Codex | Orbit + Codex |

```bash
./gradlew assembleOpenclaudeDebug   # Build one flavor
./gradlew assembleDebug             # Build all flavors
```

## Building

Prerequisites: Android SDK 36, JDK 21+.

```bash
git clone https://github.com/TheOneWhoSpeaksJanna/Orbit-AI.git
cd Orbit-AI
./gradlew assembleNormalDebug
```

The APK will include:
- `lib/arm64-v8a/libproot.so` — PRoot binary (402KB)
- `lib/arm64-v8a/libproot_loader.so` — PRoot loader (18KB)
- `assets/alpine-rootfs.tar.gz` — Alpine Linux rootfs (3.9MB)
- `assets/skills/android-environment.md` — Agent awareness skill

## First Launch Flow

1. App starts → Setup Wizard
2. User selects agent + provider + API key
3. App extracts Alpine rootfs from APK assets (3.9MB → 8.9MB extracted)
4. App runs `apk add nodejs npm git gh` inside PRoot (first launch only, ~30s)
5. App installs the selected agent via `npm install` inside PRoot
6. App creates a wrapper script that runs the agent via PRoot
7. Chat opens — messages are piped to the agent via the PRoot wrapper

## Logging

Logs go to both file and logcat:

- **File**: Settings → Diagnostics shows the path (usually `/storage/emulated/0/Android/data/<pkg>/files/omniclaw_logs/`)
- **Logcat**: `adb logcat -s OmniClaw` shows real-time logs

Log levels: `I` (info), `W` (warnings), `E` (errors), `D` (debug). Every command execution, package install, and agent run is logged with full context.

## Security

- PRoot runs as the app's unprivileged UID — no privilege escalation
- Agents are sandboxed inside the app's data directory
- SUDO commands via Shizuku require explicit user approval per command
- API keys stored via `androidx.security.crypto` (encrypted SharedPreferences)

## CI/CD

GitHub Actions (`.github/workflows/build.yml`):
- Builds all 5 flavors on push to `main` and on PRs
- Runs unit tests on PRs
- Uploads APKs as artifacts (30-day retention)
- Auto-creates a GitHub Release on push to `main`
