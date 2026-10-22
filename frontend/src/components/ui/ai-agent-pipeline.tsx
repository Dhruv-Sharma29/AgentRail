"use client";

import { useEffect, useId, useState, useSyncExternalStore } from "react";
import { AnimatePresence, LazyMotion, domAnimation, m } from "framer-motion";
import { Pause, Play } from "lucide-react";

// Illustrative steps, never connected to the planner or execution API.
const steps = [
  { label: "Receive input", detail: "Support issue", x: 16, y: 24 },
  { label: "AI proposes", detail: "Approved tools", x: 220, y: 24 },
  { label: "Java validates", detail: "Policy + arguments", x: 424, y: 24 },
  { label: "Human approval", detail: "Review exact action", x: 424, y: 112 },
  { label: "Execute & record", detail: "Durable audit trail", x: 220, y: 112 },
];
const messages = [
  { node: 0, text: 'Received: "Cannot log in. Password reset failed."' },
  { node: 1, text: "AI proposes classify_issue and search_documentation." },
  { node: 2, text: "Java checks tool permissions, arguments and run limits." },
  { node: 3, text: "Sensitive action paused. Awaiting human approval." },
  { node: 3, text: "Example reviewer approves the frozen result action." },
  { node: 4, text: "Java records the result and persists the audit trail." },
];
const paths = [
  "M156,51 L220,51",
  "M360,51 L424,51",
  "M494,78 L494,112",
  "M424,139 L360,139",
];

// Subscribe directly: the installed Motion hook only captures the initial preference.
function subscribeToReducedMotion(onChange: () => void) {
  const query = window.matchMedia("(prefers-reduced-motion: reduce)");
  query.addEventListener("change", onChange);
  return () => query.removeEventListener("change", onChange);
}
function getReducedMotion() {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export default function EnterpriseAIPipeline() {
  const markerId = useId();
  const reducedMotion = useSyncExternalStore(
    subscribeToReducedMotion,
    getReducedMotion,
    () => true,
  );
  const [enabled, setEnabled] = useState(true);
  const [messageIndex, setMessageIndex] = useState(0);
  const playing = enabled && !reducedMotion;
  const current = messages[messageIndex];

  useEffect(() => {
    if (!playing) return;
    const interval = window.setInterval(() => {
      setMessageIndex((previous) => (previous + 1) % messages.length);
    }, 2700);
    return () => window.clearInterval(interval);
  }, [playing]);

  return (
    <LazyMotion features={domAnimation}>
      <section
        aria-label="AgentRail workflow preview"
        className="@container w-full max-w-[660px] overflow-hidden rounded-2xl border border-[#9aebbb]/20 bg-[#101618] text-[#edf2f0] shadow-[0_20px_70px_-35px_#050909]"
      >
        <div className="flex items-center justify-between gap-3 border-b border-white/10 px-4 py-3">
          <div className="flex items-center gap-2">
            <m.span
              className="size-1.5 rounded-full bg-[#9aebbb]"
              animate={{ opacity: playing ? [1, 0.4, 1] : 1 }}
              transition={{ duration: 2, repeat: playing ? Infinity : 0 }}
              aria-hidden="true"
            />
            <span className="font-mono text-[10px] tracking-widest text-[#b8c6c4]">
              WORKFLOW PREVIEW
            </span>
          </div>
          <button
            type="button"
            aria-label={playing ? "Pause preview" : "Play preview"}
            disabled={!!reducedMotion}
            title={
              reducedMotion
                ? "Animations disabled by your reduced motion setting"
                : undefined
            }
            onClick={() => setEnabled(!enabled)}
            className="pipeline-control"
          >
            {playing ? <Pause size={13} /> : <Play size={13} />}
          </button>
        </div>

        <svg
          viewBox="0 0 580 184"
          className="hidden w-full @min-[480px]:block"
          aria-hidden="true"
        >
          <defs>
            <marker
              id={markerId}
              viewBox="0 0 10 10"
              refX="8"
              refY="5"
              markerWidth="5"
              markerHeight="5"
              orient="auto"
            >
              <path
                d="M2 1.5L7.5 5L2 8.5"
                fill="none"
                stroke="#9aebbb"
                strokeWidth="1.6"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </marker>
          </defs>
          {paths.map((path, index) => (
            <g key={path}>
              <path
                d={path}
                fill="none"
                stroke="#869797"
                strokeWidth="1.3"
                strokeDasharray="3 5"
                markerEnd={`url(#${markerId})`}
              />
              {playing && (
                <circle r="2.5" fill="#9aebbb">
                  <animateMotion
                    dur="1.6s"
                    repeatCount="indefinite"
                    begin={`${index * 0.35}s`}
                    path={path}
                  />
                </circle>
              )}
            </g>
          ))}
          {steps.map((step, index) => {
            const active = current.node === index;
            return (
              <g key={step.label}>
                <m.rect
                  x={step.x}
                  y={step.y}
                  width="140"
                  height="54"
                  rx="9"
                  fill={active ? "#1b3028" : "#16221e"}
                  animate={{ stroke: active ? "#9aebbb" : "#315344" }}
                  transition={{ duration: playing ? 0.3 : 0 }}
                />
                <text
                  x={step.x + 14}
                  y={step.y + 13}
                  fontSize="8.5"
                  fill="#9badad"
                  fontFamily="monospace"
                  letterSpacing="1"
                >
                  0{index + 1}
                </text>
                <m.circle
                  cx={step.x + 124}
                  cy={step.y + 12}
                  r="2.5"
                  fill={active ? "#9aebbb" : "#869797"}
                  animate={{ opacity: active && playing ? [0.4, 1, 0.4] : 1 }}
                  transition={{
                    duration: 1.6,
                    repeat: active && playing ? Infinity : 0,
                  }}
                />
                <text
                  x={step.x + 14}
                  y={step.y + 30}
                  fontSize="12"
                  fill="#edf2f0"
                  fontFamily="system-ui"
                >
                  {step.label}
                </text>
                <text
                  x={step.x + 14}
                  y={step.y + 45}
                  fontSize="9"
                  fill="#9badad"
                  fontFamily="system-ui"
                >
                  {step.detail}
                </text>
              </g>
            );
          })}
          <text
            x="20"
            y="136"
            fontSize="10"
            fill="#9badad"
            fontFamily="monospace"
          >
            AI PROPOSES.
          </text>
          <text
            x="20"
            y="152"
            fontSize="10"
            fill="#9aebbb"
            fontFamily="monospace"
          >
            YOU STAY IN CONTROL.
          </text>
        </svg>

        <ol className="grid gap-3 px-5 py-5 @min-[480px]:sr-only">
          {steps.map((step, index) => (
            <li
              key={step.label}
              className="flex items-center gap-3 text-[13px]"
            >
              <span className="flex size-7 shrink-0 items-center justify-center rounded-md border border-[#9aebbb]/25 font-mono text-[10px] text-[#9aebbb]">
                0{index + 1}
              </span>
              <div className="flex flex-1 flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                <span>{step.label}</span>
                <span className="text-[11px] text-[#9badad]">
                  {step.detail}
                </span>
              </div>
            </li>
          ))}
        </ol>

        <div className="flex min-h-16 items-center gap-3 border-t border-white/10 px-4 py-3 font-mono text-[11px] leading-relaxed">
          <span className="text-[#9aebbb]" aria-hidden="true">
            ›
          </span>
          <div
            className="min-w-0 flex-1"
            aria-live="off"
            data-testid="pipeline-message"
          >
            <AnimatePresence mode="wait" initial={false}>
              <m.div
                key={messageIndex}
                initial={{ opacity: playing ? 0 : 1, y: playing ? 4 : 0 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: playing ? 0 : 1 }}
                transition={{ duration: playing ? 0.2 : 0 }}
              >
                {current.text}
              </m.div>
            </AnimatePresence>
          </div>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-white/10 px-4 py-3 text-[10px] text-[#9badad]">
          <span>Preview only · no actions run</span>
          <span className="font-mono text-[#b8c6c4]">
            JAVA CONTROLS EXECUTION
          </span>
        </div>
      </section>
    </LazyMotion>
  );
}
