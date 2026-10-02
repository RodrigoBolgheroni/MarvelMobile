"""Compose the original two-room art with the generated archives room.

Requires Pillow. Geometry and navigation are authored together so the two
passages and furniture collision cells remain aligned with the final bitmap.
"""

import json
import hashlib
from collections import deque
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "Marvel/app/src/main/res/drawable-nodpi"
ASSETS = ROOT / "Marvel/app/src/main/assets/room"
ARCHIVES_RAW = ROOT / "REFERENCIAS/room_archives_generated.png"
WIDTH, HEIGHT, CELL = 864, 288, 8
COLUMNS, ROWS = WIDTH // CELL, HEIGHT // CELL


def compose_map():
    original = Image.open(RES / "room_map_288x144.png").convert("RGB")
    archive_raw = Image.open(ARCHIVES_RAW).convert("RGB")
    archive = archive_raw.resize((144, 144), Image.Resampling.NEAREST)
    archive.save(RES / "room_archives_144x144.png")

    world = Image.new("RGB", (432, 144))
    world.paste(original, (0, 0))
    world.paste(archive, (288, 0))

    # The existing center bulkhead and the two outer walls are opened only at
    # the passages. Reuse neighboring floor pixels instead of painting art.
    for wall, left_floor, right_floor in ((144, 125, 158), (288, 225, 321)):
        for y in range(64, 89):
            for x in range(wall - 12, wall + 13):
                floor_x = left_floor + ((x - wall + 8) % 7) if x < wall else right_floor + ((x - wall) % 7)
                world.putpixel((x, y), world.getpixel((floor_x, y)))

    # One native art pixel becomes exactly 2x2 logical pixels; no stretching.
    runtime = world.resize((WIDTH, HEIGHT), Image.Resampling.NEAREST)
    runtime.save(RES / "room_map_864x288.png")
    runtime.resize((WIDTH * 3, HEIGHT * 3), Image.Resampling.NEAREST).save(
        ASSETS / "room_preview_stage_2592x864.png"
    )


def build_navigation():
    walk = [[False] * COLUMNS for _ in range(ROWS)]

    def open_rect(left, top, right, bottom):
        for row in range(top, bottom + 1):
            for col in range(left, right + 1):
                walk[row][col] = True

    def block_rect(left, top, right, bottom):
        for row in range(top, bottom + 1):
            for col in range(left, right + 1):
                walk[row][col] = False

    # Room interiors: the walls and exterior stay solid by default.
    open_rect(5, 10, 34, 29)
    open_rect(39, 10, 70, 29)
    open_rect(76, 10, 103, 29)
    # Two visible 48px-high openings centered on the common wall.
    open_rect(35, 16, 38, 21)
    open_rect(71, 16, 75, 21)

    # Briefing: sofa, central holo-table, and six surrounding seats.
    block_rect(7, 15, 10, 24)
    block_rect(13, 15, 29, 22)
    for left, right in ((15, 17), (20, 22), (25, 27)):
        block_rect(left, 12, right, 14)
        block_rect(left, 23, right, 25)
    # Laboratory: consoles, reactor, lower workbench and server banks.
    block_rect(40, 10, 48, 15)
    block_rect(54, 10, 69, 15)
    block_rect(50, 17, 57, 22)
    block_rect(40, 25, 52, 29)
    block_rect(65, 17, 70, 23)
    block_rect(62, 25, 69, 29)
    # Archives: shelves and desks along walls; central evidence console.
    block_rect(77, 10, 103, 14)
    block_rect(77, 15, 80, 24)
    block_rect(86, 17, 95, 22)
    block_rect(101, 15, 103, 25)
    block_rect(78, 26, 85, 29)
    block_rect(96, 26, 103, 29)

    # Anchor coordinates are world pixels; the case engine uses normalized positions.
    characters = [
        ("iron_man", 90, 212, 10, 26),
        ("captain_america", 245, 205, 29, 25),
        ("thor", 360, 177, 44, 22),
        ("black_widow", 480, 207, 59, 25),
        ("hawkeye", 660, 205, 81, 25),
    ]
    clues = [
        ("clue_access_log", 180, 219, 21, 27),
        ("clue_seal_trace", 510, 188, 62, 23),
        ("clue_reagent_sample", 785, 203, 97, 25),
    ]

    def entity(item, frame):
        identity, x, y, approach_col, approach_row = item
        return {
            "id": identity,
            "x": x / WIDTH,
            "y": y / HEIGHT,
            "col": x // CELL,
            "row": y // CELL,
            "approachCol": approach_col,
            "approachRow": approach_row,
            "atlasFrame": frame,
        }

    spawn_x, spawn_y = 70, 220
    metadata = {
        "schemaVersion": 1,
        "logicalWidth": WIDTH,
        "logicalHeight": HEIGHT,
        "artTileSize": 16,
        "artColumns": WIDTH // 16,
        "artRows": HEIGHT // 16,
        "navCellSize": CELL,
        "navColumns": COLUMNS,
        "navRows": ROWS,
        "navBoundsInclusive": {
            "minCol": 0, "maxCol": COLUMNS - 1,
            "minRow": 0, "maxRow": ROWS - 1,
        },
        "connectivity": "4-neighbor; three rooms, two open passages, eight reachable approaches",
        "walkableRows": ["".join("1" if cell else "0" for cell in row) for row in walk],
        "solidCells": [],
        "spawn": {
            "x": spawn_x / WIDTH, "y": spawn_y / HEIGHT,
            "col": spawn_x // CELL, "row": spawn_y // CELL,
        },
        "characters": [entity(item, index) for index, item in enumerate(characters)],
        "clues": [entity(item, index) for index, item in enumerate(clues)],
        "rooms": [
            {"id": "briefing", "x": [0, 287]},
            {"id": "lab", "x": [288, 575]},
            {"id": "archives", "x": [576, 863]},
        ],
        "passages": [
            {"from": "briefing", "to": "lab", "columns": [35, 38], "rows": [16, 21]},
            {"from": "lab", "to": "archives", "columns": [71, 75], "rows": [16, 21]},
        ],
    }
    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / "room_walkmap.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    mask = Image.new("RGB", (COLUMNS, ROWS))
    mask.putdata([(255, 255, 255) if cell else (0, 0, 0) for row in walk for cell in row])
    mask.save(RES / "room_walkmap_108x36.png")

    origin = (spawn_x // CELL, spawn_y // CELL)
    reached = {origin}
    queue = deque([origin])
    while queue:
        col, row = queue.popleft()
        for dc, dr in ((0, -1), (-1, 0), (1, 0), (0, 1)):
            nc, nr = col + dc, row + dr
            if 0 <= nc < COLUMNS and 0 <= nr < ROWS and walk[nr][nc] and (nc, nr) not in reached:
                reached.add((nc, nr))
                queue.append((nc, nr))
    targets = [(item[3], item[4]) for item in characters + clues]
    assert len(reached) == sum(sum(row) for row in walk), "Walkable floor is disconnected"
    assert all(target in reached for target in targets), "An approach is unreachable"
    assert all(walk[row][col] for col, row in targets), "An approach is blocked"
    assert all(not walk[15][col] and not walk[22][col] for col in range(35, 39))
    assert all(not walk[15][col] and not walk[22][col] for col in range(71, 76))
    assert any(col < 35 for col, _ in reached)
    assert any(39 <= col <= 70 for col, _ in reached)
    assert any(col >= 76 for col, _ in reached)
    print(f"Map {WIDTH}x{HEIGHT}; {len(reached)} connected walk cells; 8 reachable approaches")


def write_manifest():
    files = {
        "originalTwoRooms": RES / "room_map_288x144.png",
        "generatedThirdRoom": ARCHIVES_RAW,
        "thirdRoomNative": RES / "room_archives_144x144.png",
        "runtimeMap": RES / "room_map_864x288.png",
        "walkMask": RES / "room_walkmap_108x36.png",
        "navigation": ASSETS / "room_walkmap.json",
        "preview": ASSETS / "room_preview_stage_2592x864.png",
    }
    manifest = {
        "schemaVersion": 4,
        "version": "original-two-rooms-plus-archives-v1",
        "artSource": "The first two rooms are from room_map_288x144.png; the third is image-generated from that reference. Door openings reuse neighboring floor pixels.",
        "world": {
            "logicalWidth": WIDTH, "logicalHeight": HEIGHT,
            "roomWidth": 288, "roomHeight": 288,
            "cameraWidth": 288, "cameraHeight": 144,
            "navColumns": COLUMNS, "navRows": ROWS,
            "navCellSize": CELL,
        },
        "runtime": "RoomCanvasView draws the baked map and dynamic characters/clues; RoomWalkMap reads room_walkmap.json.",
        "assets": {name: str(path.relative_to(ROOT)).replace('\\', '/') for name, path in files.items()},
        "sha256": {name: hashlib.sha256(path.read_bytes()).hexdigest() for name, path in files.items()},
    }
    (ASSETS / "room_asset_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


if __name__ == "__main__":
    compose_map()
    build_navigation()
    write_manifest()
