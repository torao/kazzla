DROP TABLE storage.lease;
DROP SCHEMA storage;
DROP TABLE domain.node;
DROP TABLE domain.account;
DROP SCHEMA domain;

-- domain スキーマ
CREATE SCHEMA domain AUTHORIZATION postgres;

CREATE TABLE domain.account(
  id uuid NOT NULL PRIMARY KEY,
  name VARCHAR(60) NOT NULL UNIQUE,
  password VARCHAR(256) NOT NULL,
  salt CHAR(8) NOT NULL
) WITH(OIDS=FALSE);
ALTER TABLE domain.account OWNER TO postgres;
COMMENT ON TABLE  domain.account IS 'アカウント情報を保持するテーブル';
COMMENT ON COLUMN domain.account.name IS 'URIとして使用するアカウント名';
COMMENT ON COLUMN domain.account.password IS '不可逆関数でハッシュ化されたパスワード';
COMMENT ON COLUMN domain.account.salt IS 'パスワードのハッシュ化に使用したSalt';

CREATE TABLE domain.node(
  id uuid NOT NULL PRIMARY KEY,
  account_id uuid NOT NULL REFERENCES domain.account(id) ON DELETE CASCADE -- ノードの所有者
) WITH(OIDS=FALSE);
ALTER TABLE domain.node OWNER TO postgres;
COMMENT ON TABLE  domain.node IS 'ノード情報';
COMMENT ON COLUMN domain.node.account_id IS 'ノードの所有者';

-- storage スキーマ
CREATE SCHEMA storage AUTHORIZATION postgres;

CREATE TABLE storage.lease(
  id serial NOT NULL PRIMARY KEY,
  transferor uuid NOT NULL REFERENCES domain.account(id) ON DELETE CASCADE, -- 領域を譲渡しているアカウント
  transferee uuid NOT NULL REFERENCES domain.account(id) ON DELETE CASCADE, -- 領域の譲渡を受けているアカウント
  size bigint NOT NULL CHECK (size > 0), -- 譲渡された領域サイズ [B]
  UNIQUE(transferor, transferee)
) WITH(OIDS=FALSE);
ALTER TABLE storage.lease OWNER TO postgres;
COMMENT ON TABLE  storage.lease IS '譲渡された領域情報を保持するテーブル';
COMMENT ON COLUMN storage.lease.transferor IS '領域を譲渡しているアカウント';
COMMENT ON COLUMN storage.lease.transferee IS '領域の譲渡を受けているアカウント';
COMMENT ON COLUMN storage.lease.size IS '譲渡された領域サイズ [B]';

