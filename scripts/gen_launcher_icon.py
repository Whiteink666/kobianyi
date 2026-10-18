"""从一张原图生成 Android launcher 图标资源（自适应图标 + 兼容密度图标）。

用法:
    python scripts/gen_launcher_icon.py <源图> [输出根目录，默认 manager/app/src/main/res]

生成内容:
    drawable-nodpi/ic_launcher_foreground.png   自适应图标前景（已按安全区缩放）
    drawable-nodpi/ic_launcher_monochrome.png  单色层（主题图标）
    mipmap-*/ic_launcher.png                   兼容用的方形图标
    mipmap-*/ic_launcher_round.png             兼容用的圆形图标
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps

FOREGROUND = 432          # 108dp @ 4x
SAFE = 288                # 72dp @ 4x，安全区
LEGACY = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def content_bbox(img: Image.Image, threshold: int = 24):
    """去掉纯黑（或纯白）留白边，返回内容区域。"""
    gray = img.convert("L")
    # 取非纯黑区域
    mask = gray.point(lambda v: 255 if v > threshold else 0)
    bbox = mask.getbbox()
    if bbox is None:
        return None
    return bbox


def square_crop(img: Image.Image) -> Image.Image:
    bbox = content_bbox(img)
    if bbox:
        img = img.crop(bbox)
    w, h = img.size
    side = max(w, h)
    # 居中裁成正方形，四周留 4% 呼吸
    canvas = Image.new("RGB", (side, side), (0, 0, 0))
    canvas.paste(img, ((side - w) // 2, (side - h) // 2))
    pad = int(side * 0.04)
    return canvas.crop((pad, pad, side - pad, side - pad)).resize((side, side), Image.LANCZOS)


def key_black(img: Image.Image, threshold: int = 32) -> Image.Image:
    """把纯黑背景抠成透明，保留主体。"""
    img = img.convert("RGBA")
    r, g, b, _ = img.split()
    gray = ImageOps.grayscale(img.convert("RGB"))
    alpha = gray.point(lambda v: 0 if v <= threshold else min(255, int((v - threshold) * 255 / (255 - threshold))))
    img.putalpha(alpha)
    return img


def main() -> int:
    src = Path(sys.argv[1])
    res_root = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("manager/app/src/main/res")

    img = Image.open(src)
    print(f"source: {src} {img.size} {img.mode}")
    # 统一按最长边放大到 1024 再处理，避免小图放大糊
    scale = 1024 / max(img.size)
    if scale > 1:
        img = img.resize((int(img.width * scale), int(img.height * scale)), Image.LANCZOS)
    img = img.convert("RGB")

    squared = square_crop(img)
    print(f"cropped square: {squared.size}")

    # 自适应前景：原图本身就是带底色的方形插画，整幅全铺，
    # 由系统的遮罩去裁形状（抠 alpha 会把插画里的暗部一起抠掉）
    foreground = squared.convert("RGBA").resize((FOREGROUND, FOREGROUND), Image.LANCZOS)

    nodpi = res_root / "drawable-nodpi"
    nodpi.mkdir(parents=True, exist_ok=True)
    foreground.save(nodpi / "ic_launcher_foreground.png", "PNG")
    print(f"wrote {nodpi/'ic_launcher_foreground.png'}")

    # 兼容图标：不抠底，黑底直接当图标背景
    for folder, size in LEGACY.items():
        d = res_root / folder
        d.mkdir(parents=True, exist_ok=True)
        squared.resize((size, size), Image.LANCZOS).save(d / "ic_launcher.png", "PNG")
        round_icon = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
        base = squared.resize((size * 4, size * 4), Image.LANCZOS).convert("RGBA")
        mask = Image.new("L", (size * 4, size * 4), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
        round_icon.paste(base, (0, 0), mask)
        round_icon.resize((size, size), Image.LANCZOS).save(d / "ic_launcher_round.png", "PNG")
        print(f"wrote {folder}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
