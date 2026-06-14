# Termux Agent Service
# This server runs inside the actual Termux application environment natively on the Android device.
# It provides an HTTP API over localhost (127.0.0.1:8080) for the Orbit AI Android app.

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import subprocess

# For AI Providers
import google.generativeai as genai
import anthropic

app = FastAPI(title="Termux Orbit Agent Service")

class CommandRequest(BaseModel):
    command: str

class CommandResponse(BaseModel):
    output: str
    exitCode: int

class AiRequest(BaseModel):
    provider: str # "gemini", "claude", "ollama"
    model: str
    prompt: str
    api_key: str = ""

class AiResponse(BaseModel):
    text: str


@app.post("/execute", response_model=CommandResponse)
def execute_command(req: CommandRequest):
    """
    Executes a shell command in the Termux environment.
    Since this runs in Termux, commands like `git checkout`, `apt install`,
    or running local compile tasks work properly unlike standard Android sandbox execution.
    """
    try:
        # shell=True allows full shell syntax like pipes and env vars
        result = subprocess.run(
            req.command, 
            shell=True,
            capture_output=True,
            text=True
        )
        
        output = result.stdout
        if result.stderr:
            output += f"\n[STDERR]:\n{result.stderr}"
            
        return CommandResponse(
            output=output.strip() if output else "(no output)",
            exitCode=result.returncode
        )
    except Exception as e:
        return CommandResponse(output=str(e), exitCode=-1)


@app.post("/ai/generate", response_model=AiResponse)
def generate_ai(req: AiRequest):
    """
    Delegates prompts to various providers (Gemini, Claude, Ollama)
    from inside Termux.
    """
    try:
        if req.provider.lower() == "gemini":
            if not req.api_key:
                raise HTTPException(status_code=400, detail="Gemini API Key required")
            genai.configure(api_key=req.api_key)
            model = genai.GenerativeModel(req.model or "gemini-1.5-pro-latest")
            response = model.generate_content(req.prompt)
            return AiResponse(text=response.text)
            
        elif req.provider.lower() == "claude":
            if not req.api_key:
                raise HTTPException(status_code=400, detail="Anthropic API Key required")
            client = anthropic.Anthropic(api_key=req.api_key)
            response = client.messages.create(
                model=req.model or "claude-3-opus-20240229",
                max_tokens=1024,
                messages=[{"role": "user", "content": req.prompt}]
            )
            text = "".join(block.text for block in response.content)
            return AiResponse(text=text)
            
        elif req.provider.lower() == "ollama":
            # Run local ollama binary within Termux
            model_name = req.model or "llama3"
            cmd = f"ollama run {model_name} '{req.prompt}'"
            result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
            if result.returncode != 0:
                raise HTTPException(status_code=500, detail=result.stderr)
            return AiResponse(text=result.stdout.strip())
            
        else:
            raise HTTPException(status_code=400, detail=f"Unknown provider: {req.provider}")

    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    # Bind to localhost so the Android app on the same device can access it without permission issues
    uvicorn.run(app, host="127.0.0.1", port=8080)
