/**
 * Дымовая проверка UI в headless Chromium: отрисовка, консольные ошибки, основные сценарии.
 * Использование: node tools/ui-check.mjs [baseUrl]
 */
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";

const base = process.argv[2] ?? "http://127.0.0.1:5173";
const shots = path.resolve("screenshots");
fs.mkdirSync(shots, { recursive: true });

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: "ru-RU" });
const page = await context.newPage();
const errors = [];
page.on("console", (message) => {
  if (message.type() === "error") errors.push(`console: ${message.text()}`);
});
page.on("pageerror", (error) => errors.push(`pageerror: ${error.message}`));

async function shot(name, fullPage = true) {
  await page.screenshot({ path: path.join(shots, name), fullPage });
  console.log("screenshot:", name);
}

try {
  await page.goto(base + "/", { waitUntil: "networkidle" });
  await page.getByRole("heading", { name: /Портфель облигаций/ }).waitFor();
  await page.getByRole("heading", { name: "Пуля" }).waitFor();
  await shot("01-home.png");

  await page.goto(base + "/strategies/LADDER", { waitUntil: "networkidle" });
  await page.getByRole("button", { name: "Рассчитать" }).waitFor();
  await shot("02-ladder-form.png");
  await page.getByRole("button", { name: "Рассчитать" }).click();
  await page.getByRole("heading", { name: "Позиции портфеля" }).waitFor({ timeout: 30_000 });
  await shot("03-ladder-results.png");

  await page.goto(base + "/portfolios", { waitUntil: "networkidle" });
  await page.waitForURL(/\/login/);
  await page.getByLabel("Email").fill("admin@local.dev");
  await page.getByLabel("Пароль").fill("admin12345");
  await page.getByRole("main").getByRole("button", { name: "Войти" }).click();
  await page.waitForURL(/\/portfolios/);
  await shot("04-portfolios-empty.png");

  await page.goto(base + "/strategies/LADDER", { waitUntil: "networkidle" });
  await page.getByRole("button", { name: "Рассчитать" }).click();
  await page.getByRole("heading", { name: "Позиции портфеля" }).waitFor({ timeout: 30_000 });
  await page.getByRole("button", { name: "Сохранить портфель" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.waitFor();
  await dialog.getByRole("button", { name: "Сохранить" }).click();
  const openLink = page.getByRole("link", { name: "Открыть" });
  await openLink.waitFor({ timeout: 20_000 });
  await shot("05-ladder-saved.png");
  await openLink.click();
  await page.getByRole("heading", { name: /Лестница/ }).first().waitFor();
  await shot("06-portfolio-detail.png");

  await page.getByRole("button", { name: "AI-разбор портфеля" }).click();
  const consent = page.getByRole("dialog", { name: /Согласие/ });
  const consentShown = await consent.waitFor({ timeout: 3000 }).then(() => true).catch(() => false);
  if (consentShown) {
    await consent.getByRole("button", { name: "Согласен" }).click();
  }
  await page.getByText(/Демонстрационный ответ LLM|Следующие действия/).first().waitFor({ timeout: 30_000 });
  await shot("07-ai-review.png");

  await page.goto(base + "/admin/sync", { waitUntil: "networkidle" });
  await page.getByRole("heading", { name: "Синхронизация MOEX ISS" }).waitFor();
  await shot("08-admin-sync.png");

  await page.goto(base + "/admin/ratings", { waitUntil: "networkidle" });
  await page.getByRole("heading", { name: /Импорт рейтингов/ }).waitFor();
  await shot("09-admin-ratings.png");

  const mobile = await context.newPage();
  await mobile.setViewportSize({ width: 390, height: 844 });
  await mobile.goto(base + "/", { waitUntil: "networkidle" });
  await mobile.getByRole("heading", { name: "Пуля" }).waitFor();
  await mobile.screenshot({ path: path.join(shots, "10-home-mobile.png"), fullPage: true });
  console.log("screenshot: 10-home-mobile.png");
} catch (error) {
  errors.push(`scenario: ${error.message}`);
  await page.screenshot({ path: path.join(shots, "failure.png"), fullPage: true }).catch(() => undefined);
}

await browser.close();

if (errors.length) {
  console.error("UI errors:");
  for (const error of errors) console.error(" -", error);
  process.exit(1);
}
console.log("UI smoke check passed without console errors");
