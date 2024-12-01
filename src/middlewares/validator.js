const { body, param, query, validationResult } = require('express-validator');

// Validate kết quả
const validate = (req, res, next) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
        return res.status(400).json({
            success: false,
            errors: errors.array()
        });
    }
    next();
};

// Validation rules cho Device
exports.deviceValidation = {
    create: [
        body('deviceId').isString().notEmpty().trim()
            .withMessage('Device ID is required'),
        body('name').isString().notEmpty().trim()
            .withMessage('Device name is required'),
        validate
    ],
    update: [
        param('id').isString().notEmpty()
            .withMessage('Device ID is required'),
        body('name').optional().isString().trim(),
        body('settings').optional().isObject(),
        body('settings.audioSampleRate').optional().isInt({ min: 8000, max: 48000 }),
        body('settings.audioFormat').optional().isIn(['wav', 'raw']),
        validate
    ]
};

// Validation rules cho Audio
exports.audioValidation = {
    startRecording: [
        body('deviceId').isString().notEmpty()
            .withMessage('Device ID is required'),
        body('duration').isInt({ min: 1, max: 300 })
            .withMessage('Duration must be between 1 and 300 seconds'),
        validate
    ],
    getRecords: [
        param('deviceId').isString().notEmpty()
            .withMessage('Device ID is required'),
        query('startDate').optional().isISO8601(),
        query('endDate').optional().isISO8601(),
        validate
    ]
};

// Validation rules cho Location
exports.locationValidation = {
    getHistory: [
        param('deviceId').isString().notEmpty()
            .withMessage('Device ID is required'),
        query('startDate').optional().isISO8601(),
        query('endDate').optional().isISO8601(),
        query('limit').optional().isInt({ min: 1, max: 1000 }),
        validate
    ],
    toggleTracking: [
        param('deviceId').isString().notEmpty()
            .withMessage('Device ID is required'),
        body('enabled').isBoolean()
            .withMessage('Enabled status must be boolean'),
        body('interval').optional().isInt({ min: 1, max: 3600 })
            .withMessage('Interval must be between 1 and 3600 seconds'),
        validate
    ]
}; 