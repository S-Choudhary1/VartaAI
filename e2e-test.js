import axios from 'axios';
import FormData from 'form-data';
import fs from 'fs';

const API_URL = 'http://localhost:8080/api/v1';

// Helper to delay
const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

async function runTest() {
  console.log('Starting E2E Test...');
  
  // 1. Register Admin
  console.log('\n1. Registering Admin User...');
  try {
    await axios.post(`${API_URL}/auth/register`, {
      username: 'admin',
      password: 'password'
    });
    console.log('✅ Admin registered');
  } catch (e) {
    if (e.response?.data?.message?.includes('exists') || e.response?.status === 500) {
      console.log('⚠️  Admin user likely already exists, proceeding...');
    } else {
      console.error('❌ Registration failed:', e.message);
      process.exit(1);
    }
  }

  // 2. Login
  console.log('\n2. Logging in...');
  let token;
  try {
    const res = await axios.post(`${API_URL}/auth/login`, {
      username: 'admin',
      password: 'password'
    });
    token = res.data.accessToken;
    console.log('✅ Login successful. Token obtained:', token ? token.substring(0, 10) + '...' : 'null');
  } catch (e) {
    console.error('❌ Login failed:', e.message, e.response?.data);
    process.exit(1);
  }

  const authConfig = {
    headers: { Authorization: `Bearer ${token}` }
  };

  // 2.5 Check Auth Status
  console.log('\n2.5 Checking /me...');
  try {
    const meRes = await axios.get(`${API_URL}/auth/me`, authConfig);
    console.log('✅ Authenticated as:', meRes.data);
  } catch (e) {
    console.error('❌ /me check failed:', e.message, e.response?.data);
  }

  // 3. Create Contact
  console.log('\n3. Creating Contact...');
  let contactId;
  try {
    const res = await axios.post(`${API_URL}/contacts`, {
      name: 'Test User',
      phone: '+15550001111',
      email: 'test@example.com'
    }, authConfig);
    contactId = res.data.id;
    console.log('✅ Contact created:', res.data.name);
  } catch (e) {
    console.error('❌ Create Contact failed:', e.message, e.response?.data);
  }

  // 4. List Contacts
  console.log('\n4. Listing Contacts...');
  try {
    const res = await axios.get(`${API_URL}/contacts`, authConfig);
    console.log(`✅ Found ${res.data.length} contacts`);
  } catch (e) {
    console.error('❌ List Contacts failed:', e.message);
  }

  // 5. Create Template
  console.log('\n5. Creating Template...');
  let templateId;
  try {
    const res = await axios.post(`${API_URL}/templates`, {
      name: `test_template_${Date.now()}`,
      content: { body: 'Hello {{1}}, this is a test message.' },
      type: 'TEXT'
    }, authConfig);
    templateId = res.data.id;
    console.log('✅ Template created:', res.data.name);
  } catch (e) {
    console.error('❌ Create Template failed:', e.message, e.response?.data);
  }

  // 6. List Templates
  console.log('\n6. Listing Templates...');
  try {
    const res = await axios.get(`${API_URL}/templates`, authConfig);
    console.log(`✅ Found ${res.data.length} templates`);
  } catch (e) {
    console.error('❌ List Templates failed:', e.message);
  }

  // 7. Create Campaign (Upload CSV)
  console.log('\n7. Creating Campaign (CSV Upload)...');
  try {
    // Create a dummy CSV
    fs.writeFileSync('test.csv', 'name,phone\nTest User,+15550001111');
    
    const form = new FormData();
    form.append('file', fs.createReadStream('test.csv'));
    form.append('name', 'E2E Test Campaign');
    form.append('templateId', templateId);
    // form.append('uploadedBy', ... ) // Backend requires UUID, but I put placeholder in UI. 
    // Let's see if backend requires it strictly. The controller takes it as RequestParam.
    // I will use a random UUID as placeholder since I don't have the user ID easily accessible 
    // (it's in token but I'd need to decode or fetch me).
    // Actually, login response has user info!
    // Let's fetch user info from login response if I saved it... oh wait, I didn't save user ID in variable.
    // Let's just use a random one for now, backend might throw if FK constraint.
    // Actually, checking AuthService login response: it returns UserInfo with ID.
    // I'll fetch it again properly.
    
    // Re-login to get ID
    const loginRes = await axios.post(`${API_URL}/auth/login`, { username: 'admin', password: 'password' });
    const userId = loginRes.data.user.id;
    
    form.append('uploadedBy', userId);

    const res = await axios.post(`${API_URL}/campaigns/upload-csv`, form, {
      headers: {
        ...authConfig.headers,
        ...form.getHeaders()
      }
    });
    console.log('✅ Campaign created:', res.data.name);
  } catch (e) {
    console.error('❌ Create Campaign failed:', e.message, e.response?.data);
  }

  // 8. List Campaigns
  console.log('\n8. Listing Campaigns...');
  try {
    const res = await axios.get(`${API_URL}/campaigns`, authConfig);
    console.log(`✅ Found ${res.data.length} campaigns`);
  } catch (e) {
    console.error('❌ List Campaigns failed:', e.message);
  }

  // 9. Quick Send
  console.log('\n9. Quick Send Message...');
  try {
    await axios.post(`${API_URL}/messages/send`, {
      to: '+15550001111',
      templateId: templateId,
      variables: { "1": "Friend" }
    }, authConfig);
    console.log('✅ Message sent successfully');
  } catch (e) {
    console.error('❌ Quick Send failed:', e.message, e.response?.data);
  }

  console.log('\n🎉 E2E Test Complete!');
}

runTest();

