// src/routes/authRoutes.js
const express = require('express');
const router = express.Router();
const { protect } = require('../middlewares/auth');
const {
    register,
    login,
    getMe
} = require('../controllers/authController');

router.post('/register', register);
router.post('/login', login);
router.get('/me', protect, getMe);

module.exports = router;