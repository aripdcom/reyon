/* reyon.aripd.com: dil seçimini hatırlar, günün formatını ve tarihini gösterir.
   Ağa çıkmaz, çerez yazmaz; localStorage'da yalnızca seçilen dil durur.
   Betik çalışmazsa sayfa eksiksiz okunur: yalnızca "bugün" vurgusu çıkmaz. */
(function () {
  "use strict";
  var KEY = "reyon.lang";
  var lang = document.documentElement.lang || "en";

  // Dil seçicide ya da dil listesinde bir dile tıklayan, kök sayfaya
  // döndüğünde tarayıcı diline yönlendirilmez (index.html'deki satır içi betik).
  document.addEventListener("click", function (event) {
    var link = event.target.closest ? event.target.closest("a[data-lang]") : null;
    if (!link) return;
    try { localStorage.setItem(KEY, link.getAttribute("data-lang")); } catch (e) { /* gizli pencere */ }
  });

  // Dil menüsü dışarı tıklayınca ya da Esc ile kapanır.
  var menu = document.querySelector("details.lang");
  if (menu) {
    document.addEventListener("click", function (event) {
      if (menu.open && !menu.contains(event.target)) menu.open = false;
    });
    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && menu.open) {
        menu.open = false;
        menu.querySelector("summary").focus();
      }
    });
  }

  // Günün formatı, uygulamadaki dailyLevel ile aynı kural: yerel tarihin
  // epoch günü mod 3 → Market, Süpermarket, Hipermarket.
  var now = new Date();
  var epochDay = Math.floor(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()) / 86400000);
  var today = ((epochDay % 3) + 3) % 3;
  var marked = document.querySelectorAll("[data-format]");
  for (var i = 0; i < marked.length; i++) {
    var el = marked[i];
    var match = Number(el.getAttribute("data-format")) === today;
    if (el.hasAttribute("data-today-only")) el.hidden = !match;
    else el.classList.toggle("is-today", match);
  }

  // Maketteki tarih, telefonun göstereceği gibi: "24 Eylül · 2/4". Rakamlar
  // uygulamadaki gibi her dilde Latin.
  var date = document.querySelector("[data-date]");
  if (date && window.Intl && Intl.DateTimeFormat) {
    try {
      date.textContent = new Intl.DateTimeFormat(lang + "-u-nu-latn", { day: "numeric", month: "long" })
        .format(now) + " · ";
    } catch (e) { /* eski tarayıcı: tarih görünmez */ }
  }

  // Gizlilik sayfası çapasız açılırsa (uygulamadaki bağlantı böyle) okurun
  // dilindeki bölüme geçer: önce seçtiği dil, sonra tarayıcının dili.
  var policy = document.querySelector("[data-policy]");
  if (policy && !location.hash) {
    var pick = null;
    try { pick = localStorage.getItem(KEY); } catch (e) { /* yok say */ }
    var prefs = pick ? [pick] : (navigator.languages || [navigator.language || ""]);
    for (var j = 0; j < prefs.length; j++) {
      var tag = String(prefs[j]).toLowerCase().split("-")[0];
      if (tag === "no" || tag === "nn") tag = "nb";
      if (tag === "en") break;
      if (document.getElementById(tag)) { location.replace("#" + tag); break; }
    }
  }
})();
