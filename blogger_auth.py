"""
Blogger OAuth2 helpers: load credentials and build authorized Blogger service.
Supports credentials file: bloger-cred.json or blogger-cred.json.
Token is stored in token.json (gitignored).
"""
import warnings
from pathlib import Path

# Suppress Google's Python 3.10 EOL warning (upgrade to 3.11+ when convenient)
warnings.filterwarnings("ignore", message=".*Python version.*3\\.10.*", category=FutureWarning)

from google.oauth2.credentials import Credentials
from google.auth.transport.requests import Request
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build

# Try both possible credential filenames (typo and correct)
CRED_DIR = Path(__file__).resolve().parent
CRED_FILES = ["blogger-cred.json", "bloger-cred.json"]
TOKEN_PATH = CRED_DIR / "token.json"

BLOGGER_SCOPE = ["https://www.googleapis.com/auth/blogger"]
# For image upload (upload to Drive, use URL in post). Re-run auth once to add this scope.
DRIVE_SCOPE = ["https://www.googleapis.com/auth/drive.file"]


def _cred_path() -> Path:
    for name in CRED_FILES:
        p = CRED_DIR / name
        if p.exists():
            return p
    raise FileNotFoundError(
        f"Credentials file not found. Add one of: {', '.join(CRED_FILES)}"
    )


class TokenNotFoundError(Exception):
    """Raised when token.json is missing. Run the auth script to create it."""

    AUTH_CMD = 'python -c "from blogger_auth import run_oauth_flow; run_oauth_flow()"'

    def __init__(self):
        super().__init__(
            f"No token.json. Run one-time auth (API can stay running): {self.AUTH_CMD}"
        )


def run_oauth_flow(open_browser: bool = True) -> None:
    """Run OAuth in browser and save token.json. Call this from the command line only."""
    flow = InstalledAppFlow.from_client_secrets_file(
        str(_cred_path()), scopes=BLOGGER_SCOPE + DRIVE_SCOPE
    )
    # Use 8080 to match your existing redirect URI in Google Cloud (no 8081 needed).
    creds = flow.run_local_server(port=8080, open_browser=open_browser)
    _save_token(creds)


def get_credentials(allow_interactive: bool = True, scopes: list | None = None) -> Credentials:
    """Load credentials from token.json, or run OAuth flow if allow_interactive and no token."""
    scopes = scopes or BLOGGER_SCOPE
    if TOKEN_PATH.exists():
        creds = Credentials.from_authorized_user_file(str(TOKEN_PATH), scopes)
        if creds and creds.valid:
            return creds
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
            _save_token(creds)
            return creds

    if not allow_interactive:
        raise TokenNotFoundError()

    run_oauth_flow()
    return Credentials.from_authorized_user_file(str(TOKEN_PATH), scopes)


def _save_token(creds: Credentials) -> None:
    with open(TOKEN_PATH, "w") as f:
        f.write(creds.to_json())


def store_token_from_dict(token_data: dict) -> None:
    """Normalize token dict (e.g. from API or file) and write token.json. No browser."""
    import json
    token_data = dict(token_data)
    # Normalize: Google may return access_token
    if "access_token" in token_data and "token" not in token_data:
        token_data["token"] = token_data["access_token"]
    # Fill client_* from security config if missing
    try:
        with open(_cred_path()) as f:
            cred = json.load(f)
        client = cred.get("web") or cred.get("installed") or cred
        for k, default in (
            ("client_id", client.get("client_id")),
            ("client_secret", client.get("client_secret")),
            ("token_uri", client.get("token_uri", "https://oauth2.googleapis.com/token")),
        ):
            if default and not token_data.get(k):
                token_data[k] = default
    except Exception:
        pass
    if "scopes" not in token_data:
        token_data["scopes"] = BLOGGER_SCOPE
    if not token_data.get("refresh_token"):
        raise ValueError("token_data must include refresh_token")
    TOKEN_PATH.write_text(json.dumps(token_data, indent=2))


def get_blogger_service(allow_interactive: bool = True):
    """Return an authorized Blogger API v3 service. Set allow_interactive=False when called from API."""
    creds = get_credentials(allow_interactive=allow_interactive)
    return build("blogger", "v3", credentials=creds)


def get_drive_service(allow_interactive: bool = True):
    """Return an authorized Drive API v3 service (for image upload). Re-auth once to add Drive scope."""
    creds = get_credentials(allow_interactive=allow_interactive, scopes=BLOGGER_SCOPE + DRIVE_SCOPE)
    return build("drive", "v3", credentials=creds)


if __name__ == "__main__":
    run_oauth_flow()
    print("token.json saved. Use GET /blogs or run the API.")
