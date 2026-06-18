package com.car.mp3player.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.car.mp3player.R
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.model.AppThemePreset
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial

object AppThemeManager {
    data class Palette(
        val background: Int,
        val surface: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val bottomNavBg: Int,
        val primary: Int
    )

    fun palette(context: Context, settings: SettingsRepository): Palette {
        return when (settings.appTheme()) {
            AppThemePreset.ZELDA -> Palette(
                background = ContextCompat.getColor(context, R.color.zelda_background),
                surface = ContextCompat.getColor(context, R.color.zelda_surface),
                textPrimary = ContextCompat.getColor(context, R.color.zelda_text_primary),
                textSecondary = ContextCompat.getColor(context, R.color.zelda_text_secondary),
                bottomNavBg = ContextCompat.getColor(context, R.color.zelda_bottom_nav),
                primary = ContextCompat.getColor(context, R.color.zelda_primary)
            )
            AppThemePreset.MARIO -> Palette(
                background = ContextCompat.getColor(context, R.color.mario_background),
                surface = ContextCompat.getColor(context, R.color.mario_surface),
                textPrimary = ContextCompat.getColor(context, R.color.mario_text_primary),
                textSecondary = ContextCompat.getColor(context, R.color.mario_text_secondary),
                bottomNavBg = ContextCompat.getColor(context, R.color.mario_bottom_nav),
                primary = ContextCompat.getColor(context, R.color.mario_primary)
            )
            AppThemePreset.NETEASE -> Palette(
                background = ContextCompat.getColor(context, R.color.background),
                surface = ContextCompat.getColor(context, R.color.surface),
                textPrimary = ContextCompat.getColor(context, R.color.text_primary),
                textSecondary = ContextCompat.getColor(context, R.color.text_secondary),
                bottomNavBg = ContextCompat.getColor(context, R.color.bottom_nav_bg),
                primary = ContextCompat.getColor(context, R.color.netease_red)
            )
        }
    }

    fun applyBottomNav(nav: BottomNavigationView, palette: Palette) {
        nav.setBackgroundColor(palette.bottomNavBg)
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val colors = intArrayOf(palette.primary, palette.textSecondary)
        nav.itemIconTintList = ColorStateList(states, colors)
        nav.itemTextColor = ColorStateList(states, colors)
    }

    fun applyCard(card: MaterialCardView, palette: Palette) {
        card.setCardBackgroundColor(palette.surface)
    }

    fun applyFragmentRoot(root: View, palette: Palette) {
        root.setBackgroundColor(palette.background)
        visitViews(root) { view ->
            when (view) {
                is MaterialCardView -> view.setCardBackgroundColor(palette.surface)
                is TextView -> applyTextColorIfThemed(view, palette)
                is RadioButton -> applyTextColorIfThemed(view, palette)
                is SwitchMaterial -> applyTextColorIfThemed(view, palette)
            }
        }
    }

    private fun applyTextColorIfThemed(view: TextView, palette: Palette) {
        val context = view.context
        val color = view.currentTextColor
        when (color) {
            ContextCompat.getColor(context, R.color.text_primary),
            ContextCompat.getColor(context, R.color.zelda_text_primary),
            ContextCompat.getColor(context, R.color.mario_text_primary) ->
                view.setTextColor(palette.textPrimary)
            ContextCompat.getColor(context, R.color.text_secondary),
            ContextCompat.getColor(context, R.color.text_hint),
            ContextCompat.getColor(context, R.color.zelda_text_secondary),
            ContextCompat.getColor(context, R.color.mario_text_secondary) ->
                view.setTextColor(palette.textSecondary)
        }
    }

    private fun visitViews(view: View, block: (View) -> Unit) {
        block(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                visitViews(view.getChildAt(i), block)
            }
        }
    }
}
