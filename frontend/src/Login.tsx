import { useState, type FormEvent } from "react";
import { ArrowRight, ShieldCheck } from "lucide-react";
import { api, message } from "./api";
import type { User } from "./types";
import { ErrorNotice, Logo } from "./ui";
export function Login({ signedIn }: { signedIn: (user: User) => void }) {
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setBusy(true);
    setError("");
    try {
      const user = await api.login(
        String(data.get("username")),
        String(data.get("password")),
      );
      form.reset();
      signedIn(user);
    } catch (failure) {
      setError(message(failure));
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="login-page">
      <section className="login-story">
        <div className="brand">
          <Logo />
          AgentFlow<span className="brand-tag">WORKSPACE</span>
        </div>
        <div>
          <span className="eyebrow">INTELLIGENCE, WITH INTENTION</span>
          <h1>
            From a good idea
            <br />
            to a reliable flow.
          </h1>
          <p>
            Connect your steps. Give agents a clear purpose.
            <br />
            Stay in control of every action.
          </p>
          <div className="story-flow">
            <span>01 · Receive</span>
            <i />
            <span>02 · Think</span>
            <i />
            <span>03 · Review</span>
            <i />
            <span>04 · Act</span>
          </div>
        </div>
        <small>
          <ShieldCheck size={17} />
          Every action leaves a trace.
        </small>
      </section>
      <section className="login-panel">
        <div className="login-form">
          <span className="eyebrow">YOUR WORKFLOW WORKSPACE</span>
          <h2>Welcome back.</h2>
          <p>Sign in to build, run, and review your workflows.</p>
          <form onSubmit={submit}>
            <label>
              Username
              <input
                name="username"
                autoComplete="username"
                required
                maxLength={100}
                autoFocus
              />
            </label>
            <label>
              Password
              <input
                name="password"
                type="password"
                autoComplete="current-password"
                required
              />
            </label>
            <ErrorNotice>{error}</ErrorNotice>
            <button className="primary" disabled={busy}>
              {busy ? "Signing in…" : "Sign in"}
              <ArrowRight size={18} />
            </button>
          </form>
          {import.meta.env.DEV && (
            <div className="dev-hint">
              Local development account
              <br />
              <code>demo</code> / <code>demo-local-only</code>
            </div>
          )}
        </div>
      </section>
    </main>
  );
}
