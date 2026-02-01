"""
FastAPI Server for the Blog Python Agent.

Implements the embabel-agent-remote REST API:
- GET /api/v1/actions - List available actions
- GET /api/v1/types - List domain types
- POST /api/v1/actions/execute - Execute an action
"""

import logging
from typing import Dict, Any, List
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from .registry import get_registry, ActionRegistry

logger = logging.getLogger(__name__)


class ExecuteRequest(BaseModel):
    """Pydantic model for action execution request."""
    action_name: str
    parameters: Dict[str, Any] = {}


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan handler for startup/shutdown."""
    # Startup: Register all actions
    logger.info("Blog Python Agent starting up...")
    from . import actions  # Import to register all actions

    registry = get_registry()
    logger.info(f"Registered {len(registry.list_actions())} actions")
    logger.info(f"Registered {len(registry.list_types())} types")

    yield

    # Shutdown
    logger.info("Blog Python Agent shutting down...")


def create_app(registry: ActionRegistry = None) -> FastAPI:
    """
    Create the FastAPI application.

    Args:
        registry: Optional custom registry. If None, uses the global registry.

    Returns:
        Configured FastAPI application
    """
    app = FastAPI(
        title="Blog Python Agent",
        description="Python-based agent exposing Blogger operations as remote actions for Embabel",
        version="0.1.0",
        lifespan=lifespan,
    )

    # CORS middleware for cross-origin requests
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Use provided registry or global
    if registry is not None:
        app.state.registry = registry

    def get_registry_for_app() -> ActionRegistry:
        """Get the registry for this app instance."""
        if hasattr(app.state, 'registry'):
            return app.state.registry
        return get_registry()

    @app.get("/")
    async def root():
        """Root endpoint with API information."""
        return {
            "name": "Blog Python Agent",
            "version": "0.1.0",
            "endpoints": {
                "actions": "/api/v1/actions",
                "types": "/api/v1/types",
                "execute": "/api/v1/actions/execute",
            }
        }

    @app.get("/health")
    async def health():
        """Health check endpoint."""
        return {"status": "healthy"}

    @app.get("/api/v1/actions")
    async def list_actions() -> List[Dict[str, Any]]:
        """
        List all available actions.

        Returns array of RestActionMetadata objects.
        """
        registry = get_registry_for_app()
        actions = registry.list_actions()
        return [action.to_dict() for action in actions]

    @app.get("/api/v1/types")
    async def list_types() -> List[Dict[str, Any]]:
        """
        List all domain types known to the server.

        Returns array of DynamicType objects.
        """
        registry = get_registry_for_app()
        types = registry.list_types()
        return [t.to_dict() for t in types]

    @app.post("/api/v1/actions/execute")
    async def execute_action(request: ExecuteRequest) -> Dict[str, Any]:
        """
        Execute an action with given parameters.

        Request body:
        {
            "action_name": "blog_create_post",
            "parameters": {
                "blog_id": "123",
                "title": "My Post",
                ...
            }
        }

        Returns the output matching the action's output type.
        """
        registry = get_registry_for_app()

        action = registry.get_action(request.action_name)
        if action is None:
            raise HTTPException(
                status_code=404,
                detail=f"Action not found: {request.action_name}",
            )

        response = await registry.execute(request.action_name, request.parameters)

        if response.status == "error":
            raise HTTPException(
                status_code=500,
                detail=response.error,
            )

        return response.to_dict()

    @app.get("/api/v1/actions/{action_name}")
    async def get_action(action_name: str) -> Dict[str, Any]:
        """Get metadata for a specific action."""
        registry = get_registry_for_app()
        action = registry.get_action(action_name)

        if action is None:
            raise HTTPException(
                status_code=404,
                detail=f"Action not found: {action_name}",
            )

        return action.metadata.to_dict()

    return app


# Create default app instance
app = create_app()
