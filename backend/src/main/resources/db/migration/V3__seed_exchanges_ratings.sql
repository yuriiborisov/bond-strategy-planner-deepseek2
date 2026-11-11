-- ============================================================
-- Seed: биржи и источники кредитных рейтингов
-- ============================================================

INSERT INTO exchanges (code, name, provider, base_url, timezone, is_default, enabled, config) VALUES
('MOEX', 'Московская биржа', 'MOEX_ISS', 'https://iss.moex.com/iss', 'Europe/Moscow', TRUE, TRUE,
 '{"boards":["TQOB","TQCB","TQMB"]}'),
('SPB', 'СПБ Биржа', 'SPB', 'https://spbexchange.com', 'Europe/Moscow', FALSE, FALSE, '{}');

INSERT INTO rating_sources (code, name, base_url, source_type, parser_config, enabled, priority, cron) VALUES
('ACRA', 'АКРА', 'https://www.acra-ratings.ru/', 'HTML',
 '{"listUrl":"https://www.acra-ratings.ru/ratings/","rowSelector":"table tbody tr","fields":{"issuer":"td:nth-child(1)","rating":"td:nth-child(2)","outlook":"td:nth-child(3)","date":"td:nth-child(4)","sourceUrl":"td:nth-child(1) a@href"},"dateFormat":"dd.MM.yyyy","encoding":"UTF-8"}',
 TRUE, 1, '0 0 4 * * MON'),
('EXPERT_RA', 'Эксперт РА', 'https://raexpert.ru/ratings/', 'HTML',
 '{"listUrl":"https://raexpert.ru/ratings/","rowSelector":"table tbody tr","fields":{"issuer":"td:nth-child(1)","rating":"td:nth-child(2)","outlook":"td:nth-child(3)","date":"td:nth-child(4)"},"dateFormat":"dd.MM.yyyy","encoding":"UTF-8"}',
 TRUE, 2, '0 10 4 * * MON'),
('NKR', 'НКР', 'https://ratings.ru/', 'HTML',
 '{"listUrl":"https://ratings.ru/ratings/","rowSelector":"table tbody tr","fields":{"issuer":"td:nth-child(1)","rating":"td:nth-child(2)","outlook":"td:nth-child(3)","date":"td:nth-child(4)"},"dateFormat":"dd.MM.yyyy","encoding":"UTF-8"}',
 TRUE, 3, '0 20 4 * * MON'),
('CBR', 'Банк России', 'https://www.cbr.ru/', 'HTML', '{}', TRUE, 4, '0 0 5 1 * *'),
('MOEX_PROXY', 'MOEX (листинг)', 'https://iss.moex.com/iss', 'API', '{}', TRUE, 5, NULL),
('CBONDS', 'CBonds', '', 'API', '{}', FALSE, 6, NULL),
('REFINITIV', 'Refinitiv', '', 'API', '{}', FALSE, 7, NULL),
('MANUAL', 'Ручной импорт', '', 'CSV', '{}', TRUE, 8, NULL);
