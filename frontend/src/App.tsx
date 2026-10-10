import { useEffect, useState } from "react";
import {
  GitBranch,
  ShieldCheck,
  LogOut,
  Blocks,
  ArrowUpRight,
} from "lucide-react";
import { api, message } from "./api";
import type { User } from "./types";
import { Login } from "./Login";
import { Workflows } from "./Workflows";
import { WorkflowPage } from "./Editor";
import { RunView } from "./RunView";
import { Approvals } from "./Approvals";
import { Tools } from "./Tools";
import { ErrorNotice, Logo } from "./ui";
import { draftGuard, normalizeRoute } from "./navigation";
export default function App() {
  const [user, setUser] = useState<User | null>();
  const [error, setError] = useState("");
  const [route, setRoute] = useState(
    normalizeRoute(window.location.hash.slice(1)),
  );
  useEffect(() => {
    let active = true;
    api.onUnauthorized = () => {
      if (active) {
        setUser(null);
        draftGuard.dirty = false;
      }
    };
    void api
      .request<User>("/api/auth/me")
      .then((value) => {
        if (active) setUser(value);
      })
      .catch(() => {
        if (active) setUser(null);
      });
    return () => {
      active = false;
      api.onUnauthorized = undefined;
    };
  }, []);
  useEffect(() => {
    const changed = () => {
      const next = normalizeRoute(window.location.hash.slice(1));
      if (
        next !== route &&
        draftGuard.dirty &&
        !window.confirm("Leave this workflow and discard unsaved changes?")
      ) {
        window.location.replace("#" + route);
        return;
      }
      setRoute(next);
    };
    window.addEventListener("hashchange", changed);
    return () => window.removeEventListener("hashchange", changed);
  }, [route]);
  const navigate = (path: string) => {
    window.location.hash = normalizeRoute(path);
  };
  async function logout() {
    if (
      draftGuard.dirty &&
      !window.confirm("Sign out and discard your unsaved workflow?")
    )
      return;
    try {
      await api.logout();
      draftGuard.dirty = false;
      setUser(null);
      setError("");
      navigate("/workflows");
    } catch (failure) {
      setError(message(failure));
    }
  }
  if (user === undefined)
    return <main className="boot">Opening your workspace…</main>;
  if (!user)
    return (
      <Login
        signedIn={(value) => {
          setUser(value);
          setError("");
        }}
      />
    );
  const [, section, id] = route.split("/");
  const labels: Record<string, string> = {
    workflows: "Workflows",
    runs: "Execution",
    approvals: "Approvals",
    tools: "Tool registry",
  };
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <Logo />
          AgentFlow
        </div>
        <div className="workspace-select">
          <span className="workspace-avatar">AF</span>
          <div>
            Personal workspace<small>Your automation studio</small>
          </div>
        </div>
        <span className="nav-caption">WORKSPACE</span>
        <nav>
          {(
            [
              ["workflows", GitBranch, "Workflows"],
              ["approvals", ShieldCheck, "Approvals"],
              ["tools", Blocks, "Tool registry"],
            ] as const
          ).map(([path, Icon, label]) => (
            <button
              key={path}
              aria-label={label}
              className={
                section === path || (path === "workflows" && section === "runs")
                  ? "active"
                  : ""
              }
              onClick={() => navigate("/" + path)}
            >
              <Icon size={18} />
              {label}
              {section === path && <span className="nav-mark" />}
            </button>
          ))}
        </nav>
        <div className="sidebar-note">
          <span className="eyebrow">BUILT FOR CONTROL</span>
          <p>
            Clear steps.
            <br />
            Considered actions.
            <br />A trace you can trust.
          </p>
          <ArrowUpRight size={18} />
        </div>
        <div className="user-box">
          <span className="user-avatar">
            {user.username.slice(0, 2).toUpperCase()}
          </span>
          <div>
            {user.username}
            <small>Workspace member</small>
          </div>
          <button aria-label="Sign out" onClick={logout}>
            <LogOut size={17} />
          </button>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <span>
            Personal workspace <span className="slash">/</span>
            {labels[section] ?? "Workflows"}
          </span>
          <div>
            <ShieldCheck size={13} />
            Session authenticated
          </div>
        </header>
        <main className="page" key={user.username}>
          <ErrorNotice>{error}</ErrorNotice>
          {section === "workflows" &&
            (id ? (
              <WorkflowPage id={id} navigate={navigate} />
            ) : (
              <Workflows
                open={(id) => navigate("/workflows/" + id)}
                create={() => navigate("/workflows/new")}
              />
            ))}
          {section === "runs" && id && <RunView id={id} navigate={navigate} />}
          {section === "approvals" && <Approvals id={id} navigate={navigate} />}
          {section === "tools" && <Tools />}
        </main>
      </div>
    </div>
  );
}
