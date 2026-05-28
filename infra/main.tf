terraform {
  required_providers {
    railway = {
      source  = "terraform-community-modules/railway"
      version = "~> 0.3"
    }
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 5.0"
    }
  }

  backend "remote" {
    # Configure your Terraform Cloud or S3 backend here
  }
}

provider "railway" {
  token = var.railway_token
}

provider "cloudflare" {
  api_token = var.cloudflare_api_token
}

# Railway project
resource "railway_project" "main" {
  name = var.project_name
}

# PostgreSQL
resource "railway_service" "postgres" {
  project_id   = railway_project.main.id
  name         = "postgres"
  source_image = "postgres:16-alpine"
}

# Redis
resource "railway_service" "redis" {
  project_id   = railway_project.main.id
  name         = "redis"
  source_image = "redis:7-alpine"
}

# App service
resource "railway_service" "app" {
  project_id = railway_project.main.id
  name       = "app"
}

# ── Cloudflare DNS ─────────────────────────────────────────────────────────────

# app.tuproducto.com → Railway, proxied through Cloudflare WAF
# proxied = true activates WAF, DDoS protection, and SSL termination
resource "cloudflare_record" "app" {
  zone_id = var.cloudflare_zone_id
  name    = "app"
  value   = var.railway_app_domain
  type    = "CNAME"
  proxied = true   # ← activates Cloudflare WAF
}

# www and apex → Cloudflare Pages (landing site)
resource "cloudflare_record" "www" {
  zone_id = var.cloudflare_zone_id
  name    = "www"
  value   = var.cloudflare_pages_domain
  type    = "CNAME"
  proxied = true
}

# ── Cloudflare WAF Rules ───────────────────────────────────────────────────────

# Block requests not originating from Cloudflare IPs.
# This prevents bypassing the WAF by hitting Railway directly.
# Cloudflare IP ranges: https://www.cloudflare.com/ips/
resource "cloudflare_ruleset" "block_non_cloudflare" {
  zone_id     = var.cloudflare_zone_id
  name        = "Block direct Railway access"
  description = "Only allow traffic through Cloudflare proxy"
  kind        = "zone"
  phase       = "http_request_firewall_custom"

  rules {
    action      = "block"
    description = "Block requests not from Cloudflare IPs"
    enabled     = true
    expression  = "(not cf.tls_client_auth.cert_verified)"
  }
}

# Rate limit login endpoint to prevent brute force
resource "cloudflare_ruleset" "rate_limit_login" {
  zone_id     = var.cloudflare_zone_id
  name        = "Rate limit auth endpoints"
  description = "Prevent brute force on login"
  kind        = "zone"
  phase       = "http_ratelimit"

  rules {
    action      = "block"
    description = "10 login attempts per minute per IP"
    enabled     = true
    expression  = "(http.request.uri.path contains \"/sign-in\")"

    action_parameters {
      response {
        status_code  = 429
        content_type = "text/plain"
        content      = "Too many requests"
      }
    }

    ratelimit {
      characteristics     = ["cf.colo.id", "ip.src"]
      period              = 60
      requests_per_period = 10
      mitigation_timeout  = 60
    }
  }
}
