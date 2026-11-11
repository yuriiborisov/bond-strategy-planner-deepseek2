package com.bondplanner.api;

import com.bondplanner.domain.entity.Portfolio;
import com.bondplanner.repository.PortfolioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Test
    void catalogReturnsFourStrategiesWithSchemas() throws Exception {
        mockMvc.perform(get("/api/v1/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].paramsSchema.type").value("object"))
                .andExpect(jsonPath("$[0].defaultParams").exists());
        mockMvc.perform(get("/api/v1/exchanges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("MOEX"))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    void ladderCalculationReturnsRungsAndResidual() throws Exception {
        Map<String, Object> body = calculation("LADDER", Map.of(
                "amount", 1_000_000,
                "rungs", 5,
                "stepMonths", 12,
                "startDate", LocalDate.now().plusDays(20).toString(),
                "maturityToleranceDays", 45));
        JsonNode result = performCalculation(body, null);
        assertThat(result.path("calculationId").asLong()).isPositive();
        assertThat(result.path("positions").size()).isLessThanOrEqualTo(5);
        assertThat(result.path("rungs").size()).isEqualTo(5);
        assertThat(result.path("summary").path("investedRub").decimalValue())
                .isLessThanOrEqualTo(result.path("summary").path("totalAmountRub").decimalValue());
        assertThat(result.path("summary").path("cashResidualRub").decimalValue().signum()).isGreaterThanOrEqualTo(0);
        for (JsonNode rung : result.path("rungs")) {
            assertThat(rung.path("status").asText()).isIn("FILLED", "SKIPPED_INSUFFICIENT_BUDGET", "NO_CANDIDATES",
                    "ASSET_CHECK_FAILED", "NO_PRICED_CANDIDATES");
        }
    }

    @Test
    void bulletCalculationHasThreeScenarios() throws Exception {
        JsonNode result = performCalculation(calculation("BULLET", Map.of(
                "targetAmount", 3_000_000,
                "targetDate", LocalDate.now().plusYears(5).toString(),
                "startCapital", 1_000_000,
                "maturityToleranceDays", 365)), null);
        assertThat(result.path("contributionScenarios").size()).isEqualTo(3);
        assertThat(result.path("monthlyContributionRub").decimalValue().signum()).isPositive();
        assertThat(result.path("summary").path("achievement").isBoolean()).isTrue();
    }

    @Test
    void barbellRejectsCrossedLegs() throws Exception {
        mockMvc.perform(post("/api/v1/calculations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calculation("BARBELL", Map.of(
                                "amount", 1_000_000, "shortMaxYears", 12, "longMinYears", 5)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INCONSISTENT_PARAMS"));
    }

    @Test
    void noCandidatesReturnsSuggestions() throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("amount", 1_000_000);
        params.put("rungs", 3);
        params.put("stepMonths", 12);
        params.put("startDate", LocalDate.now().plusDays(10).toString());
        params.put("filters", Map.of("minIssueSizeRub", 1_000_000_000_000_000L));
        mockMvc.perform(post("/api/v1/calculations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calculation("LADDER", params))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_CANDIDATES"))
                .andExpect(jsonPath("$.suggestions").isArray());
    }

    @Test
    void wheelComparesThreeScenariosAndReinvestWins() throws Exception {
        Map<String, Object> baseParams = Map.of(
                "amount", 1_000_000,
                "rungs", 5,
                "stepMonths", 12,
                "startDate", LocalDate.now().plusDays(20).toString());
        JsonNode result = performCalculation(calculation("WHEEL", Map.of(
                "baseStrategy", "LADDER",
                "baseParams", baseParams,
                "horizonYears", 5,
                "reinvestRule", "BEST_SCORE")), null);
        assertThat(result.path("comparison").size()).isEqualTo(3);
        assertThat(result.path("capitalizationSeries").size()).isGreaterThanOrEqualTo(15);
        double reinvest = 0;
        double cashOut = 0;
        for (JsonNode scenario : result.path("comparison")) {
            if ("REINVEST".equals(scenario.path("scenario").asText())) {
                reinvest = scenario.path("finalCapitalRub").asDouble();
            }
            if ("CASH_OUT".equals(scenario.path("scenario").asText())) {
                cashOut = scenario.path("finalCapitalRub").asDouble();
            }
        }
        assertThat(reinvest).isGreaterThanOrEqualTo(cashOut);
    }

    @Test
    void authPortfolioLifecycleAndExport() throws Exception {
        String email = "user" + System.nanoTime() + "@test.local";
        String token = register(email, "password123");
        MvcResult me = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();
        assertThat(me.getResponse().getContentAsString()).contains("USER");

        JsonNode calculation = performCalculation(calculation("LADDER", Map.of(
                "amount", 500_000,
                "rungs", 3,
                "stepMonths", 12,
                "startDate", LocalDate.now().plusDays(20).toString())), token);
        long calculationId = calculation.path("calculationId").asLong();

        MvcResult saved = mockMvc.perform(post("/api/v1/calculations/" + calculationId + "/save")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Мой портфель\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Мой портфель"))
                .andReturn();
        JsonNode portfolio = objectMapper.readTree(saved.getResponse().getContentAsString());
        long portfolioId = portfolio.path("id").asLong();

        mockMvc.perform(get("/api/v1/portfolios").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/portfolios/" + portfolioId + "/export?format=csv")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/portfolios")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminImportsRatingsAndReportsStats() throws Exception {
        String adminToken = login("admin@local.dev", "admin12345");
        String csv = "issuer,inn,agency,rating,outlook,date\n"
                + "ПАО Тестовый Эмитент,1234567890,ACRA,AA+,STABLE,01.10.2026\n";
        MockMultipartFile file = new MockMultipartFile("file", "ratings.csv", "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/admin/ratings/import").file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));
        mockMvc.perform(get("/api/v1/admin/stats").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bonds").isNumber());
        mockMvc.perform(get("/api/v1/admin/rating-sources"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aiReturnsUnavailableWhenDisabled() throws Exception {
        String email = "ai" + System.nanoTime() + "@test.local";
        String token = register(email, "password123");
        JsonNode calculation = performCalculation(calculation("LADDER", Map.of(
                "amount", 300_000,
                "rungs", 2,
                "stepMonths", 12,
                "startDate", LocalDate.now().plusDays(20).toString())), token);
        MvcResult saved = mockMvc.perform(post("/api/v1/calculations/" + calculation.path("calculationId").asLong() + "/save")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        long portfolioId = objectMapper.readTree(saved.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(post("/api/v1/ai/portfolio-reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"portfolioId\":" + portfolioId + "}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LLM_UNAVAILABLE"));

        mockMvc.perform(get("/api/v1/ai/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    private JsonNode performCalculation(Map<String, Object> body, String token) throws Exception {
        var request = post("/api/v1/calculations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String register(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private Map<String, Object> calculation(String strategy, Map<String, Object> params) {
        return Map.of("strategy", strategy, "exchange", "MOEX", "params", params);
    }
}
