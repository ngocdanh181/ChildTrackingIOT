#include <WiFi.h>
#include <PubSubClient.h>
#include <TinyGPS++.h>
#include <driver/i2s.h>
#include <ArduinoJson.h>
#include <Base64.h>
#include <SPIFFS.h>
#include <Preferences.h>
#include <SoftwareSerial.h>

// WiFi credentials
const char* ssid = "P1506";
const char* password = "19011994";

// MQTT Broker settings - Local Mosquitto
const char* mqtt_server = "192.168.24.2";  // Địa chỉ IP của Mosquitto broker
const int mqtt_port = 1883;

// GPRS Settings
const char* apn = "m-wap";  // APN cho Mobifone
const char* gprsUser = "";
const char* gprsPass = "";

// SIM800L Pins
#define SIM800L_RX 26
#define SIM800L_TX 27
#define SIM800L_POWER 4

// Connection state
bool isWiFiConnected = false;
bool isGPRSConnected = false;

// Create SoftwareSerial for SIM800L
SoftwareSerial simSerial(SIM800L_RX, SIM800L_TX);

// Device settings
String deviceId;  // Sẽ được tạo tự động
String baseTopic;

// GPS settings (NEO-8M)
#define GPS_RX 16
#define GPS_TX 17
#define GPS_BAUD 9600

// I2S Microphone settings (INMP441)
#define I2S_WS 25
#define I2S_SD 33
#define I2S_SCK 32
#define I2S_PORT I2S_NUM_0
#define I2S_SAMPLE_RATE 16000
#define I2S_SAMPLE_BITS 16
#define I2S_BUFFER_SIZE 512

// Audio Recording Settings
#define AUDIO_CHUNK_SIZE 4096
#define MAX_AUDIO_CHUNKS 100
#define RECORDING_BUFFER_SIZE 32768
#define MAX_RECORDING_TIME 60000  // 60 seconds

// Objects
WiFiClient espClient;
PubSubClient mqtt(espClient);
TinyGPSPlus gps;
HardwareSerial gpsSerial(1);  // Use UART1 for GPS
Preferences preferences;

// Variables
uint8_t audioBuffer[RECORDING_BUFFER_SIZE];
size_t audioBufferIndex = 0;
bool isRecording = false;
unsigned long recordingStartTime = 0;
int currentChunk = 0;
File audioFile;
unsigned long lastMsg = 0;
unsigned long lastGPS = 0;
const int gpsInterval = 10000;  // GPS update interval (10 seconds)

// Biến cho GPS tracking
bool isTracking = false;
unsigned long trackingInterval = 10000; // Default 10 seconds

void generateDeviceId() {
    preferences.begin("device", false);
    deviceId = preferences.getString("deviceId", "");
    
    if (deviceId == "") {
        // Tạo deviceId từ MAC address
        uint64_t chipid = ESP.getEfuseMac();
        deviceId = "ESP32_" + String((uint32_t)chipid, HEX);
        preferences.putString("deviceId", deviceId);
    }
    
    baseTopic = "device/" + deviceId;
    preferences.end();
    
    Serial.println("Device ID: " + deviceId);
    Serial.println("Base Topic: " + baseTopic);
}

void setup_wifi() {
    delay(10);
    Serial.println("Connecting to WiFi...");
    
    WiFi.begin(ssid, password);
    
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }
    
    Serial.println("\nWiFi connected");
    Serial.println("IP address: " + WiFi.localIP().toString());
}

void setup_gps() {
    gpsSerial.begin(GPS_BAUD, SERIAL_8N1, GPS_RX, GPS_TX);
    Serial.println("GPS initialized");
}

void setup_i2s() {
    i2s_config_t i2s_config = {
        .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_RX),
        .sample_rate = I2S_SAMPLE_RATE,
        .bits_per_sample = (i2s_bits_per_sample_t)I2S_SAMPLE_BITS,
        .channel_format = I2S_CHANNEL_FMT_ONLY_LEFT,
        .communication_format = I2S_COMM_FORMAT_I2S,
        .intr_alloc_flags = ESP_INTR_FLAG_LEVEL1,
        .dma_buf_count = 8,
        .dma_buf_len = I2S_BUFFER_SIZE,
        .use_apll = false,
        .tx_desc_auto_clear = false,
        .fixed_mclk = 0
    };
    
    i2s_pin_config_t pin_config = {
        .bck_io_num = I2S_SCK,
        .ws_io_num = I2S_WS,
        .data_out_num = I2S_PIN_NO_CHANGE,
        .data_in_num = I2S_SD
    };
    
    i2s_driver_install(I2S_PORT, &i2s_config, 0, NULL);
    i2s_set_pin(I2S_PORT, &pin_config);
    Serial.println("I2S initialized");
}

void setup_gprs() {
    Serial.println("Initializing GPRS...");
    
    // Power cycle SIM800L if needed
    pinMode(SIM800L_POWER, OUTPUT);
    digitalWrite(SIM800L_POWER, HIGH);
    delay(1000);
    digitalWrite(SIM800L_POWER, LOW);
    delay(1000);
    
    simSerial.begin(9600);
    
    // Basic AT commands
    simSerial.println("AT");
    delay(1000);
    simSerial.println("AT+CGATT=1");
    delay(1000);
    
    // Configure bearer
    simSerial.println("AT+SAPBR=3,1,\"Contype\",\"GPRS\"");
    delay(1000);
    simSerial.println("AT+SAPBR=3,1,\"APN\",\"" + String(apn) + "\"");
    delay(1000);
    
    // Connect GPRS
    simSerial.println("AT+SAPBR=1,1");
    delay(2000);
    
    // Check if connected
    simSerial.println("AT+SAPBR=2,1");
    delay(2000);
    
    // If successful
    isGPRSConnected = true;
    Serial.println("GPRS Connected");
}

void check_connection() {
    // Check WiFi first
    if (WiFi.status() != WL_CONNECTED) {
        isWiFiConnected = false;
        Serial.println("WiFi disconnected, trying to reconnect...");
        setup_wifi();
    }
    
    // If WiFi fails, try GPRS
    if (!isWiFiConnected && !isGPRSConnected) {
        Serial.println("Trying GPRS connection...");
        setup_gprs();
    }
    
    // Update MQTT client if needed
    if (isWiFiConnected || isGPRSConnected) {
        if (!mqtt.connected()) {
            mqtt_reconnect();
        }
    }
}

void mqtt_reconnect() {
    while (!mqtt.connected()) {
        Serial.println("Attempting MQTT connection...");
        
        if (mqtt.connect(deviceId.c_str())) {
            Serial.println("MQTT connected");
            
            // Subscribe to control topics
            String controlTopic = baseTopic + "/control";
            mqtt.subscribe(controlTopic.c_str());
            
            // Publish online status with connection type
            StaticJsonDocument<200> statusDoc;
            statusDoc["status"] = "online";
            statusDoc["connection"] = isWiFiConnected ? "wifi" : "gprs";
            statusDoc["rssi"] = isWiFiConnected ? WiFi.RSSI() : 0;
            
            String statusJson;
            serializeJson(statusDoc, statusJson);
            
            String statusTopic = baseTopic + "/status";
            mqtt.publish(statusTopic.c_str(), statusJson.c_str());
        } else {
            Serial.print("failed, rc=");
            Serial.print(mqtt.state());
            Serial.println(" retrying in 5 seconds");
            delay(5000);
        }
    }
}

void handle_mqtt_message(char* topic, byte* payload, unsigned int length) {
    // Convert payload to string
    char message[length + 1];
    memcpy(message, payload, length);
    message[length] = '\0';
    
    String topicStr = String(topic);
    Serial.printf("Message received: %s - %s\n", topic, message);
    
    // Parse JSON command
    StaticJsonDocument<200> doc;
    DeserializationError error = deserializeJson(doc, message);
    
    if (error) {
        Serial.println("Failed to parse JSON command");
        return;
    }
    
    // Handle commands
    const char* command = doc["command"];
    
    // Xử lý các lệnh
    if (String(command) == "start_recording") {
        int duration = doc["duration"] | 10;  // Default 10 seconds
        start_recording();
    } 
    else if (String(command) == "stop_recording") {
        stop_recording();
    }
    else if (String(command) == "start_tracking") {
        // Bắt đầu tracking GPS
        int interval = doc["interval"] | 10;  // Default 10 seconds
        start_location_tracking(interval);
    }
    else if (String(command) == "stop_tracking") {
        stop_location_tracking();
    }
    else if (String(command) == "get_location") {
        // Lấy vị trí ngay lập tức
        publish_gps_data();
    }
    else if (String(command) == "set_tracking_interval") {
        // Thay đổi interval cập nhật GPS
        int newInterval = doc["interval"] | 10;
        set_tracking_interval(newInterval);
    }
}

void publish_gps_data() {
    if (gps.location.isValid()) {
        StaticJsonDocument<200> doc;
        doc["latitude"] = gps.location.lat();
        doc["longitude"] = gps.location.lng();
        doc["satellites"] = gps.satellites.value();
        
        String jsonString;
        serializeJson(doc, jsonString);
        
        String locationTopic = baseTopic + "/location";
        mqtt.publish(locationTopic.c_str(), jsonString.c_str());
    }
}

void publish_telemetry() {
    StaticJsonDocument<200> doc;
    doc["battery"] = analogRead(34) * (3.3 / 4095.0) * 2;  // Assuming voltage divider
    doc["rssi"] = WiFi.RSSI();
    doc["free_heap"] = ESP.getFreeHeap();
    
    String jsonString;
    serializeJson(doc, jsonString);
    
    String telemetryTopic = baseTopic + "/telemetry";
    mqtt.publish(telemetryTopic.c_str(), jsonString.c_str());
}

void start_recording() {
    if (!isRecording) {
        Serial.println("Starting audio recording...");
        
        // Khởi tạo SPIFFS nếu chưa
        if (!SPIFFS.begin(true)) {
            Serial.println("SPIFFS initialization failed!");
            return;
        }

        // Xóa file cũ nếu có
        if (SPIFFS.exists("/audio.raw")) {
            SPIFFS.remove("/audio.raw");
        }

        // Tạo file mới
        audioFile = SPIFFS.open("/audio.raw", FILE_WRITE);
        if (!audioFile) {
            Serial.println("Failed to create file!");
            return;
        }

        // Reset các biến
        audioBufferIndex = 0;
        currentChunk = 0;
        recordingStartTime = millis();
        isRecording = true;

        // Cấu hình I2S
        i2s_config_t i2s_config = {
            .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_RX),
            .sample_rate = I2S_SAMPLE_RATE,
            .bits_per_sample = I2S_BITS_PER_SAMPLE_16BIT,
            .channel_format = I2S_CHANNEL_FMT_ONLY_LEFT,
            .communication_format = I2S_COMM_FORMAT_I2S,
            .intr_alloc_flags = ESP_INTR_FLAG_LEVEL1,
            .dma_buf_count = 8,
            .dma_buf_len = 1024,
            .use_apll = false
        };

        i2s_driver_install(I2S_PORT, &i2s_config, 0, NULL);
        i2s_set_pin(I2S_PORT, &i2s_pin_config);
        i2s_start(I2S_PORT);

        // Publish recording start notification
        String startMsg = "{\"status\":\"started\",\"timestamp\":" + String(millis()) + "}";
        mqtt.publish((baseTopic + "/audio/status").c_str(), startMsg.c_str());
    }
}

void handle_recording() {
    if (!isRecording) return;

    size_t bytesRead = 0;
    
    // Đọc dữ liệu từ I2S
    i2s_read(I2S_PORT, audioBuffer + audioBufferIndex, AUDIO_CHUNK_SIZE, &bytesRead, portMAX_DELAY);
    audioBufferIndex += bytesRead;

    // Khi buffer đầy, xử lý và gửi chunk
    if (audioBufferIndex >= AUDIO_CHUNK_SIZE) {
        process_and_send_chunk();
        audioBufferIndex = 0;
    }

    // Kiểm tra thời gian recording
    if (millis() - recordingStartTime >= MAX_RECORDING_TIME) {
        stop_recording();
    }
}

void process_and_send_chunk() {
    // Tạo buffer cho base64
    size_t base64Size = Base64.encodedLength(AUDIO_CHUNK_SIZE);
    char* base64Buffer = (char*)malloc(base64Size + 1);
    
    if (!base64Buffer) {
        Serial.println("Failed to allocate base64 buffer!");
        return;
    }

    // Encode chunk to base64
    Base64.encode(base64Buffer, (char*)audioBuffer, AUDIO_CHUNK_SIZE);

    // Tạo JSON message
    StaticJsonDocument<512> doc;
    doc["deviceId"] = deviceId;
    doc["chunkId"] = currentChunk;
    doc["timestamp"] = millis();
    doc["format"] = "wav";
    doc["sampleRate"] = I2S_SAMPLE_RATE;
    doc["bitDepth"] = 16;
    doc["channels"] = 1;
    
    String metadata;
    serializeJson(doc, metadata);

    // Publish metadata
    mqtt.publish((baseTopic + "/audio/metadata").c_str(), metadata.c_str());

    // Publish audio chunk
    mqtt.publish((baseTopic + "/audio/data").c_str(), base64Buffer);

    // Lưu vào SPIFFS
    if (audioFile) {
        audioFile.write(audioBuffer, AUDIO_CHUNK_SIZE);
    }

    free(base64Buffer);
    currentChunk++;

    // Debug info
    Serial.printf("Sent chunk %d, size: %d bytes\n", currentChunk, AUDIO_CHUNK_SIZE);
}

void stop_recording() {
    if (isRecording) {
        Serial.println("Stopping audio recording...");

        // Dừng I2S
        i2s_stop(I2S_PORT);
        i2s_driver_uninstall(I2S_PORT);

        // Đóng file
        if (audioFile) {
            audioFile.close();
        }

        // Reset flags
        isRecording = false;
        
        // Publish recording stop notification
        String stopMsg = "{\"status\":\"stopped\",\"totalChunks\":" + String(currentChunk) + 
                        ",\"duration\":" + String((millis() - recordingStartTime) / 1000.0) + 
                        ",\"timestamp\":" + String(millis()) + "}";
        mqtt.publish((baseTopic + "/audio/status").c_str(), stopMsg.c_str());

        // Tùy chọn: Upload toàn bộ file
        upload_complete_file();
    }
}

void upload_complete_file() {
    if (!SPIFFS.exists("/audio.raw")) {
        Serial.println("No audio file to upload!");
        return;
    }

    File file = SPIFFS.open("/audio.raw", FILE_READ);
    if (!file) {
        Serial.println("Failed to open audio file!");
        return;
    }

    // Publish file info
    StaticJsonDocument<256> doc;
    doc["deviceId"] = deviceId;
    doc["fileSize"] = file.size();
    doc["timestamp"] = millis();
    doc["format"] = "wav";
    doc["sampleRate"] = I2S_SAMPLE_RATE;
    doc["bitDepth"] = 16;
    doc["channels"] = 1;

    String fileInfo;
    serializeJson(doc, fileInfo);
    mqtt.publish((baseTopic + "/audio/file/info").c_str(), fileInfo.c_str());

    // Upload file in chunks
    uint8_t uploadBuffer[AUDIO_CHUNK_SIZE];
    size_t bytesRead;
    int chunkId = 0;

    while ((bytesRead = file.read(uploadBuffer, AUDIO_CHUNK_SIZE)) > 0) {
        // Encode to base64
        size_t base64Size = Base64.encodedLength(bytesRead);
        char* base64Buffer = (char*)malloc(base64Size + 1);
        
        if (base64Buffer) {
            Base64.encode(base64Buffer, (char*)uploadBuffer, bytesRead);
            
            // Publish chunk
            mqtt.publish((baseTopic + "/audio/file/data").c_str(), base64Buffer);
            
            free(base64Buffer);
            chunkId++;
            
            // Small delay to prevent flooding
            delay(50);
        }
    }

    file.close();

    // Publish upload complete notification
    String completeMsg = "{\"status\":\"upload_complete\",\"totalChunks\":" + String(chunkId) + 
                        ",\"timestamp\":" + String(millis()) + "}";
    mqtt.publish((baseTopic + "/audio/file/status").c_str(), completeMsg.c_str());
}

void start_location_tracking(int interval) {
    isTracking = true;
    trackingInterval = interval * 1000; // Convert to milliseconds
    
    // Publish trạng thái tracking
    StaticJsonDocument<200> doc;
    doc["status"] = "tracking_started";
    doc["interval"] = interval;
    doc["timestamp"] = millis();
    
    String jsonString;
    serializeJson(doc, jsonString);
    mqtt.publish((baseTopic + "/location/status").c_str(), jsonString.c_str());
    
    Serial.println("Location tracking started");
}

void stop_location_tracking() {
    isTracking = false;
    
    // Publish trạng thái dừng tracking
    StaticJsonDocument<200> doc;
    doc["status"] = "tracking_stopped";
    doc["timestamp"] = millis();
    
    String jsonString;
    serializeJson(doc, jsonString);
    mqtt.publish((baseTopic + "/location/status").c_str(), jsonString.c_str());
    
    Serial.println("Location tracking stopped");
}

void set_tracking_interval(int newInterval) {
    trackingInterval = newInterval * 1000;
    
    // Publish cập nhật interval
    StaticJsonDocument<200> doc;
    doc["status"] = "interval_updated";
    doc["new_interval"] = newInterval;
    doc["timestamp"] = millis();
    
    String jsonString;
    serializeJson(doc, jsonString);
    mqtt.publish((baseTopic + "/location/status").c_str(), jsonString.c_str());
    
    Serial.printf("Tracking interval updated to %d seconds\n", newInterval);
}

void setup() {
    Serial.begin(115200);
    
    // Generate unique device ID
    generateDeviceId();
    
    // Initialize components
    setup_wifi();
    setup_gps();
    setup_i2s();
    
    // Setup MQTT
    mqtt.setServer(mqtt_server, mqtt_port);
    mqtt.setCallback(handle_mqtt_message);
}

void loop() {
    // Check and maintain connection
    check_connection();
    
    if (mqtt.connected()) {
        mqtt.loop();

        // Handle GPS
        while (gpsSerial.available() > 0) {
            gps.encode(gpsSerial.read());
        }

        // Handle recording if active
        if (isRecording) {
            handle_recording();
        }

        // Regular updates
        unsigned long now = millis();
        
        // Xử lý GPS tracking
        if (isTracking && (now - lastGPS > trackingInterval)) {
            lastGPS = now;
            publish_gps_data();
        }
        
        // Telemetry updates
        if (now - lastMsg > 30000) {  // Every 30 seconds
            lastMsg = now;
            publish_telemetry();
        }
    }
}