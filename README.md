# Cabine Polaroide

App que tira fotos estilo polaroide (5,5 × 8,5 cm) com o nome da pessoa escrito na base branca e organiza as fotos em folhas A4 para imprimir: 11 por folha (6 em pé + 5 deitadas) ou 9 por folha (todas em pé). Gera um PDF em 300 dpi.

- `index.html`: o app (fonte).
- `tools/build_pwa.py`: gera `docs/` (site instalável, publicado pelo GitHub Pages). Rode depois de mudar `index.html`.
- `android/`: APK Android (WebView com câmera ao vivo). Compilado pelo GitHub Actions (`.github/workflows/apk.yml`) e publicado em Releases.

Imprima sempre em **tamanho real / 100%**.
