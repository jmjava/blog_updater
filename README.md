# Blog Updater for Blogger.com

**Platform:** This tool is specifically for [Blogger.com](https://www.blogger.com/) (Google's blogging platform). It uses the Blogger API v3 and Google Drive API for images.

Two modes of operation:
1. **Manual workflow**: Use Cursor to generate blog articles, then publish via the local FastAPI
2. **Embabel Agent workflow**: AI-orchestrated HITL (Human-in-the-Loop) content generation with RAG

## Project Structure

```
blog_updater/
├── api.py                    # FastAPI for Blogger operations
├── blogger_auth.py           # Google OAuth handling
├── drive_upload.py           # Google Drive image uploads
├── scripts/                  # CLI tools
├── drafts/                   # Markdown drafts
├── blog-agent/               # Kotlin/Spring Boot Embabel agent
└── blog-python-agent/        # Python agent (embabel-agent-remote)
```

## Prerequisites

- Python 3.10+
- A Google Cloud project with Blogger API and Drive API enabled
- OAuth 2.0 credentials (`blogger-cred.json`)

For Embabel Agent (optional):
- Java 21+
- Docker (for Neo4j)
- OpenAI API key

See **[docs/security-and-config.md](docs/security-and-config.md)** for Google Cloud project setup.

## Quick Start (Manual Workflow)

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

---

## Embabel Agent Workflow

The Embabel Blog Agent provides AI-orchestrated content generation with:
- **GOAP workflow**: Topic → Research → Draft → Review → Publish
- **RAG integration**: Ingest docs/repos into Neo4j for context-aware content
- **HITL checkpoints**: Human review before publishing
- **Python remote actions**: Blogger operations via embabel-agent-remote API

### Architecture

```
┌─────────────────────────┐     ┌─────────────────────────┐     ┌─────────────────┐
│   Kotlin Blog Agent     │     │   Blog Python Agent     │     │   Google APIs   │
│   (GOAP + Neo4j RAG)    │────▶│   /api/v1/actions/exec  │────▶│   Blogger/Drive │
│   :8081                 │     │   :8000                 │     │                 │
└─────────────────────────┘     └─────────────────────────┘     └─────────────────┘
```

### Quick Start (Embabel Agent)

```bash
# Terminal 1: Start Neo4j
cd blog-agent
docker-compose up -d

# Terminal 2: Start Python Agent (Blogger operations)
cd blog-python-agent
pip install -e .
python -m agent serve --port 8000

# Terminal 3: Start Kotlin Agent (workflow orchestration)
cd blog-agent
export OPENAI_API_KEY=your-key
./mvnw spring-boot:run
```

### REST API (Kotlin Agent - :8081)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/workflow/start` | POST | Start content workflow for a topic |
| `/api/workflow/{id}/status` | GET | Get workflow status |
| `/api/workflow/{id}/approve` | POST | Approve draft for publishing |
| `/api/workflow/{id}/feedback` | POST | Provide revision feedback |
| `/api/drafts` | GET | List all drafts |
| `/api/rag/ingest/url` | POST | Ingest URL into RAG |
| `/api/rag/ingest/directory` | POST | Ingest local directory |

### Python Agent Actions (embabel-agent-remote)

| Action | Description |
|--------|-------------|
| `blog_list_blogs` | List available blogs |
| `blog_create_post` | Create a new post |
| `blog_update_post` | Update existing post |
| `blog_publish_post` | Publish a draft |
| `blog_get_post` | Get post content |
| `blog_upload_image` | Upload image to Drive |
| `blog_list_posts` | List posts for a blog |

See [blog-agent/README.md](blog-agent/README.md) and [blog-python-agent/README.md](blog-python-agent/README.md) for details.

---

## Manual Cursor + Blogger Workflow

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

## API Endpoints (FastAPI - :8080)

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
- **[blog-agent/README.md](blog-agent/README.md)** — Kotlin Embabel agent details
- **[blog-python-agent/README.md](blog-python-agent/README.md)** — Python agent (remote actions)

## Releases

- **v0.1.0** — Original blog_updater (FastAPI + manual workflow)
- **main** — Includes Embabel agent integration
