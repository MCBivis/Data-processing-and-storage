(ns Task_C3
  (:require [clojure.test :refer :all]))

(defn pfilter
  ([pred coll] (pfilter pred coll {:chunk-size 100 :prefetch 4}))
  ([pred coll {:keys [chunk-size prefetch]
               :or   {chunk-size 100 prefetch 4}}]
   (let [chunks         (partition-all chunk-size coll)
         futures-seq    (map (fn [chunk] (future (doall (filter pred chunk)))) chunks)]
     (doall (take prefetch futures-seq))
     (letfn [(emit [fs]
               (lazy-seq
                (when-let [fs (seq fs)]
                  (let [f (first fs)
                        rest-fs (rest fs)]
                    (concat (deref f) (emit rest-fs))))))]
       (emit futures-seq)))))

(deftest test-pfilter
  (testing "Конечная последовательность"
    (is (= (filter even? (range 20))
           (pfilter even? (range 20) {:chunk-size 5 :prefetch 3}))))
  (testing "Бесконечная последовательность"
    (is (= (take 5 (filter even? (iterate inc 1)))
           (take 5 (pfilter even? (iterate inc 1) {:chunk-size 10 :prefetch 3})))))
  (testing "Отсутствие параметров"
    (is (= (take 5 (filter even? (iterate inc 1)))
           (take 5 (pfilter even? (iterate inc 1))))))
  (testing "Пустая последовательность"
    (is (= () (pfilter even? '())))))

(run-tests)

(defn heavy-even? [x]
  (Thread/sleep 10)
  (zero? (mod x 2)))

(println "\nОбычный filter:")
(time (doall (filter heavy-even? (range 20))))

(println "\nПараллельный pfilter:")
(time (doall (pfilter heavy-even? (range 20) {:chunk-size 10 :prefetch 2})))