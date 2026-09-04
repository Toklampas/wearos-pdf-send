# PdfSender

PdfSender is an Android application that allows you to seamlessly send PDF documents from your smartphone directly to your Wear OS smartwatch. 

## Features

- **Simple File Selection:** Pick any PDF document from your phone's storage.
- **Launch Watch App:** Open the companion app on your Wear OS watch directly from your phone with a single tap.
- **Progress Tracking:** See real-time transfer progress on both your phone and watch.
- **Wear OS Companion App:** A dedicated Wear OS app to receive and handle the transferred PDF documents.

## How It Works

The application utilizes the **Wearable Data Layer API** to communicate between the smartphone and the watch:
1. **MessageClient:** Used to send a quick `/launch-app` signal to wake up the companion app on the Wear OS device.
2. **ChannelClient:** Establishes a high-bandwidth connection (`/pdf-transfer`) to stream the PDF file from the phone to the watch efficiently.

## Project Structure

The repository is structured as a standard multi-module Android project:

- `app`: The smartphone application module.
- `wear`: The Wear OS companion application module.

## Prerequisites

- Android Studio (latest version recommended)
- An Android smartphone (Android 6.0+)
- A Wear OS smartwatch paired with the smartphone

## Getting Started

1. Clone this repository:
   ```bash
   git clone https://github.com/toklampas/wearos-pdf-send.git
   ```
2. Open the project in Android Studio.
3. Build and install the `app` module on your Android smartphone.
4. Build and install the `wear` module on your Wear OS device (or emulator).
5. Open the app on your phone, select a PDF, and watch it transfer!

## License

This project is open-source. Feel free to contribute or modify it to suit your needs.
