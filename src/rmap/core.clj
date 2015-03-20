(ns rmap.core
  (:import [java.util Map$Entry]
           [java.io Writer]))


;;; The dynamic var influencing realization.

(def ^:dynamic *unrealized*)


;;; The recursive map type.

;; evalled = (atom {key value}) / nil
;; fns     = {key fn}
;; meta    = {}
;; sharing = boolean
(deftype RMap [evalled fns meta sharing]
  clojure.lang.MapEquivalence

  clojure.lang.ILookup
  (valAt [this key]
    (.valAt this key nil))
  (valAt [this key default]
    (if-let [f (get fns key)]
      (f this)
      default))

  clojure.lang.IFn
  (invoke [this key]
    (.valAt this key))
  (invoke [this key default]
    (.valAt this key default))

  clojure.lang.Seqable
  (seq [this]
    (seq (remove (fn [^clojure.lang.MapEntry me]
                   (= (and (bound? (var *unrealized*))
                           (= *unrealized* :rmap.core/ignore)
                           (.val me)) :rmap.core/ignore))
                 (for [key (keys fns)] (.entryAt this key)))))

  clojure.lang.IPersistentCollection
  (count [this]
    (count (keys fns)))
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
    (RMap. (when-not sharing (atom {})) {} nil sharing))
  (equiv [this obj]
    (and (map? obj) (= (into {} this) obj)))

  clojure.lang.IPersistentMap
  (assoc [this key obj]
    (RMap. (when-not sharing (atom @evalled)) (assoc fns key (fn [_] obj)) meta sharing))
  (assocEx [this key obj]
    (if (contains? (keys fns) key)
      (throw (IllegalArgumentException. "Key already present"))
      (assoc this key obj)))
  (without [this key]
    (RMap. (when-not sharing (atom (dissoc @evalled key))) (dissoc fns key) meta sharing))
  (iterator [this]
    (clojure.lang.SeqIterator. (seq this)))

  clojure.lang.Associative
  (containsKey [this key]
    (contains? (set (keys fns)) key))
  (entryAt [this key]
    (clojure.lang.MapEntry. key (.valAt this key)))

  Object
  (toString [this]
    (binding [*unrealized* (if (bound? (var *unrealized*)) *unrealized* "??")]
      (str "{" (->> (seq this)
                    (map (fn [[key value]] (str key " " value)))
                    (interpose ", ")
                    (apply str))
           "}")))
  (equals [this obj]
    (or (identical? this obj) (.equiv this obj)))
  (hashCode [this]
    (int (clojure.lang.APersistentMap/mapHash (into {} this))))

  java.util.Map
  (get [this k]
    (.valAt this k))
  (isEmpty [this]
    (empty? this))
  (size [this]
    (count this))
  (keySet [this]
    (keys fns))
  (put [_ _ _]
    (throw (UnsupportedOperationException.)))
  (putAll [_ _]
    (throw (UnsupportedOperationException.)))
  (clear [_]
    (throw (UnsupportedOperationException.)))
  (remove [_ _]
    (throw (UnsupportedOperationException.)))
  (values [this]
    (->> this seq (map second)))
  (entrySet [this]
    (->> this seq set))

  clojure.lang.IObj
  (withMeta [this mta]
    (if (map? mta)
      (RMap. (when-not sharing (atom @evalled)) fns mta sharing)
      (throw (IllegalArgumentException. "Meta arg to with-meta must be map"))))
  (meta [this]
    meta)

  clojure.lang.IHashEq
  (hasheq [this]
    (int (clojure.lang.APersistentMap/mapHasheq (into {} this)))))

;; Remove the constructor function of the RMap type from the namespace.
(ns-unmap 'rmap.core '->RMap)

;; Make sure the .toString is used for the RMap type, otherwise it
;; does not show unrealized keys.
(defmethod print-dup rmap.core.RMap [o ^Writer w]
  (.write w (.toString o)))

(defmethod print-method rmap.core.RMap [o ^Writer w]
  (.write w (.toString o)))


;;; The public API

(defmacro assoc-lazy
  "Associate a key-value pair to the given recursive map `r`, where
  the value `f` is a lazily evaluated form. The form can use the
  symbol `s` to refer to the recursive map it is evaluated in. Returns
  a new recursive map."
  [r s k f]
  `(rmap.core.RMap.
    (when-not (.sharing ~r) (atom @(.evalled ~r)))
    (let [k# ~k]
      (assoc (.fns ~r) k#
             (if (.sharing ~r)
               (let [v# (promise)]
                 (fn [~s]
                   (if (realized? v#)
                     @v#
                     (if (bound? (var *unrealized*))
                       *unrealized*
                       (locking v#
                         (if (realized? v#)
                           @v#
                           @(deliver v# ~f)))))))
               (fn [~s]
                 (if (contains? @(.evalled ~s) k#)
                   (get @(.evalled ~s) k#)
                   (if (bound? (var *unrealized*))
                     *unrealized*
                     (locking k#
                       (if (contains? @(.evalled ~s) k#)
                         (get @(.evalled ~s) k#)
                         (get (swap! (.evalled ~s) assoc k# ~f) k#)))))))))
    (.meta ~r)
    (.sharing ~r)))


(defmacro rmap
  "Defines a lazy, recursive map. That is, expressions in the values
  of the map can use the given symbol `s` to access other keys within
  the map. See README for usage and examples."
  ([s m]
     `(rmap ~s ~m false))
  ([s m sharing]
     `(-> (rmap.core.RMap. (when-not ~sharing (atom {})) {} nil ~sharing)
          ~@(for [[k f] m]
              `(assoc-lazy ~s ~k ~f)))))


(defn merge-lazy
  "Merges two or more recursive maps, without realizing any unrealized
  values. Returns a new recursive map."
  [m1 m2 & mx]
  (RMap. (when-not (.sharing m1)
           (atom (apply merge @(.evalled m1) @(.evalled m2) (map #(-> % .evalled deref) mx))))
         (apply merge (.fns m1) (.fns m2) (map #(.fns %) mx))
         (apply merge (.meta m1) (.meta m2) (map #(.meta %) mx))
         (.sharing m1)))


(defn seq-evalled
  "Where calling `seq` on a recursive map normally evaluates all the entries,
  this function only returns a seq of the currently evaluated entries. This has
  become a convenience function for backwards compatibility, as it has the same
  effect as `(binding [*unrealized* :rmap.core/ignore] (seq <rmap>))`."
  [rmap]
  (binding [*unrealized* :rmap.core/ignore] (seq rmap)))
