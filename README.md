# rmap 

[![Build Status](https://snap-ci.com/aroemers/rmap/branch/master/build_image)](https://snap-ci.com/aroemers/rmap/branch/master)

A Clojure library designed to define literal lazy, recursive maps.

```clojure
(def m
  (rmap X
    {:what "awesome!"
     :clj (str "Clojure is " (:what X))})
(:clj m)
;=> "Clojure is awesome!"
```

## Usage

### Adding to your project

Add this to your leiningen dependencies

```clojure
[functionalbytes/rmap "0.2.0"]
```

or as a maven dependency

```xml
<dependency>
    <groupId>functionalbytes</groupId>
    <artifactId>rmap</artifactId>
    <version>0.2.0</version>
</dependency>
```

and make sure Clojars is available as one of your repositories.


### The API

This library defines one macro, called `rmap`. It takes two arguments: a symbol which can be used to access the recursive map from within the value expressions, and the map itself. It closes over locals and arbritary keys can be used. An immutable object of type `RMap` is returned, which implements all of the necessary interfaces to act like a standard map, such as `IPersistentMap`, `IPersistentCollection`, and `IFn`. This means it can be used with all of the core functions, as a function itself (taking one or two arguments), and with keyword lookups.

The API also defines one function, called `seq-evalled`, giving a sequence of realized entries only (in order of realization). This is because many core functions may or will realize all the values as a side effect. See the section "Core functions on the recursive map" below for more on this.

An example showing some of its usage:

```clojure
(let [v 100
      k [1 2 3]
      m (rmap r {:foo 'bar/baz
                 :ns  (namespace (get r :foo))
                 :cnt (+ v (count (:ns r)))
                 k (println "Only evaluated once, to nil.")})
      n (assoc m :alice 'bob, :foo 'eve/baz)]
      
  m  ;=> {:foo ??, :ns ??, :cnt ??, [1 2 3] ??}

  (:cnt m)           ;=> 103
  (get m [1 2 3])    ;=> nil
  (m :nope :default) ;=> :default
  (into {} m)        ;=> {:foo bar/baz, :ns "bar", :cnt 103, [1 2 3] nil}

  (map first (seq-evalled m))  ;=> (:foo :ns :cnt [1 2 3])
  (map first (seq-evalled n))  ;=> (:alice :foo)
  (into {} (seq-evalled n))    ;=> {:alice bob, :foo eve/baz}
  (:ns n)                      ;=> "eve"
```

#### Immutability and state

All the functions on the recursive map return new objects, so it can be regarded as immutable. Still, keep in mind that a recursive map does have state, as it lazily realizes its values. This state is (shallowly) cloned into the newly created maps.


#### Core functions on the recursive map

This subsection discusses some of the core Clojure functions, and how they work on the recursive maps. Although this is far from exhaustive, it should give a general idea of how to deal with (and possibly keep) lazines.

##### `seq`

This realizes all entries in the recursive map. This is useful for situations like converting the recursive map to a normal map, using `(into {} <rmap>)`. It may be less appropriate for situations like where you want to know all the keys in the recursive map, without realizing any unevaluated values. The standard `keys` functions uses `seq`. Therefore, a function called `seq-evalled` is available, returning a sequence of realized entries only. These entries are also in the order in which they were evaluated.

##### `keys`, `into`, etc

Uses `seq` in its implementation. See the subsection above.

##### `assoc`, `conj`, `without`, etc

Returns a new recursive map, with the given entry or entries added, overwritten or removed. Note that when realizing an unevaluated value in a "parent" or "derivative" recursive map, this does not infuence the others. You can even use this laziness to introduce entries that are required by the unrealized entries. For example, note how the `:b` entry uses an `:a` entry, which is added later:

```clojure
(-> (rmap r {:b (:a r)})
    (conj :a 42)
    :b)
;=> 42
```

##### `count`

Returns the total number of entries in the recursive map, whether realized or not. Use the `seq-evalled` function to get a count of the currently realized entries.

##### `empty`

Returns an empty, ordinary `PersistentHashMap`.

##### `=`, `.equals`, `hash`, etc

Comparing or calculating a hash of a recursive map means that it will be realized in full, before the comparison or calculation is performed. Needless to say, using a recursive map in a set or other map will trigger this as well.

##### `merge`

When a recursive map is given as the second or later argument to merge, it is fully realized. When given as the first argument, it is unaffected.


## License

Copyright © 2014 Functional Bytes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
