"""
Tests for the action registry.
"""

import pytest
from agent.registry import ActionRegistry, get_registry, reset_registry
from agent.models import Io, DynamicType, PropertyDef


@pytest.fixture(autouse=True)
def reset():
    """Reset registry before each test."""
    reset_registry()
    yield
    reset_registry()


def test_action_registration():
    """Test that actions can be registered with decorator."""
    registry = ActionRegistry()

    @registry.action(
        name="test_action",
        description="A test action",
        inputs=[Io("input1", "string")],
        outputs=[Io("output1", "string")],
    )
    async def test_handler(params):
        return {"result": params.get("input1", "default")}

    actions = registry.list_actions()
    assert len(actions) == 1
    assert actions[0].name == "test_action"
    assert actions[0].description == "A test action"


def test_type_registration():
    """Test that types can be registered."""
    registry = ActionRegistry()

    test_type = DynamicType(
        name="TestType",
        description="A test type",
        own_properties=[
            PropertyDef("field1", "string", "A field"),
        ],
    )

    registry.register_type(test_type)

    types = registry.list_types()
    assert len(types) == 1
    assert types[0].name == "TestType"


@pytest.mark.asyncio
async def test_action_execution():
    """Test that actions can be executed."""
    registry = ActionRegistry()

    @registry.action(
        name="echo",
        description="Echo the input",
    )
    async def echo_handler(params):
        return {"echo": params.get("message", "")}

    response = await registry.execute("echo", {"message": "hello"})
    
    assert response.status == "success"
    assert response.result["echo"] == "hello"


@pytest.mark.asyncio
async def test_action_not_found():
    """Test error when action not found."""
    registry = ActionRegistry()

    response = await registry.execute("nonexistent", {})
    
    assert response.status == "error"
    assert "not found" in response.error


def test_global_registry():
    """Test global registry singleton."""
    reg1 = get_registry()
    reg2 = get_registry()
    
    assert reg1 is reg2
