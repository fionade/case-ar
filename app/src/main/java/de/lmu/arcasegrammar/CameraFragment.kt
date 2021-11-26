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
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources.getColorStateList
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture
import de.lmu.arcasegrammar.databinding.FragmentCameraBinding
import de.lmu.arcasegrammar.model.DetectedObject
import de.lmu.arcasegrammar.sentencebuilder.Sentence
import de.lmu.arcasegrammar.tensorflow.YuvToRgbConverter
import de.lmu.arcasegrammar.tensorflow.tflite.Classifier
import de.lmu.arcasegrammar.tensorflow.tflite.TFLiteObjectDetectionAPIModel
import de.lmu.arcasegrammar.viewmodel.DetectionViewModel
import java.io.IOException
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
        // view duration of object labels
        const val SHOW_LABEL_DURATION = 5000L
        // image dimensions for Tensorflow input
        const val CROP_SIZE = 300
    }

    // Quiz setup
    private lateinit var optionList: Array<Chip>
    private lateinit var bottomSheet: BottomSheetBehavior<LinearLayout>

    // Tensorflow setup
    private lateinit var detector: Classifier
    private var computingDetection = false
    private lateinit var croppedBitmap: Bitmap
    // list of currently displayed labels
    private var labelList = HashMap<String, Chip>()

    // Camera setup
    private var previewWidth = 0
    private var previewHeight = 0

    // CameraX setup
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var converter: YuvToRgbConverter
    private lateinit var bitmapBuffer: Bitmap
    private var imageRotationDegrees: Int = 0

    // Logger
    private lateinit var firebaseLogger: FirebaseLogger

    // View binding
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    // connect to ViewModel
    private val viewModel: DetectionViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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

        bottomSheet = BottomSheetBehavior.from(binding.optionContainer)
        bottomSheet.isHideable = true
        bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN
//        bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED


        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // users can trigger a new quiz being created by tapping the start button
        binding.startQuiz.setOnClickListener {
            viewModel.startQuiz()
        }

        // The start button is only visible if at least one label has been selected
        viewModel.preparationList.observe(viewLifecycleOwner, {
            if (it != null && it.size > 0 && viewModel.sentence.value == null) {
                binding.startQuiz.visibility = View.VISIBLE
                binding.startQuiz.show()
            }
            else {
                binding.startQuiz.visibility = View.GONE
            }
        })

        // Show a quiz once it's available
        viewModel.sentence.observe(viewLifecycleOwner, {
            // set the sentence and show the quiz
            if (it != null) {
                showQuiz(it)
                binding.startQuiz.visibility = View.GONE
            }

            // the sentence is null - hide the quiz
            else {
                resetQuiz()
            }
        })

        optionList = arrayOf(binding.option1, binding.option2, binding.option3)
        optionList.forEach { it ->
            it.setOnClickListener {onOptionSelected(it) }
        }

        // check camera permission
        if (hasPermission()) {
            startCamera()

        } else {
            requestPermission()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        super.onViewCreated(view, savedInstanceState)
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
                requestPermission()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener(CameraRunnable(cameraProviderFuture, this), ContextCompat.getMainExecutor(requireContext()))

        try {
            detector = TFLiteObjectDetectionAPIModel.create(activity?.assets, "tensorflow/open_images_uint8.tflite", "tensorflow/open_images_labelmap_de.txt", CROP_SIZE, false)
            croppedBitmap = Bitmap.createBitmap(CROP_SIZE, CROP_SIZE, Bitmap.Config.ARGB_8888)
        }
        catch (e: IOException) {
            Log.e(TAG, "Exception initializing classifier", e)
            Snackbar.make(binding.container, "Object Detection could not be initialized", Snackbar.LENGTH_SHORT).show()
            activity?.finish()
        }
    }

    private fun onLabelSelected(text: String, x: Float, y: Float) {
        val newObject = DetectedObject(text, PointF(x, y))

        viewModel.addObject(newObject)
        bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED

    }


    private fun showQuiz(sentence: Sentence) {

        binding.optionChipGroup.removeAllViews()

        // remove all checked labels from the label list
        labelList = labelList.filter { !it.value.isChecked } as HashMap<String, Chip>

        binding.part1.text = sentence.firstPart
        binding.part2.text = sentence.secondPart

        binding.option1.text = sentence.distractors[0]
        binding.option2.text = sentence.distractors[1]
        binding.option3.text = sentence.distractors[2]

        firebaseLogger.addLogMessage("show_quiz", sentence.stringify())

        binding.quizContainer.visibility = View.VISIBLE

    }

    private fun resetQuiz() {
        // reset the radio group so no item is preselected on subsequent quizzes
        binding.options.clearCheck()

        optionList.forEach {
            it.setChipBackgroundColorResource(R.color.colorOptionBackground)
            it.setTextColor(getColorStateList(requireActivity(), R.color.chip_states))
            it.isCheckable = true
        }

        // remove all checked labels from the label list
        labelList = labelList.filter { !it.value.isChecked } as HashMap<String, Chip>

        binding.quizContainer.visibility = View.GONE
//        bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun onOptionSelected(view: View) {
        val chip = view as Chip

        if(view.text == viewModel.sentence.value?.wordToChoose) {
            // correct solution found

            firebaseLogger.addLogMessage("answer_selected", "correct: ${view.text}")

            chip.setChipBackgroundColorResource(R.color.colorAnswerCorrect)

            optionList.forEach {
                it.isCheckable = false
            }

            binding.quizContainer.postDelayed({
                if (_binding != null) {
                    viewModel.reset()
                }
            }, 3000) // hide quiz 3 seconds after a correct answer
        }
        else {

            chip.setChipBackgroundColorResource(R.color.colorAnswerIncorrect)

            // cannot be selected twice
            chip.isCheckable = false

            firebaseLogger.addLogMessage("answer_selected", "wrong: ${view.text}")
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

    private inner class CameraRunnable(private val cameraProviderFuture: ListenableFuture<ProcessCameraProvider>, private val lifecycleOwner: LifecycleOwner) : Runnable {
        override fun run() {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(CROP_SIZE, CROP_SIZE))
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
                            showLabels(mappedRecognitions)
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
                    lifecycleOwner, cameraSelector, preview, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }

        private fun showLabels(mappedRecognitions: LinkedList<Classifier.Recognition>) {
            // recreate with new items
            for (item in mappedRecognitions) {
                if (!labelList.containsKey(item.title)) {

                    val button = Chip(requireActivity()).apply {
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                        text = item.title
                        if (Build.VERSION.SDK_INT >= 23) {
                            chipBackgroundColor = getColorStateList(requireActivity(), R.color.chip_states)
                        } else {
                            setChipBackgroundColorResource(R.color.quizBackground)
                        }

                        isCheckable = true
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
                        (minDimension - location.centerY() / CROP_SIZE * minDimension).coerceAtMost(
                            (minDimension - button.width).toFloat()
                        )
                    button.y =
                        (location.centerX() / CROP_SIZE * minDimension + (previewWidth.coerceAtLeast(previewHeight) - minDimension) / 2).coerceAtMost(
                            (minDimension - button.height).toFloat()
                        )

                    button.postDelayed({
                        // only show the label for SHOW_LABEL_DURATION milliseconds
                        if (!button.isChecked) {
                            labelList.remove(item.title)
                            if (button.parent != null) {
                                (button.parent as ViewGroup).removeView(button)
                            }
                        }
                    }, SHOW_LABEL_DURATION)


                    // cropToFrameTransform.mapRect(location)

                    binding.labelContainer.addView(button)
                    labelList[item.title] = button

                    button.setOnClickListener {
                        if (button.isChecked) {
                            // add to constraint layout
                            binding.labelContainer.removeView(button)

                            // TODO performance: only add if there is no object in the group yet
                            button.x = 0F
                            button.y = 0F
                            binding.optionChipGroup.addView(button)

                            if (Build.VERSION.SDK_INT < 23) {
                                button.setChipBackgroundColorResource(R.color.backgroundSelected)
                            }
                            onLabelSelected(item.title, button.x, button.y)
                        }
                        else {

                            viewModel.deleteObject(item.title)
                            labelList.remove(item.title)
                            binding.optionChipGroup.removeView(button)

                            if (Build.VERSION.SDK_INT < 23) {
                                button.setChipBackgroundColorResource(R.color.quizBackground)
                            }

                        }

                    }
                }
            }
        }

    }

    private inner class ObjectAnalyzer(private val listener: ObjectListener): ImageAnalysis.Analyzer {

//        private fun ByteBuffer.toByteArray(): ByteArray {
//            rewind()    // Rewind the buffer to zero
//            val data = ByteArray(remaining())
//            get(data)   // Copy the buffer into a byte array
//            return data // Return the byte array
//        }


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

            croppedBitmap = ThumbnailUtils.extractThumbnail(bitmapBuffer, CROP_SIZE, CROP_SIZE)

            // to cropped bitmap
            val detected = detector.recognizeImage(croppedBitmap)
            listener(detected)
        }
    }

}