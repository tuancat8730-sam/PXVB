-- Quyết định PO 2026-10-18 (L42): Performance của OEE chặn tối đa 100% — công thức mặc định phiên bản 2.
INSERT INTO pr_oee_formula_config(id, row_version, created_at, version_no, planned_time_mode, quality_mode,
                                  cap_performance_at_100, effective_from, note)
VALUES (UNHEX('01900000000070008000000000000002'), 0, UTC_TIMESTAMP(6), 2, 'EXCLUDE_PLANNED_STOPS',
        'ASSUME_100', 1, '2000-01-02 00:00:00.000000', 'Chặn Performance tối đa 100% (PO 2026-10-18)');
