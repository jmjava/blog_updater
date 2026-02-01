"""
Data models for the Blog Python Agent REST API.

These models align with the embabel-agent-remote REST API specification:
- ActionMetadata: Describes an available action
- DynamicType: Describes a domain type with properties
- Io: Input/output binding for actions
"""

from dataclasses import dataclass, field
from typing import List, Optional, Dict, Any, Callable, Awaitable


@dataclass
class Io:
    """Input/output binding for an action."""
    name: str
    type: str

    def to_dict(self) -> Dict[str, str]:
        return {"name": self.name, "type": self.type}


@dataclass
class PropertyDef:
    """Property definition for a dynamic type."""
    name: str
    type: str
    description: str = ""

    def to_dict(self) -> Dict[str, str]:
        return {
            "name": self.name,
            "type": self.type,
            "description": self.description,
        }


@dataclass
class DynamicType:
    """
    A dynamically defined domain type.

    Corresponds to DynamicType in the Embabel agent platform.
    These types describe the structure of inputs/outputs for actions.
    """
    name: str
    description: str
    own_properties: List[PropertyDef] = field(default_factory=list)
    parents: List[str] = field(default_factory=list)
    creation_permitted: bool = True

    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "description": self.description,
            "ownProperties": [p.to_dict() for p in self.own_properties],
            "parents": self.parents,
            "creationPermitted": self.creation_permitted,
        }


@dataclass
class ActionMetadata:
    """
    Metadata describing an available action.

    Corresponds to RestActionMetadata in embabel-agent-remote.
    Actions have:
    - name: Unique identifier
    - description: Human-readable description
    - inputs: Required input bindings
    - outputs: Output bindings
    - pre: Preconditions (for GOAP planning)
    - post: Postconditions (effects on world state)
    - cost: Execution cost (0.0 to 1.0)
    - value: Value produced (0.0 to 1.0)
    - can_rerun: Whether the action can be re-executed
    """
    name: str
    description: str
    inputs: List[Io] = field(default_factory=list)
    outputs: List[Io] = field(default_factory=list)
    pre: List[str] = field(default_factory=list)
    post: List[str] = field(default_factory=list)
    cost: float = 0.5
    value: float = 0.5
    can_rerun: bool = True

    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "description": self.description,
            "inputs": [io.to_dict() for io in self.inputs],
            "outputs": [io.to_dict() for io in self.outputs],
            "pre": self.pre,
            "post": self.post,
            "cost": self.cost,
            "value": self.value,
            "can_rerun": self.can_rerun,
        }


@dataclass
class ActionExecutionRequest:
    """Request payload for executing an action."""
    action_name: str
    parameters: Dict[str, Any] = field(default_factory=dict)


@dataclass
class ActionExecutionResponse:
    """Response from action execution."""
    result: Dict[str, Any]
    status: str = "success"
    error: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        response = {
            "result": self.result,
            "status": self.status,
        }
        if self.error:
            response["error"] = self.error
        return response


@dataclass
class ServerRegistration:
    """
    Registration payload for registering with an Embabel server.

    Corresponds to RestServerRegistration in embabel-agent-remote.
    """
    base_url: str
    name: str
    description: str

    def to_dict(self) -> Dict[str, str]:
        return {
            "baseUrl": self.base_url,
            "name": self.name,
            "description": self.description,
        }


# Type alias for action handlers
ActionHandler = Callable[[Dict[str, Any]], Awaitable[Dict[str, Any]]]
