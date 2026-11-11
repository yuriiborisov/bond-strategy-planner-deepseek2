# Bond Strategy Planner

Веб-приложение для подбора, расчёта и моделирования портфелей облигаций под одну из четырёх стратегий:
**Пуля (BULLET)**, **Штанга (BARBELL)**, **Лестница (LADDER)**, **Колесо (WHEEL)**.

Источник рыночных данных — MOEX ISS API (снимки сохраняются в БД для воспроизводимости), кредитные
рейтинги — импорт CSV / коннекторы рейтинговых агентств. Каждая рекомендация проходит детерминированную
проверку (asset checks): рейтинг и прогноз, ликвидность, листинг, объём выпуска, факторы и события.
LLM-слой опционален, выключен по умолчанию и **не участвует в расчётах**.

> Сервис носит информационный характер и не является индивидуальной инвестиционной рекомендацией.

Полная спецификация — [SPEC.md](SPEC.md), архитектура и принятые решения — [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Что реализовано

| Блок | Состояние |
|---|---|
| Каталог стратегий, JSON Schema параметров, default-параметры | ✅ |
| Расчётное ядро: YTM, дюрация, NKD, лоты, комиссии, налоги, cash flow, XIRR/CAGR | ✅ |
| BULLET: PMT, 3 сценария, окно цели, проверка достижимости | ✅ |
| BARBELL: два плеча, top-K, стресс-тест ставки ±100…300 б.п. | ✅ |
| LADDER: ступени, допуск, остатки, дубликаты, rolling-симуляция | ✅ |
| WHEEL: реинвест купонов, 3 сценария, капитализация, налоги, XIRR | ✅ |
| Ранжирование и `reason` по каждой бумаге | ✅ |
| Asset checks: жёсткие гейты, verdict, score, источники и даты | ✅ |
| MOEX ISS клиент: securities, marketdata, bondization, пагинация, retry | ✅ |
| Синхронизация (reference/quotes/cashflows) + `sync_runs`, ShedLock-подобная блокировка | ✅ |
| Кредитные рейтинги: нормализация шкалы, CSV-импорт, матчинг эмитентов, очередь unmatched | ✅ |
| JWT auth (access + refresh), роли USER/ADMIN | ✅ |
| Сохранение портфелей, история, soft delete, экспорт CSV/XLSX | ✅ |
| Админка: sync, рейтинги, алиасы, статистика, LLM-конфиг | ✅ |
| LLM-модуль (опционально): асинхронный разбор, JSON Schema, grounding, fallback | ✅ |
| Frontend: React 19 + TS + Vite, все экраны и графики | ✅ |
| Docker Compose: postgres, backend, frontend (+redis/tools/llm профили) | ✅ |
| Тесты: unit (финансовая математика, стратегии, checks, рейтинги) + API-интеграция | ✅ |

## Стек

- **Backend:** Java 17, Spring Boot 3.3, Spring Data JPA, Spring Security (JWT), Flyway, Caffeine, Springdoc OpenAPI.
- **DB:** PostgreSQL 16 (прод/докер), H2 в режиме PostgreSQL (локальный запуск без Docker).
- **Frontend:** React 19, TypeScript 5, Vite, React Router 7, TanStack Query, React Hook Form + Zod, Recharts, Tailwind CSS.
- **LLM:** провайдер-агностичный слой (`LMSTUDIO`, `OPENAI_COMPATIBLE`, `YANDEX`, `GIGACHAT`, `OLLAMA`, `STUB`).

## Быстрый старт (без Docker)

Требуется JDK 17+ и Node.js 20+.

```bash
# backend: H2 in-memory (данные пересоздаются при старте), схема Flyway + демо-данные автоматически
cd backend
mvn spring-boot:run

# frontend
cd frontend
npm install
npm run dev
```

- UI: http://localhost:5173 (проксирует `/api` на `http://localhost:8080`)
- API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

Демо-доступ: `admin@local.dev` / `admin12345` (роль ADMIN) и 24 демо-выпуска MOEX с рейтингами,
купонными календарями и котировками. Данные создаются `DemoDataSeeder` при `APP_SEED_DEMO=true`
(по умолчанию). **Смените пароль и отключите демо-данные перед публичным запуском**
(`APP_SEED_DEMO=false`).

## Быстрый старт (Docker)

```bash
cp .env.example .env          # задайте JWT_SECRET
docker compose up -d --build
```

- UI: http://localhost:3000
- API: http://localhost:8080/api/v1
- PostgreSQL: localhost:5432 (`bondplanner`/`bondplanner`)
- Опционально: `docker compose --profile cache up -d` (Redis), `--profile tools` (pgAdmin), `--profile llm` (Ollama).

Если порты 3000/8080/5432 уже заняты, задайте в `.env` свободные:

```dotenv
FRONTEND_PORT=13000
BACKEND_PORT=18080
POSTGRES_PORT=15432
APP_CORS_ALLOWED_ORIGINS=http://localhost:13000,http://127.0.0.1:13000
```

UI будет доступен на `http://localhost:13000`, API — на `http://localhost:18080/api/v1`.
Compose-проект называется `bond-strategy-planner-deepseek2`, контейнеры — `bsp2-*`, поэтому он
не конфликтует с другими compose-проектами на той же машине.

Для реальной модели (OpenAI-совместимый LM Studio / vLLM / Ollama) задайте в `.env`:

```dotenv
APP_LLM_ENABLED=true
APP_LLM_PROVIDER=LMSTUDIO
APP_LLM_BASE_URL=http://host.robotcomp:1234/v1
APP_LLM_API_KEY=<ваш API key>
APP_LLM_MODEL=qwen3-14b
APP_LLM_TIMEOUT=PT120S
APP_LLM_MAX_TOKENS=2048
APP_LLM_DISABLE_THINKING=true
```

`APP_LLM_DISABLE_THINKING=true` добавляет `/no_think` для Qwen3: без этого reasoning-модель
может израсходовать весь лимит токенов на `reasoning_content` и вернуть пустой JSON.
Провайдер автоматически откатывается на запрос без `response_format`, если сервер его не
поддерживает. `APP_LLM_PROVIDER=STUB` — детерминированный режим для тестов без модели.

Демо-доступ: `admin@local.dev` / `admin12345` (ADMIN). Демо-данные создаются при первом старте
(`APP_SEED_DEMO=true`); полную синхронизацию с MOEX можно запустить в админке
(`/admin/sync`) или `POST /api/v1/admin/sync`.

## Расчётные стратегии

### BULLET
Вход: `targetAmount`, `targetDate`, `startCapital`, `monthlyContribution?`, `maturityToleranceDays`.
Считает необходимый месячный взнос по формуле аннуитета, сценарии `pessimistic/base/optimistic`
(±200 б.п.), подбирает бумаги с эффективной датой погашения в окне цели, строит денежный поток и
проверяет достижимость цели.

### BARBELL
Вход: `amount`, `shortShare`, `shortMaxYears`, `longMinYears`, `maxBondsPerLeg`.
Делит капитал на короткое и длинное плечо, берёт top-K по score, считает средневзвешенные YTM и
дюрацию, стресс-тест `ΔP ≈ -D_mod·Δy·P + 0.5·C·Δy²·P` для Δy = ±100/200/300 б.п.
Валидация: `shortMaxYears < longMinYears`.

### LADDER
Вход: `amount`, `rungs` (1–10), `stepMonths` (1–60), `startDate`, `maturityToleranceDays`, `weights?`,
`rolling?`, `reinvestCoupons?`, `offerTreatment`.
Для каждой ступени: бумага в допуске (или расширение окна с предупреждением), целое число лотов,
перенос остатка, запрет дубликатов при наличии альтернативы, rolling-симуляция реинвестирования.

### WHEEL
База — LADDER или BULLET. Сценарии `REINVEST` / `CASH_OUT` / `CASH_BUFFER`, реинвестирование по правилу
(`BEST_SCORE`, `SHORTEST`, `LONGEST`, `SAME_BOND`), налоги по годам, XIRR, CAGR, капитализация по годам.

## Asset checks

Жёсткие гейты: рейтинг ниже `minRating`, `NO_RATING` при `requireRating=true`, дефолт/реструктуризация,
несоответствие сроку, листинг, объём выпуска. `WATCH`: негативный прогноз, stale-рейтинг, низкая
ликвидность, оферта/амортизация, `UNKNOWN` по важному фактору.

Результат: `verdict` (`APPROVED` / `WATCH` / `REJECTED`), `score`, `checks[]` с `value`, `threshold`,
`source`, `asOf`. `REJECTED` в рекомендации не попадает. LLM не может изменить verdict.

## Данные и рейтинги

- `POST /api/v1/admin/sync` — запуск синхронизации MOEX (`REFERENCE` / `QUOTES` / `CASHFLOWS` / `FULL`).
- `GET /api/v1/admin/sync/runs` — история запусков.
- `POST /api/v1/admin/ratings/import` — импорт CSV `issuer,inn,agency,rating,outlook,date` (multipart).
- `GET /api/v1/admin/ratings/unmatched` — очередь несопоставленных эмитентов.
- `POST /api/v1/admin/issuer-aliases` — ручной алиас эмитента.

Нормализация шкалы: `AAA=100 … D=0`; прогноз `POSITIVE +3`, `NEGATIVE −7`, `DEVELOPING −3`.
Из нескольких рейтингов используется консервативный (минимальный score).

## LLM (опционально)

Выключен по умолчанию (`APP_LLM_ENABLED=false`) — AI-эндпоинты отвечают `503 LLM_UNAVAILABLE`, ядро
работает полностью. При включении обязательны `APP_LLM_BASE_URL` и `APP_LLM_MODEL` (иначе старт
падает с `LLM_CONFIG_INVALID`). Поддерживается любой OpenAI-совместимый endpoint: LM Studio, vLLM,
Ollama (`/v1`), OpenRouter, YandexGPT.

```env
APP_LLM_ENABLED=true
APP_LLM_PROVIDER=LMSTUDIO
APP_LLM_BASE_URL=http://host.robotcomp:1234/v1
APP_LLM_API_KEY=sk-...
APP_LLM_MODEL=qwen3-14b
APP_LLM_TIMEOUT=PT120S
APP_LLM_MAX_TOKENS=2048
APP_LLM_DISABLE_THINKING=true
```

Проверка провайдера и полного цикла:

```bash
curl http://localhost:18080/api/v1/ai/health
# {"enabled":true,"provider":"LMSTUDIO","model":"qwen3-14b","healthy":true,...}

# с токеном администратора
curl -X POST http://localhost:18080/api/v1/ai/consent -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{}'
curl -X POST http://localhost:18080/api/v1/ai/portfolio-reviews \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"portfolioId":1}'
# 202 {"analysisId":...}; далее GET /api/v1/ai/analyses/{id}
```

`APP_LLM_DISABLE_THINKING=true` добавляет `/no_think` для Qwen3 (reasoning-модель иначе может
израсходовать лимит токенов на `reasoning_content` и вернуть пустой `content`). Если сервер не
поддерживает `response_format: json_object`, провайдер повторяет запрос без него. `STUB` —
детерминированный провайдер для тестов и CI.

Ответы проходят JSON Schema и grounding-валидацию: выдуманные тикеры и ссылки удаляются,
строковые `risks`/`nextActions` нормализуются, при отсутствии источников подставляются refs из
контекста. Сервер добавляет неудаляемый дисклеймер. Все запросы сохраняются в `llm_analyses`
(токены, стоимость, latency), повторный запрос идёт через кэш.

## API

Базовый путь `/api/v1`. Ошибки — RFC 7807 (`application/problem+json`) с полем `code`
(`VALIDATION_ERROR`, `NO_CANDIDATES`, `INCONSISTENT_PARAMS`, `DATA_STALE`, `LLM_UNAVAILABLE`, …).

Основные эндпоинты: `/strategies`, `/exchanges`, `/bonds`, `/issuers`, `/calculations`, `/portfolios`,
`/ai/*`, `/admin/*`. Полная схема — Swagger UI.

## Тесты

```bash
cd backend && mvn test       # 46 тестов: unit + интеграционные (H2, MockMvc)
cd frontend && npm test      # 11 тестов: форматирование, формы, таблица позиций
```

UI-дымовая проверка в headless Chromium (backend на `:8085`, Vite на `:5173`):

```bash
cd frontend
npx playwright install chromium
node scripts/ui-check.mjs http://127.0.0.1:5173
```

Проверка проходит главную, калькулятор, результаты, логин, сохранение портфеля, AI-разбор,
админку и мобильную вёрстку; падает на любых console/page errors. Скриншоты складываются
в `frontend/screenshots/` (не коммитятся).

## Структура

```
backend/   Spring Boot API, расчётное ядро, провайдеры, миграции Flyway
frontend/  React SPA
tools/     gh-push.mjs — публикация коммитов через GitHub API (обход ограничений песочницы)
```

## Ограничения v1

- Только RUB, только MOEX ISS (провайдерная SPI готова к СПБ/иностранным биржам).
- Налоги: `RU_FLAT_13` по умолчанию, `RU_PROGRESSIVE_2025` опционально; НКД и сальдирование убытков не моделируются.
- Инфляция и дефолты не моделируются (фильтр по рейтингу/листингу).
- Парсеры HTML-страниц агентств реализованы как коннекторы с конфигом в БД; по умолчанию рейтинги
  загружаются CSV-импортом, т.к. вёрстка источников меняется и требует юридической проверки ToS.
- pgvector/RAG — задел v2; поиск по документам — полнотекстовый/LIKE.

## Лицензия

Proprietary / учебный проект. Данные MOEX ISS используются по условиям MOEX.
