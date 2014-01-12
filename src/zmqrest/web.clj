(ns zmqrest.web
  (:require [ring.adapter.jetty :refer :all]
            [ring.util.response :as ring]
            [cheshire.core :as cheshire]
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
