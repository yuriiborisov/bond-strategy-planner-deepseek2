import type { ApiProblem } from "./types";

export const API_BASE: string = import.meta.env.VITE_API_BASE_URL ?? "/api/v1";
const SESSION_KEY = "bsp.session";
const AUTH_KEY = "bsp.auth";

export interface StoredAuth {
  accessToken: string;
  refreshToken: string;
  user: { id: number; email: string; role: "USER" | "ADMIN" };
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly problem: ApiProblem | null;
  readonly suggestions: string[];

  constructor(status: number, code: string, message: string, problem: ApiProblem | null = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.problem = problem;
    this.suggestions = problem?.suggestions ?? [];
  }
}

export function getSessionId(): string {
  let value = localStorage.getItem(SESSION_KEY);
  if (!value) {
    value =
      typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
        ? crypto.randomUUID()
        : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
    localStorage.setItem(SESSION_KEY, value);
  }
  return value;
}

export function loadAuth(): StoredAuth | null {
  const raw = localStorage.getItem(AUTH_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredAuth;
  } catch {
    return null;
  }
}

export function saveAuth(auth: StoredAuth | null): void {
  if (auth) {
    localStorage.setItem(AUTH_KEY, JSON.stringify(auth));
  } else {
    localStorage.removeItem(AUTH_KEY);
  }
}

export function accessToken(): string | null {
  return loadAuth()?.accessToken ?? null;
}

async function parseError(response: Response): Promise<ApiError> {
  let problem: ApiProblem | null = null;
  try {
    const contentType = response.headers.get("content-type") ?? "";
    if (contentType.includes("json")) {
      problem = (await response.json()) as ApiProblem;
    }
  } catch {
    problem = null;
  }
  const code = problem?.code ?? `HTTP_${response.status}`;
  const message = problem?.detail ?? problem?.title ?? `Ошибка запроса (${response.status})`;
  return new ApiError(response.status, code, message, problem);
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "PUT" | "DELETE";
  body?: unknown;
  auth?: boolean;
  signal?: AbortSignal;
  raw?: boolean;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {
    Accept: "application/json",
    "X-Session-Id": getSessionId(),
  };
  const token = accessToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  let body: BodyInit | undefined;
  if (options.body instanceof FormData) {
    body = options.body;
  } else if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.body);
  }
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method: options.method ?? "GET",
      headers,
      body,
      signal: options.signal,
    });
  } catch (error) {
    if ((error as Error).name === "AbortError") throw error;
    throw new ApiError(0, "NETWORK_ERROR", "Сервер недоступен. Проверьте соединение и повторите запрос.");
  }
  if (!response.ok) {
    throw await parseError(response);
  }
  if (options.raw) {
    return response as unknown as T;
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export async function download(path: string, fallbackName: string): Promise<void> {
  const token = accessToken();
  const response = await fetch(`${API_BASE}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) throw await parseError(response);
  const blob = await response.blob();
  const disposition = response.headers.get("content-disposition") ?? "";
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = match?.[1] ?? fallbackName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
