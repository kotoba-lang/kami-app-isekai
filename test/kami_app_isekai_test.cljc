(ns kami-app-isekai-test
  "Tests for the restored kami-app-isekai config/kernel data (kami-
  engine/kami-app-isekai, deleted PR #82). The original had no
  #[test]s in any of the 3 files (it's a wasm-bindgen game entrypoint);
  these provide coverage of the ported portable kernels/data."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [kami-app-isekai]
            [kami-app-isekai.voxel-world :as vw]
            [kami-app-isekai.pipelines :as pipelines]
            [kami-app-isekai.omniverse :as omniverse]))

(deftest namespace-loads
  (testing "the restored CLJC namespace loads"
    (is (some? (find-ns 'kami-app-isekai)))
    (is (some? (find-ns 'kami-app-isekai.voxel-world)))
    (is (some? (find-ns 'kami-app-isekai.pipelines)))
    (is (some? (find-ns 'kami-app-isekai.omniverse)))))

(deftest palette-shape
  (is (= 11 (count vw/isekai-palette)))
  (is (= [1.0 0.45 0.1] (nth vw/isekai-palette vw/palette-fire)))
  (is (= [0.12 0.36 0.65] (nth vw/isekai-palette vw/palette-water))))

(deftest simple-fbm-deterministic-and-bounded
  ;; Same inputs -> same output (deterministic hash-based noise).
  (let [a (vw/simple-fbm 3.7 9.2)
        b (vw/simple-fbm 3.7 9.2)]
    (is (= a b)))
  ;; Value noise is a bilinear blend of hashes in [0,1), so the result
  ;; should also land in [0,1) for typical inputs.
  (let [v (vw/simple-fbm 12.34 56.78)]
    (is (>= v 0.0))
    (is (< v 1.0))))

(deftest terrain-voxel-at-matches-height-rule
  ;; Well below the FBM-perturbed height baseline (~18 +/- 12) ->
  ;; always stone; well above -> always air.
  (is (= vw/palette-stone (vw/terrain-voxel-at [0 0 0] 0 0 0)))
  (is (nil? (vw/terrain-voxel-at [0 0 0] 0 100 0))))

(deftest demo-house-voxels-shape
  (let [voxels (vw/demo-house-voxels)]
    (is (pos? (count voxels)))
    ;; floor is 16x16 = 256 cells
    (is (>= (count voxels) 256))
    ;; contains the DEC demo row: 2 fire cells, 8 paper cells, 2 water cells
    (is (= 2 (count (filter #(= (nth % 3) vw/palette-fire) voxels))))
    (is (= 8 (count (filter #(= (nth % 3) vw/palette-paper) voxels))))
    (is (= 2 (count (filter #(= (nth % 3) vw/palette-water) voxels))))
    ;; the stone pillar at (2,y,2) for y in 6..9
    (is (= 4 (count (filter #(and (= (nth % 0) 2) (= (nth % 2) 2) (= (nth % 3) vw/palette-stone)
                                   (<= 6 (nth % 1) 9))
                             voxels))))))

(deftest plains-terrain-config-shape
  (is (= "plains" (:biome vw/plains-terrain-config)))
  (is (= 42.0 (:sea-level vw/plains-terrain-config))))

(deftest breathing-clear-color-in-range
  (let [[r g b] (pipelines/breathing-clear-color 0)]
    (doseq [c [r g b]]
      (is (>= c 0.0))
      (is (<= c 1.0)))))

(deftest default-isekai-usda-shape
  (is (string? omniverse/default-isekai-usda))
  (is (str/includes? omniverse/default-isekai-usda "PhysicsScene"))
  (is (str/includes? omniverse/default-isekai-usda "Cartpole")))
