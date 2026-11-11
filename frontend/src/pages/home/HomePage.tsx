import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api } from "../../shared/api/endpoints";
import type { StrategyDto } from "../../shared/api/types";
import { riskClass, riskLabel } from "../../shared/lib/format";
import { Badge, Card, ErrorState, RiskDots, SectionCard } from "../../shared/ui";

export function HomePage() {
  const strategies = useQuery({ queryKey: ["strategies"], queryFn: api.strategies });

  return (
    <div className="space-y-8">
      <section className="relative overflow-hidden rounded-3xl border border-white/10 bg-ink-900/60 p-6 shadow-card sm:p-10">
        <div className="pointer-events-none absolute inset-y-0 right-0 hidden w-1/2 opacity-70 lg:block">
          <svg viewBox="0 0 400 300" className="h-full w-full" aria-hidden="true">
            <defs>
              <linearGradient id="bar" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stopColor="#2dd4bf" stopOpacity="0.55" />
                <stop offset="100%" stopColor="#2dd4bf" stopOpacity="0.02" />
              </linearGradient>
            </defs>
            {[40, 90, 150, 70, 210, 120, 260, 180, 300, 140, 240, 100].map((height, index) => (
              <rect
                key={index}
                x={20 + index * 31}
                y={300 - height}
                width="18"
                height={height}
                rx="4"
                fill="url(#bar)"
              />
            ))}
            <path d="M10 220 C 80 200, 120 120, 190 150 S 300 90, 390 60" stroke="#5eead4" strokeWidth="2.5" fill="none" opacity="0.75" />
          </svg>
        </div>

        <div className="relative max-w-2xl">
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone="info">MOEX ISS</Badge>
            <Badge tone="neutral">снимки для воспроизводимости</Badge>
            <Badge tone="success">asset checks</Badge>
          </div>
          <h1 className="mt-5 text-3xl font-semibold leading-tight text-white sm:text-4xl">
            Портфель облигаций под вашу стратегию — с расчётом и объяснением выбора
          </h1>
          <p className="mt-4 max-w-xl text-base leading-relaxed text-slate-300">
            Выберите структуру, задайте параметры и получите конкретные выпуски, денежный поток, YTM, дюрацию,
            налоги и чек-лист проверок по каждой бумаге.
          </p>
          <div className="mt-6 flex flex-wrap items-center gap-3">
            <a href="#strategies" className="btn-primary">
              Выбрать стратегию
            </a>
            <Link to="/portfolios" className="btn-secondary">
              Мои портфели
            </Link>
          </div>
          <dl className="mt-8 grid max-w-lg grid-cols-3 gap-4 text-sm">
            <div>
              <dt className="stat-label">Стратегии</dt>
              <dd className="stat-value">4</dd>
            </div>
            <div>
              <dt className="stat-label">Расчёт</dt>
              <dd className="stat-value">&lt; 2 с</dd>
            </div>
            <div>
              <dt className="stat-label">Проверки</dt>
              <dd className="stat-value">13</dd>
            </div>
          </dl>
        </div>
      </section>

      <section id="strategies" className="space-y-4">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="text-xl font-semibold text-white">Каталог стратегий</h2>
            <p className="mt-1 text-sm text-slate-400">
              Каждая стратегия — отдельная логика отбора бумаг, аллокации и оценки результата.
            </p>
          </div>
        </div>

        {strategies.isLoading ? (
          <div className="grid gap-4 md:grid-cols-2">
            {[0, 1, 2, 3].map((key) => (
              <Card key={key} className="h-56 animate-pulse" />
            ))}
          </div>
        ) : strategies.isError ? (
          <ErrorState error={strategies.error} onRetry={() => strategies.refetch()} />
        ) : (
          <div className="grid gap-4 md:grid-cols-2">
            {strategies.data?.map((strategy) => (
              <StrategyCard key={strategy.code} strategy={strategy} />
            ))}
          </div>
        )}
      </section>

      <SectionCard
        title="Как это работает"
        subtitle="От параметров до готового портфеля с проверками и объяснениями"
      >
        <ol className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {[
            {
              title: "1. Стратегия",
              text: "Выберите структуру под свою цель: одна дата, два плеча, ступени или реинвест купонов.",
            },
            {
              title: "2. Параметры",
              text: "Сумма, горизонт, допустимый риск. Расширенные фильтры — рейтинг, ликвидность, листинг.",
            },
            {
              title: "3. Расчёт",
              text: "Ядро отбирает бумаги из снимка MOEX ISS, считает лоты, денежный поток, налоги и метрики.",
            },
            {
              title: "4. Проверки",
              text: "Каждая бумага получает вердикт, score и чек-лист с источниками и датами. REJECTED скрывается.",
            },
          ].map((step) => (
            <li key={step.title} className="rounded-2xl border border-white/10 bg-white/[0.02] p-4">
              <p className="text-sm font-semibold text-accent-300">{step.title}</p>
              <p className="mt-2 text-sm leading-relaxed text-slate-400">{step.text}</p>
            </li>
          ))}
        </ol>
      </SectionCard>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card className="card-pad">
          <h2 className="text-base font-semibold text-white">Что учитывает расчёт</h2>
          <ul className="mt-3 space-y-2 text-sm text-slate-400">
            <li>• Грязная цена: clean + НКД + комиссия, лоты и перенос остатков.</li>
            <li>• YTM и дюрация пересчитываются по купонному календарю, а не берутся из API «как есть».</li>
            <li>• НДФЛ с купонов: профиль 13% или прогрессивная шкала 2025 (с учётом годового дохода).</li>
            <li>• Оферты: MATURITY / OPTION / AUTO, отдельное предупреждение о риске.</li>
            <li>• Стресс-тест ставки для «Штанги», rolling-симуляция для «Лестницы», XIRR для «Колеса».</li>
          </ul>
        </Card>
        <Card className="card-pad">
          <h2 className="text-base font-semibold text-white">Проверенные рекомендации</h2>
          <p className="mt-3 text-sm leading-relaxed text-slate-400">
            Перед показом каждая бумага проходит детерминированный чек-лист: кредитный рейтинг и прогноз,
            актуальность рейтинга, листинг, объём выпуска, ликвидность, YTM против медианы, соответствие сроку,
            факторы эмитента и события. LLM не может обойти жёсткие гейты — он только объясняет результат.
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            <Badge tone="success">APPROVED</Badge>
            <Badge tone="warn">WATCH</Badge>
            <Badge tone="danger">REJECTED</Badge>
          </div>
        </Card>
      </div>


    </div>
  );
}

function StrategyCard({ strategy }: { strategy: StrategyDto }) {
  return (
    <Card className="group flex h-full flex-col p-5 transition hover:border-accent-500/40">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold text-white">{strategy.name}</h3>
          <p className="mt-1 text-xs uppercase tracking-wide text-slate-500">{strategy.code}</p>
        </div>
        <span className={riskClass(strategy.riskLevel)}>{riskLabel(strategy.riskLevel)} риск</span>
      </div>
      <p className="mt-3 text-sm leading-relaxed text-slate-400">{strategy.shortDescription}</p>
      <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-slate-500">
        <span>
          Горизонт: {strategy.horizonMinYears ?? "—"}–{strategy.horizonMaxYears ?? "—"} лет
        </span>
        <RiskDots level={strategy.riskLevel} />
      </div>
      {strategy.bestFor.length ? (
        <ul className="mt-4 space-y-1.5 text-sm text-slate-400">
          {strategy.bestFor.slice(0, 2).map((item) => (
            <li key={item} className="flex gap-2">
              <span className="text-accent-400">✓</span>
              <span>{item}</span>
            </li>
          ))}
        </ul>
      ) : null}
      <Link to={`/strategies/${strategy.code}`} className="btn-primary mt-5 w-full">
        Рассчитать
      </Link>
    </Card>
  );
}
