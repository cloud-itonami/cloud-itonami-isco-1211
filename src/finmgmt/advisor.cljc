(ns finmgmt.advisor
  "FinanceManagementAdvisor — proposes a finance-management operation
  (draft a budget, approve an expenditure, reallocate) for a registered
  organization. Swappable mock/llm; the advisor ONLY proposes —
  `finmgmt.governor` recomputes the remaining budget from the spend
  ledger independently. Modeled on cloud-itonami-isco-4311's advisor.

  A proposal: {:op :draft-budget|:approve-expenditure|:reallocate-budget
               :effect :propose :line-id str :amount int
               :stake kw :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake line-id amount] :as request}]
  {:op op
   :effect :propose
   :line-id line-id
   :amount amount
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a finance management advisor. Given a request, propose an
   :op, the :line-id and :amount, an honest :confidence and a :stake.
   Never claim budget remains — the governor recomputes the ledger sum.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
