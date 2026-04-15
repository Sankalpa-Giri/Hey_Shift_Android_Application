<div align="center">

<img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
<img src="https://img.shields.io/badge/Language-Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white"/>
<img src="https://img.shields.io/badge/AI-Powered-FF6B6B?style=for-the-badge&logo=openai&logoColor=white"/>
<img src="https://img.shields.io/badge/Status-Active-00C851?style=for-the-badge"/>

<br/><br/>

```
 █████╗ ██╗   ██╗██████╗ ██╗██████╗  ██████╗ ████████╗
██╔══██╗██║   ██║██╔══██╗██║██╔══██╗██╔═══██╗╚══██╔══╝
███████║██║   ██║██║  ██║██║██████╔╝██║   ██║   ██║   
██╔══██║██║   ██║██║  ██║██║██╔══██╗██║   ██║   ██║   
██║  ██║╚██████╔╝██████╔╝██║██████╔╝╚██████╔╝   ██║   
╚═╝  ╚═╝ ╚═════╝ ╚═════╝ ╚═╝╚═════╝  ╚═════╝   ╚═╝   
```

### 🚗🎙️ Your AI-Powered Traffic, Navigation & Weather Voice Assistant

*Ask. Navigate. Arrive.*

<br/>

[**📲 Download APK**](#installation--setup) · [**🐛 Report Bug**](https://github.com/Sankalpa-Giri/MAP_Chatbot_Application/issues) · [**✨ Request Feature**](https://github.com/Sankalpa-Giri/MAP_Chatbot_Application/issues)

</div>

---

## 📖 Overview

**AudiBot** is a smart Android voice assistant built for drivers and commuters. It combines real-time traffic navigation, live weather updates, and AI-powered conversations — all through an intuitive chat and voice interface. Ask it anything from *"Take me to the nearest hospital"* to *"How's traffic on MG Road?"* and get instant, spoken responses.

> Built with a focus on hands-free, eyes-on-road safety — because your phone shouldn't distract you while driving.

---

## ✨ Features

<table>
<tr>
<td width="50%">

### 🎙️ Voice Interface
- Wake word detection — say **"Hey Shift"** to activate
- Speech-to-Text using Android SpeechRecognizer
- Text-to-Speech responses — fully hands-free
- Mic button for manual activation

</td>
<td width="50%">

### 🚦 Smart Navigation
- Real-time turn-by-turn directions
- Live traffic status & congestion alerts
- ETA calculation with traffic delays
- Alternate route suggestions
- Nearest place finder (hospital, petrol pump, ATM, café)

</td>
</tr>
<tr>
<td width="50%">

### 🌦️ Weather Updates
- Current temperature, feels-like, humidity
- Rain, cloud, wind condition reports
- City-based or GPS-based weather lookup
- Smart contextual responses ("Should I carry an umbrella?")

</td>
<td width="50%">

### 💬 Conversation Memory
- Context-aware follow-up understanding
- Saved addresses (home, office, gym)
- Full chat history with session management
- Multi-session support with resume capability

</td>
</tr>
<tr>
<td width="50%">

### 🔐 Authentication
- Secure JWT-based login & registration
- Token persistence across sessions
- Auto-login on app relaunch
- Profile management

</td>
<td width="50%">

### 📜 Chat History
- All sessions saved to cloud (MongoDB)
- Session browsing with title & preview
- Swipe-to-delete sessions
- Resume any previous conversation

</td>
</tr>
</table>

---

## 🛠️ Tech Stack

### 📱 Android (Frontend)
| Technology | Purpose |
|---|---|
| **Java** | Core application logic |
| **XML** | UI layouts and design |
| **Android SDK** | Platform APIs |
| **SpeechRecognizer API** | Voice input (Speech-to-Text) |
| **TextToSpeech API** | Voice output |
| **Porcupine SDK** | Wake word detection ("Hey Shift") |
| **LocationManager** | Real-time GPS coordinates |
| **RecyclerView** | Chat & session list UI |
| **Material Design** | UI components & theming |
| **Gson** | JSON serialization |
| **Gradle (Kotlin DSL)** | Build system |

### ⚙️ Backend
| Technology | Purpose |
|---|---|
| **FastAPI (Python)** | REST API server |
| **MongoDB (Motor)** | Async database for users & chats |
| **ChromaDB** | Vector memory for saved addresses |
| **Ollama (TinyLlama)** | Local LLM for intent classification |
| **LangChain** | LLM orchestration & prompt management |
| **Google Maps API** | Directions, traffic, nearby places |
| **OpenWeather API** | Real-time weather data |
| **JWT (python-jose)** | Authentication tokens |
| **Uvicorn** | ASGI server |

### 🏗️ Architecture
```
┌─────────────────┐     HTTP/REST      ┌──────────────────────┐
│   Android App   │ ◄────────────────► │   FastAPI Backend     │
│                 │                    │                       │
│  ┌───────────┐  │                    │  ┌─────────────────┐  │
│  │  Voice UI  │  │                    │  │  Domain Parser  │  │
│  │  Chat UI  │  │                    │  │  Intent Parser  │  │
│  │  History  │  │                    │  │  Action Handler │  │
│  └───────────┘  │                    │  └────────┬────────┘  │
└─────────────────┘                    │           │           │
                                       │  ┌────────▼────────┐  │
                                       │  │    MongoDB      │  │
                                       │  │    ChromaDB     │  │
                                       │  │  Google Maps    │  │
                                       │  │  OpenWeather    │  │
                                       │  └─────────────────┘  │
                                       └──────────────────────┘
```

---

## 📁 Project Structure

```
AudiBot/
│
├── 📱 Android App
│   └── app/src/main/
│       ├── java/com/example/audibot/
│       │   ├── MainActivity.java           # Main chat interface + voice
│       │   ├── LoginActivity.java          # User login
│       │   ├── RegisterActivity.java       # User registration
│       │   ├── ChatHistoryActivity.java    # Browse past sessions
│       │   ├── SessionDetailActivity.java  # View & delete a session
│       │   ├── ProfileActivity.java        # User profile
│       │   ├── ChatAdapter.java            # RecyclerView chat adapter
│       │   ├── Message.java               # Chat message model
│       │   └── Constants.java             # Server URL config
│       │
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   ├── activity_login.xml
│           │   ├── activity_chat_history.xml
│           │   ├── activity_session_detail.xml
│           │   ├── activity_profile.xml
│           │   ├── item_user.xml           # User chat bubble
│           │   ├── item_bot.xml            # Bot chat bubble
│           │   └── item_session.xml        # Session list item
│           ├── menu/
│           │   └── menu_session_detail.xml
│           └── values/
│               ├── strings.xml
│               ├── colors.xml
│               └── themes.xml
│
└── 🐍 Backend (Python)
    ├── server.py                  # FastAPI server entry point
    ├── main.py                    # Pipeline orchestrator
    ├── routes.py                  # Auth + chat history routes
    ├── models.py                  # Pydantic data models
    ├── database.py                # MongoDB connection
    ├── auth.py                    # JWT authentication
    ├── config.py                  # Configuration constants
    ├── conversation_store.py      # Session context memory
    ├── identify_domain.py         # Domain classification
    ├── identify_intent.py         # Intent & entity extraction
    ├── driver_rag.py              # Vector memory (ChromaDB)
    ├── ActionHandlers/
    │   ├── navigation_handler.py
    │   ├── traffic_status_handler.py
    │   ├── weather_handler.py
    │   ├── places_handler.py
    │   ├── memory_handler.py
    │   └── chitchat_handler.py
    ├── FetchServices/
    │   ├── fetch_maps.py          # Google Maps API
    │   └── fetch_weather.py       # OpenWeather API
    └── Generate/
        ├── generate_response.py
        └── generate_response_weather.py
```

---

## ⚙️ Installation & Setup

### Prerequisites
- Android Studio (Hedgehog or later)
- Android device or emulator (API 26+)
- Python 3.10+
- Ollama installed locally
- MongoDB Atlas account (or local MongoDB)

---

### 📱 Android App Setup

**1. Clone the repository**
```bash
git clone https://github.com/Sankalpa-Giri/MAP_Chatbot_Application.git
cd MAP_Chatbot_Application
```

**2. Open in Android Studio**
- Launch Android Studio
- Click `File → Open`
- Select the cloned folder
- Wait for Gradle sync to complete

**3. Configure Server URL**

Edit `app/src/main/java/com/example/audibot/Constants.java`:
```java
public class Constants {
    // Replace with your backend server URL
    public static final String BASE_URL = "https://your-ngrok-or-server-url.com";
}
```

**4. Add Porcupine Wake Word File**

Place your `hey-shift_en_android_v4_0_0.ppn` file in:
```
app/src/main/assets/hey-shift_en_android_v4_0_0.ppn
```

**5. Run the App**
- Connect device or start emulator
- Click ▶️ **Run** in Android Studio

---

### 🐍 Backend Setup

**1. Install Python dependencies**
```bash
cd backend
pip install -r requirements.txt
```

**2. Set up API Keys**

Create the `API Keys/` directory and add your keys:
```
backend/
└── API Keys/
    ├── Google_maps_api_key.txt     # Google Maps API key
    └── openweather_api_key.txt     # OpenWeather API key
```

**3. Configure MongoDB**

In `config.py`, update your MongoDB connection string:
```python
MONGO_URL = "mongodb+srv://username:password@cluster.mongodb.net/audibot"
```

**4. Pull Ollama models**
```bash
ollama pull tinyllama
ollama pull nomic-embed-text
```

**5. Start the server**
```bash
cd backend
python -m uvicorn server:app --host 0.0.0.0 --port 8000 --reload
```

**6. Expose via ngrok (for Android device)**
```bash
ngrok http 8000
# Copy the https URL → paste into Constants.java BASE_URL
```

---

## 📱 App Screens

| Screen | Description |
|---|---|
| 🔐 **Login / Register** | Secure JWT authentication |
| 💬 **Chat Interface** | Main voice + text assistant UI |
| 📜 **Chat History** | Browse all past sessions, swipe to delete |
| 🗂️ **Session Detail** | View full conversation, resume or delete |
| 👤 **Profile** | View account info and logout |

---

## 🗣️ Example Conversations

```
You:    "Take me to the nearest hospital"
Bot:    "The nearest hospital is KIMS Super Speciality, 1.5 km away —
         about 6 mins drive. Would you like me to navigate there?"

You:    "Yes"
Bot:    "Heading to KIMS via KIIT Campus 8 Rd. Turn-by-turn:
         1. Head east on KIIT Campus Rd — 500m..."

You:    "How's the traffic there?"
Bot:    "Traffic to KIMS is clear. You should reach in about 6 mins
         with no delays."

You:    "Is it raining?"
Bot:    "No rain expected in Bhubaneswar right now. It's 28°C,
         feels like 30°C with haze."

You:    "Save my home as KIIT Square"
Bot:    "Got it. I'll remember your home address."

You:    "Take me home"
Bot:    "Heading to KIIT Square via KIMS Rd. 1.4 km, about 6 mins.
         Traffic is clear."
```

---

## 🔮 Future Enhancements

- [ ] 🗺️ In-app map rendering with route visualization
- [ ] 📡 Real-time traffic overlay on map
- [ ] 🌐 Multi-language support
- [ ] 📊 Trip analytics dashboard
- [ ] 🔔 Proactive traffic alerts (before you leave)
- [ ] 🎵 Music/media controls via voice
- [ ] 🚨 Emergency SOS integration
- [ ] ☁️ Offline mode for basic navigation

---

## 🤝 Contributing

Contributions are welcome! Here's how:

```bash
# 1. Fork the project
# 2. Create your feature branch
git checkout -b feature/AmazingFeature

# 3. Commit your changes
git commit -m "Add AmazingFeature"

# 4. Push to the branch
git push origin feature/AmazingFeature

# 5. Open a Pull Request
```

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

---



---

<div align="center">

**⭐ If AudiBot helped you, give it a star on GitHub! ⭐**

*Built with ❤️ for safer, smarter driving*

</div>
