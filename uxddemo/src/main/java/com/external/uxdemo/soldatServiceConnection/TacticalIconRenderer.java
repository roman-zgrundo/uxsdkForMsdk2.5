package com.external.uxdemo.soldatServiceConnection;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;

public class TacticalIconRenderer {
    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static void draw(Canvas canvas, String xmlData, int size) {
        if (xmlData == null || xmlData.isEmpty()) return;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(Color.BLACK);

        canvas.save();
        // Центрируем и масштабируем (координаты в XML обычно от -50 до 50)
        canvas.translate(size / 2f, size / 2f);
        float scale = size / 120f;
        canvas.scale(scale, scale);

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xmlData));
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    String p = parser.getAttributeValue(null, "p");
                    if (p != null) {
                        float[] pts = parsePoints(p);
                        if ("line".equals(tag) && pts.length == 4) {
                            canvas.drawLine(pts[0], pts[1], pts[2], pts[3], paint);
                        } else if ("rhombus".equals(tag) && pts.length == 4) {
                            drawRhombus(canvas, pts[0], pts[1], pts[2], pts[3]);
                        } else if ("ellipse".equals(tag) && pts.length == 4) {
                            canvas.drawOval(pts[0], pts[1], pts[2], pts[3], paint);
                        }
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception ignored) {}
        canvas.restore();
    }

    private static float[] parsePoints(String p) {
        String[] split = p.split(" ");
        float[] pts = new float[split.length];
        for (int i = 0; i < split.length; i++) pts[i] = Float.parseFloat(split[i]);
        return pts;
    }

    private static void drawRhombus(Canvas c, float x1, float y1, float x2, float y2) {
        Path path = new Path();
        float midX = (x1 + x2) / 2;
        float midY = (y1 + y2) / 2;
        path.moveTo(midX, y1); path.lineTo(x2, midY);
        path.lineTo(midX, y2); path.lineTo(x1, midY);
        path.close();
        c.drawPath(path, paint);
    }
}