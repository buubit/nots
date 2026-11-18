package br.registro.notas;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;

public class MainActivity extends Activity {
    static final String HOST = "appassets.androidplatform.net";
    static final int REQ_FOTO = 1, REQ_ARQ = 2;

    WebView web;
    ValueCallback<Uri[]> retorno;
    Uri fotoUri;
    File fotoArq;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF14213D);
        getWindow().setNavigationBarColor(0xFFFFFFFF);

        // limpa fotos temporárias antigas (as boas já estão guardadas dentro do app)
        File[] velhas = FotoProvider.pasta(this).listFiles();
        if (velhas != null) for (File f : velhas) f.delete();

        if (Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 9);

        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);

        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String caminho = u.getPath();
                if (caminho == null || caminho.equals("/")) caminho = "/index.html";
                caminho = caminho.substring(1);
                try {
                    InputStream in = getAssets().open(caminho);
                    return new WebResourceResponse(tipo(caminho), "utf-8", in);
                } catch (Exception e) {
                    WebResourceResponse nada = new WebResourceResponse("text/plain", "utf-8",
                        new ByteArrayInputStream(new byte[0]));
                    nada.setStatusCodeAndReasonPhrase(404, "Not Found");
                    return nada;
                }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                Uri u = Uri.parse(url);
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (retorno != null) retorno.onReceiveValue(null);
                retorno = cb;
                String[] tipos = p.getAcceptTypes();
                boolean imagem = tipos != null && tipos.length > 0 && tipos[0] != null && tipos[0].startsWith("image");
                if (imagem && abrirCamera()) return true;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(imagem ? "image/*" : "*/*");
                try { startActivityForResult(i, REQ_ARQ); }
                catch (ActivityNotFoundException e) { retorno.onReceiveValue(null); retorno = null; return false; }
                return true;
            }
        });

        web.addJavascriptInterface(new Ponte(), "Android");
        if (b != null) web.restoreState(b);
        else web.loadUrl("https://" + HOST + "/index.html");
    }

    boolean abrirCamera() {
        fotoArq = new File(FotoProvider.pasta(this), "foto_" + System.currentTimeMillis() + ".jpg");
        fotoUri = Uri.parse("content://br.registro.notas.fotos/" + fotoArq.getName());
        Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cam.putExtra(MediaStore.EXTRA_OUTPUT, fotoUri);
        cam.setClipData(ClipData.newRawUri("foto", fotoUri));
        cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(cam, REQ_FOTO); return true; }
        catch (Exception e) { return false; }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (retorno == null) return;
        Uri[] r = null;
        if (req == REQ_FOTO) {
            if (res == RESULT_OK && fotoArq != null && fotoArq.length() > 0) r = new Uri[]{fotoUri};
        } else if (req == REQ_ARQ) {
            if (res == RESULT_OK && data != null && data.getData() != null) r = new Uri[]{data.getData()};
        }
        retorno.onReceiveValue(r);
        retorno = null;
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override public void onBackPressed() {
        web.evaluateJavascript(
            "(function(){var b=document.getElementById('btVoltar');" +
            "if((typeof V!=='undefined'&&V.visor)||(b&&!b.hidden)){b.click();return 1;}return 0;})()",
            new ValueCallback<String>() { @Override public void onReceiveValue(String r) {
                if (!"1".equals(r)) moveTaskToBack(true); } });
    }

    static String tipo(String c) {
        if (c.endsWith(".html")) return "text/html";
        if (c.endsWith(".js")) return "application/javascript";
        if (c.endsWith(".css")) return "text/css";
        if (c.endsWith(".png")) return "image/png";
        if (c.endsWith(".json") || c.endsWith(".webmanifest")) return "application/json";
        return "application/octet-stream";
    }

    /** Arquivo sendo gravado em Downloads/Registro. */
    static class Saida { Writer w; Uri uri; File arq; boolean erro; String nome; }

    class Ponte {
        final HashMap<String, Saida> abertas = new HashMap<>();
        int seq = 0;

        @JavascriptInterface public synchronized String inicio(String nome, String tipo) {
            String id = "s" + (++seq);
            Saida s = new Saida();
            s.nome = nome;
            try {
                OutputStream os;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put("_display_name", nome);
                    cv.put("mime_type", tipo);
                    cv.put("relative_path", Environment.DIRECTORY_DOWNLOADS + "/Registro");
                    cv.put("is_pending", 1);
                    s.uri = getContentResolver().insert(Uri.parse("content://media/external/downloads"), cv);
                    os = getContentResolver().openOutputStream(s.uri);
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Registro");
                    dir.mkdirs();
                    s.arq = new File(dir, nome);
                    os = new FileOutputStream(s.arq);
                }
                s.w = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"), 1 << 16);
            } catch (Exception e) { s.erro = true; }
            abertas.put(id, s);
            return id;
        }

        @JavascriptInterface public synchronized void pedaco(String id, String txt) {
            Saida s = abertas.get(id);
            if (s == null || s.erro) return;
            try { s.w.write(txt); } catch (Exception e) { s.erro = true; }
        }

        @JavascriptInterface public synchronized boolean fim(String id) {
            Saida s = abertas.remove(id);
            if (s == null) return false;
            try { if (s.w != null) s.w.close(); } catch (Exception e) { s.erro = true; }
            try {
                if (s.uri != null) {
                    if (s.erro) getContentResolver().delete(s.uri, null, null);
                    else { ContentValues cv = new ContentValues(); cv.put("is_pending", 0);
                        getContentResolver().update(s.uri, cv, null, null); }
                } else if (s.arq != null && !s.erro) {
                    MediaScannerConnection.scanFile(MainActivity.this, new String[]{s.arq.getAbsolutePath()}, null, null);
                }
            } catch (Exception e) { s.erro = true; }
            final boolean ok = !s.erro;
            final String nome = s.nome;
            runOnUiThread(new Runnable() { @Override public void run() {
                Toast.makeText(MainActivity.this,
                    ok ? "Salvo em Downloads/Registro:\n" + nome : "Não consegui salvar o arquivo.",
                    Toast.LENGTH_LONG).show(); } });
            return ok;
        }

        @JavascriptInterface public void copiar(final String txt) {
            runOnUiThread(new Runnable() { @Override public void run() {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Registro", txt));
            } });
        }
    }
}
