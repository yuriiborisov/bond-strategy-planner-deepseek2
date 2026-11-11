# Архитектура Bond Strategy Planner

Документ фиксирует принятые решения, потоки данных и отличия реализации от `SPEC.md`.
Спецификация — источник требований; здесь — как они реализованы в коде.

## 1. Компоненты

```
┌─────────────────────┐   REST/JSON    ┌────────────────────────────────────────────┐
│ React SPA (Vite)    │ ─────────────► │ Spring Boot API (/api/v1)                  │
│ React Router 7      │ ◄───────────── │                                            │
│ TanStack Query      │                │  CalculationService ─► StrategyCalculator  │
│ Recharts, Tailwind  │                │      Bullet / Barbell / Ladder / Wheel     │
└─────────────────────┘                │  UniverseService  ─► BondMetricsService    │
                                       │  RankingService   ─► AssetCheckService     │
                                       │  CashFlowEngine   ─► TaxCalculator         │
                                       │  MoexIssClient (ExchangeProvider SPI)      │
                                       │  RatingService / RatingImportService       │
                                       │  AiOrchestrator (LlmProvider SPI)           │
                                       └───────────────┬────────────────────────────┘
                                                       │ JPA / Flyway
                                               ┌───────▼────────┐
                                               │ PostgreSQL 16  │  (docker)
                                               │ H2 in-memory   │  (локально, dev)
                                               └────────────────┘
```

- **Backend:** `com.bondplanner` — `api` (controllers, dto, error), `domain` (entity, model),
  `repository`, `service` (`calc`, `strategy`, `checks`, `rating`, `sync`, `tax`, `portfolio`, `ai`, `admin`),
  `provider` (`moex`), `config`, `security`.
- **Frontend:** `app` (router, providers, guards, layout, store), `pages`, `features`
  (`calculation-form`, `results`, `ai`, `save-portfolio`), `shared` (`api`, `ui`, `lib`).

## 2. Поток расчёта

1. `POST /calculations` → Bean Validation DTO.
2. `CalculationService` загружает стратегию и биржу, разбирает `params` (JSON Schema — контракт для UI).
3. `UniverseService` строит снимок: активные бумаги, последние котировки (по `snapshot_ts`),
   купонные календари, рейтинги эмитентов (консервативный score), факторы.
4. Проверка свежести: `< 24 ч` — `FRESH`, `< 7 дней` — `STALE` с предупреждением, старше — `503 DATA_STALE`
   (если не передан `allowStale=true`; UI предлагает повторить на устаревших данных).
5. `StrategyCalculator`:
   - структурные фильтры (`UniverseFilter`) и стратегическое окно;
   - `BondMetricsService` пересчитывает грязную цену, YTM и дюрацию из `bond_cashflows`
     (fallback — YTM из MOEX, если календаря нет);
   - `RankingService` — score по формуле §3.7;
   - `AssetCheckService` — жёсткие гейты, WATCH-причины, `verdict`, `score`, `checks[]`;
   - аллокация целыми лотами, перенос остатков, комиссии, налоги, денежный поток;
   - `CalculationResult` (summary / positions / cashFlowSchedule / warnings / assumptions + extras).
6. Результат и параметры сохраняются в `calculations` вместе с `snapshot_ts` и версией алгоритма,
   ответ получает `calculationId`.

## 3. Стратегии

| Стратегия | Реализация | Ключевые детали |
|---|---|---|
| BULLET | `BulletCalculator` | PMT по аннуитету, 3 сценария ±200 б.п., окно `targetDate ± tolerance`, `allowLaterMaturity`, оферта MATURITY |
| BARBELL | `BarbellCalculator` | валидация `shortMaxYears < longMinYears`, top-K в каждом плече, EQUAL/SCORE, лимит на эмитента, стресс-тест с выпуклостью `C ≈ D²` |
| LADDER | `LadderCalculator` | 1–10 ступеней, допуск 0–180 дней, веса, перенос остатка, запрет дубликатов, расширение окна с предупреждением, rolling-проекция с реинвестом по ставке длинной ступени |
| WHEEL | `WheelCalculator` | база LADDER/BULLET, сценарии `REINVEST` / `CASH_OUT` / `CASH_BUFFER`, покупка целых лотов по правилам `BEST_SCORE`/`SHORTEST`/`LONGEST`/`SAME_BOND`, налоги, XIRR, CAGR, капитализация |

Деньги и проценты — `BigDecimal` (`MathContext(18, HALF_UP)`), округление денег до копеек,
процентов — 4 знака. Лоты не дробятся, остаток не теряется.

## 4. Asset checks

- 13 проверок с весами: `CREDIT_RATING`, `RATING_OUTLOOK`, `RATING_AGE`, `LIST_LEVEL`, `ISSUE_SIZE`,
  `LIQUIDITY`, `YTM_VS_PEER`, `DURATION_FIT`, `PRICE_MOMENTUM`, `REVENUE_GROWTH`, `DEBT_EBITDA`,
  `INTEREST_COVERAGE`, `EVENT_RISK`.
- Жёсткие гейты: рейтинг ниже `minRating`, `NO_RATING` при `requireRating=true`, дефолт/реструктуризация,
  несоответствие сроку, листинг, объём выпуска.
- `UNKNOWN` не считается `PASS`; отсутствие данных по рейтингу/возрасту/событиям даёт `WATCH`
  (или `REJECTED`, если рейтинг обязателен).
- `REJECTED` не попадает в позиции и кандидатов; `WATCH` показывается с предупреждением.
- Результат сохраняется в `result_json`, LLM не может изменить `verdict`.

## 5. Рейтинги

- `RatingNormalizer`: шкала `AAA=100 … D=0`, прогноз `+3/0/−7/−3`, из нескольких агентств берётся
  консервативный (минимальный) score, отображаются все.
- `RatingImportService`: CSV `issuer,inn,agency,rating,outlook,date`, матчинг ИНН → нормализованное имя →
  алиас → Jaro-Winkler ≥ 0.92; несопоставленные эмитенты — в очередь покрытия.
- HTML-коннекторы агентств: `rating_sources.parser_config` (jsoup, селекторы в БД), запускаются вручную
  из админки. По умолчанию рейтинги загружаются CSV: вёрстка источников меняется, нужна юридическая
  проверка ToS (см. §18 спецификации).

## 6. Синхронизация MOEX ISS

- `MoexIssClient` реализует `ExchangeProvider`: `securities+marketdata` по доскам `TQOB/TQCB/TQMB`
  с пагинацией, `bondization` для купонов/амортизации/оферт, rate limit 1 запрос/сек,
  retry с экспоненциальным backoff на 429/5xx, таймауты 5/15 с.
- `SyncService` пишет `sync_runs` (RUNNING/SUCCESS/PARTIAL/FAILED), блокирует параллельные запуски,
  поддерживает `FULL / REFERENCE / QUOTES / CASHFLOWS`.
- Планировщик включается `APP_SYNC_ENABLED=true` (в docker-compose включён); в локальном профиле
  по умолчанию выключен, чтобы запуск не зависел от сети.

## 7. LLM (опционально)

- `AiOrchestrator`: проверка feature flag, согласия (`user_consents`), квоты (`APP_LLM_DAILY_LIMIT`),
  сбор контекста (`AiContextBuilder`, режим `PERCENT_ONLY` — только доли вместо сумм), промпт из
  `prompt_templates` (fallback — встроенный), запись `llm_analyses` (PENDING) и асинхронный запуск.
- `AiExecutor`: провайдер (`STUB`, `LMSTUDIO`, `OPENAI_COMPATIBLE`, `OLLAMA`, `YANDEX` через
  OpenAI-совместимый endpoint), JSON-repair один раз, JSON Schema-проверка, grounding
  (whitelist `secid` и `sourceRefs`), серверный дисклеймер, сохранение токенов/стоимости/latency.
- При недоступности провайдера возвращается детерминированный fallback с `fallback=true`,
  ядро и расчёты не страдают.
- `APP_LLM_ENABLED=false` → AI-эндпоинты отдают `503 LLM_UNAVAILABLE`; UI показывает, что модуль выключен.
- `APP_LLM_PROVIDER=STUB` — детерминированный провайдер для демо и тестов без внешней модели.

## 8. Безопасность и приватность

- JWT access 15 минут, refresh 30 дней (хранится как SHA-256 хэш, ротация при refresh), BCrypt cost 12.
- Анонимные расчёты привязаны к `X-Session-Id`; чтение чужого анонимного расчёта запрещено.
- Портфели и AI — только авторизованным; админ-эндпоинты — `ROLE_ADMIN`.
- CORS whitelist (`localhost`/`127.0.0.1` dev-порты), stateless-сессии, валидация DTO.
- В LLM не передаются email/пароли/токены; контекст — агрегаты и проверенные факты.

## 9. Отличия от спецификации (осознанные)

| Решение | Причина |
|---|---|
| Java 17 + Spring Boot 3.3 вместо Java 21 | локальный toolchain проекта; Spring Boot 3.3 полностью поддерживает 17, Docker-образы на temurin-17 |
| H2 in-memory в локальном профиле, PostgreSQL 16 в Docker | нет доступа к Docker daemon в среде разработки; Flyway-миграции совместимы с обеими БД |
| `TEXT` вместо `JSONB` для JSON-полей | переносимость H2/PostgreSQL; сериализация выполняется Jackson, БД не парсит JSON |
| Нет pgvector/RAG в v1 | задел на v2; таблица `documents` и поиск оставлены, эмбеддинги не включены |
| HTML-парсеры агентств — коннекторы с конфигом, основной канал — CSV | вёрстка и ToS источников требуют юридической проверки; CSV-импорт покрывает AC-11 |
| YandexGPT/GigaChat — через OpenAI-совместимый адаптер | смена провайдера без изменения промптов и схемы; специфичная OAuth-авторизация GigaChat требует договора |
| `@Async` вместо отдельного брокера для AI | v1 single-node; контракт `202 + polling` сохранён |

## 10. Тесты

- Unit: `CashFlowEngineTest` (YTM, дюрация, PMT, XIRR, CAGR, грязная цена), `TaxCalculatorTest`,
  `RatingNormalizerTest`, `IssuerNamesTest`, `AssetCheckServiceTest`.
- Integration: `ApiIntegrationTest` (H2 + MockMvc) — каталог, LADDER/BULLET/WHEEL, `INCONSISTENT_PARAMS`,
  `NO_CANDIDATES`, auth/portfolio/export, импорт CSV рейтингов, `503 LLM_UNAVAILABLE`.
- Frontend: Vitest — форматирование, дефолты/ payload форм, рендер таблицы позиций.
- UI smoke: Playwright (`frontend/scripts/ui-check.mjs`) — главная, калькулятор, результат, логин,
  сохранение портфеля, AI-разбор, админка, мобильная вёрстка; падает на любых console/page errors.

Запуск: `cd backend && mvn test`, `cd frontend && npm test`; UI — backend на `:8085`, Vite на `:5173`,
`node scripts/ui-check.mjs http://127.0.0.1:5173`.
