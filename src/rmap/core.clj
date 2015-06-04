(ns rmap.core
  (:require [rmap.internals :as int]
            [rmap.middleware :as mapi]
            [rmap.middleware.default :as mdef])
  (:import [java.util Map$Entry]
           [rmap.internals RMap]))


;;; The core public API

(defn rmap*
  "Create an empty recursive map. Given no arguments, the
  default-middleware is used. One can also supply a vector of
  middlewares. An empty vector means no middleware is used, meaning
  that an entry will be realized every time it is requested."
  ([]
   (rmap* [(mdef/default-middleware)]))
  ([middlewares]
   (let [named (map (fn [middleware]
                      [(:name (mapi/info middleware)) middleware])
                    middlewares)]
     (RMap. {} nil (atom (vec named)) (atom {})))))


(defmacro rmap
  "Defines a lazy, recursive map. That is, expressions in the values
  of the map can use the given symbol `sym` to access other keys
  within the map. Optionally one can supply a vector of middlewares.
  If not supplied, the default-middleware is used. An empty vector
  means no middleware is used, meaning that an entry will be realized
  everytime it is requested. See README for usage and examples."
  ([sym m]
   `(rmap ~sym ~m [(mdef/default-middleware)]))
  ([sym m middlewares]
   `(-> (rmap* ~middlewares)
        ~@(for [[k form] m]
            `(assoc-lazy ~sym ~k ~form)))))


(defmacro assoc-lazy
  "Associates a lazy form to the given recursive map, under the key
  `k`. The form can access other entries in the map using the `sym`
  symbol."
  [rm sym k form]
  `(let [k# ~k
         f# (fn [~sym] (int/pipe-middlewares mapi/request [k#] ~sym (fn [] ~form)))]
     (int/assoc-lazy* ~rm k# f#)))


(defmacro with-unrealized
  "Binds the dynamic variable rmap.internals/*unrealized* to the given
  value, and executes the body within that binding. When bound, its
  value is used by the middlewares, such as the default middleware,
  instead of realizing an unrealized entry."
  [val & body]
  `(binding [int/*unrealized* ~val]
     ~@body))


;;; Middleware related public API

(defn add-middleware
  "Add dynamic middleware to the given recursive map, in front of the
  other middlewares. This function returns a sequence of middleware
  names in the recursive map."
  [rmap middleware]
  (let [{:keys [name dynamic?] :as meta} (mapi/info middleware)]
    (if dynamic?
      (->> (swap! (.middlewares rmap) (fn [current] (cons [name middleware] current)))
           (map first))
      (throw (IllegalArgumentException. "cannot add non-dynamic middleware after construction")))))


(defn add-middleware-after
  "Add dynamic middleware to the given recursive map, inserted right
  after the middleware with the given name. Returns a sequence of
  middleware names in the recursive map."
  [rmap middleware after-name]
  (let [{:keys [name dynamic?] :as meta} (mapi/info middleware)
        index (->> @(.middlewares rmap)
                   (keep-indexed (fn [i m] (when (= (first m) after-name) i)))
                   (first))]
    (cond
      (not dynamic?)
      (throw (IllegalArgumentException. "cannot add non-dynamic middleware after construction"))

      (not index)
      (throw (IllegalArgumentException. (str "cannot find middleware with name " after-name)))

      :otherwise
      (->> (swap! (.middlewares rmap)
                  (fn [current]
                    (vec (concat (take (inc index) current)
                                 [[name middleware]]
                                 (drop (inc index) current)))))
           (map first)))))


(defn remove-middleware
  "Remove dynamic middleware by name from a recursive map. This also
  removes the middleware data from the recursive map, if any. Returns
  a sequence of middleware names that are still in the recursive map."
  [rmap name]
  (if-let [[index middleware] (->> @(.middlewares rmap)
                                   (keep-indexed (fn [i m] (when (= (first m) name) [i (second m)])))
                                   (first))]
    (if (:dynamic? (mapi/info middleware))
      (do (swap! (.datas rmap) assoc-in name nil)
          (->> (swap! (.middlewares rmap)
                      (fn [current]
                        (vec (concat (take index current) (drop (inc index) current)))))
               (map first)))
      (throw (IllegalArgumentException. "cannot remove non-dynamic middleware")))
    (throw (IllegalArgumentException. (str "cannot find middleware with name " name)))))


(defn current-middlewares
  "Returns a sequence of the current middleware names."
  [rmap]
  (map first @(.middlewares rmap)))


;;; Utility functions.

(defn merge-lazy
  "Merges one or more recursive maps, without realizing any unrealized
  values. Returns a new recursive map. Only allowed when all the
  recursive maps have the same non-dynamic middleware, in the same
  order. Middleware data is merged as well. The middleware of the
  \"last\" recursive map is used for the merged result."
  [m1 & mx]
  (let [m1-nondynamics (int/non-dynamic-middlewares m1)]
    (if (every? (fn [rm] (= (int/non-dynamic-middlewares rm) m1-nondynamics)) mx)
      (RMap. (apply merge (.fns m1) (map #(.fns %) mx))
             (apply merge (.meta m1) (map #(.meta %) mx))
             (atom @(.middlewares (or (last mx) m1)))
             (atom (apply merge-with merge @(.datas m1) (map #(-> % .datas deref) mx))))
      (throw (IllegalArgumentException.
              "cannot lazy-merge maps with different non-dynamic middlewares")))))


(defn seq-evalled
  "Where calling `seq` on a recursive map normally evaluates all the entries,
  this function only returns a seq of the currently evaluated entries."
  [rmap]
  (binding [int/*unrealized* ::ignore]
    (remove (fn [^clojure.lang.MapEntry me]
              (= (.val me) ::ignore))
            (seq rmap))))
