from collections import deque
from hashlib import sha256
from pathlib import Path
import json

from PIL import Image

ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parents[2]
SOURCE = ROOT / "user-supplied"
PROCESSED = ROOT / "processed"
ATLAS = PROJECT_ROOT / "Marvel/app/src/main/res/drawable-nodpi/survivor_idle_atlas_80x24.png"
PREVIEW = ROOT / "user-supplied-sprites-preview.png"
MANIFEST = PROJECT_ROOT / "Marvel/app/src/main/assets/room/room_asset_manifest.json"

# User order: Captain America, Iron Man, Thor, Hawkeye, Black Widow.
USER_ORDER = [
    ("captain_america", "01-capitao-america.png"),
    ("iron_man", "02-homem-de-ferro.png"),
    ("thor", "03-thor.png"),
    ("hawkeye", "04-gaviao-arqueiro.png"),
    ("black_widow", "05-viuva-negra.png"),
]
# Existing runtime contract from room_walkmap.json and RoomCanvasView.
RUNTIME_ORDER = ["iron_man", "captain_america", "thor", "black_widow", "hawkeye"]


def background_white(pixel):
    r, g, b = pixel
    return min(r, g, b) >= 238 and max(r, g, b) - min(r, g, b) <= 24


def remove_edge_connected_white(image):
    rgb = image.convert("RGB")
    width, height = rgb.size
    visited = bytearray(width * height)
    queue = deque()

    def enqueue(x, y):
        idx = y * width + x
        if not visited[idx] and background_white(rgb.getpixel((x, y))):
            visited[idx] = 1
            queue.append((x, y))

    for x in range(width):
        enqueue(x, 0)
        enqueue(x, height - 1)
    for y in range(height):
        enqueue(0, y)
        enqueue(width - 1, y)

    while queue:
        x, y = queue.popleft()
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < width and 0 <= ny < height:
                enqueue(nx, ny)

    rgba = rgb.convert("RGBA")
    alpha = Image.new("L", (width, height), 255)
    alpha.putdata([0 if flag else 255 for flag in visited])
    rgba.putalpha(alpha)
    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError("No sprite pixels remain after background removal")
    return rgba.crop(bounds), bounds


def to_runtime_frame(sprite):
    width, height = sprite.size
    scale = min(16 / width, 24 / height)
    out_size = (max(1, round(width * scale)), max(1, round(height * scale)))
    resized = sprite.resize(out_size, Image.Resampling.NEAREST)
    frame = Image.new("RGBA", (16, 24), (0, 0, 0, 0))
    x = (16 - resized.width) // 2
    y = 24 - resized.height
    frame.alpha_composite(resized, (x, y))
    return frame


def file_hash(path):
    return sha256(path.read_bytes()).hexdigest()


def main():
    PROCESSED.mkdir(parents=True, exist_ok=True)
    frames = {}
    source_records = []
    processed_records = []
    for character_id, filename in USER_ORDER:
        source_path = SOURCE / filename
        original = Image.open(source_path).convert("RGB")
        sprite, crop = remove_edge_connected_white(original)
        frame = to_runtime_frame(sprite)
        output_path = PROCESSED / f"{character_id}_idle_16x24.png"
        frame.save(output_path, format="PNG", optimize=True)
        frames[character_id] = frame
        source_records.append({
            "sourceType": "user-provided attachment",
            "sourcePath": f"Marvel/artwork/hero-sprites/user-supplied/{filename}",
            "sourceDimensionsPx": list(original.size),
            "sourceFormat": Image.open(source_path).format,
            "sourceMode": Image.open(source_path).mode,
            "sourceSha256": file_hash(source_path),
            "usedForCharacter": character_id,
            "processedOutput": f"Marvel/artwork/hero-sprites/processed/{output_path.name}",
            "backgroundRemoval": "edge-connected near-white only; enclosed white costume pixels preserved",
            "cropBoundsPx": list(crop),
        })
        processed_records.append({
            "path": f"Marvel/artwork/hero-sprites/processed/{output_path.name}",
            "characterId": character_id,
            "dimensionsPx": [16, 24],
            "format": "PNG",
            "mode": "RGBA",
            "alpha": "transparent outside sprite",
            "sha256": file_hash(output_path),
        })

    atlas = Image.new("RGBA", (80, 24), (0, 0, 0, 0))
    for index, character_id in enumerate(RUNTIME_ORDER):
        atlas.alpha_composite(frames[character_id], (index * 16, 0))
    atlas.save(ATLAS, format="PNG", optimize=True)

    # Review preview in the order the user supplied, enlarged without smoothing.
    scale = 10
    bg = Image.new("RGBA", (5 * 16 * scale + 6 * 12, 24 * scale + 32), (23, 37, 48, 255))
    for index, (character_id, _) in enumerate(USER_ORDER):
        big = frames[character_id].resize((160, 240), Image.Resampling.NEAREST)
        bg.alpha_composite(big, (12 + index * (160 + 12), 12))
    bg.convert("RGB").save(PREVIEW, format="PNG", optimize=True)

    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    provenance = manifest.setdefault("sourceProvenance", {})
    provenance["externalReferenceImagesUsed"] = True
    provenance["userProvidedCharacterSpritesUsed"] = True
    provenance["officialMarvelOrPokemonArtworkOrLogosUsed"] = None
    provenance["licenseAndRights"] = (
        "Hero sprite source images were supplied by the user. Their underlying license/provenance "
        "was not independently verified; confirm rights before distributing the APK or video. "
        "Other generated assets retain their individual provenance records."
    )
    sources = provenance.setdefault("sources", [])
    for item in sources:
        if item.get("processedOutput") == "survivor_idle_atlas_80x24.png":
            item["status"] = "superseded by user-provided sprite references"
    new_source_paths = {item["sourcePath"] for item in source_records}
    sources[:] = [item for item in sources if item.get("sourcePath") not in new_source_paths]
    sources.extend(source_records)
    processing_step = (
        "user sprite integration: remove only edge-connected near-white background, crop silhouette, "
        "fit to 16x24 with nearest-neighbor, bottom-align, assemble runtime atlas"
    )
    if processing_step not in manifest["processing"]["steps"]:
        manifest["processing"]["steps"].append(processing_step)
    for item in manifest.get("layerInventory", []):
        if item.get("asset") == "survivor_idle_atlas_80x24.png":
            item["note"] = "Five user-provided hero sprites, runtime order iron_man, captain_america, thor, black_widow, hawkeye."

    atlas_record = next(
        item for item in manifest["assets"]
        if item.get("path", "").endswith("survivor_idle_atlas_80x24.png")
    )
    atlas_record.update({
        "dimensionsPx": [80, 24],
        "format": "PNG",
        "mode": "RGBA",
        "alpha": "transparent outside sprites",
        "details": "5 horizontal 16x24 idle frames; runtime order iron_man, captain_america, thor, black_widow, hawkeye",
        "frameSizePx": [16, 24],
        "frameOrder": RUNTIME_ORDER,
        "anchorPx": [8, 23],
        "sourceType": "user-provided character sprites",
        "sha256": file_hash(ATLAS),
    })
    processed_paths = {item["path"] for item in processed_records}
    manifest["assets"] = [item for item in manifest["assets"] if item.get("path") not in processed_paths]
    manifest["assets"].extend(processed_records)
    review_artifacts = manifest.setdefault("reviewArtifacts", [])
    review_artifacts[:] = [
        item for item in review_artifacts
        if item.get("path") != "Marvel/artwork/hero-sprites/user-supplied-sprites-preview.png"
    ]
    review_artifacts.append({
        "path": "Marvel/artwork/hero-sprites/user-supplied-sprites-preview.png",
        "dimensionsPx": list(bg.size),
        "format": "PNG",
        "details": "User-order preview, enlarged by nearest-neighbor; excluded from APK.",
        "frameOrder": [character_id for character_id, _ in USER_ORDER],
    })
    MANIFEST.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("runtime atlas:", ATLAS, atlas.size, file_hash(ATLAS))
    print("preview:", PREVIEW, bg.size)
    for record in source_records:
        print(record["usedForCharacter"], record["sourceDimensionsPx"], record["cropBoundsPx"], record["sourceSha256"])


if __name__ == "__main__":
    main()
