#!/usr/bin/env python3
"""Verify the real container boundary and recovery in a disposable Compose project."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("execution_demo", ROOT / "scripts/execution-demo.py")
demo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(demo)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check-config", action="store_true")
    parser.add_argument("--build", action="store_true")
    parser.add_argument("--browser", action="store_true", help="Run Chromium checks and retain a demo video")
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="agentflow-compose-") as directory:
        temporary = Path(directory)
        with socket.socket() as candidate:
            candidate.bind(("127.0.0.1", 0))
            port = candidate.getsockname()[1]
        env_file = temporary / "compose.env"
        env_file.write_text(f"DATABASE_PASSWORD={uuid.uuid4().hex}\nBOOTSTRAP_USERNAME=demo\n"
                            f"BOOTSTRAP_PASSWORD=demo-local-only\nWEB_PORT={port}\n")
        env_file.chmod(0o600)
        project = "agentflow-check-" + uuid.uuid4().hex[:10]
        command = ["docker", "compose", "-f", str(ROOT / "docker-compose.yml"),
                   "--env-file", str(env_file), "-p", project]

        def compose(*arguments, **options):
            return subprocess.run(command + list(arguments), check=True, **options)

        # Never print rendered Compose configuration: it contains credentials.
        configuration = json.loads(compose("config", "--format", "json", capture_output=True, text=True).stdout)
        services = configuration["services"]
        assert "frontend" in services, "The deployed web service is missing"
        assert not services["backend"].get("ports"), "The Java API must stay inside Docker"
        assert not services["postgres"].get("ports"), "PostgreSQL must stay inside Docker"
        assert services["frontend"]["ports"][0]["host_ip"] == "127.0.0.1"
        print("PASS: only the loopback web origin is published", flush=True)
        if args.check_config:
            return
        try:
            if args.build:
                compose("build")
            compose("up", "-d", "--wait", "--wait-timeout", "180")
            base = f"http://127.0.0.1:{port}"
            with urllib.request.urlopen(base, timeout=10) as response:
                assert "AgentFlow" in response.read().decode()
                assert "script-src 'self'" in response.headers["Content-Security-Policy"]
                assert response.headers["X-Content-Type-Options"] == "nosniff"
            for path, expected in [("/api/auth/me", 401), ("/actuator/env", 404)]:
                try:
                    urllib.request.urlopen(base + path, timeout=10)
                    raise AssertionError(f"{path} did not enforce its boundary")
                except urllib.error.HTTPError as error:
                    assert error.code == expected
            api = demo.Api(base, "demo", "demo-local-only")
            cookies = next(handler.cookiejar for handler in api.client.handlers
                           if isinstance(handler, urllib.request.HTTPCookieProcessor))
            assert "AGENTFLOW_SESSION" in {cookie.name for cookie in cookies}
            fixture = json.loads((ROOT / "examples/support-intake.json").read_text())
            api.request("/api/workflows", "POST", fixture, headers={"Content-Type": "application/json"}, expected=403)
            run = api.start_demo("Container recovery demo", "Password reset failed", agent=True, approval=True)
            run = api.wait(run["id"], lambda r: r["status"] == "WAITING_FOR_APPROVAL", timeout=30)
            assert run["mode"] == "DETERMINISTIC_PLANNER" and "result" not in run["context"]
            approval = run["approvals"][0]
            api.request(f"/api/approvals/{approval['id']}/decision", "POST",
                        {"decision": "APPROVE", "payloadDigest": "0" * 64}, expected=409)
            print("PASS: static build, security headers, authentication, CSRF and frozen approval", flush=True)
            frontend_id = compose("ps", "-q", "frontend", capture_output=True, text=True).stdout.strip()
            compose("up", "-d", "--no-deps", "--force-recreate", "--wait", "--wait-timeout", "180", "backend")
            deadline = time.monotonic() + 30
            while True:
                try:
                    with urllib.request.urlopen(base + "/actuator/health", timeout=3) as response:
                        if json.load(response)["status"] == "UP":
                            break
                except (urllib.error.URLError, TimeoutError):
                    pass
                if time.monotonic() >= deadline:
                    raise AssertionError("The proxy did not recover after Java was replaced")
                time.sleep(1)
            assert compose("ps", "-q", "frontend", capture_output=True, text=True).stdout.strip() == frontend_id
            api = demo.Api(base, "demo", "demo-local-only")
            assert api.request(f"/api/approvals/{approval['id']}")["status"] == "PENDING"
            print("PASS: backend replacement recovers through the unchanged web proxy", flush=True)
            with (temporary / "backup.dump").open("wb") as backup:
                compose("exec", "-T", "postgres", "pg_dump", "-U", "agentflow", "-d", "agentflow", "-Fc", stdout=backup)
            compose("exec", "-T", "postgres", "createdb", "-U", "agentflow", "agentflow_restore")
            with (temporary / "backup.dump").open("rb") as backup:
                compose("exec", "-T", "postgres", "pg_restore", "-U", "agentflow", "-d", "agentflow_restore",
                        "--exit-on-error", "--no-owner", "--no-acl", stdin=backup)
            run_id = str(uuid.UUID(run["id"]))
            restored = compose("exec", "-T", "postgres", "psql", "-U", "agentflow", "-d", "agentflow_restore",
                               "-tAc", f"SELECT status FROM workflow_runs WHERE id = '{run_id}'", capture_output=True, text=True)
            assert restored.stdout.strip() == "WAITING_FOR_APPROVAL"
            compose("down")  # Preserve this test project's volume; no user data is involved.
            compose("up", "-d", "--wait", "--wait-timeout", "180")
            api = demo.Api(base, "demo", "demo-local-only")
            persisted = api.request(f"/api/approvals/{approval['id']}")
            assert persisted["status"] == "PENDING" and persisted["payloadDigest"] == approval["payloadDigest"]
            assert persisted["payload"] == approval["payload"]
            body = {"decision": "APPROVE", "payloadDigest": approval["payloadDigest"]}
            route = f"/api/approvals/{approval['id']}/decision"
            api.request(route, "POST", body)  # Explicit synthetic test decision, never a user's request.
            api.request(route, "POST", body)
            finished = api.wait(run["id"], lambda r: r["status"] == "SUCCEEDED", timeout=30)
            assert all(step["attempts"] == 1 for step in finished["steps"])
            assert finished["plannerCalls"] == 2 and finished["toolCalls"] == 2
            assert finished["plannerTokens"] == 0 and "result" in finished["context"]
            assert sum(event["eventType"] == "APPROVAL_APPROVED" for event in finished["events"]) == 1
            api.request("/api/auth/logout", "POST", expected=204)
            print("PASS: backup restore, full stack recreation, exact pause/resume and no duplicate effects", flush=True)
            if args.browser:
                environment = dict(os.environ, AGENTFLOW_E2E_BASE_URL=base)
                subprocess.run(["npm", "run", "test:e2e"], cwd=ROOT / "frontend", env=environment, check=True)
                videos = sorted((ROOT / "frontend/test-results").glob("*canvas-save*/video.webm"))
                assert videos, "The container demo video was not recorded"
                artifacts = ROOT / "artifacts"
                artifacts.mkdir(exist_ok=True)
                shutil.copyfile(videos[0], artifacts / "agentflow-demo.webm")
                screenshots = sorted((ROOT / "frontend/test-results").glob("*canvas-save*/execution.png"))
                assert screenshots, "The execution trace screenshot was not captured"
                shutil.copyfile(screenshots[0], artifacts / "agentflow-execution.png")
                print("PASS: container browser checks; recording saved to artifacts/agentflow-demo.webm", flush=True)
        finally:
            # This randomly named project was created here with a disposable test volume.
            compose("down", "--volumes", "--remove-orphans")


if __name__ == "__main__":
    main()
