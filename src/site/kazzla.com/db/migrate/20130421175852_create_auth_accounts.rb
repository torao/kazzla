class CreateAuthAccounts < ActiveRecord::Migration
  def change
    create_table :auth_accounts do |t|
      t.string :hashed_password, :null => false
      t.string :salt, :null => false
      t.string :name, :null => false
      t.string :language, :null => false
      t.string :timezone, :null => false
			t.integer :role_id

      t.timestamps
    end
  end
end
