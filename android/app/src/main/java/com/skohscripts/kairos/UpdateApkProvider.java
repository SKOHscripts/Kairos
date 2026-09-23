package com.skohscripts.kairos;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Expose l'APK de mise à jour, téléchargé et vérifié côté Python
 * (app/updates.py), à l'installeur Android.
 *
 * Depuis l'API 24, une URI {@code file://} passée à une autre application lève
 * FileUriExposedException : il faut une URI {@code content://} avec permission
 * de lecture temporaire. {@code FileProvider} est dans AndroidX, exclu de l'APK
 * (voir docs/ANDROID_PACKAGING.md) : ce fournisseur minimal le remplace. Non
 * exporté, il ne sert qu'un seul fichier, à un chemin fixe, en lecture seule :
 * rien d'autre du stockage privé n'est atteignable par son URI.
 */
public class UpdateApkProvider extends ContentProvider {

    static final String APK_MIME = "application/vnd.android.package-archive";
    private static final String FILE_NAME = "kairos-update.apk";
    /** Même chemin que `app/updates.py::ANDROID_APK_PATH`, sous le dossier de
     *  données posé par `app/android_launcher.py` (`<filesDir>/kairos-data`). */
    private static final String RELATIVE_PATH = "kairos-data/updates/" + FILE_NAME;

    static File apkFile(Context context) {
        return new File(context.getFilesDir(), RELATIVE_PATH);
    }

    static Uri apkUri(Context context) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".updates")
                .appendPath(FILE_NAME)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return APK_MIME;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode) || !FILE_NAME.equals(uri.getLastPathSegment())) {
            throw new FileNotFoundException("Seul l'APK de mise à jour est lisible.");
        }
        return ParcelFileDescriptor.open(apkFile(getContext()), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** Nom et taille : certains installeurs les demandent avant d'ouvrir le fichier. */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(
                new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[] {FILE_NAME, apkFile(getContext()).length()});
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
