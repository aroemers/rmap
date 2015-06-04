(ns rmap.middleware.parallel
  "With this middleware you can add extra meta information about which
  entries should be realized in parralel whenever another entry that
  depends on those entries is requested."
  (:require [rmap.middleware.api :as api]
            [rmap.middleware.default :as mdef]))


(defn parallel-middleware
  "Create the parallel middleware with the given dependencies map. The
  dependencies map is a map of collections. Whenever a key is
  requested, the keys in the collection under the requested key in the
  dependencies map are requested first in parallel, before realizing
  the requested entry.

  In most cases it is best practice to add this middleware after a
  caching middleware, such as the default middleware.

  Example:

  (def r (rmap X {:a (do (Thread/sleep 1000) 1)
                  :b (do (Thread/sleep 1000) 2)
                  :c (+ (:a X) (:b X))}
               [(default-middleware)
                (parallel-middleware {:c [:b :a]})]))

  (time (:c r))
  ;=> Elapsed time: 1003.868562 msecs
  ;=> 3"
  [deps]
  (reify api/Middleware
    (request [_ key cont]
      (doseq [fut (for [k (get deps key)]
                    (future (get api/*current-map* k)))]
        (deref fut))
      (cont))
    (assoc [_ key val])
    (assoc-lazy [_ key])
    (dissoc [_ key])
    (info [_] {:name "parallel"
               :dynamic? true})))


(defmacro parallel-rmap
  "A convenience macro for creating a recursive map with the parallel
  middleware already added."
  [sym m deps]
  `(rmap.core/rmap ~sym ~m [(mdef/default-middleware)
                            (parallel-middleware ~deps)]))
