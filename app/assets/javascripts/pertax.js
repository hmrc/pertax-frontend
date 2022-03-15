/* global $, GOVUK */
$('.covid-banner__close').click(function () {
  $.ajax({
    url: '/personal-account/dismiss-ur-banner',
    success: function () {
      $('.covid-banner').fadeOut('slow');
    },
  });
});

$('.print-this').css('display', 'inline-block');

$('.error-summary').focus();

// ---------------------------------------------------
// Introduce direct skip link control, to work around voiceover failing of hash links
// https://bugs.webkit.org/show_bug.cgi?id=179011
// https://axesslab.com/skip-links/
// ---------------------------------------------------
$('.skiplink').click(function (e) {
  e.preventDefault();
  $(':header:first').attr('tabindex', '-1').focus();
});

GOVUK.shimLinksWithButtonRole.init();


$(document).ready(function() {
  var cookieData=GOVUK.getCookie("mdtpurr");
  var URbanner = $("#full-width-banner");

  if (cookieData==null) {
      URbanner.addClass("banner-panel--show");
  }

  $(".full-width-banner__close").on("click", function(e) {
      e.preventDefault();
      GOVUK.setCookie("mdtpurr", "suppress_for_all_services", {days: 30});
      URbanner.removeClass("banner-panel--show");
  });
});