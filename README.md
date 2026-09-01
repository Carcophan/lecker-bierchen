# lecker Bierchen! – AI Getränke- & Bier-Scanner (Android)

**lecker Bierchen!** ist eine moderne Android-App (optimiert für **Android 17 / API 35+**), mit der du deine Kamera einfach auf ein beliebiges Bier oder Getränk (Wein, Bier, Cocktails, Specialty Coffee, Tee, Energy-Drinks, Limonaden, Spirituosen, Kombucha etc.) richten kannst. Mittels **Google Gemini AI Vision** werden detaillierte Getränkeinformationen, Bier-Rankings, Sommelier-Geschmacksnoten, Aromenprofile, Nährwerte und Servierempfehlungen in Sekundenschnelle vollständig auf Deutsch analysiert.

---

## 🍺 5-Stufiges Bier-Ranking ("Hopfenbombe" bis "Pissbrühe")

**lecker Bierchen!** beinhaltet eine humorvolle und intelligente 5-stufige Bier-Bewertung mit individuellen Sound-Effekten, Haptik-Vibrationen und Fullscreen-Animationen:

1. 💣💥 **"Hopfenbombe!"** (Rang 1/5 – Meisterwerk):
   - Für außergewöhnliche Craft-Biere, intensive Double/Triple IPAs, Imperial Stouts, Trappistenbiere und hopfenintensive Spezialitäten.
   - *Effekt*: Energetischer Explosions-Sweep & Akkord, dezente Schwingung und Hopfen/Bomben-Partikel.
2. 🍺✨ **"Lecker Bierchen!"** (Rang 2/5 – Hohe Braukunst):
   - Für hervorragende traditionelle Qualitätsbiere und beliebte Klassiker (z. B. Augustiner, Tegernseer, Weihenstephaner, Rothaus, Paulaner, Guinness, Chiemseer etc.).
   - *Effekt*: Feierliches Fanfaren-Arpeggio, sanfter Amber-Glow und Bierkrug-Sparkles.
3. 🚶‍♂️🍻 **"Wegbier!"** (Rang 3/5 – Kiosk- & Späti-Held):
   - Für solide, süffige Alltags-Lager und Späti-Begleiter (z. B. Astra, Sternburg, Krombacher, Bitburger, Beck's, Jever, Flensburger etc.).
   - *Effekt*: Fröhlich-beschwingte Walking-Melodie und dynamische Cyan-Vibrationen.
4. 🥫🥴 **"Pennerglück!"** (Rang 4/5 – Sparfuchs-Dosenkracher):
   - Für kultiges, günstiges Discounter-Dosenbier (z. B. Oettinger, 5,0 Original, Hansa Pils, Paderborner, Karlskrone, Turmbräu etc.).
   - *Effekt*: Wonky metallisches Dosen-Klimpern und dezente Dosen-Animation.
5. ☣️🤢 **"Pissbrühe!"** (Rang 5/5 – Untrinkbare Plörre):
   - Für wässrige, abgestandene Plörre und gefürchtete Billigst-Plempe (z. B. Perlenbacher, Natty Light, Keystone Light etc.).
   - *Effekt*: Dissonanter Fail-Buzzer, toxisch-grüner Alarm-Glow und sanftes Warnsignal.

---

## 💡 Google AI Pro (Google One AI Premium) vs. API-Schlüssel

### Kurzfassung:
**Endkunden-Abonnements** (wie *Google AI Pro* / *Google One AI Premium* / *Gemini Advanced*) sind für die Endnutzer-Apps gedacht (z. B. Gemini App und `gemini.google.com`). Sie **stellen keine API-Schlüssel** für Drittanbieter-Apps oder Entwickler-SDKs bereit.

### So nutzt du Google Gemini AI für lecker Bierchen! (Kostenlos!):
1. Google stellt Entwickler-APIs über das **[Google AI Studio](https://aistudio.google.com/)** bereit.
2. **Google AI Studio bietet ein kostenloses Kontingent**, das viele Scans pro Minute für alle Gemini-Modelle erlaubt (Gemini 3.6 Flash / Gemini 2.0 Flash / Gemini 1.5 Flash / Gemini 1.5 Pro).
3. **Schritte zum Erstellen deines Schlüssels:**
   - Gehe zu [Google AI Studio API Keys](https://aistudio.google.com/app/apikey).
   - Melde dich mit deinem Google-Konto an.
   - Klicke auf **"Create API Key"** und kopiere den Schlüssel (`AIzaSy...`).
   - Füge ihn in der App unter **Einstellungen** ein oder hinterlege ihn in deiner `local.properties`:
     ```properties
     GEMINI_API_KEY=AIzaSyDeinSchluesselHier
     ```

---

## 📱 Build & Wireless Deployment via ADB Wi-Fi auf das Smartphone

Befolge diese Schritte, um dein physisches Android-Gerät kabellos zu verbinden, die App zu bauen und zu installieren.

### Schritt 1: Kabelloses Debugging am Smartphone aktivieren
1. Stelle sicher, dass **Smartphone und Computer im selben WLAN-Netzwerk** sind.
2. Öffne **Einstellungen** > **Über das Telefon** und tippe 7-mal auf die **Build-Nummer**, um die *Entwickleroptionen* zu aktivieren.
3. Öffne **Einstellungen** > **System** (oder Weitere Einstellungen) > **Entwickleroptionen**:
   - Aktiviere **USB-Debugging**.
   - Aktiviere **Kabelloses Debugging** (Wireless Debugging).

---

### Schritt 2: ADB über WLAN verbinden

#### Option A: Android 11+ Kopplungscode (Empfohlen)
1. In den **Entwickleroptionen** auf **Kabelloses Debugging** tippen.
2. Auf **"Gerät mit Kopplungscode koppeln"** tippen.
   - Notiere die **IP-Adresse & Port** (z. B. `192.168.1.50:37123`) sowie den **6-stelligen Kopplungscode**.
3. Öffne das Terminal am PC und führe aus:
   ```bash
   adb pair 192.168.1.50:37123
   # Bei Aufforderung den 6-stelligen Kopplungscode eingeben
   ```
4. Verbinde dich nun mit der Haupt-IP & Port aus dem Hauptmenü von *Kabelloses Debugging* (z. B. `192.168.1.50:41255`):
   ```bash
   adb connect 192.168.1.50:41255
   ```
5. Verbindung prüfen:
   ```bash
   adb devices
   # Ausgabe sollte zeigen: 192.168.1.50:41255    device
   ```

#### Option B: Klassisches ADB über TCP/IP (Initial per USB-Kabel)
1. Smartphone einmalig per USB an den PC anschließen.
2. TCP/IP-Modus aktivieren:
   ```bash
   adb tcpip 5555
   ```
3. USB-Kabel trennen.
4. IP-Adresse des Telefons in den Einstellungen ermitteln.
5. Kabellos verbinden:
   ```bash
   adb connect <TELEFON_IP_ADRESSE>:5555
   adb devices
   ```

---

### Schritt 3: lecker Bierchen! bauen & installieren

Projekt-Hauptverzeichnis öffnen:
```bash
cd /home/joachim/IdeaProjects/picscan
```

#### Methode 1: Direkter Build & Install via Gradle
```bash
./gradlew installDebug
```

#### Methode 2: Manueller Build & ADB-Install
1. **Debug-APK kompilieren:**
   ```bash
   ./gradlew assembleDebug
   ```
   Die APK wird generiert unter:
   `app/build/outputs/apk/debug/app-debug.apk`

2. **APK über WLAN installieren:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. **App auf dem Smartphone starten:**
   ```bash
   adb shell am start -n com.picscan.app/.MainActivity
   ```

4. **(Optional) Live-Logs verfolgen:**
   ```bash
   adb logcat -s "PicScan" "CameraX" "GenerativeModel"
   ```

---

## 🚀 Hauptfunktionen

- 📸 **Live-Kamera-Scanner**: Schneller Kamerasucher mit **CameraX**, Blitz-Umschaltung, Kamera-Wechsel und automatischer Bildkomprimierung für minimale Upload-Latenz.
- 🖼️ **Galerie-Unterstützung**: Wähle bestehende Getränkefotos aus deiner Galerie aus.
- 🍺 **5-Stufiges Bier-Ranking**:
  - 💣 **Hopfenbombe** (Meisterwerk / Craft)
  - 🍺 **Lecker Bierchen** (Klassiker / Premium)
  - 🚶‍♂️ **Wegbier** (Kiosk / Späti-Begleiter)
  - 🥫 **Pennerglück** (Discounter-Dose)
  - ☣️ **Pissbrühe** (Plörre / Notstand)
- 🤖 **Gemini Modell-Auswahl**:
  - `gemini-3.6-flash` (Neuestes High-Speed-Modell – Standard)
  - `gemini-2.0-flash` (Ultra-schnell & multimodal)
  - `gemini-1.5-flash` (Schnell & ressourcenschonend)
  - `gemini-1.5-pro` (Tiefgehende Sommelier- & Mixologie-Analyse)
- 🍷 **Vollständige Getränke-Analyse (100% Deutsch)**:
  - **Klassifikation**: Kategorie, Marke/Brauerei, Herkunftsland/-region, Alkoholgehalt oder Koffeingehalt.
  - **Sensorisches Geschmacksprofil**: Skalen für Süße, Bitterkeit und Säure, Aromenprofil und Geschmacksnoten.
  - **Nährwerte & Besonderheiten**: Geschätzte Kalorien, Zucker, Kohlenhydrate und Diät-Hinweise (Vegan, Glutenfrei, Bio, Zuckerfrei etc.).
  - **Sommelier-Servierempfehlungen**: Ideale Serviertemperatur, passendes Glas, Speisenbegleiter und Cocktail-/Mixology-Tipps.
  - **Wissenswertes & Trivia**: Spannende Hintergrundfakten zum Getränk.
- 📚 **Scan-Verlauf & Lokaler Cache**: Automatisches lokales Speichern gescannter Getränke mit Such- und Löschfunktion.
- 🎨 **Modernes Jetpack Compose & Material 3**: Dynamische Farbpaletten, Dark Mode, dezente flüssige Animationen und Edge-to-Edge-Design.
