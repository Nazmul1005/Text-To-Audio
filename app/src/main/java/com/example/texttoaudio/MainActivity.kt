package com.example.texttoaudio

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.texttoaudio.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var extractedText = ""
    private var currentFileUri: Uri? = null

    // File picker for PDF and image files
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
            val uri = result.data!!.data!!
            currentFileUri = uri
            handleFileSelection(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize PDFBox
        PDFBoxResourceLoader.init(applicationContext)

        // Initialize TextToSpeech
        setupTTS()

        // Setup UI listeners
        setupUI()
    }

    private fun setupTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                tts?.language = Locale.US
                // Check if voice is available
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Toast.makeText(this, "TTS Language not supported", Toast.LENGTH_SHORT).show()
                }
                setupTTSListener()
            }
        }
    }

    private fun setupTTSListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread {
                    binding.playPauseButton.text = "Pause"
                    binding.statusText.text = "Speaking..."
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    binding.playPauseButton.text = "Play"
                    binding.statusText.text = "Ready"
                }
            }

            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    binding.playPauseButton.text = "Play"
                    binding.statusText.text = "Error during playback"
                    Toast.makeText(
                        this@MainActivity,
                        "TTS Error",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun setupUI() {
        // Pick PDF button
        binding.pickPdfButton.setOnClickListener {
            openFilePicker("application/pdf")
        }

        // Pick Image button
        binding.pickImageButton.setOnClickListener {
            openFilePicker("image/*")
        }

        // Play/Pause button
        binding.playPauseButton.setOnClickListener {
            togglePlayback()
        }

        // Stop button
        binding.stopButton.setOnClickListener {
            stopPlayback()
        }

        // Clear button
        binding.clearButton.setOnClickListener {
            clearText()
        }

        // Make text preview scrollable
        binding.textPreview.movementMethod = ScrollingMovementMethod()
    }

    private fun openFilePicker(mimeType: String) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(Intent.createChooser(intent, "Select File"))
    }

    private fun handleFileSelection(uri: Uri) {
        val mimeType = contentResolver.getType(uri)

        // Get file name
        val fileName = getFileName(uri)
        binding.fileNameText.text = "Selected: $fileName"
        binding.statusText.text = "Extracting text..."

        when {
            mimeType?.startsWith("image/") == true -> {
                extractTextFromImage(uri)
            }
            mimeType == "application/pdf" -> {
                extractTextFromPdf(uri)
            }
            else -> {
                Toast.makeText(this, "Unsupported file type", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result = "Unknown"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    result = cursor.getString(nameIndex)
                }
            }
        }
        return result
    }

    private fun extractTextFromPdf(uri: Uri) {
        binding.statusText.text = "Extracting text from PDF..."
        binding.progressBar.visibility = View.VISIBLE

        Thread {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val document = PDDocument.load(inputStream)
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                document.close()

                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    if (text.isNotBlank()) {
                        extractedText = text.trim()
                        binding.textPreview.setText(extractedText)
                        binding.statusText.text = "PDF text extracted (${extractedText.length} characters)"
                        binding.playPauseButton.isEnabled = true
                    } else {
                        binding.statusText.text = "No text found in PDF"
                        Toast.makeText(this, "No text extracted from PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.statusText.text = "Error extracting PDF"
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun extractTextFromImage(uri: Uri) {
        binding.statusText.text = "Extracting text from image..."
        binding.progressBar.visibility = View.VISIBLE

        try {
            val image = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    binding.progressBar.visibility = View.GONE
                    val text = visionText.text.trim()
                    if (text.isNotBlank()) {
                        extractedText = text
                        binding.textPreview.setText(extractedText)
                        binding.statusText.text = "Image text extracted (${extractedText.length} characters)"
                        binding.playPauseButton.isEnabled = true
                    } else {
                        binding.statusText.text = "No text found in image"
                        Toast.makeText(this, "No text detected in image", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    binding.statusText.text = "Error extracting text from image"
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            binding.statusText.text = "Error loading image"
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePlayback() {
        if (!isTtsReady) {
            Toast.makeText(this, "TTS not ready yet", Toast.LENGTH_SHORT).show()
            return
        }

        val text = binding.textPreview.text.toString().trim()
        if (text.isBlank()) {
            Toast.makeText(this, "No text to read", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if currently speaking
        if (tts?.isSpeaking == true) {
            // Pause current speech
            tts?.stop()
            binding.playPauseButton.text = "Play"
            binding.statusText.text = "Paused"
        } else {
            // Start speaking
            extractedText = text
            speakText(extractedText)
        }
    }

    private fun speakText(text: String) {
        if (text.isBlank()) return

        // QUEUE_FLUSH drops any ongoing speech and starts fresh
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "TTS_UTTERANCE_ID_${System.currentTimeMillis()}"
        )
    }

    private fun stopPlayback() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
            binding.playPauseButton.text = "Play"
            binding.statusText.text = "Stopped"
        }
    }

    private fun clearText() {
        stopPlayback()
        extractedText = ""
        currentFileUri = null
        binding.textPreview.setText("")
        binding.fileNameText.text = ""
        binding.statusText.text = "Ready"
        binding.playPauseButton.isEnabled = false
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
