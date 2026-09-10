(ns finmgmt.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [finmgmt.store :as store]
            [finmgmt.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Works"})
    (store/register-budget-line! st {:line-id "L-ops" :client-id "client-1"
                                     :name "operations" :amount 100000})
    (store/register-spend! st {:spend-id "s-1" :client-id "client-1"
                               :line-id "L-ops" :amount 30000})
    st))
;; remaining on L-ops: 100000 - 30000 = 70000

(defn- expenditure [amount]
  {:op :approve-expenditure :effect :propose :line-id "L-ops" :amount amount
   :confidence 0.9 :stake :medium})

(def ^:private req {:client-id "client-1"})

(deftest within-budget-escalates-only
  (testing "70000 remains; 50000 fits — no HARD, but money always needs a human"
    (let [st (fresh-store)
          v (governor/check req {} (expenditure 50000) st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest exactly-remaining-is-within-budget
  (let [st (fresh-store)
        v (governor/check req {} (expenditure 70000) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest hard-on-budget-overrun
  (testing "a balance is a ledger sum, not a memory — overruns are held
            at any confidence"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (expenditure 70001)
                                          :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :budget-overrun (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (expenditure 100) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (expenditure 100)
                                        :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-invented-budget-line
  (let [st (fresh-store)
        v (governor/check req {} (assoc (expenditure 100) :line-id "L-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-budget-line (:rule %)) (:violations v)))))

(deftest hard-on-foreign-budget-line
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (expenditure 100) st)]
      (is (:hard? v))
      (is (some #(= :budget-line-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-invalid-amount
  (let [st (fresh-store)]
    (doseq [bad [0 -5 nil "100"]]
      (let [v (governor/check req {} (assoc (expenditure 100) :amount bad) st)]
        (is (:hard? v))
        (is (some #(= :invalid-amount (:rule %)) (:violations v)))))))

(deftest draft-budget-is-ok
  (let [st (fresh-store)
        v (governor/check req {} {:op :draft-budget :effect :propose
                                  :confidence 0.9 :stake :low} st)]
    (is (:ok? v))))

(deftest escalates-reallocation
  (let [st (fresh-store)
        v (governor/check req {} {:op :reallocate-budget :effect :propose
                                  :confidence 0.9 :stake :medium} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} {:op :draft-budget :effect :propose
                                  :confidence 0.3 :stake :low} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
