#!/usr/bin/env python3
"""Verify the local dev API with real session cookies and CSRF tokens."""
import http.cookiejar
import json
import os
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://127.0.0.1:8080"
cookies = http.cookiejar.CookieJar()
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))


def request(path, method="GET", body=None, headers=None, expected=200):
    req = urllib.request.Request(BASE + path, data=body, method=method, headers=headers or {})
    try:
        response = client.open(req, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        assert response.status == expected, f"{method} {path}: expected {expected}, got {response.status}"
        data = response.read()
        return json.loads(data) if data else None


request("/api/workflows", expected=401)
csrf = request("/api/auth/csrf")
login = urllib.parse.urlencode({
    "username": os.environ.get("AGENTFLOW_SMOKE_USERNAME", "demo"),
    "password": os.environ.get("AGENTFLOW_SMOKE_PASSWORD", "demo-local-only"),
    csrf["parameterName"]: csrf["token"],
}).encode()
request("/api/auth/login", "POST", login, {"Content-Type": "application/x-www-form-urlencoded"}, 204)
assert request("/api/auth/me")["username"] == os.environ.get("AGENTFLOW_SMOKE_USERNAME", "demo")
csrf = request("/api/auth/csrf")  # Authentication rotates the CSRF token.
headers = {"Content-Type": "application/json", csrf["headerName"]: csrf["token"]}
definition = {
    "nodes": [
        {"id": "intake", "type": "TRIGGER", "label": "Receive support issue"},
        {"id": "classify", "type": "AI_STEP", "label": "Classify issue"},
        {"id": "search", "type": "ACTION", "label": "Search approved documentation"},
        {"id": "review", "type": "APPROVAL", "label": "Review proposed action"},
        {"id": "ticket", "type": "ACTION", "label": "Create ticket"},
    ],
    "edges": [{"source": a, "target": b} for a, b in [
        ("intake", "classify"), ("classify", "search"), ("search", "review"), ("review", "ticket")
    ]],
}
payload = {"name": "Support intake smoke test", "description": "Local verification", "definition": definition}
workflow = request("/api/workflows", "POST", json.dumps(payload).encode(), headers, 201)
path = "/api/workflows/" + workflow["id"]
assert request(path)["currentVersion"] == 1
payload.update(name="Updated smoke test", expectedVersion=1)
assert request(path, "PUT", json.dumps(payload).encode(), headers)["currentVersion"] == 2
request(path, "PUT", json.dumps(payload).encode(), headers, 409)
assert request(path + "/versions")["total"] == 2
request(path, "DELETE", headers=headers, expected=204)
assert request(path)["archived"] is True
request("/api/auth/logout", "POST", headers=headers, expected=204)
request("/api/auth/me", expected=401)
print("PASS: login, CSRF, create, update, stale-update conflict, immutable history, archive, logout")
