# Blog Agent

An **Embabel Guide-based agent** for iterative blog content generation with Human-in-the-Loop (HITL) workflow. Uses Neo4j for RAG to ingest documentation and repositories for context-aware content generation.

## Features

- **GOAP-based Workflow**: Automatic planning from topic to published post
- **RAG Integration**: Ingest docs, repos, and local files for context
- **HITL Checkpoints**: Human review before publishing
- **Iterative Revisions**: Refine content based on feedback
- **Blogger Integration**: Direct posting via the existing FastAPI service

## Architecture

```
┌─────────────────┐     ┌───────────────────┐     ┌─────────────┐
│   Chat/API      │────▶│   Blog Agent      │────▶│   Blogger   │
│   Interface     │     │   (GOAP Engine)   │     │   API       │
└─────────────────┘     └───────────────────┘     └─────────────┘
                               │
                               ▼
                        ┌───────────────┐
                        │   Neo4j RAG   │
                        │   (Drivine)   │
                        └───────────────┘
```

## Workflow States

```
TOPIC_SELECTED → RESEARCH_COMPLETE → OUTLINE_CREATED → DRAFT_GENERATED
                                                              │
                                                              ▼
PUBLISHED ← POST_CREATED ← IMAGES_ADDED ← DRAFT_APPROVED ← AWAITING_REVIEW
                                                              │
                                                     ◀── FEEDBACK_RECEIVED (revise)
```

## Prerequisites

- Java 21+
- Maven 3.6+
- Neo4j 5.x (via Docker)
- The existing `blog_updater` FastAPI running on port 8080
- OpenAI API key (set `OPENAI_API_KEY`)

## Quick Start

### 1. Start Neo4j

```bash
cd blog-agent
docker compose up -d
```

Wait for Neo4j to be ready (check http://localhost:7474).

### 2. Start the Blog Updater API

```bash
cd ..  # blog_updater root
source .venv/bin/activate
uvicorn api:app --port 8080
```

### 3. Start the Blog Agent

```bash
cd blog-agent
export OPENAI_API_KEY=your-key-here
./mvnw spring-boot:run
```

The agent runs on port **1338** by default.

## Usage

### Via REST API

```bash
# Start a new workflow
curl -X POST http://localhost:1338/api/blog-agent/workflow/start \
  -H "Content-Type: application/json" \
  -d '{"topic": "Introduction to Embabel Agents", "instructions": "Focus on GOAP planning"}'

# Check status
curl http://localhost:1338/api/blog-agent/workflow/status/{draftId}

# Approve draft
curl -X POST http://localhost:1338/api/blog-agent/workflow/{draftId}/approve

# Provide feedback (during review)
curl -X POST http://localhost:1338/api/blog-agent/workflow/{draftId}/feedback \
  -H "Content-Type: application/json" \
  -d '{"feedback": "Add more code examples"}'

# List all drafts
curl http://localhost:1338/api/blog-agent/drafts

# RAG search
curl -X POST http://localhost:1338/api/blog-agent/rag/search \
  -H "Content-Type: application/json" \
  -d '{"query": "How do GOAP agents work?", "limit": 5}'
```

### Via Chat

Connect to the WebSocket endpoint at `ws://localhost:1338/ws` and send messages:

- `Write about [topic]` - Start a new blog post
- `status` - Check current workflow status
- `approve` - Approve draft (during review)
- `[feedback text]` - Request revisions (during review)
- `show draft` - View full draft content
- `help` - Show available commands

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
blog-agent:
  # Blogger API URL (existing FastAPI)
  blogger-api-url: http://localhost:8080

  # Default blog ID
  default-blog-id: YOUR_BLOG_ID

  # URLs to ingest for RAG
  urls:
    - https://docs.embabel.com/embabel-agent/guide/0.3.1-SNAPSHOT/
    - https://github.com/your-repo

  # Local directories to ingest
  local-dirs:
    - ../  # blog_updater root

  # HITL settings
  hitl:
    require-approval: true
    max-revisions: 3
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OPENAI_API_KEY` | OpenAI API key | (required) |
| `NEO4J_URI` | Neo4j connection URI | bolt://localhost:7687 |
| `NEO4J_USERNAME` | Neo4j username | neo4j |
| `NEO4J_PASSWORD` | Neo4j password | brahmsian |

## API Endpoints

### Workflow

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/blog-agent/workflow/start` | Start new workflow |
| GET | `/api/blog-agent/workflow/status/{id}` | Get workflow status |
| POST | `/api/blog-agent/workflow/{id}/approve` | Approve draft |
| POST | `/api/blog-agent/workflow/{id}/feedback` | Submit feedback |

### Drafts

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/blog-agent/drafts` | List all drafts |
| GET | `/api/blog-agent/drafts/{id}` | Get draft by ID |
| DELETE | `/api/blog-agent/drafts/{id}` | Delete draft |
| GET | `/api/blog-agent/drafts/awaiting-review` | List drafts pending review |

### RAG

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/blog-agent/rag/stats` | Get RAG stats |
| POST | `/api/blog-agent/rag/ingest/url` | Ingest URL |
| POST | `/api/blog-agent/rag/ingest/directory` | Ingest directory |
| POST | `/api/blog-agent/rag/search` | Search RAG store |

### Blogger

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/blog-agent/blogger/blogs` | List blogs |
| GET | `/api/blog-agent/blogger/health` | Check Blogger API health |

## Development

### Build

```bash
./mvnw clean package
```

### Run Tests

```bash
./mvnw test
```

### Project Structure

```
blog-agent/
├── src/main/kotlin/com/blogagent/
│   ├── BlogAgentApplication.kt     # Main application
│   ├── action/                     # GOAP actions
│   │   ├── ResearchActions.kt
│   │   ├── DraftActions.kt
│   │   ├── ReviewActions.kt
│   │   └── PublishActions.kt
│   ├── agent/                      # Agents
│   │   ├── BlogWorkflowAgent.kt
│   │   └── BlogChatActions.kt
│   ├── config/
│   │   └── BlogAgentProperties.kt
│   ├── controller/
│   │   └── BlogAgentController.kt
│   ├── model/                      # Domain models
│   │   ├── BlogDraft.kt
│   │   ├── BlogPost.kt
│   │   └── WorkflowState.kt
│   └── service/                    # Services
│       ├── BloggerService.kt
│       └── RagDataManager.kt
├── src/main/resources/
│   ├── application.yml
│   └── prompts/
├── docker-compose.yml              # Neo4j
├── pom.xml
└── README.md
```

## Integration with blog_updater

The Blog Agent integrates with the existing `blog_updater` Python API:

1. **Blogger Operations**: Uses the FastAPI endpoints (`/posts`, `/blogs`, etc.)
2. **Image Upload**: Uses `/upload-image` for Google Drive uploads
3. **Draft Files**: Can read/write to `../drafts/` directory
4. **Authentication**: Uses the same `token.json` via the FastAPI service

## License

Apache 2.0
