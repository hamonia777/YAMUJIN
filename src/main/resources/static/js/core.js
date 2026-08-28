/* ============================================================
   MUJIN · core runtime
   dom helpers, api client, markdown, auth, chrome, animations
   ============================================================ */

export const $  = (sel, root = document) => root.querySelector(sel);
export const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

export function el(tag, props = {}, ...kids) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (v === null || v === undefined || v === false) continue;
    if (k === 'class') node.className = v;
    else if (k === 'html') node.innerHTML = v;
    else if (k === 'style' && typeof v === 'object') Object.assign(node.style, v);
    else if (k.startsWith('on') && typeof v === 'function') node.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === 'dataset') Object.assign(node.dataset, v);
    else node.setAttribute(k, v);
  }
  for (const kid of kids.flat()) {
    if (kid === null || kid === undefined || kid === false) continue;
    node.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
  }
  return node;
}

export const esc = (s) => String(s ?? '')
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;').replace(/'/g, '&#39;');

export const num = (n) => Number(n ?? 0).toLocaleString('ko-KR');

/* ------------------------------------------------------------ progress bar */

let progressTimer = null;
export function progress(state) {
  const bar = $('#progress');
  const inner = bar.firstElementChild;
  clearTimeout(progressTimer);
  if (state === 'start') {
    bar.classList.remove('done');
    inner.style.width = '18%';
    setTimeout(() => { inner.style.width = '62%'; }, 220);
  } else {
    inner.style.width = '100%';
    bar.classList.add('done');
    progressTimer = setTimeout(() => { inner.style.width = '0'; bar.classList.remove('done'); }, 420);
  }
}

/* ------------------------------------------------------------ toast */

export function toast(message, kind = '') {
  const t = el('div', { class: `toast ${kind}` }, message);
  $('#toasts').append(t);
  setTimeout(() => t.remove(), 3600);
}

/* ------------------------------------------------------------ auth store */

const TOKEN_KEY = 'mujin.token';
export const auth = {
  get token() { return localStorage.getItem(TOKEN_KEY) || ''; },
  set token(v) { v ? localStorage.setItem(TOKEN_KEY, v) : localStorage.removeItem(TOKEN_KEY); },
  user: null,
  get isIn() { return !!this.token && !!this.user; },
};

/* ------------------------------------------------------------ api client */

export async function api(path, opts = {}) {
  const headers = { 'Accept': 'application/json', ...(opts.headers || {}) };
  if (opts.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
  if (auth.token) headers['Authorization'] = `Bearer ${auth.token}`;

  const showBar = opts.quiet !== true;
  if (showBar) progress('start');
  try {
    const res = await fetch(path, {
      ...opts,
      headers,
      body: opts.body && typeof opts.body !== 'string' ? JSON.stringify(opts.body) : opts.body,
    });
    const text = await res.text();
    let data;
    try { data = text ? JSON.parse(text) : {}; } catch { data = { raw: text }; }
    if (!res.ok) {
      const msg = data?.error || `요청 실패 (${res.status})`;
      if (res.status === 401 && auth.token) { auth.token = ''; auth.user = null; renderAuthSlot(); }
      throw new Error(msg);
    }
    return data;
  } finally {
    if (showBar) progress('done');
  }
}

/* ------------------------------------------------------------ markdown (small, safe) */

export function md(src) {
  if (!src) return '';
  const lines = String(src).replace(/\r/g, '').split('\n');
  let out = '';
  let list = null;

  const inline = (s) => esc(s)
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[\s(])\*([^*\n]+)\*/g, '$1<em>$2</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>');

  const closeList = () => { if (list) { out += `</${list}>`; list = null; } };

  for (let raw of lines) {
    const line = raw.trimEnd();
    if (!line.trim()) { closeList(); continue; }

    let m;
    if ((m = line.match(/^###\s+(.*)$/)))      { closeList(); out += `<h3>${inline(m[1])}</h3>`; continue; }
    if ((m = line.match(/^##\s+(.*)$/)))       { closeList(); out += `<h2>${inline(m[1])}</h2>`; continue; }
    if ((m = line.match(/^#\s+(.*)$/)))        { closeList(); out += `<h2>${inline(m[1])}</h2>`; continue; }
    if (/^(-{3,}|_{3,})$/.test(line.trim()))   { closeList(); out += '<hr>'; continue; }
    if ((m = line.match(/^>\s?(.*)$/)))        { closeList(); out += `<blockquote>${inline(m[1])}</blockquote>`; continue; }
    if ((m = line.match(/^\s*[-*•]\s+(.*)$/))) {
      if (list !== 'ul') { closeList(); out += '<ul>'; list = 'ul'; }
      out += `<li>${inline(m[1])}</li>`; continue;
    }
    if ((m = line.match(/^\s*\d+[.)]\s+(.*)$/))) {
      if (list !== 'ol') { closeList(); out += '<ol>'; list = 'ol'; }
      out += `<li>${inline(m[1])}</li>`; continue;
    }
    closeList();
    out += `<p>${inline(line)}</p>`;
  }
  closeList();
  return out;
}

/* ------------------------------------------------------------ reveal on scroll */

const revealObserver = new IntersectionObserver((entries) => {
  entries.forEach((entry, i) => {
    if (entry.isIntersecting) {
      entry.target.style.setProperty('--d', `${Math.min(i * 35, 180)}ms`);
      entry.target.classList.add('in');
      revealObserver.unobserve(entry.target);
    }
  });
  // pre-reveal what is just below the fold so scrolling never outruns the animation
}, { threshold: 0, rootMargin: '260px 0px 260px' });

export function observeReveals(root = document) {
  $$('[data-reveal]:not(.in)', root).forEach((n) => revealObserver.observe(n));
}

export function stagger(container, step = 55) {
  [...container.children].forEach((child, i) => {
    child.style.animationDelay = `${i * step}ms`;
  });
  container.classList.add('stagger');
}

/* ------------------------------------------------------------ animated counter */

export function countTo(node, target, { decimals = 0, suffix = '', duration = 1400 } = {}) {
  const start = performance.now();
  const from = 0;
  const tick = (now) => {
    const p = Math.min(1, (now - start) / duration);
    const eased = 1 - Math.pow(1 - p, 3);
    const v = from + (target - from) * eased;
    node.textContent = (decimals ? v.toFixed(decimals) : Math.round(v).toLocaleString('ko-KR')) + suffix;
    if (p < 1) requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
}

/* ------------------------------------------------------------ pointer sheen for cards/buttons */

export function bindSheen(root = document) {
  $$('.card, .btn', root).forEach((node) => {
    if (node.dataset.sheen) return;
    node.dataset.sheen = '1';
    node.addEventListener('pointermove', (e) => {
      const r = node.getBoundingClientRect();
      node.style.setProperty('--mx', `${((e.clientX - r.left) / r.width) * 100}%`);
      node.style.setProperty('--my', `${((e.clientY - r.top) / r.height) * 100}%`);
    });
  });
}

/* ------------------------------------------------------------ starfield */

export function startStarfield() {
  const canvas = $('#starfield');
  const ctx = canvas.getContext('2d');
  let stars = [], w = 0, h = 0, raf = null;

  const resize = () => {
    w = canvas.width = window.innerWidth * devicePixelRatio;
    h = canvas.height = window.innerHeight * devicePixelRatio;
    canvas.style.width = window.innerWidth + 'px';
    canvas.style.height = window.innerHeight + 'px';
    const count = Math.min(180, Math.round((window.innerWidth * window.innerHeight) / 12000));
    stars = Array.from({ length: count }, () => ({
      x: Math.random() * w,
      y: Math.random() * h,
      z: Math.random() * 0.8 + 0.2,
      r: (Math.random() * 1.3 + 0.3) * devicePixelRatio,
      tw: Math.random() * Math.PI * 2,
    }));
  };

  const draw = (t) => {
    ctx.clearRect(0, 0, w, h);
    const light = document.documentElement.dataset.theme === 'light';
    for (const s of stars) {
      s.y -= s.z * 0.16 * devicePixelRatio;
      if (s.y < -4) { s.y = h + 4; s.x = Math.random() * w; }
      const a = 0.28 + Math.sin(t / 900 + s.tw) * 0.24;
      ctx.beginPath();
      ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2);
      ctx.fillStyle = light
        ? `rgba(90,110,160,${a * 0.5})`
        : `rgba(190,215,255,${a})`;
      ctx.fill();
    }
    raf = requestAnimationFrame(draw);
  };

  resize();
  window.addEventListener('resize', resize);
  if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) raf = requestAnimationFrame(draw);
  return () => cancelAnimationFrame(raf);
}

/* ------------------------------------------------------------ cursor glow */

export function startCursorGlow() {
  const glow = $('#cursor-glow');
  let x = innerWidth / 2, y = innerHeight / 2, cx = x, cy = y;
  window.addEventListener('pointermove', (e) => {
    document.body.classList.add('has-pointer');
    x = e.clientX; y = e.clientY;
  }, { passive: true });
  const loop = () => {
    cx += (x - cx) * 0.09;
    cy += (y - cy) * 0.09;
    glow.style.transform = `translate3d(${cx}px, ${cy}px, 0)`;
    requestAnimationFrame(loop);
  };
  requestAnimationFrame(loop);
}

/* ------------------------------------------------------------ nav pill */

export function moveNavPill() {
  const nav = $('#nav');
  const pill = $('#navPill');
  const active = $('a.active', nav);
  if (!active) { pill.style.width = '0'; return; }
  const navBox = nav.getBoundingClientRect();
  const box = active.getBoundingClientRect();
  pill.style.width = `${box.width}px`;
  pill.style.transform = `translateX(${box.left - navBox.left - 4 + nav.scrollLeft}px)`;
}

/* ------------------------------------------------------------ auth UI */

export function renderAuthSlot() {
  const slot = $('#authSlot');
  slot.innerHTML = '';
  if (auth.isIn) {
    const initial = (auth.user.nickname || auth.user.email || '?').trim()[0].toUpperCase();
    slot.append(el('a', { class: 'avatar', href: '#/me', 'data-nav': '' },
      el('i', {}, initial),
      el('span', {}, auth.user.nickname)));
  } else {
    slot.append(el('button', { class: 'btn primary sm', onclick: () => openAuth('login') }, '로그인'));
  }
}

export function openAuth(tab = 'login') {
  const overlay = $('#authOverlay');
  overlay.hidden = false;
  switchAuthTab(tab);
  setTimeout(() => $('#authForm input[name=email]').focus(), 120);
}

export function closeAuth() { $('#authOverlay').hidden = true; }

function switchAuthTab(tab) {
  $$('.auth-tabs button').forEach((b) => b.classList.toggle('active', b.dataset.authTab === tab));
  const isRegister = tab === 'register';
  $$('[data-only=register]').forEach((n) => { n.hidden = !isRegister; });
  $('#authSubmit').textContent = isRegister ? '가입하고 시작하기' : '로그인';
  $('#authForm').dataset.mode = tab;
  $('#authMsg').textContent = '';
  $('#authForm input[name=password]').autocomplete = isRegister ? 'new-password' : 'current-password';
}

export async function loadMe() {
  if (!auth.token) { auth.user = null; renderAuthSlot(); return; }
  try {
    const me = await api('/api/auth/me', { quiet: true });
    auth.user = me.authenticated ? me : null;
    if (!me.authenticated) auth.token = '';
  } catch {
    auth.user = null;
    auth.token = '';
  }
  renderAuthSlot();
}

export function bindAuthForm(onDone) {
  $$('.auth-tabs button').forEach((b) => b.addEventListener('click', () => switchAuthTab(b.dataset.authTab)));
  $$('[data-close-auth]').forEach((b) => b.addEventListener('click', closeAuth));
  $('#authOverlay').addEventListener('click', (e) => { if (e.target.id === 'authOverlay') closeAuth(); });

  $('#authForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const form = e.currentTarget;
    const mode = form.dataset.mode || 'login';
    const fd = new FormData(form);
    const msg = $('#authMsg');
    const btn = $('#authSubmit');
    msg.className = 'auth-msg';
    msg.textContent = '';
    btn.disabled = true;
    try {
      const body = mode === 'register'
        ? { email: fd.get('email'), password: fd.get('password'), nickname: (fd.get('nickname') || '').trim() || '야무진' }
        : { email: fd.get('email'), password: fd.get('password') };
      const res = await api(`/api/auth/${mode}`, { method: 'POST', body });
      auth.token = res.token;
      auth.user = res.user;
      renderAuthSlot();
      closeAuth();
      toast(res.message || '환영합니다', 'good');
      form.reset();
      onDone?.();
    } catch (err) {
      msg.textContent = err.message;
    } finally {
      btn.disabled = false;
    }
  });
}

export function logout() {
  auth.token = '';
  auth.user = null;
  renderAuthSlot();
  toast('로그아웃되었습니다');
  location.hash = '#/';
}

/* ------------------------------------------------------------ bookmarks */

export async function bookmark(payload) {
  if (!auth.isIn) { openAuth('login'); toast('로그인하면 스크랩할 수 있습니다'); return false; }
  await api('/api/auth/bookmarks', { method: 'POST', body: payload });
  toast('스크랩했습니다', 'good');
  return true;
}

/* ------------------------------------------------------------ AI dock */

export function bindAiDock() {
  const dock = $('#aiDock');
  const log = $('#aiLog');
  const open = () => {
    dock.hidden = false;
    if (!log.children.length) {
      pushBubble(log, 'ai', '무엇이든 물어보세요. 지금 크롤링된 전세계 헤드라인을 근거로 답합니다.');
    }
    setTimeout(() => $('#aiQ').focus(), 100);
  };
  $('#aiFab').addEventListener('click', () => (dock.hidden ? open() : (dock.hidden = true)));
  $('#aiClose').addEventListener('click', () => { dock.hidden = true; });

  window.addEventListener('keydown', (e) => {
    if (e.altKey && (e.key === 'a' || e.key === 'A')) { e.preventDefault(); dock.hidden ? open() : (dock.hidden = true); }
  });

  $('#aiForm').addEventListener('submit', (e) => {
    e.preventDefault();
    const input = $('#aiQ');
    const q = input.value.trim();
    if (!q) return;
    input.value = '';
    pushBubble(log, 'me', q);
    const bubble = pushBubble(log, 'ai', '');
    bubble.classList.add('streaming');
    streamAsk(q, (chunk) => {
      bubble.dataset.raw = (bubble.dataset.raw || '') + chunk;
      bubble.innerHTML = md(bubble.dataset.raw);
      log.scrollTop = log.scrollHeight;
    }, () => {
      bubble.classList.remove('streaming');
      log.scrollTop = log.scrollHeight;
    });
  });
}

function pushBubble(log, kind, text) {
  const b = el('div', { class: `bubble ${kind}` });
  b.innerHTML = kind === 'ai' ? md(text) : esc(text);
  log.append(b);
  log.scrollTop = log.scrollHeight;
  return b;
}

/** SSE helper shared by the dock and the AI desk. */
export function streamAsk(question, onChunk, onDone) {
  const src = new EventSource(`/api/ai/chat/stream?q=${encodeURIComponent(question)}`);
  src.addEventListener('delta', (e) => {
    try { onChunk(JSON.parse(e.data)); } catch { onChunk(e.data); }
  });
  src.addEventListener('done', () => { src.close(); onDone?.(); });
  src.onerror = () => { src.close(); onDone?.(); };
  return src;
}

export function streamBriefing(onChunk, onDone) {
  const src = new EventSource('/api/ai/briefing/stream');
  src.addEventListener('delta', (e) => {
    try { onChunk(JSON.parse(e.data)); } catch { onChunk(e.data); }
  });
  src.addEventListener('done', () => { src.close(); onDone?.(); });
  src.onerror = () => { src.close(); onDone?.(); };
  return src;
}

/* ------------------------------------------------------------ ticker */

export async function loadTicker() {
  try {
    const data = await api('/api/news?limit=26', { quiet: true });
    const track = $('#tickerTrack');
    track.innerHTML = '';
    const items = data.items || [];
    if (!items.length) return;
    const build = () => items.map((a) => {
      const node = el('a', { class: 'ticker-item', href: a.link, target: '_blank', rel: 'noopener' },
        el('span', {}, a.flag),
        el('b', {}, a.title.length > 74 ? a.title.slice(0, 74) + '…' : a.title),
        el('span', { style: { opacity: .55 } }, a.relative));
      return node;
    });
    build().forEach((n) => track.append(n));
    build().forEach((n) => track.append(n)); // duplicate for seamless marquee
  } catch { /* ticker is decorative; never block the page */ }
}

/* ------------------------------------------------------------ command palette */

const COMMANDS = [
  { icon: '◎', label: '대시보드',        hint: '#/',       run: () => (location.hash = '#/') },
  { icon: '◈', label: '뉴스 피드',       hint: '#/news',   run: () => (location.hash = '#/news') },
  { icon: '◭', label: '프리즘 (국가별 관점 비교)', hint: '#/prism', run: () => (location.hash = '#/prism') },
  { icon: '⌖', label: '대한민국 정세',   hint: '#/korea',  run: () => (location.hash = '#/korea') },
  { icon: '⌁', label: '마켓 · TRUMP WATCH', hint: '#/market', run: () => (location.hash = '#/market') },
  { icon: '◫', label: '최저가 헌터',     hint: '#/shop',   run: () => (location.hash = '#/shop') },
  { icon: '⁂', label: '지식 그래프',     hint: '#/graph',  run: () => (location.hash = '#/graph') },
  { icon: '✦', label: 'AI 데스크',       hint: '#/ai',     run: () => (location.hash = '#/ai') },
  { icon: '⌬', label: '독립 랩 페이지',  hint: '#/labs',   run: () => (location.hash = '#/labs') },
  { icon: '☻', label: '재미 / 미니게임', hint: '#/fun',    run: () => (location.hash = '#/fun') },
  { icon: '☺', label: '마이페이지',      hint: '#/me',     run: () => (location.hash = '#/me') },
  { icon: '↻', label: '지금 다시 크롤링', hint: 'refresh',  run: () => window.MUJIN.refresh() },
  { icon: '◐', label: '라이트/다크 전환', hint: 'theme',    run: () => toggleTheme() },
  { icon: '⎋', label: '로그아웃',        hint: 'logout',   run: () => logout() },
];

export function bindCommandPalette() {
  const overlay = $('#cmdOverlay');
  const input = $('#cmdInput');
  const list = $('#cmdList');
  let items = [];
  let sel = 0;

  const render = (rows) => {
    items = rows;
    sel = 0;
    list.innerHTML = '';
    rows.forEach((r, i) => {
      const node = el('div', { class: `cmd-item ${i === 0 ? 'sel' : ''}` },
        el('span', { class: 'ic' }, r.icon),
        el('span', {}, r.label),
        el('small', {}, r.hint));
      node.addEventListener('click', () => { close(); r.run(); });
      node.addEventListener('pointerenter', () => {
        sel = i;
        $$('.cmd-item', list).forEach((n, j) => n.classList.toggle('sel', j === i));
      });
      list.append(node);
    });
  };

  const open = () => {
    overlay.hidden = false;
    input.value = '';
    render(COMMANDS);
    setTimeout(() => input.focus(), 60);
  };
  const close = () => { overlay.hidden = true; };

  $('#cmdBtn').addEventListener('click', open);
  overlay.addEventListener('click', (e) => { if (e.target === overlay) close(); });

  input.addEventListener('input', async () => {
    const q = input.value.trim().toLowerCase();
    if (!q) return render(COMMANDS);
    const local = COMMANDS.filter((c) => c.label.toLowerCase().includes(q) || c.hint.includes(q));
    const rows = [...local, {
      icon: '⌕', label: `"${input.value.trim()}" 뉴스 검색`, hint: 'search',
      run: () => (location.hash = `#/news?q=${encodeURIComponent(input.value.trim())}`),
    }, {
      icon: '◭', label: `"${input.value.trim()}" 프리즘 분석`, hint: 'prism',
      run: () => (location.hash = `#/prism?q=${encodeURIComponent(input.value.trim())}`),
    }, {
      icon: '◫', label: `"${input.value.trim()}" 최저가 검색`, hint: 'shop',
      run: () => (location.hash = `#/shop?q=${encodeURIComponent(input.value.trim())}`),
    }];
    render(rows);
  });

  input.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      sel = (sel + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
      $$('.cmd-item', list).forEach((n, j) => n.classList.toggle('sel', j === sel));
      $$('.cmd-item', list)[sel]?.scrollIntoView({ block: 'nearest' });
    } else if (e.key === 'Enter') {
      e.preventDefault();
      close();
      items[sel]?.run();
    }
  });

  window.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && (e.key === 'k' || e.key === 'K')) { e.preventDefault(); overlay.hidden ? open() : close(); }
    if (e.key === 'Escape') { close(); closeAuth(); }
  });
}

/* ------------------------------------------------------------ theme */

export function toggleTheme() {
  const root = document.documentElement;
  const next = root.dataset.theme === 'light' ? 'dark' : 'light';
  root.dataset.theme = next;
  localStorage.setItem('mujin.theme', next);
  toast(next === 'light' ? '라이트 모드' : '다크 모드');
}

export function initTheme() {
  const saved = localStorage.getItem('mujin.theme');
  if (saved) document.documentElement.dataset.theme = saved;
}

/* ------------------------------------------------------------ boss key (Ctrl+Alt+B)
   Deliberately opt-in and shortcut-only: it never appears on its own. */

export function bindBossKey() {
  const boss = $('#bossMode');
  let built = false;

  const build = () => {
    const cols = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K'];
    const headers = ['항목', '1분기', '2분기', '3분기', '4분기', '합계', '전년대비', '비고'];
    const rows = [
      ['매출액', 128400, 131250, 129870, 142310, null, '+8.2%', ''],
      ['매출원가', 74210, 76040, 75330, 81120, null, '+6.9%', ''],
      ['매출총이익', 54190, 55210, 54540, 61190, null, '+11.4%', ''],
      ['판관비', 31420, 32010, 31980, 34220, null, '+5.1%', ''],
      ['영업이익', 22770, 23200, 22560, 26970, null, '+18.3%', '목표 초과'],
      ['영업이익률', 17.7, 17.7, 17.4, 19.0, null, '', '%'],
      ['인건비', 18220, 18540, 18610, 19040, null, '+4.2%', ''],
      ['감가상각비', 4120, 4180, 4210, 4260, null, '+3.1%', ''],
      ['R&D 투자', 9840, 10120, 10450, 11880, null, '+14.7%', '증액 승인'],
      ['해외매출', 41220, 43870, 44120, 49330, null, '+16.2%', ''],
      ['국내매출', 87180, 87380, 85750, 92980, null, '+3.8%', ''],
    ];
    const table = el('table');
    const thead = el('tr', {}, el('th', { class: 'rowhead' }, ''), ...cols.map((c) => el('th', {}, c)));
    table.append(thead);
    table.append(el('tr', {}, el('td', { class: 'rowhead' }, '1'),
      ...headers.map((h) => el('td', { style: { fontWeight: '600', background: '#eef3f8' } }, h)),
      ...Array(cols.length - headers.length).fill(0).map(() => el('td', {}, ''))));
    rows.forEach((r, i) => {
      const sum = r.slice(1, 5).reduce((a, b) => a + (typeof b === 'number' ? b : 0), 0);
      const cells = [r[0], ...r.slice(1, 5).map((v) => (typeof v === 'number' ? v.toLocaleString('en-US') : '')),
        sum ? sum.toLocaleString('en-US') : '', r[6], r[7]];
      table.append(el('tr', {}, el('td', { class: 'rowhead' }, String(i + 2)),
        ...cells.map((c) => el('td', { style: { textAlign: typeof c === 'string' && /^[\d,.]+$/.test(c) ? 'right' : 'left' } }, c ?? '')),
        ...Array(Math.max(0, cols.length - cells.length)).fill(0).map(() => el('td', {}, ''))));
    });
    for (let i = 0; i < 26; i++) {
      table.append(el('tr', {}, el('td', { class: 'rowhead' }, String(rows.length + 2 + i)),
        ...cols.map(() => el('td', {}, ''))));
    }
    boss.append(
      el('div', { class: 'bossbar' }, '2026_사업계획_최종_v7_진짜최종.xlsx  -  Excel'),
      el('div', { class: 'ribbon' }, '파일   홈   삽입   페이지 레이아웃   수식   데이터   검토   보기   도움말'),
      table,
      el('div', { class: 'statusbar' }, '준비   |   Sheet1   Sheet2   Sheet3   |   평균: 34,281   개수: 88   합계: 3,016,728   |   100%'),
    );
    built = true;
  };

  window.addEventListener('keydown', (e) => {
    if (e.ctrlKey && e.altKey && (e.key === 'b' || e.key === 'B' || e.code === 'KeyB')) {
      e.preventDefault();
      if (!built) build();
      boss.hidden = !boss.hidden;
      document.title = boss.hidden ? 'MUJIN · 글로벌 인텔리전스 허브' : '2026_사업계획_최종_v7_진짜최종.xlsx - Excel';
    }
    if (e.key === 'Escape' && !boss.hidden) {
      boss.hidden = true;
      document.title = 'MUJIN · 글로벌 인텔리전스 허브';
    }
  });
}

/* ------------------------------------------------------------ misc formatting */

export function sentimentChip(v) {
  if (v > 0.15) return { cls: 'good', text: `긍정 ${v.toFixed(2)}` };
  if (v < -0.15) return { cls: 'bad', text: `부정 ${v.toFixed(2)}` };
  return { cls: '', text: `중립 ${Number(v).toFixed(2)}` };
}

export function skeletonGrid(count = 6, cls = 'g3') {
  const wrap = el('div', { class: `grid ${cls}` });
  for (let i = 0; i < count; i++) wrap.append(el('div', { class: 'skel skel-card' }));
  return wrap;
}

export function sectionHead(title, sub, ...right) {
  return el('div', { class: 'section-head' },
    el('h2', {}, title),
    sub ? el('span', { class: 'sub' }, sub) : null,
    el('span', { class: 'spacer' }),
    ...right);
}
