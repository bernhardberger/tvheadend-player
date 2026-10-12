#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import runpy
import struct
import subprocess
import sys
import zipfile
import zlib
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


CANONICAL_KEY_ALIAS = "tvhplayer-release"
CANONICAL_CERTIFICATE_SHA256 = (
    "1E:18:48:62:F5:BB:A2:D1:C8:40:6D:6A:7A:79:65:7F:"
    "F3:A7:3D:25:8C:1E:1B:75:FA:25:02:58:75:E5:AB:C9"
)
SCHEMA_VERSION = 3
BUNDLETOOL_VERSION = "1.18.3"
APPLICATION_IDS = {
    "play": "at.bernhardberger.tvhplayer",
    "appliance": "at.bernhardberger.tvhplayer.appliance",
}
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
    file_names: set[str] = set()
    for name, artifact in artifacts.items():
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
        if file_name in file_names:
            raise ValueError("manifest contains duplicate artifact file names")
        file_names.add(file_name)
        if name.endswith(("Apk", "Aab")):
            flavor = "appliance" if "Appliance" in name else "play"
            if artifact.get("applicationId") != APPLICATION_IDS[flavor]:
                raise ValueError(f"artifact application ID does not match {name}")
            if artifact.get("flavor") != flavor:
                raise ValueError(f"artifact flavor does not match {name}")
            if not file_name.endswith(".aab" if name.endswith("Aab") else ".apk"):
                raise ValueError(f"artifact extension does not match {name}")
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
    if application_id != APPLICATION_IDS["play"]:
        raise ValueError("release application ID must be the Play application ID")
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
            "unsignedPlayApk",
            "unsignedApplianceApk",
            "unsignedPlayAab",
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
    if manifest.get("applicationId") != APPLICATION_IDS["play"]:
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
            "unsignedPlayApk",
            "unsignedApplianceApk",
            "unsignedPlayAab",
            "sourceTarGz",
            "sourceZip",
            "nativeSourceTarGz",
            "sdkSourceTarGz",
        },
    )


def validate_signed_manifest(manifest: dict[str, Any]) -> None:
    if manifest.get("schemaVersion") != SCHEMA_VERSION or manifest.get("stage") != "signed":
        raise ValueError("not a supported signed release manifest")
    if manifest.get("applicationId") != APPLICATION_IDS["play"]:
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
            "signedPlayApk",
            "signedApplianceApk",
            "signedPlayAab",
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
    signed_play_apk: Path,
    signed_appliance_apk: Path,
    signed_play_aab: Path,
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
        "signedPlayApk": _binary_artifact(signed_play_apk, "play"),
        "signedApplianceApk": _binary_artifact(signed_appliance_apk, "appliance"),
        "signedPlayAab": _binary_artifact(signed_play_aab, "play"),
        "sourceTarGz": _artifact(source_tar_gz),
        "sourceZip": _artifact(source_zip),
        "nativeSourceTarGz": _artifact(native_source_tar_gz),
        "sdkSourceTarGz": _artifact(sdk_source_tar_gz),
    }
    _validate_artifacts(
        artifacts,
        {
            "signedPlayApk",
            "signedApplianceApk",
            "signedPlayAab",
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


def _binary_artifact(path: Path, flavor: str) -> dict[str, str]:
    return {**_artifact(path), "flavor": flavor, "applicationId": APPLICATION_IDS[flavor]}


def _write_manifest(path: Path, manifest: dict[str, Any]) -> None:
    path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def _load_manifest(path: Path) -> dict[str, Any]:
    loaded = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=_unique_fields)
    if not isinstance(loaded, dict):
        raise ValueError("release manifest must contain a JSON object")
    return loaded


def _unique_fields(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate manifest field: {key}")
        result[key] = value
    return result


def _verify_artifact_files(manifest: dict[str, Any], directory: Path) -> None:
    for artifact in manifest["artifacts"].values():
        path = directory / artifact["file"]
        if path.is_symlink() or not path.is_file() or sha256_file(path) != artifact["sha256"]:
            raise ValueError(f"artifact checksum does not match: {artifact['file']}")


def bundletool_command() -> list[str]:
    sdk = Path(os.environ.get("ANDROID_HOME", "/opt/android-sdk"))
    standalone = sorted(sdk.glob(f"**/bundletool-all-{BUNDLETOOL_VERSION}.jar"))
    if standalone:
        return ["java", "-jar", str(standalone[-1])]
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
    # AGP caches the library distribution, not the executable all-in-one JAR.
    # Supply its runtime dependencies from the same local Maven cache; never download a tool.
    coordinates = {
        "com.android.tools.build": ("bundletool", "aapt2-proto"),
        "com.google.protobuf": ("protobuf-java", "protobuf-java-util"),
        "com.google.guava": ("guava", "failureaccess"),
        "com.google.dagger": ("dagger",),
        "javax.inject": ("javax.inject",),
        "org.bitbucket.b_c": ("jose4j",),
        "org.slf4j": ("slf4j-api",),
        "com.google.code.gson": ("gson",),
    }
    jars = []
    for group, names in coordinates.items():
        for name in names:
            candidates = sorted(
                (cache / group / name).glob(f"{BUNDLETOOL_VERSION if name == 'bundletool' else '*'}/*/*.jar"),
                key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.parent.parent.name)),
            )
            candidates = [p for p in candidates if not p.name.endswith(("-sources.jar", "-javadoc.jar"))]
            if candidates:
                jars.append(candidates[-1])
            else:
                raise ValueError(f"bundletool {BUNDLETOOL_VERSION} requires local dependency {group}:{name}; install the standalone JAR or populate the Gradle cache")
    return ["java", "-cp", os.pathsep.join(map(str, jars)), "com.android.tools.build.bundletool.BundleToolMain"]


def assert_unsigned_apk(apk: Path) -> None:
    try:
        with zipfile.ZipFile(apk) as archive:
            if archive.testzip() is not None:
                raise zipfile.BadZipFile("entry checksum mismatch")
            names = archive.namelist()
            if len(names) != len(set(names)):
                raise zipfile.BadZipFile("duplicate ZIP entries")
        with apk.open("rb") as source:
            size = source.seek(0, os.SEEK_END)
            source.seek(max(0, size - 65557))
            tail = source.read()
            end = tail.rfind(b"PK\x05\x06")
            if end < 0 or len(tail) - end < 22:
                raise zipfile.BadZipFile("missing end of central directory")
            _, disk, cd_disk, count, total, cd_size, cd_offset, comment = struct.unpack_from("<4s4H2IH", tail, end)
            if (disk or cd_disk or count != total or total == 65535
                    or end + 22 + comment != len(tail)
                    or cd_offset + cd_size != size - len(tail) + end):
                raise zipfile.BadZipFile("invalid or unsupported central directory")
            source.seek(max(0, cd_offset - 16))
            signing_block = source.read(16) == b"APK Sig Block 42"
    except (zipfile.BadZipFile, EOFError, struct.error, zlib.error) as error:
        raise ValueError(f"malformed APK ZIP: {error}") from error
    if any(re.fullmatch(r"META-INF/[^/]+\.(SF|RSA|DSA|EC)", name, re.I) for name in names):
        raise ValueError("APK contains v1 signing material")
    if signing_block:
        raise ValueError("APK contains an APK Signing Block")


def validate_entry_points(manifest: ET.Element, flavor: str) -> None:
    name = "{http://schemas.android.com/apk/res/android}name"
    home = any(category.get(name) == "android.intent.category.HOME"
               for tag in ("activity", "activity-alias")
               for category in manifest.findall(f"./application/{tag}/intent-filter/category"))
    service = any(node.get(name, "").split(".")[-1] == "ApplianceEntryAccessibilityService"
                  for node in manifest.findall("./application/service"))
    expected = flavor == "appliance"
    if home != expected or service != expected:
        raise ValueError(f"{flavor} entry-point split is invalid: HOME and ApplianceEntryAccessibilityService must both be {'present' if expected else 'absent'}")


def validate_apk_entry_points(apk: Path, flavor: str, aapt: Path) -> None:
    tree = subprocess.run([str(aapt), "dump", "xmltree", str(apk), "AndroidManifest.xml"],
                          check=True, capture_output=True, text=True).stdout
    # aapt's indentation encodes the binary XML hierarchy. Only android:name is needed.
    stack: list[tuple[int, ET.Element]] = []
    root = None
    for line in tree.splitlines():
        element = re.match(r"(\s*)E: ([\w-]+)", line)
        if element:
            depth = len(element[1])
            while stack and stack[-1][0] >= depth:
                stack.pop()
            node = ET.Element(element[2])
            if stack:
                stack[-1][1].append(node)
            elif root is None:
                root = node
            else:
                raise ValueError("invalid APK manifest tree")
            stack.append((depth, node))
        attribute = re.match(r'\s*A: android:name\([^)]*\)="([^"]+)"', line)
        if attribute and stack:
            stack[-1][1].set("{http://schemas.android.com/apk/res/android}name", attribute[1])
    if root is None or root.tag != "manifest" or root.find("application") is None:
        raise ValueError("invalid APK manifest tree")
    validate_entry_points(root, flavor)


def validate_aab(aab: Path, play_apk: Path, version_code: int, version_name: str, *, unsigned: bool = False) -> None:
    with zipfile.ZipFile(aab) as bundle, zipfile.ZipFile(play_apk) as apk:
        names = bundle.namelist()
        if len(names) != len(set(names)):
            raise ValueError("AAB contains duplicate ZIP entries")
        if unsigned and any(re.fullmatch(r"META-INF/[^/]+\.(SF|RSA|DSA|EC)", name, re.I) for name in names):
            raise ValueError("incoming AAB is already signed")
        libraries = {name.removeprefix("base/"): bundle.read(name) for name in names if name.startswith("base/lib/") and name.endswith(".so")}
        apk_libraries = {name: apk.read(name) for name in apk.namelist() if name.startswith("lib/") and name.endswith(".so")}
        if not libraries or libraries != apk_libraries:
            raise ValueError("AAB native payload does not match the validated Play APK")
        if not {"arm64-v8a", "armeabi-v7a"} <= {name.split("/")[1] for name in libraries}:
            raise ValueError("AAB must include both 32-bit and 64-bit ARM native libraries")
        native_checks = runpy.run_path(str(Path(__file__).with_name("check-native-libs")))
        for name, data in libraries.items():
            if name.split("/")[1] in {"arm64-v8a", "x86_64"}:
                if any(alignment < 16384 for alignment in native_checks["load_alignments"](data, name)):
                    raise ValueError(f"AAB native ELF load alignment is below 16 KB: {name}")
    command = bundletool_command()
    config = json.loads(subprocess.run(command + ["dump", "config", f"--bundle={aab}"], check=True, capture_output=True, text=True).stdout)
    native = config.get("optimizations", {}).get("uncompressNativeLibraries", {})
    if native.get("enabled") is not True or native.get("alignment") != "PAGE_ALIGNMENT_16K":
        raise ValueError("AAB must request uncompressed, 16 KB aligned native libraries in generated APKs")
    xml = subprocess.run(command + ["dump", "manifest", f"--bundle={aab}", "--module=base"], check=True, capture_output=True, text=True).stdout
    manifest = ET.fromstring(xml)
    validate_entry_points(manifest, "play")
    android = "{http://schemas.android.com/apk/res/android}"
    if (manifest.get("package"), manifest.get(android + "versionCode"), manifest.get(android + "versionName")) != (APPLICATION_IDS["play"], str(version_code), version_name):
        raise ValueError("AAB identity does not match the Play release")


def verify_apk_certificate(report: str) -> str:
    fingerprints = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", report)
    if len(fingerprints) != 1 or normalize_fingerprint(fingerprints[0]) != CANONICAL_CERTIFICATE_SHA256:
        raise ValueError("APK signing certificate is not canonical")
    return CANONICAL_CERTIFICATE_SHA256


def verify_aab_signature(aab: Path) -> str:
    locale = ["-J-Duser.language=en", "-J-Duser.country=US"]
    verification = subprocess.run(["jarsigner", *locale, "-verify", "-strict", "-verbose", "-certs", str(aab)], capture_output=True, text=True)
    # A pinned self-signed certificate is intentionally not a public CA chain (bit 4).
    # Reject all other strict errors, including unsigned entries (16) and signer errors (32).
    if verification.returncode not in (0, 4) or "jar verified" not in verification.stdout.lower():
        raise ValueError("AAB JAR signature verification failed")
    report = (verification.stdout + (verification.stderr or "")).lower()
    if re.search(r"expired|not[- ]yet[- ]valid|not valid until", report):
        raise ValueError("AAB signing certificate is expired or not yet valid")
    if verification.returncode == 4 and "self-signed" not in report:
        raise ValueError("AAB JAR signature error is not a self-signed certificate warning")
    certificates = subprocess.run(["keytool", *locale, "-printcert", "-jarfile", str(aab)], check=True, capture_output=True, text=True).stdout
    fingerprints = re.findall(r"SHA256:\s*([0-9A-Fa-f:]+)", certificates)
    if len(fingerprints) != 1 or normalize_fingerprint(fingerprints[0]) != CANONICAL_CERTIFICATE_SHA256:
        raise ValueError("AAB signing certificate is not canonical")
    return verification.stdout + certificates


def _create_unsigned(args: argparse.Namespace) -> None:
    artifacts = {
        "unsignedPlayApk": _binary_artifact(args.unsigned_play_apk, "play"),
        "unsignedApplianceApk": _binary_artifact(args.unsigned_appliance_apk, "appliance"),
        "unsignedPlayAab": _binary_artifact(args.unsigned_play_aab, "play"),
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
            signed_play_apk=args.signed_play_apk,
            signed_appliance_apk=args.signed_appliance_apk,
            signed_play_aab=args.signed_play_aab,
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
    create_unsigned.add_argument("--unsigned-play-apk", required=True, type=Path)
    create_unsigned.add_argument("--unsigned-appliance-apk", required=True, type=Path)
    create_unsigned.add_argument("--unsigned-play-aab", required=True, type=Path)
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
    create_signed.add_argument("--signed-play-apk", required=True, type=Path)
    create_signed.add_argument("--signed-appliance-apk", required=True, type=Path)
    create_signed.add_argument("--signed-play-aab", required=True, type=Path)
    create_signed.add_argument("--source-tar-gz", required=True, type=Path)
    create_signed.add_argument("--source-zip", required=True, type=Path)
    create_signed.add_argument("--native-source-tar-gz", required=True, type=Path)
    create_signed.add_argument("--sdk-source-tar-gz", required=True, type=Path)
    create_signed.add_argument("--certificate-sha256", required=True)
    create_signed.add_argument("--output", required=True, type=Path)
    create_signed.set_defaults(handler=_create_signed)

    validate_bundle = subparsers.add_parser("validate-aab")
    validate_bundle.add_argument("--aab", required=True, type=Path)
    validate_bundle.add_argument("--play-apk", required=True, type=Path)
    validate_bundle.add_argument("--version-code", required=True, type=int)
    validate_bundle.add_argument("--version-name", required=True)
    validate_bundle.add_argument("--unsigned", action="store_true")
    validate_bundle.set_defaults(handler=lambda args: validate_aab(args.aab, args.play_apk, args.version_code, args.version_name, unsigned=args.unsigned))

    verify_bundle = subparsers.add_parser("verify-aab-signature")
    verify_bundle.add_argument("aab", type=Path)
    verify_bundle.set_defaults(handler=lambda args: print(verify_aab_signature(args.aab)))

    verify_apk = subparsers.add_parser("verify-apk-certificate")
    verify_apk.add_argument("report", type=Path)
    verify_apk.set_defaults(handler=lambda args: print(verify_apk_certificate(args.report.read_text(encoding="utf-8"))))
    unsigned_apk = subparsers.add_parser("assert-unsigned-apk")
    unsigned_apk.add_argument("apk", type=Path)
    unsigned_apk.set_defaults(handler=lambda args: assert_unsigned_apk(args.apk))
    entry_points = subparsers.add_parser("validate-apk-entry-points")
    entry_points.add_argument("apk", type=Path)
    entry_points.add_argument("--flavor", choices=APPLICATION_IDS, required=True)
    entry_points.add_argument("--aapt", type=Path, required=True)
    entry_points.set_defaults(handler=lambda args: validate_apk_entry_points(args.apk, args.flavor, args.aapt))
    return parser


def main() -> int:
    args = _parser().parse_args()
    try:
        args.handler(args)
    except (OSError, ValueError, json.JSONDecodeError, subprocess.CalledProcessError, zipfile.BadZipFile, ET.ParseError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
