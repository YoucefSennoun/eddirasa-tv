package com.youcef.eddirasatv

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

data class NavItem(
    val title: String,
    val subtitle: String? = null,
    val isHeader: Boolean = false,
    val screen: String? = null,
    val a: String = "",
    val b: String = "",
    val c: String = "",
    val pdfUrl: String? = null
)

object Catalog {

    const val SCREEN_LEVELS = "levels"
    const val SCREEN_SUBJECTS = "subjects"
    const val SCREEN_TAGS = "tags"
    const val SCREEN_DOCS = "docs"
    const val SCREEN_EXAM_YEARS = "exam_years"
    const val SCREEN_EXAM_STREAMS = "exam_streams"
    const val SCREEN_EXAM_SUBJECTS = "exam_subjects"
    const val SCREEN_EXAM_DOCS = "exam_docs"

    private const val DB_NAME = "catalog.db"
    private const val DB_VERSION = 1
    private const val META_PREFS = "catalog_meta"
    private const val META_KEY = "asset_version"

    @Volatile
    private var database: SQLiteDatabase? = null

    private val SUBJECT_NAMES = mapOf(
        "arabic" to "اللغة العربية",
        "math" to "الرياضيات",
        "physics" to "العلوم الفيزيائية",
        "science" to "علوم الطبيعة والحياة",
        "sciences" to "التربية العلمية",
        "chimi" to "الكيمياء",
        "islamic" to "التربية الإسلامية",
        "civic" to "التربية المدنية",
        "his_geo" to "التاريخ والجغرافيا",
        "philo" to "الفلسفة",
        "french" to "اللغة الفرنسية",
        "english" to "اللغة الإنجليزية",
        "deutsch" to "اللغة الألمانية",
        "espanol" to "اللغة الإسبانية",
        "italian" to "اللغة الإيطالية",
        "tamazight" to "اللغة الأمازيغية",
        "informatique" to "الإعلام الآلي",
        "technologie" to "هندسة الطرائق",
        "elect" to "الهندسة الكهربائية",
        "mecanical" to "الهندسة الميكانيكية",
        "genie-civil" to "الهندسة المدنية",
        "gestion" to "التسيير المحاسبي والمالي",
        "eco" to "الاقتصاد والمناجمنت",
        "droit" to "القانون",
        "sport" to "التربية البدنية والرياضية",
        "art" to "التربية الفنية",
        "music" to "التربية الموسيقية"
    )

    private val SUBJECT_ORDER = listOf(
        "arabic", "math", "physics", "science", "sciences", "chimi",
        "islamic", "civic", "his_geo", "philo",
        "french", "english", "deutsch", "espanol", "italian", "tamazight",
        "informatique", "technologie", "elect", "mecanical", "genie-civil",
        "gestion", "eco", "droit", "sport", "art", "music"
    )

    private val LEVEL_NAMES = linkedMapOf(
        "0ap" to "القسم التحضيري",
        "1ap" to "السنة الأولى ابتدائي",
        "2ap" to "السنة الثانية ابتدائي",
        "3ap" to "السنة الثالثة ابتدائي",
        "4ap" to "السنة الرابعة ابتدائي",
        "5ap" to "السنة الخامسة ابتدائي",
        "1am" to "السنة الأولى متوسط",
        "2am" to "السنة الثانية متوسط",
        "3am" to "السنة الثالثة متوسط",
        "4am" to "السنة الرابعة متوسط",
        "1as" to "السنة الأولى ثانوي",
        "2as" to "السنة الثانية ثانوي",
        "3as" to "السنة الثالثة ثانوي"
    )

    private val GROUP_ORDER = listOf("learning", "assessments", "practice", "books", "files", "other")

    fun ensureReady(context: Context) {
        if (database != null) return
        synchronized(this) {
            if (database != null) return
            val target = File(context.filesDir, DB_NAME)
            val prefs = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
            val installed = prefs.getInt(META_KEY, 0)
            if (!target.exists() || installed != DB_VERSION) {
                context.assets.open(DB_NAME).use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output, 256 * 1024)
                    }
                }
                prefs.edit().putInt(META_KEY, DB_VERSION).apply()
            }
            database = SQLiteDatabase.openDatabase(
                target.path, null, SQLiteDatabase.OPEN_READONLY
            )
        }
    }

    private fun db(): SQLiteDatabase =
        database ?: throw IllegalStateException("Catalog not ready")

    fun categories(): List<NavItem> = listOf(
        NavItem("التعليم الابتدائي", "من التحضيري إلى الخامسة ابتدائي", screen = SCREEN_LEVELS, a = "primary"),
        NavItem("التعليم المتوسط", "من الأولى إلى الرابعة متوسط", screen = SCREEN_LEVELS, a = "middle"),
        NavItem("التعليم الثانوي", "من الأولى إلى الثالثة ثانوي", screen = SCREEN_LEVELS, a = "secondary"),
        NavItem("بكالوريا", "مواضيع وحلول البكالوريا", screen = SCREEN_EXAM_YEARS, a = "bac"),
        NavItem("شهادة التعليم المتوسط", "مواضيع وحلول شهادة التعليم المتوسط", screen = SCREEN_EXAM_YEARS, a = "bem")
    )

    fun levels(category: String): List<NavItem> {
        val prefixes = when (category) {
            "primary" -> listOf("0ap", "1ap", "2ap", "3ap", "4ap", "5ap")
            "middle" -> listOf("1am", "2am", "3am", "4am")
            "secondary" -> listOf("1as", "2as", "3as")
            else -> emptyList()
        }
        return prefixes.mapNotNull { prefix ->
            LEVEL_NAMES[prefix]?.let {
                NavItem(it, screen = SCREEN_SUBJECTS, a = prefix)
            }
        }
    }

    fun subjects(level: String): List<NavItem> {
        val slugs = ArrayList<String>()
        db().rawQuery(
            "SELECT path FROM csv_files WHERE kind='index' AND path LIKE ?",
            arrayOf("$level/%")
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val path = cursor.getString(0)
                val slug = path.substringAfterLast('/').removeSuffix(".csv")
                if (slug != "bac" && slug != "bem") slugs.add(slug)
            }
        }
        return slugs.sortedBy { SUBJECT_ORDER.indexOf(it).let { i -> if (i < 0) 999 else i } }
            .map { slug ->
                NavItem(SUBJECT_NAMES[slug] ?: slug, screen = SCREEN_TAGS, a = level, b = slug)
            }
    }

    fun tags(level: String, subject: String): List<NavItem> {
        val rows = ArrayList<Triple<String, String, String>>()
        db().rawQuery(
            "SELECT r.content, r.title, COALESCE(m.group_key,'other') " +
                "FROM csv_rows r LEFT JOIN tag_display_meta m ON m.path = r.content " +
                "WHERE r.path = ? ORDER BY r.row_index",
            arrayOf("$level/$subject.csv")
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
            }
        }
        val grouped = rows.groupBy { it.third }
        val items = ArrayList<NavItem>()
        for (groupKey in GROUP_ORDER) {
            val groupRows = grouped[groupKey] ?: continue
            if (groupRows.isEmpty()) continue
            items.add(NavItem(groupTitle(groupKey), isHeader = true))
            for ((tagPath, title, _) in groupRows) {
                items.add(NavItem(title, screen = SCREEN_DOCS, a = tagPath))
            }
        }
        for ((groupKey, groupRows) in grouped) {
            if (GROUP_ORDER.contains(groupKey)) continue
            items.add(NavItem(groupTitle(groupKey), isHeader = true))
            for ((tagPath, title, _) in groupRows) {
                items.add(NavItem(title, screen = SCREEN_DOCS, a = tagPath))
            }
        }
        return items
    }

    fun docs(tagPath: String): List<NavItem> {
        val items = ArrayList<NavItem>()
        db().rawQuery(
            "SELECT title, content, date FROM csv_rows WHERE path = ? ORDER BY row_index",
            arrayOf(tagPath)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(0)
                val url = cursor.getString(1)
                val date = cursor.getString(2)
                if (!url.lowercase().endsWith(".pdf")) continue
                items.add(NavItem(title, subtitle = date.ifBlank { null }, pdfUrl = url))
            }
        }
        return items
    }

    fun examYears(examType: String): List<NavItem> {
        val items = ArrayList<NavItem>()
        db().rawQuery(
            "SELECT y.year, MAX(y.title), " +
                "(SELECT COUNT(*) FROM exam_archive_topics t WHERE t.exam_type=y.exam_type AND t.year=y.year) AS topics, " +
                "(SELECT t2.legacy_url FROM exam_archive_years t2 WHERE t2.exam_type=y.exam_type AND t2.year=y.year AND t2.is_legacy=1 LIMIT 1) AS legacy " +
                "FROM exam_archive_years y WHERE y.exam_type=? GROUP BY y.year ORDER BY y.year DESC",
            arrayOf(examType)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val year = cursor.getInt(0)
                val title = cursor.getString(1) ?: year.toString()
                val topics = cursor.getInt(2)
                val legacy = cursor.getString(3) ?: ""
                if (topics > 0) {
                    items.add(NavItem(title, screen = SCREEN_EXAM_STREAMS, a = examType, b = year.toString()))
                } else if (legacy.isNotBlank()) {
                    items.add(NavItem(title, pdfUrl = legacy))
                }
            }
        }
        return items
    }

    fun examStreams(examType: String, year: String): List<NavItem> {
        val items = ArrayList<NavItem>()
        db().rawQuery(
            "SELECT DISTINCT stream_slug, stream_name, stream_sort FROM exam_archive_topics " +
                "WHERE exam_type=? AND year=? ORDER BY stream_sort",
            arrayOf(examType, year)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items.add(
                    NavItem(
                        cursor.getString(1),
                        screen = SCREEN_EXAM_SUBJECTS,
                        a = examType, b = year, c = cursor.getString(0)
                    )
                )
            }
        }
        return items
    }

    fun examSubjects(examType: String, year: String, stream: String): List<NavItem> {
        val items = ArrayList<NavItem>()
        db().rawQuery(
            "SELECT DISTINCT subject_slug, subject_name, subject_sort FROM exam_archive_topics " +
                "WHERE exam_type=? AND year=? AND stream_slug=? ORDER BY subject_sort",
            arrayOf(examType, year, stream)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                items.add(
                    NavItem(
                        cursor.getString(1),
                        screen = SCREEN_EXAM_DOCS,
                        a = examType, b = year, c = cursor.getString(0) + "|" + stream
                    )
                )
            }
        }
        return items
    }

    fun examDocs(examType: String, year: String, subjectAndStream: String): List<NavItem> {
        val subject = subjectAndStream.substringBefore('|')
        val stream = subjectAndStream.substringAfter('|')
        val items = ArrayList<NavItem>()
        db().rawQuery(
            "SELECT title, session_label, topic_url, correction_url, detailed_correction_url " +
                "FROM exam_archive_topics WHERE exam_type=? AND year=? AND stream_slug=? AND subject_slug=? " +
                "ORDER BY sort_order",
            arrayOf(examType, year, stream, subject)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(0)
                val session = cursor.getString(1)
                val topic = cursor.getString(2)
                val correction = cursor.getString(3)
                val detailed = cursor.getString(4)
                if (topic.isNotBlank()) {
                    items.add(NavItem("الموضوع", subtitleOf(session, title), pdfUrl = topic))
                }
                if (correction.isNotBlank()) {
                    items.add(NavItem("التصحيح", subtitleOf(session, title), pdfUrl = correction))
                }
                if (detailed.isNotBlank()) {
                    items.add(NavItem("التصحيح المفصل", subtitleOf(session, title), pdfUrl = detailed))
                }
            }
        }
        return items
    }

    private fun subtitleOf(session: String, title: String): String =
        if (session.isBlank()) title else "$session · $title"

    private fun groupTitle(key: String): String = when (key) {
        "learning" -> "الدروس والملخصات"
        "assessments" -> "الفروض والاختبارات"
        "practice" -> "التدريب والتمارين"
        "books" -> "الكتب والمراجع"
        "files" -> "ملفات وأدوات"
        "bac" -> "البكالوريا والمسابقات"
        else -> "موارد أخرى"
    }
}
