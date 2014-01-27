(ns zmqrest.worker-test
  (:require [clojure.test :refer :all]
            [zmqrest.worker :as worker]))

(deftest basic-router-create
  (testing "Create basic router and register callback on router"
    (let [router (-> (worker/router)
                     (worker/add-route
                       #"test"
                       (fn [req] {:resp "Test"
                                  :foobar (:foobar req)})))]
      (is (not (nil? router)))
      (are [k v req]
           (= v (-> (.normalize (worker/dispatch router req)) k))
           :resp "Test" {:func "test" :action :get}
           :status 404 {:func "test" :action :post}
           :resp "Test" {:func "test"}
           :status 200 {:func "test"}
           :error 404 {:func nil}
           :status 404 {:func nil}
           :foobar "FOOBAR" {:func "test" :foobar "FOOBAR"}))))

(deftest advanced-router-create
  (testing "Create route with explicit action"
    (let [router (-> (worker/router)
                     (worker/add-route
                       #"test"
                       :get (fn [req] {:resp "GET"
                                       :foobar (:foobar req)})))]
      (is (not (nil? router)))
      (are [k v req]
           (= v (-> (.normalize (worker/dispatch router req)) k))
           :resp "GET" {:func "test" :action :get}
           :status 404 {:func "test" :action :post}
           :status 404 {:func "test" :action :put}
           :status 404 {:func "test" :action :delete}
           :foobar "FOOBAR" {:func "test" :foobar "FOOBAR" :action :get}
           :foobar nil {:func "test" :foobar "FOOBAR" :action :post}
           :foobar nil {:func "test" :foobar "FOOBAR" :action :put}
           :foobar nil {:func "test" :foobar "FOOBAR" :action :delete}))))

(deftest router-create-multiple
  (testing "Create route with multiple registered routes"
    (let [router (-> (worker/router)
                     (worker/add-route
                       #"test"
                       :get (fn [req] {:resp "GET"
                                       :foobar (:foobar req)})
                       :post (fn [req] {:resp "POST"
                                        :foobar (:foobar req)})
                       :put (fn [req] {:resp "PUT"
                                       :foobar (:foobar req)})
                       :delete (fn [req] {:resp "DELETE"
                                          :foobar (:foobar req)})))]
      (is (not (nil? router)))
      (are [k v req]
           (= v (-> (.normalize (worker/dispatch router req)) k))
           :resp "GET" {:func "test" :action :get}
           :resp "POST" {:func "test" :action :post}
           :resp "PUT" {:func "test" :action :put}
           :resp "DELETE" {:func "test" :action :delete}
           :foobar "FOOBAR" {:func "test" :foobar "FOOBAR" :action :get}
           :foobar "FOOBAR" {:func "test" :foobar "FOOBAR" :action :post}
           :foobar "FOOBAR" {:func "test" :foobar "FOOBAR" :action :put}
           :foobar "FOOBAR" {:func "test" :foobar "FOOBAR" :action :delete}))))

(deftest worker-create
  (testing "Create worker object"
    (let [router (worker/router)
          wrk (worker/workers router)
          wrk-custom (worker/workers router {:host "tcp://127.0.0.1:33001"
                                             :timeout 10000})]
      (are [a b] (= b (a (.config wrk)))
           :host "tcp://127.0.0.1:32001"
           :timeout 5000)
      (are [a b] (= b (a (.config wrk-custom)))
           :host "tcp://127.0.0.1:33001"
           :timeout 10000))))
