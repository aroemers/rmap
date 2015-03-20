(ns rmap.core-test
  (:require [clojure.test :refer :all]
            [rmap.core :refer :all]
            [collection-check :refer :all]))


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
    (is (= (get m :nil :default) nil))
    (is (= (m :nil :default) nil))
    (is (= (:nil m :default) nil))
    (is (= @a 1))))

(deftest rmap-default
  (let [m (rmap r {})]
    (is (= (get m :foo :default) :default))
    (is (= (m :foo :default) :default))
    (is (= (:foo m :default) :default))))

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

(deftest rmap-assoc
  (let [m (assoc (rmap r {:b (:a r)}) :a 42)]
    (is (= (:b m) 42))))

(deftest rmap-count
  (let [m (rmap r {:a 1, :b 2})]
    (:a m)
    (is (= (count m) 2))))

(deftest rmap-cons
  (let [m (rmap r {:b (:a r)})
        n (conj m {:a 42})]
    (is (= (:b n) 42))))

(deftest rmap-seq-evalled
  (let [m (rmap r {:a 42, :b (:a r), :c 0})]
    (:b m)
    (is (= (set (seq-evalled m)) #{[:a 42], [:b 42]}))))

(deftest rmap-collection-check
  (assert-map-like 100 (rmap r {})
                   clojure.test.check.generators/int
                   clojure.test.check.generators/int))


(comment ;; non-macro model
  (let [v 5]
    (let [fn# (fn [r key#]
                (let [keyset# (.keyset r)
                      evalled# (.)]
                  (when-let [lock# (get keyset# key#)]
                    (let [val# (or (get evalled# key#)
                                   (locking lock#
                                     (or (get evalled# key#)
                                         (let [val# (or (condp = key#
                                                          :foo 'bar/baz
                                                          :ns (do (println 'NAMESPACE) (namespace (:foo r)))
                                                          :count (+ (count (:ns r)) v)
                                                          :nil (println 'NIL))
                                                        :rmap.core/nil)]
                                           (.put evalled# key#)
                                           val#))))]
                      (when (not= val# :rmap.core/nil) val#)))))
          keyset# #{:foo :ns :count :nil}
          evalled# (LinkedHashMap.)]
      (RMap. keyset# evalled# fn#))))

(comment ;; non-macro model
  (deftype RMap [m]
    clojure.lang.ILookup
    (valAt [this key]
      (.valAt this key nil))
    (valAt [this key default]
      (if-let [f (get m key)]
        (f this)
        default))

    clojure.lang.IPersistentMap
    (assoc [this key obj]
      (RMap. (assoc m key obj)))

    clojure.lang.Associative
    (containsKey [this key]
      (contains? (keys m) key))
    (entryAt [this key]
      (clojure.lang.MapEntry. key (.valAt this key)))

    clojure.lang.Seqable
    (seq [this]
      (seq (for [key (keys m)] (.entryAt this key)))))

  (defmacro assoc-lazy
    [r s k f]
    `(assoc ~r ~k
            (let [v# (promise)]
              (fn [~s]
                (if (realized? v#)
                  @v#
                  (locking v#
                    (if (realized? v#)
                      @v#
                      @(deliver v# ~f))))))))

  (defmacro rmap
    [s m]
    `(-> (RMap. {})
         ~@(for [[k f] m]
             `(assoc-lazy ~s ~k ~f))))

  (let [v 5]
    (-> (rmap r {:foo 'bar/baz
                 :ns (do (println 'NAMESPACE) (namespace (:foo r)))
                 :count (+ (count (:ns r)) v)
                 :nil (println 'NIL)
                 :b (:a r)})
        (assoc-lazy r :a 42))))
