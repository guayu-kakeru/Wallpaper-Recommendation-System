# 智能壁纸推荐系统（Wallpaper Recommendation System）

基于 Java 的智能壁纸推荐系统：提供 **Web UI + 多模型个性化推荐 + 相似推荐 + 场景/时间推荐 + 智能搜索 + 离线评测**。

---

**Author**: 掛鱼kakeru & SakuraQ

---
![Project_demo](readme_data/project_demo.png)

## 目录

- [项目亮点](#项目亮点)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始](#快速开始)
- [Web 页面使用说明](#web-页面使用说明)
- [HTTP API](#http-api)
- [数据格式与对齐规则](#数据格式与对齐规则)
- [离线评测](#离线评测)
- [数据/脚本工具](#数据脚本工具)
- [许可证与致谢](#许可证与致谢)

---

## 项目亮点

- **可演示**：启动后直接打开网页即可体验推荐、搜索、相似、场景、换一批等交互
- **可对比**：个性化推荐支持 `emb / itemcf / popularity` 三类模型，并支持离线指标输出
- **更工程化**：统一入口、清晰分层（data manager / rec process / service / webroot）
- **更“产品化”**：模型切换持久化、骨架屏加载、按钮激活态、粒子动效背景

---

## 技术栈

- **后端**：Java 8+、Jetty、Jackson
- **前端**：原生 HTML/CSS/JS（无框架）、Canvas 粒子动效背景
- **构建**：Maven（`maven-assembly-plugin` 打包 `jar-with-dependencies`；`exec-maven-plugin` 支持直接运行 mainClass）
- **数据**：CSV（壁纸元数据、评分、embedding）

---

## 项目结构

> 仅列关键文件，路径与当前仓库保持一致。

```
wallpaper recommendation/
├── src/
│   └── main/
│       ├── java/com/wallpaperrecsys/
│       │   ├── Main.java                        # 命令行演示入口
│       │   ├── WallpaperServer.java              # Jetty Web 服务（静态页 + /api）
│       │   ├── datamanager/                      # 数据加载/索引：CSV -> 内存结构
│       │   ├── model/Embedding.java              # 向量与相似度
│       │   ├── recprocess/                       # 推荐逻辑（个性化/相似/场景/时间）
│       │   ├── service/                          # 搜索/embedding 服务
│       │   ├── eval/OfflineEvalMain.java         # 离线评测入口（指标对比输出 CSV）
│       │   └── util/Config.java                  # 默认数据路径/配置
│       └── resources/webroot/                    # 前端静态资源
│           ├── index.html
│           ├── css/style.css
│           └── js/
│               ├── wallpaper.js                  # 前端主逻辑（模型切换/换一批/骨架屏等）
│               └── particles.js                  # 黑色炫酷粒子背景（随鼠标动）
│ 
├── scripts/                                      # 数据抓取/生成/处理脚本
├── reports/                                      # 离线评测输出（CSV）
├── data/                                         # 数据文件（默认路径见 Config.java）
│   └── images/                                   # 可选：下载的壁纸图片（用于本地缓存/展示）
├── pom.xml
└── README.md
```

---

## 快速开始

### 环境要求

- JDK 8+（建议 17/21/25 也可）
- Maven 3.6+（用于重新打包 jar；也可在 IntelliJ IDEA 内用 Maven）

### 1）构建（推荐）

在项目根目录：

```bash
mvn -DskipTests clean package
```

构建成功后会生成：
- `target/wallpaper-recommendation-system-1.0-SNAPSHOT-jar-with-dependencies.jar`

### 2）启动 Web Server

```bash
java -cp target/wallpaper-recommendation-system-1.0-SNAPSHOT-jar-with-dependencies.jar com.wallpaperrecsys.WallpaperServer
```

浏览器打开：`http://localhost:6020/`

> 端口被占用时会自动尝试 6021/6022/...，以控制台输出为准。

### 3）运行离线评测（输出对比表）

```bash
java -cp target/wallpaper-recommendation-system-1.0-SNAPSHOT-jar-with-dependencies.jar \
  com.wallpaperrecsys.eval.OfflineEvalMain --k=10 --like=4.0 --leaveOut=1 --minTrain=3 --reportDir=reports
```

### 4）可选：用 Maven 直接运行 mainClass

- 启动 Web：

```bash
mvn -DskipTests exec:java -Dexec.mainClass=com.wallpaperrecsys.WallpaperServer
```

- 离线评测：

```bash
mvn -DskipTests exec:java -Dexec.mainClass=com.wallpaperrecsys.eval.OfflineEvalMain \
  -Dexec.args="--k=10 --like=4.0 --leaveOut=1 --minTrain=3 --reportDir=reports"
```

---

## Web 页面使用说明

- **模型切换**：页面顶部可选 `Embedding / ItemCF / Popularity`
  - 选择会写入浏览器 `localStorage`，刷新不丢
- **换一批**：推荐区域标题旁提供「换一批」
  - 后端使用 `page` 参数分页返回，前端递增 `page` 实现换批
- **场景按钮**：工作 / 夜间氛围 / 游戏 / 阅读 / 睡前
  - “夜间氛围”固定走 `night` 场景
- **粒子动效背景**：黑色炫酷粒子 + 连线，随鼠标轻微扰动

---

## HTTP API

服务启动后默认挂载：`/api/*`

- **个性化推荐**：`GET /api/rec/personal?userId=1&size=30&model=emb|itemcf|popularity&page=0`
- **场景推荐**：`GET /api/rec/scenario?scene=work&userId=1&size=30&page=0`
- **时间推荐**：`GET /api/rec/time?userId=1&size=30&page=0`
- **相似壁纸**：`GET /api/rec/similar?wallpaperId=1&size=18&model=emb`
- **搜索**：`GET /api/search?q=动漫&size=40`

参数说明：
- `size`：每页条数
- `page`：第几页（从 0 开始；超出范围会自动回到第一页，便于“循环换一批”）

---

## 数据格式与对齐规则

### 壁纸元数据：`data/wallpapers.csv`
当前实际格式：

```csv
id,path,thumb,resolution,colors,tags
```

字段含义：
- `id`：外部字符串 ID（例如 wallhaven 的 `dpqdxj`）
- `path`：原图 URL
- `thumb`：缩略图 URL
- `resolution`：如 `3648x2736`
- `colors`：如 `#000000|#424153|...`
- `tags`：如 `风景|城市|写实`

### 评分数据：`data/ratings.csv`

```csv
userId,wallpaperId,rating,timestamp
```

> **重要对齐规则**：系统按 `wallpapers.csv` 的行顺序给每张壁纸分配内部数值 `wallpaperId = 1..N`。因此 `ratings.csv` 中的 `wallpaperId` 必须使用该内部编号，而不是外部字符串 `id`。

### 壁纸向量：`data/wallpaper_embeddings.csv`

```
externalId:0.1 0.2 0.3 ...
```

> key 是 `externalId`（字符串），与 `wallpapers.csv` 的 `id` 对齐。

### 用户向量：`data/user_embeddings.csv`

```
userId:0.1 0.2 0.3 ...
```

---

## 离线评测

入口：`com.wallpaperrecsys.eval.OfflineEvalMain`

评测策略（简述）：
- 按用户时间戳切分（Time-based split）：每个用户留出最后 `leaveOut` 条为测试集
- 正例定义：`rating >= like`（默认 4.0）
- 输出指标：`Precision@K / Recall@K / NDCG@K / Hit@K`
- 输出文件：`reports/offline_eval_k{K}_like{threshold}.csv`

> 提示：如果数据很少或正例稀疏，可降低 `--minTrain` 或 `--like`。

---

## 数据/脚本工具

脚本目录：`scripts/`

### 1）壁纸抓取与下载：`scripts/download_wallpapers.py`

用途：从 Wallhaven API 抓取壁纸元数据并下载原图到本地。

依赖：

```bash
pip install requests
```

运行（在项目根目录）：

```bash
python3 scripts/download_wallpapers.py
```

输出：
- 元数据 CSV：`data/wallpapers.csv`
- 图片目录：`data/images/`（文件名为壁纸 `id`，保留扩展名）

可在脚本顶部调整：
- `SEARCH_KEYWORDS`：关键词列表
- `PAGES / PER_PAGE`：抓取数量与速度

### 2）生成壁纸 CLIP Embedding：`scripts/gen_clip_embeddings.py`

用途：为本地图片生成 CLIP 向量，供后端 `emb` 推荐与相似推荐使用。

依赖：

```bash
pip install sentence-transformers Pillow torch
```

运行（在项目根目录）：

```bash
python3 scripts/gen_clip_embeddings.py
```

输入：
- `data/wallpapers.csv`（或根目录 `wallpapers.csv`，脚本会自动探测）
- 本地图片目录：`data/images/`

输出：
- `data/wallpaper_embeddings.csv`（格式：`externalId:val1 val2 ...`）

### 3）自动语义打标（写回 tags）：`scripts/gen_auto_tags.py`

用途：根据 `data/wallpaper_embeddings.csv`，为壁纸自动打中文语义标签（如“动漫/风景/城市/太空/抽象”等），增强搜索与召回效果。

依赖：

```bash
pip install sentence-transformers numpy
```

运行（在项目根目录）：

```bash
python3 scripts/gen_auto_tags.py
```

输入：
- `data/wallpapers.csv`
- `data/wallpaper_embeddings.csv`

输出：
- 覆盖更新 `data/wallpapers.csv` 的 `tags` 列（会把自动标签写回）

> 建议先运行 `gen_clip_embeddings.py` 再运行本脚本。

### 4）生成合成评分（推荐用于答辩展示/评测）：`scripts/gen_synthetic_ratings.py`

用途：快速扩容评分数据到指定条数，并同步生成可用的 `user_embeddings.csv`（保证 `emb` 个性化可跑、模型对比更明显）。

运行示例：

```bash
python3 scripts/gen_synthetic_ratings.py --num_ratings 10000 --num_users 200 --seed 42
```

会生成：
- `data/ratings.csv`（自动备份旧文件为 `*.bak_YYYYMMDD_HHMMSS`）
- `data/user_embeddings.csv`（用高分壁纸 embedding 的均值合成用户向量）

生成后请重启服务让后端重新加载数据。

其余脚本（按你现有实现）：
- `scripts/wallhaven_fetch.py`
- `scripts/download_wallpapers.py`（下载到 `data/images/`）
- `scripts/gen_auto_tags.py`
- `scripts/gen_clip_embeddings.py`

---

## 许可证与致谢

本项目为厦门大学信息学院学科实践3课程实践性质 Demo，参考 Sparrow RecSys 的分层思路实现。<br>

**Author**: 掛鱼kakeru & SakuraQ

