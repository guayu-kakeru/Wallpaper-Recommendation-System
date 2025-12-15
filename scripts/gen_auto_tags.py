#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
根据已有的 CLIP 向量（wallpaper_embeddings.csv），
为壁纸自动打「标签/类别/风格/情绪」等语义标签，并写回 wallpapers.csv 的 tags 列。

效果：
- 搜索「动漫」等关键词时，可以命中对应壁纸
- SimilarWallpaperProcess 可以用标签做候选召回
- 场景推荐可以根据标签间接推断类别/风格等

使用方式（在 wallpaper 目录下）：
    python scripts/gen_auto_tags.py

依赖：
    pip install sentence-transformers numpy
"""

import csv
import math
from pathlib import Path
from typing import Dict, List, Tuple

import numpy as np
from sentence_transformers import SentenceTransformer

ROOT = Path(__file__).resolve().parent.parent
CSV_PATH = ROOT / "data" / "wallpapers.csv"
EMB_PATH = ROOT / "data" / "wallpaper_embeddings.csv"

# 相似度阈值与每类最大标签数
SIM_THRESHOLD = 0.22
MAX_TAGS_PER_GROUP = 2

# 预设标签文本，会被编码成 CLIP 文本向量
TEXT_LABEL_GROUPS = {
    "anime": [
        "动漫壁纸", "二次元", "anime style illustration", "卡通人物", "日系动漫"
    ],
    "nature": [
        "自然风景", "山脉", "森林", "湖泊", "草地", "海边", "日出日落"
    ],
    "city": [
        "城市夜景", "城市建筑", "街景", "摩天大楼"
    ],
    "space": [
        "星空", "宇宙", "银河", "太空", "科幻太空"
    ],
    "abstract": [
        "抽象艺术", "极简壁纸", "几何图形"
    ],
    "style": [
        "写实摄影", "插画风格", "极简风格", "霓虹赛博朋克"
    ],
    "mood": [
        "宁静氛围", "平静舒缓", "活力热烈", "暗黑氛围", "温暖舒适", "冷色系"
    ],
}

# 将上面的文本映射为 tags 中使用的短标签
GROUP_TO_TAG_MAP: Dict[str, str] = {
    "anime": "动漫",
    "nature": "风景",
    "city": "城市",
    "space": "太空",
    "abstract": "抽象",
}


def load_image_embeddings(path: Path) -> Dict[str, np.ndarray]:
    emb_dict: Dict[str, np.ndarray] = {}
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                wid, vec_str = line.split(":", 1)
            except ValueError:
                continue
            vals = [float(x) for x in vec_str.strip().split()]
            emb_dict[wid] = np.asarray(vals, dtype="float32")
    return emb_dict


def normalize(v: np.ndarray) -> np.ndarray:
    norm = np.linalg.norm(v)
    if norm == 0:
        return v
    return v / norm


def encode_text_labels(model: SentenceTransformer) -> Dict[str, List[Tuple[str, np.ndarray]]]:
    text_embs: Dict[str, List[Tuple[str, np.ndarray]]] = {}
    for group, texts in TEXT_LABEL_GROUPS.items():
        embs = model.encode(texts, convert_to_numpy=True, show_progress_bar=False)
        embs = np.asarray([normalize(e) for e in embs], dtype="float32")
        text_embs[group] = list(zip(texts, embs))
    return text_embs


def infer_tags_for_image(
    img_emb: np.ndarray,
    text_embs: Dict[str, List[Tuple[str, np.ndarray]]],
) -> List[str]:
    img_emb = normalize(img_emb)
    tags: List[str] = []

    for group, label_embs in text_embs.items():
        scores: List[Tuple[float, str]] = []
        for text, t_emb in label_embs:
            sim = float(np.dot(img_emb, t_emb))
            scores.append((sim, text))

        scores.sort(reverse=True, key=lambda x: x[0])

        # 选出该 group 下相似度最高的几个标签
        selected = [
            text for sim, text in scores[:MAX_TAGS_PER_GROUP] if sim >= SIM_THRESHOLD
        ]
        # 映射成简短 tags
        if group in GROUP_TO_TAG_MAP and selected:
            tags.append(GROUP_TO_TAG_MAP[group])

        # style / mood 直接保留中文短语
        if group == "style":
            for text in selected:
                if "写实" in text:
                    tags.append("写实")
                elif "插画" in text:
                    tags.append("插画")
                elif "极简" in text:
                    tags.append("极简")
                elif "霓虹" in text or "赛博朋克" in text:
                    tags.append("赛博朋克")
        if group == "mood":
            for text in selected:
                if "宁静" in text or "平静" in text:
                    tags.append("宁静")
                elif "活力" in text or "热烈" in text:
                    tags.append("活力")
                elif "暗黑" in text:
                    tags.append("暗黑")
                elif "温暖" in text:
                    tags.append("温暖")
                elif "冷色" in text:
                    tags.append("冷色")

    # 去重
    dedup: List[str] = []
    for t in tags:
        if t not in dedup:
            dedup.append(t)
    return dedup


def main() -> None:
    if not CSV_PATH.exists():
        raise FileNotFoundError(f"未找到 {CSV_PATH}")
    if not EMB_PATH.exists():
        raise FileNotFoundError(f"未找到 {EMB_PATH}，请先运行 gen_clip_embeddings.py 生成向量。")

    print("加载图片 Embedding ...")
    img_embs = load_image_embeddings(EMB_PATH)
    print(f"共加载 {len(img_embs)} 条向量")

    print("加载 CLIP 文本模型 clip-ViT-B-32 ...")
    model = SentenceTransformer("clip-ViT-B-32")
    text_embs = encode_text_labels(model)

    tmp_path = CSV_PATH.with_suffix(".with_tags.tmp.csv")

    print("开始为每张壁纸自动打标签 ...")
    with CSV_PATH.open("r", encoding="utf-8") as in_f, \
            tmp_path.open("w", encoding="utf-8", newline="") as out_f:
        reader = csv.DictReader(in_f)
        fieldnames = reader.fieldnames or ["id", "path", "thumb", "resolution", "colors", "tags"]
        writer = csv.DictWriter(out_f, fieldnames=fieldnames)
        writer.writeheader()

        row_count = 0
        for row in reader:
            row_count += 1
            wid = (row.get("id") or row.get("wallpaperId") or "").strip()
            if not wid or wid not in img_embs:
                writer.writerow(row)
                continue

            auto_tags = infer_tags_for_image(img_embs[wid], text_embs)
            existing_tags = row.get("tags", "") or ""
            if existing_tags.strip():
                # 合并原有 tags
                merged = existing_tags.split("|") + auto_tags
            else:
                merged = auto_tags

            # 去重并拼回字符串
            final_tags: List[str] = []
            for t in merged:
                t = t.strip()
                if t and t not in final_tags:
                    final_tags.append(t)
            row["tags"] = "|".join(final_tags)
            writer.writerow(row)

    print(f"已写入带标签文件：{tmp_path.name}，将覆盖原 wallpapers.csv")
    tmp_path.replace(CSV_PATH)
    print("完成！重新启动 Java 服务后，前端即可看到新的标签与相似推荐效果。")


if __name__ == "__main__":
    main()


