#!/usr/bin/env python3
"""Verify process restart recovery with an isolated PostgreSQL container and API port."""
import importlib.util
import argparse
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("execution_demo", ROOT / "scripts/execution-demo.py")
demo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(demo)


def docker(*args):
    return subprocess.check_output(["docker", *args], text=True, timeout=60).strip()


def wait_ready(process, base, log):
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"API exited early; inspect {log}")
        try:
            with urllib.request.urlopen(base + "/actuator/health", timeout=1) as response:
                if json.load(response).get("status") == "UP":
                    return
        except (OSError, ValueError):
            pass
        time.sleep(0.2)
    raise RuntimeError(f"API did not start; inspect {log}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--agent", action="store_true", help="Verify agent proposal and budget recovery")
    args = parser.parse_args()
    jar = ROOT / "backend/target/agentflow-0.1.0-SNAPSHOT.jar"
    if not jar.is_file():
        raise RuntimeError("Build the executable jar first: cd backend && ./mvnw package")
    container = None
    process = None
    with tempfile.TemporaryDirectory(prefix="agentflow-restart-") as directory:
        log = Path(directory) / "api.log"
        try:
            container = docker("run", "-d", "--rm", "-e", "POSTGRES_USER=agentflow", "-e", "POSTGRES_DB=agentflow",
                               "-e", "POSTGRES_PASSWORD=restart-test-only", "-p", "127.0.0.1::5432", "postgres:17-alpine")
            port = docker("port", container, "5432/tcp").rsplit(":", 1)[1]
            for _ in range(60):
                ready = subprocess.run(["docker", "exec", container, "pg_isready", "-U", "agentflow"],
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5)
                if ready.returncode == 0:
                    break
                time.sleep(0.2)
            else:
                raise RuntimeError("Test PostgreSQL did not become ready")
            with socket.socket() as listener:
                listener.bind(("127.0.0.1", 0))
                api_port = listener.getsockname()[1]
            base = f"http://127.0.0.1:{api_port}"
            env = dict(os.environ, DATABASE_URL=f"jdbc:postgresql://127.0.0.1:{port}/agentflow",
                       DATABASE_USERNAME="agentflow", DATABASE_PASSWORD="restart-test-only",
                       BOOTSTRAP_USERNAME="restart-check", BOOTSTRAP_PASSWORD="restart-check-only-password",
                       SESSION_COOKIE_SECURE="false")
            env.pop("SPRING_PROFILES_ACTIVE", None)
            command = ["java", "-jar", str(jar), "--server.address=127.0.0.1", f"--server.port={api_port}",
                       "--agentflow.execution.worker-enabled=true", "--agentflow.execution.poll-delay-ms=2000"]
            with log.open("w") as output:
                process = subprocess.Popen(command, env=env, cwd=directory, stdout=output, stderr=subprocess.STDOUT)
                wait_ready(process, base, log)
                api = demo.Api(base, "restart-check", "restart-check-only-password")
                run = api.start_demo("Cannot log in", "Password reset failed", args.agent)
                completed = 1 if args.agent else 0
                checkpoint = api.wait(run["id"], lambda r: r["status"] == "RUNNING" and r["steps"][completed]["status"] == "SUCCEEDED")
                if checkpoint["steps"][completed + 1]["status"] != "PENDING":
                    raise RuntimeError("Missed the checkpoint; rerun the check")
                if args.agent and (checkpoint["plannerCalls"] != 1 or checkpoint["toolCalls"] != 1):
                    raise RuntimeError("First agent checkpoint did not preserve its usage")
                process.kill()  # Only the test-owned child process; intentionally no graceful shutdown.
                process.wait(timeout=10)
                process = subprocess.Popen(command, env=env, cwd=directory, stdout=output, stderr=subprocess.STDOUT)
                wait_ready(process, base, log)
                api = demo.Api(base, "restart-check", "restart-check-only-password")
                recovered = api.wait(run["id"], lambda r: r["status"] in {"SUCCEEDED", "FAILED", "CANCELLED"})
                if recovered["status"] != "SUCCEEDED" or any(step["attempts"] != 1 for step in recovered["steps"]):
                    raise RuntimeError("Restart lost or repeated a checkpoint")
                if len(recovered["events"]) != (13 if args.agent else 7):
                    raise RuntimeError("Restart duplicated or lost audit events")
                if args.agent and (recovered["plannerCalls"] != 2 or recovered["toolCalls"] != 2
                                   or any(step["policyStatus"] != "APPROVED" or not step["proposal"] for step in recovered["steps"][1:3])):
                    raise RuntimeError("Restart lost planner usage or approved proposals")
                print("PASS: PostgreSQL checkpoint survived SIGKILL; fresh Java process resumed all remaining steps once")
        except Exception:
            if log.exists():
                print(log.read_text()[-6000:])
            raise
        finally:
            if process is not None and process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
            if container:
                docker("rm", "-f", container)


if __name__ == "__main__":
    main()
