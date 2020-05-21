(ns rmap.core
  "The core API for recursive maps."
  (:require [clojure.pprint :refer [simple-dispatch]]))

;;; Internals

(deftype RVal [f]
  clojure.lang.IFn
  (invoke [this ref]
    (f ref)))

(defmethod print-method RVal [rval ^java.io.Writer writer]
  (.write writer "??"))

(defmethod simple-dispatch RVal [rval]
  (print-method rval *out*))

(declare rval?)

(deftype RMap [cache]
  clojure.lang.IFn
  (invoke [this key]
    (.valAt this key nil))
  (invoke [this key not-found]
    (.valAt this key not-found))

  clojure.lang.ILookup
  (valAt [this key]
    (.valAt this key nil))
  (valAt [this key not-found]
    (if-let [[_ val] (find @cache key)]
      (if (rval? val)
        (locking cache
          (let [val (get @cache key)]
            (if (rval? val)
              (let [ret (val this)]
                (swap! cache assoc key ret)
                ret)
              val)))
        val)
      not-found))

  clojure.lang.Associative
  (containsKey [this key]
    (contains? @cache key))
  (entryAt [this key]
    (clojure.lang.MapEntry. key (.valAt this key)))

  clojure.lang.IPersistentCollection
  (empty [this]
    (empty @cache))

  clojure.lang.Indexed
  (nth [this i]
    (.valAt this key nil))
  (nth [this i not-found]
    (.valAt this key not-found))

  clojure.lang.Seqable
  (seq [this]
    (seq (if (map? @cache)
           (map #(.entryAt this %) (keys @cache))
           (map #(.valAt this %) (range (count @cache)))))))

(defmethod print-method RMap [rmap ^java.io.Writer writer]
  (.write writer (str "#<RMap: " (pr-str @(.cache rmap)) ">")))

(defmethod simple-dispatch RMap [rmap]
  (print-method rmap *out*))

;;; Public API

(defmacro rval
  "Takes a body of expressions and yields an RVal object. The body has
  implicit access to a `ref` RMap object and is not evaluated yet."
  [& body]
  `(RVal. (fn [~'ref] ~@body)))

(defn rval?
  "Returns true if x is an RVal."
  [x]
  (instance? RVal x))

(defmacro rvals
  "Takes a literal associative datastructure m and returns m where each
  of the value expressions are wrapped with rval."
  [m]
  (reduce-kv (fn [a k v] (assoc a k `(rval ~v))) m m))

(defn rmap?
  "Returns true if x is an RMap."
  [x]
  (instance? RMap x))

(defn ->rmap
  "Takes an associative datastructure m (or an RMap) and yields an RMap
  object of it."
  [m]
  (if (rmap? m)
    (->rmap @(.cache m))
    (RMap. (atom m))))

(defn ->clj
  "Takes an RMap object m (or a Clojure associative datastructure) and
  returns a standard Clojure datastructure where all rval values are
  evaluated."
  [m]
  (if (rmap? m)
    (into (empty m) m)
    (->clj (->rmap m))))

(defmacro rmap
  "Same as rvals, but composed with `->rmap`."
  [m]
  `(->rmap (rvals ~m)))

(defmacro rmap!
  "Same as rmap, but composed with `->clj`."
  [m]
  `(->clj (rmap ~m)))
