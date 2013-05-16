class CreateCodeCountries < ActiveRecord::Migration
  def change
    create_table :code_countries do |t|
      t.string :code, :null => false, :limit => 2
      t.string :name, :null => false

      t.timestamps
    end
		add_index :code_countries, :code, :unique => true, :length => 2
  end
end
