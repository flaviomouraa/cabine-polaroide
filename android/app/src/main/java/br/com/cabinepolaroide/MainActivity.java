package br.com.cabinepolaroide;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Abre a Cabine Polaroide (os arquivos de docs/ dentro do APK) num WebView.
 * O endereço https://appassets.androidplatform.net dá ao app um contexto seguro,
 * necessário para a câmera ao vivo (getUserMedia) e para guardar as fotos no aparelho.
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final int PEDIDO_ARQUIVO = 1;
    private static final int PEDIDO_CAMERA = 2;

    private WebView web;
    private ValueCallback<Uri[]> arquivoPendente;
    private Uri fotoUri;
    private PermissionRequest cameraPendente;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        setContentView(web);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false); // o vídeo da câmera começa sozinho
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback, FileChooserParams params) {
                return abrirSeletor(callback, params);
            }

            /** A página pediu a câmera ao vivo: libera se o Android já deu permissão, senão pergunta. */
            @Override
            public void onPermissionRequest(final PermissionRequest req) {
                runOnUiThread(() -> {
                    boolean querVideo = false;
                    for (String r : req.getResources()) if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) querVideo = true;
                    if (!querVideo) { req.deny(); return; }
                    if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        req.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        if (cameraPendente != null) cameraPendente.deny();
                        cameraPendente = req;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, PEDIDO_CAMERA);
                    }
                });
            }
        });

        web.addJavascriptInterface(new Ponte(), "AndroidBridge");

        if (estado != null) web.restoreState(estado);
        else web.loadUrl("https://" + HOST + "/index.html");
    }

    @Override
    public void onRequestPermissionsResult(int pedido, String[] perms, int[] res) {
        super.onRequestPermissionsResult(pedido, perms, res);
        if (pedido != PEDIDO_CAMERA || cameraPendente == null) return;
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            cameraPendente.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
        } else {
            cameraPendente.deny();
            Toast.makeText(this, "Sem permissão da câmera. Libere em Configurações › Apps › Cabine Polaroide.", Toast.LENGTH_LONG).show();
        }
        cameraPendente = null;
    }

    /** Plano B: abre o app de câmera do celular quando a câmera ao vivo não está disponível. */
    private boolean abrirSeletor(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
        if (arquivoPendente != null) arquivoPendente.onReceiveValue(null);
        arquivoPendente = callback;
        fotoUri = null;

        boolean imagem = false;
        for (String t : params.getAcceptTypes()) if (t != null && t.startsWith("image")) imagem = true;

        Intent intent = null;
        if (imagem && params.isCaptureEnabled()) {
            try {
                File pasta = new File(getCacheDir(), "fotos");
                pasta.mkdirs();
                File foto = new File(pasta, "foto-" + System.currentTimeMillis() + ".jpg");
                fotoUri = FileProvider.getUriForFile(this, getPackageName() + ".arquivos", foto);
                intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, fotoUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) {
                intent = null;
                fotoUri = null;
            }
        }
        if (intent == null) {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(imagem ? "image/*" : "*/*");
        }
        try {
            startActivityForResult(intent, PEDIDO_ARQUIVO);
        } catch (Exception e) {
            arquivoPendente.onReceiveValue(null);
            arquivoPendente = null;
            Toast.makeText(this, "Não encontrei um app de câmera.", Toast.LENGTH_LONG).show();
        }
        return true;
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent dados) {
        super.onActivityResult(pedido, resultado, dados);
        if (pedido != PEDIDO_ARQUIVO || arquivoPendente == null) return;
        Uri[] escolhido = null;
        if (resultado == RESULT_OK) {
            if (fotoUri != null) escolhido = new Uri[]{fotoUri};
            else if (dados != null && dados.getData() != null) escolhido = new Uri[]{dados.getData()};
        }
        arquivoPendente.onReceiveValue(escolhido);
        arquivoPendente = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle estado) {
        super.onSaveInstanceState(estado);
        web.saveState(estado);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /** Chamado pelo app web para salvar o PDF das folhas. */
    private class Ponte {
        @JavascriptInterface
        public String saveFile(String nome, String base64, String tipo) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, nome);
                    cv.put(MediaStore.Downloads.MIME_TYPE, tipo);
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Cabine Polaroide");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) { out.write(bytes); }
                    avisar("Salvo em Downloads › Cabine Polaroide: " + nome);
                    return "saved";
                }
                // Android 9 ou anterior: oferece compartilhar (Drive, WhatsApp, e-mail…)
                File pasta = new File(getCacheDir(), "compartilhar");
                pasta.mkdirs();
                File arq = new File(pasta, nome);
                try (FileOutputStream out = new FileOutputStream(arq)) { out.write(bytes); }
                Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".arquivos", arq);
                Intent envio = new Intent(Intent.ACTION_SEND);
                envio.setType(tipo);
                envio.putExtra(Intent.EXTRA_STREAM, uri);
                envio.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> startActivity(Intent.createChooser(envio, nome)));
                return "shared";
            } catch (Exception e) {
                avisar("Não consegui salvar o arquivo.");
                return "error";
            }
        }

        private void avisar(String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
        }
    }
}
