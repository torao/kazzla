class CreateAuthPasswordResetSecrets < ActiveRecord::Migration
  def change
    create_table :auth_password_reset_secrets do |t|
      t.integer :account_id, :null => false
      t.string :secret, :null => false
      t.timestamp :issued_at, :null => false

      t.timestamps
    end
  end
end
