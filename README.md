# regie

## What is this?
A tiny library for regular expressions expressed as plain data.

## But doesn't Clojure have regular expressions already?

It does and this library is not an attempt at re-implementing regular expressions. 
It is however an attempt at exploring how a composable regular expression engine would 
look like and how that could help at writing regular expressions that are easier
to read and write.

## What do you mean by "composable regular expressions"

Let's say that we wanted to implement regular expressions for matching emails.

One way of tackling this problem is by divide and conquer: Lets split the problem
into its sub-parts. Consider the following EBNF-ish syntax:

```
# An email is composed of a username, an @ and a domain
email ::== username "@" domain
```

We can then go a bit deeper and describe how a domain looks like:

```
domain ::== hostname "." tld
```

We could go to [IANA](http://data.iana.org/TLD/tlds-alpha-by-domain.txt) and 
fetch the list of tlds and then write them explicitly.

```
tld ::== "co" 
    |    "com"
    |    "net"
    ...
```

Putting it all together we would end up having something that looks like this:

```
username ::== ...
tld ::== "co" | "com" | "net" ...
domain ::== hostname "." tld
email ::== username "@" domain
```

Which is much more readable than the infamous [email regex](http://emailregex.com/).

## Show me some code

The following examples should give you a good feeling of how to write simple regular expression with `regie`.
To get started first import `regie.core` e.g. `(use 'regie.core)`.

A regular expression that matches either a or b

```clojure
(def a-or-b (regie [:or "a" "b"]))
```

A regular expression that matches zero or more 'a's.

```clojure
(def zero-or-more-as (regie [:+ "a"]))
```

A regular expression that matches either a or b followed by zero or more 'a's.
This example should show you how regex composition looks like.

```clojure
(regie [:cat a-or-b zero-or-more-as])
```
Regie will recursively traverse the given regular expression tree (also called a RegieExpression).

## How to check if a string matches a regex?

The `regie.core` namespace exposes a bunch of functions but you only need one to get started: 
the `matches?` function which takes two arguments a RegieExpression and a string and checks if 
the given string matches the given expression.

Consider the following examples:

```clojure
;; First let's import the regie.core namespace.
(use 'regie.core)

;; To check if regex fully matches string
(matches? regex string)
 
;; Every string is a regex that matches itself
(matches? "hello" "hello")                          ;; => true
 
;; matches? checks that the whole string matches the given regex
(matches? "hello" " hello")                         ;; => false
 
;; Match the concatenation of "hello" and "world"
(matches? [:cat "hello" "world"] "helloworld")      ;; => true
 
;; Match either "hello" or "world"
(matches? [:or "hello" "world"] "hello")            ;; => true
 
;; Matches zero or more "hello"s
(matches? [:* "hello"] "")                          ;; => true
```

## Supported operators

Regie supports the five most common regex operators.

```clojure
;; matches 1 or more of regex
(matches? [:+ regex] string)
 
;; matches 0 or more of regex
(matches? [:* regex] string)
 
;; matches zero or 1 regex
(matches? [:? regex])
 
;; matches regex1 folowed by regex2 followed by regex3...
(matches? [:cat regex1 regex2 regex3 ...])
 
;; matches either regex1 or regex2 or regex3 ....
(matches? [:or regex1 regex2 regex3 ....])
```

## Regexes as data

Notice that since regexes are just data they can be manipulated just as 
you would manipulate regular data-structures.

Consider the following example which builds a regular expression to match
 TLDs by fetching the list of domains from the actual IANA site.
 
In general its probably not a good idea to have a regex depend on the result of a network call but
this should give you an idea of the power of representing regexes as plain data.

```clojure

;; Will result in [:or "AAA" "AARP" "ABARTH" "ABB" "ABBOTT" ...]
(def iana-tlds
  (as-> ;; Fetch the official list of valid TLDs.
      (slurp "http://data.iana.org/TLD/tlds-alpha-by-domain.txt") $
      (.split $ "\n")
      ;; filter out comments in the file
      (filter #(not (.startsWith % "#")) $)
      (cons :or $)
      (vec $)))
 
;; A roughly valid hostname regex.
(def hostname [:+ [:or letter digit "_"]])
                       
(def domain [:cat hostname "." iana-tlds])
 
(matches? domain "github.com") ;; => true
```

## Warning

Don't use this in production. This library is still just a proof of concept.

## License

Copyright © 2018 fhur

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
