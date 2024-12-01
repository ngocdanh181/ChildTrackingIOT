const rateLimit = require('express-rate-limit');

// Rate limiter cho API chung
exports.apiLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 phút
    max: 100, // Giới hạn mỗi IP: 100 requests mỗi 15 phút
    message: {
        success: false,
        error: 'Too many requests from this IP, please try again after 15 minutes'
    }
});

// Rate limiter cho audio recording
exports.recordingLimiter = rateLimit({
    windowMs: 60 * 1000, // 1 phút
    max: 5, // Giới hạn mỗi IP: 5 recording requests mỗi phút
    message: {
        success: false,
        error: 'Too many recording requests, please try again after 1 minute'
    }
});

// Rate limiter cho authentication
exports.authLimiter = rateLimit({
    windowMs: 60 * 60 * 1000, // 1 giờ
    max: 5, // Giới hạn mỗi IP: 5 failed attempts mỗi giờ
    message: {
        success: false,
        error: 'Too many failed attempts, please try again after an hour'
    }
});

// Rate limiter cho device registration
exports.deviceRegisterLimiter = rateLimit({
    windowMs: 24 * 60 * 60 * 1000, // 24 giờ
    max: 10, // Giới hạn mỗi IP: 10 device registrations mỗi ngày
    message: {
        success: false,
        error: 'Too many device registrations, please try again tomorrow'
    }
}); 