(defproject functionalbytes/rmap "0.5.0"
  :description "A Clojure library designed to define literal lazy, recursive maps."
  :url "http://github.com/aroemers/rmap"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]]
  :profiles {:dev {:dependencies [[collection-check "0.1.5"]]}}
  :plugins [[codox "0.8.12"]]
  :codox {:exclude [rmap.internals]})
