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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.android.material.snackbar.Snackbar
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
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
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
        cameraProvider?.unbindAll()
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
                    // Navigate to ResultActivity (external); start via explicit intent
                    try {
                        val intent = Intent(requireContext(), Class.forName("com.jody.freshfood.ResultActivity"))
                        // state.scanResult is now a UI-friendly ScanResult Parcelable
                        intent.putExtra("SCAN_RESULT", state.scanResult)
                        startActivity(intent)
                    } catch (ex: ClassNotFoundException) {
                        // Activity not present yet - just log and stay
                        Log.w("ScanFragment", "ResultActivity not found: ${ex.message}")
                    }
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
        val ctx = requireContext()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(binding.previewView.display.rotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (ex: Exception) {
                Log.e("ScanFragment", "Camera init failed", ex)
                Snackbar.make(binding.root, getString(R.string.scan_error_camera_init), Snackbar.LENGTH_LONG).show()
            }

        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return
        val file = createImageFile(requireContext())
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                // compress/resize and process
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val compressed = compressImageTo(file, 1024)
                        viewModel.processScanImage(compressed.absolutePath)
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
                viewModel.processScanImage(compressed.absolutePath)
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
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width.coerceAtMost(maxDim)),
            (bitmap.height.coerceAtMost(maxDim)),
            true
        )
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file
    }

}
