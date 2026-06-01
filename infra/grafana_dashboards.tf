# ── Grafana Dashboards ─────────────────────────────────────────────────────────

resource "grafana_dashboard" "operational" {
  config_json = templatefile("${path.module}/dashboards/operational.json.tpl", {
    prometheus_uid = var.grafana_prometheus_datasource_uid
  })
  overwrite = true
}
