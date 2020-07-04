(ns rmap.core
  "The core API for recursive maps."
  (:refer-clojure :exclude [ref])
  (:require [clojure.pprint :refer [simple-dispatch]]))

;;; Internals

(deftype RefTag [key])

(defn- ref-tag? [x]
  (instance? RefTag x))

(defn- resolve-ref-tags [ref data]
  (clojure.walk/postwalk #(cond-> % (ref-tag? %) (-> .key ref)) data))

(deftype RVal [f])

(declare rval?)

(defn- ->ref [cache f]
  (fn ref
    ([key] (ref key nil))
    ([key not-found]
     (if-let [[_ val] (find @cache key)]
       (if (rval? val)
         (locking cache
           (let [val (get @cache key)]
             (if (rval? val)
               (let [ret (f (resolve-ref-tags ref ((.f val) ref)))]
                 (swap! cache assoc key ret)
                 ret)
               val)))
         val)
       not-found))))


;;; Printing

(defmethod print-method RefTag [^rmap.core.RefTag reftag ^java.io.Writer writer]
  (.write writer (str "#rmap/ref " (.key reftag))))

(defmethod simple-dispatch RefTag [^rmap.core.RefTag reftag]
  (print-method reftag *out*))

(defmethod print-method RVal [^rmap.core.RVal rval ^java.io.Writer writer]
  (.write writer "??"))

(defmethod simple-dispatch RVal [^rmap.core.RVal rval]
  (print-method rval *out*))


;;; Public API

(def ^{:dynamic  true
       :arglists '([key] [key not-found])
       :doc "Returns the value mapped to key, not-found or nil if key
  not present. Can only be used inside an [[rval]]
  during [[valuate!]]."} ref)

(defmacro rval
  "Takes a body of expressions and yields an RVal object. The body is
  not evaluated yet. The body can use the [[ref]] function while it is
  evaluated. You can bind it locally for use at a later stage."
  [& body]
  `(RVal. (fn [ref#]
            (binding [ref ref#]
              ~@body))))

(defn rval?
  "Returns true if x is an RVal."
  [x]
  (instance? RVal x))

(defmacro rmap
  "Takes an associative datastructure m and returns m where each of the
  values are wrapped as an [[rval]]. Can be a literal representation,
  then the unevaluated expressions are passed to [[rval]]."
  [m]
  (if (or (map? m) (vector? m))
    (reduce-kv (fn [a k v] (assoc a k `(rval ~v))) m m)
    `(let [m# ~m]
       (reduce-kv (fn [a# k# v#] (assoc a# k# (rval v#))) m# m#))))

(defn valuate!
  "Given associative datastructure m, returns m where all RVal values
  are evaluated. Takes an optional post-evaluation wrapper function."
  ([m] (valuate! m identity))
  ([m f]
   (let [ref (->ref (atom m) f)]
     (reduce-kv (fn [a k _] (assoc a k (ref k))) m m))))

(defn valuate-keys!
  "Given associative datastructure m, returns m where all RVal values
  under the given keys and their dependencies are evaluated."
  [m & keys]
  (let [cache (atom m)
        ref  (->ref cache identity)]
    (run! #(ref %) keys)
    @cache))

(defmacro rmap!
  "Same as [[rmap]], but composed with [[valuate!]]."
  ([m] `(valuate! (rmap ~m)))
  ([m f] `(valuate! (rmap ~m) ~f)))

(defn ref-tag
  "A tagged literal processor, for use with clojure.edn/read-string.

  (clojure.edn/read-string {:readers {'rmap/ref rmap.core/ref-tag}}
    \"{:foo 1 :bar #rmap/ref :foo}\")"
  [key]
  (RefTag. key))


;;; Deprecated stuff

(defn ^{:no-doc true :deprecated "2.1.1"} ->rmap
  [m]
  (reduce-kv (fn [a k v] (assoc a k (rval v))) m m))

(defn ^{:no-doc true :deprecated "2.1.1"} ->rmap!
  ([m] (valuate! (->rmap m)))
  ([m f] (valuate! (->rmap m) f)))
