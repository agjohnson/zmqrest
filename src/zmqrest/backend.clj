(ns zmqrest.backend
  (:import [org.jeromq ZMQ ZMQQueue ZMQ$Poller])
  (:require [taoensso.timbre :as timbre :refer [log debug]]
            [zmqrest.web :as web]
            [zmqrest.worker :as worker]))

; Master
(defprotocol MasterProtocol
  (workers [this router opts] [this router])
  (middleware [this handler opts] [this handler])
  (config [this])
  (start [this]))

(defrecord Master [config]
  MasterProtocol
  (workers [this router opts]
    (worker/workers
      router
      (merge
        {:host (-> this .config :backend)}
        opts)))
  (workers [this router] (.workers this router {}))

  (middleware [this handler opts]
    (web/middleware
      handler
      (merge
        {:host (-> this .config :frontend)}
        opts)))
  (middleware [this handler] (.middleware this handler {}))

  (config [this]
    (merge
      {:frontend "tcp://127.0.0.1:32002"
       :backend "tcp://127.0.0.1:32001"}
      (:config this)))

  (start [this]
    (let [ctx (atom (ZMQ/context 1))
          router (.socket @ctx ZMQ/ROUTER)
          dealer (.socket @ctx ZMQ/DEALER)]
      (future
        (do
          (.bind router (-> this .config :frontend))
          (.bind dealer (-> this .config :backend))
          (debug "running")
          (.run
            (ZMQQueue. @ctx router dealer))))
      (assoc
        this
        :ctx ctx
        :router (atom router)
        :dealer (atom dealer)))))

(defn master
  "Master constructor"
  ([] (Master. {}))
  ([config] (Master. config)))
