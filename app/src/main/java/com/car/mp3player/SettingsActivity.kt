package com.car.mp3player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.databinding.ActivitySettingsBinding
import com.car.mp3player.model.LrcChar
import com.car.mp3player.model.LrcLine
import com.car.mp3player.model.LyricState

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: SettingsRepository

    private val overlayPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SettingsRepository(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings)

        binding.fontSizeSlider.value = settings.fontSizeSp
        when (settings.highlightColor) {
            0xFF1DB954.toInt() -> binding.colorGreen.isChecked = true
            0xFF00E5FF.toInt() -> binding.colorCyan.isChecked = true
            0xFFFF4081.toInt() -> binding.colorPink.isChecked = true
            else -> binding.colorGold.isChecked = true
        }
        when (settings.overlayPosition) {
            SettingsRepository.POSITION_TOP -> binding.posTop.isChecked = true
            SettingsRepository.POSITION_BOTTOM -> binding.posBottom.isChecked = true
            else -> binding.posCenter.isChecked = true
        }

        showPreview()
        binding.fontSizeSlider.addOnChangeListener { _, _, _ -> showPreview() }
        binding.highlightColorGroup.setOnCheckedChangeListener { _, _ -> showPreview() }

        binding.btnGrantOverlay.setOnClickListener {
            overlayPermission.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        binding.btnPreviewOverlay.setOnClickListener {
            saveSettings()
            LyricsOverlayService.start(this)
            LyricsOverlayService.updateLyrics(this, previewState())
            Toast.makeText(this, "已在悬浮窗预览，请切到桌面或地图查看", Toast.LENGTH_LONG).show()
        }
        binding.btnSave.setOnClickListener {
            saveSettings()
            LyricsOverlayService.refresh(this)
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun saveSettings() {
        settings.fontSizeSp = binding.fontSizeSlider.value
        settings.highlightColor = when (binding.highlightColorGroup.checkedRadioButtonId) {
            R.id.colorGreen -> 0xFF1DB954.toInt()
            R.id.colorCyan -> 0xFF00E5FF.toInt()
            R.id.colorPink -> 0xFFFF4081.toInt()
            else -> 0xFFFFD54F.toInt()
        }
        settings.overlayPosition = when (binding.positionGroup.checkedRadioButtonId) {
            R.id.posTop -> SettingsRepository.POSITION_TOP
            R.id.posBottom -> SettingsRepository.POSITION_BOTTOM
            else -> SettingsRepository.POSITION_CENTER
        }
        settings.pendingColor = 0x99FFFFFF.toInt()
        settings.nextLineColor = 0x66FFFFFF.toInt()
    }

    private fun showPreview() {
        settings.fontSizeSp = binding.fontSizeSlider.value
        settings.highlightColor = when (binding.highlightColorGroup.checkedRadioButtonId) {
            R.id.colorGreen -> 0xFF1DB954.toInt()
            R.id.colorCyan -> 0xFF00E5FF.toInt()
            R.id.colorPink -> 0xFFFF4081.toInt()
            else -> 0xFFFFD54F.toInt()
        }
        binding.previewLyricView.applySettings()
        binding.previewLyricView.update(previewState())
    }

    private fun previewState(): LyricState {
        val line1 = LrcLine(
            chars = "卡拉OK逐字变色".mapIndexed { i, c ->
                LrcChar(c.toString(), 1000L + i * 200L)
            },
            startTimeMs = 1000L,
            endTimeMs = 3000L
        )
        val line2 = LrcLine(
            chars = "第二行歌词显示".map { LrcChar(it.toString(), 3000L) },
            startTimeMs = 3000L,
            endTimeMs = 5000L
        )
        val idx = ((System.currentTimeMillis() / 200L) % line1.chars.size).toInt()
        val pos = line1.chars[idx].startTimeMs
        return LyricState(line1, line2, pos)
    }
}
