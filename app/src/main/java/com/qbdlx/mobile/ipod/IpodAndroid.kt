package com.qbdlx.mobile.ipod

import android.database.sqlite.SQLiteDatabase
import android.database.Cursor

/** [Sql] on top of Android's SQLiteDatabase. */
class AndroidSql(private val db: SQLiteDatabase) : Sql {
    override fun query(sql: String, args: List<Any?>): List<Row> {
        // The arguments are written into the statement as literals: rawQuery only binds text, and a text value does not
        // compare equal to a number in a column without a declared type.
        db.rawQuery(inline(sql, args), null).use { c ->
            val out = ArrayList<Row>()
            while (c.moveToNext()) {
                val row = Row()
                for (i in 0 until c.columnCount) row[c.getColumnName(i)] = value(c, i)
                out.add(row)
            }
            return out
        }
    }

    override fun exec(sql: String, args: List<Any?>) = db.execSQL(sql, args.toTypedArray())

    private fun value(c: Cursor, i: Int): Any? = when (c.getType(i)) {
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
        Cursor.FIELD_TYPE_STRING -> c.getString(i)
        Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
        else -> null
    }

    companion object {
        /** Replaces each `?` with the SQL literal of the next argument. The library's statements contain no `?` inside quotes. */
        internal fun inline(sql: String, args: List<Any?>): String {
            var n = 0
            return buildString {
                for (ch in sql) {
                    if (ch != '?') { append(ch); continue }
                    require(n < args.size) { "not enough arguments for: $sql" }
                    append(literal(args[n++]))
                }
                require(n == args.size) { "too many arguments for: $sql" }
            }
        }

        private fun literal(v: Any?): String = when (v) {
            null -> "NULL"
            is Long, is Int, is Short, is Byte -> v.toString()
            is Double, is Float -> v.toString()
            is String -> "'" + v.replace("'", "''") + "'"
            else -> error("unsupported SQL argument ${v.javaClass.simpleName}")
        }
    }
}
