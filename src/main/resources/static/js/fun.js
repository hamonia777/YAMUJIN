/* ============================================================
   MUJIN · 재미 / 미니게임 / 랩
   전부 명시적 클릭으로만 실행됩니다. 메인 흐름에는 절대 끼어들지 않습니다.
   ============================================================ */

import {
  $, $$, el, api, md, toast, auth, observeReveals, bindSheen, sectionHead, countTo,
} from './core.js';
import { speak } from './views.js';

/* ============================================================
   VIEW: fun
   ============================================================ */

export async function viewFun(root) {
  root.append(el('section', { class: 'hero', style: { paddingBottom: '10px' } },
    el('div', { class: 'kicker' }, el('span', { class: 'dot' }), 'NON-ESSENTIAL WING · 전부 수동 실행'),
    el('h1', { style: { fontSize: 'clamp(28px,4.6vw,52px)' } }, '쓸데없이 ', el('span', { class: 'grad' }, '정성 들인 것들')),
    el('p', { class: 'lede' },
      '여기 있는 건 전부 눌러야만 작동합니다. 대시보드나 뉴스 읽는 흐름에는 절대 끼어들지 않습니다. ' +
      '팝업도, 랜덤 효과음도 없습니다.')));

  const grid = el('div', { class: 'grid g2' });
  root.append(el('section', { class: 'section' },
    sectionHead('도구들', '', el('a', { class: 'btn sm', href: '#/games', 'data-nav': '' }, '미니게임 →')),
    grid));

  grid.append(cardGacha(), cardFortune(), cardJoseon(), cardTone(), cardSalary(), cardExcuse(), cardDoomsday(), cardBossKey());

  // 다 부숴
  root.append(el('section', { class: 'section' },
    sectionHead('마지막 버튼', '누르면 이 페이지의 카드들이 물리법칙에 굴복합니다'),
    el('div', { class: 'card', style: { textAlign: 'center' } },
      el('p', { style: { marginBottom: '14px' } }, '되돌리려면 새로고침하거나 다른 탭으로 이동했다 오면 됩니다.'),
      el('button', { class: 'btn primary', onclick: () => demolish(root) }, '💥 다 부수기'))));

  observeReveals(root);
  bindSheen(root);
}

/* ---------------- 가챠 ---------------- */

function cardGacha() {
  const stage = el('div', { style: { minHeight: '190px', display: 'grid', placeItems: 'center' } },
    el('p', { class: 'sub' }, '오늘의 헤드라인을 카드로 뽑습니다. 등급은 그 기사의 실제 화제도로 정해집니다.'));

  const card = el('div', { class: 'card lift', 'data-reveal': '' },
    el('h3', {}, '🎴 뉴스 가챠'),
    stage,
    el('div', { class: 'filters' },
      el('button', {
        class: 'btn primary sm', onclick: async (e) => {
          const btn = e.currentTarget;
          btn.disabled = true;
          stage.innerHTML = '';
          const spinner = el('div', { style: { fontSize: '52px', animation: 'spin .55s linear infinite' } }, '🎴');
          stage.append(spinner);
          const d = await api(`/api/fun/gacha?seed=${Date.now()}`, { quiet: true }).catch(() => null);
          setTimeout(() => {
            stage.innerHTML = '';
            if (!d || d.error) { stage.append(el('p', {}, d?.error || '뽑기 실패')); btn.disabled = false; return; }
            const face = el('div', {
              style: {
                width: '100%', padding: '18px', borderRadius: '16px',
                border: `1.5px solid ${d.color}`, background: `linear-gradient(160deg, ${d.color}22, transparent)`,
                boxShadow: `0 18px 44px -20px ${d.color}`,
                animation: 'pop .6s cubic-bezier(.22,1,.36,1) both',
              },
            },
              el('div', { class: 'filters', style: { marginBottom: '10px' } },
                el('span', { class: 'chip', style: { color: d.color, borderColor: d.color } }, d.rarity),
                el('span', { class: 'chip' }, `${d.flag} ${d.region}`),
                el('span', { class: 'chip' }, d.source)),
              el('div', { style: { fontSize: '15px', fontWeight: '600', lineHeight: '1.45', marginBottom: '12px' } }, d.title),
              el('div', { class: 'grid g3', style: { gap: '8px' } },
                miniStat('ATK', d.attack, d.color), miniStat('DEF', d.defense, d.color), miniStat('SPD', d.speed, d.color)),
              el('a', { class: 'btn sm ghost', href: d.link, target: '_blank', rel: 'noopener', style: { marginTop: '12px' } }, '원문 보기 ↗'));
            stage.append(face);
            btn.disabled = false;
          }, 620);
        },
      }, '뽑기'),
      el('span', { class: 'sub' }, 'UR / SSR / SR / R / N')));
  return card;
}

function miniStat(label, value, color) {
  const v = el('b', { style: { display: 'block', fontSize: '18px', color } }, '0');
  setTimeout(() => countTo(v, value, { duration: 900 }), 120);
  return el('div', { style: { textAlign: 'center', padding: '8px', borderRadius: '10px', background: 'var(--panel)' } },
    el('small', { style: { fontSize: '10px', color: 'var(--text-faint)', fontFamily: 'var(--mono)' } }, label), v);
}

/* ---------------- 운세 ---------------- */

function cardFortune() {
  const body = el('div', {}, el('p', { class: 'sub' }, '같은 사람, 같은 날짜면 결과가 항상 같습니다. 좋은 결과 나올 때까지 새로고침하는 걸 막아뒀습니다.'));
  return el('div', { class: 'card lift', 'data-reveal': '' },
    el('h3', {}, '🔮 오늘의 뉴스 운세'),
    body,
    el('div', { class: 'filters' },
      el('button', {
        class: 'btn primary sm', onclick: async () => {
          const d = await api('/api/fun/fortune', { quiet: true }).catch(() => null);
          if (!d) return;
          body.innerHTML = '';
          body.append(
            el('div', { class: 'filters', style: { marginBottom: '12px' } },
              el('span', { class: 'chip gold', style: { fontSize: '14px', padding: '6px 14px' } }, d.grade),
              el('span', { class: 'chip' }, `운세 지수 ${d.luck}`),
              el('span', { class: 'chip' }, d.date)),
            el('div', { class: 'grid g3', style: { gap: '8px', marginBottom: '12px' } },
              kv('행운의 색', d.color), kv('행운의 방향', d.direction), kv('지녀야 할 것', d.item)),
            el('p', { style: { fontSize: '13.5px' } }, d.reading),
            el('p', { style: { fontSize: '13px', color: 'var(--bad)' } }, `오늘 피할 것: ${d.avoid}`),
            el('p', { style: { fontSize: '11px', color: 'var(--text-faint)', marginTop: '8px' } }, d.disclaimer));
        },
      }, '오늘 운세 보기')));
}

function kv(k, v) {
  return el('div', { style: { padding: '9px', borderRadius: '10px', background: 'var(--panel)', textAlign: 'center' } },
    el('small', { style: { fontSize: '10px', color: 'var(--text-faint)', display: 'block' } }, k),
    el('b', { style: { fontSize: '13px' } }, v));
}

/* ---------------- 조선왕조실록 ---------------- */

function cardJoseon() {
  const body = el('div', {}, el('p', { class: 'sub' }, '오늘의 헤드라인을 사관(史官)이 실록에 적으면 이렇게 됩니다. 사신은 논한다.'));
  return el('div', { class: 'card lift', 'data-reveal': '' },
    el('h3', {}, '📜 조선왕조실록 번역기'),
    body,
    el('div', { class: 'filters' },
      el('button', {
        class: 'btn primary sm', onclick: async (e) => {
          e.currentTarget.disabled = true;
          body.innerHTML = '<div class="skel skel-line"></div><div class="skel skel-line" style="width:80%"></div>';
          const d = await api('/api/fun/joseon').catch(() => null);
          body.innerHTML = '';
          if (!d) { body.append(el('p', {}, '실패했습니다')); e.currentTarget.disabled = false; return; }
          body.append(
            el('div', {
              class: 'prose',
              style: { whiteSpace: 'pre-wrap', fontFamily: 'serif', fontSize: '14.5px', lineHeight: '1.9' },
            }, d.text),
            el('div', { class: 'filters', style: { marginTop: '10px' } },
              el('button', { class: 'btn sm ghost', onclick: () => speak(d.text) }, '🔊 낭독')));
          e.currentTarget.disabled = false;
        },
      }, '실록에 기록하기')));
}

/* ---------------- 톤 변환 ---------------- */

function cardTone() {
  const input = el('textarea', { rows: '3', placeholder: '변환할 문장을 넣으세요' });
  const out = el('div', { class: 'prose', style: { minHeight: '40px' } });
  const tones = ['조폭', '할머니', '아나운서', '중2병', 'MZ', '면접관'];
  let tone = '아나운서';
  const seg = el('div', { class: 'seg' });
  tones.forEach((t) => seg.append(el('button', {
    class: t === tone ? 'active' : '',
    onclick: (e) => { tone = t; $$('button', seg).forEach((b) => b.classList.remove('active')); e.currentTarget.classList.add('active'); },
  }, t)));

  return el('div', { class: 'card lift', 'data-reveal': '' },
    el('h3', {}, '🎭 문체 변환기'),
    el('p', { class: 'sub', style: { marginBottom: '10px' } }, '사실관계는 그대로 두고 말투만 바꿉니다.'),
    el('label', { class: 'field' }, input),
    el('div', { class: 'filters' }, seg),
    el('div', { class: 'filters' },
      el('button', {
        class: 'btn primary sm', onclick: async () => {
          const text = input.value.trim();
          if (!text) { toast('문장을 입력하세요'); return; }
          out.innerHTML = '<div class="skel skel-line"></div>';
          const d = await api('/api/fun/tone', { method: 'POST', body: { text, tone } }).catch(() => null);
          out.innerHTML = d ? md(d.text) : '실패';
        },
      }, '변환'),
      el('button', {
        class: 'btn sm ghost', onclick: async () => {
          const b = await api('/api/ai/briefing', { quiet: true }).catch(() => null);
          if (b) { input.value = (b.markdown || '').replace(/[#*>]/g, '').slice(0, 600); toast('오늘 브리핑을 넣었습니다'); }
        },
      }, '오늘 브리핑 넣기')),
    out);
}

/* ---------------- 월급 카운터 ---------------- */

function cardSalary() {
  const KEY = 'mujin.salary';
  const saved = JSON.parse(localStorage.getItem(KEY) || '{}');
  const salaryInput = el('input', { type: 'number', value: saved.salary || 40000000, min: '0', step: '1000000' });
  const startInput = el('input', { type: 'time', value: saved.start || '09:00' });
  const endInput = el('input', { type: 'time', value: saved.end || '18:00' });
  const readout = el('div', { style: { fontSize: '34px', fontWeight: '800', letterSpacing: '-.04em', fontFamily: 'var(--mono)' } }, '0원');
  const sub = el('p', { class: 'sub' }, '오늘 근무 시작 이후 적립된 금액입니다.');
  let timer = null;

  const tick = () => {
    const salary = Number(salaryInput.value) || 0;
    const [sh, sm] = startInput.value.split(':').map(Number);
    const [eh, em] = endInput.value.split(':').map(Number);
    const now = new Date();
    const start = new Date(now); start.setHours(sh, sm, 0, 0);
    const end = new Date(now); end.setHours(eh, em, 0, 0);
    const workMs = Math.max(1, end - start);
    const perMs = salary / 12 / 21 / workMs; // 월급 / 근무일 / 하루 근무시간
    const elapsed = Math.min(Math.max(0, now - start), workMs);
    const earned = perMs * elapsed;
    readout.textContent = `${Math.floor(earned).toLocaleString('ko-KR')}원`;
    const pct = (elapsed / workMs) * 100;
    sub.textContent = now < start
      ? '아직 근무 시작 전입니다. 커피 드세요.'
      : now > end ? '오늘 근무 종료. 퇴근하셨길 바랍니다.'
      : `오늘 ${pct.toFixed(1)}% 소화 · 초당 ${(perMs * 1000).toFixed(2)}원`;
    localStorage.setItem(KEY, JSON.stringify({ salary: salaryInput.value, start: startInput.value, end: endInput.value }));
  };

  const card = el('div', { class: 'card lift', 'data-reveal': '' },
    el('h3', {}, '💸 실시간 월급 적립기'),
    el('div', { class: 'grid g3', style: { gap: '8px' } },
      el('label', { class: 'field' }, el('span', {}, '연봉(원)'), salaryInput),
      el('label', { class: 'field' }, el('span', {}, '출근'), startInput),
      el('label', { class: 'field' }, el('span', {}, '퇴근'), endInput)),
    readout, sub);

  [salaryInput, startInput, endInput].forEach((n) => n.addEventListener('input', tick));
  tick();
  timer = setInterval(tick, 1000);
  // stop the interval when this card leaves the DOM
  new MutationObserver((_, obs) => {
    if (!document.contains(card)) { clearInterval(timer); obs.disconnect(); }
  }).observe(document.body, { childList: true, subtree: true });

  return card;
}

/* ---------------- 변명 생성기 ---------------- */

function cardExcuse() {
  const input = el('input', { placeholder: '예: 보고서 마감을 하루 넘겼다' });
  const out = el('div', { class: 'prose' });
  return el('div', { class: 'card lift', 'data-reveal': '' },
    el('h3', {}, '🙇 변명 생성기'),
    el('p', { class: 'sub', style: { marginBottom: '10px' } }, '거짓말은 안 만듭니다. 정공법 / 무난 / 절대 보내면 안 되는 버전 3종.'),
    el('label', { class: 'field' }, input),
    el('div', { class: 'filters' },
      el('button', {
        class: 'btn primary sm', onclick: async () => {
          const situation = input.value.trim();
          if (!situation) { toast('상황을 입력하세요'); return; }
          out.innerHTML = '<div class="skel skel-line"></div>';
          const d = await api('/api/fun/excuse', { method: 'POST', body: { situation, level: '보통' } }).catch(() => null);
          out.innerHTML = d ? md(d.text) : '실패';
        },
      }, '생성')),
    out);
}

/* ---------------- 종말시계 ---------------- */

function cardDoomsday() {
  const body = el('div', {}, el('p', { class: 'sub' }, '오늘 수집된 기사의 부정 비율로 계산합니다. 과학적 근거는 전혀 없습니다.'));
  return el('div', { class: 'card lift', 'data-reveal': '' },
    el('h3', {}, '🕛 근거 없는 종말시계'),
    body,
    el('div', { class: 'filters' },
      el('button', {
        class: 'btn primary sm', onclick: async () => {
          const d = await api('/api/fun/doomsday', { quiet: true }).catch(() => null);
          if (!d) return;
          body.innerHTML = '';
          body.append(
            el('div', { style: { fontSize: '30px', fontWeight: '800', fontFamily: 'var(--mono)', letterSpacing: '-.03em' } }, d.display),
            el('div', { class: 'filters', style: { margin: '10px 0' } },
              el('span', { class: 'chip bad' }, `부정 기사 ${d.negativeRatio}%`),
              el('span', { class: 'chip' }, `표본 ${d.sampled}건`)),
            el('p', { style: { fontSize: '13.5px' } }, d.verdict),
            el('p', { style: { fontSize: '11px', color: 'var(--text-faint)' } }, d.disclaimer));
        },
      }, '시계 확인')));
}

/* ---------------- 보스키 안내 ---------------- */

function cardBossKey() {
  return el('div', { class: 'card lift', 'data-reveal': '' },
    el('h3', {}, '🕴️ 사장님 오셨다 모드'),
    el('p', {}, '누르는 즉시 화면 전체가 지루한 분기 실적 스프레드시트로 바뀝니다. 탭 제목까지 바뀝니다. ' +
      '해제도 같은 키 또는 Esc.'),
    el('div', { class: 'filters', style: { marginTop: '12px' } },
      el('span', { class: 'chip gold' }, 'Ctrl + Alt + B'),
      el('button', {
        class: 'btn sm', onclick: () => {
          window.dispatchEvent(new KeyboardEvent('keydown', { key: 'b', code: 'KeyB', ctrlKey: true, altKey: true }));
        },
      }, '지금 시험해보기')),
    el('p', { style: { fontSize: '11px', color: 'var(--text-faint)', marginTop: '10px' } },
      '이 단축키는 이 사이트가 열려 있을 때만 동작합니다.'));
}

/* ---------------- 다 부수기 ---------------- */

function demolish(root) {
  const cards = $$('.card', root);
  if (!cards.length) return;
  const bodies = cards.map((node) => {
    const r = node.getBoundingClientRect();
    const clone = node.cloneNode(true);
    Object.assign(clone.style, {
      position: 'fixed', left: `${r.left}px`, top: `${r.top}px`,
      width: `${r.width}px`, height: `${r.height}px`, margin: '0', zIndex: '500',
      pointerEvents: 'none', transformOrigin: 'center',
    });
    document.body.append(clone);
    node.style.visibility = 'hidden';
    return {
      node: clone, x: r.left, y: r.top, w: r.width, h: r.height,
      vx: (Math.random() - 0.5) * 9, vy: -Math.random() * 9 - 2,
      rot: 0, vr: (Math.random() - 0.5) * 9,
    };
  });

  const floor = () => window.innerHeight;
  let frames = 0;
  const step = () => {
    frames++;
    for (const b of bodies) {
      b.vy += 0.62;
      b.x += b.vx;
      b.y += b.vy;
      b.rot += b.vr;
      if (b.y + b.h > floor()) { b.y = floor() - b.h; b.vy *= -0.42; b.vx *= 0.76; b.vr *= 0.7; }
      if (b.x < -b.w * 0.6) { b.x = -b.w * 0.6; b.vx *= -0.6; }
      if (b.x > window.innerWidth - b.w * 0.4) { b.x = window.innerWidth - b.w * 0.4; b.vx *= -0.6; }
      b.node.style.transform = `translate3d(${b.x - parseFloat(b.node.style.left)}px, ${b.y - parseFloat(b.node.style.top)}px, 0) rotate(${b.rot}deg)`;
    }
    if (frames < 760) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);

  toast('복구하려면 다른 탭에 갔다 오거나 새로고침하세요');
  const restore = el('button', {
    class: 'btn primary',
    style: { position: 'fixed', left: '50%', bottom: '90px', transform: 'translateX(-50%)', zIndex: '600' },
    onclick: () => { bodies.forEach((b) => b.node.remove()); cards.forEach((c) => (c.style.visibility = '')); restore.remove(); },
  }, '🧹 원상복구');
  document.body.append(restore);
}

/* ============================================================
   VIEW: games
   ============================================================ */

export async function viewGames(root) {
  root.append(el('section', { class: 'hero', style: { paddingBottom: '10px' } },
    el('div', { class: 'kicker' }, el('span', { class: 'dot' }), 'MINI GAMES · 실제 크롤링 데이터로 만듭니다'),
    el('h1', { style: { fontSize: 'clamp(28px,4.6vw,52px)' } }, '뉴스로 ', el('span', { class: 'grad' }, '노는 법')),
    el('p', { class: 'lede' }, '전부 방금 수집한 진짜 기사에서 문제를 만듭니다. 첫 번째 게임은 Claude가 가짜 헤드라인을 직접 씁니다.')));

  const grid = el('div', { class: 'grid g2' });
  root.append(el('section', { class: 'section' }, grid));
  grid.append(gameRealFake(), gameHeat());

  root.append(el('section', { class: 'section' },
    sectionHead('뉴스 인베이더', '헤드라인이 내려옵니다. 가짜뉴스만 쏘세요 — 진짜를 쏘면 감점'),
    gameInvaders()));

  observeReveals(root);
  bindSheen(root);
}

/* ---------------- 진짜 vs 가짜 ---------------- */

function gameRealFake() {
  const KEY = 'mujin.realfake';
  let score = JSON.parse(localStorage.getItem(KEY) || '{"win":0,"lose":0}');
  const scoreLine = el('div', { class: 'filters' });
  const stage = el('div', { style: { minHeight: '200px' } });

  const paintScore = () => {
    scoreLine.innerHTML = '';
    const total = score.win + score.lose;
    scoreLine.append(
      el('span', { class: 'chip good' }, `맞춤 ${score.win}`),
      el('span', { class: 'chip bad' }, `틀림 ${score.lose}`),
      el('span', { class: 'chip' }, total ? `정답률 ${Math.round((score.win / total) * 100)}%` : '아직 기록 없음'));
  };
  paintScore();

  const load = async () => {
    stage.innerHTML = '<div class="skel skel-line"></div><div class="skel skel-line"></div><div class="skel skel-line"></div>';
    const d = await api('/api/game/realfake').catch(() => null);
    stage.innerHTML = '';
    if (!d || d.error) { stage.append(el('p', {}, d?.error || '문제를 만들지 못했습니다')); return; }

    stage.append(el('p', { class: 'sub', style: { marginBottom: '10px' } },
      d.live ? 'Claude가 지금 가짜 헤드라인 하나를 지어냈습니다. 찾아보세요.' : '가짜 헤드라인 하나가 섞여 있습니다.'));

    d.options.forEach((o, i) => {
      const btn = el('button', {
        class: 'btn block', style: { justifyContent: 'flex-start', textAlign: 'left', marginBottom: '8px', lineHeight: '1.5' },
        onclick: () => {
          const correct = i === d.answer;
          if (correct) { score.win++; toast('정답! 가짜를 찾았습니다', 'good'); }
          else { score.lose++; toast('아쉽네요, 그건 진짜 기사입니다', 'bad'); }
          localStorage.setItem(KEY, JSON.stringify(score));
          paintScore();
          $$('button', stage).forEach((b, j) => {
            b.disabled = true;
            if (j === d.answer) b.style.borderColor = 'var(--good)';
            else if (j === i) b.style.borderColor = 'var(--bad)';
          });
          const reveal = d.reveal[d.answer];
          stage.append(el('div', { class: 'card', style: { marginTop: '10px', background: 'var(--panel-strong)' } },
            el('p', { style: { fontSize: '13px' } },
              el('b', {}, '정답: '), reveal.text,
              el('br'), el('span', { class: 'sub' }, `출처: ${reveal.source}`)),
            el('button', { class: 'btn sm primary', style: { marginTop: '10px' }, onclick: load }, '다음 문제 →')));
        },
      }, `${i + 1}. ${o.text}`);
      stage.append(btn);
    });
  };

  const card = el('div', { class: 'card lift', 'data-reveal': '' },
    el('h3', {}, '🕵️ 진짜 뉴스 vs AI 가짜 뉴스'),
    scoreLine, stage,
    el('div', { class: 'filters', style: { marginTop: '10px' } },
      el('button', { class: 'btn primary sm', onclick: load }, '문제 받기')));
  return card;
}

/* ---------------- 화제도 대결 ---------------- */

function gameHeat() {
  const KEY = 'mujin.heatgame';
  let best = Number(localStorage.getItem(KEY) || 0);
  let streak = 0;
  const info = el('div', { class: 'filters' });
  const stage = el('div', { style: { minHeight: '180px' } });

  const paint = () => {
    info.innerHTML = '';
    info.append(el('span', { class: 'chip accent' }, `연속 ${streak}`), el('span', { class: 'chip gold' }, `최고 ${best}`));
  };
  paint();

  const load = async () => {
    stage.innerHTML = '<div class="skel skel-card" style="height:80px"></div>';
    const d = await api('/api/game/heat', { quiet: true }).catch(() => null);
    stage.innerHTML = '';
    if (!d || d.error) { stage.append(el('p', {}, '데이터가 부족합니다')); return; }

    const mkSide = (side, item) => el('button', {
      class: 'btn block', style: { flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', padding: '14px', lineHeight: '1.5', minHeight: '92px' },
      onclick: () => {
        const ok = side === d.answer;
        if (ok) { streak++; best = Math.max(best, streak); localStorage.setItem(KEY, String(best)); toast('정답!', 'good'); }
        else { streak = 0; toast(`아깝습니다. 정답은 ${d.answer === 'left' ? '왼쪽' : '오른쪽'}`, 'bad'); }
        paint();
        $$('button', stage).forEach((b) => (b.disabled = true));
        stage.append(el('div', { class: 'filters', style: { marginTop: '10px' } },
          el('span', { class: 'chip' }, `왼쪽 🔥${Math.round(d.left.heat)}`),
          el('span', { class: 'chip' }, `오른쪽 🔥${Math.round(d.right.heat)}`),
          el('button', { class: 'btn sm primary', onclick: load }, '다음 →')));
      },
    },
      el('span', { class: 'chip', style: { marginBottom: '6px' } }, `${item.flag} ${item.source}`),
      el('span', {}, item.title));

    stage.append(el('div', { class: 'grid g2', style: { gap: '10px' } },
      mkSide('left', d.left), mkSide('right', d.right)));
  };

  return el('div', { class: 'card lift', 'data-reveal': '' },
    el('h3', {}, '🔥 어느 쪽이 더 뜨거운가'),
    el('p', { class: 'sub', style: { marginBottom: '10px' } }, '두 기사 중 화제도가 높은 쪽을 고르세요. 화제도는 최신성 × 매체 반복도로 계산됩니다.'),
    info, stage,
    el('div', { class: 'filters', style: { marginTop: '10px' } },
      el('button', { class: 'btn primary sm', onclick: load }, '시작')));
}

/* ---------------- 뉴스 인베이더 ---------------- */

function gameInvaders() {
  const canvas = el('canvas', { style: { width: '100%', height: '460px', display: 'block', borderRadius: 'var(--radius)', background: 'rgba(0,0,0,.28)' } });
  const hud = el('div', { class: 'filters' });
  const wrap = el('div', { class: 'card', style: { padding: '0', overflow: 'hidden' } }, canvas);
  const shell = el('div', {}, el('div', { class: 'filters', style: { marginBottom: '10px' } },
    el('button', { class: 'btn primary sm', onclick: () => start() }, '▶ 시작'),
    el('span', { class: 'sub' }, '← → 이동 · Space 발사 · 가짜(붉은색)만 쏘세요')), hud, wrap);

  let raf = null, running = false;
  const ctx = canvas.getContext('2d');
  let W = 0, H = 0;
  const resize = () => {
    const r = canvas.getBoundingClientRect();
    W = canvas.width = r.width * devicePixelRatio;
    H = canvas.height = r.height * devicePixelRatio;
    ctx.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0);
  };
  resize();
  window.addEventListener('resize', resize);

  const keys = new Set();
  window.addEventListener('keydown', (e) => {
    if (!running) return;
    if ([' ', 'ArrowLeft', 'ArrowRight'].includes(e.key)) e.preventDefault();
    keys.add(e.key);
  });
  window.addEventListener('keyup', (e) => keys.delete(e.key));

  async function start() {
    if (running) return;
    const feed = await api('/api/news?limit=30', { quiet: true }).catch(() => ({ items: [] }));
    const fakeSeed = ['AI가 지어낸 헤드라인', '출처 불명 속보', '검증되지 않은 주장', '익명 소식통 단독', '확인되지 않은 루머'];
    const w = () => W / devicePixelRatio, h = () => H / devicePixelRatio;

    const invaders = [];
    (feed.items || []).slice(0, 22).forEach((a, i) => {
      const fake = Math.random() < 0.34;
      invaders.push({
        text: fake ? fakeSeed[i % fakeSeed.length] : (a.title.length > 30 ? a.title.slice(0, 30) + '…' : a.title),
        fake,
        x: 40 + (i % 4) * (w() - 120) / 3.4,
        y: -Math.floor(i / 4) * 84 - 20,
        alive: true,
        speed: 0.24 + Math.random() * 0.18,
      });
    });

    const ship = { x: w() / 2, w: 46, h: 16 };
    const bullets = [];
    let score = 0, lives = 3, over = false;

    const paintHud = () => {
      hud.innerHTML = '';
      hud.append(el('span', { class: 'chip accent' }, `점수 ${score}`),
        el('span', { class: 'chip bad' }, `목숨 ${'♥'.repeat(Math.max(0, lives))}`),
        el('span', { class: 'chip' }, `남은 표적 ${invaders.filter((i) => i.alive).length}`));
    };
    paintHud();

    running = true;
    const loop = () => {
      ctx.clearRect(0, 0, w(), h());

      if (keys.has('ArrowLeft')) ship.x -= 6.6;
      if (keys.has('ArrowRight')) ship.x += 6.6;
      ship.x = Math.max(24, Math.min(w() - 24, ship.x));
      if (keys.has(' ') && (!bullets.length || bullets[bullets.length - 1].y < h() - 90)) {
        bullets.push({ x: ship.x, y: h() - 46 });
      }

      // bullets
      for (const b of bullets) b.y -= 9;
      for (let i = bullets.length - 1; i >= 0; i--) if (bullets[i].y < -10) bullets.splice(i, 1);

      // invaders
      for (const inv of invaders) {
        if (!inv.alive) continue;
        inv.y += inv.speed;
        if (inv.y > h() - 60) {
          inv.alive = false;
          if (inv.fake) { lives--; paintHud(); }
        }
        ctx.font = '600 12px Pretendard, sans-serif';
        const tw = ctx.measureText(inv.text).width + 18;
        ctx.fillStyle = inv.fake ? 'rgba(255,94,168,.20)' : 'rgba(110,231,255,.14)';
        ctx.strokeStyle = inv.fake ? '#ff5ea8' : '#6ee7ff';
        ctx.lineWidth = 1;
        roundRect(ctx, inv.x - tw / 2, inv.y - 12, tw, 26, 8);
        ctx.fill(); ctx.stroke();
        ctx.fillStyle = inv.fake ? '#ffd7ea' : '#cdf3ff';
        ctx.textAlign = 'center';
        ctx.fillText(inv.text, inv.x, inv.y + 5);

        for (let bi = bullets.length - 1; bi >= 0; bi--) {
          const b = bullets[bi];
          if (Math.abs(b.x - inv.x) < tw / 2 && Math.abs(b.y - inv.y) < 18) {
            inv.alive = false;
            bullets.splice(bi, 1);
            if (inv.fake) { score += 100; }
            else { score = Math.max(0, score - 60); lives--; }
            paintHud();
            break;
          }
        }
      }

      // bullets render
      ctx.fillStyle = '#ffcf5c';
      for (const b of bullets) ctx.fillRect(b.x - 1.5, b.y, 3, 12);

      // ship
      ctx.fillStyle = '#a78bfa';
      ctx.beginPath();
      ctx.moveTo(ship.x, h() - 52);
      ctx.lineTo(ship.x - 20, h() - 28);
      ctx.lineTo(ship.x + 20, h() - 28);
      ctx.closePath();
      ctx.fill();

      const left = invaders.filter((i) => i.alive).length;
      if (lives <= 0 || left === 0) {
        over = true;
        running = false;
        ctx.fillStyle = 'rgba(5,6,11,.82)';
        ctx.fillRect(0, 0, w(), h());
        ctx.fillStyle = '#e8ecf6';
        ctx.textAlign = 'center';
        ctx.font = '800 30px Pretendard, sans-serif';
        ctx.fillText(lives <= 0 ? 'GAME OVER' : 'CLEAR!', w() / 2, h() / 2 - 8);
        ctx.font = '400 15px Pretendard, sans-serif';
        ctx.fillText(`최종 점수 ${score}`, w() / 2, h() / 2 + 22);
        return;
      }
      raf = requestAnimationFrame(loop);
    };
    cancelAnimationFrame(raf);
    loop();
  }

  function roundRect(c, x, y, w, h, r) {
    c.beginPath();
    c.moveTo(x + r, y);
    c.arcTo(x + w, y, x + w, y + h, r);
    c.arcTo(x + w, y + h, x, y + h, r);
    c.arcTo(x, y + h, x, y, r);
    c.arcTo(x, y, x + w, y, r);
    c.closePath();
  }

  return shell;
}

/* ============================================================
   VIEW: labs — 독립 실행 가능한 HTML 페이지들
   ============================================================ */

const LABS = [
  { file: 'wall.html', icon: '🧱', name: '뉴스 월', desc: '전세계 헤드라인이 끝없이 흐르는 전체화면 월. 모니터 하나 띄워두기 좋습니다.' },
  { file: 'globe.html', icon: '🌐', name: '회전 지구본', desc: '캔버스로 그린 3D 와이어프레임 지구본 위에 권역별 뉴스 밀도를 띄웁니다.' },
  { file: 'radar.html', icon: '📡', name: '정세 레이더', desc: '대한민국 5축 정세를 레이더 차트로 실시간 표시하는 상황판.' },
  { file: 'matrix.html', icon: '🟩', name: '헤드라인 매트릭스', desc: '뉴스 키워드가 초록 비처럼 떨어지는 스크린세이버.' },
  { file: 'clock.html', icon: '🕐', name: '세계 시계', desc: '6개 권역 현재 시각 + 그 시각 그 지역의 최신 기사.' },
  { file: 'terminal.html', icon: '💻', name: '터미널 뉴스', desc: '80년대 터미널 스타일로 뉴스를 타이핑해서 출력합니다.' },
];

export async function viewLabs(root) {
  root.append(el('section', { class: 'hero', style: { paddingBottom: '10px' } },
    el('div', { class: 'kicker' }, el('span', { class: 'dot' }), 'STANDALONE PAGES · 동시 실행 가능'),
    el('h1', { style: { fontSize: 'clamp(28px,4.6vw,52px)' } }, '독립 ', el('span', { class: 'grad' }, '랩')),
    el('p', { class: 'lede' },
      '각각 완전히 독립된 HTML 페이지입니다. 새 탭·새 창으로 여러 개를 동시에 띄워도 서로 간섭하지 않고, ' +
      '모두 같은 API에서 실시간 데이터를 받습니다. 모니터 여러 대에 하나씩 띄워두는 용도.')));

  const grid = el('div', { class: 'grid g3' });
  root.append(el('section', { class: 'section' },
    sectionHead('랩 페이지', `${LABS.length}개`,
      el('button', {
        class: 'btn sm primary',
        onclick: () => {
          LABS.forEach((l, i) => setTimeout(() => window.open(`/labs/${l.file}`, '_blank', 'noopener'), i * 260));
          toast(`${LABS.length}개 페이지를 새 탭으로 동시에 엽니다`);
        },
      }, '⚡ 전부 동시에 열기')),
    grid));

  LABS.forEach((l, i) => {
    grid.append(el('div', { class: 'card lift', 'data-reveal': '', style: { '--d': `${i * 55}ms` } },
      el('div', { style: { fontSize: '34px', marginBottom: '10px' } }, l.icon),
      el('h3', {}, l.name),
      el('p', {}, l.desc),
      el('div', { class: 'filters', style: { marginTop: '14px' } },
        el('a', { class: 'btn sm primary', href: `/labs/${l.file}`, target: '_blank', rel: 'noopener' }, '새 탭에서 열기 ↗'),
        el('button', {
          class: 'btn sm ghost',
          onclick: () => {
            window.open(`/labs/${l.file}`, `lab-${l.file}`, 'width=1100,height=720,noopener');
          },
        }, '새 창'))));
  });

  root.append(el('section', { class: 'section' },
    sectionHead('한꺼번에 띄우기', '멀티 모니터 상황판 구성'),
    el('div', { class: 'card' },
      el('p', {}, '아래 버튼은 각 랩을 크기가 정해진 별도 창으로 엽니다. 창 위치는 브라우저 정책상 사용자가 배치해야 합니다.'),
      el('div', { class: 'filters', style: { marginTop: '12px' } },
        ...LABS.map((l) => el('button', {
          class: 'btn sm',
          onclick: () => window.open(`/labs/${l.file}`, `lab-${l.file}`, 'width=960,height=640,noopener'),
        }, `${l.icon} ${l.name}`))))));

  observeReveals(root);
  bindSheen(root);
}
