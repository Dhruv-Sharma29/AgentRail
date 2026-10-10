#!/usr/bin/env python3
"""Create private local Compose credentials without overwriting an existing file."""
import argparse
import os
from pathlib import Path
import secrets


def initialize(path: Path) -> bool:
    try:
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        return False
    with os.fdopen(descriptor, "w") as configuration:
        configuration.write(f"DATABASE_PASSWORD={secrets.token_urlsafe(24)}\n"
                            "BOOTSTRAP_USERNAME=admin\n"
                            f"BOOTSTRAP_PASSWORD={secrets.token_urlsafe(24)}\n"
                            "WEB_PORT=8090\n")
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=Path(__file__).resolve().parent.parent / ".env")
    path = parser.parse_args().env_file
    created = initialize(path)
    print(f"{'Created private configuration' if created else 'Kept existing configuration'}: {path}")
    print("Read BOOTSTRAP_USERNAME and BOOTSTRAP_PASSWORD from that file to sign in. Keep it private.")


if __name__ == "__main__":
    main()
