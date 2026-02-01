"""
Command Line Interface for the Blog Python Agent.

Usage:
    # Start the server
    python -m agent serve --port 8000

    # List actions
    python -m agent list-actions

    # Execute an action
    python -m agent execute blog_list_blogs
"""

import asyncio
import logging
import sys
from typing import Optional

import click
import uvicorn

from .registry import get_registry
from .server import create_app


def setup_logging(verbose: bool = False) -> None:
    """Configure logging."""
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    )


@click.group()
@click.version_option(version="0.1.0", prog_name="blog-python-agent")
def cli():
    """
    Blog Python Agent

    Exposes Blogger operations as remote actions for the Embabel agent platform.
    """
    pass


@cli.command()
@click.option(
    "--host",
    default="0.0.0.0",
    help="Host to bind to",
)
@click.option(
    "--port",
    default=8000,
    type=int,
    help="Port to listen on",
)
@click.option(
    "--reload",
    is_flag=True,
    help="Enable auto-reload for development",
)
@click.option(
    "-v", "--verbose",
    is_flag=True,
    help="Enable verbose output",
)
def serve(
    host: str,
    port: int,
    reload: bool,
    verbose: bool,
):
    """
    Start the Blog Python Agent server.

    The server exposes REST endpoints for:
    - GET /api/v1/actions - List available actions
    - GET /api/v1/types - List domain types
    - POST /api/v1/actions/execute - Execute an action
    """
    setup_logging(verbose)
    logger = logging.getLogger(__name__)

    # Import actions to register them
    from . import actions  # noqa: F401

    registry = get_registry()
    logger.info(f"Registered {len(registry.list_actions())} actions")
    logger.info(f"Registered {len(registry.list_types())} types")

    # Print registered actions
    click.echo("\nRegistered Actions:")
    for action in registry.list_actions():
        click.echo(f"  - {action.name}: {action.description[:60]}...")

    click.echo(f"\nStarting server on {host}:{port}")
    click.echo(f"API docs: http://{host}:{port}/docs")

    uvicorn.run(
        "agent.server:app",
        host=host,
        port=port,
        reload=reload,
        log_level="debug" if verbose else "info",
    )


@cli.command("list-actions")
@click.option(
    "--json",
    "as_json",
    is_flag=True,
    help="Output as JSON",
)
def list_actions(as_json: bool):
    """
    List all registered actions.
    """
    # Import actions to register them
    from . import actions  # noqa: F401

    registry = get_registry()
    action_list = registry.list_actions()

    if as_json:
        import json
        click.echo(json.dumps([a.to_dict() for a in action_list], indent=2))
        return

    click.echo(f"\nRegistered Actions ({len(action_list)}):\n")

    for action in action_list:
        click.echo(f"  {click.style(action.name, bold=True)}")
        click.echo(f"    {action.description}")

        if action.inputs:
            inputs = ", ".join(f"{i.name}:{i.type}" for i in action.inputs)
            click.echo(f"    Inputs:  {inputs}")

        if action.outputs:
            outputs = ", ".join(f"{o.name}:{o.type}" for o in action.outputs)
            click.echo(f"    Outputs: {outputs}")

        if action.pre:
            click.echo(f"    Pre:     {', '.join(action.pre)}")

        if action.post:
            click.echo(f"    Post:    {', '.join(action.post)}")

        click.echo(f"    Cost: {action.cost}, Value: {action.value}")
        click.echo()


@cli.command("list-types")
@click.option(
    "--json",
    "as_json",
    is_flag=True,
    help="Output as JSON",
)
def list_types(as_json: bool):
    """
    List all registered types.
    """
    # Import actions to register them (they register types too)
    from . import actions  # noqa: F401

    registry = get_registry()
    type_list = registry.list_types()

    if as_json:
        import json
        click.echo(json.dumps([t.to_dict() for t in type_list], indent=2))
        return

    click.echo(f"\nRegistered Types ({len(type_list)}):\n")

    for dtype in type_list:
        click.echo(f"  {click.style(dtype.name, bold=True)}")
        click.echo(f"    {dtype.description}")

        if dtype.own_properties:
            click.echo("    Properties:")
            for prop in dtype.own_properties:
                click.echo(f"      - {prop.name}: {prop.type}")
                if prop.description:
                    click.echo(f"        {prop.description}")
        click.echo()


@cli.command()
@click.argument("action_name")
@click.option(
    "--params",
    "-p",
    multiple=True,
    help="Parameters as key=value pairs",
)
@click.option(
    "--json-params",
    help="Parameters as JSON string",
)
@click.option(
    "-v", "--verbose",
    is_flag=True,
    help="Enable verbose output",
)
def execute(
    action_name: str,
    params: tuple,
    json_params: Optional[str],
    verbose: bool,
):
    """
    Execute an action locally (for testing).

    Example:
        python -m agent execute blog_list_blogs
        python -m agent execute blog_get_post --params blog_id=123 --params post_id=456
    """
    setup_logging(verbose)

    # Import actions to register them
    from . import actions  # noqa: F401

    # Parse parameters
    parameters = {}

    if json_params:
        import json
        parameters = json.loads(json_params)

    for param in params:
        if "=" in param:
            key, value = param.split("=", 1)
            # Try to parse as JSON for complex values
            try:
                import json
                value = json.loads(value)
            except json.JSONDecodeError:
                pass
            parameters[key] = value

    registry = get_registry()

    click.echo(f"Executing action: {action_name}")
    click.echo(f"Parameters: {parameters}")

    async def run():
        response = await registry.execute(action_name, parameters)
        return response

    response = asyncio.run(run())

    if response.status == "error":
        click.echo(click.style(f"Error: {response.error}", fg="red"))
        sys.exit(1)

    import json
    click.echo(click.style("Success!", fg="green"))
    click.echo(json.dumps(response.result, indent=2))


def main():
    """Entry point for the CLI."""
    cli()


if __name__ == "__main__":
    main()
