"""
Action Registry for the Blog Python Agent.

Provides registration and lookup of actions and types.
Actions are registered with their metadata and handler functions.
"""

import logging
from typing import Dict, List, Optional, Any
from dataclasses import dataclass

from .models import ActionMetadata, DynamicType, ActionHandler, ActionExecutionResponse

logger = logging.getLogger(__name__)


@dataclass
class RegisteredAction:
    """An action registered with its handler."""
    metadata: ActionMetadata
    handler: ActionHandler


class ActionRegistry:
    """
    Registry for actions and types.

    Actions can be registered using decorators:

        registry = ActionRegistry()

        @registry.action(
            name="blog_create_post",
            description="Create a new blog post",
            inputs=[Io("request", "CreatePostRequest")],
            outputs=[Io("result", "PostResponse")],
            pre=["draft_approved"],
            post=["post_created"],
        )
        async def create_post(params: Dict[str, Any]) -> Dict[str, Any]:
            # Implementation
            return {"id": "123", "status": "draft"}
    """

    def __init__(self):
        self._actions: Dict[str, RegisteredAction] = {}
        self._types: Dict[str, DynamicType] = {}

    def action(
        self,
        name: str,
        description: str,
        inputs: List = None,
        outputs: List = None,
        pre: List[str] = None,
        post: List[str] = None,
        cost: float = 0.5,
        value: float = 0.5,
        can_rerun: bool = True,
    ):
        """
        Decorator for registering an action.

        Example:
            @registry.action(
                name="my_action",
                description="Does something",
                inputs=[Io("input1", "InputType")],
                outputs=[Io("output1", "OutputType")],
            )
            async def my_handler(params):
                return {"result": "success"}
        """
        def decorator(handler: ActionHandler):
            metadata = ActionMetadata(
                name=name,
                description=description,
                inputs=inputs or [],
                outputs=outputs or [],
                pre=pre or [],
                post=post or [],
                cost=cost,
                value=value,
                can_rerun=can_rerun,
            )
            self._actions[name] = RegisteredAction(metadata=metadata, handler=handler)
            logger.debug(f"Registered action: {name}")
            return handler
        return decorator

    def register_action(
        self,
        metadata: ActionMetadata,
        handler: ActionHandler,
    ) -> None:
        """Register an action programmatically."""
        self._actions[metadata.name] = RegisteredAction(metadata=metadata, handler=handler)
        logger.debug(f"Registered action: {metadata.name}")

    def register_type(self, dynamic_type: DynamicType) -> None:
        """Register a dynamic type."""
        self._types[dynamic_type.name] = dynamic_type
        logger.debug(f"Registered type: {dynamic_type.name}")

    def get_action(self, name: str) -> Optional[RegisteredAction]:
        """Get a registered action by name."""
        return self._actions.get(name)

    def get_type(self, name: str) -> Optional[DynamicType]:
        """Get a registered type by name."""
        return self._types.get(name)

    def list_actions(self) -> List[ActionMetadata]:
        """List all registered actions."""
        return [ra.metadata for ra in self._actions.values()]

    def list_types(self) -> List[DynamicType]:
        """List all registered types."""
        return list(self._types.values())

    async def execute(self, action_name: str, parameters: Dict[str, Any]) -> ActionExecutionResponse:
        """
        Execute an action by name with given parameters.

        Returns an ActionExecutionResponse with the result or error.
        """
        registered = self._actions.get(action_name)
        if registered is None:
            return ActionExecutionResponse(
                result={},
                status="error",
                error=f"Action not found: {action_name}",
            )

        try:
            logger.info(f"Executing action: {action_name} with params: {parameters}")
            result = await registered.handler(parameters)
            return ActionExecutionResponse(result=result, status="success")
        except Exception as e:
            logger.exception(f"Error executing action {action_name}: {e}")
            return ActionExecutionResponse(
                result={},
                status="error",
                error=str(e),
            )


# Global registry instance
_global_registry: Optional[ActionRegistry] = None


def get_registry() -> ActionRegistry:
    """Get or create the global action registry."""
    global _global_registry
    if _global_registry is None:
        _global_registry = ActionRegistry()
    return _global_registry


def reset_registry() -> None:
    """Reset the global registry (for testing)."""
    global _global_registry
    _global_registry = None
