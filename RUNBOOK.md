# Runbook — Commands Only

Quick reference for the Blog Updater workflow. For setup details, see [docs/security-and-config.md](docs/security-and-config.md).

---

## One-Time Setup

### 1. Install dependencies

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 2. Add credentials

Place your Google OAuth client JSON as `blogger-cred.json` in this directory. See [docs/security-and-config.md](docs/security-and-config.md) for Google Cloud project setup.

### 3. One-time auth (creates token.json)

```bash
python scripts/auth_blogger.py
# Browser opens → sign in → approve → token.json saved
```

**Note:** OAuth uses port 8080. If the API is running, stop it first (Ctrl+C).

---

## Daily Workflow

### 1. Start the API

```bash
source .venv/bin/activate
uvicorn api:app --port 8080
```

### 2. Check your blog ID (first time)

```bash
curl http://localhost:8080/blogs
```

### 3. Create a draft file

```bash
cp drafts/template.md drafts/my-post.md
# Edit: set title, blog_id, Topic/outline, Images, Content
```

### 4. Post to Blogger

**Create new post:**
```bash
python scripts/post_from_draft.py drafts/my-post.md
```

**Update existing post** (add `post_id` to frontmatter first):
```bash
python scripts/post_from_draft.py drafts/my-post.md --update
```

**Publish a draft:**
```bash
curl -X POST http://localhost:8080/posts/{blog_id}/{post_id}/publish
```

### 5. Stop the API

```bash
# Ctrl+C in the terminal, or:
fuser -k 8080/tcp
```

---

## Draft File Format

```markdown
---
title: My Post Title
blog_id: 12345678
post_id: 9876543210  # Add after first post to enable --update
draft: true
labels: []
---

## Topic and outline for this post

(Instructions for Cursor — not sent to Blogger)

## Images

- local-image.png - Caption for the image

## Content

<p>Your HTML content here...</p>
```

- **Topic and outline** — Instructions for Cursor to generate content
- **Images** — Local paths; script uploads to Drive and uses the URL
- **Content** — HTML sent to Blogger

---

## Image Upload

Images in the **Images** section with local paths are uploaded to Google Drive automatically.

**Manual upload:**
```bash
curl -X POST http://localhost:8080/upload-image \
  -F "file=@path/to/image.png"
# Returns: {"url": "https://drive.google.com/..."}
```

**Requires:** Drive API enabled and token with Drive scope (re-run auth once after enabling).

---

## Troubleshooting

### Port 8080 in use

```bash
fuser -k 8080/tcp
# Then start the API again
```

### redirect_uri_mismatch

1. Stop the API
2. Run `python scripts/auth_blogger.py`
3. Ensure Google Cloud Console has redirect URI: `http://localhost:8080/`

### access_denied (app in testing)

Add your email as a test user in Google Cloud Console → OAuth consent screen → Test users.

### Token expired

```bash
# Re-run auth to refresh
python scripts/auth_blogger.py
```

### Image not showing in post

Google Drive URLs may be blocked by browsers. For reliable images:
- Upload directly through Blogger's editor, or
- Use a different image host (Imgur, etc.)

---

## API Quick Reference

```bash
# Health check
curl http://localhost:8080/health

# List blogs
curl http://localhost:8080/blogs

# List posts
curl "http://localhost:8080/posts/{blog_id}?fetch_drafts=true"

# Get one post
curl http://localhost:8080/posts/{blog_id}/{post_id}

# Create post (JSON body)
curl -X POST http://localhost:8080/posts \
  -H "Content-Type: application/json" \
  -d '{"blog_id":"123","title":"Test","content":"<p>Hi</p>","draft":true}'

# Update post
curl -X PUT http://localhost:8080/posts/{blog_id}/{post_id} \
  -H "Content-Type: application/json" \
  -d '{"content":"<p>Updated</p>"}'

# Publish draft
curl -X POST http://localhost:8080/posts/{blog_id}/{post_id}/publish
```
