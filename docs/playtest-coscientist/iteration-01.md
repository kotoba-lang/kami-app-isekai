# Playtest Co-Scientist — iteration 01 (スライムハント / Slime Hunt)

> Driver: scripts/playtest/driver.mjs (real headless-Chromium playthrough, kotoba-lang/webgpu PR #9's `chromium.executablePath()` technique) · Scorer: kami-app-isekai.playtest.vision-score (3 candidate round(s) + 1 baseline round) · Judge: kotoba-lang/qa-governor (rubric + governor + append-only ledger) · following the ai-gftd-animeka coscientist pattern (generate -> score -> keep-winner) and isekai.ux.coscientist's propose/evaluate/iterate CLI shape.

Generated 2026-07-09T06:21:55.600461Z by `scripts/playtest_coscientist.clj`.

Vision scoring path: **offline pixel heuristic** (ANTHROPIC_API_KEY unset — see `kami-app-isekai.playtest.vision-score/heuristic-score`)

**Note on this specific run's repo state**: this is a real, unedited run of the tool (see
Verdict below) — the tool's own logic genuinely left `scene.edn` set to `gen-3`'s params
after this run. Since ANTHROPIC_API_KEY wasn't available in this environment, every score
in this iteration came from the offline pixel heuristic (a crude stand-in, see
`vision-score/heuristic-score`'s own docstring for exactly what it does and doesn't measure)
rather than a real vision-LLM's judgment of actual game feel — and the margin here (+0.7) is
well within that heuristic's noise. Rather than land unvetted, heuristic-only-scored juice
tuning in the same PR that introduces the tool, `scene.edn` was manually reverted to its
original baseline after this verification run so a human reviews the actual visual diff
(or re-runs with a live ANTHROPIC_API_KEY) before adopting a candidate. The table/params
below are exactly what the tool produced; only the final on-disk `scene.edn` was reverted
post-hoc for this PR.

## ★ Measured baseline

- total=64.8 grade=c verdict=approved
- scores: juice=59.8, feel=53.0, bugs=80.6, clarity=64.6
- driver: victory phase=victory, lose phase=gameover

## Roadmap (rounds tried)

| round | total | grade | verdict | scores | victory | lose |
|---|---|---|---|---|---|---|
| gen-1 | 64.9 | c | approved | juice=60.0, feel=53.2, bugs=80.6, clarity=64.4 | victory | gameover |
| gen-2 | 65.1 | c | approved | juice=60.4, feel=53.2, bugs=80.6, clarity=64.8 | victory | gameover |
| gen-3 | 65.6 | c | approved | juice=61.8, feel=53.2, bugs=80.8, clarity=64.8 | victory | gameover |

## Params tried per round

### gen-1

```
fx shake amp: 14 -> 13
fx shake frames: 12 -> 7
fx burst pick n: 10 -> 3
fx burst pick spd: 2.0 -> 3.298067156551542
fx burst pick grav: 0.1 -> 0.19247191426238086
fx burst pick life: 22 -> 16
fx burst hit n: 14 -> 11
fx burst hit spd: 2.6 -> 1.8266978394313473
fx burst hit grav: 0.16 -> 0.2447600464226409
fx burst hit life: 24 -> 27
fx burst hit size: 6 -> 9
audio pick freq: 660 -> 696
audio pick dur: 0.11 -> 0.16492106521676742
audio pick gain: 0.15 -> 0.2295139343500466
audio hit freq: 200 -> 80
audio hit dur: 0.17 -> 0.3786295525609026
audio hit gain: 0.22 -> 0.32251438665475146
audio victory freq: 523 -> 500
audio victory dur: 0.5 -> 0.5714893777913935
audio victory gain: 0.18 -> 0.13363332268497277
audio gameover freq: 220 -> 652
audio gameover dur: 0.55 -> 0.5873148086809798
audio gameover gain: 0.16 -> 0.08035443844834168
```

### gen-2

```
fx shake amp: 14 -> 13
fx shake frames: 12 -> 16
fx burst pick n: 10 -> 3
fx burst pick spd: 2.0 -> 1.3714871979553047
fx burst pick grav: 0.1 -> 0.19407902964915558
fx burst pick life: 22 -> 19
fx burst hit n: 14 -> 8
fx burst hit spd: 2.6 -> 3.321670662954869
fx burst hit grav: 0.16 -> 0.23150488554417512
fx burst hit life: 24 -> 13
fx burst hit size: 6 -> 7
audio pick freq: 660 -> 725
audio pick dur: 0.11 -> 0.08353547373994857
audio pick gain: 0.15 -> 0.1405572915441793
audio hit freq: 200 -> 489
audio hit dur: 0.17 -> 0.05
audio hit gain: 0.22 -> 0.18650748100134384
audio victory freq: 523 -> 80
audio victory dur: 0.5 -> 0.35760064770421185
audio victory gain: 0.18 -> 0.1272699002605549
audio gameover freq: 220 -> 585
audio gameover dur: 0.55 -> 0.5398078103924229
audio gameover gain: 0.16 -> 0.17196067833206125
```

### gen-3

```
fx shake amp: 14 -> 18
fx shake frames: 12 -> 11
fx burst pick n: 10 -> 13
fx burst pick spd: 2.0 -> 2.0745835834665782
fx burst pick grav: 0.1 -> 0.004543352246421592
fx burst pick life: 22 -> 19
fx burst pick size: 5 -> 3
fx burst hit n: 14 -> 5
fx burst hit spd: 2.6 -> 3.258995704171393
fx burst hit grav: 0.16 -> 0.14312192846167895
fx burst hit life: 24 -> 19
fx burst hit size: 6 -> 7
audio pick freq: 660 -> 901
audio pick dur: 0.11 -> 0.05
audio pick gain: 0.15 -> 0.06688470228828493
audio hit freq: 200 -> 301
audio hit dur: 0.17 -> 0.05
audio hit gain: 0.22 -> 0.3165332903891797
audio victory freq: 523 -> 567
audio victory dur: 0.5 -> 0.28827819063399296
audio victory gain: 0.18 -> 0.07345530394485907
audio gameover freq: 220 -> 80
audio gameover dur: 0.55 -> 0.36743827878899554
audio gameover gain: 0.16 -> 0.20805980017937192
```

## ✅ Verdict — BUILD gen-3

**gen-3** beat the baseline (64.8 -> 65.6, +0.7) and was governor-approved — `scene.edn` was left set to this candidate's params.

## Screenshots (not published — local-only PNGs, see --out-dir)

- baseline: title, pickup, victory, hit, gameover
- gen-1: title, pickup, victory, hit, gameover
- gen-2: title, pickup, victory, hit, gameover
- gen-3: title, pickup, victory, hit, gameover

## → Seed for next iteration

This pass only tunes :fx/:audio params (kami-app-isekai.playtest.tuning/tunable-paths) — logic.cljc gameplay speeds/ranges/layout are explicitly out of scope (documented follow-up, not built now). A follow-up iteration could: (1) extend tunable-paths to :render/sky or particle :colors as a small categorical choice set (deliberately excluded from this pass's numeric random-walk, see tuning.cljc), (2) raise --rounds once round latency is validated in CI, (3) wire the live Claude vision path into a scheduled run once ANTHROPIC_API_KEY is available in that environment.
