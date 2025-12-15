// Simple front-end logic for Wallpaper RecSys Web UI

function getBaseUrl() {
    var loc = window.location;
    return loc.protocol + '//' + loc.host + '/';
}

var baseUrl = getBaseUrl();

var MODEL_STORAGE_KEY = 'wp_model';
var BATCH_PAGE_KEY = 'wp_batch_page';
var VIEW_KEY = 'wp_view';

// 当前视图（用于“换一批”复用）
var currentView = { type: 'personal', scene: null, title: '为你推荐', subtitle: '' };

function buildTags(w) {
    var tags = [];
    if (w.tags && w.tags.length) {
        tags = w.tags;
    }
    return tags.slice(0, 3);
}

function renderGrid(title, subtitle, list) {
    var grid = document.getElementById('wallpaper-grid');
    var empty = document.getElementById('empty-hint');
    document.getElementById('section-title').textContent = title;
    document.getElementById('section-subtitle').textContent = subtitle;

    grid.innerHTML = '';

    if (!list || !list.length) {
        empty.style.display = 'block';
        return;
    }
    empty.style.display = 'none';

    list.forEach(function (w) {
        var card = document.createElement('div');
        card.className = 'wp-card';

        var img = document.createElement('img');
        img.src = w.thumbnailUrl && w.thumbnailUrl.length ? w.thumbnailUrl : w.imageUrl;
        img.alt = w.title || ('Wallpaper ' + w.wallpaperId);

        var gradient = document.createElement('div');
        gradient.className = 'wp-card-gradient';

        var bottom = document.createElement('div');
        bottom.className = 'wp-card-bottom';

        var tagsContainer = document.createElement('div');
        tagsContainer.className = 'wp-card-tags';

        var tags = buildTags(w);
        tags.forEach(function (t) {
            var span = document.createElement('span');
            span.className = 'wp-tag';
            span.textContent = t;
            tagsContainer.appendChild(span);
        });

        if (w.style) {
            var styleChip = document.createElement('span');
            styleChip.className = 'wp-chip';
            styleChip.textContent = w.style;
            tagsContainer.appendChild(styleChip);
        }
        if (w.mood) {
            var moodChip = document.createElement('span');
            moodChip.className = 'wp-chip';
            moodChip.textContent = w.mood;
            tagsContainer.appendChild(moodChip);
        }

        var titleDiv = document.createElement('div');
        titleDiv.className = 'wp-card-title';
        titleDiv.textContent = w.title || ('Wallpaper ' + w.wallpaperId);

        bottom.appendChild(tagsContainer);
        bottom.appendChild(titleDiv);

        card.appendChild(img);
        card.appendChild(gradient);
        card.appendChild(bottom);

        card.addEventListener('click', function () {
            openDetail(w);
        });

        grid.appendChild(card);
    });
}

function renderSkeleton(count) {
    var grid = document.getElementById('wallpaper-grid');
    var empty = document.getElementById('empty-hint');
    if (!grid) return;
    grid.innerHTML = '';
    if (empty) empty.style.display = 'none';
    var n = count || 12;
    for (var i = 0; i < n; i++) {
        var card = document.createElement('div');
        card.className = 'wp-card wp-skeleton-card';
        var shimmer = document.createElement('div');
        shimmer.className = 'wp-skeleton-shimmer';
        card.appendChild(shimmer);
        grid.appendChild(card);
    }
}

function setActiveTop(id) {
    var ids = ['btn-for-you', 'btn-work', 'btn-night', 'btn-gaming', 'btn-reading', 'btn-sleep'];
    ids.forEach(function (x) {
        var el = document.getElementById(x);
        if (!el) return;
        if (x === id) el.classList.add('active');
        else el.classList.remove('active');
    });
}

function setActiveCategory(id) {
    var ids = ['cat-all', 'cat-anime', 'cat-nature', 'cat-city', 'cat-space', 'cat-abstract'];
    ids.forEach(function (x) {
        var el = document.getElementById(x);
        if (!el) return;
        if (x === id) el.classList.add('active');
        else el.classList.remove('active');
    });
}

function fetchJson(url, onSuccess) {
    fetch(url)
        .then(function (res) {
            if (!res.ok) throw new Error('HTTP ' + res.status);
            return res.json();
        })
        .then(onSuccess)
        .catch(function (err) {
            console.error('Request error', err);
            renderGrid('出错了', '请求失败，请稍后重试。', []);
        });
}

function getSelectedModel() {
    var sel = document.getElementById('model-select');
    if (!sel) return 'emb';
    return sel.value || 'emb';
}

function setSelectedModel(model) {
    var sel = document.getElementById('model-select');
    if (!sel) return;
    var v = model || 'emb';
    // 仅允许这三个值，避免脏数据
    if (v !== 'emb' && v !== 'itemcf' && v !== 'popularity') {
        v = 'emb';
    }
    sel.value = v;
}

function saveSelectedModel(model) {
    try {
        window.localStorage.setItem(MODEL_STORAGE_KEY, model);
    } catch (e) {
        // ignore
    }
}

function loadSavedModel() {
    try {
        return window.localStorage.getItem(MODEL_STORAGE_KEY);
    } catch (e) {
        return null;
    }
}

function modelSubtitle(model) {
    if (model === 'itemcf') {
        return '基于协同过滤（ItemCF）：用“相似用户共同喜欢的壁纸”来做个性化推荐。';
    }
    if (model === 'popularity') {
        return '基于流行度：综合平均评分等统计特征，推荐更“热门/口碑更好”的壁纸。';
    }
    return '基于 Embedding：用用户向量与壁纸向量相似度，为你推荐可能喜欢的壁纸。';
}

function setCurrentView(view) {
    currentView = view || { type: 'personal', scene: null, title: '为你推荐', subtitle: '' };
    try {
        window.localStorage.setItem(VIEW_KEY, JSON.stringify(currentView));
    } catch (e) {}
}

function getBatchPage() {
    try {
        var v = window.localStorage.getItem(BATCH_PAGE_KEY);
        var n = parseInt(v || '0', 10);
        return isNaN(n) ? 0 : n;
    } catch (e) {
        return 0;
    }
}

function setBatchPage(p) {
    try {
        window.localStorage.setItem(BATCH_PAGE_KEY, String(p));
    } catch (e) {}
}

function resetBatchPage() {
    setBatchPage(0);
}

function loadPersonal() {
    var model = getSelectedModel();
    var page = getBatchPage();
    setActiveTop('btn-for-you');
    setActiveCategory('cat-all');
    setCurrentView({
        type: 'personal',
        scene: null,
        title: '为你推荐 · ' + model.toUpperCase(),
        subtitle: modelSubtitle(model)
    });
    renderSkeleton(18);
    fetchJson(baseUrl + 'api/rec/personal?userId=1&size=30&model=' + encodeURIComponent(model) + '&page=' + page, function (list) {
        renderGrid(currentView.title, currentView.subtitle, list);
    });
}

function loadCategory(category, label) {
    var filteredTitle = label ? ('分类：' + label) : '全部';
    setActiveTop('btn-for-you');
    fetchJson(baseUrl + 'api/search?q=' + encodeURIComponent(category) + '&size=40', function (list) {
        renderGrid(filteredTitle, '基于标签和类别筛选出的壁纸。', list);
    });
}

function loadScenario(scene, label) {
    var page = getBatchPage();
    setActiveCategory('cat-all');
    setCurrentView({
        type: 'scenario',
        scene: scene,
        title: label + ' 场景',
        subtitle: '根据场景配置推荐适合当前使用场景的壁纸。'
    });
    renderSkeleton(18);
    fetchJson(baseUrl + 'api/rec/scenario?scene=' + encodeURIComponent(scene) + '&userId=1&size=30&page=' + page, function (list) {
        renderGrid(currentView.title, currentView.subtitle, list);
    });
}

function loadTime() {
    var page = getBatchPage();
    setActiveCategory('cat-all');
    setCurrentView({
        type: 'time',
        scene: null,
        title: '时间感知推荐',
        subtitle: '根据当前时间段（早/午/晚/深夜）推荐壁纸。'
    });
    renderSkeleton(18);
    fetchJson(baseUrl + 'api/rec/time?userId=1&size=30&page=' + page, function (list) {
        renderGrid(currentView.title, currentView.subtitle, list);
    });
}

function refreshBatch() {
    // page+1 循环“换一批”
    var p = getBatchPage();
    setBatchPage(p + 1);
    if (currentView.type === 'scenario' && currentView.scene) {
        // 直接复用当前 title/subtitle，避免出现“场景 场景”
        var page = getBatchPage();
        renderSkeleton(18);
        fetchJson(baseUrl + 'api/rec/scenario?scene=' + encodeURIComponent(currentView.scene) + '&userId=1&size=30&page=' + page, function (list) {
            renderGrid(currentView.title, currentView.subtitle, list);
        });
        return;
    }
    if (currentView.type === 'time') {
        var page2 = getBatchPage();
        renderSkeleton(18);
        fetchJson(baseUrl + 'api/rec/time?userId=1&size=30&page=' + page2, function (list) {
            renderGrid(currentView.title, currentView.subtitle, list);
        });
        return;
    }
    // personal
    loadPersonal();
}

function searchWallpapers() {
    var input = document.getElementById('search-input');
    var q = input.value.trim();
    if (!q) {
        loadPersonal();
        return;
    }
    setActiveTop('btn-for-you');
    setActiveCategory(null);
    renderSkeleton(18);
    fetchJson(baseUrl + 'api/search?q=' + encodeURIComponent(q) + '&size=40', function (list) {
        renderGrid('搜索结果: ' + q, '基于语义 / 文本匹配返回与你的关键词相关的壁纸。', list);
    });
}

function openDetail(w) {
    var overlay = document.getElementById('detail-overlay');
    var main = document.getElementById('overlay-main');
    var strip = document.getElementById('similar-strip');

    main.innerHTML = '';
    strip.innerHTML = '';

    var left = document.createElement('div');
    left.className = 'wp-overlay-image';
    var img = document.createElement('img');
    img.src = w.imageUrl || w.thumbnailUrl;
    img.alt = w.title || ('Wallpaper ' + w.wallpaperId);
    left.appendChild(img);

    var right = document.createElement('div');
    var title = document.createElement('div');
    title.className = 'wp-overlay-meta-title';
    title.textContent = w.title || ('Wallpaper ' + w.wallpaperId);

    var line1 = document.createElement('div');
    line1.className = 'wp-overlay-meta-line';
    line1.textContent = 'ID: ' + w.wallpaperId;

    var line2 = document.createElement('div');
    line2.className = 'wp-overlay-meta-line';
    line2.textContent = '分辨率: ' + (w.resolutionWidth || '?') + ' x ' + (w.resolutionHeight || '?');

    var tagsBox = document.createElement('div');
    tagsBox.className = 'wp-overlay-tags';

    var tags = buildTags(w);
    tags.forEach(function (t) {
        var span = document.createElement('span');
        span.className = 'wp-tag';
        span.textContent = t;
        tagsBox.appendChild(span);
    });
    if (w.style) {
        var styleChip = document.createElement('span');
        styleChip.className = 'wp-chip';
        styleChip.textContent = '风格: ' + w.style;
        tagsBox.appendChild(styleChip);
    }
    if (w.mood) {
        var moodChip = document.createElement('span');
        moodChip.className = 'wp-chip';
        moodChip.textContent = '情绪: ' + w.mood;
        tagsBox.appendChild(moodChip);
    }

    right.appendChild(title);
    right.appendChild(line1);
    right.appendChild(line2);
    right.appendChild(tagsBox);

    main.appendChild(left);
    main.appendChild(right);

    overlay.style.display = 'flex';

    // load similar wallpapers
    fetchJson(baseUrl + 'api/rec/similar?wallpaperId=' + w.wallpaperId + '&size=18&model=emb', function (list) {
        strip.innerHTML = '';
        if (!list || !list.length) {
            return;
        }
        list.forEach(function (sw) {
            var sc = document.createElement('div');
            sc.className = 'wp-strip-card';
            var sImg = document.createElement('img');
            sImg.src = sw.thumbnailUrl && sw.thumbnailUrl.length ? sw.thumbnailUrl : sw.imageUrl;
            sImg.alt = sw.title || ('Wallpaper ' + sw.wallpaperId);

            var st = document.createElement('div');
            st.className = 'wp-strip-card-title';
            st.textContent = sw.title || ('Wallpaper ' + sw.wallpaperId);

            sc.appendChild(sImg);
            sc.appendChild(st);

            sc.addEventListener('click', function () {
                openDetail(sw);
            });
            strip.appendChild(sc);
        });
    });
}

function closeDetail() {
    var overlay = document.getElementById('detail-overlay');
    overlay.style.display = 'none';
}

document.addEventListener('DOMContentLoaded', function () {
    // 恢复上次选择的模型（刷新不丢）
    var saved = loadSavedModel();
    if (saved) {
        setSelectedModel(saved);
    }

    document.getElementById('search-btn').addEventListener('click', searchWallpapers);
    document.getElementById('search-input').addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
            searchWallpapers();
        }
    });
    document.getElementById('btn-for-you').addEventListener('click', loadPersonal);
    var modelSelect = document.getElementById('model-select');
    if (modelSelect) {
        modelSelect.addEventListener('change', function () {
            saveSelectedModel(getSelectedModel());
            loadPersonal();
        });
    }
    document.getElementById('btn-work').addEventListener('click', function () {
        resetBatchPage();
        setActiveTop('btn-work');
        loadScenario('work', '工作');
    });
    document.getElementById('btn-night').addEventListener('click', function () {
        // “夜间氛围”应固定走 night 场景，而不是随时间变化
        resetBatchPage();
        setActiveTop('btn-night');
        loadScenario('night', '夜间氛围');
    });
    document.getElementById('btn-gaming').addEventListener('click', function () {
        resetBatchPage();
        setActiveTop('btn-gaming');
        loadScenario('gaming', '游戏');
    });
    document.getElementById('btn-reading').addEventListener('click', function () {
        resetBatchPage();
        setActiveTop('btn-reading');
        loadScenario('reading', '阅读');
    });
    document.getElementById('btn-sleep').addEventListener('click', function () {
        resetBatchPage();
        setActiveTop('btn-sleep');
        loadScenario('sleep', '睡前');
    });

    // 分类筛选
    document.getElementById('cat-all').addEventListener('click', loadPersonal);
    document.getElementById('cat-anime').addEventListener('click', function () {
        setActiveCategory('cat-anime');
        loadCategory('动漫', '动漫');
    });
    document.getElementById('cat-nature').addEventListener('click', function () {
        setActiveCategory('cat-nature');
        loadCategory('风景', '风景');
    });
    document.getElementById('cat-city').addEventListener('click', function () {
        setActiveCategory('cat-city');
        loadCategory('城市', '城市');
    });
    document.getElementById('cat-space').addEventListener('click', function () {
        setActiveCategory('cat-space');
        loadCategory('太空', '太空');
    });
    document.getElementById('cat-abstract').addEventListener('click', function () {
        setActiveCategory('cat-abstract');
        loadCategory('抽象', '抽象');
    });
    document.getElementById('overlay-close').addEventListener('click', closeDetail);
    document.getElementById('detail-overlay').addEventListener('click', function (e) {
        if (e.target === this) {
            closeDetail();
        }
    });

    var refreshBtn = document.getElementById('btn-refresh-batch');
    if (refreshBtn) {
        refreshBtn.addEventListener('click', function () {
            refreshBatch();
        });
    }

    // 默认加载个性化推荐
    resetBatchPage();
    loadPersonal();
});


