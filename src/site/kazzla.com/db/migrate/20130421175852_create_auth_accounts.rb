class CreateAuthAccounts < ActiveRecord::Migration
  def change
    create_table :auth_accounts do |t|
      t.string :hashed_password
      t.string :salt
      t.string :name
      t.string :locale
      t.string :timezone
      t.timestamp :last_login

      t.timestamps
    end
  end
end
