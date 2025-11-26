package com.jody.freshfood.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.jody.freshfood.R
import com.jody.freshfood.databinding.FragmentSettingsBinding
import com.jody.freshfood.ml.UpdateStatus
import com.jody.freshfood.ui.contribute.ContributeActivity
// Menambahkan import untuk ConnectivityReceiver
import com.jody.freshfood.receiver.ConnectivityReceiver

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // set initial version text using package info
        val versionName = try {
            val info = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            info.versionName ?: "1.0"
        } catch (ex: Exception) {
            "1.0"
        }
        binding.textAppVersion.text = getString(R.string.settings_app_version_format, versionName)

        // load current metadata
        viewModel.loadModelMetadata(requireContext())
        
        // load supported foods
        viewModel.loadSupportedFoods(requireContext())

        // =======================================================
        // KODE BUTTON CHECK UPDATES BARU (Telah Digabungkan)
        // =======================================================
        binding.buttonCheckUpdates.setOnClickListener {
            // Check network first
            if (!ConnectivityReceiver.isNetworkAvailable(requireContext())) {
                Toast.makeText(
                    requireContext(),
                    "No internet connection. Update will start when network is available.",
                    Toast.LENGTH_LONG
                ).show()

                // Mark as pending
                ConnectivityReceiver.markPendingUpdate(requireContext())
                return@setOnClickListener
            }

            // Proceed with update
            binding.textUpdateStatus.visibility = View.GONE
            binding.progressBarUpdate.visibility = View.VISIBLE
            binding.buttonCheckUpdates.isEnabled = false
            viewModel.checkForUpdates(requireContext())
        }
        // =======================================================

        viewModel.updateStatus.observe(viewLifecycleOwner, Observer { status ->
            when (status) {
                is UpdateStatus.Idle -> {
                    binding.progressBarUpdate.visibility = View.GONE
                    binding.textUpdateStatus.visibility = View.GONE
                    binding.buttonCheckUpdates.isEnabled = true
                }
                is UpdateStatus.Checking -> {
                    binding.progressBarUpdate.visibility = View.VISIBLE
                    binding.textUpdateStatus.visibility = View.VISIBLE
                    binding.textUpdateStatus.text = getString(R.string.settings_checking_updates)
                    binding.buttonCheckUpdates.isEnabled = false
                }
                is UpdateStatus.Downloading -> {
                    binding.progressBarUpdate.visibility = View.VISIBLE
                    binding.textUpdateStatus.visibility = View.VISIBLE
                    binding.textUpdateStatus.text = getString(R.string.settings_downloading_model, status.modelName)
                    binding.buttonCheckUpdates.isEnabled = false
                }
                is UpdateStatus.Success -> {
                    binding.progressBarUpdate.visibility = View.GONE
                    binding.textUpdateStatus.visibility = View.VISIBLE
                    if (status.updatedModels.isEmpty()) {
                        binding.textUpdateStatus.text = getString(R.string.settings_update_success_none)
                    } else {
                        binding.textUpdateStatus.text = getString(R.string.settings_update_success_format, status.updatedModels.size)
                    }
                    binding.buttonCheckUpdates.isEnabled = true
                }
                is UpdateStatus.Error -> {
                    binding.progressBarUpdate.visibility = View.GONE
                    binding.textUpdateStatus.visibility = View.VISIBLE
                    binding.textUpdateStatus.text = getString(R.string.settings_update_error_format, status.message)
                    binding.buttonCheckUpdates.isEnabled = true
                }
            }
        })

        viewModel.modelMetadata.observe(viewLifecycleOwner, Observer { list ->
            if (list.isNullOrEmpty()) {
                binding.textModelVersions.text = getString(R.string.settings_loading_versions)
            } else {
                val sb = StringBuilder()
                for (m in list) {
                    sb.append("${m.modelName} – v${m.version}\n")
                }
                binding.textModelVersions.text = sb.toString().trim()
            }
        })

        viewModel.supportedFoods.observe(viewLifecycleOwner, Observer { list ->
            if (list.isNullOrEmpty()) {
                binding.textSupportedFoodsList.text = getString(R.string.settings_no_foods)
            } else {
                val sb = StringBuilder()
                for (food in list) {
                    val states = food.ripenessStates.joinToString(", ")
                    sb.append("• ${food.fruitName} ($states)\n")
                }
                binding.textSupportedFoodsList.text = sb.toString().trim()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}