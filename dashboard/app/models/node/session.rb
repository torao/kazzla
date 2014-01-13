class Node::Session < ActiveRecord::Base
  attr_accessible :session_id, :node_id, :endpoints, :proxy
end
