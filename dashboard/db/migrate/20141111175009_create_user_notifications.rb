class CreateUserNotifications < ActiveRecord::Migration
  def change
    create_table :user_notifications do |t|
      t.integer :account_id, :null => false
      t.integer :priority, :null => false
      t.string :informant, :null => false
      t.datetime :read_at
      t.datetime :pinned_at
      t.string :code, :null => false
      t.string :args, :null => false

      t.timestamps
    end
    add_index(:user_notifications, :account_id)
  end
end
