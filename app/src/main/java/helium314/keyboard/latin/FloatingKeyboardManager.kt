package helium314.keyboard.latin

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ResourceUtils

/**
 * Manages the floating keyboard within the IME's native TYPE_INPUT_METHOD window.
 * The keyboard frame is positioned dynamically inside InputView via translation coordinates
 * and wrapped with an interactive floating header bar (drag pill, close button, resize handle).
 */
class FloatingKeyboardManager(private val context: Context, private val latinIME: LatinIME) {

    companion object {
        private const val TAG = "FloatingKeyboardManager"
        private const val PREFS_NAME = "floating_keyboard_prefs"
        private const val PREF_X = "floating_x"
        private const val PREF_Y = "floating_y"
        private const val PREF_WIDTH = "floating_width"
        private const val PREF_SCALE = "floating_scale"
        private const val PREF_IS_ACTIVE = "floating_is_active"
        private const val FLOATING_WIDTH_FRACTION = 0.75f
        private const val HEADER_HEIGHT_DP = 28
        private const val CORNER_RADIUS_DP = 16f
    }

    private val prefs: SharedPreferences by lazy {
        DeviceProtectedUtils.getSharedPreferences(context, PREFS_NAME)
    }

    fun wasFloatingLastTime(): Boolean = prefs.getBoolean(PREF_IS_ACTIVE, false)

    var isFloating = false
        private set

    @Volatile
    var isDragging = false
        private set

    @Volatile
    var isResizing = false
        private set

    // Touch tracking for drag & resize
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialTransX = 0f
    private var initialTransY = 0f

    private var initialResizeTouchX = 0f
    private var initialResizeTouchY = 0f
    private var initialResizeTransX = 0f
    private var initialResizeTransY = 0f
    private var initialResizeWidth = 0
    private var initialResizeHeight = 0
    private var initialResizeScale = 1.0f

    private var headerBar: FrameLayout? = null

    fun getKeyboardFrame(): View? = latinIME.mInputView?.findViewById(R.id.main_keyboard_frame)

    /**
     * Returns the bounding rectangle of the floating keyboard in window coordinates,
     * or null if the keyboard is not currently floating.
     */
    fun getFloatingTouchableRect(): Rect? {
        val frame = getKeyboardFrame() ?: return null
        if (!isFloating || frame.visibility != View.VISIBLE || frame.width <= 0 || frame.height <= 0) {
            return null
        }
        val loc = IntArray(2)
        frame.getLocationInWindow(loc)
        return Rect(loc[0], loc[1], loc[0] + frame.width, loc[1] + frame.height)
    }

    fun show() {
        val frame = getKeyboardFrame() ?: return

        val dm = context.resources.displayMetrics
        val density = dm.density
        val minWidth = (dm.widthPixels * 0.40f).toInt()
        val maxWidth = (dm.widthPixels * 0.95f).toInt()
        val defaultWidth = (dm.widthPixels * FLOATING_WIDTH_FRACTION).toInt()
        val savedWidth = prefs.getInt(PREF_WIDTH, -1)
        val floatingWidth = (if (savedWidth != -1) savedWidth else defaultWidth).coerceIn(minWidth, maxWidth)
        val savedScale = prefs.getFloat(PREF_SCALE, 1.0f).coerceIn(0.5f, 1.8f)

        val colors = Settings.getValues().mColors
        val bgColor = colors.get(ColorType.MAIN_BACKGROUND)
        val textColor = colors.get(ColorType.KEY_TEXT)
        val cornerRadius = CORNER_RADIUS_DP * density
        val headerHeight = (HEADER_HEIGHT_DP * density).toInt()

        // Configure or create header bar inside main_keyboard_frame
        val headerContainer = frame.findViewById<FrameLayout>(R.id.floating_header_bar)
        if (headerContainer != null) {
            if (headerContainer.childCount == 0) {
                headerBar = createHeaderBar(headerHeight, bgColor, textColor, density, cornerRadius)
                headerContainer.addView(headerBar)
            }
            headerContainer.visibility = View.VISIBLE
        }

        // Configure FrameLayout layoutParams
        val lp = frame.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(
            floatingWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.width = floatingWidth
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        frame.layoutParams = lp

        // Calculate and clamp position
        val savedX = prefs.getInt(PREF_X, -1)
        val savedY = prefs.getInt(PREF_Y, -1)
        val maxX = (dm.widthPixels - floatingWidth).coerceAtLeast(0)
        val maxY = (dm.heightPixels - (220 * density).toInt()).coerceAtLeast(0)

        val posX = if (savedX != -1) savedX.coerceIn(0, maxX) else (dm.widthPixels - floatingWidth) / 2
        val posY = if (savedY != -1) savedY.coerceIn(0, maxY) else dm.heightPixels / 3

        frame.translationX = posX.toFloat()
        frame.translationY = posY.toFloat()

        // Background styling: rounded corners and elevation
        val bgDrawable = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadii = floatArrayOf(
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius,
                cornerRadius, cornerRadius
            )
        }
        frame.background = bgDrawable
        frame.clipToOutline = true
        frame.outlineProvider = ViewOutlineProvider.BACKGROUND
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            frame.elevation = 8f * density
        }

        isFloating = true
        prefs.edit().putBoolean(PREF_IS_ACTIVE, true).apply()

        // Set floating overrides and reload keyboard to recalculate key geometry
        ResourceUtils.setFloatingKeyboardWidth(floatingWidth)
        ResourceUtils.setFloatingKeyboardScale(savedScale)
        KeyboardSwitcher.getInstance().reloadKeyboard()

        latinIME.onFloatingKeyboardShown()
        Log.i(TAG, "Floating keyboard shown at ${floatingWidth}px width, scale $savedScale")
    }

    fun hide(showDockedKeyboard: Boolean = true) {
        if (!isFloating) return

        if (showDockedKeyboard) {
            prefs.edit().putBoolean(PREF_IS_ACTIVE, false).apply()
        }

        isFloating = false
        isDragging = false
        isResizing = false

        ResourceUtils.setFloatingKeyboardWidth(0)
        ResourceUtils.setFloatingKeyboardScale(0.0f)

        val frame = getKeyboardFrame()
        if (frame != null) {
            frame.findViewById<View>(R.id.floating_header_bar)?.visibility = View.GONE
            frame.translationX = 0f
            frame.translationY = 0f

            val lp = frame.layoutParams as? FrameLayout.LayoutParams
            if (lp != null) {
                lp.gravity = Gravity.BOTTOM
                lp.width = FrameLayout.LayoutParams.MATCH_PARENT
                lp.height = FrameLayout.LayoutParams.WRAP_CONTENT
                frame.layoutParams = lp
            }

            Settings.getValues().mColors.setBackground(frame, ColorType.MAIN_BACKGROUND)
            frame.clipToOutline = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                frame.elevation = 0f
            }
        }

        KeyboardSwitcher.getInstance().reloadKeyboard()
        latinIME.onFloatingKeyboardHidden(showDockedKeyboard)
        Log.i(TAG, "Floating keyboard hidden, docked mode restored")
    }

    fun toggle() {
        if (isFloating) {
            hide()
        } else {
            show()
        }
    }

    fun resetDragAndResizeState() {
        if (isDragging || isResizing) {
            isDragging = false
            isResizing = false
            latinIME.requestInsetsUpdate()
        }
    }

    fun onInputViewRecreated(newInputView: View) {
        if (!isFloating) return
        Log.i(TAG, "Input view recreated while floating, reapplying floating layout")
        show()
    }

    fun destroy() {
        if (isFloating) {
            ResourceUtils.setFloatingKeyboardWidth(0)
            ResourceUtils.setFloatingKeyboardScale(0.0f)
            isFloating = false
            isDragging = false
            isResizing = false
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun createHeaderBar(
        height: Int,
        bgColor: Int,
        textColor: Int,
        density: Float,
        cornerRadius: Float
    ): FrameLayout {
        val dm = context.resources.displayMetrics
        val minWidth = (dm.widthPixels * 0.40f).toInt()
        val maxWidth = (dm.widthPixels * 0.95f).toInt()
        val minHeight = (120 * density).toInt()
        val maxHeight = (dm.heightPixels * 0.75f).toInt()

        val headerBar = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height
            )
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadii = floatArrayOf(
                    cornerRadius, cornerRadius,
                    cornerRadius, cornerRadius,
                    0f, 0f,
                    0f, 0f
                )
            }
        }

        // Sleek drag handle pill in center
        val pillWidth = (44 * density).toInt()
        val pillHeight = (5 * density).toInt()
        val defaultPillColor = (textColor and 0x00FFFFFF) or 0x66000000.toInt()
        val activePillColor = (textColor and 0x00FFFFFF) or 0xCC000000.toInt()
        val dragHandleBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = 2.5f * density
            setColor(defaultPillColor)
        }
        val dragHandle = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(pillWidth, pillHeight).apply {
                gravity = Gravity.CENTER
            }
            background = dragHandleBg
            contentDescription = context.getString(R.string.floating_keyboard_drag_handle)
        }
        headerBar.addView(dragHandle)

        // Close button (Top-Right)
        val closeBtnSize = (height * 0.75f).toInt()
        val closePadding = (3 * density).toInt()
        val closeBtn = ImageButton(context).apply {
            layoutParams = FrameLayout.LayoutParams(closeBtnSize, closeBtnSize).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                marginEnd = (6 * density).toInt()
            }
            setPadding(closePadding, closePadding, closePadding, closePadding)
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(textColor)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = 6 * density
                setColor((textColor and 0x00FFFFFF) or 0x1A000000.toInt())
            }
            contentDescription = "Close floating keyboard"
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setOnClickListener { hide(showDockedKeyboard = true) }
        }
        headerBar.addView(closeBtn)

        // Inverted L-shaped Corner Pill Resize Button (Top-Left)
        val btnSize = closeBtnSize
        val defaultAlpha = 0x66000000.toInt()
        val activeAlpha = 0xEE000000.toInt()
        val defaultBgColor = (textColor and 0x00FFFFFF) or 0x1F000000.toInt()
        val activeBgColor = (textColor and 0x00FFFFFF) or 0x55000000.toInt()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (textColor and 0x00FFFFFF) or defaultAlpha
            strokeWidth = 3.5f * density
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }

        val resizeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = 6 * density
            setColor(defaultBgColor)
        }

        val resizeBtn = object : View(context) {
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                val pad = 6.5f * density
                val oval = RectF(pad, pad, w - pad, h - pad)
                canvas.drawArc(oval, 180f, 90f, false, paint)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                marginStart = (6 * density).toInt()
            }
            background = resizeBg
            contentDescription = "Resize floating keyboard"
        }

        resizeBtn.setOnTouchListener { _, event ->
            val frame = getKeyboardFrame() ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isResizing = true
                    initialResizeTouchX = event.rawX
                    initialResizeTouchY = event.rawY
                    initialResizeTransX = frame.translationX
                    initialResizeTransY = frame.translationY
                    initialResizeWidth = frame.width.takeIf { it > 0 } ?: ResourceUtils.getFloatingKeyboardWidth()
                    initialResizeHeight = frame.height
                    initialResizeScale = ResourceUtils.getFloatingKeyboardScale().let { if (it > 0f) it else 1.0f }

                    paint.color = (textColor and 0x00FFFFFF) or activeAlpha
                    paint.strokeWidth = 4.5f * density
                    resizeBg.setColor(activeBgColor)
                    resizeBtn.invalidate()
                    latinIME.requestInsetsUpdate()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialResizeTouchX).toInt()
                    val dy = (event.rawY - initialResizeTouchY).toInt()

                    val targetWidth = (initialResizeWidth - dx).coerceIn(minWidth, maxWidth)
                    val targetHeight = (initialResizeHeight - dy).coerceIn(minHeight, maxHeight)

                    val effectiveDx = initialResizeWidth - targetWidth
                    val effectiveDy = initialResizeHeight - targetHeight

                    val inputView = latinIME.mInputView
                    val maxW = ((inputView?.width ?: dm.widthPixels) - targetWidth).coerceAtLeast(0)
                    val maxH = ((inputView?.height ?: dm.heightPixels) - targetHeight).coerceAtLeast(0)

                    val newX = (initialResizeTransX - effectiveDx).coerceIn(0f, maxW.toFloat())
                    val newY = (initialResizeTransY - effectiveDy).coerceIn(0f, maxH.toFloat())

                    frame.translationX = newX
                    frame.translationY = newY

                    val lp = frame.layoutParams
                    lp.width = targetWidth
                    frame.layoutParams = lp
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isResizing = false
                    paint.color = (textColor and 0x00FFFFFF) or defaultAlpha
                    paint.strokeWidth = 3.5f * density
                    resizeBg.setColor(defaultBgColor)
                    resizeBtn.invalidate()

                    val finalWidth = frame.layoutParams.width
                    val heightRatio = if (initialResizeHeight > 0) frame.height.toFloat() / initialResizeHeight else 1.0f
                    val finalScale = (initialResizeScale * heightRatio).coerceIn(0.5f, 1.8f)

                    prefs.edit()
                        .putInt(PREF_X, frame.translationX.toInt())
                        .putInt(PREF_Y, frame.translationY.toInt())
                        .putInt(PREF_WIDTH, finalWidth)
                        .putFloat(PREF_SCALE, finalScale)
                        .apply()

                    ResourceUtils.setFloatingKeyboardWidth(finalWidth)
                    ResourceUtils.setFloatingKeyboardScale(finalScale)
                    KeyboardSwitcher.getInstance().reloadKeyboard()
                    latinIME.requestInsetsUpdate()
                    true
                }
                else -> false
            }
        }
        headerBar.addView(resizeBtn)

        // Drag listener on the entire header bar
        headerBar.setOnTouchListener { _, event ->
            val frame = getKeyboardFrame() ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialTransX = frame.translationX
                    initialTransY = frame.translationY
                    dragHandleBg.setColor(activePillColor)
                    latinIME.requestInsetsUpdate()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    val inputView = latinIME.mInputView
                    val maxW = ((inputView?.width ?: dm.widthPixels) - frame.width).coerceAtLeast(0)
                    val maxH = ((inputView?.height ?: dm.heightPixels) - frame.height).coerceAtLeast(0)

                    val newX = (initialTransX + dx).coerceIn(0f, maxW.toFloat())
                    val newY = (initialTransY + dy).coerceIn(0f, maxH.toFloat())

                    frame.translationX = newX
                    frame.translationY = newY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    dragHandleBg.setColor(defaultPillColor)
                    prefs.edit()
                        .putInt(PREF_X, frame.translationX.toInt())
                        .putInt(PREF_Y, frame.translationY.toInt())
                        .apply()
                    latinIME.requestInsetsUpdate()
                    true
                }
                else -> false
            }
        }

        return headerBar
    }
}
