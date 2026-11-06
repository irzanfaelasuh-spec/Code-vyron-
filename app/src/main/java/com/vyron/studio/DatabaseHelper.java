package com.vyron.studio;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "vyron_local.db";
    private static final int DB_VERSION = 1;
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;

    public DatabaseHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL UNIQUE," +
                "salt TEXT NOT NULL," +
                "hash TEXT NOT NULL," +
                "created_at INTEGER NOT NULL");
        db.execSQL("CREATE TABLE projects(" +
                "user_id INTEGER NOT NULL," +
                "name TEXT NOT NULL," +
                "content TEXT NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "PRIMARY KEY(user_id,name))");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Reserved for future migrations. VYRON keeps the schema deliberately small.
    }

    public synchronized long createUser(String name, String password) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        if (findUserId(name) != -1) return -2;
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        String hash = hash(password, salt);
        ContentValues v = new ContentValues();
        v.put("name", name.trim());
        v.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP));
        v.put("hash", hash);
        v.put("created_at", System.currentTimeMillis());
        return db.insertOrThrow("users", null, v);
    }

    public synchronized long verifyUser(String name, String password) throws Exception {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("users", new String[]{"id", "salt", "hash"}, "name=?",
                new String[]{name.trim()}, null, null, null)) {
            if (!c.moveToFirst()) return -1;
            byte[] salt = Base64.decode(c.getString(1), Base64.NO_WRAP);
            String actual = hash(password, salt);
            return actual.equals(c.getString(2)) ? c.getLong(0) : -1;
        }
    }

    public synchronized String getUserName(long id) {
        try (Cursor c = getReadableDatabase().query("users", new String[]{"name"}, "id=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }

    public synchronized void saveProject(long userId, String name, String content) {
        ContentValues v = new ContentValues();
        v.put("user_id", userId); v.put("name", name); v.put("content", content); v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("projects", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized String loadLatestProject(long userId) {
        try (Cursor c = getReadableDatabase().query("projects", new String[]{"content"}, "user_id=?",
                new String[]{String.valueOf(userId)}, null, null, "updated_at DESC", "1")) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    public synchronized List<String> listProjectNames(long userId) {
        List<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("projects", new String[]{"name"}, "user_id=?",
                new String[]{String.valueOf(userId)}, null, null, "updated_at DESC")) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    private long findUserId(String name) {
        try (Cursor c = getReadableDatabase().query("users", new String[]{"id"}, "name=?",
                new String[]{name.trim()}, null, null, null, "1")) {
            return c.moveToFirst() ? c.getLong(0) : -1;
        }
    }

    private static String hash(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        try {
            byte[] encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.encodeToString(encoded, Base64.NO_WRAP);
        } finally { spec.clearPassword(); }
    }
}
