require 'test_helper'

class UserMailerTest < ActionMailer::TestCase
  test "address_confirmation" do
    mail = UserMailer.address_confirmation
    assert_equal "Address confirmation", mail.subject
    assert_equal ["to@example.org"], mail.to
    assert_equal ["from@example.com"], mail.from
    assert_match "Hi", mail.body.encoded
  end

end
