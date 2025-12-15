#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成壁纸的 CLIP Embedding

输入：
- wallpapers.csv（默认：项目根目录下或 data/wallpapers.csv）
- 已下载的图片目录（默认：data/images/）

输出：
- data/wallpaper_embeddings.csv  格式：id:val1 val2 ... valN

依赖：
    pip install sentence-transformers Pillow torch
模型：
    clip-ViT-B-32（sentence-transformers）
"""

import csv
import glob
import os
from pathlib import Path
from typing import List, Optional, Tuple

from PIL import Image
from sentence_transformers import SentenceTransformer


ROOT = Path(__file__).resolve().parent.parent
CSV_CANDIDATES = [
    ROOT / "data" / "wallpapers.csv",
    ROOT / "wallpapers.csv",
]
IMAGES_DIR = ROOT / "data" / "images"
OUTPUT_PATH = ROOT / "data" / "wallpaper_embeddings.csv"
MODEL_NAME = "clip-ViT-B-32"
BATCH_SIZE = 16


def find_csv() -> Path:
    for p in CSV_CANDIDATES:
        if p.exists():
            return p
    raise FileNotFoundError("未找到 wallpapers.csv，请确认路径（根目录或 data/ 下）。")


def load_wallpapers(csv_path: Path) -> List[Tuple[str, str]]:
    rows = []
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            wid = row.get("id") or row.get("wallpaperId")
            path = row.get("path") or ""
            if wid:
                rows.append((wid.strip(), path.strip()))
    return rows


def local_image_path(wid: str) -> Optional[Path]:
    # 优先匹配 data/images/{id}.*
    candidates = glob.glob(str(IMAGES_DIR / f"{wid}.*"))
    if candidates:
        return Path(candidates[0])
    return None


def load_image_safe(img_path: Path) -> Optional[Image.Image]:
    try:
        return Image.open(img_path).convert("RGB")
    except Exception:
        return None


def chunked(iterable, size):
    buf = []
    for item in iterable:
        buf.append(item)
        if len(buf) == size:
            yield buf
            buf = []
    if buf:
        yield buf


def main():
    csv_path = find_csv()
    wallpapers = load_wallpapers(csv_path)
    print(f"读取到 {len(wallpapers)} 条记录，开始匹配本地图片…")

    pairs = []
    for wid, _ in wallpapers:
        img_path = local_image_path(wid)
        if img_path and img_path.exists():
            pairs.append((wid, img_path))
    print(f"找到本地图片 {len(pairs)} 条，将生成 Embedding")

    if not pairs:
        print("未找到任何本地图片，请先下载图片到 data/images/。")
        return

    model = SentenceTransformer(MODEL_NAME)
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)

    with open(OUTPUT_PATH, "w", encoding="utf-8") as out_f:
        for batch in chunked(pairs, BATCH_SIZE):
            ids = []
            images = []
            for wid, img_path in batch:
                img = load_image_safe(img_path)
                if img is None:
                    continue
                ids.append(wid)
                images.append(img)

            if not images:
                continue

            embs = model.encode(images, batch_size=len(images), convert_to_numpy=True, show_progress_bar=False)
            for wid, emb in zip(ids, embs):
                emb_str = " ".join(f"{x:.6f}" for x in emb.tolist())
                out_f.write(f"{wid}:{emb_str}\n")

    print(f"已生成 Embedding：{OUTPUT_PATH}")


if __name__ == "__main__":
    main()

