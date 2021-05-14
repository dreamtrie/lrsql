(ns lrsql.lrs-test
  (:require [clojure.test :refer [deftest testing is]]
            [config.core  :refer [env]]
            [next.jdbc    :as jdbc]
            [clojure.data.json :as json]
            [com.stuartsierra.component    :as component]
            [com.yetanalytics.lrs.protocol :as lrsp]
            [lrsql.system :as system]))

(def stmt-1
  {"id"     "030e001f-b32a-4361-b701-039a3d9fceb1"
   "actor"  {"mbox"       "mailto:sample.agent@example.com"
             "name"       "Sample Agent"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/answered"
             "display" {"en-US" "answered"}}
   "object" {"id"         "http://www.example.com/tincan/activities/multipart"
             "objectType" "Activity"
             "definition" {"name"        {"en-US" "Multi Part Activity"}
                           "description" {"en-US" "Multi Part Activity Description"}}}})

(def stmt-2
  {"id"     "3f5f3c7d-c786-4496-a3a8-6d6e4461aa9d"
   "actor"  {"account"    {"name"     "Sample Agent 2"
                           "homepage" "https://example.org"}
             "name"       "Sample Agent 2"
             "objectType" "Agent"}
   "verb"   {"id"      "http://adlnet.gov/expapi/verbs/voided"
             "display" "Voided"}
   "object" {"objectType" "StatementRef"
             "id"         "030e001f-b32a-4361-b701-039a3d9fceb1"}})

(def stmt-3
  (-> stmt-1
      (assoc "id" "708b3377-2fa0-4b96-9ff1-b10208b599b1")
      (assoc "actor" {"openid"     "https://example.org"
                      "name"       "Sample Agent 3"
                      "objectType" "Agent"})
      (assoc-in ["context" "instructor"] (get stmt-1 "actor"))
      (assoc-in ["object" "id"] "http://www.example.com/tincan/activities/multipart-2")
      (assoc-in ["context" "contextActivities" "other"] [(get stmt-1 "object")])))

(defn- assert-in-mem-db
  []
  (when (not= "h2:mem" (:db-type env))
    (throw (ex-info "Test can only be run on in-memory H2 database!"
                    {:kind    ::non-mem-db
                     :db-type (:db-type env)}))))

(defn- drop-all!
  "Drop all tables in the db, in preparation for adding them again.
   DO NOT RUN THIS DURING PRODUCTION!!!"
  [tx]
  (doseq [cmd ["DROP TABLE IF EXISTS xapi_statement"
               "DROP TABLE IF EXISTS agent"
               "DROP TABLE IF EXISTS activity"
               "DROP TABLE IF EXISTS attachment"
               "DROP TABLE IF EXISTS statement_to_agent"
               "DROP TABLE IF EXISTS statement_to_activity"
               "DROP TABLE IF EXISTS statement_to_attachment"]]
    (jdbc/execute! tx [cmd])))

(defn- remove-props
  "Remove properties added by `input/prepare-statement`."
  [statement]
  (-> statement
      (dissoc "timestamp")
      (dissoc "stored")
      (dissoc "authority")
      (dissoc "version")))

(defn- remove-props-res
  "Apply `remove-props` to a StatementResult object."
  [query-res]
  (update query-res :statements (partial map remove-props)))

(deftest test-lrs-protocol-fns
  (let [_     (assert-in-mem-db)
        sys   (system/system)
        sys'  (component/start sys)
        lrs   (:lrs sys')
        id-1  (get stmt-1 "id")
        id-2  (get stmt-2 "id")
        id-3  (get stmt-3 "id")
        ts    "3000-01-01T01:00:00Z" ; Date far into the future
        agt-1 (-> stmt-1 (get "actor") (json/write-str))
        vrb-1 (get-in stmt-1 ["verb" "id"])
        act-1 (get-in stmt-1 ["object" "id"])]
    (testing "Statement insertions"
      (is (= [id-1] (lrsp/-store-statements lrs {} [stmt-1] [])))
      (is (= [id-2 id-3] (lrsp/-store-statements lrs {} [stmt-2 stmt-3] []))))
    (testing "Statement ID queries"
      ;; Statement ID queries
      (is (= stmt-1
             (remove-props
              (lrsp/-get-statements lrs {} {:voidedStatementId id-1} {}))))
      (is (= stmt-2
             (remove-props
              (lrsp/-get-statements lrs {} {:statementId id-2} {}))))
      (is (= stmt-3
             (remove-props
              (lrsp/-get-statements lrs {} {:statementId id-3} {})))))
    (testing "Statement Property Queries"
      (is (= {:statements [] :more ""}
             (lrsp/-get-statements lrs {} {:since ts} {})))
      (is (= {:statements [stmt-1 stmt-2 stmt-3] :more ""}
             (remove-props-res
              (lrsp/-get-statements lrs {} {:until ts} {}))))
      (is (= {:statements [stmt-1] :more ""}
             (remove-props-res
              (lrsp/-get-statements lrs {} {:agent agt-1} {}))))
      (is (= {:statements [stmt-1 stmt-3] :more ""}
             (remove-props-res
              (lrsp/-get-statements lrs {} {:agent agt-1 :related_agents true} {}))))
      (is (= {:statements [stmt-1 stmt-3] :more ""}
             (remove-props-res
              (lrsp/-get-statements lrs {} {:verb vrb-1} {}))))
      (is (= {:statements [stmt-1] :more ""}
             (remove-props-res
              (lrsp/-get-statements lrs {} {:activity act-1} {}))))
      (is (= {:statements [stmt-1 stmt-3] :more ""}
             (remove-props-res
              (lrsp/-get-statements lrs {} {:activity act-1 :related_activities true} {})))))
    (jdbc/with-transaction [tx ((:conn-pool lrs))]
      (drop-all! tx))
    (component/stop sys')))