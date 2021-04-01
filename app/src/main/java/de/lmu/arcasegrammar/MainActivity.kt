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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.View
import android.view.View.TEXT_ALIGNMENT_CENTER
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RadioButton
import android.widget.Toast
import com.google.android.material.chip.Chip
import de.lmu.arcasegrammar.model.DetectedObject
import de.lmu.arcasegrammar.sentencebuilder.Sentence
import de.lmu.arcasegrammar.sentencebuilder.SentenceManager
import de.lmu.arcasegrammar.tensorflow.CameraActivity
import de.lmu.arcasegrammar.tensorflow.env.ImageUtils
import de.lmu.arcasegrammar.tensorflow.tflite.Classifier
import de.lmu.arcasegrammar.tensorflow.tflite.TFLiteObjectDetectionAPIModel
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.util.*
import kotlin.collections.HashMap

class MainActivity : CameraActivity() {


    companion object {
        const val TAG = "MainActivity"

        // Tensorflow minimum confidence for displaying label
        const val MINIMUM_CONFIDENCE_OBJECT_DETECTION = 0.65f
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
    private lateinit var detector: Classifier
    private var computingDetection = false
    private lateinit var rgbFrameBitmap: Bitmap
    private lateinit var croppedBitmap: Bitmap
    private lateinit var frameToCropTransform: Matrix
    private lateinit var cropToFrameTransform: Matrix
    // list to track times where objects first appeared
    private val preparationList = HashMap<String, Long>()
    // list of currently displayed labels
    private val labelList = HashMap<String, Chip>()
    private val cropSize = 300

    private lateinit var infoFragment: InfoFragment

    private lateinit var firebaseLogger: FirebaseLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase logging
        firebaseLogger = FirebaseLogger.getInstance()

        val sharedPreferences = getSharedPreferences("casear", Context.MODE_PRIVATE)
        var userID = sharedPreferences.getString("userID", "")
        userID?.let {
            if (it.isEmpty()) {
                userID = UUID.randomUUID().toString().substring(IntRange(0, 7))
                sharedPreferences
                    .edit()
                    .putString("userID", userID)
                    .apply()
                // user ID should be set with the first log message!
                firebaseLogger.setUserId(userID!!)
            }
        }

        firebaseLogger.addLogMessage("app_started")

        // Preview setup
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // set layout
        setContentView(R.layout.activity_main)

        // info button opens infoFragment
        infoFragment = InfoFragment()

        infoButton.setOnClickListener {
            if (!infoFragment.isAdded) {
                val fragmentManager = supportFragmentManager
                val fragmentTransaction = fragmentManager.beginTransaction()
                fragmentTransaction.setCustomAnimations(R.anim.slide_in, R.anim.slide_out, R.anim.slide_in, R.anim.slide_out)
                fragmentTransaction.replace(R.id.fragmentContainer, infoFragment)
                fragmentTransaction.addToBackStack("info")
                fragmentTransaction.commit()
                firebaseLogger.addLogMessage("show_info")
            }
        }

        // Quiz logic setup
        sentenceManager = SentenceManager(this)

        // View setup
        firstSelectedLabel.setOnCloseIconClickListener {
            resetQuiz()
        }

        optionList = arrayOf(option1, option2, option3)

    }

    override fun onPreviewSizeChosen(size: Size?, rotation: Int) {

        Log.d(TAG, "Preview size: width %d, height %d, rotation %d".format(size!!.width, size.height, rotation))

        // setting up Tensorflow Object Detection
        try {
            detector = TFLiteObjectDetectionAPIModel.create(assets, "tensorflow/detect.tflite", "tensorflow/labelmap.txt", cropSize, true)
        }
        catch (e: IOException) {
            Log.e(TAG, "Exception initializing classifier", e)
            Toast.makeText(applicationContext, "Object Detection could not be initialized", Toast.LENGTH_SHORT).show()
            finish()
        }

        previewWidth = size.width
        previewHeight = size.height

        val sensorOrientation = rotation - screenOrientation
        Log.i(TAG, "Camera orientation relative to screen canvas: %d".format(sensorOrientation))

        // The object detection works with cropped images, so the camera preview image needs to be prepared
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888)
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)


        // matrix definitions for transforming the preview bitmap to the object detection bitmap and back
        frameToCropTransform =
            ImageUtils.getTransformationMatrix(
                previewWidth,
                previewHeight,
                cropSize,
                cropSize,
                sensorOrientation,
                false // MAINTAIN aspect false
            )

        cropToFrameTransform = Matrix()
        frameToCropTransform.invert(cropToFrameTransform)

    }

    override fun processImage() {

        if (computingDetection) {
            readyForNextImage()
            return
        }

        // flag to avoid simultaneous processing
        computingDetection = true
        // LOGGER.i("Preparing image for detection in bg thread.")

        // get camera preview pixels
        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight)

        readyForNextImage()

        // create cropped image for object detector
        val canvas = Canvas(croppedBitmap)
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null)

        runInBackground {

            // LOGGER.i("running detection on image")
            val results = detector.recognizeImage(croppedBitmap)

            val mappedRecognitions = LinkedList<Classifier.Recognition>()

            for (result in results ) {
                val location = result.location
                if (location != null && result.confidence >= MINIMUM_CONFIDENCE_OBJECT_DETECTION) {
                    Log.i(TAG, "Found object: %s, confidence: %s".format(result.title, result.confidence))
                    mappedRecognitions.add(result)
                }
            }

            computingDetection = false

            runOnUiThread {

                // recreate with new items
                for (item in mappedRecognitions) {
                    if (preparationList.containsKey(item.title) && preparationList[item.title]!! > 800) {
                        if (!labelList.containsKey(item.title)) {

                            val button = Chip(this).apply {
                                textAlignment = TEXT_ALIGNMENT_CENTER
                                text = item.title
                                if (Build.VERSION.SDK_INT >= 23) {
                                    chipBackgroundColor = getColorStateList(R.color.chip_states)
                                }
                                else {
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
                            button.x = location.centerX() / cropSize * previewHeight
                            button.y = location.centerY() / cropSize * previewWidth
                            // cropToFrameTransform.mapRect(location)

                            labelContainer.addView(button)
                            labelList[item.title] = button
                            button.setOnClickListener {
                                onLabelSelected(item.title, button.x, button.y)
                                button.isChecked = true

                                if (Build.VERSION.SDK_INT < 23) {
                                    button.setChipBackgroundColorResource(R.color.backgroundSelected)
                                }
                            }
                            button.postDelayed( {
                                // only show the label for SHOW_LABEL_DURATION milliseconds
                                labelList.remove(item.title)
                                (button.parent as ViewGroup).removeView(button)
                                preparationList.remove(item.title)
                            }, SHOW_LABEL_DURATION)
                        }
                    }
                    else {
                        preparationList[item.title] = System.currentTimeMillis()
                    }

                }

            }

        }
    }

    override fun getLayoutId(): Int {
        return R.layout.camera_connection_fragment_tracking
    }

    override fun getDesiredPreviewFrameSize(): Size {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        // choose a 4:3 ratio based on the screen width
        val height = displayMetrics.widthPixels / 3 * 4

        return Size(displayMetrics.widthPixels, height)
    }


    private fun onLabelSelected(text: String, x: Float, y: Float) {
        val newObject = DetectedObject(text, PointF(x, y))
        when {
            firstObject == null -> {
                firstObject = newObject
                firstSelectedLabel.text = text
                firstSelectedLabel.visibility = View.VISIBLE
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
                firstSelectedLabel.text = firstObject?.name
                firstSelectedLabel.visibility = View.VISIBLE
                quizShown = true
                firebaseLogger.addLogMessage("label_tapped", "reset and added first object $text")
            }
        }
    }

    private fun showQuiz() {

        firstSelectedLabel.visibility = View.GONE
        quizContainer.visibility = View.VISIBLE

        // test: try with one fixed sentence
        sentence = sentenceManager.constructSentence(firstObject!!, secondObject!!)

        part1.text = sentence.firstPart
        part2.text = sentence.secondPart

        option1.text = sentence.distractors[0]
        option2.text = sentence.distractors[1]
        option3.text = sentence.distractors[2]

        solution.text = sentence.wordToChoose
        quizShown = true

        firebaseLogger.addLogMessage("show_quiz", sentence.stringify())
    }

    private fun resetQuiz() {
        if (quizShown) {

            // reset the radio group so no item is preselected on subsequent quizzes
            options.clearCheck()

            solution.visibility = View.GONE
            options.visibility = View.VISIBLE
            quizContainer.visibility = View.GONE
            firstSelectedLabel.visibility = View.GONE

            firstObject = null
            secondObject = null

            quizShown = false

            optionList.forEach {
                if (Build.VERSION.SDK_INT >= 23) {
                    it.setTextColor(resources.getColor(R.color.colorText, application.theme))
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
                options.visibility = View.GONE
                solution.visibility = View.VISIBLE

                firebaseLogger.addLogMessage("answer_selected", "correct: ${sentence.wordToChoose}")

                quizContainer.postDelayed({
                    if (solution.visibility == View.VISIBLE) {
                        resetQuiz()
                    }
                }, 3000) // hide quiz 3 seconds after a correct answer
            }
            else {
                if (Build.VERSION.SDK_INT >= 23) {
                    view.setTextColor(resources.getColor(R.color.colorAccent, application.theme))
                }
                else {
                    view.setTextColor(resources.getColor(R.color.colorAccent))
                }

                firebaseLogger.addLogMessage("answer_selected", "wrong: ${view.text}")
            }
        }

    }

    override fun onPause() {
        firebaseLogger.addLogMessage("app_paused")
        super.onPause()
    }


}
