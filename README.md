# PicScan - AI Drink Scanner (Android)

**PicScan** is a modern Android application built for **Android 17 / API 35+** that lets you point your camera at any drink (wine, beer, cocktails, specialty coffee, tea, energy drinks, sodas, spirits, kombucha) and uses **Google Gemini AI Vision** to instantly extract comprehensive drink information, sommelier tasting notes, flavor profiles, nutritional breakdown, and pairing recommendations.

---

## 🍺 5-Stufiges Bier-Ranking ("Hopfenbombe" bis "Pissbrühe")

PicScan beinhaltet eine intelligente 5-stufige Bier-Bewertung mit individuellen Sound-Effekten, Haptik-Vibrationen und Fullscreen-Animationen:

1. 💣💥 **"Hopfenbombe!"** (Rang 1/5 - Meisterwerk):
   - Für außergewöhnliche Craft Biere, intensive Double/Triple IPAs, Imperial Stouts, Trappistenbiere und hopfenintensive Spezialitäten.
   - *Effekt*: Energetischer Explosions-Sweep & Akkord, Shockwave-Animation und Hopfen/Bomben-Partikel.
2. 🍻✨ **"Lecker Bierchen!"** (Rang 2/5 - Hohe Braukunst):
   - Für hervorragende traditionelle Qualitätsbiere und beliebte Klassiker (z. B. Augustiner, Tegernseer, Weihenstephaner, Rothaus, Paulaner, Guinness, Chiemseer, etc.).
   - *Effekt*: Feierliches Fanfaren-Arpeggio, Goldener Amber-Strobe und Bierkrug-Sparkles.
3. 🚶‍♂️🍺 **"Wegbier!"** (Rang 3/5 - Kiosk- & Späti-Held):
   - Für solide, süffige Alltags-Lager und Späti-Begleiter (z. B. Astra, Sternburg, Krombacher, Bitburger, Beck's, Jever, Flensburger, etc.).
   - *Effekt*: Fröhlich-beschwingte Walking-Melodie und dynamische Cyan-Vibrationen.
4. 🥫🥴 **"Pennerglück!"** (Rang 4/5 - Sparfuchs-Dosenkracher):
   - Für kultiges, günstiges Discounter-Dosenbier (z. B. Oettinger, 5,0 Original, Hansa Pils, Paderborner, Karlskrone, Turmbräu, etc.).
   - *Effekt*: Wonky metallisches Dosen-Klimpern und wackelnde Dose-Animation.
5. 🤢🚽 **"Pissbrühe!"** (Rang 5/5 - Untrinkbare Plörre):
   - Für wässrige, abgestandene Plörre und gefürchtete Billigst-Plempe (z. B. Perlenbacher, Natty Light, Keystone Light, etc.).
   - *Effekt*: Dissonanter Fail-Buzzer, toxisch-grüner Alarm-Strobe und Gefahrensymbol-Erdbeben.

---

## 💡 Can I use my Google AI Pro (Google One AI Premium) plan?

### Short Answer:
**Consumer subscriptions** (like *Google AI Pro* / *Google One AI Premium* / *Gemini Advanced*) are intended for consumer chat applications (e.g. Gemini mobile app and `gemini.google.com`). They **do not provide API keys** for standalone apps or developer SDKs.

### How to use Google Gemini AI for this app (Free!):
1. Google provides developer API access through **[Google AI Studio](https://aistudio.google.com/)**.
2. **Google AI Studio provides a free tier** that gives you free requests per minute for Gemini models (Gemini 3.6 Flash / Gemini 2.0 Flash / Gemini 1.5 Flash / Gemini 1.5 Pro).
3. **Steps to get your key:**
   - Go to [Google AI Studio API Keys](https://aistudio.google.com/app/apikey).
   - Sign in with your Google account.
   - Click **"Create API Key"** and copy the key (`AIzaSy...`).
   - Paste it in the **Settings** screen inside PicScan, or define it in your `local.properties`:
     ```properties
     GEMINI_API_KEY=AIzaSyYourKeyHere
     ```

---

## 📱 How to Build & Deploy via ADB Wi-Fi to Phone

Follow these steps to connect your physical Android phone wirelessly, build the app, and install it.

### Step 1: Enable Wireless Debugging on Phone
1. Ensure your **phone and computer are on the same Wi-Fi network**.
2. Open **Settings** > **About Phone** and tap **Build Number** 7 times to enable *Developer Options*.
3. Go to **Settings** > **System** (or Additional Settings) > **Developer Options**:
   - Turn on **USB Debugging**.
   - Turn on **Wireless Debugging** (allow permissions if prompted).

---

### Step 2: Connect ADB over Wi-Fi

#### Option A: Android 11+ Native Wireless Pairing (Recommended)
1. In **Developer Options**, tap directly on **Wireless Debugging**.
2. Tap **"Pair device with pairing code"** .
   - Note the **IP address & Port** (e.g., `192.168.1.50:37123`) and the **6-digit pairing code**.
3. Open your terminal on your PC and run:
   ```bash
   adb pair 192.168.1.50:37123
   # When prompted, enter the 6-digit pairing code
   ```
4. Now connect to the device using the main IP & Port shown on the **Wireless Debugging** main screen (e.g., `192.168.1.50:41255`):
   ```bash
   adb connect 192.168.1.50:41255
   ```
5. Confirm connection:
   ```bash
   adb devices
   # Output should show: 192.168.1.50:41255    device
   ```

#### Option B: Classic ADB over TCP/IP (Initial USB Cable)
1. Plug your phone into your computer via USB once.
2. Enable TCP/IP on port 5555:
   ```bash
   adb tcpip 5555
   ```
3. Disconnect the USB cable.
4. Find your phone's IP in **Settings > About Phone > Status information > IP address**.
5. Connect wirelessly:
   ```bash
   adb connect <PHONE_IP_ADDRESS>:5555
   adb devices
   ```

---

### Step 3: Build & Deploy PicScan

Navigate to the project root directory:
```bash
cd /home/joachim/IdeaProjects/picscan
```

#### Method 1: One-Step Build & Direct Install via Gradle
```bash
./gradlew installDebug
```

#### Method 2: Manual Build & ADB Install
1. **Compile Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   The APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

2. **Install APK to Phone over Wi-Fi:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Launch the App on Your Phone:**
   ```bash
   adb shell am start -n com.picscan.app/.MainActivity
   ```

4. **(Optional) Monitor Real-time Logs:**
   ```bash
   adb logcat -s "PicScan" "CameraX" "GenerativeModel"
   ```

---

## 🚀 Key Features

- 📸 **Live Camera Scanner**: High-performance camera viewfinder powered by **CameraX** with flash toggle, camera flip, and automatic image scaling for lightning fast uploads.
- 🖼️ **Gallery Photo Support**: Pick existing drink pictures from your device to analyze.
- 🍺 **5-Stufiges Bier-Ranking**:
  - 💣 **Hopfenbombe** (Meisterwerk / Craft)
  - 🍻 **Lecker Bierchen** (Klassiker / Premium)
  - 🚶‍♂️ **Wegbier** (Kiosk / Späti-Begleiter)
  - 🥫 **Pennerglück** (Discounter-Dose)
  - 🤢 **Pissbrühe** (Plörre / Notstand)
- 🤖 **Multi-model Gemini Support**: Choose between:
  - `gemini-3.6-flash` (Latest High Speed - Default)
  - `gemini-2.0-flash` (Ultra high-speed multimodal)
  - `gemini-1.5-flash` (Fast, efficient analysis)
  - `gemini-1.5-pro` (Deep sommelier and mixologist evaluation)
- 🍷 **Comprehensive Beverage Insights**:
  - **Classification**: Drink category, brand/producer, country/region of origin, ABV% or Caffeine content.
  - **Sensory Flavor Breakdown**: Sweetness, Bitterness, Acidity level meters, aroma profile, and tasting notes.
  - **Nutrition & Dietary**: Estimated calories, sugars, carbs, and dietary highlights (Vegan, Gluten-free, Organic, Zero Sugar, etc.).
  - **Sommelier Serving Guide**: Ideal serving temperature, glassware recommendations, food pairings, and mixology recipes.
  - **Fun Facts & Trivia**: Cultural history and trivia about the drink.
- 📚 **Scan History & Offline Cache**: Automatically saves your scanned drinks and images locally with instant search and filter capabilities.
- 🎨 **Modern Jetpack Compose & Material 3**: Adaptive dynamic theming, dark mode, smooth animations, and edge-to-edge support.
