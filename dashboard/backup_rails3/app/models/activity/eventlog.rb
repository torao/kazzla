class Activity::Eventlog < ActiveRecord::Base
  attr_accessible :account_id, :code, :level, :message, :remote
end
