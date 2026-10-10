#!/usr/bin/env python3
"""Run the deterministic support workflow against a running local AgentFlow API."""
import argparse
import http.cookiejar
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


class Api:
    def __init__(self, base, username, password):
        self.base = base
        self.client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        token = self.request("/api/auth/csrf")
        self.request("/api/auth/login", "POST", urllib.parse.urlencode({
            "username": username, "password": password, token["parameterName"]: token["token"],
        }).encode(), {"Content-Type": "application/x-www-form-urlencoded"}, 204)
        token = self.request("/api/auth/csrf")
        self.headers = {"Content-Type": "application/json", token["headerName"]: token["token"]}

    def request(self, path, method="GET", body=None, headers=None, expected=200):
        if isinstance(body, dict):
            body = json.dumps(body).encode()
        request = urllib.request.Request(self.base + path, data=body, method=method,
                                         headers=headers if headers is not None else getattr(self, "headers", {}))
        try:
            response = self.client.open(request, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            if response.status != expected:
                raise RuntimeError(f"{method} {path}: expected {expected}, got {response.status}")
            content = response.read()
            return json.loads(content) if content else None

    def start_demo(self, subject, body, agent=False):
        example = Path(__file__).resolve().parent.parent / "examples" / ("agent-support-intake.json" if agent else "support-intake.json")
        workflow = self.request("/api/workflows", "POST", json.loads(example.read_text()), expected=201)
        payload = {"versionNumber": workflow["currentVersion"], "idempotencyKey": str(uuid.uuid4()),
                   "input": {"subject": subject, "body": body}}
        route = f"/api/workflows/{workflow['id']}/runs"
        run = self.request(route, "POST", payload, expected=202)
        replay = self.request(route, "POST", payload, expected=202)
        if replay["id"] != run["id"]:
            raise RuntimeError("Duplicate start created another run")
        return run

    def wait(self, run_id, predicate, timeout=30):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            run = self.request(f"/api/runs/{run_id}")
            if predicate(run):
                return run
            time.sleep(0.1)
        raise RuntimeError(f"Run {run_id} did not reach the expected state within {timeout}s")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--subject", default="Cannot log in")
    parser.add_argument("--body", default="Password reset failed; please help restore account access.")
    parser.add_argument("--agent", action="store_true", help="Use agent steps with proposals validated by Java")
    args = parser.parse_args()
    api = Api(args.base_url, os.environ.get("AGENTFLOW_SMOKE_USERNAME", "demo"),
              os.environ.get("AGENTFLOW_SMOKE_PASSWORD", "demo-local-only"))
    run = api.start_demo(args.subject, args.body, args.agent)
    print(f"Started run {run['id']} in {run['mode']} mode", flush=True)
    run = api.wait(run["id"], lambda r: r["status"] in {"SUCCEEDED", "FAILED", "CANCELLED"}, timeout=150)
    if run["status"] != "SUCCEEDED":
        raise RuntimeError(f"Run ended {run['status']}: {run['errorCode']}")
    print(" → ".join(f"{step['nodeId']}: {step['status']}" for step in run["steps"]))
    for step in run["steps"]:
        if step.get("plannerId"):
            tools = ", ".join(call["tool"] for call in step["proposal"]["toolCalls"])
            print(f"{step['nodeId']}: {step['plannerId']} proposed {tools}; Java policy {step['policyStatus']}")
    if args.agent:
        print(f"Usage: {run['plannerCalls']} planner calls, {run['toolCalls']} tool calls")
        if run["mode"] in {"OPENAI_PLANNER", "GEMINI_PLANNER"}:
            print(f"Tokens: {run['plannerTokens']} reported, {run['reservedPlannerTokens']} reserved")
    print(json.dumps(run["context"]["result"], indent=2))
    print(f"PASS: duplicate start reused the run; {len(run['events'])} audit events persisted")
    print(f"Trace: {args.base_url}/api/runs/{run['id']} (authenticated session required)")
    api.request("/api/auth/logout", "POST", expected=204)


if __name__ == "__main__":
    main()
