"""
Entry point for running the agent as a module.

Usage:
    python -m agent serve --port 8000
    python -m agent list-actions
"""

from .cli import main

if __name__ == "__main__":
    main()
