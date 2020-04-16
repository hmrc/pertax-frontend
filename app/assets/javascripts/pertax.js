$(".full-width-banner__close, .covid-banner__close").click(function(){
  $.ajax({
    url: "/personal-account/dismiss-ur-banner",
    success: function(){
      $('.full-width-banner, .covid-banner').fadeOut('slow')
    }
  })
});


$(".print-this").css('display', 'inline-block');


$('.error-summary').focus();