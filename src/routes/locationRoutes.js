const express = require('express');
const router = express.Router();
const { protect } = require('../middlewares/auth');
const {
    getLatestLocation,
    startTracking,
    stopTracking
} = require('../controllers/locationController');

// Áp dụng middleware authentication cho tất cả routes
router.use(protect);

// Routes cho location tracking
router.post('/track/:deviceId/start', startTracking);
router.post('/track/:deviceId/stop', stopTracking);
router.get('/latest/:deviceId', getLatestLocation);

module.exports = router;
