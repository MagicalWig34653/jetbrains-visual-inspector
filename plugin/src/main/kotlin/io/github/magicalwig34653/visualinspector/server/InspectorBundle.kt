package io.github.magicalwig34653.visualinspector.server

/** The built inspector script (`browser-extension/dist/inspector.js`), packaged as a plugin resource. */
object InspectorBundle {
    const val RESOURCE = "/visualinspector/inspector.js"
    const val MISSING_MESSAGE =
        "inspector.js is not packaged: run 'npm run build:inspector -w browser-extension' before building the plugin."

    /** Script text, or null when the build did not include it. */
    val source: String? by lazy {
        InspectorBundle::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** Page bootstrap: the bundle, then a transport bound to `window.__viSend` (JCEF query, set up by the host). */
    fun bootstrap(bundle: String): String = """
(function(){
if(window.__viStarted)return;
$bundle
;if(typeof window.startInspector!=='function')return;
window.__viStarted=true;
var recv=null;
window.__viReceive=function(m){if(recv&&typeof m==='string')recv(m);};
window.startInspector({
  send:function(m){try{window.__viSend(m);}catch(e){}},
  subscribe:function(r){recv=r;return function(){recv=null;};}
});
})();
"""
}
