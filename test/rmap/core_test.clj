(ns rmap.core-test
  (:require [clojure.test :refer :all]
            [rmap.core :refer :all]))

(deftest rval-test
  (let [rv (rval (inc (ref :a 101)))]
    (is (= 2 ((.f rv) {:a 1})))
    (is (= 102 ((.f rv) {})))
    (is (rval? rv))))

(deftest rmap-test
  (let [rm-map (rmap {:a 1 :b (inc (ref :a))})
        rm-vec (rmap [1 (inc (ref 0))])]
    (is (every? rval? (vals rm-map)))
    (is (every? rval? rm-vec))))

(deftest valuate!-test
  (let [a-calcs (atom 0)
        rm-map  (rmap {:a (do (swap! a-calcs inc) 1)
                       :b (inc (ref :a))
                       :c (+ (ref :a) (ref :d 41))})
        rm-vec  (rmap [1 (inc (ref 0))])]
    (is (= (valuate! rm-map) {:a 1 :b 2 :c 42}))
    (is (= (valuate! rm-vec) [1 2]))
    (is (= 1 @a-calcs))
    (is (= (valuate! rm-map inc) {:a 2 :b 4 :c 44}))))

(deftest valuate-keys!-test
  (let [rv-a (rval 1)
        rv-b (rval 2)
        rm   {:a rv-a :b rv-b}]
    (is (= (valuate-keys! rm :a) {:a 1 :b rv-b}))))

(deftest rmap!-test
  (let [rm-map (rmap! {:a 1 :b (inc (ref :a))})
        rm-vec (rmap! [1 (inc (ref 0))])]
    (is (= {:a 1 :b 2} rm-map))
    (is (= [1 2] rm-vec))
    (is (= (rmap! rm-map) rm-map))
    (is (= (rmap! rm-vec) rm-vec))))

(deftest ref-tag-test
  (let [rm-map (rmap {:a 1 :b #rmap/ref :a})
        rm-vec (rmap [1 #rmap/ref 0])]
    (is (= (valuate! rm-map) {:a 1 :b 1}))
    (is (= (valuate! rm-vec) [1 1]))))

(deftest ref-let-test
  (let [result
        (rmap! {:one                  1
                'two                  2
                "three"               3
                :user/four            4
                'user/five            5
                :flipped-nine         6
                :rmap.core-test/seven 7
                :rest                 [8 9]

                :total
                (ref-let [{:keys        [one user/four ::seven ten]
                           :syms        [two]
                           :user/syms   [five]
                           :strs        [three]
                           six          :flipped-nine
                           [eight nine] :rest
                           :or          {ten (+ 5 5)}
                           :as          my-ref}]
                  (+ one two three four five six seven eight nine ten (my-ref :one)))})]
    (is (:total result) 56)))
