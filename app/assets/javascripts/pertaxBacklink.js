/* global $ */
// =====================================================
// Back link mimics browser back functionality
// =====================================================
// store referrer value to cater for IE - https://developer.microsoft.com/en-us/microsoft-edge/platform/issues/10474810/  */
var docReferrer = document.referrer;
// prevent resubmit warning
if (
  window.history &&
  window.history.replaceState &&
  typeof window.history.replaceState === 'function'
) {
  window.history.replaceState(null, null, window.location.href);
}
// back click handle, dependent upon presence of referrer & no host change
const backlink = document.getElementById('back-link');

if(backlink != null && backlink != 'undefined' ) {
    backlink.addEventListener('click', function (e) {
      e.preventDefault();
      if (
        window.history &&
        window.history.back &&
        typeof window.history.back === 'function' &&
        docReferrer !== '' &&
        docReferrer.indexOf(window.location.host) !== -1
      ) {
        window.history.back();
      }
    });
}