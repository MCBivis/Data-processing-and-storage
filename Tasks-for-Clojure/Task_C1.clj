(def alphabet (list "a" "b" "c"))
(def N 3)
(defn generate-strings [alphabet N]
  (reduce
   (fn [acc _]
     (apply concat
            (map (fn [s]
                   (map (fn [l] (str s l))
                        (filter (fn [l]
                                  (not= (str (last s)) l))
                                alphabet)))
                 acc)))
   alphabet
   (range (dec N))))
(println (generate-strings alphabet N))