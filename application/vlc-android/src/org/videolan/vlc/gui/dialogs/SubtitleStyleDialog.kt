package org.videolan.vlc.gui.dialogs

import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import org.videolan.resources.SubtitlePreset
import org.videolan.resources.SubtitleStyle
import org.videolan.tools.KEY_SUBTITLES_VERTICAL_OFFSET
import org.videolan.tools.Settings
import org.videolan.tools.dp
import org.videolan.vlc.R
import org.videolan.vlc.gui.video.SubtitlePositionHelper
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.gui.view.SubtitlePreviewView

/**
 * In-player subtitle style panel. Every change is written to the same preferences as the subtitles
 * settings screen right away.
 *
 * libVLC only reads the style when its instance is created, so when the panel is closed after a style
 * change the player is reloaded once ([VideoPlayerActivity.reloadForSubtitleStyle]). The position is
 * applied live and never needs a reload.
 */
class SubtitleStyleDialog : VLCBottomSheetDialogFragment() {

    private lateinit var root: View
    private lateinit var settings: SharedPreferences
    private lateinit var preview: SubtitlePreviewView
    private lateinit var sizeBar: SeekBar
    private lateinit var boldSwitch: SwitchCompat
    private lateinit var textColors: LinearLayout
    private lateinit var backgroundSwitch: SwitchCompat
    private lateinit var backgroundOptions: View
    private lateinit var backgroundColors: LinearLayout
    private lateinit var backgroundOpacityBar: SeekBar
    private lateinit var outlineSwitch: SwitchCompat
    private lateinit var shadowSwitch: SwitchCompat
    private lateinit var positionBar: SeekBar

    /** The state and the reload rules, see [SubtitleStyleEditor] */
    private lateinit var editor: SubtitleStyleEditor
    private val style get() = editor.style
    private val offset get() = editor.offset

    /** A style change made before a rotation destroyed the previous panel, still waiting for its reload */
    private var carriedChange = false

    /** Set while the widgets are updated from code, to ignore the listeners they trigger */
    private var updating = false

    // Opens low on the screen so the video, and the real subtitles moving, stay visible above the panel
    override fun getDefaultState(): Int = BottomSheetBehavior.STATE_HALF_EXPANDED

    // The panel does not depend on the orientation and must not be re-shown (it would count as a close)
    override fun needToManageOrientation(): Boolean = false

    override fun initialFocusedView(): View = root.findViewById(R.id.subtitle_style_title)

    override fun allowRemote() = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        carriedChange = savedInstanceState?.getBoolean(STATE_STYLE_CHANGED) ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_STYLE_CHANGED, if (::editor.isInitialized) editor.reloadNeeded else carriedChange)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        root = inflate(inflater, container, R.layout.subtitle_style_dialog)!!
        settings = Settings.getInstance(requireContext())
        editor = SubtitleStyleEditor(
            SubtitleStyle.read(
                { key, def -> settings.getString(key, def) ?: def },
                { key, def -> settings.getInt(key, def) },
                { key, def -> settings.getBoolean(key, def) }
            ),
            settings.getFloat(KEY_SUBTITLES_VERTICAL_OFFSET, 0f),
            carriedChange
        )

        preview = root.findViewById(R.id.subtitle_style_preview)
        sizeBar = root.findViewById(R.id.subtitle_style_size)
        boldSwitch = root.findViewById(R.id.subtitle_style_bold)
        textColors = root.findViewById(R.id.subtitle_style_text_colors)
        backgroundSwitch = root.findViewById(R.id.subtitle_style_background)
        backgroundOptions = root.findViewById(R.id.subtitle_style_background_options)
        backgroundColors = root.findViewById(R.id.subtitle_style_background_colors)
        backgroundOpacityBar = root.findViewById(R.id.subtitle_style_background_opacity)
        outlineSwitch = root.findViewById(R.id.subtitle_style_outline)
        shadowSwitch = root.findViewById(R.id.subtitle_style_shadow)
        positionBar = root.findViewById(R.id.subtitle_style_position)

        positionBar.max = POSITION_STEPS
        buildPresets(root.findViewById(R.id.subtitle_style_presets))
        root.findViewById<View>(R.id.subtitle_style_reset).setOnClickListener {
            applyStyle(SubtitleStyle.DEFAULT)
            applyOffset(0f)
            refresh()
        }

        sizeBar.setOnSeekBarChangeListener(onUserProgress { applyStyle(style.copy(size = SubtitleStyle.SIZE_VALUES[it])) })
        backgroundOpacityBar.setOnSeekBarChangeListener(onUserProgress { applyStyle(style.copy(backgroundOpacity = it + MIN_OPACITY)) })
        positionBar.setOnSeekBarChangeListener(onUserProgress {
            applyOffset(SubtitlePositionHelper.MIN_OFFSET + it / POSITION_STEPS.toFloat() * (SubtitlePositionHelper.MAX_OFFSET - SubtitlePositionHelper.MIN_OFFSET), fromSlider = true)
        })
        boldSwitch.setOnCheckedChangeListener { _, checked -> if (!updating) applyStyle(style.copy(bold = checked)) }
        backgroundSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updating) {
                applyStyle(style.copy(backgroundEnabled = checked))
                refresh()
            }
        }
        outlineSwitch.setOnCheckedChangeListener { _, checked -> if (!updating) applyStyle(style.copy(outlineEnabled = checked)) }
        shadowSwitch.setOnCheckedChangeListener { _, checked -> if (!updating) applyStyle(style.copy(shadowEnabled = checked)) }
        preview.onOffsetDragged = { applyOffset(it, fromPreview = true) }

        refresh()
        return root
    }

    override fun onStart() {
        super.onStart()
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            BottomSheetBehavior.from(it).apply {
                isFitToContents = false
                halfExpandedRatio = HALF_EXPANDED_RATIO
            }
        }
        // The video behind must stay visible to judge the position
        dialog?.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0f)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        val activity = activity as? VideoPlayerActivity ?: return
        if (!::editor.isInitialized) return // dismissed before the view was created
        // A rotation destroys the panel without the user closing it: the flag is restored with the new one
        if (editor.reloadNeeded && !activity.isChangingConfigurations) {
            editor.markReloaded()
            activity.reloadForSubtitleStyle()
        }
    }

    private fun buildPresets(container: LinearLayout) {
        SubtitlePreset.entries.forEach { preset ->
            val chip = Chip(requireContext()).apply {
                setText(presetName(preset))
                isCheckable = false
                setOnClickListener {
                    applyStyle(preset.style)
                    refresh()
                }
            }
            container.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = 8.dp })
        }
    }

    private fun presetName(preset: SubtitlePreset): Int = when (preset) {
        SubtitlePreset.DEFAULT -> R.string.subtitles_preset_default
        SubtitlePreset.LARGE -> R.string.subtitles_preset_default_large
        SubtitlePreset.LARGEST_BOXED -> R.string.subtitles_preset_default_largest
        SubtitlePreset.BOXED -> R.string.subtitles_preset_white_background
        SubtitlePreset.YELLOW -> R.string.subtitles_yellow
        SubtitlePreset.YELLOW_BOXED -> R.string.subtitles_yellow_background
    }

    /** Save [newStyle] to the preferences and flag that a reload is needed. Callers call [refresh] when widgets must follow */
    private fun applyStyle(newStyle: SubtitleStyle) {
        if (!editor.setStyle(newStyle)) return
        settings.edit {
            newStyle.toPrefValues().forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Boolean -> putBoolean(key, value)
                }
            }
        }
        preview.style = newStyle
    }

    /** Save the position and move the real subtitles right away, no reload needed */
    private fun applyOffset(newOffset: Float, fromSlider: Boolean = false, fromPreview: Boolean = false) {
        editor.setOffset(newOffset)
        settings.edit { putFloat(KEY_SUBTITLES_VERTICAL_OFFSET, offset) }
        (activity as? VideoPlayerActivity)?.applySubtitleOffset()
        // Keep the other position widgets in sync without rebuilding everything on each drag frame
        if (!fromPreview) preview.offset = offset
        if (!fromSlider) {
            updating = true
            positionBar.progress = positionProgress()
            updating = false
        }
    }

    private fun positionProgress(): Int =
        ((offset - SubtitlePositionHelper.MIN_OFFSET) / (SubtitlePositionHelper.MAX_OFFSET - SubtitlePositionHelper.MIN_OFFSET) * POSITION_STEPS).toInt()

    /** Update every widget from the current [style] and [offset] */
    private fun refresh() {
        updating = true
        try {
            preview.style = style
            preview.offset = offset
            sizeBar.progress = style.sizeIndex
            boldSwitch.isChecked = style.bold
            backgroundSwitch.isChecked = style.backgroundEnabled
            backgroundOptions.visibility = if (style.backgroundEnabled) View.VISIBLE else View.GONE
            backgroundOpacityBar.progress = (style.backgroundOpacity - MIN_OPACITY).coerceIn(0, backgroundOpacityBar.max)
            outlineSwitch.isChecked = style.outlineEnabled
            shadowSwitch.isChecked = style.shadowEnabled
            positionBar.progress = positionProgress()
            buildSwatches(textColors, style.color) { applyStyle(style.copy(color = it)); refresh() }
            buildSwatches(backgroundColors, style.backgroundColor) { applyStyle(style.copy(backgroundColor = it)); refresh() }
        } finally {
            updating = false
        }
    }

    private fun buildSwatches(container: LinearLayout, selected: Int, onPick: (Int) -> Unit) {
        container.removeAllViews()
        val accent = TypedValue().also { requireContext().theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, it, true) }.data
        SWATCHES.forEach { color ->
            val isSelected = (color and 0xFFFFFF) == (selected and 0xFFFFFF)
            val swatch = View(requireContext()).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(if (isSelected) 3.dp else 1.dp, if (isSelected) accent else 0x66808080)
                }
                isFocusable = true
                isClickable = true
                contentDescription = String.format("#%06X", color and 0xFFFFFF)
                setOnClickListener { onPick(color) }
            }
            container.addView(swatch, LinearLayout.LayoutParams(36.dp, 36.dp).apply { setMargins(4.dp, 4.dp, 4.dp, 4.dp) })
        }
    }

    /** A listener reporting the progress only when the user moves the bar, not when it is set by code */
    private fun onUserProgress(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser && !updating) onChange(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {}
        override fun onStopTrackingTouch(seekBar: SeekBar) {}
    }

    companion object {
        const val TAG = "VLC/SubtitleStyleDialog"
        private const val STATE_STYLE_CHANGED = "style_changed"
        private const val POSITION_STEPS = 500
        private const val MIN_OPACITY = 50

        /** Fraction of the screen height the panel covers when it first opens (the user can drag it up) */
        private const val HALF_EXPANDED_RATIO = 0.4f

        private val SWATCHES = intArrayOf(
            SubtitleStyle.WHITE,
            SubtitleStyle.YELLOW,
            0xFF00FFFF.toInt(),
            0xFF00FF00.toInt(),
            0xFFFFA500.toInt(),
            0xFFFF4040.toInt(),
            SubtitleStyle.BLACK
        )
    }
}
