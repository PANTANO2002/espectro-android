package br.com.espectro;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.UUID;

/** Read-only, temporary text attachments, granted only through the Android chooser. */
public final class MessageShareProvider extends ContentProvider {
    static Uri create(Context context, String text) throws IOException {
        File directory = new File(context.getCacheDir(), "recados");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cache indisponível");
        File[] old = directory.listFiles();
        if (old != null) for (File file : old) if (System.currentTimeMillis() - file.lastModified() > 86400000L) file.delete();
        File file = new File(directory, UUID.randomUUID() + ".txt");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8)) { writer.write(text); }
        return new Uri.Builder().scheme("content").authority(context.getPackageName() + ".recados").appendPath(file.getName()).build();
    }
    private File file(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (!"content".equals(uri.getScheme()) || !(getContext().getPackageName() + ".recados").equals(uri.getAuthority())
                || uri.getPathSegments().size() != 1 || name == null || !name.matches("[a-f0-9-]{36}\\.txt")) throw new FileNotFoundException();
        File file = new File(new File(getContext().getCacheDir(), "recados"), name);
        if (!file.isFile()) throw new FileNotFoundException();
        return file;
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "text/plain"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File file = file(uri);
            String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns); MatrixCursor.RowBuilder row = cursor.newRow();
            for (String column : columns) row.add(OpenableColumns.DISPLAY_NAME.equals(column) ? "recado-espectro.txt" : OpenableColumns.SIZE.equals(column) ? file.length() : null);
            return cursor;
        } catch (FileNotFoundException e) { return null; }
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Somente leitura");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
