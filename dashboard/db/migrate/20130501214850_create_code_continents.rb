class CreateCodeContinents < ActiveRecord::Migration
  def change
    create_table :code_continents do |t|
			t.string :code, :null => false, :limit => 2
      t.string :name, :null => false

      t.timestamps
    end
		add_index :code_continents, :code, :unique => true, :length => 2
  end
end
