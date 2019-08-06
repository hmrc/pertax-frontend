$(".full-width-banner__close").click(function(){
  $.ajax({
    url: "/personal-account/dismiss-ur-banner",
    success: function(){
      $('.full-width-banner').fadeOut('slow')
    }
  })
});

$(".print-this").css('display', 'inline-block');

//DDCNLS-6488: Added JS to override the UR link to one of 10 random URLs specified in the list below
$('document').ready(function(){
    var href = ['https://zwgy80l7.optimalworkshop.com/chalkmark/live_as_is_01', 'https://zwgy80l7.optimalworkshop.com/chalkmark/live_as_is_02', 'https://zwgy80l7.optimalworkshop.com/chalkmark/live_as_is_03', 'https://zwgy80l7.optimalworkshop.com/chalkmark/live_as_is_04', 'https://zwgy80l7.optimalworkshop.com/chalkmark/live_as_is_05', 'https://zwgy80l7.optimalworkshop.com/chalkmark/live_prototype_01', 'https://zwgy80l7.optimalworkshop.com/chalkmark/live_prototype_02', 'https://zwgy80l7.optimalworkshop.com/chalkmark/live_prototype_03', 'https://zwgy80l7.optimalworkshop.com/chalkmark/live_prototype_04', 'https://zwgy80l7.optimalworkshop.com/chalkmark/live_prototype_05'];
    $('#full-width-banner-link').attr('href', href[Math.floor(Math.random()*href.length)]);
});