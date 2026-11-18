package br.registro.notas;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/** Entrega para a câmera um arquivo temporário onde ela grava a foto. */
public class FotoProvider extends ContentProvider {
    static File pasta(android.content.Context c) {
        File f = new File(c.getCacheDir(), "fotos");
        f.mkdirs();
        return f;
    }

    private File arquivo(Uri uri) throws FileNotFoundException {
        String nome = uri.getLastPathSegment();
        if (nome == null || nome.contains("/") || nome.contains("..")) throw new FileNotFoundException();
        return new File(pasta(getContext()), nome);
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = arquivo(uri);
        int m = mode.contains("w")
            ? ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE
            : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(f, m);
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        File f;
        try { f = arquivo(uri); } catch (FileNotFoundException e) { return null; }
        MatrixCursor c = new MatrixCursor(new String[]{"_display_name", "_size"});
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override public String getType(Uri uri) { return "image/jpeg"; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
