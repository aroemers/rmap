(ns rmap.core
  (:import [java.util Map$Entry LinkedHashMap]
           [java.io Writer]))

(deftype RMap [keyset evalled val-fn]
  clojure.lang.ILookup
  (valAt [this key]
    (val-fn this key))
  (valAt [this key default]
    (or (val-fn this key) default))

  clojure.lang.IFn
  (invoke [this key]
    (.valAt this key))
  (invoke [this key default]
    (.valAt this key default))

  clojure.lang.Seqable
  (seq [this]
    (seq (for [key keyset] (.entryAt this key))))

  clojure.lang.IPersistentCollection
  (count [this]
    (count keyset))
  (cons [this obj]
    (if (instance? Map$Entry obj)
      (assoc this (.getKey obj) (.getValue obj))
      (if (vector? obj)
        (if (= (count obj) 2)
          (assoc this (first obj) (second obj))
          (throw (IllegalArgumentException. "Vector arg to map conj must be pair")))
        (reduce (fn [m entry]
                  (assoc m (.getKey entry) (.getValue entry)))
                this (seq obj)))))
  (empty [this]
    {})
  (equiv [this obj]
    (throw (UnsupportedOperationException. "Not sure how to equiv yet")))

  clojure.lang.IPersistentMap
  (assoc [this key obj]
    (RMap. (conj keyset key) (doto (.clone evalled) (.put key obj)) val-fn))
  (assocEx [this key obj]
    (if (contains? this key)
      (throw (IllegalArgumentException. "Key already present"))
      (assoc this key obj)))
  (without [this key]
    (RMap. (disj keyset key) (doto (.clone evalled) (.remove key)) val-fn))

  clojure.lang.Associative
  (containsKey [this key]
    (contains? keyset key))
  (entryAt [this key]
    (clojure.lang.MapEntry. key (val-fn this key)))

  Object
  (toString [_]
    (str "{" (->> keyset
                  (map (fn [key]
                         (str key " "
                              (if-let [val (get evalled key)]
                                (if (= ::nil val) "nil" val)
                                "??"))) )
                  (interpose ", ")
                  (apply str))
         "}")))

(defmethod print-dup rmap.core.RMap [o ^Writer w]
  (.write w (.toString o)))

(defmethod print-method rmap.core.RMap [o ^Writer w]
  (.write w (.toString o)))


(defmacro rmap
  "Defines a lazy, recursive map. That is, expressions in the values
  of the map can use the given symbol to access other keys within the
  map. An object of clojure.lang.IFn/ILookup/Seqable is returned.

  For example:
  (let [v 100
        k [1 2 3]
        m (rmap r {:foo 'bar/baz
                   :ns  (namespace (get r :foo))
                   :cnt (+ v (count (:ns r)))
                   k (println \"Only evaluated once, to nil.\")})]
    (:cnt m) ;=> 103
    (get m [1 2 3]) ;=> nil
    (m :nope :default) ;=> :default
    (into {} m)) ;=> {:foo bar/baz, :ns \"bar\", :cnt 103, [1 2 3] nil}"
  [s m]
  `(let [fn# (fn rmap# [~s key#]
               (let [keyset# (.keyset ~s)
                     evalled# (.evalled ~s)]
                 (when-let [lock# (get keyset# key#)]
                   (let [val# (or (get evalled# key#)
                                  (locking lock#
                                    (or (get evalled# key#)
                                        (let [val# (or (condp = key#
                                                         ~@(for [key-and-expression m
                                                                 key-or-expression key-and-expression]
                                                             key-or-expression))
                                                       :rmap.core/nil)]
                                          (.put evalled# key# val#)
                                          val#))))]
                     (when (not= val# :rmap.core/nil) val#)))))
         evalled# (LinkedHashMap.)
         keyset# ~(set (keys m))]
     (rmap.core.RMap. keyset# evalled# fn#)))


(defn seq-evalled
  "Where calling `seq` on a recursive map evaluates all the entries,
  this function only returns a seq of the currently evaluated entries.
  They are also returned in the order they were evaluated.

  This function can also be used to avoid evaluation when using a
  function like `keys` (which uses `seq`). For example, by using `(map
  first (seq-evalled <rmap>))`:

  (map first (seq-evalled (doto (rmap r {:foo 'bar/baz, :ns (namespace (:foo r))}) :foo)))
  ;=> (:foo)"
  [rmap]
  (seq (into {} (.evalled rmap))))
