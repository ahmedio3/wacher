# قواعد الهجرة (Migration Rules)

## المبدأ الأساسي
- `fallbackToDestructiveMigration()` محظور تماماً من اليوم فصاعداً.
- أي تعديل على أي Entity (إضافة/حذف/تعديل عمود أو جدول) يجب أن:
  1. يرفع `version` في `@Database` بمقدار 1.
  2. يُرفق بـ `Migration(versionOld, versionNew)` صريح.
  3. يُحدّث `MIGRATION_GUIDE.md` بوصف التغيير.
  4. يُضاف ملف schema JSON الجديد إلى `app/schemas/`.

## الصيغ المتاحة
- `ALTER TABLE ... ADD COLUMN` (إضافة عمود)
- `CREATE TABLE IF NOT EXISTS` (جدول جديد)
- `ALTER TABLE ... RENAME TO ... + CREATE TABLE ...` (تعديل عمود/حذف عمود)

## المحظورات
- `DELETE FROM` (فقدان بيانات دائم)
- `DROP TABLE` (إلا إذا كان متعمداً وموثقاً)
- `fallbackToDestructiveMigration()`

## أمثلة جاهزة للصق
- إضافة عمود:
  ```kotlin
  val MIGRATION_9_10 = Migration(9, 10) { db ->
      db.execSQL("ALTER TABLE watchlist ADD COLUMN my_new_column TEXT DEFAULT '' NOT NULL")
  }
  ```
- جدول جديد:
  ```kotlin
  val MIGRATION_9_10 = Migration(9, 10) { db ->
      db.execSQL("CREATE TABLE IF NOT EXISTS my_new_table (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL)")
  }
  ```
- تعديل عمود (حذف ثم إعادة إنشاء):
  ```kotlin
  val MIGRATION_9_10 = Migration(9, 10) { db ->
      db.execSQL("CREATE TABLE watchlist_new (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, posterPath TEXT NOT NULL, mediaType TEXT NOT NULL, rating REAL NOT NULL, addedAt INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'PLAN_TO_WATCH', isDeleted INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL DEFAULT 0)")
      db.execSQL("INSERT INTO watchlist_new SELECT * FROM watchlist")
      db.execSQL("DROP TABLE watchlist")
      db.execSQL("ALTER TABLE watchlist_new RENAME TO watchlist")
  }
  ```
  (ملاحظة: SQLite لا يدعم ALTER COLUMN أو DROP COLUMN مباشرة)

## كيفية إضافة Migration بعد تعديل Entity
1. ارفع `version` في `@Database` إلى `10` (أو أعلى حسب آخر إصدار).
2. أضف `Migration` جديداً في قائمة `addMigrations()`.
3. شغّل build — سيُنتج schema JSON جديد في `app/schemas/`.
4. تحقق من الـ diff بين schema القديم والجديد للتأكد من صحة الـ SQL.
5. اختبر بترقية قاعدة بيانات موجودة (لا تحذف التطبيق — استخدم adb install -r).
