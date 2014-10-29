class Node::Region < ActiveRecord::Base
  attr_accessible :continent, :country, :name, :state
end
