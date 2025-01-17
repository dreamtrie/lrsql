/* Pragmas */

/* We maintain this as a snippet so that we have access to the SQL string
   (as opposed to a HugSql function). */

-- :snip ensure-foreign-keys-snip
PRAGMA foreign_keys = ON;

/* Statement + Attachment Tables */

-- :name create-statement-table!
-- :command :execute
-- :doc Create the `statement` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS xapi_statement (
  id           TEXT NOT NULL PRIMARY KEY ASC,  -- uuid
  statement_id TEXT NOT NULL UNIQUE,           -- uuid
  registration TEXT,                           -- uuid
  verb_iri     TEXT NOT NULL,                  -- iri string
  is_voided    INTEGER DEFAULT FALSE NOT NULL, -- boolean
  payload      BLOB NOT NULL                   -- json
)

-- :name create-desc-id-index!
-- :command :execute
-- :doc Create a second, descending index on the statement primary key.
CREATE INDEX IF NOT EXISTS desc_id_idx ON xapi_statement(id DESC)

-- :name create-verb-iri-index!
-- :command :execute
-- :doc Create an index on the `xapi_statement.verb_iri` column.
CREATE INDEX IF NOT EXISTS verb_iri_idx ON xapi_statement(verb_iri)

-- :name create-registration-index!
-- :command :execute
-- :doc Create an index on the `xapi_statement.registration` column.
CREATE INDEX IF NOT EXISTS registration_idx ON xapi_statement(registration)

-- :name create-actor-table!
-- :command :execute
-- :doc Create the `actor` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS actor (
  id         TEXT NOT NULL PRIMARY KEY, -- uuid
  actor_ifi  TEXT NOT NULL,             -- ifi string
  actor_type TEXT CHECK (
               actor_type IN ('Agent', 'Group')
             ) NOT NULL,                -- enum
  payload    BLOB NOT NULL,             -- json
  UNIQUE (actor_ifi, actor_type)
)

-- :name create-activity-table!
-- :command :execute
-- :doc Create the `activity` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS activity (
  id           TEXT NOT NULL PRIMARY KEY, -- uuid
  activity_iri TEXT NOT NULL UNIQUE,      -- iri string
  payload      BLOB NOT NULL              -- json
)

-- :name create-attachment-table!
-- :command :execute
-- :doc Create the `attachment` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS attachment (
  id             TEXT NOT NULL PRIMARY KEY, -- uuid
  statement_id   TEXT NOT NULL,             -- uuid
  attachment_sha TEXT NOT NULL,             -- sha2 string
  content_type   TEXT NOT NULL,             -- string
  content_length INTEGER NOT NULL,          -- integer
  contents       BLOB NOT NULL,             -- binary data
  FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id)
)

-- :name create-attachment-statement-id-index!
-- :command :execute
-- :doc Create an index on the `attachment.statement_id` column.
CREATE INDEX IF NOT EXISTS attachment_statement_id_idx ON attachment(statement_id)

-- :name create-statement-to-actor-table!
-- :command :execute
-- :doc Create the `statement_to_actor` link table if it doesn't exist yet.
CREATE TABLE IF NOT EXISTS statement_to_actor (
  id           TEXT NOT NULL PRIMARY KEY, -- uuid
  statement_id TEXT NOT NULL,             -- uuid
  usage        TEXT CHECK (
                 usage IN ('Actor', 'Object', 'Authority', 'Instructor', 'Team',
                           'SubActor', 'SubObject', 'SubInstructor', 'SubTeam')
               ) NOT NULL,                -- enum
  actor_ifi    TEXT NOT NULL,             -- ifi string
  actor_type   TEXT CHECK (
                 actor_type IN ('Agent', 'Group')
               ) NOT NULL,                -- enum
  FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id),
  FOREIGN KEY (actor_ifi, actor_type) REFERENCES actor(actor_ifi, actor_type)
)

-- :name create-statement-actor-statement-id-index!
-- :command :execute
-- :doc Create an index on the `statement_to_actor.statement_id` column.
CREATE INDEX IF NOT EXISTS statement_actor_statement_id_idx ON statement_to_actor(statement_id)

-- :name create-statement-actor-ifi-index!
-- :command :execute
-- :doc Create an index on the `statement_to_actor.actor_ifi` column.
CREATE INDEX IF NOT EXISTS statement_actor_ifi_idx ON statement_to_actor(actor_ifi)

-- :name create-statement-to-activity-table!
-- :command :execute
-- :doc Create the `statement_to_activity` link table if it doesn't exist yet.
CREATE TABLE IF NOT EXISTS statement_to_activity (
  id           TEXT NOT NULL PRIMARY KEY, -- uuid
  statement_id TEXT NOT NULL,             -- uuid
  usage        TEXT CHECK (
                 usage IN ('Object', 'Category', 'Grouping', 'Parent', 'Other',
                           'SubObject', 'SubCategory', 'SubGrouping', 'SubParent', 'SubOther')
               ) NOT NULL,                -- enum
  activity_iri TEXT NOT NULL,             -- iri string
  FOREIGN KEY (statement_id) REFERENCES xapi_statement(statement_id),
  FOREIGN KEY (activity_iri) REFERENCES activity(activity_iri)
)

-- :name create-statement-activity-statement-id-index!
-- :command :execute
-- :doc Create an index on the `statement_to_activity.statement_id` column.
CREATE INDEX IF NOT EXISTS statement_activity_statement_id_idx ON statement_to_activity(statement_id)

-- :name create-statement-activity-iri-index!
-- :command :execute
-- :doc Create an index on the `statement_to_activity.activity_iri` column.
CREATE INDEX IF NOT EXISTS statement_activity_iri_idx ON statement_to_activity(activity_iri)

-- :name create-statement-to-statement-table!
-- :command :execute
-- :doc Create the `statement_to_statement` link table, used for StatementRef associations, if it doesn't exist yet.
CREATE TABLE IF NOT EXISTS statement_to_statement (
  id            TEXT NOT NULL PRIMARY KEY, -- uuid
  ancestor_id   TEXT NOT NULL,             -- uuid
  descendant_id TEXT NOT NULL,             -- uuid
  FOREIGN KEY (ancestor_id) REFERENCES xapi_statement(statement_id),
  FOREIGN KEY (descendant_id) REFERENCES xapi_statement(statement_id)
)

-- :name create-sts-ancestor-id-index!
-- :command :execute
-- :doc Create an index on the `statement_to_statement.ancestor_id` column.
CREATE INDEX IF NOT EXISTS sts_ancestor_id_idx ON statement_to_statement(ancestor_id)

-- :name create-sts-descendant-id-index!
-- :command :execute
-- :doc Create an index on the `statement_to_statement.descendant_id` column.
CREATE INDEX IF NOT EXISTS sts_descendant_id_idx ON statement_to_statement(descendant_id)



/* Document Tables */

-- :name create-state-document-table!
-- :command :execute
-- :doc Create the `state_document` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS state_document (
  id             TEXT NOT NULL PRIMARY KEY, -- uuid
  state_id       TEXT NOT NULL,             -- iri string
  activity_iri   TEXT NOT NULL,             -- iri string
  agent_ifi      TEXT NOT NULL,             -- ifi string
  registration   TEXT,                      -- uuid
  last_modified  TEXT NOT NULL,             -- timestamp
  content_type   TEXT NOT NULL,             -- string
  content_length INTEGER NOT NULL,          -- integer
  contents       BLOB NOT NULL,             -- binary data
  UNIQUE (state_id, activity_iri, agent_ifi, registration) ON CONFLICT IGNORE
)

-- :name create-agent-profile-document-table!
-- :command :execute
-- :doc Create the `agent_profile_document` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS agent_profile_document (
  id             TEXT NOT NULL PRIMARY KEY, -- uuid
  profile_id     TEXT NOT NULL,             -- iri string
  agent_ifi      TEXT NOT NULL,             -- ifi string
  last_modified  TEXT NOT NULL,             -- timestamp
  content_type   TEXT NOT NULL,             -- string
  content_length INTEGER NOT NULL,          -- integer
  contents       BLOB NOT NULL,             -- binary data
  UNIQUE (profile_id, agent_ifi) ON CONFLICT IGNORE
)

-- :name create-activity-profile-document-table!
-- :command :execute
-- :doc Create the `activity_profile_document` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS activity_profile_document (
  id             TEXT NOT NULL PRIMARY KEY, -- uuid
  profile_id     TEXT NOT NULL,             -- iri string
  activity_iri   TEXT NOT NULL,             -- iri string
  last_modified  TEXT NOT NULL,             -- timestamp
  content_type   TEXT NOT NULL,             -- string
  content_length INTEGER NOT NULL,          -- integer
  contents       BLOB NOT NULL,             -- binary data
  UNIQUE (profile_id, activity_iri) ON CONFLICT IGNORE
)

/* Admin Account Table */

-- :name create-admin-account-table!
-- :command :execute
-- :doc Create the `admin_account` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS admin_account (
  id       TEXT NOT NULL PRIMARY KEY, -- uuid
  username TEXT NOT NULL UNIQUE,      -- string
  passhash TEXT NOT NULL              -- string
)

/* Credential Tables */

-- :name create-credential-table!
-- :command :execute
-- :doc Create the `lrs_credential` table if it does not exist yet.
CREATE TABLE IF NOT EXISTS lrs_credential (
  id         TEXT NOT NULL PRIMARY KEY, -- uuid
  api_key    TEXT NOT NULL,             -- hex string
  secret_key TEXT NOT NULL,             -- hex string
  account_id TEXT NOT NULL,             -- uuid
  UNIQUE (api_key, secret_key),
  FOREIGN KEY (account_id)
    REFERENCES admin_account(id)
    ON DELETE CASCADE
)

-- :name create-credential-to-scope-table!
-- :command :execute
-- :doc Create the `credential_to_scope` link table if it does not exist yet.
CREATE TABLE IF NOT EXISTS credential_to_scope (
  id         TEXT NOT NULL PRIMARY KEY, -- uuid
  api_key    TEXT NOT NULL,             -- string
  secret_key TEXT NOT NULL,             -- string
  scope      TEXT CHECK (
               scope IN ('statements/write',
                         'statements/read',
                         'all/read',
                         'all'
             )), -- enum
  FOREIGN KEY (api_key, secret_key)
    REFERENCES lrs_credential(api_key, secret_key)
    ON DELETE CASCADE
)
