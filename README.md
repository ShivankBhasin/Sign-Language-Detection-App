# 🤟 Hastavakta: A Real-Time Sign Language Detection Android App

**Hastavakta** is a real-time sign language detection Android application that uses **ConvLSTM-based deep learning** to recognize sign language gestures from live camera input. The app helps bridge the communication gap between sign language users and non-signers, providing an inclusive and accessible communication experience.

---

## ⭐ Features

- **Real-time Gesture Recognition** – Detects and classifies sign language gestures from live Android camera feed.
- **Custom Dataset** – Trained on a manually collected dataset of sign gestures, with temporal keypoint sequences.
- **Deep Learning-Based** – Uses a ConvLSTM model for capturing spatial and temporal patterns in gestures.
- **Mediapipe Integration** – Utilizes advanced hand, pose, and face tracking for accurate keypoint extraction.
- **Android Interface** – Runs as a native mobile app with intuitive UI and real-time prediction feedback.

---

## 🛠️ Tech Stack

| Category              | Technology                         |
|----------------------|-------------------------------------|
| Programming Language  | Java (Android), Python (for model training) |
| Model Framework       | TensorFlow / Keras (ConvLSTM)      |
| Pose & Hand Tracking  | Mediapipe                          |
| Mobile Development    | Android Studio (Java, XML, CameraX)|
| Model Deployment      | TensorFlow Lite (TFLite)           |
| UI Components         | ConstraintLayout, ViewFlipper, Buttons, TextViews |
| Key Libraries         | OpenCV, TFLite, Mediapipe, Android TTS |

---

## 📲 Installation & Setup (Android)

1️⃣ **Clone the Repository**

```bash
git clone https://github.com/ShivankBhasin/Sign-Language-Detection-App.git
cd Sign-Language-Detection-App

2️⃣ Open in Android Studio

Launch Android Studio

Select "Open an existing project"

Navigate to SignLangApp folder and open it

3️⃣ Build & Run

Connect your Android device or start an emulator

Click Run ▶ to install and launch the app

🎯 How It Works
Live Camera Input: The app captures real-time video using Android's CameraX API.

Pose Detection: Mediapipe detects hand, pose, and face keypoints from each frame.

Keypoint Processing: Frames are converted into structured arrays of 3D coordinates.

ConvLSTM Prediction: A trained TFLite model processes temporal sequences of keypoints to classify gestures.

Real-time Output: The app displays the predicted sign and adds it to a rolling sentence with optional text-to-speech output.

📁 App Structure Highlights
SignLangApp/
├── app/
│   ├── java/com/example/signlangapp/
│   │   ├── MainActivity.java             # Splash screen + user role prompt
│   │   ├── SignDetectionActivity.java    # Core real-time detection logic
│   │   └── ReverseSignActivity.java      # Converts text back into sign visuals
│   └── res/
│       ├── layout/                       # XML layout files
│       ├── values/                       # Strings, themes
│       └─



─ drawable/                     # Sign images (for reverse mode)
├── assets/
│   └── convlstm_model.tflite             # Trained TFLite model

📷 Demo
https://github.com/user-attachments/assets/acce5a4c-ed4b-4dfb-b848-0ac0ddd2b91f
