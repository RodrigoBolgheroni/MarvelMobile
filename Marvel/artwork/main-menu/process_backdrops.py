"""Deterministic pixel-art export for the generated menu backdrop sources."""

from pathlib import Path
from PIL import Image


ROOT = Path(__file__).parent
PALETTE = [
    (0x0D, 0x17, 0x20),
    (0x24, 0x35, 0x43),
    (0x38, 0x4B, 0x57),
    (0x52, 0x66, 0x6A),
    (0xC4, 0xD5, 0xC8),
    (0x44, 0xCF, 0xBD),
    (0xF2, 0xB8, 0x4B),
    (0xE4, 0x67, 0x5D),
]

EXPORTS = [
    (
        "menu_backdrop_landscape_imagegen_original.png",
        "menu_backdrop_landscape_1024x576.png",
        (256, 144),
    ),
    (
        "menu_backdrop_portrait_imagegen_original.png",
        "menu_backdrop_portrait_576x1024.png",
        (144, 256),
    ),
]

CROP_PREVIEWS = [
    (
        "runtime/menu_backdrop_landscape_1024x576.png",
        "preview/menu_backdrop_landscape_centercrop_20x9.png",
        (20, 9),
    ),
    (
        "runtime/menu_backdrop_portrait_576x1024.png",
        "preview/menu_backdrop_portrait_centercrop_9x20.png",
        (9, 20),
    ),
]


def fixed_palette_image() -> Image.Image:
    palette = Image.new("P", (1, 1))
    flat = [channel for color in PALETTE for channel in color]
    flat.extend(list(PALETTE[0]) * (256 - len(PALETTE)))
    palette.putpalette(flat)
    return palette


def center_crop(image: Image.Image, target_width: int, target_height: int) -> Image.Image:
    target_ratio = target_width / target_height
    width, height = image.size
    if width / height > target_ratio:
        crop_width = round(height * target_ratio)
        left = (width - crop_width) // 2
        return image.crop((left, 0, left + crop_width, height))
    crop_height = round(width / target_ratio)
    top = (height - crop_height) // 2
    return image.crop((0, top, width, top + crop_height))


def apply_quiet_zone(image: Image.Image, axis: str, start: int, fade_start: int) -> Image.Image:
    """Leave a flat, low-contrast area for XML controls, with a short pixel-grid fade."""
    pixels = image.load()
    width, height = image.size
    bg = PALETTE[0]
    span = max(1, start - fade_start)
    for y in range(height):
        for x in range(width):
            position = x if axis == "x" else y
            if position >= start:
                pixels[x, y] = bg
            elif position >= fade_start:
                strength = (position - fade_start + 1) / span
                source = pixels[x, y]
                mixed = tuple(round(source[i] * (1 - strength) + bg[i] * strength) for i in range(3))
                pixels[x, y] = min(PALETTE, key=lambda color: sum((color[i] - mixed[i]) ** 2 for i in range(3)))
    return image


def main() -> None:
    palette = fixed_palette_image()
    for source_name, output_name, logical_size in EXPORTS:
        source_path = ROOT / "sources" / source_name
        output_path = ROOT / "runtime" / output_name
        width, height = logical_size
        source = Image.open(source_path).convert("RGB")
        crop = center_crop(source, width, height)
        logical = crop.resize(logical_size, Image.Resampling.LANCZOS)
        snapped = logical.quantize(palette=palette, dither=Image.Dither.NONE).convert("RGB")
        if "landscape" in output_name:
            apply_quiet_zone(snapped, "x", start=148, fade_start=132)
        else:
            apply_quiet_zone(snapped, "y", start=141, fade_start=128)
        final_size = (width * 4, height * 4)
        final = snapped.resize(final_size, Image.Resampling.NEAREST)
        final.save(output_path, optimize=True)
        colors = set(final.get_flattened_data())
        unexpected = colors.difference(set(PALETTE))
        if final.size != final_size or unexpected:
            raise RuntimeError(f"Invalid export: {output_path}")
        print(f"{output_path.relative_to(ROOT)}: {final.width}x{final.height}; logical {width}x{height}; palette {len(colors)} colors")

    (ROOT / "preview").mkdir(exist_ok=True)
    for source_name, output_name, target_aspect in CROP_PREVIEWS:
        source = Image.open(ROOT / source_name).convert("RGB")
        cropped = center_crop(source, *target_aspect)
        output_path = ROOT / output_name
        cropped.save(output_path, optimize=True)
        print(f"{output_path.relative_to(ROOT)}: {cropped.width}x{cropped.height}; center-crop QA")


if __name__ == "__main__":
    main()
