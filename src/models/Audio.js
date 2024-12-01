const mongoose = require('mongoose');

const audioSchema = new mongoose.Schema({
    deviceId: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'Device',
        required: true
    },
    fileName: {
        type: String,
        required: true
    },
    fileUrl: {
        type: String,
        required: true
    },
    duration: {
        type: Number,  // Duration in seconds
        required: true
    },
    fileSize: {
        type: Number,  // Size in bytes
        required: true
    },
    format: {
        type: String,
        enum: ['wav', 'raw'],
        default: 'wav'
    },
    sampleRate: {
        type: Number,
        default: 16000  // INMP441 typical sample rate
    },
    bitDepth: {
        type: Number,
        default: 16    // INMP441 bit depth
    },
    channels: {
        type: Number,
        default: 1     // Mono
    },
    status: {
        type: String,
        enum: ['uploading', 'processing', 'completed', 'error'],
        default: 'uploading'
    },
    metadata: {
        location: {
            type: {
                type: String,
                enum: ['Point'],
                default: 'Point'
            },
            coordinates: {
                type: [Number],  // [longitude, latitude]
                required: true
            }
        },
        batteryLevel: {
            type: Number,
            min: 0,
            max: 100
        },
        signalStrength: Number,  // RSSI value
        temperature: Number,     // Device temperature during recording
        errorMessage: String     // In case of error status
    }
}, {
    timestamps: true
});

// Add geospatial index for location-based queries
audioSchema.index({ 'metadata.location': '2dsphere' });

module.exports = mongoose.model('Audio', audioSchema);
