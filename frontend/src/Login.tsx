import { lazy, Suspense, useState, type FormEvent } from "react";
import { ArrowRight } from "lucide-react";
import { api, message } from "./api";
import type { User } from "./types";
import { ErrorNotice, Logo } from "./ui";
const EnterpriseAIPipeline = lazy(
  () => import("@/components/ui/ai-agent-pipeline"),
);
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
          AgentRail
        </div>
        <div className="story-intro">
          <h1>
            Agent workflows.
            <br />
            Controlled execution.
          </h1>
          <p>
            Design workflows, review proposed actions, and inspect every result.
          </p>
        </div>
        <Suspense
          fallback={<div className="pipeline-placeholder" aria-hidden="true" />}
        >
          <EnterpriseAIPipeline />
        </Suspense>
      </section>
      <section className="login-panel">
        <div className="login-form">
          <div className="brand login-mobile-brand">
            <Logo />
            AgentRail
          </div>
          <h2>Sign in</h2>
          <p>Access your workflow workspace.</p>
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
            <details className="dev-hint">
              <summary>Local development account</summary>
              <code>demo</code> / <code>demo-local-only</code>
            </details>
          )}
        </div>
      </section>
    </main>
  );
}
