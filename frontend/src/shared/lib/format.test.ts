import { describe, expect, it } from "vitest";
import {
  checkLabel,
  fmtDate,
  fmtMoney,
  fmtPct,
  freshnessClass,
  riskLabel,
  strategyLabel,
  verdictClass,
} from "./format";

describe("format helpers", () => {
  it("formats money in rubles", () => {
    const value = fmtMoney(1_234_567);
    expect(value).toContain("1");
    expect(value).toContain("₽");
  });

  it("returns dash for empty values", () => {
    expect(fmtMoney(null)).toBe("—");
    expect(fmtPct(undefined)).toBe("—");
    expect(fmtDate(null)).toBe("—");
  });

  it("formats percents with comma", () => {
    expect(fmtPct(14.256)).toBe("14,26%");
  });

  it("labels strategies and risks", () => {
    expect(strategyLabel("LADDER")).toBe("Лестница");
    expect(riskLabel(2)).toBe("Умеренный");
  });

  it("maps verdict and check classes", () => {
    expect(verdictClass("APPROVED")).toBe("badge-success");
    expect(verdictClass("REJECTED")).toBe("badge-danger");
    expect(checkLabel("UNKNOWN")).toBe("UNKNOWN");
    expect(freshnessClass("STALE")).toBe("badge-warn");
  });
});
