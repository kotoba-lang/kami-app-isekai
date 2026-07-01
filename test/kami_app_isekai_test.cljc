(ns kami_app_isekai-test
  (:require [clojure.test :refer [deftest is testing]]
            [kami_app_isekai]))
(deftest namespace-loads
  (testing "the restored CLJC namespace loads"
    (is (some? kami_app_isekai))))
