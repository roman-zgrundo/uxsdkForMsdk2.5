package com.external.uxdemo.soldatServiceConnection

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.*
import android.widget.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class ClassifierUIHelper(
    private val context: Context,
    private val container: LinearLayout,
    private val soldatManager: SoldatManager,
    private val onObjectSent: () -> Unit
) {
    private val basePath = "classifier/asu_2_v1/"

    fun fillPanel(lat: Double, lon: Double) {
        container.removeAllViews()
        var currentGroup: LinearLayout? = null

        try {
            val inputStream = context.assets.open("${basePath}_map_item_classifier.xml")
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "group" -> {
                            val name = parser.getAttributeValue(null, "name")
                            currentGroup = addGroupHeader(name)
                        }
                        "type" -> {
                            val id = parser.getAttributeValue(null, "id").toInt()
                            val name = parser.getAttributeValue(null, "name")
                            val src = parser.getAttributeValue(null, "src")
                            currentGroup?.let { addClassifierButton(it, id, name, basePath + src, lat, lon) }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("ClassifierUI", "XML Error: ${e.message}")
        }
    }

    private fun addGroupHeader(title: String): LinearLayout {
        val tv = TextView(context).apply {
            text = "▼  ${title.uppercase()}"
            setTextColor(Color.parseColor("#FFCC00"))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(40, 30, 40, 30)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8f
                setColor(Color.parseColor("#444444"))
                setStroke(2, Color.parseColor("#666666"))
            }
        }

        val subContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 0, 0, 0)
            visibility = View.GONE
        }

        tv.setOnClickListener {
            val isCollapsed = subContainer.visibility == View.GONE
            subContainer.visibility = if (isCollapsed) View.VISIBLE else View.GONE
            tv.text = (if (isCollapsed) "▲  " else "▼  ") + title.uppercase()
        }

        container.addView(tv)
        container.addView(subContainer)
        return subContainer
    }

    private fun addClassifierButton(group: LinearLayout, id: Int, name: String, iconPath: String, lat: Double, lon: Double) {
        val btn = Button(context).apply {
            text = name
            setTextColor(Color.parseColor("#EEEEEE"))
            isAllCaps = false
            textSize = 13f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(30, 25, 30, 25)
        }

        // Иконка
        try {
            val xmlContent = context.assets.open(iconPath).bufferedReader().use { it.readText() }
            val bitmap = Bitmap.createBitmap(55, 55, Bitmap.Config.ARGB_8888)
            TacticalIconRenderer.draw(Canvas(bitmap), xmlContent, 55)
            btn.setCompoundDrawablesWithIntrinsicBounds(BitmapDrawable(context.resources, bitmap).apply { setTint(Color.parseColor("#00BFFF")) }, null, null, null)
            btn.setCompoundDrawablePadding(35)
        } catch (e: Exception) { }

        btn.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            soldatManager.sendObject(lat, lon, id, name)
            onObjectSent()
        }
        group.addView(btn)
    }
}