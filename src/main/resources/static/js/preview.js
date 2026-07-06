import { render, h } from 'preact';
import { useState } from 'preact/hooks';
import { api } from './utils/api.js';
import { Header } from './components/Header.js';
import { BooksList } from './components/BooksList.js';
import { BookDetail } from './components/BookDetail.js';
import { KindleManagement } from './components/KindleManagement.js';
import { AdminPanel } from './components/AdminPanel.js';

const previewUser = {
  data: {
    name: 'Preview User',
    avatarUrl: ''
  }
};

const books = [
  {
    id: 1,
    title: 'The Left Hand of Darkness',
    authors: ['Ursula K. Le Guin'],
    genres: ['Science Fiction', 'Classic'],
    language: 'EN',
    series: 'Hainish Cycle',
    seriesNumber: 4
  },
  {
    id: 2,
    title: 'Solaris',
    authors: ['Stanislaw Lem'],
    genres: ['Science Fiction', 'Philosophy'],
    language: 'EN'
  },
  {
    id: 3,
    title: 'A Wizard of Earthsea',
    authors: ['Ursula K. Le Guin'],
    genres: ['Fantasy', 'Coming of Age'],
    language: 'EN',
    series: 'Earthsea',
    seriesNumber: 1
  },
  {
    id: 4,
    title: 'The Master and Margarita',
    authors: ['Mikhail Bulgakov'],
    genres: ['Literary Fiction', 'Satire'],
    language: 'EN'
  },
  {
    id: 5,
    title: 'Roadside Picnic',
    authors: ['Arkady Strugatsky', 'Boris Strugatsky'],
    genres: ['Science Fiction', 'Adventure'],
    language: 'EN'
  },
  {
    id: 6,
    title: 'Invisible Cities',
    authors: ['Italo Calvino'],
    genres: ['Literary Fiction', 'Experimental'],
    language: 'EN'
  }
];

const devices = [
  { id: 1, name: 'Paperwhite', email: 'reader@kindle.com' },
  { id: 2, name: 'Travel Kindle', email: 'travel@kindle.com' }
];

const sendHistory = [
  {
    id: 1,
    bookTitle: 'Solaris',
    deviceName: 'Paperwhite',
    format: 'EPUB',
    status: 'COMPLETED',
    createdAt: new Date(Date.now() - 1000 * 60 * 24).toISOString()
  },
  {
    id: 2,
    bookTitle: 'Roadside Picnic',
    deviceName: 'Travel Kindle',
    format: 'EPUB',
    status: 'PROCESSING',
    createdAt: new Date(Date.now() - 1000 * 60 * 8).toISOString()
  }
];

function detailBook(id) {
  const book = books.find(item => item.id === id) || books[0];
  return {
    ...book,
    authors: book.authors.map(fullName => ({ fullName })),
    series: book.series ? { name: book.series } : null,
    annotation: 'A compact preview of the refreshed detail view. Metadata, actions, chips, and longer descriptions now sit on a lighter surface with softer borders and more readable spacing.'
  };
}

api.get = async (url) => {
  if (url.startsWith('/api/books?') || url.startsWith('/api/search/books?')) {
    return { data: { books, total: books.length, hasMore: false } };
  }

  const bookMatch = url.match(/^\/api\/books\/(\d+)$/);
  if (bookMatch) {
    return { data: detailBook(Number(bookMatch[1])) };
  }

  if (url.match(/^\/api\/books\/(\d+)\/similar$/)) {
    return { data: books.slice(1, 5) };
  }

  if (url === '/api/kindle/devices') {
    return { data: devices };
  }

  if (url.startsWith('/api/kindle/sends')) {
    return { data: { items: sendHistory } };
  }

  if (url === '/api/admin/jobs') {
    return {
      data: {
        status: 'RUNNING',
        startedAt: new Date(Date.now() - 1000 * 60 * 11).toISOString(),
        completedAt: null,
        inpFilesProcessed: 14,
        booksAdded: 1280,
        bookDeleted: 9,
        bookErrors: 2,
        messages: {
          [new Date(Date.now() - 1000 * 60 * 10).toISOString()]: 'Reading INPX metadata',
          [new Date(Date.now() - 1000 * 60 * 7).toISOString()]: 'Updating Lucene index',
          [new Date(Date.now() - 1000 * 60 * 2).toISOString()]: 'Generating enrichment queue'
        }
      }
    };
  }

  if (url.startsWith('/api/admin/users/pending')) {
    return {
      data: {
        users: [
          {
            id: 1,
            name: 'Ada Lovelace',
            email: 'ada@example.com',
            avatarUrl: '',
            createdAt: new Date(Date.now() - 1000 * 60 * 60 * 3).toISOString()
          }
        ]
      }
    };
  }

  return { data: null };
};

api.post = async () => ({ data: {} });
api.delete = async () => ({ data: {} });

function PreviewApp() {
  const [currentView, setCurrentView] = useState('books');
  const [selectedBookId, setSelectedBookId] = useState(null);

  const navigate = (view) => {
    setCurrentView(view);
    setSelectedBookId(null);
  };

  return h('div', { className: 'app-shell' },
    h(Header, {
      user: previewUser,
      currentView,
      onNavigate: navigate,
      isAdmin: true
    }),
    selectedBookId
      ? h(BookDetail, {
          bookId: selectedBookId,
          onBack: () => setSelectedBookId(null),
          onSelectBook: setSelectedBookId
        })
      : [
          currentView === 'books' && h(BooksList, {
            onSelectBook: setSelectedBookId,
            initialOffset: 0,
            onPageChange: () => {}
          }),
          currentView === 'kindle' && h(KindleManagement),
          currentView === 'admin' && h(AdminPanel)
        ]
  );
}

render(h(PreviewApp), document.getElementById('app'));
