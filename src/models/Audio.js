const mongoose = require('mongoose');

const audioSchema = new mongoose.Schema({
    deviceId: {
        type: String,
        required: true,
        ref: 'Device'
    },
    isListening: {
        type: Boolean,
        default: false
    },
    sampleRate: {
        type: Number,
        default: 16000
    },
    channelCount: {
        type: Number,
        default: 1
    },
    lastUpdate: {
        type: Date,
        default: Date.now
    }
}, {
    timestamps: true
});

module.exports = mongoose.model('Audio', audioSchema);
