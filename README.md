# Case AR
Android app for practicing German case grammar with object detection and AR labels

Based on https://github.com/tensorflow/examples/tree/master/lite/examples/object_detection/android

Tested with two Tensorflow Lite models:
* a pre-trained model created from the COCO data set (https://cocodataset.org/)
* an SSD Mobilenet model on the Open Images data set v4 (https://www.tensorflow.org/datasets/catalog/open_images_v4) converted to TF Lite by Katsuya Hyodo, retrieved via https://github.com/PINTO0309/PINTO_model_zoo/tree/main/045_ssd_mobilenet_v2_oid_v4

# Notes
* Logging functionality via Firebase is implemented but currently deactivated. See FirebaseLogger.kt for details.
* Activation of logging requires a suitable Firebase project and corresponding google-services.json

# Authors
Fiona Draxler (LMU Munich), Audrey Labrie, Elena Wallwitz
