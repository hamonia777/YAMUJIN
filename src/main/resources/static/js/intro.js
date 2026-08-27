/* ============================================================
   YAMUJIN · boot sequence
   The intro is not a fake progress bar. It shows the feeds actually
   connecting, and it ends when the data is really there.
   ============================================================ */

import { $, el } from './core.js';

const FEEDS = [
  ['🇰🇷', '연합뉴스'], ['🇰🇷', '한겨레'], ['🇰🇷', '매일경제'], ['🇰🇷', '동아일보'], ['🇰🇷', '경향신문'],
  ['🇺🇸', 'NPR'], ['🇺🇸', 'WSJ'], ['🇺🇸', 'CNBC'], ['🇺🇸', 'TechCrunch'], ['🇺🇸', 'The Verge'],
  ['🇪🇺', 'BBC Europe'], ['🇪🇺', 'Deutsche Welle'], ['🇪🇺', 'Euronews'], ['🇪🇺', 'France 24'], ['🇪🇺', 'The Guardian'],
  ['🇯🇵', 'NHK'], ['🇯🇵', 'Japan Times'], ['🇨🇳', 'SCMP'], ['🇨🇳', 'Global Times'],
  ['🌍', 'BBC World'], ['🌍', 'Al Jazeera'], ['🌍', 'NYTimes'], ['🌍', 'Sky News'],
  ['⌁', 'Yahoo Finance'], ['🇺🇸', 'Truth Social'],
];

/**
 * Runs the boot overlay. Resolves once the sequence has finished AND
 * `ready` has settled - whichever takes longer, capped so a dead network
 * never traps the user on a splash screen.
 */
export function runIntro(ready) {
  const root = $('#intro');
  if (!root) return Promise.resolve();

  // A reload inside the same session gets the short version. Nobody wants
  // the full title sequence 40 times while they work.
  const seen = sessionStorage.getItem('yamujin.booted') === '1';
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  if (seen || reduced) {
    root.classList.add('quick');
    return ready.finally(() => finish(root, 220));
  }
  sessionStorage.setItem('yamujin.booted', '1');

  const log = $('#introLog', root);
  const bar = $('#introBar', root);
  const pct = $('#introPct', root);
  const status = $('#introStatus', root);

  let done = false;
  const skip = () => { if (!done) { done = true; finish(root, 420); } };
  root.addEventListener('click', skip);
  window.addEventListener('keydown', function esc(e) {
    if (e.key === 'Escape') { window.removeEventListener('keydown', esc); skip(); }
  });

  // ---- feed roll ----
  let i = 0;
  const total = FEEDS.length;
  const roll = setInterval(() => {
    if (i >= total) { clearInterval(roll); return; }
    const [flag, name] = FEEDS[i];
    const line = el('div', { class: 'intro-line' },
      el('span', { class: 'f' }, flag),
      el('span', { class: 'n' }, name),
      el('span', { class: 'ok' }, 'CONNECTED'));
    log.append(line);
    // keep the log short so it reads as a live tail, not a wall
    while (log.children.length > 7) log.firstElementChild.remove();
    i++;
    const p = Math.round((i / total) * 100);
    bar.style.width = `${p}%`;
    pct.textContent = `${p}%`;
    if (p > 30 && p < 70) status.textContent = '피드 파싱 중';
    if (p >= 70) status.textContent = '중복 제거 및 화제도 계산';
  }, 62);

  const sequence = new Promise((resolve) => setTimeout(resolve, total * 62 + 320));
  const capped = Promise.race([ready, new Promise((r) => setTimeout(r, 9000))]);

  return Promise.all([sequence, capped]).then(() => {
    clearInterval(roll);
    if (done) return;
    done = true;
    bar.style.width = '100%';
    pct.textContent = '100%';
    status.textContent = '준비 완료';
    return finish(root, 620);
  });
}

/** The exit is a warp: the overlay rushes toward the viewer and dissolves. */
function finish(root, delay) {
  return new Promise((resolve) => {
    setTimeout(() => {
      root.classList.add('warp');
      setTimeout(() => {
        root.remove();
        document.body.classList.add('booted');
        resolve();
      }, 720);
    }, delay);
  });
}
