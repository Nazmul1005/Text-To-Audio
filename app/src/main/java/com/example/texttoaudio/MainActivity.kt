package com.example.texttoaudio

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.texttoaudio.databinding.ActivityMainBinding
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.util.Locale
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var extractedText = ""
    private var currentFileUri: Uri? = null
    private var pendingPhotoUri: Uri? = null
    private val ocrExecutor = Executors.newSingleThreadExecutor()

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val photoUri = pendingPhotoUri
        pendingPhotoUri = null
        if (saved && photoUri != null) {
            currentFileUri = photoUri
            handleFileSelection(photoUri)
        } else {
            binding.statusText.setText(R.string.status_photo_cancelled)
        }
    }

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
                setupTTSListener()
            } else {
                binding.statusText.text = getString(R.string.tts_unavailable)
            }
        }
    }

    private fun setupTTSListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread {
                    binding.playPauseButton.setText(R.string.pause_label)
                    binding.statusText.setText(R.string.status_speaking)
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    binding.playPauseButton.setText(R.string.play_label)
                    binding.statusText.setText(R.string.status_ready)
                }
            }

            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    binding.playPauseButton.setText(R.string.play_label)
                    binding.statusText.setText(R.string.status_tts_error)
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.status_tts_error),
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

        binding.takePhotoButton.setOnClickListener {
            takePhoto()
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

        binding.ttsLanguageToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                binding.languageHintText.text = when (checkedId) {
                    R.id.bengali_language_button -> getString(R.string.language_bengali_selected)
                    R.id.english_language_button -> getString(R.string.language_english_selected)
                    else -> getString(R.string.language_auto_selected)
                }
            }
        }
    }

    private fun openFilePicker(mimeType: String) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = mimeType
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.chooser_title)))
    }

    private fun takePhoto() {
        try {
            val photoDirectory = File(cacheDir, "captured-images").apply {
                if (!exists() && !mkdirs()) throw IllegalStateException("Could not prepare camera storage")
            }
            val photoFile = File.createTempFile("texttoaudio-", ".jpg", photoDirectory)
            val photoUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                photoFile
            )
            pendingPhotoUri = photoUri
            takePhotoLauncher.launch(photoUri)
        } catch (error: Exception) {
            binding.statusText.setText(R.string.status_photo_error)
            Toast.makeText(this, error.message ?: getString(R.string.status_photo_error), Toast.LENGTH_LONG).show()
        }
    }

    private fun handleFileSelection(uri: Uri) {
        val mimeType = contentResolver.getType(uri)

        // Get file name
        val fileName = getFileName(uri)
        binding.fileNameText.text = getString(R.string.selected_file, fileName)
        binding.statusText.text = getString(R.string.status_reading_image)

        when {
            mimeType?.startsWith("image/") == true ||
                uri.lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase() in
                setOf("jpg", "jpeg", "png", "webp", "heic") -> {
                extractTextFromImage(uri)
            }
            mimeType == "application/pdf" -> {
                extractTextFromPdf(uri)
            }
            else -> {
                Toast.makeText(this, R.string.toast_unsupported_file, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result = getString(R.string.status_error)
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
        binding.statusText.text = getString(R.string.status_reading_pdf)
        binding.progressBar.visibility = View.VISIBLE

        ocrExecutor.execute {
            var ocr: BengaliOcr? = null
            try {
                val pages = mutableListOf<String>()
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Could not open the selected PDF")
                inputStream.use { stream ->
                    PDDocument.load(stream).use { document ->
                        val renderer = PDFRenderer(document)
                        for (pageIndex in 0 until document.numberOfPages) {
                            val stripper = PDFTextStripper().apply {
                                startPage = pageIndex + 1
                                endPage = pageIndex + 1
                            }
                            var pageText = stripper.getText(document).trim()
                            if (pageText.isBlank()) {
                                if (ocr == null) ocr = BengaliOcr(this)
                                val pageBitmap = renderer.renderImageWithDPI(pageIndex, 150f)
                                pageText = try {
                                    ocr!!.recognize(pageBitmap)
                                } finally {
                                    pageBitmap.recycle()
                                }
                            }
                            if (pageText.isNotBlank()) pages += pageText
                        }
                    }
                }
                val text = pages.joinToString("\n\n")

                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    if (text.isNotBlank()) {
                        extractedText = text.trim()
                        binding.textPreview.setText(extractedText)
                        binding.statusText.text = getString(
                            R.string.status_pdf_complete,
                            extractedText.length
                        )
                        binding.playPauseButton.isEnabled = true
                    } else {
                        binding.statusText.text = getString(R.string.status_no_text)
                        Toast.makeText(this, R.string.toast_no_text, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.statusText.text = getString(R.string.status_error)
                    Toast.makeText(this, e.message ?: getString(R.string.status_error), Toast.LENGTH_SHORT).show()
                }
            } finally {
                ocr?.close()
            }
        }
    }

    private fun extractTextFromImage(uri: Uri) {
        binding.statusText.text = getString(R.string.status_reading_image)
        binding.progressBar.visibility = View.VISIBLE

        ocrExecutor.execute {
            var ocr: BengaliOcr? = null
            try {
                ocr = BengaliOcr(this)
                val text = ocr.recognize(contentResolver, uri)
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    if (text.isNotBlank()) {
                        extractedText = text
                        binding.textPreview.setText(extractedText)
                        binding.statusText.text = getString(
                            R.string.status_image_complete,
                            extractedText.length
                        )
                        binding.playPauseButton.isEnabled = true
                    } else {
                        binding.statusText.text = getString(R.string.status_no_text)
                        Toast.makeText(this, R.string.toast_no_text, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.statusText.text = getString(R.string.status_error)
                    Toast.makeText(this, e.message ?: getString(R.string.status_error), Toast.LENGTH_SHORT).show()
                }
            } finally {
                ocr?.close()
            }
        }
    }

    private fun togglePlayback() {
        if (!isTtsReady) {
            Toast.makeText(this, R.string.toast_tts_not_ready, Toast.LENGTH_SHORT).show()
            return
        }

        val text = binding.textPreview.text.toString().trim()
        if (text.isBlank()) {
            Toast.makeText(this, R.string.toast_no_text_to_read, Toast.LENGTH_SHORT).show()
            return
        }

        val language = selectedTtsLocale(text)
        val languageSupport = tts?.isLanguageAvailable(language) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (languageSupport < TextToSpeech.LANG_AVAILABLE) {
            val message = if (language.language == "bn") {
                getString(R.string.toast_bengali_voice_missing)
            } else {
                getString(R.string.toast_english_voice_missing)
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }
        tts?.setLanguage(language)

        // Check if currently speaking
        if (tts?.isSpeaking == true) {
            // Pause current speech
            tts?.stop()
            binding.playPauseButton.setText(R.string.play_label)
            binding.statusText.setText(R.string.status_paused)
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

    private fun selectedTtsLocale(text: String): Locale {
        return when (binding.ttsLanguageToggle.checkedButtonId) {
            R.id.bengali_language_button -> Locale("bn", "BD")
            R.id.english_language_button -> Locale.US
            else -> if (text.any { it in '\u0980'..'\u09FF' }) {
                Locale("bn", "BD")
            } else {
                Locale.US
            }
        }
    }

    private fun stopPlayback() {
        if (tts?.isSpeaking == true) {
            tts?.stop()
            binding.playPauseButton.setText(R.string.play_label)
            binding.statusText.setText(R.string.status_stopped)
        }
    }

    private fun clearText() {
        stopPlayback()
        extractedText = ""
        currentFileUri = null
        binding.textPreview.setText("")
        binding.fileNameText.text = ""
        binding.statusText.setText(R.string.status_ready)
        binding.playPauseButton.isEnabled = false
    }

    override fun onDestroy() {
        ocrExecutor.shutdownNow()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
