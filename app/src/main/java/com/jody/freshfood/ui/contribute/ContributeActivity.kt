package com.jody.freshfood.ui.contribute

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.jody.freshfood.R
import com.jody.freshfood.databinding.ActivityContributeBinding
import com.jody.freshfood.network.ContributeService
import com.jody.freshfood.network.dto.ContributeRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ContributeActivity : AppCompatActivity() {

    private var _binding: ActivityContributeBinding? = null
    private val binding get() = _binding!!

    private var selectedImageUri: Uri? = null
    private var selectedImagePath: String? = null
    private lateinit var imagePickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityContributeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Register image picker
        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            uri?.let { handleImageSelection(it) }
        }

        // Set up AutoCompleteTextView adapter for fruit types
        val fruitTypes = resources.getStringArray(R.array.contribute_fruit_types)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            fruitTypes
        )
        binding.spinnerFruitType.setAdapter(adapter)

        // Set up button click listeners
        binding.buttonSelectImage.setOnClickListener {
            imagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.buttonSubmit.setOnClickListener {
            validateAndSubmit()
        }
    }

    private fun handleImageSelection(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Open InputStream and decode bitmap
                val inputStream = contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                // Calculate scaling to max 1024x1024 while maintaining aspect ratio
                val maxSize = 1024
                val width = originalBitmap.width
                val height = originalBitmap.height
                val scaleFactor = minOf(
                    maxSize.toFloat() / width,
                    maxSize.toFloat() / height
                )

                val newWidth = (width * scaleFactor).toInt()
                val newHeight = (height * scaleFactor).toInt()

                // Create scaled bitmap
                val scaledBitmap = Bitmap.createScaledBitmap(
                    originalBitmap,
                    newWidth,
                    newHeight,
                    true
                )

                // Save as JPEG to cache directory
                val imageFile = File(cacheDir, "contribute_${System.currentTimeMillis()}.jpg")
                FileOutputStream(imageFile).use { out ->
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }

                // Store file path
                selectedImagePath = imageFile.absolutePath

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    binding.imagePreview.load(imageFile)
                    binding.cardImagePreview.visibility = View.VISIBLE
                }

                // Clean up bitmaps
                originalBitmap.recycle()
                scaledBitmap.recycle()

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ContributeActivity,
                        "Failed to load image: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun validateAndSubmit() {
        // Validate image is selected
        if (selectedImagePath == null) {
            Toast.makeText(this, R.string.contribute_image_required, Toast.LENGTH_SHORT).show()
            return
        }

        // Validate fruit type is selected
        if (binding.spinnerFruitType.text.isEmpty()) {
            Toast.makeText(this, R.string.contribute_validation_fruit_type, Toast.LENGTH_SHORT).show()
            return
        }

        // Validate freshness level is selected
        if (binding.radioGroupFreshness.checkedRadioButtonId == -1) {
            Toast.makeText(this, R.string.contribute_validation_freshness, Toast.LENGTH_SHORT).show()
            return
        }

        // Validate consent checkbox is checked
        if (!binding.checkboxConsent.isChecked) {
            Toast.makeText(this, R.string.contribute_consent_required, Toast.LENGTH_SHORT).show()
            return
        }

        // All validations passed, proceed with upload
        uploadContribution()
    }

    private fun uploadContribution() {
        // Disable submit button, show progress bar
        binding.buttonSubmit.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.textStatus.visibility = View.GONE

        // Get selected values
        val fruitType = binding.spinnerFruitType.text.toString()
        val freshnessLevel = when (binding.radioGroupFreshness.checkedRadioButtonId) {
            R.id.radioFresh -> "Fresh"
            R.id.radioRipe -> "Ripe"
            R.id.radioOverripe -> "Overripe"
            R.id.radioSpoiled -> "Spoiled"
            else -> ""
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                performUploadWithRetry(fruitType, freshnessLevel)

                // Success - update UI on main thread
                withContext(Dispatchers.Main) {
                    // Increment contribution count in SharedPreferences
                    val prefs = getSharedPreferences("FreshFoodPrefs", MODE_PRIVATE)
                    val currentCount = prefs.getInt("contribution_count", 0)
                    prefs.edit().putInt("contribution_count", currentCount + 1).apply()

                    // Show success message
                    Toast.makeText(
                        this@ContributeActivity,
                        R.string.contribute_success,
                        Toast.LENGTH_LONG
                    ).show()

                    // Return to Settings
                    finish()
                }

            } catch (e: Exception) {
                // Error - update UI on main thread
                withContext(Dispatchers.Main) {
                    binding.buttonSubmit.isEnabled = true
                    binding.progressBar.visibility = View.GONE

                    val errorMessage = when (e) {
                        is IOException -> getString(R.string.contribute_error_network)
                        else -> getString(R.string.contribute_error_format, e.message ?: "Unknown error")
                    }

                    Toast.makeText(
                        this@ContributeActivity,
                        errorMessage,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private suspend fun performUploadWithRetry(fruitType: String, freshnessLevel: String) {
        val service = ContributeService.create()
        
        // Read image file and encode to Base64
        val imageBytes = File(selectedImagePath!!).readBytes()
        val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        // Get app version
        val appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"

        // Create request
        val request = ContributeRequestDto(
            fruitType = fruitType,
            freshnessLevel = freshnessLevel,
            imageBase64 = imageBase64,
            timestamp = System.currentTimeMillis(),
            appVersion = appVersion
        )

        // Implement retry logic (max 3 attempts)
        var lastException: Exception? = null
        val maxAttempts = 3

        for (attempt in 1..maxAttempts) {
            try {
                val response = service.uploadContribution(request)
                if (response.success) {
                    return // Success!
                } else {
                    throw IOException("Server returned success=false: ${response.message}")
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxAttempts) {
                    // Calculate exponential backoff delay
                    val delayMillis = when (attempt) {
                        1 -> 1000L
                        2 -> 2000L
                        else -> 4000L
                    }
                    delay(delayMillis)
                }
            }
        }

        // Max attempts reached, throw last exception
        throw lastException ?: IOException("Upload failed after $maxAttempts attempts")
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
