-- Existing assigned jobs retain their current status; approval applies to new assignments.
CREATE TABLE job_applications (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES delivery_requests(id),
    helper_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED', 'WITHDRAWN', 'NOT_SELECTED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (request_id, helper_id)
);
CREATE INDEX ix_job_applications_helper ON job_applications(helper_id, id DESC);
CREATE UNIQUE INDEX ix_job_applications_one_approved ON job_applications(request_id) WHERE status = 'APPROVED';
