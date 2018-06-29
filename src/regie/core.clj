(ns regie.core
  (:require
    [clojure.walk :as walk]
    [regie.dfas :as dfas]
    [regie.errors :as errors]
    [regie.nfas :as nfas])
  (:import (regie.dfas Dfa)))


;; ---- Expression Parsing Logic ----
;;
;; Expressions are represented using the following grammar
;;
;; RegieExpression ::== string
;;                  |   '[' symbol RegieExpression+ ']'
;;
;; Examples:
;;
;; To match either a or b
;; [:or "a" "b"]
;;
;; To match a followed by b
;; [:cat "a" "b"]
;;
;; To match a followed by many bs
;; [:cat "a" [:+ "b"]]


(declare regie->nfa)


(defn- expression-vector->nfa
  "Maps a regie-expression to an NFA"
  ;; TODO implement error handling, e.g. nfa-? should take only one argument, etc.
  ;; The current implementation simply ignores the remaining arguments
  [regie-expression]
  (let [[head & tail] regie-expression]
    (case head
          :cat (apply nfas/nfa-cat tail)
          :or  (apply nfas/nfa-or tail)
          :*   (nfas/nfa-* (first tail))
          :?   (nfas/nfa-? (first tail))
          :+   (nfas/nfa-+ (first tail)))))


(defn- regie->nfa
  "Walks a regie-expression tree mapping every sub expression to a corresponding NFA."
  [regie-expression]
  (walk/postwalk
    (fn [expression-leaf]
      (cond
        (keyword? expression-leaf)
        expression-leaf

        (integer? expression-leaf)
        (nfas/nfa-string (str expression-leaf))

        (string? expression-leaf)
        (nfas/nfa-string expression-leaf)

        (vector? expression-leaf)
        (expression-vector->nfa expression-leaf)

        :else
        (errors/unable-to-parse-expression expression-leaf)))

    regie-expression))


(defn regie
  "Creates a regular expression. Supported operators are

  [:+ exp]               one or many
  [:? exp]               one or none
  [:cat exp1 exp2 ...]   a followed by b
  [:or exp1 exp2 ...]    either a or b
  [:* exp]               none or many"
  [regie-expression]
  (->> regie-expression
       (regie->nfa)
       (nfas/nfa->dfa)))


(defn matches?
  "Takes a DFA as produced by `regie` and a string and returns a boolean value
  indicating if the given string conforms to the regex."
  [regie-expression-or-dfa string-query]
  (if (= (type regie-expression-or-dfa) Dfa)
    (dfas/matches? regie-expression-or-dfa string-query)
    (matches? (regie regie-expression-or-dfa) string-query)))


;; ---- Regie Transformers ----

(defn apply-regie-expression
  "Analogous to the (apply x & args) function, returns a flattened
  regie expression with the given regie-cmd appended to it.

  Examples:
  (apply-regie-expression [:cat [1 2 3]])  ;; => [:cat 1 2 3]
  (apply-regie-expression [:or '(a b)])  ;; => [:or a b]"
  [regex-cmd coll]
  (vec (cons regex-cmd coll)))


(defn n-or-more
  "Returns a regie-expressions that onlny matches if there are at least
  n matches of the given regie-expressions.

  - n must be >= 0

  Examples:
  (n-or-more 2 a)  ;; => [:cat a a [:* a]]
  (n-or-more 3 [:? a])  ;; => [:cat [:? a] [:? a] [:? a] [:* [:? a]]]"
  [n regie-expression]
  (assert (>= n 0))
  (if (zero? n)
    [:* regie-expression]
    (as-> (repeat n regie-expression) $
          (concat $ [[:* regie-expression]])
          (apply-regie-expression :cat $))))


;;
;; ---- Built In Regexes ----
;;
;; Since regie-expressions are just data they can easily be composed using regular functions.
;; Below you will find a list of some commonly used regular expressions such as
;; regie-lower-case-english-letters or regie-digits.


;; XXX this means that to represent all unicode letters we need at least 100k transitions
;; Maybe it would be smarter to be able to represent sets/ranges of characters compactly...
;;
;; Some ideas:
;; - a range is a pair of ints [start, end)
;; - inclusion in the range is just start <= num < end
;; - DFAs hold a table of HashMap<IntRange, Integer>[]
(def regie-lower-case-english-letters
  "A regex that matches a single lowercase english letter e.g. /[a-z]/"
  (->> (map str "abcdefghijklmnopqrstuvwxyz")
       (apply-regie-expression :or)))


(def regie-upper-case-english-letters
  "A regex that matches a single lowercase english letter e.g. /[A-Z]/"
  (->> (rest regie-lower-case-english-letters)
       (map #(.toUpperCase %))
       (apply-regie-expression :or)))


(def regie-digits
  "A regex that matches a single digit e.g. /\\d/"
  (->> (range 0 10)
       (map str)
       (apply-regie-expression :or)))
