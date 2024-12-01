const express = require('express');
const router = express.Router();
const { protect } = require('../middlewares/auth');
const { deviceValidation } = require('../middlewares/validator');
const { deviceRegisterLimiter } = require('../middlewares/rateLimiter');
const {
    getAllDevices,
    getDevice,
    createDevice,
    updateDevice,
    deleteDevice,
    restartDevice,
    updateFirmware
} = require('../controllers/deviceController');

// Áp dụng authentication cho tất cả routes
router.use(protect);

// Device management routes
router.route('/')
    .get(getAllDevices)
    .post(
        deviceRegisterLimiter,
        deviceValidation.create,
        createDevice
    );

router.route('/:id')
    .get(deviceValidation.update, getDevice)
    .put(deviceValidation.update, updateDevice)
    .delete(deleteDevice);

// Device control routes
router.post(
    '/:id/restart',
    deviceValidation.update,
    restartDevice
);

router.post(
    '/:id/firmware',
    deviceValidation.update,
    updateFirmware
);

module.exports = router;
