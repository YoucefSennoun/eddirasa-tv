package com.youcef.eddirasatv

/**
 * JavaScript spatial-navigation engine injected into the site's WebView.
 *
 * The remote only sends D-pad directions and OK. Instead of a point cursor the
 * engine keeps a list of the page's interactive elements, moves a highlighted
 * box between them in the pressed direction and clicks the highlighted one on
 * OK. The highlight is drawn as a fixed overlay so it is never clipped by the
 * page layout and always tracks the element while scrolling.
 */
object WebNav {

    /**
     * Hides the site's intrusive overlays: the "حمّل التطبيق من Google Play"
     * app-promo modal and the anti-adblock "support gate". The promo script is
     * also blocked at the network layer, this is a belt-and-braces backup for
     * nodes injected later.
     */
    const val BLOCK_SCRIPT = """
(function () {
  if (window.__edAds) { return; }
  window.__edAds = true;

  var CSS = '#eddirasaAppPromo,.ed-app-promo,#eddirasaSupportGate,' +
    '.ed-support-gate{display:none !important;visibility:hidden !important;}';
  try {
    var style = document.createElement('style');
    style.setAttribute('data-ed', '1');
    style.textContent = CSS;
    (document.head || document.documentElement).appendChild(style);
  } catch (e) {}

  var SELECTORS = ['#eddirasaAppPromo', '.ed-app-promo',
    '#eddirasaSupportGate', '.ed-support-gate'];

  function kill() {
    for (var i = 0; i < SELECTORS.length; i++) {
      var nodes = document.querySelectorAll(SELECTORS[i]);
      for (var j = 0; j < nodes.length; j++) {
        try { nodes[j].parentNode.removeChild(nodes[j]); } catch (e) {}
      }
    }
    try {
      document.documentElement.style.overflow = '';
      if (document.body) { document.body.style.overflow = ''; }
    } catch (e) {}
  }

  kill();
  try {
    new MutationObserver(kill).observe(document.documentElement,
      {childList: true, subtree: true});
  } catch (e) {}
})();
"""

    const val SCRIPT = """
(function () {
  if (window.__ed) { return; }
  window.__ed = true;

  var SELECTOR = 'a[href],button,input:not([type=hidden]),select,textarea,' +
    '[role="button"],[role="link"],[onclick],[tabindex]:not([tabindex="-1"])';
  var current = null;
  var box = null;

  function root() { return document.body || document.documentElement; }

  function ensureBox() {
    if (box && box.parentNode) { return box; }
    box = document.createElement('div');
    box.setAttribute('data-ed', '1');
    box.style.cssText =
      'position:fixed;left:0;top:0;width:0;height:0;pointer-events:none;' +
      'border:3px solid #008B57;border-radius:8px;box-sizing:border-box;' +
      'box-shadow:0 0 0 2px rgba(0,0,0,.7),0 0 16px 4px rgba(0,139,87,.9);' +
      'z-index:2147483647;display:none;' +
      'transition:left .1s,top .1s,width .1s,height .1s;';
    root().appendChild(box);
    return box;
  }

  function isVisible(el) {
    if (!el || !el.getBoundingClientRect) { return false; }
    var r = el.getBoundingClientRect();
    if (r.width < 6 || r.height < 6) { return false; }
    var s = window.getComputedStyle(el);
    if (!s || s.display === 'none' || s.visibility === 'hidden') { return false; }
    if (parseFloat(s.opacity) === 0) { return false; }
    return true;
  }

  function collect() {
    var nodes = document.querySelectorAll(SELECTOR);
    var out = [];
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (el === box || (el.hasAttribute && el.hasAttribute('data-ed'))) { continue; }
      if (el.disabled) { continue; }
      if (el.getAttribute && el.getAttribute('aria-hidden') === 'true') { continue; }
      if (!isVisible(el)) { continue; }
      out.push(el);
    }
    return out;
  }

  function draw() {
    if (!current || !current.getBoundingClientRect) {
      if (box) { box.style.display = 'none'; }
      return;
    }
    var r = current.getBoundingClientRect();
    if (r.width < 1 || r.height < 1) {
      if (box) { box.style.display = 'none'; }
      return;
    }
    ensureBox();
    var p = 5;
    box.style.display = 'block';
    box.style.left = (r.left - p) + 'px';
    box.style.top = (r.top - p) + 'px';
    box.style.width = (r.width + p * 2) + 'px';
    box.style.height = (r.height + p * 2) + 'px';
  }

  function focus(el, scroll) {
    current = el;
    if (scroll) {
      try { el.scrollIntoView({block: 'center', inline: 'center'}); }
      catch (e) { try { el.scrollIntoView(); } catch (e2) {} }
    }
    try { el.focus({preventScroll: true}); } catch (e) {}
    draw();
    setTimeout(draw, 250);
    setTimeout(draw, 550);
  }

  function move(dir) {
    var els = collect();
    if (!els.length) { return; }
    if (!current || els.indexOf(current) < 0) { focus(els[0], true); return; }

    var cr = current.getBoundingClientRect();
    var cx = cr.left + cr.width / 2;
    var cy = cr.top + cr.height / 2;
    var best = null;
    var bestScore = Infinity;

    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (el === current) { continue; }
      var r = el.getBoundingClientRect();
      var x = r.left + r.width / 2;
      var y = r.top + r.height / 2;
      var dx = x - cx;
      var dy = y - cy;
      var major, minor;
      if (dir === 'left') {
        if (dx > -2) { continue; }
        major = -dx; minor = Math.abs(dy);
      } else if (dir === 'right') {
        if (dx < 2) { continue; }
        major = dx; minor = Math.abs(dy);
      } else if (dir === 'up') {
        if (dy > -2) { continue; }
        major = -dy; minor = Math.abs(dx);
      } else {
        if (dy < 2) { continue; }
        major = dy; minor = Math.abs(dx);
      }
      var score = major + minor * 3;
      if (score < bestScore) { bestScore = score; best = el; }
    }
    if (best) {
      focus(best, true);
    } else if (dir === 'up' || dir === 'down') {
      var page = Math.round((window.innerHeight || 600) * 0.6);
      try { window.scrollBy(0, dir === 'down' ? page : -page); } catch (e) {}
      draw();
    }
  }

  function click() {
    if (!current) { move('down'); return; }
    var el = current;
    try { el.focus({preventScroll: true}); } catch (e) {}
    try {
      if (typeof el.click === 'function') {
        el.click();
      } else {
        el.dispatchEvent(new MouseEvent('click',
          {bubbles: true, cancelable: true, view: window}));
      }
    } catch (e) {}
    setTimeout(draw, 300);
  }

  window.__ed_move = move;
  window.__ed_click = click;
  window.__ed_first = function () {
    var els = collect();
    if (els.length) { focus(els[0], false); }
  };
  window.__ed_reset = function () {
    current = null;
    if (box) { box.style.display = 'none'; }
  };

  window.addEventListener('scroll', draw, true);
  window.addEventListener('resize', draw);
})();
"""
}
