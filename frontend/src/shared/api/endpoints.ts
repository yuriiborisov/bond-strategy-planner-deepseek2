import { download, request } from "./client";
import type {
  AdminStats,
  AiAnalysis,
  AiHealth,
  CalculationResult,
  ExchangeDto,
  PortfolioSummary,
  RatingSource,
  StrategyDto,
  SyncRun,
  TokenResponse,
  UserDto,
} from "./types";

export const api = {
  strategies: () => request<StrategyDto[]>("/strategies"),
  strategy: (code: string) => request<StrategyDto>(`/strategies/${code}`),
  exchanges: () => request<ExchangeDto[]>("/exchanges"),

  calculate: (payload: { strategy: string; exchange: string; params: Record<string, unknown>; allowStale?: boolean }) =>
    request<CalculationResult>("/calculations", { method: "POST", body: payload }),
  calculation: (id: number) => request<CalculationResult>(`/calculations/${id}`),
  saveCalculation: (id: number, name: string) =>
    request<PortfolioSummary>(`/calculations/${id}/save`, { method: "POST", body: { name } }),

  register: (email: string, password: string) =>
    request<TokenResponse>("/auth/register", { method: "POST", body: { email, password } }),
  login: (email: string, password: string) =>
    request<TokenResponse>("/auth/login", { method: "POST", body: { email, password } }),
  me: () => request<UserDto>("/auth/me"),

  portfolios: (page = 0, size = 50) =>
    request<{ content: PortfolioSummary[]; page: number; size: number; totalElements: number }>(
      `/portfolios?page=${page}&size=${size}`,
    ),
  portfolio: (id: number) => request<PortfolioSummary>(`/portfolios/${id}`),
  renamePortfolio: (id: number, name: string) =>
    request(`/portfolios/${id}`, { method: "PATCH", body: { name } }),
  deletePortfolio: (id: number) => request<void>(`/portfolios/${id}`, { method: "DELETE" }),
  exportPortfolio: (id: number, format: "csv" | "xlsx") =>
    download(`/portfolios/${id}/export?format=${format}`, `portfolio-${id}.${format}`),

  aiHealth: () => request<AiHealth>("/ai/health"),
  aiConsent: () => request<{ granted: boolean }>("/ai/consent"),
  grantConsent: () => request<{ granted: boolean }>("/ai/consent", { method: "POST", body: {} }),
  revokeConsent: () => request<{ granted: boolean }>("/ai/consent", { method: "DELETE" }),
  aiUsage: () =>
    request<{ enabled: boolean; usedToday: number; dailyLimit: number; remainingToday: number | null }>(
      "/ai/usage",
    ),
  startPortfolioReview: (portfolioId: number) =>
    request<{ analysisId: number; status: string }>("/ai/portfolio-reviews", {
      method: "POST",
      body: { portfolioId },
    }),
  startDiscovery: (calculationId: number) =>
    request<{ analysisId: number; status: string }>("/ai/discovery", {
      method: "POST",
      body: { calculationId },
    }),
  aiAnalysis: (id: number) => request<AiAnalysis>(`/ai/analyses/${id}`),
  aiAnalyses: (type?: string, page = 0, size = 20) =>
    request<{ content: AiAnalysis[]; totalElements: number }>(
      `/ai/analyses?page=${page}&size=${size}${type ? `&type=${type}` : ""}`,
    ),

  admin: {
    stats: () => request<AdminStats>("/admin/stats"),
    sync: (exchange: string, kind: string) =>
      request<SyncRun>("/admin/sync", { method: "POST", body: { exchange, kind } }),
    syncRuns: () => request<SyncRun[]>("/admin/sync/runs"),
    importRatings: (file: File) => {
      const form = new FormData();
      form.append("file", file);
      return request<{
        imported: number;
        createdIssuers: number;
        errors: Array<Record<string, unknown>>;
        unmatched: Array<Record<string, unknown>>;
      }>("/admin/ratings/import", { method: "POST", body: form });
    },
    syncRatings: (source: string) =>
      request<Record<string, unknown>>("/admin/ratings/sync", { method: "POST", body: { source } }),
    unmatched: () =>
      request<Array<{ issuerId: number; name: string; inn?: string; reason: string }>>(
        "/admin/ratings/unmatched",
      ),
    ratingSources: () => request<RatingSource[]>("/admin/rating-sources"),
    patchRatingSource: (id: number, body: { enabled?: boolean; priority?: number; cron?: string }) =>
      request<Record<string, unknown>>(`/admin/rating-sources/${id}`, { method: "PATCH", body }),
    addAlias: (issuerId: number, alias: string) =>
      request<Record<string, unknown>>("/admin/issuer-aliases", { method: "POST", body: { issuerId, alias } }),
    aiAnalyses: (status?: string) =>
      request<{ content: AiAnalysis[]; totalElements: number }>(
        `/admin/ai/analyses${status ? `?status=${status}` : ""}`,
      ),
    aiConfig: (body: { enabled?: boolean; dailyLimit?: number }) =>
      request<Record<string, unknown>>("/admin/ai/config", { method: "PATCH", body }),
  },
};
