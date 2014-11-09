# encoding: UTF-8
# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# Note that this schema.rb definition is the authoritative source for your
# database schema. If you need to create the application database on another
# system, you should be using db:schema:load, not running all the migrations
# from scratch. The latter is a flawed and unsustainable approach (the more migrations
# you'll amass, the slower it'll run and the greater likelihood for issues).
#
# It's strongly recommended that you check this file into your version control system.

ActiveRecord::Schema.define(version: 20141109070602) do

  # These are extensions that must be enabled in order to support this database
  enable_extension "plpgsql"

  create_table "activity_eventlogs", force: true do |t|
    t.integer  "account_id"
    t.integer  "level",      null: false
    t.integer  "code",       null: false
    t.string   "remote",     null: false
    t.text     "message",    null: false
    t.datetime "created_at"
    t.datetime "updated_at"
  end

  create_table "auth_accounts", force: true do |t|
    t.string   "hashed_password", null: false
    t.string   "salt",            null: false
    t.string   "name",            null: false
    t.string   "language",        null: false
    t.string   "timezone",        null: false
    t.integer  "role_id"
    t.datetime "created_at"
    t.datetime "updated_at"
  end

  create_table "auth_contacts", force: true do |t|
    t.integer  "account_id",                   null: false
    t.string   "schema",                       null: false
    t.string   "uri",                          null: false
    t.boolean  "confirmed",    default: false, null: false
    t.datetime "confirmed_at"
    t.datetime "created_at"
    t.datetime "updated_at"
  end

  add_index "auth_contacts", ["account_id"], name: "index_auth_contacts_on_account_id", using: :btree
  add_index "auth_contacts", ["schema", "uri"], name: "index_auth_contacts_on_schema_and_uri", unique: true, using: :btree

  create_table "auth_password_reset_secrets", force: true do |t|
    t.integer  "account_id", null: false
    t.string   "secret",     null: false
    t.datetime "issued_at",  null: false
    t.datetime "created_at"
    t.datetime "updated_at"
  end

  create_table "auth_roles", force: true do |t|
    t.string   "name",        null: false
    t.string   "permissions", null: false
    t.datetime "created_at"
    t.datetime "updated_at"
  end

  create_table "auth_tokens", force: true do |t|
    t.integer  "account_id", null: false
    t.integer  "scheme",     null: false
    t.integer  "object"
    t.uuid     "token",      null: false
    t.datetime "issued_at",  null: false
    t.datetime "expired_at", null: false
    t.datetime "created_at"
    t.datetime "updated_at"
  end

  add_index "auth_tokens", ["account_id", "scheme", "token"], name: "index_auth_tokens_on_account_id_and_scheme_and_token", unique: true, using: :btree

  create_table "code_continents", force: true do |t|
    t.string   "code",       limit: 2, null: false
    t.string   "name",                 null: false
    t.datetime "created_at"
    t.datetime "updated_at"
  end

  add_index "code_continents", ["code"], name: "index_code_continents_on_code", unique: true, using: :btree

  create_table "code_countries", force: true do |t|
    t.string   "code",       limit: 2, null: false
    t.string   "name",                 null: false
    t.datetime "created_at"
    t.datetime "updated_at"
  end

  add_index "code_countries", ["code"], name: "index_code_countries_on_code", unique: true, using: :btree

  create_table "code_languages", force: true do |t|
    t.string   "code",       null: false
    t.string   "name",       null: false
    t.datetime "created_at"
    t.datetime "updated_at"
  end

  add_index "code_languages", ["code"], name: "index_code_languages_on_code", unique: true, using: :btree

  create_table "code_messages", force: true do |t|
    t.string   "language",   null: false
    t.string   "country"
    t.string   "code",       null: false
    t.string   "content",    null: false
    t.datetime "created_at"
    t.datetime "updated_at"
  end

  add_index "code_messages", ["language", "country", "code"], name: "index_code_messages_on_language_and_country_and_code", unique: true, using: :btree

  create_table "code_timezones", force: true do |t|
    t.string   "code",            null: false
    t.string   "name",            null: false
    t.integer  "utc_offset",      null: false
    t.integer  "daylight_saving", null: false
    t.datetime "created_at"
    t.datetime "updated_at"
  end

  add_index "code_timezones", ["code"], name: "index_code_timezones_on_code", unique: true, using: :btree

  create_table "node_nodes", force: true do |t|
    t.integer  "account_id",      null: false
    t.string   "uuid",            null: false
    t.string   "name"
    t.integer  "region_id"
    t.string   "continent"
    t.string   "country"
    t.string   "state"
    t.float    "latitude"
    t.float    "longitude"
    t.string   "agent"
    t.float    "qos"
    t.string   "status"
    t.binary   "certificate",     null: false
    t.datetime "disconnected_at"
    t.datetime "created_at"
    t.datetime "updated_at"
  end

  add_index "node_nodes", ["uuid"], name: "index_node_nodes_on_uuid", unique: true, using: :btree

  create_table "node_regions", force: true do |t|
    t.string   "name",       null: false
    t.string   "continent"
    t.string   "country"
    t.string   "state"
    t.datetime "created_at"
    t.datetime "updated_at"
  end

  create_table "node_sessions", force: true do |t|
    t.string   "session_id"
    t.string   "node_id"
    t.string   "endpoints"
    t.string   "proxy"
    t.datetime "created_at"
    t.datetime "updated_at"
  end

end
