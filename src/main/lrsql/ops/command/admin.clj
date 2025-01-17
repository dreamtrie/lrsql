(ns lrsql.ops.command.admin
  (:require [clojure.spec.alpha :as s]
            [lrsql.backend.protocol :as bp]
            [lrsql.spec.common :refer [transaction?]]
            [lrsql.spec.admin :as ads]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Admin Account Insertion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef insert-admin!
  :args (s/cat :bk ads/admin-backend?
               :tx transaction?
               :input ads/insert-admin-input-spec)
  :ret ads/insert-admin-ret-spec)

(defn insert-admin!
  "Insert a new admin username, hashed password, and the hash salt into the
   `admin_account` table. Returns a map with `:result` either being the
   account ID on success or an error keyword on failure."
  [bk tx input]
  (if-not (bp/-query-account-exists bk tx (select-keys input [:username]))
    (do
      (bp/-insert-admin-account! bk tx input)
      {:result (:primary-key input)})
    {:result :lrsql.admin/existing-account-error}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Admin Account Deletion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/fdef delete-admin!
  :args (s/cat :bk ads/admin-backend?
               :tx transaction?
               :input ads/admin-id-input-spec)
  :ret ads/delete-admin-ret-spec)

(defn delete-admin!
  "Delete the admin account and any associated credentials. Returns a map
   where `:result` is the account ID."
  [bk tx input]
  (if (bp/-query-account-exists bk tx input)
    (do
      (bp/-delete-admin-account! bk tx input)
      {:result (:account-id input)})
    {:result :lrsql.admin/missing-account-error}))
