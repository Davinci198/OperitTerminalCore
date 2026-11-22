package com.ai.assistance.operit.terminal.utils

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import com.ai.assistance.operit.terminal.view.canvas.RenderConfig
import com.ai.assistance.operit.terminal.R
import java.io.File

/**
 * 终端字体配置管理器
 * 管理终端字体的设置，包括字体大小、字体路径、字体名称等
 */
class TerminalFontConfigManager private constructor(context: Context) {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        "terminal_font_prefs",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_FONT_SIZE_UNIT = "font_size_unit"
        private const val KEY_FONT_PATH = "font_path"
        private const val KEY_FONT_NAME = "font_name"
        private const val KEY_TARGET_FPS = "target_fps"

        // Dimensiunea se ține în sp, ca să respecte scara de font a sistemului.
        // Valoarea veche (px, implicit 42) folosea aceeași cheie, deci o migrăm o dată.
        private const val UNIT_SP = "sp"
        private const val DEFAULT_FONT_SIZE_SP = 15f
        private const val MIN_FONT_SIZE_SP = 8f
        private const val MAX_FONT_SIZE_SP = 32f
        private const val LEGACY_DEFAULT_FONT_SIZE_PX = 42f

        /** Coloanele care trebuie să încapă pe lățime când se apasă „potrivește”. */
        const val FIT_COLUMNS = 80
        private const val DEFAULT_TARGET_FPS = 60
        private const val MIN_TARGET_FPS = 15
        private const val MAX_TARGET_FPS = 120
        
        @Volatile
        private var INSTANCE: TerminalFontConfigManager? = null
        
        fun getInstance(context: Context): TerminalFontConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TerminalFontConfigManager(context.applicationContext).also { 
                    INSTANCE = it 
                }
            }
        }
    }

    /**
     * 加载完整的渲染配置
     */
    fun loadRenderConfig(): RenderConfig {
        return RenderConfig(
            fontSize = getFontSizePx(),
            typeface = loadTypeface(),
            targetFps = getTargetFps()
            // nerdFontPath 和其他参数可以使用 RenderConfig 的默认值
        )
    }

    /**
     * 根据保存的路径或名称加载字体
     *
     * 优先使用用户指定的字体路径/名称；当用户未显式指定时，
     * 默认回退到内置的 JetBrains Mono Nerd Font，确保是真正等宽的字体，
     * 避免各家 ROM 对 "monospace" 映射不一致导致的对齐问题。
     */
    private fun loadTypeface(): Typeface {
        val fontPath = getFontPath()
        val fontName = getFontName()

        return try {
            // 1. 用户显式指定了字体文件路径
            fontPath?.let { path ->
                val file = File(path)
                if (file.exists() && file.isFile) {
                    return Typeface.createFromFile(file)
                }
            }

            // 2. 用户显式指定了系统字体名称
            fontName?.let { name ->
                return when (name.lowercase()) {
                    // 这里仍然允许用户强制使用系统字体
                    "monospace", "mono" -> Typeface.MONOSPACE
                    "serif" -> Typeface.SERIF
                    "sans-serif", "sans" -> Typeface.SANS_SERIF
                    else -> Typeface.create(name, Typeface.NORMAL)
                }
            }

            // 3. 未指定任何字体时，统一使用内置 JetBrains Mono Nerd Font（真·等宽）
            appContext.resources.getFont(R.font.jetbrains_mono_nerd_font_regular)
        } catch (e: Exception) {
            // 兜底：如果资源加载或自定义字体失败，仍然回退到系统 MONOSPACE
            Typeface.MONOSPACE
        }
    }

    /** Cât de mare e un sp în pixeli, ținând cont și de scara de font a sistemului. */
    private val spScale: Float
        get() =
            appContext.resources.displayMetrics.density *
                appContext.resources.configuration.fontScale

    /**
     * 获取字体大小（sp）
     *
     * Valorile scrise de versiunile anterioare erau pixeli, deci la prima citire le
     * convertim în sp și marcăm unitatea; altfel un 42 vechi ar deveni 42sp (uriaș).
     */
    fun getFontSize(): Float {
        val stored = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE_SP)
        if (prefs.getString(KEY_FONT_SIZE_UNIT, null) == UNIT_SP) {
            return stored.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        }
        val legacyPx = if (stored > 0f) stored else LEGACY_DEFAULT_FONT_SIZE_PX
        val migrated = (legacyPx / spScale).coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        prefs
            .edit()
            .putFloat(KEY_FONT_SIZE, migrated)
            .putString(KEY_FONT_SIZE_UNIT, UNIT_SP)
            .apply()
        return migrated
    }

    /**
     * 设置字体大小（sp）
     */
    fun setFontSize(sizeSp: Float) {
        prefs
            .edit()
            .putFloat(KEY_FONT_SIZE, sizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP))
            .putString(KEY_FONT_SIZE_UNIT, UNIT_SP)
            .apply()
    }

    /** Dimensiunea în pixeli, adică ce primește motorul de randare. */
    fun getFontSizePx(): Float = getFontSize() * spScale

    /**
     * Împarte lățimea dată în [FIT_COLUMNS] coloane egale și întoarce mărimea în sp
     * care ar face textul să încapă exact. Se bazează pe lățimea reală a glifei „M”
     * din fontul activ, deci funcționează și cu un font Nerd instalat de utilizator.
     */
    fun fitToWidthSp(widthPx: Float, columns: Int = FIT_COLUMNS): Float {
        if (widthPx <= 0f || columns <= 0) return getFontSize()
        val paint = android.graphics.Paint().apply {
            typeface = loadTypeface()
            textSize = 100f
        }
        val ratio = paint.measureText("M") / 100f
        if (ratio <= 0f) return getFontSize()
        val pxPerCell = widthPx / columns
        return (pxPerCell / ratio / spScale).coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
    }
    
    /**
     * 获取字体文件路径
     */
    fun getFontPath(): String? {
        val path = prefs.getString(KEY_FONT_PATH, null)
        return if (path.isNullOrBlank()) null else path
    }
    
    /**
     * 设置字体文件路径
     */
    fun setFontPath(path: String?) {
        prefs.edit().putString(KEY_FONT_PATH, path).apply()
    }
    
    /**
     * 获取系统字体名称
     */
    fun getFontName(): String? {
        val name = prefs.getString(KEY_FONT_NAME, null)
        return if (name.isNullOrBlank()) null else name
    }
    
    /**
     * 设置系统字体名称（如 "monospace", "serif", "sans-serif"）
     */
    fun setFontName(name: String?) {
        prefs.edit().putString(KEY_FONT_NAME, name).apply()
    }

    /**
     * 获取目标帧率
     */
    fun getTargetFps(): Int {
        return prefs.getInt(KEY_TARGET_FPS, DEFAULT_TARGET_FPS)
            .coerceIn(MIN_TARGET_FPS, MAX_TARGET_FPS)
    }

    /**
     * 设置目标帧率
     */
    fun setTargetFps(fps: Int) {
        prefs.edit()
            .putInt(KEY_TARGET_FPS, fps.coerceIn(MIN_TARGET_FPS, MAX_TARGET_FPS))
            .apply()
    }
    
    /**
     * 清除所有字体设置，恢复默认
     */
    fun resetToDefault() {
        prefs.edit()
            .remove(KEY_FONT_SIZE)
            .remove(KEY_FONT_SIZE_UNIT)
            .remove(KEY_FONT_PATH)
            .remove(KEY_FONT_NAME)
            .remove(KEY_TARGET_FPS)
            .apply()
    }
}

