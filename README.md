# ⏳ Not-Timer

**Not-Timer** is a minimalist Android productivity tool that lives entirely within your status bar. By stripping away the traditional app interface, it provides a seamless, distraction-free timing experience driven purely by the Android notification system.

---

## 📸 Screenshots

| Start Timer | Active Timer | Paused Timer | Alarm Ringing |
| :---: | :---: | :---: | :---: |
| <img src="https://github.com/user-attachments/assets/8fa46d8c-3458-404d-9a94-ad4e907d4d0b" width="200" alt="Start Timer"> | <img src="https://github.com/user-attachments/assets/d21003e5-9eb5-4590-b406-b2658970e3ac" width="200" alt="Active Timer"> | <img src="https://github.com/user-attachments/assets/81072ec3-992c-4211-9666-0f5e95dda168" width="200" alt="Paused Timer"> | <img src="https://github.com/user-attachments/assets/ddb17385-1a80-4f8b-8d1d-869d7a888dda" width="200" alt="Alarm Ringing"> |

---

## 🚀 The "Not-App" Philosophy

Unlike standard timers that require you to stay within an activity, **Not-Timer** treats the notification drawer as the primary interface. 

* **Direct Interaction:** Manage starts, stops, and resets directly from the notification actions.
* **Lightweight Logic:** Replaced legacy alarm/broadcast overhead with a streamlined, modern execution flow.
* **Always Available:** Access your timer without switching apps or breaking your current workflow.

---

## 🛠️ Technical Overview

* **Language:** 100% Kotlin
* **UI Pattern:** Notification-only (No persistent Activity lifecycle)
* **Target SDK:** Android 8.0+ (Oreo) for advanced Notification Channel support
* **Core Logic:** Optimized for low-latency updates within the notification tray.

---

## 📂 Project Structure

The codebase is structured to handle high-frequency notification updates without UI overhead:

1. **Service Layer:** Manages the core countdown logic and notification lifecycle.
2. **Notification Builder:** Handles the dynamic rendering of the "Not-UI."
3. **Intent Handlers:** Processes user interactions from the notification buttons.

---

## 📥 Getting Started

1. **Clone:** `git clone https://github.com/bomb468/Not-Timer.git`
2. **Import:** Open the project in Android Studio.
3. **Deploy:** Build and run on a physical device to see the notification interaction in action.
