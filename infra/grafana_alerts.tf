# ── Grafana Alerts ─────────────────────────────────────────────────────────────

resource "grafana_folder" "alerts" {
  title = "SaaS Template Alerts"
}

resource "grafana_contact_point" "email" {
  name = "email"
  email {
    addresses = [var.alert_email]
  }
}

resource "grafana_notification_policy" "default" {
  contact_point = grafana_contact_point.email.name
  group_by      = ["alertname", "tenant_id"]
}

# ── Auth ────────────────────────────────────────────────────────────────────────

resource "grafana_rule_group" "auth" {
  name             = "Auth"
  folder_uid       = grafana_folder.alerts.uid
  interval_seconds = 60

  # >0.5 invalid tokens/sec sustained — possible credential stuffing.
  # Tune after observing one week of baseline traffic — see README → Alerts as code.
  rule {
    name           = "HighJwtInvalidRate"
    for            = "5m"
    condition      = "B"
    no_data_state  = "OK"
    exec_err_state = "Error"
    labels = {
      severity = "critical"
    }
    annotations = {
      summary     = "Sustained invalid JWT rate — possible credential stuffing"
      description = "rate(saasstarter_auth_jwt_seconds_count{outcome=\"invalid\"}[5m]) > 0.5 for 5m"
    }
    data {
      ref_id     = "A"
      query_type = ""
      relative_time_range {
        from = 300
        to   = 0
      }
      datasource_uid = var.grafana_prometheus_datasource_uid
      model = jsonencode({
        refId         = "A"
        instant       = true
        intervalMs    = 1000
        maxDataPoints = 43200
        expr          = "rate(saasstarter_auth_jwt_seconds_count{outcome=\"invalid\"}[5m])"
      })
    }
    data {
      ref_id     = "B"
      query_type = ""
      relative_time_range {
        from = 0
        to   = 0
      }
      datasource_uid = "__expr__"
      model = jsonencode({
        refId      = "B"
        type       = "threshold"
        expression = "A"
        conditions = [{
          evaluator = { type = "gt", params = [0.5] }
          type      = "query"
        }]
        datasource = { type = "__expr__", uid = "__expr__" }
      })
    }
  }

  # Clock skew or token refresh bug causing wide user impact.
  # Tune after observing one week of baseline traffic — see README → Alerts as code.
  rule {
    name           = "JwtExpiredSpike"
    for            = "5m"
    condition      = "B"
    no_data_state  = "OK"
    exec_err_state = "Error"
    labels = {
      severity = "warning"
    }
    annotations = {
      summary     = "JWT expired spike — clock skew or token refresh bug"
      description = "rate(saasstarter_auth_jwt_seconds_count{outcome=\"expired\"}[5m]) > 1 for 5m"
    }
    data {
      ref_id     = "A"
      query_type = ""
      relative_time_range {
        from = 300
        to   = 0
      }
      datasource_uid = var.grafana_prometheus_datasource_uid
      model = jsonencode({
        refId         = "A"
        instant       = true
        intervalMs    = 1000
        maxDataPoints = 43200
        expr          = "rate(saasstarter_auth_jwt_seconds_count{outcome=\"expired\"}[5m])"
      })
    }
    data {
      ref_id     = "B"
      query_type = ""
      relative_time_range {
        from = 0
        to   = 0
      }
      datasource_uid = "__expr__"
      model = jsonencode({
        refId      = "B"
        type       = "threshold"
        expression = "A"
        conditions = [{
          evaluator = { type = "gt", params = [1] }
          type      = "query"
        }]
        datasource = { type = "__expr__", uid = "__expr__" }
      })
    }
  }
}

# ── Rate limiting ───────────────────────────────────────────────────────────────

resource "grafana_rule_group" "ratelimit" {
  name             = "RateLimit"
  folder_uid       = grafana_folder.alerts.uid
  interval_seconds = 60

  # >10% of rate-limit checks denied — legitimate traffic being throttled.
  # Tune after observing one week of baseline traffic — see README → Alerts as code.
  rule {
    name           = "RateLimitDenialSpike"
    for            = "10m"
    condition      = "B"
    no_data_state  = "OK"
    exec_err_state = "Error"
    labels = {
      severity = "warning"
    }
    annotations = {
      summary     = "Rate-limit denial spike — legitimate traffic may be throttled"
      description = "rate(saasstarter_ratelimit_seconds_count{outcome=\"denied\"}[5m]) / rate(saasstarter_ratelimit_seconds_count[5m]) > 0.1 for 10m"
    }
    data {
      ref_id     = "A"
      query_type = ""
      relative_time_range {
        from = 300
        to   = 0
      }
      datasource_uid = var.grafana_prometheus_datasource_uid
      model = jsonencode({
        refId         = "A"
        instant       = true
        intervalMs    = 1000
        maxDataPoints = 43200
        expr          = "rate(saasstarter_ratelimit_seconds_count{outcome=\"denied\"}[5m]) / rate(saasstarter_ratelimit_seconds_count[5m])"
      })
    }
    data {
      ref_id     = "B"
      query_type = ""
      relative_time_range {
        from = 0
        to   = 0
      }
      datasource_uid = "__expr__"
      model = jsonencode({
        refId      = "B"
        type       = "threshold"
        expression = "A"
        conditions = [{
          evaluator = { type = "gt", params = [0.1] }
          type      = "query"
        }]
        datasource = { type = "__expr__", uid = "__expr__" }
      })
    }
  }
}

# ── Redis locks ─────────────────────────────────────────────────────────────────

resource "grafana_rule_group" "locks" {
  name             = "Locks"
  folder_uid       = grafana_folder.alerts.uid
  interval_seconds = 60

  # Any lock errors indicate Redis connectivity or scripting issues.
  # Tune after observing one week of baseline traffic — see README → Alerts as code.
  rule {
    name           = "LockErrorRate"
    for            = "1m"
    condition      = "B"
    no_data_state  = "OK"
    exec_err_state = "Error"
    labels = {
      severity = "critical"
    }
    annotations = {
      summary     = "Redis lock errors — possible connectivity or scripting issues"
      description = "rate(saasstarter_lock_seconds_count{outcome=\"error\"}[5m]) > 0 for 1m"
    }
    data {
      ref_id     = "A"
      query_type = ""
      relative_time_range {
        from = 300
        to   = 0
      }
      datasource_uid = var.grafana_prometheus_datasource_uid
      model = jsonencode({
        refId         = "A"
        instant       = true
        intervalMs    = 1000
        maxDataPoints = 43200
        expr          = "rate(saasstarter_lock_seconds_count{outcome=\"error\"}[5m])"
      })
    }
    data {
      ref_id     = "B"
      query_type = ""
      relative_time_range {
        from = 0
        to   = 0
      }
      datasource_uid = "__expr__"
      model = jsonencode({
        refId      = "B"
        type       = "threshold"
        expression = "A"
        conditions = [{
          evaluator = { type = "gt", params = [0] }
          type      = "query"
        }]
        datasource = { type = "__expr__", uid = "__expr__" }
      })
    }
  }

  # Sustained lock contention — possible long-running operations blocking others.
  # Tune after observing one week of baseline traffic — see README → Alerts as code.
  rule {
    name           = "LockContentionHigh"
    for            = "10m"
    condition      = "B"
    no_data_state  = "OK"
    exec_err_state = "Error"
    labels = {
      severity = "warning"
    }
    annotations = {
      summary     = "High Redis lock contention — long-running operations may be blocking others"
      description = "rate(saasstarter_lock_seconds_count{outcome=\"contended\"}[5m]) > 0.2 for 10m"
    }
    data {
      ref_id     = "A"
      query_type = ""
      relative_time_range {
        from = 300
        to   = 0
      }
      datasource_uid = var.grafana_prometheus_datasource_uid
      model = jsonencode({
        refId         = "A"
        instant       = true
        intervalMs    = 1000
        maxDataPoints = 43200
        expr          = "rate(saasstarter_lock_seconds_count{outcome=\"contended\"}[5m])"
      })
    }
    data {
      ref_id     = "B"
      query_type = ""
      relative_time_range {
        from = 0
        to   = 0
      }
      datasource_uid = "__expr__"
      model = jsonencode({
        refId      = "B"
        type       = "threshold"
        expression = "A"
        conditions = [{
          evaluator = { type = "gt", params = [0.2] }
          type      = "query"
        }]
        datasource = { type = "__expr__", uid = "__expr__" }
      })
    }
  }
}

# ── Jobs & webhooks ─────────────────────────────────────────────────────────────

resource "grafana_rule_group" "jobs_webhooks" {
  name             = "JobsWebhooks"
  folder_uid       = grafana_folder.alerts.uid
  interval_seconds = 60

  # Any job scheduling failures (JobRunr storage unreachable etc.).
  # Tune after observing one week of baseline traffic — see README → Alerts as code.
  rule {
    name           = "JobSchedulerErrors"
    for            = "1m"
    condition      = "B"
    no_data_state  = "OK"
    exec_err_state = "Error"
    labels = {
      severity = "critical"
    }
    annotations = {
      summary     = "Job scheduler errors — JobRunr storage may be unreachable"
      description = "rate(saasstarter_job_seconds_count{error!=\"\"}[5m]) > 0 for 1m"
    }
    data {
      ref_id     = "A"
      query_type = ""
      relative_time_range {
        from = 300
        to   = 0
      }
      datasource_uid = var.grafana_prometheus_datasource_uid
      model = jsonencode({
        refId         = "A"
        instant       = true
        intervalMs    = 1000
        maxDataPoints = 43200
        expr          = "rate(saasstarter_job_seconds_count{error!=\"\"}[5m])"
      })
    }
    data {
      ref_id     = "B"
      query_type = ""
      relative_time_range {
        from = 0
        to   = 0
      }
      datasource_uid = "__expr__"
      model = jsonencode({
        refId      = "B"
        type       = "threshold"
        expression = "A"
        conditions = [{
          evaluator = { type = "gt", params = [0] }
          type      = "query"
        }]
        datasource = { type = "__expr__", uid = "__expr__" }
      })
    }
  }

  # Unhandled Stripe webhook events — billing state may be diverging.
  # Tune after observing one week of baseline traffic — see README → Alerts as code.
  rule {
    name           = "WebhookProcessingErrors"
    for            = "1m"
    condition      = "B"
    no_data_state  = "OK"
    exec_err_state = "Error"
    labels = {
      severity = "critical"
    }
    annotations = {
      summary     = "Stripe webhook processing errors — billing state may be diverging"
      description = "rate(saasstarter_webhook_stripe_seconds_count{outcome=\"error\"}[5m]) > 0 for 1m"
    }
    data {
      ref_id     = "A"
      query_type = ""
      relative_time_range {
        from = 300
        to   = 0
      }
      datasource_uid = var.grafana_prometheus_datasource_uid
      model = jsonencode({
        refId         = "A"
        instant       = true
        intervalMs    = 1000
        maxDataPoints = 43200
        expr          = "rate(saasstarter_webhook_stripe_seconds_count{outcome=\"error\"}[5m])"
      })
    }
    data {
      ref_id     = "B"
      query_type = ""
      relative_time_range {
        from = 0
        to   = 0
      }
      datasource_uid = "__expr__"
      model = jsonencode({
        refId      = "B"
        type       = "threshold"
        expression = "A"
        conditions = [{
          evaluator = { type = "gt", params = [0] }
          type      = "query"
        }]
        datasource = { type = "__expr__", uid = "__expr__" }
      })
    }
  }
}

# ── HTTP ────────────────────────────────────────────────────────────────────────

resource "grafana_rule_group" "http" {
  name             = "HTTP"
  folder_uid       = grafana_folder.alerts.uid
  interval_seconds = 60

  # >1% of requests returning 5xx.
  # Tune after observing one week of baseline traffic — see README → Alerts as code.
  rule {
    name           = "High5xxRate"
    for            = "5m"
    condition      = "B"
    no_data_state  = "OK"
    exec_err_state = "Error"
    labels = {
      severity = "critical"
    }
    annotations = {
      summary     = "High HTTP 5xx error rate"
      description = "rate(http_server_requests_seconds_count{status=~\"5..\"}[5m]) / rate(http_server_requests_seconds_count[5m]) > 0.01 for 5m"
    }
    data {
      ref_id     = "A"
      query_type = ""
      relative_time_range {
        from = 300
        to   = 0
      }
      datasource_uid = var.grafana_prometheus_datasource_uid
      model = jsonencode({
        refId         = "A"
        instant       = true
        intervalMs    = 1000
        maxDataPoints = 43200
        expr          = "rate(http_server_requests_seconds_count{status=~\"5..\"}[5m]) / rate(http_server_requests_seconds_count[5m])"
      })
    }
    data {
      ref_id     = "B"
      query_type = ""
      relative_time_range {
        from = 0
        to   = 0
      }
      datasource_uid = "__expr__"
      model = jsonencode({
        refId      = "B"
        type       = "threshold"
        expression = "A"
        conditions = [{
          evaluator = { type = "gt", params = [0.01] }
          type      = "query"
        }]
        datasource = { type = "__expr__", uid = "__expr__" }
      })
    }
  }

  # P99 latency above 2s.
  # Tune after observing one week of baseline traffic — see README → Alerts as code.
  rule {
    name           = "HighP99Latency"
    for            = "10m"
    condition      = "B"
    no_data_state  = "OK"
    exec_err_state = "Error"
    labels = {
      severity = "warning"
    }
    annotations = {
      summary     = "P99 request latency above 2s"
      description = "histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m])) > 2 for 10m"
    }
    data {
      ref_id     = "A"
      query_type = ""
      relative_time_range {
        from = 300
        to   = 0
      }
      datasource_uid = var.grafana_prometheus_datasource_uid
      model = jsonencode({
        refId         = "A"
        instant       = true
        intervalMs    = 1000
        maxDataPoints = 43200
        expr          = "histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))"
      })
    }
    data {
      ref_id     = "B"
      query_type = ""
      relative_time_range {
        from = 0
        to   = 0
      }
      datasource_uid = "__expr__"
      model = jsonencode({
        refId      = "B"
        type       = "threshold"
        expression = "A"
        conditions = [{
          evaluator = { type = "gt", params = [2] }
          type      = "query"
        }]
        datasource = { type = "__expr__", uid = "__expr__" }
      })
    }
  }
}

# ── JVM ─────────────────────────────────────────────────────────────────────────

resource "grafana_rule_group" "jvm" {
  name             = "JVM"
  folder_uid       = grafana_folder.alerts.uid
  interval_seconds = 60

  # Heap above 85% — GC pressure likely; OOM risk.
  # Tune after observing one week of baseline traffic — see README → Alerts as code.
  rule {
    name           = "JvmHeapPressure"
    for            = "10m"
    condition      = "B"
    no_data_state  = "OK"
    exec_err_state = "Error"
    labels = {
      severity = "warning"
    }
    annotations = {
      summary     = "JVM heap above 85% — GC pressure likely, OOM risk"
      description = "jvm_memory_used_bytes{area=\"heap\"} / jvm_memory_max_bytes{area=\"heap\"} > 0.85 for 10m"
    }
    data {
      ref_id     = "A"
      query_type = ""
      relative_time_range {
        from = 300
        to   = 0
      }
      datasource_uid = var.grafana_prometheus_datasource_uid
      model = jsonencode({
        refId         = "A"
        instant       = true
        intervalMs    = 1000
        maxDataPoints = 43200
        expr          = "jvm_memory_used_bytes{area=\"heap\"} / jvm_memory_max_bytes{area=\"heap\"}"
      })
    }
    data {
      ref_id     = "B"
      query_type = ""
      relative_time_range {
        from = 0
        to   = 0
      }
      datasource_uid = "__expr__"
      model = jsonencode({
        refId      = "B"
        type       = "threshold"
        expression = "A"
        conditions = [{
          evaluator = { type = "gt", params = [0.85] }
          type      = "query"
        }]
        datasource = { type = "__expr__", uid = "__expr__" }
      })
    }
  }
}
