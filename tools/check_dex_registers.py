#!/usr/bin/env python3
"""Check app-owned methods in the already-built, unminified Play APK."""

import argparse
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path


APP_DESCRIPTOR_PREFIX = "Lat/bernhardberger/tvhplayer/"
REGISTER_LIMIT = 200


def method_registers(lines):
    """Read dexdump -d records, including signatures to distinguish overloads."""
    owner = name = signature = None
    for line in lines:
        if line.startswith("  Class descriptor"):
            owner = line.split("'", 2)[1]
            name = signature = None
        elif line.startswith("      name"):
            name = line.split("'", 2)[1]
            signature = None
        elif line.startswith("      type"):
            signature = line.split("'", 2)[1]
        elif line.startswith("      registers"):
            if owner is None or name is None or signature is None:
                raise ValueError("dexdump register record lacks a method identity")
            yield owner, name, signature, int(line.split(":", 1)[1])


def check_apk(apk, dexdump):
    violations = []
    app_methods = 0
    maximum = 0
    with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory(prefix="dex-registers-") as directory:
        dex_names = sorted(name for name in archive.namelist() if re.fullmatch(r"classes(?:[2-9]|[1-9][0-9]+)?\.dex", name))
        if not dex_names:
            raise ValueError("APK contains no classes dex files")
        for dex_name in dex_names:
            dex_path = Path(directory) / dex_name
            dex_path.write_bytes(archive.read(dex_name))
            with tempfile.TemporaryFile(mode="w+") as errors:
                process = subprocess.Popen(
                    [str(dexdump), "-d", str(dex_path)],
                    stdout=subprocess.PIPE, stderr=errors, text=True, errors="replace",
                )
                try:
                    for owner, name, signature, registers in method_registers(process.stdout):
                        if not owner.startswith(APP_DESCRIPTOR_PREFIX):
                            continue
                        app_methods += 1
                        maximum = max(maximum, registers)
                        if registers > REGISTER_LIMIT:
                            violations.append(f"{dex_name}: {owner}->{name}{signature}: {registers} registers (limit {REGISTER_LIMIT})")
                finally:
                    process.stdout.close()
                    result = process.wait()
                if result != 0:
                    raise ValueError(f"dexdump failed for {dex_name} (exit {result})")
            dex_path.unlink()
    if not app_methods:
        raise ValueError("no app method register records found; use the unminified Play debug APK")
    return violations, app_methods, maximum


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--dexdump", required=True, type=Path)
    args = parser.parse_args()
    try:
        violations, count, maximum = check_apk(args.apk, args.dexdump)
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"error: dex register check: {error}", file=sys.stderr)
        return 1
    if violations:
        for violation in violations:
            print(f"error: {violation}", file=sys.stderr)
        return 1
    print(f"Dex register check passed: {count} app methods, maximum {maximum} (limit {REGISTER_LIMIT})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
