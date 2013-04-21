class CreateAuthContacts < ActiveRecord::Migration
  def change
    create_table :auth_contacts do |t|
      t.integer :account_id
      t.string :uri

      t.timestamps
    end
  end
end
