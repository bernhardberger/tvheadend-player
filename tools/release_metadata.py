#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any


CANONICAL_KEY_ALIAS = "tvhplayer-release"
CANONICAL_CERTIFICATE_SHA256 = (
    "1E:18:48:62:F5:BB:A2:D1:C8:40:6D:6A:7A:79:65:7F:"
    "F3:A7:3D:25:8C:1E:1B:75:FA:25:02:58:75:E5:AB:C9"
)
SCHEMA_VERSION = 2
_SHA256_PATTERN = re.compile(r"[0-9a-f]{64}")
_COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
_CHECKSUM_LINE_PATTERN = re.compile(r"([0-9a-fA-F]{64}) [ *](.+)")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def normalize_fingerprint(value: str) -> str:
    if not isinstance(value, str):
        raise ValueError("certificate SHA-256 fingerprint must be text")
    compact = re.sub(r"[^0-9a-fA-F]", "", value)
    if len(compact) != 64:
        raise ValueError("certificate SHA-256 fingerprint must contain 64 hexadecimal digits")
    return ":".join(compact[index : index + 2] for index in range(0, 64, 2)).upper()


def validate_checksum_file(
    checksums_path: Path,
    directory: Path,
    expected_file_names: set[str],
) -> None:
    recorded: dict[str, str] = {}
    for line in checksums_path.read_text(encoding="utf-8").splitlines():
        match = _CHECKSUM_LINE_PATTERN.fullmatch(line)
        if match is None:
            raise ValueError("checksum file contains an invalid line")
        digest, file_name = match.groups()
        if Path(file_name).name != file_name or file_name in {".", ".."}:
            raise ValueError(f"checksum must use a safe file name: {file_name!r}")
        if file_name in recorded:
            raise ValueError(f"checksum file contains a duplicate: {file_name}")
        recorded[file_name] = digest.lower()
    if set(recorded) != expected_file_names:
        raise ValueError("checksum file does not list exactly the expected release files")
    for file_name, digest in recorded.items():
        path = directory / file_name
        if path.is_symlink() or not path.is_file() or sha256_file(path) != digest:
            raise ValueError(f"checksum does not match: {file_name}")


def _validate_artifacts(artifacts: dict[str, dict[str, str]], required: set[str]) -> None:
    if set(artifacts) != required:
        raise ValueError(f"manifest artifacts must be exactly: {', '.join(sorted(required))}")
    for artifact in artifacts.values():
        if not isinstance(artifact, dict):
            raise ValueError("manifest artifact entries must be JSON objects")
        file_name = artifact.get("file", "")
        if (
            not isinstance(file_name, str)
            or not file_name
            or Path(file_name).name != file_name
            or file_name in {".", ".."}
        ):
            raise ValueError(f"artifact must use a safe file name: {file_name!r}")
        digest_value = artifact.get("sha256", "")
        if not isinstance(digest_value, str):
            raise ValueError(f"artifact has an invalid SHA-256 digest: {file_name}")
        digest = digest_value.lower()
        if not _SHA256_PATTERN.fullmatch(digest):
            raise ValueError(f"artifact has an invalid SHA-256 digest: {file_name}")
        artifact["sha256"] = digest


def _build_manifest(
    *,
    stage: str,
    application_id: str,
    version_code: int,
    version_name: str,
    source_commit: str,
    sdk_source_commit: str,
    artifacts: dict[str, dict[str, str]],
) -> dict[str, Any]:
    if not application_id:
        raise ValueError("application ID must not be empty")
    if version_code < 1:
        raise ValueError("versionCode must be positive")
    if not version_name:
        raise ValueError("versionName must not be empty")
    if not _COMMIT_PATTERN.fullmatch(source_commit.lower()):
        raise ValueError("source commit must be a full 40-digit Git object ID")
    if not _COMMIT_PATTERN.fullmatch(sdk_source_commit.lower()):
        raise ValueError("SDK source commit must be a full 40-digit Git object ID")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "stage": stage,
        "applicationId": application_id,
        "versionCode": version_code,
        "versionName": version_name,
        "sourceCommit": source_commit.lower(),
        "sdkSourceCommit": sdk_source_commit.lower(),
        "artifacts": artifacts,
    }


def build_unsigned_manifest(
    *,
    application_id: str,
    version_code: int,
    version_name: str,
    source_commit: str,
    sdk_source_commit: str,
    artifacts: dict[str, dict[str, str]],
) -> dict[str, Any]:
    _validate_artifacts(
        artifacts,
        {
            "unsignedApk",
            "sourceTarGz",
            "sourceZip",
            "nativeSourceTarGz",
            "sdkSourceTarGz",
        },
    )
    return _build_manifest(
        stage="unsigned",
        application_id=application_id,
        version_code=version_code,
        version_name=version_name,
        source_commit=source_commit,
        sdk_source_commit=sdk_source_commit,
        artifacts=artifacts,
    )


def validate_unsigned_manifest(
    manifest: dict[str, Any],
    *,
    application_id: str,
    version_code: int,
    version_name: str,
) -> None:
    if manifest.get("schemaVersion") != SCHEMA_VERSION or manifest.get("stage") != "unsigned":
        raise ValueError("not a supported unsigned release manifest")
    if not isinstance(manifest.get("applicationId"), str) or not manifest["applicationId"]:
        raise ValueError("manifest application ID is invalid")
    if not isinstance(manifest.get("versionCode"), int) or manifest["versionCode"] < 1:
        raise ValueError("manifest versionCode is invalid")
    if not isinstance(manifest.get("versionName"), str) or not manifest["versionName"]:
        raise ValueError("manifest versionName is invalid")
    if manifest.get("applicationId") != application_id:
        raise ValueError("manifest application ID does not match the APK")
    if manifest.get("versionCode") != version_code:
        raise ValueError("manifest versionCode does not match the APK")
    if manifest.get("versionName") != version_name:
        raise ValueError("manifest versionName does not match the APK")
    source_commit = manifest.get("sourceCommit", "")
    if not isinstance(source_commit, str) or not _COMMIT_PATTERN.fullmatch(source_commit):
        raise ValueError("manifest source commit is invalid")
    sdk_source_commit = manifest.get("sdkSourceCommit", "")
    if not isinstance(sdk_source_commit, str) or not _COMMIT_PATTERN.fullmatch(
        sdk_source_commit
    ):
        raise ValueError("manifest SDK source commit is invalid")
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, dict):
        raise ValueError("manifest artifacts are invalid")
    _validate_artifacts(
        artifacts,
        {
            "unsignedApk",
            "sourceTarGz",
            "sourceZip",
            "nativeSourceTarGz",
            "sdkSourceTarGz",
        },
    )


def validate_signed_manifest(manifest: dict[str, Any]) -> None:
    if manifest.get("schemaVersion") != SCHEMA_VERSION or manifest.get("stage") != "signed":
        raise ValueError("not a supported signed release manifest")
    if not isinstance(manifest.get("applicationId"), str) or not manifest["applicationId"]:
        raise ValueError("signed manifest application ID is invalid")
    if not isinstance(manifest.get("versionCode"), int) or manifest["versionCode"] < 1:
        raise ValueError("signed manifest versionCode is invalid")
    if not isinstance(manifest.get("versionName"), str) or not manifest["versionName"]:
        raise ValueError("signed manifest versionName is invalid")
    source_commit = manifest.get("sourceCommit", "")
    if not isinstance(source_commit, str) or not _COMMIT_PATTERN.fullmatch(source_commit):
        raise ValueError("signed manifest source commit is invalid")
    sdk_source_commit = manifest.get("sdkSourceCommit", "")
    if not isinstance(sdk_source_commit, str) or not _COMMIT_PATTERN.fullmatch(
        sdk_source_commit
    ):
        raise ValueError("signed manifest SDK source commit is invalid")
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, dict):
        raise ValueError("signed manifest artifacts are invalid")
    _validate_artifacts(
        artifacts,
        {
            "signedApk",
            "sourceTarGz",
            "sourceZip",
            "nativeSourceTarGz",
            "sdkSourceTarGz",
        },
    )
    signing = manifest.get("signing")
    if not isinstance(signing, dict) or signing.get("keyAlias") != CANONICAL_KEY_ALIAS:
        raise ValueError("signed manifest key alias is not canonical")
    if normalize_fingerprint(signing.get("certificateSha256", "")) != CANONICAL_CERTIFICATE_SHA256:
        raise ValueError("signed manifest certificate is not canonical")


def build_signed_manifest(
    *,
    unsigned_manifest: dict[str, Any],
    signed_apk: Path,
    certificate_sha256: str,
    source_tar_gz: Path,
    source_zip: Path,
    native_source_tar_gz: Path,
    sdk_source_tar_gz: Path,
) -> dict[str, Any]:
    validate_unsigned_manifest(
        unsigned_manifest,
        application_id=unsigned_manifest.get("applicationId"),
        version_code=unsigned_manifest.get("versionCode"),
        version_name=unsigned_manifest.get("versionName"),
    )
    certificate = normalize_fingerprint(certificate_sha256)
    if certificate != CANONICAL_CERTIFICATE_SHA256:
        raise ValueError("signing certificate does not match the canonical fingerprint")
    artifacts = {
        "signedApk": _artifact(signed_apk),
        "sourceTarGz": _artifact(source_tar_gz),
        "sourceZip": _artifact(source_zip),
        "nativeSourceTarGz": _artifact(native_source_tar_gz),
        "sdkSourceTarGz": _artifact(sdk_source_tar_gz),
    }
    _validate_artifacts(
        artifacts,
        {
            "signedApk",
            "sourceTarGz",
            "sourceZip",
            "nativeSourceTarGz",
            "sdkSourceTarGz",
        },
    )
    manifest = _build_manifest(
        stage="signed",
        application_id=unsigned_manifest["applicationId"],
        version_code=unsigned_manifest["versionCode"],
        version_name=unsigned_manifest["versionName"],
        source_commit=unsigned_manifest["sourceCommit"],
        sdk_source_commit=unsigned_manifest["sdkSourceCommit"],
        artifacts=artifacts,
    )
    manifest["signing"] = {
        "keyAlias": CANONICAL_KEY_ALIAS,
        "certificateSha256": certificate,
    }
    return manifest


def validate_source_lineage(
    unsigned_manifest: dict[str, Any],
    signed_manifest: dict[str, Any],
) -> None:
    for field in ("sourceCommit", "sdkSourceCommit"):
        if unsigned_manifest.get(field) != signed_manifest.get(field):
            raise ValueError(f"signed and unsigned manifests have different {field}")
    for name in (
        "sourceTarGz",
        "sourceZip",
        "nativeSourceTarGz",
        "sdkSourceTarGz",
    ):
        unsigned_artifact = unsigned_manifest.get("artifacts", {}).get(name)
        signed_artifact = signed_manifest.get("artifacts", {}).get(name)
        if unsigned_artifact != signed_artifact:
            raise ValueError(f"signed bundle changed source artifact: {name}")


def _artifact(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise ValueError(f"artifact does not exist: {path}")
    return {"file": path.name, "sha256": sha256_file(path)}


def _write_manifest(path: Path, manifest: dict[str, Any]) -> None:
    path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def _load_manifest(path: Path) -> dict[str, Any]:
    loaded = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(loaded, dict):
        raise ValueError("release manifest must contain a JSON object")
    return loaded


def _verify_artifact_files(manifest: dict[str, Any], directory: Path) -> None:
    for artifact in manifest["artifacts"].values():
        path = directory / artifact["file"]
        if path.is_symlink() or not path.is_file() or sha256_file(path) != artifact["sha256"]:
            raise ValueError(f"artifact checksum does not match: {artifact['file']}")


def _create_unsigned(args: argparse.Namespace) -> None:
    artifacts = {
        "unsignedApk": _artifact(args.unsigned_apk),
        "sourceTarGz": _artifact(args.source_tar_gz),
        "sourceZip": _artifact(args.source_zip),
        "nativeSourceTarGz": _artifact(args.native_source_tar_gz),
        "sdkSourceTarGz": _artifact(args.sdk_source_tar_gz),
    }
    _write_manifest(
        args.output,
        build_unsigned_manifest(
            application_id=args.application_id,
            version_code=args.version_code,
            version_name=args.version_name,
            source_commit=args.source_commit,
            sdk_source_commit=args.sdk_source_commit,
            artifacts=artifacts,
        ),
    )


def _validate_unsigned(args: argparse.Namespace) -> None:
    manifest = _load_manifest(args.manifest)
    validate_unsigned_manifest(
        manifest,
        application_id=args.application_id,
        version_code=args.version_code,
        version_name=args.version_name,
    )
    _verify_artifact_files(manifest, args.directory)


def _validate_checksums(args: argparse.Namespace) -> None:
    manifest = _load_manifest(args.manifest)
    validate_unsigned_manifest(
        manifest,
        application_id=manifest.get("applicationId"),
        version_code=manifest.get("versionCode"),
        version_name=manifest.get("versionName"),
    )
    expected = {artifact["file"] for artifact in manifest["artifacts"].values()}
    expected.add(args.manifest.name)
    validate_checksum_file(args.checksums, args.directory, expected)


def _create_signed(args: argparse.Namespace) -> None:
    unsigned = _load_manifest(args.unsigned_manifest)
    validate_unsigned_manifest(
        unsigned,
        application_id=args.application_id,
        version_code=args.version_code,
        version_name=args.version_name,
    )
    _write_manifest(
        args.output,
        build_signed_manifest(
            unsigned_manifest=unsigned,
            signed_apk=args.signed_apk,
            certificate_sha256=args.certificate_sha256,
            source_tar_gz=args.source_tar_gz,
            source_zip=args.source_zip,
            native_source_tar_gz=args.native_source_tar_gz,
            sdk_source_tar_gz=args.sdk_source_tar_gz,
        ),
    )


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Create and validate TVHeadend Player release metadata")
    subparsers = parser.add_subparsers(dest="command", required=True)

    def add_identity_arguments(command: argparse.ArgumentParser) -> None:
        command.add_argument("--application-id", required=True)
        command.add_argument("--version-code", required=True, type=int)
        command.add_argument("--version-name", required=True)

    create_unsigned = subparsers.add_parser("create-unsigned")
    add_identity_arguments(create_unsigned)
    create_unsigned.add_argument("--source-commit", required=True)
    create_unsigned.add_argument("--sdk-source-commit", required=True)
    create_unsigned.add_argument("--unsigned-apk", required=True, type=Path)
    create_unsigned.add_argument("--source-tar-gz", required=True, type=Path)
    create_unsigned.add_argument("--source-zip", required=True, type=Path)
    create_unsigned.add_argument("--native-source-tar-gz", required=True, type=Path)
    create_unsigned.add_argument("--sdk-source-tar-gz", required=True, type=Path)
    create_unsigned.add_argument("--output", required=True, type=Path)
    create_unsigned.set_defaults(handler=_create_unsigned)

    validate_unsigned = subparsers.add_parser("validate-unsigned")
    add_identity_arguments(validate_unsigned)
    validate_unsigned.add_argument("--manifest", required=True, type=Path)
    validate_unsigned.add_argument("--directory", required=True, type=Path)
    validate_unsigned.set_defaults(handler=_validate_unsigned)

    validate_checksums = subparsers.add_parser("validate-checksums")
    validate_checksums.add_argument("--checksums", required=True, type=Path)
    validate_checksums.add_argument("--manifest", required=True, type=Path)
    validate_checksums.add_argument("--directory", required=True, type=Path)
    validate_checksums.set_defaults(handler=_validate_checksums)

    create_signed = subparsers.add_parser("create-signed")
    add_identity_arguments(create_signed)
    create_signed.add_argument("--unsigned-manifest", required=True, type=Path)
    create_signed.add_argument("--signed-apk", required=True, type=Path)
    create_signed.add_argument("--source-tar-gz", required=True, type=Path)
    create_signed.add_argument("--source-zip", required=True, type=Path)
    create_signed.add_argument("--native-source-tar-gz", required=True, type=Path)
    create_signed.add_argument("--sdk-source-tar-gz", required=True, type=Path)
    create_signed.add_argument("--certificate-sha256", required=True)
    create_signed.add_argument("--output", required=True, type=Path)
    create_signed.set_defaults(handler=_create_signed)
    return parser


def main() -> int:
    args = _parser().parse_args()
    try:
        args.handler(args)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
