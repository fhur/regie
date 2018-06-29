(ns regie.nfas
  "Implements NFAs (non-deterministic finite automatas)
  
  States are modelled as auto-incrementing integers. To get a new state
  call the create-state function.
  
  To create a new NFA call the create-nfa function."
  (:require
    [regie.errors :as errors]
    [regie.dfas :as dfas]))


(def ^:dynamic *state-id-counter* (atom 0))


;; Models an NFA.
;; Transitions are maps of [current-state input] => #{next-state}
;; which can be interpreted as if on current-state, then given input
;; the NFA will land on any of the states in #{next-state}
(defrecord Nfa [start-state end-state transitions])


(defn create-state []
  (swap! *state-id-counter* inc))


(defn create-nfa
  "Creates a new NFA. The start state and end state should both be obtained
  by calling `create-state`"
  [start-state end-state]
  (map->Nfa
   {:start-state start-state
    :end-state end-state
    :transitions {}}))


(defn merge-transitions [^Nfa nfa-left ^Nfa nfa-right]
  (update nfa-left
          :transitions
          merge (:transitions nfa-right)))


(defn nfa? [x]
  (= Nfa (type x)))


(defn state? [x]
  ;; TODO consider something more general like adding a type to states.
  ;; on the other hand its nice to represent a state simply by a number
  (number? x))


(defn transition
  "The set of states that result from transitioning from `state` with `input`.
  If `state` doesn't accept `input` then an empty set will be returned."
  [^Nfa nfa state input]
  (get (:transitions nfa) [state input] #{}))


(defn connect
  ([^Nfa nfa start transition end]
   (cond
     (and (state? start) (state? end))
     (update-in nfa [:transitions [start transition]]
                (fn [states]
                  (conj (or states #{}) end)))

     (and (state? start) (nfa? end))
     (-> (merge-transitions nfa end)
         (connect start transition (:start-state end)))

     (and (nfa? start) (state? end))
     (-> (merge-transitions nfa start)
         (connect (:end-state start) transition end))

     (and (nfa? start) (nfa? end))
     (-> (merge-transitions nfa start)
         (merge-transitions end)
         (connect (:end-state start) transition (:start-state end)))))

  ([^Nfa nfa start transition1 middle transition2 end]
   (-> nfa
       (connect start transition1 middle)
       (connect middle transition2 end)))

  ([^Nfa nfa start transition1 middle1 transition2 middle2 transition3 end]
   (-> nfa
       (connect start transition1 middle1)
       (connect middle1 transition2 middle2)
       (connect middle2 transition3 end))))


;; ---- Regex Factory Functions ----

(defn nfa-?
  [^Nfa nfa]
  (let [start (create-state)
        end (create-state)]
    (-> (create-nfa start end)
        (connect start ::epsilon end)
        (connect start ::epsilon nfa ::epsilon end))))


(defn nfa-or
  ([^Nfa nfa-left] nfa-left)
  ([^Nfa nfa-left ^Nfa nfa-right]
   (let [start (create-state)
         end (create-state)]
     (-> (create-nfa start end)
         (connect start ::epsilon nfa-left ::epsilon end)
         (connect start ::epsilon nfa-right ::epsilon end))))
  ([nfa-left nfa-right & nfas]
   (reduce nfa-or (nfa-or nfa-left nfa-right) nfas)))


(defn nfa-cat
  ([^Nfa nfa-left] nfa-left)
  ([^Nfa nfa-left ^Nfa nfa-right]
   (let [start (create-state)
         end (create-state)]
     (-> (create-nfa start end)
         (connect start ::epsilon nfa-left ::epsilon nfa-right ::epsilon end))))
  ([nfa-left nfa-right & nfas]
   (reduce nfa-cat (nfa-cat nfa-left nfa-right) nfas)))


(defn nfa-string
  [^String string]
  (when (empty? string)
    (errors/unable-to-create-regex-from-empty-string))
  (if (= (count string) 1)
    (let [start (create-state)
          end (create-state)]
      (-> (create-nfa start end)
          (connect start string end)))
    (->> (map str string)
         (map nfa-string)
         (apply nfa-cat))))


(defn nfa-*
  [^Nfa nfa]
  (let [start (create-state)
        end (create-state)]
    (-> (create-nfa start end)
        (connect start ::epsilon end)
        (connect start ::epsilon nfa ::epsilon start))))


(defn nfa-+
  [^Nfa nfa]
  (let [start (create-state)
        end (create-state)]
    (-> (create-nfa start end)
        (connect start ::epsilon nfa ::epsilon end)
        (connect nfa ::epsilon start))))

;; ---- End Nfa Factory Functions ----

(defn bfs
  "Performs a Breadth First Search (BFS) traversal over an implicit graph
  starting at `node`. Successors are determined by the successors-fun function
  which takes a state as argument and returns a collection of successors for the
  given state.

  Example:

  ;; Traverses a NFA following all edges that accept input 'a'.
  (bfs starting-node
       (fn [node] (transition nfa node 'a')))
  "
  [node successors-fun]
  (loop [remaining-nodes [node]
         visited-nodes #{}]
    (if (empty? remaining-nodes)
      visited-nodes
      (let [[head & tail] remaining-nodes
            successors (successors-fun head)]
        (if (contains? visited-nodes head)
          (recur tail visited-nodes)
          (recur (into tail (successors-fun head))
                 (conj visited-nodes head)))))))


(defn input-set
  "If given a single argument returns all the inputs that the given NFA accepts.
  If given two arguments returns the inputs that the given state accepts."

  ([nfa]
   (->> (:transitions nfa)
        (keys)
        (map (fn [[_state input]] input))
        (filter #(not= % ::epsilon))
        (set))))

   
(defn epsilon-closure
  "The epsilon-closure of a state in an NFA is the set of all
  states reachable from state with zero or more epsilon moves.
  By definition the epsilon-closure always contains `state`."
  [nfa state]
  (bfs state
       (fn [current-state]
         (transition nfa current-state ::epsilon))))


(defn full-closure
  "Returns the set of states that follow from first transitioning
  from `state` when given `input` followed by the epsilon-closure
  over each resulting state.
  
  Example:
  If the transition from state 1 given 'a' leads to #{2,3}
  then we apply the epsilon-closure over 2 and 3 and return

  (set (concat #{2,3} 
               (epsilon-closure nfa 2) 
               (epsilon-closure nfa 3))
  "
  [nfa state input]
  (let [states (transition nfa state input)]
    (->> (mapcat #(epsilon-closure nfa %) states)
         (concat states)
         (set)))) 



(defn- nfa-states->flat-transitions
  ;; TODO add documentation
  [nfa states]
  (->> (input-set nfa)
       (map (fn [input]
              (dfas/map->FlatTransition
                {:state states
                 :input input
                 :resulting-state (->> states
                                       (mapcat #(full-closure nfa % input))
                                       (set))}))) 
       (filter (fn [flat-transition]
                 (not (empty? (:resulting-state flat-transition)))))))


(defn nfa->dfa-flat-transitions
  [nfa]
  (let [new-start-state (epsilon-closure nfa (:start-state nfa))]
    (loop [remaining-states [new-start-state]
           visited-states #{}
           result []] 
      (if (empty? remaining-states)
        result
        (let [current-state (first remaining-states)
              tail (rest remaining-states)]
          (if (contains? visited-states current-state)
            (recur tail visited-states result)
            (let [trns-map (nfa-states->flat-transitions nfa current-state)]
              (recur (into tail (map :resulting-state trns-map))
                     (conj visited-states current-state)
                     (into result trns-map))))))))) 


(defn nfa->dfa
  "Converts an NFA to a DFA"
  [^Nfa nfa]
  (let [new-transitions (nfa->dfa-flat-transitions nfa)
        new-start-state (epsilon-closure nfa (:start-state nfa))
        end-state (:end-state nfa)
        new-end-states (->> (map :resulting-state new-transitions) 
                            (cons new-start-state)
                            (filter #(contains? % end-state))
                            (set))]
    (dfas/create-dfa
      {:start-state new-start-state
       :end-states new-end-states
       :flat-transitions new-transitions})))
     
