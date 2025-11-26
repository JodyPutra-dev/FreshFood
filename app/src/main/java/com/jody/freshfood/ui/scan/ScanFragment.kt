package com.jody.freshfood.ui.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.jody.freshfood.databinding.FragmentScanBinding
import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.CameraController
import androidx.camera.view.PreviewView
import com.google.android.material.snackbar.Snackbar
import com.jody.freshfood.ui.result.ResultActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.jody.freshfood.data.model.ScanResult
import com.jody.freshfood.R

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!
    private var cameraController: LifecycleCameraController? = null
    private var isFlashEnabled = false
    private val viewModel: ScanViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else showPermissionDenied()
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleGalleryImage(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        cameraExecutor = Executors.newSingleThreadExecutor()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraController?.unbind()
        cameraController = null
        cameraExecutor.shutdown()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PermissionChecker.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.fabCapture.setOnClickListener { captureImage() }
        binding.fabGallery.setOnClickListener { pickImage.launch("image/*") }
        binding.fabFlashToggle.setOnClickListener { toggleFlash() }

        viewModel.predictionResult.observe(viewLifecycleOwner) { state ->
            when (state) {
                is PredictionState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.fabCapture.isEnabled = true
                    binding.fabGallery.isEnabled = true
                }
                is PredictionState.Processing -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.fabCapture.isEnabled = false
                    binding.fabGallery.isEnabled = false
                }
                is PredictionState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.fabCapture.isEnabled = true
                    binding.fabGallery.isEnabled = true
                    // Navigate to ResultActivity
                    val intent = Intent(requireContext(), ResultActivity::class.java)
                    // Pass ScanResult as primitive extras to avoid Knox Parcelable issues
                    intent.putExtra("FRESHNESS_LABEL", state.scanResult.freshnessLabel)
                    intent.putExtra("CONFIDENCE", state.scanResult.confidence)
                    intent.putExtra("IMAGE_PATH", state.scanResult.imagePath)
                    intent.putExtra("INSIGHTS", state.scanResult.insights)
                    intent.putExtra("ADVICE", state.scanResult.advice)
                    intent.putExtra("DAYS_LEFT", state.scanResult.daysLeft ?: -1)
                    Log.d("ScanFragment", "Launching ResultActivity with primitives: ${state.scanResult}")
                    startActivity(intent)
                    viewModel.reset()
                }
                is PredictionState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.fabCapture.isEnabled = true
                    binding.fabGallery.isEnabled = true
                    Snackbar.make(binding.root, state.message ?: getString(R.string.scan_error_prediction), Snackbar.LENGTH_LONG).show()
                    viewModel.reset()
                }
            }
        }

    }

    private fun showPermissionDenied() {
        Snackbar.make(binding.root, getString(R.string.scan_permission_denied), Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.scan_open_settings)) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }.show()
    }

    private fun startCamera() {
        try {
            val controller = LifecycleCameraController(requireContext())
            controller.cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
            controller.setImageCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            
            binding.previewView.controller = controller
            controller.bindToLifecycle(viewLifecycleOwner)
            controller.enableTorch(false)
            
            cameraController = controller
        } catch (ex: Exception) {
            Log.e("ScanFragment", "Camera init failed", ex)
            Snackbar.make(binding.root, getString(R.string.scan_error_camera_init), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun toggleFlash() {
        val controller = cameraController ?: return
        
        if (controller.cameraInfo?.hasFlashUnit() == false) {
            Snackbar.make(binding.root, "Flash not available", Snackbar.LENGTH_SHORT).show()
            return
        }
        
        isFlashEnabled = !isFlashEnabled
        controller.enableTorch(isFlashEnabled)
        
        binding.fabFlashToggle.setImageResource(
            if (isFlashEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        )
        binding.fabFlashToggle.contentDescription = getString(
            if (isFlashEnabled) R.string.scan_flash_on else R.string.scan_flash_off
        )
    }

    private fun captureImage() {
        val controller = cameraController ?: return
        val file = createImageFile(requireContext())
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        controller.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                // compress/resize and process
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val compressed = compressImageTo(file, 1024)
                        viewModel.processScanImage(compressed.absolutePath, requireActivity().applicationContext)
                    } catch (ex: Exception) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            Snackbar.make(binding.root, getString(R.string.scan_error_capture), Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e("ScanFragment", "Photo capture failed: ${exc.message}", exc)
                lifecycleScope.launch(Dispatchers.Main) {
                    Snackbar.make(binding.root, getString(R.string.scan_error_capture), Snackbar.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun handleGalleryImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = copyUriToCache(requireContext(), uri)
                val compressed = compressImageTo(file, 1024)
                viewModel.processScanImage(compressed.absolutePath, requireActivity().applicationContext)
            } catch (ex: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    Snackbar.make(binding.root, getString(R.string.scan_error_gallery), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "SCAN_${timeStamp}.jpg"
        return File(context.cacheDir, fileName)
    }

    private fun copyUriToCache(context: Context, uri: Uri): File {
        val input: InputStream? = context.contentResolver.openInputStream(uri)
        val outFile = createImageFile(context)
        input.use { ins ->
            FileOutputStream(outFile).use { fos ->
                ins?.copyTo(fos)
            }
        }
        return outFile
    }

    private fun compressImageTo(file: File, maxDim: Int): File {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        var (w, h) = options.outWidth to options.outHeight
        var scale = 1
        while (w / scale > maxDim || h / scale > maxDim) scale *= 2

        val opts2 = BitmapFactory.Options().apply { inSampleSize = scale }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts2)
            ?: throw java.io.IOException("Failed to decode image")
        
        try {
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width.coerceAtMost(maxDim)),
                (bitmap.height.coerceAtMost(maxDim)),
                true
            )
            try {
                FileOutputStream(file).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            } finally {
                if (scaled != bitmap) scaled.recycle()
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }

}
