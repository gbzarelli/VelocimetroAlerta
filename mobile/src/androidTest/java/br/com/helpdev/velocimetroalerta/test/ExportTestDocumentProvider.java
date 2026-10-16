package br.com.helpdev.velocimetroalerta.test;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;

public final class ExportTestDocumentProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return "application/gpx+xml"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) {
        String filename = uri.getLastPathSegment();
        if (filename == null || !filename.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid test document name");
        }
        int flags = mode.contains("w")
                ? ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_WRITE_ONLY
                : ParcelFileDescriptor.MODE_READ_ONLY;
        try {
            return ParcelFileDescriptor.open(new File(getContext().getFilesDir(), filename), flags);
        } catch (java.io.FileNotFoundException error) {
            throw new IllegalStateException(error);
        }
    }
}
