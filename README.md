# YAMUJIN · 글로벌 인텔리전스 허브

전세계 뉴스를 실시간으로 크롤링해서 Claude가 하나로 종합해주는 개인용 데스크.
대한민국 정세 지표, 최저가 헌터, 지식 그래프, JWT 회원 시스템, 독립 실행 랩 페이지까지 한 서버에 들어 있습니다.

```
Spring Boot 4.1 · Java 26 · H2(파일) · JWT · Anthropic Java SDK · 순수 ES 모듈 프론트
```

---

## 실행

```bash
./gradlew build
java -jar build/libs/the_greatest_project-0.0.1-SNAPSHOT.jar
```

→ http://localhost:8080

API 키가 하나도 없어도 **모든 화면이 동작합니다.** AI가 필요한 자리는 로컬 추출 엔진이 대신 답하고,
화면에는 `◇ 로컬 엔진` 배지가 붙습니다.

### 프론트를 고칠 때 (재빌드 없이 즉시 반영)

```bash
java -jar build/libs/the_greatest_project-0.0.1-SNAPSHOT.jar \
  --spring.web.resources.static-locations=file:src/main/resources/static/
```

---

## Claude 자격증명 — API 키 없이 쓰기

Claude를 붙이는 경로는 네 단계이고, **위에서부터 되는 걸 자동으로 씁니다.**

| 순위 | 경로 | 필요한 것 | 과금 |
|---|---|---|---|
| 1 | Anthropic SDK | `ANTHROPIC_API_KEY` 환경변수 | API 조직 크레딧 |
| 2 | Anthropic SDK | `ant auth login` OAuth 프로필 | API 조직 크레딧 |
| 3 | **Claude Code CLI** | 이 PC에 `claude`가 설치·로그인돼 있으면 끝 | **구독 한도** |
| 4 | 로컬 추출 엔진 | 없음 (네트워크도 불필요) | 무료 |

3번이 기본 동작입니다. **키를 어디에도 붙여넣을 필요가 없습니다.** 이미 로그인된
Claude Code를 헤드리스(`claude -p`)로 호출합니다. `~/.claude/.credentials.json` 을
읽어가는 게 아니라 CLI를 서브프로세스로 띄우는 방식이라, 토큰은 그것을 소유한
도구 안에 그대로 남습니다.

CLI 호출은 순수 완성 엔드포인트처럼 동작하도록 잠가 두었습니다 — `--safe-mode`
(CLAUDE.md·스킬·플러그인·훅 무시), `--strict-mcp-config`(MCP 미접속),
`--max-turns 1`(도구 루프 없음), 작업 디렉터리는 프로젝트가 아닌 임시 폴더.
프롬프트는 argv가 아니라 stdin으로 넘깁니다 — 윈도우 명령줄 길이 제한(약 32k)에
하루치 뉴스 다이제스트가 그대로 걸리기 때문입니다.

지금 어떤 경로로 붙어 있는지는 `GET /api/meta` 의 `claude.source` 가 알려주고,
로그인 상태를 바꾼 뒤에는 재시작 없이 `POST /api/meta/recheck` 로 갱신됩니다.

| 변수 | 기본값 | 용도 |
|---|---|---|
| `ANTHROPIC_API_KEY` | 없음 | 있으면 1순위로 사용 |
| `CLAUDE_MODEL` | `claude-sonnet-5` | 원하는 모델로 교체 |
| `YAMUJIN_CLAUDE_USE_CLI` | `true` | `false` 면 CLI 경로를 끄고 로컬 엔진으로 내려감 |
| `CLAUDE_CLI_PATH` | 자동 탐색 | PATH·`~/.local/bin` 밖에 있을 때만 지정 |
| `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET` | 없음 | 네이버 쇼핑 실가격 비교 + Claude 구매 판정 |
| `JWT_SECRET` | 내장 개발용 키 | 운영용 서명 키 |

```powershell
# 키 없이: claude 로그인만 돼 있으면 그대로 실행
java -jar build\libs\the_greatest_project-0.0.1-SNAPSHOT.jar

# API 키를 쓰고 싶을 때만
$env:ANTHROPIC_API_KEY = "sk-ant-..."
java -jar build\libs\the_greatest_project-0.0.1-SNAPSHOT.jar
```

> 쇼핑 키가 없을 때 **가짜 가격을 지어내지 않습니다.** 실가격 대신 몰별 딥링크와
> 카테고리별 "돈 새는 지점" 체크리스트만 돌려줍니다.

---

## 화면

| 경로 | 내용 |
|---|---|
| `#/` | 대시보드 — 권역 밀도 지도, 오늘의 글로벌 브리핑(SSE 스트리밍), 전 지구 동시 이슈, 트렌드 키워드 |
| `#/news` | 뉴스 피드 — 6개 권역 × 카테고리 필터 + 전문 검색 |
| `#/prism` | **프리즘** — 같은 키워드를 6개국 언론이 어떻게 다르게 프레이밍하는지 비교 + 타임라인 |
| `#/korea` | 대한민국 정세 — KOREA PULSE 지수, 5축 진단, 트랙별 기사, Claude 국면 분석 |
| `#/market` | **마켓 + TRUMP WATCH** — 10개 지표 시세 보드(스파크라인), 크로스헤어 인트라데이 차트, 트럼프 Truth Social 원문 + Claude 요약 |
| `#/shop` | 최저가 헌터 — 판매처별 가격표, Claude 구매 판정, 뉴스에서 역산한 소비 추천 |
| `#/graph` | 지식 그래프 — 키워드 동시출현 force-directed 네트워크 (드래그/클릭) |
| `#/ai` | AI 데스크 — 권역 심층 다이제스트, 오디오 브리핑(TTS), 자유 질의 스트리밍 |
| `#/labs` | 독립 랩 페이지 6종 (아래) |
| `#/fun` | 재미 — 전부 수동 실행. 메인 흐름에 절대 안 끼어듭니다 |
| `#/games` | 미니게임 3종 |
| `#/me` | 마이페이지 — 스크랩북, 관심 키워드 브리핑, AI 질의 기록 |

### 단축키

| 키 | 동작 |
|---|---|
| `Ctrl + K` | 명령 팔레트 (이동 + 뉴스/프리즘/최저가 검색) |
| `Alt + A` | 야무진 비서 (AI 도크) |
| `Ctrl + Alt + B` | 사장님 모드 — 화면 전체가 분기 실적 스프레드시트로 위장 (탭 제목까지) |
| `Esc` | 팔레트·모달·위장모드 해제 |

---

## 독립 랩 페이지 — 동시 실행용

`/labs/index.html` 또는 `#/labs` → **⚡ 전부 동시에 열기**

각각 완전히 독립된 HTML 파일입니다. 여러 개를 동시에 띄워도 서로 간섭하지 않고,
모두 같은 API에서 실시간 데이터를 받습니다. 모니터마다 하나씩 띄워두는 용도.

| 파일 | 내용 |
|---|---|
| `wall.html` | 뉴스 월 — 컬럼마다 속도·방향이 다른 무한 스크롤 전체화면 월 |
| `globe.html` | 회전 지구본 — 와이어프레임 globe 위 권역별 밀도 (드래그 회전, 휠 확대) |
| `radar.html` | 정세 레이더 — 5축 레이더 차트 + 스윕 애니메이션 상황판 |
| `matrix.html` | 헤드라인 매트릭스 — 수집된 키워드가 초록 비로 떨어지는 스크린세이버 |
| `clock.html` | 세계 시계 — 6개 권역 시각 + 그 지역 최신 기사 |
| `terminal.html` | 터미널 뉴스 — CRT 감성 타이핑 출력 |

---

## 마켓 & TRUMP WATCH

**시세** — Yahoo Finance 공개 chart 엔드포인트 (키 불필요, 60초 캐시).
나스닥 · S&P 500 · 다우 · 코스피 · 코스닥 · 원/달러 · 비트코인 · WTI · 금 · VIX.
가져오지 못한 심볼은 조용히 빠집니다 — 값을 추정하거나 직전 값을 재사용하지 않습니다.

**트럼프** — X(트위터)는 API 유료화 + 스크래핑 차단이라 키 없이는 접근할 방법이 없습니다.
대신 그가 실제로 글을 쓰는 플랫폼인 **Truth Social**을 공개 아카이브(`trumpstruth.org/feed`)로
직접 수집합니다. 원문 링크는 truthsocial.com 본문으로 연결됩니다.
게시물마다 대문자 비율(`shouty`)을 계산해서 지금 소리지르는 중인지 정량화하고,
Claude가 한국어 요약 + 시장에서 볼 지점 + 한국 관련 언급을 뽑습니다.

차트 규칙: 단일 시계열 · 단일 축(이중축 금지) · 등락은 색 단독이 아니라 **▲/▼ + 부호**를 항상 동반 ·
인트라데이 차트에는 전일 종가 기준선과 크로스헤어 툴팁.

> 지연 시세일 수 있습니다. 투자 판단의 근거로 쓰지 마세요.

---

## 데이터 파이프라인

```
34개 RSS 피드 (6개 권역)
  → 가상 스레드로 병렬 수집 (죽은 피드는 페이지를 못 막음)
  → jsoup XML/Atom 파싱 → Article 정규화
  → 제목 정규화 해시로 중복 제거 (모든 매체가 받아쓰는 기사 정리)
  → TF 키워드 추출 + 어휘 감성 점수
  → heat = 최신성 감쇠 × 매체 반복도 × 감성 강도
  → 8분 캐시 + 7분마다 백그라운드 예열
```

**heat**(화제도)와 **KOREA PULSE**는 여론조사가 아니라 보도량·어휘 기반 파생 지표입니다.
UI에도 그렇게 표시해 두었습니다.

### 주요 API

```
GET  /api/news?region=KR&limit=60      GET  /api/news/search?q=
GET  /api/news/pulse                   GET  /api/news/trends
GET  /api/news/global-issues           GET  /api/news/graph
GET  /api/ai/briefing                  GET  /api/ai/briefing/stream   (SSE)
GET  /api/ai/prism?q=                  GET  /api/ai/chat/stream?q=    (SSE)
GET  /api/korea/pulse                  GET  /api/shop/search?q=
GET  /api/market                       GET  /api/market/{code}?range=1d&interval=5m
GET  /api/trump?limit=20               GET  /api/trump/digest
POST /api/auth/register | login        GET  /api/auth/me
GET  /api/meta                         POST /api/news/refresh
```

---

## 인증

BCrypt(10) 해싱 + HS256 JWT(기본 12시간). `JwtAuthFilter`가 `/api/*`에서 Bearer 토큰이나
`yamujin_token` 쿠키를 읽어 요청에 신원을 심고, 보호된 엔드포인트는 `Auth.require()`로 401을 냅니다.
계정과 스크랩은 `./data/yamujin.mv.db` (이 컴퓨터의 H2 파일)에만 저장됩니다.

---

## 재미 파트 규칙

`#/fun`, `#/games` 안에서 **명시적으로 눌러야만** 동작합니다.
랜덤 팝업, 자동 효과음, 예고 없는 화면 변형은 없습니다. 사장님 모드도 단축키를 직접 눌러야 켜집니다.

- 🎴 뉴스 가챠 — 등급은 그 기사의 실제 heat로 결정
- 🔮 오늘의 뉴스 운세 — 같은 사람·같은 날짜면 항상 같은 결과 (새로고침 어뷰징 차단)
- 📜 조선왕조실록 번역기 — 오늘 헤드라인을 사관 문체로. "사신은 논한다"
- 🎭 문체 변환기 — 조폭 / 할머니 / 아나운서 / 중2병 / MZ / 면접관
- 💸 실시간 월급 적립기 — 연봉·근무시간 기준 초당 적립액
- 🙇 변명 생성기 — 정공법 / 무난 / 절대 보내면 안 되는 버전
- 🕛 근거 없는 종말시계 — 부정 기사 비율로 계산 (근거 없음을 응답에 명시)
- 💥 다 부수기 — 페이지 카드가 물리엔진으로 무너짐 (원상복구 버튼 제공)

**미니게임**
- 🕵️ 진짜 뉴스 vs AI 가짜 뉴스 — 진짜 헤드라인 3개 + Claude가 방금 지어낸 가짜 1개
- 🔥 화제도 대결 — 두 기사 중 더 뜨거운 쪽 맞히기
- 👾 뉴스 인베이더 — 내려오는 헤드라인 중 가짜만 격추 (진짜 쏘면 감점)
