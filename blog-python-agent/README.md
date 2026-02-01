# Blog Python Agent

A **Python-based agent** that exposes Blogger operations as remote actions following the **embabel-agent-remote REST API** pattern. This allows the Kotlin Blog Agent to orchestrate workflows while Python handles the actual Blogger/Drive API calls.

## Architecture

```
┌─────────────────────────┐     ┌─────────────────────────┐
│   Kotlin Blog Agent     │     │   Blog Python Agent     │
│   (GOAP Orchestration)  │────▶│   (Blogger Operations)  │
│                         │     │                         │
│   BlogPythonAgentClient │     │   /api/v1/actions/exec  │
└─────────────────────────┘     └─────────────────────────┘
                                          │
                                          ▼
                                ┌─────────────────────────┐
                                │   Blogger API / Drive   │
                                │   (Google APIs)         │
                                └─────────────────────────┘
```

## Available Actions

| Action | Description | Pre | Post |
|--------|-------------|-----|------|
| `blog_list_blogs` | List available blogs | - | - |
| `blog_create_post` | Create a new post | draft_approved | post_created |
| `blog_update_post` | Update existing post | post_created | post_updated |
| `blog_publish_post` | Publish a draft | post_created | post_published |
| `blog_get_post` | Get post content | - | - |
| `blog_upload_image` | Upload image to Drive | - | image_uploaded |
| `blog_list_posts` | List posts for a blog | - | - |

## Quick Start

### 1. Install Dependencies

```bash
cd blog-python-agent
pip install -e .
# Or
pip install -r requirements.txt
```

### 2. Set Up Authentication

The Python agent uses the same authentication as the existing `blog_updater`:

```bash
# Ensure token.json exists in parent directory
ls ../token.json
```

### 3. Start the Agent

```bash
python -m agent serve --port 8000
```

Or with verbose logging:

```bash
python -m agent serve --port 8000 -v
```

### 4. Verify

```bash
# Health check
curl http://localhost:8000/health

# List actions
curl http://localhost:8000/api/v1/actions

# List blogs
curl -X POST http://localhost:8000/api/v1/actions/execute \
  -H "Content-Type: application/json" \
  -d '{"action_name": "blog_list_blogs", "parameters": {}}'
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | API information |
| GET | `/health` | Health check |
| GET | `/api/v1/actions` | List all actions |
| GET | `/api/v1/types` | List all types |
| GET | `/api/v1/actions/{name}` | Get action metadata |
| POST | `/api/v1/actions/execute` | Execute an action |

## Execute Request Format

```json
{
  "action_name": "blog_create_post",
  "parameters": {
    "blog_id": "123456789",
    "title": "My Blog Post",
    "content": "<p>Hello world!</p>",
    "labels": ["tech", "tutorial"],
    "draft": true
  }
}
```

## Execute Response Format

```json
{
  "result": {
    "id": "987654321",
    "url": "https://...",
    "status": "draft",
    "title": "My Blog Post"
  },
  "status": "success"
}
```

## CLI Commands

```bash
# Start server
python -m agent serve --port 8000

# List actions
python -m agent list-actions

# List types
python -m agent list-types

# Execute action (testing)
python -m agent execute blog_list_blogs
python -m agent execute blog_get_post --params blog_id=123 --params post_id=456
```

## Integration with Kotlin Blog Agent

Configure the Kotlin agent to use the Python agent:

```yaml
# blog-agent/src/main/resources/application.yml
blog-agent:
  python-agent:
    url: http://localhost:8000
```

The `BloggerService` will automatically delegate to the Python agent when configured.

## Development

### Run Tests

```bash
pytest tests/
```

### Format Code

```bash
black agent/
ruff check agent/
```

## Project Structure

```
blog-python-agent/
├── agent/
│   ├── __init__.py
│   ├── __main__.py
│   ├── cli.py              # CLI commands
│   ├── models.py           # Data models (ActionMetadata, etc.)
│   ├── registry.py         # Action registry
│   ├── server.py           # FastAPI server
│   └── actions/
│       ├── __init__.py
│       └── blog_actions.py # Blog action implementations
├── tests/
│   └── ...
├── pyproject.toml
├── requirements.txt
└── README.md
```

## License

Apache 2.0
