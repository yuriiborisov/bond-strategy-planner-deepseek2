export type StrategyCode = "BULLET" | "BARBELL" | "LADDER" | "WHEEL";

export interface StrategyDto {
  code: StrategyCode;
  name: string;
  shortDescription: string;
  riskLevel: number;
  horizonMinYears: number | null;
  horizonMaxYears: number | null;
  paramsSchema: Record<string, unknown>;
  defaultParams: Record<string, unknown>;
  enabled: boolean;
  sortOrder: number;
  bestFor: string[];
  notFor: string[];
  pros: string[];
  cons: string[];
}

export interface ExchangeDto {
  id: number;
  code: string;
  name: string;
  provider: string;
  timezone: string;
  isDefault: boolean;
}

export interface UserDto {
  id: number;
  email: string;
  role: "USER" | "ADMIN";
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  user: UserDto;
}

export interface BondInfo {
  secid: string;
  isin?: string | null;
  name?: string | null;
  issuer?: string | null;
  maturityDate?: string | null;
  offerDate?: string | null;
  effectiveMaturity?: string | null;
  pricePct?: number | null;
  nkdRub?: number | null;
  ytmPct?: number | null;
  durationYears?: number | null;
  lotSize?: number;
  listLevel?: number | null;
  issueSizeRub?: number | null;
  rating?: string | null;
  ratingAgency?: string | null;
  ratingDate?: string | null;
  ratingOutlook?: string | null;
}

export type CheckStatus = "PASS" | "WARN" | "FAIL" | "UNKNOWN";
export type Verdict = "APPROVED" | "WATCH" | "REJECTED";

export interface CheckItem {
  code: string;
  status: CheckStatus;
  value: unknown;
  threshold?: string | null;
  source?: string | null;
  asOf?: string | null;
}

export interface AssetCheck {
  verdict: Verdict;
  score: number;
  checks: CheckItem[];
  explanation?: string;
  asOf?: string;
}

export interface Position {
  rungIndex?: number | null;
  leg?: string | null;
  targetDate?: string | null;
  bond: BondInfo;
  quantity: number;
  amountRub: number;
  weight?: number | null;
  ytmPct?: number | null;
  durationYears?: number | null;
  couponIncomeRub?: number | null;
  costRub?: number | null;
  residualRub?: number | null;
  lots?: number | null;
  reason: string;
  checks?: AssetCheck | null;
}

export interface CashFlowPoint {
  date: string;
  type: string;
  amountRub: number;
}

export interface WarningItem {
  code: string;
  severity: "INFO" | "WARN" | "ERROR" | string;
  message: string;
}

export interface ContributionScenario {
  scenario: string;
  ytmPct: number;
  monthlyContributionRub: number;
  projectedValueRub: number;
  surplusOrGapRub: number;
}

export interface Rung {
  index: number;
  targetDate: string;
  budgetRub: number;
  bond: BondInfo | null;
  quantity: number;
  lots?: number;
  costRub: number;
  residualRub: number;
  couponIncomeRub: number;
  ytmPct?: number | null;
  durationYears?: number | null;
  weight?: number | null;
  reason?: string | null;
  status: string;
  checks?: AssetCheck | null;
}

export interface RollingRow {
  year: number;
  date: string;
  couponGrossRub: number;
  taxRub: number;
  maturitiesRub: number;
  reinvestedRub: number;
  projectedCapitalRub: number;
}

export interface RollingProjection {
  horizonYears: number;
  reinvestRatePct: number;
  rows: RollingRow[];
  totalCouponsRub: number;
  totalTaxRub: number;
  totalReinvestedRub: number;
  finalCapitalRub: number;
}

export interface Leg {
  leg: string;
  targetShare: number;
  amountRub: number;
  investedRub: number;
  weightedYtmPct?: number | null;
  weightedDurationYears?: number | null;
  bondsCount: number;
}

export interface StressRow {
  deltaBp: number;
  longLegChangeRub: number;
  portfolioChangeRub: number;
  portfolioChangePct?: number | null;
}

export interface ComparisonRow {
  scenario: string;
  finalCapitalRub: number;
  xirrPct?: number | null;
  totalCouponsGrossRub: number;
  totalTaxRub: number;
  totalReinvestedRub: number;
  cashResidualRub: number;
}

export interface CapitalizationPoint {
  date: string;
  year: number;
  scenario: string;
  valueRub: number;
}

export interface Reinvestment {
  date: string;
  amountRub: number;
  bond: { secid: string; name: string; maturityDate?: string | null };
  quantity: number;
  pricePct?: number | null;
}

export interface FundingRow {
  date: string;
  period: string;
  contributionRub: number;
  purchasesRub: number;
  cashRub: number;
  investedRub: number;
  portfolioValueRub: number;
  label?: string;
}

export interface FundingPurchase {
  date: string;
  secid: string;
  name?: string;
  quantity: number;
  amountRub: number;
  kind: string;
}

export interface FundingSchedule {
  startDate: string;
  endDate: string;
  monthlyContributionRub: number;
  initialContributionRub: number;
  totalContributionsRub: number;
  totalPurchasesRub: number;
  cashResidualRub: number;
  investedRub: number;
  rows: FundingRow[];
  purchases: FundingPurchase[];
  note?: string;
}

export interface Candidate {
  bond: BondInfo;
  score: number;
  reason: string;
  checks?: AssetCheck | null;
}

export interface CalculationResult {
  calculationId: number;
  strategy: StrategyCode | string;
  exchange: string;
  snapshotTs: string;
  dataFreshness: "FRESH" | "STALE" | "EXPIRED" | "UNKNOWN";
  algorithmVersion?: string;
  anonymousSessionId?: string;
  summary: Record<string, number | string | boolean | null>;
  positions: Position[];
  cashFlowSchedule: CashFlowPoint[];
  warnings: WarningItem[];
  assumptions: Record<string, unknown>;
  // bullet
  targetDate?: string;
  monthlyContributionRub?: number;
  contributionScenarios?: ContributionScenario[];
  candidates?: Candidate[];
  // barbell
  legs?: Leg[];
  stressTest?: StressRow[];
  // ladder
  rungs?: Rung[];
  rollingProjection?: RollingProjection | null;
  cashResidualRub?: number;
  // wheel
  comparison?: ComparisonRow[];
  capitalizationSeries?: CapitalizationPoint[];
  reinvestments?: Reinvestment[];
  fundingSchedule?: FundingSchedule;
  baseStrategy?: string;
  horizonYears?: number;
  reinvestRule?: string;
}

export interface PortfolioSummary {
  id: number;
  name: string;
  strategy: string;
  calculationId: number;
  createdAt: string;
  updatedAt?: string;
  snapshotTs: string;
  positionsCount: number;
  totalAmountRub: number;
  positions: Array<{
    secid: string;
    name?: string;
    issuer?: string;
    maturityDate?: string;
    quantity: number;
    amountRub: number;
    weight?: number;
    ytmPct?: number;
    reason?: string;
    rungIndex?: number;
  }>;
  result?: CalculationResult;
}

export interface AiAnalysis {
  analysisId: number;
  type: string;
  status: "PENDING" | "RUNNING" | "DONE" | "FAILED" | "REJECTED";
  provider: string;
  model: string;
  promptVersion: string;
  createdAt: string;
  finishedAt?: string | null;
  error?: string | null;
  usage?: { tokensIn?: number; tokensOut?: number; costUsd?: number; latencyMs?: number };
  result?: AiResult | null;
  summary?: string;
  portfolioHealth?: string;
  disclaimer?: string;
}

export interface AiResult {
  summary: string;
  portfolioHealth: string;
  nextActions?: Array<{
    date: string;
    type: string;
    priority: "LOW" | "MEDIUM" | "HIGH";
    title: string;
    details?: string;
    relatedSecids?: string[];
    sourceRefs?: string[];
  }>;
  risks?: Array<{ code: string; severity: string; message: string }>;
  watchlist?: Array<{ secid: string; reason: string }>;
  questions?: string[];
  sources?: Array<{ ref: string; title: string; url?: string }>;
  candidates?: Array<{ secid: string; why: string; risks: string; checkBeforeBuy?: string }>;
  disclaimer?: string;
  grounding?: { removed?: number; issues?: string[] };
  fallback?: boolean;
}

export interface AiHealth {
  enabled: boolean;
  provider: string;
  model: string;
  healthy: boolean;
  privacyMode: string;
}

export interface SyncRun {
  id: number;
  kind: string;
  provider: string;
  exchange?: string | null;
  status: string;
  startedAt: string;
  finishedAt?: string | null;
  rowsUpserted: number;
  error?: string | null;
}

export interface AdminStats {
  bonds: number;
  issuers: number;
  ratings: number;
  ratedIssuers: number;
  ratingCoveragePct: number;
  calculations: number;
  portfolios: number;
  aiAnalyses: number;
  latestSnapshotTs?: string | null;
  snapshotAgeMinutes?: number;
  syncRuns24h: number;
  llmEnabled: boolean;
  llmProvider: string;
  lastSync?: SyncRun | null;
}

export interface RatingSource {
  id: number;
  code: string;
  name: string;
  baseUrl: string;
  sourceType: string;
  enabled: boolean;
  priority: number;
  cron?: string | null;
  lastRunAt?: string | null;
  lastStatus?: string | null;
}

export interface ApiProblem {
  type?: string;
  title?: string;
  status?: number;
  code?: string;
  detail?: string;
  instance?: string;
  suggestions?: string[];
  errors?: Record<string, string>;
}
