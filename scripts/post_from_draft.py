#!/usr/bin/env python3
"""
Post a draft file to the Blogger API, or pull/update for iterating with Cursor.

Reads a markdown file with optional YAML frontmatter (title, blog_id, post_id, draft, labels)
and sections: Topic and outline for this post (or Instructions for Cursor), Images, Content.
Content can be HTML or Markdown (Markdown is converted to HTML).

Usage:
  # Create new post
  python scripts/post_from_draft.py drafts/my-post.md
  python scripts/post_from_draft.py drafts/my-post.md --publish

  # Update existing post (set post_id in frontmatter, or use --update)
  python scripts/post_from_draft.py drafts/my-post.md --update
  python scripts/post_from_draft.py drafts/my-post.md --update POST_ID

  # Pull post from Blogger into draft (edit in Cursor, then push with --update)
  python scripts/post_from_draft.py pull drafts/my-post.md

Requires the API to be running: uvicorn api:app --reload --port 8080

Local images: use a path relative to the draft file (e.g. cursor-worflow.png).
The script uploads them to Google Drive and uses the URL in the post (no separate hosting).
Re-run python blogger_auth.py once to add Drive scope; enable Drive API in Google Cloud.
"""
import argparse
import re
import sys
from pathlib import Path

import requests

# Add repo root for imports
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

# Optional: markdown to HTML (install with pip install markdown)
try:
    import markdown
    HAS_MARKDOWN = True
except ImportError:
    HAS_MARKDOWN = False

# Default API port (see config.env.example)
API_BASE = "http://localhost:8080"


def load_draft(path: Path) -> tuple[dict, str, list[dict], str]:
    """Parse draft file. Returns (frontmatter, instructions, images, content)."""
    text = path.read_text(encoding="utf-8")

    # Frontmatter
    front = {}
    if text.startswith("---"):
        end = text.index("---", 3)
        if end != -1:
            try:
                import yaml
                front = yaml.safe_load(text[3:end]) or {}
            except Exception:
                pass
            text = text[end + 3:].lstrip("\n")

    # Sections (## Instructions, ## Images, ## Content)
    instructions = ""
    images = []
    content = ""

    section = None
    current = []
    for line in text.split("\n"):
        m = re.match(r"^##\s+(.+)$", line)
        if m:
            if section in ("Instructions for Cursor", "Topic and outline for this post"):
                instructions = "\n".join(current).strip()
            elif section == "Images":
                images = _parse_images(current)
            elif section == "Content":
                content = "\n".join(current).strip()
            section = m.group(1).strip()
            current = []
            continue
        if section:
            current.append(line)

    if section in ("Instructions for Cursor", "Topic and outline for this post"):
        instructions = "\n".join(current).strip()
    elif section == "Images":
        images = _parse_images(current)
    elif section == "Content":
        content = "\n".join(current).strip()

    return front, instructions, images, content


def _parse_images(lines: list[str]) -> list[dict]:
    """Parse image lines. Accepts:
    - filename.png - Caption
    - https://example.com/image.png - Caption
    - ![alt](filename.png)
    Skips lines that don't look like image references.
    """
    out = []
    for line in lines:
        line = line.strip()
        if not line or line.startswith("#") or line.startswith("*") or line.startswith("**"):
            continue
        # Markdown image: ![alt](url)
        m = re.match(r"!\[([^\]]*)\]\(([^)]+)\)", line)
        if m:
            alt, url = m.groups()
            out.append({"url": url.strip(), "caption": alt.strip() or None})
            continue
        # List item: "- filename.png" or "- filename.png - Caption"
        if line.startswith("- "):
            rest = line[2:].strip()
            # Must look like a filename or URL (has extension or starts with http)
            if not (rest.endswith(('.png', '.jpg', '.jpeg', '.gif', '.webp')) or 
                    rest.startswith('http://') or rest.startswith('https://') or
                    ' - ' in rest and any(rest.split(' - ')[0].strip().endswith(ext) 
                                          for ext in ('.png', '.jpg', '.jpeg', '.gif', '.webp'))):
                continue
            if " - " in rest:
                url, caption = rest.split(" - ", 1)
                out.append({"url": url.strip(), "caption": caption.strip()})
            else:
                out.append({"url": rest, "caption": None})
    return out


def markdown_to_html(md: str) -> str:
    if not md:
        return ""
    if HAS_MARKDOWN:
        return markdown.markdown(md, extensions=["extra", "nl2br"])
    return md  # pass through as-is (assume HTML)


def _resolve_image_url(url_or_path: str, draft_dir: Path) -> str:
    """If url_or_path is a local file path, upload to Drive and return URL. Else return as-is."""
    s = url_or_path.strip()
    if s.startswith("http://") or s.startswith("https://"):
        return s
    local = (draft_dir / s).resolve()
    if local.exists():
        try:
            from drive_upload import upload_image_to_drive
            return upload_image_to_drive(local, allow_interactive=False)
        except Exception as e:
            print(f"Warning: could not upload {local}: {e}", file=sys.stderr)
            return s
    return s


def post_draft(
    path: Path,
    publish: bool = False,
    update_post_id: str | None = None,
    api_base: str = API_BASE,
) -> None:
    front, _instructions, images, content = load_draft(path)

    # Resolve local image paths: upload to Drive and use returned URL
    draft_dir = path.parent
    for img in images or []:
        img["url"] = _resolve_image_url(img["url"], draft_dir)

    title = front.get("title") or path.stem.replace("-", " ").title()
    blog_id = front.get("blog_id")
    post_id = update_post_id or front.get("post_id")
    draft = front.get("draft", True) if not publish else False
    labels = front.get("labels") or []

    if not blog_id or blog_id == "YOUR_BLOG_ID":
        print("Error: Set blog_id in frontmatter (or use GET /blogs to find it).", file=sys.stderr)
        sys.exit(1)

    # Content: convert Markdown to HTML if it looks like Markdown
    if content and not content.strip().startswith("<"):
        content = markdown_to_html(content)

    if post_id:
        # Update existing post
        body = {
            "title": title,
            "content": content or None,
            "labels": labels if isinstance(labels, list) else [],
            "images": images or None,
        }
        r = requests.put(f"{api_base}/posts/{blog_id}/{post_id}", json=body, timeout=30)
        r.raise_for_status()
        data = r.json()
        print(f"Updated post: {data.get('title')} (id={data.get('id')}, status={data.get('status')})")
        if data.get("url"):
            print(f"URL: {data['url']}")
    else:
        # Create new post (API expects blog_id as string; YAML may give int)
        body = {
            "blog_id": str(blog_id),
            "title": title,
            "content": content or "(No content yet)",
            "labels": labels if isinstance(labels, list) else [],
            "images": images,
            "draft": draft,
        }
        r = requests.post(f"{api_base}/posts", json=body, timeout=30)
        r.raise_for_status()
        data = r.json()
        print(f"Created post: {data.get('title')} (id={data.get('id')}, status={data.get('status')})")
        if data.get("url"):
            print(f"URL: {data['url']}")
        print("Tip: Add 'post_id:", data.get("id"), "' to frontmatter to update this post next time.")


def pull_draft(path: Path, api_base: str = API_BASE) -> None:
    """Fetch post from Blogger and write content into the draft file (Content section)."""
    front, instructions, images, _content = load_draft(path)
    blog_id = front.get("blog_id")
    post_id = front.get("post_id")

    if not blog_id or blog_id == "YOUR_BLOG_ID":
        print("Error: Set blog_id in frontmatter.", file=sys.stderr)
        sys.exit(1)
    if not post_id:
        print("Error: Set post_id in frontmatter (or create the post first, then add its id).", file=sys.stderr)
        sys.exit(1)

    r = requests.get(f"{api_base}/posts/{blog_id}/{post_id}", params={"fetch_body": True}, timeout=30)
    r.raise_for_status()
    post = r.json()
    content = post.get("content", "")
    title = post.get("title", "")

    # Rebuild file: frontmatter + Instructions + Images + Content
    try:
        import yaml
        fm = {**front, "title": title, "post_id": post_id}
        front_block = "---\n" + yaml.dump(fm, default_flow_style=False, allow_unicode=True).strip() + "\n---\n\n"
    except Exception:
        raw = path.read_text(encoding="utf-8")
        parts = raw.split("---", 2)
        front_block = "---" + parts[1] + "---\n\n" if len(parts) >= 2 else ""

    def section(name: str, body: str) -> str:
        return f"## {name}\n\n{body.strip()}\n\n" if body.strip() else f"## {name}\n\n(empty)\n\n"

    images_text = "\n".join(
        f"- {img['url']}" + (f" - {img['caption']}" if img.get("caption") else "")
        for img in images
    ).strip() if images else "(none)"

    new_content = (
        front_block
        + section("Topic and outline for this post", instructions)
        + section("Images", images_text)
        + section("Content", content)
    )
    path.write_text(new_content, encoding="utf-8")
    print(f"Pulled post into {path}: {title}")


def main():
    parser = argparse.ArgumentParser(description="Post a draft to Blogger API, or pull/update")
    parser.add_argument("cmd_or_path", nargs="?", help="'pull' or path to draft .md file")
    parser.add_argument("draft_path", nargs="?", type=Path, help="Path to draft (required when using 'pull')")
    parser.add_argument("--publish", action="store_true", help="Publish immediately (default: save as draft)")
    parser.add_argument("--update", nargs="?", const=True, metavar="POST_ID", help="Update existing post (use post_id from frontmatter or pass POST_ID)")
    parser.add_argument("--api", default=API_BASE, help=f"API base URL (default {API_BASE})")
    args = parser.parse_args()

    # pull drafts/x.md  => cmd_or_path=pull, draft_path=drafts/x.md
    # drafts/x.md        => cmd_or_path=drafts/x.md, draft_path=None
    if (args.cmd_or_path or "").strip().lower() == "pull":
        draft_path = args.draft_path
        if not draft_path or not Path(draft_path).exists():
            print("Error: 'pull' requires a draft file path.", file=sys.stderr)
            parser.print_help()
            sys.exit(1)
        pull_draft(Path(draft_path), api_base=args.api)
        return

    draft_path = Path(args.cmd_or_path) if args.cmd_or_path else args.draft_path
    if not draft_path:
        parser.print_help()
        sys.exit(1)
    draft_path = Path(draft_path)
    if not draft_path.exists():
        print(f"Error: File not found: {draft_path}", file=sys.stderr)
        sys.exit(1)

    update_id = None
    if args.update is not None:
        update_id = args.update if isinstance(args.update, str) else None

    post_draft(draft_path, publish=args.publish, update_post_id=update_id, api_base=args.api)


if __name__ == "__main__":
    main()
