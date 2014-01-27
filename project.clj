(defproject zmqrest "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [com.rmoquin.bundle/jeromq "0.2.0"]
                 [cheshire "5.3.0"]
                 [com.taoensso/nippy "2.5.2"]
                 [com.taoensso/timbre "3.0.0-RC4"]])
