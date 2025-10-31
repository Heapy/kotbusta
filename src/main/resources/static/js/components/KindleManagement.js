import { useState, useEffect } from 'preact/hooks';
import { html } from '../utils/html.js';
import { api } from '../utils/api.js';

export function KindleManagement() {
  const [devices, setDevices] = useState([]);
  const [sendHistory, setSendHistory] = useState([]);
  const [showAddDevice, setShowAddDevice] = useState(false);
  const [newDevice, setNewDevice] = useState({ email: '', name: '' });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const [devicesRes, historyRes] = await Promise.all([
        api.get('/api/kindle/devices'),
        api.get('/api/kindle/sends?limit=20')
      ]);
      setDevices(devicesRes.data || []);
      setSendHistory(historyRes.data.items || []);
    } catch (err) {
      console.error('Failed to load Kindle data:', err);
    } finally {
      setLoading(false);
    }
  };

  const addDevice = async (e) => {
    e.preventDefault();
    try {
      await api.post('/api/kindle/devices', newDevice);
      setNewDevice({ email: '', name: '' });
      setShowAddDevice(false);
      await loadData();
    } catch (err) {
      alert('Failed to add device: ' + err.message);
    }
  };

  const deleteDevice = async (deviceId) => {
    if (!confirm('Delete this device?')) return;
    try {
      await api.delete(`/api/kindle/devices/${deviceId}`);
      await loadData();
    } catch (err) {
      alert('Failed to delete device: ' + err.message);
    }
  };

  if (loading) {
    return html`<div style=${{ padding: '2rem', textAlign: 'center' }}>Loading...</div>`;
  }

  return html`
    <div style=${{ padding: '2rem' }}>
      <h2>Kindle Management</h2>

      <div style=${{ marginBottom: '2rem' }}>
        <div style=${{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h3>My Devices</h3>
          <button onClick=${() => setShowAddDevice(!showAddDevice)} style=${{
            padding: '0.5rem 1rem',
            background: '#27ae60',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            cursor: 'pointer'
          }}>
            + Add Device
          </button>
        </div>

        ${showAddDevice && html`
          <form onSubmit=${addDevice} style=${{
            background: '#ecf0f1',
            padding: '1rem',
            borderRadius: '8px',
            marginBottom: '1rem'
          }}>
            <input
              type="email"
              value=${newDevice.email}
              onInput=${(e) => setNewDevice({ ...newDevice, email: e.target.value })}
              placeholder="Kindle Email (e.g., user@kindle.com)"
              required
              style=${{
                width: '100%',
                padding: '0.75rem',
                border: '1px solid #bdc3c7',
                borderRadius: '4px',
                marginBottom: '0.5rem',
                boxSizing: 'border-box'
              }}
            />
            <input
              type="text"
              value=${newDevice.name}
              onInput=${(e) => setNewDevice({ ...newDevice, name: e.target.value })}
              placeholder="Device Name (e.g., My Kindle)"
              required
              style=${{
                width: '100%',
                padding: '0.75rem',
                border: '1px solid #bdc3c7',
                borderRadius: '4px',
                marginBottom: '0.5rem',
                boxSizing: 'border-box'
              }}
            />
            <div style=${{ display: 'flex', gap: '0.5rem' }}>
              <button type="submit" style=${{
                padding: '0.5rem 1rem',
                background: '#27ae60',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }}>
                Add
              </button>
              <button type="button" onClick=${() => setShowAddDevice(false)} style=${{
                padding: '0.5rem 1rem',
                background: '#95a5a6',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }}>
                Cancel
              </button>
            </div>
          </form>
        `}

        <div style=${{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          ${devices.map(device => html`
            <div key=${device.id} style=${{
              padding: '1rem',
              border: '1px solid #e1e8ed',
              borderRadius: '8px',
              background: 'white',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}>
              <div>
                <div style=${{ fontWeight: 'bold' }}>${device.name}</div>
                <div style=${{ color: '#7f8c8d', fontSize: '0.875rem' }}>${device.email}</div>
              </div>
              <button onClick=${() => deleteDevice(device.id)} style=${{
                padding: '0.5rem 1rem',
                background: '#e74c3c',
                color: 'white',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }}>
                Delete
              </button>
            </div>
          `)}
        </div>

        ${devices.length === 0 && html`
          <div style=${{ textAlign: 'center', padding: '2rem', color: '#95a5a6' }}>
            No devices yet. Add your first Kindle device to get started!
          </div>
        `}
      </div>

      <div>
        <h3>Send History</h3>
        <div style=${{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          ${sendHistory.map(item => html`
            <div key=${item.id} style=${{
              padding: '0.75rem',
              border: '1px solid #e1e8ed',
              borderRadius: '4px',
              background: 'white'
            }}>
              <div style=${{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
                <div>
                  <div style=${{ fontWeight: 'bold' }}>${item.bookTitle}</div>
                  <div style=${{ color: '#7f8c8d', fontSize: '0.875rem' }}>
                    to ${item.deviceName} · ${item.format}
                  </div>
                  <div style=${{ color: '#95a5a6', fontSize: '0.75rem' }}>
                    ${new Date(item.createdAt).toLocaleString()}
                  </div>
                </div>
                <span style=${{
                  padding: '0.25rem 0.75rem',
                  borderRadius: '4px',
                  fontSize: '0.75rem',
                  background: item.status === 'COMPLETED' ? '#d5f4e6' :
                             item.status === 'FAILED' ? '#fadbd8' :
                             item.status === 'PROCESSING' ? '#fff3cd' : '#e8f4f8',
                  color: item.status === 'COMPLETED' ? '#27ae60' :
                         item.status === 'FAILED' ? '#e74c3c' :
                         item.status === 'PROCESSING' ? '#f39c12' : '#3498db'
                }}>
                  ${item.status}
                </span>
              </div>
              ${item.lastError && html`
                <div style=${{ color: '#e74c3c', fontSize: '0.75rem', marginTop: '0.5rem' }}>
                  Error: ${item.lastError}
                </div>
              `}
            </div>
          `)}
        </div>

        ${sendHistory.length === 0 && html`
          <div style=${{ textAlign: 'center', padding: '2rem', color: '#95a5a6' }}>
            No send history yet
          </div>
        `}
      </div>
    </div>
  `;
}
