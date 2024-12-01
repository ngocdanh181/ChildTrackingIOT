const express = require('express');
const router = express.Router();
const { protect } = require('../middlewares/auth');
const {
    getLatestLocation,
    getLocationHistory,
    startTracking,
    stopTracking,
    getCurrentLocation,
    updateTrackingInterval
} = require('../controllers/locationController');

// Áp dụng middleware authentication cho tất cả routes
router.use(protect);

// Routes cho location tracking
router.post('/track/:deviceId/start', startTracking);
router.post('/track/:deviceId/stop', stopTracking);
router.post('/track/:deviceId/interval', updateTrackingInterval);

// Routes cho location data
router.get('/current/:deviceId', getCurrentLocation);
router.get('/latest/:deviceId', getLatestLocation);
router.get('/history/:deviceId', getLocationHistory);

module.exports = router;
