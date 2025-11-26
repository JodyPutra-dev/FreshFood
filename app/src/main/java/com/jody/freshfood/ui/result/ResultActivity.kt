package com.jody.freshfood.ui.result

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import com.jody.freshfood.R
import com.jody.freshfood.data.model.ScanResult
import com.jody.freshfood.data.model.toEntity
import com.jody.freshfood.data.repository.ScanRepository
import com.jody.freshfood.databinding.ActivityResultBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private var scanResult: ScanResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Reconstruct ScanResult from primitive Intent extras
        val freshnessLabel = intent.getStringExtra("FRESHNESS_LABEL") ?: ""
        val confidence = intent.getFloatExtra("CONFIDENCE", 0f)
        val imagePath = intent.getStringExtra("IMAGE_PATH") ?: ""
        val insights = intent.getStringExtra("INSIGHTS") ?: ""
        val advice = intent.getStringExtra("ADVICE") // nullable, no default
        val daysLeftRaw = intent.getIntExtra("DAYS_LEFT", -1)
        val daysLeft = if (daysLeftRaw == -1) null else daysLeftRaw
        
        scanResult = ScanResult(
            freshnessLabel = freshnessLabel,
            confidence = confidence,
            imagePath = imagePath,
            insights = insights,
            advice = advice,
            daysLeft = daysLeft
        )
        
        Log.d("ResultActivity", "Reconstructed ScanResult from primitives: $scanResult")
        
        if (scanResult == null) {
            Log.e("ResultActivity", "ScanResult reconstruction failed! Intent extras: ${intent.extras?.keySet()?.joinToString()}, values: freshnessLabel=$freshnessLabel, confidence=$confidence")
            Toast.makeText(this, getString(R.string.result_error_no_data), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        populateUI(scanResult!!)

        binding.buttonSave.setOnClickListener {
            saveToHistory(scanResult!!)
        }

        binding.buttonShare.setOnClickListener {
            shareResult(scanResult!!)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun populateUI(item: ScanResult) {
        // Load image
        val imgFile = File(item.imagePath)
        if (imgFile.exists()) {
            binding.imageFood.load(imgFile) {
                crossfade(true)
                placeholder(R.mipmap.ic_launcher)
                error(R.mipmap.ic_launcher)
            }
        } else {
            binding.imageFood.setImageResource(R.mipmap.ic_launcher)
        }

        binding.textFruitType.text = item.freshnessLabel.replaceFirstChar { it.uppercaseChar() }

        // Badge background selection
        val label = item.freshnessLabel.lowercase()
        val bg = when {
            label.contains("fresh") -> R.drawable.bg_badge_fresh
            label.contains("overripe") -> R.drawable.bg_badge_overripe
            label.contains("ripe") -> R.drawable.bg_badge_ripe
            label.contains("spoil") || label.contains("bad") -> R.drawable.bg_badge_spoiled
            else -> R.drawable.bg_badge_fresh
        }
        binding.textFreshnessLabel.text = item.freshnessLabel
        binding.textFreshnessLabel.setBackgroundResource(bg)

        // Confidence
        val conf = (item.confidence.coerceIn(0f, 1f) * 100).toInt()
        binding.textConfidence.text = String.format(getString(R.string.result_confidence_format), conf)
        binding.progressBarConfidence.progress = conf

        // Insights are provided in the UI model
        binding.textInsights.text = item.insights.ifEmpty { getString(R.string.result_insights_dummy_note) }
    }


    private fun saveToHistory(item: ScanResult) {
        Log.d("ResultActivity", "saveToHistory called with: $item")
        binding.buttonSave.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val repo = ScanRepository(this@ResultActivity)
                val entity = item.toEntity()
                Log.d("ResultActivity", "Entity to save: $entity")
                val insertedId = repo.insertScanResult(entity)
                Log.d("ResultActivity", "Saved to database with ID: $insertedId")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ResultActivity, getString(R.string.result_save_success), Toast.LENGTH_SHORT).show()
                }
            } catch (ex: Exception) {
                Log.e("ResultActivity", "Save failed", ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ResultActivity, getString(R.string.result_save_error), Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { binding.buttonSave.isEnabled = true }
            }
        }
    }

    private fun shareResult(item: ScanResult) {
        val file = File(item.imagePath)
        val shareText = StringBuilder().apply {
            append("FreshFood Scan Result\n")
            append("Freshness: ${item.freshnessLabel}\n")
            append("Confidence: ${(item.confidence.coerceIn(0f,1f)*100).toInt()}%\n")
        }.toString()

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }

        if (file.exists()) {
            try {
                val uri: Uri = FileProvider.getUriForFile(this, "com.jody.freshfood.fileprovider", file)
                sendIntent.putExtra(Intent.EXTRA_STREAM, uri)
                sendIntent.type = "image/*"
                sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (ex: Exception) {
                Log.w("ResultActivity", "FileProvider failed: ${ex.message}")
            }
        }

        try {
            startActivity(Intent.createChooser(sendIntent, getString(R.string.result_button_share)))
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.result_share_error), Toast.LENGTH_LONG).show()
        }
    }
}
