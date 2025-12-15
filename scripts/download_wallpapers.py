#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Wallhaven 壁纸抓取脚本

功能：
- 关键词：landscape、anime
- 抓取前 5 页（约 100 张）壁纸元数据
- 保存字段到 wallpapers.csv：id, path, thumb, resolution, colors, tags
- 下载图片到 data/images/，文件名使用 ID（保留扩展名）

依赖：requests
    pip install requests
"""

import csv
import os
import time
from pathlib import Path
from typing import Dict, List
from urllib.parse import urlparse

import requests


SEARCH_KEYWORDS = ["landscape", "anime", "city", "nature", "abstract"]
# 每页条数（Wallhaven上限 24），配合关键词数和页数控制总量
PER_PAGE = 24
# 每个关键词抓取页数：5 个关键词 × 30 页 × 24/页 ≈ 3600 张，符合 3000~5000 目标
PAGES = 30
BASE_URL = "https://wallhaven.cc/api/v1/search"
OUTPUT_CSV = Path(__file__).resolve().parent.parent / "data" /"wallpapers.csv"
IMAGES_DIR = Path(__file__).resolve().parent.parent / "data" / "images"


def ensure_dirs() -> None:
    IMAGES_DIR.mkdir(parents=True, exist_ok=True)


def get_ext_from_url(url: str) -> str:
    path = urlparse(url).path
    ext = os.path.splitext(path)[1].lower()
    if ext in {".jpg", ".jpeg", ".png", ".webp"}:
        return ext
    return ".jpg"


def download_image(session: requests.Session, url: str, save_path: Path) -> bool:
    try:
        with session.get(url, stream=True, timeout=20) as r:
            r.raise_for_status()
            with open(save_path, "wb") as f:
                for chunk in r.iter_content(chunk_size=8192):
                    if chunk:
                        f.write(chunk)
        return True
    except Exception as e:
        print(f"[WARN] 下载失败 {url}: {e}")
        return False


def fetch_page(session: requests.Session, query: str, page: int) -> List[Dict]:
    params = {
        "q": query,
        "page": page,
        "sorting": "relevance",
        "per_page": PER_PAGE,
    }
    resp = session.get(BASE_URL, params=params, timeout=15)
    resp.raise_for_status()
    data = resp.json()
    return data.get("data", [])


def main():
    ensure_dirs()
    session = requests.Session()
    all_items: Dict[str, Dict] = {}

    for query in SEARCH_KEYWORDS:
        print(f"=== 抓取关键词: {query} ===")
        for page in range(1, PAGES + 1):
            try:
                items = fetch_page(session, query, page)
            except Exception as e:
                print(f"[WARN] 获取 {query} 第 {page} 页失败: {e}")
                continue

            print(f"  第 {page} 页，获取 {len(items)} 条")
            for item in items:
                wid = item.get("id")
                if not wid or wid in all_items:
                    continue

                path = item.get("path") or ""
                thumbs = item.get("thumbs", {})
                thumb_url = thumbs.get("large") or thumbs.get("original") or thumbs.get("small") or ""
                resolution = item.get("resolution") or ""
                colors = item.get("colors") or []
                tags = item.get("tags") or []
                tag_names = ",".join(t.get("name", "") for t in tags if t.get("name"))

                all_items[wid] = {
                    "id": wid,
                    "path": path,
                    "thumb": thumb_url,
                    "resolution": resolution,
                    "colors": "|".join(colors),
                    "tags": tag_names,
                }

            # 礼貌等待，避免触发限流
            time.sleep(0.5)

    print(f"总计去重后: {len(all_items)} 条")

    # 写入 CSV
    OUTPUT_CSV.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_CSV, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["id", "path", "thumb", "resolution", "colors", "tags"])
        for item in all_items.values():
            writer.writerow([
                item["id"],
                item["path"],
                item["thumb"],
                item["resolution"],
                item["colors"],
                item["tags"],
            ])
    print(f"已写入 CSV: {OUTPUT_CSV}")

    # 下载图片
    print("开始下载图片 ...")
    for item in all_items.values():
        url = item["path"]
        if not url:
            continue
        ext = get_ext_from_url(url)
        save_path = IMAGES_DIR / f"{item['id']}{ext}"
        if save_path.exists():
            continue
        download_image(session, url, save_path)
        time.sleep(0.2)  # 轻微限速

    print(f"图片已保存至: {IMAGES_DIR}")


if __name__ == "__main__":
    main()

