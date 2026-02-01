"""
Upload a local image to Google Drive and return a public URL for use in Blogger posts.
Blogger API has no image upload; we use Drive API and embed the file's view URL.
"""
from pathlib import Path

from googleapiclient.http import MediaFileUpload

from blogger_auth import get_drive_service

# Direct link for embedding in <img src="...">
DRIVE_VIEW_URL = "https://drive.google.com/uc?export=view&id={file_id}"


def upload_image_to_drive(file_path: str | Path, allow_interactive: bool = True) -> str:
    """
    Upload a local image file to Google Drive, make it viewable by anyone with the link,
    and return a URL suitable for <img src="..."> in a Blogger post.

    Requires Drive API enabled in your Google Cloud project and token with Drive scope
    (re-run python blogger_auth.py once to add Drive scope).
    """
    path = Path(file_path).resolve()
    if not path.exists():
        raise FileNotFoundError(f"Image file not found: {path}")

    name = path.name
    mime = _mime_for(path.suffix)

    drive = get_drive_service(allow_interactive=allow_interactive)
    media = MediaFileUpload(str(path), mimetype=mime, resumable=False)
    body = {"name": name}
    file = drive.files().create(body=body, media_body=media, fields="id").execute()
    file_id = file["id"]

    drive.permissions().create(
        fileId=file_id,
        body={"type": "anyone", "role": "reader"},
    ).execute()

    return DRIVE_VIEW_URL.format(file_id=file_id)


def _mime_for(suffix: str) -> str:
    m = {
        ".png": "image/png",
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".gif": "image/gif",
        ".webp": "image/webp",
    }
    return m.get(suffix.lower(), "application/octet-stream")
