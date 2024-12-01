const mongoose = require('mongoose');

const locationSchema = new mongoose.Schema({
    deviceId: {
        type: String,
        ref: 'Device',
        required: [true, 'Device ID is required']
    },
    latitude: {
        type: Number,
        required: [true, 'Latitude is required'],
        min: -90,
        max: 90
    },
    longitude: {
        type: Number,
        required: [true, 'Longitude is required'],
        min: -180,
        max: 180
    },
    timestamp: {
        type: Date,
        default: Date.now,
        index: true  // Add index for better query performance
    },
    metadata: {
        satellites: {
            type: Number,
            min: 0,
            max: 50
        },
        signalStrength: {
            type: Number,
            min: -120,  // Typical minimum RSSI value
            max: 0      // Typical maximum RSSI value
        }
    }
}, {
    timestamps: true
});

// Compound index for deviceId and timestamp for faster queries
locationSchema.index({ deviceId: 1, timestamp: -1 });

// Add validation for coordinates
locationSchema.pre('save', function(next) {
    if (isNaN(this.latitude) || isNaN(this.longitude)) {
        next(new Error('Invalid coordinates'));
    }
    next();
});

module.exports = mongoose.model('Location', locationSchema);
