/* ============================================================
   YAMUJIN · 마켓 & TRUMP WATCH
   single-series charts: the number carries the value, the line carries the shape.
   direction is never color-alone - every change ships with an arrow and a sign.
   ============================================================ */

import {
  $, $$, el, api, md, observeReveals, bindSheen, sectionHead,
} from './core.js';

const UP = 'var(--good)';
const DOWN = 'var(--bad)';
const FLAT = 'var(--text-faint)';

export const dirColor = (d) => (d === 'up' ? UP : d === 'down' ? DOWN : FLAT);
export const dirArrow = (d) => (d === 'up' ? '▲' : d === 'down' ? '▼' : '―');

export function fmtPrice(v, unit) {
  if (v === null || v === undefined) return '—';
  const n = Number(v);
  const digits = Math.abs(n) >= 1000 ? 0 : Math.abs(n) >= 10 ? 2 : 4;
  return n.toLocaleString('ko-KR', { minimumFractionDigits: digits, maximumFractionDigits: digits })
    + (unit === 'pt' || !unit ? '' : ` ${unit}`);
}

/* ------------------------------------------------------------ sparkline
   A micro-chart inside a stat tile: no axis, no legend, no tooltip.
   Decorative by design - the printed number is the accessible value. */

export function sparkline(values, direction, { w = 132, h = 34 } = {}) {
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  svg.setAttribute('viewBox', `0 0 ${w} ${h}`);
  svg.setAttribute('width', w);
  svg.setAttribute('height', h);
  svg.setAttribute('aria-hidden', 'true');
  svg.style.display = 'block';
  svg.style.overflow = 'visible';

  const vals = (values || []).filter((v) => typeof v === 'number' && isFinite(v));
  if (vals.length < 2) return svg;

  const min = Math.min(...vals);
  const max = Math.max(...vals);
  const span = max - min || 1;
  const x = (i) => (i / (vals.length - 1)) * w;
  const y = (v) => h - 2 - ((v - min) / span) * (h - 4);

  const id = `sg${Math.random().toString(36).slice(2, 8)}`;
  const color = direction === 'up' ? '#34e0a1' : direction === 'down' ? '#ff6b6b' : '#8b95ad';

  const defs = document.createElementNS(ns, 'defs');
  const grad = document.createElementNS(ns, 'linearGradient');
  grad.setAttribute('id', id);
  grad.setAttribute('x1', '0'); grad.setAttribute('y1', '0');
  grad.setAttribute('x2', '0'); grad.setAttribute('y2', '1');
  const s1 = document.createElementNS(ns, 'stop');
  s1.setAttribute('offset', '0%'); s1.setAttribute('stop-color', color); s1.setAttribute('stop-opacity', '.28');
  const s2 = document.createElementNS(ns, 'stop');
  s2.setAttribute('offset', '100%'); s2.setAttribute('stop-color', color); s2.setAttribute('stop-opacity', '0');
  grad.append(s1, s2);
  defs.append(grad);
  svg.append(defs);

  const d = vals.map((v, i) => `${i ? 'L' : 'M'} ${x(i).toFixed(2)} ${y(v).toFixed(2)}`).join(' ');

  const area = document.createElementNS(ns, 'path');
  area.setAttribute('d', `${d} L ${w} ${h} L 0 ${h} Z`);
  area.setAttribute('fill', `url(#${id})`);
  svg.append(area);

  const line = document.createElementNS(ns, 'path');
  line.setAttribute('d', d);
  line.setAttribute('fill', 'none');
  line.setAttribute('stroke', color);
  line.setAttribute('stroke-width', '1.8');
  line.setAttribute('stroke-linecap', 'round');
  line.setAttribute('stroke-linejoin', 'round');
  svg.append(line);

  const dot = document.createElementNS(ns, 'circle');
  dot.setAttribute('cx', x(vals.length - 1));
  dot.setAttribute('cy', y(vals[vals.length - 1]));
  dot.setAttribute('r', '2.6');
  dot.setAttribute('fill', color);
  svg.append(dot);

  return svg;
}

/* ------------------------------------------------------------ intraday line chart
   One series, one axis. Crosshair + tooltip on hover (never a dual axis). */

export function lineChart(points, { height = 320, unit = '', prevClose = null } = {}) {
  const ns = 'http://www.w3.org/2000/svg';
  const W = 900, H = height;
  const PAD = { t: 16, r: 62, b: 26, l: 12 };

  const data = (points || []).filter((p) => typeof p.v === 'number' && isFinite(p.v));
  const wrap = el('div', { style: { position: 'relative' } });
  if (data.length < 2) {
    wrap.append(el('div', { class: 'empty' }, '이 구간의 시세 데이터가 없습니다'));
    return wrap;
  }

  const vals = data.map((p) => p.v);
  let min = Math.min(...vals), max = Math.max(...vals);
  if (prevClose !== null && isFinite(prevClose)) { min = Math.min(min, prevClose); max = Math.max(max, prevClose); }
  const pad = (max - min) * 0.12 || Math.abs(max) * 0.001 || 1;
  min -= pad; max += pad;

  const px = (i) => PAD.l + (i / (data.length - 1)) * (W - PAD.l - PAD.r);
  const py = (v) => PAD.t + (1 - (v - min) / (max - min)) * (H - PAD.t - PAD.b);

  const up = vals[vals.length - 1] >= (prevClose ?? vals[0]);
  const color = up ? '#34e0a1' : '#ff6b6b';

  const svg = document.createElementNS(ns, 'svg');
  svg.setAttribute('viewBox', `0 0 ${W} ${H}`);
  svg.setAttribute('role', 'img');
  svg.setAttribute('aria-label', '기간 내 가격 추이');
  svg.style.width = '100%';
  svg.style.height = 'auto';
  svg.style.display = 'block';

  const mk = (t, a = {}) => {
    const n = document.createElementNS(ns, t);
    for (const [k, v] of Object.entries(a)) n.setAttribute(k, v);
    return n;
  };

  // recessive gridlines + right-hand value axis
  const ticks = 4;
  for (let i = 0; i <= ticks; i++) {
    const v = min + ((max - min) * i) / ticks;
    const y = py(v);
    svg.append(mk('line', {
      x1: PAD.l, y1: y, x2: W - PAD.r, y2: y,
      stroke: 'currentColor', 'stroke-width': .5, opacity: .10,
    }));
    const label = mk('text', {
      x: W - PAD.r + 8, y: y + 3.5, 'font-size': 10.5, fill: 'currentColor', opacity: .5,
      'font-family': 'JetBrains Mono, ui-monospace, monospace',
    });
    label.textContent = fmtPrice(v, '');
    svg.append(label);
  }

  // previous close reference - the line every intraday reader actually compares against
  if (prevClose !== null && isFinite(prevClose)) {
    const y = py(prevClose);
    svg.append(mk('line', {
      x1: PAD.l, y1: y, x2: W - PAD.r, y2: y,
      stroke: 'currentColor', 'stroke-width': 1, opacity: .34, 'stroke-dasharray': '5 5',
    }));
    const tag = mk('text', {
      x: PAD.l + 4, y: y - 6, 'font-size': 10, fill: 'currentColor', opacity: .55,
    });
    tag.textContent = '전일 종가';
    svg.append(tag);
  }

  const d = data.map((p, i) => `${i ? 'L' : 'M'} ${px(i).toFixed(2)} ${py(p.v).toFixed(2)}`).join(' ');

  const gid = `lg${Math.random().toString(36).slice(2, 8)}`;
  const defs = mk('defs');
  const grad = mk('linearGradient', { id: gid, x1: '0', y1: '0', x2: '0', y2: '1' });
  grad.append(mk('stop', { offset: '0%', 'stop-color': color, 'stop-opacity': '.26' }));
  grad.append(mk('stop', { offset: '100%', 'stop-color': color, 'stop-opacity': '0' }));
  defs.append(grad);
  svg.append(defs);

  svg.append(mk('path', {
    d: `${d} L ${px(data.length - 1)} ${H - PAD.b} L ${px(0)} ${H - PAD.b} Z`,
    fill: `url(#${gid})`,
  }));
  svg.append(mk('path', {
    d, fill: 'none', stroke: color, 'stroke-width': 2,
    'stroke-linecap': 'round', 'stroke-linejoin': 'round',
  }));

  // time axis - first / middle / last only, so labels never collide
  [0, Math.floor(data.length / 2), data.length - 1].forEach((i) => {
    const t = mk('text', {
      x: px(i), y: H - 8, 'font-size': 10.5, fill: 'currentColor', opacity: .45,
      'text-anchor': i === 0 ? 'start' : i === data.length - 1 ? 'end' : 'middle',
      'font-family': 'JetBrains Mono, ui-monospace, monospace',
    });
    t.textContent = data[i].label;
    svg.append(t);
  });

  // crosshair layer
  const cross = mk('line', {
    x1: 0, y1: PAD.t, x2: 0, y2: H - PAD.b,
    stroke: 'currentColor', 'stroke-width': 1, opacity: 0,
  });
  const marker = mk('circle', { cx: 0, cy: 0, r: 4.5, fill: color, stroke: 'var(--bg)', 'stroke-width': 2, opacity: 0 });
  svg.append(cross, marker);

  const hit = mk('rect', { x: 0, y: 0, width: W, height: H, fill: 'transparent' });
  svg.append(hit);

  const tip = el('div', {
    style: {
      position: 'absolute', pointerEvents: 'none', opacity: '0', transition: 'opacity .15s',
      background: 'var(--panel-strong)', border: '1px solid var(--stroke)', borderRadius: '10px',
      padding: '7px 11px', fontSize: '12px', whiteSpace: 'nowrap', transform: 'translate(-50%, -130%)',
      backdropFilter: 'blur(10px)', zIndex: '5',
    },
  });
  wrap.append(svg, tip);

  const move = (e) => {
    const rect = svg.getBoundingClientRect();
    const rel = ((e.clientX - rect.left) / rect.width) * W;
    let i = Math.round(((rel - PAD.l) / (W - PAD.l - PAD.r)) * (data.length - 1));
    i = Math.max(0, Math.min(data.length - 1, i));
    const p = data[i];
    cross.setAttribute('x1', px(i)); cross.setAttribute('x2', px(i));
    cross.setAttribute('opacity', '.28');
    marker.setAttribute('cx', px(i)); marker.setAttribute('cy', py(p.v));
    marker.setAttribute('opacity', '1');
    const diff = prevClose !== null && isFinite(prevClose) ? p.v - prevClose : null;
    tip.innerHTML = `<b>${fmtPrice(p.v, unit)}</b>`
      + (diff !== null ? ` <span style="color:${diff >= 0 ? UP : DOWN}">${diff >= 0 ? '▲' : '▼'} ${fmtPrice(Math.abs(diff), '')}</span>` : '')
      + `<br><span style="opacity:.6">${p.label}</span>`;
    tip.style.left = `${(px(i) / W) * 100}%`;
    tip.style.top = `${(py(p.v) / H) * 100}%`;
    tip.style.opacity = '1';
  };
  const leave = () => {
    cross.setAttribute('opacity', '0');
    marker.setAttribute('opacity', '0');
    tip.style.opacity = '0';
  };
  svg.addEventListener('pointermove', move);
  svg.addEventListener('pointerleave', leave);

  return wrap;
}

/* ------------------------------------------------------------ market strip (dashboard) */

export function marketStrip() {
  const strip = el('div', { class: 'grid g4' });
  for (let i = 0; i < 4; i++) strip.append(el('div', { class: 'skel', style: { height: '104px' } }));

  api('/api/market', { quiet: true }).then((data) => {
    strip.innerHTML = '';
    const items = data.items || [];
    if (!items.length) {
      strip.className = '';
      strip.append(el('div', { class: 'card' }, el('p', {}, '시세를 불러오지 못했습니다.')));
      return;
    }
    items.slice(0, 8).forEach((m, i) => strip.append(marketTile(m, i)));
    observeReveals(strip);
    bindSheen(strip);
  }).catch(() => {
    strip.innerHTML = '';
    strip.className = '';
    strip.append(el('div', { class: 'card' }, el('p', {}, '시세 서버에 연결하지 못했습니다.')));
  });

  return strip;
}

export function marketTile(m, i = 0) {
  const color = dirColor(m.direction);
  const sign = m.change > 0 ? '+' : '';
  const tile = el('div', {
    class: 'card lift stat', 'data-reveal': '', style: { '--d': `${i * 45}ms`, cursor: 'pointer', padding: '15px 16px' },
    title: `${m.label} 상세 차트 열기`,
  },
    el('div', { class: 'label', style: { display: 'flex', alignItems: 'center', gap: '6px' } },
      m.label,
      m.marketState && m.marketState !== 'REGULAR'
        ? el('span', { style: { fontSize: '9px', opacity: .6 } }, m.marketState) : null),
    el('div', { class: 'value', style: { fontSize: '23px', margin: '6px 0 2px' } }, fmtPrice(m.price, m.unit)),
    el('div', { style: { display: 'flex', alignItems: 'center', gap: '8px' } },
      el('span', { style: { color, fontSize: '12.5px', fontWeight: '600', fontFamily: 'var(--mono)' } },
        `${dirArrow(m.direction)} ${sign}${fmtPrice(m.change, '')} (${sign}${m.changePct}%)`)),
    (m.spark || []).length > 1
      ? el('div', { style: { marginTop: '9px' } }, sparkline(m.spark, m.direction))
      : null);
  tile.addEventListener('click', () => (location.hash = `#/market?code=${encodeURIComponent(m.code)}`));
  return tile;
}

/* ------------------------------------------------------------ trump watch (dashboard) */

export function trumpCard() {
  const card = el('div', { class: 'card', 'data-reveal': '' },
    el('div', { class: 'skel skel-line', style: { width: '38%' } }),
    el('div', { class: 'skel skel-line' }));

  api('/api/trump?limit=6', { quiet: true }).then((data) => {
    const posts = data.posts || [];
    card.innerHTML = '';
    if (!posts.length) {
      card.append(el('p', {}, 'Truth Social 아카이브에 연결하지 못했습니다.'));
      return;
    }
    const avgShout = posts.reduce((a, p) => a + p.shouty, 0) / posts.length;
    card.append(
      el('div', { class: 'filters', style: { marginBottom: '12px' } },
        el('span', { class: 'chip gold' }, '🇺🇸 TRUMP WATCH'),
        el('span', { class: 'chip' }, `최근 ${posts.length}건`),
        el('span', { class: `chip ${avgShout > 25 ? 'bad' : ''}` }, `대문자 ${avgShout.toFixed(1)}%`),
        el('span', { style: { flex: 1 } }),
        el('a', { class: 'btn sm', href: '#/market', 'data-nav': '' }, '전체 보기 →')),
      ...posts.slice(0, 5).map((p) => el('a', {
        href: p.link || '#', target: '_blank', rel: 'noopener',
        style: {
          display: 'block', padding: '11px 0', borderTop: '1px solid var(--stroke-soft)',
          fontSize: '13.5px', lineHeight: '1.6',
        },
      },
        el('div', { style: { fontSize: '11px', color: 'var(--text-faint)', marginBottom: '3px', display: 'flex', gap: '7px' } },
          el('span', {}, p.relative),
          p.retruth ? el('span', { class: 'chip' }, '리트루스') : null,
          p.shouty > 30 ? el('span', { class: 'chip bad' }, '고성') : null),
        el('div', {}, p.text.length > 190 ? p.text.slice(0, 190) + '…' : p.text))),
      el('p', { style: { fontSize: '11px', color: 'var(--text-faint)', marginTop: '12px' } },
        '출처: trumpstruth.org · Truth Social 공개 아카이브. X(트위터)는 키 없이 접근할 수 없어 원 플랫폼을 직접 봅니다.'));
    bindSheen(card);
  }).catch(() => {
    card.innerHTML = '';
    card.append(el('p', {}, 'TRUMP WATCH를 불러오지 못했습니다.'));
  });

  return card;
}

/* ============================================================
   VIEW: market
   ============================================================ */

const RANGES = [
  { key: '1d', label: '1일', interval: '5m' },
  { key: '5d', label: '5일', interval: '30m' },
  { key: '1mo', label: '1개월', interval: '1d' },
  { key: '6mo', label: '6개월', interval: '1d' },
  { key: '1y', label: '1년', interval: '1d' },
  { key: '5y', label: '5년', interval: '1wk' },
];

export async function viewMarket(root, params) {
  let code = params.get('code') || '^IXIC';
  let range = RANGES[0];

  root.append(el('section', { class: 'hero', style: { paddingBottom: '12px' } },
    el('div', { class: 'kicker' }, el('span', { class: 'dot' }), 'MARKETS · TRUMP WATCH'),
    el('h1', { style: { fontSize: 'clamp(28px,4.6vw,52px)' } }, '숫자와 ', el('span', { class: 'grad' }, '소음')),
    el('p', { class: 'lede' },
      '나스닥·S&P·다우·코스피·코스닥·환율·비트코인·유가·금·VIX를 한 줄에 놓고, ' +
      '그 옆에 시장을 흔드는 사람의 실시간 게시물을 붙였습니다.')));

  // ---- board ----
  const board = el('div', { class: 'grid g4' });
  for (let i = 0; i < 8; i++) board.append(el('div', { class: 'skel', style: { height: '104px' } }));
  const moodChip = el('span', { class: 'sub' }, '');
  root.append(el('section', { class: 'section' },
    sectionHead('시세 보드', '', moodChip), board));

  // ---- detail chart ----
  const rangeSeg = el('div', { class: 'seg' });
  const symbolSeg = el('div', { class: 'seg' });
  const chartBox = el('div', { class: 'card', 'data-reveal': '' }, el('div', { class: 'skel skel-card', style: { height: '320px' } }));
  root.append(el('section', { class: 'section' },
    sectionHead('상세 차트', '마우스를 올리면 그 시각의 값이 표시됩니다'),
    el('div', { class: 'filters' }, symbolSeg),
    el('div', { class: 'filters' }, rangeSeg),
    chartBox));

  // ---- trump ----
  const trumpBox = el('div', { class: 'split' });
  root.append(el('section', { class: 'section' },
    sectionHead('TRUMP WATCH', 'Truth Social 원문 + Claude 요약',
      el('button', { class: 'btn sm', onclick: () => loadTrump(true) }, '↻ 다시 분석')),
    trumpBox));

  observeReveals(root);

  // ---- load board ----
  const data = await api('/api/market').catch(() => null);
  board.innerHTML = '';
  if (!data?.items?.length) {
    board.className = '';
    board.append(el('div', { class: 'empty' }, el('div', { class: 'big' }, '📉'), el('div', {}, '시세를 불러오지 못했습니다')));
  } else {
    data.items.forEach((m, i) => board.append(marketTile(m, i)));
    moodChip.textContent = `${data.mood.verdict} · 상승 ${data.mood.up} / 하락 ${data.mood.down}`;

    data.items.forEach((m) => {
      symbolSeg.append(el('button', {
        class: m.code === code ? 'active' : '',
        onclick: (e) => {
          code = m.code;
          $$('button', symbolSeg).forEach((b) => b.classList.remove('active'));
          e.currentTarget.classList.add('active');
          loadChart();
        },
      }, m.label));
    });
  }

  RANGES.forEach((r) => rangeSeg.append(el('button', {
    class: r.key === range.key ? 'active' : '',
    onclick: (e) => {
      range = r;
      $$('button', rangeSeg).forEach((b) => b.classList.remove('active'));
      e.currentTarget.classList.add('active');
      loadChart();
    },
  }, r.label)));

  async function loadChart() {
    chartBox.innerHTML = '';
    chartBox.append(el('div', { class: 'skel skel-card', style: { height: '320px' } }));
    const q = await api(`/api/market/${encodeURIComponent(code)}?range=${range.key}&interval=${range.interval}`)
      .catch(() => null);
    chartBox.innerHTML = '';
    if (!q || q.error) {
      chartBox.append(el('div', { class: 'empty' }, q?.error || '차트를 불러오지 못했습니다'));
      return;
    }
    const intraday = range.key === '1d' || range.key === '5d';
    const points = (q.series || []).map((v, i) => {
      const ts = (q.timestamps || [])[i];
      const dt = ts ? new Date(ts * 1000) : null;
      return {
        v,
        label: dt
          ? (intraday
            ? dt.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false })
            : dt.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' }))
          : String(i),
      };
    });
    const sign = q.change > 0 ? '+' : '';
    chartBox.append(
      el('div', { class: 'filters', style: { marginBottom: '14px', alignItems: 'baseline' } },
        el('h3', { style: { margin: 0, fontSize: '18px' } }, q.label),
        el('span', { style: { fontSize: '26px', fontWeight: '800', letterSpacing: '-.03em' } }, fmtPrice(q.price, q.unit)),
        el('span', {
          style: { color: dirColor(q.direction), fontWeight: '600', fontFamily: 'var(--mono)', fontSize: '13px' },
        }, `${dirArrow(q.direction)} ${sign}${fmtPrice(q.change, '')} (${sign}${q.changePct}%)`),
        el('span', { style: { flex: 1 } }),
        q.exchange ? el('span', { class: 'chip' }, q.exchange) : null,
        el('span', { class: 'chip' }, range.label)),
      lineChart(points, { unit: q.unit, prevClose: q.previousClose }),
      el('p', { style: { fontSize: '11px', color: 'var(--text-faint)', marginTop: '10px' } },
        'Yahoo Finance 공개 데이터 · 지연 시세일 수 있습니다. 투자 판단의 근거로 쓰지 마세요.'));
  }
  loadChart();

  async function loadTrump(force = false) {
    trumpBox.innerHTML = '';
    trumpBox.append(el('div', { class: 'card' },
      el('div', { class: 'skel skel-line', style: { width: '34%' } }),
      el('div', { class: 'skel skel-line' })));
    const d = await api(`/api/trump/digest${force ? '?force=true' : ''}`).catch(() => null);
    trumpBox.innerHTML = '';
    if (!d) {
      trumpBox.append(el('div', { class: 'empty' }, 'TRUMP WATCH를 불러오지 못했습니다'));
      return;
    }
    trumpBox.append(
      el('div', { class: 'card', 'data-reveal': '' },
        el('div', { class: 'filters', style: { marginBottom: '10px' } },
          el('span', { class: `chip ${d.live ? 'good' : 'gold'}` }, d.live ? `✦ ${d.engine}` : '◇ 로컬 엔진'),
          el('span', { class: 'chip' }, `${d.count ?? 0}건 수집`),
          d.avgShout !== undefined ? el('span', { class: 'chip' }, `대문자 ${d.avgShout}%`) : null),
        el('div', { class: 'prose', html: md(d.markdown) }),
        el('p', { style: { fontSize: '11px', color: 'var(--text-faint)', marginTop: '10px' } }, d.source || '')),
      el('div', { class: 'card', 'data-reveal': '' },
        el('h3', {}, '원문'),
        ...(d.posts || []).map((p) => el('a', {
          href: p.link || '#', target: '_blank', rel: 'noopener',
          style: { display: 'block', padding: '10px 0', borderTop: '1px solid var(--stroke-soft)', fontSize: '13px', lineHeight: '1.6' },
        },
          el('div', { style: { fontSize: '11px', color: 'var(--text-faint)', marginBottom: '3px' } },
            `${p.relative}${p.retruth ? ' · 리트루스' : ''}${p.shouty > 30 ? ' · 고성' : ''}`),
          el('div', {}, p.text.length > 260 ? p.text.slice(0, 260) + '…' : p.text)))));
    observeReveals(trumpBox);
    bindSheen(trumpBox);
  }
  loadTrump();

  bindSheen(root);
}
