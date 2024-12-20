const express = require('express');
const router = express.Router();
const { protect } = require('../middlewares/auth');
const {
    startListening,
    stopListening,
    getListeningStatus
} = require('../controllers/audioController');

// Áp dụng authentication cho tất cả routes
router.use(protect);

// Routes cho audio streaming
router.post('/listen/:deviceId/start', startListening);
router.post('/listen/:deviceId/stop', stopListening);
router.get('/status/:deviceId', getListeningStatus);

module.exports = router;
