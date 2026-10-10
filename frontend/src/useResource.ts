import { useEffect, useState } from "react";
import { message } from "./api";
export function useResource<T>(
  load: (signal: AbortSignal) => Promise<T>,
  key: string,
  pollMs = 0,
  stopWhen?: (data: T) => boolean,
) {
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{
    key: string;
    data?: T;
    error?: string;
    loading: boolean;
  }>({ key, loading: true });
  useEffect(() => {
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    async function refresh() {
      let continuePolling = true;
      try {
        const data = await load(controller.signal);
        continuePolling = !stopWhen?.(data);
        if (!controller.signal.aborted) setState({ key, data, loading: false });
      } catch (error) {
        if (!controller.signal.aborted)
          setState((previous) => ({
            key,
            data: previous.key === key ? previous.data : undefined,
            error: message(error),
            loading: false,
          }));
      } finally {
        if (pollMs && continuePolling && !controller.signal.aborted)
          timer = setTimeout(() => {
            if (document.visibilityState === "visible") void refresh();
            else timer = setTimeout(refresh, pollMs);
          }, pollMs);
      }
    }
    setState((previous) => ({
      key,
      data: previous.key === key ? previous.data : undefined,
      loading: true,
    }));
    void refresh();
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
    // Callers put all resource identity parameters into key; each revision refreshes the current loader.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, revision, pollMs]);
  return {
    revision,
    ...(state.key === key ? state : { key, loading: true }),
    reload: () => setRevision((n) => n + 1),
  };
}
