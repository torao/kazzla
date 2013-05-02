class CreateCodeLanguages < ActiveRecord::Migration
  def change
    create_table :code_languages do |t|
      t.string :code, :null => false		# [iso639]-[variant]
      t.string :name, :null => false

      t.timestamps
    end
		add_index :code_languages, :code, :unique => true
  end
end
