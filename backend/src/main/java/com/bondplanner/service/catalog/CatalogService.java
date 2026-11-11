package com.bondplanner.service.catalog;

import com.bondplanner.api.dto.StrategyDto;
import com.bondplanner.api.error.ApiException;
import com.bondplanner.domain.entity.Strategy;
import com.bondplanner.repository.StrategyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final StrategyRepository strategyRepository;
    private final ObjectMapper objectMapper;

    public List<StrategyDto> strategies() {
        return strategyRepository.findAllByOrderBySortOrderAsc().stream().map(this::toDto).toList();
    }

    public StrategyDto strategy(String code) {
        Strategy strategy = strategyRepository.findById(code.toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Стратегия не найдена: " + code));
        if (!strategy.isEnabled()) {
            throw ApiException.notFound("Стратегия отключена: " + code);
        }
        return toDto(strategy);
    }

    private StrategyDto toDto(Strategy strategy) {
        Meta meta = meta(strategy.getCode());
        return new StrategyDto(
                strategy.getCode(),
                strategy.getName(),
                strategy.getShortDescription(),
                strategy.getRiskLevel(),
                strategy.getHorizonMinYears(),
                strategy.getHorizonMaxYears(),
                readJson(strategy.getParamsSchema()),
                readJson(strategy.getDefaultParams()),
                strategy.isEnabled(),
                strategy.getSortOrder(),
                meta.bestFor(),
                meta.notFor(),
                meta.pros(),
                meta.cons());
    }

    private JsonNode readJson(String raw) {
        try {
            return objectMapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private record Meta(List<String> bestFor, List<String> notFor, List<String> pros, List<String> cons) {
    }

    private Meta meta(String code) {
        return switch (code) {
            case "BULLET" -> new Meta(
                    List.of("Цель с известной датой: обучение, крупная покупка, закрытие кредита",
                            "Инвестор готов держать бумаги до погашения и не продавать раньше"),
                    List.of("Дата цели плавает или капитал может понадобиться раньше срока",
                            "Нужен регулярный денежный поток, а не одна выплата"),
                    List.of("Максимальная определённость даты и суммы", "Простая логика: бумаги гасятся в цель"),
                    List.of("Концентрация в одной дате", "Риск реинвестирования после погашения"));
            case "BARBELL" -> new Meta(
                    List.of("Инвестор ждёт изменения кривой доходности и готов к волатильности",
                            "Нужен баланс между ликвидностью короткого плеча и доходностью длинного"),
                    List.of("Консервативный инвестор без запаса по риску",
                            "Горизонт меньше 5 лет — длинное плечо не успеет отработать"),
                    List.of("Высокая доходность длинного плеча", "Ликвидное короткое плечо"),
                    List.of("Просадка длинного плеча при росте ставок", "Требует регулярного контроля дюрации"));
            case "LADDER" -> new Meta(
                    List.of("Нужен ровный денежный поток и регулярные погашения",
                            "Инвестор хочет снизить риск неудачного момента входа"),
                    List.of("Нужна максимальная доходность в одной точке",
                            "Сумма слишком мала для нескольких ступеней"),
                    List.of("Регулярные выплаты", "Низкая зависимость от одной даты", "Удобная база для реинвеста"),
                    List.of("Средняя доходность ниже длинных бумаг", "Больше сделок — больше комиссий"));
            case "WHEEL" -> new Meta(
                    List.of("Инвестор не тратит купоны и готов их реинвестировать",
                            "Горизонт от 3 лет, важна капитализация сложного процента"),
                    List.of("Купонный доход нужен для текущих расходов",
                            "Нет доступа к подходящим бумагам для реинвестирования"),
                    List.of("Сложный процент", "Наглядное сравнение «реинвест / вывод / кэш»"),
                    List.of("Эффект зависит от ставки реинвестирования", "Налоги уменьшают капитализацию"));
            default -> new Meta(List.of(), List.of(), List.of(), List.of());
        };
    }
}
