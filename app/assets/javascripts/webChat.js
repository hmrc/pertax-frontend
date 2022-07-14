/* global $ */
//Webchat automatic popup
function offerChat(chatId) {
  (function (e, f) {
    var d,
      c,
      b,
      a = e.createElement('iframe');
    a.src = 'about:blank';
    a.title = '';
    a.id = 'egot_iframe';
    (a.frameElement || a).style.cssText = 'width:0;height:0;border:0';
    b = e.getElementsByTagName('script');
    b = b[b.length - 1];
    b.parentNode.insertBefore(a, b);
    try {
      c = a.contentWindow.document;
    } catch (g) {
      (d = e.domain),
        (a.src =
          "javascript:var d=document.open();d.domain='" + d + "';void(0);"),
        (c = a.contentWindow.document);
    }
    c.open()._d = function () {
      var a = this.createElement('script');
      d && (this.domain = d);
      a.src = f;
      this.isEGFIF = !0;
      this.body.appendChild(a);
    };
    c.write('<body onload="document._d();">');
    c.close();
  })(document, '//analytics.analytics-egain.com/onetag/' + chatId);
}

//Webchat click to chat
document.addEventListener("DOMContentLoaded", function(event) {
  if (typeof openChat !== 'undefined') {
    $('.webchat-container').css({ display: 'block' });
    $('.openChat').click(openChat);
  }
});
