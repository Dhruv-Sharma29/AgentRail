import { useEffect, useRef, type ReactNode } from "react";
export function Modal({
  titleId,
  busy = false,
  close,
  children,
}: {
  titleId: string;
  busy?: boolean;
  close: () => void;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    dialog?.showModal();
    return () => dialog?.close();
  }, []);
  return (
    <dialog
      ref={ref}
      className="modal"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) close();
      }}
    >
      {children}
    </dialog>
  );
}
