(ns zmqrest.web
  (:import [org.jeromq ZMQ ZMQQueue ZMQ$Poller])
  (:require [ring.adapter.jetty :refer :all]
            [ring.util.response :as ring]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as timbre :refer [log debug]]
            [taoensso.nippy :as nippy]))

; Request and request utility functions
(defprotocol RequestProtocol
  (create? [this])
  (replace? [this])
  (update? [this])
  (delete? [this])
  (toZMQ [this]))

(defrecord Request [data]
  RequestProtocol
  (create? [this]
    (= (keyword (-> this :data :action)) :create))
  (replace? [this]
    (= (keyword (-> this :data :action)) :replace))
  (update? [this]
    (= (keyword (-> this :data :action)) :update))
  (delete? [this]
    (= (keyword (-> this :data :action)) :delete))
  (toZMQ [this]
    (nippy/freeze (:data this))))

(defn request-type [data]
  (cond
    (instance? (type (byte-array [])) data) :byte
    (string? data) :string
    (map? data) :map
    :else nil))

(defmulti request request-type)
(defmethod request nil [] (Request. nil))
(defmethod request :byte [data]
  (Request. (nippy/thaw data)))
(defmethod request :string [data]
  (Request. (nippy/thaw (.getBytes data))))
(defmethod request :map [data]
  (Request. data))

; TODO expand this list
(defn http-to-zmq-req [req]
  (request
    {:action (let [action (get req :request-method)]
               (cond
                 (string? action) (keyword (.toLowerCase action))
                 (keyword? action) action
                 :else :get))
     :func (get req :uri)
     :cid (str (java.util.UUID/randomUUID))}))

; Response and response utility functions
(defprotocol ResponseProtocol
  (normalize [this])
  (toHTTP [this])
  (toZMQ [this]))

(defrecord Response [data]
  ResponseProtocol
  ; Normailze data
  (normalize [this]
    (let [status (if-let [error (-> this :data :error)]
                   error
                   200)]
      (merge (:data this)
             {:status status})))
  ; Convert to JSON HTTP response
  (toHTTP [this]
    (let [normalized (.normalize this)]
      (-> (ring/response
            (cheshire/generate-string normalized))
          (ring/status (:status normalized))
          (ring/content-type "text/javascript"))))
  ; Convert to ZMQ response
  (toZMQ [this]
    (nippy/freeze (:data this))))

(defmulti response type)
(defmethod response (type (byte-array []))
  [data]
  (Response. (nippy/thaw data)))
(defmethod response (type (hash-map))
  [data]
  (Response. data))

; Middleware creation
(defn- match-path
  "Test for matching path to request URI"
  [req path]
  (let [path (re-pattern path)]
    (re-matches path (:uri req))))

(defn middleware
  "ZMQREST middleware for ring applications"
  [handler & [config]]
  (let [ctx (atom (ZMQ/context 1))
        config (merge
                 {:host "tcp://127.0.0.1:32002"
                  :timeout 5000
                  :path #"^/api/.*"}
                 config)]
    (fn [req]
      (if (match-path req (:path config))
        ; Start request
        (do
          (let [zmqreq (http-to-zmq-req req)
                sck (.socket @ctx ZMQ/REQ)
                poller (.poller @ctx 1)]
            (debug "connecting to " (:host config))
            (.connect sck (:host config))
            (debug (str "sending from client " zmqreq))
            (.send sck (.toZMQ zmqreq))
            (.register poller sck ZMQ$Poller/POLLIN)
            (debug "polling")
            (.poll poller (:timeout config))
            (debug "done polling")
            (try
              (if (.pollin poller 0)
                ; Received poll response, convert to HTTP response
                (do
                  (debug "receive response")
                  (.toHTTP (response (.recv sck 0))))
                ; Polling failed, return error
                (throw (Exception. "No response")))
              (catch Exception e
                (.toHTTP
                  (response {:error 500
                             :msg (.getMessage e)}))))))
        ; Didn't match path, pass this on to normal handler
        (handler req)))))
