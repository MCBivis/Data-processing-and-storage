(ns Task-C5)

(defn run-simulation
  [{:keys [n-philosophers think-ms eat-ms cycles symmetric?]
    :or   {think-ms 1
           eat-ms   1
           cycles   5000
           symmetric? false}}]

  (println "\n=== Simulation start ===")
  (println "Philosophers:" n-philosophers
           "think-ms:" think-ms "eat-ms:" eat-ms
           "cycles:" cycles
           "symmetric?" symmetric?)

  (let [forks         (vec (repeatedly n-philosophers #(ref {:owner nil :count 0})))
        retry-counter (atom 0)]

    (letfn [(counted-dosync
      [f]
      (dosync
        (swap! retry-counter inc)
        (f)))

    (left-fork  [i] i)
    (right-fork [i] (mod (inc i) n-philosophers))

    ;; симметричный/асимметричный порядок попытки захвата
    (fork-order [i]
      (if (or symmetric? (even? i))
        [(left-fork i) (right-fork i)]
        [(right-fork i) (left-fork i)]))

    ;; попытка захватить обе вилки
    (try-acquire [i f1 f2]
      (counted-dosync
        (fn []
          (let [fork1 @f1
                fork2 @f2]
            (when (and (nil? (:owner fork1))
                       (nil? (:owner fork2)))
              (alter f1 assoc :owner i)
              (alter f2 assoc :owner i)
              true)))))

    ;; освободить вилки
    (release-forks [i f1 f2]
      (counted-dosync
        (fn []
          ;; увеличиваем счётчик использования при освобождении
          (alter f1 (fn [m]
                      (-> m
                          (assoc :owner nil)
                          (update :count inc))))
          (alter f2 (fn [m]
                      (-> m
                          (assoc :owner nil)
                          (update :count inc)))))))

    ;; поток философа
    (philosopher [i]
      (future
        (dotimes [_ cycles]
          (Thread/sleep think-ms)
          (let [[a b] (fork-order i)
                f1 (forks a)
                f2 (forks b)]

            ;; крутимся пока не получится захватить обе вилки
            (loop []
              (when-not (try-acquire i f1 f2)
                (Thread/sleep think-ms)
                (recur)))

            ;; едим – вилки удерживаются
            (Thread/sleep eat-ms)

            ;; освобождаем
            (release-forks i f1 f2)))))]

      (let [start (System/nanoTime)
            tasks (doall (map philosopher (range n-philosophers)))]
        (doseq [t tasks] @t)
        (let [elapsed-ms (/ (- (System/nanoTime) start) 1e6)
          usage      (mapv (fn [r] (:count @r)) forks)
          result     {:elapsed-ms elapsed-ms
                      :dosync-attempts @retry-counter
                      :fork-usage usage}]

          ;; print results
          (println "--- Results ---")
          (println "Elapsed:" (:elapsed-ms result) "ms")
          (println "Dosync attempts:" (:dosync-attempts result))
          (println "Fork usage:" (:fork-usage result))
          (println "=== Simulation end ===")

          ;; also return result
          result)))))

;; 1) Odd number + asymmetric order
(run-simulation {:n-philosophers 5
                 :symmetric? false})

;; 2) Even number + symmetric
(run-simulation {:n-philosophers 6
                 :symmetric? true})
