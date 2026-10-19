package com.clearqr

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val captured = AtomicBoolean(false)
    private val processingFrame = AtomicBoolean(false)
    private var cameraProvider: ProcessCameraProvider? = null
    private var textRecognizer: TextRecognizer? = null
    private var previewView: PreviewView? = null
    private var scanFrame: FrameLayout? = null

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showScanner() else showPermissionMessage()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(245, 244, 239)
        window.navigationBarColor = Color.rgb(245, 244, 239)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showScanner()
        } else {
            showPermissionMessage()
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showScanner() {
        captured.set(false)
        val root = createRoot()
        addHeader(root, "Give it a\nclearer look.", "Read a QR code from anywhere and bring it back to life.")

        val card = verticalLayout().apply {
            background = roundedBackground(Color.WHITE, 26f)
            setPadding(dp(14), dp(14), dp(14), dp(4))
            elevation = dp(5).toFloat()
        }
        val params = LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(8)
            bottomMargin = dp(16)
        }
        root.addView(card, params)

        val cameraFrame = FrameLayout(this).apply {
            background = roundedBackground(Color.rgb(220, 229, 220), 18f)
            clipToOutline = true
        }
        card.addView(cameraFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        cameraFrame.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        addScanCorners(cameraFrame)
        scanFrame = cameraFrame

        val hint = TextView(this).apply {
            text = "●   Align the code inside the frame"
            setTextColor(Color.rgb(91, 101, 94))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(15), 0, dp(15))
        }
        card.addView(hint, LinearLayout.LayoutParams(-1, -2))
        addFooter(root)
        setContentView(root)
        startCamera()
    }


    private fun showPermissionMessage() {
        val root = createRoot()
        addHeader(
            root,
            "Camera access\nneeded.",
            "Your camera reads the code in real time. No photos are taken or stored."
        )
        val button = actionButton("Enable camera") {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
        root.addView(button, LinearLayout.LayoutParams(-1, dp(54)).apply {
            topMargin = dp(30)
        })
        addFooter(root)
        setContentView(root)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView?.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            val scanner = BarcodeScanning.getClient(options)
            val recognizer = textRecognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .also { textRecognizer = it }

            analysis.setAnalyzer(cameraExecutor) { imageProxy -> processFrame(scanner, recognizer, imageProxy) }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) {
                showCameraError()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processFrame(
        scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
        recognizer: TextRecognizer,
        proxy: ImageProxy
    ) {
        val mediaImage = proxy.image
        if (mediaImage == null || captured.get() || !processingFrame.compareAndSet(false, true)) {
            proxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                val code = codes.firstOrNull { !it.rawValue.isNullOrEmpty() }
                if (code == null) {
                    finishFrame(proxy)
                    return@addOnSuccessListener
                }
                val value = code.rawValue ?: run {
                    finishFrame(proxy)
                    return@addOnSuccessListener
                }
                if (code.format != Barcode.FORMAT_QR_CODE) {
                    showCapturedResult(value, code.format, emptyList())
                    finishFrame(proxy)
                    return@addOnSuccessListener
                }
                val barcodeBounds = code.boundingBox
                if (barcodeBounds == null) {
                    showCapturedResult(value, code.format, emptyList())
                    finishFrame(proxy)
                    return@addOnSuccessListener
                }

                recognizer.process(input)
                    .addOnSuccessListener { recognizedText ->
                        val bounds = barcodeBounds.toImageBounds()
                        val elements = recognizedText.textBlocks.flatMap { block ->
                            block.lines.flatMap { line ->
                                line.elements.mapNotNull { element ->
                                    element.boundingBox?.let { RecognizedTextElement(element.text, it.toImageBounds()) }
                                }
                            }
                        }
                        showCapturedResult(value, code.format, NearbyNumberExtractor.extract(bounds, elements))
                    }
                    .addOnFailureListener {
                        showCapturedResult(value, code.format, emptyList())
                    }
                    .addOnCompleteListener { finishFrame(proxy) }
            }
            .addOnFailureListener { finishFrame(proxy) }
    }

    private fun android.graphics.Rect.toImageBounds() = ImageBounds(left, top, right, bottom)

    private fun showCapturedResult(value: String, format: Int, nearbyNumbers: List<String>) {
        if (captured.compareAndSet(false, true)) showResult(value, format, nearbyNumbers)
    }

    private fun finishFrame(proxy: ImageProxy) {
        try {
            proxy.close()
        } finally {
            processingFrame.set(false)
        }
    }

    private fun showResult(value: String, scannedFormat: Int, nearbyNumbers: List<String>) {
        cameraProvider?.unbindAll()
        val root = createRoot()
        addHeader(root, "Ready to scan.", "Scan it!.")
        val card = verticalLayout().apply {
            background = roundedBackground(Color.WHITE, 26f)
            setPadding(dp(22), dp(22), dp(22), dp(22))
            gravity = Gravity.CENTER_HORIZONTAL
            elevation = dp(5).toFloat()
        }
        root.addView(card, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(8)
            bottomMargin = dp(16)
        })

        try {
            val barcodeFormat = scannedFormat.toZxingFormat()
                ?: throw IllegalArgumentException("Unsupported barcode format")
            val bitmap = createBarcodeBitmap(value, barcodeFormat)
            val image = ImageView(this).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Recreated ${scannedFormat.displayName()}"
                background = roundedBackground(Color.WHITE, 16f)
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }
            val barcodeRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val imageParams = if (nearbyNumbers.isEmpty()) {
                LinearLayout.LayoutParams(dp(250), dp(250))
            } else {
                LinearLayout.LayoutParams(0, dp(250), 1f)
            }
            barcodeRow.addView(image, imageParams)
            if (nearbyNumbers.isNotEmpty()) {
                val numberView = label(nearbyNumbers.joinToString("\n"), 24f, Color.rgb(23, 37, 31)).apply {
                    gravity = Gravity.CENTER
                    maxLines = nearbyNumbers.size
                    setPadding(dp(12), 0, 0, 0)
                    contentDescription = "Numbers next to barcode: ${nearbyNumbers.joinToString(", ")}"
                }
                barcodeRow.addView(numberView, LinearLayout.LayoutParams(0, dp(250), 1f))
            }
            val rowParams = if (nearbyNumbers.isEmpty()) {
                LinearLayout.LayoutParams(dp(250), dp(250))
            } else {
                LinearLayout.LayoutParams(-1, dp(250))
            }
            card.addView(barcodeRow, rowParams)
        } catch (_: Exception) {
            val error = label("This barcode format or data could not be recreated.", 15f, Color.rgb(145, 65, 54))
            card.addView(error)
        }

        val caption =
            label("YOUR RECREATED ${scannedFormat.displayName().uppercase()}", 10f, Color.rgb(130, 144, 135)).apply {
                letterSpacing = 0.16f
                setPadding(0, dp(18), 0, dp(7))
            }
        card.addView(caption)
        card.addView(label(value, 13f, Color.rgb(55, 69, 61)))
        val again = actionButton("Scan another") {
            showScanner()
        }
        card.addView(again, LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(20)
        })
        addFooter(root)
        setContentView(root)
    }

    private fun showCameraError() {
        val root = createRoot()
        addHeader(root, "Camera unavailable.", "Close other camera apps and try again.")
        root.addView(actionButton("Try again") { showScanner() }, LinearLayout.LayoutParams(-1, dp(54)))
        addFooter(root)
        setContentView(root)
    }

    private fun createBarcodeBitmap(value: String, format: BarcodeFormat): Bitmap {
        val isLinear = format in setOf(
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODABAR,
            BarcodeFormat.EAN_8,
            BarcodeFormat.EAN_13,
            BarcodeFormat.ITF,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E
        )
        val width =
            if (format == BarcodeFormat.QR_CODE || format == BarcodeFormat.DATA_MATRIX || format == BarcodeFormat.AZTEC) 720 else 960
        val height = when {
            isLinear -> 300
            format == BarcodeFormat.PDF_417 -> 480
            else -> 720
        }
        val hints = mapOf(EncodeHintType.MARGIN to if (isLinear) 16 else 1)
        val matrix = MultiFormatWriter().encode(value, format, width, height, hints)
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            pixels[y * matrix.width + x] = if (matrix[x, y]) Color.rgb(23, 37, 31) else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }

    private fun Int.toZxingFormat(): BarcodeFormat? = when (this) {
        Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
        Barcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
        Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
        Barcode.FORMAT_PDF417 -> BarcodeFormat.PDF_417
        Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
        Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
        Barcode.FORMAT_CODE_93 -> BarcodeFormat.CODE_93
        Barcode.FORMAT_CODABAR -> BarcodeFormat.CODABAR
        Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
        Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
        Barcode.FORMAT_ITF -> BarcodeFormat.ITF
        Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
        Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
        else -> null
    }

    private fun Int.displayName(): String = when (this) {
        Barcode.FORMAT_QR_CODE -> "QR code"
        Barcode.FORMAT_AZTEC -> "Aztec barcode"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix barcode"
        Barcode.FORMAT_PDF417 -> "PDF417 barcode"
        Barcode.FORMAT_CODE_128 -> "Code 128 barcode"
        Barcode.FORMAT_CODE_39 -> "Code 39 barcode"
        Barcode.FORMAT_CODE_93 -> "Code 93 barcode"
        Barcode.FORMAT_CODABAR -> "Codabar"
        Barcode.FORMAT_EAN_8 -> "EAN-8 barcode"
        Barcode.FORMAT_EAN_13 -> "EAN-13 barcode"
        Barcode.FORMAT_ITF -> "ITF barcode"
        Barcode.FORMAT_UPC_A -> "UPC-A barcode"
        Barcode.FORMAT_UPC_E -> "UPC-E barcode"
        else -> "barcode"
    }

    private fun createRoot(): LinearLayout = verticalLayout().apply {
        setBackgroundColor(Color.rgb(245, 244, 239))
        setPadding(dp(26), dp(18), dp(26), 0)
        fitsSystemWindows = true
    }

    private fun addHeader(root: LinearLayout, title: String, subtitle: String) {
        val brand = TextView(this).apply {
            text = "▣  CLEAR QR"
            setTextColor(Color.rgb(49, 92, 73))
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(18))
        }
        root.addView(brand)
        val heading = TextView(this).apply {
            text = title
            setTextColor(Color.rgb(23, 37, 31))
            textSize = 36f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = -0.03f
            setLineSpacing(0f, 0.96f)
            setPadding(dp(8), 0, 0, 0)
        }
        root.addView(heading)
        val description = TextView(this).apply {
            text = subtitle
            setTextColor(Color.rgb(114, 122, 115))
            textSize = 15f
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(8), dp(9), 0, 0)
        }
        root.addView(description)
    }

    private fun addFooter(root: LinearLayout) {
        val footer = TextView(this).apply {
            text = "✦   No saved photos here"
            setTextColor(Color.rgb(137, 145, 137))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(20))
        }
        root.addView(footer)
    }

    private fun addScanCorners(frame: FrameLayout) {
        val size = dp(42)
        val inset = dp(28)
        val green = Color.rgb(167, 237, 179)
        val stroke = dp(4)
        val corners = listOf(
            Triple(Gravity.TOP or Gravity.START, true, true),
            Triple(Gravity.TOP or Gravity.END, true, false),
            Triple(Gravity.BOTTOM or Gravity.START, false, true),
            Triple(Gravity.BOTTOM or Gravity.END, false, false),
        )
        corners.forEach { (gravity, top, left) ->
            val corner = View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(stroke, green)
                    cornerRadius = dp(9).toFloat()
                }
                // Cover the two inner edges to leave a clean L-shaped finder.
                if (top || left) alpha = 0.96f
            }
            val params = FrameLayout.LayoutParams(size, size, gravity).apply {
                if (gravity and Gravity.START == Gravity.START) leftMargin = inset else rightMargin = inset
                if (gravity and Gravity.TOP == Gravity.TOP) topMargin = inset else bottomMargin = inset
            }
            frame.addView(corner, params)
        }
        val line = View(this).apply { setBackgroundColor(green) }
        frame.addView(line, FrameLayout.LayoutParams(-1, dp(2), Gravity.CENTER).apply {
            leftMargin = inset * 2
            rightMargin = inset * 2
        })
    }

    private fun actionButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 14f
        isAllCaps = false
        background = roundedBackground(Color.rgb(49, 92, 73), 14f)
        setOnClickListener { action() }
    }

    private fun label(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        maxLines = 3
    }

    private fun verticalLayout() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    private fun roundedBackground(color: Int, radius: Float) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        textRecognizer?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
