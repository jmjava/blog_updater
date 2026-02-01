# Cursor + Blogger workflow

Use Cursor to generate blog articles from your outline and images, then publish via the Blogger API. You can **adjust content and iterate** with Cursor, then push updates to the same draft on Blogger.

## Overview

1. **Create a draft file** using the template in `drafts/` (title, instructions for Cursor, image URLs).
2. **Ask Cursor** to write the blog article from your instructions and include the images.
3. **Post to Blogger** (create or update) using the script or API.
4. **Iterate**: edit the draft in Cursor, ask Cursor to refine, then **push** again to the same post.

## Step 1: Create a draft

Copy `drafts/template.md` to a new file, e.g. `drafts/my-next-post.md`.

Fill in:

- **Title** – Post title.
- **Topic and outline for this post** – What *this* blog entry is about: your outline, tone, audience, and key points (e.g. “Write for developers”, “Include a short intro”, “Mention X and Y”).
- **Images** – List image URLs and optional captions. Cursor can reference these when writing; the API will also append them to the post.
- **Content** – Leave empty; Cursor will generate this from your instructions (and optionally from the images).

Example instructions you can give Cursor:

- “Write a 500-word blog post from the instructions above. Output the body as HTML (use `<p>`, `<h2>`, `<ul>`, etc.). Include the images in a logical place or describe where they should go.”
- “Turn the outline above into a full article. Use a friendly tone. Output HTML for the body.”

## Step 2: Have Cursor generate the article

In Cursor, open your draft file and say something like:

- **“Using the Topic and outline and Images sections in this file, write the full blog article. Put the result in the Content section as HTML (paragraphs, headings, lists). Keep the same frontmatter and Topic/Images; only fill in Content.”**

Or:

- **“Generate a blog post from the instructions in this draft. Output HTML for the body and replace the Content section with it.”**

Cursor will use your title, instructions, and image list to produce the article. You can iterate by editing the instructions and asking again.

## Step 3: Publish via the API

1. **Start the API** (if not already running):

   ```bash
   cd blog_updater
   source .venv/bin/activate
   uvicorn api:app --reload --port 8080
   ```

2. **Post the draft** using the script (recommended):

   ```bash
   python scripts/post_from_draft.py drafts/my-next-post.md
   ```

   Or call the API yourself (e.g. from the [Swagger UI](http://localhost:8080/docs)):

   - **GET /blogs** – Get your `blog_id`.
   - **POST /posts** – Body: `blog_id`, `title`, `content` (the HTML from the draft), optional `images` (list of `{ "url": "...", "caption": "..." }`), `draft: true` or `false`.

3. **Publish a saved draft** (if you created it as draft):

   ```bash
   curl -X POST "http://localhost:8080/posts/YOUR_BLOG_ID/POST_ID/publish"
   ```

   Or use **POST /posts/{blog_id}/{post_id}/publish** in the docs.

## Images

- **In the draft**: List image URLs (and optional captions) in the Images section. Use public URLs (e.g. from your own hosting, Imgur, or Blogger’s existing media).
- **In the API**: The `images` array is optional. Each item: `{ "url": "https://...", "caption": "Optional caption" }`. These are appended as `<figure>` blocks after the main content.
- **In Cursor**: Ask Cursor to reference the images in the narrative (e.g. “see the diagram below”) and output HTML. The API will add the actual `<figure>` blocks from the `images` list.

## Iterate: adjust content and push updates

After you create a post, you can keep editing the draft in Cursor and push changes to the **same** post on Blogger.

1. **Create the post once** (as above). The script prints the new post id.
2. **Add `post_id` to frontmatter** in your draft:
   ```yaml
   ---
   title: Your post title
   blog_id: YOUR_BLOG_ID
   post_id: 1234567890123456789   # paste the id from step 1
   draft: true
   ---
   ```
3. **Edit in Cursor**: change the Content section (or Instructions/Images), ask Cursor to refine wording, shorten, add a paragraph, etc.
4. **Push the update**:
   ```bash
   python scripts/post_from_draft.py drafts/your-draft.md --update
   ```
   This updates the existing post on Blogger with the new title/content/images.

5. **Pull from Blogger** (optional): if you edited the post in Blogger’s UI and want to edit in Cursor again:
   ```bash
   python scripts/post_from_draft.py pull drafts/your-draft.md
   ```
   This overwrites the **Content** section in your draft with the post body from Blogger (Instructions and Images are kept). Then edit in Cursor and push with `--update`.

**Summary**: Create once → add `post_id` to frontmatter → edit draft in Cursor → `post_from_draft.py drafts/x.md --update` → repeat. Use `pull` to sync Blogger → draft when needed.

## Quick reference

| Task              | How |
|-------------------|-----|
| List your blogs   | `GET http://localhost:8080/blogs` |
| Create post       | `POST http://localhost:8080/posts` with `blog_id`, `title`, `content`, optional `images`, `draft` |
| Get one post      | `GET http://localhost:8080/posts/{blog_id}/{post_id}` (for pull) |
| Update post       | `PUT http://localhost:8080/posts/{blog_id}/{post_id}` with `title`, `content`, optional `images` |
| List posts        | `GET http://localhost:8080/posts/{blog_id}?fetch_drafts=true` |
| Publish draft     | `POST http://localhost:8080/posts/{blog_id}/{post_id}/publish` |
| Post from file    | `python scripts/post_from_draft.py drafts/your-draft.md` |
| Update from file  | `python scripts/post_from_draft.py drafts/your-draft.md --update` (set `post_id` in frontmatter) |
| Pull post → draft | `python scripts/post_from_draft.py pull drafts/your-draft.md` (set `blog_id`, `post_id` in frontmatter) |
