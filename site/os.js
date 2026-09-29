// Met en avant le système du visiteur (facultatif : la page est complète sans).
(function () {
  var ua = navigator.userAgent;
  var os = /Android/i.test(ua) ? "android"
    : /Windows/i.test(ua) ? "windows"
    : /Mac OS X|Macintosh/i.test(ua) ? "macos"
    : /Linux|X11/i.test(ua) ? "linux" : null;
  if (!os) return;
  var card = document.querySelector('.os[data-os="' + os + '"]');
  if (card) card.classList.add("current");
})();
