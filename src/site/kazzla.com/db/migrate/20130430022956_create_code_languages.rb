class CreateCodeLanguages < ActiveRecord::Migration
  def change
    create_table :code_languages do |t|
      t.string :code, :null => false
      t.string :name, :null => false

      t.timestamps
    end
		add_index :code_languages, :code, :unique => true
  end
end
