(ns rmap.middleware.default
  "The default middleware for recursive maps."
  (:require [rmap.internals :as int]
            [rmap.middleware.api :as api]))


(deftype LockKey [key])

(defn default-middleware
  "This middleware caches realized entries, such that they are
  realized once and only once. It also respects the *unrealized*
  dynamic variable. This middleware is non-dynamic (see
  `add-middleware`)."
  []
  (reify api/Middleware

    (request [_ key cont]
      ;; Get latest data and the entry value.
      (let [val (get (api/latest-data) key ::novalue)]
        (cond
          ;; If no value is available at all, it is not in the map.
          (= val ::novalue)
          nil

          ;; If it is a LockKey, this means it has not been realized yet. Thus lock on the
          ;; original key.
          (instance? LockKey val)
          (locking (.key val)
            ;; Within the lock, get the - possibly updated - value from the latest data.
            (let [val (get (api/latest-data) key)]
              ;; If it is still a LockKey, it needs to be realized.
              (if (instance? LockKey val)
                ;; Check for the *unrealized* binding. If bound, return that value instead.
                (if (bound? (var int/*unrealized*))
                  int/*unrealized*
                  ;; Call the continuation, leading up to the realization, and update the cache.
                  (let [val (cont)]
                    (api/update-data assoc key val)
                    val))
                ;; The entry has been realized already.
                val)))

          ;; Entry is already realized, return realized value.
          :otherwise
          val)))

    (assoc [_ key val]
      (api/update-data assoc key val))

    (assoc-lazy [_ key]
      (api/update-data assoc key (LockKey. key)))

    (dissoc [_ key]
      (api/update-data dissoc key))

    (info [_]
      {:name "default"
       :dynamic? false})))
