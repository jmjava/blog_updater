"""
Blog Python Agent

A Python-based agent that exposes Blogger operations as remote actions
following the embabel-agent-remote REST API pattern.

This enables the Kotlin Blog Agent to orchestrate the workflow while
Python handles the actual Blogger/Drive API calls.

Endpoints:
- GET /api/v1/actions - List available actions
- GET /api/v1/types - List domain types
- POST /api/v1/actions/execute - Execute an action
"""

__version__ = "0.1.0"

from .models import ActionMetadata, DynamicType, Io, ActionExecutionRequest
from .registry import ActionRegistry
from .server import create_app

__all__ = [
    "ActionMetadata",
    "DynamicType",
    "Io",
    "ActionExecutionRequest",
    "ActionRegistry",
    "create_app",
]
