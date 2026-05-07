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
  default     = "mvp-saas"
}

variable "railway_app_domain" {
  description = "Railway-generated domain for the app service"
  type        = string
}

variable "cloudflare_pages_domain" {
  description = "Cloudflare Pages domain for the landing site"
  type        = string
}
