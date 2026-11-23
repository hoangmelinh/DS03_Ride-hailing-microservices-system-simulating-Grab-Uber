// API Gateway client & Distributed Flow Simulation Engine
const GATEWAY_BASE_URL = 'http://localhost:8080';

// Fixed Seed UUIDs for demo
export const DEMO_PASSENGER_ID = '11111111-1111-1111-1111-111111111111';
export const DEMO_DRIVER_ID = '5c072be5-37cf-458d-a31e-212934a8c28e';
export const DEMO_USER_DRIVER_ID = '780b1f53-93ee-48fe-a57f-ab9ddfabb8e4';

let cachedToken = null;

export async function checkGatewayHealth() {
  try {
    const res = await fetch(`${GATEWAY_BASE_URL}/actuator/health`, { method: 'GET' });
    return res.ok;
  } catch (err) {
    return false;
  }
}

export async function loginUser(email = 'passenger@goride.com', password = 'Password123!') {
  try {
    const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    });
    if (res.ok) {
      const data = await res.json();
      if (data?.data?.accessToken) {
        cachedToken = data.data.accessToken;
      }
      return data;
    }
  } catch (err) {
    console.warn('Backend login unavailable, fallback to mock token', err);
  }

  return {
    success: true,
    data: {
      accessToken: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.mock",
      userId: DEMO_PASSENGER_ID,
      role: "PASSENGER"
    }
  };
}

export async function getAuthToken() {
  if (cachedToken) return cachedToken;
  const loginRes = await loginUser();
  return loginRes?.data?.accessToken || null;
}

export async function registerUser(phone, email, password, fullName, role = 'PASSENGER') {
  try {
    const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone, email, password, fullName, role })
    });
    return await res.json();
  } catch (err) {
    return { success: false, error: err.message };
  }
}

export async function estimateFare(pickupLat, pickupLng, dropoffLat, dropoffLng) {
  try {
    const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/trips/estimate-fare`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        pickupLatitude: pickupLat,
        pickupLongitude: pickupLng,
        destinationLatitude: dropoffLat,
        destinationLongitude: dropoffLng
      })
    });
    if (res.ok) {
      return await res.json();
    }
  } catch (err) {
    console.warn('Real estimate-fare failed, using Haversine fallback', err);
  }

  const dist = calculateHaversine(pickupLat, pickupLng, dropoffLat, dropoffLng);
  const fare = Math.round(15000 + dist * 10000);
  return {
    success: true,
    data: {
      estimatedDistanceKm: Number(dist.toFixed(2)),
      estimatedDurationMinutes: Math.round(dist * 2.5),
      estimatedFare: fare,
      currency: "VND",
      surgeMultiplier: 1.0
    }
  };
}

export async function createTrip(payload, token, correlationId) {
  const authToken = token || await getAuthToken();
  const reqBody = {
    passengerId: payload.passengerId || DEMO_PASSENGER_ID,
    pickupLatitude: payload.pickupLatitude,
    pickupLongitude: payload.pickupLongitude,
    pickupAddress: payload.pickupAddress,
    destinationLatitude: payload.destinationLatitude || payload.dropoffLatitude,
    destinationLongitude: payload.destinationLongitude || payload.dropoffLongitude,
    destinationAddress: payload.destinationAddress || payload.dropoffAddress,
    estimatedFare: payload.estimatedFare || 45000,
    currency: "VND"
  };

  try {
    const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/trips`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(authToken ? { 'Authorization': `Bearer ${authToken}` } : {}),
        ...(correlationId ? { 'X-Correlation-Id': correlationId } : {})
      },
      body: JSON.stringify(reqBody)
    });
    if (res.ok) {
      return await res.json();
    }
  } catch (err) {
    console.warn('Real trip creation failed, using simulation mode', err);
  }

  return {
    success: true,
    data: {
      id: "tr_" + Math.random().toString(36).substring(2, 9),
      status: "REQUESTED",
      passengerId: reqBody.passengerId,
      pickupAddress: reqBody.pickupAddress,
      destinationAddress: reqBody.destinationAddress,
      estimatedFare: reqBody.estimatedFare,
      currency: "VND"
    }
  };
}

export async function getTripById(tripId, token) {
  const authToken = token || await getAuthToken();
  try {
    const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/trips/${tripId}`, {
      method: 'GET',
      headers: {
        ...(authToken ? { 'Authorization': `Bearer ${authToken}` } : {})
      }
    });
    if (res.ok) {
      return await res.json();
    }
  } catch (err) {
    console.warn('Failed to fetch trip by id', err);
  }
  return null;
}

export async function getMatchingSaga(tripId, token) {
  const authToken = token || await getAuthToken();
  try {
    const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/matching/sagas/trip/${tripId}`, {
      method: 'GET',
      headers: {
        ...(authToken ? { 'Authorization': `Bearer ${authToken}` } : {})
      }
    });
    if (res.ok) {
      return await res.json();
    }
  } catch (err) {
    console.warn('Failed to fetch matching saga', err);
  }
  return null;
}

export async function assignDriver(tripId, driverId, token) {
  const authToken = token || await getAuthToken();
  try {
    const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/trips/${tripId}/assign`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        ...(authToken ? { 'Authorization': `Bearer ${authToken}` } : {})
      },
      body: JSON.stringify({ driverId })
    });
    if (res.ok) {
      return await res.json();
    }
  } catch (err) {
    console.warn('Assign driver fallback', err);
  }
  return { success: true, data: { id: tripId, driverId, status: "DRIVER_ASSIGNED" } };
}

export async function updateTripStatus(tripId, action, token) {
  const authToken = token || await getAuthToken();
  try {
    const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/trips/${tripId}/${action}`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        ...(authToken ? { 'Authorization': `Bearer ${authToken}` } : {})
      }
    });
    if (res.ok) {
      return await res.json();
    }
  } catch (err) {
    console.warn(`Trip status action ${action} fallback`, err);
  }

  const statusMap = {
    accept: "ACCEPTED",
    start: "IN_PROGRESS",
    complete: "COMPLETED",
    cancel: "CANCELLED"
  };
  return { success: true, data: { id: tripId, status: statusMap[action] || "UPDATED" } };
}

export async function updateDriverStatus(driverId, status, token) {
  const authToken = token || await getAuthToken();
  try {
    const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/drivers/${driverId}/status`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        ...(authToken ? { 'Authorization': `Bearer ${authToken}` } : {})
      },
      body: JSON.stringify({ status })
    });
    if (res.ok) {
      return await res.json();
    }
  } catch (err) {
    console.warn('Driver status update fallback', err);
  }
  return { success: true, data: { id: driverId, status } };
}

export async function updateDriverLocation(driverId, latitude, longitude, token) {
  const authToken = token || await getAuthToken();
  try {
    const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/drivers/${driverId}/location`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        ...(authToken ? { 'Authorization': `Bearer ${authToken}` } : {})
      },
      body: JSON.stringify({ latitude, longitude })
    });
    if (res.ok) {
      return await res.json();
    }
  } catch (err) {
    console.warn('Driver location update fallback', err);
  }
  return { success: true };
}

// ==============================================================================
// REAL LIVE BACKEND SCENARIO RUNNERS
// ==============================================================================

/**
 * R02 - Triggers REAL Saga Compensation on Backend:
 * Calls matching-service /trigger with a non-existent tripId.
 * Saga reserves driver -> fails assigning to trip -> triggers compensation RELEASE_DRIVER!
 */
export async function triggerLiveSagaCompensation() {
  const token = await getAuthToken();
  const fakeTripId = '00000000-0000-0000-0000-' + Math.random().toString(16).substring(2, 14).padEnd(12, '0');
  
  const triggerRes = await fetch(`${GATEWAY_BASE_URL}/api/v1/matching/trigger`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
      'X-Correlation-Id': 'corr_saga_live_' + Math.random().toString(36).substring(2, 7)
    },
    body: JSON.stringify({
      tripId: fakeTripId,
      passengerId: DEMO_PASSENGER_ID,
      pickupLatitude: 21.0051,
      pickupLongitude: 105.8432
    })
  });

  const triggerJson = await triggerRes.json();

  // Poll matching_sagas record directly from database via Gateway
  await new Promise(r => setTimeout(r, 600));
  const sagaRes = await getMatchingSaga(fakeTripId, token);
  return {
    fakeTripId,
    triggerResponse: triggerJson,
    sagaRecord: sagaRes?.data || triggerJson?.data
  };
}

/**
 * S01 - Triggers REAL Security Invalid Token test on Gateway:
 */
export async function triggerLiveSecurityInvalidToken() {
  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/trips`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer FAKE_TAMPERED_JWT_SIGNATURE_XYZ_123'
    },
    body: JSON.stringify({ test: true })
  });
  const data = await res.json();
  return { httpStatus: res.status, data };
}

/**
 * S03 - Triggers REAL Data Validation failure (400 Bad Request):
 */
export async function triggerLiveSecurityValidation() {
  const token = await getAuthToken();
  const res = await fetch(`${GATEWAY_BASE_URL}/api/v1/trips`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({
      passengerId: DEMO_PASSENGER_ID,
      pickupLatitude: null, // Vi phạm validation
      pickupAddress: "Hà Nội"
    })
  });
  const data = await res.json();
  return { httpStatus: res.status, data };
}

/**
 * C01 - Triggers REAL Concurrency test on Backend:
 * Sends 2 parallel trip creations to Gateway targeting the single available driver.
 */
export async function triggerLiveConcurrency() {
  const token = await getAuthToken();
  const corr = 'race_' + Math.random().toString(36).substring(2, 8);
  const [res1, res2] = await Promise.all([
    createTrip({ passengerId: DEMO_PASSENGER_ID, pickupLatitude: 21.0051, pickupLongitude: 105.8432, pickupAddress: "ĐH Bách Khoa A" }, token, corr + '_A'),
    createTrip({ passengerId: DEMO_PASSENGER_ID, pickupLatitude: 21.0052, pickupLongitude: 105.8433, pickupAddress: "ĐH Bách Khoa B" }, token, corr + '_B')
  ]);
  return { res1, res2, correlationId: corr };
}

export async function bootstrapDemoData() {
  try {
    await loginUser();
  } catch (e) {}
}

function calculateHaversine(lat1, lon1, lat2, lon2) {
  const R = 6371; // Earth's radius in km
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}
