class CreateAuthProfileImages < ActiveRecord::Migration
  def change
    create_table :auth_profile_images do |t|
      t.integer :account_id, :null => false
      t.string :original_name, :null => false
      t.string :content_type, :null => false
      t.binary :content, :null => false

      t.timestamps
    end
    add_index(:auth_profile_images, :account_id, { :unique => true })
  end
end
