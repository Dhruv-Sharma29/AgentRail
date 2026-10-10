import type { ReactNode } from "react";
import { AlertCircle, GitBranch, ArrowLeft, ArrowRight } from "lucide-react";
export function Logo() {
  return (
    <span className="logo">
      <GitBranch size={23} strokeWidth={2.5} />
    </span>
  );
}
export function Badge({ value }: { value: string }) {
  return (
    <span className={`badge ${value.toLowerCase()}`}>
      {value.replaceAll("_", " ")}
    </span>
  );
}
export const date = (value?: string | null) =>
  value
    ? new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(value))
    : "—";
export function ErrorNotice({ children }: { children?: ReactNode }) {
  return children ? (
    <div role="alert" className="error">
      <AlertCircle size={17} />
      {children}
    </div>
  ) : null;
}
export function Pager({
  page,
  total,
  size = 12,
  change,
}: {
  page: number;
  total: number;
  size?: number;
  change: (page: number) => void;
}) {
  return (
    <div className="pager">
      <span>
        {total ? page * size + 1 : 0}–{Math.min((page + 1) * size, total)} of{" "}
        {total}
      </span>
      <button
        aria-label="Previous page"
        disabled={page === 0}
        onClick={() => change(page - 1)}
      >
        <ArrowLeft size={16} />
      </button>
      <button
        aria-label="Next page"
        disabled={(page + 1) * size >= total}
        onClick={() => change(page + 1)}
      >
        <ArrowRight size={16} />
      </button>
    </div>
  );
}
export function JsonView({ value, label }: { value: unknown; label: string }) {
  return (
    <details className="json">
      <summary>{label}</summary>
      <pre>{JSON.stringify(value, null, 2)}</pre>
    </details>
  );
}
