from __future__ import annotations

import copy
import runpy
import tempfile
import unittest
import zipfile
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
CHECKER = runpy.run_path(
    str(ROOT / "tools/check-native-libs"),
    run_name="native_checker_test",
)


def provenance_leaf_paths(value: object, path: tuple[object, ...] = ()) -> list[tuple[object, ...]]:
    if isinstance(value, dict):
        return [
            leaf
            for key, child in value.items()
            for leaf in provenance_leaf_paths(child, path + (key,))
        ]
    if isinstance(value, list):
        return [
            leaf
            for index, child in enumerate(value)
            for leaf in provenance_leaf_paths(child, path + (index,))
        ]
    return [path]


def value_at_path(value: object, path: tuple[object, ...]) -> Any:
    current: Any = value
    for segment in path:
        current = current[segment]
    return current


def parent_at_path(value: object, path: tuple[object, ...]) -> tuple[Any, object]:
    parent: Any = value
    for segment in path[:-1]:
        parent = parent[segment]
    return parent, path[-1]


def mutated_scalar(value: object) -> object:
    if value is None:
        return "unexpected"
    if isinstance(value, bool):
        return not value
    if isinstance(value, int):
        return value + 1
    if isinstance(value, str):
        return f"{value}-mutated"
    raise AssertionError(f"unsupported provenance leaf type: {type(value).__name__}")


class NativePublicationEvidenceTest(unittest.TestCase):
    def test_complete_sources_archive_is_independently_pinned(self) -> None:
        self.assertEqual(
            CHECKER["EXPECTED_SOURCES_SHA256"],
            "d34915cdfd1fa2d1a2de82c642a7656227e5c3458a45fe353ffc3cdcc335fd02",
        )

    def test_every_pinned_source_document_mutation_and_removal_is_rejected(self) -> None:
        version_directory = CHECKER["VERSION_DIRECTORY"]
        sources = sorted(version_directory.glob("*-sources.jar"))
        self.assertEqual(len(sources), 1, sources)
        source = sources[0]
        self.assertEqual(
            CHECKER["sha256"](source),
            CHECKER["EXPECTED_SOURCES_SHA256"],
        )

        expected_entries = CHECKER["EXPECTED_SOURCE_ENTRY_SHA256"]
        validate = CHECKER["validate_published_sources"]
        for entry in expected_entries:
            for operation in ("mutate", "remove"):
                with self.subTest(entry=entry, operation=operation):
                    with tempfile.TemporaryDirectory() as temporary_directory:
                        candidate = Path(temporary_directory) / "decoder-sources.jar"
                        with zipfile.ZipFile(source) as input_archive:
                            with zipfile.ZipFile(candidate, "w") as output_archive:
                                for info in input_archive.infolist():
                                    data = input_archive.read(info.filename)
                                    if info.filename == entry:
                                        if operation == "remove":
                                            continue
                                        data += b"\nmutation"
                                    output_archive.writestr(info, data)

                        errors: list[str] = []
                        validate(candidate, True, errors)
                        self.assertTrue(
                            errors,
                            f"accepted {operation} operation for pinned source entry {entry}",
                        )
        self.assertEqual(
            CHECKER["EXPECTED_SOURCE_ENTRY_SHA256"],
            {
                "META-INF/LICENSE":
                    "e1c0ad728983d8a57335e52cf1064f1affd1d454173d8cebd3ed8b4a72b48704",
                "META-INF/NOTICE.md":
                    "bfbbb125728d0c8a1716dc6f6e1ca56d1e26154e0747b716ce4a6e86f4636258",
                "META-INF/tvheadend-player-sdk/README.md":
                    "3e92b714762b06e07d3d71205fcaa7c1b135d88005b0c777ce05ed4a85aef0f6",
                "META-INF/tvheadend-player-sdk/native-dependencies.json":
                    "80fd549740337c924955b518f73a03d77a10c6df36b62f8549d71ae0a3b1d12d",
                "META-INF/tvheadend-player-sdk/tools/build-media3-ffmpeg":
                    "f4215fd5e53b3012fad48c11d7a90394d924f290677924372f40ddc2aa2eadd5",
                "META-INF/tvheadend-player-sdk/patches/media3-ffmpeg-disable-iconv.patch":
                    "9ac6ff30b7c2dd19ed5e1678999b8fa920afab68226ebfbbad4f5e1eb4bd4f0d",
                "META-INF/tvheadend-player-sdk/licenses/media3-APACHE-2.0.txt":
                    "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30",
                "META-INF/tvheadend-player-sdk/licenses/ffmpeg-LGPL-2.1.txt":
                    "b634ab5640e258563c536e658cad87080553df6f34f62269a21d554844e58bfe",
                "META-INF/tvheadend-player-sdk/licenses/ffmpeg-LICENSE.md":
                    "cb48bf09a11f5fb576cddb0431c8f5ed0a60157a9ec942adffc13907cbe083f2",
            },
        )

    def test_every_material_provenance_mutation_is_rejected(self) -> None:
        expected = CHECKER["EXPECTED_PROVENANCE"]
        validate = CHECKER["validate_provenance"]
        errors: list[str] = []
        validate(copy.deepcopy(expected), True, errors)
        self.assertEqual(errors, [])

        leaf_paths = provenance_leaf_paths(expected)
        self.assertGreaterEqual(len(leaf_paths), 30)
        for path in leaf_paths:
            label = ".".join(str(segment) for segment in path)
            with self.subTest(label=label, operation="mutate"):
                candidate = copy.deepcopy(expected)
                parent, segment = parent_at_path(candidate, path)
                parent[segment] = mutated_scalar(value_at_path(candidate, path))
                mutation_errors: list[str] = []
                validate(candidate, True, mutation_errors)
                self.assertTrue(mutation_errors, f"accepted mutated {label}")

            with self.subTest(label=label, operation="remove"):
                candidate = copy.deepcopy(expected)
                parent, segment = parent_at_path(candidate, path)
                del parent[segment]
                removal_errors: list[str] = []
                validate(candidate, True, removal_errors)
                self.assertTrue(removal_errors, f"accepted removed {label}")


if __name__ == "__main__":
    unittest.main()
