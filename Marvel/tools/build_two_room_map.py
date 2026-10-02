"""Build the slightly smaller two-room map and matching walk grid.

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
WIDTH, HEIGHT, CELL = 544, 272, 8
COLUMNS, ROWS = WIDTH // CELL, HEIGHT // CELL
SOURCE_WIDTH, SOURCE_HEIGHT = 576, 288


def compose_map():
    original = Image.open(RES / "room_map_288x144.png").convert("RGB")
    assert original.size == (288, 144)
    room = original.copy()

    # Open the original center bulkhead, reusing floor pixels from each side.
    for y in range(64, 89):
        for x in range(132, 157):
            floor_x = 125 + ((x - 132) % 7) if x < 144 else 158 + ((x - 144) % 7)
            room.putpixel((x, y), original.getpixel((floor_x, y)))

    # Previous rooms were 288x288. Both are now 272x272 (17/18 of that size).
    # The same scale is applied horizontally and vertically to avoid stretching.
    runtime = room.resize((WIDTH, HEIGHT), Image.Resampling.NEAREST)
    runtime.save(RES / "room_map_544x272.png")
    runtime.resize((WIDTH * 3, HEIGHT * 3), Image.Resampling.NEAREST).save(
        ASSETS / "room_preview_stage_1632x816.png"
    )


def build_navigation():
    # Author collision at the earlier 576x288 scale, then sample it at each
    # 8px target-cell center after the uniform 17/18 size reduction.
    old = [[False] * 72 for _ in range(36)]

    def set_rect(left, top, right, bottom, value):
        for row in range(top, bottom + 1):
            for col in range(left, right + 1):
                old[row][col] = value

    set_rect(5, 10, 34, 29, True)
    set_rect(39, 10, 70, 29, True)
    set_rect(35, 16, 38, 21, True)
    # Briefing: sofa, holo-table and six seats.
    set_rect(7, 15, 10, 24, False)
    set_rect(13, 15, 29, 22, False)
    for left, right in ((15, 17), (20, 22), (25, 27)):
        set_rect(left, 12, right, 14, False)
        set_rect(left, 23, right, 25, False)
    # Laboratory: wall consoles, reactor, workbench and server banks.
    for bounds in (
        (40, 10, 48, 15), (54, 10, 69, 15),
        (50, 17, 57, 22), (40, 25, 52, 29),
        (65, 17, 70, 23), (62, 25, 69, 29),
    ):
        set_rect(*bounds, False)

    walk = []
    for row in range(ROWS):
        source_row = min(35, int((row + 0.5) * 36 / ROWS))
        walk.append([
            old[source_row][min(71, int((col + 0.5) * 72 / COLUMNS))]
            for col in range(COLUMNS)
        ])

    def walkable(x, y):
        return walk[y // CELL][x // CELL]

    # Anchors match CaseSession.java; all five characters and three clues remain.
    characters = [
        ("iron_man", 85, 200),
        ("captain_america", 231, 194),
        ("thor", 340, 167),
        ("black_widow", 453, 196),
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
    assert any(col < 34 for col, _ in reached)
    assert any(col >= 37 for col, _ in reached)
    print(f"Map {WIDTH}x{HEIGHT}; {len(reached)} connected walk cells; 8 reachable approaches")


def write_manifest():
    files = {
        "originalTwoRooms": RES / "room_map_288x144.png",
        "runtimeMap": RES / "room_map_544x272.png",
        "walkMask": RES / "room_walkmap_68x34.png",
        "navigation": ASSETS / "room_walkmap.json",
        "preview": ASSETS / "room_preview_stage_1632x816.png",
    }
    manifest = {
        "schemaVersion": 5,
        "version": "original-two-rooms-slightly-smaller-v1",
        "artSource": "Both rooms come from the original room_map_288x144.png. The center passage reuses neighboring floor pixels.",
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
