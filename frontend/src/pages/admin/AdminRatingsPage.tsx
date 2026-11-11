import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { api } from "../../shared/api/endpoints";
import type { RatingSource } from "../../shared/api/types";
import { fmtDateTime, fmtNumber } from "../../shared/lib/format";
import { Badge, Button, Card, ErrorState, Field, LoadingBlock, SectionCard, TextInput } from "../../shared/ui";

export function AdminRatingsPage() {
  const queryClient = useQueryClient();
  const sources = useQuery({ queryKey: ["rating-sources"], queryFn: api.admin.ratingSources });
  const unmatched = useQuery({ queryKey: ["rating-unmatched"], queryFn: api.admin.unmatched });
  const fileRef = useRef<HTMLInputElement>(null);
  const [importResult, setImportResult] = useState<string | null>(null);
  const [importError, setImportError] = useState<string | null>(null);
  const [issuerId, setIssuerId] = useState("");
  const [alias, setAlias] = useState("");
  const [aliasMessage, setAliasMessage] = useState<string | null>(null);
  const [sourceError, setSourceError] = useState<string | null>(null);

  const importMutation = useMutation({
    mutationFn: (file: File) => api.admin.importRatings(file),
    onSuccess: (result) => {
      setImportError(null);
      setImportResult(
        `Импортировано: ${result.imported}, создано эмитентов: ${result.createdIssuers}, ошибок: ${result.errors.length}, несопоставлено: ${result.unmatched.length}`,
      );
      queryClient.invalidateQueries({ queryKey: ["rating-unmatched"] });
      queryClient.invalidateQueries({ queryKey: ["admin-stats"] });
    },
    onError: (error: Error) => {
      setImportResult(null);
      setImportError(error.message);
    },
  });

  const patchSource = useMutation({
    mutationFn: ({ id, body }: { id: number; body: { enabled?: boolean; priority?: number } }) =>
      api.admin.patchRatingSource(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["rating-sources"] }),
    onError: (error: Error) => setSourceError(error.message),
  });

  const syncSource = useMutation({
    mutationFn: (code: string) => api.admin.syncRatings(code),
    onSuccess: () => {
      setSourceError(null);
      queryClient.invalidateQueries({ queryKey: ["rating-sources"] });
      queryClient.invalidateQueries({ queryKey: ["rating-unmatched"] });
    },
    onError: (error: Error) => setSourceError(error.message),
  });

  const addAlias = useMutation({
    mutationFn: () => api.admin.addAlias(Number(issuerId), alias.trim()),
    onSuccess: () => {
      setAlias("");
      setAliasMessage("Алиас добавлен");
      queryClient.invalidateQueries({ queryKey: ["rating-unmatched"] });
    },
    onError: (error: Error) => setAliasMessage(error.message),
  });

  return (
    <div className="space-y-5">
      <SectionCard
        title="Импорт рейтингов из CSV"
        subtitle="Формат: issuer,inn,agency,rating,outlook,date. Fallback и ручные правки администратора"
      >
        <div className="flex flex-wrap items-center gap-3">
          <input
            ref={fileRef}
            type="file"
            accept=".csv,text/csv"
            className="hidden"
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) importMutation.mutate(file);
              event.target.value = "";
            }}
          />
          <Button onClick={() => fileRef.current?.click()} disabled={importMutation.isPending}>
            {importMutation.isPending ? "Импортируем…" : "Выбрать CSV"}
          </Button>
          <span className="text-xs text-slate-500">
            Пример: ПАО «Газпром»,7736050003,ACRA,AA+,STABLE,01.10.2026
          </span>
        </div>
        {importResult ? <p className="mt-3 text-sm text-emerald-300">{importResult}</p> : null}
        {importError ? <p className="mt-3 text-sm text-rose-300">{importError}</p> : null}
      </SectionCard>

      <SectionCard title="Источники рейтингов" subtitle="Приоритет и включение меняются без релиза">
        {sources.isLoading ? <LoadingBlock /> : null}
        {sources.isError ? <ErrorState error={sources.error} onRetry={() => sources.refetch()} /> : null}
        {sourceError ? <p className="mb-3 text-sm text-rose-300">{sourceError}</p> : null}
        <div className="table-wrap">
          <table className="w-full min-w-[720px] text-sm">
            <thead>
              <tr className="border-b border-white/10 text-left text-xs uppercase tracking-wide text-slate-500">
                <th className="px-3 py-2">Код</th>
                <th className="px-3 py-2">Тип</th>
                <th className="px-3 py-2">Приоритет</th>
                <th className="px-3 py-2">Включён</th>
                <th className="px-3 py-2">Последний запуск</th>
                <th className="px-3 py-2">Статус</th>
                <th className="px-3 py-2">Действия</th>
              </tr>
            </thead>
            <tbody>
              {(sources.data ?? []).map((source) => (
                <SourceRow
                  key={source.id}
                  source={source}
                  onToggle={(enabled) => patchSource.mutate({ id: source.id, body: { enabled } })}
                  onPriority={(priority) => patchSource.mutate({ id: source.id, body: { priority } })}
                  onSync={() => syncSource.mutate(source.code)}
                  syncing={syncSource.isPending && syncSource.variables === source.code}
                />
              ))}
            </tbody>
          </table>
        </div>
      </SectionCard>

      <div className="grid gap-4 lg:grid-cols-2">
        <SectionCard title="Эмитенты без рейтинга" subtitle="Очередь покрытия: добавьте CSV или алиас">
          {unmatched.isLoading ? <LoadingBlock /> : null}
          {unmatched.isError ? <ErrorState error={unmatched.error} onRetry={() => unmatched.refetch()} /> : null}
          {(unmatched.data ?? []).length === 0 ? (
            <p className="text-sm text-slate-400">Все эмитенты покрыты рейтингами.</p>
          ) : (
            <ul className="max-h-72 space-y-2 overflow-y-auto text-sm">
              {unmatched.data?.map((issuer) => (
                <li key={issuer.issuerId} className="flex items-center justify-between rounded-xl border border-white/10 px-3 py-2">
                  <span className="text-slate-300">
                    {issuer.name}
                    {issuer.inn ? <span className="ml-2 text-xs text-slate-500">ИНН {issuer.inn}</span> : null}
                  </span>
                  <Badge tone="warn">{issuer.reason}</Badge>
                </li>
              ))}
            </ul>
          )}
        </SectionCard>

        <SectionCard title="Алиас эмитента" subtitle="Связывает название из источника рейтинга с эмитентом">
          <div className="space-y-3">
            <Field label="ID эмитента" htmlFor="issuer-id">
              <TextInput id="issuer-id" value={issuerId} onChange={(event) => setIssuerId(event.target.value)} placeholder="например, 3" />
            </Field>
            <Field label="Алиас" htmlFor="issuer-alias">
              <TextInput id="issuer-alias" value={alias} onChange={(event) => setAlias(event.target.value)} placeholder="ПАО «Газпром»" />
            </Field>
            <Button
              onClick={() => addAlias.mutate()}
              disabled={!issuerId || !alias.trim() || addAlias.isPending}
            >
              Добавить алиас
            </Button>
            {aliasMessage ? <p className="text-sm text-slate-300">{aliasMessage}</p> : null}
          </div>
        </SectionCard>
      </div>
    </div>
  );
}

function SourceRow({
  source,
  onToggle,
  onPriority,
  onSync,
  syncing,
}: {
  source: RatingSource;
  onToggle: (enabled: boolean) => void;
  onPriority: (priority: number) => void;
  onSync: () => void;
  syncing: boolean;
}) {
  const [priority, setPriority] = useState(String(source.priority));
  return (
    <tr className="border-b border-white/5 last:border-0">
      <td className="px-3 py-2">
        <div className="text-slate-200">{source.code}</div>
        <div className="text-xs text-slate-500">{source.name}</div>
      </td>
      <td className="px-3 py-2 text-slate-400">{source.sourceType}</td>
      <td className="px-3 py-2">
        <div className="flex items-center gap-1">
          <input
            className="input w-16 px-2 py-1 text-xs"
            value={priority}
            onChange={(event) => setPriority(event.target.value)}
            onBlur={() => {
              const value = Number(priority);
              if (Number.isFinite(value) && value !== source.priority) onPriority(value);
            }}
          />
        </div>
      </td>
      <td className="px-3 py-2">
        <input type="checkbox" checked={source.enabled} onChange={(event) => onToggle(event.target.checked)} className="h-4 w-4 accent-teal-400" />
      </td>
      <td className="px-3 py-2 text-slate-400">{fmtDateTime(source.lastRunAt)}</td>
      <td className="px-3 py-2">
        <Badge tone={source.lastStatus === "SUCCESS" ? "success" : source.lastStatus === "FAILED" ? "danger" : "neutral"}>
          {source.lastStatus ?? "—"}
        </Badge>
      </td>
      <td className="px-3 py-2">
        <Button variant="ghost" size="sm" onClick={onSync} disabled={syncing || !source.enabled}>
          {syncing ? "…" : "Синхронизировать"}
        </Button>
      </td>
    </tr>
  );
}
