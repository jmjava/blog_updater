#!/usr/bin/env python3
"""Obtain token via OAuth 2.0 device flow (no local browser).

Prints a URL and code; you authorize on any device (phone/laptop). Then the script
polls and saves the token. Requires Google Cloud OAuth client type "TV and Limited
Input Device" if using device flow; otherwise get token once via browser and use
  python scripts/store_token.py < token.json
  or  curl -X POST http://localhost:8080/auth/token -H "Content-Type: application/json" -d @token.json
"""
import json
import sys
import time
from pathlib import Path

import requests

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from blogger_auth import BLOGGER_SCOPE, _cred_path, store_token_from_dict

DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
TOKEN_URL = "https://oauth2.googleapis.com/token"


def load_client():
    with open(_cred_path()) as f:
        data = json.load(f)
    c = data.get("web") or data.get("installed") or data
    return c["client_id"], c["client_secret"]


def main():
    client_id, client_secret = load_client()
    scope = " ".join(BLOGGER_SCOPE)
    r = requests.post(
        DEVICE_CODE_URL,
        data={"client_id": client_id, "scope": scope},
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        timeout=30,
    )
    if r.status_code != 200:
        print(f"Device code request failed: {r.status_code} {r.text}", file=sys.stderr)
        print("Tip: Device flow may require 'TV and Limited Input Device' OAuth client in Google Cloud.", file=sys.stderr)
        print("Alternatively: run browser auth once, then: python scripts/store_token.py < token.json", file=sys.stderr)
        sys.exit(1)
    data = r.json()
    user_code = data.get("user_code")
    verification_url = data.get("verification_url", "https://www.google.com/device")
    device_code = data.get("device_code")
    interval = data.get("interval", 5)
    print("Open this URL in any browser (phone or laptop):")
    print(verification_url)
    print("Enter this code:", user_code)
    print("Waiting for you to authorize...")
    while True:
        time.sleep(interval)
        tr = requests.post(
            TOKEN_URL,
            data={
                "client_id": client_id,
                "client_secret": client_secret,
                "code": device_code,
                "grant_type": "urn:ietf:params:oauth:grant-type:device_code",
            },
            headers={"Content-Type": "application/x-www-form-urlencoded"},
            timeout=30,
        )
        if tr.status_code == 200:
            tok = tr.json()
            store_token_from_dict({
                "token": tok.get("access_token"),
                "refresh_token": tok.get("refresh_token"),
                "token_uri": TOKEN_URL,
                "client_id": client_id,
                "client_secret": client_secret,
                "scopes": BLOGGER_SCOPE,
            })
            print("Token saved. You can use GET /blogs now.")
            return
        err = tr.json() if tr.headers.get("content-type", "").startswith("application/json") else {}
        if err.get("error") == "authorization_pending":
            continue
        if err.get("error") == "slow_down":
            interval += 5
            continue
        print(f"Token request failed: {tr.status_code} {tr.text}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
