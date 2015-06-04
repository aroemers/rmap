(ns rmap.internals
  "The recursive map internals and type."
  (:require [rmap.middleware :as mapi])
  (:import [java.util Map$Entry]
           [java.io Writer]))


;;; Middleware application, continuation wise.

(defn pipe-middlewares
  "Builds a continuation chain that, when called, applies the
  Middleware protocol function `action` to all the middlewares of the
  recursive map. Each middleware will receive a 0-arg continuation to
  call as its last parameter. The 0-arity function `f` will be the
  last continuation."
  [action args rm f]
  (let [args (vec args)]
    (loop [wares (reverse @(.middlewares rm))
           cont f]
      (if-let [[mname middleware] (first wares)]
        (recur (next wares)
               (fn []
                 (binding [mapi/*current-map* rm
                           mapi/*current-middleware* mname]
                   (apply action middleware (conj args cont)))))
        (cont)))))

(defn inform-middlewares
  "Calls the Middleware protocol function `action` on all the current
  middleware in the given recursive map."
  [action args rm]
  (doseq [[mname middleware] @(.middlewares rm)]
    (binding [mapi/*current-map* rm
              mapi/*current-middleware* mname]
      (apply action middleware args))))


(def ^{:dynamic true
       :doc "When bound, its value is used by the default-middleware
            when an entry is requested, instead of realizing an
            unrealized entry."}
  *unrealized*)

;; fns = {key (fn [~sym] ~form)}
;; meta = {key val}
;; middlewares = (atom [["name" fn] ...])
;; datas = (atom {"name" middleware-data})
(deftype RMap [fns meta middlewares datas]
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
    (seq (for [key (keys fns)] (.entryAt this key))))

  clojure.lang.IPersistentCollection
  (count [this]
    (count (keys fns)))
  (cons [this obj]
    (if (instance? Map$Entry obj)
      (assoc this (.getKey obj) (.getValue obj))
      (if (vector? obj)
        (if (= (count obj) 2)
          (assoc this (first obj) (second obj))
          (throw (IllegalArgumentException. "vector arg to map conj must be pair")))
        (reduce (fn [m entry]
                  (assoc m (.getKey entry) (.getValue entry)))
                this (seq obj)))))
  (empty [this]
    (RMap. {} nil (atom @middlewares) (atom {})))
  (equiv [this obj]
    (and (map? obj) (= (into {} this) obj)))

  clojure.lang.IPersistentMap
  (assoc [this key obj]
    (let [f (fn [rm] (pipe-middlewares mapi/request [key] rm (fn [] obj)))
          new (RMap. (assoc fns key f) meta (atom @middlewares) (atom @datas))]
      (inform-middlewares mapi/assoc [key obj] new)
      new))
  (assocEx [this key obj]
    (if (contains? (keys fns) key)
      (throw (IllegalArgumentException. "key already present"))
      (assoc this key obj)))
  (without [this key]
    (let [new (RMap. (dissoc fns key) meta (atom @middlewares) (atom @datas))]
      (inform-middlewares mapi/dissoc [key] new)
      new))
  (iterator [this]
    (clojure.lang.SeqIterator. (seq this)))

  clojure.lang.Associative
  (containsKey [this key]
    (contains? (set (keys fns)) key))
  (entryAt [this key]
    (clojure.lang.MapEntry. key (.valAt this key)))

  Object
  (toString [this]
    (binding [*unrealized* (if (bound? (var *unrealized*)) *unrealized* '??)]
      (str "{" (->> (seq this)
                    (map (fn [[key value]]
                           (str key " " (or (and (nil? value) "nil") value))))
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
      (RMap. fns mta (atom @middlewares) (atom @datas))
      (throw (IllegalArgumentException. "meta arg to with-meta must be map"))))
  (meta [this]
    meta)

  clojure.lang.IHashEq
  (hasheq [this]
    (int (clojure.lang.APersistentMap/mapHasheq (into {} this)))))

;; Remove the constructor function of the RMap type from the namespace.
(ns-unmap 'rmap.core '->RMap)

;; Make sure the .toString is used for the RMap type, otherwise it
;; does not show unrealized keys.
(defmethod print-dup rmap.internals.RMap [o ^Writer w]
  (.write w (.toString o)))

(defmethod print-method rmap.internals.RMap [o ^Writer w]
  (.write w (.toString o)))


(defn assoc-lazy*
  "Returns a new recursive map, with the given function `f` added
  under the `k` key. All the middleware is applied on the new
  recursive map, with the :assoc-lazy action."
  [rm k f]
  (let [new (RMap. (assoc (.fns rm) k f) (.meta rm) (atom @(.middlewares rm)) (atom @(.datas rm)))]
    (inform-middlewares mapi/assoc-lazy [k] new)
    new))


(defn non-dynamic-middlewares
  "Returns a vector of non-dynamic middleware names currently in the
  given recursive map."
  [rm]
  (->> @(.middlewares rm)
       (map #((second %) :meta nil nil))
       (filter #(not (:dynamic? %)))
       (mapv :name)))
