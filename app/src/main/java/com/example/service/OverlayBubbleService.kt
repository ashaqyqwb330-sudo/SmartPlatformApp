package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.example.database.SmartRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayBubbleService : Service() {
    private var windowManager: WindowManager? = null
    private var bubbleLayout: FrameLayout? = null
    private lateinit var repository: SmartRepository
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        repository = SmartRepository(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // تصميم الفقاقيع العائمة الذهبية برمجياً بدلاً من XML
        val context = this
        bubbleLayout = FrameLayout(context)

        // تحديد الحجم بـ 60dp
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 60f, resources.displayMetrics
        ).toInt()

        // إنشاء شكل دائري ذهبي مع تدرج لوني فخم
        val goldBackground = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#FFE082"), // ذهبي فاتح
                Color.parseColor("#FFB300"), // ذهبي غامق
                Color.parseColor("#FF8F00")  // ذهبي عسلي عميق
            )
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(2, Color.parseColor("#FFD54F"))
        }

        bubbleLayout?.background = goldBackground

        // إضافة نص أو أيقونة بالمنتصف
        val textView = TextView(context).apply {
            text = "⚡"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        val textParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        bubbleLayout?.addView(textView, textParams)

        // إعدادات WindowManager اللازمة لعرض نافذة تراكب عائمة فوق التطبيقات الأخرى
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        // إضافة السحب واللمس
        bubbleLayout?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDrag = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDrag = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()

                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDrag = true
                        }

                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(bubbleLayout, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDrag) {
                            // نقرة واحدة: توليد التقرير ونسخه للحافظة تلقائياً
                            onBubbleClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager?.addView(bubbleLayout, params)
            scope.launch {
                repository.addLog("system", "تم تفعيل الفقاعة العائمة الذهبية السريعة.", "success")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "فشل تفعيل الفقاعة العائمة: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun onBubbleClick() {
        scope.launch {
            repository.addLog("system", "تم النقر على الفقاعة العائمة لتوليد تقرير شجرة سريع.", "info")
            val report = repository.runFastTreeReport()
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@OverlayBubbleService,
                    "📋 تم نسخ تقرير الشجرة الفوري للحافظة عبر الاختصار العائم!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bubbleLayout != null) {
            try {
                windowManager?.removeView(bubbleLayout)
            } catch (e: Exception) {
                // View already removed
            }
        }
        scope.cancel()
    }
}
