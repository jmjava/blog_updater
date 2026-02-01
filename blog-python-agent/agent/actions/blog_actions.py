"""
Blog Actions for the Python Agent.

Exposes Blogger API operations as remote actions:
- blog_list_blogs: List available blogs
- blog_create_post: Create a new post
- blog_update_post: Update existing post
- blog_publish_post: Publish a draft
- blog_get_post: Get post content
- blog_upload_image: Upload image to Google Drive
"""

import logging
import sys
from pathlib import Path
from typing import Dict, Any, Optional

# Add parent blog_updater to path for imports
BLOG_UPDATER_ROOT = Path(__file__).parent.parent.parent.parent
sys.path.insert(0, str(BLOG_UPDATER_ROOT))

from ..registry import get_registry
from ..models import Io, DynamicType, PropertyDef

logger = logging.getLogger(__name__)

# Get the global registry
registry = get_registry()


# =============================================================================
# Type Definitions
# =============================================================================

CreatePostRequest = DynamicType(
    name="CreatePostRequest",
    description="Request to create a new blog post",
    own_properties=[
        PropertyDef("blog_id", "string", "Blog ID to post to"),
        PropertyDef("title", "string", "Post title"),
        PropertyDef("content", "string", "HTML content"),
        PropertyDef("labels", "array", "Labels/tags for the post"),
        PropertyDef("images", "array", "Images to attach (url + caption)"),
        PropertyDef("draft", "boolean", "Whether to save as draft"),
    ],
)

UpdatePostRequest = DynamicType(
    name="UpdatePostRequest",
    description="Request to update an existing post",
    own_properties=[
        PropertyDef("blog_id", "string", "Blog ID"),
        PropertyDef("post_id", "string", "Post ID to update"),
        PropertyDef("title", "string", "New title (optional)"),
        PropertyDef("content", "string", "New content (optional)"),
        PropertyDef("labels", "array", "New labels (optional)"),
        PropertyDef("images", "array", "Images to attach"),
    ],
)

PostResponse = DynamicType(
    name="PostResponse",
    description="Response from post creation/update",
    own_properties=[
        PropertyDef("id", "string", "Post ID"),
        PropertyDef("url", "string", "Post URL"),
        PropertyDef("status", "string", "Post status (draft/live)"),
        PropertyDef("title", "string", "Post title"),
    ],
)

BlogInfo = DynamicType(
    name="BlogInfo",
    description="Blog information",
    own_properties=[
        PropertyDef("id", "string", "Blog ID"),
        PropertyDef("name", "string", "Blog name"),
        PropertyDef("url", "string", "Blog URL"),
    ],
)

ImageUploadResult = DynamicType(
    name="ImageUploadResult",
    description="Result from image upload",
    own_properties=[
        PropertyDef("url", "string", "Public URL of uploaded image"),
    ],
)

# Register all types
for dtype in [CreatePostRequest, UpdatePostRequest, PostResponse, BlogInfo, ImageUploadResult]:
    registry.register_type(dtype)


# =============================================================================
# Helper Functions
# =============================================================================

def _get_blogger_service():
    """Get the Blogger service from the existing blog_updater code."""
    try:
        from blogger_auth import get_blogger_service
        return get_blogger_service(allow_interactive=False)
    except Exception as e:
        logger.error(f"Failed to get Blogger service: {e}")
        raise


def _build_content_with_images(body_html: str, images: list) -> str:
    """Append image figures to post content."""
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


# =============================================================================
# Action Handlers
# =============================================================================

@registry.action(
    name="blog_list_blogs",
    description="List all blogs for the authenticated user. Returns blog IDs and names.",
    inputs=[],
    outputs=[Io("blogs", "array")],
    pre=[],
    post=[],
    cost=0.1,
    value=0.3,
    can_rerun=True,
)
async def blog_list_blogs(params: Dict[str, Any]) -> Dict[str, Any]:
    """List available blogs."""
    logger.info("Listing blogs")
    
    try:
        service = _get_blogger_service()
        blogs = service.blogs().listByUser(userId="self").execute()
        items = blogs.get("items") or []
        
        return {
            "blogs": [
                {"id": b["id"], "name": b.get("name"), "url": b.get("url")}
                for b in items
            ]
        }
    except Exception as e:
        logger.exception(f"Failed to list blogs: {e}")
        raise


@registry.action(
    name="blog_create_post",
    description="Create a new blog post. Can be saved as draft or published immediately.",
    inputs=[Io("request", "CreatePostRequest")],
    outputs=[Io("result", "PostResponse")],
    pre=["draft_approved"],
    post=["post_created"],
    cost=0.3,
    value=0.8,
    can_rerun=False,
)
async def blog_create_post(params: Dict[str, Any]) -> Dict[str, Any]:
    """Create a new blog post."""
    request = params.get("request", params)
    
    blog_id = request.get("blog_id")
    title = request.get("title")
    content = request.get("content", "")
    labels = request.get("labels", [])
    images = request.get("images", [])
    draft = request.get("draft", True)
    
    if not blog_id:
        raise ValueError("blog_id is required")
    if not title:
        raise ValueError("title is required")
    
    logger.info(f"Creating post '{title}' on blog {blog_id}")
    
    # Build content with images
    if images:
        content = _build_content_with_images(content, images)
    
    try:
        service = _get_blogger_service()
        post_body = {
            "title": title,
            "content": content,
        }
        if labels:
            post_body["labels"] = labels
        
        result = (
            service.posts()
            .insert(
                blogId=blog_id,
                body=post_body,
                isDraft=draft,
            )
            .execute()
        )
        
        return {
            "id": result["id"],
            "url": result.get("url"),
            "status": "draft" if draft else "live",
            "title": result.get("title", title),
        }
    except Exception as e:
        logger.exception(f"Failed to create post: {e}")
        raise


@registry.action(
    name="blog_update_post",
    description="Update an existing blog post with new content, title, or labels.",
    inputs=[Io("request", "UpdatePostRequest")],
    outputs=[Io("result", "PostResponse")],
    pre=["post_created"],
    post=["post_updated"],
    cost=0.2,
    value=0.6,
    can_rerun=True,
)
async def blog_update_post(params: Dict[str, Any]) -> Dict[str, Any]:
    """Update an existing post."""
    request = params.get("request", params)
    
    blog_id = request.get("blog_id")
    post_id = request.get("post_id")
    title = request.get("title")
    content = request.get("content")
    labels = request.get("labels")
    images = request.get("images")
    
    if not blog_id:
        raise ValueError("blog_id is required")
    if not post_id:
        raise ValueError("post_id is required")
    
    logger.info(f"Updating post {post_id} on blog {blog_id}")
    
    try:
        service = _get_blogger_service()
        
        # Get existing post
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
        
        # Build update body
        update_title = title if title is not None else existing.get("title")
        update_content = content if content is not None else existing.get("content", "")
        
        if images:
            update_content = _build_content_with_images(update_content, images)
        
        update_labels = labels if labels is not None else existing.get("labels") or []
        
        post_body = {
            "title": update_title,
            "content": update_content,
            "labels": update_labels,
        }
        
        result = (
            service.posts()
            .update(
                blogId=blog_id,
                postId=post_id,
                body=post_body,
            )
            .execute()
        )
        
        return {
            "id": result["id"],
            "url": result.get("url"),
            "status": result.get("status", "draft"),
            "title": result.get("title", update_title),
        }
    except Exception as e:
        logger.exception(f"Failed to update post: {e}")
        raise


@registry.action(
    name="blog_publish_post",
    description="Publish a draft post to make it live.",
    inputs=[
        Io("blog_id", "string"),
        Io("post_id", "string"),
    ],
    outputs=[Io("result", "PostResponse")],
    pre=["post_created"],
    post=["post_published"],
    cost=0.1,
    value=0.9,
    can_rerun=False,
)
async def blog_publish_post(params: Dict[str, Any]) -> Dict[str, Any]:
    """Publish a draft post."""
    blog_id = params.get("blog_id")
    post_id = params.get("post_id")
    
    if not blog_id:
        raise ValueError("blog_id is required")
    if not post_id:
        raise ValueError("post_id is required")
    
    logger.info(f"Publishing post {post_id} on blog {blog_id}")
    
    try:
        service = _get_blogger_service()
        result = (
            service.posts()
            .publish(blogId=blog_id, postId=post_id)
            .execute()
        )
        
        return {
            "id": result["id"],
            "url": result.get("url"),
            "status": "live",
            "title": result.get("title"),
            "published": result.get("published"),
        }
    except Exception as e:
        logger.exception(f"Failed to publish post: {e}")
        raise


@registry.action(
    name="blog_get_post",
    description="Get a post's content for editing or review.",
    inputs=[
        Io("blog_id", "string"),
        Io("post_id", "string"),
    ],
    outputs=[Io("post", "object")],
    pre=[],
    post=[],
    cost=0.1,
    value=0.4,
    can_rerun=True,
)
async def blog_get_post(params: Dict[str, Any]) -> Dict[str, Any]:
    """Get a post by ID."""
    blog_id = params.get("blog_id")
    post_id = params.get("post_id")
    
    if not blog_id:
        raise ValueError("blog_id is required")
    if not post_id:
        raise ValueError("post_id is required")
    
    logger.info(f"Getting post {post_id} from blog {blog_id}")
    
    try:
        service = _get_blogger_service()
        result = (
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
    except Exception as e:
        logger.exception(f"Failed to get post: {e}")
        raise


@registry.action(
    name="blog_upload_image",
    description="Upload an image to Google Drive and return a public URL.",
    inputs=[Io("file_path", "string")],
    outputs=[Io("result", "ImageUploadResult")],
    pre=[],
    post=["image_uploaded"],
    cost=0.3,
    value=0.5,
    can_rerun=True,
)
async def blog_upload_image(params: Dict[str, Any]) -> Dict[str, Any]:
    """Upload an image to Google Drive."""
    file_path = params.get("file_path")
    
    if not file_path:
        raise ValueError("file_path is required")
    
    path = Path(file_path)
    if not path.exists():
        raise ValueError(f"File not found: {file_path}")
    
    logger.info(f"Uploading image: {file_path}")
    
    try:
        from drive_upload import upload_image_to_drive
        url = upload_image_to_drive(str(path), allow_interactive=False)
        
        return {
            "url": url,
        }
    except ImportError:
        logger.error("drive_upload module not available")
        raise ValueError("Drive upload not available")
    except Exception as e:
        logger.exception(f"Failed to upload image: {e}")
        raise


@registry.action(
    name="blog_list_posts",
    description="List recent posts for a blog.",
    inputs=[
        Io("blog_id", "string"),
        Io("max_results", "number"),
        Io("fetch_drafts", "boolean"),
    ],
    outputs=[Io("posts", "array")],
    pre=[],
    post=[],
    cost=0.1,
    value=0.3,
    can_rerun=True,
)
async def blog_list_posts(params: Dict[str, Any]) -> Dict[str, Any]:
    """List posts for a blog."""
    blog_id = params.get("blog_id")
    max_results = params.get("max_results", 10)
    fetch_drafts = params.get("fetch_drafts", False)
    
    if not blog_id:
        raise ValueError("blog_id is required")
    
    logger.info(f"Listing posts for blog {blog_id}")
    
    try:
        service = _get_blogger_service()
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
    except Exception as e:
        logger.exception(f"Failed to list posts: {e}")
        raise
