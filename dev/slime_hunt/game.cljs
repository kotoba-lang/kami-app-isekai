(ns slime-hunt.game
  "スライムハント (Slime Hunt) — the playable harness.

   Same architecture gftdcojp/network-isekai's `isekai.game` already runs in production
   (verified live: `drive`/KAMI Drive uses this exact shape): `public/games/slime-hunt/
   logic.cljc` is compiled to real WASM bytes via `kotoba.engine-clj` (ast → codegen →
   wasm-bytes — a `.cljc` port of the same compiler `isekai.game` uses; this repo's PR
   description documents the standalone JVM+Node smoke test that proved the compiled bytes
   actually instantiate and run correctly against `kami.host`'s ABI before this harness was
   written), instantiated against `kami.host` (kotoba-lang/webgpu — the browser ECS/ABI host),
   ticked every `requestAnimationFrame`, and rendered via `kami.scene2d` + `kami.webgl`
   (GPU-instanced 2D quads, WebGL2 — the same `kami.sprite-gpu` pipeline this repo's own
   render-test already pixel-verified for `terrain-height`, not Canvas2D).

   Trimmed from `isekai.game.cljc`'s ~700 lines to this game's own needs: no netsync/level/
   itonami-bridge/leaderboard/audio (all opt-in bits network-isekai uses for OTHER games), no
   FSM-driven player animation (a straightforward simplification, not a requirement — the win/
   lose GAMEPLAY is unaffected). What IS kept, because the task requires it: the exact generic
   win/lose convention — game-over fires when the \"player\"-tagged entity count hits 0,
   victory fires when the \"picked\"-tagged count reaches `scene :flow :victory :when-picked`
   (see `victory-picked` / `game-over?`/`victory?` below) — read from scene.edn data, not
   hardcoded here, exactly like drive."
  (:require [cljs.reader :as edn]
            [kami.host :as host]
            [kami.scene2d :as scene2d]
            [kami.webgl :as wgl]
            [kami.ui :as ui]
            [kami.input :as input]
            [kotoba.engine-clj.ast :as kast]
            [kotoba.engine-clj.codegen :as kcodegen]
            [kotoba.engine-clj.wasm-bytes :as kwasm]))

;; ---- compile logic.cljc source -> real wasm bytes -------------------------------------------
;; The exact workaround `isekai.game.cljc`'s `cljc-compile-game` documents: `kotoba.engine-clj/
;; compile-str` is JVM-only (its reader needs `java.io.PushbackReader`), so this goes straight to
;; the portable `kast/parse-program` (already-read forms, no JVM reader) fed by `cljs.reader`
;; (wrapping the source in `[...]` so one `read-string` call reads every top-level form as a
;; vector), then the portable `codegen/compile` + `wasm-bytes/emit-module-bytes`.
(defn- compile-game [logic-src]
  (-> (str "[" logic-src "]") edn/read-string kast/parse-program kcodegen/compile kwasm/emit-module-bytes))

(defn- fetch-text [url] (-> (js/fetch url) (.then (fn [r] (.text r)))))

;; ---- terrain background: bake scene :terrain :tiles into static GPU quads, once at boot -----
;; (kami-app-isekai.voxel-world/terrain-height + .heightmap-color already ran at scene-authoring
;; time — see scripts/gen_scene.clj; this just turns the baked {:x :y :color} tiles into the
;; {:pos :size :rot :shape :color} quad shape kami.sprite-gpu/kami.scene2d expects. shape 1 = rect.)
(defn- terrain-quads [scene]
  (let [{:keys [tile-size tiles]} (:terrain scene)
        half (/ tile-size 2.0)]
    (mapv (fn [{:keys [x y color]}]
            {:pos [x y] :size [half half] :rot 0.0 :shape 1 :color (conj (vec color) 1.0)})
          tiles)))

;; ---- HUD: EDN → kami.ui panel, from the live snapshot + scene :hud data ----------------------
(defn- hud-spec [scene snap]
  (let [hud (:hud scene)
        ctag (fn [t] (count (filter #(= (:tag %) t) snap)))
        picked (ctag "picked")
        lives  (ctag "life")
        lspec  (get hud :lives {:max 3 :full "❤️" :empty "🖤"})
        maxl   (:max lspec 3)
        hearts (apply str (concat (repeat lives (:full lspec "❤️")) (repeat (max 0 (- maxl lives)) (:empty lspec "🖤"))))]
    [[:panel {:at :top-left}
      [:text {:value (:title hud "")}]
      [:text {:value (str (get-in hud [:stats 0 :icon] "💧") " " picked
                           " / " (get-in scene [:flow :victory :when-picked] 0))}]
      [:text {:value hearts}]
      [:text {:value (:hint hud "")}]]]))

;; ---- flow overlay: title / game-over / victory cards + a click/SPACE to advance --------------
;; A trimmed version of isekai.game.cljc's mount-flow! — no wipe animation, no BGM, no
;; leaderboard fetch (none of those are opt-in bits this game uses) — just the 3 cards the win/
;; lose convention needs, with data-testid hooks a Playwright test can read deterministically
;; (the harness-generic contract: `phase` transitions :title -> :play -> :gameover|:victory).
(defn- mk-el [tag styles]
  (let [e (.createElement js/document tag)]
    (doseq [[k v] styles] (aset (.-style e) (name k) v))
    e))

(defn- mount-flow! [canvas scene]
  (let [flow (:flow scene)
        parent (.-parentElement canvas)
        overlay (mk-el "div" {:position "absolute" :inset "0" :display "flex" :z-index "20"
                              :align-items "center" :justify-content "center" :text-align "center"
                              :font-family "system-ui, sans-serif" :color "#fff"
                              :background "rgba(4,8,18,.68)" :cursor "pointer" :user-select "none"})
        logo (mk-el "div" {:font-size "40px" :font-weight "800"})
        sub  (mk-el "div" {:font-size "16px" :opacity ".85" :margin-top "8px"})
        prompt (mk-el "div" {:font-size "14px" :opacity ".9" :margin-top "20px"})
        phase (atom :title)]
    (.setAttribute overlay "data-testid" "flow-overlay")
    (.setAttribute overlay "data-phase" "title")
    (doto overlay (.appendChild logo) (.appendChild sub) (.appendChild prompt))
    (set! (.. parent -style -position) "relative")
    (.appendChild parent overlay)
    (letfn [(paint! [k]
              (let [s (get flow k)]
                (set! (.-textContent logo) (or (:logo s) ""))
                (set! (.-textContent sub) (or (:sub s) ""))
                (set! (.-textContent prompt) (or (:prompt s) ""))))
            (show! [v?] (set! (.. overlay -style -display) (if v? "flex" "none")))
            (set-phase! [p] (reset! phase p) (.setAttribute overlay "data-phase" (name p))
                        (set! (.-__slimeHuntPhase js/window) (name p)))]
      (set-phase! :title) (paint! :title) (show! true)
      (.addEventListener overlay "click"
        (fn [_] (case @phase
                  :title (do (set-phase! :play) (show! false))
                  (:gameover :victory) (.reload js/location)
                  nil)))
      (.addEventListener js/window "keydown"
        (fn [e] (when (and (= " " (.-key e)) (not= @phase :play))
                  (.preventDefault e)
                  (case @phase
                    :title (do (set-phase! :play) (show! false))
                    (:gameover :victory) (.reload js/location)
                    nil))))
      {:phase phase
       :game-over! (fn [] (when (= @phase :play) (set-phase! :gameover) (paint! :gameover) (show! true)))
       :victory!   (fn [] (when (= @phase :play) (set-phase! :victory) (paint! :victory) (show! true)))})))

;; ---- boot + live loop --------------------------------------------------------------------
(defn- run-loop! [gl render! overlay flow scene tstate running]
  (let [fr (atom 0)]
    (letfn [(loop! []
              (when @running
                (host/tick! tstate 16)
                (let [snap (host/snapshot (:state tstate))
                      ctag (fn [t] (count (filter #(= (:tag %) t) snap)))
                      w (.-width (.-canvas gl)) h (.-height (.-canvas gl))
                      ;; background terrain quads drawn first (under), scene2d's entity/fx quads
                      ;; appended after (over) — draw order = array order in kami.webgl.
                      frame (update (scene2d/frame-quads scene snap [] @fr w h)
                                    :quads #(into (:bg-quads scene) %))]
                  (render! frame [w h])
                  (when (zero? (mod (swap! fr inc) 4))
                    (ui/render! overlay (hud-spec scene snap)))
                  (when (= @(:phase flow) :play)
                    (cond
                      (zero? (ctag "player")) ((:game-over! flow))
                      (>= (ctag "picked") (get-in scene [:flow :victory :when-picked] 999999)) ((:victory! flow)))))
                (js/requestAnimationFrame loop!)))]
      (loop!))))

(defn- boot! [canvas overlay flow scene logic-src]
  (let [dpr (or (.-devicePixelRatio js/window) 1)
        _ (set! (.-width canvas) (js/Math.max 1 (js/Math.round (* (.-clientWidth canvas) dpr))))
        _ (set! (.-height canvas) (js/Math.max 1 (js/Math.round (* (.-clientHeight canvas) dpr))))
        gl (wgl/webgl2-context canvas)
        render! (wgl/scene-renderer gl)
        scene (assoc scene :bg-quads (terrain-quads scene))
        st (host/new-state)
        running (atom true)]
    (input/wire! (:input scene)
                 {:on-axes (fn [_] nil)
                  :on-action (fn [a down?] (swap! st update :keys (if down? conj disj) (name a)))})
    (set! (.-__slimeHuntSnapshot js/window) (fn [] (clj->js (host/snapshot st))))
    (-> (js/Promise.resolve (compile-game logic-src))
        (.then (fn [bytes]
                 (set! (.-__slimeHuntStatus js/window) (str "compiled -> " (.-length bytes) " bytes"))
                 (host/instantiate! st bytes)))
        (.then (fn [h] (run-loop! gl render! overlay flow scene h running)))
        (.catch (fn [e]
                  (set! (.-__slimeHuntStatus js/window) (str "error: " e))
                  (js/console.error "slime-hunt boot failed" e))))
    {:running running}))

(defn ^:export main []
  (let [canvas (js/document.getElementById "game-canvas")
        status (js/document.getElementById "status")]
    (-> (js/Promise.all #js [(fetch-text "public/games/slime-hunt/scene.edn") (fetch-text "public/games/slime-hunt/logic.cljc")])
        (.then (fn [texts]
                 (let [scene-src (aget texts 0) logic-src (aget texts 1)
                       scene (edn/read-string scene-src)
                       overlay (ui/mount! canvas)
                       flow (mount-flow! canvas scene)]
                   (set! (.-textContent status) (str (:game/title scene) " — loading…"))
                   (boot! canvas overlay flow scene logic-src)
                   (set! (.-textContent status) (:game/title scene)))))
        (.catch (fn [e]
                  (set! (.-textContent status) (str "load failed: " e))
                  (js/console.error "slime-hunt fetch/boot failed" e))))))

(main)
