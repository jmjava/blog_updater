# Security and Configuration

This tool is for **Blogger.com** (Google's blogging platform) and uses Google APIs.

---

## Google Cloud Project Setup

### 1. Create a project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click **Select a project** → **New Project**
3. Name it (e.g., "Blogger Automation") → **Create**

### 2. Enable APIs

1. Go to **APIs & Services** → **Library**
2. Search and enable:
   - **Blogger API v3** — for creating/updating posts
   - **Google Drive API** — for image uploads (optional but recommended)

### 3. Configure OAuth consent screen

1. Go to **APIs & Services** → **OAuth consent screen**
2. Choose **External** (unless you have Google Workspace)
3. Fill in:
   - **App name**: e.g., "Blogger Automation"
   - **User support email**: your email
   - **Developer contact**: your email
4. Click **Save and Continue**
5. **Scopes**: Click **Add or Remove Scopes**, add:
   - `https://www.googleapis.com/auth/blogger`
   - `https://www.googleapis.com/auth/drive.file` (for images)
6. Click **Save and Continue**
7. **Test users**: Add your Google account email
8. Click **Save and Continue** → **Back to Dashboard**

### 4. Create OAuth credentials

1. Go to **APIs & Services** → **Credentials**
2. Click **+ Create Credentials** → **OAuth client ID**
3. Application type: **Desktop app** (or **Web application**)
4. Name: e.g., "Blog Updater"
5. If Web application, add **Authorized redirect URI**: `http://localhost:8080/`
6. Click **Create**
7. Click **Download JSON**
8. Save as `blogger-cred.json` in the blog_updater directory

---

## Credentials and Tokens

### blogger-cred.json

Your Google OAuth 2.0 client credentials (client ID and client secret). 

- **Location**: Project root (`blogger-cred.json` or `bloger-cred.json`)
- **Source**: Google Cloud Console → Credentials → Download JSON
- **Gitignored**: Yes — never commit this file

### token.json

OAuth refresh token created after one-time browser authentication.

- **Location**: Project root
- **Created by**: `python scripts/auth_blogger.py` or `python blogger_auth.py`
- **Gitignored**: Yes — never commit this file
- **Contains**: Access token, refresh token, scopes

### config.env

Optional local configuration overrides.

- **Location**: Project root (copy from `config.env.example`)
- **Gitignored**: Yes
- **Contents**: API port and other settings

---

## One-Time Authentication

The OAuth flow uses **http://localhost:8080/** as the redirect URI.

```bash
# Stop the API if running (it uses port 8080)
fuser -k 8080/tcp

# Run auth
python scripts/auth_blogger.py
# Browser opens → sign in → approve → token.json saved

# Start the API
uvicorn api:app --port 8080
```

### API-driven token storage (no browser)

If you have `token.json` from another machine:

```bash
# Via API (localhost only)
curl -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d @token.json

# Via script
python scripts/store_token.py < token.json
```

---

## Troubleshooting

### Error 400: redirect_uri_mismatch

The redirect URI in your app doesn't match Google Cloud Console.

1. **Stop the API** (Ctrl+C or `fuser -k 8080/tcp`)
2. In [Google Cloud Console → Credentials](https://console.cloud.google.com/apis/credentials), ensure your OAuth client has:
   - **Authorized redirect URI**: `http://localhost:8080/`
3. Run `python scripts/auth_blogger.py`
4. Start the API again

### Error 403: access_denied (app in testing)

Your OAuth app is in Testing mode and your account isn't a test user.

1. Go to [Google Cloud Console → OAuth consent screen](https://console.cloud.google.com/apis/credentials/consent)
2. In **Test users**, click **+ ADD USERS**
3. Add your Google account email
4. Click **SAVE**
5. Run auth again

You can leave the app in Testing for personal use. Publishing to production requires Google verification.

### Error: invalid_client

The credentials file is invalid or doesn't match the project.

1. Re-download `blogger-cred.json` from Google Cloud Console
2. Ensure it's in the project root
3. Run auth again

### Token expired or revoked

```bash
# Re-run auth to get a fresh token
python scripts/auth_blogger.py
```

---

## Image Upload (Google Drive)

The Blogger API doesn't support image upload. We use **Google Drive API**:
1. Images are uploaded to Drive
2. Made publicly viewable
3. The view URL is embedded in the post

### Setup

1. **Enable Drive API** in Google Cloud Console
2. **Re-run auth** so the token includes Drive scope:
   ```bash
   fuser -k 8080/tcp  # Stop API
   python scripts/auth_blogger.py  # Re-auth with Drive scope
   uvicorn api:app --port 8080  # Start API
   ```

### Usage

In drafts, put local image paths in the **Images** section:
```markdown
## Images

- my-image.png - Caption for the image
```

The `post_from_draft.py` script uploads to Drive automatically.

Or upload manually:
```bash
curl -X POST http://localhost:8080/upload-image -F "file=@image.png"
# Returns: {"url": "https://drive.google.com/..."}
```

### Image display issues

Google Drive URLs may be blocked by some browsers/contexts. For guaranteed display:
- Upload images directly through Blogger's post editor
- Use an alternative host (Imgur, your own server, etc.)

---

## Files (gitignored)

These files contain secrets and are **never committed**:

| File | Purpose |
|------|---------|
| `blogger-cred.json` | OAuth client credentials |
| `bloger-cred.json` | (alternate name) |
| `token.json` | OAuth refresh token |
| `config.env` | Local config overrides |
| `*.token.json` | Any token files |
| `*-cred.json` | Any credential files |
