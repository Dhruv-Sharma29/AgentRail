import { useEffect, useState } from "react";
import { GitBranch, ShieldCheck, LogOut, Blocks, Activity } from "lucide-react";
import { api, message } from "./api";
import type { User } from "./types";
import { Login } from "./Login";
import { Workflows } from "./Workflows";
import { WorkflowPage } from "./Editor";
import { RunView } from "./RunView";
import { Approvals } from "./Approvals";
import { Tools } from "./Tools";
import { Operations } from "./Operations";
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
    operations: "Operations",
  };
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <Logo />
          AgentRail
        </div>
        <span className="nav-caption">WORKSPACE</span>
        <nav>
          {(
            [
              ["workflows", GitBranch, "Workflows"],
              ["approvals", ShieldCheck, "Approvals"],
              ["tools", Blocks, "Tool registry"],
              ["operations", Activity, "Operations"],
            ] as const
          ).map(([path, Icon, label]) => (
            <button
              key={path}
              aria-label={label}
              aria-current={
                section === path || (path === "workflows" && section === "runs")
                  ? "page"
                  : undefined
              }
              className={
                section === path || (path === "workflows" && section === "runs")
                  ? "active"
                  : ""
              }
              onClick={() => navigate("/" + path)}
            >
              <Icon size={18} />
              {label}
            </button>
          ))}
        </nav>
        <div className="user-box">
          <span className="user-avatar">
            {user.username.slice(0, 2).toUpperCase()}
          </span>
          <div>{user.username}</div>
          <button aria-label="Sign out" onClick={logout}>
            <LogOut size={17} />
          </button>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <span>
            Workspace <span className="slash">/</span>
            {labels[section] ?? "Workflows"}
          </span>
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
          {section === "operations" && <Operations navigate={navigate} />}
        </main>
      </div>
    </div>
  );
}
