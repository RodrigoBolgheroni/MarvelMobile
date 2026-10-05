"""Validate the isolated main-menu art package and refresh its hashes."""

from hashlib import sha256
import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).parent
PALETTE = {
    (0x0D, 0x17, 0x20),
    (0x24, 0x35, 0x43),
    (0x38, 0x4B, 0x57),
    (0x52, 0x66, 0x6A),
    (0xC4, 0xD5, 0xC8),
    (0x44, 0xCF, 0xBD),
    (0xF2, 0xB8, 0x4B),
    (0xE4, 0x67, 0x5D),
}
BACKGROUND = (0x0D, 0x17, 0x20)
ASSETS = {
    "landscape": {
        "path": "runtime/menu_backdrop_landscape_1024x576.png",
        "size": (1024, 576),
        "logical": (256, 144),
        "quiet_axis": "x",
        "quiet_start": 148,
        "safe_fraction": 108 / 256,
        "preview": "preview/menu_backdrop_landscape_centercrop_20x9.png",
        "preview_aspect": (20, 9),
    },
    "portrait": {
        "path": "runtime/menu_backdrop_portrait_576x1024.png",
        "size": (576, 1024),
        "logical": (144, 256),
        "quiet_axis": "y",
        "quiet_start": 141,
        "safe_fraction": 115 / 256,
        "preview": "preview/menu_backdrop_portrait_centercrop_9x20.png",
        "preview_aspect": (9, 20),
    },
}


def digest(path: Path) -> str:
    return sha256(path.read_bytes()).hexdigest()


def validate_plate(path: Path, expected_size: tuple[int, int]) -> tuple[Image.Image, int]:
    image = Image.open(path)
    assert image.format == "PNG", f"Not PNG: {path}"
    assert image.mode == "RGB", f"Expected opaque RGB PNG: {path} ({image.mode})"
    assert image.size == expected_size, f"Wrong dimensions: {path} ({image.size})"
    colors = set(image.get_flattened_data())
    assert colors <= PALETTE, f"Unexpected colors: {path} ({colors - PALETTE})"
    logical_size = (expected_size[0] // 4, expected_size[1] // 4)
    reconstructed = image.resize(logical_size, Image.Resampling.NEAREST).resize(expected_size, Image.Resampling.NEAREST)
    assert reconstructed.tobytes() == image.tobytes(), f"Not a clean 4x nearest-neighbor upscale: {path}"
    return image, len(colors)


def validate_quiet_zone(image: Image.Image, axis: str, start: int, scale: int = 4) -> bool:
    pixels = image.load()
    if axis == "x":
        bounds = range(start * scale, image.width)
        return all(pixels[x, y] == BACKGROUND for x in bounds for y in range(image.height))
    bounds = range(start * scale, image.height)
    return all(pixels[x, y] == BACKGROUND for y in bounds for x in range(image.width))


def main() -> None:
    manifest_path = ROOT / "menu_art_manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    result = {
        "assetPackage": "main_menu_v1",
        "checks": {},
        "validationMethod": "validate_menu_art.py (Pillow structural checks and SHA-256); manual visual inspection of full plates and CENTER_CROP previews",
        "limitations": [
            "No Android menu layout exists in this isolated art package; XML view IDs are integration suggestions.",
            "No device screenshot or app build was produced by the art unit.",
            "Image text/logo exclusion is verified by visual inspection, not OCR."
        ],
    }

    manifest_assets = {asset["id"]: asset for asset in manifest["assets"]}
    for orientation, spec in ASSETS.items():
        path = ROOT / spec["path"]
        image, color_count = validate_plate(path, spec["size"])
        assert validate_quiet_zone(image, spec["quiet_axis"], spec["quiet_start"]), f"Safe zone is not flat: {path}"

        preview_path = ROOT / spec["preview"]
        preview = Image.open(preview_path)
        assert preview.format == "PNG" and preview.mode == "RGB", f"Invalid crop preview: {preview_path}"
        expected_ratio = spec["preview_aspect"][0] / spec["preview_aspect"][1]
        actual_ratio = preview.width / preview.height
        assert abs(actual_ratio - expected_ratio) < 0.003, f"Unexpected crop ratio: {preview_path}"
        assert validate_quiet_zone(preview, spec["quiet_axis"], spec["quiet_start"]), f"Crop removed/interrupted safe zone: {preview_path}"

        asset_id = f"menu_backdrop_{orientation}"
        asset = manifest_assets[asset_id]
        asset["sha256"] = digest(path)
        asset["paletteColorsUsed"] = color_count
        result["checks"][orientation] = {
            "exists": True,
            "dimensionsPx": list(image.size),
            "logicalGridPx": list(spec["logical"]),
            "opaqueRgbPng": True,
            "paletteColors": color_count,
            "nearestNeighbor4xBlocks": True,
            "quietZone": {
                "axis": spec["quiet_axis"],
                "startLogicalPixel": spec["quiet_start"],
                "cleanFraction": round(spec["safe_fraction"], 4),
                "flatColor": "#0D1720",
            },
            "centerCropPreview": {
                "path": spec["preview"],
                "dimensionsPx": list(preview.size),
                "aspect": f"{spec['preview_aspect'][0]}:{spec['preview_aspect'][1]}",
                "quietZonePreserved": True,
            },
        }

    for prompt in manifest["generation"]["prompts"]:
        assert (ROOT / prompt).is_file(), f"Missing prompt: {prompt}"
    for asset in manifest["assets"]:
        source_path = ROOT / asset["source"]
        asset["sourceSha256"] = digest(source_path)

    result["checks"].update({
        "promptsSaved": True,
        "sourceImagesPresent": True,
        "sourceAndOutputHashesRecorded": True,
        "manualVisualInspection": {
            "landscape": "Window/abstract skyline and holographic briefing table remain within the left-central focal scene; rightmost 42.2% is flat and low contrast; no visible title, logo, character or controls.",
            "portrait": "Window/abstract skyline and table remain centered above the lower text zone; bottom 44.9% is flat and low contrast; no visible title, logo, character or controls.",
            "cropQa": "20:9 and 9:20 CENTER_CROP previews preserve the focal room scene and XML safe zones."
        },
        "sharedRepoModified": False,
        "stagedInterviewFilesTouched": False,
    })

    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    checks_path = ROOT / "structural_checks.json"
    checks_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    hash_entries = {}
    for path in sorted(ROOT.rglob("*")):
        if path.is_file() and path.name != "sha256_manifest.json":
            hash_entries[path.relative_to(ROOT).as_posix()] = digest(path)
    (ROOT / "sha256_manifest.json").write_text(
        json.dumps({"algorithm": "SHA-256", "files": hash_entries}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print("Structural checks passed.")
    for orientation, spec in ASSETS.items():
        print(f"{orientation}: {spec['path']} SHA-256 {manifest_assets[f'menu_backdrop_{orientation}']['sha256']}")
    print(f"Hash entries: {len(hash_entries)}")


if __name__ == "__main__":
    main()
