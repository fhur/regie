(ns regie.errors
  "A namespace that holds functions that throw errors.")

(defn error 
  [msg]
  (throw (new RuntimeException msg)))

(defn unable-to-parse-expression 
  [subexpression]
  (error 
    (str "Unable to process expression '" subexpression "'.\n"
         "Make sure your expressions adheres to the following grammar:

          E ::== string
             |   [ symbol E+ ]

         Examples:

         ; matches o followed by many f's e.g. offfffff
         [:cat \"o\" [:+ \"f\"]

         ; matches either a or b
         [:or \"a\" \"b\"]
         
         ")))

(defn unable-to-create-regex-from-empty-string
  []
  (error 
    (str "Unable to create regex from empty string.
         Empty strings are not supported since its not obvious if they
         match everything and consume no input or if they should match
         ONLY the empty string. 

         If you want a regex that matches either the string s or nothing
         consider using this instead: [:? s]

         Example:

         ; matches \"\" or \"a\"
         [:? \"a\"]

         ; matches \"\" or \"a\" or \"aa\" or \"aaa\" ...
         [:* \"a\"]
         ")))
