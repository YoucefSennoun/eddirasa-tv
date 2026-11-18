package com.youcef.eddirasatv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        Catalog.ensureReady(this)

        val screen = intent.getStringExtra(EXTRA_SCREEN) ?: return finish()
        val a = intent.getStringExtra(EXTRA_A).orEmpty()
        val b = intent.getStringExtra(EXTRA_B).orEmpty()
        val c = intent.getStringExtra(EXTRA_C).orEmpty()

        findViewById<TextView>(R.id.listTitle).text =
            intent.getStringExtra(EXTRA_TITLE).orEmpty()

        val items = when (screen) {
            Catalog.SCREEN_LEVELS -> Catalog.levels(a)
            Catalog.SCREEN_SUBJECTS -> Catalog.subjects(a)
            Catalog.SCREEN_TAGS -> Catalog.tags(a, b)
            Catalog.SCREEN_DOCS -> Catalog.docs(a)
            Catalog.SCREEN_EXAM_YEARS -> Catalog.examYears(a)
            Catalog.SCREEN_EXAM_STREAMS -> Catalog.examStreams(a, b)
            Catalog.SCREEN_EXAM_SUBJECTS -> Catalog.examSubjects(a, b, c)
            Catalog.SCREEN_EXAM_DOCS -> Catalog.examDocs(a, b, c)
            else -> emptyList()
        }

        val recycler = findViewById<RecyclerView>(R.id.listRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = ListAdapter(items) { item -> handle(item) }
    }

    private fun handle(item: NavItem) {
        item.pdfUrl?.let {
            PdfOpener.open(this, it, item.title)
            return
        }
        val screen = item.screen ?: return
        startActivity(
            newIntent(
                this,
                screen = screen,
                title = item.title,
                a = item.a,
                b = item.b,
                c = item.c
            )
        )
    }

    companion object {
        private const val EXTRA_SCREEN = "screen"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_A = "a"
        private const val EXTRA_B = "b"
        private const val EXTRA_C = "c"

        fun newIntent(
            context: Context,
            screen: String,
            title: String,
            a: String = "",
            b: String = "",
            c: String = ""
        ): Intent = Intent(context, ListActivity::class.java)
            .putExtra(EXTRA_SCREEN, screen)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_A, a)
            .putExtra(EXTRA_B, b)
            .putExtra(EXTRA_C, c)
    }
}
