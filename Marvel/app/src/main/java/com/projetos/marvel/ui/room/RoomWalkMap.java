package com.projetos.marvel.ui.room;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.graphics.Color;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 4-connected navigation over the 108x18, 8px-per-cell three-room floor grid. */
final class RoomWalkMap {
    static final int COLUMNS = 108;
    static final int ROWS = 18;
    static final float CELL_SIZE = 8f;

    private static final int DEFAULT_MIN_COLUMN = 2;
    private static final int DEFAULT_MAX_COLUMN = COLUMNS - 3;
    private static final int DEFAULT_MIN_ROW = 2;
    private static final int DEFAULT_MAX_ROW = 15;
    private static final int[] DC = {0, -1, 1, 0};
    private static final int[] DR = {-1, 0, 0, 1};

    private final Bitmap walkMask;
    private final int[][] tilemap;
    private final int tileAtlasColumns;
    private final int artColumns;
    private final int artRows;
    private final String[] walkableRows;
    private final int minColumn;
    private final int maxColumn;
    private final int minRow;
    private final int maxRow;
    private final Set<Cell> occupied = new HashSet<>();
    private final Map<String, Cell> approaches = new HashMap<>();
    private final Map<String, Integer> characterFrames = new HashMap<>();
    private final Map<String, Integer> clueFrames = new HashMap<>();
    private final List<PropPlacement> props;

    RoomWalkMap(Context context) {
        JSONObject metadata = readMetadata(context);
        TileMetadata tileMetadata = loadTileMetadata(metadata);
        tilemap = tileMetadata.tilemap;
        tileAtlasColumns = tileMetadata.atlasColumns;
        artColumns = tileMetadata.artColumns;
        artRows = tileMetadata.artRows;
        NavigationMetadata navigation = loadNavigationMetadata(metadata);
        walkMask = navigation == null ? loadMask(context) : null;
        EntityMetadata entities = loadEntityMetadata(metadata);
        props = loadProps(metadata);
        walkableRows = navigation == null ? null : navigation.walkableRows;
        minColumn = navigation == null ? DEFAULT_MIN_COLUMN : navigation.minColumn;
        maxColumn = navigation == null ? DEFAULT_MAX_COLUMN : navigation.maxColumn;
        minRow = navigation == null ? DEFAULT_MIN_ROW : navigation.minRow;
        maxRow = navigation == null ? DEFAULT_MAX_ROW : navigation.maxRow;
        if (navigation == null) {
            occupied.add(new Cell(10, 8));
            occupied.add(new Cell(18, 8));
            occupied.add(new Cell(25, 8));
            occupied.add(new Cell(7, 13));
            occupied.add(new Cell(28, 13));
        } else {
            occupied.addAll(navigation.solidCells);
        }

        // Static fallback until the metadata file is present.
        approaches.put("iron_man", new Cell(5, 4));
        approaches.put("captain_america", new Cell(18, 4));
        approaches.put("thor", new Cell(30, 4));
        approaches.put("clue_access_log", new Cell(10, 9));
        approaches.put("clue_seal_trace", new Cell(18, 9));
        approaches.put("clue_reagent_sample", new Cell(25, 9));
        approaches.put("black_widow", new Cell(7, 12));
        approaches.put("hawkeye", new Cell(28, 12));
        characterFrames.put("iron_man", 0);
        characterFrames.put("captain_america", 1);
        characterFrames.put("thor", 2);
        characterFrames.put("black_widow", 3);
        characterFrames.put("hawkeye", 4);
        clueFrames.put("clue_access_log", 0);
        clueFrames.put("clue_seal_trace", 1);
        clueFrames.put("clue_reagent_sample", 2);
        if (entities != null) {
            approaches.putAll(entities.approaches);
            characterFrames.putAll(entities.characterFrames);
            clueFrames.putAll(entities.clueFrames);
        }
    }

    Cell approachFor(String id) { return approaches.get(id); }

    int characterFrameFor(String id, int fallback) {
        Integer frame = characterFrames.get(id);
        return frame == null ? fallback : frame;
    }

    int clueFrameFor(String id, int fallback) {
        Integer frame = clueFrames.get(id);
        return frame == null ? fallback : frame;
    }

    List<PropPlacement> props() { return props; }

    boolean hasTilemap() { return tilemap != null; }

    int tileAtlasColumns() { return tileAtlasColumns; }

    int tileColumns() { return artColumns; }

    int tileRows() { return artRows; }

    int tileAt(int column, int row) {
        if (tilemap == null || row < 0 || row >= tilemap.length
                || column < 0 || column >= tilemap[row].length) return -1;
        return tilemap[row][column];
    }

    boolean isWalkable(int column, int row) {
        if (column < minColumn || column > maxColumn || row < minRow || row > maxRow) {
            return false;
        }
        if (occupied.contains(new Cell(column, row))) return false;
        if (walkableRows != null) return walkableRows[row].charAt(column) == '1';
        if (walkMask == null) return true;
        int pixel = walkMask.getPixel(column, row);
        return Color.alpha(pixel) > 0
                && Color.red(pixel) >= 128
                && Color.green(pixel) >= 128
                && Color.blue(pixel) >= 128;
    }

    List<PointF> pathToCell(float startX, float startY, Cell target) {
        if (target == null) return Collections.emptyList();
        return findPath(startX, startY, centerX(target.column), centerY(target.row));
    }

    List<PointF> pathToNormalized(float startX, float startY, float targetX, float targetY) {
        return findPath(clamp(startX), clamp(startY), clamp(targetX), clamp(targetY));
    }

    private List<PointF> findPath(float startX, float startY, float targetX, float targetY) {
        Cell start = cellAt(startX, startY);
        if (!isWalkable(start.column, start.row)) {
            start = nearestWalkable(startX, startY);
            if (start == null) return Collections.emptyList();
        }

        int[][] distance = new int[ROWS][COLUMNS];
        Cell[][] previous = new Cell[ROWS][COLUMNS];
        for (int[] row : distance) Arrays.fill(row, -1);

        ArrayDeque<Cell> queue = new ArrayDeque<>();
        queue.add(start);
        distance[start.row][start.column] = 0;
        previous[start.row][start.column] = start;
        while (!queue.isEmpty()) {
            Cell current = queue.removeFirst();
            for (int i = 0; i < DC.length; i++) {
                int nextColumn = current.column + DC[i];
                int nextRow = current.row + DR[i];
                if (!isWalkable(nextColumn, nextRow)
                        || distance[nextRow][nextColumn] >= 0) continue;
                Cell next = new Cell(nextColumn, nextRow);
                distance[nextRow][nextColumn] = distance[current.row][current.column] + 1;
                previous[nextRow][nextColumn] = current;
                queue.addLast(next);
            }
        }

        Cell requested = cellAt(targetX, targetY);
        Cell destination = isReachable(requested, distance) ? requested
                : nearestReachable(targetX, targetY, distance);
        if (destination == null) return Collections.emptyList();

        List<Cell> cells = new ArrayList<>();
        Cell cursor = destination;
        while (!cursor.equals(start)) {
            cells.add(cursor);
            cursor = previous[cursor.row][cursor.column];
            if (cursor == null) return Collections.emptyList();
        }
        cells.add(start);
        Collections.reverse(cells);

        List<PointF> path = new ArrayList<>();
        path.add(new PointF(startX, startY));
        float centerX = centerX(start.column);
        float centerY = centerY(start.row);
        // Enter the start cell center using cardinal segments before following the BFS route.
        if (Math.abs(centerX - startX) > 0.00001f) {
            path.add(new PointF(centerX, startY));
        }
        if (Math.abs(centerY - startY) > 0.00001f) {
            path.add(new PointF(centerX, centerY));
        }
        for (int i = 1; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            path.add(new PointF(centerX(cell.column), centerY(cell.row)));
        }
        if (path.size() == 1) path.add(new PointF(centerX, centerY));
        return path;
    }

    private boolean isReachable(Cell cell, int[][] distance) {
        return isWalkable(cell.column, cell.row) && distance[cell.row][cell.column] >= 0;
    }

    private Cell nearestReachable(float targetX, float targetY, int[][] distance) {
        Cell best = null;
        float bestPositionDistance = Float.MAX_VALUE;
        int bestPathDistance = Integer.MAX_VALUE;
        for (int row = minRow; row <= maxRow; row++) {
            for (int column = minColumn; column <= maxColumn; column++) {
                if (!isWalkable(column, row) || distance[row][column] < 0) continue;
                float dx = (centerX(column) - targetX) * RoomViewport.LOGICAL_WIDTH;
                float dy = (centerY(row) - targetY) * RoomViewport.LOGICAL_HEIGHT;
                float positionDistance = dx * dx + dy * dy;
                int pathDistance = distance[row][column];
                if (positionDistance < bestPositionDistance
                        || (positionDistance == bestPositionDistance
                        && pathDistance < bestPathDistance)) {
                    best = new Cell(column, row);
                    bestPositionDistance = positionDistance;
                    bestPathDistance = pathDistance;
                }
            }
        }
        return best;
    }

    private Cell nearestWalkable(float x, float y) {
        Cell best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int row = minRow; row <= maxRow; row++) {
            for (int column = minColumn; column <= maxColumn; column++) {
                if (!isWalkable(column, row)) continue;
                float dx = (centerX(column) - x) * RoomViewport.LOGICAL_WIDTH;
                float dy = (centerY(row) - y) * RoomViewport.LOGICAL_HEIGHT;
                float distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    best = new Cell(column, row);
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static Bitmap loadMask(Context context) {
        int id = context.getResources().getIdentifier("room_walkmap_108x18", "drawable",
                context.getPackageName());
        if (id == 0) return null;
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), id);
        if (bitmap == null || bitmap.getWidth() != COLUMNS || bitmap.getHeight() != ROWS) {
            if (bitmap != null) bitmap.recycle();
            return null;
        }
        return bitmap;
    }

    private static JSONObject readMetadata(Context context) {
        try (InputStream input = context.getAssets().open("room/room_walkmap.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static TileMetadata loadTileMetadata(JSONObject root) {
        try {
            if (root == null) return TileMetadata.unavailable();
            int columns = root.getInt("tileAtlasColumns");
            int artColumns = root.getInt("artColumns");
            int artRows = root.getInt("artRows");
            if (root.getInt("schemaVersion") != 1
                    || root.getInt("logicalWidth") != RoomViewport.LOGICAL_WIDTH
                    || root.getInt("logicalHeight") != RoomViewport.LOGICAL_HEIGHT
                    || root.getInt("artTileSize") != 16
                    || artColumns != (int) (RoomViewport.LOGICAL_WIDTH / 16f)
                    || artRows != (int) (RoomViewport.LOGICAL_HEIGHT / 16f)
                    || columns != 4
                    || root.getInt("tileAtlasRows") != 4
                    || root.getInt("tileAtlasCount") != 16) {
                return TileMetadata.unavailable();
            }
            JSONArray rows = root.getJSONArray("tilemap");
            if (rows.length() != artRows) return TileMetadata.unavailable();
            int[][] values = new int[artRows][artColumns];
            for (int row = 0; row < artRows; row++) {
                JSONArray tiles = rows.getJSONArray(row);
                if (tiles.length() != artColumns) return TileMetadata.unavailable();
                for (int column = 0; column < artColumns; column++) {
                    int frame = tiles.getInt(column);
                    if (frame < 0 || frame >= 16) return TileMetadata.unavailable();
                    values[row][column] = frame;
                }
            }
            return new TileMetadata(values, columns, artColumns, artRows);
        } catch (Exception ignored) {
            return TileMetadata.unavailable();
        }
    }

    private static NavigationMetadata loadNavigationMetadata(JSONObject root) {
        try {
            if (root == null) return null;
            if (root.getInt("navCellSize") != 8
                    || root.getInt("navColumns") != COLUMNS
                    || root.getInt("navRows") != ROWS
                    || !"4-neighbor; spawn connects to all eight character and clue approach cells across three rooms"
                    .equals(root.getString("connectivity"))) {
                return null;
            }

            JSONArray rows = root.getJSONArray("walkableRows");
            if (rows.length() != ROWS) return null;
            String[] walkable = new String[ROWS];
            for (int row = 0; row < ROWS; row++) {
                String value = rows.getString(row);
                if (value.length() != COLUMNS) return null;
                for (int column = 0; column < COLUMNS; column++) {
                    char cell = value.charAt(column);
                    if (cell != '0' && cell != '1') return null;
                }
                walkable[row] = value;
            }

            JSONArray solids = root.getJSONArray("solidCells");
            Set<Cell> solidCells = new HashSet<>();
            for (int i = 0; i < solids.length(); i++) {
                JSONArray pair = solids.getJSONArray(i);
                if (pair.length() != 2) return null;
                int column = pair.getInt(0);
                int row = pair.getInt(1);
                if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) return null;
                solidCells.add(new Cell(column, row));
            }

            JSONObject bounds = root.getJSONObject("navBoundsInclusive");
            int minColumn = bounds.getInt("minCol");
            int maxColumn = bounds.getInt("maxCol");
            int minRow = bounds.getInt("minRow");
            int maxRow = bounds.getInt("maxRow");
            if (minColumn < 0 || maxColumn >= COLUMNS || minColumn > maxColumn
                    || minRow < 0 || maxRow >= ROWS || minRow > maxRow) return null;
            return new NavigationMetadata(walkable, solidCells,
                    minColumn, maxColumn, minRow, maxRow);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static EntityMetadata loadEntityMetadata(JSONObject root) {
        if (root == null) return null;
        try {
            JSONObject spawn = root.getJSONObject("spawn");
            if (!anchorLookupMatches(spawn)) return null;
            EntityMetadata metadata = new EntityMetadata();
            if (!readEntityArray(root.getJSONArray("characters"), metadata, true)
                    || !readEntityArray(root.getJSONArray("clues"), metadata, false)
                    || metadata.characterFrames.size() != 5
                    || metadata.clueFrames.size() != 3
                    || !containsAll(metadata.characterFrames, "iron_man", "captain_america",
                    "thor", "black_widow", "hawkeye")
                    || !containsAll(metadata.clueFrames, "clue_access_log",
                    "clue_seal_trace", "clue_reagent_sample")) return null;
            return metadata;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean readEntityArray(JSONArray items, EntityMetadata destination,
                                          boolean character) throws Exception {
        Map<String, Integer> frames = character
                ? destination.characterFrames : destination.clueFrames;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String id = item.getString("id");
            if (!anchorLookupMatches(item)) return false;
            int approachColumn = item.getInt("approachCol");
            int approachRow = item.getInt("approachRow");
            int frame = item.getInt("atlasFrame");
            if (approachColumn < 0 || approachColumn >= COLUMNS
                    || approachRow < 0 || approachRow >= ROWS
                    || frame < 0 || frame >= (character ? 5 : 3)
                    || frames.put(id, frame) != null) return false;
            destination.approaches.put(id, new Cell(approachColumn, approachRow));
        }
        return true;
    }

    private static boolean anchorLookupMatches(JSONObject item) throws Exception {
        double x = item.getDouble("x");
        double y = item.getDouble("y");
        if (x < 0d || x > 1d || y < 0d || y > 1d) return false;
        int column = Math.min(COLUMNS - 1, (int) Math.floor(COLUMNS * x));
        int row = Math.min(ROWS - 1, (int) Math.floor(ROWS * y));
        return item.getInt("col") == column && item.getInt("row") == row;
    }

    private static boolean containsAll(Map<String, Integer> values, String... ids) {
        for (String id : ids) if (!values.containsKey(id)) return false;
        return true;
    }

    private static List<PropPlacement> loadProps(JSONObject root) {
        if (root == null) return Collections.emptyList();
        JSONArray placements = root.optJSONArray("props");
        if (placements == null) return Collections.emptyList();
        List<PropPlacement> result = new ArrayList<>();
        try {
            for (int i = 0; i < placements.length(); i++) {
                JSONObject item = placements.getJSONObject(i);
                String atlas = item.getString("atlas");
                int frame = item.getInt("atlasFrame");
                int atlasX = item.getInt("atlasX");
                int atlasY = item.getInt("atlasY");
                int column = item.getInt("col");
                int row = item.getInt("row");
                int width = item.optInt("width", 16);
                int height = item.optInt("height", 16);
                String layer = item.getString("layer");
                int artColumns = root.optInt("artColumns", 18);
                int artRows = root.optInt("artRows", 9);
                boolean atlasProp = "room_props_16.png".equals(atlas)
                        && frame >= 0 && atlasX >= 0 && atlasX < 4
                        && atlasY >= 0 && atlasY < 4
                        && width == 16 && height == 16;
                boolean standaloneProp = "room_tactical_briefing_table.png".equals(atlas)
                        && width > 0 && height > 0
                        && width <= RoomViewport.LOGICAL_WIDTH
                        && height <= RoomViewport.LOGICAL_HEIGHT;
                if ((!atlasProp && !standaloneProp) || column < 0 || column >= artColumns
                        || row < 0 || row >= artRows
                        || column * 16 + width > RoomViewport.LOGICAL_WIDTH
                        || row * 16 + height > RoomViewport.LOGICAL_HEIGHT) {
                    return Collections.emptyList();
                }
                result.add(new PropPlacement(item.getString("id"), frame,
                        atlasX, atlasY, column, row, width, height, atlas, layer));
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(result);
    }

    private static Cell cellAt(float x, float y) {
        int column = Math.min(COLUMNS - 1, Math.max(0, (int) Math.floor(COLUMNS * clamp(x))));
        int row = Math.min(ROWS - 1, Math.max(0, (int) Math.floor(ROWS * clamp(y))));
        return new Cell(column, row);
    }

    private static float centerX(int column) { return (column + 0.5f) / COLUMNS; }
    private static float centerY(int row) { return (row + 0.5f) / ROWS; }
    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }

    static final class Cell {
        final int column;
        final int row;

        Cell(int column, int row) {
            this.column = column;
            this.row = row;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Cell)) return false;
            Cell cell = (Cell) other;
            return column == cell.column && row == cell.row;
        }

        @Override public int hashCode() { return 31 * column + row; }
    }

    static final class PropPlacement {
        final String id;
        final int atlasFrame;
        final int atlasX;
        final int atlasY;
        final int column;
        final int row;
        final int width;
        final int height;
        final String atlas;
        final String layer;

        PropPlacement(String id, int atlasFrame, int atlasX, int atlasY,
                      int column, int row, int width, int height, String atlas, String layer) {
            this.id = id;
            this.atlasFrame = atlasFrame;
            this.atlasX = atlasX;
            this.atlasY = atlasY;
            this.column = column;
            this.row = row;
            this.width = width;
            this.height = height;
            this.atlas = atlas;
            this.layer = layer;
        }
    }

    private static final class TileMetadata {
        final int[][] tilemap;
        final int atlasColumns;
        final int artColumns;
        final int artRows;

        TileMetadata(int[][] tilemap, int atlasColumns, int artColumns, int artRows) {
            this.tilemap = tilemap;
            this.atlasColumns = atlasColumns;
            this.artColumns = artColumns;
            this.artRows = artRows;
        }

        static TileMetadata unavailable() { return new TileMetadata(null, 4, 0, 0); }
    }

    private static final class NavigationMetadata {
        final String[] walkableRows;
        final Set<Cell> solidCells;
        final int minColumn;
        final int maxColumn;
        final int minRow;
        final int maxRow;

        NavigationMetadata(String[] walkableRows, Set<Cell> solidCells,
                           int minColumn, int maxColumn, int minRow, int maxRow) {
            this.walkableRows = walkableRows;
            this.solidCells = solidCells;
            this.minColumn = minColumn;
            this.maxColumn = maxColumn;
            this.minRow = minRow;
            this.maxRow = maxRow;
        }
    }

    private static final class EntityMetadata {
        final Map<String, Cell> approaches = new HashMap<>();
        final Map<String, Integer> characterFrames = new HashMap<>();
        final Map<String, Integer> clueFrames = new HashMap<>();
    }
}
