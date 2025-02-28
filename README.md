# Sign-Language-Detection-App
This project is focused on creating a real-time sign language detection app that can recognize and interpret specific sign gestures from live video. The app will utilize Mediapipe, a framework for building real-time machine learning pipelines, to detect key points of the body, face, and hands. These key points are processed by a Long Short-Term Memory (LSTM) neural network, a type of recurrent neural network (RNN) well-suited for processing sequential data, to predict and classify sign language gestures. 

⭐ Features
- Real-time Gesture Recognition – Detects and classifies sign language gestures from live video.
- Custom Dataset – Trained on a manually collected dataset of sign gestures.
- Deep Learning-Based – Uses an LSTM model for accurate sign detection.
- Mediapipe Integration – Utilizes advanced hand and body tracking for precise keypoint extraction.
- User-Friendly Interface – Runs on Jupyter Notebook for easy execution and visualization.

🛠️ Tech Stack
    - Programming Language:	Python 
    - Computer Vision:	OpenCV 
    - Machine Learning Framework:	TensorFlow/Keras 
    - Deep Learning Model:	LSTM (Long Short-Term Memory) 
    - Pose & Hand Tracking:	Mediapipe 
    - Data Processing:	NumPy & Pandas 
    - Visualization:	Matplotlib 

🔧 Installation & Setup

1️⃣ Install Dependencies

Ensure you have Python installed. Then, run the following command:
pip install opencv-python numpy mediapipe tensorflow scikit-learn matplotlib

2️⃣ Clone the Repository

git clone https://github.com/ShivankBhasin/https://github.com/ShivankBhasin/Sign-Language-Detection-App.git

cd Sign-Language-Detection-App

3️⃣ Run the Jupyter Notebook
- Open the notebook and execute all cells to train the model.
- Once trained, run the sign detection script to start real-time recognition.

🎯 How It Works
 - Captures real-time video feed using OpenCV.
 - Uses Mediapipe to extract keypoints from hands, face, and body.
 - Feeds the extracted features into an LSTM model trained on sign gestures.
 - Classifies the sign and displays the recognized word on the screen.
