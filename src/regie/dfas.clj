(ns regie.dfas
  "Implements DFAs or deterministic finite automatas.

  - call create-dfa to obtain a new DFA instance.
  - call matches? to check if a given string matches a DFA.
  "
  (:import
    [java.util HashMap]))


;; A FlatTransition is an intermediate data structure
;; that represents a possible transition in a DFA from a state `state`
;; to a state `resulting-state` when applied the input `input`.
(defrecord FlatTransition 
  [state input resulting-state])


(defn- map-states
  "Takes an ID mapping function (old-id->new-id) and re-maps all states in the given
   flat-transition."
  [old-id->new-id ^FlatTransition flat-transition]
  (-> flat-transition
      (update :state old-id->new-id)
      (update :resulting-state old-id->new-id)))


; A Dfa a.k.a. deterministic finite automata
; States are represented as integers for memory efficiency.
; The transitions element is implemented as a java array of InputTable
; where the i-th entry represents the possible transitions from
(defrecord Dfa
  [;; An integer in the range [0,)
   start-state
   ;; A set of states where each
   end-states
   ;; holds a HashMap<String, Integer>[]
   ;; where each entry maps input => next state
   ;; thus transition-table[0].get("b") == 2
   ;; means that state 0 when accepts input b and goes to 2.
   transition-table])


(defn- flat-transitions->transition-table
  [flat-transitions old-id->new-id]
  (let [compressed-transitions
          (->> flat-transitions
               ;; Apply the old-id->new-id function to all flat-transitions.
               ;; This will result in all states being converted to ints.
               (map #(map-states old-id->new-id %))
               (group-by :state)
               (map (fn [[state transition-tables]]
                      (let [m (new HashMap (count transition-tables))]
                        (doseq [transition-table transition-tables]
                          (.put m (:input transition-table)
                                  (:resulting-state transition-table)))
                        {:int-state state
                         :transition-hash-map m}))))
        transition-array (make-array HashMap (count old-id->new-id))]

    ;; initialize all indices
    (doseq [index (range (count transition-array))]
      (aset transition-array index (new HashMap 0)))

    (doseq [item compressed-transitions]
      (aset transition-array (:int-state item)
                             (:transition-hash-map item))) 

    transition-array))


;; ---- API ----


(defn is-end-state?
  "Returns true if the given state is a valid end-state or termination state."
  [^Dfa dfa state]
  (contains? (:end-states dfa) state))


(defn create-dfa
  "Takes a DFA and replaces the existing states with a set of
  integer states ranging from 0 ... n
  Preserves the structure of the given DFA.
  "
  [{:keys [start-state end-states flat-transitions]}]
  (let [; A set that holds old the states given as input
        states (set (concat [start-state]
                            (map :state flat-transitions)
                            (map :resulting-state flat-transitions)
                            end-states))

        ; A function that maps the old states to the new states
        ; by their index. This is essentially a sort of compression
        ; of the states into the range 0..N-1
        ; It makes pretty-printing nicer and makes the resulting
        ; NFA more memory efficient.
        old-id->new-id (->> (map vector states (range))
                            (apply concat)
                            (apply hash-map))

        new-start-state (old-id->new-id start-state)

        new-end-states (set (map old-id->new-id end-states))

        new-transition-table (flat-transitions->transition-table 
                               flat-transitions
                               old-id->new-id)]
    (map->Dfa
      {:start-state new-start-state
       :end-states new-end-states
       :transition-table new-transition-table})))


(defn matches?
  "Returns true if the given string fully matches the given dfa."
  [^Dfa dfa ^String string-query]
  (let [;; pre-calculate this value to avoid duplicate calculations in the main loop.
        str-size (count string-query)
        dfa-transition-table (:transition-table dfa)]
    (loop [current-state (:start-state dfa)
           index 0]
      (if (= index str-size)
        (is-end-state? dfa current-state)

        (let [input (.substring string-query index (inc index))
              next-state (-> ^"[Ljava.util.HashMap;" 
                           dfa-transition-table
                           ^HashMap 
                           (aget ^Integer current-state)
                           (.get input))] 
          (if next-state
            (recur next-state (inc index))
            false))))))
