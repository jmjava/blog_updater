"""
Blogger API: create and publish posts from Cursor-generated content.

Endpoints:
  POST /auth/token     - Store token JSON (localhost only); then all ops are API-driven
  GET  /blogs          - List your blogs (use blogId from here)
  POST /posts          - Create a post (draft or publish), with optional images
  GET  /posts/{blogId} - List posts for a blog
  GET  /posts/{blogId}/{postId} - Get one post (for pull into draft)
  PUT  /posts/{blogId}/{postId} - Update post (iterate from Cursor)
  POST /posts/{blogId}/{postId}/publish - Publish a draft

Run: uvicorn api:app --reload --port 8080
Docs: http://localhost:8080/docs
(Config: API_PORT=8080 in config.env.example)
"""
import logging
import sys
from typing import Optional

from fastapi import FastAPI, File, HTTPException, Request, UploadFile
from pydantic import BaseModel, Field

from blogger_auth import TokenNotFoundError, get_blogger_service, store_token_from_dict

log = logging.getLogger(__name__)

app = FastAPI(
    title="Blogger API",
    description="Create and upload blog posts from Cursor. Use /blogs to get your blogId.",
    version="1.0.0",
)


# --- Helpers ---


def _build_content_with_images(body_html: str | None, images: list[dict]) -> str:
    """Append image figures to post content. Each image: {url, caption?}."""
    if not images:
        return (body_html or "").strip() or ""
    parts = [(body_html or "").strip() or ""]
    for img in images:
        url = (img.get("url") or "").strip()
        if not url:
            continue
        caption = (img.get("caption") or "").strip()
        if caption:
            parts.append(
                f'<figure><img src="{url}" alt="{_escape(caption)}"/>'
                f'<figcaption>{_escape(caption)}</figcaption></figure>'
            )
        else:
            parts.append(f'<figure><img src="{url}" alt=""/></figure>')
    return "\n\n".join(parts)


def _escape(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


# --- Request/Response models ---


class ImageRef(BaseModel):
    """Image to include in the post (URL + optional caption)."""

    url: str = Field(..., description="Image URL (must be publicly accessible)")
    caption: Optional[str] = Field(default=None, description="Optional caption")


class CreatePostRequest(BaseModel):
    """Body for creating a new post."""

    blog_id: str = Field(..., description="Blog ID (from GET /blogs)")
    title: str = Field(..., description="Post title")
    content: str = Field(
        ...,
        description="HTML or plain text body (from Cursor-generated article)",
    )
    labels: Optional[list[str]] = Field(default=None, description="Labels/tags")
    images: Optional[list[ImageRef]] = Field(
        default=None,
        description="Images to append to the post (url + optional caption)",
    )
    draft: bool = Field(
        default=True,
        description="If True, create as draft; if False, publish immediately",
    )


class PostResponse(BaseModel):
    """Post created or published."""

    id: str
    url: Optional[str] = None
    status: str = "draft"  # draft or live
    title: str


class UpdatePostRequest(BaseModel):
    """Body for updating an existing post (iterate from Cursor)."""

    title: Optional[str] = Field(default=None, description="New title")
    content: Optional[str] = Field(
        default=None,
        description="New HTML body (from Cursor edits)",
    )
    labels: Optional[list[str]] = Field(default=None, description="Labels/tags")
    images: Optional[list[ImageRef]] = Field(
        default=None,
        description="Images to append (url + optional caption)",
    )


# --- Endpoints ---


@app.get("/blogs")
def list_blogs():
    """List blogs for the authenticated user. Use 'id' as blog_id in POST /posts."""
    try:
        service = get_blogger_service(allow_interactive=False)
        blogs = service.blogs().listByUser(userId="self").execute()
        items = blogs.get("items") or []
        return {
            "blogs": [
                {"id": b["id"], "name": b.get("name"), "url": b.get("url")}
                for b in items
            ]
        }
    except TokenNotFoundError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except FileNotFoundError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        log.exception("GET /blogs failed")
        raise HTTPException(status_code=500, detail=f"{type(e).__name__}: {e}")


@app.post("/posts", response_model=PostResponse)
def create_post(body: CreatePostRequest):
    """
    Create a new post. Use Cursor to generate title and content, then POST here.

    - Include `images` (url + optional caption) to append figures to the post.
    - Set draft=True to save as draft and publish later via POST /posts/{blogId}/{postId}/publish
    - Set draft=False to publish immediately.
    """
    try:
        content = body.content
        if body.images:
            content = _build_content_with_images(
                content, [{"url": i.url, "caption": i.caption} for i in body.images]
            )

        service = get_blogger_service(allow_interactive=False)
        post_body = {
            "title": body.title,
            "content": content,
        }
        if body.labels:
            post_body["labels"] = body.labels

        result = (
            service.posts()
            .insert(
                blogId=body.blog_id,
                body=post_body,
                isDraft=body.draft,
            )
            .execute()
        )

        url = result.get("url")
        return PostResponse(
            id=result["id"],
            url=url,
            status="draft" if body.draft else "live",
            title=result.get("title", body.title),
        )
    except TokenNotFoundError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except FileNotFoundError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/posts/{blog_id}")
def list_posts(blog_id: str, max_results: int = 10, fetch_drafts: bool = False):
    """List recent posts for a blog. Optional: fetch_drafts=True to include drafts."""
    try:
        service = get_blogger_service(allow_interactive=False)
        kwargs = dict(
            blogId=blog_id,
            maxResults=max_results,
            fetchBodies=False,
            fetchImages=False,
        )
        if fetch_drafts:
            kwargs["status"] = "all"
        result = service.posts().list(**kwargs).execute()
        items = result.get("items") or []
        return {
            "posts": [
                {
                    "id": p["id"],
                    "title": p.get("title"),
                    "status": p.get("status", "unknown"),
                    "url": p.get("url"),
                    "published": p.get("published"),
                    "updated": p.get("updated"),
                }
                for p in items
            ]
        }
    except TokenNotFoundError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except FileNotFoundError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/posts/{blog_id}/{post_id}")
def get_post(blog_id: str, post_id: str, fetch_body: bool = True):
    """Get one post (for pulling content into a draft to edit in Cursor)."""
    try:
        service = get_blogger_service(allow_interactive=False)
        result = (
            service.posts()
            .get(
                blogId=blog_id,
                postId=post_id,
                fetchBody=fetch_body,
                fetchImages=False,
                view="ADMIN",
            )
            .execute()
        )
        return {
            "id": result["id"],
            "title": result.get("title"),
            "content": result.get("content", ""),
            "labels": result.get("labels") or [],
            "status": result.get("status", "unknown"),
            "url": result.get("url"),
            "published": result.get("published"),
            "updated": result.get("updated"),
        }
    except TokenNotFoundError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except FileNotFoundError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.put("/posts/{blog_id}/{post_id}", response_model=PostResponse)
def update_post(blog_id: str, post_id: str, body: UpdatePostRequest):
    """
    Update an existing post. Use this to push edits from your draft after iterating with Cursor.

    Only provided fields are updated. Include content to replace the post body.
    """
    try:
        service = get_blogger_service(allow_interactive=False)
        # Build updated post body from existing + request (view=ADMIN to access drafts)
        existing = (
            service.posts()
            .get(
                blogId=blog_id,
                postId=post_id,
                fetchBody=True,
                fetchImages=False,
                view="ADMIN",
            )
            .execute()
        )
        title = body.title if body.title is not None else existing.get("title")
        content = existing.get("content", "")
        if body.content is not None:
            content = body.content
        if body.images:
            content = _build_content_with_images(
                content, [{"url": i.url, "caption": i.caption} for i in body.images]
            )
        labels = body.labels if body.labels is not None else existing.get("labels") or []

        post_body = {"title": title, "content": content, "labels": labels}
        result = (
            service.posts()
            .update(
                blogId=blog_id,
                postId=post_id,
                body=post_body,
            )
            .execute()
        )
        return PostResponse(
            id=result["id"],
            url=result.get("url"),
            status=result.get("status", "draft"),
            title=result.get("title", title),
        )
    except TokenNotFoundError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except FileNotFoundError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/posts/{blog_id}/{post_id}/publish")
def publish_post(blog_id: str, post_id: str):
    """Publish a draft post."""
    try:
        service = get_blogger_service(allow_interactive=False)
        result = (
            service.posts()
            .publish(blogId=blog_id, postId=post_id)
            .execute()
        )
        return {
            "id": result["id"],
            "url": result.get("url"),
            "status": "live",
            "published": result.get("published"),
        }
    except TokenNotFoundError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except FileNotFoundError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/upload-image")
async def upload_image(file: UploadFile = File(...)):
    """
    Upload an image file to Google Drive and return a URL for use in posts.
    No separate hosting needed — use this URL in the post's images array or HTML.
    """
    import tempfile
    from pathlib import Path
    try:
        from drive_upload import upload_image_to_drive
    except ImportError:
        raise HTTPException(status_code=503, detail="drive_upload not available")
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Upload must be an image (e.g. image/png)")
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=Path(file.filename or "image").suffix) as tmp:
            tmp.write(await file.read())
            tmp_path = tmp.name
        url = upload_image_to_drive(tmp_path, allow_interactive=False)
        Path(tmp_path).unlink(missing_ok=True)
        return {"url": url}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/auth/token")
def auth_store_token(request: Request, body: dict):
    """
    Store token JSON so the API can call Blogger. No browser needed.
    Only allowed from localhost. Body is the same as token.json (must include refresh_token).
    After this, GET /blogs and other endpoints work.
    """
    host = (request.client and request.client.host) or ""
    if host not in ("127.0.0.1", "::1", "localhost"):
        raise HTTPException(status_code=403, detail="POST /auth/token is only allowed from localhost")
    try:
        store_token_from_dict(body)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    return {"status": "ok", "message": "Token stored. Use GET /blogs etc."}


@app.get("/health")
def health():
    """Health check."""
    return {"status": "ok"}
