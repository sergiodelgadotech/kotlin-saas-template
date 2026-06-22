#!/usr/bin/env python3
"""
Idempotent Zitadel seed: registers the local-dev OIDC app, creates a test user,
provisions a management-api service account with a PAT for the Spring app,
and configures SMTP for invitation emails via the Zitadel Admin API.
Authenticates via the machine service account key file generated during Zitadel setup.
"""
import json, sys, time, base64, os
from pathlib import Path
from urllib.parse import urlsplit

# ZITADEL_URL is the *external* / public URL (issuer, JWT audience, and the Host
# header Zitadel's virtual-host routing requires). CONNECT_URL is where we open the
# actual TCP connection. They differ when this container can't share the host
# network (e.g. inside an sbx microVM, where `network_mode: host` is not honored):
# we then connect over the Docker bridge to `http://zitadel:8080` while still sending
# `Host: localhost:8089` so Zitadel resolves the instance correctly. When CONNECT_URL
# is unset they're identical, matching the original host-networking behavior.
ZITADEL_URL = os.getenv("ZITADEL_URL", "http://zitadel:8080")
CONNECT_URL = os.getenv("ZITADEL_CONNECT_URL", ZITADEL_URL).rstrip("/")
_EXTERNAL_HOST = urlsplit(ZITADEL_URL).netloc  # e.g. "localhost:8089"
KEY_FILE_PATH = Path(os.getenv("KEY_FILE_PATH", "/keys/zitadel-init-sa.json"))
OUTPUT_FILE = Path(os.getenv("OUTPUT_FILE", "/output/.local-client-id"))
MGMT_PAT_FILE = Path(os.getenv("MGMT_PAT_FILE", "/output/management-api.pat"))
MGMT_PROPS_FILE = Path(os.getenv("MGMT_PROPS_FILE", "/output/.local-management.properties"))
SMTP_CONFIGURED_FILE = Path("/output/.smtp-configured")
DEFAULT_REDIRECT_CONFIGURED_FILE = Path("/output/.default-redirect-uri-configured")
REDIRECT_URI = "http://localhost:8080/login/oauth2/code/zitadel"
POST_LOGOUT_URI = "http://localhost:8080/"
DEFAULT_REDIRECT_URI = "http://localhost:8080/"


def _request(path: str, data: bytes | None = None,
             headers: dict | None = None, method: str | None = None):
    """Build a urllib Request against CONNECT_URL, always carrying the external
    Host header so Zitadel's virtual-host routing resolves the instance even when
    we connect over the Docker bridge (zitadel:8080) rather than localhost:8089."""
    import urllib.request
    merged = {"Host": _EXTERNAL_HOST}
    if headers:
        merged.update(headers)
    return urllib.request.Request(f"{CONNECT_URL}{path}", data=data,
                                  headers=merged, method=method)


def wait_for_zitadel(max_attempts: int = 60, delay: int = 5) -> None:
    import urllib.request
    # Poll the OIDC discovery endpoint — it only becomes available after Zitadel
    # has completed setup and registered all routes (later than /debug/ready).
    endpoint = f"{CONNECT_URL}/.well-known/openid-configuration"
    print(f"Waiting for Zitadel OIDC discovery at {endpoint} (Host: {_EXTERNAL_HOST}) ...")
    for attempt in range(1, max_attempts + 1):
        try:
            with urllib.request.urlopen(_request("/.well-known/openid-configuration"), timeout=3) as r:
                if r.status == 200:
                    print("  Zitadel is ready.")
                    return
        except Exception as e:
            print(f"  [{attempt}/{max_attempts}] not ready ({e}), retrying in {delay}s...")
        time.sleep(delay)
    print("ERROR: Zitadel did not become ready in time.", file=sys.stderr)
    sys.exit(1)


def read_key_file():
    if not KEY_FILE_PATH.exists():
        print(f"ERROR: Key file not found at {KEY_FILE_PATH}", file=sys.stderr)
        sys.exit(1)
    return json.loads(KEY_FILE_PATH.read_text())


def generate_jwt(key_data: dict) -> str:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding

    key_id = key_data["keyId"]
    user_id = key_data["userId"]
    now = int(time.time())

    header = base64.urlsafe_b64encode(
        json.dumps({"alg": "RS256", "typ": "JWT", "kid": key_id}).encode()
    ).rstrip(b"=").decode()

    payload = base64.urlsafe_b64encode(
        json.dumps({"iss": user_id, "sub": user_id, "aud": ZITADEL_URL,
                    "iat": now, "exp": now + 60}).encode()
    ).rstrip(b"=").decode()

    signing_input = f"{header}.{payload}"
    private_key = serialization.load_pem_private_key(key_data["key"].encode(), password=None)
    signature = private_key.sign(signing_input.encode(), padding.PKCS1v15(), hashes.SHA256())
    return f"{signing_input}.{base64.urlsafe_b64encode(signature).rstrip(b'=').decode()}"


def get_access_token(jwt: str) -> str:
    import urllib.request, urllib.parse
    data = urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": jwt,
        "scope": "openid urn:zitadel:iam:org:project:id:zitadel:aud",
    }).encode()
    req = _request("/oauth/v2/token", data=data,
                   headers={"Content-Type": "application/x-www-form-urlencoded"})
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read())["access_token"]


def api(method: str, path: str, token: str, body: dict | None = None,
        _retries: int = 6, _retry_delay: int = 5):
    import urllib.request, urllib.error
    data = json.dumps(body).encode() if body else None
    req = _request(
        path, data=data,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        method=method,
    )
    for attempt in range(1, _retries + 1):
        try:
            with urllib.request.urlopen(req) as resp:
                return json.loads(resp.read())
        except urllib.error.HTTPError as e:
            body_text = e.read().decode()
            if e.code == 409:
                return {"already_exists": True, "detail": body_text}
            if e.code == 403:
                return {"forbidden": True, "detail": body_text}
            if e.code == 503 and attempt < _retries:
                print(f"  503 on {method} {path}, retrying in {_retry_delay}s... ({attempt}/{_retries})")
                time.sleep(_retry_delay)
                continue
            print(f"ERROR {e.code} {method} {path}: {body_text}", file=sys.stderr)
            sys.exit(1)


def configure_smtp(token: str) -> None:
    smtp_password = os.getenv("RESEND_API_KEY", "")
    if not smtp_password:
        print("RESEND_API_KEY not set, skipping SMTP configuration.")
        return

    if SMTP_CONFIGURED_FILE.exists():
        print("SMTP already configured (flag file exists), skipping.")
        return

    smtp_from = os.getenv("RESEND_FROM_ADDRESS", "")
    smtp_from_name = os.getenv("RESEND_FROM_NAME", "")

    print("Configuring SMTP provider (Resend)...")
    resp = api("POST", "/admin/v1/email/smtp", token, {
        "senderAddress": smtp_from,
        "senderName": smtp_from_name,
        "tls": True,
        "host": "smtp.resend.com:465",
        "user": "resend",
        "password": smtp_password,
    })

    if resp.get("forbidden"):
        print("  WARNING: zitadel-init-sa lacks IAM_OWNER — cannot configure SMTP via API.")
        print("  Configure it manually: http://localhost:8089/ui/console")
        print("  Instance Settings → Messaging → Email → Add provider → SMTP")
        return

    if resp.get("already_exists"):
        print("  SMTP provider already exists, skipping.")
        SMTP_CONFIGURED_FILE.touch()
        return

    provider_id = resp["id"]
    print(f"  Created SMTP provider (id={provider_id})")

    api("POST", f"/admin/v1/email/{provider_id}/_activate", token, {})
    print("  Activated SMTP provider.")

    SMTP_CONFIGURED_FILE.touch()


def configure_default_redirect_uri(token: str) -> None:
    if DEFAULT_REDIRECT_CONFIGURED_FILE.exists():
        print("Default Redirect URI already configured (flag file exists), skipping.")
        return

    print("Configuring instance Default Redirect URI...")

    get_resp = api("GET", "/admin/v1/policies/login", token)
    policy = get_resp.get("policy", {})

    if policy.get("defaultRedirectUri") == DEFAULT_REDIRECT_URI:
        print("  Default Redirect URI already set correctly, skipping.")
        DEFAULT_REDIRECT_CONFIGURED_FILE.touch()
        return

    # UpdateLoginPolicy is a full-replacement PUT (no FieldMask) — copy all
    # existing fields to avoid zeroing MFA timeouts, allowUsernamePassword, etc.
    excluded = {"details", "isDefault"}
    put_body = {k: v for k, v in policy.items() if k not in excluded}
    put_body["defaultRedirectUri"] = DEFAULT_REDIRECT_URI

    resp = api("PUT", "/admin/v1/policies/login", token, put_body)

    if resp.get("forbidden"):
        print("  WARNING: zitadel-init-sa lacks IAM_OWNER — cannot configure Default Redirect URI via API.")
        print("  Configure it manually: http://localhost:8089/ui/console")
        print("  Default Settings → Login Behavior and Security → Default Redirect URI")
        return

    print(f"  Default Redirect URI set to {DEFAULT_REDIRECT_URI}")
    DEFAULT_REDIRECT_CONFIGURED_FILE.touch()


def provision_management_service_account(token: str) -> None:
    """
    Idempotently creates a management-api-sa service account, grants it ORG_OWNER,
    and writes a PAT to MGMT_PAT_FILE.  Skips all steps if the PAT file already exists.
    """
    if MGMT_PROPS_FILE.exists():
        print(f"Management properties already exist at {MGMT_PROPS_FILE}, skipping.")
        return

    print("Provisioning management-api service account...")

    # 1. Find or create the machine user
    search_resp = api("POST", "/management/v1/users/_search", token, {
        "queries": [{"userNameQuery": {"userName": "management-api-sa",
                                       "method": "TEXT_QUERY_METHOD_EQUALS"}}]
    })
    existing = search_resp.get("result") or []
    if existing:
        user_id = existing[0]["id"]
        print(f"  management-api-sa already exists (ID: {user_id}), reusing.")
    else:
        create_resp = api("POST", "/management/v1/users/machine", token, {
            "userName": "management-api-sa",
            "name": "Management API Service Account",
            "description": "Service account used by the Spring app to manage Zitadel users",
            "accessTokenType": "ACCESS_TOKEN_TYPE_BEARER",
        })
        user_id = create_resp["userId"]
        print(f"  Created machine user management-api-sa (ID: {user_id})")

    # 2. Grant ORG_OWNER so the account can manage users within the default org
    member_resp = api("POST", "/management/v1/orgs/me/members", token, {
        "userId": user_id,
        "roles": ["ORG_OWNER"],
    })
    if member_resp.get("already_exists"):
        print("  ORG_OWNER membership already exists, skipping.")
    else:
        print("  Granted ORG_OWNER to management-api-sa.")

    # 3. Generate a PAT (no expiry for local dev convenience)
    pat_resp = api("POST", f"/management/v1/users/{user_id}/pats", token, {
        "expirationDate": "2099-01-01T00:00:00Z",
    })
    pat_token = pat_resp["token"]

    # 4. Fetch the default org ID so the app knows which org to scope users to
    org_resp = api("GET", "/management/v1/orgs/me", token, None)
    org_id = org_resp["org"]["id"]

    # 5. Persist the PAT (standalone file, kept for backwards compat / easy inspection)
    MGMT_PAT_FILE.parent.mkdir(parents=True, exist_ok=True)
    MGMT_PAT_FILE.write_text(pat_token)
    print(f"  PAT written to {MGMT_PAT_FILE}")

    # 6. Write Spring-importable properties file — auto-loaded by application-local.yml
    MGMT_PROPS_FILE.write_text(
        f"saastemplate.zitadel.management.organization-id={org_id}\n"
        f"saastemplate.zitadel.management.pat={pat_token}\n"
    )
    print(f"  Management properties written to {MGMT_PROPS_FILE}")


def main():
    wait_for_zitadel()

    print("Reading service account key file...")
    key_data = read_key_file()

    print("Generating JWT assertion and obtaining access token...")
    token = get_access_token(generate_jwt(key_data))

    print("Creating project...")
    project_resp = api("POST", "/management/v1/projects", token, {"name": "Kotlin SaaS Template"})
    if project_resp.get("already_exists"):
        projects = api("POST", "/management/v1/projects/_search", token, {
            "queries": [{"nameQuery": {"name": "Kotlin SaaS Template",
                                       "method": "TEXT_QUERY_METHOD_EQUALS"}}]
        })
        project_id = projects["result"][0]["id"]
        print(f"  Project already exists (ID: {project_id}), skipping.")
    else:
        project_id = project_resp["id"]
        print(f"  Created project (ID: {project_id})")

    print("Creating OIDC web application...")
    app_resp = api("POST", f"/management/v1/projects/{project_id}/apps/oidc", token, {
        "name": "local-dev",
        "redirectUris": [REDIRECT_URI],
        "responseTypes": ["OIDC_RESPONSE_TYPE_CODE"],
        "grantTypes": ["OIDC_GRANT_TYPE_AUTHORIZATION_CODE"],
        "appType": "OIDC_APP_TYPE_WEB",
        "authMethodType": "OIDC_AUTH_METHOD_TYPE_NONE",
        "postLogoutRedirectUris": [POST_LOGOUT_URI],
        "devMode": True,
    })

    if app_resp.get("already_exists"):
        print("  App already exists.")
        if OUTPUT_FILE.exists():
            props = dict(line.split("=", 1) for line in OUTPUT_FILE.read_text().splitlines() if "=" in line)
            client_id = props.get("ZITADEL_CLIENT_ID")
        else:
            client_id = None
        if not client_id:
            print("  ERROR: App exists but no .local-client.properties found. Delete the Zitadel volume and re-run.",
                  file=sys.stderr)
            sys.exit(1)
    else:
        client_id = app_resp["clientId"]
        OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT_FILE.write_text(f"ZITADEL_CLIENT_ID={client_id}\n")
        print(f"  Created app (client ID: {client_id})")

    print("Creating test user (test@example.com / Test1234!)...")
    api("POST", "/management/v1/users/human/_import", token, {
        "userName": "test",
        "profile": {"firstName": "Test", "lastName": "User"},
        "email": {"email": "test@example.com", "isEmailVerified": True},
        "password": "Test1234!",
    })

    provision_management_service_account(token)
    configure_smtp(token)
    configure_default_redirect_uri(token)

    print()
    print("=" * 60)
    print(f"ZITADEL_CLIENT_ID={client_id}")
    print("Written to docker/zitadel-init/.local-client.properties")
    print("Spring Boot auto-imports it — no manual copy needed.")
    print()
    print(f"Management properties written to docker/zitadel-init/.local-management.properties")
    print("Spring Boot auto-imports them — no manual copy needed.")
    print("=" * 60)


if __name__ == "__main__":
    main()
