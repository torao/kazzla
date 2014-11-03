class CreateAuthContacts < ActiveRecord::Migration
  def change
    create_table :auth_contacts do |t|
      t.integer :account_id, :null => false
      t.string :schema, :null => false
      t.string :uri, :null => false
			t.boolean :confirmed, :null => false, :default => false
			t.timestamp :confirmed_at, :null => true

      t.timestamps
    end
    # アカウントIDによる連絡先一覧の取得
    add_index :auth_contacts, :account_id, :unique => false
    # メールアドレスによる認証
		add_index :auth_contacts, [ :schema, :uri ], :unique => true
  end
end
