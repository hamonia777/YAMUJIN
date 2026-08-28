/* ============================================================
   MUJIN · router + bootstrap
   ============================================================ */

import {
  $, $$, el, api, toast, auth, loadMe, bindAuthForm, renderAuthSlot, bindAiDock,
  bindCommandPalette, bindBossKey, initTheme, startStarfield, startCursorGlow,
  moveNavPill, loadTicker, observeReveals, bindSheen, progress,
} from './core.js';

import {
  viewDashboard, viewNews, viewPrism, viewKorea, viewShop, viewGraph, viewAi, viewMe,
} from './views.js';

import { viewFun, viewGames, viewLabs } from './fun.js';
import { viewMarket } from './market.js';

/* ------------------------------------------------------------ routes */

const ROUTES = {
  '/':      { title: '대시보드',        render: viewDashboard },
  '/news':  { title: '뉴스',            render: viewNews },
  '/prism': { title: '프리즘',          render: viewPrism },
  '/korea': { title: '대한민국 정세',   render: viewKorea },
  '/market':{ title: '마켓',            render: viewMarket },
  '/shop':  { title: '최저가',          render: viewShop },
  '/graph': { title: '지식 그래프',     render: viewGraph },
  '/ai':    { title: 'AI 데스크',       render: viewAi },
  '/labs':  { title: '독립 랩',         render: viewLabs },
  '/fun':   { title: '재미',            render: viewFun },
  '/games': { title: '미니게임',        render: viewGames },
  '/me':    { title: '마이페이지',      render: viewMe },
};

function parseHash() {
  const raw = location.hash.replace(/^#/, '') || '/';
  const [path, qs] = raw.split('?');
  return { path: path || '/', params: new URLSearchParams(qs || '') };
}

let currentPath = null;
// Every render gets a number. Only the newest one is allowed to finish the job -
// touch the progress bar, wire reveals, or report an error.
let renderToken = 0;

async function render() {
  const token = ++renderToken;

  const { path, params } = parseHash();
  const route = ROUTES[path] || ROUTES['/'];

  document.title = path === '/' ? 'MUJIN · 글로벌 인텔리전스 허브' : `${route.title} · MUJIN`;
  markActiveNav(path);

  const view = $('#view');
  const same = currentPath === path;
  currentPath = path;

  // Each render draws into its own container. A navigation that lands mid-render
  // detaches this one, so a slow screen's late writes go somewhere invisible instead
  // of spraying cards into whatever the user opened next. Claude-backed views can
  // take twenty seconds; queueing them behind each other made the nav feel dead.
  const swap = () => {
    const canvas = el('div', { class: 'view-canvas' });
    view.replaceChildren(canvas);
    view.scrollTop = 0;
    if (!same) window.scrollTo({ top: 0, behavior: 'instant' in window ? 'instant' : 'auto' });
    return canvas;
  };

  const fill = async (canvas) => {
    progress('start');
    try {
      await route.render(canvas, params);
    } catch (err) {
      if (token !== renderToken) return;
      console.error(err);
      canvas.append(el('div', { class: 'empty' },
        el('div', { class: 'big' }, '⚠'),
        el('h2', {}, '이 화면을 그리지 못했습니다'),
        el('p', {}, err.message || String(err)),
        el('button', { class: 'btn primary', style: { marginTop: '14px' }, onclick: () => render() }, '다시 시도')));
    } finally {
      if (token === renderToken) {
        progress('done');
        observeReveals(canvas);
        bindSheen(canvas);
      }
    }
  };

  // Hand-rolled zoom rather than the View Transitions API. Two reasons, both measured:
  // startViewTransition kept throwing InvalidStateError here (it refuses while the document
  // is hidden or another transition is live), and it holds its update callback - so the old
  // shape, which awaited the whole render inside it, stalled the animation on screens that
  // wait seconds on Claude. Same keyframes either way; what is lost is the simultaneous
  // cross-fade of the two snapshots, which this trades for out-then-in.
  if (same) {
    await fill(swap());
    return;
  }

  warpFlash();
  view.classList.add('leaving');
  await new Promise((r) => setTimeout(r, 210));
  view.classList.remove('leaving');

  const canvas = swap();
  view.classList.add('entering');
  setTimeout(() => view.classList.remove('entering'), 620);

  await fill(canvas);
}

/** A short radial sweep behind the zoom, so the cut reads as intentional. */
function warpFlash() {
  let node = $('#warpFlash');
  if (!node) {
    node = el('div', { id: 'warpFlash' });
    document.body.append(node);
  }
  node.classList.remove('on');
  void node.offsetWidth;   // restart the animation
  node.classList.add('on');
}

function markActiveNav(path) {
  $$('#nav a').forEach((a) => {
    const target = a.getAttribute('href').replace(/^#/, '').split('?')[0];
    a.classList.toggle('active', target === path);
  });
  // /games and /me have no nav entry of their own - highlight the closest relative
  if (!$('#nav a.active') && path === '/games') {
    $('#nav a[href="#/fun"]')?.classList.add('active');
  }
  requestAnimationFrame(moveNavPill);
}

/* ------------------------------------------------------------ chrome bindings */

function bindChrome() {
  // intercept in-app links so the router animates instead of the browser jumping
  document.addEventListener('click', (e) => {
    const a = e.target.closest('a[data-nav]');
    if (!a) return;
    const href = a.getAttribute('href');
    if (!href?.startsWith('#')) return;
    e.preventDefault();
    if (href === location.hash) render();
    else location.hash = href;
  });

  $('#refreshBtn').addEventListener('click', () => refresh());

  window.addEventListener('hashchange', render);
  window.addEventListener('resize', () => requestAnimationFrame(moveNavPill));
  $('#nav').addEventListener('scroll', () => moveNavPill());

  // web fonts land after first paint and change link widths - re-measure once they do
  document.fonts?.ready?.then(() => moveNavPill());
  setTimeout(moveNavPill, 400);
  setTimeout(moveNavPill, 1200);
}

async function refresh() {
  const btn = $('#refreshBtn');
  btn.classList.add('spinning');
  try {
    const r = await api('/api/news/refresh', { method: 'POST' });
    toast(`다시 크롤링했습니다 · ${r.crawled}건`, 'good');
    await loadTicker();
    await render();
  } catch (e) {
    toast(e.message, 'bad');
  } finally {
    btn.classList.remove('spinning');
  }
}

async function loadFooterMeta() {
  try {
    const meta = await api('/api/meta', { quiet: true });
    const parts = [];
    // the credential path matters when debugging a quiet failure - say which one answered
    const via = /CLI/.test(meta.claude.source || '') ? ' (구독 CLI)' : '';
    parts.push(meta.claude.live ? `Claude ${meta.claude.engine}${via}` : '로컬 엔진');
    parts.push(meta.shopping.live ? '쇼핑 실가격 ON' : '쇼핑 딥링크 모드');
    if (meta.crawler?.cachedArticles) parts.push(`캐시 ${meta.crawler.cachedArticles}건`);
    if (meta.crawler?.feeds) $('#footFeeds').textContent = meta.crawler.feeds;
    // the CLI tier is the one with a real ceiling - say so when it starts refusing work
    if (meta.claude?.usage?.rejected) parts.push(`CLI 대기 반려 ${meta.claude.usage.rejected}건`);
    $('#footMeta').textContent = `· ${parts.join(' · ')}`;
  } catch { /* footer meta is cosmetic */ }
}

/* ------------------------------------------------------------ boot */

(async function boot() {
  initTheme();
  startStarfield();
  startCursorGlow();
  bindChrome();
  bindCommandPalette();
  bindBossKey();
  bindAiDock();
  bindAuthForm(() => render());
  renderAuthSlot();

  // Warm the first sweep and the session in parallel, then paint immediately.
  // Nothing is gated behind an animation - the dashboard is the landing screen.
  api('/api/news/pulse', { quiet: true }).catch(() => {});
  await loadMe();

  render();
  loadTicker();
  loadFooterMeta();

  // keep the ticker and footer fresh without disturbing the current view
  setInterval(loadTicker, 5 * 60 * 1000);
  setInterval(loadFooterMeta, 5 * 60 * 1000);

  window.MUJIN = { render, refresh, api, auth };
  console.log('%cMUJIN', 'font:700 22px Space Grotesk;background:linear-gradient(90deg,#6ee7ff,#a78bfa,#ff5ea8);-webkit-background-clip:text;color:transparent',
    '\nCtrl+K 명령 팔레트 · Alt+A AI 비서 · Ctrl+Alt+B 사장님 모드');
})();
