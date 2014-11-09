class CreateAuthTokens < ActiveRecord::Migration
  def change
    create_table :auth_tokens do |t|
      t.integer  :account_id, :null => false    # アカウントID
      t.integer  :scheme, :null => false        # 0:メールアドレスの確認, 1:パスワードのリセット
      t.integer  :object                        # 操作対象
      t.uuid     :token, :null => false         # Token
      t.datetime :issued_at, :null => false     # 発行日時
      t.datetime :expired_at, :null => false    # 有効期限

      t.timestamps
    end
    # メールアドレスによる認証
    add_index :auth_tokens, [ :account_id, :scheme, :token ], :unique => true
  end
end
