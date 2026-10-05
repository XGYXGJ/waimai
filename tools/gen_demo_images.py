#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成演示数据用的本地占位图片（离线可用，不依赖外网图床）。

为什么需要：
    sql/demo_data.sql 里的商家 logo/封面、菜品图片都指向 `/uploads/demo/*.jpg`。
    但 `uploads/` 在 .gitignore 里（运行时数据不提交），所以换台机器跑演示数据时
    图片会 404。跑一次本脚本即可补齐全部占位图。

用法：
    python tools/gen_demo_images.py                 # 写入 uploads/demo/（后端实际读取的目录）
    python tools/gen_demo_images.py --out <目录>    # 追加自定义输出目录（可重复传多次）
    python tools/gen_demo_images.py --check         # 只检查是否齐全，不写文件

注意目录位置：
    后端 `waimai.upload.dir: ./uploads`（application.yml:26-27）是**相对 JVM 工作目录**解析的。
    本机 IDEA 运行配置的工作目录是仓库根 `waimai/`，所以静态资源真实根目录是
    `waimai/uploads/`，而不是 `waimai-server/uploads/`。默认值即按这个口径。
    若换到工作目录为模块目录的启动方式，用 `--out` 指到对应 uploads 即可。

产物（共 40 张，命名与 demo_data.sql 严格对应）：
    shop-<商家ID>-logo.jpg     200x200    商家头像
    shop-<商家ID>-cover.jpg    800x400    商家封面
    dish-<菜品ID>.jpg          400x300    菜品图

依赖：Pillow（本机已装 12.2.0）。中文字体取 Windows 自带 msyh.ttc，
      取不到时自动退回 PIL 默认字体（中文会显示为方块，但不影响流程）。
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:  # pragma: no cover
    print("缺少 Pillow，请先执行：python -m pip install Pillow", file=sys.stderr)
    raise SystemExit(2)

ROOT = Path(__file__).resolve().parents[1]
# 后端静态资源根 = JVM 工作目录下的 ./uploads（见 docstring 说明）
DEFAULT_OUT = ROOT / "uploads" / "demo"

# ---- 与 demo_data.sql 严格一致的清单 -------------------------------------------------
SHOPS: list[tuple[int, str, str]] = [
    (1, "张记小厨", "家常小炒 · 现炒现做"),
    (1001, "川味小馆", "地道川菜 · 麻辣鲜香"),
    (1002, "甜芯奶茶", "现制茶饮 · 每日鲜果"),
    (1003, "深海寿司", "空运刺身 · 匠心手作"),
    (1004, "老王炸鸡", "现炸现做 · 酥脆多汁"),
]

DISHES: list[tuple[int, str, str]] = [
    (1, "招牌红烧肉饭", "张记小厨"),
    (2, "宫保鸡丁饭", "张记小厨"),
    (3, "扬州炒饭", "张记小厨"),
    (4, "番茄鸡蛋面", "张记小厨"),
    (5, "冰镇酸梅汤", "张记小厨"),
    (6, "柠檬绿茶", "张记小厨"),
    (1001, "水煮牛肉", "川味小馆"),
    (1002, "麻婆豆腐", "川味小馆"),
    (1003, "回锅肉", "川味小馆"),
    (1004, "鱼香肉丝", "川味小馆"),
    (1005, "担担面", "川味小馆"),
    (1006, "红糖冰粉", "川味小馆"),
    (1007, "芋泥啵啵奶茶", "甜芯奶茶"),
    (1008, "生椰拿铁", "甜芯奶茶"),
    (1009, "杨枝甘露", "甜芯奶茶"),
    (1010, "满杯百香果", "甜芯奶茶"),
    (1011, "提拉米苏", "甜芯奶茶"),
    (1012, "芝士蛋挞", "甜芯奶茶"),
    (1013, "三文鱼刺身", "深海寿司"),
    (1014, "豪华寿司拼盘", "深海寿司"),
    (1015, "金枪鱼寿司", "深海寿司"),
    (1016, "鳗鱼饭", "深海寿司"),
    (1017, "味噌汤", "深海寿司"),
    (1018, "日式茶碗蒸", "深海寿司"),
    (1019, "香辣炸鸡桶", "老王炸鸡"),
    (1020, "黄金鸡块", "老王炸鸡"),
    (1021, "双层牛肉堡", "老王炸鸡"),
    (1022, "香辣鸡腿堡", "老王炸鸡"),
    (1023, "可乐（中杯）", "老王炸鸡"),
    (1024, "黄金薯条", "老王炸鸡"),
]

# 每张图一个渐变色对（深→浅），按索引轮转，保证视觉上有区分度
PALETTE: list[tuple[tuple[int, int, int], tuple[int, int, int]]] = [
    ((198, 40, 40), (255, 138, 101)),    # 红
    ((216, 67, 21), (255, 183, 77)),     # 橙
    ((245, 124, 0), (255, 213, 79)),     # 琥珀
    ((46, 125, 50), (139, 195, 74)),     # 绿
    ((0, 121, 107), (77, 182, 172)),     # 青
    ((21, 101, 192), (79, 195, 247)),    # 蓝
    ((94, 53, 177), (179, 157, 219)),    # 紫
    ((173, 20, 87), (240, 98, 146)),     # 玫红
    ((109, 76, 65), (188, 170, 164)),    # 棕
    ((69, 90, 100), (144, 164, 174)),    # 蓝灰
]

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\msyhbd.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
    r"C:\Windows\Fonts\simsun.ttc",
]
CJK_FONT_PATH: str | None = next((p for p in FONT_CANDIDATES if Path(p).exists()), None)


def font(size: int) -> "ImageFont.FreeTypeFont | ImageFont.ImageFont":
    if CJK_FONT_PATH:
        return ImageFont.truetype(CJK_FONT_PATH, size)
    return ImageFont.load_default()


def gradient(size: tuple[int, int], top: tuple[int, int, int],
             bottom: tuple[int, int, int]) -> Image.Image:
    """竖向线性渐变底图。"""
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        t = y / max(1, h - 1)
        row = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        for x in range(w):
            px[x, y] = row
    return img


def center_text(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], text: str,
                f: "ImageFont.FreeTypeFont | ImageFont.ImageFont",
                fill: tuple[int, int, int, int] = (255, 255, 255, 255)) -> None:
    x0, y0, x1, y1 = box
    left, top, right, bottom = draw.textbbox((0, 0), text, font=f)
    draw.text((x0 + (x1 - x0 - (right - left)) / 2 - left,
               y0 + (y1 - y0 - (bottom - top)) / 2 - top), text, font=f, fill=fill)


def make_dish(dish_id: int, name: str, shop: str, idx: int) -> Image.Image:
    top, bottom = PALETTE[idx % len(PALETTE)]
    img = gradient((400, 300), top, bottom)
    draw = ImageDraw.Draw(img, "RGBA")
    # 顶部半透明条：店名
    draw.rectangle([0, 0, 400, 34], fill=(0, 0, 0, 90))
    draw.text((12, 8), shop, font=font(16), fill=(255, 255, 255, 235))
    # 中央菜名（带描边，浅色底也看得清）
    f = font(34)
    center_text(draw, (0, 40, 400, 250), name, f, (0, 0, 0, 70))
    center_text(draw, (0, 37, 400, 247), name, f)
    # 底部标注
    draw.text((12, 272), f"示例图片 dish-{dish_id}", font=font(13), fill=(255, 255, 255, 200))
    return img


def make_logo(shop_id: int, name: str, slogan: str, idx: int) -> Image.Image:
    top, bottom = PALETTE[idx % len(PALETTE)]
    img = gradient((200, 200), top, bottom)
    draw = ImageDraw.Draw(img, "RGBA")
    draw.ellipse([16, 16, 184, 184], outline=(255, 255, 255, 160), width=3)
    short = name[:4]
    f = font(40)
    center_text(draw, (16, 40, 184, 160), short, f)
    center_text(draw, (16, 140, 184, 180), "DEMO", font(13), (255, 255, 255, 200))
    return img


def make_cover(shop_id: int, name: str, slogan: str, idx: int) -> Image.Image:
    top, bottom = PALETTE[idx % len(PALETTE)]
    img = gradient((800, 400), top, bottom)
    draw = ImageDraw.Draw(img, "RGBA")
    draw.rectangle([0, 300, 800, 400], fill=(0, 0, 0, 80))
    center_text(draw, (0, 130, 800, 220), name, font(58))
    center_text(draw, (0, 225, 800, 275), slogan, font(22), (255, 255, 255, 220))
    draw.text((16, 366), f"示例封面 shop-{shop_id}", font=font(15), fill=(255, 255, 255, 190))
    return img


def expected_files() -> list[tuple[str, str]]:
    """[(文件名, 说明)] —— 供 --check 与写入共用。"""
    out: list[tuple[str, str]] = []
    for i, (sid, name, slogan) in enumerate(SHOPS):
        out.append((f"shop-{sid}-logo.jpg", f"{name} logo"))
        out.append((f"shop-{sid}-cover.jpg", f"{name} cover"))
    for i, (did, name, shop) in enumerate(DISHES):
        out.append((f"dish-{did}.jpg", f"{name} @ {shop}"))
    return out


def build_all() -> dict[str, Image.Image]:
    imgs: dict[str, Image.Image] = {}
    for i, (sid, name, slogan) in enumerate(SHOPS):
        imgs[f"shop-{sid}-logo.jpg"] = make_logo(sid, name, slogan, i)
        imgs[f"shop-{sid}-cover.jpg"] = make_cover(sid, name, slogan, i)
    for i, (did, name, shop) in enumerate(DISHES):
        imgs[f"dish-{did}.jpg"] = make_dish(did, name, shop, i)
    return imgs


def main() -> int:
    ap = argparse.ArgumentParser(description="生成演示数据占位图")
    ap.add_argument("--out", action="append", default=None,
                    help="输出目录，可重复传入以同步多个目录（默认 uploads/demo/）")
    ap.add_argument("--check", action="store_true", help="只检查文件是否齐全")
    args = ap.parse_args()

    outs = [Path(p) for p in (args.out or [str(DEFAULT_OUT)])]
    expected = expected_files()

    if args.check:
        rc = 0
        for out in outs:
            missing = [n for n, _ in expected if not (out / n).is_file()]
            print(f"目录: {out}")
            print(f"  应有 {len(expected)} 张，缺失 {len(missing)} 张")
            for n in missing:
                print("  MISSING", n)
            if missing:
                rc = 1
        return rc

    imgs = build_all()
    for out in outs:
        out.mkdir(parents=True, exist_ok=True)
        total = 0
        for name, img in imgs.items():
            path = out / name
            img.save(path, "JPEG", quality=82, optimize=True)
            total += path.stat().st_size
        print(f"已生成 {len(imgs)} 张占位图 → {out}（{total / 1024:.1f} KB）")
    print(f"字体: {CJK_FONT_PATH or 'PIL 默认(中文会显示为方块)'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
