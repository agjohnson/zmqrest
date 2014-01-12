(ns zmqrest.daemons
  (:import [org.jeromq ZMQ ZMQQueue ZMQ$Poller])
  (:require [ring.adapter.jetty :refer :all]
            [cheshire.core :as cheshire]
            [taoensso.nippy :as nippy]
            [taoensso.timbre :as timbre :refer [log debug]]
            [zmqrest.web :refer :all])
  (:import [zmqrest.web Request Response]))

; Master
(defn master
  "Create the master routing server"
  [config]
  (let [defaults {:listen "tcp://127.0.0.1:32001"
                  :channel "zmqrest"
                  :serializer :nippy}
        config (merge defaults config)
        ctx (ZMQ/context 1)
        router (.socket ctx ZMQ/ROUTER)
        dealer (.socket ctx ZMQ/DEALER)]
    (future
      (do
        (.bind router "tcp://127.0.0.1:32002")
        (.bind dealer (get config :listen))
        (debug "master running")
        (.run
          (ZMQQueue. ctx router dealer)))
      (atom {:ctx ctx
             :router router
             :dealer dealer}))))

; Web server
; TODO config param for timeout
(defn web []
  (let [ctx (ZMQ/context 1)
        sck (.socket ctx ZMQ/REQ)]
    (debug "client connect")
    (.connect sck "tcp://127.0.0.1:32002")
    (run-jetty
      (fn [req]
        (let [zmqreq (http-to-zmq-req req)
              poller (.poller ctx 1)]
          (debug "send")
          (.send sck (.toZMQ zmqreq))
          (.register poller sck ZMQ$Poller/POLLIN)
          (debug "polling")
          (.poll poller (* 5 1000))
          (debug "done polling")
          (try
            (if (.pollin poller 0)
              ; Got response, handle and return HTTP response
              (do
                (debug "recv")
                (.toHTTP (response (.recv sck 0))))
              ; No response, throw error
              (throw (Exception. "No response")))
            (catch Exception e
              (.toHTTP
                (response {:error 500
                           :msg (.getMessage e)}))))))
      {:port 8080})))
