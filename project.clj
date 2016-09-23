(defproject marathon-rabbitmq-autoscale "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.novemberain/langohr "3.5.1"]
                 [marathon-client "0.1.2"]
                 [puppetlabs/typesafe-config "0.1.5"]]
  :main ^:skip-aot marathon-rabbitmq-autoscale.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
