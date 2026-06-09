#!/usr/bin/env python3
"""
Idempotent Zitadel seed: registers the local-dev OIDC app and creates a test user.
Authenticates via the machine service account key file generated during Zitadel setup.
"""
import json, sys, time, base64, os
from pathlib import Path

ZITADEL_URL = os.getenv("ZITADEL_URL", "http://zitadel:8080")
KEY_FILE_PATH = Path(os.getenv("KEY_FILE_PATH", "/keys/zitadel-init-sa.json"))
OUTPUT_FILE = Path(os.getenv("OUTPUT_FILE", "/output/.local-client-id"))
REDIRECT_URI = "http://localhost:8080/login/oauth2/code/zitadel"
POST_LOGOUT_URI = "http://localhost:8080/"


def wait_for_zitadel(max_attempts: int = 60, delay: int = 5) -> None:
    import urllib.request
    # Poll the OIDC discovery endpoint — it only becomes available after Zitadel
    # has completed setup and registered all routes (later than /debug/ready).
    endpoint = f"{ZITADEL_URL}/.well-known/openid-configuration"
    print(f"Waiting for Zitadel OIDC discovery at {endpoint} ...")
    for attempt in range(1, max_attempts + 1):
        try:
            with urllib.request.urlopen(endpoint, timeout=3) as r:
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
    req = urllib.request.Request(
        f"{ZITADEL_URL}/oauth/v2/token", data=data,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read())["access_token"]


def api(method: str, path: str, token: str, body: dict | None = None):
    import urllib.request, urllib.error
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(
        f"{ZITADEL_URL}{path}", data=data,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        method=method,
    )
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        body_text = e.read().decode()
        if e.code == 409:
            return {"already_exists": True, "detail": body_text}
        print(f"ERROR {e.code} {method} {path}: {body_text}", file=sys.stderr)
        sys.exit(1)


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

    print()
    print("=" * 60)
    print(f"ZITADEL_CLIENT_ID={client_id}")
    print("Written to docker/zitadel-init/.local-client.properties")
    print("Spring Boot auto-imports it — no manual copy needed.")
    print("=" * 60)


if __name__ == "__main__":
    main()
