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

(defn ^:no-doc ->ref [cache]
  (fn ref
    ([key] (ref key nil))
    ([key not-found]
     (if-let [[_ val] (find @cache key)]
       (if (rval? val)
         (locking cache
           (let [val (get @cache key)]
             (if (rval? val)
               (let [ret (val ref)]
                 (swap! cache assoc key ret)
                 ret)
               val)))
         val)
       not-found))))

;;; Public API

(defmacro rval
  "Takes a body of expressions and yields an RVal object. The body has
  implicit access to a `(fn ref [key] [key not-found])` function and
  is not evaluated yet."
  [& body]
  `(RVal. (fn [~'ref] ~@body)))

(defn rval?
  "Returns true if x is an RVal."
  [x]
  (instance? RVal x))

(defmacro rmap
  "Takes a literal associative datastructure m and returns m where each
  of the value expressions are wrapped with [[rval]]."
  [m]
  (reduce-kv (fn [a k v] (assoc a k `(rval ~v))) m m))

(defn valuate!
  "Given associative datastructure m, returns m where all RVal values
  are evaluated."
  [m]
  (let [ref (->ref (atom m))]
    (reduce-kv (fn [a k _] (assoc a k (ref k))) m m)))

(defn valuate-keys!
  "Given associative datastructure m, returns m where all RVal values
  under the given keys and their dependencies are evaluated."
  [m & keys]
  (let [cache (atom m)
        ref  (->ref cache)]
    (run! #(ref %) keys)
    @cache))

(defmacro rmap!
  "Same as [[rmap]], but composed with [[valuate!]]."
  [m]
  `(valuate! (rmap ~m)))
