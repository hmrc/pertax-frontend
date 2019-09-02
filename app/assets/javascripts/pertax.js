$(".full-width-banner__close").click(function(){
  $.ajax({
    url: "/personal-account/dismiss-ur-banner",
    success: function(){
      $('.full-width-banner').fadeOut('slow')
    }
  })
});

$(".print-this").css('display', 'inline-block');
