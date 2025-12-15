#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Generate synthetic ratings.csv (and user_embeddings.csv) for demo/eval.

目标：
- 把 data/ratings.csv 扩容到指定条数（默认 10000）
- 同时生成 data/user_embeddings.csv：用用户高分壁纸的向量均值合成（使 emb 模型对新用户也可用）

用法（在项目根目录）：
  python3 scripts/gen_synthetic_ratings.py --num_ratings 10000 --num_users 200
"""

from __future__ import annotations

import argparse
import csv
import os
import random
import shutil
import time
from collections import Counter, defaultdict
from typing import Dict, List, Tuple, Optional


def clamp(x: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, x))


def round_to_half(x: float) -> float:
    return round(x * 2.0) / 2.0


def backup_if_exists(path: str) -> Optional[str]:
    if not os.path.exists(path):
        return None
    ts = time.strftime("%Y%m%d_%H%M%S")
    bak = f"{path}.bak_{ts}"
    shutil.copy2(path, bak)
    return bak


def read_wallpapers(wallpapers_csv: str) -> Tuple[Dict[int, str], Dict[int, List[str]], Dict[str, List[int]]]:
    """
    Returns:
      - id_to_external: internal wallpaperId (1..N) -> externalId (string)
      - id_to_tags: internal wallpaperId -> tags list
      - tag_to_ids: tag -> list[wallpaperId]
    """
    id_to_external: Dict[int, str] = {}
    id_to_tags: Dict[int, List[str]] = {}
    tag_to_ids: Dict[str, List[int]] = defaultdict(list)

    with open(wallpapers_csv, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        internal_id = 0
        for row in reader:
            internal_id += 1
            external_id = (row.get("id") or "").strip()
            tags_raw = (row.get("tags") or "").strip()
            tags = [t.strip() for t in tags_raw.split("|") if t.strip()]

            id_to_external[internal_id] = external_id
            id_to_tags[internal_id] = tags
            for t in tags:
                tag_to_ids[t].append(internal_id)

    return id_to_external, id_to_tags, tag_to_ids


def read_wallpaper_embeddings(path: str) -> Dict[str, List[float]]:
    """
    wallpaper_embeddings.csv format:
      externalId:0.1 0.2 0.3 ...
    """
    m: Dict[str, List[float]] = {}
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            if ":" not in line:
                continue
            k, v = line.split(":", 1)
            k = k.strip()
            vec = []
            for s in v.strip().split():
                try:
                    vec.append(float(s))
                except ValueError:
                    pass
            if vec:
                m[k] = vec
    return m


def weighted_choice(items: List[str], weights: List[float]) -> str:
    # random.choices is available in py3.6+
    return random.choices(items, weights=weights, k=1)[0]


def build_tag_sampling(tag_to_ids: Dict[str, List[int]]) -> Tuple[List[str], List[float]]:
    counter = Counter({t: len(ids) for t, ids in tag_to_ids.items() if ids})
    tags = list(counter.keys())
    # 频次越高权重越大，但做一个 0.75 次幂，避免头部太集中
    weights = [float(counter[t]) ** 0.75 for t in tags]
    return tags, weights


def pick_user_preferences(all_tags: List[str], all_weights: List[float]) -> List[str]:
    # 每个用户偏好 3~6 个标签（可能重复，下面去重）
    k = random.randint(3, 6)
    picked = [weighted_choice(all_tags, all_weights) for _ in range(k)]
    # 去重但保序
    seen = set()
    prefs = []
    for t in picked:
        if t not in seen:
            prefs.append(t)
            seen.add(t)
    return prefs


def sample_items_for_user(
    prefs: List[str],
    tag_to_ids: Dict[str, List[int]],
    total_items: int,
    n_interactions: int,
) -> List[int]:
    # 70% 从偏好标签的并集中采样，30% 随机探索
    pool: List[int] = []
    for t in prefs:
        pool.extend(tag_to_ids.get(t, []))
    pool = list(dict.fromkeys(pool))  # 去重

    chosen: List[int] = []
    chosen_set = set()

    def pick_from_pool() -> int:
        if pool:
            return random.choice(pool)
        return random.randint(1, total_items)

    while len(chosen) < n_interactions:
        if random.random() < 0.7:
            wid = pick_from_pool()
        else:
            wid = random.randint(1, total_items)
        if wid in chosen_set:
            continue
        chosen.append(wid)
        chosen_set.add(wid)
    return chosen


def score_for_item(prefs: List[str], item_tags: List[str]) -> float:
    if not prefs:
        base = 3.0
        noise = random.uniform(-1.2, 1.2)
        return round_to_half(clamp(base + noise, 1.0, 5.0))

    overlap = 0
    item_tag_set = set(item_tags or [])
    for t in prefs:
        if t in item_tag_set:
            overlap += 1

    # overlap 越多，评分越高；并加入噪声
    # 0 overlap -> around 2.5~3.5
    # 1 overlap -> around 3.5~4.5
    # 2+ overlap -> around 4.0~5.0
    base = 2.8 + 1.0 * overlap
    noise = random.uniform(-0.9, 0.9)
    return round_to_half(clamp(base + noise, 1.0, 5.0))


def avg_vectors(vectors: List[List[float]]) -> Optional[List[float]]:
    if not vectors:
        return None
    dim = len(vectors[0])
    if any(len(v) != dim for v in vectors):
        return None
    out = [0.0] * dim
    for v in vectors:
        for i in range(dim):
            out[i] += v[i]
    n = float(len(vectors))
    return [x / n for x in out]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--num_ratings", type=int, default=10000)
    ap.add_argument("--num_users", type=int, default=200)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--wallpapers_csv", default="data/wallpapers.csv")
    ap.add_argument("--wallpaper_emb_csv", default="data/wallpaper_embeddings.csv")
    ap.add_argument("--out_ratings_csv", default="data/ratings.csv")
    ap.add_argument("--out_user_emb", default="data/user_embeddings.csv")
    ap.add_argument("--like_threshold", type=float, default=4.0)
    ap.add_argument("--start_ts", type=int, default=1700000000)
    args = ap.parse_args()

    random.seed(args.seed)

    id_to_external, id_to_tags, tag_to_ids = read_wallpapers(args.wallpapers_csv)
    total_items = len(id_to_external)
    if total_items <= 0:
        raise RuntimeError("wallpapers.csv 读取失败：没有壁纸数据")

    all_tags, all_weights = build_tag_sampling(tag_to_ids)
    if not all_tags:
        # 没有标签就走纯随机
        all_tags = ["__random__"]
        all_weights = [1.0]

    # 每个用户交互数分配：基础均匀 + 少量抖动
    base = args.num_ratings // args.num_users
    rem = args.num_ratings % args.num_users

    wallpaper_emb = read_wallpaper_embeddings(args.wallpaper_emb_csv) if os.path.exists(args.wallpaper_emb_csv) else {}

    # 备份旧文件
    bak_r = backup_if_exists(args.out_ratings_csv)
    bak_u = backup_if_exists(args.out_user_emb)
    if bak_r:
        print(f"[backup] {args.out_ratings_csv} -> {bak_r}")
    if bak_u:
        print(f"[backup] {args.out_user_emb} -> {bak_u}")

    # 生成 ratings
    records: List[Tuple[int, int, float, int]] = []
    user_to_items: Dict[int, List[Tuple[int, float]]] = defaultdict(list)

    for ui in range(args.num_users):
        user_id = ui + 1
        n = base + (1 if ui < rem else 0)
        # 抖动：让不同用户交互数不完全一致（但保证总量不变）
        if n >= 10:
            jitter = random.randint(-5, 5)
            n = max(5, n + jitter)

        prefs = pick_user_preferences(all_tags, all_weights)
        items = sample_items_for_user(prefs, tag_to_ids, total_items, n)

        # 时间戳：每个用户从不同起点开始，按交互递增
        ts = args.start_ts + ui * 86400  # 用户间错开一天
        for idx, wid in enumerate(items):
            # 每条交互间隔 5min~6h
            ts += random.randint(300, 21600)
            rating = score_for_item(prefs, id_to_tags.get(wid, []))
            records.append((user_id, wid, rating, ts))
            user_to_items[user_id].append((wid, rating))

    # 调整总量到精确 num_ratings（因为 jitter 可能带偏）
    if len(records) > args.num_ratings:
        records = records[: args.num_ratings]
    elif len(records) < args.num_ratings:
        need = args.num_ratings - len(records)
        # 从最后一个用户补齐
        user_id = args.num_users
        ts = records[-1][3] if records else args.start_ts
        prefs = pick_user_preferences(all_tags, all_weights)
        existing = {wid for (wid, _) in user_to_items.get(user_id, [])}
        while need > 0:
            wid = random.randint(1, total_items)
            if wid in existing:
                continue
            existing.add(wid)
            ts += random.randint(300, 21600)
            rating = score_for_item(prefs, id_to_tags.get(wid, []))
            records.append((user_id, wid, rating, ts))
            user_to_items[user_id].append((wid, rating))
            need -= 1

    # 写 ratings.csv
    os.makedirs(os.path.dirname(args.out_ratings_csv) or ".", exist_ok=True)
    with open(args.out_ratings_csv, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["userId", "wallpaperId", "rating", "timestamp"])
        for user_id, wid, rating, ts in records:
            w.writerow([user_id, wid, f"{rating:.1f}", ts])

    print(f"[ok] wrote {args.out_ratings_csv}: {len(records)} ratings, users={args.num_users}, items={total_items}")

    # 生成 user_embeddings.csv（用高分壁纸 embedding 均值）
    # internal wallpaperId -> externalId -> vector
    user_emb_lines: List[str] = []
    for user_id in range(1, args.num_users + 1):
        rated = user_to_items.get(user_id, [])
        liked_ids = [wid for (wid, r) in rated if r >= args.like_threshold]
        # 如果没有高分，用全部评分 >=3.5 的近似偏好
        if not liked_ids:
            liked_ids = [wid for (wid, r) in rated if r >= 3.5]
        vecs: List[List[float]] = []
        for wid in liked_ids:
            ext = id_to_external.get(wid)
            if not ext:
                continue
            v = wallpaper_emb.get(ext)
            if v:
                vecs.append(v)
        avg = avg_vectors(vecs)
        if avg is None:
            continue
        user_emb_lines.append(str(user_id) + ":" + " ".join(f"{x:.6f}" for x in avg))

    os.makedirs(os.path.dirname(args.out_user_emb) or ".", exist_ok=True)
    with open(args.out_user_emb, "w", encoding="utf-8") as f:
        for line in user_emb_lines:
            f.write(line + "\n")

    print(f"[ok] wrote {args.out_user_emb}: {len(user_emb_lines)} user embeddings (like>={args.like_threshold})")


if __name__ == "__main__":
    main()


