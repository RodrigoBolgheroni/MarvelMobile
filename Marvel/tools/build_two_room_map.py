"""Build the two-room map with smaller furniture and matching walk grid.

Requires Pillow. This is deterministic processing of the existing project art;
all collision and entity coordinates are checked against the exported map.
"""

import hashlib
import json
from collections import deque
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "Marvel/app/src/main/res/drawable-nodpi"
ASSETS = ROOT / "Marvel/app/src/main/assets/room"
SMALL_FURNITURE_RAW = ROOT / "REFERENCIAS/room_two_rooms_small_furniture_raw.png"
WIDTH, HEIGHT, CELL = 544, 272, 8
COLUMNS, ROWS = WIDTH // CELL, HEIGHT // CELL


def compose_map():
    raw = Image.open(SMALL_FURNITURE_RAW).convert("RGB")
    assert raw.width == 2 * raw.height, "The edited room must remain exactly 2:1"
    runtime = raw.resize((WIDTH, HEIGHT), Image.Resampling.NEAREST)
    runtime.save(RES / "room_map_544x272.png")
    runtime.resize((WIDTH * 3, HEIGHT * 3), Image.Resampling.NEAREST).save(
        ASSETS / "room_preview_stage_1632x816.png"
    )


def build_navigation():
    walk = [[False] * COLUMNS for _ in range(ROWS)]

    def set_rect(left, top, right, bottom, value):
        for row in range(top, bottom + 1):
            for col in range(left, right + 1):
                walk[row][col] = value

    # Interior and open central passage. Everything else is a solid wall.
    set_rect(5, 9, 33, 28, True)
    set_rect(37, 9, 64, 28, True)
    set_rect(34, 15, 36, 20, True)
    # Briefing: smaller sofa, holo-table and six chairs.
    set_rect(5, 14, 6, 22, False)
    set_rect(13, 15, 25, 19, False)
    for left, right in ((15, 16), (19, 20), (23, 24)):
        set_rect(left, 12, right, 14, False)
        set_rect(left, 20, right, 22, False)
    # Laboratory: cabinets, upper desk, reactor, wall console and lower devices.
    for bounds in (
        (38, 9, 45, 10), (51, 9, 60, 11),
        (47, 15, 51, 19), (60, 15, 64, 20),
        (39, 24, 47, 28), (57, 23, 59, 28),
        (62, 23, 64, 28),
    ):
        set_rect(*bounds, False)

    def walkable(x, y):
        return walk[y // CELL][x // CELL]

    # Anchors match CaseSession.java; all five characters and three clues remain.
    characters = [
        ("iron_man", 85, 200),
        ("captain_america", 231, 194),
        ("thor", 340, 167),
        ("black_widow", 440, 115),
        ("hawkeye", 424, 209),
    ]
    clues = [
        ("clue_access_log", 170, 207),
        ("clue_seal_trace", 481, 178),
        ("clue_reagent_sample", 390, 105),
    ]

    def entity(item, frame):
        identity, x, y = item
        col, row = x // CELL, y // CELL
        assert walkable(x, y), f"{identity} is placed on furniture or wall"
        neighbors = ((col - 1, row), (col, row - 1),
                     (col + 1, row), (col, row + 1))
        approaches = [(c, r) for c, r in neighbors
                      if 0 <= c < COLUMNS and 0 <= r < ROWS and walk[r][c]]
        assert approaches, f"{identity} has no adjacent approach"
        approach_col, approach_row = min(
            approaches,
            key=lambda cell: (cell[0] * CELL + CELL / 2 - x) ** 2
            + (cell[1] * CELL + CELL / 2 - y) ** 2,
        )
        return {
            "id": identity, "x": x / WIDTH, "y": y / HEIGHT,
            "col": col, "row": row,
            "approachCol": approach_col, "approachRow": approach_row,
            "atlasFrame": frame,
        }

    spawn_x, spawn_y = 66, 208
    assert walkable(spawn_x, spawn_y), "Spawn is blocked"
    entities = [entity(item, frame) for frame, item in enumerate(characters + clues)]

    metadata = {
        "schemaVersion": 1,
        "logicalWidth": WIDTH, "logicalHeight": HEIGHT,
        "navCellSize": CELL, "navColumns": COLUMNS, "navRows": ROWS,
        "navBoundsInclusive": {
            "minCol": 0, "maxCol": COLUMNS - 1,
            "minRow": 0, "maxRow": ROWS - 1,
        },
        "connectivity": "4-neighbor; two rooms, one open passage, eight reachable approaches",
        "walkableRows": ["".join("1" if cell else "0" for cell in row) for row in walk],
        "solidCells": [],
        "spawn": {
            "x": spawn_x / WIDTH, "y": spawn_y / HEIGHT,
            "col": spawn_x // CELL, "row": spawn_y // CELL,
        },
        "characters": entities[:5], "clues": entities[5:],
        "rooms": [
            {"id": "briefing", "x": [0, 271]},
            {"id": "lab", "x": [272, 543]},
        ],
        "passages": [
            {"from": "briefing", "to": "lab", "columns": [33, 36], "rows": [15, 20]},
        ],
    }
    (ASSETS / "room_walkmap.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    mask = Image.new("RGB", (COLUMNS, ROWS))
    mask.putdata([(255, 255, 255) if cell else (0, 0, 0)
                  for row in walk for cell in row])
    mask.save(RES / "room_walkmap_68x34.png")

    origin = (spawn_x // CELL, spawn_y // CELL)
    reached = {origin}
    queue = deque([origin])
    while queue:
        col, row = queue.popleft()
        for dc, dr in ((0, -1), (-1, 0), (1, 0), (0, 1)):
            nc, nr = col + dc, row + dr
            if (0 <= nc < COLUMNS and 0 <= nr < ROWS
                    and walk[nr][nc] and (nc, nr) not in reached):
                reached.add((nc, nr))
                queue.append((nc, nr))
    assert len(reached) == sum(sum(row) for row in walk), "Floor is disconnected"
    assert all((item["approachCol"], item["approachRow"]) in reached
               for item in entities), "An interaction is unreachable"
    assert all(walk[17][col] for col in range(34, 37)), "Passage is blocked"
    assert all(not walk[10][col] for col in range(34, 37)), "Wall is passable"
    assert not walk[17][19] and not walk[17][49], "Furniture collision is missing"
    assert any(col < 34 for col, _ in reached)
    assert any(col >= 37 for col, _ in reached)
    print(f"Map {WIDTH}x{HEIGHT}; {len(reached)} connected walk cells; 8 reachable approaches")


def write_manifest():
    files = {
        "originalTwoRooms": RES / "room_map_288x144.png",
        "editedFurnitureRaw": SMALL_FURNITURE_RAW,
        "runtimeMap": RES / "room_map_544x272.png",
        "walkMask": RES / "room_walkmap_68x34.png",
        "navigation": ASSETS / "room_walkmap.json",
        "preview": ASSETS / "room_preview_stage_1632x816.png",
    }
    manifest = {
        "schemaVersion": 6,
        "version": "original-two-rooms-smaller-furniture-v1",
        "artSource": "The two-room art was edited with image_gen from room_map_544x272.png to shrink furniture while preserving the room footprint.",
        "world": {
            "logicalWidth": WIDTH, "logicalHeight": HEIGHT,
            "roomWidth": 272, "roomHeight": 272,
            "cameraWidth": 288, "cameraHeight": 144,
            "navColumns": COLUMNS, "navRows": ROWS, "navCellSize": CELL,
        },
        "runtime": "RoomCanvasView draws the map and dynamic entities; RoomWalkMap reads room_walkmap.json.",
        "assets": {name: str(path.relative_to(ROOT)).replace('\\', '/')
                   for name, path in files.items()},
        "sha256": {name: hashlib.sha256(path.read_bytes()).hexdigest()
                   for name, path in files.items()},
    }
    (ASSETS / "room_asset_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


if __name__ == "__main__":
    compose_map()
    build_navigation()
    write_manifest()
