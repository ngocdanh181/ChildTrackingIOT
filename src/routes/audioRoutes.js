const express = require('express');
const router = express.Router();
const { protect } = require('../middlewares/auth');
const { audioValidation } = require('../middlewares/validator');
const { recordingLimiter } = require('../middlewares/rateLimiter');
const {
    startRecording,
    stopRecording,
    getAudioRecords,
    deleteAudioRecord,
    getRecordingStatus
} = require('../controllers/audioController');

// Áp dụng authentication cho tất cả routes
router.use(protect);

// Routes cho recording
router.post(
    '/record/start',
    recordingLimiter,
    audioValidation.startRecording,
    startRecording
);

router.post(
    '/record/:deviceId/stop',
    audioValidation.getRecords,
    stopRecording
);

// Routes cho quản lý audio records
router.get(
    '/device/:deviceId',
    audioValidation.getRecords,
    getAudioRecords
);

// Lấy trạng thái recording
router.get(
    '/status/:deviceId',
    getRecordingStatus
);

// Xóa audio record
router.delete(
    '/:id',
    deleteAudioRecord
);

module.exports = router;
