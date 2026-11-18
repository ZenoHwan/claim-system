-- Local demo data. Every account uses the same dev password (see README).
INSERT INTO team (id, name, market_code) VALUES
    ('10000000-0000-0000-0000-000000000001', 'Malaysia Claims', 'MY'),
    ('10000000-0000-0000-0000-000000000002', 'Singapore Claims', 'SG')
ON CONFLICT (id) DO NOTHING;

INSERT INTO app_user (id, email, full_name, password_hash, role, market_code, team_id) VALUES
    ('20000000-0000-0000-0000-000000000001', 'manager.my@claims.test',  'Lim Mei Ling',  '$2a$10$g.tXBPqe4rmjr2pxcvNDGukWwrVurNx9.VIgbaes1k8ZfkdtGZ52u', 'MANAGER',  'MY', '10000000-0000-0000-0000-000000000001'),
    ('20000000-0000-0000-0000-000000000002', 'officer1.my@claims.test', 'Tan Wei Ming',  '$2a$10$g.tXBPqe4rmjr2pxcvNDGukWwrVurNx9.VIgbaes1k8ZfkdtGZ52u', 'OFFICER',  'MY', '10000000-0000-0000-0000-000000000001'),
    ('20000000-0000-0000-0000-000000000003', 'officer2.my@claims.test', 'Kumar Raj',     '$2a$10$g.tXBPqe4rmjr2pxcvNDGukWwrVurNx9.VIgbaes1k8ZfkdtGZ52u', 'OFFICER',  'MY', '10000000-0000-0000-0000-000000000001'),
    ('20000000-0000-0000-0000-000000000004', 'officer.sg@claims.test',  'Wong Jia Hui',  '$2a$10$g.tXBPqe4rmjr2pxcvNDGukWwrVurNx9.VIgbaes1k8ZfkdtGZ52u', 'OFFICER',  'SG', '10000000-0000-0000-0000-000000000002'),
    ('30000000-0000-0000-0000-000000000001', 'aisha@claims.test',       'Aisha Rahman',  '$2a$10$g.tXBPqe4rmjr2pxcvNDGukWwrVurNx9.VIgbaes1k8ZfkdtGZ52u', 'CLAIMANT', 'MY', NULL),
    ('30000000-0000-0000-0000-000000000002', 'daniel@claims.test',      'Daniel Lee',    '$2a$10$g.tXBPqe4rmjr2pxcvNDGukWwrVurNx9.VIgbaes1k8ZfkdtGZ52u', 'CLAIMANT', 'MY', NULL),
    ('30000000-0000-0000-0000-000000000003', 'siti@claims.test',        'Siti Tan',      '$2a$10$g.tXBPqe4rmjr2pxcvNDGukWwrVurNx9.VIgbaes1k8ZfkdtGZ52u', 'CLAIMANT', 'SG', NULL)
ON CONFLICT (id) DO UPDATE SET password_hash = EXCLUDED.password_hash;

INSERT INTO policy (id, policy_number, holder_id, line_of_business, market_code, currency, sum_insured, effective_from, effective_to) VALUES
    ('40000000-0000-0000-0000-000000000001', 'MY-MOT-000451', '30000000-0000-0000-0000-000000000001', 'MOTOR',    'MY', 'MYR',  80000.00, '2026-01-01', '2027-12-31'),
    ('40000000-0000-0000-0000-000000000002', 'MY-PRP-000112', '30000000-0000-0000-0000-000000000001', 'PROPERTY', 'MY', 'MYR', 500000.00, '2026-01-01', '2027-12-31'),
    ('40000000-0000-0000-0000-000000000003', 'MY-MOT-000452', '30000000-0000-0000-0000-000000000002', 'MOTOR',    'MY', 'MYR',  60000.00, '2026-01-01', '2027-12-31'),
    ('40000000-0000-0000-0000-000000000004', 'SG-MOT-000201', '30000000-0000-0000-0000-000000000003', 'MOTOR',    'SG', 'SGD', 120000.00, '2026-01-01', '2027-12-31')
ON CONFLICT (id) DO NOTHING;
