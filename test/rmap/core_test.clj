(ns rmap.core-test
  (:require [clojure.test :refer :all]
            [rmap.core :refer :all]))


(deftest rmap-plain
  (let [m (rmap r {:foo :bar})]
    (is (= (get m :foo) :bar))
    (is (= (:foo m) :bar))
    (is (= (m :foo) :bar))))

(deftest rmap-one-deep
  (let [m (rmap r {:foo 'bar/baz
                   :ns (namespace (get r :foo))})]
    (is (= (get m :ns) "bar")))
  (let [m (rmap r {:foo 'bar/baz
                   :ns (namespace (:foo r))})]
    (is (= (get m :ns) "bar")))
  (let [m (rmap r {:foo 'bar/baz
                   :ns (namespace (r :foo))})]
    (is (= (get m :ns) "bar"))))

(deftest rmap-two-deep
  (let [m (rmap r {:foo 'bar/baz
                   :ns (namespace (get r :foo))
                   :cnt (count (get r :ns))})]
    (is (= (get m :cnt) 3))))

(deftest rmap-locals
  (let [l 100
        m (rmap r {:foo 'bar/baz
                   :ns (namespace (get r :foo))
                   :cnt (+ l (count (get r :ns)))})]
    (is (= (get m :cnt) 103))))

(deftest rmap-lazy-once
  (let [a (atom 0)
        m (rmap r {:lazy (swap! a inc)})]
    (is (= @a 0))
    (is (= (get m :lazy) 1))
    (is (= (get m :lazy) 1))))

(deftest rmap-nil-default-lazy
  (let [a (atom 0)
        m (rmap r {:nil (do (swap! a inc) nil)})]
    (is (= @a 0))
    (is (= (get m :nil) nil))
    (is (= @a 1))
    (is (= (get m :nil :default) :default))
    (is (= (m :nil :default) :default))
    (is (= (:nil m :default) :default))
    (is (= @a 1))))

(deftest rmap-seq
  (let [m (rmap r {:foo 'bar/baz
                   :ns (namespace (get r :foo))})]
    (is (= (into {} m) {:foo 'bar/baz, :ns "bar"}))))


(comment ;; non-macro model
  (let [v 5]
    (let [keyset #{:foo :ns :count :nil}
          evalled (atom {})
          rm_123 (fn rm_234 [k_123]
                   (when (get keyset k_123)
                     (let [r (proxy [clojure.lang.AFn clojure.lang.ILookup] []
                               (invoke
                                 ([k_234] (rm_234 k_234))
                                 ([k_234 d_123] (or (rm_234 k_234) d_123)))
                               (valAt
                                 ([k_234] (rm_234 k_234))
                                 ([k_234 d_123] (or (rm_234 k_234) d_123))))
                           v_123 (or (get @evalled k_123)
                                     (locking evalled
                                       (or (get @evalled k_123)
                                           (get (swap! evalled assoc k_123
                                                       (or (condp = k_123
                                                             :foo 'bar/baz
                                                             :ns (do (println 'NAMESPACE) (namespace (:foo r)))
                                                             :count (+ (count (:ns r)) v)
                                                             :nil (println 'NIL))
                                                           ::nil))
                                                k_123))))]
                       (when (not= v_123 ::nil) v_123))))]
      (proxy [clojure.lang.AFn clojure.lang.ILookup] []
        (invoke
          ([k_234] (rm_123 k_234))
          ([k_234 d_123] (or (rm_123 k_234) d_123)))
        (valAt
          ([k_234] (rm_123 k_234))
          ([k_234 d_123] (or (rm_123 k_234) d_123)))))))
