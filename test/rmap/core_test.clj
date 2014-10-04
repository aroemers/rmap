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

(deftest rmap-concurrent
  (let [a (atom 0)
        b (atom 0)
        m (rmap r {:a (swap! a inc)
                   :b (reset! b (:a r))})
        l (java.util.concurrent.CountDownLatch. 1)
        d (java.util.concurrent.CountDownLatch. 100)]
    (dotimes [i 50] (future (.await l) (:a m) (.countDown d)))
    (dotimes [i 50] (future (.await l) (:b m) (.countDown d)))
    (.countDown l)
    (.await d)
    (is (= @a 1))
    (is (= @b 1))))


(comment ;; non-macro model
  (deftype RMap [keyset evalled val-fn]
    clojure.lang.IFn
    (invoke [this key] (val-fn this key))
    (invoke [this key default] (or (val-fn this key) default))

    clojure.lang.ILookup
    (valAt [this key] (val-fn this key))
    (valAt [this key default] (or (val-fn this key) default))

    clojure.lang.Seqable
    (seq [this] (seq (for [key keyset] [key (val-fn this key)])))

    Object
    (toString [_]
      (let [eval-now @evalled]
        (str "{" (->> keyset
                          (map (fn [key]
                                 (str key " "
                                      (if-let [val (get eval-now key)]
                                        (if (= ::nil val) "nil" val)
                                        "??"))) )
                          (interpose ", ")
                          (apply str))
             "}"))))

  (let [v 5]
    (let [keyset_123 #{:foo :ns :count :nil}
          evalled_123 (atom {})
          rm_123 (fn rm_234 [r k_123]
                   (when-let [lock_123 (get keyset_123 k_123)]
                     (let [v_123 (or (get @evalled_123 k_123)
                                     (locking lock_123
                                       (or (get @evalled_123 k_123)
                                           (get (swap! evalled_123 assoc k_123
                                                       (or (condp = k_123
                                                             :foo 'bar/baz
                                                             :ns (do (println 'NAMESPACE) (namespace (:foo r)))
                                                             :count (+ (count (:ns r)) v)
                                                             :nil (println 'NIL))
                                                           :rmap.core/nil))
                                                k_123))))]
                       (when (not= v_123 :rmap.core/nil) v_123))))]
      (RMap. keyset_123 evalled_123 rm_123))))
