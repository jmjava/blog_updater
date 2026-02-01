#!/usr/bin/env python3
"""Run one-time OAuth flow and save token.json. API can stay running on 8080."""
import sys
from pathlib import Path

# Run from repo root so blogger_auth finds creds and writes token.json here
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from blogger_auth import run_oauth_flow

if __name__ == "__main__":
    print("OAuth uses http://localhost:8080/ (your existing redirect URI).")
    print("Stop the API first (Ctrl+C), then run this; then start the API again.")
    print()
    # Use open_browser=False to avoid stale redirects; open the printed URL in your browser.
    run_oauth_flow(open_browser=("--no-browser" not in sys.argv))
    print("token.json saved. You can use GET /blogs now.")
