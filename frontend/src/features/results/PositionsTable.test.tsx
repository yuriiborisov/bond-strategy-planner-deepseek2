import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import type { Position } from "../../shared/api/types";
import { PositionsTable } from "./PositionsTable";

const position: Position = {
  bond: {
    secid: "SU26238RMFS4",
    name: "ОФЗ 26238",
    issuer: "Минфин России",
    maturityDate: "2027-11-19",
    effectiveMaturity: "2027-11-19",
    pricePct: 95.2,
    nkdRub: 15.83,
    ytmPct: 15.4,
    durationYears: 1.2,
    lotSize: 1,
    rating: "AAA(RU)",
    ratingAgency: "ACRA",
    ratingDate: "2026-09-27",
    ratingOutlook: "STABLE",
  },
  quantity: 100,
  amountRub: 96_800,
  weight: 0.2,
  ytmPct: 15.4,
  durationYears: 1.2,
  reason: "Погашение за 13 дней до целевой даты",
  checks: null,
};

describe("PositionsTable", () => {
  it("renders bond row with reason and expands details", async () => {
    const user = userEvent.setup();
    render(<PositionsTable positions={[position]} />);
    expect(screen.getByText("ОФЗ 26238")).toBeInTheDocument();
    expect(screen.getByText("SU26238RMFS4")).toBeInTheDocument();
    expect(screen.getByText("AAA(RU) · ACRA")).toBeInTheDocument();
    await user.click(screen.getByTitle("Показать проверки и обоснование"));
    expect(screen.getByText("Обоснование выбора")).toBeInTheDocument();
    expect(screen.getByText(/Погашение за 13 дней/)).toBeInTheDocument();
    expect(screen.getByText("Проверки не запрошены для этого расчёта.")).toBeInTheDocument();
  });

  it("shows empty message without positions", () => {
    render(<PositionsTable positions={[]} />);
    expect(screen.getByText(/Позиции не сформированы/)).toBeInTheDocument();
  });
});
