variable "railway_token" {
  description = "Railway API token"
  type        = string
  sensitive   = true
}

variable "cloudflare_api_token" {
  description = "Cloudflare API token"
  type        = string
  sensitive   = true
}

variable "cloudflare_zone_id" {
  description = "Cloudflare Zone ID for your domain"
  type        = string
}

variable "project_name" {
  description = "Project name in Railway"
  type        = string
  default     = "kotlin-saas-template"
}

variable "railway_app_domain" {
  description = "Railway-generated domain for the app service"
  type        = string
}

variable "cloudflare_pages_domain" {
  description = "Cloudflare Pages domain for the landing site"
  type        = string
}

variable "grafana_url" {
  description = "Grafana Cloud stack URL (e.g. https://yourstack.grafana.net)"
  type        = string
}

variable "grafana_api_key" {
  description = "Grafana API key with Editor role"
  type        = string
  sensitive   = true
}

variable "grafana_prometheus_datasource_uid" {
  description = "UID of the Prometheus datasource in Grafana Cloud (Connections → Data sources → Prometheus → copy from URL)"
  type        = string
}

variable "alert_email" {
  description = "Email address to receive Grafana alert notifications"
  type        = string
}
