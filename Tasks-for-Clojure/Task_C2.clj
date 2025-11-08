(ns Task_C2
  (:require [clojure.test :refer :all]))

(defn sieve [s]
  (lazy-seq
   (cons (first s)
         (sieve (filter #(not= 0 (mod % (first s)))
                        (rest s))))))
(def primes (sieve (iterate inc 2)))
(println (take 10 primes))

(deftest test-sieve
  (testing "Первые простые числа"
    (is (= [2 3 5 7 11 13]
           (take 6 primes))))
  (testing "10-е простое число"
    (is (= 29 (nth primes 9)))))
(run-tests)