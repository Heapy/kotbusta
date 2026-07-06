import { h } from 'preact';
import { useState, useEffect } from 'preact/hooks';
import { api } from '../utils/api.js';

function statusClass(status) {
  if (status === 'COMPLETED') return 'status-badge success';
  if (status === 'FAILED') return 'status-badge danger';
  if (status === 'PROCESSING') return 'status-badge warning';
  return 'status-badge';
}

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

  const addDevice = async (event) => {
    event.preventDefault();
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
    return h('main', { className: 'page' },
      h('div', { className: 'loading-state' }, 'Loading...')
    );
  }

  return h('main', { className: 'page' },
    h('div', { className: 'page-header' },
      h('h2', { className: 'page-title' }, 'Kindle'),
      h('button', {
        className: 'button primary',
        onClick: () => setShowAddDevice(!showAddDevice)
      }, showAddDevice ? 'Close' : 'Add Device')
    ),

    h('section', { className: 'section' },
      h('div', { className: 'section-header' },
        h('h3', { className: 'section-title' }, 'My Devices')
      ),

      showAddDevice && h('form', {
        className: 'form-panel',
        onSubmit: addDevice
      },
        h('input', {
          className: 'input',
          type: 'email',
          value: newDevice.email,
          onInput: (event) => setNewDevice({ ...newDevice, email: event.target.value }),
          placeholder: 'Kindle email, for example user@kindle.com',
          required: true
        }),
        h('input', {
          className: 'input',
          type: 'text',
          value: newDevice.name,
          onInput: (event) => setNewDevice({ ...newDevice, name: event.target.value }),
          placeholder: 'Device name',
          required: true
        }),
        h('div', { className: 'form-actions' },
          h('button', { className: 'button success', type: 'submit' }, 'Add'),
          h('button', {
            className: 'button',
            type: 'button',
            onClick: () => setShowAddDevice(false)
          }, 'Cancel')
        )
      ),

      h('div', { className: 'list-stack' },
        devices.map(device =>
          h('div', { key: device.id, className: 'list-item' },
            h('div', null,
              h('div', { className: 'item-title' }, device.name),
              h('div', { className: 'item-subtitle' }, device.email)
            ),
            h('button', {
              className: 'button danger compact',
              onClick: () => deleteDevice(device.id)
            }, 'Delete')
          )
        )
      ),

      devices.length === 0 && h('div', {
        className: 'empty-state'
      }, 'No devices yet.')
    ),

    h('section', { className: 'section' },
      h('div', { className: 'section-header' },
        h('h3', { className: 'section-title' }, 'Send History')
      ),
      h('div', { className: 'list-stack' },
        sendHistory.map(item =>
          h('div', { key: item.id, className: 'list-item history-row' },
            h('div', { className: 'history-topline' },
              h('div', null,
                h('div', { className: 'item-title' }, item.bookTitle),
                h('div', { className: 'item-subtitle' },
                  `to ${item.deviceName} - ${item.format}`
                ),
                h('div', { className: 'item-note' },
                  new Date(item.createdAt).toLocaleString()
                )
              ),
              h('span', { className: statusClass(item.status) }, item.status)
            ),
            item.lastError && h('div', {
              className: 'error-text'
            }, `Error: ${item.lastError}`)
          )
        )
      ),

      sendHistory.length === 0 && h('div', {
        className: 'empty-state'
      }, 'No send history yet.')
    )
  );
}
