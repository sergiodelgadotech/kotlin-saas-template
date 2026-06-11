---
name: zitadel-smtp-port-465
description: Zitadel SMTP for Resend must use port 465 (SMTPS/direct TLS), not port 587 (STARTTLS)
metadata:
  type: feedback
---

Use `smtp.resend.com:465` with `tls=true` when configuring Zitadel's SMTP provider for Resend.

**Why:** Two bugs/limitations discovered through painful debugging:
1. `DefaultInstance.SMTPConfiguration` env vars silently drop `SMTP.User` and `SMTP.Password` — credentials never make it to the DB. Must configure via Admin API (`POST /admin/v1/email/smtp`).
2. Zitadel's STARTTLS fallback path (port 587, `tls=true`) drops auth credentials when the initial TLS dial fails and falls back to plain+STARTTLS. Port 587 with `tls=false` also fails because Zitadel doesn't negotiate STARTTLS at all. Port 465 with `tls=true` uses direct TLS from the start — no fallback, no lost credentials.

**How to apply:** In `init.py`'s `configure_smtp()`, always use `"host": "smtp.resend.com:465"` and `"tls": True`.
