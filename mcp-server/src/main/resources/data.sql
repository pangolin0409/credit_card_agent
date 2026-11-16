INSERT INTO customers (customer_id, full_name, monthly_income, existing_unsecured_debt, credit_score, is_aml_blacklist, has_travel_notice, travel_country) VALUES
    ('A100000001', '王志明', 300000,       0, 800, TRUE,  FALSE, NULL),
    ('A100000002', '林淑芬',  60000, 1250000, 720, FALSE, FALSE, NULL),
    ('A100000003', '陳建宏', 200000,       0, 780, FALSE, FALSE, NULL),
    ('A100000004', '張雅婷',  80000,       0, 700, FALSE, FALSE, NULL),
    ('A100000005', '李俊傑',  60000, 1200000, 680, FALSE, FALSE, NULL),
    ('A100000006', '黃美惠', 200000,       0, 700, FALSE, FALSE, NULL),
    ('A100000007', '吳宗翰',  40000,       0, 700, FALSE, FALSE, NULL),
    ('B200000001', '林小旅',  80000,       0, 720, FALSE, TRUE,  'JP'),
    ('B200000002', '陳大明',  80000,       0, 720, FALSE, FALSE, NULL),
    ('B200000003', '張美玲',  80000,       0, 720, FALSE, TRUE,  'JP'),
    ('B200000004', '黃志強',  80000,       0, 720, FALSE, FALSE, NULL),
    ('B200000005', '吳建國',  80000,       0, 720, FALSE, FALSE, NULL),
    ('B200000006', '李小華',  80000,       0, 720, FALSE, FALSE, NULL);

INSERT INTO credit_cards (card_number, customer_id, card_type, credit_limit, status) VALUES
    ('4000000000000001', 'B200000001', 'SIGNATURE',  150000, 'ACTIVE'),
    ('4000000000000002', 'B200000002', 'INFINITE',   500000, 'ACTIVE'),
    ('4000000000000003', 'B200000003', 'SIGNATURE',  150000, 'ACTIVE'),
    ('4000000000000004', 'B200000004', 'SIGNATURE',  150000, 'ACTIVE'),
    ('4000000000000005', 'B200000005', 'INFINITE',  1000000, 'ACTIVE'),
    ('4000000000000006', 'B200000006', 'SIGNATURE',  150000, 'ACTIVE');

INSERT INTO transactions (transaction_id, card_number, transaction_time, amount, merchant_name, country_code, is_online) VALUES
    ('TX101', '4000000000000001', '2026-11-16 09:00:00',  85000, 'Bic Camera Shinjuku',       'JP', FALSE),
    ('TX102', '4000000000000001', '2026-11-16 09:40:00',  42000, 'Isetan Shinjuku',           'JP', FALSE),
    ('TX201', '4000000000000002', '2026-11-15 20:00:00',   1200, '全聯福利中心',               'TW', FALSE),
    ('TX202', '4000000000000002', '2026-11-16 02:05:00',  18000, 'BestBuy Online',            'US', TRUE),
    ('TX203', '4000000000000002', '2026-11-16 02:25:00',  21000, 'Apple Store Online',        'US', TRUE),
    ('TX204', '4000000000000002', '2026-11-16 02:50:00',  23000, 'Amazon US',                 'US', TRUE),
    ('TX301', '4000000000000003', '2026-11-16 03:10:00',  30000, 'Galeries Lafayette Online', 'FR', TRUE),
    ('TX302', '4000000000000003', '2026-11-16 03:40:00',  25000, 'Fnac Online',               'FR', TRUE),
    ('TX401', '4000000000000004', '2026-11-16 04:00:00',      1, 'GOOGLE *TEMPORARY HOLD',    'US', TRUE),
    ('TX402', '4000000000000004', '2026-11-16 04:03:00',      5, 'STEAM PURCHASE',            'US', TRUE),
    ('TX403', '4000000000000004', '2026-11-16 04:06:00',     30, 'APPLE.COM/BILL',            'US', TRUE),
    ('TX404', '4000000000000004', '2026-11-16 04:08:00',     50, 'PAYPAL *VERIFY',            'US', TRUE),
    ('TX501', '4000000000000005', '2026-11-16 05:00:00', 680000, 'Rolex Geneva Online',       'CH', TRUE),
    ('TX601', '4000000000000006', '2026-11-16 12:00:00',    350, '7-ELEVEN',                  'TW', FALSE),
    ('TX602', '4000000000000006', '2026-11-16 12:30:00',   1280, '誠品書店',                   'TW', FALSE);
