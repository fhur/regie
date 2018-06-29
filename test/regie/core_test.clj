(ns regie.core-test
  (:require [clojure.test :refer :all]
            [regie.core :refer [regie matches? regie-digits regie-lower-case-english-letters n-or-more]]))

(def random-seed 0xc0de)

(def random-string
  "random-string [size]
  Returns a random string of the given size. If no size is
  given then a random size will be picked from 1 to 20"
  ;; TODO this only creates base64 strings, make it return a random unicode string
  ;; XXX that ^ is tedious since not every byte array is a valid UTF-8 string :(
  (let [random (new java.util.Random random-seed)]
    (fn random-string
      ([size]
       (let [byte-array1 (byte-array size)]
         (.nextBytes random byte-array1)
         (-> (java.util.Base64/getEncoder)
             (.encodeToString byte-array1))))
      ([]
       (-> random
           (.nextInt 20)
           inc
           (random-string))))))


(defn assert-matches [regex string]
  (is (matches? (regie regex) string)
      (str "Expected regex " regex " to match string '" string "'")))


(defn assert-not-matches [regex string]
  (is (not (matches? (regie regex) string))
      (str "Expected regex " regex " NOT to match string '" string "'")))


(deftest simple-string-regexes
  (testing "Every non-empty string regex should match itself"
    (let [string (random-string)
          regex string] 
      (assert-not-matches regex "")
      (assert-not-matches regex (str "a" string))
      (assert-not-matches regex (str string "b"))
      (assert-matches regex string)))

  (testing ":cat => Concatenation"
    (let [string-left (random-string)
          string-right (random-string)
          regex [:cat string-left string-right]]
      (assert-not-matches regex "")
      (assert-not-matches regex string-left)
      (assert-not-matches regex string-right)
      (assert-matches regex (str string-left string-right))))

  (testing ":or => Alternation"
    (let [string-left (random-string)
          string-right (random-string)
          regex [:or string-left string-right]]
      (assert-not-matches regex "")
      (assert-not-matches regex (str string-left string-right))
      (assert-matches regex string-left)
      (assert-matches regex string-right)))

  (testing ":* => Kleen Star"
    (let [string (random-string)
          regex [:* string]]
      (assert-matches regex "")
      (assert-matches regex string)
      (assert-matches regex (str string string))
      (assert-matches regex (str string string string string)))) 

  (testing ":? => Option"
    (let [string (random-string)
          regex [:? string]]
      (assert-not-matches regex (str string string))
      (assert-not-matches regex (str string "."))
      (assert-not-matches regex (str "x" string))
      (assert-matches regex "")
      (assert-matches regex string)))

  (testing ":+ => Multiple"
    (let [string (random-string)
          regex [:+ string]]
      (assert-not-matches regex "")
      (assert-matches regex string)
      (assert-matches regex (str string string))
      (assert-matches regex (str string string string string))))

  (testing "Digit support"
    (let [regex [:cat 1 2 3 123]]
      (assert-matches regex "123123")))


  (testing "Composing Regexes: 01+0"
    (let [regex [:cat "0" [:+ "1"] "0"]]
      (assert-not-matches regex "1")
      (assert-not-matches regex "01")
      (assert-not-matches regex "01111111")
      (assert-matches regex "010")
      (assert-matches regex "0110")
      (assert-matches regex "01111111111111111110")))

  (testing "Composing Regexes: 0+1+2?3*"
    (let [regex [:cat [:+ "0"]
                      [:+ "1"]
                      [:? "2"]
                      [:? [:cat regie-digits regie-lower-case-english-letters]]
                      [:* "3"]]]
      (assert-matches regex "01")
      (assert-matches regex "012")
      (assert-matches regex "013")
      (assert-matches regex "0123")
      (assert-matches regex "00000000123")
      (assert-matches regex "000000001113")
      (assert-matches regex "00000000111")
      (assert-matches regex "000000001113333333")
      (assert-matches regex "000000001117a3333333")
      (assert-matches regex "000000001118b3333333"))))


(deftest test-regie-transformers
  (testing "n-or-more: should match the given regex n or more times"
    (testing "Matching 0 or more"
      (let [regex-0-or-more (n-or-more 0 "bar")]
        (assert-matches regex-0-or-more "")
        (assert-matches regex-0-or-more "bar")
        (assert-matches regex-0-or-more "barbar")
        (assert-matches regex-0-or-more "barbarbar")))
    (testing "Matching 2 or more"
      (let [regex-2-or-more (n-or-more 2 "bar")]
        (assert-not-matches regex-2-or-more "")
        (assert-not-matches regex-2-or-more "bar")
        (assert-matches regex-2-or-more "barbar")
        (assert-matches regex-2-or-more "barbarbar")
        (assert-matches regex-2-or-more "barbarbarbar")))))

(deftest test-builtin-regexes
  (testing "regie-digits should match any digit"
    (assert-matches regie-digits "0")
    (assert-matches regie-digits "1")
    (assert-matches regie-digits "2")
    (assert-matches regie-digits "3")
    (assert-matches regie-digits "4")
    (assert-matches regie-digits "5")
    (assert-matches regie-digits "6")
    (assert-matches regie-digits "7")
    (assert-matches regie-digits "8")
    (assert-matches regie-digits "9")
    (assert-not-matches regie-digits "10")))

