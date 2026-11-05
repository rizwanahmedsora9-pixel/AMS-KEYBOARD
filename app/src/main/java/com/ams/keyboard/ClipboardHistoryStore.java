package com.ams.keyboard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent, full-text clipboard history for the keyboard.
 *
 * <p>Clips are stored as complete strings: no truncation, no length cap. The on-screen card only
 * shortens the preview, and {@link #getClipText(long)} always returns the whole entry so pasting a
 * 200 kB block still works.
 */
final class ClipboardHistoryStore extends SQLiteOpenHelper {

    /** Default number of clips kept; the user can raise it up to {@link #MAX_ALLOWED_ITEMS}. */
    static final int MAX_ITEMS = KeyboardPreferences.DEFAULT_CLIPBOARD_LIMIT;
    static final int MAX_ALLOWED_ITEMS = KeyboardPreferences.MAX_CLIPBOARD_LIMIT;
    static final int PREVIEW_CHARACTERS = 240;

    private static final String DATABASE_NAME = "ams_clipboard_history.db";
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE_CLIPS = "clipboard_clips";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_TEXT = "clip_text";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_PINNED = "pinned";

    private final Context mContext;

    ClipboardHistoryStore(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
        mContext = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createClipsTable(db);
    }

    private static void createClipsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_CLIPS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COLUMN_TEXT + " TEXT NOT NULL, "
                + COLUMN_CREATED_AT + " INTEGER NOT NULL, "
                + COLUMN_PINNED + " INTEGER NOT NULL DEFAULT 0"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Keep existing clips; just add the pin flag.
            db.execSQL("ALTER TABLE " + TABLE_CLIPS
                    + " ADD COLUMN " + COLUMN_PINNED + " INTEGER NOT NULL DEFAULT 0");
            return;
        }
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CLIPS);
        createClipsTable(db);
    }

    /** The configured limit, in entries. */
    int getLimit() {
        return KeyboardPreferences.getClipboardLimit(mContext);
    }

    /** Stores the complete text without shortening it. */
    void addClip(String text) {
        insertClip(text, false);
    }

    /** Adds the current system clipboard on keyboard startup unless it is already newest. */
    void addClipIfDifferentFromNewest(String text) {
        insertClip(text, true);
    }

    private void insertClip(String text, boolean skipNewestDuplicate) {
        if (text == null || text.length() == 0) {
            return;
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (skipNewestDuplicate) {
                Cursor cursor = db.query(TABLE_CLIPS,
                        new String[]{COLUMN_TEXT}, null, null, null, null,
                        COLUMN_ID + " DESC", "1");
                try {
                    if (cursor.moveToFirst() && text.equals(cursor.getString(0))) {
                        db.setTransactionSuccessful();
                        return;
                    }
                } finally {
                    cursor.close();
                }
            }

            ContentValues values = new ContentValues();
            values.put(COLUMN_TEXT, text);
            values.put(COLUMN_CREATED_AT, System.currentTimeMillis());
            db.insertOrThrow(TABLE_CLIPS, null, values);
            trimToLimit(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Drops the oldest unpinned clips so the table honours the configured history size. */
    void trimToLimit(SQLiteDatabase db) {
        int limit = getLimit();
        db.execSQL("DELETE FROM " + TABLE_CLIPS + " WHERE " + COLUMN_PINNED + "=0 AND "
                + COLUMN_ID + " NOT IN (SELECT " + COLUMN_ID + " FROM " + TABLE_CLIPS
                + " WHERE " + COLUMN_PINNED + "=0"
                + " ORDER BY " + COLUMN_ID + " DESC LIMIT " + limit + ")");
    }

    void trimToLimit() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            trimToLimit(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    List<ClipPreview> getRecentPreviews() {
        ArrayList<ClipPreview> clips = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT " + COLUMN_ID + ", substr(" + COLUMN_TEXT + ", 1, "
                + (PREVIEW_CHARACTERS + 1) + "), length(" + COLUMN_TEXT + "), "
                + COLUMN_PINNED + " FROM " + TABLE_CLIPS
                + " ORDER BY " + COLUMN_PINNED + " DESC, " + COLUMN_ID + " DESC";
        Cursor cursor = db.rawQuery(sql, null);
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String preview = cursor.getString(1);
                long characterCount = cursor.getLong(2);
                boolean pinned = cursor.getInt(3) == 1;
                if (preview == null) {
                    preview = "";
                }

                if (characterCount > PREVIEW_CHARACTERS) {
                    int availableCodePoints = preview.codePointCount(0, preview.length());
                    int end = preview.offsetByCodePoints(0,
                            Math.min(PREVIEW_CHARACTERS, availableCodePoints));
                    preview = preview.substring(0, end) + "…";
                }
                clips.add(new ClipPreview(id, preview, characterCount, pinned));
            }
        } finally {
            cursor.close();
        }
        return clips;
    }

    String getClipText(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_CLIPS, new String[]{COLUMN_TEXT},
                COLUMN_ID + "=?", new String[]{String.valueOf(id)},
                null, null, null, "1");
        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    int getCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_CLIPS, null);
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            cursor.close();
        }
    }

    void deleteClip(long id) {
        getWritableDatabase().delete(TABLE_CLIPS, COLUMN_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    void setPinned(long id, boolean pinned) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_PINNED, pinned ? 1 : 0);
        getWritableDatabase().update(TABLE_CLIPS, values, COLUMN_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    void clear() {
        getWritableDatabase().delete(TABLE_CLIPS, null, null);
    }

    // ------------------------------------------------------------ static helpers

    /** Applies a new history limit from the settings screen without waiting for the IME. */
    static void applyLimit(Context context, int limit) {
        ClipboardHistoryStore store = new ClipboardHistoryStore(context);
        try {
            store.trimToLimit();
        } catch (RuntimeException ignored) {
            // Nothing to trim yet, or the database is being recreated.
        } finally {
            store.close();
        }
    }

    /** Clears every stored clip from the settings screen. */
    static void clearAll(Context context) {
        ClipboardHistoryStore store = new ClipboardHistoryStore(context);
        try {
            store.clear();
        } catch (RuntimeException ignored) {
            // Best effort: the IME also exposes a clear button.
        } finally {
            store.close();
        }
    }

    static final class ClipPreview {
        final long id;
        final String text;
        final long characterCount;
        final boolean pinned;

        ClipPreview(long id, String text, long characterCount, boolean pinned) {
            this.id = id;
            this.text = text;
            this.characterCount = characterCount;
            this.pinned = pinned;
        }
    }
}
