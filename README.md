# kotoba-lang/kami-app-isekai

Zero-dep portable `.cljc` — restored from the legacy `kami-engine/kami-app-isekai`
Rust crate (2107 lines across `lib.rs`/`omniverse.rs`/`pipelines.rs`, deleted in
kotoba-lang/kami-engine PR #82 "Remove Rust workspace from kami-engine") as part of
the **clj-wgsl migration** (ADR-2607010930, `com-junkawasaki/root`).

## What this is

The original crate was primarily a `wasm-bindgen` entrypoint (`run_isekai_v2`/
`run_isekai_v2_scene`) orchestrating `KamiApp` builder calls, a per-frame `on_update`
tick closure driving DEC field simulation (`kami_dec::ScalarField`/`EdgeField`/
`FaceField` — heat/moisture/wind/EM fields tightly coupled to native `Rc<RefCell<...>>`
state and `kami_pipelines` GPU adapters), voxel-chunk streaming, and an Omniverse/
PhysX/USD facade — native WASM/wgpu substrate with no meaningful portable
representation as a whole program.

Rather than attempt a 1:1 tick-loop port (which would carry no computational value
without the native simulation state it operates on), this restoration extracts the
genuinely **portable computational kernels and configuration data**:

| Namespace | From | Purpose |
|---|---|---|
| `kami-app-isekai.voxel-world` | `lib.rs` | 2-octave value noise, voxel palette, terrain-height generation rule, pre-authored demo-house voxel layout |
| `kami-app-isekai.pipelines` | `pipelines.rs` | breathing-gradient clear-color function (pure time -> RGB) |
| `kami-app-isekai.omniverse` | `omniverse.rs` | default USDA scene-description text constant |

**Excluded entirely** (native-only, no portable logic): the wasm-bindgen entrypoints,
the per-frame DEC tick closure and its native `Rc<RefCell>` field state, voxel-chunk
GPU streaming/upload, and the Omniverse/USD/PhysX parse-and-drive orchestration.

## Status

Restored (scoped) — 8 tests / 29 assertions, 0 failures (the original had no
`#[test]`s in any of the 3 files; these provide coverage of the ported kernels/data).

## Develop

```bash
clojure -M:test
```
