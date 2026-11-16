"""Gera o app instalável (site/PWA) em docs/ a partir de index.html. O APK Android também usa docs/.

Uso: python3 tools/build_pwa.py
"""
import hashlib, json, pathlib, shutil

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "docs"
src = (ROOT / "index.html").read_text(encoding="utf-8")

# jsPDF local (funciona sem internet)
cdn = '<script src="https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js"></script>'
assert cdn in src
src = src.replace(cdn, '<script src="vendor/jspdf.umd.min.js"></script>')

register = """<script>
if ("serviceWorker" in navigator && location.protocol !== "file:" && !window.AndroidBridge) {
  window.addEventListener("load", () => navigator.serviceWorker.register("sw.js").catch(() => {}));
}
</script>"""

head = """<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover">
<meta name="theme-color" content="#C9770F">
<meta name="description" content="Tire fotos estilo polaroide com o nome da pessoa e imprima em folhas A4.">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-title" content="Polaroide">
<meta name="apple-mobile-web-app-status-bar-style" content="default">
<link rel="manifest" href="manifest.webmanifest">
<link rel="icon" href="icons/icon-192.png" type="image/png">
<link rel="apple-touch-icon" href="icons/apple-touch-icon.png">
<style>:root{padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}body{margin:0}img{max-width:100%}html{touch-action:manipulation}</style>
"""
marker = '<div class="wrap">'
i = src.index(marker)
html = head + src[:i] + "</head>\n<body>\n" + src[i:] + "\n" + register + "\n</body>\n</html>\n"

if OUT.exists():
    for p in OUT.iterdir():
        if p.name != "icons":
            shutil.rmtree(p) if p.is_dir() else p.unlink()
OUT.mkdir(exist_ok=True)
(OUT / "vendor").mkdir(exist_ok=True)
(OUT / "index.html").write_text(html, encoding="utf-8")
shutil.copy(ROOT / "tools" / "vendor" / "jspdf.umd.min.js", OUT / "vendor" / "jspdf.umd.min.js")

manifest = {
    "name": "Cabine Polaroide",
    "short_name": "Polaroide",
    "description": "Tire fotos estilo polaroide com o nome da pessoa e imprima em folhas A4.",
    "lang": "pt-BR",
    "start_url": "./",
    "scope": "./",
    "display": "standalone",
    "orientation": "portrait",
    "background_color": "#E8EAED",
    "theme_color": "#C9770F",
    "icons": [
        {"src": "icons/icon-192.png", "sizes": "192x192", "type": "image/png"},
        {"src": "icons/icon-512.png", "sizes": "512x512", "type": "image/png"},
        {"src": "icons/icon-maskable-512.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable"},
    ],
}
(OUT / "manifest.webmanifest").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

shell = ["./", "index.html", "manifest.webmanifest", "vendor/jspdf.umd.min.js",
         "icons/icon-192.png", "icons/icon-512.png", "icons/apple-touch-icon.png"]
version = hashlib.sha256(html.encode()).hexdigest()[:10]
sw = (ROOT / "tools" / "sw.template.js").read_text(encoding="utf-8")
sw = sw.replace("__VERSION__", version).replace("__SHELL__", json.dumps(shell))
(OUT / "sw.js").write_text(sw, encoding="utf-8")
print(f"docs/ gerado (versão {version})")
