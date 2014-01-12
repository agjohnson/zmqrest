(ns zmqrest.web-test
  (:require [clojure.test :refer :all]
            [zmqrest.web :refer :all])
  (:import [zmqrest.web Request Response]))

; Test create request
(deftest create-req
  (testing "Create request"
    (let [req (request {:action :create})]
      (is (instance? Request req) "Is instance")
      (is (not (nil? (-> req :data))))
      (is (.create? req) "Is create")
      (is (not (.update? req)) "Isn't update"))))

(deftest request-type-return
  (testing "Request constructor"
    (are [a b] (= (request-type a) b)
         (.getBytes "foobar") :byte
         (str "foobar") :string
         {:action :create, :func "test"} :map
         nil nil)))

(deftest request-return
  (testing "Request method return"
    (let [req (request {:action :create})]
      (are [a] (instance? Request a)
           (request {:action :create})
           (request (.toZMQ req))))))

(deftest normalize-req
  (testing "Normalize request"
    (let [fix {:action :get
               :func "/foo/bar/12345"
               :cid 1}]
      (are [a] (= (-> (http-to-zmq-req a) :data :action) (:action fix))
           {:request-method "GET"}
           {:request-method :get}
           {:request-method "get"}
           {:request-method nil}))))

; Test responses
(deftest create-resp
  (testing "Create response"
    (let [resp (response {:foo "foo"
                          :bar "bar"})]
      (is (instance? Response resp) "Is instance")
      (is (not (nil? (-> resp :data))))
      (are [a b] (= b (a (.normalize resp)))
           :status 200))))

(deftest response-return
  (testing "Response constructor"
    (let [resp (response {:foo "foo"})]
      (are [a] (instance? Response a)
           (response {:bar "bar"})
           (response (.toZMQ resp))))))

(deftest normalize-resp
  (testing "Normalize response"
    (let [fix {:foo "foo"
               :status 200}]
      (are [a] (= (:status (.normalize (response a))) (:status fix))
           {:foo "foobar"}
           {:foo "foobar", :status 500}
           {:foo "foobar", :status "foobar"})))
  (testing "Normalize response error"
    (let [fix {:foo "foo"
               :status 500}]
      (are [a] (= (:status (.normalize (response a))) (:status fix))
           {:foo "foobar", :error 500}
           {:foo "foobar", :status 200, :error 500}))))
