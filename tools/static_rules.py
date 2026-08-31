#!/usr/bin/env python3
import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path


PROBE_CODE_PATTERNS = (
    ("ConnectionProbeUiState", r"\bConnectionProbeUiState\b"),
    ("testConnection", r"\btestConnection\b"),
    ("R.string.test_connection", r"\bR\s*\.\s*string\s*\.\s*test_connection\b"),
)
LIGHT_THEME_IDENTIFIERS = ("LightColors", "darkTheme", "lightColorScheme")


def kotlin_code(text: str) -> str:
    result = ["\n" if character == "\n" else " " for character in text]

    def scan_code(index: int, interpolation: bool = False) -> int:
        brace_depth = 0
        while index < len(text):
            if text.startswith("//", index):
                end = text.find("\n", index)
                index = len(text) if end < 0 else end
            elif text.startswith("/*", index):
                depth = 1
                index += 2
                while index < len(text) and depth:
                    if text.startswith("/*", index):
                        depth += 1
                        index += 2
                    elif text.startswith("*/", index):
                        depth -= 1
                        index += 2
                    else:
                        index += 1
            elif text.startswith('"""', index):
                index = scan_string(index + 3, '"""', escaped=False)
            elif text[index] == '"':
                index = scan_string(index + 1, '"', escaped=True)
            elif text[index] == "'":
                index += 1
                while index < len(text):
                    if text[index] == "\\":
                        index += 2
                    elif text[index] == "'":
                        index += 1
                        break
                    else:
                        index += 1
            elif text[index] == "{":
                result[index] = "{"
                brace_depth += 1
                index += 1
            elif text[index] == "}" and interpolation:
                result[index] = "}"
                if brace_depth == 0:
                    return index + 1
                brace_depth -= 1
                index += 1
            else:
                result[index] = text[index]
                index += 1
        return index

    def scan_string(index: int, delimiter: str, escaped: bool) -> int:
        while index < len(text):
            if text.startswith(delimiter, index):
                return index + len(delimiter)
            if escaped and text[index] == "\\":
                index += 2
            elif text.startswith("${", index):
                result[index] = "$"
                result[index + 1] = "{"
                index = scan_code(index + 2, interpolation=True)
            elif text[index] == "$" and index + 1 < len(text) and \
                    (text[index + 1].isalpha() or text[index + 1] == "_"):
                result[index] = "$"
                index += 1
                while index < len(text) and (text[index].isalnum() or text[index] == "_"):
                    result[index] = text[index]
                    index += 1
            else:
                index += 1
        return index

    scan_code(0)
    return "".join(result)


def line_number(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def find_static_rule_violations(root: Path) -> list[str]:
    violations = []
    source_root = root / "app/src/main"
    for path in sorted(source_root.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        code = kotlin_code(text)
        for label, pattern in PROBE_CODE_PATTERNS:
            for match in re.finditer(pattern, code):
                violations.append(
                    f"NoConnectionProbe: {path.relative_to(root)}:"
                    f"{line_number(code, match.start())} uses {label}"
                )

    for path in sorted((source_root / "res").rglob("*.xml")):
        root_element = ElementTree.parse(path).getroot()
        elements = root_element.iter()
        if any(
            element.attrib.get("name") == "test_connection" or
            "@string/test_connection" in element.attrib.values() or
            (element.text or "").strip() == "@string/test_connection"
            for element in elements
        ):
            violations.append(
                f"NoConnectionProbe: {path.relative_to(root)} declares or uses test_connection"
            )

    theme_path = source_root / "java/at/bernhardberger/tvhplayer/ui/Theme.kt"
    theme_text = theme_path.read_text(encoding="utf-8")
    theme_code = kotlin_code(theme_text)
    for identifier in LIGHT_THEME_IDENTIFIERS:
        for match in re.finditer(rf"\b{re.escape(identifier)}\b", theme_code):
            violations.append(
                f"DarkOnlyTheme: {theme_path.relative_to(root)}:"
                f"{line_number(theme_code, match.start())} uses {identifier}"
            )

    themes_path = source_root / "res/values/themes.xml"
    themes = ElementTree.parse(themes_path).getroot()
    product_theme = next(
        (element for element in themes.findall("style")
         if element.attrib.get("name") == "Theme.TVHeadendPlayer"),
        None,
    )
    expected_parent = "Theme.Material3.Dark.NoActionBar"
    actual_parent = product_theme.attrib.get("parent") if product_theme is not None else None
    if actual_parent != expected_parent:
        violations.append(
            f"DarkOnlyTheme: {themes_path.relative_to(root)} must give Theme.TVHeadendPlayer "
            f"parent {expected_parent}; found {actual_parent}"
        )
    return violations


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    violations = find_static_rule_violations(root)
    if violations:
        for violation in violations:
            print(f"error: {violation}", file=sys.stderr)
        return 1
    print("Static rule checks passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
