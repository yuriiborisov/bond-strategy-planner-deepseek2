import { describe, expect, it } from "vitest";
import { buildCalculationsPayload, buildDefaults } from "./formConfig";

describe("calculation form config", () => {
  it("builds ladder defaults with a future start date", () => {
    const values = buildDefaults("LADDER", {});
    expect(values.rungs).toBe(5);
    expect(values.stepMonths).toBe(12);
    expect(new Date(values.startDate).getTime()).toBeGreaterThan(Date.now());
    expect(values.filters.requireRating).toBe(true);
    expect(values.costs.taxProfile).toBe("RU_FLAT_13");
  });

  it("builds bullet payload with target date and filters", () => {
    const values = buildDefaults("BULLET", { targetAmount: 5_000_000 });
    const payload = buildCalculationsPayload("BULLET", values) as Record<string, any>;
    expect(payload.targetAmount).toBe(5_000_000);
    expect(payload.filters.minRating).toBe("A-");
    expect(payload.costs.commissionPct).toBe(0.05);
  });

  it("maps wheel fields into baseParams", () => {
    const values = buildDefaults("WHEEL", {});
    const payload = buildCalculationsPayload("WHEEL", values) as Record<string, any>;
    expect(payload.baseStrategy).toBe("LADDER");
    expect(payload.baseParams.amount).toBe(1_000_000);
    expect(payload.baseParams.rungs).toBe(5);
    expect(payload.baseParams.startDate).toBe(values.startDate);
    expect(payload.horizonYears).toBe(5);
  });

  it("respects provided filters and costs", () => {
    const values = buildDefaults("BARBELL", {
      filters: { minRating: "AA", requireRating: false, minListLevel: 1 },
      costs: { commissionPct: 0.1 },
    });
    expect(values.filters.minRating).toBe("AA");
    expect(values.filters.requireRating).toBe(false);
    expect(values.costs.commissionPct).toBe(0.1);
  });
});
