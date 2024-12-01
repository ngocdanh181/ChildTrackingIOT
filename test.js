// test-local-mqtt.js
const mqtt = require('mqtt');

// Kết nối đến Mosquitto local
const client = mqtt.connect('mqtt://localhost:1883');

// Topics cho ứng dụng
const TOPICS = {
    GPS: 'device/gps',
    AUDIO: 'device/audio',
    STATUS: 'device/status'
};

client.on('connect', () => {
    console.log('Connected to Mosquitto Broker!');
    
    // Subscribe vào các topics
    Object.values(TOPICS).forEach(topic => {
        client.subscribe(topic, (err) => {
            if (!err) {
                console.log(`Subscribed to ${topic}`);
                // Gửi message test
                client.publish(topic, `Test message for ${topic}`);
            }
        });
    });
});

client.on('message', (topic, message) => {
    console.log(`Received on ${topic}: ${message.toString()}`);
});

client.on('error', (err) => {
    console.error('MQTT Error:', err);
});