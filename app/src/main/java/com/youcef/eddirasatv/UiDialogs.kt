package com.youcef.eddirasatv

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.TextView

object UiDialogs {

    fun showHelp(activity: Activity) {
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_help)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(widthFraction(activity, 0.72f), ViewGroup.LayoutParams.WRAP_CONTENT)

        val close = dialog.findViewById<TextView>(R.id.helpClose)
        close.setOnClickListener { dialog.dismiss() }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dialog.dismiss()
                true
            } else {
                false
            }
        }
        dialog.show()
        close.requestFocus()
    }

    fun confirmExit(activity: Activity) {
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_exit)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(widthFraction(activity, 0.5f), ViewGroup.LayoutParams.WRAP_CONTENT)

        val yes = dialog.findViewById<TextView>(R.id.yesButton)
        val no = dialog.findViewById<TextView>(R.id.noButton)
        yes.setOnClickListener {
            dialog.dismiss()
            activity.finish()
        }
        no.setOnClickListener { dialog.dismiss() }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dialog.dismiss()
                true
            } else {
                false
            }
        }
        dialog.show()
        no.requestFocus()
    }

    private fun widthFraction(activity: Activity, fraction: Float): Int =
        (activity.resources.displayMetrics.widthPixels * fraction).toInt()
}
