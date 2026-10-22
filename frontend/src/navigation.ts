// Ephemeral draft guard only. No workflow content or credentials are persisted here.
export const draftGuard = { dirty: false };
export const normalizeRoute = (path: string) =>
  /^\/(workflows|approvals|runs|tools|operations)(\/(new|[0-9a-f-]{36}))?$/.test(
    path,
  )
    ? path
    : "/workflows";
