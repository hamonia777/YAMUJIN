/* ============================================================
   MUJIN · main views
   ============================================================ */

import { marketStrip, trumpCard } from './market.js';
import {
  $, $$, el, esc, num, api, md, toast, auth, openAuth, bookmark, logout,
  observeReveals, stagger, countTo, bindSheen, sentimentChip, skeletonGrid,
  sectionHead, streamBriefing, streamAsk, toggleTheme,
} from './core.js';

/* ------------------------------------------------------------ shared pieces */

export function newsCard(a, i = 0) {
  const s = sentimentChip(a.sentiment);
  const card = el('article', { class: 'card lift news-card', 'data-reveal': '', style: { '--d': `${(i % 12) * 45}ms` } },
    el('div', { class: 'meta' },
      el('span', { class: 'flag' }, a.flag || '🌐'),
      el('span', { class: 'src' }, a.source),
      el('span', {}, '·'),
      el('span', {}, a.relative),
      a.category ? el('span', { class: 'chip' }, a.category) : null),
    el('h4', {}, a.title),
    a.snippet ? el('p', { class: 'snip' }, a.snippet) : null,
    el('div', { class: 'tail' },
      el('span', { class: `chip ${s.cls}` }, s.text),
      el('span', { class: 'heat-bar' }, el('i', { style: { width: '0%' } })),
      el('span', { class: 'chip gold' }, `🔥 ${Math.round(a.heat)}`),
      el('button', {
        class: 'chip accent',
        title: '스크랩',
        onclick: (e) => {
          e.stopPropagation();
          bookmark({ kind: 'NEWS', title: a.title, url: a.link, source: a.source, memo: a.snippet });
        },
      }, '＋ 스크랩'),
      el('button', {
        class: 'chip',
        title: '이 이슈 프리즘 분석',
        onclick: (e) => { e.stopPropagation(); location.hash = `#/prism?q=${encodeURIComponent(a.keywords?.[0] || a.title.slice(0, 12))}`; },
      }, '◭ 프리즘')));

  card.addEventListener('click', () => window.open(a.link, '_blank', 'noopener'));
  requestAnimationFrame(() => {
    const bar = $('.heat-bar i', card);
    if (bar) setTimeout(() => { bar.style.width = `${Math.min(100, a.heat)}%`; }, 200 + (i % 12) * 45);
  });
  return card;
}

/** Region bubbles on an equirectangular graticule. */
function worldMap(pulse) {
  const COORD = {
    KR: [127.0, 30.5], US: [-98, 39], EU: [10, 50], JP: [147, 40], CN: [100, 30], WORLD: [-40, -18],
  };
  // crop to the latitude band that actually carries newsrooms - the poles were dead space
  const W = 900, H = 300;
  const LAT_TOP = 72, LAT_BOTTOM = -40;
  const proj = ([lon, lat]) =>
    [((lon + 180) / 360) * W, ((LAT_TOP - lat) / (LAT_TOP - LAT_BOTTOM)) * H];
  const maxCount = Math.max(1, ...pulse.map((p) => p.count));

  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  svg.setAttribute('viewBox', `0 0 ${W} ${H}`);
  svg.setAttribute('role', 'img');
  svg.setAttribute('aria-label', '권역별 뉴스 밀도 지도');

  const mk = (tag, attrs = {}) => {
    const n = document.createElementNS(ns, tag);
    for (const [k, v] of Object.entries(attrs)) n.setAttribute(k, v);
    return n;
  };

  // graticule
  for (let lon = -180; lon <= 180; lon += 30) {
    const [x] = proj([lon, 0]);
    svg.append(mk('line', { x1: x, y1: 0, x2: x, y2: H, stroke: 'currentColor', 'stroke-width': .5, opacity: .16 }));
  }
  for (let lat = -30; lat <= 60; lat += 30) {
    const [, y] = proj([0, lat]);
    svg.append(mk('line', { x1: 0, y1: y, x2: W, y2: y, stroke: 'currentColor', 'stroke-width': .5, opacity: .16 }));
  }
  const [, eq] = proj([0, 0]);
  svg.append(mk('line', { x1: 0, y1: eq, x2: W, y2: eq, stroke: 'currentColor', 'stroke-width': 1, opacity: .38 }));

  // connections KR -> everyone (this desk is Korea-centric on purpose)
  const [kx, ky] = proj(COORD.KR);
  pulse.forEach((p) => {
    if (p.region === 'KR' || !COORD[p.region]) return;
    const [x, y] = proj(COORD[p.region]);
    const mx = (kx + x) / 2, my = Math.min(ky, y) - 42;
    const path = mk('path', {
      d: `M ${kx} ${ky} Q ${mx} ${my} ${x} ${y}`,
      fill: 'none', stroke: 'url(#linkGrad)', 'stroke-width': 1.4, opacity: .6,
      'stroke-dasharray': '4 6',
    });
    const anim = mk('animate', { attributeName: 'stroke-dashoffset', from: '40', to: '0', dur: '2.6s', repeatCount: 'indefinite' });
    path.append(anim);
    svg.append(path);
  });

  const defs = mk('defs');
  const grad = mk('linearGradient', { id: 'linkGrad', x1: '0', y1: '0', x2: '1', y2: '0' });
  grad.append(mk('stop', { offset: '0%', 'stop-color': '#6ee7ff' }));
  grad.append(mk('stop', { offset: '100%', 'stop-color': '#ff5ea8' }));
  defs.append(grad);
  svg.append(defs);

  pulse.forEach((p) => {
    if (!COORD[p.region]) return;
    const [x, y] = proj(COORD[p.region]);
    const r = 9 + (p.count / maxCount) * 22;
    const hot = p.sentiment < -0.12 ? '#ff6b6b' : p.sentiment > 0.12 ? '#34e0a1' : '#6ee7ff';

    const g = mk('g', { class: 'wm-node' });
    g.append(mk('circle', { cx: x, cy: y, r: r * 1.9, fill: hot, class: 'halo' }));
    const pulseRing = mk('circle', { cx: x, cy: y, r, fill: 'none', stroke: hot, 'stroke-width': 1.2, class: 'wm-pulse' });
    g.append(pulseRing);
    g.append(mk('circle', { cx: x, cy: y, r, fill: hot, opacity: .85 }));
    const label = mk('text', { x, y: y + r + 15, 'text-anchor': 'middle' });
    label.textContent = `${p.label} ${p.count}`;
    g.append(label);
    const title = mk('title');
    title.textContent = `${p.label} · 기사 ${p.count}건 · 감성 ${p.sentiment} · 화제도 ${p.heat}`;
    g.append(title);
    g.addEventListener('click', () => (location.hash = `#/news?region=${p.region}`));
    svg.append(g);
  });

  return el('div', { class: 'worldmap' }, svg);
}

function gauge(value, label = 'KOREA PULSE') {
  const R = 78, C = 2 * Math.PI * R;
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  svg.setAttribute('width', '196'); svg.setAttribute('height', '196'); svg.setAttribute('viewBox', '0 0 196 196');
  const mk = (t, a) => { const n = document.createElementNS(ns, t); for (const [k, v] of Object.entries(a)) n.setAttribute(k, v); return n; };
  const defs = mk('defs', {});
  const g = mk('linearGradient', { id: 'gaugeGrad', x1: '0', y1: '0', x2: '1', y2: '1' });
  g.append(mk('stop', { offset: '0%', 'stop-color': '#ff5ea8' }));
  g.append(mk('stop', { offset: '50%', 'stop-color': '#a78bfa' }));
  g.append(mk('stop', { offset: '100%', 'stop-color': '#6ee7ff' }));
  defs.append(g); svg.append(defs);
  svg.append(mk('circle', { cx: 98, cy: 98, r: R, fill: 'none', 'stroke-width': 12, class: 'ring-bg' }));
  const fg = mk('circle', {
    cx: 98, cy: 98, r: R, fill: 'none', 'stroke-width': 12, class: 'ring-fg',
    'stroke-dasharray': C, 'stroke-dashoffset': C,
  });
  svg.append(fg);
  const wrap = el('div', { class: 'gauge' }, svg,
    el('div', { class: 'readout' }, el('b', {}, '0'), el('span', {}, label)));
  requestAnimationFrame(() => setTimeout(() => {
    fg.setAttribute('stroke-dashoffset', String(C * (1 - value / 100)));
    countTo($('.readout b', wrap), value, { duration: 1600 });
  }, 260));
  return wrap;
}

function axisBars(axes) {
  const wrap = el('div', { class: 'axes' });
  Object.entries(axes).forEach(([k, v], i) => {
    const bar = el('i');
    wrap.append(el('div', { class: 'axis-row' },
      el('span', {}, k),
      el('span', { class: 'track' }, bar),
      el('span', { class: 'num' }, String(v))));
    setTimeout(() => { bar.style.width = `${v}%`; }, 220 + i * 110);
  });
  return wrap;
}

/* ============================================================
   VIEW: dashboard
   ============================================================ */

export async function viewDashboard(root) {
  const meta = await api('/api/meta', { quiet: true }).catch(() => null);
  const claudeLive = meta?.claude?.live;

  root.append(
    el('section', { class: 'hero' },
      el('div', { class: 'kicker' },
        el('span', { class: `dot ${claudeLive ? '' : 'off'}` }),
        claudeLive ? `CLAUDE 연동됨 · ${meta.claude.engine}` : 'LOCAL ENGINE · ANTHROPIC_API_KEY 미설정'),
      el('h1', {},
        '세상은 오늘도 ', el('span', { class: 'grad' }, '시끄럽다'), el('br'),
        '대신 읽어드립니다'),
      el('p', { class: 'lede' },
        '대한민국·미국·유럽·일본·중국·세계 6개 권역, 34개 언론사 피드를 실시간으로 긁어와 중복을 제거하고, ' +
        'Claude가 하나의 브리핑으로 종합합니다. 같은 사건을 각국이 어떻게 다르게 쓰는지까지 비교합니다.'),
      el('div', { class: 'hero-actions' },
        el('button', { class: 'btn primary', onclick: () => document.getElementById('briefing')?.scrollIntoView({ behavior: 'smooth' }) }, '오늘의 브리핑 읽기'),
        el('a', { class: 'btn', href: '#/prism', 'data-nav': '' }, '프리즘으로 비교하기'),
        el('a', { class: 'btn ghost', href: '#/labs', 'data-nav': '' }, '독립 랩 페이지 ↗'))),
  );

  // ---------- markets ----------
  root.append(el('section', { class: 'section' },
    sectionHead('시장', '클릭하면 상세 차트',
      el('a', { class: 'btn sm', href: '#/market', 'data-nav': '' }, '마켓 전체 →')),
    marketStrip()));

  // ---------- stats ----------
  const statGrid = el('div', { class: 'grid g4' });
  const statSection = el('section', { class: 'section' }, statGrid);
  root.append(statSection);

  // ---------- world map ----------
  const mapCard = el('div', { class: 'card', 'data-reveal': '' }, el('div', { class: 'skel skel-card' }));
  root.append(el('section', { class: 'section' },
    sectionHead('세계 뉴스 밀도', '권역을 클릭하면 해당 지역 피드로 이동합니다'),
    mapCard));

  // ---------- briefing ----------
  const briefBody = el('div', { class: 'prose' });
  const briefCard = el('div', { class: 'card', 'data-reveal': '' },
    el('div', { class: 'skel skel-line', style: { width: '40%' } }),
    el('div', { class: 'skel skel-line' }),
    el('div', { class: 'skel skel-line', style: { width: '85%' } }));
  const briefSection = el('section', { class: 'section', id: 'briefing' },
    sectionHead('오늘의 글로벌 브리핑', claudeLive ? 'Claude가 실시간 헤드라인으로 작성' : '로컬 추출 엔진',
      el('button', {
        class: 'btn sm', onclick: (e) => {
          e.currentTarget.disabled = true;
          briefCard.innerHTML = '';
          briefBody.textContent = '';
          briefBody.classList.add('streaming');
          briefCard.append(briefBody);
          let raw = '';
          streamBriefing((c) => { raw += c; briefBody.innerHTML = md(raw); },
            () => { briefBody.classList.remove('streaming'); e.currentTarget.disabled = false; });
        },
      }, '⟳ 스트리밍으로 다시 쓰기')),
    briefCard);
  root.append(briefSection);

  // ---------- global issues + trends ----------
  const issuesWrap = el('div', { class: 'grid g2' });
  root.append(el('section', { class: 'section' },
    sectionHead('전 지구가 동시에 보는 이슈', '3개 이상 권역에서 동시 관측된 키워드'),
    issuesWrap));

  const cloud = el('div', { class: 'tag-cloud' });
  root.append(el('section', { class: 'section' },
    sectionHead('트렌드 키워드', '클릭하면 프리즘 분석'),
    cloud));

  // ---------- trump watch ----------
  root.append(el('section', { class: 'section' },
    sectionHead('TRUMP WATCH', 'Truth Social 원문을 직접 수집합니다',
      el('a', { class: 'btn sm', href: '#/market', 'data-nav': '' }, 'AI 요약 보기 →')),
    trumpCard()));

  // ---------- top headlines ----------
  const feedWrap = skeletonGrid(6);
  root.append(el('section', { class: 'section' },
    sectionHead('지금 가장 뜨거운 기사', '화제도 순',
      el('a', { class: 'btn sm', href: '#/news', 'data-nav': '' }, '전체 보기 →')),
    feedWrap));

  observeReveals(root);

  // ---------- load ----------
  // A cold server has an empty cache and the first request kicks off a full sweep.
  // Wait for it instead of freezing an empty map onto the page.
  let pulse = [];
  for (let attempt = 0; attempt < 4; attempt++) {
    pulse = await api('/api/news/pulse', { quiet: true }).catch(() => []);
    if (pulse.some((p) => p.count > 0)) break;
    mapCard.innerHTML = '';
    mapCard.append(el('div', { class: 'empty' },
      el('div', { class: 'big' }, '📡'),
      el('div', {}, '34개 피드를 처음 수집하는 중입니다… 10초쯤 걸립니다')));
    await new Promise((r) => setTimeout(r, 3500));
  }

  const [brief, issues, trends, feed] = await Promise.all([
    api('/api/ai/briefing').catch(() => null),
    api('/api/news/global-issues?limit=6', { quiet: true }).catch(() => []),
    api('/api/news/trends?limit=34', { quiet: true }).catch(() => []),
    api('/api/news?limit=9', { quiet: true }).catch(() => ({ items: [] })),
  ]);

  // stats
  const totalArticles = pulse.reduce((a, p) => a + p.count, 0);
  const avgSenti = pulse.length ? pulse.reduce((a, p) => a + p.sentiment, 0) / pulse.length : 0;
  const stats = [
    { label: 'CRAWLED', value: totalArticles, foot: '지금 캐시에 있는 기사', grad: true },
    { label: 'REGIONS', value: pulse.length, foot: '동시 감시 중인 권역' },
    { label: 'SENTIMENT', value: avgSenti, foot: '전 권역 평균 감성', decimals: 2 },
    { label: 'ENGINE', value: null, foot: claudeLive ? meta.claude.engine : '로컬 엔진', text: claudeLive ? 'CLAUDE' : 'LOCAL' },
  ];
  stats.forEach((s, i) => {
    const valueNode = el('div', { class: `value ${s.grad ? 'grad' : ''}` }, s.text || '0');
    statGrid.append(el('div', { class: 'card lift stat', 'data-reveal': '' },
      el('div', { class: 'label' }, s.label), valueNode, el('div', { class: 'foot' }, s.foot)));
    if (s.value !== null && s.value !== undefined) {
      setTimeout(() => countTo(valueNode, s.value, { decimals: s.decimals || 0 }), 200 + i * 90);
    }
  });

  // map
  mapCard.innerHTML = '';
  if (pulse.some((p) => p.count > 0)) {
    mapCard.append(worldMap(pulse));
  } else {
    mapCard.append(el('div', { class: 'empty' },
      el('div', { class: 'big' }, '📡'),
      el('div', {}, '아직 수집된 기사가 없습니다.'),
      el('button', { class: 'btn primary', style: { marginTop: '14px' }, onclick: () => window.MUJIN.refresh() },
        '지금 크롤링하기')));
  }

  // briefing
  briefCard.innerHTML = '';
  briefBody.innerHTML = md(brief?.markdown || '브리핑을 불러오지 못했습니다.');
  briefCard.append(
    el('div', { class: 'filters', style: { marginBottom: '14px' } },
      el('span', { class: `chip ${brief?.live ? 'good' : 'gold'}` }, brief?.live ? `✦ ${brief.engine}` : '◇ 로컬 엔진'),
      brief?.generatedAt ? el('span', { class: 'chip' }, brief.generatedAt) : null,
      el('span', { class: 'chip' }, `표본 ${brief?.sampled ?? 0}건`),
      el('span', { style: { flex: 1 } }),
      el('button', {
        class: 'btn sm', onclick: () => bookmark({
          kind: 'BRIEFING', title: `글로벌 브리핑 ${brief?.generatedAt || ''}`,
          url: location.origin + '/#/', source: brief?.engine, memo: (brief?.markdown || '').slice(0, 1800),
        }),
      }, '＋ 브리핑 스크랩'),
      el('button', {
        class: 'btn sm', onclick: () => speak(brief?.markdown?.replace(/[#*>_`-]/g, '') || ''),
      }, '🔊 읽어주기')),
    briefBody);

  // issues
  issuesWrap.innerHTML = '';
  if (!issues.length) {
    issuesWrap.append(el('div', { class: 'card' }, el('p', {}, '아직 여러 권역에 동시에 걸친 키워드가 없습니다. 크롤링이 더 쌓이면 나타납니다.')));
  }
  issues.forEach((it, i) => {
    const regions = Object.entries(it.regions || {});
    const card = el('div', { class: 'card lift', 'data-reveal': '', style: { cursor: 'pointer' } },
      el('div', { class: 'filters', style: { marginBottom: '10px' } },
        el('span', { class: 'chip gold' }, `${it.global}개 권역 동시`),
        el('span', { class: 'chip' }, `${it.count}건`),
        el('span', { class: `chip ${it.sentiment < -0.1 ? 'bad' : it.sentiment > 0.1 ? 'good' : ''}` }, `감성 ${it.sentiment}`)),
      el('h3', { style: { fontSize: '21px' } }, it.keyword),
      el('div', { class: 'axes', style: { marginTop: '12px' } },
        ...regions.map(([r, c]) => {
          const max = Math.max(...regions.map((x) => x[1]));
          const bar = el('i');
          setTimeout(() => { bar.style.width = `${(c / max) * 100}%`; }, 300 + i * 80);
          return el('div', { class: 'axis-row' }, el('span', {}, r), el('span', { class: 'track' }, bar), el('span', { class: 'num' }, String(c)));
        })));
    card.addEventListener('click', () => (location.hash = `#/prism?q=${encodeURIComponent(it.keyword)}`));
    issuesWrap.append(card);
  });

  // trends
  const maxCount = Math.max(1, ...trends.map((t) => t.count));
  trends.forEach((t) => {
    const size = 12 + (t.count / maxCount) * 14;
    cloud.append(el('button', {
      style: { fontSize: `${size}px`, fontWeight: t.count / maxCount > .6 ? '700' : '500' },
      onclick: () => (location.hash = `#/prism?q=${encodeURIComponent(t.keyword)}`),
    }, `${t.keyword}`, el('span', { style: { opacity: .45, fontSize: '10px', marginLeft: '5px' } }, String(t.count))));
  });

  // feed
  feedWrap.innerHTML = '';
  feedWrap.className = 'grid g3';
  (feed.items || []).forEach((a, i) => feedWrap.append(newsCard(a, i)));

  observeReveals(root);
  bindSheen(root);
}

/* ============================================================
   VIEW: news
   ============================================================ */

export async function viewNews(root, params) {
  let region = params.get('region') || 'ALL';
  let query = params.get('q') || '';
  let category = '전체';

  const regions = await api('/api/news/regions', { quiet: true }).catch(() => []);

  const searchInput = el('input', { type: 'search', placeholder: '키워드로 전세계 기사 검색...', value: query });
  const regionSeg = el('div', { class: 'seg' });
  const catSeg = el('div', { class: 'seg' });
  const grid = skeletonGrid(9);
  const counter = el('span', { class: 'sub' }, '');

  const head = sectionHead('뉴스 피드', '', counter);

  root.append(el('section', { class: 'section' }, head,
    el('div', { class: 'filters' },
      el('div', { class: 'search-wrap' }, searchInput),
      regionSeg),
    el('div', { class: 'filters' }, catSeg),
    grid));

  const allRegions = [{ code: 'ALL', label: '전체', flag: '🛰️' }, ...regions];
  const renderRegions = () => {
    regionSeg.innerHTML = '';
    allRegions.forEach((r) => {
      regionSeg.append(el('button', {
        class: r.code === region ? 'active' : '',
        onclick: () => { region = r.code; query = ''; searchInput.value = ''; renderRegions(); load(); },
      }, `${r.flag} ${r.label}`));
    });
  };
  renderRegions();

  let timer = null;
  searchInput.addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => { query = searchInput.value.trim(); load(); }, 340);
  });

  async function load() {
    grid.innerHTML = '';
    grid.className = 'grid g3';
    for (let i = 0; i < 6; i++) grid.append(el('div', { class: 'skel skel-card' }));

    const data = query
      ? await api(`/api/news/search?q=${encodeURIComponent(query)}&limit=60`).catch(() => ({ items: [] }))
      : await api(`/api/news?region=${region}&limit=60${category !== '전체' ? `&category=${encodeURIComponent(category)}` : ''}`).catch(() => ({ items: [] }));

    counter.textContent = query ? `"${query}" 검색 결과 ${data.total ?? 0}건` : `${data.total ?? 0}건 수집됨`;

    // categories
    catSeg.innerHTML = '';
    const cats = ['전체', ...(data.categories || [])];
    if (!query) {
      cats.forEach((c) => catSeg.append(el('button', {
        class: c === category ? 'active' : '',
        onclick: () => { category = c; load(); },
      }, c)));
      catSeg.parentElement.style.display = '';
    } else {
      catSeg.parentElement.style.display = 'none';
    }

    grid.innerHTML = '';
    const items = data.items || [];
    if (!items.length) {
      grid.className = '';
      grid.append(el('div', { class: 'empty' }, el('div', { class: 'big' }, '◌'),
        el('div', {}, '결과가 없습니다. 다른 키워드로 시도해보세요.')));
      return;
    }
    items.forEach((a, i) => grid.append(newsCard(a, i)));
    observeReveals(grid);
    bindSheen(grid);
  }

  await load();
}

/* ============================================================
   VIEW: prism
   ============================================================ */

export async function viewPrism(root, params) {
  const initial = params.get('q') || '';

  const input = el('input', { type: 'search', placeholder: '키워드 입력 — 예: 반도체, 금리, 선거, AI', value: initial });
  const body = el('div');
  const suggestWrap = el('div', { class: 'tag-cloud', style: { marginBottom: '18px' } });

  root.append(
    el('section', { class: 'hero', style: { paddingBottom: '20px' } },
      el('div', { class: 'kicker' }, el('span', { class: 'dot' }), 'CROSS-COUNTRY FRAMING ANALYSIS'),
      el('h1', { style: { fontSize: 'clamp(30px,5vw,58px)' } }, '같은 사건, ', el('span', { class: 'grad' }, '여섯 개의 서사')),
      el('p', { class: 'lede' },
        '하나의 키워드를 6개 권역 언론이 각각 어떻게 프레이밍하는지 나란히 놓고 비교합니다. ' +
        '무엇을 강조하고 무엇을 빼는지가 그 나라의 이해관계입니다.')),
    el('section', { class: 'section' },
      el('div', { class: 'filters' },
        el('div', { class: 'search-wrap' }, input),
        el('button', { class: 'btn primary', onclick: () => run(input.value.trim()) }, '분석'),
        el('button', { class: 'btn ghost', onclick: () => run(input.value.trim(), true) }, '↻ 새로 분석')),
      suggestWrap,
      body));

  api('/api/news/global-issues?limit=8', { quiet: true }).then((issues) => {
    if (!issues.length) return;
    suggestWrap.append(el('span', { class: 'sub', style: { marginRight: '6px' } }, '추천:'));
    issues.forEach((it) => suggestWrap.append(el('button', {
      onclick: () => { input.value = it.keyword; run(it.keyword); },
    }, `${it.keyword}`)));
  }).catch(() => {});

  async function run(q, force = false) {
    if (!q) { toast('키워드를 입력하세요'); return; }
    location.replace(`#/prism?q=${encodeURIComponent(q)}`);
    body.innerHTML = '';
    body.append(el('div', { class: 'card' },
      el('div', { class: 'skel skel-line', style: { width: '30%' } }),
      el('div', { class: 'skel skel-line' }),
      el('div', { class: 'skel skel-line', style: { width: '70%' } })));

    const data = await api(`/api/ai/prism?q=${encodeURIComponent(q)}${force ? '&force=true' : ''}`).catch((e) => {
      toast(e.message, 'bad'); return null;
    });
    if (!data) { body.innerHTML = ''; return; }

    body.innerHTML = '';
    const analysis = el('div', { class: 'card', 'data-reveal': '' },
      el('div', { class: 'filters', style: { marginBottom: '12px' } },
        el('span', { class: 'chip accent' }, `키워드: ${data.keyword}`),
        el('span', { class: 'chip' }, `${data.total}건 수집`),
        el('span', { class: `chip ${data.live ? 'good' : 'gold'}` }, data.live ? `✦ ${data.engine}` : '◇ 로컬 엔진'),
        el('span', { style: { flex: 1 } }),
        el('button', { class: 'btn sm', onclick: () => location.hash = `#/news?q=${encodeURIComponent(data.keyword)}` }, '원문 기사 보기 →')),
      el('div', { class: 'prose', html: md(data.markdown) }));

    const cols = el('div', { class: 'grid g3', style: { marginTop: '20px' } });
    Object.entries(data.byRegion || {}).forEach(([code, items], i) => {
      const col = el('div', { class: 'card lift', 'data-reveal': '', style: { '--d': `${i * 70}ms` } },
        el('h3', {}, `${items[0]?.flag || '🌐'} ${items[0]?.regionLabel || code}`,
          el('span', { class: 'chip', style: { marginLeft: '8px' } }, `${items.length}건`)),
        ...items.map((a) => el('a', {
          href: a.link, target: '_blank', rel: 'noopener',
          style: { display: 'block', padding: '9px 0', borderTop: '1px solid var(--stroke-soft)', fontSize: '13px' },
        },
          el('div', { style: { color: 'var(--text-dim)', fontSize: '11px', marginBottom: '2px' } }, `${a.source} · ${a.relative}`),
          el('div', {}, a.title))));
      cols.append(col);
    });

    const tl = el('div', { class: 'card', 'data-reveal': '', style: { marginTop: '20px' } },
      el('h3', {}, '타임라인'),
      el('div', { class: 'timeline', id: 'prismTl' }, el('div', { class: 'skel skel-line' })));

    body.append(analysis, cols, tl);
    observeReveals(body);
    bindSheen(body);

    api(`/api/news/timeline?q=${encodeURIComponent(q)}`, { quiet: true }).then((t) => {
      const node = $('#prismTl', body);
      if (!node) return;
      node.innerHTML = '';
      (t.steps || []).slice(0, 24).forEach((s) => {
        node.append(el('div', { class: 'tl-item' },
          el('div', { class: 'when' }, `${s.relative} · ${s.flag} ${s.source}`),
          el('a', { class: 'what', href: s.link, target: '_blank', rel: 'noopener' }, s.title)));
      });
      if (!node.children.length) node.append(el('p', { class: 'sub' }, '타임라인을 만들 기사가 부족합니다.'));
    }).catch(() => {});
  }

  if (initial) run(initial);
}

/* ============================================================
   VIEW: korea pulse
   ============================================================ */

export async function viewKorea(root) {
  root.append(el('section', { class: 'hero', style: { paddingBottom: '18px' } },
    el('div', { class: 'kicker' }, el('span', { class: 'dot' }), 'REPUBLIC OF KOREA · SITUATION DESK'),
    el('h1', { style: { fontSize: 'clamp(30px,5vw,58px)' } }, '대한민국 ', el('span', { class: 'grad' }, '정세')),
    el('p', { class: 'lede' },
      '국회·대통령실·외교·남북·경제정책 트랙을 따로 크롤링해서 갈등/협치/외교긴장/경제불안/민생 5개 축으로 점수화하고, ' +
      'Claude가 국면을 읽습니다.')));

  const shell = el('div', { class: 'split' });
  const left = el('div');
  const right = el('div');
  shell.append(left, right);
  root.append(el('section', { class: 'section' }, shell));

  left.append(el('div', { class: 'card' },
    el('div', { class: 'skel skel-line', style: { width: '35%' } }),
    el('div', { class: 'skel skel-line' }),
    el('div', { class: 'skel skel-line', style: { width: '80%' } })));
  right.append(el('div', { class: 'card' }, el('div', { class: 'skel skel-card' })));

  const data = await api('/api/korea/pulse').catch((e) => { toast(e.message, 'bad'); return null; });
  if (!data) return;

  left.innerHTML = '';
  right.innerHTML = '';

  right.append(el('div', { class: 'card', 'data-reveal': '', style: { textAlign: 'center' } },
    gauge(data.pulse),
    el('h3', { style: { marginTop: '14px', fontSize: '19px' } }, `${data.grade}`),
    el('p', { style: { fontSize: '13px' } }, data.gradeDesc),
    el('div', { class: 'filters', style: { justifyContent: 'center', marginTop: '14px' } },
      el('span', { class: 'chip' }, `표본 ${data.sampleSize}건`),
      el('span', { class: `chip ${data.sentiment < 0 ? 'bad' : 'good'}` }, `감성 ${data.sentiment}`)),
    el('p', { style: { fontSize: '11px', color: 'var(--text-faint)', marginTop: '12px' } }, data.disclaimer)));

  right.append(el('div', { class: 'card', 'data-reveal': '', style: { marginTop: '16px' } },
    el('h3', {}, '5축 진단'), axisBars(data.axes)));

  const cloud = el('div', { class: 'tag-cloud' });
  (data.keywords || []).forEach((k) => cloud.append(el('button', {
    onclick: () => (location.hash = `#/prism?q=${encodeURIComponent(k.word)}`),
    style: { fontSize: `${11 + Math.min(10, k.count * 1.6)}px` },
  }, k.word)));
  right.append(el('div', { class: 'card', 'data-reveal': '', style: { marginTop: '16px' } },
    el('h3', {}, '정세 키워드'), cloud));

  left.append(el('div', { class: 'card', 'data-reveal': '' },
    el('div', { class: 'filters', style: { marginBottom: '12px' } },
      el('span', { class: `chip ${data.live ? 'good' : 'gold'}` }, data.live ? `✦ ${data.engine}` : '◇ 로컬 엔진'),
      el('span', { style: { flex: 1 } }),
      el('button', { class: 'btn sm', onclick: () => reload(true) }, '↻ 다시 분석')),
    el('div', { class: 'prose', html: md(data.analysis) })));

  Object.entries(data.tracks || {}).forEach(([track, items]) => {
    left.append(el('div', { class: 'card lift', 'data-reveal': '', style: { marginTop: '16px' } },
      el('h3', {}, `${track}`, el('span', { class: 'chip', style: { marginLeft: '8px' } }, `${items.length}건`)),
      ...items.map((a) => el('a', {
        href: a.link, target: '_blank', rel: 'noopener',
        style: { display: 'flex', gap: '10px', alignItems: 'baseline', padding: '8px 0', borderTop: '1px solid var(--stroke-soft)', fontSize: '13.5px' },
      },
        el('span', { class: `chip ${a.sentiment < -0.15 ? 'bad' : a.sentiment > 0.15 ? 'good' : ''}`, style: { flexShrink: 0 } }, a.relative),
        el('span', {}, a.title)))));
  });

  observeReveals(root);
  bindSheen(root);

  async function reload(force) {
    toast('다시 분석 중...');
    const fresh = await api(`/api/korea/pulse?force=${force}`).catch(() => null);
    if (fresh) { root.innerHTML = ''; await viewKorea(root); }
  }
}

/* ============================================================
   VIEW: shop
   ============================================================ */

export async function viewShop(root, params) {
  const initial = params.get('q') || '';
  const input = el('input', { type: 'search', placeholder: '상품명 입력 — 예: 무선 이어폰, 게이밍 의자', value: initial });
  const body = el('div');
  const picksWrap = el('div', { class: 'grid g3' });

  root.append(
    el('section', { class: 'hero', style: { paddingBottom: '18px' } },
      el('div', { class: 'kicker' }, el('span', { class: 'dot' }), 'PRICE HUNTER'),
      el('h1', { style: { fontSize: 'clamp(30px,5vw,58px)' } }, '제일 싼 걸 ', el('span', { class: 'grad' }, '찾아드림')),
      el('p', { class: 'lede' },
        '쿠팡·네이버·다나와·11번가·G마켓·에누리·알리를 한 번에 훑는 검색과, 실제 수집 가격 기반 Claude 판정. ' +
        '가격 데이터가 없으면 지어내지 않고 딥링크만 드립니다.')),
    el('section', { class: 'section' },
      el('div', { class: 'filters' },
        el('div', { class: 'search-wrap' }, input),
        el('button', { class: 'btn primary', onclick: () => run(input.value.trim()) }, '최저가 찾기')),
      body),
    el('section', { class: 'section' },
      sectionHead('뉴스가 예고하는 소비', 'Claude가 지금 뜨는 뉴스 키워드에서 살 만한 걸 역산합니다',
        el('button', { class: 'btn sm', onclick: () => loadPicks(true) }, '↻ 다시')),
      picksWrap));

  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') run(input.value.trim()); });

  async function run(q) {
    if (!q) { toast('상품명을 입력하세요'); return; }
    location.replace(`#/shop?q=${encodeURIComponent(q)}`);
    body.innerHTML = '';
    body.append(el('div', { class: 'card' }, el('div', { class: 'skel skel-card' })));

    const data = await api(`/api/shop/search?q=${encodeURIComponent(q)}&limit=18`).catch((e) => { toast(e.message, 'bad'); return null; });
    if (!data) { body.innerHTML = ''; return; }

    body.innerHTML = '';

    // mall deep links
    const links = el('div', { class: 'filters', style: { marginBottom: '16px' } },
      ...data.links.map((l) => el('a', {
        class: 'btn sm', href: l.url, target: '_blank', rel: 'noopener',
        style: { borderColor: l.color },
      }, `${l.mall} ↗`)));

    body.append(el('div', { class: 'card', 'data-reveal': '' },
      el('div', { class: 'filters', style: { marginBottom: '10px' } },
        el('span', { class: 'chip accent' }, `"${data.query}"`),
        el('span', { class: `chip ${data.live ? 'good' : 'gold'}` }, data.live ? '실가격 연동' : '딥링크 모드')),
      el('h3', {}, '몰별 바로가기'), links));

    if (data.offers?.length) {
      const table = el('table', { class: 'data' },
        el('tr', {}, el('th', {}, '순위'), el('th', {}, '판매처'), el('th', {}, '상품'), el('th', {}, '가격'), el('th', {}, '최저가 대비')),
        ...data.offers.map((o, i) => el('tr', {},
          el('td', {}, String(i + 1)),
          el('td', {}, o.mall),
          el('td', {}, el('a', { href: o.link, target: '_blank', rel: 'noopener' }, o.title)),
          el('td', { class: `price ${i === 0 ? 'min' : ''}` }, `${num(o.price)}원`),
          el('td', {}, o.vsMin === 0 ? el('span', { class: 'chip good' }, '최저가') : `+${num(o.vsMin)}원`))));

      const st = data.stats;
      body.append(el('div', { class: 'grid g4', style: { marginBottom: '16px' } },
        statCard('최저가', `${num(st.min)}원`, '지금 확인된 최저'),
        statCard('평균가', `${num(st.avg)}원`, `${st.count}개 판매처`),
        statCard('최고가', `${num(st.max)}원`, ''),
        statCard('가격 편차', `${num(st.spread)}원`, `${st.spreadPct}%`)));
      body.append(el('div', { class: 'card scroll-x', 'data-reveal': '' }, table));
    }

    body.append(el('div', { class: 'card', 'data-reveal': '', style: { marginTop: '16px' } },
      el('div', { class: 'prose', html: md(data.verdict) })));

    if (data.checklist?.length) {
      body.append(el('div', { class: 'card', 'data-reveal': '', style: { marginTop: '16px' } },
        el('h3', {}, '돈 새는 지점 체크리스트'),
        el('ul', { class: 'prose' }, ...data.checklist.map((c) => el('li', {}, c)))));
    }

    observeReveals(body);
    bindSheen(body);
  }

  async function loadPicks() {
    picksWrap.innerHTML = '';
    for (let i = 0; i < 3; i++) picksWrap.append(el('div', { class: 'skel skel-card' }));
    const data = await api('/api/shop/trending').catch(() => null);
    picksWrap.innerHTML = '';
    if (!data?.picks?.length) {
      picksWrap.append(el('div', { class: 'card' }, el('p', {}, '추천을 만들지 못했습니다. 잠시 후 다시 시도하세요.')));
      return;
    }
    data.picks.forEach((p, i) => {
      picksWrap.append(el('div', { class: 'card lift', 'data-reveal': '', style: { '--d': `${i * 60}ms` } },
        el('span', { class: 'chip gold' }, p.category || '추천'),
        el('h3', { style: { marginTop: '10px' } }, p.keyword),
        el('p', {}, p.reason),
        el('div', { class: 'filters', style: { marginTop: '12px' } },
          el('button', { class: 'btn sm primary', onclick: () => { input.value = p.keyword; run(p.keyword); window.scrollTo({ top: 0, behavior: 'smooth' }); } }, '최저가 검색'),
          ...(p.links || []).slice(0, 3).map((l) => el('a', { class: 'btn sm ghost', href: l.url, target: '_blank', rel: 'noopener' }, l.mall)))));
    });
    observeReveals(picksWrap);
    bindSheen(picksWrap);
  }

  function statCard(label, value, foot) {
    return el('div', { class: 'card stat lift', 'data-reveal': '' },
      el('div', { class: 'label' }, label),
      el('div', { class: 'value', style: { fontSize: '24px' } }, value),
      el('div', { class: 'foot' }, foot));
  }

  if (initial) run(initial);
  loadPicks();
}

/* ============================================================
   VIEW: knowledge graph
   ============================================================ */

export async function viewGraph(root) {
  root.append(el('section', { class: 'hero', style: { paddingBottom: '14px' } },
    el('div', { class: 'kicker' }, el('span', { class: 'dot' }), 'KEYWORD CO-OCCURRENCE NETWORK'),
    el('h1', { style: { fontSize: 'clamp(28px,4.6vw,52px)' } }, '지식 ', el('span', { class: 'grad' }, '그래프')),
    el('p', { class: 'lede' }, '같은 기사에 함께 등장한 키워드를 잇습니다. 노드를 끌어 옮길 수 있고, 클릭하면 그 키워드로 프리즘 분석이 열립니다.')));

  const canvas = el('canvas', { style: { width: '100%', height: '620px', display: 'block', borderRadius: 'var(--radius)', cursor: 'grab' } });
  const wrap = el('div', { class: 'card', style: { padding: '0', overflow: 'hidden' } }, canvas);
  const legend = el('div', { class: 'filters', style: { marginTop: '12px' } });
  root.append(el('section', { class: 'section' }, wrap, legend));

  const data = await api('/api/news/graph?nodes=54').catch(() => null);
  if (!data?.nodes?.length) {
    wrap.innerHTML = '';
    wrap.append(el('div', { class: 'empty' }, el('div', { class: 'big' }, '⁂'), el('div', {}, '그래프를 만들 데이터가 부족합니다.')));
    return;
  }

  legend.append(el('span', { class: 'chip' }, `노드 ${data.nodes.length}`),
    el('span', { class: 'chip' }, `연결 ${data.links.length}`),
    el('span', { class: 'chip' }, `표본 ${data.sampled}건`),
    el('span', { class: 'sub' }, '드래그: 이동 · 클릭: 프리즘 분석'));

  runForceGraph(canvas, data);
}

const REGION_COLOR = { KR: '#6ee7ff', US: '#a78bfa', EU: '#34e0a1', JP: '#ff5ea8', CN: '#ffcf5c', WORLD: '#94a3b8' };

function runForceGraph(canvas, data) {
  const ctx = canvas.getContext('2d');
  let W = 0, H = 0, dragging = null, hover = null;

  const nodes = data.nodes.map((n) => ({
    ...n, x: Math.random() * 600 + 100, y: Math.random() * 400 + 100, vx: 0, vy: 0,
    r: 5 + Math.min(20, n.weight * 1.7),
  }));
  const index = new Map(nodes.map((n) => [n.id, n]));
  const links = data.links
    .map((l) => ({ s: index.get(l.source), t: index.get(l.target), w: l.weight }))
    .filter((l) => l.s && l.t);

  const resize = () => {
    const rect = canvas.getBoundingClientRect();
    W = canvas.width = rect.width * devicePixelRatio;
    H = canvas.height = rect.height * devicePixelRatio;
    ctx.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0);
  };
  resize();
  window.addEventListener('resize', resize);

  const step = () => {
    const w = W / devicePixelRatio, h = H / devicePixelRatio;
    // repulsion
    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const a = nodes[i], b = nodes[j];
        let dx = b.x - a.x, dy = b.y - a.y;
        let d2 = dx * dx + dy * dy || 1;
        const d = Math.sqrt(d2);
        const force = 2400 / d2;
        const fx = (dx / d) * force, fy = (dy / d) * force;
        a.vx -= fx; a.vy -= fy; b.vx += fx; b.vy += fy;
      }
    }
    // springs
    for (const l of links) {
      const dx = l.t.x - l.s.x, dy = l.t.y - l.s.y;
      const d = Math.sqrt(dx * dx + dy * dy) || 1;
      const target = 90 + 40 / Math.sqrt(l.w);
      const f = (d - target) * 0.0032 * Math.min(3, l.w);
      const fx = (dx / d) * f, fy = (dy / d) * f;
      l.s.vx += fx; l.s.vy += fy; l.t.vx -= fx; l.t.vy -= fy;
    }
    // center gravity + integrate
    for (const n of nodes) {
      n.vx += (w / 2 - n.x) * 0.0016;
      n.vy += (h / 2 - n.y) * 0.0016;
      n.vx *= 0.86; n.vy *= 0.86;
      if (n !== dragging) { n.x += n.vx; n.y += n.vy; }
      n.x = Math.max(n.r + 6, Math.min(w - n.r - 6, n.x));
      n.y = Math.max(n.r + 6, Math.min(h - n.r - 6, n.y));
    }
  };

  const draw = () => {
    const w = W / devicePixelRatio, h = H / devicePixelRatio;
    ctx.clearRect(0, 0, w, h);
    for (const l of links) {
      const active = hover && (l.s === hover || l.t === hover);
      ctx.beginPath();
      ctx.moveTo(l.s.x, l.s.y);
      ctx.lineTo(l.t.x, l.t.y);
      ctx.strokeStyle = active ? 'rgba(110,231,255,.55)' : 'rgba(255,255,255,.09)';
      ctx.lineWidth = active ? 1.6 : Math.min(2, l.w * 0.4);
      ctx.stroke();
    }
    for (const n of nodes) {
      const c = REGION_COLOR[n.region] || '#94a3b8';
      const active = n === hover;
      ctx.beginPath();
      ctx.arc(n.x, n.y, n.r, 0, Math.PI * 2);
      ctx.fillStyle = active ? c : c + 'cc';
      ctx.shadowBlur = active ? 26 : 12;
      ctx.shadowColor = c;
      ctx.fill();
      ctx.shadowBlur = 0;
      if (n.r > 9 || active) {
        ctx.font = `${active ? 600 : 400} ${Math.max(10, Math.min(14, n.r * 0.8))}px Pretendard, sans-serif`;
        ctx.fillStyle = 'rgba(232,236,246,.92)';
        ctx.textAlign = 'center';
        ctx.fillText(n.id, n.x, n.y + n.r + 13);
      }
    }
  };

  const loop = () => { step(); draw(); requestAnimationFrame(loop); };
  loop();

  const pick = (e) => {
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left, y = e.clientY - rect.top;
    return nodes.find((n) => (n.x - x) ** 2 + (n.y - y) ** 2 < (n.r + 6) ** 2);
  };
  canvas.addEventListener('pointermove', (e) => {
    if (dragging) {
      const rect = canvas.getBoundingClientRect();
      dragging.x = e.clientX - rect.left;
      dragging.y = e.clientY - rect.top;
      return;
    }
    hover = pick(e);
    canvas.style.cursor = hover ? 'pointer' : 'grab';
  });
  canvas.addEventListener('pointerdown', (e) => { dragging = pick(e); if (dragging) canvas.setPointerCapture(e.pointerId); });
  canvas.addEventListener('pointerup', (e) => {
    if (dragging) {
      const moved = Math.abs(dragging.vx) + Math.abs(dragging.vy);
      const target = dragging;
      dragging = null;
      if (moved < 6) location.hash = `#/prism?q=${encodeURIComponent(target.id)}`;
    }
  });
}

/* ============================================================
   VIEW: AI desk
   ============================================================ */

export async function viewAi(root) {
  const meta = await api('/api/meta', { quiet: true }).catch(() => null);

  root.append(el('section', { class: 'hero', style: { paddingBottom: '14px' } },
    el('div', { class: 'kicker' },
      el('span', { class: `dot ${meta?.claude?.live ? '' : 'off'}` }),
      meta?.claude?.hint || 'AI DESK'),
    el('h1', { style: { fontSize: 'clamp(28px,4.6vw,52px)' } }, 'AI ', el('span', { class: 'grad' }, '데스크')),
    el('p', { class: 'lede' }, '권역별 심층 다이제스트, 음성 브리핑, 자유 질의를 한 곳에서.')));

  // region digests
  const regions = await api('/api/news/regions', { quiet: true }).catch(() => []);
  const digestBody = el('div');
  const seg = el('div', { class: 'seg' });
  let current = 'KR';

  regions.forEach((r) => seg.append(el('button', {
    class: r.code === current ? 'active' : '',
    onclick: (e) => {
      current = r.code;
      $$('button', seg).forEach((b) => b.classList.remove('active'));
      e.currentTarget.classList.add('active');
      loadDigest();
    },
  }, `${r.flag} ${r.label}`)));

  root.append(el('section', { class: 'section' },
    sectionHead('권역 심층 다이제스트', '해당 권역만 따로 크롤링해 분석합니다'),
    el('div', { class: 'filters' }, seg,
      el('button', { class: 'btn sm ghost', onclick: () => loadDigest(true) }, '↻ 다시 분석')),
    digestBody));

  async function loadDigest(force = false) {
    digestBody.innerHTML = '';
    digestBody.append(el('div', { class: 'card' },
      el('div', { class: 'skel skel-line', style: { width: '30%' } }),
      el('div', { class: 'skel skel-line' }),
      el('div', { class: 'skel skel-line', style: { width: '76%' } })));
    const d = await api(`/api/ai/region/${current}${force ? '?force=true' : ''}`).catch(() => null);
    digestBody.innerHTML = '';
    if (!d) { digestBody.append(el('div', { class: 'empty' }, '분석을 불러오지 못했습니다')); return; }
    digestBody.append(el('div', { class: 'split' },
      el('div', { class: 'card', 'data-reveal': '' },
        el('div', { class: 'filters', style: { marginBottom: '10px' } },
          el('span', { class: 'chip accent' }, `${d.flag} ${d.label}`),
          el('span', { class: 'chip' }, `${d.count}건`),
          el('span', { class: `chip ${d.live ? 'good' : 'gold'}` }, d.live ? `✦ ${d.engine}` : '◇ 로컬'),
          el('span', { style: { flex: 1 } }),
          el('button', { class: 'btn sm', onclick: () => speak(d.markdown.replace(/[#*>_`-]/g, '')) }, '🔊 읽기')),
        el('div', { class: 'prose', html: md(d.markdown) })),
      el('div', { class: 'card', 'data-reveal': '' },
        el('h3', {}, '수집된 헤드라인'),
        ...(d.headlines || []).slice(0, 14).map((a) => el('a', {
          href: a.link, target: '_blank', rel: 'noopener',
          style: { display: 'block', padding: '8px 0', borderTop: '1px solid var(--stroke-soft)', fontSize: '13px' },
        }, el('div', { style: { fontSize: '11px', color: 'var(--text-faint)' } }, `${a.source} · ${a.relative}`), a.title)))));
    observeReveals(digestBody);
    bindSheen(digestBody);
  }
  loadDigest();

  // podcast
  const podBody = el('div', { class: 'card', 'data-reveal': '' }, el('div', { class: 'skel skel-line' }));
  root.append(el('section', { class: 'section' },
    sectionHead('오디오 브리핑', '브라우저 음성합성으로 읽어드립니다'),
    podBody));

  api('/api/ai/podcast').then((p) => {
    podBody.innerHTML = '';
    podBody.append(
      el('div', { class: 'filters', style: { marginBottom: '12px' } },
        el('button', { class: 'btn primary', onclick: () => speak(p.script) }, '▶ 재생'),
        el('button', { class: 'btn', onclick: () => speechSynthesis.cancel() }, '■ 정지'),
        el('span', { class: `chip ${p.live ? 'good' : 'gold'}` }, p.live ? `✦ ${p.engine}` : '◇ 로컬'),
        el('span', { class: 'chip' }, p.generatedAt || '')),
      el('div', { class: 'prose', style: { whiteSpace: 'pre-wrap' } }, p.script));
  }).catch(() => { podBody.innerHTML = '<p class="sub">대본을 불러오지 못했습니다.</p>'; });

  // free chat
  const log = el('div', { style: { display: 'grid', gap: '12px', marginBottom: '14px' } });
  const qi = el('input', { type: 'text', placeholder: '예: 지금 유럽에서 제일 큰 이슈가 뭐야?' });
  root.append(el('section', { class: 'section' },
    sectionHead('자유 질의', '실시간 헤드라인을 근거로 답합니다'),
    el('div', { class: 'card' }, log,
      el('form', {
        class: 'filters',
        onsubmit: (e) => {
          e.preventDefault();
          const q = qi.value.trim();
          if (!q) return;
          qi.value = '';
          log.append(el('div', { class: 'bubble me', style: { alignSelf: 'flex-end' } }, q));
          const answer = el('div', { class: 'prose streaming' });
          log.append(el('div', { class: 'card', style: { background: 'var(--panel-strong)' } }, answer));
          let raw = '';
          streamAsk(q, (c) => { raw += c; answer.innerHTML = md(raw); },
            () => answer.classList.remove('streaming'));
        },
      },
        el('div', { class: 'search-wrap' }, qi),
        el('button', { class: 'btn primary', type: 'submit' }, '질문')))));

  observeReveals(root);
  bindSheen(root);
}

export function speak(text) {
  if (!('speechSynthesis' in window)) { toast('이 브라우저는 음성합성을 지원하지 않습니다', 'bad'); return; }
  speechSynthesis.cancel();
  const clean = String(text || '').replace(/[#*_`>|]/g, '').slice(0, 4500);
  const u = new SpeechSynthesisUtterance(clean);
  u.lang = 'ko-KR';
  u.rate = 1.03;
  const voices = speechSynthesis.getVoices();
  const ko = voices.find((v) => v.lang?.startsWith('ko'));
  if (ko) u.voice = ko;
  speechSynthesis.speak(u);
  toast('읽는 중… 정지하려면 다시 정지 버튼');
}

/* ============================================================
   VIEW: me
   ============================================================ */

export async function viewMe(root) {
  if (!auth.isIn) {
    root.append(el('div', { class: 'empty' },
      el('div', { class: 'big' }, '☺'),
      el('h2', {}, '로그인이 필요합니다'),
      el('p', {}, '스크랩, 관심 키워드, AI 질의 기록은 계정에 저장됩니다.'),
      el('div', { style: { marginTop: '18px' } },
        el('button', { class: 'btn primary', onclick: () => openAuth('login') }, '로그인'),
        el('button', { class: 'btn', style: { marginLeft: '8px' }, onclick: () => openAuth('register') }, '회원가입'))));
    return;
  }

  const u = auth.user;
  root.append(el('section', { class: 'hero', style: { paddingBottom: '14px' } },
    el('div', { class: 'kicker' }, el('span', { class: 'dot' }), `${u.email}`),
    el('h1', { style: { fontSize: 'clamp(28px,4.6vw,52px)' } }, u.nickname, el('span', { class: 'grad' }, ' 님')),
    el('p', { class: 'lede' }, `${u.visitCount}번째 방문입니다. 스크랩 ${u.bookmarkCount ?? 0}건.`)));

  const shell = el('div', { class: 'split' });
  const left = el('div');
  const right = el('div');
  shell.append(left, right);
  root.append(el('section', { class: 'section' }, shell));

  // profile form
  const nick = el('input', { value: u.nickname });
  const interests = el('input', { value: u.interests || '' });
  right.append(el('div', { class: 'card', 'data-reveal': '' },
    el('h3', {}, '프로필'),
    el('label', { class: 'field' }, el('span', {}, '닉네임'), nick),
    el('label', { class: 'field' }, el('span', {}, '관심 키워드 (쉼표로 구분)'), interests),
    el('div', { class: 'filters' },
      el('button', {
        class: 'btn primary sm', onclick: async () => {
          try {
            const updated = await api('/api/auth/profile', { method: 'PUT', body: { nickname: nick.value, interests: interests.value } });
            auth.user = { ...auth.user, ...updated };
            toast('저장했습니다', 'good');
          } catch (e) { toast(e.message, 'bad'); }
        },
      }, '저장'),
      el('button', { class: 'btn sm ghost', onclick: () => toggleTheme() }, '테마 전환'),
      el('button', { class: 'btn sm ghost', onclick: () => logout() }, '로그아웃'))));

  // interest feed
  const interestWrap = el('div', { class: 'grid', style: { gap: '10px' } });
  right.append(el('div', { class: 'card', 'data-reveal': '', style: { marginTop: '16px' } },
    el('h3', {}, '관심 키워드 브리핑'), interestWrap));

  const kws = (u.interests || '').split(',').map((s) => s.trim()).filter(Boolean).slice(0, 5);
  if (!kws.length) interestWrap.append(el('p', { class: 'sub' }, '관심 키워드를 등록하면 여기에 관련 기사가 모입니다.'));
  kws.forEach(async (k) => {
    const box = el('div', { style: { borderTop: '1px solid var(--stroke-soft)', paddingTop: '10px' } },
      el('div', { class: 'chip accent' }, k), el('div', { class: 'skel skel-line', style: { marginTop: '8px' } }));
    interestWrap.append(box);
    const r = await api(`/api/news/search?q=${encodeURIComponent(k)}&limit=3`, { quiet: true }).catch(() => ({ items: [] }));
    box.innerHTML = '';
    box.append(el('div', { class: 'chip accent' }, `${k} · ${r.total ?? 0}건`));
    (r.items || []).forEach((a) => box.append(el('a', {
      href: a.link, target: '_blank', rel: 'noopener',
      style: { display: 'block', fontSize: '13px', marginTop: '7px' },
    }, `${a.flag} ${a.title}`)));
  });

  // bookmarks
  const bmWrap = el('div', { class: 'grid g2' });
  left.append(el('div', { class: 'section-head' }, el('h2', {}, '스크랩북')), bmWrap);
  const bms = await api('/api/auth/bookmarks').catch(() => []);
  if (!bms.length) bmWrap.append(el('div', { class: 'card' }, el('p', {}, '아직 스크랩이 없습니다. 기사 카드의 ＋ 스크랩 버튼을 눌러보세요.')));
  bms.forEach((b, i) => {
    bmWrap.append(el('div', { class: 'card lift', 'data-reveal': '', style: { '--d': `${i * 45}ms` } },
      el('div', { class: 'filters', style: { marginBottom: '8px' } },
        el('span', { class: 'chip gold' }, b.kind),
        b.source ? el('span', { class: 'chip' }, b.source) : null,
        el('span', { style: { flex: 1 } }),
        el('button', {
          class: 'chip bad', onclick: async (e) => {
            await api(`/api/auth/bookmarks/${b.id}`, { method: 'DELETE' });
            e.currentTarget.closest('.card').remove();
            toast('삭제했습니다');
          },
        }, '삭제')),
      el('h4', { style: { margin: '0 0 6px', fontSize: '14.5px' } },
        b.url ? el('a', { href: b.url, target: '_blank', rel: 'noopener' }, b.title) : b.title),
      b.memo ? el('p', { style: { fontSize: '12.5px' } }, b.memo.slice(0, 180)) : null));
  });

  // chat history
  const hist = await api('/api/ai/history').catch(() => []);
  if (hist.length) {
    left.append(el('div', { class: 'section-head', style: { marginTop: '30px' } }, el('h2', {}, 'AI 질의 기록')));
    const list = el('div', { class: 'timeline' });
    hist.slice(0, 12).forEach((h) => list.append(el('div', { class: 'tl-item' },
      el('div', { class: 'when' }, `${new Date(h.createdAt).toLocaleString('ko-KR')} · ${h.engine}`),
      el('div', { class: 'what' }, el('b', {}, h.question)),
      el('div', { class: 'prose', style: { fontSize: '13px', marginTop: '6px' }, html: md((h.answer || '').slice(0, 700)) }))));
    left.append(el('div', { class: 'card' }, list));
  }

  observeReveals(root);
  bindSheen(root);
}
