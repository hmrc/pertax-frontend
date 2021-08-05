/* global $, GOVUK */
$('.full-width-banner__close, .covid-banner__close').click(function () {
  $.ajax({
    url: '/personal-account/dismiss-ur-banner',
    success: function () {
      $('.full-width-banner, .covid-banner').fadeOut('slow');
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
