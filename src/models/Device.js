const mongoose = require('mongoose');

const deviceSchema = new mongoose.Schema({
    deviceId: {
        type: String,
        required: true,
        unique: true
    },
    name: {
        type: String,
        required: true
    },
    deviceType: {
        type: String,
        enum: ['ESP32'],
        default: 'ESP32'
    },
    status: {
        type: String,
        enum: ['online', 'offline', 'error'],
        default: 'offline'
    },
    firmwareVersion: {
        type: String
    },
    batteryLevel: {
        type: Number,
        min: 0,
        max: 100
    },
    signalStrength: {
        type: Number  // RSSI value
    },
    lastSeen: {
        type: Date,
        default: Date.now
    },
    mqttTopic: {
        type: String,
        required: true,
        unique: true
    },
    settings: {
        audioSampleRate: {
            type: Number,
            default: 16000  // Default for INMP441
        },
        audioFormat: {
            type: String,
            enum: ['wav', 'raw'],
            default: 'wav'
        },
        locationUpdateInterval: {
            type: Number,
            default: 30  // seconds
        }
    },
    isTracking: {
        type: Boolean,
        default: false
    },
    trackingInterval: {
        type: Number,
        default: 10,
        min: 5,
        max: 3600
    }
}, {
    timestamps: true
});

module.exports = mongoose.model('Device', deviceSchema);
