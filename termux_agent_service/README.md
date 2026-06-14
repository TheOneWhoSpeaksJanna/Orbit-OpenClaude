# Termux Agent Service Setup

This directory contains the Python server intended to be run **inside the Termux application** on your Android device.

## Why is this needed?

Standard Android applications run in heavily restricted, isolated sandboxes. Calling `Runtime.getRuntime().exec("sh")` from within the Orbit AI APK will only give you access to a severely limited environment without tools like `git`, `apt`, `npm`, or `python`.

By running this service directly inside Termux, we expose a secure localhost HTTP API (`127.0.0.1:8080`) that Orbit AI can communicate with. This grants Orbit AI true access to the full, authentic Termux CLI environment for shell execution, file persistence, and AI interactions.

## Setup Instructions (Run these inside Termux)

1. **Install dependencies:**
   ```bash
   pkg update
   pkg install python
   pip install fastapi uvicorn google-generativeai anthropic pydantic
   ```
2. **Transfer `server.py`:**
   Copy `server.py` to your Termux home directory.
   
3. **Start the server:**
   ```bash
   python server.py
   ```
   
Once the server is running, Orbit AI's `TermuxExecutor` will successfully dispatch commands to it.
