package com.youcef.eddirasatv

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val recycler = findViewById<RecyclerView>(R.id.listRecycler)
        val loading = findViewById<TextView>(R.id.homeLoading)
        recycler.layoutManager = LinearLayoutManager(this)

        val thread = Thread {
            Catalog.ensureReady(this)
            val items = Catalog.categories()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loading.visibility = View.GONE
                recycler.adapter = ListAdapter(items) { item ->
                    item.screen?.let { screen ->
                        startActivity(ListActivity.newIntent(this, screen, item.title, item.a))
                    }
                }
            }
        }
        thread.isDaemon = true
        thread.start()
    }
}
