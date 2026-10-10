import type { User } from "./types";
interface Csrf {
  token: string;
  headerName: string;
  parameterName: string;
}
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export class Api {
  private token?: Csrf;
  private tokenRequest?: Promise<Csrf>;
  private epoch = 0;
  onUnauthorized?: () => void;
  clear() {
    this.epoch++;
    this.token = undefined;
    this.tokenRequest = undefined;
  }
  private async csrf(): Promise<Csrf> {
    if (this.token) return this.token;
    if (!this.tokenRequest) {
      const epoch = this.epoch;
      this.tokenRequest = this.send<Csrf>("/api/auth/csrf", "GET")
        .then((token) => {
          if (epoch === this.epoch) this.token = token;
          return token;
        })
        .finally(() => {
          if (epoch === this.epoch) this.tokenRequest = undefined;
        });
    }
    return this.tokenRequest;
  }
  private async send<T>(
    path: string,
    method: string,
    body?: BodyInit,
    headers?: Record<string, string>,
    signal?: AbortSignal,
  ): Promise<T> {
    // Only fixed same-origin API paths may receive session cookies or CSRF headers.
    if (
      !path.startsWith("/api/") ||
      path.includes("\\") ||
      path.includes("://")
    )
      throw new Error("Invalid API path");
    const epoch = this.epoch;
    const response = await fetch(path, {
      method,
      body,
      headers,
      credentials: "same-origin",
      cache: "no-store",
      redirect: "error",
      signal: signal
        ? AbortSignal.any([signal, AbortSignal.timeout(15000)])
        : AbortSignal.timeout(15000),
    });
    if (!response.ok) {
      if (
        response.status === 401 &&
        path !== "/api/auth/login" &&
        epoch === this.epoch
      ) {
        this.clear();
        this.onUnauthorized?.();
      }
      const defaults: Record<number, string> = {
        400: "Check the workflow and required fields.",
        401: "Sign in to continue.",
        403: "Permission denied. Refresh your session before trying again.",
        404: "This item is unavailable.",
        409: "This item changed. Reload it before continuing.",
      };
      throw new ApiError(
        response.status,
        defaults[response.status] ??
          "The server could not complete this request.",
      );
    }
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }
  async request<T>(
    path: string,
    method = "GET",
    value?: unknown,
    signal?: AbortSignal,
  ): Promise<T> {
    if (method === "GET")
      return this.send<T>(path, method, undefined, undefined, signal);
    const token = await this.csrf();
    return this.send<T>(
      path,
      method,
      value === undefined ? undefined : JSON.stringify(value),
      { "Content-Type": "application/json", [token.headerName]: token.token },
      signal,
    );
  }
  async login(username: string, password: string): Promise<User> {
    this.clear();
    const token = await this.csrf();
    const form = new URLSearchParams({
      username,
      password,
      [token.parameterName]: token.token,
    });
    await this.send("/api/auth/login", "POST", form, {
      "Content-Type": "application/x-www-form-urlencoded",
    });
    this.clear();
    await this.csrf();
    return this.request<User>("/api/auth/me");
  }
  async logout() {
    await this.request("/api/auth/logout", "POST");
    this.clear();
  }
}
export const api = new Api();
export const message = (error: unknown) =>
  error instanceof ApiError
    ? error.message
    : "Connection failed. Check the server and try again.";
