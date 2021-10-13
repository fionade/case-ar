/*
 * Copyright 2021 Fiona Draxler, Elena Wallwitz. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lmu.arcasegrammar

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources.getColorStateList
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import de.lmu.arcasegrammar.databinding.FragmentCameraBinding
import de.lmu.arcasegrammar.model.DetectedObject
import de.lmu.arcasegrammar.model.HistoryDatabase
import de.lmu.arcasegrammar.sentencebuilder.Sentence
import de.lmu.arcasegrammar.sentencebuilder.SentenceDao
import de.lmu.arcasegrammar.sentencebuilder.SentenceManager
import de.lmu.arcasegrammar.tensorflow.YuvToRgbConverter
import de.lmu.arcasegrammar.tensorflow.tflite.Classifier
import de.lmu.arcasegrammar.tensorflow.tflite.TFLiteObjectDetectionAPIModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.HashMap

typealias ObjectListener = (objects: List<Classifier.Recognition>) -> Unit

class CameraFragment: Fragment() {

    companion object {
        const val TAG = "CameraFragment"

        // Camera permission
        private const val PERMISSIONS_REQUEST = 1
        private const val PERMISSION_CAMERA = Manifest.permission.CAMERA

        // Tensorflow minimum confidence for displaying label
        const val MINIMUM_CONFIDENCE_OBJECT_DETECTION = 0.55f
        const val SHOW_LABEL_DURATION = 5000L
    }

    // Quiz setup
    private lateinit var sentenceManager: SentenceManager
    private lateinit var sentence: Sentence
    private var firstObject: DetectedObject? = null
    private var secondObject: DetectedObject? = null
    private var quizShown = false
    private lateinit var optionList: Array<RadioButton>

    // Tensorflow setup
    protected lateinit var detector: Classifier
    private var computingDetection = false
    private lateinit var croppedBitmap: Bitmap
    // list to track times where objects first appeared
    private val preparationList = HashMap<String, Long>()
    // list of currently displayed labels
    private val labelList = HashMap<String, Chip>()
    private val cropSize = 300

    // Camera setup
    private var previewWidth: Int = 0
    private var previewHeight = 0
    protected var rgbBytes: IntArray? = null

    // CameraX setup
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var converter: YuvToRgbConverter
    private lateinit var bitmapBuffer: Bitmap
    private var imageRotationDegrees: Int = 0

    private lateinit var firebaseLogger: FirebaseLogger

    // View binding
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    // History in Room database
    private lateinit var sentenceDao: SentenceDao

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        val view = binding.root

        // Firebase logging
        firebaseLogger = FirebaseLogger.getInstance()
        firebaseLogger.addLogMessage("opened_camera")

        // Preview setup
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.window?.insetsController?.hide(WindowInsets.Type.statusBars())
        }
        else {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        // Quiz logic setup
        sentenceManager = SentenceManager(requireContext())

        // View setup
        binding.firstSelectedLabel.setOnCloseIconClickListener {
            resetQuiz()
        }

        optionList = arrayOf(binding.option1, binding.option2, binding.option3)
        optionList.forEach { it -> it.setOnClickListener {
            onOptionSelected(it)
        } }


        // check camera permission
        if (hasPermission()) {
            startCamera()

            try {
                detector = TFLiteObjectDetectionAPIModel.create(activity?.assets, "tensorflow/open_images_uint8.tflite", "tensorflow/open_images_labelmap_de.txt", cropSize, false)
                croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888)
            }
            catch (e: IOException) {
                Log.e(TAG, "Exception initializing classifier", e)
                Snackbar.make(binding.container, "Object Detection could not be initialized", Snackbar.LENGTH_SHORT).show()
                activity?.finish()
            }
        } else {
            requestPermission()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        sentenceDao = HistoryDatabase.getDatabase(requireContext()).sentenceDao()

        return view
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity?.checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(
                    context,
                    "Camera permission is required for this app",
                    Toast.LENGTH_LONG
                )
                    .show()
            }
            requestPermissions(
                arrayOf(PERMISSION_CAMERA),
                PERMISSIONS_REQUEST
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                requestPermission();
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(cropSize, cropSize))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ObjectAnalyzer { results ->
                        val mappedRecognitions = LinkedList<Classifier.Recognition>()

                        for (result in results ) {
                            val location = result.location
                            if (location != null && result.confidence >= MINIMUM_CONFIDENCE_OBJECT_DETECTION) {
                                Log.i(MainActivity.TAG, "Found object: %s, confidence: %s".format(result.title, result.confidence))
                                mappedRecognitions.add(result)
                            }
                        }

                        computingDetection = false

                        activity?.runOnUiThread {

                            // recreate with new items
                            for (item in mappedRecognitions) {
                                if (preparationList.containsKey(item.title) && preparationList[item.title]!! > 800) {
                                    if (!labelList.containsKey(item.title)) {

                                        val button = Chip(requireActivity()).apply {
                                            textAlignment = View.TEXT_ALIGNMENT_CENTER
                                            text = item.title
                                            if (Build.VERSION.SDK_INT >= 23) {
                                                chipBackgroundColor = getColorStateList(
                                                    requireActivity(),
                                                    R.color.chip_states
                                                )
                                            } else {
                                                setChipBackgroundColorResource(R.color.quizBackground)
                                            }
                                            // TODO else?

                                            isCheckable = true

                                            if (firstObject?.name == item.title || secondObject?.name == item.title) {
                                                isChecked = true

                                                if (Build.VERSION.SDK_INT < 23) {
                                                    setChipBackgroundColorResource(R.color.backgroundSelected)
                                                }
                                            }
                                        }

                                        // map the object location from the cropped frame to the full camera preview
                                        val location = item.location

                                        // use inverse scaling for the portrait view (landscape camera)
                                        // adjust for center cropping
                                        if (previewWidth == 0 || previewHeight == 0) {
                                            previewWidth = binding.previewView.width
                                            previewHeight = binding.previewView.height
                                        }
                                        val minDimension = previewWidth.coerceAtMost(previewHeight)
                                        button.x =
                                            (minDimension - location.centerY() / cropSize * minDimension).coerceAtMost(
                                                (minDimension - button.width).toFloat()
                                            )
                                        button.y =
                                            (location.centerX() / cropSize * minDimension + (previewWidth.coerceAtLeast(previewHeight) - minDimension) / 2).coerceAtMost(
                                                (minDimension - button.height).toFloat()
                                            )
                                        // cropToFrameTransform.mapRect(location)

                                        binding.labelContainer.addView(button)
                                        labelList[item.title] = button
                                        button.setOnClickListener {
                                            onLabelSelected(item.title, button.x, button.y)
                                            button.isChecked = true

                                            if (Build.VERSION.SDK_INT < 23) {
                                                button.setChipBackgroundColorResource(R.color.backgroundSelected)
                                            }
                                        }
                                        button.postDelayed({
                                            // only show the label for SHOW_LABEL_DURATION milliseconds
                                            labelList.remove(item.title)
                                            (button.parent as ViewGroup).removeView(button)
                                            preparationList.remove(item.title)
                                        }, SHOW_LABEL_DURATION)
                                    }
                                } else {
                                    preparationList[item.title] = System.currentTimeMillis()
                                }
                            }
                        }
                    })
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            converter = YuvToRgbConverter(requireContext())

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun onLabelSelected(text: String, x: Float, y: Float) {
        val newObject = DetectedObject(text, PointF(x, y))
        when {
            firstObject == null -> {
                firstObject = newObject
                binding.firstSelectedLabel.text = text
                binding.firstSelectedLabel.visibility = View.VISIBLE
                quizShown = true
                firebaseLogger.addLogMessage("label_tapped", "added first object $text")
            }
            secondObject == null -> {
                secondObject = newObject
                firebaseLogger.addLogMessage("label_tapped", "added second object $text")
                showQuiz()
            }
            else -> {
                resetQuiz()
                firstObject = newObject
                binding.firstSelectedLabel.text = firstObject?.name
                binding.firstSelectedLabel.visibility = View.VISIBLE
                quizShown = true
                firebaseLogger.addLogMessage("label_tapped", "reset and added first object $text")
            }
        }
    }


    private fun showQuiz() {

        val constructedSentence = sentenceManager.constructSentence(firstObject!!, secondObject!!)
        if (constructedSentence != null) {

            binding.firstSelectedLabel.visibility = View.GONE
            binding.quizContainer.visibility = View.VISIBLE

            sentence = constructedSentence!!

            lifecycleScope.launch(Dispatchers.IO) {
                sentenceDao.insertSentence(sentence)
            }

            binding.part1.text = sentence.firstPart
            binding.part2.text = sentence.secondPart

            binding.option1.text = sentence.distractors[0]
            binding.option2.text = sentence.distractors[1]
            binding.option3.text = sentence.distractors[2]

            binding.solution.text = sentence.wordToChoose
            quizShown = true

            firebaseLogger.addLogMessage("show_quiz", sentence.stringify())
        }
        else {
            Snackbar.make(binding.root, getString(R.string.error_quiz_creation), Snackbar.LENGTH_SHORT).show()
            firebaseLogger.addLogMessage("error_crating_quiz", "${firstObject!!.name} and ${secondObject!!.name}")
        }


    }

    private fun resetQuiz() {
        if (quizShown) {

            // reset the radio group so no item is preselected on subsequent quizzes
            binding.options.clearCheck()

            binding.solution.visibility = View.GONE
            binding.options.visibility = View.VISIBLE
            binding.quizContainer.visibility = View.GONE
            binding.firstSelectedLabel.visibility = View.GONE

            firstObject = null
            secondObject = null

            quizShown = false

            optionList.forEach {
                if (Build.VERSION.SDK_INT >= 23) {
                    it.setTextColor(resources.getColor(R.color.colorText, activity?.theme))
                }
                else {
                    it.setTextColor(resources.getColor(R.color.colorText))
                }
            }
        }
    }

    fun onOptionSelected(view: View) {
        val checked = (view as RadioButton).isChecked

        if (checked) {
            if(view.text == sentence.wordToChoose) {
                binding.options.visibility = View.GONE
                binding.solution.visibility = View.VISIBLE

                firebaseLogger.addLogMessage("answer_selected", "correct: ${sentence.wordToChoose}")

                binding.quizContainer.postDelayed({
                    if (_binding?.solution?.visibility == View.VISIBLE) {
                        resetQuiz()
                    }
                }, 3000) // hide quiz 3 seconds after a correct answer
            }
            else {
                if (Build.VERSION.SDK_INT >= 23) {
                    view.setTextColor(resources.getColor(R.color.colorAccent, activity?.theme))
                }
                else {
                    view.setTextColor(resources.getColor(R.color.colorAccent))
                }

                firebaseLogger.addLogMessage("answer_selected", "wrong: ${view.text}")
            }
        }

    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class ObjectAnalyzer(private val listener: ObjectListener): ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }


        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(image: ImageProxy) {
            if (!::bitmapBuffer.isInitialized) {
                // The image rotation and RGB image buffer are initialized only once
                // the analyzer has started running
                imageRotationDegrees = image.imageInfo.rotationDegrees
                bitmapBuffer = Bitmap.createBitmap(
                    image.width, image.height, Bitmap.Config.ARGB_8888)
            }

            image.use { converter.yuvToRgb(image.image!!, bitmapBuffer) }

            croppedBitmap = ThumbnailUtils.extractThumbnail(bitmapBuffer, cropSize, cropSize)

            // to cropped bitmap
            val detected = detector.recognizeImage(croppedBitmap)
            listener(detected)
        }
    }

}