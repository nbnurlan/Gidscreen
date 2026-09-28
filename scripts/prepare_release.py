"""Package the built APK and its actual Android version metadata for releases."""
import argparse
import json
import re
import shutil
from pathlib import Path

APPLICATION_ID = "com.aistudio.screenlasso.aiwzqp"
REPOSITORY = "nbnurlan/Gidscreen"


def prepare(metadata_path: Path, destination: Path) -> dict:
    data = json.loads(metadata_path.read_text(encoding="utf-8"))
    if data.get("applicationId") != APPLICATION_ID:
        raise ValueError("Unexpected applicationId")
    elements = data.get("elements", [])
    if len(elements) != 1 or elements[0].get("filters"):
        raise ValueError("Expected one universal APK")
    element = elements[0]
    code = element.get("versionCode")
    name = element.get("versionName", "")
    if type(code) is not int or not 0 < code <= 2_100_000_000:
        raise ValueError("Invalid Android versionCode")
    if not isinstance(name, str) or not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", name):
        raise ValueError("Use a stable three-part versionName, e.g. 1.2.1")
    filename = element.get("outputFile", "")
    if not isinstance(filename, str) or Path(filename).name != filename or not filename.endswith(".apk"):
        raise ValueError("Invalid APK filename")
    apk = metadata_path.parent / filename
    if not apk.is_file() or apk.stat().st_size == 0:
        raise ValueError("Built APK is missing or empty")
    manifest = {
        "applicationId": APPLICATION_ID,
        "versionCode": code,
        "versionName": name,
        "apkUrl": f"https://github.com/{REPOSITORY}/releases/download/v{name}/Gidscreen.apk",
    }
    destination.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(apk, destination / "Gidscreen.apk")
    (destination / "update.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("metadata", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    prepare(args.metadata, args.destination)
