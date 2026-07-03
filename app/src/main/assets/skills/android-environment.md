# Android Environment — Orbit-AI Agent Context

You are running inside **Orbit-AI**, a portable Android application that provides a full Linux environment for AI agents. This skill tells you everything you need to know about your environment so you can use it effectively.

## Your Environment

### Operating System
- **Host OS**: Android (Linux kernel, Bionic libc)
- **Container OS**: Alpine Linux 3.20 (musl libc) running inside PRoot
- **Architecture**: aarch64 (ARM 64-bit)
- **You are NOT running on a desktop Linux** — you are inside a PRoot container on an Android phone

### How PRoot Works
- PRoot intercepts system calls via `ptrace` and translates filesystem paths
- You appear to be `root` (uid 0) inside the container, but you're actually running as the app's unprivileged UID
- File operations are translated: `/etc/hosts` in the container maps to a file in the app's private storage
- No actual privilege escalation occurs — you have the same permissions as the Android app

### Filesystem Layout
```
/                    — Alpine Linux rootfs (read-write)
├── bin/             — Standard Linux binaries (sh, ls, cat, etc.)
├── usr/bin/         — Installed tools (node, npm, git, gh)
├── usr/lib/         — Shared libraries
├── etc/             — Configuration files
├── root/            — Your home directory (cd ~)
├── tmp/             — Temporary files (bind-mounted from app storage)
├── agents/          — AI agent code (bind-mounted from app storage)
│   ├── openclaude/  — OpenClaude agent
│   ├── opencode/    — OpenCode agent
│   ├── claude-code/ — Claude Code agent
│   └── codex/       — Codex agent
├── workspace/       — User workspace (bind-mounted from app storage)
└── sdcard/          — Android external storage (if granted permission)
```

### Installed Tools
The following tools are pre-installed in your environment:
- **node** (v22+) — JavaScript runtime
- **npm** — Node package manager
- **git** — Version control
- **gh** — GitHub CLI
- **curl** — HTTP client
- **wget** — File downloader
- **python3** — Python 3 (if installed via apk)
- **busybox** — 300+ POSIX utilities (sh, cp, mv, tar, grep, sed, awk, etc.)

### Installing Additional Packages
You can install Alpine packages using `apk`:
```sh
apk add <package-name>
```
Examples:
```sh
apk add python3 py3-pip
apk add openssh
apk add make gcc
```

## Android-Specific Capabilities

### Shizuku (Elevated Privileges)
If the user has Shizuku running and has granted permission, you can execute commands with elevated privileges using the `[SUDO: command]` tag. This gives you access to system-level operations:
- `settings put system screen_brightness 200` — change screen brightness
- `svc wifi enable` / `svc wifi disable` — toggle WiFi
- `svc bluetooth enable` / `svc bluetooth disable` — toggle Bluetooth
- `pm list packages` — list installed apps
- `am start -n com.package.name/.ActivityName` — open apps
- `input tap x y` — simulate screen taps
- `input keyevent KEYCODE_HOME` — simulate home button
- `screencap /sdcard/screenshot.png` — take screenshots

**Only use [SUDO: ...] when you actually need elevated privileges.** For normal file operations, use regular `[RUN: ...]` commands.

### File Access
- **Container files** (`/`, `/etc`, `/usr`, etc.): Full read-write access
- **App workspace** (`/workspace`): Full read-write access — use this for user files
- **Agent code** (`/agents`): Read-only (contains agent source code)
- **External storage** (`/sdcard`): Read-write if user granted "All files access" permission
- **Android system** (`/system`, `/apex`, `/vendor`): Read-only (visible but not writable)

### Network
- Full network access is available (HTTP, HTTPS, TCP, UDP)
- DNS resolution works (configured to use 8.8.8.8 / 8.8.4.4)
- You can make API calls, download files, clone repos, etc.

### Running Commands
Use the `[RUN: command]` tag to execute shell commands in your environment:
- `[RUN: ls -la /workspace]` — list files in workspace
- `[RUN: cd /workspace && git clone https://github.com/user/repo.git]` — clone a repo
- `[RUN: node /agents/openclaude/cli.js --help]` — run a node script

For commands that need elevated privileges, use `[SUDO: command]` instead.

## Best Practices

### File Operations
1. Use `/workspace` for user-facing files (it's bind-mounted to app storage and persists)
2. Use `/tmp` for temporary files (cleared on app restart)
3. Use `~/` (which is `/root/`) for your own config files
4. Don't modify files in `/agents/` — they contain agent source code

### Performance
1. The PRoot environment adds ~10-20% overhead due to syscall interception
2. Avoid running hundreds of tiny commands in sequence — batch them with `&&`
3. For long-running operations, use `nohup` or background processes

### Error Handling
1. If a command fails, check stderr for details
2. Common issues:
   - "Permission denied" on /sdcard → user hasn't granted storage permission
   - "command not found" → install it with `apk add <name>`
   - "connection refused" → check network connectivity

### Security
1. You are running as the app's UID — you have the same permissions as the Android app
2. You CANNOT access other apps' data (Android sandboxing)
3. You CANNOT modify system files (read-only mounts)
4. SUDO commands via Shizuku run with system-level privileges — use carefully

## Quick Reference

| What | How |
|------|-----|
| List files | `[RUN: ls -la /workspace]` |
| Read a file | `[RUN: cat /path/to/file]` |
| Write a file | `[RUN: echo "content" > /path/to/file]` |
| Run a Node script | `[RUN: node /path/to/script.js]` |
| Install a package | `[RUN: apk add python3]` |
| Clone a repo | `[RUN: cd /workspace && git clone URL]` |
| Take screenshot | `[SUDO: screencap /sdcard/screenshot.png]` |
| Check battery | `[SUDO: dumpsys battery]` |
| List apps | `[SUDO: pm list packages]` |
| Open app | `[SUDO: am start -n com.package/.Activity]` |
