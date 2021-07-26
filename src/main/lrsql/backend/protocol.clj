(ns lrsql.backend.protocol
  "Protocols that serve as a low-level backend for DB functions.")

(defprotocol BackendInit
  (-create-all! [this tx]
    "Create all tables and indexes."))

(defprotocol BackendIOSetter
  (-set-read! [this]
    "Set data conversion behavior when reading from the backend.")
  (-set-write! [this]
    "Set data conversion behavior when writing from the backend."))

(defprotocol StatementBackend
  ;; Commands
  (-insert-statement! [this tx input])
  (-insert-statement-to-statement! [this tx input])
  (-void-statement! [this tx input])
  ;; Queries
  (-query-statement [this tx input])
  (-query-statements [this tx input])
  (-query-statement-exists [this tx input])
  (-query-statement-descendants [this tx input]))

(defprotocol ActorBackend
  ;; Commands
  (-insert-actor! [this tx input])
  (-insert-statement-to-actor! [this tx input])
  (-update-actor! [this tx input])
  ;; Queries
  (-query-actor [this tx input]))

(defprotocol ActivityBackend
  ;; Commands
  (-insert-activity! [this tx input])
  (-insert-statement-to-activity! [this tx input])
  (-update-activity! [this tx input])
  ;; Queries
  (-query-activity [this tx input]))

(defprotocol AttachmentBackend
  ;; Commands
  (-insert-attachment! [this tx input])
  ;; Queries
  (-query-attachments [this tx input]))

(defprotocol StateDocumentBackend
  ;; Commands
  (-insert-state-document! [this tx input])
  (-update-state-document! [this tx input])
  (-delete-state-document! [this tx input])
  (-delete-state-documents! [this tx input])
  ;; Queries
  (-query-state-document [this tx input])
  (-query-state-document-ids [this tx input]))

(defprotocol AgentProfileDocumentBackend
  ;; Commands
  (-insert-agent-profile-document! [this tx input])
  (-update-agent-profile-document! [this tx input])
  (-delete-agent-profile-document! [this tx input])
  ;; Queries
  (-query-agent-profile-document [this tx input])
  (-query-agent-profile-document-ids [this tx input]))

(defprotocol ActivityProfileDocumentBackend
  ;; Commands
  (-insert-activity-profile-document! [this tx input])
  (-update-activity-profile-document! [this tx input])
  (-delete-activity-profile-document! [this tx input])
  ;; Queries
  (-query-activity-profile-document [this tx input])
  (-query-activity-profile-document-ids [this tx input]))

(defprotocol AdminAccountBackend
  ;; Commands
  (-insert-admin-account! [this tx input])
  (-delete-admin-account! [this tx input])
  ;; Queries
  (-query-account [this tx input])
  (-query-account-exists [this tx input]))

(defprotocol CredentialBackend
  ;; Commands
  (-insert-credential! [this tx input])
  (-insert-credential-scope! [this tx input])
  (-delete-credential! [this tx input])
  (-delete-credential-scope! [this tx input])
  ;; Queries
  (-query-credentials [this tx input])
  (-query-credential-exists [this tx input])
  (-query-credential-scopes [this tx input]))