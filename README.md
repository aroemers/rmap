[![Clojars Project](https://img.shields.io/clojars/v/functionalbytes/rmap.svg)](https://clojars.org/functionalbytes/rmap)
[![cljdoc badge](https://cljdoc.org/badge/functionalbytes/rmap)](https://cljdoc.org/d/functionalbytes/rmap/CURRENT)
[![Clojure CI](https://github.com/aroemers/rmap/workflows/Clojure%20CI/badge.svg?branch=master)](https://github.com/aroemers/rmap/actions?query=workflow%3A%22Clojure+CI%22)
[![Clojars Project](https://img.shields.io/clojars/dt/functionalbytes/rmap?color=blue)](https://clojars.org/functionalbytes/rmap)
![Map](https://img.shields.io/badge/map-recursive-brightgreen)

# ➰ rmap

A Clojure library for literal recursive maps.

![Banner](banner.png)

## Usage

### The RVal object

We start with a basic building block: a recursive value.
A recursive value is an unevaluated expession, which has access to the associative datastructure - i.e. a map or a vector - it will be evaluated in.
The expression can access this datastructure using the implicit `ref` object.

A recursive value is represented in the form of an RVal object.
You can create an RVal using the `rval` macro.
Is simply takes one or more expressions as its body.
Let's create a simple Clojure map with an RVal object in it and print it:

```clj
(def basic
  {:foo 1
   :bar (rval (println "Calculating bar...")
              (inc (ref :foo)))})
;=> #'user/basic

basic
;=> {:foo 1, :bar ??}
```

As you can see, the `:bar` entry is an RVal and uses the `ref` object to fetch the value mapped to `:foo`.
You can also see that the `:bar` entry is not evaluated yet.

There is a complementary macro, called `rvals`.
It lets you create a datastructure from a literal representation, where all values are automatically RVal objects.
For example, the following creates a similar map, except that the `:foo` value is now also an RVal:

```clj
(def basic
  (rvals {:foo 1
          :bar (do (println "Calculating bar...")
                   (inc (ref :foo)))}))
;=> #'user/basic

basic
;=> {:foo ??, :bar ??}
```

### The RMap object

To evaluate an RVal, it needs a `ref` object to access the other entries of the context it is evaluated in.
While you could build your own, this library offers an RMap object for that purpose.
An RMap object acts as an associative datastructure, but is read-only.

We can create such an RMap by passing a standard map or vector to `->rmap`.
The resulting object can be used to fetch values from the "wrapped" map or vector.
If a requested value is an RVal, the RMap will evaluate it first _by passing itself_.
Recursion! 💥

Let's create an RMap for the basic map we created before and use it:

```clj
(def basic-r (->rmap basic))
;=> #'user/basic-r

basic-r
;=> #<RMap: {:foo ?? :bar ??}>

(basic-r :b)
Calculating bar...
;=> 2

(basic-r :b)
;=> 2

basic-r
;=> #<RMap: {:foo 1 :bar 2}>
```

You can see that the `:bar` entry is evaluated now, yielding the expected result.
You can also see that the result is cached, as the "calculating" message is only printed once.
This caching happens on the level of the RMap object.
The original map itself has not changed:

```clj
basic
;=> {:foo ??, :bar ??}
```

Some final remarks about RMaps.

- Evaluating entries through an RMap is thread-safe and cached in the scope of that particular RMap.
- Most common access methods are supported on an RMap object, such as `(get rmap x)`, `(seq rmap)`, `(:foo rmap)` for maps and `(nth rmap 2)` for vectors, et cetera.
- Calling `seq` on an RMap will evaluate all entries. Remember that many Clojure core functions use `seq` underwater, even for simple things like `keys`.
- Passing an RMap instead of a Clojure datastructure to `->rmap` will create a copy of that RMap with its own cache.

### Combining the building blocks

To make it easer to create recursive maps or vectors, another macro is provided, called `rmap`.
This is a combination of `rvals` and `->rmap`.
It takes a literal map or vector and returns an RMap object directly.
For example:

```clj
(def basic-r
  (rmap {:foo 1
         :bar (inc (ref :foo))
         :baz (+ (ref :bar) (ref :whut 40)}))
;=> #'user/basic-r

basic-r
;=> #<RMap: {:a ?? :b ?? :c ??}>
```

Now you can access the entries through the RMap again.
The RMap is somewhat limited though.
While this is on purpose, you may want to have a normal Clojure datastructure with all the entries evaluated.
By passing the RMap to `->clj` you get just that.
For example:

```clj
(->clj basic-r)
;=> {:foo 1 :bar 2 :baz 42}

(->clj (assoc basic :bar 1001))
;=> {:bar 1001 :bar 1002 :baz 1042}
```

Now you can work on the maps like you're used to.
Note that you can use `->clj` on a normal datastructure as well.
In that case it will create an RMap under water to evaluate all values.

The last macro that is provided is `rmap!`.
This is the same as `rmap`, but returns an instantly evaluated Clojure datastructure (using `->clj`).
For example:

```clj
(rmap! {:foo 1
        :bar (inc (ref :foo))
        :baz (inc (ref :bar))})
;=> {:foo 1 :bar 2 :baz 3}
```

Maybe this `rmap!` is all you need for your purposes.
The other macros and functions are provided to give you all the tools you might need.
This way the rmap library aims to be both simple and easy.

_That's it. Enjoy!_ 🚀

## License

Copyright © 2014-2020 Functional Bytes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
