#!/usr/bin/env python3
"""Review a durable approval checkpoint and explicitly approve or reject its local action."""
import argparse
import importlib.util
import json
import os
from pathlib import Path

spec = importlib.util.spec_from_file_location("execution_demo", Path(__file__).with_name("execution-demo.py"))
demo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(demo)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--agent", action="store_true", help="Use the server-selected planner for classification/search")
    parser.add_argument("--decision", choices=["approve", "reject"], help="Explicit decision for automated demo verification; omitted means interactive review")
    args = parser.parse_args()
    api = demo.Api(args.base_url, os.environ.get("AGENTFLOW_SMOKE_USERNAME", "demo"),
                   os.environ.get("AGENTFLOW_SMOKE_PASSWORD", "demo-local-only"))
    try:
        run = api.start_demo("Cannot log in", "Password reset failed; please help restore account access.", args.agent, approval=True)
        print(f"Started run {run['id']} in {run['mode']} mode", flush=True)
        run = api.wait(run["id"], lambda r: r["status"] in {"WAITING_FOR_APPROVAL", "SUCCEEDED", "FAILED", "CANCELLED"}, timeout=150)
        if run["status"] != "WAITING_FOR_APPROVAL":
            raise RuntimeError(f"Expected approval pause; got {run['status']}: {run['errorCode']}")
        approval = run["approvals"][0]
        print("WAITING_FOR_APPROVAL: the result action has not executed.")
        print(json.dumps(approval["payload"], indent=2))
        print(f"Approval: {approval['id']} | digest: {approval['payloadDigest']}")
        decision = args.decision or input("Review the action above. Enter approve or reject: ").strip().lower()
        if decision not in {"approve", "reject"}:
            raise RuntimeError("No decision submitted; the run remains waiting for review")
        body = {"decision": decision.upper(), "payloadDigest": approval["payloadDigest"]}
        route = f"/api/approvals/{approval['id']}/decision"
        api.request(route, "POST", body)
        replay = api.request(route, "POST", body)
        if replay["status"] != ("APPROVED" if decision == "approve" else "REJECTED"):
            raise RuntimeError("Duplicate decision changed the approval")
        done = api.wait(run["id"], lambda r: r["status"] in {"SUCCEEDED", "FAILED", "CANCELLED"})
        expected = "SUCCEEDED" if decision == "approve" else "FAILED"
        if done["status"] != expected or (decision == "reject" and done["errorCode"] != "APPROVAL_REJECTED"):
            raise RuntimeError(f"Unexpected outcome: {done['status']}: {done['errorCode']}")
        if ("result" in done["context"]) != (decision == "approve"):
            raise RuntimeError("Result action did not respect the human decision")
        print(" → ".join(f"{s['nodeId']}: {s['status']}" for s in done["steps"]))
        print(f"PASS: {decision} persisted; duplicate decision was idempotent; {len(done['events'])} audit events saved")
        print(f"Trace: {args.base_url}/api/runs/{run['id']} (authenticated session required)")
    finally:
        api.request("/api/auth/logout", "POST", expected=204)


if __name__ == "__main__":
    main()
