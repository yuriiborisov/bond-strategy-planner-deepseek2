import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { api } from "../../shared/api/endpoints";
import { fmtDateTime, strategyLabel } from "../../shared/lib/format";
import { Badge, Card, ErrorState, LoadingBlock, SectionCard } from "../../shared/ui";
import { AnalysisCard } from "../../features/ai/AiSection";

export function AiAnalysisPage() {
  const { id } = useParams<{ id: string }>();
  const analysisId = Number(id);
  const query = useQuery({
    queryKey: ["ai-analysis", analysisId],
    queryFn: () => api.aiAnalysis(analysisId),
    enabled: Number.isFinite(analysisId),
    refetchInterval: (q) => {
      const data = q.state.data;
      return data && (data.status === "PENDING" || data.status === "RUNNING") ? 1500 : false;
    },
  });

  if (query.isLoading) return <LoadingBlock label="Загружаем AI-анализ…" />;
  if (query.isError || !query.data) return <ErrorState error={query.error} onRetry={() => query.refetch()} />;

  const analysis = query.data;

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-2 text-sm text-slate-500">
        <Link to="/portfolios" className="hover:text-slate-300">
          Портфели
        </Link>
        <span>/</span>
        <span className="text-slate-300">AI-анализ #{analysis.analysisId}</span>
      </div>

      <Card className="card-pad">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-xl font-semibold text-white">AI-анализ портфеля</h1>
            <p className="mt-1 text-xs text-slate-500">
              {strategyLabel(analysis.type)} · {fmtDateTime(analysis.createdAt)} · {analysis.provider} ·{" "}
              {analysis.model}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <Badge tone={analysis.status === "DONE" ? "success" : analysis.status === "FAILED" ? "danger" : "warn"}>
              {analysis.status}
            </Badge>
            <Badge tone="neutral">промпт {analysis.promptVersion}</Badge>
          </div>
        </div>
      </Card>

      <SectionCard title="Результат" subtitle="Проверен по JSON-схеме и grounding-валидатору">
        <AnalysisCard analysis={analysis} />
      </SectionCard>
    </div>
  );
}
