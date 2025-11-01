import { render, h } from 'preact';
import { useState, useEffect } from 'preact/hooks';
import { api } from './utils/api.js';

// Components
import { Header } from './components/Header.js';
import { BookDetail } from './components/BookDetail.js';
import { BooksList } from './components/BooksList.js';
import { Activity } from './components/Activity.js';
import { KindleManagement } from './components/KindleManagement.js';
import { AdminPanel } from './components/AdminPanel.js';
import { LoginPage } from './components/LoginPage.js';
import { ErrorPage } from './components/ErrorPage.js';

// Parse URL hash to get current route
function parseRoute() {
  const hash = window.location.hash.slice(1) || '/books';

  // Check if it's a book detail route: /view/book/:id
  const bookMatch = hash.match(/^\/([^/]+)\/book\/(\d+)$/);
  if (bookMatch) {
    return { view: bookMatch[1], bookId: parseInt(bookMatch[2], 10) };
  }

  // Otherwise it's a view route
  const view = hash.slice(1) || 'books'; // Remove leading slash
  return { view, bookId: null };
}

// Update URL hash based on current state
function updateRoute(view, bookId) {
  if (bookId) {
    window.location.hash = `#/${view}/book/${bookId}`;
  } else {
    window.location.hash = `#/${view}`;
  }
}

// Main App Component
function App() {
  const [user, setUser] = useState(null);
  const [isAdmin, setIsAdmin] = useState(false);
  const [currentView, setCurrentView] = useState('books');
  const [selectedBookId, setSelectedBookId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Initialize route from URL hash
  useEffect(() => {
    const route = parseRoute();
    if (route.view) {
      setCurrentView(route.view);
    }
    if (route.bookId) {
      setSelectedBookId(route.bookId);
    }
  }, []);

  // Listen to browser back/forward buttons
  useEffect(() => {
    const handleHashChange = () => {
      const route = parseRoute();
      if (route.view) {
        setCurrentView(route.view);
      }
      if (route.bookId) {
        setSelectedBookId(route.bookId);
      } else {
        setSelectedBookId(null);
      }
    };

    window.addEventListener('hashchange', handleHashChange);
    return () => window.removeEventListener('hashchange', handleHashChange);
  }, []);

  useEffect(() => {
    loadUser();
  }, []);

  const loadUser = async () => {
    setLoading(true);
    setError(null);
    try {
      const userRes = await api.get('/api/me');
      console.log('User response:', userRes);
      setUser(userRes);

      try {
        const adminRes = await api.get('/api/admin/status');
        setIsAdmin(adminRes.data.isAdmin || false);
      } catch (err) {
        setIsAdmin(false);
      }
    } catch (err) {
      console.error('Failed to load user:', err);

      // Check if it's a 500 error (server error) vs 401 (not authenticated)
      if (err.message && err.message.includes('500')) {
        setError(err.message);
      } else {
        // Not authenticated, show login page
        setUser(null);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleNavigate = (view) => {
    setCurrentView(view);
    setSelectedBookId(null);
    updateRoute(view, null);
  };

  const handleSelectBook = (bookId) => {
    setSelectedBookId(bookId);
    updateRoute(currentView, bookId);
  };

  const handleBackFromBook = () => {
    window.history.back();
  };

  if (loading) {
    return h('div', {
      style: {
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100vh',
        background: '#ecf0f1'
      }
    },
      h('div', { style: { textAlign: 'center' } },
        h('h2', null, 'Loading...')
      )
    );
  }

  // Show error page if there's a server error
  if (error) {
    return h(ErrorPage, { error: error, onRetry: loadUser });
  }

  // Show login page if user is not authenticated
  if (!user) {
    return h(LoginPage);
  }

  return h('div', { style: { minHeight: '100vh', background: '#ecf0f1' } },
    h(Header, {
      user: user,
      onNavigate: handleNavigate,
      currentView: currentView,
      isAdmin: isAdmin
    }),

    selectedBookId
      ? h(BookDetail, {
          bookId: selectedBookId,
          onBack: handleBackFromBook,
          onRefresh: () => {}
        })
      : [
          currentView === 'books' && h(BooksList, { onSelectBook: handleSelectBook }),
          currentView === 'starred' && h(BooksList, { starred: true, onSelectBook: handleSelectBook }),
          currentView === 'activity' && h(Activity, { onSelectBook: handleSelectBook }),
          currentView === 'kindle' && h(KindleManagement),
          currentView === 'admin' && isAdmin && h(AdminPanel)
        ]
  );
}

// Render the app
render(h(App), document.getElementById('app'));
