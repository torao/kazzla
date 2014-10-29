class CreateAuthContacts < ActiveRecord::Migration
  def change
    create_table :auth_contacts do |t|
      t.integer :account_id, :null => false
      t.string :uri, :null => false
			t.boolean :confirmed, :null => false, :default => false
			t.timestamp :confirmed_at, :null => true

      t.timestamps
    end
		add_index :auth_contacts, :uri, :unique => true
  end
end
