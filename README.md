# rmap

A Clojure library designed to define literal lazy, recursive maps.

## Usage

### Adding to your project

Add this to your leiningen dependencies

```clojure
[functionalbytes/rmap "0.1.1"]
```

or as a maven dependency

```xml
<dependency>
    <groupId>functionalbytes</groupId>
    <artifactId>rmap</artifactId>
    <version>0.1.1</version>
</dependency>
```

and make sure Clojars is available as one of your repositories.

### The API

This library defines just one macro, called `rmap`. It takes two arguments: a symbol which can be used to access the recursive map from within the value expressions, and the map itself. It closes over locals and arbritary keys can be used. An object of type `AFn + ILookup + Seqable` is currently returned, which means it can be used with the core `get` function, as a function itself (taking one or two arguments), with keyword lookups, and all functions using `seq`s, such as `into`.

For example:

```clojure
(let [v 100
      k [1 2 3]
      m (rmap r {:foo 'bar/baz
                 :ns  (namespace (get r :foo))
                 :cnt (+ v (count (:ns r)))
                 k (println "Only evaluated once, to nil.")})]

  (:cnt m)           ;=> 103
  (get m [1 2 3])    ;=> nil
  (m :nope :default) ;=> :default
  (into {} m))       ;=> {:foo bar/baz, :ns "bar", :cnt 103, [1 2 3] nil}
```

## License

Copyright © 2014 Functional Bytes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
