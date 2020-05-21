(ns rmap.core-test
  (:require [clojure.test :refer :all]
            [rmap.core :refer :all]))

(deftest rval-test
  (let [rv (rval (inc (ref :a)))]
    (is (= 2 (rv {:a 1})))
    (is (rval? rv))))

(deftest rvals-test
  (let [rvsm (rvals {:a 1 :b (inc (ref :a))})
        rvsv (rvals [1 (inc (ref 0))])]
    (is (every? rval? (vals rvsm)))
    (is (every? rval? rvsv))))

(deftest ->rmap-test
  (let [rm (->rmap {:a 1 :b 2})]
    (is (rmap? rm))
    (is (rmap? (->rmap rm)))))

(deftest rmap-test-map
  (let [rmm (rmap {:a 1 :b (inc (ref :a))})]
    (is (= 2 (:b rmm)))
    (is (= 2 (rmm :b)))
    (is (contains? rmm :b))
    (is (= #{[:a 1] [:b 2]} (set (seq rmm))))
    (is (= (into {} rmm) {:a 1 :b 2}))
    (is (= {} (empty rmm)))))

(deftest rmap-test-vector
  (let [rmv (rmap [1 (inc (ref 0))])]
    (is (= 2 (get rmv 1)))
    (is (= 2 (rmv 1)))
    (is (contains? rmv 1))
    (is (= [1 2] (seq rmv)))
    (is (= (into [] rmv) [1 2]))
    (is (= [] (empty rmv)))))

(deftest ->clj-test
  (let [rvsm (rvals {:a 1 :b (inc (ref :a))})
        rvsv (rvals [1 (inc (ref 0))])
        m (->clj rvsm)
        v (->clj rvsv)]
    (is (instance? clojure.lang.IPersistentMap m))
    (is (instance? clojure.lang.IPersistentVector v))
    (is (= {:a 1 :b 2} m))
    (is (= [1 2] v))))

(deftest rmap!-test
  (let [m (rmap! {:a 1 :b (inc (ref :a))})
        v (rmap! [1 (inc (ref 0))])]
    (is (instance? clojure.lang.IPersistentMap m))
    (is (instance? clojure.lang.IPersistentVector v))))
