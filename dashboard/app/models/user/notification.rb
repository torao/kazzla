class User::Notification < ActiveRecord::Base

  PRIORITY_CRITICAL = 0
  PRIORITY_WARNING = 100
  PRIORITY_INFORMATION = 200
  PRIORITY_EVENTLOG = 300

  # アカウントに対する未読通知件数の取得
  def self.add(account_id, priority, informant, code, args)
    n = self.new({
      account_id: account_id,
      priority: priority,
      informant: informant.to_s,
      code: code,
      args: args.to_json,
    })
    n.save!
  end

  # アカウントに対する未読通知件数の取得
  def self.unread_items_count(account_id)
    self.where(['account_id=? and read_at is null', account_id]).count
  end

  # 既読設定
  def self.make_items_to_read(account_id, *id)
    now = Time.now
    self.where(['account_id=? and id in(?)', account_id, id]).each{ |n|
      n.read_at = now
      n.save!
    }
  end

end
