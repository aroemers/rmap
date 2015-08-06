### [API](#the-core-api) | [Change log](CHANGELOG.md) | [Gitter chat](https://gitter.im/aroemers/rmap) | [Twitter](https://twitter.com/functionalbytes)

# Rmap, lazy recursive maps

A Clojure library designed to define literal lazy, recursive maps.

```clojure
(def m
  (rmap X
    {:what "awesome!"
     :clj (str "Clojure is " (:what X))})
(:clj m)
;=> "Clojure is awesome!"
```

## Getting started and quick overview

### Add rmap to your project

Add this to your leiningen dependencies (**latest stable build**, the rest of this README may already be in the future!!1)

```clojure
[functionalbytes/rmap "0.5.0"]
```

or as a maven dependency

```xml
<dependency>
    <groupId>functionalbytes</groupId>
    <artifactId>rmap</artifactId>
    <version>0.5.0</version>
</dependency>
```

and make sure Clojars is available as one of your repositories.

### Create and use an rmap with default behaviour


Create a new rmap with `(rmap <symbol> <literal map using symbol in values>)`

```clojure
(def r 
  (rmap X {:bob "bob" 
           :alice (str "alice and " (:bob X))}))
;=> #'user/r
```

All initial entries are lazy and not yet realized, as one can see when printing the rmap.

```clojure
r
;=> {:bob ??, :alice ??}
```

When requesting an unrealized entry, it will be realized, possibly realizing other entries as well. Note how the rmap instance in which the `:alice` key is evaluated influences the returned value. Also note that the original rmap `r` is not affected, since the `:alice` entry is evaluated in a immutable descendant of `r` due to the `assoc`.

```clojure
(:alice (assoc r :bob "eve"))
;=> "alice and eve"

r
;=> {:bob ??, :alice ??}
```

When requesting an entry in the rmap, its value is cached (in the default behaviour). The next time the entry is requested, the form is not evaluated again. This can be seen when printing the rmap.


```clojure
(:alice r)
;=> "alice and bob"

r
;=> {:bob "bob", :alice "alice and bob"}
```

Read the *API* section for a more elaborate discussion on the core functions and macros. The *Middleware* section shows what and how extra functionality can be added or changed to the behaviour of recursive maps.


## The core API

#### `(rmap <sym> {<key> <form>, ...})`

The main macro in this library is called `rmap`. It takes two arguments: a symbol which can be used to access the recursive map from within the value expressions, and the map itself. It closes over locals and arbritary keys can be used. An immutable object of type `RMap` is returned, which implements all of the necessary interfaces to act like a standard Clojure map, such as `IPersistentMap`, `IPersistentCollection`, and `IFn`. This means it can be used with all of the core functions, as a function itself (taking one or two arguments), and with keyword lookups.

All the entries in the initial map are lazy, i.e. the value forms have not been evaluated yet. We say that these entries are unrealized. Whenever a value is requested from the recursive map, it is realized. Realization -- evaluating the value form -- is done so in the context of the given recursive map instance. This means that whenever an entry uses other entries in the map, it uses the entries from that particular instance.

By default, whenever a lazy entry is realized, it is only realized for that particular instance. Only descendants (like when `assoc`ing) will have the realized entry, if it has been realized at that time. This means that "parent" instances may still have the unrealized entry.

Optionally, the `rmap` macro can take a third parameter: a sequence of middlewares. More on this in the *Middleware* section.

#### `(assoc-lazy <rmap> <sym> <key> <form>)`

The API defines one other macro, called `assoc-lazy`. This returns a new recursive map with the given form added to the given instance, without the form being realized. The form can use the symbol `<sym>` to refer to other entries in the recursive map it is evaluated in.

#### `(merge-lazy <rmap1> <rmap2> ...)`

This function merges two or more recursive maps, without realizing any unrealized entries. For example:

```clojure
(def x (rmap r {:foo 'bar/baz :ns (namespace (:foo r))}))
(def y (rmap r {:foo 'eve/baz :extra 'thing}))

(def z (merge-lazy x y))

z 
;=> {:extra ??, :ns ??, :foo ??}

(:ns z)
;=> "eve"
```

Note that the _non-dynamic_ middleware of the merging recursive maps must be the same. More on this in the *Middleware* section.

#### `(with-unrealized <val> <exprs>)`

The `rmap.internals` namespace has a dynamic variable, called `*unrealized*`. By default it is unbound. The `with-unrealized` macro binds this variable to the given value, around the given expressions. When it is bound, its value is used for unrealized entries whenever such entry is requested. That entry will stay unrealized. Many core library functions evaluate all entries inside a map, such as `seq` and `=`. Using this macro prevents this.

```clojure
(let [x (rmap r {:foo "bar"})]
  (binding [*unrealized* "baz"]
    (:foo x)))   
;=> "baz"
```

#### `(seq-realized <rmap>)`

As calling `seq` on a recursive map normally evaluates all the entries, this function only returns a seq of the currently evaluated entries.

## Middleware

The former API section described the basic, default behaviour of recursive maps. Although this is already powerful, and in many cases enough, a whole set of other behaviours and additions can be thought of. Therefore, recursive maps supports middleware. Actually, the default behaviour is also just middleware around a tiny core. Some middlewares replace other middlewares, others are additions.

### Examples

#### Replacing default behaviour with structural realization sharing

In the former API section it was said that, by default, whenever a map entry is realized in a descendant instance, the entry in the parent or descendant instances are not realized. However, this behaviour can be changed. The namespace `rmap.middleware.sharing` provides middleware that replaces the default behaviour, such that whenever an entry is realized, it is realized in all the rmaps that still have this entry, both parents and descendants. In other words, the realization of entry values are structurally shared. Now the context in which an entry is realized becomes more important as well. For instance, have a look at these examples:

```clojure
(defn mk-example-sharing-maps []
  (let [x (rmap r {:a 1, :b (:a r)} [(sharing-middleware)])]
    [x (assoc x :a 2)]))

(let [[x y] (mk-example-sharing-maps)]
   x        ;=> {:a ??, :b ??}
   y        ;=> {:a 2, :b ??}

   ;; realize :b in the context of x
   (:b x)   ;=> 1

   x        ;=> {:a 1, :b 1}
   y        ;=> {:a 2, :b 1})  ; here :b is also 1

(let [[x y] (mk-example-sharing-maps)]
   x        ;=> {:a ??, :b ??}
   y        ;=> {:a 2, :b ??}

   ;; realize :b in the context of y
   (:b y)   ;=> 2

   x        ;=> {:a 1, :b 2}   ; here :b is also 2
   y        ;=> {:a 2, :b 2})
```

This middleware can be useful, by being sure that a lazy entry is really only evaluated once. But be careful. If another thread realizes an entry in a different (possibly unknown) context, the value of that entry might not be what you expect in your own context. This can be a feature, or downright annoying. If anything, it shows how the default behaviour can be altered.

#### Enhancing default behaviour with parallel realization

While the former example replaces the default behaviour, most middlewares will enhance it. One could think of adding debug messages, timing evaluation per entry or keeping track of the order in which entries are realized. Another possibility, which is actually available in the `rmap.middleware.parallel` namspace, is parallel realization of entries. By adding some meta data to the recursive map, whenever an entry is realized, it knows which dependencies should be realized in parallel first. Below one can see for example that realizing the `:c` entry requires only one second, instead of two with only the default behaviour.

```clojure
(def r (rmap X {:a (do (Thread/sleep 1000) 1)
                :b (do (Thread/sleep 1000) 2)
                :c (+ (:a X) (:b X))}
             [(default-middleware)
              (parallel-middleware {:c [:b :a]})]))

(time (:c r))
;=> Elapsed time: 1003.868562 msecs
;=> 3"
```

### Using middleware

Middleware can either be dynamic or non-dynamic. Dynamic middleware  means that by its nature it can be added and removed to/from a recursive map, without causing consistency problems. Non-dynamic middleware must be added while creating a recursive map, for instance by passing it to the `rmap` macro.

Adding dynamic middleware can be done through the `add-middleware` or `add-middleware-after` functions. The first puts the middleware at the front, the latter places the middleware after the middleware with the given name. Dynamic middleware can be removed by name, with `remove-middleware`. Adding and removing middleware does _not_ result in a new recursive map instance. To get a list of current middleware names, the `current-middlewares` can be used.

### Implementing middleware

#### The protocol

To implement middleware, is has to satisfy the `rmap.middleware/Middleware` protocol. It contains the following five functions.

<table>
	<tr><th>Function</th><th>Role</th></tr>
	<tr>
		<td>(info&nbsp;[this])</td>
		<td>
			This function should return a map with keys <code>:name</code> and <code>:dynamic?</code>. Its values are the name of the middleware, and a boolean indicating whether the middleware is dynamic, respectively.
		</td>
	</tr>
		<tr>
		<td>(request&nbsp;[this&nbsp;key&nbsp;cont])</td>
		<td>
			This function is called whenever an entry in the recursive map is requested. It should either return a value, or return the result of calling the 0-arg continuation. Calling the continuation results in calling the <code>request</code> function of the middleware next in line.
		</td>
	</tr>
		<tr>
		<td>(assoc&nbsp;[this&nbsp;key&nbsp;val]), (assoc-lazy&nbsp;[this&nbsp;key]), (dissoc&nbsp;[this&nbsp;key])</td>
		<td>
			These functions are called to inform the middleware that a (lazy) entry has just been added or removed from the recursive map. Note that this addition or removal resulted in a new instance. The middleware may want to update its data accordingly.
		</td>
	</tr>
</table>


#### Data functions

Middleware implementations can store and retrieve data from the recursive map instance calling their functions. New descendents of the instance will carry over the data, but updating the data does not influence the data of other instances. Updating the data does not result in a new instance either. The data functions can be found in the `rmap.middleware` namespace.

<table>
	<tr><th>Function</th><th>Role</th></tr>
	<tr>
		<td>(latest-data)</td>
		<td>
			Returns the data for the currently applied middleware at the current time.
		</td>
	</tr>
	<tr>
		<td>(update&#8209;data&nbsp;[f&nbsp;&&nbsp;args])</td>
		<td>
			Updates the data for the currently applied middleware, by applying function `f` on the current data, and the optional `args`. Updates are atomic.
		</td>
	</tr>
</table>


## Clojure.core functions on recursive maps

All Clojure core functions you use on maps can be used on recursive maps. However, there are some things to keep in mind, due to the characteristics of recursive maps. This section discusses some of the core functions, and how they work on the recursive maps. Although this is not a complete overview, it should give a general idea on how to deal with (and possibly keep) laziness.

<table>
	<tr><th>Function</th><th>Note</th></tr>
	<tr>
		<td>seq</td>
		<td>
			This realizes all entries in the recursive map. This is useful for situations like converting the recursive map to a normal map, using <code>(into {} rmap)</code>. It may be less appropriate for situations like where you want to know all the keys in the recursive map, without realizing any unevaluated values; the core <code>keys</code> functions uses <code>seq</code>.<br/>
			<br/>
			Using <code>with-unrealized</code> or <code>seq-realized</code> prevents realization, and thus can be used to retrieve the keys of all entries or realized entries, respectively.
		</td>
	</tr>
	<tr>
		<td>keys, into, ...</td>
		<td>
			Uses <code>seq</code> in its implementation. See the subsection above.
		</td>
	</tr>
	<tr>
		<td>assoc, conj, without, ...</td>
		<td>
			Returns a new recursive map, with the given entry or entries added, overwritten or removed. You can use the laziness of recursive maps to introduce entries that are required by still unrealized entries. For example, note how the <code>:b</code> entry uses an <code>:a</code> entry, which is added later: <code>(-> (rmap r {:b (:a r)}) (conj [:a 42]) :b) ;=> 42</code>
		</td>
	</tr>
	<tr>
		<td>count</td>
		<td>
			Returns the total number of entries in the recursive map, both realized and unrealized.
		</td>
	</tr>
	<tr>
		<td>empty</td>
		<td>
			Returns an empty recursive map, with the same middleware as the instance it was called on.
		</td>
	</tr>
	<tr>
		<td>=, .equals, hash, ...</td>
		<td>
			Comparing or calculating a hash of a recursive map means that it will be realized in full, before the comparison or calculation is performed. Using <code>with-unrealized</code> prevents this.
		</td>
	</tr>
	<tr>
		<td>merge</td>
		<td>
			When a recursive map is given as the second or later argument to <code>merge</code>, it is fully realized. When given as the first argument, it is unaffected. To merge two or more recursive maps, while not realizing the unrealized forms, use the <code>merge-lazy</code> function as explained in the API section.
		</td>
	</tr>
</table>


## License

Copyright © 2014-2015 Functional Bytes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
