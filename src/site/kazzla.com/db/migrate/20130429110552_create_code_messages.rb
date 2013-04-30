class CreateCodeMessages < ActiveRecord::Migration
  def change
    create_table :code_messages do |t|
      t.string :language, :null => false
      t.string :country
      t.string :code, :null => false
      t.string :content, :null => false

      t.timestamps
    end
  end
end
