(ns the-house-edge.run-sim
  (:require [the-house-edge.performance.core :as perf]
            [clojure.pprint :as pprint]))

(defn -main [& args]
  (println "==================================================")
  (println "Running baseline simulation (Consensus Only)...")
  (let [baseline-result (perf/run-baseline-simulation!)]
    (pprint/pprint baseline-result)
    
    (println "\n==================================================")
    (println "Running Poisson Simulation (Blended Probs)...")
    (let [poisson-result (perf/run-poisson-simulation!)]
      (pprint/pprint poisson-result))
    
    (println "Done."))
  (System/exit 0))
