(ns finmgmt.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [finmgmt.actor :as actor]
            [finmgmt.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Works"})
    (store/register-budget-line! st {:line-id "L-ops" :client-id "client-1"
                                     :name "operations" :amount 100000})
    st))

(deftest approved-expenditure-decrements-future-budget
  (testing "the commit node registers the spend, so a second expenditure
            exceeding the NEW remaining balance is held — the governor
            recomputes from the ledger, proving state, not memory"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          first-req {:client-id "client-1" :op :approve-expenditure :stake :medium
                     :line-id "L-ops" :amount 80000}
          interrupted (actor/run-request! graph first-req {} "thread-1")]
      (is (= :interrupted (:status interrupted)))
      (let [resumed (actor/approve! graph "thread-1")]
        (is (= :done (:status resumed)))
        (is (= 1 (count (store/spends-of st "L-ops")))))
      ;; 20000 remains; 30000 must now be a HARD overrun
      (let [second-req {:client-id "client-1" :op :approve-expenditure :stake :medium
                        :line-id "L-ops" :amount 30000}
            result (actor/run-request! graph second-req {} "thread-2")]
        (is (= :hold (:disposition (:state result))))
        (is (= 1 (count (store/spends-of st "L-ops"))))))))

(deftest holds-an-overrun-without-recording-spend
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-expenditure :stake :medium
                 :line-id "L-ops" :amount 100001}
        result (actor/run-request! graph request {} "thread-3")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/spends-of st "L-ops")))))

(deftest commits-a-draft-budget
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :draft-budget :stake :low}
        result (actor/run-request! graph request {} "thread-4")]
    (is (= :done (:status result)))
    (is (= 1 (count (store/records-of st "client-1"))))))
