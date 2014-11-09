class UserMailer < ActionMailer::Base
#  default from: "postmaster@kazzla.com"
  default from: 'kazzla.development@gmail.com'

  def reset_password(toaddr, url)
    @url = url
    mail(
      to: toaddr,
      subject: "Reset Password",
    )
  end

  # Subject can be set in your I18n file at config/locales/en.yml
  # with the following lookup:
  #
  #   en.user_mailer.address_confirmation.subject
  #
  def address_confirmation(to, url)
    @url = url
    mail({ to: to, cc: 'koiroha@gmail.com', bcc: 'kazzla.development@gmail.com' })
  end

end
