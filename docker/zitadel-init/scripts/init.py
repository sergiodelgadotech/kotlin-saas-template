#!/usr/bin/env python3
"""
Idempotent Zitadel seed: registers the local-dev OIDC app and creates a test user.
Authenticates via the machine service account key file generated during Zitadel setup.
"""
import json, sys, time, base64, os, subprocess
from pathlib import Path

ZITADEL_URL = os.getenv("ZITADEL_URL", "http://zitadel:8080")
KEY_FILE_PATH = Path(os.getenv("KEY_FILE_PATH", "/keys/zitadel-init-sa.json"))
OUTPUT_FILE = Path(os.getenv("OUTPUT_FILE", "/output/.local-client-id"))
REDIRECT_URI = "http://localhost:8080/login/oauth2/code/zitadel"
POST_LOGOUT_URI = "http://localhost:8080/"


def read_key_file():
    if not KEY_FILE_PATH.exists():
        print(f"ERROR: Key file not found at {KEY_FILE_PATH}", file=sys.stderr)
        sys.exit(1)
    return json.loads(KEY_FILE_PATH.read_text())


def generate_jwt(key_data: dict) -> str:
    key_id = key_data["keyId"]
    user_id = key_data["userId"]
    private_key = key_data["key"]
    now = int(time.time())
    exp = now + 60

    header = base64.urlsafe_b64encode(
        json.dumps({"alg": "RS256", "typ": "JWT", "kid": key_id}).encode()
    ).rstrip(b"=").decode()

    payload = base64.urlsafe_b64encode(
        json.dumps({"iss": user_id, "sub": user_id, "aud": ZITADEL_URL,
                    "iat": now, "exp": exp}).encode()
    ).rstrip(b"=").decode()

    signing_input = f"{header}.{payload}"
    key_path = Path("/tmp/init-sa.pem")
    key_path.write_text(private_key)

    result = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", str(key_path)],
        input=signing_input.encode(), capture_output=True,
    )
    if result.returncode != 0:
        print(f"ERROR: openssl signing failed: {result.stderr.decode()}", file=sys.stderr)
        sys.exit(1)

    return f"{signing_input}.{base64.urlsafe_b64encode(result.stdout).rstrip(b'=').decode()}"


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
        client_id = OUTPUT_FILE.read_text().strip() if OUTPUT_FILE.exists() else None
        if not client_id:
            print("  ERROR: App exists but no .local-client-id found. Delete the Zitadel volume and re-run.",
                  file=sys.stderr)
            sys.exit(1)
    else:
        client_id = app_resp["clientId"]
        OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT_FILE.write_text(client_id)
        print(f"  Created app (client ID: {client_id})")

    print("Creating test user (test@example.com / Test1234!)...")
    api("POST", "/management/v1/users/human/_import", token, {
        "userName": "test",
        "profile": {"firstName": "Test", "lastName": "User"},
        "email": {"email": "test@example.com", "isEmailVerified": True},
        "password": {"password": "Test1234!", "passwordChangeRequired": False},
    })

    print()
    print("=" * 60)
    print(f"ZITADEL_CLIENT_ID={client_id}")
    print("Copy the above into application-local.yml")
    print("=" * 60)


if __name__ == "__main__":
    main()
