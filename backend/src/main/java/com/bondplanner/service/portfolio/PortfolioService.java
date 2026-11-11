package com.bondplanner.service.portfolio;

import com.bondplanner.api.error.ApiException;
import com.bondplanner.domain.entity.Bond;
import com.bondplanner.domain.entity.Calculation;
import com.bondplanner.domain.entity.Portfolio;
import com.bondplanner.domain.entity.PortfolioPosition;
import com.bondplanner.domain.entity.User;
import com.bondplanner.repository.BondRepository;
import com.bondplanner.repository.CalculationRepository;
import com.bondplanner.repository.PortfolioPositionRepository;
import com.bondplanner.repository.PortfolioRepository;
import com.bondplanner.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final CalculationRepository calculationRepository;
    private final BondRepository bondRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> save(Long userId, Long calculationId, String name) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
        Calculation calculation = calculationRepository.findById(calculationId)
                .orElseThrow(() -> ApiException.notFound("Расчёт не найден: " + calculationId));
        if (calculation.getUser() != null && !calculation.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Нет доступа к расчёту");
        }
        Portfolio portfolio = new Portfolio();
        portfolio.setUser(user);
        portfolio.setCalculation(calculation);
        portfolio.setName(name == null || name.isBlank() ? "Портфель от " + LocalDate.now() : name.trim());
        portfolio.setCreatedAt(Instant.now());
        portfolio.setUpdatedAt(Instant.now());
        portfolioRepository.save(portfolio);

        List<PortfolioPosition> positions = new ArrayList<>();
        try {
            JsonNode result = objectMapper.readTree(calculation.getResultJson());
            for (JsonNode node : result.path("positions")) {
                String secid = node.path("bond").path("secid").asText(null);
                if (secid == null) {
                    continue;
                }
                Bond bond = bondRepository.findByExchangeIdAndSecid(calculation.getExchange().getId(), secid)
                        .orElse(null);
                if (bond == null) {
                    continue;
                }
                PortfolioPosition position = new PortfolioPosition();
                position.setPortfolio(portfolio);
                position.setBond(bond);
                position.setQuantity(node.path("quantity").asInt(0));
                position.setAmountRub(node.path("amountRub").decimalValue());
                if (node.hasNonNull("rungIndex")) {
                    position.setRungIndex(node.get("rungIndex").asInt());
                }
                if (node.hasNonNull("targetDate")) {
                    position.setTargetDate(LocalDate.parse(node.get("targetDate").asText()));
                }
                if (node.hasNonNull("weight")) {
                    position.setWeight(node.get("weight").decimalValue());
                }
                position.setMeta(objectMapper.writeValueAsString(Map.of(
                        "ytmPct", node.path("ytmPct").decimalValue(),
                        "reason", node.path("reason").asText(""))));
                positions.add(position);
            }
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "RESULT_CORRUPTED",
                    "Не удалось прочитать результат расчёта");
        }
        positionRepository.saveAll(positions);
        return summary(portfolio, calculation, positions);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(Long userId, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 200));
        var portfolios = portfolioRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable);
        List<Map<String, Object>> content = portfolios.getContent().stream().map(portfolio -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", portfolio.getId());
            map.put("name", portfolio.getName());
            map.put("strategy", portfolio.getCalculation().getStrategyCode());
            map.put("calculationId", portfolio.getCalculation().getId());
            map.put("createdAt", portfolio.getCreatedAt());
            try {
                JsonNode result = objectMapper.readTree(portfolio.getCalculation().getResultJson());
                map.put("summary", result.path("summary"));
            } catch (Exception ignored) {
                // пропускаем повреждённый результат
            }
            return map;
        }).toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", content);
        response.put("page", portfolios.getNumber());
        response.put("size", portfolios.getSize());
        response.put("totalElements", portfolios.getTotalElements());
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(Long userId, Long id) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Портфель не найден: " + id));
        List<PortfolioPosition> positions = positionRepository.findByPortfolioIdOrderByRungIndexAscIdAsc(id);
        Map<String, Object> response = summary(portfolio, portfolio.getCalculation(), positions);
        try {
            JsonNode result = objectMapper.readTree(portfolio.getCalculation().getResultJson());
            response.put("result", result);
        } catch (Exception ex) {
            response.put("result", null);
        }
        return response;
    }

    @Transactional
    public Map<String, Object> rename(Long userId, Long id, String name) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Портфель не найден: " + id));
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Название не может быть пустым");
        }
        portfolio.setName(name.trim());
        portfolio.setUpdatedAt(Instant.now());
        portfolioRepository.save(portfolio);
        return Map.of("id", portfolio.getId(), "name", portfolio.getName());
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Портфель не найден: " + id));
        portfolio.setDeletedAt(Instant.now());
        portfolioRepository.save(portfolio);
    }

    @Transactional(readOnly = true)
    public byte[] export(Long userId, Long id, String format) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> ApiException.notFound("Портфель не найден: " + id));
        List<PortfolioPosition> positions = positionRepository.findByPortfolioIdOrderByRungIndexAscIdAsc(id);
        if ("xlsx".equalsIgnoreCase(format)) {
            return exportXlsx(portfolio, positions);
        }
        return exportCsv(portfolio, positions).getBytes(StandardCharsets.UTF_8);
    }

    private String exportCsv(Portfolio portfolio, List<PortfolioPosition> positions) {
        StringBuilder sb = new StringBuilder();
        sb.append("portfolio,strategy,secid,name,issuer,maturity,quantity,amount_rub,weight,ytm_pct,reason\n");
        for (PortfolioPosition position : positions) {
            Map<String, Object> meta = readMeta(position);
            sb.append(csv(portfolio.getName())).append(',')
                    .append(portfolio.getCalculation().getStrategyCode()).append(',')
                    .append(csv(position.getBond().getSecid())).append(',')
                    .append(csv(position.getBond().getShortName())).append(',')
                    .append(csv(position.getBond().getIssuerName())).append(',')
                    .append(position.getBond().getMaturityDate()).append(',')
                    .append(position.getQuantity()).append(',')
                    .append(position.getAmountRub()).append(',')
                    .append(position.getWeight() == null ? "" : position.getWeight()).append(',')
                    .append(meta.getOrDefault("ytmPct", "")).append(',')
                    .append(csv(String.valueOf(meta.getOrDefault("reason", "")))).append('\n');
        }
        return sb.toString();
    }

    private byte[] exportXlsx(Portfolio portfolio, List<PortfolioPosition> positions) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Позиции");
            String[] headers = {"Тикер", "Название", "Эмитент", "Погашение", "Количество", "Сумма, ₽",
                    "Доля", "YTM, %", "Обоснование"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            int rowIndex = 1;
            for (PortfolioPosition position : positions) {
                Map<String, Object> meta = readMeta(position);
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(position.getBond().getSecid());
                row.createCell(1).setCellValue(nullSafe(position.getBond().getShortName()));
                row.createCell(2).setCellValue(nullSafe(position.getBond().getIssuerName()));
                row.createCell(3).setCellValue(position.getBond().getMaturityDate() == null
                        ? "" : position.getBond().getMaturityDate().toString());
                row.createCell(4).setCellValue(position.getQuantity());
                Cell amount = row.createCell(5);
                amount.setCellValue(position.getAmountRub() == null ? 0 : position.getAmountRub().doubleValue());
                row.createCell(6).setCellValue(position.getWeight() == null ? 0 : position.getWeight().doubleValue());
                Object ytm = meta.get("ytmPct");
                if (ytm instanceof Number number) {
                    row.createCell(7).setCellValue(number.doubleValue());
                }
                row.createCell(8).setCellValue(String.valueOf(meta.getOrDefault("reason", "")));
            }
            Sheet info = workbook.createSheet("Портфель");
            info.createRow(0).createCell(0).setCellValue("Портфель");
            info.getRow(0).createCell(1).setCellValue(portfolio.getName());
            info.createRow(1).createCell(0).setCellValue("Стратегия");
            info.getRow(1).createCell(1).setCellValue(portfolio.getCalculation().getStrategyCode());
            info.createRow(2).createCell(0).setCellValue("Снимок");
            info.getRow(2).createCell(1).setCellValue(portfolio.getCalculation().getSnapshotTs().toString());
            info.createRow(3).createCell(0).setCellValue("Дисклеймер");
            info.getRow(3).createCell(1).setCellValue(
                    "Не является индивидуальной инвестиционной рекомендацией");
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "EXPORT_FAILED",
                    "Не удалось сформировать XLSX: " + ex.getMessage());
        }
    }

    private Map<String, Object> summary(Portfolio portfolio, Calculation calculation,
                                        List<PortfolioPosition> positions) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", portfolio.getId());
        map.put("name", portfolio.getName());
        map.put("strategy", calculation.getStrategyCode());
        map.put("calculationId", calculation.getId());
        map.put("createdAt", portfolio.getCreatedAt());
        map.put("updatedAt", portfolio.getUpdatedAt());
        map.put("snapshotTs", calculation.getSnapshotTs());
        map.put("positionsCount", positions.size());
        map.put("totalAmountRub", positions.stream()
                .map(p -> p.getAmountRub() == null ? BigDecimal.ZERO : p.getAmountRub())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        List<Map<String, Object>> positionDtos = new ArrayList<>();
        for (PortfolioPosition position : positions) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", position.getId());
            dto.put("secid", position.getBond().getSecid());
            dto.put("name", position.getBond().getShortName());
            dto.put("issuer", position.getBond().getIssuerName());
            dto.put("maturityDate", position.getBond().getMaturityDate());
            dto.put("quantity", position.getQuantity());
            dto.put("amountRub", position.getAmountRub());
            dto.put("weight", position.getWeight());
            dto.put("rungIndex", position.getRungIndex());
            dto.put("targetDate", position.getTargetDate());
            dto.putAll(readMeta(position));
            positionDtos.add(dto);
        }
        map.put("positions", positionDtos);
        return map;
    }

    private Map<String, Object> readMeta(PortfolioPosition position) {
        if (position.getMeta() == null || position.getMeta().isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(position.getMeta(), Map.class);
            return map;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
