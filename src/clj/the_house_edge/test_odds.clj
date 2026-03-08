(ns the-house-edge.test-odds
  (:require [the-house-edge.odds.core :as odds]
            [the-house-edge.slips.core :as slips]
            [clojure.pprint :refer [pprint]]))

(defn -main []
  (try
    (println "Running generate-weekend-slips...")
    (let [res (slips/generate-weekend-slips)]
      (println "Success! Result:")
      (pprint res))
    (catch Exception e
      (println "Exception caught!")
      (println (.getMessage e))
      (.printStackTrace e)))
  (System/exit 0))
