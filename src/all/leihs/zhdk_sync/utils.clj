(ns leihs.zhdk-sync.utils
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.utils.core :refer [presence str keyword]]
    ))

(defn deviates-by-more-than-tenth? [n1 n2]
  (let [[smaller larger] (if (<= n1 n2)
                           [n1 n2]
                           [n2 n1])
        part (float (/ (- larger smaller) larger))]
    (< 0.1 part)))

