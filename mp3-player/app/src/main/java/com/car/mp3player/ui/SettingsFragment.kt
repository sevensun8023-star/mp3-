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
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.databinding.FragmentSettingsBinding
import com.car.mp3player.model.ThemeMode

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var settings: SettingsRepository

    private val overlayPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        binding.switchOverlay.isChecked = settings.overlayEnabled && Settings.canDrawOverlays(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = SettingsRepository(requireContext())
        binding.scanPathsInput.setText(settings.scanPathsText)
        binding.switchAutoResume.isChecked = settings.autoResumePlayback
        binding.switchOverlay.isChecked = settings.overlayEnabled
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

        binding.btnScan.setOnClickListener {
            settings.scanPathsText = binding.scanPathsInput.text?.toString().orEmpty()
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
