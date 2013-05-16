class CreateCodeTimezones < ActiveRecord::Migration
  def change
    create_table :code_timezones do |t|
      t.string :code, :null => false
      t.string :name, :null => false
      t.integer :utc_offset, :null => false
      t.integer :daylight_saving, :null => false

      t.timestamps
    end
		add_index :code_timezones, :code, :unique => true
  end
end
