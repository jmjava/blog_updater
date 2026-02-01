#!/usr/bin/env python3
"""Store token JSON to token.json so the API can use it. No browser needed.

Read token from stdin or from a file. Token JSON must include refresh_token and
either token or access_token; client_id, client_secret, token_uri are filled from
the security config (blogger-cred.json) if missing.

Usage:
  python scripts/store_token.py < token.json
  python scripts/store_token.py /path/to/token.json

Or store via API: POST http://localhost:8080/auth/token with JSON body.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from blogger_auth import store_token_from_dict, TOKEN_PATH

def main():
    if len(sys.argv) > 1:
        path = Path(sys.argv[1])
        if not path.exists():
            print(f"Error: file not found: {path}", file=sys.stderr)
            sys.exit(1)
        raw = path.read_text()
    else:
        raw = sys.stdin.read()

    try:
        token_data = json.loads(raw)
    except json.JSONDecodeError as e:
        print(f"Error: invalid JSON: {e}", file=sys.stderr)
        sys.exit(1)

    try:
        store_token_from_dict(token_data)
    except ValueError as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
    print(f"Stored token to {TOKEN_PATH}")
    print("You can use GET /blogs and other API endpoints now.")

if __name__ == "__main__":
    main()
