/* Injected into the stock web client. Sends regular pages to Lantern; settings and admin pages stay classic. */
(function () {
  try {
    var KEY = 'lantern.classic';
    var base = location.pathname.replace(/\/web(\/index\.html|\/)?$/i, '');
    var lantern = base + '/lantern';
    var SETTINGS = /^\/?(dashboard|configurationpage|plugins|mypreferences|userprofile|wizard|metadata|edititemmetadata|livetv|tasks|networking|apikeys|log|devices|activity|notificationsettings|serveractivity|library|encodingsettings|playbackconfiguration|streamingsettings|users|useredit|userlibraryaccess|userparentalcontrol|userpassword|usernew|branding|quickconnect)/i;
    var route = function () { return (location.hash || '').replace(/^#!?/, ''); };
    var classic = function () { return localStorage.getItem(KEY) === '1' || /[?&]classic=1/.test(location.search); };
    var wantsClassic = function () {
      var r = route();
      if (SETTINGS.test(r)) return true;
      // the stock login page on its way to a settings page
      var m = /^\/?login\?(.*)$/i.exec(r);
      if (m) {
        var to = new URLSearchParams(m[1]).get('url') || '';
        return SETTINGS.test(decodeURIComponent(to));
      }
      return false;
    };
    if (!classic() && !wantsClassic()) {
      location.replace(lantern);
      return;
    }

    // Going back to Home from a settings page returns to Lantern.
    window.addEventListener('hashchange', function () {
      if (!classic() && /^\/?(home)?(\?|$)/i.test(route())) location.replace(lantern);
    });

    // In classic mode, add a small button to the header to switch back.
    if (localStorage.getItem(KEY) === '1') {
      var add = function () {
        var hr = document.querySelector('.headerRight');
        if (!hr || hr.querySelector('.lanternBack')) return;
        var b = document.createElement('button');
        b.type = 'button';
        b.className = 'headerButton headerButtonRight paper-icon-button-light lanternBack';
        b.title = 'Switch to Lantern';
        b.innerHTML = '<span class="material-icons" aria-hidden="true">live_tv</span>';
        b.onclick = function () { localStorage.removeItem(KEY); location.replace(lantern); };
        hr.insertBefore(b, hr.firstChild);
      };
      new MutationObserver(add).observe(document.documentElement, { childList: true, subtree: true });
    }
  } catch (e) { /* never break the stock client */ }
})();
