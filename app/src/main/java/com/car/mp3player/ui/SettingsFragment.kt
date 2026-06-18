package com.car.mp3player.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.car.mp3player.LyricsOverlayService
import com.car.mp3player.R
import com.car.mp3player.data.ScanPathHelper
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.databinding.FragmentSettingsBinding
import com.car.mp3player.model.ThemeMode
import com.google.android.material.chip.Chip

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var settings: SettingsRepository

    private val overlayPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        binding.switchOverlay.isChecked = settings.overlayEnabled && Settings.canDrawOverlays(requireContext())
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)
        }
        settings.addScanTreeUri(uri.toString())
        refreshScanPathsUi()
        Toast.makeText(requireContext(), R.string.folder_added, Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = SettingsRepository(requireContext())
        binding.switchAutoResume.isChecked = settings.autoResumePlayback
        binding.switchOverlay.isChecked = settings.overlayEnabled
        binding.switchOnlineLyrics.isChecked = settings.onlineLyricsEnabled
        binding.switchOnlineCover.isChecked = settings.onlineCoverEnabled
        binding.fontSizeSlider.value = settings.fontSizeSp

        when (settings.themeMode) {
            ThemeMode.LIGHT -> binding.themeLight.isChecked = true
            ThemeMode.DARK -> binding.themeDark.isChecked = true
            ThemeMode.SYSTEM -> binding.themeSystem.isChecked = true
        }
        when (settings.overlayPosition) {
            SettingsRepository.POSITION_TOP -> binding.posTop.isChecked = true
            SettingsRepository.POSITION_BOTTOM -> binding.posBottom.isChecked = true
            else -> binding.posCenter.isChecked = true
        }

        refreshScanPathsUi()

        binding.btnPickFolder.setOnClickListener { pickFolder.launch(null) }
        binding.btnAddMusic.setOnClickListener { addPresetPath("内置 Music") }
        binding.btnAddDownload.setOnClickListener { addPresetPath("下载目录") }

        binding.btnScan.setOnClickListener {
            binding.btnScan.isEnabled = false
            binding.btnScan.text = getString(R.string.scanning)
            (activity as? MainHost)?.scanMusic { count ->
                binding.btnScan.isEnabled = true
                binding.btnScan.text = getString(R.string.settings_scan)
                Toast.makeText(requireContext(), getString(R.string.scan_done, count), Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchAutoResume.setOnCheckedChangeListener { _, checked ->
            settings.autoResumePlayback = checked
        }

        binding.switchOnlineLyrics.setOnCheckedChangeListener { _, checked ->
            settings.onlineLyricsEnabled = checked
        }

        binding.switchOnlineCover.setOnCheckedChangeListener { _, checked ->
            settings.onlineCoverEnabled = checked
        }

        binding.themeGroup.setOnCheckedChangeListener { _, checkedId ->
            settings.themeMode = when (checkedId) {
                R.id.themeLight -> ThemeMode.LIGHT
                R.id.themeDark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            settings.applyTheme()
            activity?.recreate()
        }

        binding.switchOverlay.setOnCheckedChangeListener { _, checked ->
            if (checked && !Settings.canDrawOverlays(requireContext())) {
                binding.switchOverlay.isChecked = false
                requestOverlay()
                return@setOnCheckedChangeListener
            }
            settings.overlayEnabled = checked
            if (checked) LyricsOverlayService.start(requireContext()) else LyricsOverlayService.stop(requireContext())
        }

        binding.btnGrantOverlay.setOnClickListener { requestOverlay() }

        binding.fontSizeSlider.addOnChangeListener { _, value, _ ->
            settings.fontSizeSp = value
            LyricsOverlayService.refresh(requireContext())
        }

        binding.positionGroup.setOnCheckedChangeListener { _, checkedId ->
            settings.overlayPosition = when (checkedId) {
                R.id.posTop -> SettingsRepository.POSITION_TOP
                R.id.posBottom -> SettingsRepository.POSITION_BOTTOM
                else -> SettingsRepository.POSITION_CENTER
            }
            LyricsOverlayService.refresh(requireContext())
        }
    }

    private fun addPresetPath(label: String) {
        val path = ScanPathHelper.presetPaths().firstOrNull { it.first == label }?.second ?: return
        settings.addScanPath(path)
        refreshScanPathsUi()
        Toast.makeText(requireContext(), getString(R.string.folder_added), Toast.LENGTH_SHORT).show()
    }

    private fun refreshScanPathsUi() {
        binding.scanPathsChipGroup.removeAllViews()
        val entries = settings.allScanEntries()
        if (entries.isEmpty()) {
            binding.scanPathsEmpty.visibility = View.VISIBLE
        } else {
            binding.scanPathsEmpty.visibility = View.GONE
            entries.forEach { entry ->
                val chip = Chip(requireContext()).apply {
                    text = ScanPathHelper.displayName(requireContext(), entry)
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        settings.removeScanEntry(entry)
                        refreshScanPathsUi()
                    }
                }
                binding.scanPathsChipGroup.addView(chip)
            }
        }
    }

    private fun requestOverlay() {
        overlayPermission.launch(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireContext().packageName}"))
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
