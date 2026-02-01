# Blog Updater for Blogger.com

**Platform:** This tool is specifically for [Blogger.com](https://www.blogger.com/) (Google's blogging platform). It uses the Blogger API v3 and Google Drive API for images.

Use Cursor to generate blog articles (with images and your outline/comments), then publish to Blogger via a local API. **API-driven**: after you store a token once, all operations are via the API (no GUI).

## Prerequisites

- Python 3.10+
- A Google Cloud project with Blogger API and Drive API enabled
- OAuth 2.0 credentials (`blogger-cred.json`)

See **[docs/security-and-config.md](docs/security-and-config.md)** for Google Cloud project setup.

## Quick Start

```bash
# 1. Clone and setup
git clone <repo>
cd blog_updater
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# 2. Add your Google OAuth credentials
# Place blogger-cred.json in this directory (see docs/security-and-config.md)

# 3. One-time auth (creates token.json)
python scripts/auth_blogger.py

# 4. Start the API
uvicorn api:app --port 8080

# 5. Post a draft
python scripts/post_from_draft.py drafts/your-draft.md
```

See **[RUNBOOK.md](RUNBOOK.md)** for the full command flow.

## Cursor + Blogger Workflow

1. **Create a draft** from `drafts/template.md`: set title, **Topic and outline** (instructions for Cursor), and **Images** (local paths).
2. **Ask Cursor** to write the article and put HTML in the **Content** section.
3. **Start the API** and post:
   ```bash
   uvicorn api:app --port 8080
   python scripts/post_from_draft.py drafts/your-draft.md
   ```
4. **Iterate**: add `post_id` to frontmatter, edit in Cursor, push updates:
   ```bash
   python scripts/post_from_draft.py drafts/your-draft.md --update
   ```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/blogs` | GET | List your blogs (get blog_id) |
| `/posts` | POST | Create a new post |
| `/posts/{blog_id}/{post_id}` | GET | Get a post (for pull) |
| `/posts/{blog_id}/{post_id}` | PUT | Update a post |
| `/posts/{blog_id}/{post_id}/publish` | POST | Publish a draft |
| `/upload-image` | POST | Upload image to Drive, get URL |
| `/health` | GET | Health check |

API docs: http://localhost:8080/docs (when running)

## Images

Local images in the draft's **Images** section are uploaded to Google Drive automatically. Enable Drive API and re-run auth once. See [docs/security-and-config.md](docs/security-and-config.md).

## Security

Credentials and tokens are **gitignored** and must never be committed:
- `blogger-cred.json` / `bloger-cred.json` — OAuth client credentials
- `token.json` — OAuth refresh token
- `config.env` — local config overrides

## Documentation

- **[RUNBOOK.md](RUNBOOK.md)** — Commands-only quick reference
- **[docs/security-and-config.md](docs/security-and-config.md)** — Google Cloud setup, OAuth, troubleshooting
- **[docs/cursor-blog-workflow.md](docs/cursor-blog-workflow.md)** — Detailed Cursor workflow
