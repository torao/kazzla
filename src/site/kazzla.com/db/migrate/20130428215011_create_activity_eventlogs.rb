class CreateActivityEventlogs < ActiveRecord::Migration
  def change
    create_table :activity_eventlogs do |t|
      t.integer :account_id, :null => true
      t.integer :level, :null => false
      t.integer :code, :null => false
			t.string :remote, :null => false
      t.text :message, :null => false

      t.timestamps
    end
  end
end
