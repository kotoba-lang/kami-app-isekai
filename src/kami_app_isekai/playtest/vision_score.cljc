(ns kami-app-isekai.playtest.vision-score
  "Vision-LLM quality scoring for スライムハント (Slime Hunt) playtest screenshots — the
  Playtest Co-Scientist's evaluate step. Wraps `langchain.jvm/vision-json`
  (kotoba-lang/langchain), following the exact proven call pattern
  `ai-gftd-project-mangaka`'s `mangaka.graphs.vision-score`/`mangaka.llm` already run in
  production (`(llm/vision-json prompt img)` -> a structured map, `parse-structured` ->
  nil on an unparseable reply): build a multimodal prompt+image message
  (`langchain.jvm/vision-content`, used internally by `vision-json`), send it through an
  injected `ChatModel` (`langchain.model/anthropic-model`), parse the JSON reply.

  4 axes (matching this session's own quality dimensions from repeatedly play-testing this
  game): :juice (見た目・演出), :feel (遊びやすさ・バランス), :bugs (bug-FREE-ness — 100 =
  nothing looks broken/glitched/stuck), :clarity (does the screenshot legibly communicate
  what just happened — pickup/hit/victory/game-over feedback).

  Credential convention (same as `isekai.ux.coscientist`/`mangaka.llm`): `ANTHROPIC_API_KEY`
  env var, nil -> graceful degrade. Unlike the generic `langchain.jvm/chat-model` mock (which
  just echoes text — useless here, `vision-json`'s `parse-structured` would return nil on an
  echoed reply, i.e. no score at all), the offline path here is a REAL pixel heuristic over
  the actual screenshot bytes (`heuristic-score` below) — every field varies with the real
  image, never a fake constant. No new credential-fetching machinery: env var only."
  (:require [langchain.jvm :as jvm]
            [langchain.model :as model]
            #?(:clj [clojure.java.io :as io])))

;; ───────────────────────── model selection / credentials ─────────────────────────

(def model-id
  "Vision-capable Claude model id. Override with PLAYTEST_VISION_MODEL (same env-var-first,
  no-hardcoded-model convention as mangaka.llm's MANGAKA_CRITIQUE_MODEL)."
  #?(:clj (or (System/getenv "PLAYTEST_VISION_MODEL") model/default-model)
     :cljs model/default-model))

#?(:clj
   (defn- api-key [] (System/getenv "ANTHROPIC_API_KEY")))

#?(:clj
   (defn live-model
     "The live Anthropic ChatModel when ANTHROPIC_API_KEY is set, else nil (score-screenshot
     then takes the pixel-heuristic path below — NOT langchain.jvm's generic echo mock, see
     namespace docstring for why that mock is the wrong offline fallback here)."
     []
     (when-let [k (api-key)]
       (model/anthropic-model {:api-key k :model model-id
                                :http-fn jvm/jvm-http-fn
                                :json-write jvm/json-write :json-read jvm/json-read}))))

#?(:clj
   (defn available?
     "True when a live vision-LLM call is actually possible (ANTHROPIC_API_KEY set)."
     []
     (some? (api-key))))

;; ───────────────────────── prompt ─────────────────────────

(def ^:private axis-doc
  (str "juice = 見た目・演出 (do particle bursts / screen-shake / HUD flourish read as "
       "satisfying juice, or flat and lifeless?); "
       "feel = 遊びやすさ・バランス (does this moment read as fair, readable, well-paced — "
       "not janky or confusing?); "
       "bugs = bug-FREE-ness, 100 meaning nothing in the frame looks broken, glitched, "
       "stuck, or missing (LOWER score = more suspicious-looking, not more buggy-as-a-word); "
       "clarity = does this single screenshot legibly communicate what just happened "
       "(a pickup / a hit / victory / game-over) to someone who only sees this one frame?"))

(defn- prompt [moment]
  (str "You are QA-reviewing one screenshot from a real-time 2D dodge/collect arena game "
       "(\"スライムハント\" / Slime Hunt). This screenshot was captured at the moment: "
       (name moment) ". Score it 0-100 on each of these 4 axes — " axis-doc " "
       "Reply with ONLY a JSON object, no prose outside it: "
       "{\"juice\": <0-100 integer>, \"feel\": <0-100 integer>, \"bugs\": <0-100 integer>, "
       "\"clarity\": <0-100 integer>, \"notes\": \"<one or two sentence critique>\"}."))

;; ───────────────────────── offline heuristic (mock/degrade path) ─────────────────────────
;; JVM-only (javax.imageio / java.awt.image): this whole feature is a JVM CLI tool (see
;; scripts/playtest_coscientist.clj), never invoked from a :cljs/WASM build — no portable
;; fallback is needed on those platforms.

#?(:clj
   (defn- decode-image ^java.awt.image.BufferedImage [b64]
     (let [bytes (.decode (java.util.Base64/getDecoder) b64)]
       (javax.imageio.ImageIO/read (java.io.ByteArrayInputStream. bytes)))))

#?(:clj
   (defn- pixel-stats
     "Samples a coarse grid of pixels (every `stride`th pixel on both axes — a 1000x1080
     screenshot at stride 6 is ~30k samples: fast, plenty for a coarse heuristic) and
     returns real stats over the ACTUAL image bytes: {:mean-luma :luma-stddev :vivid-frac
     :n-colors :n}. :vivid-frac = fraction of sampled pixels whose max-channel minus
     min-channel exceeds 40 (a cheap 'this pixel is saturated/colorful, not grey' test).
     :n-colors = count of distinct coarsely-quantized (64-level-per-channel) colors seen."
     [^java.awt.image.BufferedImage img]
     (let [w (.getWidth img) h (.getHeight img) stride 6
           pixels (for [y (range 0 h stride) x (range 0 w stride)] (.getRGB img x y))
           rgbs (mapv (fn [p] [(bit-and (bit-shift-right p 16) 0xff)
                               (bit-and (bit-shift-right p 8) 0xff)
                               (bit-and p 0xff)])
                      pixels)
           lumas (mapv (fn [[r g b]] (+ (* 0.299 r) (* 0.587 g) (* 0.114 b))) rgbs)
           n (max 1 (count lumas))
           mean (/ (reduce + lumas) n)
           variance (/ (reduce + (map (fn [l] (let [d (- l mean)] (* d d))) lumas)) n)
           stddev (Math/sqrt (double variance))
           vivid (count (filter (fn [[r g b]] (> (- (max r g b) (min r g b)) 40)) rgbs))
           buckets (into #{} (map (fn [[r g b]] [(quot r 64) (quot g 64) (quot b 64)])) rgbs)]
       {:mean-luma mean :luma-stddev stddev :vivid-frac (/ (double vivid) n)
        :n-colors (count buckets) :n n})))

#?(:clj
   (defn- clamp0-100 [x] (long (Math/round (double (max 0 (min 100 x)))))))

#?(:clj
   (defn heuristic-score
     "Offline stand-in for score-screenshot when ANTHROPIC_API_KEY is unset. A REAL
     pixel-variance/color-presence heuristic over `image-b64`'s actual bytes — every field
     below is computed from the real image and varies screenshot-to-screenshot; this is NOT
     a fake constant score. Necessarily much cruder than a real vision model:

       :juice   color richness (:n-colors) + :vivid-frac, a proxy for particle bursts / FX
                reading as visually alive vs a flat, monochrome frame.
       :bugs    bug-FREE-ness. Penalizes a near-uniform frame (luma :luma-stddev below a
                threshold) — a cheap proxy for a blank/stuck/glitched render. Catches gross
                breakage only, nothing subtler (a real vision model reads actual content).
       :clarity luma contrast (:luma-stddev), a proxy for 'something legible is happening
                here' (a flat grey frame scores low; a frame with a bold high-contrast
                overlay card, like this game's victory/game-over cards, scores higher).
       :feel    遊びやすさ・バランス genuinely CANNOT be assessed from one static frame's
                pixels (it's about input responsiveness/pacing across time, not a single
                image) — regresses toward a neutral midpoint (damped toward 50 from the
                juice/clarity average) instead of inventing an unrelated number. Documented
                here so this axis is never mistaken for a real feel signal when offline.

     `moment` (a keyword like :title/:pickup/:hit/:victory/:gameover) is accepted for
     interface parity with score-screenshot/the live path but does not change the
     heuristic — a real vision model DOES use it; this mock is honest that it can't."
     [image-b64 moment]
     (let [img (decode-image image-b64)
           {:keys [mean-luma luma-stddev vivid-frac n-colors n]} (pixel-stats img)
           juice (clamp0-100 (+ 20 (* vivid-frac 130) (* (min 1.0 (/ n-colors 40.0)) 30)))
           bugs (clamp0-100 (if (< luma-stddev 6.0) 15 (+ 55 (min 40 (* luma-stddev 0.6)))))
           clarity (clamp0-100 (+ 25 (* luma-stddev 0.9)))
           feel (clamp0-100 (+ 50 (* 0.25 (- (/ (+ juice clarity) 2.0) 50))))]
       {:juice juice :feel feel :bugs bugs :clarity clarity
        :notes (str "heuristic (offline mock, moment=" (name moment) "): "
                    "mean-luma=" (clamp0-100 mean-luma)
                    " luma-stddev=" (Math/round (double luma-stddev))
                    " vivid-frac=" (format "%.2f" (double vivid-frac))
                    " distinct-color-buckets=" n-colors "/" n " samples")
        :mock true :parsed true})))

;; ───────────────────────── public entry point ─────────────────────────

(defn score-screenshot
  "Given a screenshot (`image-b64`, base64 PNG — see file->b64) + which moment it's from
  (a keyword: :title/:pickup/:hit/:victory/:gameover), returns a structured score map
  {:juice :feel :bugs :clarity :notes :mock :parsed} across the 4 axes documented above.

  Live Claude vision model when ANTHROPIC_API_KEY is set (m defaults to (live-model));
  else the REAL pixel heuristic (heuristic-score), never a fake constant. A live call whose
  reply doesn't parse as JSON degrades honestly (all axes nil, :parsed false) rather than
  silently falling through to the heuristic (that would look like an offline run succeeded
  when actually the live call just returned garbage)."
  ([image-b64 moment] (score-screenshot image-b64 moment #?(:clj (live-model) :cljs nil)))
  ([image-b64 moment m]
   (if m
     (or (some-> (jvm/vision-json m (prompt moment) image-b64) (assoc :mock false :parsed true))
         {:juice nil :feel nil :bugs nil :clarity nil
          :notes "live vision model reply did not parse as structured JSON"
          :mock false :parsed false})
     #?(:clj (heuristic-score image-b64 moment)
        :cljs {:juice nil :feel nil :bugs nil :clarity nil
               :notes "no vision path available on this platform (JVM-only feature)"
               :mock true :parsed false}))))

#?(:clj
   (defn file->b64
     "Reads the file at `path` (a screenshot PNG) and returns its base64 encoding — the
     wire format both `langchain.jvm/vision-json` and `heuristic-score` expect."
     [path]
     (with-open [in (io/input-stream (io/file path))]
       (let [bytes (.readAllBytes in)]
         (.encodeToString (java.util.Base64/getEncoder) bytes)))))
