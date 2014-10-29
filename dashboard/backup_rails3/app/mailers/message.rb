class Message < ActionMailer::Base
  default from: "postmaster@kazzla.com"

  # Subject can be set in your I18n file at config/locales/en.yml
  # with the following lookup:
  #
  #   en.message.reset_password.subject
  #
  def reset_password(toaddr, url)
		@url = url

    mail(
			to: toaddr,
			subject: "Reset Password",
		)
  end
end
