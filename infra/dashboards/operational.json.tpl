{
  "title": "Operational",
  "uid": "saas-template-operational",
  "schemaVersion": 37,
  "refresh": "30s",
  "time": { "from": "now-1h", "to": "now" },
  "templating": {
    "list": [
      {
        "name": "tenant_id",
        "label": "Tenant",
        "type": "query",
        "datasource": { "type": "prometheus", "uid": "${prometheus_uid}" },
        "definition": "label_values(saasstarter_auth_jwt_seconds_count, tenant_id)",
        "query": "label_values(saasstarter_auth_jwt_seconds_count, tenant_id)",
        "includeAll": true,
        "allValue": ".*",
        "multi": true,
        "refresh": 2,
        "sort": 1
      }
    ]
  },
  "panels": [
    {
      "type": "row",
      "title": "HTTP Traffic",
      "gridPos": { "h": 1, "w": 24, "x": 0, "y": 0 },
      "collapsed": false
    },
    {
      "type": "timeseries",
      "title": "Request rate by status class",
      "datasource": { "type": "prometheus", "uid": "${prometheus_uid}" },
      "gridPos": { "h": 8, "w": 8, "x": 0, "y": 1 },
      "targets": [
        {
          "expr": "sum by (status) (rate(http_server_requests_seconds_count{tenant_id=~\"$tenant_id\"}[5m]))",
          "legendFormat": "{{status}}"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "reqps",
          "custom": { "lineWidth": 1, "fillOpacity": 10 }
        }
      }
    },
    {
      "type": "timeseries",
      "title": "P99 latency by URI",
      "datasource": { "type": "prometheus", "uid": "${prometheus_uid}" },
      "gridPos": { "h": 8, "w": 8, "x": 8, "y": 1 },
      "targets": [
        {
          "expr": "histogram_quantile(0.99, sum by (le, uri) (rate(http_server_requests_seconds_bucket{tenant_id=~\"$tenant_id\"}[5m])))",
          "legendFormat": "{{uri}}"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "s",
          "custom": { "lineWidth": 1, "fillOpacity": 10 }
        }
      }
    },
    {
      "type": "timeseries",
      "title": "5xx error rate",
      "datasource": { "type": "prometheus", "uid": "${prometheus_uid}" },
      "gridPos": { "h": 8, "w": 8, "x": 16, "y": 1 },
      "targets": [
        {
          "expr": "sum(rate(http_server_requests_seconds_count{status=~\"5..\",tenant_id=~\"$tenant_id\"}[5m])) / sum(rate(http_server_requests_seconds_count{tenant_id=~\"$tenant_id\"}[5m]))",
          "legendFormat": "error rate"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "percentunit",
          "custom": { "lineWidth": 1, "fillOpacity": 10 },
          "thresholds": {
            "mode": "absolute",
            "steps": [
              { "color": "green", "value": null },
              { "color": "red", "value": 0.01 }
            ]
          }
        }
      }
    },
    {
      "type": "row",
      "title": "Auth & Rate Limiting",
      "gridPos": { "h": 1, "w": 24, "x": 0, "y": 9 },
      "collapsed": false
    },
    {
      "type": "timeseries",
      "title": "JWT auth outcomes",
      "datasource": { "type": "prometheus", "uid": "${prometheus_uid}" },
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 10 },
      "targets": [
        {
          "expr": "sum by (outcome) (rate(saasstarter_auth_jwt_seconds_count{tenant_id=~\"$tenant_id\"}[5m]))",
          "legendFormat": "{{outcome}}"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "reqps",
          "custom": { "lineWidth": 1, "fillOpacity": 10 }
        }
      }
    },
    {
      "type": "timeseries",
      "title": "Rate-limit denied ratio",
      "datasource": { "type": "prometheus", "uid": "${prometheus_uid}" },
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 10 },
      "targets": [
        {
          "expr": "sum(rate(saasstarter_ratelimit_seconds_count{outcome=\"denied\",tenant_id=~\"$tenant_id\"}[5m])) / sum(rate(saasstarter_ratelimit_seconds_count{tenant_id=~\"$tenant_id\"}[5m]))",
          "legendFormat": "denied ratio"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "percentunit",
          "custom": { "lineWidth": 1, "fillOpacity": 10 },
          "thresholds": {
            "mode": "absolute",
            "steps": [
              { "color": "green", "value": null },
              { "color": "yellow", "value": 0.05 },
              { "color": "red", "value": 0.2 }
            ]
          }
        }
      }
    },
    {
      "type": "row",
      "title": "Background Jobs & Webhooks",
      "gridPos": { "h": 1, "w": 24, "x": 0, "y": 18 },
      "collapsed": false
    },
    {
      "type": "timeseries",
      "title": "Job rate by operation",
      "datasource": { "type": "prometheus", "uid": "${prometheus_uid}" },
      "gridPos": { "h": 8, "w": 8, "x": 0, "y": 19 },
      "targets": [
        {
          "expr": "sum by (operation) (rate(saasstarter_job_seconds_count{tenant_id=~\"$tenant_id\"}[5m]))",
          "legendFormat": "{{operation}}"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "reqps",
          "custom": { "lineWidth": 1, "fillOpacity": 10 }
        }
      }
    },
    {
      "type": "timeseries",
      "title": "Failed jobs by operation",
      "datasource": { "type": "prometheus", "uid": "${prometheus_uid}" },
      "gridPos": { "h": 8, "w": 8, "x": 8, "y": 19 },
      "targets": [
        {
          "expr": "sum by (operation) (rate(saasstarter_job_seconds_count{error!=\"\",tenant_id=~\"$tenant_id\"}[5m]))",
          "legendFormat": "{{operation}} (error)"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "reqps",
          "custom": { "lineWidth": 1, "fillOpacity": 10 },
          "color": { "mode": "fixed", "fixedColor": "red" }
        }
      }
    },
    {
      "type": "timeseries",
      "title": "Stripe webhook outcomes",
      "datasource": { "type": "prometheus", "uid": "${prometheus_uid}" },
      "gridPos": { "h": 8, "w": 8, "x": 16, "y": 19 },
      "targets": [
        {
          "expr": "sum by (outcome) (rate(saasstarter_webhook_stripe_seconds_count{tenant_id=~\"$tenant_id\"}[5m]))",
          "legendFormat": "{{outcome}}"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "reqps",
          "custom": { "lineWidth": 1, "fillOpacity": 10 }
        }
      }
    },
    {
      "type": "row",
      "title": "Infrastructure",
      "gridPos": { "h": 1, "w": 24, "x": 0, "y": 27 },
      "collapsed": false
    },
    {
      "type": "timeseries",
      "title": "JVM heap usage",
      "description": "Process-level metric — not tenant-scoped.",
      "datasource": { "type": "prometheus", "uid": "${prometheus_uid}" },
      "gridPos": { "h": 8, "w": 8, "x": 0, "y": 28 },
      "targets": [
        {
          "expr": "jvm_memory_used_bytes{area=\"heap\"}",
          "legendFormat": "used"
        },
        {
          "expr": "jvm_memory_committed_bytes{area=\"heap\"}",
          "legendFormat": "committed"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "bytes",
          "custom": { "lineWidth": 1, "fillOpacity": 10 }
        }
      }
    },
    {
      "type": "timeseries",
      "title": "Redis lock contention rate",
      "datasource": { "type": "prometheus", "uid": "${prometheus_uid}" },
      "gridPos": { "h": 8, "w": 8, "x": 8, "y": 28 },
      "targets": [
        {
          "expr": "sum(rate(saasstarter_lock_seconds_count{outcome=\"contended\",tenant_id=~\"$tenant_id\"}[5m]))",
          "legendFormat": "contended"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "reqps",
          "custom": { "lineWidth": 1, "fillOpacity": 10 }
        }
      }
    },
    {
      "type": "timeseries",
      "title": "Redis lock error rate",
      "datasource": { "type": "prometheus", "uid": "${prometheus_uid}" },
      "gridPos": { "h": 8, "w": 8, "x": 16, "y": 28 },
      "targets": [
        {
          "expr": "sum(rate(saasstarter_lock_seconds_count{outcome=\"error\",tenant_id=~\"$tenant_id\"}[5m]))",
          "legendFormat": "error"
        }
      ],
      "fieldConfig": {
        "defaults": {
          "unit": "reqps",
          "custom": { "lineWidth": 1, "fillOpacity": 10 },
          "color": { "mode": "fixed", "fixedColor": "red" }
        }
      }
    }
  ]
}
