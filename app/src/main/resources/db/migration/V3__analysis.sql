-- V3__analysis.sql
-- OLS analysis results with full traceability

CREATE TABLE analysis_results (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    run_id                   UUID         NOT NULL,   -- groups all results from one pipeline run
    client                   VARCHAR(255) NOT NULL,
    article                  VARCHAR(255) NOT NULL,
    window                   VARCHAR(50)  NOT NULL,   -- LAST_PRICE_INCREASE | LAST_3_MONTHS | LAST_6_MONTHS
    slope                    DOUBLE PRECISION NOT NULL,
    intercept                DOUBLE PRECISION NOT NULL,
    r_squared                DOUBLE PRECISION NOT NULL,
    sample_size              INT          NOT NULL,
    has_positive_slope_warning BOOLEAN    NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analysis_results_org_run  ON analysis_results(organization_id, run_id);
CREATE INDEX idx_analysis_results_warnings ON analysis_results(organization_id, has_positive_slope_warning)
    WHERE has_positive_slope_warning = TRUE;
