# Place all the behaviors and hooks related to the matching controller here.
# All this logic will automatically be available in application.js.
# You can use CoffeeScript in this file: http://jashkenas.github.com/coffee-script/

$ ->
  # 通知ページでクリックされたら既読へ切り替える
  $('.notifications .notice-item').each () ->
    a = $(this)
    id = a.closest("a").data("id")
    a.click (e) ->
      if a.hasClass('unread-notice-item')
        $.ajax
          url: '/user/notifications/' + id,
          type: 'POST',
          dataType: 'json',
          success: (json) ->
            set_unread_notification_count(json.unread)
            a.removeClass('unread-notice-item')
            $('p', a).addClass('text-muted')
      false

