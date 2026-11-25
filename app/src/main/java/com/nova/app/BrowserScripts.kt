package com.nova.app

import org.json.JSONObject

/**
 * HTML & injected JavaScript helpers for NOVA Browser:
 *  - Custom nova://home quick-dial homepage
 *  - Prism.js-style syntax-highlighted View Page Source HTML wrapper
 *  - Video/HLS sniffer (.mp4, .webm, .m3u8)
 *  - Credential capture & autofill helper
 *  - Night mode CSS fallback
 */
object BrowserScripts {
    const val HOME_URL = "nova://home"

    fun homeHtml(bookmarks: List<Pair<String, String>>): String {
        val bmHtml = StringBuilder()
        for ((title, url) in bookmarks.take(6)) {
            val safeT = title.ifBlank { url }.replace("<", "&lt;").take(18)
            val safeU = url.replace("'", "%27").replace("\"", "&quot;")
            bmHtml.append("<a class='dial' href='$safeU'><div class='dot'>★</div><span>$safeT</span></a>")
        }
        return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<title>NOVA Home</title>
<style>
  *{box-sizing:border-box;font-family:-apple-system,BlinkMacSystemFont,Roboto,sans-serif}
  body{margin:0;padding:24px 16px;background:radial-gradient(circle at 50% 10%,#17284a 0%,#0b1220 75%);color:#eef4ff;min-height:100vh}
  .logo{text-align:center;font-size:32px;font-weight:800;letter-spacing:8px;color:#00e5ff;margin:18px 0 6px}
  .sub{text-align:center;font-size:12px;color:#8fa3bf;margin-bottom:20px}
  .search{display:flex;background:#141e33;border:1px solid #00e5ff66;border-radius:26px;padding:6px 14px;margin:0 auto 22px;max-width:520px}
  .search input{flex:1;background:transparent;border:none;outline:none;color:#fff;font-size:15px;padding:8px 4px}
  .search button{background:#00e5ff;color:#05101a;border:none;border-radius:20px;padding:8px 16px;font-weight:700;font-size:13px}
  .sec{font-size:12px;color:#8fa3bf;letter-spacing:1.5px;text-transform:uppercase;margin:14px 4px 10px}
  .grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;max-width:540px;margin:0 auto}
  .dial{display:flex;flex-direction:column;align-items:center;text-decoration:none;color:#dce7f7;background:#141e33cc;border:1px solid #263759;border-radius:16px;padding:12px 6px}
  .dot{width:38px;height:38px;border-radius:19px;background:#1e2e4f;color:#00e5ff;display:flex;align-items:center;justify-content:center;font-weight:800;font-size:16px;margin-bottom:6px}
  .dial span{font-size:11.5px;text-align:center;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:100%}
</style>
</head>
<body>
  <div class="logo">NOVA</div>
  <div class="sub">Persistent Sessions • AdBlock • Video Sniffer • View Source</div>
  <form class="search" onsubmit="var q=document.getElementById('q').value.trim();if(q){location.href=/^(https?:|view-source:)/i.test(q)?q:'https://www.google.com/search?q='+encodeURIComponent(q);}return false;">
    <input id="q" type="text" placeholder="Search Google or enter URL / view-source:" autocomplete="off">
    <button type="submit">Go</button>
  </form>
  <div class="sec">Quick Dials</div>
  <div class="grid">
    <a class="dial" href="https://www.google.com"><div class="dot">G</div><span>Google</span></a>
    <a class="dial" href="https://chat.deepseek.com/"><div class="dot">D</div><span>DeepSeek</span></a>
    <a class="dial" href="https://chatgpt.com/"><div class="dot">C</div><span>ChatGPT</span></a>
    <a class="dial" href="https://claude.ai/"><div class="dot">A</div><span>Claude</span></a>
    <a class="dial" href="https://gemini.google.com/"><div class="dot">✦</div><span>Gemini</span></a>
    <a class="dial" href="https://github.com/"><div class="dot">⌥</div><span>GitHub</span></a>
    <a class="dial" href="https://dailypakistan.com.pk/"><div class="dot">📰</div><span>Daily PK</span></a>
    <a class="dial" href="https://en.m.wikipedia.org/"><div class="dot">W</div><span>Wikipedia</span></a>
  </div>
  ${if (bookmarks.isNotEmpty()) "<div class='sec'>Bookmarks</div><div class='grid'>$bmHtml</div>" else ""}
</body>
</html>
""".trimIndent()
    }

    /**
     * Offline Prism-Tomorrow-Night-inspired HTML syntax-highlighted View Page Source wrapper.
     * Escapes raw HTML safely, applies syntax colorization per line, renders line numbers, and handles large pages fast.
     */
    fun viewSourceHtml(url: String, rawSource: String): String {
        val clipped = if (rawSource.length > 350_000) rawSource.take(350_000) + "\n<!-- ... truncated for smooth display (${rawSource.length} chars total) ... -->" else rawSource
        val safeUrl = url.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val encodedJson = JSONObject.quote(clipped)
        return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>view-source:$safeUrl</title>
<style>
  :root{--bg:#0d1321;--gutter:#141d31;--line:#23314d;--txt:#e6edf7;--tag:#ff7b72;--attr:#ffa657;--val:#7ee787;--com:#6e7681;--kw:#d2a8ff;--num:#79c0ff}
  *{box-sizing:border-box}
  body{margin:0;background:var(--bg);color:var(--txt);font-family:'Roboto Mono',ui-monospace,SFMono-Regular,Consolas,monospace;font-size:12.5px;line-height:1.55}
  .meta{position:sticky;top:0;z-index:5;background:#111a2e;border-bottom:1px solid var(--line);padding:8px 12px;font-size:11.5px;color:#9cb3d1;display:flex;justify-content:space-between;align-items:center}
  .meta strong{color:#ff7a59}
  .wrap-toggle{background:#1f2c47;color:#eef4ff;border:1px solid #34476e;border-radius:12px;padding:4px 10px;font-size:11px;font-family:sans-serif}
  table{width:100%;border-collapse:collapse;table-layout:auto}
  td.ln{width:1%;min-width:38px;padding:0 8px 0 6px;text-align:right;color:var(--com);background:var(--gutter);border-right:1px solid var(--line);user-select:none;vertical-align:top;font-size:11px}
  td.cd{padding:0 12px;white-space:pre;vertical-align:top}
  body.wrap td.cd{white-space:pre-wrap;word-break:break-word}
  .t-com{color:var(--com);font-style:italic}
  .t-tag{color:var(--tag)}
  .t-attr{color:var(--attr)}
  .t-val{color:var(--val)}
  .t-kw{color:var(--kw)}
  .t-num{color:var(--num)}
</style>
</head>
<body class="wrap">
  <div class="meta">
    <span><strong>view-source:</strong>$safeUrl</span>
    <button class="wrap-toggle" onclick="document.body.classList.toggle('wrap')">Toggle Wrap</button>
  </div>
  <table><tbody id="src"></tbody></table>
<script>
(function(){
  var raw = $encodedJson;
  function esc(s){return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}
  function highlightLine(line){
    var e = esc(line);
    if(/&lt;!--[\s\S]*?--&gt;/.test(e)){
      e = e.replace(/(&lt;!--[\s\S]*?--&gt;)/g,'<span class="t-com">$1</span>');
    }
    e = e.replace(/(&lt;\/?)([a-zA-Z0-9:-]+)([\s\S]*?)(\/?&gt;)/g, function(_, open, tag, attrs, close){
      var hiAttrs = attrs.replace(/([a-zA-Z0-9_:-]+)(\s*=\s*)("[^"]*"|'[^']*')/g,
        '<span class="t-attr">$1</span>$2<span class="t-val">$3</span>');
      return '<span class="t-tag">'+open+tag+'</span>'+hiAttrs+'<span class="t-tag">'+close+'</span>';
    });
    e = e.replace(/\b(function|const|let|var|return|if|else|for|while|import|export|class|async|await|true|false|null)\b/g,'<span class="t-kw">$1</span>');
    return e || ' ';
  }
  var lines = raw.split('\n');
  var tb = document.getElementById('src');
  var chunk = [];
  for(var i=0;i<lines.length;i++){
    chunk.push('<tr><td class="ln">'+(i+1)+'</td><td class="cd">'+highlightLine(lines[i])+'</td></tr>');
    if(chunk.length>=400 || i===lines.length-1){
      tb.insertAdjacentHTML('beforeend', chunk.join(''));
      chunk = [];
    }
  }
})();
</script>
</body>
</html>
""".trimIndent()
    }

    const val JS_SNIFF = """
(function(){
  if(window.__nvSniff)return; window.__nvSniff=1;
  function rep(u,clicked){
    if(!u||typeof u!=='string')return;
    var ok=(u.indexOf('http')===0&&(/\.(mp4|webm|m3u8)(\?|$)/i.test(u)||u.indexOf('mime=video')>=0||u.indexOf('/video')>=0));
    if(ok){
      try{NovaDl.onMedia(u);}catch(e){}
      if(clicked){try{NovaDl.onVideoTap(u);}catch(e){}}
    }
  }
  function scan(){
    document.querySelectorAll('video,video source,source').forEach(function(el){
      rep(el.currentSrc||el.src||el.getAttribute('src'),false);
    });
  }
  try{
    var origPlay=HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play=function(){
      try{rep(this.currentSrc||this.src,false);}catch(e){}
      return origPlay.apply(this,arguments);
    };
  }catch(e){}
  document.addEventListener('click',function(e){
    try{
      var v=e.target&&(e.target.closest?e.target.closest('video'):null);
      if(!v&&e.target&&e.target.tagName==='VIDEO')v=e.target;
      if(!v){
        var p=e.target&&e.target.parentElement;
        if(p)v=p.querySelector('video');
      }
      if(v){
        var src=v.currentSrc||v.src||(v.querySelector('source')&&v.querySelector('source').src)||'';
        if(src)rep(src,true);
      }
    }catch(err){}
  },true);
  scan();
  setInterval(scan,2500);
})();"""

    const val JS_PASS_HOOK = """
(function(){
  if(window.__nvPass)return; window.__nvPass=1;
  document.addEventListener('submit',function(e){
    try{
      var f=e.target; if(!f||!f.querySelector)return;
      var pw=f.querySelector('input[type="password"]');
      if(!pw||!pw.value)return;
      var us=f.querySelector('input[type="email"],input[type="text"],input[name*="user"],input[name*="mail"]');
      NovaDl.onCreds(location.hostname,us?us.value:'',pw.value);
    }catch(err){}
  },true);
})();"""

    const val JS_NIGHT_ON = """
(function(){
  var s=document.getElementById('__nvNight');
  if(!s){s=document.createElement('style');s.id='__nvNight';s.textContent='html{filter:invert(0.9) hue-rotate(180deg);background:#111!important;}img,video,picture,canvas{filter:invert(1) hue-rotate(180deg);}';(document.head||document.documentElement).appendChild(s);}
})();"""

    const val JS_NIGHT_OFF = """
(function(){var s=document.getElementById('__nvNight');if(s)s.remove();})();"""

    const val JS_READER_EXTRACT = """
(function(){
  var el=document.querySelector('article,main,[role="main"],.post-content,.article-body')||document.body;
  var clone=el.cloneNode(true);
  clone.querySelectorAll('script,style,nav,footer,aside,iframe,ins,.ad,.ads').forEach(function(n){n.remove();});
  var txt=(clone.innerText||'').replace(/\n{3,}/g,'\n\n').trim();
  return JSON.stringify({title:document.title||'',url:location.href,text:txt.slice(0,24000)});
})();"""

    const val JS_AD_COSMETIC = """
(function(){
  if(window.__nvAdCos)return; window.__nvAdCos=1;
  try{
    var s=document.createElement('style');
    s.textContent='ins.adsbygoogle,[id*="google_ads"],[class*="popunder"],[id*="taboola"],[id*="outbrain"]{display:none!important;}';
    (document.head||document.documentElement).appendChild(s);
  }catch(e){}
})();"""

    fun jsFillCreds(user: String, pass: String): String = """
(function(u,p){
  var pw=document.querySelector('input[type="password"]');
  if(!pw)return 'no-password-field';
  var us=document.querySelector('input[type="email"],input[type="text"],input[name*="user"],input[name*="mail"]');
  function setVal(el,v){
    if(!el)return;
    var proto=HTMLInputElement.prototype;
    var d=Object.getOwnPropertyDescriptor(proto,'value');
    if(d&&d.set)d.set.call(el,v); else el.value=v;
    el.dispatchEvent(new Event('input',{bubbles:true}));
    el.dispatchEvent(new Event('change',{bubbles:true}));
  }
  if(us&&u)setVal(us,u);
  setVal(pw,p);
  return 'filled';
})(${JSONObject.quote(user)},${JSONObject.quote(pass)});"""
}
