(ns zmqrest.server
  (:require [zmqrest.daemons :as daemons]
            [zmqrest.worker :as worker])
  (:import [zmqrest.worker Worker]))

(defn -main [& args]
  (let [wrk (->
              (Worker. [])
              (worker/register-callback
                "/test"
                (fn [x] {:foobar "FOOBAR"}))
              (worker/register-callback
                "/version"
                (fn [x] {:version 1.0})))]
    (daemons/master {})
    (worker/workers wrk 5)
    (daemons/web)))
